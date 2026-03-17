package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.LeagueId

/** Lista lig (F3.2): GameAPI.listLeagues – na stubie pusta lista; wybór ligi → widok ligi (TODO). */
class LeagueListScreen(val game: FMGame) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.center()
  table.add(new Label("Wybierz ligę", skin.get("title", classOf[Label.LabelStyle]))).padBottom(Assets.padSection).row()
  game.currentUser match {
    case Some((userId, _)) =>
      game.gameApi.listLeagues(userId) match {
        case Right(leagues) if leagues.nonEmpty =>
          val bySystem = leagues.groupBy(_.leagueSystemName.getOrElse(""))
          bySystem.get("").toList.flatten.foreach { league =>
            val btn = new TextButton(league.name, skin)
            btn.addListener(new ChangeListener {
              override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                game.setScreen(new LeagueViewScreen(game, LeagueId(league.id)))
            })
            table.add(btn).padBottom(4).row()
          }
          bySystem.filter(_._1.nonEmpty).toList.sortBy(_._1).foreach { case (sysName, sysLeagues) =>
            table.add(new Label(sysName + " (4 szczeble)", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(Assets.padSection).padBottom(Assets.padControl).row()
            sysLeagues.sortBy(_.tier.getOrElse(0)).foreach { league =>
              val btn = new TextButton(s"  ${league.name}", skin)
              btn.addListener(new ChangeListener {
                override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                  game.setScreen(new LeagueViewScreen(game, LeagueId(league.id)))
              })
              table.add(btn).left().padBottom(4).row()
            }
          }
        case _ =>
          table.add(new Label("Brak lig.", skin)).padBottom(12).row()
      }
    case None =>
      table.add(new Label("Zaloguj się.", skin)).padBottom(12).row()
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
