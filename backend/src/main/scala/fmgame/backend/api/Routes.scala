package fmgame.backend.api

import fmgame.backend.service.*
import fmgame.backend.auth.*
import fmgame.backend.domain.AppError
import fmgame.shared.api.*
import fmgame.shared.domain.*
import zio.*
import zio.http.*
import zio.http.codec.PathCodec
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.Header.{AccessControlAllowOrigin, AccessControlAllowMethods, AccessControlAllowHeaders, Origin}
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.generic.auto.*
import fmgame.backend.api.MatchSummaryDtoCodec.*
import java.time.ZoneId

class ApiRoutes(
  userService: UserService,
  leagueService: LeagueService,
  jwtSecret: String,
  adminSecret: Option[String],
  allowedOrigin: Option[String] = None
) {

  private def errBody(code: String, message: String): String =
    ErrorBody(code, message).asJson.noSpaces

  private def jsonResp[A: io.circe.Encoder](a: A, status: Status): Response =
    Response.json(a.asJson.noSpaces).status(status)

  private def errResp(status: Status, code: String, message: String): Response =
    Response.json(errBody(code, message)).status(status)

  private def parseBody[A: io.circe.Decoder](req: Request): ZIO[Any, String, A] =
    req.body.asString(Charsets.Utf8)
      .orElseFail("Missing body")
      .flatMap(s => ZIO.fromEither(decode[A](s).left.map(_.getMessage)))

  private def extractUserId(req: Request): Option[UserId] =
    req.rawHeader(Header.Authorization).flatMap { v =>
      if (v.startsWith("Bearer ")) AuthService.verifyToken(v.drop(7), jwtSecret).map(p => UserId(p.userId))
      else None
    }

  private def isValidTimezone(id: String): Boolean =
    try { ZoneId.of(id); true } catch { case _: Exception => false }

  private def runZio[A](eff: ZIO[Any, String, A]): ZIO[Any, Nothing, Either[String, A]] =
    eff.tapError(err => ZIO.logWarning(s"API error: $err")).either

  private def classifyError(err: String): Response = {
    val appErr = AppError.fromServiceError(err)
    appErr match {
      case _: AppError.Forbidden       => errResp(Status.Forbidden, "FORBIDDEN", appErr.message)
      case _: AppError.NotFound        => errResp(Status.NotFound, "NOT_FOUND", appErr.message)
      case _: AppError.ValidationError => errResp(Status.BadRequest, "VALIDATION_ERROR", appErr.message)
      case _: AppError.Conflict        => errResp(Status.Conflict, "CONFLICT", appErr.message)
      case _: AppError.General         => errResp(Status.BadRequest, "BAD_REQUEST", appErr.message)
    }
  }

  private val unauthorized: Response = errResp(Status.Unauthorized, "UNAUTHORIZED", "Missing or invalid token")
  private def forbidden(msg: String): Response = errResp(Status.Forbidden, "FORBIDDEN", msg)

  private def pathSegment(req: Request, index: Int): Option[String] =
    req.path.segments.toList.lift(index).map(_.toString)

  val app: zio.http.Routes[Any, Response] = zio.http.Routes(
    Method.GET / "metrics" -> handler { (req: Request) =>
      val adminOk = adminSecret.exists(s => req.rawHeader("X-Admin-Secret").exists(h => java.security.MessageDigest.isEqual(s.getBytes, h.getBytes)))
      val userOk = extractUserId(req).isDefined
      if (adminOk || userOk)
        leagueService.getMetrics.map(metrics => jsonResp(metrics, Status.Ok))
      else ZIO.succeed(unauthorized)
    },

    Method.POST / "api" / "v1" / "auth" / "register" -> handler { (req: Request) =>
      parseBody[RegisterRequest](req).flatMap { body =>
        runZio(userService.register(body.email, body.password, body.displayName)).map {
          case Left(err)  => errResp(Status.BadRequest, "BAD_REQUEST", err)
          case Right(user) => jsonResp(user, Status.Created)
        }
      }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
    }.sandbox,

    Method.POST / "api" / "v1" / "auth" / "login" -> handler { (req: Request) =>
      parseBody[LoginRequest](req).flatMap { body =>
        runZio(userService.login(body.email, body.password, jwtSecret)).map {
          case Left(err)   => errResp(Status.Unauthorized, "UNAUTHORIZED", err)
          case Right(login) => jsonResp(login, Status.Ok)
        }
      }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
    }.sandbox,

    Method.GET / "api" / "v1" / "auth" / "me" -> handler { (req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(userService.getById(userId)).map {
            case Left(_)  => unauthorized
            case Right(user) => jsonResp(user, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" -> handler { (req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.listLeagues(userId)).map {
            case Left(err)   => classifyError(err)
            case Right(list) => jsonResp(list, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "invitations" -> handler { (req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.listPendingInvitations(userId)).map {
            case Left(err)   => classifyError(err)
            case Right(list) => jsonResp(list, Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "leagues" -> handler { (req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(creatorId) =>
          parseBody[CreateLeagueRequest](req).flatMap { body =>
            val tz = body.timezone.getOrElse("Europe/Warsaw")
            if (!isValidTimezone(tz)) ZIO.succeed(errResp(Status.BadRequest, "BAD_REQUEST", s"Invalid timezone: $tz"))
            else runZio(leagueService.create(body.name, body.teamCount, body.myTeamName, tz, creatorId)).map {
              case Left(err)  => classifyError(err)
              case Right((league, team)) => jsonResp(CreateLeagueResponse(league, team), Status.Created)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getLeagueForUser(LeagueId(leagueId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(league) => jsonResp(league, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "table" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getTableForUser(LeagueId(leagueId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(table) => jsonResp(table, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "player-stats" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getLeaguePlayerStatsForUser(LeagueId(leagueId), userId)).map {
            case Left(err)  => classifyError(err)
            case Right(stats) => jsonResp(stats, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "player-stats-advanced" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getLeaguePlayerAdvancedStatsForUser(LeagueId(leagueId), userId)).map {
            case Left(err)  => classifyError(err)
            case Right(stats) => jsonResp(stats, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") / "training-plan" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getTrainingPlanForUser(TeamId(teamId), userId)).map {
            case Left(err)  => classifyError(err)
            case Right(plan) => jsonResp(plan, Status.Ok)
          }
      }
    }.sandbox,

    Method.PUT / "api" / "v1" / "teams" / PathCodec.string("teamId") / "training-plan" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[UpsertTrainingPlanRequest](req).flatMap { body =>
            runZio(leagueService.upsertTrainingPlanForUser(TeamId(teamId), userId, body.week)).map {
              case Left(err)  => classifyError(err)
              case Right(plan) => jsonResp(plan, Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "teams" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.listTeamsForUser(LeagueId(leagueId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(teams) => jsonResp(teams, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "players" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val posOpt = req.url.queryParam("pos")
          val qOpt = req.url.queryParam("q")
          val minOverallOpt = req.url.queryParam("minOverall").flatMap(s => scala.util.Try(s.toDouble).toOption)
          runZio(leagueService.listLeaguePlayersForUser(LeagueId(leagueId), userId, posOpt, minOverallOpt, qOpt)).map {
            case Left(err) => classifyError(err)
            case Right(list) => jsonResp(list, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") / "shortlist" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getShortlistForUser(TeamId(teamId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(list) => jsonResp(list, Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "teams" / PathCodec.string("teamId") / "shortlist" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[AddToShortlistRequest](req).flatMap { body =>
            runZio(leagueService.addToShortlistForUser(TeamId(teamId), userId, PlayerId(body.playerId))).map {
              case Left(err) => classifyError(err)
              case Right(_)  => jsonResp((), Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.DELETE / "api" / "v1" / "teams" / PathCodec.string("teamId") / "shortlist" / PathCodec.string("playerId") -> handler { (teamId: String, playerId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.removeFromShortlistForUser(TeamId(teamId), userId, PlayerId(playerId))).map {
            case Left(err) => classifyError(err)
            case Right(_)  => jsonResp((), Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") / "scouting-reports" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.listScoutingReportsForUser(TeamId(teamId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(list) => jsonResp(list, Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "teams" / PathCodec.string("teamId") / "scouting-reports" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[CreateScoutingReportRequest](req).flatMap { body =>
            runZio(leagueService.createScoutingReportForUser(TeamId(teamId), userId, PlayerId(body.playerId), body.rating, body.notes)).map {
              case Left(err) => classifyError(err)
              case Right(r)  => jsonResp(r, Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "fixtures" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val limitOpt = req.url.queryParam("limit").flatMap(s => scala.util.Try(s.toInt).toOption)
          val offsetOpt = req.url.queryParam("offset").flatMap(s => scala.util.Try(s.toInt).toOption)
          runZio(leagueService.getFixturesForUser(LeagueId(leagueId), limitOpt, offsetOpt, userId)).map {
            case Left(err) => classifyError(err)
            case Right(fixtures) => jsonResp(fixtures, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "h2h" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val t1 = req.url.queryParam("teamId1").getOrElse("")
          val t2 = req.url.queryParam("teamId2").getOrElse("")
          if (t1.isEmpty || t2.isEmpty) ZIO.succeed(errResp(Status.BadRequest, "BAD_REQUEST", "teamId1 and teamId2 are required"))
          else {
            val limit = req.url.queryParam("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(10).min(50)
            runZio(leagueService.getH2HForUser(LeagueId(leagueId), TeamId(t1), TeamId(t2), limit, userId)).map {
              case Left(err) => classifyError(err)
              case Right(matches) => jsonResp(matches, Status.Ok)
            }
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "matchday-prognosis" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val matchdayOpt = req.url.queryParam("matchday").flatMap(s => scala.util.Try(s.toInt).toOption)
          runZio(leagueService.getMatchdayPrognosisForUser(LeagueId(leagueId), matchdayOpt, userId)).map {
            case Left(err) => classifyError(err)
            case Right(prognoses) => jsonResp(prognoses, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "compare-players" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val pid1 = req.url.queryParam("playerId1").getOrElse("")
          val pid2 = req.url.queryParam("playerId2").getOrElse("")
          if (pid1.isEmpty || pid2.isEmpty) ZIO.succeed(errResp(Status.BadRequest, "BAD_REQUEST", "playerId1 and playerId2 are required"))
          else runZio(leagueService.getComparePlayersForUser(LeagueId(leagueId), PlayerId(pid1), PlayerId(pid2), userId)).map {
            case Left(err) => classifyError(err)
            case Right(dto) => jsonResp(dto, Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "invite" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(inviterId) =>
          parseBody[InviteRequest](req).flatMap { body =>
            runZio(leagueService.createInvitation(LeagueId(leagueId), body.email, inviterId)).map {
              case Left(err) => classifyError(err)
              case Right(inv) => jsonResp(inv, Status.Created)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "invitations" / "accept" -> handler { (req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[AcceptInvitationRequest](req).flatMap { body =>
            runZio(leagueService.acceptInvitation(body.token, body.teamName, userId)).map {
              case Left(err) => classifyError(err)
              case Right((league, team)) => jsonResp(AcceptInvitationResponse(league, team), Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "start" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[StartSeasonRequest](req).flatMap { body =>
            runZio(leagueService.startSeason(LeagueId(leagueId), userId, body.startDate)).map {
              case Left(err) => classifyError(err)
              case Right(league) => jsonResp(league, Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "add-bots" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[AddBotsRequest](req).flatMap { body =>
            runZio(leagueService.addBots(LeagueId(leagueId), userId, body.count)).map {
              case Left(err) => classifyError(err)
              case Right(_) => Response.status(Status.Created)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "matchdays" / "current" / "play" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.playMatchday(LeagueId(leagueId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(_) => jsonResp((), Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "matches" / PathCodec.string("matchId") -> handler { (matchId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getMatchForUser(MatchId(matchId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(m) => jsonResp(m, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "matches" / PathCodec.string("matchId") / "log" -> handler { (matchId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val limitOpt = req.url.queryParam("limit").flatMap(s => scala.util.Try(s.toInt).toOption)
          val offsetOpt = req.url.queryParam("offset").flatMap(s => scala.util.Try(s.toInt).toOption)
          runZio(leagueService.getMatchLogForUser(MatchId(matchId), limitOpt, offsetOpt, userId)).map {
            case Left(err) => classifyError(err)
            case Right(log) => jsonResp(log, Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "matches" / PathCodec.string("matchId") / "press-conference" -> handler { (matchId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val teamIdOpt = req.url.queryParam("teamId")
          (for {
            body   <- parseBody[CreatePressConferenceRequest](req)
            teamId <- ZIO.fromOption(teamIdOpt).orElseFail("Missing teamId query param")
          } yield (body, teamId)).flatMap { case (body, teamId) =>
            runZio(leagueService.applyPressConference(MatchId(matchId), TeamId(teamId), userId, body.phase, body.tone)).map {
              case Left(err) => classifyError(err)
              case Right(_)  => Response.json(io.circe.Json.obj("status" -> io.circe.Json.fromString("ok")).noSpaces).status(Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid request"))
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "matches" / PathCodec.string("matchId") / "squads" -> handler { (matchId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getMatchSquadsForUser(MatchId(matchId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(squads) => jsonResp(squads, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "matches" / PathCodec.string("matchId") / "assistant-tip" -> handler { (matchId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          val teamIdOpt = req.url.queryParam("teamId")
          ZIO.fromOption(teamIdOpt).flatMap { teamId =>
            runZio(leagueService.getAssistantTipForUser(MatchId(matchId), TeamId(teamId), userId)).map {
              case Left(err) => classifyError(err)
              case Right(dto) => jsonResp(dto, Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Missing teamId query param"))
      }
    }.sandbox,

    Method.PUT / "api" / "v1" / "matches" / PathCodec.string("matchId") / "squads" / PathCodec.string("squadTeamId") -> handler { (matchId: String, squadTeamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[SubmitMatchSquadRequest](req).flatMap { body =>
            runZio(leagueService.submitMatchSquad(MatchId(matchId), TeamId(squadTeamId), userId, body)).map {
              case Left(err) => classifyError(err)
              case Right(squad) => jsonResp(squad, Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getTeamForUser(TeamId(teamId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(team) => jsonResp(team, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") / "players" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getTeamPlayersForUser(TeamId(teamId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(players) => jsonResp(players, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") / "game-plans" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getTeamGamePlansForUser(TeamId(teamId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(plans) => jsonResp(plans, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "teams" / PathCodec.string("teamId") / "game-plans" / PathCodec.string("snapshotId") -> handler { (teamId: String, snapshotId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getGamePlanSnapshotForUser(TeamId(teamId), GamePlanSnapshotId(snapshotId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(detail) => jsonResp(detail, Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "teams" / PathCodec.string("teamId") / "game-plans" -> handler { (teamId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[SaveGamePlanRequest](req).flatMap { body =>
            runZio(leagueService.saveGamePlan(TeamId(teamId), userId, body.name, body.gamePlanJson)).map {
              case Left(err) => classifyError(err)
              case Right(snap) => jsonResp(snap, Status.Created)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "transfer-windows" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.getTransferWindowsForUser(LeagueId(leagueId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(windows) => jsonResp(windows, Status.Ok)
          }
      }
    }.sandbox,

    Method.GET / "api" / "v1" / "transfer-offers" -> handler { (req: Request) =>
      val leagueIdOpt = req.url.queryParam("leagueId")
      val teamIdOpt = req.url.queryParam("teamId")
      leagueIdOpt match {
        case None => ZIO.succeed(errResp(Status.BadRequest, "BAD_REQUEST", "leagueId query parameter required"))
        case Some(lid) =>
          extractUserId(req) match {
            case None => ZIO.succeed(unauthorized)
            case Some(userId) =>
              runZio(leagueService.getTransferOffersForUser(LeagueId(lid), teamIdOpt.map(TeamId.apply), userId)).map {
                case Left(err) => classifyError(err)
                case Right(offers) => jsonResp(offers, Status.Ok)
              }
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "leagues" / PathCodec.string("leagueId") / "transfer-offers" -> handler { (leagueId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[CreateTransferOfferRequest](req).flatMap { body =>
            runZio(leagueService.createTransferOffer(LeagueId(leagueId), userId, body)).map {
              case Left(err) => classifyError(err)
              case Right(offer) => jsonResp(offer, Status.Created)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "transfer-offers" / PathCodec.string("offerId") / "accept" -> handler { (offerId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.acceptTransferOffer(TransferOfferId(offerId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(_) => jsonResp((), Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "transfer-offers" / PathCodec.string("offerId") / "reject" -> handler { (offerId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          runZio(leagueService.rejectTransferOffer(TransferOfferId(offerId), userId)).map {
            case Left(err) => classifyError(err)
            case Right(_) => jsonResp((), Status.Ok)
          }
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "transfer-offers" / PathCodec.string("offerId") / "counter" -> handler { (offerId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[CounterTransferOfferRequest](req).flatMap { body =>
            scala.util.Try(BigDecimal(body.counterAmount.toString)).fold(
              _ => ZIO.succeed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid counter amount")),
              amount => runZio(leagueService.counterTransferOffer(TransferOfferId(offerId), userId, amount)).map {
                case Left(err) => classifyError(err)
                case Right(offer) => jsonResp(offer, Status.Ok)
              }
            )
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.PATCH / "api" / "v1" / "players" / PathCodec.string("playerId") -> handler { (playerId: String, req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[UpdatePlayerRequest](req).flatMap { body =>
            runZio(leagueService.updatePlayer(PlayerId(playerId), userId, body)).map {
              case Left(err) => classifyError(err)
              case Right(player) => jsonResp(player, Status.Ok)
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "export" / "match-logs" -> handler { (req: Request) =>
      extractUserId(req) match {
        case None => ZIO.succeed(unauthorized)
        case Some(userId) =>
          parseBody[ExportMatchLogsRequest](req).flatMap { body =>
            runZio(leagueService.exportMatchLogsWithFilters(
              body.matchIds.map(MatchId.apply),
              body.format,
              userId,
              body.leagueId.map(LeagueId.apply),
              body.fromMatchday,
              body.toMatchday,
              body.teamId.map(TeamId.apply),
              body.eventTypes
            )).map {
              case Left(err) => classifyError(err)
              case Right(out) =>
                val contentType = if (body.format.equalsIgnoreCase("csv")) "text/csv; charset=utf-8" else "application/json; charset=utf-8"
                Response(Status.Ok, headers = Headers(Header.Custom("Content-Type", contentType)), body = Body.fromString(out))
            }
          }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Invalid JSON"))
      }
    }.sandbox,

    Method.POST / "api" / "v1" / "admin" / "models" / PathCodec.string("kind") -> handler { (kind: String, req: Request) =>
      val adminOk = adminSecret.exists { secret =>
        req.rawHeader("X-Admin-Secret").exists(h => java.security.MessageDigest.isEqual(secret.getBytes, h.getBytes))
      }
      if (!adminOk) ZIO.succeed(forbidden("Admin access required"))
      else
        req.body.asChunk.flatMap { chunk =>
          val body = chunk.toArray
          val ct = req.header(Header.ContentType).fold("application/octet-stream")(_.renderedValue)
          runZio(leagueService.uploadEngineModel(kind, ct, body)).map {
            case Left(err) => errResp(Status.BadRequest, "BAD_REQUEST", err)
            case Right(_) => Response.json(io.circe.Json.obj("status" -> io.circe.Json.fromString("ok")).noSpaces).status(Status.Ok)
          }
        }.orElseSucceed(errResp(Status.BadRequest, "BAD_REQUEST", "Missing body"))
    }.sandbox
  ) @@ cors(
    CorsConfig(
      allowedOrigin = allowedOrigin match {
        case Some(origin) => { (o: Origin) =>
          val rendered = o.renderedValue
          val allowed = rendered == origin || rendered == s"https://$origin" || rendered == s"http://$origin"
          if (allowed) Some(AccessControlAllowOrigin.Specific(o)) else None
        }
        case None => _ => Some(AccessControlAllowOrigin.All)
      },
      allowedMethods = AccessControlAllowMethods.All,
      allowedHeaders = AccessControlAllowHeaders.All
    )
  )
}

private case class ExportMatchLogsRequest(
  matchIds: List[String],
  format: String,
  leagueId: Option[String] = None,
  fromMatchday: Option[Int] = None,
  toMatchday: Option[Int] = None,
  teamId: Option[String] = None,
  /** Gdy podane: eksport tylko zdarzeń o typie z listy (np. ["Pass", "Shot", "Goal"]). */
  eventTypes: Option[List[String]] = None
)
