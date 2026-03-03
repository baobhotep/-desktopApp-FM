package fmgame.backend

import fmgame.backend.api.ApiRoutes
import fmgame.backend.engine.{EngineConfig, EngineModelFactory, EngineModels, FullMatchEngine}
import fmgame.backend.repository.*
import fmgame.backend.service.*
import zio.*
import zio.http.*
import zio.http.codec.PathCodec
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import java.util.Properties
import java.io.File

object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    val jwtSecret = Option(java.lang.System.getenv("JWT_SECRET")).filter(_.nonEmpty).getOrElse("change-me-in-production")
    val isProduction = Option(java.lang.System.getenv("ENV")).exists(_.equalsIgnoreCase("production"))
    val adminSecret = Option(java.lang.System.getenv("ADMIN_SECRET")).filter(_.nonEmpty)
    val dbUrl = Option(java.lang.System.getenv("DATABASE_URL")).filter(_.nonEmpty).getOrElse("jdbc:h2:mem:fmgame;DB_CLOSE_DELAY=-1")
    val dbUser = "sa"
    val dbPass = ""

    (for {
      _ <- ZIO.fail("JWT_SECRET must be set when ENV=production").when(isProduction && jwtSecret == "change-me-in-production")
      _ <- ZIO.logInfo("FM Game backend starting")
      runtime <- ZIO.runtime[Any]
      props <- ZIO.succeed { val p = new Properties; p.setProperty("user", dbUser); p.setProperty("password", dbPass); p }
      xa <- ZIO.succeed {
        implicit val r: zio.Runtime[Any] = runtime
        Transactor.fromDriverManager[zio.Task]("org.h2.Driver", dbUrl, props, None)
      }
      _ <- ZIO.logDebug(s"Database URL configured (driver: H2)")
      _ <- Database.initSchema(xa)
      _ <- ZIO.logInfo("Database schema initialized")
      userSvc = UserServiceLive(UserRepository.impl, xa)
      config <- EngineConfig.fromEnvZIO
      initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
      engineModelsRef <- Ref.make(initialModels)
      leagueSvc = LeagueServiceLive(
        LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
        PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
        MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
        TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
        TrainingPlanRepository.impl,
        ShortlistRepository.impl,
        ScoutingReportRepository.impl,
        LeaguePlayerMatchStatsRepository.impl,
        FullMatchEngine, engineModelsRef, xa
      )
      routes = new ApiRoutes(userSvc, leagueSvc, jwtSecret, adminSecret)
      frontendDir = Option(java.lang.System.getenv("FRONTEND_DIR")).getOrElse("frontend")
      staticRoutes = zio.http.Routes(
        Method.GET / "target" / PathCodec.trailing -> handler { (path: zio.http.Path, _: Request) =>
          val file = new File(frontendDir, "target" + File.separator + path.encode)
          if (file.isFile) Handler.fromFile(file).runZIO(()).catchAll(_ => ZIO.succeed(Response.notFound))
          else ZIO.succeed(Response.notFound)
        }.sandbox,
        Method.GET / PathCodec.trailing -> handler { (_: zio.http.Path, _: Request) =>
          val indexFile = new File(frontendDir, "index.html")
          if (indexFile.isFile) Handler.fromFile(indexFile).runZIO(()).catchAll(_ => ZIO.succeed(Response.notFound))
          else ZIO.succeed(Response.notFound)
        }.sandbox
      )
      allRoutes = routes.app ++ staticRoutes
      portInt = Option(java.lang.System.getenv("PORT")).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(8080)
      _ <- ZIO.logInfo(s"HTTP server binding to port $portInt")
      schedulerFiber <- leagueSvc.runScheduledMatchdays().repeat(Schedule.spaced(Duration.fromSeconds(300))).fork
      _ <- ZIO.addFinalizer(schedulerFiber.interrupt.unit)
      _ <- ZIO.logInfo(s"FM Game backend ready on port $portInt (frontend from $frontendDir)")
      _ <- Server.serve(allRoutes.toHttpApp).provide(Server.defaultWith(_.port(portInt)))
    } yield ())
  }
}
