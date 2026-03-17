package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.LeagueId

/** Statystyki ligi: król strzelców, lider asyst, zaawansowane statystyki, prognoza kolejki. */
class LeagueStatsScreen(val game: FMGame, val leagueId: LeagueId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Statystyki ligi", skin.get("title", classOf[Label.LabelStyle]))).padBottom(20).row()

  game.currentUser match {
    case Some((userId, _)) =>
      game.gameApi.getLeague(leagueId, userId).toOption.foreach { league =>
        table.add(new Label(s"${league.name} – kolejka ${league.currentMatchday}/${league.totalMatchdays}", skin)).left().padBottom(12).row()
      }
      game.gameApi.getLeaguePlayerStats(leagueId, userId) match {
        case Right(stats) =>
          table.add(new Label("Król strzelców", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(8).padBottom(4).row()
          if (stats.topScorers.isEmpty) table.add(new Label("Brak danych.", skin)).left().padBottom(8).row()
          else stats.topScorers.take(15).zipWithIndex.foreach { case (row, i) =>
            table.add(new Label(s"${i + 1}. ${row.playerName} (${row.teamName}) – ${row.goals} goli", skin)).left().padBottom(2).row()
          }
          table.add(new Label("Lider asyst", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(12).padBottom(4).row()
          if (stats.topAssists.isEmpty) table.add(new Label("Brak danych.", skin)).left().padBottom(8).row()
          else stats.topAssists.take(15).zipWithIndex.foreach { case (row, i) =>
            table.add(new Label(s"${i + 1}. ${row.playerName} (${row.teamName}) – ${row.assists} asyst", skin)).left().padBottom(2).row()
          }
        case Left(msg) =>
          table.add(new Label("Błąd statystyk: " + msg, skin)).left().padBottom(8).row()
      }
      game.gameApi.getLeaguePlayerAdvancedStats(leagueId, userId) match {
        case Right(adv) =>
          table.add(new Label("Statystyki zaawansowane (mecze, minuty, gole, asysty, xG, podania)", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(12).padBottom(4).row()
          if (adv.rows.isEmpty) table.add(new Label("Brak danych.", skin)).left().padBottom(8).row()
          else adv.rows.take(20).foreach { r =>
            table.add(new Label(f"${r.playerName} (${r.teamName}): ${r.matches} meczów, ${r.minutes} min, ${r.goals} goli, ${r.assists} asyst, xG ${r.xg}%.2f", skin)).left().padBottom(2).row()
          }
        case Left(_) =>
      }
      game.gameApi.getLeague(leagueId, userId).toOption.foreach { league =>
        game.gameApi.getMatchdayPrognosis(leagueId, userId, Some(league.currentMatchday + 1)) match {
          case Right(prognosis) if prognosis.nonEmpty =>
            table.add(new Label("Prognoza kolejki (Elo)", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(12).padBottom(4).row()
            prognosis.take(10).foreach { p =>
              val ph = (p.pHome * 100).toInt
              val pd = (p.pDraw * 100).toInt
              val pa = (p.pAway * 100).toInt
              table.add(new Label(s"${p.homeName} vs ${p.awayName}: ${ph}% / ${pd}% / ${pa}%", skin)).left().padBottom(2).row()
            }
          case _ =>
        }
      }
    case None =>
      table.add(new Label("Zaloguj się.", skin)).row()
  }

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new LeagueViewScreen(game, leagueId))
  })
  table.add(backBtn).padTop(20).row()

  val scroll = new ScrollPane(table, skin)
  scroll.setFadeScrollBars(false)
  scroll.setFillParent(true)
  stage.addActor(scroll)

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
