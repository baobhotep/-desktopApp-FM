package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.LeagueId

/** Lista zaproszeń do lig i akceptacja. */
class InvitationsScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(Assets.padSection)

  table.add(new Label("Zaproszenia do lig", skin.get("title", classOf[Label.LabelStyle]))).padBottom(Assets.padSection).row()

  game.currentUser.foreach { case (userId, _) =>
    game.gameApi.listPendingInvitations(userId) match {
      case Right(invitations) =>
        if (invitations.isEmpty)
          table.add(new Label("Brak oczekujących zaproszeń.", skin)).left().row()
        else
          invitations.foreach { inv =>
            val row = new Table(skin)
            row.add(new Label(s"Liga ID: ${inv.leagueId} (token: ${inv.token.take(8)}...)", skin)).left().padRight(12)
            val nameField = new TextField("Nazwa drużyny", skin)
            nameField.setWidth(180f)
            val acceptBtn = new TextButton("Zaakceptuj", skin)
            acceptBtn.addListener(new ChangeListener {
              override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
                val teamName = nameField.getText.trim
                if (teamName.nonEmpty) {
                  game.gameApi.acceptInvitation(userId, inv.token, teamName) match {
                    case Right(resp) => game.setScreen(new LeagueViewScreen(game, LeagueId(resp.league.id)))
                    case Left(msg) => ()
                  }
                }
              }
            })
            row.add(nameField).padRight(8)
            row.add(acceptBtn)
            table.add(row).left().padBottom(8).row()
          }
      case Left(msg) => table.add(new Label("Błąd: " + msg, skin)).left().row()
    }
  }

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new MainMenuScreen(game))
  })
  table.add(backBtn).padTop(Assets.padSection).row()

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
