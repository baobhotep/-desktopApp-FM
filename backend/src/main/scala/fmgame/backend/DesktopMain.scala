package fmgame.backend

import zio.*

/** Punkt wejścia dla buildu gry desktop (bez HTTP). Inicjuje DB w katalogu użytkownika
  * i serwisy, po czym kończy (albo ZIO.never do testów). F1.2, F1.4.
  * Build gry desktop używa tej main; obecny Main.scala pozostaje dla wersji web/testów.
  */
object DesktopMain extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    ZIO.runtime[Any].flatMap { runtime =>
      DesktopBootstrap.bootstrap(runtime)
        .tap(_ => ZIO.logInfo("Desktop stack started (no HTTP); exit or use from desktop module."))
        .flatMap(_ => ZIO.never)
        .catchAll(err => ZIO.logError(s"Desktop bootstrap failed: $err") *> ZIO.fail(err))
    }
}
