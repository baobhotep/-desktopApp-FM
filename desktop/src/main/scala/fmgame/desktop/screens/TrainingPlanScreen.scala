package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.{LeagueId, TeamId}

/** Plan treningowy (7 dni tygodnia). */
class TrainingPlanScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  val dayLabels = scala.List("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
  val fields = dayLabels.map(_ => new TextField("", skin))
  fields.foreach(_.setMessageText("np. Odpoczynek, Taktyka..."))

  table.add(new Label("Plan treningowy (7 dni)", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()

  dayLabels.zip(fields).foreach { case (label, field) =>
    table.add(new Label(label + ":", skin)).left().width(120).padRight(8)
    table.add(field).width(280).padBottom(6).row()
  }

  game.currentUser.foreach { case (userId, _) =>
    game.gameApi.getTrainingPlan(teamId, userId).toOption.foreach { plan =>
      plan.week.zipWithIndex.foreach { case (s, i) =>
        if (i < fields.length) fields(i).setText(s)
      }
    }
  }

  val saveBtn = new TextButton("Zapisz plan", skin)
  saveBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.currentUser.foreach { case (userId, _) =>
        val week = fields.map(_.getText.trim).map(s => if (s.isEmpty) "—" else s)
        game.gameApi.upsertTrainingPlan(teamId, userId, week) match {
          case Right(_) => statusLabel.setText("Zapisano.")
          case Left(msg) => statusLabel.setText("Błąd: " + msg)
        }
      }
  })
  table.add(saveBtn).left().padTop(12).row()

  val statusLabel = new Label("", skin)
  table.add(statusLabel).left().padTop(4).row()

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new TeamViewScreen(game, leagueId))
  })
  table.add(backBtn).padTop(16).row()

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
