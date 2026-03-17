package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.PlayerDto
import fmgame.shared.domain.{LeagueId, TeamId}

/** Widok głębokości kadry: liczba zawodników na pozycję (jak FM Squad Planner). */
class SquadDepthScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Głębokość kadry", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padSection).row()

  game.currentUser match {
    case Some((userId, _)) =>
      val contractEndByPlayer = game.gameApi.getTeamContracts(teamId, userId).fold(_ => Map.empty[String, Int], _.map(c => c.playerId -> c.endMatchday).toMap)
      game.gameApi.getTeamPlayers(teamId, userId) match {
        case Right(players) =>
          val positions = Seq("GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST")
          def playerLine(p: fmgame.shared.api.PlayerDto): String = {
            val base = s"${p.firstName} ${p.lastName} (${f"${p.overall}%.1f"})"
            contractEndByPlayer.get(p.id).fold(base)(end => s"$base do kol.$end")
          }
          val byPos = positions.map { pos =>
            val count = players.count(p => p.preferredPositions.contains(pos))
            val names = players.filter(p => p.preferredPositions.contains(pos))
              .sortBy(p => -p.overall)
              .take(8)
              .map(playerLine)
              .mkString(", ")
            (pos, count, names)
          }
          table.add(new Label("Pozycja", skin.get("title", classOf[Label.LabelStyle]))).left().width(80f).padRight(Assets.padControl)
          table.add(new Label("Liczba", skin.get("title", classOf[Label.LabelStyle]))).left().width(60f).padRight(Assets.padControl)
          table.add(new Label("Zawodnicy (OVR, kontrakt)", skin.get("title", classOf[Label.LabelStyle]))).left().growX().padBottom(Assets.padControl).row()
          byPos.foreach { case (pos, count, names) =>
            val colorHint = if (count == 0) " [BRAK]" else if (count < 2 && pos == "GK") " [SŁABO]" else ""
            table.add(new Label(pos + colorHint, skin)).left().width(80f).padRight(Assets.padControl).padBottom(2)
            table.add(new Label(count.toString, skin)).left().width(60f).padRight(Assets.padControl).padBottom(2)
            table.add(new Label(if (names.isEmpty) "—" else names, skin)).left().growX().padBottom(2).row()
          }
        case Left(_) =>
          table.add(new Label("Brak dostępu do listy zawodników.", skin)).row()
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
