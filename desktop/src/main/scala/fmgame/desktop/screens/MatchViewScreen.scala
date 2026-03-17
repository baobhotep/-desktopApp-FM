package fmgame.desktop.screens

import scala.collection.immutable.{List => ScalaList}

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.{MatchLogDto, MatchSummaryDto}
import fmgame.shared.domain.{LeagueId, MatchId}

/** Ekran meczu z zakładkami: Podsumowanie, Statystyki pełne, Odtwarzanie, Formacja. */
class MatchViewScreen(val game: FMGame, val matchId: MatchId, val leagueId: LeagueId, val matchLog: MatchLogDto) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val rootTable = new Table(skin)
  rootTable.setFillParent(true)
  rootTable.top().left()
  rootTable.pad(Assets.padSection)

  rootTable.add(new Label("Mecz", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padControl).row()

  private val contentTable = new Table(skin)
  contentTable.top().left()

  private def showSummary(): Unit = {
    contentTable.clear()
    matchLog.summary match {
      case Some(s) =>
        contentTable.add(new Label(s"Wynik: ${s.homeGoals} : ${s.awayGoals}", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(12).row()
        val poss = s.possessionPercent
        if (poss.size >= 2) contentTable.add(new Label(f"Posiadanie: ${poss(0)}%.0f%% : ${poss(1)}%.0f%%", skin)).left().padBottom(8).row()
        val xg = s.xgTotal
        if (xg.size >= 2) contentTable.add(new Label(f"xG: ${xg(0)}%.2f : ${xg(1)}%.2f", skin)).left().padBottom(8).row()
        contentTable.add(new Label(s"Strzały: ${s.shotsTotal.headOption.getOrElse(0)} : ${s.shotsTotal.lift(1).getOrElse(0)}", skin)).left().padBottom(4).row()
        if (s.interceptions.nonEmpty) contentTable.add(new Label(s"Przechwyty: ${s.interceptions.headOption.getOrElse(0)} : ${s.interceptions.lift(1).getOrElse(0)}", skin)).left().padBottom(4).row()
        if (s.tacklesTotal.nonEmpty) contentTable.add(new Label(s"Wślizgi wygrane: ${s.tacklesWon.headOption.getOrElse(0)} : ${s.tacklesWon.lift(1).getOrElse(0)}", skin)).left().padBottom(12).row()
      case None =>
        contentTable.add(new Label("Brak statystyk podsumowania.", skin)).left().padBottom(12).row()
    }
    val playBtn = new TextButton("Odtwórz mecz 2D", skin)
    playBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
        game.setPreviousScreen(MatchViewScreen.this)
        game.setScreen(new MatchPlaybackScreen(game, matchLog))
      }
    })
    contentTable.add(playBtn).left().padBottom(8).row()
  }

  private def showFullStats(): Unit = {
    contentTable.clear()
    contentTable.add(new Label("Statystyki meczu (gospodarze : goście)", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padControl).row()
    matchLog.summary match {
      case Some(s) =>
        def row(name: String, h: String, a: String): Unit =
          contentTable.add(new Label(s"$name: $h : $a", skin)).left().padBottom(2).row()
        def listRow(name: String, list: ScalaList[Double], fmt: Double => String = d => f"$d%.1f"): Unit =
          if (list.size >= 2) row(name, fmt(list(0)), fmt(list(1)))
        def intListRow(name: String, list: ScalaList[Int]): Unit =
          if (list.nonEmpty) row(name, list.headOption.getOrElse(0).toString, list.lift(1).getOrElse(0).toString)
        listRow("Posiadanie %", s.possessionPercent)
        row("Gole", s.homeGoals.toString, s.awayGoals.toString)
        intListRow("Strzały", s.shotsTotal)
        intListRow("Strzały na bramkę", s.shotsOnTarget)
        intListRow("Strzały obok", s.shotsOffTarget)
        intListRow("Strzały zablokowane", s.shotsBlocked)
        intListRow("Wielkie szanse", s.bigChances)
        listRow("xG", s.xgTotal, d => f"$d%.2f")
        intListRow("Podania", s.passesTotal)
        intListRow("Podania celne", s.passesCompleted)
        listRow("Celność podań %", s.passAccuracyPercent)
        intListRow("Podania w 1/3 ataku", s.passesInFinalThird)
        intListRow("Dośrodkowania", s.crossesTotal)
        intListRow("Dośrodkowania celne", s.crossesSuccessful)
        intListRow("Długie piłki", s.longBallsTotal)
        intListRow("Długie piłki celne", s.longBallsSuccessful)
        intListRow("Wślizgi", s.tacklesTotal)
        intListRow("Wślizgi wygrane", s.tacklesWon)
        intListRow("Przechwyty", s.interceptions)
        intListRow("Wybicia", s.clearances)
        intListRow("Bloki", s.blocks)
        intListRow("Obrony bramkarza", s.saves)
        intListRow("Stracone gole", s.goalsConceded)
        intListRow("Faule", s.fouls)
        intListRow("Żółte kartki", s.yellowCards)
        intListRow("Czerwone kartki", s.redCards)
        intListRow("Faule wywołane", s.foulsSuffered)
        intListRow("Rzuty rożne", s.corners)
        intListRow("Rzuty rożne wygrane", s.cornersWon)
        intListRow("Wrzuty", s.throwIns)
        intListRow("Rzuty wolne wygrane", s.freeKicksWon)
        intListRow("Spalone", s.offsides)
        s.duelsWon.foreach(l => if (l.size >= 2) row("Pojedynki wygrane", l(0).toString, l(1).toString))
        s.aerialDuelsWon.foreach(l => if (l.size >= 2) row("Pojedynki powietrzne", l(0).toString, l(1).toString))
        s.vaepTotal.foreach(l => if (l.size >= 2) listRow("VAEP", l, d => f"$d%.2f"))
        s.fieldTilt.foreach(l => if (l.size >= 2) listRow("Field tilt", l))
        s.passValueTotal.foreach(l => if (l.size >= 2) listRow("xPass suma", l, d => f"$d%.2f"))
        s.poissonPrognosis.foreach(l => if (l.size >= 3) contentTable.add(new Label(f"Prognoza Poisson: wygrana gosp. ${l(0)}%.2f, remis ${l(1)}%.2f, wygrana gości ${l(2)}%.2f", skin)).left().padBottom(4).row())
      case None =>
        contentTable.add(new Label("Brak statystyk.", skin)).left().row()
    }
  }

  private def showPlayback(): Unit = {
    contentTable.clear()
    contentTable.add(new Label("Odtwarzanie meczu", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(12).row()
    contentTable.add(new Label("Kliknij poniżej, aby otworzyć wizualizację 2D zdarzeń na boisku.", skin)).left().padBottom(8).row()
    val playBtn = new TextButton("Odtwórz mecz 2D", skin)
    playBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
        game.setPreviousScreen(MatchViewScreen.this)
        game.setScreen(new MatchPlaybackScreen(game, matchLog))
      }
    })
    contentTable.add(playBtn).left().row()
  }

  private def showFormation(): Unit = {
    contentTable.clear()
    contentTable.add(new Label("Składy (formacja)", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padControl).row()
    game.currentUser match {
      case Some((userId, _)) =>
        game.gameApi.getMatchSquads(matchId, userId) match {
          case Right(squads) if squads.nonEmpty =>
            squads.foreach { squad =>
              contentTable.add(new Label(s"Skład: ${squad.teamId.take(8)}... (źródło: ${squad.source})", skin)).left().padBottom(4).row()
              squad.lineup.sortBy(_.positionSlot).foreach { slot =>
                contentTable.add(new Label(s"  ${slot.positionSlot}: ${slot.playerId.take(8)}", skin)).left().padBottom(2).row()
              }
              contentTable.add(new Label("", skin)).padBottom(8).row()
            }
          case _ =>
            contentTable.add(new Label("Brak danych o składach na ten mecz.", skin)).left().row()
        }
      case None =>
        contentTable.add(new Label("Zaloguj się.", skin)).left().row()
    }
  }

  private val tabSummary = new TextButton("Podsumowanie", skin)
  private val tabStats = new TextButton("Statystyki pełne", skin)
  private val tabPlayback = new TextButton("Odtwarzanie", skin)
  private val tabFormation = new TextButton("Formacja", skin)
  tabSummary.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = showSummary()
  })
  tabStats.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = showFullStats()
  })
  tabPlayback.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = showPlayback()
  })
  tabFormation.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = showFormation()
  })
  rootTable.add(tabSummary).padRight(4)
  rootTable.add(tabStats).padRight(4)
  rootTable.add(tabPlayback).padRight(4)
  rootTable.add(tabFormation).padBottom(Assets.padControl).row()
  rootTable.add(new ScrollPane(contentTable, skin)).expand().fill().padBottom(Assets.padControl).row()

  showSummary()

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.returnToPreviousScreen()
  })
  rootTable.add(backBtn).left().row()
  stage.addActor(rootTable)

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
