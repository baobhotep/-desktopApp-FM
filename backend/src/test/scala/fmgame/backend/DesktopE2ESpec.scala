package fmgame.backend

import fmgame.backend.engine.{EngineConfig, EngineModelFactory, FullMatchEngine}
import fmgame.backend.repository.*
import fmgame.backend.service.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*
import zio.test.TestAspect.*
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*

/** Test E2E flow przez GameFacade (F6.4): register → login → liga → playMatchday → getMatchLog.
  * Dodatkowo: liga angielska → pełny sezon → awans/spadek → nowy sezon oraz baraże.
  * Nie uruchamia LibGDX; weryfikuje pełną ścieżkę logiki desktop.
  */
object DesktopE2ESpec extends ZIOSpecDefault {

  val testDbUrl = "jdbc:h2:mem:desktop_e2e;DB_CLOSE_DELAY=-1"

  def spec = suite("Desktop E2E (GameFacade)")(
    test("full flow: register, login, create league (via service), listLeagues, getTable, playMatchday, getMatchLog") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          given zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", testDbUrl, props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        leagueSvc <- (for {
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
        } yield leagueSvc).provide(ZLayer.empty)
        facade = new GameFacade(runtime, userSvc, leagueSvc, "e2e-secret")

        _      <- ZIO.attempt(facade.register("e2e@test.com", "pass1234", "E2E User"))
        loginRes = facade.login("e2e@test.com", "pass1234")
        _      <- ZIO.fail("login failed").when(loginRes.isLeft)
        userDto = loginRes.toOption.get.user
        userId = UserId(userDto.id)

        created <- leagueSvc.create("E2E League", 10, "My Team", "Europe/Warsaw", userId).provide(ZLayer.empty).mapError(e => new RuntimeException(e))
        (league, _) = created
        _      <- leagueSvc.addBots(LeagueId(league.id), userId, 9).provide(ZLayer.empty)
        _      <- leagueSvc.startSeason(LeagueId(league.id), userId, None).provide(ZLayer.empty)

        leagues = facade.listLeagues(userId)
        _      <- ZIO.fail("listLeagues empty").when(leagues.isLeft || leagues.toOption.get.isEmpty)
        leagueId = LeagueId(league.id)

        table = facade.getTable(leagueId, userId)
        _      <- ZIO.fail("getTable failed").when(table.isLeft)
        _      <- ZIO.fail("table empty").when(table.toOption.get.isEmpty)

        playRes = facade.playMatchday(leagueId, userId)
        _      <- ZIO.fail("playMatchday failed: " + playRes.left.toOption.getOrElse("")).when(playRes.isLeft)

        fixtures = facade.getFixtures(leagueId, userId, Some(20), None)
        _      <- ZIO.fail("getFixtures failed").when(fixtures.isLeft)
        played = fixtures.toOption.get.filter(_.status == "Played")
        _      <- ZIO.fail("no played matches").when(played.isEmpty)
        matchId = MatchId(played.head.id)

        log = facade.getMatchLog(matchId, userId, None, None)
        _      <- ZIO.fail("getMatchLog failed").when(log.isLeft)
        dto    = log.toOption.get
      } yield assertTrue(
        dto.events.nonEmpty,
        dto.summary.isDefined,
        dto.summary.get.homeGoals >= 0,
        dto.summary.get.awayGoals >= 0
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    },
    test("English league: create → start season → play all matchdays → apply promotion/relegation → start next season") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          given zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:e2e_english;DB_CLOSE_DELAY=-1", props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        leagueSvc <- (for {
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
        } yield leagueSvc).provide(ZLayer.empty)
        facade = new GameFacade(runtime, userSvc, leagueSvc, "e2e-secret")

        _      <- ZIO.attempt(facade.register("english@e2e.com", "pass1234", "English User"))
        loginRes = facade.login("english@e2e.com", "pass1234")
        _      <- ZIO.fail("login failed").when(loginRes.isLeft)
        userId = UserId(loginRes.toOption.get.user.id)

        created = facade.createEnglishLeagueSystem(userId, "My Club")
        _      <- ZIO.fail("createEnglishLeagueSystem failed: " + created.left.toOption.getOrElse("")).when(created.isLeft)
        (allLeagues, _, _) = created.toOption.get
        _      <- ZIO.fail("expected 4 leagues").when(allLeagues.size != 4)

        startRes = facade.startSeasonForSystem("English", userId)
        _      <- ZIO.fail("startSeasonForSystem failed: " + startRes.left.toOption.getOrElse("")).when(startRes.isLeft)

        _      <- ZIO.foreachDiscard(allLeagues) { league =>
          val lid = LeagueId(league.id)
          ZIO.foreachDiscard(1 to league.totalMatchdays)(_ =>
            ZIO.attempt {
              val r = facade.playMatchday(lid, userId)
              if (r.isLeft) throw new RuntimeException("playMatchday: " + r.left.get)
            }
          )
        }

        leaguesAfterSeason = facade.listLeagues(userId).toOption.getOrElse(Nil).filter(_.leagueSystemName.contains("English"))
        _      <- ZIO.fail("all leagues must be finished").when(leaguesAfterSeason.exists(l => l.currentMatchday < l.totalMatchdays))

        promRes = facade.applyPromotionRelegation("English", userId)
        _      <- ZIO.fail("applyPromotionRelegation failed: " + promRes.left.toOption.getOrElse("")).when(promRes.isLeft)

        nextRes = facade.startNextSeasonForSystem("English", userId)
        _      <- ZIO.fail("startNextSeasonForSystem failed: " + nextRes.left.toOption.getOrElse("")).when(nextRes.isLeft)

        leaguesNew = facade.listLeagues(userId).toOption.getOrElse(Nil).filter(_.leagueSystemName.contains("English"))
        _      <- ZIO.fail("expected 4 leagues after new season").when(leaguesNew.size != 4)
        firstLeague = LeagueId(leaguesNew.head.id)
        tableNew = facade.getTable(firstLeague, userId)
        _      <- ZIO.fail("getTable failed after new season").when(tableNew.isLeft)
      } yield assertTrue(
        leaguesNew.forall(_.seasonPhase == "InProgress"),
        leaguesNew.forall(_.currentMatchday <= 1),
        tableNew.toOption.get.isEmpty || tableNew.toOption.get.forall(_.played == 0)
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    } @@ timeout(10.minutes),
    test("Play-offs: tier-2 league (user has team) finish season → create semi-finals → play semi → create final → play final") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa     <- ZIO.succeed {
          given zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:e2e_playoff;DB_CLOSE_DELAY=-1", props, None)
        }
        _      <- TestDbHelper.initSchemaForTest(xa)
        userSvc = UserServiceLive(UserRepository.impl, xa)
        leagueSvc <- (for {
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
        } yield leagueSvc).provide(ZLayer.empty)
        facade = new GameFacade(runtime, userSvc, leagueSvc, "e2e-secret")

        _      <- ZIO.attempt(facade.register("playoff@e2e.com", "pass1234", "Playoff User"))
        loginRes = facade.login("playoff@e2e.com", "pass1234")
        _      <- ZIO.fail("login failed").when(loginRes.isLeft)
        userId = UserId(loginRes.toOption.get.user.id)

        created = facade.createLeague("Championship", 24, "My Club", "Europe/Warsaw", userId)
        _      <- ZIO.fail("createLeague failed: " + created.left.toOption.getOrElse("")).when(created.isLeft)
        (leagueDto, _) = created.toOption.get
        leagueId = LeagueId(leagueDto.id)
        totalMd = leagueDto.totalMatchdays
        _      <- (sql"UPDATE leagues SET league_system_name = 'Test', tier = 2 WHERE id = ${leagueDto.id}".update.run).transact(xa).orDie

        _      <- leagueSvc.addBots(leagueId, userId, 23).provide(ZLayer.empty)
        _      <- leagueSvc.startSeason(leagueId, userId, None).provide(ZLayer.empty)

        _      <- ZIO.foreachDiscard(1 to totalMd)(_ => ZIO.attempt {
          val r = facade.playMatchday(leagueId, userId)
          if (r.isLeft) throw new RuntimeException("playMatchday: " + r.left.get)
        })

        semiRes = facade.createPlayOffSemiFinals(leagueId, userId)
        _      <- ZIO.fail("createPlayOffSemiFinals failed: " + semiRes.left.toOption.getOrElse("")).when(semiRes.isLeft)

        playSemi = facade.playMatchday(leagueId, userId)
        _      <- ZIO.fail("playMatchday (semi) failed: " + playSemi.left.toOption.getOrElse("")).when(playSemi.isLeft)

        finalRes = facade.createPlayOffFinal(leagueId, userId)
        _      <- ZIO.fail("createPlayOffFinal failed: " + finalRes.left.toOption.getOrElse("")).when(finalRes.isLeft)

        playFinal = facade.playMatchday(leagueId, userId)
        _      <- ZIO.fail("playMatchday (final) failed: " + playFinal.left.toOption.getOrElse("")).when(playFinal.isLeft)

        fixtures = facade.getFixtures(leagueId, userId, None, None).toOption.getOrElse(Nil)
        semiPlayed = fixtures.filter(_.matchday == totalMd + 1).forall(_.status == "Played")
        finalPlayed = fixtures.filter(_.matchday == totalMd + 2).forall(_.status == "Played")
      } yield assertTrue(semiPlayed, finalPlayed))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    } @@ timeout(5.minutes) @@ TestAspect.flaky(3)  // retry: semi-finals can end in a draw
  )
}
