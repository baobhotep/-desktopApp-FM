package fmgame.backend

import java.io.File
import java.nio.file.Paths

/** Konfiguracja dla buildu desktop (gra Steam): ścieżka bazy H2 w katalogu użytkownika.
  * F1.1 – bez sztywnych ścieżek; env/plik.
  */
object GameConfig {

  /** Katalog danych gry w katalogu użytkownika.
    * Linux/macOS: ~/.local/share/FMGame
    * Windows: %APPDATA%\FMGame
    * Nadpisanie: zmienna env GAME_DATA_DIR (bezwzględna ścieżka).
    */
  def userDataDir(): String = {
    val fromEnv = Option(java.lang.System.getenv("GAME_DATA_DIR")).filter(_.nonEmpty)
    fromEnv.getOrElse(defaultUserDataDir())
  }

  private def defaultUserDataDir(): String = {
    val os = Option(java.lang.System.getProperty("os.name")).getOrElse("").toLowerCase
    if (os.contains("win")) {
      val appData = Option(java.lang.System.getenv("APPDATA")).getOrElse(
        Paths.get(java.lang.System.getProperty("user.home"), "AppData", "Roaming").toString
      )
      Paths.get(appData, "FMGame").toString
    } else {
      Paths.get(java.lang.System.getProperty("user.home"), ".local", "share", "FMGame").toString
    }
  }

  /** Pełna ścieżka do pliku bazy H2 (bez sufiksu .db – H2 dodaje własne).
    * Katalog jest tworzony, jeśli nie istnieje.
    */
  def databasePath(): String = {
    val dir = userDataDir()
    val file = new File(dir)
    if (!file.exists()) file.mkdirs()
    Paths.get(dir, "fmgame").toString
  }

  /** URL JDBC dla H2 w pliku.
    * Nadpisanie: zmienna env DATABASE_URL (pełny URL).
    * Domyślnie: jdbc:h2:file:<userDataDir>/fmgame;DB_CLOSE_DELAY=-1
    */
  def jdbcUrl(): String = {
    Option(java.lang.System.getenv("DATABASE_URL")).filter(_.nonEmpty).getOrElse {
      val path = databasePath()
      val normalized = new File(path).getAbsolutePath.replace("\\", "/")
      s"jdbc:h2:file:$normalized;DB_CLOSE_DELAY=-1"
    }
  }

  def dbUser(): String =
    Option(java.lang.System.getenv("DATABASE_USER")).filter(_.nonEmpty).getOrElse("sa")

  def dbPassword(): String =
    Option(java.lang.System.getenv("DATABASE_PASSWORD")).filter(_.nonEmpty).getOrElse("")
}
