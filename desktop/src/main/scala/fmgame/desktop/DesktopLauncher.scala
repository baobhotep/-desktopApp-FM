package fmgame.desktop

import com.badlogic.gdx.backends.lwjgl3.{Lwjgl3Application, Lwjgl3ApplicationConfiguration}
import fmgame.backend.DesktopBootstrap
import zio.Unsafe
import zio.Runtime

object DesktopLauncher {
  def main(args: Array[String]): Unit = {
    val config = new Lwjgl3ApplicationConfiguration
    config.setTitle("FM Game")
    config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
    config.setForegroundFPS(60)

    val api = Unsafe.unsafe { implicit u =>
      val runtime = Runtime.default
      runtime.unsafe.run(DesktopBootstrap.bootstrap(runtime)) match {
        case zio.Exit.Success(facade) => new GameFacadeAdapter(facade)
        case zio.Exit.Failure(cause)  =>
          System.err.println("Desktop bootstrap failed (DB/services): " + cause.prettyPrint)
          System.err.println("Falling back to StubGameAPI (no persistence).")
          StubGameAPI
      }
    }

    new Lwjgl3Application(new FMGame(api), config)
  }
}
