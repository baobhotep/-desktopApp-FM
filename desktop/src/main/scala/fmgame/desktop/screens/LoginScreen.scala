package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame, SessionPersistence}
import fmgame.shared.domain.UserId

/** Ekran logowania (F2.2): email/hasło, GameAPI.login, zapis użytkownika, przejście do menu. */
class LoginScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val emailField = new TextField("", skin)
  emailField.setMessageText("Email")
  emailField.setWidth(300f)
  private val passwordField = new TextField("", skin)
  passwordField.setMessageText("Hasło")
  passwordField.setPasswordMode(true)
  passwordField.setWidth(300f)

  private val errorLabel = new Label("", skin)
  errorLabel.setColor(1f, 0.4f, 0.4f, 1f)

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()
  table.add(new Label("FM Game – Logowanie", skin.get("title", classOf[Label.LabelStyle]))).padBottom(24).row()
  table.add(emailField).padBottom(8).row()
  table.add(passwordField).padBottom(12).row()
  table.add(errorLabel).padBottom(8).row()
  private val rememberCheck = new CheckBox("Zapamiętaj mnie", skin)
  table.add(rememberCheck).left().padBottom(8).row()
  val loginBtn = new TextButton("Zaloguj", skin)
  loginBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      doLogin()
  })
  table.add(loginBtn).padBottom(8).row()
  val registerBtn = new TextButton("Rejestracja", skin)
  registerBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new RegisterScreen(game))
  })
  table.add(registerBtn).row()
  stage.addActor(table)

  private def doLogin(): Unit = {
    errorLabel.setText("")
    val email = emailField.getText.trim
    val password = passwordField.getText
    if (email.isEmpty || password.isEmpty) {
      errorLabel.setText("Podaj email i hasło.")
      return
    }
    game.gameApi.login(email, password) match {
      case Right((userDto, _)) =>
        game.setCurrentUser(Some((UserId(userDto.id), userDto)))
        if (rememberCheck.isChecked) SessionPersistence.saveUserId(userDto.id)
        game.setScreen(new MainMenuScreen(game))
      case Left(msg) =>
        errorLabel.setText(msg)
    }
  }

  override def show(): Unit = Gdx.input.setInputProcessor(stage)
  override def render(delta: Float): Unit = {
    Gdx.gl.glClearColor(Assets.screenBackgroundColor.r, Assets.screenBackgroundColor.g, Assets.screenBackgroundColor.b, 1f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    stage.act(delta)
    stage.draw()
  }
  override def resize(width: Int, height: Int): Unit = viewport.update(width, height, true)
  override def pause(): Unit = ()
  override def resume(): Unit = ()
  override def hide(): Unit = ()
  override def dispose(): Unit = stage.dispose()
}
