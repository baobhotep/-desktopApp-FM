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

/** Widok drużyny użytkownika (F3.4): getTeam, getTeamPlayers w wybranej lidze. */
class TeamViewScreen(val game: FMGame, val leagueId: LeagueId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  game.currentUser match {
    case Some((userId, _)) =>
      game.gameApi.listTeams(leagueId, userId) match {
        case Right(teams) =>
          teams.find(t => t.ownerUserId.contains(userId.value)) match {
            case Some(myTeam) =>
              game.gameApi.getTeam(TeamId(myTeam.id), userId) match {
                case Right(team) =>
                  table.add(new Label(s"Drużyna: ${team.name}", skin.get("title", classOf[Label.LabelStyle]))).padBottom(10).row()
                  val wageBill = game.gameApi.getTeamContracts(TeamId(team.id), userId).fold(_ => 0.0, _.map(_.weeklySalary).sum)
                  table.add(new Label(s"Budżet: ${team.budget} | Elo: ${team.eloRating} | Pensje/tydz.: ${f"$wageBill%.0f"}", skin)).padBottom(4).row()
                  team.managerName.foreach { mn => table.add(new Label(s"Trener: $mn", skin)).left().padBottom(2).row() }
                  table.add(new Label(s"Właściciel: ${team.ownerType} | ID ligi: ${team.leagueId.take(8)}...", skin)).left().padBottom(4).row()
                  game.gameApi.getLeague(leagueId, userId).toOption.flatMap(_.tier).foreach { tier =>
                    val goal = if (tier == 1) "Cel: mistrzostwo / Europa" else "Cel: awans (lub baraże)"
                    table.add(new Label(goal, skin)).padBottom(8).row()
                  }
                  game.gameApi.listGamePlanSnapshots(TeamId(team.id), userId) match {
                    case Right(snapshots) if snapshots.nonEmpty =>
                      table.add(new Label("Zapisane plany taktyczne", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(8).padBottom(4).row()
                      snapshots.take(10).foreach { snap =>
                        table.add(new Label(s"• ${snap.name} (zapisany)", skin)).left().padBottom(2).row()
                      }
                      if (snapshots.size > 10) table.add(new Label(s"... i ${snapshots.size - 10} więcej", skin)).left().padBottom(8).row()
                    case _ =>
                  }
                  val statsBtn = new TextButton("Statystyki ligi", skin)
                  statsBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new LeagueStatsScreen(game, leagueId))
                  })
                  table.add(statsBtn).left().padRight(8)
                  val compareBtn = new TextButton("Porównaj zawodników", skin)
                  compareBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new ComparePlayersScreen(game, leagueId, TeamId(team.id)))
                  })
                  table.add(compareBtn).left().padBottom(8).row()
                  val trainingBtn = new TextButton("Plan treningowy", skin)
                  trainingBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new TrainingPlanScreen(game, leagueId, TeamId(team.id)))
                  })
                  table.add(trainingBtn).left().padRight(8)
                  val scoutingBtn = new TextButton("Scouting", skin)
                  scoutingBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new ScoutingScreen(game, leagueId, TeamId(team.id)))
                  })
                  table.add(scoutingBtn).left().padRight(8)
                  val transfersBtn = new TextButton("Transfery", skin)
                  transfersBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new TransfersScreen(game, leagueId, TeamId(team.id)))
                  })
                  val depthBtn = new TextButton("Głębokość kadry", skin)
                  depthBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new SquadDepthScreen(game, leagueId, TeamId(team.id)))
                  })
                  table.add(transfersBtn).left().padRight(8)
                  val contractsBtn = new TextButton("Kontrakty", skin)
                  contractsBtn.addListener(new ChangeListener {
                    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                      game.setScreen(new ContractsScreen(game, leagueId, TeamId(team.id)))
                  })
                  table.add(contractsBtn).left().padRight(8)
                  table.add(depthBtn).left().padBottom(12).row()
                  game.gameApi.getTeamPlayers(TeamId(team.id), userId) match {
                    case Right(players) =>
                      table.add(new Label("Zawodnicy", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padControl).row()
                      def mkBar(value: Double, maxVal: Float = 20f): ProgressBar = {
                        val pb = new ProgressBar(0f, maxVal, 0.01f, false, skin)
                        pb.setValue(value.toFloat.min(maxVal).max(0f))
                        pb.setAnimateDuration(0f)
                        pb
                      }
                      players.take(25).foreach { p =>
                        val pos = p.preferredPositions.headOption.getOrElse("?")
                        val row = new Table(skin)
                        row.add(new Label(s"${p.firstName} ${p.lastName} ($pos)", skin)).left().width(140f).padRight(Assets.padControl)
                        row.add(new Label(s"OVR ${f"${p.overall}%.1f"}", skin)).left().padRight(4)
                        row.add(new Label(s"Kond ${f"${p.condition * 100}%.0f"}%", skin)).left().padRight(2)
                        row.add(mkBar(p.condition, 1f)).width(36f).padRight(4)
                        row.add(new Label(s"Ostr ${f"${p.matchSharpness * 100}%.0f"}%", skin)).left().padRight(2)
                        row.add(mkBar(p.matchSharpness, 1f)).width(36f).padRight(Assets.padControl)
                        row.add(new Label(s"Fiz ${f"${p.physicalAvg}%.0f"}/20", skin)).left().padRight(2)
                        row.add(mkBar(p.physicalAvg)).width(40f).padRight(Assets.padControl)
                        row.add(new Label(s"Tech ${f"${p.technicalAvg}%.0f"}/20", skin)).left().padRight(2)
                        row.add(mkBar(p.technicalAvg)).width(40f).padRight(Assets.padControl)
                        row.add(new Label(s"Men ${f"${p.mentalAvg}%.0f"}/20", skin)).left().padRight(2)
                        row.add(mkBar(p.mentalAvg)).width(40f).padRight(Assets.padControl)
                        row.add(new Label(s"Obr ${f"${p.defenseAvg}%.0f"}/20", skin)).left().padRight(2)
                        row.add(mkBar(p.defenseAvg)).width(40f).padRight(Assets.padControl)
                        val editBtn = new TextButton("Edytuj", skin)
                        editBtn.addListener(new ChangeListener {
                          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                            game.setScreen(new PlayerEditorScreen(game, leagueId, TeamId(team.id), p))
                        })
                        row.add(editBtn)
                        table.add(row).left().padBottom(4).row()
                      }
                      if (players.size > 25) table.add(new Label(s"... i ${players.size - 25} więcej", skin)).padTop(4).row()
                    case Left(_) =>
                      table.add(new Label("Brak listy zawodników.", skin)).row()
                  }
                case Left(msg) =>
                  table.add(new Label(s"Błąd: $msg", skin)).row()
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

  val backBtn = new TextButton("Wstecz do widoku ligi", skin)
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
}
