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

trait LeagueService extends MatchService with TransferService with ScoutingService with ExportService {
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
  def getTeam(teamId: TeamId): ZIO[Any, String, TeamDto]
  /** Team with access check. */
  def getTeamForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, TeamDto]
  def getTeamPlayers(teamId: TeamId): ZIO[Any, String, List[PlayerDto]]
  def getTeamPlayersForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[PlayerDto]]
  def getTeamGamePlans(teamId: TeamId): ZIO[Any, String, List[GamePlanSnapshotDto]]
  def getTeamGamePlansForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[GamePlanSnapshotDto]]
  def getGamePlanSnapshot(teamId: TeamId, snapshotId: GamePlanSnapshotId): ZIO[Any, String, GamePlanSnapshotDetailDto]
  def getGamePlanSnapshotForUser(teamId: TeamId, snapshotId: GamePlanSnapshotId, userId: UserId): ZIO[Any, String, GamePlanSnapshotDetailDto]
  def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): ZIO[Any, String, PlayerDto]
  def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): ZIO[Any, String, GamePlanSnapshotDto]
  /** Called by scheduler: play matchdays that are due (17:00 Wed/Sat in league timezone). */
  def runScheduledMatchdays(): ZIO[Any, Nothing, Unit]
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
  /** Metryki dla endpointu /metrics (monitoring). */
  def getMetrics: ZIO[Any, Nothing, MetricsDto]
  /** Tworzy system ligi angielskiej (4 szczeble: Premier, Championship, League One, League Two) z 92 drużynami i graczami. Zwraca (wszystkie 4 ligi, liga użytkownika, drużyna użytkownika). */
  def createEnglishLeagueSystem(creatorUserId: UserId, myTeamName: String): ZIO[Any, String, (List[LeagueDto], LeagueDto, TeamDto)]
  /** Uruchamia sezon we wszystkich ligach danego systemu (np. "English"). */
  def startSeasonForSystem(systemName: String, userId: UserId): ZIO[Any, String, Unit]
  /** Awans/spadek: po zakończeniu sezonu we wszystkich ligach systemu przenosi drużyny (3 ostatnie → niżej, 3 pierwsze → wyżej). */
  def applyPromotionRelegation(systemName: String, userId: UserId): ZIO[Any, String, Unit]
  /** Po awansie/spadku: resetuje terminarze i uruchamia nowy sezon we wszystkich ligach systemu (nowe kolejki, okna transferowe). */
  def startNextSeasonForSystem(systemName: String, userId: UserId): ZIO[Any, String, Unit]
  /** Baraże: tworzy półfinały (3. vs 6., 4. vs 5.) – tylko ligi tier 2–4. */
  def createPlayOffSemiFinals(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit]
  /** Baraże: tworzy finał (zwycięzcy półfinałów) – tylko ligi tier 2–4. */
  def createPlayOffFinal(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit]
  /** Kontrakty drużyny (z nazwą zawodnika) – tylko właściciel. */
  def getTeamContractsForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ContractDto]]
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
  contractRepo: ContractRepository,
  engine: MatchEngine,
  engineModelsRef: zio.Ref[EngineModels],
  xa: doobie.Transactor[zio.Task]
) extends LeagueService {

  private val matchdayLocks = new java.util.concurrent.ConcurrentHashMap[String, zio.Semaphore]()
  private def withMatchdayLock[R, E, A](leagueId: LeagueId)(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    for {
      sem <- zio.Semaphore.make(1).map { newSem =>
        val existing = matchdayLocks.putIfAbsent(leagueId.value, newSem)
        if (existing != null) existing else newSem
      }
      result <- sem.withPermit(effect)
    } yield result

  private val pressConferenceGiven = {
    val maxEntries = 10000
    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap[(String, String, String), Boolean](256, 0.75f, true) {
      override def removeEldestEntry(eldest: java.util.Map.Entry[(String, String, String), Boolean]): Boolean = size > maxEntries
    })
  }
  private val connUnit: ConnectionIO[Unit] = cats.Applicative[ConnectionIO].pure(())
  private def traverseConn[A, B](as: List[A])(f: A => ConnectionIO[B]): ConnectionIO[List[B]] =
    as.foldRight(cats.Applicative[ConnectionIO].pure(List.empty[B]))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))

  def create(name: String, teamCount: Int, myTeamName: String, timezone: String, creatorUserId: UserId): ZIO[Any, String, (LeagueDto, TeamDto)] =
    for {
      _ <- ZIO.fail("League name must be 1-100 characters").when(name.trim.isEmpty || name.trim.length > 100)
      _ <- ZIO.fail("Team name must be 1-100 characters").when(myTeamName.trim.isEmpty || myTeamName.trim.length > 100)
      _ <- ZIO.fail("teamCount must be even between 10 and 24").when(teamCount < 10 || teamCount > 24 || teamCount % 2 != 0)
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
      _ <- {
        import cats.implicits.*
        (leagueRepo.create(league) *> teamRepo.create(team)).transact(xa).orDie
      }
      leagueDto = toLeagueDto(league)
      teamDto = toTeamDto(team)
    } yield (leagueDto, teamDto)

  def createEnglishLeagueSystem(creatorUserId: UserId, myTeamName: String): ZIO[Any, String, (List[LeagueDto], LeagueDto, TeamDto)] = {
    Ref.make(0).flatMap { nameOffsetRef =>
      def nextNameIdx(): ZIO[Any, Nothing, Int] = nameOffsetRef.modify(n => (n, n + 1))
      (for {
        _ <- ZIO.fail("Team name must be 1-100 characters").when(myTeamName.trim.isEmpty || myTeamName.trim.length > 100)
        tz = ZoneId.of("Europe/London")
        seed = ("English" + creatorUserId.value).hashCode.toLong
        rng = new Random(seed)
        leaguesAndUser <- ZIO.foldLeft(EnglishLeaguePreset.englishLeagueTiers.zipWithIndex)((List.empty[LeagueDto], Option.empty[LeagueDto], Option.empty[TeamDto])) { case ((leagueDtos, userLeagueOpt, userTeamOpt), ((leagueName, teamCount), tierIdx)) =>
          for {
            leagueId <- IdGen.genLeagueId
            totalMatchdays = 2 * (teamCount - 1)
            league = League(
              id = leagueId,
              name = leagueName,
              teamCount = teamCount,
              currentMatchday = 0,
              totalMatchdays = totalMatchdays,
              seasonPhase = SeasonPhase.Setup,
              homeAdvantage = 1.05,
              startDate = None,
              createdByUserId = creatorUserId,
              createdAt = Instant.now(),
              timezone = tz,
              leagueSystemName = Some("English"),
              tier = Some(tierIdx + 1)
            )
            _ <- leagueRepo.create(league).transact(xa).orDie
            isPremier = tierIdx == 0
            teamIds <- ZIO.foreach(0 until teamCount)(_ => IdGen.genTeamId)
            botIds <- ZIO.foreach(0 until (if (isPremier) teamCount - 1 else teamCount))(_ => IdGen.genBotId)
            names <- ZIO.foreach(0 until teamCount) { i =>
              if (isPremier && i == 0) ZIO.succeed(myTeamName.trim)
              else nextNameIdx().map(idx => EnglishLeaguePreset.nameForIndex(idx))
            }
            teams = (0 until teamCount).map { i =>
              val teamId = teamIds(i)
              if (isPremier && i == 0)
                Team(teamId, leagueId, names(i), TeamOwnerType.Human, Some(creatorUserId), None, BigDecimal(1000000), None, Instant.now(), managerName = None)
              else {
                val botIdx = if (isPremier) i - 1 else i
                Team(teamId, leagueId, names(i), TeamOwnerType.Bot, None, Some(botIds(botIdx)), BigDecimal(500_000 + rng.nextInt(1_500_001)), None, Instant.now(), managerName = Some(s"Manager ${i + 1}"))
              }
            }.toList
            _ <- { import cats.implicits.*; teams.traverse_(t => teamRepo.create(t)).transact(xa).orDie }
            allSquads = teams.flatMap(t => PlayerGenerator.generateSquad(t.id, rng))
            _ <- {
              import cats.implicits.*
              (for {
                _ <- allSquads.traverse_(p => playerRepo.create(p))
                _ <- teams.traverse_ { t =>
                  val squad = allSquads.filter(_.teamId == t.id)
                  val contracts = PlayerGenerator.generateInitialContracts(squad, t.id, totalMatchdays)
                  contracts.traverse_(c => contractRepo.create(c))
                }
              } yield ()).transact(xa).orDie
            }
            newDtos = leagueDtos :+ toLeagueDto(league)
            (uLeague, uTeam) = if (isPremier) (Some(toLeagueDto(league)), Some(toTeamDto(teams.head))) else (userLeagueOpt, userTeamOpt)
          } yield (newDtos, uLeague, uTeam)
        }
        (list, uL, uT) = leaguesAndUser
        userLeague <- ZIO.fromOption(uL).orElseFail("English system has no Premier League")
        userTeam <- ZIO.fromOption(uT).orElseFail("User team not found")
      } yield (list, userLeague, userTeam))
    }
  }

  def startSeasonForSystem(systemName: String, userId: UserId): ZIO[Any, String, Unit] =
    for {
      leagues <- leagueRepo.listByLeagueSystemName(systemName).transact(xa).orDie
      _ <- ZIO.fail(s"No leagues found for system: $systemName").when(leagues.isEmpty)
      _ <- ZIO.foreachDiscard(leagues)(league => startSeason(league.id, userId, None).unit)
    } yield ()

  def applyPromotionRelegation(systemName: String, userId: UserId): ZIO[Any, String, Unit] =
    for {
      leagues <- leagueRepo.listByLeagueSystemName(systemName).transact(xa).orDie
      _ <- ZIO.fail(s"No leagues found for system: $systemName").when(leagues.isEmpty)
      sorted = leagues.filter(_.tier.nonEmpty).sortBy(_.tier.get)
      _ <- ZIO.fail("All leagues in system must have tier set").when(sorted.size != leagues.size)
      _ <- ZIO.fail("Season not finished in all leagues").when(
        sorted.exists(l => l.currentMatchday < l.totalMatchdays)
      )
      _ <- ZIO.foreachDiscard(0 until (sorted.size - 1)) { idx =>
        val upperLeague = sorted(idx)
        val lowerLeague = sorted(idx + 1)
        for {
          upperTable <- getTable(upperLeague.id)
          lowerTable <- getTable(lowerLeague.id)
          numPromoted = 3
          relegated = upperTable.takeRight(numPromoted).map(r => TeamId(r.teamId))
          promoted = lowerTable.take(numPromoted).map(r => TeamId(r.teamId))
          _ <- ZIO.fail(s"Not enough teams for promotion/relegation: ${upperLeague.name} / ${lowerLeague.name}").when(
            relegated.size < numPromoted || promoted.size < numPromoted
          )
          _ <- {
            import cats.implicits.*
            (for {
              _ <- relegated.traverse_(tid => teamRepo.updateLeagueId(tid, lowerLeague.id))
              _ <- promoted.traverse_(tid => teamRepo.updateLeagueId(tid, upperLeague.id))
            } yield ()).transact(xa).orDie
          }
        } yield ()
      }
      _ <- ZIO.logInfo(s"Promotion/relegation applied for system: $systemName")
    } yield ()

  def startNextSeasonForSystem(systemName: String, userId: UserId): ZIO[Any, String, Unit] =
    for {
      leagues <- leagueRepo.listByLeagueSystemName(systemName).transact(xa).orDie
      _ <- ZIO.fail(s"No leagues found for system: $systemName").when(leagues.isEmpty)
      _ <- ZIO.fail("Season not finished in all leagues").when(
        leagues.exists(l => l.currentMatchday < l.totalMatchdays)
      )
      _ <- ZIO.foreachDiscard(leagues) { league =>
        val rng = new Random(league.id.value.hashCode)
        for {
          teams <- teamRepo.listByLeague(league.id).transact(xa).orDie
          _ <- ZIO.fail(s"League ${league.name}: teams count mismatch").when(teams.size != league.teamCount)
          referees <- refereeRepo.listByLeague(league.id).transact(xa).orDie
          _ <- {
            import cats.implicits.*
            (for {
              _ <- leaguePlayerMatchStatsRepo.deleteByLeague(league.id)
              _ <- matchRepo.deleteByLeague(league.id)
              _ <- transferOfferRepo.deleteByLeague(league.id)
              _ <- transferWindowRepo.deleteByLeague(league.id)
            } yield ()).transact(xa).orDie
          }
          startDate = nextWedOrSat(LocalDate.now(league.timezone))
          teamIds = teams.map(_.id)
          matches = FixtureGenerator.generate(league.id, teamIds, referees, startDate, league.timezone, rng)
          transferWindowIds <- ZIO.foreach((2 to (league.totalMatchdays - 2) by 2).toList)(_ => IdGen.genTransferWindowId)
          transferWindows = transferWindowIds.zip((2 to (league.totalMatchdays - 2) by 2).toList).map { case (twId, k) =>
            TransferWindow(twId, league.id, k, k + 2, TransferWindowStatus.Closed)
          }
          _ <- {
            import cats.implicits.*
            (for {
              _ <- matches.traverse_(m => matchRepo.create(m))
              _ <- transferWindows.traverse_(tw => transferWindowRepo.create(tw))
              _ <- leagueRepo.update(league.copy(
                currentMatchday = 0,
                startDate = Some(startDate),
                seasonPhase = SeasonPhase.InProgress
              ))
            } yield ()).transact(xa).orDie
          }
        } yield ()
      }
      _ <- ZIO.logInfo(s"Next season started for system: $systemName")
    } yield ()

  def createPlayOffSemiFinals(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit] =
    ensureUserHasAccessToLeague(userId, leagueId) *> (for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Baraże tylko dla lig tier 2–4").when(!league.tier.exists(t => t >= 2 && t <= 4))
      _ <- ZIO.fail("Sezon musi być zakończony").when(league.currentMatchday < league.totalMatchdays)
      existing <- matchRepo.listByLeagueAndMatchday(leagueId, league.totalMatchdays + 1).transact(xa).orDie
      _ <- ZIO.fail("Półfinały baraży już utworzone").when(existing.nonEmpty)
      table <- getTable(leagueId)
      _ <- ZIO.fail("Tabela musi mieć co najmniej 6 drużyn").when(table.size < 6)
      pos3 = TeamId(table(2).teamId)
      pos4 = TeamId(table(3).teamId)
      pos5 = TeamId(table(4).teamId)
      pos6 = TeamId(table(5).teamId)
      referees <- refereeRepo.listByLeague(leagueId).transact(xa).orDie
      _ <- ZIO.fail("Brak sędziów w lidze").when(referees.size < 2)
      semiDate = nextWedOrSat(LocalDate.now(league.timezone).plusDays(7))
      at17 = semiDate.atTime(java.time.LocalTime.of(17, 0)).atZone(league.timezone).toInstant
      id1 <- IdGen.genMatchId
      id2 <- IdGen.genMatchId
      m1 = Match(id1, leagueId, league.totalMatchdays + 1, pos3, pos6, at17, MatchStatus.Scheduled, None, None, referees(0).id, None)
      m2 = Match(id2, leagueId, league.totalMatchdays + 1, pos4, pos5, at17, MatchStatus.Scheduled, None, None, referees(1).id, None)
      _ <- (for { _ <- matchRepo.create(m1); _ <- matchRepo.create(m2) } yield ()).transact(xa).orDie
      _ <- ZIO.logInfo(s"Play-off semi-finals created for league ${league.name}")
    } yield ()).tapError(e => ZIO.logWarning(s"createPlayOffSemiFinals: $e"))

  def createPlayOffFinal(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit] =
    ensureUserHasAccessToLeague(userId, leagueId) *> (for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Baraże tylko dla lig tier 2–4").when(!league.tier.exists(t => t >= 2 && t <= 4))
      semiMatches <- matchRepo.listByLeagueAndMatchday(leagueId, league.totalMatchdays + 1).transact(xa).orDie
      _ <- ZIO.fail("Najpierw rozegraj półfinały baraży").when(semiMatches.size != 2)
      _ <- ZIO.fail("Oba półfinały muszą być rozegrane").when(semiMatches.exists(_.status != MatchStatus.Played))
      winners = semiMatches.flatMap { m =>
        val (h, a) = (m.homeGoals.getOrElse(0), m.awayGoals.getOrElse(0))
        if (h > a) Some(m.homeTeamId) else if (a > h) Some(m.awayTeamId) else None
      }
      _ <- ZIO.fail("Oba półfinały muszą mieć zwycięzcę (bez remisów)").when(winners.size != 2)
      existingFinal <- matchRepo.listByLeagueAndMatchday(leagueId, league.totalMatchdays + 2).transact(xa).orDie
      _ <- ZIO.fail("Finał baraży już utworzony").when(existingFinal.nonEmpty)
      refs <- refereeRepo.listByLeague(leagueId).transact(xa).orDie
      _ <- ZIO.fail("Brak sędziego").when(refs.isEmpty)
      finalDate = nextWedOrSat(LocalDate.now(league.timezone).plusDays(14))
      at17 = finalDate.atTime(java.time.LocalTime.of(17, 0)).atZone(league.timezone).toInstant
      id <- IdGen.genMatchId
      finalMatch = Match(id, leagueId, league.totalMatchdays + 2, winners(0), winners(1), at17, MatchStatus.Scheduled, None, None, refs(0).id, None)
      _ <- matchRepo.create(finalMatch).transact(xa).orDie
      _ <- ZIO.logInfo(s"Play-off final created for league ${league.name}")
    } yield ()).tapError(e => ZIO.logWarning(s"createPlayOffFinal: $e"))

  def getById(leagueId: LeagueId): ZIO[Any, String, LeagueDto] =
    leagueRepo.findById(leagueId).transact(xa).orDie.flatMap {
      case None => ZIO.fail("League not found")
      case Some(l) => ZIO.succeed(toLeagueDto(l))
    }

  /** Fails with "Forbidden" if user has no team in the league. */
  private def ensureUserHasAccessToLeague(userId: UserId, leagueId: LeagueId): ZIO[Any, String, Unit] =
    sql"SELECT 1 FROM teams WHERE league_id = ${leagueId.value} AND owner_user_id = ${userId.value} LIMIT 1"
      .query[Int].option.transact(xa).orDie.flatMap {
        case Some(_) => ZIO.unit
        case None    => ZIO.fail("Forbidden")
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
      token = IdGen.token
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

  def acceptInvitation(token: String, teamName: String, userId: UserId): ZIO[Any, String, (LeagueDto, TeamDto)] = {
    val me = MonadError[ConnectionIO, Throwable]
    def require[A](opt: Option[A], msg: String): ConnectionIO[A] =
      opt.fold(me.raiseError[A](new RuntimeException(msg)))(me.pure(_))
    def guard(cond: Boolean, msg: String): ConnectionIO[Unit] =
      if (cond) me.raiseError(new RuntimeException(msg)) else me.pure(())

    for {
      teamId <- IdGen.genTeamId
      result <- (for {
        invOpt    <- invitationRepo.findByToken(token)
        inv       <- require(invOpt, "Invalid or expired invitation token")
        _         <- guard(inv.status != InvitationStatus.Pending, "This invitation was already used")
        _         <- guard(inv.expiresAt.isBefore(Instant.now()), "Invitation has expired")
        _         <- guard(inv.invitedUserId != userId, "This invitation was sent to another user")
        leagueOpt <- leagueRepo.findById(inv.leagueId)
        league    <- require(leagueOpt, "League not found")
        _         <- guard(league.seasonPhase != SeasonPhase.Setup, "League is no longer in Setup")
        count     <- teamRepo.countByLeague(inv.leagueId)
        _         <- guard(count >= league.teamCount, "League has no free slots")
        team = Team(
          id = teamId, leagueId = inv.leagueId, name = teamName,
          ownerType = TeamOwnerType.Human, ownerUserId = Some(userId), ownerBotId = None,
          budget = BigDecimal(1000000), defaultGamePlanId = None,
          createdAt = Instant.now(), managerName = None
        )
        _         <- teamRepo.create(team)
        _         <- invitationRepo.update(inv.copy(status = InvitationStatus.Accepted))
      } yield (toLeagueDto(league), toTeamDto(team))).transact(xa).mapError(e => Option(e.getMessage).getOrElse("Accept invitation failed"))
    } yield result
  }

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
      allSquads = teams.flatMap(team => PlayerGenerator.generateSquad(team.id, rng))
      numReferees = league.teamCount / 2
      refereeIds <- ZIO.foreach(List.fill(numReferees)(()))(_ => IdGen.genRefereeId)
      referees = refereeIds.zipWithIndex.map { case (refId, i) =>
        Referee(refId, leagueId, s"Referee ${i + 1}", 0.3 + rng.nextDouble() * 0.5)
      }
      teamIds = teams.map(_.id)
      matches = FixtureGenerator.generate(leagueId, teamIds, referees, startDate, league.timezone, rng)
      transferWindowIds <- ZIO.foreach((2 to (league.totalMatchdays - 2) by 2).toList)(_ => IdGen.genTransferWindowId)
      transferWindows = transferWindowIds.zip((2 to (league.totalMatchdays - 2) by 2).toList).map { case (twId, k) =>
        TransferWindow(twId, leagueId, k, k + 2, TransferWindowStatus.Closed)
      }
      positionStats = LeagueContextComputer.computePositionStats(allSquads)
      leagueContextId <- IdGen.genLeagueContextId
      ctx = LeagueContext(leagueContextId, leagueId, positionStats, Instant.now())
      updatedLeague = league.copy(startDate = Some(startDate), seasonPhase = SeasonPhase.InProgress)
      _ <- {
        import cats.implicits.*
        (for {
          _ <- allSquads.traverse_(p => playerRepo.create(p))
          _ <- teams.traverse_ { t =>
            val squad = allSquads.filter(_.teamId == t.id)
            val contracts = PlayerGenerator.generateInitialContracts(squad, t.id, league.totalMatchdays)
            contracts.traverse_(c => contractRepo.create(c))
          }
          _ <- referees.traverse_(r => refereeRepo.create(r))
          _ <- matches.traverse_(m => matchRepo.create(m))
          _ <- transferWindows.traverse_(tw => transferWindowRepo.create(tw))
          _ <- leagueContextRepo.create(ctx)
          _ <- leagueRepo.update(updatedLeague)
        } yield ()).transact(xa).orDie
      }
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
      botTeams <- ZIO.foreach(0 until count) { i =>
        for {
          teamId <- IdGen.genTeamId
          botId <- IdGen.genBotId
        } yield {
          val seed = (leagueId.value.hashCode.toLong << 32) | (i.toLong & 0xFFFFFFFFL)
          val rnd = new Random(seed)
          val budgetAmount = 500_000 + rnd.nextInt(1_000_001)
          val baseIdx = (current + i) % botTeamNamePresets.size
          val suffix = if (current + i >= botTeamNamePresets.size) s" ${(current + i) / botTeamNamePresets.size + 1}" else ""
          Team(
            id = teamId,
            leagueId = leagueId,
            name = botTeamNamePresets(baseIdx) + suffix,
            ownerType = TeamOwnerType.Bot,
            ownerUserId = None,
            ownerBotId = Some(botId),
            budget = BigDecimal(budgetAmount),
            defaultGamePlanId = None,
            createdAt = Instant.now(),
            managerName = Some(botManagerNamePresets((current + i) % botManagerNamePresets.size) + suffix)
          )
        }
      }
      _ <- {
        import cats.implicits.*
        import cats.MonadError
        val me = MonadError[doobie.ConnectionIO, Throwable]
        (for {
          recheck <- teamRepo.countByLeague(leagueId)
          _ <- if (recheck + count > league.teamCount) me.raiseError[Unit](new RuntimeException(s"Race: only ${league.teamCount - recheck} free slot(s)"))
               else me.pure(())
          _ <- botTeams.toList.traverse_(t => teamRepo.create(t))
        } yield ()).transact(xa).mapError(e => Option(e.getMessage).getOrElse("Failed to add bots"))
      }
      _ <- ZIO.logInfo(s"Added $count bot(s) to league=${league.name} (now ${current + count}/${league.teamCount} teams)")
    } yield ()).tapError(err => ZIO.logWarning(s"Add bots failed: $err"))

  def playMatchday(leagueId: LeagueId, userId: UserId): ZIO[Any, String, Unit] =
    withMatchdayLock(leagueId)(for {
      leagueOpt <- leagueRepo.findById(leagueId).transact(xa).orDie
      league <- ZIO.fromOption(leagueOpt).orElseFail("League not found")
      _ <- ZIO.fail("Only league creator can play matchday").when(league.createdByUserId != userId)
      nextMd = league.currentMatchday + 1
      isPlayOffRound = league.tier.exists(t => t >= 2 && t <= 4) && nextMd >= league.totalMatchdays + 1 && nextMd <= league.totalMatchdays + 2
      _ <- ZIO.fail("League must be InProgress (or play-off round)").when(
        league.seasonPhase != SeasonPhase.InProgress && !(league.seasonPhase == SeasonPhase.Finished && isPlayOffRound)
      )
      _ <- ZIO.fail("Season finished").when(nextMd > league.totalMatchdays && !isPlayOffRound)
      matches <- matchRepo.listByLeagueAndMatchday(leagueId, nextMd).transact(xa).orDie
      toPlay = matches.filter(_.status == MatchStatus.Scheduled)
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
      ))),
      p.defenseFormationName,
      p.defenseCustomPositions.flatMap(parseCustomPositions)
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
    oppositionInstructions: Option[List[OppositionInstructionParsed]] = None,
    defenseFormationName: Option[String] = None,
    defenseCustomPositions: Option[List[List[Double]]] = None
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
        val highlights = MatchHighlights.extract(result.events, m.homeTeamId, m.awayTeamId).map(_.toMap)
        val summary = result.analytics.fold(baseSummary.copy(highlights = Some(highlights)))(a => baseSummary.copy(
          vaepTotal = Some(a.vaepTotal),
          wpaFinal = Some(a.wpaFinal),
          fieldTilt = a.fieldTilt,
          ppda = a.ppda,
          ballTortuosity = a.ballTortuosity,
          metabolicLoad = Some(a.metabolicLoad),
          xtByZone = Some((1 to PitchModel.TotalZones).map(z => a.xtValueByZone.getOrElse(z, 0.0)).toList),
          homeShareByZone = Some((1 to PitchModel.TotalZones).map(z => a.homeShareByZone.getOrElse(z, 0.5)).toList),
          vaepBreakdownByPlayer = Some(a.vaepByPlayerByEventType.map { case (pid, m) => pid.value -> m }),
          pressingByPlayer = Some(a.defensiveActionsByPlayer.map { case (pid, n) => pid.value -> n }),
          estimatedDistanceByPlayer = Some(a.estimatedDistanceByPlayer.map { case (pid, d) => pid.value -> d }),
          influenceByPlayer = Some(a.playerActivityByZone.map { case (pid, m) => pid.value -> m.map { case (z, c) => z.toString -> c } }),
          avgDefendersInConeByZone = Some((1 to PitchModel.TotalZones).map(z => a.shotContextByZone.get(z).map(_._1).getOrElse(0.0)).toList),
          avgGkDistanceByZone = Some((1 to PitchModel.TotalZones).map(z => a.shotContextByZone.get(z).map(_._2).getOrElse(0.0)).toList),
          setPieceZoneActivity = Some(a.setPieceZoneActivity.view.mapValues(m => (1 to PitchModel.TotalZones).map(z => m.getOrElse(z, 0)).toList).toMap),
          pressingInOppHalfByPlayer = Some(a.pressingInOppHalfByPlayer.map { case (pid, n) => pid.value -> n }),
          playerTortuosityByPlayer = Some(a.playerTortuosityByPlayer.map { case (pid, d) => pid.value -> d }),
          metabolicLoadByPlayer = Some(a.metabolicLoadByPlayer.map { case (pid, d) => pid.value -> d }),
          iwpByPlayer = Some(a.iwpByPlayer.map { case (pid, d) => pid.value -> d }),
          setPiecePatternW = Some(a.setPiecePatternW),
          setPiecePatternH = Some(a.setPiecePatternH.map(hMap => hMap.map { case (k, v) => k.toString -> v }.toMap)),
          setPieceRoutineCluster = Some(a.setPieceRoutineCluster),
          poissonPrognosis = a.poissonPrognosis,
          voronoiCentroidByZone = Some((1 to PitchModel.TotalZones).map(z => a.voronoiCentroidByZone.getOrElse(z, 0.5)).toList),
          passValueByPlayer = Some(a.passValueByPlayer.map { case (pid, d) => pid.value -> d }),
          passValueTotal = Some(a.passValueTotal),
          passValueUnderPressureTotal = Some(a.passValueUnderPressureTotal),
          passValueUnderPressureByPlayer = Some(a.passValueUnderPressureByPlayer.map { case (pid, d) => pid.value -> d }),
          influenceScoreByPlayer = Some(a.influenceScoreByPlayer.map { case (pid, d) => pid.value -> d })
        ).copy(highlights = Some(highlights)))
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
        val played = homeLineupIds.contains(p.id)
        val newFreshness = if (played) (p.freshness - 0.25).max(0.0) else p.freshness
        val newMorale = (p.morale + homeDelta).max(0.0).min(1.0)
        val newCondition = if (played) (p.condition - 0.12).max(0.0) else p.condition
        val newSharpness = if (played) (p.matchSharpness + 0.03).min(1.0) else (p.matchSharpness - 0.01).max(0.0)
        for {
          _ <- playerRepo.updateFreshnessMorale(p.id, newFreshness, newMorale)
          _ <- playerRepo.updateConditionSharpness(p.id, newCondition, newSharpness)
        } yield ()
      }
      _ <- traverseConn(awayPlayers) { p =>
        val played = awayLineupIds.contains(p.id)
        val newFreshness = if (played) (p.freshness - 0.25).max(0.0) else p.freshness
        val newMorale = (p.morale + awayDelta).max(0.0).min(1.0)
        val newCondition = if (played) (p.condition - 0.12).max(0.0) else p.condition
        val newSharpness = if (played) (p.matchSharpness + 0.03).min(1.0) else (p.matchSharpness - 0.01).max(0.0)
        for {
          _ <- playerRepo.updateFreshnessMorale(p.id, newFreshness, newMorale)
          _ <- playerRepo.updateConditionSharpness(p.id, newCondition, newSharpness)
        } yield ()
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
            val newCondition = (p.condition + 0.18).min(1.0).max(0.0)
            val clearInjury = p.injury.exists(_.returnAtMatchday <= currentMatchday)
            for {
              _ <- playerRepo.updateFreshnessMorale(p.id, newFreshness, p.morale)
              _ <- playerRepo.updateConditionSharpness(p.id, newCondition, p.matchSharpness)
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
        ZIO.foreachDiscard(humanTeams) { humanTeam =>
          val alreadyHasBotOffersForTeam = windowOffers.exists(o => botIds.contains(o.fromTeamId) && o.toTeamId == humanTeam.id)
          if (alreadyHasBotOffersForTeam) ZIO.unit
          else {
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
      lineupIds = ordered.map(_._1.id).toSet
      bench = all.filter(p => !lineupIds.contains(p.id)).take(7).map(p => PlayerMatchInput(p, p.freshness, p.morale, recentMinutes.get(p.id)))
      players = ordered.map { case (p, slot) => PlayerMatchInput(p, p.freshness, p.morale, recentMinutes.get(p.id)) } ++ bench
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
    val pressHome = (1 to PitchModel.TotalZones).map { z =>
      events.count(e => e.zone.contains(z) && defensiveTypes.contains(e.eventType) && e.teamId.exists(_.value == homeTeamId))
    }.toList
    val pressAway = (1 to PitchModel.TotalZones).map { z =>
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

  private val formationTacticalNotes: Map[String, (List[String], List[String], List[String])] = Map(
    "4-4-2" -> (
      List("Solidna obrona z dwoma blisko siebie stoperami", "Dobre pokrycie skrzydeł", "Prosta struktura defensywna"),
      List("Brak dodatkowego gracza w środku pola", "Wolne strefy między liniami", "Skrzydłowi muszą pokrywać dużo terenu"),
      List("Graj z trójką pomocników, aby uzyskać przewagę w środku", "Wykorzystaj strefy 14–15 (między liniami)", "Szybkie kombinacje przez środek")
    ),
    "4-3-3" -> (
      List("Silny pressing wysoki z trzema napastnikami", "Dominacja w posiadaniu piłki", "Elastyczne skrzydła"),
      List("Odsłonięte skrzydła obrony przy pressingu", "Przestrzenie za linią obrony", "Kontrataki przez boki"),
      List("Graj niskim blokiem i kontratakuj", "Celuj w strefy 2, 5 (boczne, za wahadłowymi)", "Długie podania za linię obrony")
    ),
    "3-5-2" -> (
      List("Przewaga numeryczna w środku pola", "Silne wahadłowe pokrycie boków", "Dobra budowa z trzech z tyłu"),
      List("Odsłonięte boki przy wahadłowych wysoko", "Trójka obrońców wrażliwa na szybkich skrzydłowych", "Brak szerokości w ataku"),
      List("Wykorzystaj skrzydła (strefy 4, 10, 16, 22)", "Szybkie zmiany strony gry", "Crossing z boków do pola karnego")
    ),
    "4-2-3-1" -> (
      List("Kontrola środka pola przez dwóch defensywnych", "Kreatywny rozgrywający za napastnikiem", "Solidna defensywa"),
      List("Wolne strefy między '6' a '10'", "Skrzydłowi muszą wracać do obrony", "Jeden napastnik łatwy do zneutralizowania"),
      List("Pressing na rozgrywającego (strefa 14–15)", "Odetnij podania do '10'", "Graj bezpośrednio, omijając środek pola")
    ),
    "3-4-3" -> (
      List("Bardzo ofensywne ustawienie", "Szeroki atak z trzema napastnikami", "Dominacja na bokach"),
      List("Bardzo odsłonięta obrona", "Trójka stoperów wrażliwa na kontry", "Duże przestrzenie za wahadłowymi"),
      List("Kontrataki przez środek za linię obrony", "Celuj w przestrzenie między stoperami", "Defensywne ustawienie i cierpliwa gra")
    ),
    "5-3-2" -> (
      List("Bardzo solidna defensywa z pięcioma z tyłu", "Trudna do przełamania", "Dobra na kontrataki"),
      List("Mało kreatywności w ataku", "Brak szerokości", "Wolna budowa akcji"),
      List("Cierpliwa rozgrywka z dużą ilością podań", "Wymuszaj błędy wysokim pressingiem", "Crossing z daleka")
    ),
    "4-1-4-1" -> (
      List("Solidna defensywa z dodatkowym '6'", "Dobre pokrycie strefy środkowej", "Kontrola tempa gry"),
      List("Izolowany napastnik", "Wolna budowa z tyłu", "Brak wsparcia dla napastnika"),
      List("Pressing wysoki na samotnego '6'", "Szybkie podwójne podania za linię", "Celuj w przestrzenie za bocznym pomocnikami")
    ),
    "4-5-1" -> (
      List("Bardzo defensywna formacja", "Pięciu pomocników kontroluje środek", "Trudna do przełamania"),
      List("Jeden napastnik - łatwy do ubezpieczenia", "Mało opcji ofensywnych", "Zależność od kontrataków"),
      List("Wymuszaj błędy pressingiem", "Crossing z boków", "Zmiana stron gry wymusza przesunięcia")
    ),
    "4-4-1-1" -> (
      List("Dodatkowy gracz między liniami", "Elastyczne przejście obrona-atak", "Solidna obrona"),
      List("Wolne strefy na bokach", "Napastnik izolowany", "Brak szerokości"),
      List("Graj szeroko, rozciągaj obronę", "Celuj w strefy 4, 10 (boki)", "Presuj gracza 'za napastnikiem'")
    ),
    "3-4-1-2" -> (
      List("Silny środek pola", "Kreatywny rozgrywający", "Dwa napastnikii w polu karnym"),
      List("Odsłonięte boki", "Trzy z tyłu wrażliwe na crossing", "Wahadłowi muszą wracać daleko"),
      List("Wykorzystaj boki (strefy 4, 10, 16, 22)", "Dośrodkowania z głębi", "Presuj wahadłowych wysoko")
    )
  )

  private def analyzeOpponentPlayers(players: List[Player]): (List[String], List[String], List[String]) = {
    if (players.isEmpty) return (Nil, Nil, Nil)

    val outfield = players.filter(_.preferredPositions != Set("GK"))
    val gks = players.filter(_.preferredPositions == Set("GK"))
    val avgPace = outfield.flatMap(_.physical.get("pace")).sum.toDouble / math.max(1, outfield.size)
    val avgStamina = outfield.flatMap(_.physical.get("stamina")).sum.toDouble / math.max(1, outfield.size)
    val avgPassing = outfield.flatMap(_.technical.get("passing")).sum.toDouble / math.max(1, outfield.size)
    val avgShooting = outfield.flatMap(_.technical.get("shooting")).sum.toDouble / math.max(1, outfield.size)
    val avgTackling = outfield.flatMap(_.technical.get("tackling")).sum.toDouble / math.max(1, outfield.size)
    val avgComposure = outfield.flatMap(_.mental.get("composure")).sum.toDouble / math.max(1, outfield.size)
    val gkOverall = gks.headOption.map(PlayerOverall.overall).getOrElse(10.0)

    val strengths = scala.collection.mutable.ListBuffer[String]()
    val weaknesses = scala.collection.mutable.ListBuffer[String]()

    if (avgPace >= 14) strengths += s"Szybki zespół (śr. szybkość ${f"$avgPace%.1f"})"
    else if (avgPace <= 10) weaknesses += s"Wolny zespół (śr. szybkość ${f"$avgPace%.1f"})"

    if (avgPassing >= 14) strengths += s"Dobra gra podaniami (śr. podania ${f"$avgPassing%.1f"})"
    else if (avgPassing <= 10) weaknesses += s"Słaba gra podaniami (śr. podania ${f"$avgPassing%.1f"})"

    if (avgShooting >= 14) strengths += s"Silni w wykończeniu (śr. strzały ${f"$avgShooting%.1f"})"
    else if (avgShooting <= 10) weaknesses += s"Słabi w wykończeniu (śr. strzały ${f"$avgShooting%.1f"})"

    if (avgTackling >= 14) strengths += s"Agresywna defensywa (śr. odbiór ${f"$avgTackling%.1f"})"
    else if (avgTackling <= 10) weaknesses += s"Słaba defensywa (śr. odbiór ${f"$avgTackling%.1f"})"

    if (avgStamina <= 10) weaknesses += s"Niska wytrzymałość (śr. ${f"$avgStamina%.1f"}) — późne minuty to szansa"
    if (avgComposure <= 10) weaknesses += "Niska opanowanie — presja może wymusić błędy"
    if (gkOverall <= 10) weaknesses += s"Słaby bramkarz (overall ${f"$gkOverall%.1f"}) — strzały z dystansu mogą być skuteczne"
    else if (gkOverall >= 15) strengths += s"Silny bramkarz (overall ${f"$gkOverall%.1f"})"

    val keyPlayers = outfield
      .map(p => (p, PlayerOverall.overall(p)))
      .sortBy(-_._2)
      .take(3)
      .map { case (p, ov) => s"${p.firstName} ${p.lastName} (${p.preferredPositions.mkString("/")} — overall ${f"$ov%.1f"})" }

    (strengths.toList, weaknesses.toList, keyPlayers)
  }

  private def suggestFormationsAgainst(formation: String, oppWeaknesses: List[String]): List[String] = {
    val base = formation match {
      case f if f.contains("4-4-2") => List("4-3-3", "4-2-3-1")
      case f if f.contains("4-3-3") => List("4-4-2", "4-5-1", "5-3-2")
      case f if f.contains("3-5-2") => List("4-3-3", "4-2-3-1")
      case f if f.contains("4-2-3-1") => List("4-3-3", "3-5-2")
      case f if f.contains("3-4-3") => List("5-3-2", "4-4-2", "4-5-1")
      case f if f.contains("5-3-2") => List("4-3-3", "3-4-3")
      case f if f.contains("4-1-4-1") => List("4-3-3", "3-4-3")
      case f if f.contains("4-5-1") => List("4-3-3", "3-4-3")
      case f if f.contains("4-4-1-1") => List("4-3-3", "3-5-2")
      case f if f.contains("3-4-1-2") => List("4-3-3", "4-4-2")
      case _ => List("4-3-3", "4-4-2")
    }
    val hasWeakDefense = oppWeaknesses.exists(w => w.contains("defensyw") || w.contains("odbiór"))
    if (hasWeakDefense) (base :+ "3-4-3").distinct.take(3) else base.take(3)
  }

  def getAssistantTipForUser(matchId: MatchId, teamId: TeamId, userId: UserId): ZIO[Any, String, AssistantTipDto] =
    for {
      m <- getMatch(matchId)
      _ <- ensureUserHasAccessToLeague(userId, LeagueId(m.leagueId))
      squads <- matchSquadRepo.listByMatch(matchId).transact(xa).orDie
      opponentTeamId = if (TeamId(m.homeTeamId) == teamId) TeamId(m.awayTeamId) else TeamId(m.homeTeamId)
      opponentSquad = squads.find(_.teamId != teamId)
      formation = opponentSquad.flatMap { s => val (plan, _) = parseGamePlan(s.gamePlanJson); Some(plan.formationName) }.getOrElse("4-3-3")
      allOpponentPlayers <- playerRepo.listByTeam(opponentTeamId).transact(xa).orDie
      startingXiIds = opponentSquad.map(_.lineup.map(_.playerId).toSet).getOrElse(Set.empty[PlayerId])
      opponentPlayers = if (startingXiIds.nonEmpty) allOpponentPlayers.filter(p => startingXiIds.contains(p.id)) else allOpponentPlayers
      (playerStrengths, playerWeaknesses, keyPlayers) = analyzeOpponentPlayers(opponentPlayers)
      (formStrengths, formWeaknesses, formTactics) = formationTacticalNotes.getOrElse(formation,
        (List("Niestandardowe ustawienie"), List("Nieprzewidywalna struktura"), List("Dostosuj pressing do obserwacji")))
      allStrengths = formStrengths ++ playerStrengths
      allWeaknesses = formWeaknesses ++ playerWeaknesses
      suggestedFormations = suggestFormationsAgainst(formation, allWeaknesses)
      tipLines = List(
        s"--- ANALIZA RYWALA: formacja $formation ---",
        "",
        "MOCNE STRONY RYWALA:",
        allStrengths.zipWithIndex.map { case (s, i) => s"  ${i + 1}. $s" }.mkString("\n"),
        "",
        "SŁABE STRONY RYWALA:",
        allWeaknesses.zipWithIndex.map { case (w, i) => s"  ${i + 1}. $w" }.mkString("\n"),
        "",
        "SUGESTIE TAKTYCZNE:",
        formTactics.zipWithIndex.map { case (t, i) => s"  ${i + 1}. $t" }.mkString("\n"),
        "",
        s"SUGEROWANE FORMACJE: ${suggestedFormations.mkString(", ")}",
        "",
        s"KLUCZOWI GRACZE DO OBSERWACJI: ${keyPlayers.mkString("; ")}"
      )
      tip = tipLines.mkString("\n")
    } yield AssistantTipDto(
      tip = tip,
      opponentFormation = Some(formation),
      opponentStrengths = Some(allStrengths),
      opponentWeaknesses = Some(allWeaknesses),
      tacticalSuggestions = Some(formTactics),
      keyPlayersToWatch = Some(keyPlayers),
      suggestedFormations = Some(suggestedFormations)
    )

  def getTeam(teamId: TeamId): ZIO[Any, String, TeamDto] =
    teamRepo.findById(teamId).transact(xa).orDie.flatMap {
      case None => ZIO.fail("Team not found")
      case Some(t) => ZIO.succeed(toTeamDto(t))
    }

  def getTeamForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, TeamDto] =
    getTeam(teamId).flatMap(dto => ensureUserHasAccessToLeague(userId, LeagueId(dto.leagueId)).as(dto))

  private def toPlayerDto(p: Player): PlayerDto = {
    val ov = PlayerOverall.overall(p)
    val phys = PlayerOverall.physicalAvg(p)
    val tech = PlayerOverall.technicalAvg(p)
    val ment = PlayerOverall.mentalAvg(p)
    val def_ = PlayerOverall.defenseAvg(p)
    PlayerDto(
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
      traits = p.traits,
      overall = BigDecimal(ov).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
      physicalAvg = BigDecimal(phys).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
      technicalAvg = BigDecimal(tech).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
      mentalAvg = BigDecimal(ment).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
      defenseAvg = BigDecimal(def_).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
      condition = p.condition.max(0).min(1),
      matchSharpness = p.matchSharpness.max(0).min(1)
    )
  }

  def getTeamPlayers(teamId: TeamId): ZIO[Any, String, List[PlayerDto]] =
    for {
      _ <- getTeam(teamId)
      players <- playerRepo.listByTeam(teamId).transact(xa).orDie
    } yield players.map(toPlayerDto)

  def getTeamPlayersForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[PlayerDto]] =
    getTeamForUser(teamId, userId) *> getTeamPlayers(teamId)

  def getTeamContractsForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ContractDto]] =
    getTeamForUser(teamId, userId) *>
      (for {
        contracts <- contractRepo.listByTeam(teamId).transact(xa).orDie
        dtos <- ZIO.foreach(contracts) { c =>
          playerRepo.findById(c.playerId).transact(xa).orDie.map {
            case Some(p) => ContractDto(
              c.id.value, c.playerId.value, c.teamId.value,
              s"${p.firstName} ${p.lastName}",
              c.weeklySalary.toDouble, c.startMatchday, c.endMatchday,
              c.releaseClause.map(_.toDouble)
            )
            case None => ContractDto(c.id.value, c.playerId.value, c.teamId.value, "?", c.weeklySalary.toDouble, c.startMatchday, c.endMatchday, c.releaseClause.map(_.toDouble))
          }
        }
      } yield dtos)

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
      allPlayers <- playerRepo.listByTeamIds(teams.map(_.id)).transact(xa).orDie
      playerNames = allPlayers.collect { case p if playerIds.contains(p.id) => p.id -> s"${p.firstName} ${p.lastName}" }.toMap
    } yield filtered.map(o => TransferOfferDto(
      o.id.value, o.windowId.value, o.fromTeamId.value, o.toTeamId.value, o.playerId.value,
      o.amount.toDouble, o.status.toString, o.createdAt.toEpochMilli, o.respondedAt.map(_.toEpochMilli),
      counterAmount = o.counterAmount.map(_.toDouble),
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
      _ <- ZIO.fail("Transfer window is not open").when(!windows.exists(w => w.id == windowId && w.status == TransferWindowStatus.Open))
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
            (for {
              _ <- playerRepo.updateTeamId(offer.playerId, offer.fromTeamId)
              _ <- teamRepo.update(fromTeam.copy(budget = fromTeam.budget - offer.amount))
              _ <- teamRepo.update(toTeam.copy(budget = toTeam.budget + offer.amount))
              _ <- transferOfferRepo.update(offer.copy(status = TransferOfferStatus.Accepted, respondedAt = Some(now)))
            } yield ()).transact(xa).orDie
          } else ZIO.unit
        } yield ok
      } else ZIO.succeed(false)
    } yield TransferOfferDto(offer.id.value, offer.windowId.value, offer.fromTeamId.value, offer.toTeamId.value, offer.playerId.value, offer.amount.toDouble, if (accepted) TransferOfferStatus.Accepted.toString else offer.status.toString, offer.createdAt.toEpochMilli, if (accepted) Some(now.toEpochMilli) else None, counterAmount = offer.counterAmount.map(_.toDouble), fromTeamName = Some(fromTeam.name), toTeamName = Some(toTeam.name), playerName = Some(s"${player.firstName} ${player.lastName}"))

  def acceptTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit] = {
    val me = MonadError[ConnectionIO, Throwable]
    def require[A](opt: Option[A], msg: String): ConnectionIO[A] =
      opt.fold(me.raiseError[A](new RuntimeException(msg)))(me.pure(_))
    def guard(cond: Boolean, msg: String): ConnectionIO[Unit] =
      if (cond) me.raiseError(new RuntimeException(msg)) else me.pure(())

    val txn = for {
      opt       <- transferOfferRepo.findById(offerId)
      offer     <- require(opt, "Offer not found")
      _         <- guard(offer.status != TransferOfferStatus.Pending, "Offer already responded")
      fromTOpt  <- teamRepo.findById(offer.fromTeamId)
      fromTeam  <- require(fromTOpt, "From team not found")
      toTOpt    <- teamRepo.findById(offer.toTeamId)
      toTeam    <- require(toTOpt, "To team not found")
      _         <- guard(!toTeam.ownerUserId.contains(userId), "Only the selling team owner can accept")
      fromCount <- playerRepo.countByTeam(offer.fromTeamId)
      toCount   <- playerRepo.countByTeam(offer.toTeamId)
      _         <- guard(toCount <= 16, "Selling would leave fewer than 16 players")
      _         <- guard(fromCount >= 20, "Buyer would exceed 20 players")
      _         <- guard(fromTeam.budget < offer.amount, "Insufficient budget")
      now        = Instant.now()
      _         <- playerRepo.updateTeamId(offer.playerId, offer.fromTeamId)
      _         <- teamRepo.update(fromTeam.copy(budget = fromTeam.budget - offer.amount))
      _         <- teamRepo.update(toTeam.copy(budget = toTeam.budget + offer.amount))
      _         <- transferOfferRepo.update(offer.copy(status = TransferOfferStatus.Accepted, respondedAt = Some(now)))
    } yield offer
    txn.transact(xa).mapError(e => Option(e.getMessage).getOrElse("Transfer failed")).flatMap { offer =>
      ZIO.logInfo(s"Transfer accepted: offerId=${offerId.value.take(8)} player=${offer.playerId.value.take(8)} amount=${offer.amount} from=${offer.fromTeamId.value.take(8)} to=${offer.toTeamId.value.take(8)}")
    }
  }

  def rejectTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit] = {
    val me = MonadError[ConnectionIO, Throwable]
    def require[A](opt: Option[A], msg: String): ConnectionIO[A] =
      opt.fold(me.raiseError[A](new RuntimeException(msg)))(me.pure(_))
    def guard(cond: Boolean, msg: String): ConnectionIO[Unit] =
      if (cond) me.raiseError(new RuntimeException(msg)) else me.pure(())

    val txn = for {
      opt    <- transferOfferRepo.findById(offerId)
      offer  <- require(opt, "Offer not found")
      _      <- guard(offer.status != TransferOfferStatus.Pending, "Offer already responded")
      toTOpt <- teamRepo.findById(offer.toTeamId)
      toTeam <- require(toTOpt, "To team not found")
      _      <- guard(!toTeam.ownerUserId.contains(userId), "Only the selling team owner can reject")
      now     = Instant.now()
      _      <- transferOfferRepo.update(offer.copy(status = TransferOfferStatus.Rejected, respondedAt = Some(now)))
    } yield ()
    txn.transact(xa).mapError(e => Option(e.getMessage).getOrElse("Reject failed"))
  }

  def counterTransferOffer(offerId: TransferOfferId, userId: UserId, counterAmount: BigDecimal): ZIO[Any, String, TransferOfferDto] = {
    val me = MonadError[ConnectionIO, Throwable]
    def require[A](opt: Option[A], msg: String): ConnectionIO[A] =
      opt.fold(me.raiseError[A](new RuntimeException(msg)))(me.pure(_))
    def guard(cond: Boolean, msg: String): ConnectionIO[Unit] =
      if (cond) me.raiseError(new RuntimeException(msg)) else me.pure(())

    ZIO.fail("Counter amount must be positive").when(counterAmount <= 0) *> {
      val txn = for {
        opt      <- transferOfferRepo.findById(offerId)
        offer    <- require(opt, "Offer not found")
        _        <- guard(offer.status != TransferOfferStatus.Pending, "Offer already responded")
        toTOpt   <- teamRepo.findById(offer.toTeamId)
        toTeam   <- require(toTOpt, "To team not found")
        _        <- guard(!toTeam.ownerUserId.contains(userId), "Only the selling team owner can counter")
        fromTOpt <- teamRepo.findById(offer.fromTeamId)
        fromTeam <- require(fromTOpt, "From team not found")
        pOpt     <- playerRepo.findById(offer.playerId)
        player   <- require(pOpt, "Player not found")
        updated   = offer.copy(status = TransferOfferStatus.Countered, counterAmount = Some(counterAmount))
        _        <- transferOfferRepo.update(updated)
      } yield (updated, fromTeam, toTeam, player)
      txn.transact(xa).mapError(e => Option(e.getMessage).getOrElse("Counter failed")).map { case (updated, fromTeam, toTeam, player) =>
        TransferOfferDto(
          updated.id.value, updated.windowId.value, updated.fromTeamId.value, updated.toTeamId.value, updated.playerId.value,
          updated.amount.toDouble, updated.status.toString, updated.createdAt.toEpochMilli, None,
          counterAmount = Some(counterAmount.toDouble),
          fromTeamName = Some(fromTeam.name),
          toTeamName = Some(toTeam.name),
          playerName = Some(s"${player.firstName} ${player.lastName}")
        )
      }
    }
  }

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
      preferredPositions = req.preferredPositions.fold(player.preferredPositions)(_.toSet.filter(_.nonEmpty))
      _ <- playerRepo.updatePreferredPositions(playerId, preferredPositions).transact(xa).orDie.when(preferredPositions != player.preferredPositions)
      clampAttr = (m: Map[String, Int]) => m.view.mapValues(v => math.max(1, math.min(20, v))).toMap
      physical = req.physical.fold(player.physical)(clampAttr)
      technical = req.technical.fold(player.technical)(clampAttr)
      mental = req.mental.fold(player.mental)(clampAttr)
      _ <- playerRepo.updateAttributes(playerId, physical, technical, mental).transact(xa).orDie.when(req.physical.isDefined || req.technical.isDefined || req.mental.isDefined)
      updated = player.copy(firstName = firstName, lastName = lastName, preferredPositions = preferredPositions, physical = physical, technical = technical, mental = mental)
    } yield toPlayerDto(updated)

  def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): ZIO[Any, String, GamePlanSnapshotDto] =
    for {
      _ <- ZIO.fail("Game plan JSON too large (max 20 KB)").when(gamePlanJson.length > 20 * 1024)
      _ <- ZIO.fail("Game plan name must be 1-100 characters").when(name.trim.isEmpty || name.trim.length > 100)
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
                case Some(md) if league.currentMatchday < md =>
                  val overdue = md - league.currentMatchday
                  ZIO.foreachDiscard(1 to overdue.min(5))(_ =>
                    playMatchday(league.id, league.createdByUserId).tapError(e => ZIO.logError(s"Scheduled matchday failed for league=${league.id.value.take(8)}: $e")).ignore
                  )
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
      leagueIdOpt.fold(ZIO.fail("leagueId is required when using filters"): ZIO[Any, String, List[MatchId]]) { lid =>
        ensureUserHasAccessToLeague(userId, lid) *>
          matchRepo.listByLeague(lid).transact(xa).orDie.map { matches =>
            val filtered = matches.filter { m =>
              val mdOk = fromMatchdayOpt.forall(m.matchday >= _) && toMatchdayOpt.forall(m.matchday <= _)
              val teamOk = teamIdOpt.forall(tid => m.homeTeamId == tid || m.awayTeamId == tid)
              mdOk && teamOk
            }
            filtered.sortBy(_.matchday).take(ExportMatchIdsMax).map(_.id)
          }
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
            val onTarget = if (e.eventType == "Goal" || e.outcome.contains("Saved") || e.outcome.contains("Success")) 1 else 0
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
          .take(math.min(limit, 50))
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
        dto1 = toPlayerDto(p1)
        dto2 = toPlayerDto(p2)
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
      team <- getTeamForUser(teamId, userId)
      _ <- ZIO.fail("Forbidden: you do not own this team").when(!team.ownerUserId.contains(userId.value))
      now = Instant.now()
      _ <- shortlistRepo.add(teamId, playerId, now).transact(xa).orDie
    } yield ()

  def removeFromShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit] =
    for {
      team <- getTeamForUser(teamId, userId)
      _ <- ZIO.fail("Forbidden: you do not own this team").when(!team.ownerUserId.contains(userId.value))
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
      id <- ZIO.succeed(IdGen.token)
      now = Instant.now()
      r = ScoutingReport(id, teamId, playerId, rating.max(0.0).min(10.0), notes.take(2000), now)
      _ <- scoutingReportRepo.create(r).transact(xa).orDie
      playerOpt <- playerRepo.findById(playerId).transact(xa).orDie
      pname = playerOpt.map(p => s"${p.firstName} ${p.lastName}").getOrElse(playerId.value)
    } yield ScoutingReportDto(r.id, r.teamId.value, r.playerId.value, pname, r.rating, r.notes, r.createdAt.toEpochMilli)

  def applyPressConference(matchId: MatchId, teamId: TeamId, userId: UserId, phase: String, tone: String): ZIO[Any, String, Unit] = {
    val pcKey = (matchId.value, teamId.value, phase.toLowerCase)
    for {
      _ <- ZIO.fail("Press conference already given for this phase").when(Option(pressConferenceGiven.putIfAbsent(pcKey, true)).isDefined)
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
      _       <- {
        import cats.implicits.*
        players.traverse_(p =>
          playerRepo.updateFreshnessMorale(p.id, p.freshness, (p.morale + delta).max(0.0).min(1.0))
        ).transact(xa).orDie
      }
      _       <- ZIO.logInfo(s"Press conference: match=${matchId.value.take(8)} team=${teamId.value.take(8)} phase=$phase tone=$tone moraleDelta=$delta")
    } yield ()
  }

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
    def nextMatchdayDate(matchdayIndex: Int): LocalDate = {
      if (matchdayIndex == 0) startDate
      else {
        val weekOffset = (matchdayIndex / 2) * 7
        val midWeek = (matchdayIndex % 2) * 3
        startDate.plusDays(weekOffset + midWeek)
      }
    }
    (0 until totalMatchdays).find(i => nextMatchdayDate(i) == date).map(_ + 1)
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
      l.createdByUserId.value, l.createdAt.toEpochMilli, l.timezone.getId,
      l.leagueSystemName, l.tier
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
      influenceScoreByPlayer = s.influenceScoreByPlayer,
      highlights = s.highlights
    )
}
