package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.LeagueId

/** Ekran tworzenia systemu ligi angielskiej: 4 szczeble (Premier League, Championship, League One, League Two), 92 drużyny, gracz w Premier League. */
class CreateEnglishLeagueScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val myTeamNameField = new TextField("", skin)
  myTeamNameField.setMessageText("Nazwa Twojej drużyny (Premier League)")
  myTeamNameField.setWidth(320f)

  private val statusLabel = new Label("", skin)
  private val errorLabel = new Label("", skin)
  errorLabel.setColor(1f, 0.4f, 0.4f, 1f)

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()
  table.add(new Label("Liga angielska (4 szczeble)", skin.get("title", classOf[Label.LabelStyle]))).padBottom(8).row()
  table.add(new Label("Premier League (20), Championship (24), League One (24), League Two (24). Wszystkie zespoły i piłkarze generowani losowo.", skin)).width(500f).padBottom(Assets.padSection).row()
  table.add(new Label("Nazwa Twojej drużyny", skin)).left().padBottom(4).row()
  table.add(myTeamNameField).left().padBottom(Assets.padControl).row()
  table.add(statusLabel).left().padBottom(4).row()
  table.add(errorLabel).left().padBottom(Assets.padControl).row()

  val createBtn = new TextButton("Utwórz ligę angielską i rozpocznij sezon", skin)
  createBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      doCreate()
  })
  table.add(createBtn).padBottom(Assets.padControl).row()

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new MainMenuScreen(game))
  })
  table.add(backBtn).row()
  stage.addActor(table)

  private def doCreate(): Unit = {
    errorLabel.setText("")
    statusLabel.setText("")
    val myTeamName = myTeamNameField.getText.trim
    if (myTeamName.isEmpty) {
      errorLabel.setText("Podaj nazwę swojej drużyny.")
      return
    }
    game.currentUser match {
      case None =>
        errorLabel.setText("Zaloguj się.")
        return
      case Some((userId, _)) =>
        statusLabel.setText("Tworzenie 4 lig i 92 drużyn...")
        game.gameApi.createEnglishLeagueSystem(userId, myTeamName) match {
          case Left(err) =>
            errorLabel.setText(err)
            statusLabel.setText("")
          case Right((_, userLeague, _)) =>
            statusLabel.setText("Uruchamianie sezonu we wszystkich ligach...")
            game.gameApi.startSeasonForSystem("English", userId) match {
              case Left(err) =>
                errorLabel.setText("Liga utworzona, ale start sezonu: " + err)
                statusLabel.setText("")
              case Right(_) =>
                game.setScreen(new LeagueViewScreen(game, LeagueId(userLeague.id)))
            }
        }
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
