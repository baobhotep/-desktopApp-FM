package fmgame.backend

import fmgame.backend.engine.{EngineConfig, EngineModelFactory, FullMatchEngine}
import fmgame.backend.repository.Database
import fmgame.backend.service.{LeagueService, UserService}
import zio.*
import zio.interop.catz.*
import doobie.*

/** Inicjalizacja rdzenia gry dla desktop: DB (ścieżka z GameConfig), Transactor, serwisy – bez HTTP.
  * Moduł desktop (A2) wywołuje bootstrap raz przy starcie, otrzymuje GameFacade.
  * F1.2.
  */
object DesktopBootstrap {

  /** Buduje Transactor i warstwę serwisów, zwraca GameFacade.
    * Zasoby (Hikari pool) pozostają otwarte do końca działania aplikacji.
    * @param runtime używany do budowy Transactor i wewnątrz GameFacade (runSync).
    */
  def bootstrap(runtime: Runtime[Any]): ZIO[Any, Throwable, GameFacade] = {
    val jwtSecret = Option(java.lang.System.getenv("JWT_SECRET")).filter(_.nonEmpty).getOrElse("fm-game-desktop-default")
    for {
      _ <- ZIO.logInfo("Desktop bootstrap: initializing DB and services (no HTTP)")
      dbUrl = GameConfig.jdbcUrl()
      dbUser = GameConfig.dbUser()
      dbPass = GameConfig.dbPassword()
      hikariDs <- ZIO.succeed {
        val cfg = new com.zaxxer.hikari.HikariConfig()
        cfg.setDriverClassName("org.h2.Driver")
        cfg.setJdbcUrl(dbUrl)
        cfg.setUsername(dbUser)
        cfg.setPassword(dbPass)
        cfg.setMaximumPoolSize(10)
        cfg.setMinimumIdle(2)
        new com.zaxxer.hikari.HikariDataSource(cfg)
      }
      jdbcPool <- ZIO.succeed {
        val exec = java.util.concurrent.Executors.newFixedThreadPool(10)
        (exec, scala.concurrent.ExecutionContext.fromExecutor(exec))
      }
      xa <- ZIO.succeed {
        given zio.Runtime[Any] = runtime
        Transactor.fromDataSource[zio.Task](hikariDs, jdbcPool._2)
      }
      _ <- ZIO.logDebug(s"Database: $dbUrl")
      _ <- Database.initSchema(xa)
      _ <- ZIO.logInfo("Database schema initialized")
      config <- EngineConfig.fromEnvZIO
      initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
      engineModelsRef <- Ref.make(initialModels)
      serviceLayer = ServiceLayers.make(xa, FullMatchEngine, engineModelsRef)
      env <- ZIO.service[UserService].zip(ZIO.service[LeagueService]).provide(serviceLayer)
      (userSvc, leagueSvc) = env
      facade = new GameFacade(runtime, userSvc, leagueSvc, jwtSecret)
      _ <- ZIO.logInfo("Desktop stack ready (GameFacade); no HTTP server")
    } yield facade
  }
}
