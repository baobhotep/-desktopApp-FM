package fmgame.backend

import fmgame.backend.api.ApiRoutes
import fmgame.backend.engine.{EngineConfig, EngineModelFactory, EngineModels, FullMatchEngine}
import fmgame.backend.repository.Database
import fmgame.backend.service.{LeagueService, UserService}
import zio.*
import zio.http.*
import zio.http.codec.PathCodec
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import java.io.File

object Main extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    val jwtSecret = Option(java.lang.System.getenv("JWT_SECRET")).filter(_.nonEmpty).getOrElse("change-me-in-production")
    val isProduction = Option(java.lang.System.getenv("ENV")).exists(_.equalsIgnoreCase("production"))
    val adminSecret = Option(java.lang.System.getenv("ADMIN_SECRET")).filter(_.nonEmpty)
    val allowedOrigin = Option(java.lang.System.getenv("ALLOWED_ORIGIN")).filter(_.nonEmpty)
    val dbUrl = Option(java.lang.System.getenv("DATABASE_URL")).filter(_.nonEmpty).getOrElse("jdbc:h2:mem:fmgame;DB_CLOSE_DELAY=-1")
    val dbUser = Option(java.lang.System.getenv("DATABASE_USER")).filter(_.nonEmpty).getOrElse("sa")
    val dbPass = Option(java.lang.System.getenv("DATABASE_PASSWORD")).filter(_.nonEmpty).getOrElse("")

    (for {
      _ <- ZIO.fail("JWT_SECRET must be set when ENV=production").when(isProduction && jwtSecret == "change-me-in-production")
      _ <- ZIO.logWarning("JWT_SECRET not set – using insecure default. Set JWT_SECRET env var for production.").when(jwtSecret == "change-me-in-production")
      _ <- ZIO.logWarning("ALLOWED_ORIGIN not set – CORS allows all origins. Set ALLOWED_ORIGIN for production.").when(allowedOrigin.isEmpty)
      _ <- ZIO.logInfo("FM Game backend starting")
      runtime <- ZIO.runtime[Any]
      hikariDs <- ZIO.acquireRelease(ZIO.succeed {
        val hikariConfig = new com.zaxxer.hikari.HikariConfig()
        hikariConfig.setDriverClassName("org.h2.Driver")
        hikariConfig.setJdbcUrl(dbUrl)
        hikariConfig.setUsername(dbUser)
        hikariConfig.setPassword(dbPass)
        hikariConfig.setMaximumPoolSize(10)
        hikariConfig.setMinimumIdle(2)
        new com.zaxxer.hikari.HikariDataSource(hikariConfig)
      })(ds => ZIO.succeed(ds.close()).ignoreLogged)
      jdbcPool <- ZIO.succeed {
        val exec = java.util.concurrent.Executors.newFixedThreadPool(10)
        (exec, scala.concurrent.ExecutionContext.fromExecutor(exec))
      }
      _ <- ZIO.addFinalizer(ZIO.succeed(jdbcPool._1.shutdown()).ignoreLogged)
      xa <- ZIO.succeed {
        implicit val r: zio.Runtime[Any] = runtime
        Transactor.fromDataSource[zio.Task](hikariDs, jdbcPool._2)
      }
      _ <- ZIO.logDebug(s"Database URL configured (driver: H2)")
      _ <- Database.initSchema(xa)
      _ <- ZIO.logInfo("Database schema initialized")
      config <- EngineConfig.fromEnvZIO
      initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
      engineModelsRef <- Ref.make(initialModels)
      serviceLayer = ServiceLayers.make(xa, FullMatchEngine, engineModelsRef)
      _ <- (for {
        userSvc <- ZIO.service[UserService]
        leagueSvc <- ZIO.service[LeagueService]
        routes = new ApiRoutes(userSvc, leagueSvc, jwtSecret, adminSecret, allowedOrigin)
        frontendDir = Option(java.lang.System.getenv("FRONTEND_DIR")).getOrElse("frontend")
        staticRoutes = zio.http.Routes(
          Method.GET / "target" / PathCodec.trailing -> handler { (path: zio.http.Path, _: Request) =>
            val baseDir = new File(frontendDir, "target").getCanonicalPath
            val file = new File(frontendDir, "target" + File.separator + path.encode)
            if (file.isFile && file.getCanonicalPath.startsWith(baseDir)) Handler.fromFile(file).runZIO(()).catchAll(_ => ZIO.succeed(Response.notFound))
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
      } yield ()).provideSome[Scope](serviceLayer)
    } yield ())
  }
}
