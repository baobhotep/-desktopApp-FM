package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.MatchLogDto

/** Opcjonalny ekran podsumowania meczu (F4.3): wynik, posiadanie, xG – przed odtwarzaniem. */
class MatchSummaryScreen(val game: FMGame, val matchLog: MatchLogDto) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()

  table.add(new Label("Podsumowanie meczu", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()
  matchLog.summary match {
    case Some(s) =>
      table.add(new Label(s"Wynik: ${s.homeGoals} : ${s.awayGoals}", skin)).padBottom(12).row()
      val poss = s.possessionPercent
      if (poss.size >= 2) table.add(new Label(f"Posiadanie: ${poss(0)}%.0f%% : ${poss(1)}%.0f%%", skin)).padBottom(8).row()
      val xg = s.xgTotal
      if (xg.size >= 2) table.add(new Label(f"xG: ${xg(0)}%.2f : ${xg(1)}%.2f", skin)).padBottom(8).row()
      table.add(new Label(s"Strzały: ${s.shotsTotal.headOption.getOrElse(0)} : ${s.shotsTotal.lift(1).getOrElse(0)}", skin)).padBottom(4).row()
      if (s.interceptions.nonEmpty) table.add(new Label(s"Przechwyty (odzyskania): ${s.interceptions.headOption.getOrElse(0)} : ${s.interceptions.lift(1).getOrElse(0)}", skin)).padBottom(if (s.tacklesTotal.nonEmpty) 4 else 16).row()
      if (s.tacklesTotal.nonEmpty) table.add(new Label(s"Wślizgi (wygrane): ${s.tacklesWon.headOption.getOrElse(0)} : ${s.tacklesWon.lift(1).getOrElse(0)}", skin)).padBottom(16).row()
    case None =>
      table.add(new Label("Brak statystyk podsumowania.", skin)).padBottom(16).row()
  }

  val playBtn = new TextButton("Odtwórz mecz", skin)
  playBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
      game.setPreviousScreen(MatchSummaryScreen.this)
      game.setScreen(new MatchPlaybackScreen(game, matchLog))
    }
  })
  table.add(playBtn).padBottom(8).row()
  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.returnToPreviousScreen()
  })
  table.add(backBtn).row()
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
