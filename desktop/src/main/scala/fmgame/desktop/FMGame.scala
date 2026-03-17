package fmgame.desktop

import com.badlogic.gdx.Game
import fmgame.shared.api.{GameAPI, UserDto}
import fmgame.shared.domain.UserId

/** Główna klasa gry LibGDX. Trzyma GameAPI i bieżącego użytkownika; ekrany przełączane przez setScreen. */
class FMGame(val gameApi: GameAPI) extends Game {

  private var _currentUser: Option[(UserId, UserDto)] = None
  private var _previousScreen: Option[com.badlogic.gdx.Screen] = None
  /** Tymczasowo wybrany plan taktyczny (np. z FormationEditorScreen) do użycia w SquadScreen. */
  private var _pendingGamePlanJson: Option[String] = None

  def currentUser: Option[(UserId, UserDto)] = _currentUser
  def pendingGamePlanJson: Option[String] = _pendingGamePlanJson
  def setPendingGamePlanJson(json: Option[String]): Unit = _pendingGamePlanJson = json
  def setCurrentUser(user: Option[(UserId, UserDto)]): Unit = _currentUser = user

  def setPreviousScreen(screen: com.badlogic.gdx.Screen): Unit = _previousScreen = Some(screen)
  def returnToPreviousScreen(): Unit = {
    _previousScreen match {
      case Some(s) =>
        _previousScreen = None
        setScreen(s)
      case None =>
        setScreen(new screens.MainMenuScreen(this))
    }
  }

  override def create(): Unit = {
    _currentUser = None
    val restored = for {
      id   <- SessionPersistence.loadUserId()
      dto  <- gameApi.getMe(UserId(id)).toOption
    } yield (UserId(dto.id), dto)
    restored match {
      case Some((uid, dto)) =>
        _currentUser = Some((uid, dto))
        setScreen(new screens.MainMenuScreen(this))
      case None =>
        setScreen(new screens.LoginScreen(this))
    }
  }
}
