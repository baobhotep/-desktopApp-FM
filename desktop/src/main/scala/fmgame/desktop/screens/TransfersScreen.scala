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

/** Okna transferowe i oferty. */
class TransfersScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Transfery", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()

  game.currentUser.foreach { case (userId, _) =>
    game.gameApi.getTransferWindows(leagueId, userId) match {
      case Right(windows) =>
        table.add(new Label("Okna transferowe", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(4).row()
        windows.foreach { w =>
          table.add(new Label(s"Okno: otwarcie po kolejce ${w.openAfterMatchday}, zamknięcie przed ${w.closeBeforeMatchday} – ${w.status}", skin)).left().padBottom(2).row()
        }
        val newOfferBtn = new TextButton("Złóż ofertę", skin)
        newOfferBtn.addListener(new ChangeListener {
          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
            game.setScreen(new CreateOfferScreen(game, leagueId, teamId))
        })
        table.add(newOfferBtn).left().padBottom(4).row()
        table.add(new Label("Oferty (do/z mojej drużyny)", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(12).padBottom(4).row()
        game.gameApi.getTransferOffers(leagueId, Some(teamId), userId) match {
          case Right(offers) =>
            offers.foreach { o =>
              val row = new Table(skin)
              val from = o.fromTeamName.getOrElse(o.fromTeamId)
              val to = o.toTeamName.getOrElse(o.toTeamId)
              val player = o.playerName.getOrElse(o.playerId)
              row.add(new Label(s"$from → $to: $player – ${o.amount} (${o.status})", skin)).left().padRight(8)
              if (o.status == "Pending") {
                val acceptBtn = new TextButton("Akceptuj", skin)
                acceptBtn.addListener(new ChangeListener {
                  override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
                    game.gameApi.acceptTransferOffer(o.id, userId)
                    game.setScreen(new TransfersScreen(game, leagueId, teamId))
                  }
                })
                val rejectBtn = new TextButton("Odrzuć", skin)
                rejectBtn.addListener(new ChangeListener {
                  override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
                    game.gameApi.rejectTransferOffer(o.id, userId)
                    game.setScreen(new TransfersScreen(game, leagueId, teamId))
                  }
                })
                row.add(acceptBtn).padRight(4)
                row.add(rejectBtn)
                if (o.toTeamId == teamId.value) {
                  val counterBtn = new TextButton("Skontruj", skin)
                  counterBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      showCounterDialog(o.id, o.amount, userId)
                  })
                  row.add(counterBtn).padLeft(4)
                }
              }
              table.add(row).left().padBottom(2).row()
            }
          case Left(msg) => table.add(new Label("Błąd ofert: " + msg, skin)).left().row()
        }
      case Left(msg) => table.add(new Label("Błąd okien: " + msg, skin)).left().row()
    }
  }

  private def showCounterDialog(offerId: String, currentAmount: Double, userId: fmgame.shared.domain.UserId): Unit = {
    val amountField = new TextField(currentAmount.toString, skin)
    amountField.setTextFieldFilter(new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter {
      override def acceptChar(field: com.badlogic.gdx.scenes.scene2d.ui.TextField, c: Char): Boolean =
        Character.isDigit(c) || c == '.'
    })
    val d = new Dialog("Skontruj ofertę", skin) {
      override def result(obj: AnyRef): Unit = {
        if (java.lang.Boolean.TRUE == obj) {
          val amount = scala.util.Try(amountField.getText.trim.toDouble).toOption.getOrElse(0.0)
          if (amount > 0)
            game.gameApi.counterTransferOffer(offerId, userId, amount) match {
              case Right(_) => game.setScreen(new TransfersScreen(game, leagueId, teamId))
              case Left(msg) => showErrorDialog("Błąd", msg)
            }
        }
        remove()
      }
    }
    d.getContentTable.add(new Label("Twoja kwota kontroferty:", skin)).row()
    d.getContentTable.add(amountField).width(200f).row()
    d.button("Skontruj", true)
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
