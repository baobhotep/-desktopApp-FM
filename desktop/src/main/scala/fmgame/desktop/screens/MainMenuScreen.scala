package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame, SessionPersistence}

/** Menu główne (F3.1): Wybierz ligę, Opcje, Wyloguj. */
class MainMenuScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()
  val userInfo = game.currentUser.fold("") { case (_, dto) => dto.displayName + " (" + dto.email + ")" }
  table.add(new Label("FM Game", skin.get("title", classOf[Label.LabelStyle]))).padBottom(Assets.padControl).row()
  table.add(new Label(userInfo, skin)).padBottom(Assets.padSection).row()

  val leagueBtn = new TextButton("Wybierz ligę", skin)
  leagueBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new LeagueListScreen(game))
  })
  table.add(leagueBtn).padBottom(Assets.padControl).row()

  val newLeagueBtn = new TextButton("Nowa liga", skin)
  newLeagueBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new CreateLeagueScreen(game))
  })
  table.add(newLeagueBtn).padBottom(Assets.padControl).row()

  val englishLeagueBtn = new TextButton("Nowa liga angielska (4 szczeble)", skin)
  englishLeagueBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new CreateEnglishLeagueScreen(game))
  })
  table.add(englishLeagueBtn).padBottom(Assets.padControl).row()

  val invitationsBtn = new TextButton("Zaproszenia do lig", skin)
  invitationsBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new InvitationsScreen(game))
  })
  table.add(invitationsBtn).padBottom(Assets.padControl).row()

  val optionsBtn = new TextButton("Opcje", skin)
  optionsBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      () // TODO F3 – placeholder
  })
  table.add(optionsBtn).padBottom(Assets.padControl).row()

  val logoutBtn = new TextButton("Wyloguj", skin)
  logoutBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
      game.setCurrentUser(None)
      SessionPersistence.clear()
      game.setScreen(new LoginScreen(game))
    }
  })
  table.add(logoutBtn).padBottom(Assets.padControl).row()

  val quitBtn = new TextButton("Zamknij grę", skin)
  quitBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      Gdx.app.exit()
  })
  table.add(quitBtn).row()
  stage.addActor(table)

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
