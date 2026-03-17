package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}

/** Ekran rejestracji (F2.3): email, hasło, displayName; GameAPI.register; po sukcesie powrót do logowania. */
class RegisterScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val emailField = new TextField("", skin)
  emailField.setMessageText("Email")
  emailField.setWidth(300f)
  private val passwordField = new TextField("", skin)
  passwordField.setMessageText("Hasło (min. 8 znaków)")
  passwordField.setPasswordMode(true)
  passwordField.setWidth(300f)
  private val displayNameField = new TextField("", skin)
  displayNameField.setMessageText("Nazwa wyświetlana")
  displayNameField.setWidth(300f)

  private val errorLabel = new Label("", skin)
  errorLabel.setColor(1f, 0.4f, 0.4f, 1f)

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()
  table.add(new Label("Rejestracja", skin.get("title", classOf[Label.LabelStyle]))).padBottom(24).row()
  table.add(emailField).padBottom(8).row()
  table.add(passwordField).padBottom(8).row()
  table.add(displayNameField).padBottom(12).row()
  table.add(errorLabel).padBottom(8).row()
  val registerBtn = new TextButton("Zarejestruj", skin)
  registerBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      doRegister()
  })
  table.add(registerBtn).padBottom(8).row()
  val backBtn = new TextButton("Wstecz do logowania", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new LoginScreen(game))
  })
  table.add(backBtn).row()
  stage.addActor(table)

  private def doRegister(): Unit = {
    errorLabel.setText("")
    val email = emailField.getText.trim
    val password = passwordField.getText
    val displayName = displayNameField.getText.trim
    if (email.isEmpty || displayName.isEmpty) {
      errorLabel.setText("Uzupełnij email i nazwę.")
      return
    }
    if (password.length < 8) {
      errorLabel.setText("Hasło musi mieć co najmniej 8 znaków.")
      return
    }
    game.gameApi.register(email, password, displayName) match {
      case Right(_) =>
        game.setScreen(new LoginScreen(game))
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
