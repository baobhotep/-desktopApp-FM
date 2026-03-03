package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.backend.engine.*
import fmgame.backend.repository.*
import fmgame.shared.domain.*
import fmgame.shared.api.*
import zio.*
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import cats.MonadError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import scala.util.Random
import io.circe.parser.decode
import io.circe.generic.auto._

trait LeagueService {
  def create(name: String, teamCount: Int, myTeamName: String, timezone: String, creatorUserId: UserId): ZIO[Any, String, (LeagueDto, TeamDto)]
  def getById(leagueId: LeagueId): ZIO[Any, String, LeagueDto]
  /** League by id with access check: fails with "Forbidden" if user has no team in league. */
  def getLeagueForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, LeagueDto]
  def listLeagues(userId: UserId): ZIO[Any, String, List[LeagueDto]]
  def listTeams(leagueId: LeagueId): ZIO[Any, String, List[TeamDto]]
  /** Teams with access check. */
  def listTeamsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TeamDto]]
  def getTable(leagueId: LeagueId): ZIO[Any, String, List[TableRowDto]]
  /** Table with access check. */
  def getTableForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TableRowDto]]
  def createInvitation(leagueId: LeagueId, email: String, invitedByUserId: UserId): ZIO[Any, String, InvitationDto]
  def listPendingInvitations(userId: UserId): ZIO[Any, String, List[InvitationDto]]
  def acceptInvitation(token: String, teamName: String, userId: UserId): ZIO[Any, String, (LeagueDto, TeamDto)]
  def startSeason(leagueId: LeagueId, userId: UserId, startDateOpt: Option[String]): ZIO[Any, String, LeagueDto]
  def getFixtures(leagueId: LeagueId, limitOpt: Option[Int] = None, offsetOpt: Option[Int] = None): ZIO[Any, String, List[MatchDto]]
  /** Fixtures with access check. */
  def getFixturesForUser(leagueId: LeagueId, limitOpt: Option[Int], offsetOpt: Option[Int], userId: UserId): ZIO[Any, String, List[MatchDto]]
  def addBots(leagueId: LeagueId, userId: UserId, count: Int): ZIO[Any, String, Unit]
  def playMatchday(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit]
  def getMatch(matchId: MatchId): ZIO[Any, String, MatchDto]
  /** Match with access check (user must have team in match's league). */
  def getMatchForUser(matchId: MatchId, userId: UserId): ZIO[Any, String, MatchDto]
  def getMatchLog(matchId: MatchId, limitOpt: Option[Int], offsetOpt: Option[Int]): ZIO[Any, String, MatchLogDto]
  def getMatchLogForUser(matchId: MatchId, limitOpt: Option[Int], offsetOpt: Option[Int], userId: UserId): ZIO[Any, String, MatchLogDto]
  def getMatchSquads(matchId: MatchId): ZIO[Any, String, List[MatchSquadDto]]
  def getMatchSquadsForUser(matchId: MatchId, userId: UserId): ZIO[Any, String, List[MatchSquadDto]]
  /** Rada asystenta przed meczem (formacja rywala, sugestie pressing). teamId = drużyna użytkownika. */
  def getAssistantTipForUser(matchId: MatchId, teamId: TeamId, userId: UserId): ZIO[Any, String, AssistantTipDto]
  def getTeam(teamId: TeamId): ZIO[Any, String, TeamDto]
  /** Team with access check. */
  def getTeamForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, TeamDto]
  def getTeamPlayers(teamId: TeamId): ZIO[Any, String, List[PlayerDto]]
  def getTeamPlayersForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[PlayerDto]]
  def getTeamGamePlans(teamId: TeamId): ZIO[Any, String, List[GamePlanSnapshotDto]]
  def getTeamGamePlansForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[GamePlanSnapshotDto]]
  def getGamePlanSnapshot(teamId: TeamId, snapshotId: GamePlanSnapshotId): ZIO[Any, String, GamePlanSnapshotDetailDto]
  def getGamePlanSnapshotForUser(teamId: TeamId, snapshotId: GamePlanSnapshotId, userId: UserId): ZIO[Any, String, GamePlanSnapshotDetailDto]
  def getTransferWindows(leagueId: LeagueId): ZIO[Any, String, List[TransferWindowDto]]
  def getTransferWindowsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TransferWindowDto]]
  def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId]): ZIO[Any, String, List[TransferOfferDto]]
  def getTransferOffersForUser(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): ZIO[Any, String, List[TransferOfferDto]]
  def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): ZIO[Any, String, TransferOfferDto]
  def acceptTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit]
  def rejectTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit]
  def submitMatchSquad(matchId: MatchId, teamId: TeamId, userId: UserId, req: SubmitMatchSquadRequest): ZIO[Any, String, MatchSquadDto]
  def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): ZIO[Any, String, PlayerDto]
  def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): ZIO[Any, String, GamePlanSnapshotDto]
  /** Called by scheduler: play matchdays that are due (17:00 Wed/Sat in league timezone). */
  def runScheduledMatchdays(): ZIO[Any, Nothing, Unit]
  /** Eksport logów meczów do CSV lub StatsBomb-like JSON (pod trenowanie xG/VAEP w Pythonie). Maks. 50 meczów; tylko mecze z lig użytkownika. eventTypesOpt: gdy podane, eksport tylko zdarzeń o typie z listy. */
  def exportMatchLogs(matchIds: List[MatchId], format: String, userId: UserId, eventTypesOpt: Option[List[String]] = None): ZIO[Any, String, String]
  /** Eksport z opcjonalnymi filtrami: gdy leagueId + (fromMatchday/toMatchday/teamId), matchIds są uzupełniane z terminarza ligi. eventTypes: gdy podane, eksport tylko zdarzeń o typie z listy. */
  def exportMatchLogsWithFilters(matchIds: List[MatchId], format: String, userId: UserId, leagueIdOpt: Option[LeagueId], fromMatchdayOpt: Option[Int], toMatchdayOpt: Option[Int], teamIdOpt: Option[TeamId], eventTypesOpt: Option[List[String]]): ZIO[Any, String, String]
  /** Wgrywanie modelu xG/VAEP (plik .onnx lub .json); przełączenie silnika bez redeployu. */
  def uploadEngineModel(kind: String, contentType: String, body: Array[Byte]): ZIO[Any, String, Unit]
  /** Statystyki sezonowe zawodników w lidze (król strzelców, lider asyst) z kontrolą dostępu. */
  def getLeaguePlayerStatsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, LeaguePlayerStatsDto]
  /** Zaawansowane statystyki sezonowe zawodników w lidze (Data Hub light). */
  def getLeaguePlayerAdvancedStatsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, LeaguePlayerAdvancedStatsDto]
  /** H2H: ostatnie N meczów między dwiema drużynami w lidze. */
  def getH2HForUser(leagueId: LeagueId, teamId1: TeamId, teamId2: TeamId, limit: Int, userId: UserId): ZIO[Any, String, List[MatchDto]]
  /** Prognoza kolejki (Elo): P(wygrana gosp.), P(remis), P(wygrana gości) per mecz. */
  def getMatchdayPrognosisForUser(leagueId: LeagueId, matchdayOpt: Option[Int], userId: UserId): ZIO[Any, String, List[MatchPrognosisDto]]
  /** Porównanie dwóch zawodników (atrybuty + statystyki sezonu). */
  def getComparePlayersForUser(leagueId: LeagueId, playerId1: PlayerId, playerId2: PlayerId, userId: UserId): ZIO[Any, String, ComparePlayersDto]

  /** Plan treningowy drużyny (MVP) – tylko właściciel. */
  def getTrainingPlanForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, TrainingPlanDto]
  def upsertTrainingPlanForUser(teamId: TeamId, userId: UserId, week: List[String]): ZIO[Any, String, TrainingPlanDto]

  /** Scouting / recruitment (MVP). */
  def listLeaguePlayersForUser(leagueId: LeagueId, userId: UserId, posOpt: Option[String], minOverallOpt: Option[Double], qOpt: Option[String]): ZIO[Any, String, LeaguePlayersDto]
  def getShortlistForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ShortlistEntryDto]]
  def addToShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit]
  def removeFromShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit]
  def listScoutingReportsForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ScoutingReportDto]]
  def createScoutingReportForUser(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): ZIO[Any, String, ScoutingReportDto]
  /** Konferencja prasowa po meczu: wybór tonu wpływa na morale drużyny. */
  def applyPressConference(matchId: MatchId, teamId: TeamId, userId: UserId, phase: String, tone: String): ZIO[Any, String, Unit]
  /** Metryki dla endpointu /metrics (monitoring). */
  def getMetrics: ZIO[Any, Nothing, MetricsDto]
}

case class LeagueServiceLive(
  leagueRepo: LeagueRepository,
  teamRepo: TeamRepository,
  userRepo: UserRepository,
  invitationRepo: InvitationRepository,
  playerRepo: PlayerRepository,
  refereeRepo: RefereeRepository,
  matchRepo: MatchRepository,
  matchSquadRepo: MatchSquadRepository,
  matchResultLogRepo: MatchResultLogRepository,
  transferWindowRepo: TransferWindowRepository,
  transferOfferRepo: TransferOfferRepository,
  leagueContextRepo: LeagueContextRepository,
  gamePlanSnapshotRepo: GamePlanSnapshotRepository,
  trainingPlanRepo: TrainingPlanRepository,
  shortlistRepo: ShortlistRepository,
  scoutingReportRepo: ScoutingReportRepository,
  leaguePlayerMatchStatsRepo: LeaguePlayerMatchStatsRepository,
  engine: MatchEngine,
  engineModelsRef: zio.Ref[EngineModels],
  xa: doobie.Transactor[zio.Task]
) extends LeagueService {

  private def connUnit: ConnectionIO[Unit] = sql"SELECT 1".query[Int].unique.map(_ => ())
  private def traverseConn[A, B](as: List[A])(f: A => ConnectionIO[B]): ConnectionIO[List[B]] =
    as.foldRight(sql"SELECT 1".query[Int].unique.map(_ => List.empty[B]))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))

  def create(name: String, teamCount: Int, myTeamName: String, timezone: String, creatorUserId: UserId): ZIO[Any, String, (LeagueDto, TeamDto)] =
    for {
      _ <- ZIO.fail("teamCount must be even between 10 and 20").when(teamCount < 10 || teamCount > 20 || teamCount % 2 != 0)
      leagueId <- IdGen.genLeagueId
      teamId <- IdGen.genTeamId
      totalMatchdays = 2 * (teamCount - 1)
      league = League(
        id = leagueId,
        name = name,
        teamCount = teamCount,
        currentMatchday = 0,
        totalMatchdays = totalMatchdays,
        seasonPhase = SeasonPhase.Setup,
        homeAdvantage = 1.05,
        startDate = None,
        createdByUserId = creatorUserId,
        createdAt = Instant.now(),
        timezone = ZoneId.of(timezone)
      )
      team = Team(
        id = teamId,
        leagueId = leagueId,
        name = myTeamName,
        ownerType = TeamOwnerType.Human,
        ownerUserId = Some(creatorUserId),
        ownerBotId = None,
        budget = BigDecimal(1000000),
        defaultGamePlanId = None,
        createdAt = Instant.now(),
        managerName = None
      )
      _ <- leagueRepo.create(league).transact(xa).orDie
      _ <- teamRepo.create(team).transact(xa).orDie
      leagueDto = toLeagueDto(league)
      teamDto = toTeamDto(team)
    } yield (leagueDto, teamDto)

  def getById(leagueId: LeagueId): ZIO[Any, String, LeagueDto] =
    leagueRepo.findById(leagueId).transact(xa).orDie.flatMap {
      case None => ZIO.fail("League not found")
      case Some(l) => ZIO.succeed(toLeagueDto(l))
    }

  /** Fails with "Forbidden" if user has no team in the league. */
  private def ensureUserHasAccessToLeague(userId: UserId, leagueId: LeagueId): ZIO[Any, String, Unit] =
    leagueRepo.listByUser(userId).transact(xa).orDie.flatMap { leagues =>
      if (leagues.exists(_.id == leagueId)) ZIO.unit else ZIO.fail("Forbidden")
    }

  def getLeagueForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, LeagueDto] =
    ensureUserHasAccessToLeague(userId, leagueId) *> getById(leagueId)

  def listLeagues(userId: UserId): ZIO[Any, String, List[LeagueDto]] =
    leagueRepo.listByUser(userId).transact(xa).orDie.map(_.map(toLeagueDto))

  def listTeams(leagueId: LeagueId): ZIO[Any, String, List[TeamDto]] =
    teamRepo.listByLeague(leagueId).transact(xa).orDie.map(_.map(toTeamDto))

  def listTeamsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TeamDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *> listTeams(leagueId)

  def getTable(leagueId: LeagueId): ZIO[Any, String, List[TableRowDto]] =
    for {
      _ <- getById(leagueId)
      matches <- matchRepo.listByLeague(leagueId).transact(xa).orDie
      played = matches.filter(_.status == MatchStatus.Played)
      teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
      teamIds = teams.map(_.id).toSet
      stats = teamIds.map { tid =>
        val homeMatches = played.filter(_.homeTeamId == tid)
        val awayMatches = played.filter(_.awayTeamId == tid)
        val goalsFor = homeMatches.flatMap(m => m.homeGoals).sum + awayMatches.flatMap(m => m.awayGoals).sum
        val goalsAgainst = homeMatches.flatMap(m => m.awayGoals).sum + awayMatches.flatMap(m => m.homeGoals).sum
        val (w, d, l) = (homeMatches ++ awayMatches).foldLeft((0, 0, 0)) { case ((w, d, l), m) =>
          val (hf, af) = (m.homeGoals.getOrElse(0), m.awayGoals.getOrElse(0))
          val isHome = m.homeTeamId == tid
          val (my, opp) = if (isHome) (hf, af) else (af, hf)
          if (my > opp) (w + 1, d, l) else if (my < opp) (w, d, l + 1) else (w, d + 1, l)
        }
        (tid, (w * 3 + d, w + d + l, w, d, l, goalsFor, goalsAgainst))
      }.toMap
      // H2H points for teams in a 2-way tie (same pts, gd, gf)
      h2hPoints = (tid: TeamId, opponent: TeamId) => {
        val h2h = played.filter(m => (m.homeTeamId == tid && m.awayTeamId == opponent) || (m.homeTeamId == opponent && m.awayTeamId == tid))
        h2h.foldLeft(0) { case (acc, m) =>
          val (my, opp) = if (m.homeTeamId == tid) (m.homeGoals.getOrElse(0), m.awayGoals.getOrElse(0)) else (m.awayGoals.getOrElse(0), m.homeGoals.getOrElse(0))
          acc + (if (my > opp) 3 else if (my < opp) 0 else 1)
        }
      }
      withStats = teams.map(t => (t, stats.getOrElse(t.id, (0, 0, 0, 0, 0, 0, 0))))
      grouped = withStats.groupBy { case (_, (pts, _, _, _, _, gf, ga)) => (pts, gf - ga, gf) }
      withH2h = withStats.map { case (t, (pts, played, w, d, l, gf, ga)) =>
        val gd = gf - ga
        val group = grouped.getOrElse((pts, gd, gf), Nil)
        val h2h = if (group.size == 2) {
          val other = group.find { case (ot, _) => ot.id != t.id }.map(_._1.id).getOrElse(t.id)
          h2hPoints(t.id, other)
        } else 0
        (t, (pts, played, w, d, l, gf, ga), h2h)
      }
      sorted = withH2h.sortBy { case (t, (pts, _, _, _, _, gf, ga), h2h) =>
        (-pts, -(gf - ga), -gf, -h2h, t.id.value)
      }
    } yield sorted.zipWithIndex.map { case ((t, (pts, played, w, d, l, gf, ga), _), i) =>
      TableRowDto(t.id.value, t.name, i + 1, pts, played, w, d, l, gf, ga, gf - ga)
    }

  def getTableForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TableRowDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *> getTable(leagueId)

  def createInvitation(leagueId: LeagueId, email: String, invitedByUserId: UserId): ZIO[Any, String, InvitationDto] =
    (for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Only league creator can invite").when(league.createdByUserId != invitedByUserId)
      _ <- ZIO.fail("League must be in Setup to invite").when(league.seasonPhase != SeasonPhase.Setup)
      invitedOpt <- userRepo.findByEmail(email).transact(xa).orDie
      invitedUser <- ZIO.fromOption(invitedOpt).orElseFail("No user with this email")
      count <- teamRepo.countByLeague(leagueId).transact(xa).orDie
      _ <- ZIO.fail("League has no free slots").when(count >= league.teamCount)
      token = java.util.UUID.randomUUID().toString
      expiresAt = Instant.now().plus(7, ChronoUnit.DAYS)
      id <- IdGen.genInvitationId
      inv = Invitation(
        id = id,
        leagueId = leagueId,
        invitedUserId = invitedUser.id,
        invitedByUserId = invitedByUserId,
        token = token,
        status = InvitationStatus.Pending,
        createdAt = Instant.now(),
        expiresAt = expiresAt
      )
      _ <- invitationRepo.create(inv).transact(xa).orDie
    } yield InvitationDto(inv.id.value, inv.leagueId.value, inv.invitedUserId.value, Some(inv.invitedByUserId.value), inv.token, inv.status.toString, inv.expiresAt.toEpochMilli)).tapError(err => ZIO.logWarning(s"Create invitation failed: $err"))

  def listPendingInvitations(userId: UserId): ZIO[Any, String, List[InvitationDto]] =
    invitationRepo.listPendingByInvitedUser(userId).transact(xa).orDie.map {
      _.map(inv => InvitationDto(inv.id.value, inv.leagueId.value, inv.invitedUserId.value, Some(inv.invitedByUserId.value), inv.token, inv.status.toString, inv.expiresAt.toEpochMilli))
    }

  def acceptInvitation(token: String, teamName: String, userId: UserId): ZIO[Any, String, (LeagueDto, TeamDto)] =
    for {
      invOpt <- invitationRepo.findByToken(token).transact(xa).orDie
      inv <- ZIO.fromOption(invOpt).orElseFail("Invalid or expired invitation token")
      _ <- ZIO.fail("This invitation was already used").when(inv.status != InvitationStatus.Pending)
      _ <- ZIO.fail("Invitation has expired").when(inv.expiresAt.isBefore(Instant.now()))
      _ <- ZIO.fail("This invitation was sent to another user").when(inv.invitedUserId != userId)
      leagueOpt <- leagueRepo.findById(inv.leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("League is no longer in Setup").when(league.seasonPhase != SeasonPhase.Setup)
      count <- teamRepo.countByLeague(inv.leagueId).transact(xa).orDie
      _ <- ZIO.fail("League has no free slots").when(count >= league.teamCount)
      teamId <- IdGen.genTeamId
      team = Team(
        id = teamId,
        leagueId = inv.leagueId,
        name = teamName,
        ownerType = TeamOwnerType.Human,
        ownerUserId = Some(userId),
        ownerBotId = None,
        budget = BigDecimal(1000000),
        defaultGamePlanId = None,
        createdAt = Instant.now(),
        managerName = None
      )
      _ <- teamRepo.create(team).transact(xa).orDie
      updatedInv = inv.copy(status = InvitationStatus.Accepted)
      _ <- invitationRepo.update(updatedInv).transact(xa).orDie
      leagueDto = toLeagueDto(league)
      teamDto = toTeamDto(team)
    } yield (leagueDto, teamDto)

  def startSeason(leagueId: LeagueId, userId: UserId, startDateOpt: Option[String]): ZIO[Any, String, LeagueDto] =
    (for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Only league creator can start season").when(league.createdByUserId != userId)
      _ <- ZIO.fail("League must be in Setup").when(league.seasonPhase != SeasonPhase.Setup)
      teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
      _ <- ZIO.fail("All team slots must be filled").when(teams.size != league.teamCount)
      startDate <- startDateOpt match {
        case Some(s) => ZIO.attempt(LocalDate.parse(s)).mapError(_ => "Invalid startDate, use YYYY-MM-DD")
        case None => ZIO.succeed(nextWedOrSat(LocalDate.now(league.timezone)))
      }
      rng = new Random(league.id.value.hashCode)
      _ <- ZIO.foreachDiscard(teams) { team =>
        val squad = PlayerGenerator.generateSquad(team.id, rng)
        ZIO.foreachDiscard(squad)(p => playerRepo.create(p).transact(xa).orDie)
      }
      numReferees = league.teamCount / 2
      refereeIds <- ZIO.foreach(List.fill(numReferees)(()))(_ => IdGen.genRefereeId)
      referees = refereeIds.zipWithIndex.map { case (refId, i) =>
        Referee(refId, leagueId, s"Referee ${i + 1}", 0.3 + rng.nextDouble() * 0.5)
      }
      _ <- ZIO.foreachDiscard(referees)(r => refereeRepo.create(r).transact(xa).orDie)
      teamIds = teams.map(_.id)
      matches = FixtureGenerator.generate(leagueId, teamIds, referees, startDate, league.timezone, rng)
      _ <- ZIO.foreachDiscard(matches)(m => matchRepo.create(m).transact(xa).orDie)
      transferWindowIds <- ZIO.foreach((2 to (league.totalMatchdays - 2) by 2).toList)(_ => IdGen.genTransferWindowId)
      _ <- ZIO.foreachDiscard(transferWindowIds.zip((2 to (league.totalMatchdays - 2) by 2).toList)) { case (twId, k) =>
        val tw = TransferWindow(twId, leagueId, k, k + 2, TransferWindowStatus.Closed)
        transferWindowRepo.create(tw).transact(xa).orDie
      }
      allPlayers <- ZIO.foreach(teams)(t => playerRepo.listByTeam(t.id).transact(xa).orDie).map(_.flatten)
      positionStats = LeagueContextComputer.computePositionStats(allPlayers)
      leagueContextId <- IdGen.genLeagueContextId
      ctx = LeagueContext(leagueContextId, leagueId, positionStats, Instant.now())
      _ <- leagueContextRepo.create(ctx).transact(xa).orDie
      updatedLeague = league.copy(startDate = Some(startDate), seasonPhase = SeasonPhase.InProgress)
      _ <- leagueRepo.update(updatedLeague).transact(xa).orDie
      _ <- ZIO.logInfo(s"Season started: league=${league.name} startDate=$startDate ${teams.size} teams")
    } yield toLeagueDto(updatedLeague)).tapError(err => ZIO.logWarning(s"Start season failed: $err"))

  def getFixtures(leagueId: LeagueId, limitOpt: Option[Int] = None, offsetOpt: Option[Int] = None): ZIO[Any, String, List[MatchDto]] =
    for {
      _ <- getById(leagueId)
      matches <- matchRepo.listByLeague(leagueId).transact(xa).orDie
      referees <- refereeRepo.listByLeague(leagueId).transact(xa).orDie
      refByName = referees.map(r => r.id -> r.name).toMap
      all = matches.map(m => MatchDto(m.id.value, m.leagueId.value, m.matchday, m.homeTeamId.value, m.awayTeamId.value, m.scheduledAt.toEpochMilli, m.status.toString, m.homeGoals, m.awayGoals, m.refereeId.value, refByName.get(m.refereeId)))
      sliced = (limitOpt, offsetOpt) match {
        case (Some(lim), Some(off)) => all.slice(off, off + math.min(100, math.max(1, lim)))
        case (Some(lim), None)     => all.take(math.min(100, math.max(1, lim)))
        case _                     => all
      }
    } yield sliced

  def getFixturesForUser(leagueId: LeagueId, limitOpt: Option[Int], offsetOpt: Option[Int], userId: UserId): ZIO[Any, String, List[MatchDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *> getFixtures(leagueId, limitOpt, offsetOpt)

  def addBots(leagueId: LeagueId, userId: UserId, count: Int): ZIO[Any, String, Unit] =
    (for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Only league creator can add bots").when(league.createdByUserId != userId)
      _ <- ZIO.fail("League must be in Setup").when(league.seasonPhase != SeasonPhase.Setup)
      current <- teamRepo.countByLeague(leagueId).transact(xa).orDie
      space = league.teamCount - current
      _ <- ZIO.fail(s"Only $space free slot(s)").when(count <= 0 || count > space)
      botTeamNamePresets = Vector(
        "FC Bot United", "Bot City", "Bot Athletic", "Bot Wanderers", "Bot Rovers",
        "Bot FC", "United Bots", "Bot Dynamo", "Bot Rangers", "Bot Albion",
        "Bot Hotspur", "Bot Villa", "Bot Hammers", "Bot Eagles", "Bot Saints"
      )
      botManagerNamePresets = Vector(
        "Trener Smith", "Trener Kowalski", "Trener Nowak", "Trener Wiśniewski", "Trener Wójcik",
        "Trener Kamiński", "Trener Lewandowski", "Trener Zieliński", "Trener Szymański", "Trener Woźniak",
        "Trener Dąbrowski", "Trener Kozłowski", "Trener Jankowski", "Trener Mazur", "Trener Krawczyk"
      )
      _ <- ZIO.foreachDiscard(0 until count) { i =>
        for {
          teamId <- IdGen.genTeamId
          botId <- IdGen.genBotId
          seed = (leagueId.value.hashCode.toLong << 32) | (i.toLong & 0xFFFFFFFFL)
          rnd = new Random(seed)
          budgetAmount = 500_000 + rnd.nextInt(1_000_001)
          presetName = botTeamNamePresets((current + i) % botTeamNamePresets.size)
          managerName = botManagerNamePresets((current + i) % botManagerNamePresets.size)
          team = Team(
            id = teamId,
            leagueId = leagueId,
            name = presetName,
            ownerType = TeamOwnerType.Bot,
            ownerUserId = None,
            ownerBotId = Some(botId),
            budget = BigDecimal(budgetAmount),
            defaultGamePlanId = None,
            createdAt = Instant.now(),
            managerName = Some(managerName)
          )
          _ <- teamRepo.create(team).transact(xa).orDie
        } yield ()
      }
      _ <- ZIO.logInfo(s"Added $count bot(s) to league=${league.name} (now ${current + count}/${league.teamCount} teams)")
    } yield ()).tapError(err => ZIO.logWarning(s"Add bots failed: $err"))

  def playMatchday(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit] =
    (for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Only league creator can play matchday").when(league.createdByUserId != userId)
      _ <- ZIO.fail("League must be InProgress").when(league.seasonPhase != SeasonPhase.InProgress)
      nextMd = league.currentMatchday + 1
      _ <- ZIO.fail("Season finished").when(nextMd > league.totalMatchdays)
      matches <- matchRepo.listByLeagueAndMatchday(leagueId, nextMd).transact(xa).orDie
      toPlay = matches.filter(_.status != MatchStatus.Played)
      _ <- if (toPlay.isEmpty) ZIO.logDebug(s"Play matchday league=${leagueId.value.take(8)}: no matches to play")
           else for {
             _ <- ZIO.logInfo(s"Playing matchday $nextMd/${league.totalMatchdays} league=${league.name} (${toPlay.size} matches)")
             results <- ZIO.foreach(toPlay)(m => runMatchOnly(m, league))
             logIds <- ZIO.foreach(results)(_ => IdGen.genMatchResultLogId)
             _ <- writeMatchdayInTransaction(league, results.zip(logIds).map { case ((m, r), id) => (m, r, id) }).transact(xa).orDie
             _ <- ZIO.foreachDiscard(results) { case (m, result) =>
               for {
                 squads <- matchSquadRepo.listByMatch(m.id).transact(xa).orDie
                 lineupIds = squads.flatMap(s => s.lineup.map(_.playerId)).toSet
                 playerToTeam = squads.flatMap(s => s.lineup.map(slot => slot.playerId -> s.teamId)).toMap
                 goalsAssists = computePlayerStatsFromEvents(result.events)
                 minutes = computeMinutesByPlayer(lineupIds, result.events)
                 rows = playerToTeam.toList.map { case (pid, tid) =>
                   (pid, tid, goalsAssists.get(pid).map(_.goals).getOrElse(0), goalsAssists.get(pid).map(_.assists).getOrElse(0), minutes.getOrElse(pid, 0))
                 }
                 _ <- leaguePlayerMatchStatsRepo.insertForMatch(m.leagueId, m.id, rows).transact(xa).orDie
               } yield ()
             }
             _ <- ZIO.logInfo(s"Matchday $nextMd completed: ${results.map(r => s"${r._2.homeGoals}-${r._2.awayGoals}").mkString(", ")}")
             _ <- generateBotOffersForOpenWindows(leagueId).tapError(e => ZIO.logWarning(s"Bot offers generation: $e")).ignore
           } yield ()
    } yield ()).tapError(err => ZIO.logWarning(s"Play matchday failed league=${leagueId.value.take(8)}: $err"))

  /** Parsuje gamePlanJson z składu do GamePlanInput. Przy nieprawidłowym JSON zwraca domyślny plan (4-3-3). */
  private def parseGamePlan(json: String): (GamePlanInput, Boolean) = decode[GamePlanParsed](json).toOption match {
    case Some(p) => (GamePlanInput(
      p.formationName.getOrElse("4-3-3"),
      p.throwInConfig.map(t => fmgame.shared.domain.ThrowInConfig(defaultTakerPlayerId = t.defaultTakerPlayerId)),
      p.triggerConfig.map(t => TriggerConfig(t.pressZones.getOrElse(Nil), t.counterTriggerZone)),
      p.customPositions.flatMap(parseCustomPositions),
      p.slotRoles,
      p.teamInstructions.map(m => fmgame.backend.engine.TeamInstructions(m.get("tempo"), m.get("width"), m.get("passingDirectness"), m.get("pressingIntensity"))),
      p.playerInstructions,
      p.setPieces.map(sp => SetPiecesInput(
        cornerTakerPlayerId = sp.cornerTakerPlayerId.map(PlayerId.apply),
        freeKickTakerPlayerId = sp.freeKickTakerPlayerId.map(PlayerId.apply),
        penaltyTakerPlayerId = sp.penaltyTakerPlayerId.map(PlayerId.apply),
        cornerRoutine = sp.cornerRoutine,
        freeKickRoutine = sp.freeKickRoutine
      )),
      p.oppositionInstructions.map(_.map(oi => OppositionInstruction(
        targetPlayerId = PlayerId(oi.targetPlayerId),
        pressIntensity = oi.pressIntensity,
        tackle = oi.tackle,
        mark = oi.mark
      )))
    ), true)
    case _ => (GamePlanInput("4-3-3"), false)
  }

  /** customPositions w JSON: lista 11 elementów [x, y] (0–1). */
  private def parseCustomPositions(raw: List[List[Double]]): Option[List[(Double, Double)]] =
    if (raw.size != 11) None
    else Some(raw.take(11).map {
      case x :: y :: _ => (x.max(0).min(1), y.max(0).min(1))
      case _ => (0.5, 0.5)
    }.padTo(11, (0.5, 0.5)))

  private case class GamePlanParsed(
    formationName: Option[String],
    throwInConfig: Option[ThrowInConfigParsed] = None,
    triggerConfig: Option[TriggerConfigParsed],
    customPositions: Option[List[List[Double]]] = None,
    slotRoles: Option[Map[String, String]] = None,
    teamInstructions: Option[Map[String, String]] = None,
    playerInstructions: Option[Map[String, Map[String, String]]] = None,
    setPieces: Option[SetPiecesParsed] = None,
    oppositionInstructions: Option[List[OppositionInstructionParsed]] = None
  )
  private case class ThrowInConfigParsed(defaultTakerPlayerId: Option[String] = None)
  private case class TriggerConfigParsed(pressZones: Option[List[Int]], counterTriggerZone: Option[Int])
  private case class SetPiecesParsed(
    cornerTakerPlayerId: Option[String] = None,
    freeKickTakerPlayerId: Option[String] = None,
    penaltyTakerPlayerId: Option[String] = None,
    cornerRoutine: Option[String] = None,
    freeKickRoutine: Option[String] = None
  )
  private case class OppositionInstructionParsed(
    targetPlayerId: String,
    pressIntensity: Option[String] = None,
    tackle: Option[String] = None,
    mark: Option[String] = None
  )

  /** Symuluje mecz bez zapisu do bazy; zwraca wynik do zapisania w jednej transakcji z resztą kolejki. */
  private def runMatchOnly(m: Match, league: League): ZIO[Any, String, (Match, MatchEngineResult)] =
    for {
      homeSquad <- ensureSquad(m.id, m.homeTeamId, league.currentMatchday + 1)
      awaySquad <- ensureSquad(m.id, m.awayTeamId, league.currentMatchday + 1)
      refOpt <- refereeRepo.findById(m.refereeId).transact(xa).orDie
      ref <- ZIO.fromOption(refOpt).orElseFail(s"Referee not found: ${m.refereeId.value}")
      homeInput <- squadToTeamInput(homeSquad, m.homeTeamId, Some(m.leagueId), Some(m.matchday))
      awayInput <- squadToTeamInput(awaySquad, m.awayTeamId, Some(m.leagueId), Some(m.matchday))
      ctxOpt <- leagueContextRepo.findByLeagueId(m.leagueId).transact(xa).orDie
      leagueCtx = ctxOpt.fold(LeagueContextInput(Map.empty)) { ctx =>
        LeagueContextInput(ctx.positionStats.view.mapValues(_.view.mapValues { case (mean, std) => PositionAttrStats(mean, std) }.toMap).toMap)
      }
      (homePlan, homePlanOk) = parseGamePlan(homeSquad.gamePlanJson)
      (awayPlan, awayPlanOk) = parseGamePlan(awaySquad.gamePlanJson)
      _ <- ZIO.logWarning("Invalid home gamePlan JSON, using default 4-3-3").when(!homePlanOk)
      _ <- ZIO.logWarning("Invalid away gamePlan JSON, using default 4-3-3").when(!awayPlanOk)
      models <- engineModelsRef.get
      input = MatchEngineInput(
        homeTeam = homeInput,
        awayTeam = awayInput,
        homePlan = homePlan,
        awayPlan = awayPlan,
        homeAdvantage = league.homeAdvantage,
        referee = RefereeInput(ref.strictness),
        leagueContext = leagueCtx,
        randomSeed = Some(m.id.value.hashCode.toLong),
        xgModelOverride = Some(models.xg),
        vaepModelOverride = Some(models.vaep)
      )
      result <- engine.simulate(input).tap(r => ZIO.logDebug(s"Match simulated ${m.id.value.take(8)}: ${r.homeGoals}-${r.awayGoals} (${r.events.size} events)")).mapError {
        case InvalidLineup(msg) => msg
        case EngineFault(t) => t.getMessage
      }
    } yield (m, result)

  /** Jedna transakcja JDBC: zapis logów, aktualizacja meczów, post-match, regeneracja, okna transferowe, currentMatchday. */
  private def writeMatchdayInTransaction(league: League, resultsWithLogIds: List[(Match, MatchEngineResult, MatchResultLogId)]): ConnectionIO[Unit] = {
    val nextMd = league.currentMatchday + 1
    for {
      _ <- traverseConn(resultsWithLogIds) { case (m, result, logId) =>
        val baseSummary = MatchSummaryAggregator.fromEvents(result.events, m.homeTeamId, m.awayTeamId, result.homeGoals, result.awayGoals)
        val summary = result.analytics.fold(baseSummary)(a => baseSummary.copy(
          vaepTotal = Some(a.vaepTotal),
          wpaFinal = Some(a.wpaFinal),
          fieldTilt = a.fieldTilt,
          ppda = a.ppda,
          ballTortuosity = a.ballTortuosity,
          metabolicLoad = Some(a.metabolicLoad),
          xtByZone = Some((1 to 12).map(z => a.xtValueByZone.getOrElse(z, 0.0)).toList),
          homeShareByZone = Some((1 to 12).map(z => a.homeShareByZone.getOrElse(z, 0.5)).toList),
          vaepBreakdownByPlayer = Some(a.vaepByPlayerByEventType.map { case (pid, m) => pid.value -> m }),
          pressingByPlayer = Some(a.defensiveActionsByPlayer.map { case (pid, n) => pid.value -> n }),
          estimatedDistanceByPlayer = Some(a.estimatedDistanceByPlayer.map { case (pid, d) => pid.value -> d }),
          influenceByPlayer = Some(a.playerActivityByZone.map { case (pid, m) => pid.value -> m.map { case (z, c) => z.toString -> c } }),
          avgDefendersInConeByZone = Some((1 to 12).map(z => a.shotContextByZone.get(z).map(_._1).getOrElse(0.0)).toList),
          avgGkDistanceByZone = Some((1 to 12).map(z => a.shotContextByZone.get(z).map(_._2).getOrElse(0.0)).toList),
          setPieceZoneActivity = Some(a.setPieceZoneActivity.view.mapValues(m => (1 to 12).map(z => m.getOrElse(z, 0)).toList).toMap),
          pressingInOppHalfByPlayer = Some(a.pressingInOppHalfByPlayer.map { case (pid, n) => pid.value -> n }),
          playerTortuosityByPlayer = Some(a.playerTortuosityByPlayer.map { case (pid, d) => pid.value -> d }),
          metabolicLoadByPlayer = Some(a.metabolicLoadByPlayer.map { case (pid, d) => pid.value -> d }),
          iwpByPlayer = Some(a.iwpByPlayer.map { case (pid, d) => pid.value -> d }),
          setPiecePatternW = Some(a.setPiecePatternW),
          setPiecePatternH = Some(a.setPiecePatternH.map(hMap => hMap.map { case (k, v) => k.toString -> v }.toMap)),
          setPieceRoutineCluster = Some(a.setPieceRoutineCluster),
          poissonPrognosis = a.poissonPrognosis,
          voronoiCentroidByZone = Some((1 to 12).map(z => a.voronoiCentroidByZone.getOrElse(z, 0.5)).toList),
          passValueByPlayer = Some(a.passValueByPlayer.map { case (pid, d) => pid.value -> d }),
          passValueTotal = Some(a.passValueTotal),
          passValueUnderPressureTotal = Some(a.passValueUnderPressureTotal),
          passValueUnderPressureByPlayer = Some(a.passValueUnderPressureByPlayer.map { case (pid, d) => pid.value -> d }),
          influenceScoreByPlayer = Some(a.influenceScoreByPlayer.map { case (pid, d) => pid.value -> d })
        ))
        val now = Instant.now()
        val log = MatchResultLog(logId, m.id, result.events, Some(summary), now)
        val updatedMatch = m.copy(status = MatchStatus.Played, homeGoals = Some(result.homeGoals), awayGoals = Some(result.awayGoals), resultLogId = Some(logId))
        for {
          _ <- matchResultLogRepo.create(log)
          _ <- matchRepo.update(updatedMatch)
        } yield ()
      }
      _ <- traverseConn(resultsWithLogIds) { case (m, result, _) => updateEloAfterMatchConnectionIO(m.homeTeamId, m.awayTeamId, result.homeGoals, result.awayGoals) }
      _ <- traverseConn(resultsWithLogIds) { case (m, _, _) => applyPostMatchUpdatesConnectionIO(m.id, nextMd) }
      _ <- applyRegenerationAndClearInjuriesConnectionIO(league.id, nextMd)
      _ <- updateTransferWindowStatusesConnectionIO(league.id, nextMd)
      newPhase = if (nextMd >= league.totalMatchdays) SeasonPhase.Finished else league.seasonPhase
      _ <- leagueRepo.update(league.copy(currentMatchday = nextMd, seasonPhase = newPhase))
    } yield ()
  }

  /** Aktualizuje rating Elo obu drużyn po meczu (K=32). */
  private def updateEloAfterMatchConnectionIO(homeTeamId: TeamId, awayTeamId: TeamId, homeGoals: Int, awayGoals: Int): ConnectionIO[Unit] = {
    val K = 32.0
    for {
      homeOpt <- teamRepo.findById(homeTeamId)
      awayOpt <- teamRepo.findById(awayTeamId)
      _ <- (homeOpt, awayOpt) match {
        case (Some(home), Some(away)) =>
          val eloH = home.eloRating
          val eloA = away.eloRating
          val expectedHome = 1.0 / (1.0 + math.pow(10.0, (eloA - eloH) / 400.0))
          val (actualHome, actualAway) = if (homeGoals > awayGoals) (1.0, 0.0) else if (homeGoals < awayGoals) (0.0, 1.0) else (0.5, 0.5)
          val newEloH = eloH + K * (actualHome - expectedHome)
          val newEloA = eloA + K * (actualAway - (1.0 - expectedHome))
          for {
            _ <- teamRepo.updateElo(homeTeamId, newEloH)
            _ <- teamRepo.updateElo(awayTeamId, newEloA)
          } yield ()
        case _ => connUnit
      }
    } yield ()
  }

  private def applyPostMatchUpdatesConnectionIO(matchId: MatchId, matchday: Int): ConnectionIO[Unit] = {
    val me = summon[MonadError[ConnectionIO, Throwable]]
    for {
      mOpt <- matchRepo.findById(matchId)
      m <- mOpt.fold(me.raiseError[Match](new Exception("Match not found")))(a => me.pure(a))
      logOpt <- matchResultLogRepo.findByMatchId(matchId)
      log <- logOpt.fold(me.raiseError[MatchResultLog](new Exception("Match log not found")))(a => me.pure(a))
      homeSquadOpt <- matchSquadRepo.findByMatchAndTeam(matchId, m.homeTeamId)
      awaySquadOpt <- matchSquadRepo.findByMatchAndTeam(matchId, m.awayTeamId)
      homeSquad <- homeSquadOpt.fold(me.raiseError[MatchSquad](new Exception("Home squad not found")))(a => me.pure(a))
      awaySquad <- awaySquadOpt.fold(me.raiseError[MatchSquad](new Exception("Away squad not found")))(a => me.pure(a))
      (hG, aG) = (m.homeGoals.getOrElse(0), m.awayGoals.getOrElse(0))
      homeDelta = if (hG > aG) 0.05 else if (hG < aG) -0.05 else 0.0
      awayDelta = if (aG > hG) 0.05 else if (aG < hG) -0.05 else 0.0
      homePlayers <- playerRepo.listByTeam(m.homeTeamId)
      awayPlayers <- playerRepo.listByTeam(m.awayTeamId)
      homeLineupIds = homeSquad.lineup.map(_.playerId).toSet
      awayLineupIds = awaySquad.lineup.map(_.playerId).toSet
      _ <- traverseConn(homePlayers) { p =>
        val newFreshness = if (homeLineupIds.contains(p.id)) (p.freshness - 0.25).max(0.0) else p.freshness
        val newMorale = (p.morale + homeDelta).max(0.0).min(1.0)
        playerRepo.updateFreshnessMorale(p.id, newFreshness, newMorale)
      }
      _ <- traverseConn(awayPlayers) { p =>
        val newFreshness = if (awayLineupIds.contains(p.id)) (p.freshness - 0.25).max(0.0) else p.freshness
        val newMorale = (p.morale + awayDelta).max(0.0).min(1.0)
        playerRepo.updateFreshnessMorale(p.id, newFreshness, newMorale)
      }
      _ <- traverseConn(log.events.filter(_.eventType == "Injury").toList) { e =>
        e.actorPlayerId.fold(connUnit) { pid =>
          val returnMd = e.metadata.get("returnMatchday").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(matchday + 2)
          val severity = e.metadata.getOrElse("severity", "Medium")
          playerRepo.updateInjury(pid, Some(InjuryStatus(matchday, returnMd, severity)))
        }
      }
    } yield ()
  }

  private def applyRegenerationAndClearInjuriesConnectionIO(leagueId: LeagueId, currentMatchday: Int): ConnectionIO[Unit] =
    for {
      teams <- teamRepo.listByLeague(leagueId)
      _ <- traverseConn(teams) { team =>
        for {
          players <- playerRepo.listByTeam(team.id)
          planOpt <- trainingPlanRepo.findByTeamId(team.id)
          week = planOpt.map(_.week).getOrElse(List.fill(7)("Balanced")).padTo(7, "Balanced").take(7)
          regenBonus = {
            val rest = week.count(s => s.equalsIgnoreCase("Rest") || s.equalsIgnoreCase("Recovery"))
            val hard = week.count(s => s.equalsIgnoreCase("Physical"))
            (rest * 0.015 - hard * 0.01).max(-0.03).min(0.05)
          }
          intensity = week.count(s => !s.equalsIgnoreCase("Rest") && !s.equalsIgnoreCase("Recovery"))
          seed = (team.id.value.hashCode.toLong << 32) ^ currentMatchday.toLong
          rng = new scala.util.Random(seed)
          _ <- traverseConn(players) { p =>
            val baseRegen = 0.15
            val newFreshness = (p.freshness + baseRegen + regenBonus).min(1.0).max(0.0)
            val clearInjury = p.injury.exists(_.returnAtMatchday <= currentMatchday)
            for {
              _ <- playerRepo.updateFreshnessMorale(p.id, newFreshness, p.morale)
              _ <- if (clearInjury) playerRepo.updateInjury(p.id, None) else connUnit
              _ <- {
                // MVP: minimalny rozwój atrybutów od treningu (rzadko, małe +1, cap 20)
                val chance = (0.01 + intensity * 0.008).min(0.08)
                if (p.injury.isDefined) connUnit
                else if (rng.nextDouble() > chance) connUnit
                else {
                  val focus = week(rng.nextInt(week.size)).toLowerCase
                  def bump(map: Map[String, Int], keys: List[String]): Map[String, Int] = {
                    val k = keys(rng.nextInt(keys.size))
                    val cur = map.getOrElse(k, 10)
                    map.updated(k, (cur + 1).min(20))
                  }
                  val (newPh, newTe, newMe) =
                    if (focus.contains("attack")) (p.physical, bump(p.technical, List("finishing", "passing", "firstTouch", "technique")), p.mental)
                    else if (focus.contains("defen")) (p.physical, bump(p.technical, List("tackling", "marking")), bump(p.mental, List("positioning", "decisions")))
                    else if (focus.contains("physical")) (bump(p.physical, List("pace", "stamina", "strength")), p.technical, p.mental)
                    else if (focus.contains("mental")) (p.physical, p.technical, bump(p.mental, List("composure", "decisions", "workRate")))
                    else (p.physical, p.technical, p.mental)
                  playerRepo.updateAttributes(p.id, newPh, newTe, newMe)
                }
              }
            } yield ()
          }
        } yield ()
      }
    } yield ()

  private def updateTransferWindowStatusesConnectionIO(leagueId: LeagueId, currentMatchday: Int): ConnectionIO[Unit] =
    for {
      windows <- transferWindowRepo.listByLeague(leagueId)
      _ <- traverseConn(windows) { tw =>
        val newStatus = if (currentMatchday >= tw.closeBeforeMatchday) TransferWindowStatus.Closed
                       else if (currentMatchday > tw.openAfterMatchday) TransferWindowStatus.Open
                       else tw.status
        if (newStatus != tw.status) transferWindowRepo.update(tw.copy(status = newStatus)) else connUnit
      }
    } yield ()

  /** Generuje oferty transferowe od botów (bot kupuje pod braki kadry). BOTY §3.4. */
  private def generateBotOffersForOpenWindows(leagueId: LeagueId): ZIO[Any, String, Unit] =
    for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      windows <- transferWindowRepo.listByLeague(leagueId).transact(xa).orDie
      openWindows = windows.filter(_.status == TransferWindowStatus.Open)
      allOffers <- transferOfferRepo.listByLeague(leagueId).transact(xa).orDie
      teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
      humanTeams = teams.filter(_.ownerType == TeamOwnerType.Human)
      botTeams = teams.filter(_.ownerType == TeamOwnerType.Bot)
      botPlayersMap <- ZIO.foreach(botTeams)(b => playerRepo.listByTeam(b.id).transact(xa).orDie.map(ps => (b.id, ps))).map(_.toMap)
      needGroupsPerBot = botTeams.map(b => b.id -> BotSquadBuilder.squadNeedGroups(botPlayersMap.getOrElse(b.id, Nil), league.currentMatchday)).toMap
      _ <- ZIO.foreachDiscard(openWindows) { w =>
        val windowOffers = allOffers.filter(_.windowId == w.id)
        val botIds = botTeams.map(_.id).toSet
        val alreadyHasBotOffers = windowOffers.exists(o => botIds.contains(o.fromTeamId))
        if (alreadyHasBotOffers) ZIO.unit
        else
          ZIO.foreachDiscard(humanTeams) { humanTeam =>
            for {
              players <- playerRepo.listByTeam(humanTeam.id).transact(xa).orDie
              _ <- if (players.size < 16) ZIO.unit
                   else for {
                     botCounts <- ZIO.foreach(botTeams)(b => playerRepo.countByTeam(b.id).transact(xa).orDie.map(c => (b.id, c))).map(_.toMap)
                     rnd = new Random(leagueId.value.hashCode ^ w.id.value.hashCode ^ humanTeam.id.value.hashCode)
                     priorityGroups = needGroupsPerBot.values.flatten.toList.distinct
                     byNeed = if (priorityGroups.isEmpty) players
                              else players.filter(p => BotSquadBuilder.positionGroups(p).exists(priorityGroups.contains))
                     sortedByOverall = (if (byNeed.nonEmpty) byNeed else players).sortBy(p => -PlayerOverall.overall(p))
                     chosen = sortedByOverall.take(2)
                     _ <- ZIO.foreachDiscard(chosen) { player =>
                       for {
                         existing <- transferOfferRepo.listPendingByPlayerAndWindow(player.id, w.id).transact(xa).orDie
                         _ <- if (existing.nonEmpty) ZIO.unit
                              else {
                                val price = BigDecimal(estimatedPlayerPrice(player))
                                val minAmount = price * BigDecimal(0.85)
                                val playerGroups = BotSquadBuilder.positionGroups(player)
                                val availableBots = botTeams.filter(b =>
                                  b.budget >= minAmount && botCounts.getOrElse(b.id, 0) < 20 &&
                                    (playerGroups.isEmpty || playerGroups.exists(g => needGroupsPerBot.getOrElse(b.id, Nil).contains(g)))
                                )
                                val botsToUse = if (availableBots.nonEmpty) availableBots else botTeams.filter(b => b.budget >= minAmount && botCounts.getOrElse(b.id, 0) < 20)
                                if (botsToUse.isEmpty) ZIO.unit
                                else {
                                  val bot = botsToUse(rnd.nextInt(botsToUse.size))
                                  val (minM, maxM) = BotSquadBuilder.buyPriceMultiplierRange(BotSquadBuilder.botDifficulty(bot.id))
                                  val amount = price * BigDecimal(minM + rnd.nextDouble() * (maxM - minM))
                                  for {
                                    offerId <- IdGen.genTransferOfferId
                                    now = Instant.now()
                                    offer = TransferOffer(offerId, w.id, bot.id, humanTeam.id, player.id, amount, TransferOfferStatus.Pending, now, None)
                                    _ <- transferOfferRepo.create(offer).transact(xa).orDie
                                    _ <- ZIO.logInfo(s"Bot offer: ${bot.name} -> ${humanTeam.name} for ${player.firstName} ${player.lastName} $amount")
                                  } yield ()
                                }
                              }
                       } yield ()
                     }
                   } yield ()
            } yield ()
          }
      }
    } yield ()

  /** Siła rywala 0–1 (średni overall kadry przeciwnika z meczu). BOTY §3.2: formacja i teamInstructions od rywala. */
  private def getOpponentStrength(matchId: MatchId, teamId: TeamId): ZIO[Any, Nothing, Option[Double]] =
    matchRepo.findById(matchId).transact(xa).orDie.flatMap {
      case None => ZIO.succeed(None)
      case Some(m) =>
        val opponentId = if (m.homeTeamId == teamId) m.awayTeamId else m.homeTeamId
        playerRepo.listByTeam(opponentId).transact(xa).orDie.map { oppPlayers =>
          if (oppPlayers.isEmpty) None
          else {
            val avg = oppPlayers.map(PlayerOverall.overall).sum.toDouble / oppPlayers.size
            val strength = ((avg - 1.0) / 19.0).max(0.0).min(1.0)
            Some(strength)
          }
        }
    }

  private def ensureSquad(matchId: MatchId, teamId: TeamId, currentMatchday: Int): ZIO[Any, String, MatchSquad] =
    matchSquadRepo.findByMatchAndTeam(matchId, teamId).transact(xa).orDie.flatMap {
      case Some(s) => ZIO.succeed(s)
      case None =>
        for {
          teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
          players <- playerRepo.listByTeam(teamId).transact(xa).orDie
          trio <- (teamOpt match {
            case Some(team) if team.ownerType == TeamOwnerType.Bot =>
              for {
                opponentStrength <- getOpponentStrength(matchId, teamId)
                difficulty = BotSquadBuilder.botDifficulty(team.id)
                style = BotSquadBuilder.botStyle(team.id)
                formation = BotSquadBuilder.pickFormation(team.id, opponentStrength, style)
                line <- ZIO.fromOption(BotSquadBuilder.buildBotLineup(players, currentMatchday, formation, difficulty))
                  .orElseFail(s"Team ${teamId.value} has no valid 11 (need 1 GK + 10 outfield)")
                gpJson = BotSquadBuilder.defaultGamePlanJson(formation, BotSquadBuilder.useTriggers(team.id), opponentStrength, style)
              } yield (line, gpJson, MatchSquadSource.Bot)
            case _ =>
              ZIO.fromOption(DefaultSquadBuilder.buildDefaultLineup(players, currentMatchday))
                .orElseFail(s"Team ${teamId.value} has no valid 11 (need 1 GK + 10 outfield)")
                .map { line => (line, DefaultSquadBuilder.defaultGamePlanJson, MatchSquadSource.Default) }
          })
          matchSquadId <- IdGen.genMatchSquadId
          squad = MatchSquad(matchSquadId, matchId, teamId, trio._1, trio._2, Instant.now(), trio._3)
          _ <- matchSquadRepo.create(squad).transact(xa).orDie
        } yield squad
    }

  /** Minuty rozegrane w ostatnich 3 meczach (ACWR). */
  private def getRecentMinutesPlayed(leagueId: LeagueId, teamId: TeamId, beforeMatchday: Int): ZIO[Any, Nothing, Map[PlayerId, Int]] =
    for {
      matches <- matchRepo.listByLeague(leagueId).transact(xa).orDie
      playedForTeam = matches.filter(m => m.status == MatchStatus.Played && m.matchday < beforeMatchday && (m.homeTeamId == teamId || m.awayTeamId == teamId)).sortBy(-_.matchday).take(3)
      pairs <- ZIO.foreach(playedForTeam)(m => matchSquadRepo.findByMatchAndTeam(m.id, teamId).transact(xa).orDie.map(opt => (m, opt)))
      map = scala.collection.mutable.Map.empty[PlayerId, Int]
      _ = pairs.foreach { case (_, squadOpt) =>
        squadOpt.foreach(s => s.lineup.foreach(slot => map(slot.playerId) = map.getOrElse(slot.playerId, 0) + 90))
      }
    } yield map.toMap

  private def squadToTeamInput(squad: MatchSquad, teamId: TeamId, leagueId: Option[LeagueId], beforeMatchday: Option[Int]): ZIO[Any, String, MatchTeamInput] =
    for {
      recentMinutes <- (leagueId, beforeMatchday) match {
        case (Some(lid), Some(md)) => getRecentMinutesPlayed(lid, teamId, md)
        case _ => ZIO.succeed(Map.empty[PlayerId, Int])
      }
      all <- playerRepo.listByTeam(teamId).transact(xa).orDie
      idToPlayer = all.map(p => p.id -> p).toMap
      ordered = squad.lineup.flatMap(s => idToPlayer.get(s.playerId).map(p => (p, s.positionSlot)))
      _ <- ZIO.fail(s"Missing players for squad").when(ordered.size != 11)
      players = ordered.map { case (p, slot) => PlayerMatchInput(p, p.freshness, p.morale, recentMinutes.get(p.id)) }
      lineupMap = ordered.map { case (p, slot) => p.id -> slot }.toMap
    } yield MatchTeamInput(teamId, players, lineupMap)

  def getMatch(matchId: MatchId): ZIO[Any, String, MatchDto] =
    matchRepo.findById(matchId).transact(xa).orDie.flatMap {
      case None => ZIO.fail("Match not found")
      case Some(m) =>
        refereeRepo.findById(m.refereeId).transact(xa).orDie.map { refOpt =>
          MatchDto(m.id.value, m.leagueId.value, m.matchday, m.homeTeamId.value, m.awayTeamId.value, m.scheduledAt.toEpochMilli, m.status.toString, m.homeGoals, m.awayGoals, m.refereeId.value, refereeName = refOpt.map(_.name))
        }
    }

  def getMatchForUser(matchId: MatchId, userId: UserId): ZIO[Any, String, MatchDto] =
    getMatch(matchId).flatMap(m => ensureUserHasAccessToLeague(userId, LeagueId(m.leagueId)).as(m))

  def getMatchLog(matchId: MatchId, limitOpt: Option[Int], offsetOpt: Option[Int]): ZIO[Any, String, MatchLogDto] =
    matchResultLogRepo.findByMatchId(matchId).transact(xa).orDie.flatMap {
      case None => ZIO.fail("Match log not found")
      case Some(log) =>
        val allEvents = log.events.map(e => MatchEventDto(e.minute, e.eventType, e.actorPlayerId.map(_.value), e.secondaryPlayerId.map(_.value), e.teamId.map(_.value), e.zone, e.outcome, e.metadata))
        val (events, totalOpt) = (limitOpt, offsetOpt) match {
          case (Some(limit), Some(offset)) =>
            val off = math.max(0, offset)
            val lim = math.max(1, math.min(500, limit))
            (allEvents.slice(off, off + lim), Some(allEvents.size))
          case (Some(limit), None) =>
            val lim = math.max(1, math.min(500, limit))
            (allEvents.take(lim), Some(allEvents.size))
          case _ => (allEvents, None)
        }
        val summaryOpt = log.summary.map(toMatchSummaryDto)
        ZIO.succeed(MatchLogDto(events, summaryOpt, totalOpt))
    }

  /** Oblicza possession per 15 min oraz pressing per strefa z pełnej listy zdarzeń. */
  private def computePossessionAndPressByZone(events: List[fmgame.backend.domain.MatchEventRecord], homeTeamId: String, awayTeamId: String): (List[Double], List[Int], List[Int]) = {
    val segments = List((0, 15), (15, 30), (30, 45), (45, 60), (60, 75), (75, 91))
    val possessionBySegment = segments.map { case (from, to) =>
      val inSegment = events.filter(e => e.minute >= from && e.minute < to).flatMap(e => e.teamId.map(_.value))
      val home = inSegment.count(_ == homeTeamId)
      val away = inSegment.count(_ == awayTeamId)
      val total = home + away
      if (total == 0) 0.5 else home.toDouble / total
    }
    val defensiveTypes = Set("Tackle", "PassIntercepted")
    val pressHome = (1 to 12).map { z =>
      events.count(e => e.zone.contains(z) && defensiveTypes.contains(e.eventType) && e.teamId.exists(_.value == homeTeamId))
    }.toList
    val pressAway = (1 to 12).map { z =>
      events.count(e => e.zone.contains(z) && defensiveTypes.contains(e.eventType) && e.teamId.exists(_.value == awayTeamId))
    }.toList
    (possessionBySegment, pressHome, pressAway)
  }

  def getMatchLogForUser(matchId: MatchId, limitOpt: Option[Int], offsetOpt: Option[Int], userId: UserId): ZIO[Any, String, MatchLogDto] =
    getMatch(matchId).flatMap { m =>
      ensureUserHasAccessToLeague(userId, LeagueId(m.leagueId)) *>
        getMatchLog(matchId, limitOpt, offsetOpt).flatMap { dto =>
          if (m.status != "Played" || dto.summary.isEmpty) ZIO.succeed(dto)
          else
            for {
              logOpt  <- matchResultLogRepo.findByMatchId(matchId).transact(xa).orDie
              homeOpt <- teamRepo.findById(TeamId(m.homeTeamId)).transact(xa).orDie
              awayOpt <- teamRepo.findById(TeamId(m.awayTeamId)).transact(xa).orDie
              momName <- logOpt.flatMap { log =>
                val scorers = log.events.filter(_.eventType == "Goal").flatMap(_.actorPlayerId)
                if (scorers.isEmpty) None else Some(scorers.groupBy(identity).maxBy(_._2.size)._1)
              } match {
                case None => ZIO.succeed(None: Option[String])
                case Some(pid) =>
                  playerRepo.findById(pid).transact(xa).orDie.map(_.map(p => s"${p.firstName} ${p.lastName}"))
              }
              homeName = homeOpt.map(_.name).getOrElse(m.homeTeamId)
              awayName = awayOpt.map(_.name).getOrElse(m.awayTeamId)
              report   = buildMatchReportText(m, homeName, awayName, dto.summary.get, momName)
              (possSeg, pressH, pressA) = logOpt.map(log => computePossessionAndPressByZone(log.events, m.homeTeamId, m.awayTeamId)).getOrElse((Nil, Nil, Nil))
              enriched = dto.copy(
                matchReport = Some(report),
                possessionBySegment = if (possSeg.size == 6) Some(possSeg) else None,
                pressByZoneHome = if (pressH.size == 12) Some(pressH) else None,
                pressByZoneAway = if (pressA.size == 12) Some(pressA) else None
              )
            } yield enriched
        }
    }

  def getMatchSquads(matchId: MatchId): ZIO[Any, String, List[MatchSquadDto]] =
    matchSquadRepo.listByMatch(matchId).transact(xa).orDie.map { squads =>
      squads.map(s => MatchSquadDto(s.id.value, s.matchId.value, s.teamId.value, s.lineup.map(l => LineupSlotDto(l.playerId.value, l.positionSlot)), s.source.toString))
    }

  def getMatchSquadsForUser(matchId: MatchId, userId: UserId): ZIO[Any, String, List[MatchSquadDto]] =
    getMatch(matchId).flatMap(m => ensureUserHasAccessToLeague(userId, LeagueId(m.leagueId)) *> getMatchSquads(matchId))

  private val formationTips: Map[String, String] = Map(
    "4-4-2" -> "Rywal gra 4-4-2; rozważ pressing w strefach 7–8 i szybkie przejście w kontrę.",
    "4-3-3" -> "Rywal gra 4-3-3; utrzymaj dyscyplinę w środku pola (strefy 5–8).",
    "3-5-2" -> "Rywal gra 3-5-2; wykorzystaj skrzydła (strefy 2, 5, 8, 11) i pressing w środku.",
    "4-2-3-1" -> "Rywal gra 4-2-3-1; odetnij podania do rozgrywającego (strefy 6–7).",
    "Własna" -> "Rywal z własnym ustawieniem; dostosuj pressing do słabych stref (środek lub skrzydła)."
  )
  private val defaultTip = "Przygotuj się taktycznie; dostosuj pressing i kontrataki do stylu rywala."

  def getAssistantTipForUser(matchId: MatchId, teamId: TeamId, userId: UserId): ZIO[Any, String, AssistantTipDto] =
    for {
      m <- getMatch(matchId)
      _ <- ensureUserHasAccessToLeague(userId, LeagueId(m.leagueId))
      squads <- matchSquadRepo.listByMatch(matchId).transact(xa).orDie
      opponentSquad = squads.find(_.teamId != teamId)
      formation = opponentSquad.flatMap { s => val (plan, _) = parseGamePlan(s.gamePlanJson); Some(plan.formationName) }.getOrElse("4-3-3")
      tip = formationTips.get(formation).getOrElse(defaultTip)
    } yield AssistantTipDto(tip)

  def getTeam(teamId: TeamId): ZIO[Any, String, TeamDto] =
    teamRepo.findById(teamId).transact(xa).orDie.flatMap {
      case None => ZIO.fail("Team not found")
      case Some(t) => ZIO.succeed(toTeamDto(t))
    }

  def getTeamForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, TeamDto] =
    getTeam(teamId).flatMap(dto => ensureUserHasAccessToLeague(userId, LeagueId(dto.leagueId)).as(dto))

  def getTeamPlayers(teamId: TeamId): ZIO[Any, String, List[PlayerDto]] =
    for {
      _ <- getTeam(teamId)
      players <- playerRepo.listByTeam(teamId).transact(xa).orDie
    } yield players.map(p => PlayerDto(
      p.id.value,
      p.teamId.value,
      p.firstName,
      p.lastName,
      p.preferredPositions.toList,
      p.injury.map(i => s"return matchday ${i.returnAtMatchday}"),
      p.freshness,
      p.morale,
      physical = p.physical,
      technical = p.technical,
      mental = p.mental,
      traits = p.traits
    ))

  def getTeamPlayersForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[PlayerDto]] =
    getTeamForUser(teamId, userId) *> getTeamPlayers(teamId)

  def getTeamGamePlans(teamId: TeamId): ZIO[Any, String, List[GamePlanSnapshotDto]] =
    for {
      _ <- getTeam(teamId)
      list <- gamePlanSnapshotRepo.listByTeam(teamId).transact(xa).orDie
    } yield list.map(s => GamePlanSnapshotDto(s.id.value, s.teamId.value, s.name, s.createdAt.toEpochMilli))

  def getTeamGamePlansForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[GamePlanSnapshotDto]] =
    getTeamForUser(teamId, userId) *> getTeamGamePlans(teamId)

  def getGamePlanSnapshot(teamId: TeamId, snapshotId: GamePlanSnapshotId): ZIO[Any, String, GamePlanSnapshotDetailDto] =
    for {
      _ <- getTeam(teamId)
      opt <- gamePlanSnapshotRepo.findById(snapshotId).transact(xa).orDie
      snap <- ZIO.fromOption(opt).orElseFail("Game plan not found")
      _ <- ZIO.fail("Game plan not for this team").when(snap.teamId != teamId)
    } yield GamePlanSnapshotDetailDto(snap.id.value, snap.teamId.value, snap.name, snap.gamePlanJson, snap.createdAt.toEpochMilli)

  def getGamePlanSnapshotForUser(teamId: TeamId, snapshotId: GamePlanSnapshotId, userId: UserId): ZIO[Any, String, GamePlanSnapshotDetailDto] =
    getTeamForUser(teamId, userId) *> getGamePlanSnapshot(teamId, snapshotId)

  def getTransferWindows(leagueId: LeagueId): ZIO[Any, String, List[TransferWindowDto]] =
    for {
      _ <- getById(leagueId)
      list <- transferWindowRepo.listByLeague(leagueId).transact(xa).orDie
    } yield list.map(tw => TransferWindowDto(tw.id.value, tw.leagueId.value, tw.openAfterMatchday, tw.closeBeforeMatchday, tw.status.toString))

  def getTransferWindowsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TransferWindowDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *> getTransferWindows(leagueId)

  def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId]): ZIO[Any, String, List[TransferOfferDto]] =
    for {
      _ <- getById(leagueId)
      list <- transferOfferRepo.listByLeague(leagueId).transact(xa).orDie
      filtered = teamIdOpt match {
        case Some(tid) => list.filter(o => o.fromTeamId == tid || o.toTeamId == tid)
        case None => list
      }
      teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
      teamNames = teams.map(t => t.id -> t.name).toMap
      playerIds = filtered.map(_.playerId).distinct
      playerOpts <- ZIO.foreach(playerIds)(id => playerRepo.findById(id).transact(xa).orDie)
      playerNames = playerIds.zip(playerOpts).flatMap { case (id, opt) =>
        opt.map(p => id -> s"${p.firstName} ${p.lastName}").toList
      }.toMap
    } yield filtered.map(o => TransferOfferDto(
      o.id.value, o.windowId.value, o.fromTeamId.value, o.toTeamId.value, o.playerId.value,
      o.amount.toDouble, o.status.toString, o.createdAt.toEpochMilli, o.respondedAt.map(_.toEpochMilli),
      fromTeamName = teamNames.get(o.fromTeamId),
      toTeamName = teamNames.get(o.toTeamId),
      playerName = playerNames.get(o.playerId)
    ))

  def getTransferOffersForUser(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): ZIO[Any, String, List[TransferOfferDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *> getTransferOffers(leagueId, teamIdOpt)

  def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): ZIO[Any, String, TransferOfferDto] =
    for {
      _ <- getById(leagueId)
      teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
      fromTeam <- ZIO.fromOption(teams.find(t => t.ownerUserId.contains(userId))).orElseFail("No team in this league for current user")
      windowId = TransferWindowId(req.windowId)
      windows <- transferWindowRepo.listByLeague(leagueId).transact(xa).orDie
      _ <- ZIO.fail("Transfer window not found or not in this league").when(!windows.exists(_.id == windowId))
      toTeamId = TeamId(req.toTeamId)
      _ <- ZIO.fail("Cannot make offer to own team").when(toTeamId == fromTeam.id)
      _ <- ZIO.fail("To team must be in same league").when(!teams.exists(_.id == toTeamId))
      playerId = PlayerId(req.playerId)
      playerOpt <- playerRepo.findById(playerId).transact(xa).orDie
      player <- ZIO.fromOption(playerOpt).orElseFail("Player not found")
      _ <- ZIO.fail("Player must belong to the team you are buying from (toTeamId)").when(player.teamId != toTeamId)
      _ <- ZIO.fail("Amount must be positive").when(req.amount <= 0)
      toTeam <- ZIO.fromOption(teams.find(_.id == toTeamId)).orElseFail("To team not found")
      _ <- ZIO.fail("Buyer has insufficient budget").when(fromTeam.budget < BigDecimal(req.amount))
      fromCount <- playerRepo.countByTeam(fromTeam.id).transact(xa).orDie
      toCount <- playerRepo.countByTeam(toTeamId).transact(xa).orDie
      _ <- ZIO.fail("Selling would leave fewer than 16 players").when(toCount <= 16)
      _ <- ZIO.fail("Buyer would exceed 20 players").when(fromCount >= 20)
      existingPending <- transferOfferRepo.listPendingByPlayerAndWindow(playerId, windowId).transact(xa).orDie
      sameFromTeam = existingPending.filter(_.fromTeamId == fromTeam.id)
      now = Instant.now()
      _ <- ZIO.foreachDiscard(sameFromTeam) { old =>
        transferOfferRepo.update(old.copy(status = TransferOfferStatus.Rejected, respondedAt = Some(now))).transact(xa).orDie
      }
      offerId <- IdGen.genTransferOfferId
      offer = TransferOffer(offerId, windowId, fromTeam.id, toTeamId, playerId, BigDecimal(req.amount), TransferOfferStatus.Pending, now, None)
      _ <- transferOfferRepo.create(offer).transact(xa).orDie
      accepted <- if (toTeam.ownerType == TeamOwnerType.Bot) {
        for {
          leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
          currentMd = leagueOpt.fold(0)(_.currentMatchday)
          botPlayers <- playerRepo.listByTeam(toTeamId).transact(xa).orDie
          difficulty = BotSquadBuilder.botDifficulty(toTeam.id)
          isKey = BotSquadBuilder.isPlayerInBestLineup(player.id, botPlayers, currentMd, toTeam.id)
          premium = BotSquadBuilder.keyPlayerPremium(difficulty)
          requiredPrice = if (isKey) estimatedPlayerPrice(player) * premium else estimatedPlayerPrice(player)
          ok = req.amount >= requiredPrice
          _ <- if (ok) {
            for {
              _ <- playerRepo.updateTeamId(offer.playerId, offer.fromTeamId).transact(xa).orDie
              _ <- teamRepo.update(fromTeam.copy(budget = fromTeam.budget - offer.amount)).transact(xa).orDie
              _ <- teamRepo.update(toTeam.copy(budget = toTeam.budget + offer.amount)).transact(xa).orDie
              _ <- transferOfferRepo.update(offer.copy(status = TransferOfferStatus.Accepted, respondedAt = Some(now))).transact(xa).orDie
            } yield ()
          } else ZIO.unit
        } yield ok
      } else ZIO.succeed(false)
    } yield TransferOfferDto(offer.id.value, offer.windowId.value, offer.fromTeamId.value, offer.toTeamId.value, offer.playerId.value, offer.amount.toDouble, if (accepted) TransferOfferStatus.Accepted.toString else offer.status.toString, offer.createdAt.toEpochMilli, if (accepted) Some(now.toEpochMilli) else None, fromTeamName = Some(fromTeam.name), toTeamName = Some(toTeam.name), playerName = Some(s"${player.firstName} ${player.lastName}"))

  def acceptTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit] =
    for {
      opt <- transferOfferRepo.findById(offerId).transact(xa).orDie
      offer <- ZIO.fromOption(opt).orElseFail("Offer not found")
      _ <- ZIO.fail("Offer already responded").when(offer.status != TransferOfferStatus.Pending)
      fromTeamOpt <- teamRepo.findById(offer.fromTeamId).transact(xa).orDie
      fromTeam <- ZIO.fromOption(fromTeamOpt).orElseFail("From team not found")
      toTeamOpt <- teamRepo.findById(offer.toTeamId).transact(xa).orDie
      toTeam <- ZIO.fromOption(toTeamOpt).orElseFail("To team not found")
      _ <- ZIO.fail("Only the selling team owner can accept").when(!toTeam.ownerUserId.contains(userId))
      fromCount <- playerRepo.countByTeam(offer.fromTeamId).transact(xa).orDie
      toCount <- playerRepo.countByTeam(offer.toTeamId).transact(xa).orDie
      _ <- ZIO.fail("Selling would leave fewer than 16 players").when(toCount <= 16)
      _ <- ZIO.fail("Buyer would exceed 20 players").when(fromCount >= 20)
      _ <- ZIO.fail("Insufficient budget").when(fromTeam.budget < offer.amount)
      _ <- playerRepo.updateTeamId(offer.playerId, offer.fromTeamId).transact(xa).orDie
      _ <- teamRepo.update(fromTeam.copy(budget = fromTeam.budget - offer.amount)).transact(xa).orDie
      _ <- teamRepo.update(toTeam.copy(budget = toTeam.budget + offer.amount)).transact(xa).orDie
      now = Instant.now()
      _ <- transferOfferRepo.update(offer.copy(status = TransferOfferStatus.Accepted, respondedAt = Some(now))).transact(xa).orDie
      _ <- ZIO.logInfo(s"Transfer accepted: offerId=${offerId.value.take(8)} player=${offer.playerId.value.take(8)} amount=${offer.amount} from=${offer.fromTeamId.value.take(8)} to=${offer.toTeamId.value.take(8)}")
    } yield ()

  def rejectTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit] =
    for {
      opt <- transferOfferRepo.findById(offerId).transact(xa).orDie
      offer <- ZIO.fromOption(opt).orElseFail("Offer not found")
      _ <- ZIO.fail("Offer already responded").when(offer.status != TransferOfferStatus.Pending)
      toTeamOpt <- teamRepo.findById(offer.toTeamId).transact(xa).orDie
      toTeam <- ZIO.fromOption(toTeamOpt).orElseFail("To team not found")
      _ <- ZIO.fail("Only the selling team owner can reject").when(!toTeam.ownerUserId.contains(userId))
      now = Instant.now()
      _ <- transferOfferRepo.update(offer.copy(status = TransferOfferStatus.Rejected, respondedAt = Some(now))).transact(xa).orDie
    } yield ()

  def submitMatchSquad(matchId: MatchId, teamId: TeamId, userId: UserId, req: SubmitMatchSquadRequest): ZIO[Any, String, MatchSquadDto] =
    for {
      mOpt <- matchRepo.findById(matchId).transact(xa).orDie
      m <- ZIO.fromOption(mOpt).orElseFail("Match not found")
      _ <- ZIO.fail("Match already played").when(m.status != MatchStatus.Scheduled)
      _ <- ZIO.fail("Team not in this match").when(m.homeTeamId != teamId && m.awayTeamId != teamId)
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can submit squad").when(!team.ownerUserId.contains(userId))
      lineup = req.lineup.map(s => LineupSlot(PlayerId(s.playerId), s.positionSlot))
      _ <- ZIO.fail("Squad must have exactly 11 players").when(lineup.size != 11)
      gkSlots = lineup.count(_.positionSlot == "GK")
      _ <- ZIO.fail("Squad must have exactly 1 goalkeeper (slot GK)").when(gkSlots != 1)
      players <- playerRepo.listByTeam(teamId).transact(xa).orDie
      playerIds = players.map(_.id).toSet
      _ <- ZIO.fail("All players must belong to your team").when(!lineup.forall(s => playerIds.contains(s.playerId)))
      available = players.filter(p => p.injury.forall(_.returnAtMatchday <= m.matchday))
      availableIds = available.map(_.id).toSet
      _ <- ZIO.fail("Some selected players are injured or unavailable").when(!lineup.forall(s => availableIds.contains(s.playerId)))
      _ <- ZIO.fail("Game plan JSON too long (max 20 KB)").when(req.gamePlanJson.length > 20 * 1024)
      now = Instant.now()
      existing <- matchSquadRepo.findByMatchAndTeam(matchId, teamId).transact(xa).orDie
      squad <- existing match {
        case Some(s) =>
          val updated = s.copy(lineup = lineup, gamePlanJson = req.gamePlanJson, submittedAt = now, source = MatchSquadSource.Manual)
          matchSquadRepo.update(updated).transact(xa).orDie.as(updated)
        case None =>
          for {
            newSquadId <- IdGen.genMatchSquadId
            newSquad = MatchSquad(newSquadId, matchId, teamId, lineup, req.gamePlanJson, now, MatchSquadSource.Manual)
            _ <- matchSquadRepo.create(newSquad).transact(xa).orDie
          } yield newSquad
      }
    } yield MatchSquadDto(squad.id.value, squad.matchId.value, squad.teamId.value, squad.lineup.map(l => LineupSlotDto(l.playerId.value, l.positionSlot)), squad.source.toString)

  def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): ZIO[Any, String, PlayerDto] =
    for {
      playerOpt <- playerRepo.findById(playerId).transact(xa).orDie
      player <- ZIO.fromOption(playerOpt).orElseFail("Player not found")
      teamOpt <- teamRepo.findById(player.teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can update player").when(!team.ownerUserId.contains(userId))
      firstName = req.firstName.getOrElse(player.firstName)
      lastName = req.lastName.getOrElse(player.lastName)
      _ <- playerRepo.updateName(playerId, firstName, lastName).transact(xa).orDie
      updated = player.copy(firstName = firstName, lastName = lastName)
    } yield PlayerDto(updated.id.value, updated.teamId.value, updated.firstName, updated.lastName, updated.preferredPositions.toList, updated.injury.map(i => s"return matchday ${i.returnAtMatchday}"), updated.freshness, updated.morale, physical = updated.physical, technical = updated.technical, mental = updated.mental, traits = updated.traits)

  def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): ZIO[Any, String, GamePlanSnapshotDto] =
    for {
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can save game plan").when(!team.ownerUserId.contains(userId))
      snapId <- IdGen.genGamePlanSnapshotId
      snap = GamePlanSnapshot(snapId, teamId, name, gamePlanJson, Instant.now())
      _ <- gamePlanSnapshotRepo.create(snap).transact(xa).orDie
    } yield GamePlanSnapshotDto(snap.id.value, snap.teamId.value, snap.name, snap.createdAt.toEpochMilli)

  def runScheduledMatchdays(): ZIO[Any, Nothing, Unit] = {
    val getLeagues: zio.Task[List[League]] = leagueRepo.listBySeasonPhase(SeasonPhase.InProgress).transact(xa)
    getLeagues.orDie.flatMap { leagues =>
      ZIO.foreachDiscard(leagues) { league =>
        league.startDate match {
          case None => ZIO.unit
          case Some(start) =>
            val nowInTz = Instant.now().atZone(league.timezone)
            val today = nowInTz.toLocalDate
            val hour = nowInTz.getHour
            val isWedOrSat = today.getDayOfWeek == DayOfWeek.WEDNESDAY || today.getDayOfWeek == DayOfWeek.SATURDAY
            val dueNow = isWedOrSat && hour >= 17
            if (!dueNow) ZIO.unit
            else {
              val matchdayOpt = matchdayForDate(start, league.totalMatchdays, today)
              matchdayOpt match {
                case Some(md) if league.currentMatchday + 1 == md =>
                  playMatchday(league.id, league.createdByUserId).ignore
                case _ => ZIO.unit
              }
            }
        }
      }
    }
  }

  private val ExportMatchIdsMax = 50

  def exportMatchLogs(matchIds: List[MatchId], format: String, userId: UserId, eventTypesOpt: Option[List[String]] = None): ZIO[Any, String, String] =
    (for {
      _ <- ZIO.fail(s"Too many match IDs (max $ExportMatchIdsMax)").when(matchIds.length > ExportMatchIdsMax)
      matches <- ZIO.foreach(matchIds)(id => matchRepo.findById(id).transact(xa).orDie)
      _ <- ZIO.foreach(matches.zip(matchIds)) { case (opt, mid) =>
        opt match {
          case None => ZIO.fail(s"Match not found: ${mid.value}")
          case Some(m) => ensureUserHasAccessToLeague(userId, m.leagueId)
        }
      }
      logs <- if (matchIds.isEmpty) ZIO.succeed(Nil)
              else matchResultLogRepo.findByMatchIds(matchIds).transact(xa).orDie
      allowedTypes = eventTypesOpt.flatMap(types => { val t = types.map(_.trim).filter(_.nonEmpty); if (t.isEmpty) None else Some(t.toSet) })
      logsToUse = allowedTypes match {
        case None => logs
        case Some(allowed) => logs.map(log => log.copy(events = log.events.filter(e => allowed.contains(e.eventType))))
      }
      rows = logsToUse.flatMap(log => log.events.map(e => (log.matchId, e)))
      out <- if (format.equalsIgnoreCase("csv")) ZIO.succeed(ExportFormats.eventsToCsv(rows))
             else if (format.equalsIgnoreCase("statsbomb") || format.equalsIgnoreCase("json")) ZIO.succeed(ExportFormats.eventsToStatsBombJson(rows))
             else if (format.equalsIgnoreCase("json-full")) {
               import io.circe.syntax._
               import fmgame.backend.api.MatchSummaryDtoCodec._
               val arr = logsToUse.map { log =>
                 val eventsJson = io.circe.Json.arr(log.events.map(e => ExportFormats.eventToJson(log.matchId, e)): _*)
                 val summaryJson = log.summary.map(s => toMatchSummaryDto(s).asJson)
                 io.circe.Json.obj(
                   "matchId" -> io.circe.Json.fromString(log.matchId.value),
                   "events" -> eventsJson,
                   "summary" -> summaryJson.getOrElse(io.circe.Json.Null)
                 )
               }
               ZIO.succeed(io.circe.Json.arr(arr: _*).noSpaces)
             }
             else ZIO.fail("format must be csv, json, or json-full")
      _ <- ZIO.logInfo(s"Exported ${rows.size} events from ${matchIds.size} match(es) as $format")
    } yield out).tapError(err => ZIO.logWarning(s"Export match logs failed: $err"))

  def exportMatchLogsWithFilters(matchIds: List[MatchId], format: String, userId: UserId, leagueIdOpt: Option[LeagueId], fromMatchdayOpt: Option[Int], toMatchdayOpt: Option[Int], teamIdOpt: Option[TeamId], eventTypesOpt: Option[List[String]]): ZIO[Any, String, String] = {
    val useFilters = leagueIdOpt.nonEmpty && (fromMatchdayOpt.nonEmpty || toMatchdayOpt.nonEmpty || teamIdOpt.nonEmpty)
    val idsZ = if (useFilters) {
      val lid = leagueIdOpt.get
      ensureUserHasAccessToLeague(userId, lid) *>
        matchRepo.listByLeague(lid).transact(xa).orDie.map { matches =>
          val filtered = matches.filter { m =>
            val mdOk = fromMatchdayOpt.forall(m.matchday >= _) && toMatchdayOpt.forall(m.matchday <= _)
            val teamOk = teamIdOpt.forall(tid => m.homeTeamId == tid || m.awayTeamId == tid)
            mdOk && teamOk
          }
          filtered.sortBy(_.matchday).take(ExportMatchIdsMax).map(_.id)
        }
    } else ZIO.succeed(matchIds)
    idsZ.flatMap(ids => exportMatchLogs(ids, format, userId, eventTypesOpt))
  }

  def uploadEngineModel(kind: String, contentType: String, body: Array[Byte]): ZIO[Any, String, Unit] =
    (for {
      current <- engineModelsRef.get
      updated <- (kind.toLowerCase, contentType.toLowerCase) match {
        case ("xg", ct) if ct.contains("json") =>
          val json = new String(body, java.nio.charset.StandardCharsets.UTF_8)
          ZIO.succeed(current.copy(xg = EngineModelFactory.xGFromJson(json)))
        case ("xg", _) =>
          ZIO.attemptBlocking {
            val path = java.io.File.createTempFile("xg_", ".onnx")
            java.nio.file.Files.write(path.toPath, body)
            path.getAbsolutePath
          }.mapError(_.getMessage).map { p => current.copy(xg = EngineModelFactory.xGFromPath(p)) }
        case ("vaep", _) =>
          ZIO.succeed(current)
        case _ => ZIO.fail("kind must be xg or vaep")
      }
      _ <- engineModelsRef.set(updated)
      _ <- ZIO.logInfo(s"Engine model updated: kind=$kind contentType=$contentType bodySize=${body.length}")
    } yield ()).tapError(err => ZIO.logWarning(s"Upload engine model failed: $err"))

  /** Pomocnicze zagregowane statystyki dla jednego meczu. */
  private case class PlayerAgg(goals: Int = 0, assists: Int = 0)

  /** Na podstawie listy zdarzeń meczu wylicza gole i asysty (ostatnie udane podanie jako asysta). */
  private def computePlayerStatsFromEvents(events: List[MatchEventRecord]): Map[PlayerId, PlayerAgg] = {
    val stats = scala.collection.mutable.Map.empty[PlayerId, PlayerAgg]
    var lastPassByTeam = Map.empty[TeamId, PlayerId]
    events.foreach { e =>
      e.eventType match {
        case "Pass" | "LongPass" if e.outcome.contains("Success") =>
          for {
            tid <- e.teamId
            pid <- e.actorPlayerId
          } lastPassByTeam = lastPassByTeam.updated(tid, pid)
        case "Goal" =>
          // gole
          e.actorPlayerId.foreach { pid =>
            val prev = stats.getOrElse(pid, PlayerAgg())
            stats(pid) = prev.copy(goals = prev.goals + 1)
          }
          // asysta: ostatni udany Pass/LongPass tej samej drużyny, inny zawodnik
          for {
            tid <- e.teamId
            passer <- lastPassByTeam.get(tid)
            scorerOpt = e.actorPlayerId
            if !scorerOpt.contains(passer)
          } {
            val prev = stats.getOrElse(passer, PlayerAgg())
            stats(passer) = prev.copy(assists = prev.assists + 1)
          }
        case _ =>
      }
    }
    stats.toMap
  }

  private case class PlayerAggAdv(
    matches: Int = 0,
    minutes: Int = 0,
    goals: Int = 0,
    assists: Int = 0,
    shots: Int = 0,
    shotsOnTarget: Int = 0,
    xg: Double = 0.0,
    keyPasses: Int = 0,
    passes: Int = 0,
    passesCompleted: Int = 0,
    tackles: Int = 0,
    interceptions: Int = 0
  )

  /** Minuty na podstawie lineup + zdarzeń Substitution. */
  private def computeMinutesByPlayer(lineupIds: Set[PlayerId], events: List[MatchEventRecord]): Map[PlayerId, Int] = {
    val startMinute = scala.collection.mutable.Map.empty[PlayerId, Int]
    val endMinute = scala.collection.mutable.Map.empty[PlayerId, Int]
    lineupIds.foreach(pid => startMinute(pid) = 0)
    events.filter(_.eventType == "Substitution").foreach { e =>
      val minute = e.minute
      val inPidOpt = e.actorPlayerId
      val outPidOpt = e.secondaryPlayerId
      outPidOpt.foreach { outPid =>
        if (lineupIds.contains(outPid)) endMinute.update(outPid, endMinute.getOrElse(outPid, 90).min(minute))
      }
      inPidOpt.foreach { inPid =>
        // jeśli wszedł z ławki, naliczamy od tej minuty
        if (!startMinute.contains(inPid)) startMinute(inPid) = minute
      }
    }
    (startMinute.keySet ++ endMinute.keySet).map { pid =>
      val st = startMinute.getOrElse(pid, 0)
      val en = endMinute.getOrElse(pid, 90)
      pid -> (en - st).max(0).min(90)
    }.toMap
  }

  /** Zaawansowane statystyki z eventów (xG, strzały, podania, key passes, tackli, przechwyty). */
  private def computeAdvancedFromEvents(events: List[MatchEventRecord]): (Map[PlayerId, PlayerAggAdv], Map[PlayerId, PlayerAgg]) = {
    val base = scala.collection.mutable.Map.empty[PlayerId, PlayerAggAdv]
    val goalsAssists = computePlayerStatsFromEvents(events)
    var lastPassByTeam = Map.empty[TeamId, PlayerId]
    events.foreach { e =>
      e.eventType match {
        case "Pass" | "LongPass" =>
          e.actorPlayerId.foreach { pid =>
            val prev = base.getOrElse(pid, PlayerAggAdv())
            val att = prev.passes + 1
            val comp = prev.passesCompleted + (if (e.outcome.contains("Success")) 1 else 0)
            base(pid) = prev.copy(passes = att, passesCompleted = comp)
          }
          if (e.outcome.contains("Success")) {
            for { tid <- e.teamId; pid <- e.actorPlayerId } lastPassByTeam = lastPassByTeam.updated(tid, pid)
          }
        case "Shot" | "Goal" =>
          e.actorPlayerId.foreach { shooter =>
            val prev = base.getOrElse(shooter, PlayerAggAdv())
            val onTarget = if (e.outcome.contains("Saved") || e.outcome.contains("Success")) 1 else 0
            val xg = e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(if (e.eventType == "Goal") 0.5 else 0.2)
            base(shooter) = prev.copy(shots = prev.shots + 1, shotsOnTarget = prev.shotsOnTarget + onTarget, xg = prev.xg + xg)
          }
          for {
            tid <- e.teamId
            passer <- lastPassByTeam.get(tid)
            shooter <- e.actorPlayerId
            if passer != shooter
          } {
            val prev = base.getOrElse(passer, PlayerAggAdv())
            base(passer) = prev.copy(keyPasses = prev.keyPasses + 1)
          }
        case "Tackle" =>
          e.actorPlayerId.foreach { pid =>
            val prev = base.getOrElse(pid, PlayerAggAdv())
            base(pid) = prev.copy(tackles = prev.tackles + 1)
          }
        case "PassIntercepted" =>
          e.actorPlayerId.foreach { pid =>
            val prev = base.getOrElse(pid, PlayerAggAdv())
            base(pid) = prev.copy(interceptions = prev.interceptions + 1)
          }
        case _ =>
      }
    }
    (base.toMap, goalsAssists)
  }

  def getLeaguePlayerAdvancedStatsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, LeaguePlayerAdvancedStatsDto] =
    ensureUserHasAccessToLeague(userId, leagueId) *> {
      for {
        matches <- matchRepo.listByLeague(leagueId).transact(xa).orDie
        played = matches.filter(_.status == MatchStatus.Played)
        playedIds = played.map(_.id)
        logs <- matchResultLogRepo.findByMatchIds(playedIds).transact(xa).orDie
        squadsByMatch <- ZIO.foreach(playedIds)(mid => matchSquadRepo.listByMatch(mid).transact(xa).orDie)
        // (matchId -> teamId -> lineupIds)
        matchLineups = playedIds.zip(squadsByMatch).map { case (mid, squads) =>
          mid -> squads.map(s => s.teamId -> s.lineup.map(_.playerId).toSet).toMap
        }.toMap
        perMatchAgg = logs.map(log => (log.matchId, log.events))
        // player stats acc
        acc = scala.collection.mutable.Map.empty[PlayerId, PlayerAggAdv]
        _ = perMatchAgg.foreach { case (mid, events) =>
          val (adv, ga) = computeAdvancedFromEvents(events)
          // minuty: dla obu drużyn z lineupów
          val lineups = matchLineups.getOrElse(mid, Map.empty)
          val lineupIds = lineups.values.flatten.toSet
          val minutesByPlayer = computeMinutesByPlayer(lineupIds, events)
          lineupIds.foreach { pid =>
            val prev = acc.getOrElse(pid, PlayerAggAdv())
            val mins = minutesByPlayer.getOrElse(pid, 0)
            acc(pid) = prev.copy(matches = prev.matches + (if (mins > 0) 1 else 0), minutes = prev.minutes + mins)
          }
          adv.foreach { case (pid, a) =>
            val prev = acc.getOrElse(pid, PlayerAggAdv())
            val gaAgg = ga.getOrElse(pid, PlayerAgg())
            acc(pid) = prev.copy(
              goals = prev.goals + gaAgg.goals,
              assists = prev.assists + gaAgg.assists,
              shots = prev.shots + a.shots,
              shotsOnTarget = prev.shotsOnTarget + a.shotsOnTarget,
              xg = prev.xg + a.xg,
              keyPasses = prev.keyPasses + a.keyPasses,
              passes = prev.passes + a.passes,
              passesCompleted = prev.passesCompleted + a.passesCompleted,
              tackles = prev.tackles + a.tackles,
              interceptions = prev.interceptions + a.interceptions
            )
          }
        }
        teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
        playersByTeam <- traverseConn(teams)(t => playerRepo.listByTeam(t.id).map(ps => (t, ps))).transact(xa).orDie
        playerMeta = playersByTeam.flatMap { case (t, ps) =>
          ps.map(p => p.id -> (s"${p.firstName} ${p.lastName}", t.id, t.name))
        }.toMap
        rows = acc.toList.flatMap { case (pid, a) =>
          playerMeta.get(pid).map { case (name, tid, teamName) =>
            PlayerSeasonAdvancedStatsRowDto(
              playerId = pid.value,
              playerName = name,
              teamId = tid.value,
              teamName = teamName,
              matches = a.matches,
              minutes = a.minutes,
              goals = a.goals,
              assists = a.assists,
              shots = a.shots,
              shotsOnTarget = a.shotsOnTarget,
              xg = BigDecimal(a.xg).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble,
              keyPasses = a.keyPasses,
              passes = a.passes,
              passesCompleted = a.passesCompleted,
              tackles = a.tackles,
              interceptions = a.interceptions
            )
          }
        }.sortBy(r => (-r.goals, -r.assists, -r.xg, r.playerName))
      } yield LeaguePlayerAdvancedStatsDto(rows)
    }

  def getH2HForUser(leagueId: LeagueId, teamId1: TeamId, teamId2: TeamId, limit: Int, userId: UserId): ZIO[Any, String, List[MatchDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *>
      (for {
        matches <- matchRepo.listByLeague(leagueId).transact(xa).orDie
        h2h = matches.filter(m => (m.homeTeamId == teamId1 && m.awayTeamId == teamId2) || (m.homeTeamId == teamId2 && m.awayTeamId == teamId1))
          .filter(_.status == MatchStatus.Played)
          .sortBy(-_.matchday)
          .take(math.min(limit, 20))
        referees <- refereeRepo.listByLeague(leagueId).transact(xa).orDie
        refByName = referees.map(r => r.id -> r.name).toMap
      } yield h2h.map(m => MatchDto(m.id.value, m.leagueId.value, m.matchday, m.homeTeamId.value, m.awayTeamId.value, m.scheduledAt.toEpochMilli, m.status.toString, m.homeGoals, m.awayGoals, m.refereeId.value, refByName.get(m.refereeId))))

  def getMatchdayPrognosisForUser(leagueId: LeagueId, matchdayOpt: Option[Int], userId: UserId): ZIO[Any, String, List[MatchPrognosisDto]] =
    ensureUserHasAccessToLeague(userId, leagueId) *>
      (for {
        league <- getById(leagueId)
        md = matchdayOpt.getOrElse(league.currentMatchday + 1)
        matches <- matchRepo.listByLeagueAndMatchday(leagueId, md).transact(xa).orDie
        teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
        teamById = teams.map(t => t.id -> t).toMap
        prognoses <- ZIO.foreach(matches) { m =>
          val home = teamById.get(m.homeTeamId).fold(1500.0)(_.eloRating)
          val away = teamById.get(m.awayTeamId).fold(1500.0)(_.eloRating)
          val expectedHome = 1.0 / (1.0 + math.pow(10.0, (away - home) / 400.0))
          val expectedAway = 1.0 - expectedHome
          val (pHome, pDraw, pAway) = (expectedHome * 0.85, 0.15 + (0.5 - math.abs(expectedHome - 0.5)) * 0.2, expectedAway * 0.85)
          val sum = pHome + pDraw + pAway
          val (ph, pd, pa) = (pHome / sum, pDraw / sum, pAway / sum)
          val homeName = teamById.get(m.homeTeamId).fold(m.homeTeamId.value)(_.name)
          val awayName = teamById.get(m.awayTeamId).fold(m.awayTeamId.value)(_.name)
          ZIO.succeed(MatchPrognosisDto(m.id.value, m.homeTeamId.value, m.awayTeamId.value, homeName, awayName, ph, pd, pa))
        }
      } yield prognoses)

  def getComparePlayersForUser(leagueId: LeagueId, playerId1: PlayerId, playerId2: PlayerId, userId: UserId): ZIO[Any, String, ComparePlayersDto] =
    ensureUserHasAccessToLeague(userId, leagueId) *>
      (for {
        adv <- getLeaguePlayerAdvancedStatsForUser(leagueId, userId)
        allRows = adv.rows
        p1Opt <- playerRepo.findById(playerId1).transact(xa).orDie
        p2Opt <- playerRepo.findById(playerId2).transact(xa).orDie
        p1 <- ZIO.fromOption(p1Opt).orElseFail("Player 1 not found")
        p2 <- ZIO.fromOption(p2Opt).orElseFail("Player 2 not found")
        teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
        teamIds = teams.map(_.id).toSet
        _ <- ZIO.fail("Both players must be in this league").when(!teamIds.contains(p1.teamId) || !teamIds.contains(p2.teamId))
        dto1 = PlayerDto(
          p1.id.value, p1.teamId.value, p1.firstName, p1.lastName, p1.preferredPositions.toList,
          p1.injury.map(i => s"return matchday ${i.returnAtMatchday}"), p1.freshness, p1.morale,
          physical = p1.physical, technical = p1.technical, mental = p1.mental, traits = p1.traits
        )
        dto2 = PlayerDto(
          p2.id.value, p2.teamId.value, p2.firstName, p2.lastName, p2.preferredPositions.toList,
          p2.injury.map(i => s"return matchday ${i.returnAtMatchday}"), p2.freshness, p2.morale,
          physical = p2.physical, technical = p2.technical, mental = p2.mental, traits = p2.traits
        )
        stats1 = allRows.find(_.playerId == playerId1.value)
        stats2 = allRows.find(_.playerId == playerId2.value)
      } yield ComparePlayersDto(dto1, dto2, stats1, stats2))

  def getTrainingPlanForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, TrainingPlanDto] =
    for {
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can view training plan").when(!team.ownerUserId.contains(userId))
      planOpt <- trainingPlanRepo.findByTeamId(teamId).transact(xa).orDie
      now = Instant.now()
      plan = planOpt.getOrElse(TeamTrainingPlan(teamId, week = List.fill(7)("Balanced"), updatedAt = now))
    } yield TrainingPlanDto(teamId.value, plan.week.padTo(7, "Balanced").take(7), plan.updatedAt.toEpochMilli)

  def upsertTrainingPlanForUser(teamId: TeamId, userId: UserId, week: List[String]): ZIO[Any, String, TrainingPlanDto] =
    for {
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can update training plan").when(!team.ownerUserId.contains(userId))
      now = Instant.now()
      saved <- trainingPlanRepo.upsert(teamId, week.padTo(7, "Balanced").take(7), now).transact(xa).orDie
    } yield TrainingPlanDto(teamId.value, saved.week, saved.updatedAt.toEpochMilli)

  private def overallOf(p: Player): Double = {
    val all = p.physical.values ++ p.technical.values ++ p.mental.values
    if (all.isEmpty) 10.0 else all.sum.toDouble / all.size
  }

  def listLeaguePlayersForUser(leagueId: LeagueId, userId: UserId, posOpt: Option[String], minOverallOpt: Option[Double], qOpt: Option[String]): ZIO[Any, String, LeaguePlayersDto] =
    ensureUserHasAccessToLeague(userId, leagueId) *>
      (for {
        teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
        teamById = teams.map(t => t.id -> t).toMap
        playersByTeam <- traverseConn(teams)(t => playerRepo.listByTeam(t.id).map(ps => (t, ps))).transact(xa).orDie
        flat = playersByTeam.flatMap { case (t, ps) =>
          ps.map { p =>
            val overall = overallOf(p)
            LeaguePlayerRowDto(
              playerId = p.id.value,
              playerName = s"${p.firstName} ${p.lastName}",
              teamId = t.id.value,
              teamName = t.name,
              preferredPositions = p.preferredPositions.toList,
              overall = BigDecimal(overall).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
            )
          }
        }
        posF = posOpt.map(_.trim.toLowerCase).filter(_.nonEmpty)
        qF = qOpt.map(_.trim.toLowerCase).filter(_.nonEmpty)
        minO = minOverallOpt.getOrElse(0.0)
        filtered = flat.filter { r =>
          val okPos = posF.forall(p => r.preferredPositions.exists(_.toLowerCase.contains(p)))
          val okQ = qF.forall(q => r.playerName.toLowerCase.contains(q) || r.teamName.toLowerCase.contains(q))
          okPos && okQ && r.overall >= minO
        }.sortBy(r => (-r.overall, r.playerName))
      } yield LeaguePlayersDto(filtered))

  def getShortlistForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ShortlistEntryDto]] =
    for {
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can view shortlist").when(!team.ownerUserId.contains(userId))
      entries <- shortlistRepo.listByTeam(teamId).transact(xa).orDie
      // map playerId -> (name, fromTeamName)
      leagueTeams <- teamRepo.listByLeague(team.leagueId).transact(xa).orDie
      playersByTeam <- traverseConn(leagueTeams)(t => playerRepo.listByTeam(t.id).map(ps => (t, ps))).transact(xa).orDie
      meta = playersByTeam.flatMap { case (t, ps) => ps.map(p => p.id -> (s"${p.firstName} ${p.lastName}", t.name)) }.toMap
      out = entries.flatMap { e =>
        meta.get(e.playerId).map { case (name, fromTeamName) =>
          ShortlistEntryDto(teamId.value, e.playerId.value, name, fromTeamName, e.createdAt.toEpochMilli)
        }
      }
    } yield out

  def addToShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit] =
    for {
      _ <- getTeamForUser(teamId, userId)
      now = Instant.now()
      _ <- shortlistRepo.add(teamId, playerId, now).transact(xa).orDie
    } yield ()

  def removeFromShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit] =
    for {
      _ <- getTeamForUser(teamId, userId)
      _ <- shortlistRepo.remove(teamId, playerId).transact(xa).orDie
    } yield ()

  def listScoutingReportsForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ScoutingReportDto]] =
    for {
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can view scouting reports").when(!team.ownerUserId.contains(userId))
      reports <- scoutingReportRepo.listByTeam(teamId).transact(xa).orDie
      leagueTeams <- teamRepo.listByLeague(team.leagueId).transact(xa).orDie
      playersByTeam <- traverseConn(leagueTeams)(t => playerRepo.listByTeam(t.id).map(ps => (t, ps))).transact(xa).orDie
      meta = playersByTeam.flatMap { case (_, ps) => ps.map(p => p.id -> s"${p.firstName} ${p.lastName}") }.toMap
      out = reports.map { r =>
        ScoutingReportDto(r.id, r.teamId.value, r.playerId.value, meta.getOrElse(r.playerId, r.playerId.value), r.rating, r.notes, r.createdAt.toEpochMilli)
      }
    } yield out

  def createScoutingReportForUser(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): ZIO[Any, String, ScoutingReportDto] =
    for {
      teamOpt <- teamRepo.findById(teamId).transact(xa).orDie
      team <- ZIO.fromOption(teamOpt).orElseFail("Team not found")
      _ <- ZIO.fail("Only team owner can create scouting reports").when(!team.ownerUserId.contains(userId))
      id <- ZIO.succeed(java.util.UUID.randomUUID().toString)
      now = Instant.now()
      r = ScoutingReport(id, teamId, playerId, rating.max(0.0).min(10.0), notes.take(2000), now)
      _ <- scoutingReportRepo.create(r).transact(xa).orDie
      playerOpt <- playerRepo.findById(playerId).transact(xa).orDie
      pname = playerOpt.map(p => s"${p.firstName} ${p.lastName}").getOrElse(playerId.value)
    } yield ScoutingReportDto(r.id, r.teamId.value, r.playerId.value, pname, r.rating, r.notes, r.createdAt.toEpochMilli)

  def applyPressConference(matchId: MatchId, teamId: TeamId, userId: UserId, phase: String, tone: String): ZIO[Any, String, Unit] =
    for {
      mOpt <- matchRepo.findById(matchId).transact(xa).orDie
      m    <- ZIO.fromOption(mOpt).orElseFail("Match not found")
      _    <- ensureUserHasAccessToLeague(userId, m.leagueId)
      _    <- ZIO.fail("Team must be home or away in this match").when(teamId.value != m.homeTeamId.value && teamId.value != m.awayTeamId.value)
      tOpt <- teamRepo.findById(teamId).transact(xa).orDie
      t    <- ZIO.fromOption(tOpt).orElseFail("Team not found")
      _    <- ZIO.fail("Only team owner can give press conference").when(!t.ownerUserId.contains(userId))
      delta = tone.toLowerCase match {
        case "praise" | "pochwala"   => 0.03
        case "criticize" | "krytyka" => -0.02
        case "calm" | "spokojnie"    => 0.01
        case _                      => 0.0
      }
      players <- playerRepo.listByTeam(teamId).transact(xa).orDie
      _       <- ZIO.foreachDiscard(players)(p =>
        playerRepo.updateFreshnessMorale(p.id, p.freshness, (p.morale + delta).max(0.0).min(1.0)).transact(xa).orDie
      )
      _       <- ZIO.logInfo(s"Press conference: match=${matchId.value.take(8)} team=${teamId.value.take(8)} phase=$phase tone=$tone moraleDelta=$delta")
    } yield ()

  def getMetrics: ZIO[Any, Nothing, MetricsDto] =
    matchRepo.countPlayedAndTotalGoals.transact(xa).orDie.map { case (count, totalGoals) =>
      MetricsDto(count, totalGoals, if (count > 0) totalGoals.toDouble / count else 0.0)
    }

  /** Statystyki sezonowe zawodników w lidze (gole, asysty). Preferuje agregację z tabeli league_player_match_stats; fallback do agregacji z logów. */
  def getLeaguePlayerStatsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, LeaguePlayerStatsDto] =
    ensureUserHasAccessToLeague(userId, leagueId) *> {
      for {
        aggregated <- leaguePlayerMatchStatsRepo.sumByLeague(leagueId).transact(xa).orDie
        teams <- teamRepo.listByLeague(leagueId).transact(xa).orDie
        playersByTeam <- traverseConn(teams)(t => playerRepo.listByTeam(t.id).map(ps => (t, ps))).transact(xa).orDie
        playerMeta = playersByTeam.flatMap { case (t, ps) =>
          ps.map(p => p.id -> (s"${p.firstName} ${p.lastName}", t.id, t.name))
        }.toMap
        rows <- if (aggregated.nonEmpty) {
          val fromTable = aggregated.flatMap { case (pid, tid, goals, assists) =>
            playerMeta.get(pid).map { case (name, _, teamName) =>
              PlayerSeasonStatsRowDto(pid.value, name, tid.value, teamName, goals, assists)
            }
          }
          ZIO.succeed(fromTable)
        } else {
          for {
            matches <- matchRepo.listByLeague(leagueId).transact(xa).orDie
            playedIds = matches.filter(_.status == MatchStatus.Played).map(_.id)
            logs <- matchResultLogRepo.findByMatchIds(playedIds).transact(xa).orDie
            eventsPerMatch = logs.map(_.events)
            statsByPlayer = {
              val acc = scala.collection.mutable.Map.empty[PlayerId, PlayerAgg]
              eventsPerMatch.foreach { evs =>
                computePlayerStatsFromEvents(evs).foreach { case (pid, agg) =>
                  val prev = acc.getOrElse(pid, PlayerAgg())
                  acc(pid) = PlayerAgg(prev.goals + agg.goals, prev.assists + agg.assists)
                }
              }
              acc.toMap
            }
          } yield statsByPlayer.toList.flatMap { case (pid, agg) =>
            playerMeta.get(pid).map { case (name, tid, teamName) =>
              PlayerSeasonStatsRowDto(pid.value, name, tid.value, teamName, agg.goals, agg.assists)
            }
          }
        }
        topScorers = rows.sortBy(r => (-r.goals, -r.assists, r.playerName)).take(10)
        topAssists = rows.sortBy(r => (-r.assists, -r.goals, r.playerName)).take(10)
      } yield LeaguePlayerStatsDto(topScorers, topAssists)
    }

  private def matchdayForDate(startDate: LocalDate, totalMatchdays: Int, date: LocalDate): Option[Int] = {
    val dates = Iterator.iterate(startDate)(d => if (d.getDayOfWeek == DayOfWeek.WEDNESDAY) d.plusDays(3) else d.plusDays(4)).take(totalMatchdays).toList
    dates.indexOf(date) match { case -1 => None; case i => Some(i + 1) }
  }

  /** MODELE §9.3: estimated price = basePrice * (overall/10)^2; bot accepts if amount >= this. */
  private def estimatedPlayerPrice(player: Player): Double = {
    val allValues = player.physical.values ++ player.technical.values ++ player.mental.values
    val overall = if (allValues.isEmpty) 10.0 else allValues.sum.toDouble / allValues.size
    val basePrice = 100000.0
    basePrice * math.pow(overall / 10.0, 2)
  }

  private def nextWedOrSat(now: LocalDate): LocalDate = {
    val day = now.getDayOfWeek
    if (day == DayOfWeek.WEDNESDAY || day == DayOfWeek.SATURDAY) now
    else {
      var d = now
      while (!(d.getDayOfWeek == DayOfWeek.WEDNESDAY || d.getDayOfWeek == DayOfWeek.SATURDAY))
        d = d.plusDays(1)
      d
    }
  }

  private def toLeagueDto(l: League): LeagueDto =
    LeagueDto(
      l.id.value, l.name, l.teamCount, l.currentMatchday, l.totalMatchdays,
      l.seasonPhase.toString, l.homeAdvantage, l.startDate.map(_.toString),
      l.createdByUserId.value, l.createdAt.toEpochMilli, l.timezone.getId
    )

  private def toTeamDto(t: Team): TeamDto =
    TeamDto(
      t.id.value, t.leagueId.value, t.name, t.ownerType.toString,
      t.ownerUserId.map(_.value), t.ownerBotId.map(_.value),
      t.budget.toDouble, t.eloRating, t.managerName, t.createdAt.toEpochMilli
    )

  private def buildMatchReportText(m: MatchDto, homeName: String, awayName: String, summary: MatchSummaryDto, momName: Option[String]): String = {
    val hg  = m.homeGoals.getOrElse(0)
    val ag  = m.awayGoals.getOrElse(0)
    val ph  = summary.possessionPercent.lift(0).getOrElse(0.0).toInt
    val pa  = summary.possessionPercent.lift(1).getOrElse(0.0).toInt
    val xh  = summary.xgTotal.lift(0).getOrElse(0.0)
    val xa  = summary.xgTotal.lift(1).getOrElse(0.0)
    val mom = momName.fold("")(n => s" MOM: $n.")
    s"Mecz kolejki ${m.matchday}: $homeName $hg : $ag $awayName. Posiadanie: ${ph}% - ${pa}%. xG: ${f"$xh%.2f"} - ${f"$xa%.2f"}.$mom"
  }

  private def toMatchSummaryDto(s: MatchSummary): MatchSummaryDto =
    MatchSummaryDto(
      possessionPercent = List(s.possessionPercent._1, s.possessionPercent._2),
      homeGoals = s.homeGoals,
      awayGoals = s.awayGoals,
      shotsTotal = List(s.shotsTotal._1, s.shotsTotal._2),
      shotsOnTarget = List(s.shotsOnTarget._1, s.shotsOnTarget._2),
      shotsOffTarget = List(s.shotsOffTarget._1, s.shotsOffTarget._2),
      shotsBlocked = List(s.shotsBlocked._1, s.shotsBlocked._2),
      bigChances = List(s.bigChances._1, s.bigChances._2),
      xgTotal = List(s.xgTotal._1, s.xgTotal._2),
      passesTotal = List(s.passesTotal._1, s.passesTotal._2),
      passesCompleted = List(s.passesCompleted._1, s.passesCompleted._2),
      passAccuracyPercent = List(s.passAccuracyPercent._1, s.passAccuracyPercent._2),
      passesInFinalThird = List(s.passesInFinalThird._1, s.passesInFinalThird._2),
      crossesTotal = List(s.crossesTotal._1, s.crossesTotal._2),
      crossesSuccessful = List(s.crossesSuccessful._1, s.crossesSuccessful._2),
      longBallsTotal = List(s.longBallsTotal._1, s.longBallsTotal._2),
      longBallsSuccessful = List(s.longBallsSuccessful._1, s.longBallsSuccessful._2),
      tacklesTotal = List(s.tacklesTotal._1, s.tacklesTotal._2),
      tacklesWon = List(s.tacklesWon._1, s.tacklesWon._2),
      interceptions = List(s.interceptions._1, s.interceptions._2),
      clearances = List(s.clearances._1, s.clearances._2),
      blocks = List(s.blocks._1, s.blocks._2),
      saves = List(s.saves._1, s.saves._2),
      goalsConceded = List(s.goalsConceded._1, s.goalsConceded._2),
      fouls = List(s.fouls._1, s.fouls._2),
      yellowCards = List(s.yellowCards._1, s.yellowCards._2),
      redCards = List(s.redCards._1, s.redCards._2),
      foulsSuffered = List(s.foulsSuffered._1, s.foulsSuffered._2),
      corners = List(s.corners._1, s.corners._2),
      cornersWon = List(s.cornersWon._1, s.cornersWon._2),
      throwIns = List(s.throwIns._1, s.throwIns._2),
      freeKicksWon = List(s.freeKicksWon._1, s.freeKicksWon._2),
      offsides = List(s.offsides._1, s.offsides._2),
      duelsWon = s.duelsWon.map { case (h, a) => List(h, a) },
      aerialDuelsWon = s.aerialDuelsWon.map { case (h, a) => List(h, a) },
      possessionLost = s.possessionLost.map { case (h, a) => List(h, a) },
      vaepTotal = s.vaepTotal.map { case (h, a) => List(h, a) },
      wpaFinal = s.wpaFinal,
      fieldTilt = s.fieldTilt.map { case (h, a) => List(h, a) },
      ppda = s.ppda.map { case (h, a) => List(h, a) },
      ballTortuosity = s.ballTortuosity,
      metabolicLoad = s.metabolicLoad,
      xtByZone = s.xtByZone,
      injuries = Some(List(s.injuries._1, s.injuries._2)),
      homeShareByZone = s.homeShareByZone,
      vaepBreakdownByPlayer = s.vaepBreakdownByPlayer,
      pressingByPlayer = s.pressingByPlayer,
      estimatedDistanceByPlayer = s.estimatedDistanceByPlayer,
      influenceByPlayer = s.influenceByPlayer,
      avgDefendersInConeByZone = s.avgDefendersInConeByZone,
      avgGkDistanceByZone = s.avgGkDistanceByZone,
      setPieceZoneActivity = s.setPieceZoneActivity,
      pressingInOppHalfByPlayer = s.pressingInOppHalfByPlayer,
      playerTortuosityByPlayer = s.playerTortuosityByPlayer,
      metabolicLoadByPlayer = s.metabolicLoadByPlayer,
      iwpByPlayer = s.iwpByPlayer,
      setPiecePatternW = s.setPiecePatternW,
      setPiecePatternH = s.setPiecePatternH,
      setPieceRoutineCluster = s.setPieceRoutineCluster,
      poissonPrognosis = s.poissonPrognosis.map { case (h, d, a) => List(h, d, a) },
      voronoiCentroidByZone = s.voronoiCentroidByZone,
      passValueByPlayer = s.passValueByPlayer,
      passValueTotal = s.passValueTotal.map { case (h, a) => List(h, a) },
      passValueUnderPressureTotal = s.passValueUnderPressureTotal.map { case (h, a) => List(h, a) },
      passValueUnderPressureByPlayer = s.passValueUnderPressureByPlayer,
      influenceScoreByPlayer = s.influenceScoreByPlayer
    )
}
