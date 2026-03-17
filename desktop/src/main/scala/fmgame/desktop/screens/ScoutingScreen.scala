package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.LeaguePlayerRowDto
import fmgame.shared.domain.{LeagueId, PlayerId, TeamId}

/** Scouting: zawodnicy w lidze, shortlist, raporty. */
class ScoutingScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Scouting", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()

  game.currentUser.foreach { case (userId, _) =>
    val posOpt = scala.List("", "GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST")
    val posSelect = new SelectBox[String](skin)
    posSelect.setItems(posOpt.toArray: _*)
    table.add(new Label("Pozycja:", skin)).left().padRight(8)
    table.add(posSelect).width(100).padBottom(8).row()

    val content = new Table(skin)
    def refresh(): Unit = {
      content.clear()
      val pos = Option(posSelect.getSelected).filter(_.nonEmpty)
      game.gameApi.listLeaguePlayers(leagueId, userId, pos, None, None) match {
        case Right(dto) =>
          content.add(new Label("Zawodnicy w lidze", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(4).row()
          dto.players.take(30).foreach { p =>
            val row = new Table(skin)
            row.add(new Label(s"${p.playerName} (${p.teamName}) ${p.preferredPositions.mkString(",")} OVR ${p.overall}", skin)).left().padRight(12)
            val addBtn = new TextButton("+ Shortlist", skin)
            addBtn.addListener(new ChangeListener {
              override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
                game.gameApi.addToShortlist(teamId, userId, PlayerId(p.playerId))
                refresh()
              }
            })
            row.add(addBtn).padRight(4)
            val reportBtn = new TextButton("Raport", skin)
            reportBtn.addListener(new ChangeListener {
              override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                showReportDialog(p.playerId, p.playerName, userId, () => refresh())
            })
            row.add(reportBtn)
            content.add(row).left().padBottom(2).row()
          }
        case Left(msg) => content.add(new Label("Błąd: " + msg, skin)).left().row()
      }
      refreshShortlist()
    }
    def refreshShortlist(): Unit = {
      content.add(new Label("Shortlist", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(12).padBottom(4).row()
      game.gameApi.getShortlist(teamId, userId) match {
        case Right(list) =>
          list.foreach { e =>
            val row = new Table(skin)
            row.add(new Label(s"${e.playerName} (${e.fromTeamName})", skin)).left().padRight(12)
            val remBtn = new TextButton("Usuń", skin)
            remBtn.addListener(new ChangeListener {
              override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
                game.gameApi.removeFromShortlist(teamId, userId, PlayerId(e.playerId))
                refresh()
              }
            })
            row.add(remBtn)
            content.add(row).left().padBottom(2).row()
          }
        case Left(_) =>
      }
      content.add(new Label("Raporty scoutingowe", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(12).padBottom(4).row()
      game.gameApi.listScoutingReports(teamId, userId) match {
        case Right(reports) =>
          reports.take(15).foreach { r =>
            content.add(new Label(s"${r.playerName}: ${r.rating} – ${r.notes}", skin)).left().padBottom(2).row()
          }
        case Left(_) =>
      }
    }
    posSelect.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = refresh()
    })
    table.add(content).left().row()
    refresh()
  }

  private def showReportDialog(playerId: String, playerName: String, userId: fmgame.shared.domain.UserId, onSuccess: () => Unit): Unit = {
    val ratingField = new TextField("7", skin)
    ratingField.setTextFieldFilter(new TextField.TextFieldFilter {
      override def acceptChar(field: TextField, c: Char): Boolean = Character.isDigit(c) || c == '.'
    })
    val notesField = new TextField("", skin)
    notesField.setMessageText("Notatki (opcjonalnie)")
    val d = new Dialog("Raport scoutingowy: " + playerName, skin) {
      override def result(obj: AnyRef): Unit = {
        if (java.lang.Boolean.TRUE == obj) {
          val rating = scala.util.Try(ratingField.getText.trim.toDouble).toOption.filter(r => r >= 0 && r <= 10).getOrElse(7.0)
          game.gameApi.createScoutingReport(teamId, userId, PlayerId(playerId), rating, notesField.getText.trim) match {
            case Right(_) => onSuccess()
            case Left(msg) => showErrorDialog("Błąd", msg)
          }
        }
        remove()
      }
    }
    d.getContentTable.add(new Label("Ocena (0–10):", skin)).row()
    d.getContentTable.add(ratingField).width(120f).row()
    d.getContentTable.add(new Label("Notatki:", skin)).row()
    d.getContentTable.add(notesField).width(280f).row()
    d.button("Zapisz", true)
    d.button("Anuluj", false)
    d.show(stage)
  }

  private def showErrorDialog(title: String, msg: String): Unit = {
    val d = new Dialog(title, skin) {
      override def result(obj: AnyRef): Unit = remove()
    }
    d.getContentTable.add(new Label(msg, skin)).width(400f)
    d.button("Zamknij", true)
    d.show(stage)
  }

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new TeamViewScreen(game, leagueId))
  })
  table.add(backBtn).padTop(16).row()

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
