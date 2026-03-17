package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.LeagueId

/** Ekran tworzenia nowej ligi: nazwa, liczba drużyn, nazwa mojej drużyny, strefa czasowa → createLeague, addBots, startSeason. */
class CreateLeagueScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val nameField = new TextField("", skin)
  nameField.setMessageText("Nazwa ligi")
  nameField.setWidth(320f)
  private val teamCountField = new TextField("10", skin)
  teamCountField.setMessageText("Liczba drużyn (np. 10)")
  teamCountField.setWidth(120f)
  private val myTeamNameField = new TextField("", skin)
  myTeamNameField.setMessageText("Nazwa mojej drużyny")
  myTeamNameField.setWidth(320f)
  private val timezoneField = new TextField("Europe/Warsaw", skin)
  timezoneField.setMessageText("Strefa czasowa")
  timezoneField.setWidth(200f)

  private val errorLabel = new Label("", skin)
  errorLabel.setColor(1f, 0.4f, 0.4f, 1f)

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()
  table.add(new Label("Nowa liga", skin.get("title", classOf[Label.LabelStyle]))).padBottom(24).row()
  table.add(new Label("Nazwa ligi", skin)).left().padBottom(4).row()
  table.add(nameField).left().padBottom(12).row()
  table.add(new Label("Liczba drużyn (łącznie z Tobą)", skin)).left().padBottom(4).row()
  table.add(teamCountField).left().padBottom(12).row()
  table.add(new Label("Nazwa Twojej drużyny", skin)).left().padBottom(4).row()
  table.add(myTeamNameField).left().padBottom(12).row()
  table.add(new Label("Strefa czasowa", skin)).left().padBottom(4).row()
  table.add(timezoneField).left().padBottom(16).row()
  table.add(errorLabel).left().padBottom(8).row()

  val createBtn = new TextButton("Utwórz ligę, dodaj boty i rozpocznij sezon", skin)
  createBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      doCreate()
  })
  table.add(createBtn).padBottom(12).row()

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new MainMenuScreen(game))
  })
  table.add(backBtn).row()
  stage.addActor(table)

  private def doCreate(): Unit = {
    errorLabel.setText("")
    val name = nameField.getText.trim
    val myTeamName = myTeamNameField.getText.trim
    val timezone = timezoneField.getText.trim
    if (name.isEmpty || myTeamName.isEmpty) {
      errorLabel.setText("Podaj nazwę ligi i nazwę swojej drużyny.")
      return
    }
    val teamCountStr = teamCountField.getText.trim
    val teamCount = if (teamCountStr.isEmpty) 10 else teamCountStr.toIntOption.getOrElse(10)
    if (teamCount < 2 || teamCount > 24) {
      errorLabel.setText("Liczba drużyn: 2–24.")
      return
    }
    val tz = if (timezone.isEmpty) "Europe/Warsaw" else timezone

    game.currentUser match {
      case Some((userId, _)) =>
        game.gameApi.createLeague(name, teamCount, myTeamName, tz, userId) match {
          case Right((league, _)) =>
            val leagueId = LeagueId(league.id)
            val botCount = teamCount - 1
            game.gameApi.addBots(leagueId, userId, botCount) match {
              case Right(_) =>
                game.gameApi.startSeason(leagueId, userId, None) match {
                  case Right(_) =>
                    game.setScreen(new LeagueViewScreen(game, leagueId))
                  case Left(e) =>
                    errorLabel.setText("Start sezonu: " + e)
                }
              case Left(e) =>
                errorLabel.setText("Dodawanie botów: " + e)
            }
          case Left(e) =>
            errorLabel.setText(e)
        }
      case None =>
        errorLabel.setText("Zaloguj się.")
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
