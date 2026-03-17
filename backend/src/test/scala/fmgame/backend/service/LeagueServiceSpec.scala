package fmgame.backend.service

import fmgame.backend.TestDbHelper
import fmgame.backend.engine.{EngineConfig, EngineModelFactory, FullMatchEngine}
import fmgame.backend.repository.*
import fmgame.shared.api.MatchPrognosisDto
import fmgame.shared.domain.*
import zio.*
import zio.test.*
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*

object LeagueServiceSpec extends ZIOSpecDefault {

  val testDbUrl = "jdbc:h2:mem:league_spec;DB_CLOSE_DELAY=-1"

  def spec = suite("LeagueService")(
    test("create league → add-bots → start season → GET fixtures → play matchday → GET table") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          implicit val r: zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", testDbUrl, props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        _      <- userSvc.register("creator@test.com", "pass1234", "Creator")
        login  <- userSvc.login("creator@test.com", "pass1234", "secret")
        creatorId = UserId(login.user.id)
        config <- EngineConfig.fromEnvZIO
        initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
        engineModelsRef <- Ref.make(initialModels)
        leagueSvc = LeagueServiceLive(
          LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
          PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
          MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
          TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
          TrainingPlanRepository.impl, ShortlistRepository.impl, ScoutingReportRepository.impl,
          LeaguePlayerMatchStatsRepository.impl,
          ContractRepository.impl,
          FullMatchEngine, engineModelsRef, xa
        )
        created     <- leagueSvc.create("Test League", 10, "My Team", "Europe/Warsaw", creatorId)
        league = created._1
        _            <- leagueSvc.addBots(LeagueId(league.id), creatorId, 9)
        _            <- leagueSvc.startSeason(LeagueId(league.id), creatorId, None)
        fixtures     <- leagueSvc.getFixtures(LeagueId(league.id))
        _            <- leagueSvc.playMatchday(LeagueId(league.id), creatorId)
        table        <- leagueSvc.getTable(LeagueId(league.id))
      } yield assertTrue(
        league.name == "Test League",
        league.teamCount == 10,
        fixtures.nonEmpty,
        fixtures.exists(_.matchday == 1),
        table.nonEmpty,
        table.size == 10,
        table.exists(_.played > 0)
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    },
    test("after play matchday, getMatchLog returns summary with advanced analytics fields") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          implicit val r: zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:league_log_spec;DB_CLOSE_DELAY=-1", props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        _      <- userSvc.register("u2@test.com", "pass1234", "U2")
        login  <- userSvc.login("u2@test.com", "pass1234", "secret")
        userId = UserId(login.user.id)
        config <- EngineConfig.fromEnvZIO
        initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
        engineModelsRef <- Ref.make(initialModels)
        leagueSvc = LeagueServiceLive(
          LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
          PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
          MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
          TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
          TrainingPlanRepository.impl, ShortlistRepository.impl, ScoutingReportRepository.impl,
          LeaguePlayerMatchStatsRepository.impl,
          ContractRepository.impl,
          FullMatchEngine, engineModelsRef, xa
        )
        created <- leagueSvc.create("Log Test League", 10, "My Team", "Europe/Warsaw", userId)
        _       <- leagueSvc.addBots(LeagueId(created._1.id), userId, 9)
        _       <- leagueSvc.startSeason(LeagueId(created._1.id), userId, None)
        fixtures <- leagueSvc.getFixtures(LeagueId(created._1.id))
        matchId  = fixtures.filter(_.matchday == 1).headOption.map(m => MatchId(m.id)).get
        _       <- leagueSvc.playMatchday(LeagueId(created._1.id), userId)
        log     <- leagueSvc.getMatchLog(matchId, Some(500), Some(0))
      } yield assertTrue(
        log.summary.isDefined,
        log.summary.get.possessionPercent.size >= 2,
        log.events.nonEmpty
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    },
    test("exportMatchLogs with format json-full returns events and full summary per match") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          implicit val r: zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:league_export_spec;DB_CLOSE_DELAY=-1", props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        _      <- userSvc.register("exp@test.com", "pass1234", "Exp")
        login  <- userSvc.login("exp@test.com", "pass1234", "secret")
        userId = UserId(login.user.id)
        config <- EngineConfig.fromEnvZIO
        initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
        engineModelsRef <- Ref.make(initialModels)
        leagueSvc = LeagueServiceLive(
          LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
          PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
          MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
          TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
          TrainingPlanRepository.impl, ShortlistRepository.impl, ScoutingReportRepository.impl,
          LeaguePlayerMatchStatsRepository.impl,
          ContractRepository.impl,
          FullMatchEngine, engineModelsRef, xa
        )
        created <- leagueSvc.create("Export League", 10, "My Team", "Europe/Warsaw", userId)
        _       <- leagueSvc.addBots(LeagueId(created._1.id), userId, 9)
        _       <- leagueSvc.startSeason(LeagueId(created._1.id), userId, None)
        fixtures <- leagueSvc.getFixtures(LeagueId(created._1.id))
        matchId  = fixtures.filter(_.matchday == 1).headOption.map(m => m.id).get
        _       <- leagueSvc.playMatchday(LeagueId(created._1.id), userId)
        out     <- leagueSvc.exportMatchLogs(List(MatchId(matchId)), "json-full", userId)
      } yield assertTrue(
        out.contains("\"matchId\""),
        out.contains("\"events\""),
        out.contains("\"summary\""),
        out.contains("possessionPercent") || out.contains("poissonPrognosis") || out.contains("passValueByPlayer")
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    },
    test("addBots assigns variable budget 500k–1.5M per bot") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          implicit val r: zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:league_budget_spec;DB_CLOSE_DELAY=-1", props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        _      <- userSvc.register("budget@test.com", "pass1234", "Budget")
        login  <- userSvc.login("budget@test.com", "pass1234", "secret")
        userId = UserId(login.user.id)
        config <- EngineConfig.fromEnvZIO
        initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
        engineModelsRef <- Ref.make(initialModels)
        leagueSvc = LeagueServiceLive(
          LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
          PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
          MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
          TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
          TrainingPlanRepository.impl, ShortlistRepository.impl, ScoutingReportRepository.impl,
          LeaguePlayerMatchStatsRepository.impl,
          ContractRepository.impl,
          FullMatchEngine, engineModelsRef, xa
        )
        created <- leagueSvc.create("Budget League", 10, "My Team", "Europe/Warsaw", userId)
        _       <- leagueSvc.addBots(LeagueId(created._1.id), userId, 2)
        teams   <- leagueSvc.listTeams(LeagueId(created._1.id))
        botTeams = teams.filter(_.ownerType == "Bot")
        budgets = botTeams.map(_.budget)
      } yield assertTrue(
        botTeams.size == 2,
        budgets.forall(b => b >= 500_000 && b <= 1_500_000)
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    },
    test("getMatchdayPrognosisForUser returns Elo-based P(home/draw/away) summing to 1") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          implicit val r: zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:league_prognosis_spec;DB_CLOSE_DELAY=-1", props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        _      <- userSvc.register("prog@test.com", "pass1234", "Prog")
        login  <- userSvc.login("prog@test.com", "pass1234", "secret")
        userId = UserId(login.user.id)
        config <- EngineConfig.fromEnvZIO
        initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
        engineModelsRef <- Ref.make(initialModels)
        leagueSvc = LeagueServiceLive(
          LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
          PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
          MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
          TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
          TrainingPlanRepository.impl, ShortlistRepository.impl, ScoutingReportRepository.impl,
          LeaguePlayerMatchStatsRepository.impl,
          ContractRepository.impl,
          FullMatchEngine, engineModelsRef, xa
        )
        created <- leagueSvc.create("Prognosis League", 10, "My Team", "Europe/Warsaw", userId)
        _       <- leagueSvc.addBots(LeagueId(created._1.id), userId, 9)
        _       <- leagueSvc.startSeason(LeagueId(created._1.id), userId, None)
        prognoses <- leagueSvc.getMatchdayPrognosisForUser(LeagueId(created._1.id), None, userId)
      } yield {
        val sumsOne = prognoses.forall { (p: MatchPrognosisDto) =>
          val sum = p.pHome + p.pDraw + p.pAway
          sum >= 0.999 && sum <= 1.001
        }
        val inRange = prognoses.forall { (p: MatchPrognosisDto) =>
          p.pHome >= 0 && p.pHome <= 1 && p.pDraw >= 0 && p.pDraw <= 1 && p.pAway >= 0 && p.pAway <= 1
        }
        assertTrue(prognoses.nonEmpty, sumsOne, inRange)
      })
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    }
  )
}
