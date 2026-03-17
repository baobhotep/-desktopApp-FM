package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.{ComparePlayersDto, PlayerDto}
import fmgame.shared.domain.{LeagueId, PlayerId, TeamId}

/** Porównanie dwóch zawodników: atrybuty i statystyki sezonu. */
class ComparePlayersScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Porównaj zawodników", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()

  var players: scala.List[PlayerDto] = Nil
  game.currentUser.foreach { case (userId, _) =>
    game.gameApi.getTeamPlayers(teamId, userId) match {
      case Right(ps) =>
        players = ps
        if (ps.size >= 2) {
          val names = ps.map(p => s"${p.firstName} ${p.lastName}")
          val arr = names.toArray
          val select1 = new SelectBox[String](skin)
          val select2 = new SelectBox[String](skin)
          select1.setItems(arr: _*)
          select2.setItems(arr: _*)
          if (arr.length > 1) select2.setSelected(arr(1))
          table.add(new Label("Zawodnik 1:", skin)).left().padRight(8)
          table.add(select1).width(200).padBottom(8).row()
          table.add(new Label("Zawodnik 2:", skin)).left().padRight(8)
          table.add(select2).width(200).padBottom(12).row()
          val resultArea = new Table(skin)
          val compareBtn = new TextButton("Porównaj", skin)
          compareBtn.addListener(new ChangeListener {
            override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
              game.currentUser.foreach { case (uid, _) =>
                val i1 = names.indexOf(select1.getSelected)
                val i2 = names.indexOf(select2.getSelected)
                if (i1 >= 0 && i2 >= 0 && i1 != i2) {
                  val p1 = players(i1)
                  val p2 = players(i2)
                  game.gameApi.getComparePlayers(leagueId, PlayerId(p1.id), PlayerId(p2.id), uid) match {
                    case Right(dto) => showComparison(dto, resultArea)
                    case Left(msg) => resultArea.clear(); resultArea.add(new Label("Błąd: " + msg, skin)).row()
                  }
                }
              }
          })
          table.add(compareBtn).left().padBottom(16).row()
          table.add(resultArea).left().colspan(2).row()
        } else
          table.add(new Label("Potrzebujesz co najmniej 2 zawodników w drużynie.", skin)).left().row()
      case Left(msg) =>
        table.add(new Label("Błąd: " + msg, skin)).left().row()
    }
  }

  private def showComparison(dto: ComparePlayersDto, result: Table): Unit = {
    result.clear()
    val (p1, p2) = (dto.player1, dto.player2)
    result.add(new Label(s"${p1.firstName} ${p1.lastName}", skin.get("title", classOf[Label.LabelStyle]))).padRight(40)
    result.add(new Label("vs", skin)).padRight(40)
    result.add(new Label(s"${p2.firstName} ${p2.lastName}", skin.get("title", classOf[Label.LabelStyle]))).row()
    val allKeys = (p1.physical.keys ++ p2.physical.keys).toSet.toList.sorted
    result.add(new Label("Atrybuty", skin)).colspan(3).left().padTop(8).row()
    allKeys.foreach { k =>
      val v1 = p1.physical.getOrElse(k, 0)
      val v2 = p2.physical.getOrElse(k, 0)
      result.add(new Label(s"$k: $v1", skin)).left().padRight(20)
      result.add(new Label("", skin))
      result.add(new Label(s"$v2", skin)).left().row()
    }
    (dto.stats1, dto.stats2) match {
      case (Some(s1), Some(s2)) =>
        result.add(new Label("Statystyki sezonu", skin)).colspan(3).left().padTop(12).row()
        result.add(new Label(s"Mecze: ${s1.matches} | Min: ${s1.minutes} | Gole: ${s1.goals} | Asysty: ${s1.assists} | xG: ${f"${s1.xg}%.2f"}", skin)).left().colspan(3).row()
        result.add(new Label(s"Mecze: ${s2.matches} | Min: ${s2.minutes} | Gole: ${s2.goals} | Asysty: ${s2.assists} | xG: ${f"${s2.xg}%.2f"}", skin)).left().colspan(3).row()
      case _ =>
    }
  }

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new TeamViewScreen(game, leagueId))
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
