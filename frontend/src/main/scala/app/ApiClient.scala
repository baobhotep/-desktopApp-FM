package app

import fmgame.shared.api._
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import app.MatchSummaryDtoCodec.*
import app.PlayerDtoCodec.*
import org.scalajs.dom.experimental.Fetch
import org.scalajs.dom.experimental.Headers
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.Thenable.Implicits.*

object ApiClient {
  private val ApiPrefix = "/api/v1"
  private val RequestTimeoutMs = 30000
  /** Gdy apiBaseUrl jest pusty lub równy origin strony, używaj względnych URL (ten sam origin, brak CORS). */
  private def baseUri(): String = {
    val base = AppState.apiBaseUrl.now()
    val origin = org.scalajs.dom.window.location.origin
    if (base.isEmpty || (origin.nonEmpty && base.startsWith(origin))) "" else base
  }

  /** Gdy token jest None, nie wysyłaj requestu – zwróć Left. */
  private def withToken[A](f: String => Future[Either[String, A]]): Future[Either[String, A]] =
    AppState.token.now() match {
      case None    => Future.successful(Left("Not logged in"))
      case Some(t) => f(t)
    }

  private def fetchEither[A](method: String, path: String, body: Option[String], token: Option[String])(
    decode: String => Either[String, A]
  ): Future[Either[String, A]] = {
    val uri = s"${baseUri()}$ApiPrefix$path"
    val headers = new Headers()
    headers.set("Content-Type", "application/json")
    token.foreach { t => headers.set("Authorization", s"Bearer $t") }
    val controller = new org.scalajs.dom.experimental.AbortController()
    val timeoutId = org.scalajs.dom.window.setTimeout(() => controller.abort(), RequestTimeoutMs)
    val init = scala.scalajs.js.Dictionary(
      "method" -> method,
      "headers" -> headers,
      "body" -> body.orNull,
      "signal" -> controller.signal
    ).asInstanceOf[org.scalajs.dom.experimental.RequestInit]
    Fetch.fetch(uri, init).toFuture.flatMap { r =>
      org.scalajs.dom.window.clearTimeout(timeoutId)
      r.text().toFuture.map { text =>
        if (r.ok) decode(text)
        else {
          io.circe.parser.decode[ErrorBody](text).fold(_ => Left(text), err => Left(err.message))
        }
      }
    }.recover { case t: Throwable => Left(t.getMessage) }
  }

  def login(req: LoginRequest): Future[Either[String, LoginResponse]] =
    fetchEither("POST", "/auth/login", Some(LoginRequest(req.email, req.password).asJson.noSpaces), None) { text =>
      decode[LoginResponse](text).left.map(_.getMessage)
    }

  def register(req: RegisterRequest): Future[Either[String, UserDto]] =
    fetchEither("POST", "/auth/register", Some(RegisterRequest(req.email, req.password, req.displayName).asJson.noSpaces), None) { text =>
      decode[UserDto](text).left.map(_.getMessage)
    }

  def me(token: String): Future[Either[String, UserDto]] =
    fetchEither("GET", "/auth/me", None, Some(token)) { text =>
      decode[UserDto](text).left.map(_.getMessage)
    }

  def createLeague(token: String, req: CreateLeagueRequest): Future[Either[String, CreateLeagueResponse]] =
    fetchEither("POST", "/leagues", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[CreateLeagueResponse](text).left.map(_.getMessage)
    }

  def getTable(token: String, leagueId: String): Future[Either[String, List[TableRowDto]]] =
    fetchEither("GET", s"/leagues/$leagueId/table", None, Some(token)) { text =>
      decode[List[TableRowDto]](text).left.map(_.getMessage)
    }

  def listLeagues(token: String): Future[Either[String, List[LeagueDto]]] =
    fetchEither("GET", "/leagues", None, Some(token)) { text =>
      decode[List[LeagueDto]](text).left.map(_.getMessage)
    }

  def listInvitations(token: String): Future[Either[String, List[InvitationDto]]] =
    fetchEither("GET", "/invitations", None, Some(token)) { text =>
      decode[List[InvitationDto]](text).left.map(_.getMessage)
    }

  def getLeague(token: String, leagueId: String): Future[Either[String, LeagueDto]] =
    fetchEither("GET", s"/leagues/$leagueId", None, Some(token)) { text =>
      decode[LeagueDto](text).left.map(_.getMessage)
    }

  def getLeaguePlayerStats(token: String, leagueId: String): Future[Either[String, LeaguePlayerStatsDto]] =
    fetchEither("GET", s"/leagues/$leagueId/player-stats", None, Some(token)) { text =>
      decode[LeaguePlayerStatsDto](text).left.map(_.getMessage)
    }

  def getLeaguePlayerAdvancedStats(token: String, leagueId: String): Future[Either[String, LeaguePlayerAdvancedStatsDto]] =
    fetchEither("GET", s"/leagues/$leagueId/player-stats-advanced", None, Some(token)) { text =>
      decode[LeaguePlayerAdvancedStatsDto](text).left.map(_.getMessage)
    }

  def getTrainingPlan(token: String, teamId: String): Future[Either[String, TrainingPlanDto]] =
    fetchEither("GET", s"/teams/$teamId/training-plan", None, Some(token)) { text =>
      decode[TrainingPlanDto](text).left.map(_.getMessage)
    }

  def upsertTrainingPlan(token: String, teamId: String, req: UpsertTrainingPlanRequest): Future[Either[String, TrainingPlanDto]] =
    fetchEither("PUT", s"/teams/$teamId/training-plan", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[TrainingPlanDto](text).left.map(_.getMessage)
    }

  def getFixtures(leagueId: String, limitOpt: Option[Int] = None, offsetOpt: Option[Int] = None): Future[Either[String, List[MatchDto]]] =
    withToken { token =>
      val q = (limitOpt, offsetOpt) match {
        case (Some(l), Some(o)) => s"?limit=$l&offset=$o"
        case (Some(l), None)    => s"?limit=$l&offset=0"
        case (None, Some(o))    => s"?limit=500&offset=$o"
        case _                  => ""
      }
      fetchEither("GET", s"/leagues/$leagueId/fixtures$q", None, Some(token)) { text =>
        decode[List[MatchDto]](text).left.map(_.getMessage)
      }
    }

  def getTeams(leagueId: String): Future[Either[String, List[TeamDto]]] =
    withToken(token => fetchEither("GET", s"/leagues/$leagueId/teams", None, Some(token)) { text =>
      decode[List[TeamDto]](text).left.map(_.getMessage)
    })

  def listLeaguePlayers(token: String, leagueId: String, pos: Option[String], minOverall: Option[String], q: Option[String]): Future[Either[String, LeaguePlayersDto]] = {
    val qp = List(
      pos.filter(_.nonEmpty).map(v => s"pos=${java.net.URLEncoder.encode(v, "UTF-8")}"),
      minOverall.filter(_.nonEmpty).map(v => s"minOverall=${java.net.URLEncoder.encode(v, "UTF-8")}"),
      q.filter(_.nonEmpty).map(v => s"q=${java.net.URLEncoder.encode(v, "UTF-8")}")
    ).flatten
    val suffix = if (qp.isEmpty) "" else s"?${qp.mkString("&")}"
    fetchEither("GET", s"/leagues/$leagueId/players$suffix", None, Some(token)) { text =>
      decode[LeaguePlayersDto](text).left.map(_.getMessage)
    }
  }

  def getShortlist(token: String, teamId: String): Future[Either[String, List[ShortlistEntryDto]]] =
    fetchEither("GET", s"/teams/$teamId/shortlist", None, Some(token)) { text =>
      decode[List[ShortlistEntryDto]](text).left.map(_.getMessage)
    }

  def addToShortlist(token: String, teamId: String, playerId: String): Future[Either[String, Unit]] =
    fetchEither("POST", s"/teams/$teamId/shortlist", Some(AddToShortlistRequest(playerId).asJson.noSpaces), Some(token)) { _ => Right(()) }

  def removeFromShortlist(token: String, teamId: String, playerId: String): Future[Either[String, Unit]] =
    fetchEither("DELETE", s"/teams/$teamId/shortlist/$playerId", None, Some(token)) { _ => Right(()) }

  def getScoutingReports(token: String, teamId: String): Future[Either[String, List[ScoutingReportDto]]] =
    fetchEither("GET", s"/teams/$teamId/scouting-reports", None, Some(token)) { text =>
      decode[List[ScoutingReportDto]](text).left.map(_.getMessage)
    }

  def createScoutingReport(token: String, teamId: String, req: CreateScoutingReportRequest): Future[Either[String, ScoutingReportDto]] =
    fetchEither("POST", s"/teams/$teamId/scouting-reports", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[ScoutingReportDto](text).left.map(_.getMessage)
    }

  def getTeam(teamId: String): Future[Either[String, TeamDto]] =
    withToken(token => fetchEither("GET", s"/teams/$teamId", None, Some(token)) { text =>
      decode[TeamDto](text).left.map(_.getMessage)
    })

  def getTeamPlayers(teamId: String): Future[Either[String, List[PlayerDto]]] =
    withToken(token => fetchEither("GET", s"/teams/$teamId/players", None, Some(token)) { text =>
      decode[List[PlayerDto]](text).left.map(_.getMessage)
    })

  def getMatch(matchId: String): Future[Either[String, MatchDto]] =
    withToken(token => fetchEither("GET", s"/matches/$matchId", None, Some(token)) { text =>
      decode[MatchDto](text).left.map(_.getMessage)
    })

  def getMatchLog(matchId: String, limitOpt: Option[Int] = None, offsetOpt: Option[Int] = None): Future[Either[String, MatchLogDto]] =
    withToken { token =>
      val query = (limitOpt, offsetOpt) match {
        case (Some(l), Some(o)) => s"?limit=$l&offset=$o"
        case (Some(l), None)    => s"?limit=$l&offset=0"
        case (None, Some(o))    => s"?limit=500&offset=$o"
        case _                  => ""
      }
      fetchEither("GET", s"/matches/$matchId/log$query", None, Some(token)) { text =>
        decode[MatchLogDto](text).left.map(_.getMessage)
      }
    }

  def getTransferWindows(leagueId: String): Future[Either[String, List[TransferWindowDto]]] =
    withToken(token => fetchEither("GET", s"/leagues/$leagueId/transfer-windows", None, Some(token)) { text =>
      decode[List[TransferWindowDto]](text).left.map(_.getMessage)
    })

  def getTransferOffers(leagueId: String, teamId: Option[String]): Future[Either[String, List[TransferOfferDto]]] =
    withToken { token =>
      val q = teamId.fold(s"?leagueId=$leagueId")(t => s"?leagueId=$leagueId&teamId=$t")
      fetchEither("GET", s"/transfer-offers$q", None, Some(token)) { text =>
        decode[List[TransferOfferDto]](text).left.map(_.getMessage)
      }
    }

  def playMatchday(token: String, leagueId: String): Future[Either[String, Unit]] =
    fetchEither("POST", s"/leagues/$leagueId/matchdays/current/play", None, Some(token)) { _ =>
      Right(())
    }

  def acceptInvitation(token: String, req: AcceptInvitationRequest): Future[Either[String, AcceptInvitationResponse]] =
    fetchEither("POST", "/invitations/accept", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[AcceptInvitationResponse](text).left.map(_.getMessage)
    }

  def submitMatchSquad(token: String, matchId: String, teamId: String, req: SubmitMatchSquadRequest): Future[Either[String, MatchSquadDto]] =
    fetchEither("PUT", s"/matches/$matchId/squads/$teamId", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[MatchSquadDto](text).left.map(_.getMessage)
    }

  def createTransferOffer(token: String, leagueId: String, req: CreateTransferOfferRequest): Future[Either[String, TransferOfferDto]] =
    fetchEither("POST", s"/leagues/$leagueId/transfer-offers", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[TransferOfferDto](text).left.map(_.getMessage)
    }

  def acceptTransferOffer(token: String, offerId: String): Future[Either[String, Unit]] =
    fetchEither("POST", s"/transfer-offers/$offerId/accept", None, Some(token)) { _ => Right(()) }

  def rejectTransferOffer(token: String, offerId: String): Future[Either[String, Unit]] =
    fetchEither("POST", s"/transfer-offers/$offerId/reject", None, Some(token)) { _ => Right(()) }

  def updatePlayer(token: String, playerId: String, req: UpdatePlayerRequest): Future[Either[String, PlayerDto]] =
    fetchEither("PATCH", s"/players/$playerId", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[PlayerDto](text).left.map(_.getMessage)
    }

  def getMatchSquads(token: String, matchId: String): Future[Either[String, List[MatchSquadDto]]] =
    fetchEither("GET", s"/matches/$matchId/squads", None, Some(token)) { text =>
      decode[List[MatchSquadDto]](text).left.map(_.getMessage)
    }

  def getAssistantTip(matchId: String, teamId: String): Future[Either[String, AssistantTipDto]] =
    withToken(token =>
      fetchEither("GET", s"/matches/$matchId/assistant-tip?teamId=${scala.scalajs.js.URIUtils.encodeURIComponent(teamId)}", None, Some(token)) { text =>
        decode[AssistantTipDto](text).left.map(_.getMessage)
      }
    )

  def getGamePlans(teamId: String): Future[Either[String, List[GamePlanSnapshotDto]]] =
    withToken(token => fetchEither("GET", s"/teams/$teamId/game-plans", None, Some(token)) { text =>
      decode[List[GamePlanSnapshotDto]](text).left.map(_.getMessage)
    })

  def getGamePlanSnapshot(teamId: String, snapshotId: String): Future[Either[String, GamePlanSnapshotDetailDto]] =
    withToken(token => fetchEither("GET", s"/teams/$teamId/game-plans/$snapshotId", None, Some(token)) { text =>
      decode[GamePlanSnapshotDetailDto](text).left.map(_.getMessage)
    })

  def saveGamePlan(token: String, teamId: String, req: SaveGamePlanRequest): Future[Either[String, GamePlanSnapshotDto]] =
    fetchEither("POST", s"/teams/$teamId/game-plans", Some(req.asJson.noSpaces), Some(token)) { text =>
      decode[GamePlanSnapshotDto](text).left.map(_.getMessage)
    }

  def inviteToLeague(token: String, leagueId: String, email: String): Future[Either[String, Unit]] =
    fetchEither("POST", s"/leagues/$leagueId/invite", Some(InviteRequest(email).asJson.noSpaces), Some(token)) { _ => Right(()) }

  def addBots(token: String, leagueId: String, count: Int): Future[Either[String, Unit]] =
    fetchEither("POST", s"/leagues/$leagueId/add-bots", Some(AddBotsRequest(count).asJson.noSpaces), Some(token)) { _ => Right(()) }

  def startSeason(token: String, leagueId: String, startDate: Option[String]): Future[Either[String, LeagueDto]] =
    fetchEither("POST", s"/leagues/$leagueId/start", Some(StartSeasonRequest(startDate).asJson.noSpaces), Some(token)) { text =>
      decode[LeagueDto](text).left.map(_.getMessage)
    }

  def exportMatchLogs(token: String, matchIds: List[String], format: String, leagueId: Option[String] = None, fromMatchday: Option[Int] = None, toMatchday: Option[Int] = None, teamId: Option[String] = None, eventTypes: Option[List[String]] = None): Future[Either[String, String]] = {
      val base = io.circe.Json.obj("matchIds" -> matchIds.asJson, "format" -> format.asJson)
      val withFilters = leagueId.fold(base)(id => base.deepMerge(io.circe.Json.obj("leagueId" -> io.circe.Json.fromString(id))))
        .deepMerge(fromMatchday.fold(io.circe.Json.obj())(m => io.circe.Json.obj("fromMatchday" -> io.circe.Json.fromInt(m))))
        .deepMerge(toMatchday.fold(io.circe.Json.obj())(m => io.circe.Json.obj("toMatchday" -> io.circe.Json.fromInt(m))))
        .deepMerge(teamId.fold(io.circe.Json.obj())(id => io.circe.Json.obj("teamId" -> io.circe.Json.fromString(id))))
        .deepMerge(eventTypes.fold(io.circe.Json.obj())(types => io.circe.Json.obj("eventTypes" -> types.asJson)))
      fetchEither("POST", s"/export/match-logs", Some(withFilters.noSpaces), Some(token)) { text => Right(text) }
    }

  def getH2H(token: String, leagueId: String, teamId1: String, teamId2: String, limit: Int = 10): Future[Either[String, List[MatchDto]]] =
    fetchEither("GET", s"/leagues/$leagueId/h2h?teamId1=${scala.scalajs.js.URIUtils.encodeURIComponent(teamId1)}&teamId2=${scala.scalajs.js.URIUtils.encodeURIComponent(teamId2)}&limit=$limit", None, Some(token)) { text =>
      decode[List[MatchDto]](text).left.map(_.getMessage)
    }

  def getMatchdayPrognosis(token: String, leagueId: String, matchday: Option[Int]): Future[Either[String, List[MatchPrognosisDto]]] = {
      val q = matchday.fold("")(m => s"&matchday=$m")
      fetchEither("GET", s"/leagues/$leagueId/matchday-prognosis?$q".stripPrefix("&"), None, Some(token)) { text =>
        decode[List[MatchPrognosisDto]](text).left.map(_.getMessage)
      }
    }

  def getComparePlayers(token: String, leagueId: String, playerId1: String, playerId2: String): Future[Either[String, ComparePlayersDto]] =
    fetchEither("GET", s"/leagues/$leagueId/compare-players?playerId1=${scala.scalajs.js.URIUtils.encodeURIComponent(playerId1)}&playerId2=${scala.scalajs.js.URIUtils.encodeURIComponent(playerId2)}", None, Some(token)) { text =>
      decode[ComparePlayersDto](text).left.map(_.getMessage)
    }

  def submitPressConference(token: String, matchId: String, teamId: String, phase: String, tone: String): Future[Either[String, Unit]] =
    fetchEither("POST", s"/matches/$matchId/press-conference?teamId=$teamId", Some(io.circe.Json.obj("phase" -> phase.asJson, "tone" -> tone.asJson).noSpaces), Some(token)) { _ =>
      Right(())
    }
}
