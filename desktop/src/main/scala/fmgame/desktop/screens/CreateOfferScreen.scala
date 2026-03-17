package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.CreateTransferOfferRequest
import fmgame.shared.domain.{LeagueId, TeamId}
import scala.util.Try

/** Formularz złożenia oferty transferowej: wybór drużyny (sprzedawcy), zawodnika, kwota. */
class CreateOfferScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Złóż ofertę transferową", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()

  game.currentUser match {
    case Some((userId, _)) =>
      val windows = game.gameApi.getTransferWindows(leagueId, userId).toOption.getOrElse(Nil)
      val openWindow = windows.find(_.status == "Open").map(_.id)
      val teams = game.gameApi.listTeams(leagueId, userId).toOption.getOrElse(Nil).filter(_.id != teamId.value)
      if (openWindow.isEmpty)
        table.add(new Label("Brak otwartego okna transferowego. Oferty można składać tylko gdy okno jest otwarte.", skin)).left().padBottom(16).row()
      else if (teams.isEmpty)
        table.add(new Label("Brak innych drużyn w lidze.", skin)).left().padBottom(16).row()
      else {
        val windowId = openWindow.get
        val teamIds = teams.map(_.id)
        val teamNames = teams.map(_.name).toArray
        val playerTable = new Table(skin)
        def loadPlayers(toTeamId: String): Unit = {
          playerTable.clear()
          game.gameApi.getTeamPlayers(TeamId(toTeamId), userId) match {
            case Right(players) =>
              players.foreach { p =>
                val btn = new TextButton(s"${p.firstName} ${p.lastName} (${f"${p.overall}%.1f"}) – Oferuj", skin)
                btn.addListener(new ChangeListener {
                  override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                    showAmountDialog(p.id, toTeamId, windowId, userId)
                })
                playerTable.add(btn).left().padBottom(2).row()
              }
            case Left(_) =>
              playerTable.add(new Label("Błąd ładowania zawodników.", skin)).left().row()
          }
        }
        val teamSelectBox = new SelectBox[String](skin)
        teamSelectBox.setItems(teamNames: _*)
        teamSelectBox.addListener(new ChangeListener {
          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
            val idx = teamSelectBox.getSelectedIndex
            if (idx >= 0 && idx < teamIds.size) loadPlayers(teamIds(idx))
          }
        })
        table.add(new Label("Drużyna (kupuję od):", skin)).left().row()
        table.add(teamSelectBox).left().width(280f).padBottom(8).row()
        table.add(new Label("Zawodnik – kliknij „Oferuj” i podaj kwotę:", skin)).left().row()
        table.add(playerTable).left().padBottom(16).row()
        loadPlayers(teamIds.head)
      }
    case None =>
      table.add(new Label("Zaloguj się.", skin)).row()
  }

  private def showAmountDialog(playerId: String, toTeamId: String, windowId: String, userId: fmgame.shared.domain.UserId): Unit = {
    val amountField = new TextField("100000", skin)
    amountField.setTextFieldFilter((field: TextField, c: Char) => Character.isDigit(c) || c == '.')
    val d = new Dialog("Kwota oferty", skin) {
      override def result(obj: AnyRef): Unit = {
        if (java.lang.Boolean.TRUE == obj) {
          val amount = Try(amountField.getText.trim.toDouble).toOption.getOrElse(0.0)
          if (amount > 0)
            game.gameApi.createTransferOffer(leagueId, userId, CreateTransferOfferRequest(windowId, toTeamId, playerId, amount)) match {
              case Right(_) => game.setScreen(new TransfersScreen(game, leagueId, teamId))
              case Left(msg) => showErrorDialog("Błąd", msg)
            }
        }
        remove()
      }
    }
    d.getContentTable.add(new Label("Kwota (np. 100000):", skin)).row()
    d.getContentTable.add(amountField).width(200f).row()
    d.button("Złóż ofertę", true)
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
      game.setScreen(new TransfersScreen(game, leagueId, teamId))
  })
  table.add(backBtn).padTop(8).row()

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
