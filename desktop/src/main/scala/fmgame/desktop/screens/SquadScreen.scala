package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.{AssistantTipDto, MatchDto, SubmitMatchSquadRequest}
import fmgame.shared.domain.{LeagueId, MatchId, TeamId}

/** Ekran składu przed meczem: lista z nazwiskami, ustaw formację, zatwierdź skład z gamePlanJson. */
class SquadScreen(val game: FMGame, val matchId: MatchId, val leagueId: LeagueId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)
  table.add(new Label("Skład na mecz", skin.get("title", classOf[Label.LabelStyle]))).padBottom(8).row()

  game.currentUser match {
    case Some((userId, _)) =>
      game.gameApi.listTeams(leagueId, userId) match {
        case Right(teams) =>
          teams.find(t => t.ownerUserId.contains(userId.value)) match {
            case Some(myTeam) =>
              game.gameApi.getAssistantTip(matchId, TeamId(myTeam.id), userId).toOption.foreach { tip =>
                val tipLabel = new Label("Asystent: " + tip.tip, skin)
                tipLabel.setWrap(true)
                table.add(tipLabel).left().width(math.max(300f, (Gdx.graphics.getWidth - 80).toFloat)).padBottom(8).row()
              }
              val playerNames = game.gameApi.getTeamPlayers(TeamId(myTeam.id), userId).toOption
                .map(_.map(p => p.id -> s"${p.firstName} ${p.lastName}").toMap).getOrElse(Map.empty)
              def nameOf(playerId: String): String = playerNames.getOrElse(playerId, playerId.take(8) + "...")

              game.gameApi.getMatchSquads(matchId, userId) match {
                case Right(squads) =>
                  squads.find(_.teamId == myTeam.id) match {
                    case Some(squad) =>
                      table.add(new Label(s"Skład drużyny (${squad.lineup.size} zawodników)", skin)).padBottom(4).row()
                      squad.lineup.zipWithIndex.foreach { case (slot, i) =>
                        table.add(new Label(s"${i + 1}. ${slot.positionSlot}: ${nameOf(slot.playerId)}", skin)).left().padBottom(2).row()
                      }
                      val formationBtn = new TextButton("Ustaw formację", skin)
                      formationBtn.addListener(new ChangeListener {
                        override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                          game.setScreen(new FormationEditorScreen(game, leagueId, matchId, TeamId(myTeam.id)))
                      })
                      table.add(formationBtn).padTop(8).row()
                      game.gameApi.getMatch(matchId, userId).toOption.foreach { m =>
                        val opponentId = if (m.homeTeamId == myTeam.id) m.awayTeamId else m.homeTeamId
                        val teamNames = game.gameApi.listTeams(leagueId, userId).toOption.map(_.map(t => t.id -> t.name).toMap).getOrElse(Map.empty)
                        val assistantBtn = new TextButton("Rada asystenta", skin)
                        assistantBtn.addListener(new ChangeListener {
                          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                            game.gameApi.getAssistantTip(matchId, TeamId(myTeam.id), userId) match {
                              case Right(tip) => showAssistantTipDialog(tip)
                              case Left(msg) => showErrorDialog("Rada asystenta", msg)
                            }
                        })
                        table.add(assistantBtn).padTop(4).row()
                        val h2hBtn = new TextButton("Ostatnie mecze z rywalem (H2H)", skin)
                        h2hBtn.addListener(new ChangeListener {
                          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                            game.gameApi.getH2H(leagueId, TeamId(myTeam.id), TeamId(opponentId), 10, userId) match {
                              case Right(matches) => showH2HDialog(matches, teamNames)
                              case Left(msg) => showErrorDialog("H2H", msg)
                            }
                        })
                        table.add(h2hBtn).padTop(4).row()
                      }
                      val submitBtn = new TextButton("Zatwierdź skład", skin)
                      submitBtn.addListener(new ChangeListener {
                        override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
                          val gamePlan = game.pendingGamePlanJson.getOrElse("{}")
                          game.gameApi.submitMatchSquad(matchId, TeamId(myTeam.id), userId, SubmitMatchSquadRequest(squad.lineup, gamePlan)) match {
                            case Right(_) =>
                              game.setPendingGamePlanJson(None)
                              table.add(new Label("Skład zapisany.", skin)).padTop(8).row()
                            case Left(msg) => table.add(new Label(s"Błąd: $msg", skin)).padTop(8).row()
                          }
                        }
                      })
                      table.add(submitBtn).padTop(8).row()
                    case None =>
                      table.add(new Label("Brak składu dla Twojej drużyny.", skin)).row()
                  }
                case Left(msg) =>
                  table.add(new Label(s"Błąd składy: $msg", skin)).row()
              }
            case None =>
              table.add(new Label("Nie masz drużyny w tej lidze.", skin)).row()
          }
        case Left(msg) =>
          table.add(new Label(s"Błąd: $msg", skin)).row()
      }
    case None =>
      table.add(new Label("Zaloguj się.", skin)).row()
  }

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new LeagueViewScreen(game, leagueId))
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

  private def showErrorDialog(title: String, msg: String): Unit = {
    val d = new Dialog(title, skin) {
      override def result(obj: AnyRef): Unit = remove()
    }
    d.getContentTable.add(new Label(msg, skin)).width(400f)
    d.button("Zamknij", true)
    d.show(stage)
  }

  private def showAssistantTipDialog(tip: AssistantTipDto): Unit = {
    val d = new Dialog("Rada asystenta", skin) {
      override def result(obj: AnyRef): Unit = remove()
    }
    val content = new Table(skin)
    content.add(new Label(tip.tip, skin)).left().width(450f).padBottom(6).row()
    tip.opponentFormation.foreach { f => content.add(new Label("Formacja rywala: " + f, skin)).left().row() }
    tip.opponentStrengths.foreach { s => content.add(new Label("Mocne strony: " + s.mkString(", "), skin)).left().width(450f).row() }
    tip.opponentWeaknesses.foreach { w => content.add(new Label("Słabe strony: " + w.mkString(", "), skin)).left().width(450f).row() }
    tip.tacticalSuggestions.foreach { t => content.add(new Label("Sugestie: " + t.mkString("; "), skin)).left().width(450f).row() }
    tip.suggestedFormations.foreach { f => content.add(new Label("Sugerowane formacje: " + f.mkString(", "), skin)).left().row() }
    d.getContentTable.add(content)
    d.button("Zamknij", true)
    d.show(stage)
  }

  private def showH2HDialog(matches: scala.List[MatchDto], teamNames: Map[String, String]): Unit = {
    val d = new Dialog("Ostatnie mecze (H2H)", skin) {
      override def result(obj: AnyRef): Unit = remove()
    }
    def name(id: String): String = teamNames.getOrElse(id, id.take(8))
    if (matches.isEmpty) d.getContentTable.add(new Label("Brak meczów.", skin)).row()
    else matches.foreach { m =>
      val score = m.homeGoals.fold("–")(h => m.awayGoals.fold(s"$h")(a => s"$h : $a"))
      d.getContentTable.add(new Label(s"${name(m.homeTeamId)} – ${name(m.awayTeamId)}  $score", skin)).left().row()
    }
    d.button("Zamknij", true)
    d.show(stage)
  }
}
