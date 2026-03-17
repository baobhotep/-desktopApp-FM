package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.ContractDto
import fmgame.shared.domain.{LeagueId, TeamId}

/** Lista kontraktów drużyny: zawodnik, pensja, wygaśnięcie (kolejka). */
class ContractsScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Kontrakty", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padSection).row()

  game.currentUser match {
    case Some((userId, _)) =>
      game.gameApi.getTeamContracts(teamId, userId) match {
        case Right(contracts) =>
          val totalWage = contracts.map(_.weeklySalary).sum
          table.add(new Label(s"Suma pensji/tydz.: ${f"$totalWage%.0f"}", skin)).left().padBottom(Assets.padControl).row()
          table.add(new Label("Zawodnik", skin.get("title", classOf[Label.LabelStyle]))).left().width(180f).padRight(Assets.padControl)
          table.add(new Label("Pensja/tydz.", skin.get("title", classOf[Label.LabelStyle]))).left().width(100f).padRight(Assets.padControl)
          table.add(new Label("Do kolejki", skin.get("title", classOf[Label.LabelStyle]))).left().width(80f).padRight(Assets.padControl)
          table.add(new Label("Klauzula", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padControl).row()
          contracts.sortBy(c => (-c.endMatchday, c.playerName)).foreach { c =>
            val endHint = if (c.endMatchday <= 38) " [wkrótce]" else ""
            table.add(new Label(c.playerName + endHint, skin)).left().width(180f).padRight(Assets.padControl).padBottom(2)
            table.add(new Label(f"${c.weeklySalary}%.0f", skin)).left().width(100f).padRight(Assets.padControl).padBottom(2)
            table.add(new Label(c.endMatchday.toString, skin)).left().width(80f).padRight(Assets.padControl).padBottom(2)
            table.add(new Label(c.releaseClause.fold("—")(v => f"$v%.0f"), skin)).left().padBottom(2).row()
          }
          if (contracts.isEmpty) table.add(new Label("Brak kontraktów.", skin)).left().padTop(8).row()
        case Left(msg) =>
          table.add(new Label(s"Błąd: $msg", skin)).left().row()
      }
    case None =>
      table.add(new Label("Zaloguj się.", skin)).row()
  }

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new TeamViewScreen(game, leagueId))
  })
  table.add(backBtn).left().padTop(Assets.padSection).row()

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
