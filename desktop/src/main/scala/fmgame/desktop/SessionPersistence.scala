package fmgame.desktop

import fmgame.backend.GameConfig

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/** Zapis/odczyt „zapamiętanej” sesji w katalogu użytkownika (F2.4).
  * Jedna linia: userId. Przy starcie: getMe(userId) → jeśli OK, auto-login.
  */
object SessionPersistence {

  private def sessionPath = Paths.get(GameConfig.userDataDir(), "session.txt")

  def saveUserId(userId: String): Unit = {
    val dir = Paths.get(GameConfig.userDataDir())
    if (!Files.exists(dir)) Files.createDirectories(dir)
    Files.write(sessionPath, userId.getBytes(StandardCharsets.UTF_8))
  }

  def loadUserId(): Option[String] = {
    val path = sessionPath
    if (!Files.exists(path)) return None
    val line = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim
    if (line.isEmpty) None else Some(line)
  }

  def clear(): Unit = {
    val path = sessionPath
    if (Files.exists(path)) Files.delete(path)
  }
}
