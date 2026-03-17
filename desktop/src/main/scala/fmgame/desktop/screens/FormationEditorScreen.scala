package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame, FormationPresets}
import fmgame.shared.domain.{LeagueId, MatchId, TeamId}

/** Edytor formacji: wybór presetu (4-3-3, 4-4-2, …), generowanie gamePlanJson. „Zastosuj” ustawia plan i wraca do składu. */
class FormationEditorScreen(val game: FMGame, val leagueId: LeagueId, val matchId: MatchId, val teamId: TeamId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Ustaw formację", skin.get("title", classOf[Label.LabelStyle]))).padBottom(16).row()

  var selectedPreset = FormationPresets.Formation433
  val presetNames = FormationPresets.All.map(_._1).toArray
  val selectBox = new SelectBox[String](skin)
  selectBox.setItems(presetNames: _*)
  selectBox.setSelected(selectedPreset._1)
  table.add(new Label("Formacja (atak):", skin)).left().padRight(8)
  table.add(selectBox).width(180).padBottom(12).row()

  val slotLabel = new Label(slotSummary(selectedPreset._2), skin)
  table.add(slotLabel).left().padBottom(8).row()

  selectBox.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
      val name = selectBox.getSelected
      selectedPreset = FormationPresets.All.find(_._1 == name).getOrElse(FormationPresets.Formation433)
      slotLabel.setText(slotSummary(selectedPreset._2))
    }
  })

  var selectedDefensePreset: Option[(String, List[String], List[(Double, Double)])] = None
  val defenseSelectBox = new SelectBox[String](skin)
  defenseSelectBox.setItems(("Brak (jak atak)" +: presetNames): _*)
  defenseSelectBox.setSelected("Brak (jak atak)")
  table.add(new Label("Formacja w obronie:", skin)).left().padRight(8)
  table.add(defenseSelectBox).width(180).padBottom(12).row()
  val defenseSlotLabel = new Label("—", skin)
  table.add(defenseSlotLabel).left().padBottom(16).row()
  defenseSelectBox.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
      val name = defenseSelectBox.getSelected
      selectedDefensePreset = if (name == "Brak (jak atak)") None else FormationPresets.All.find(_._1 == name)
      defenseSlotLabel.setText(selectedDefensePreset.fold("—")(p => slotSummary(p._2)))
    }
  })

  def slotSummary(slots: scala.List[String]): String = slots.mkString(" • ")

  game.currentUser.foreach { case (userId, _) =>
    game.gameApi.listGamePlanSnapshots(teamId, userId).toOption.foreach { snapshots =>
      if (snapshots.nonEmpty) {
        table.add(new Label("Zapisane plany:", skin)).left().padTop(12).row()
        val snapNames = snapshots.map(_.name).toArray
        val snapSelect = new SelectBox[String](skin)
        snapSelect.setItems(snapNames: _*)
        table.add(snapSelect).width(220).padBottom(4).row()
        val loadBtn = new TextButton("Wczytaj plan", skin)
        loadBtn.addListener(new ChangeListener {
          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
            snapshots.find(_.name == snapSelect.getSelected).foreach { snap =>
              game.gameApi.getGamePlanSnapshot(teamId, snap.id, userId) match {
                case Right(detail) =>
                  val nameOpt = parseFormationName(detail.gamePlanJson)
                  nameOpt.foreach { name =>
                    FormationPresets.All.find(_._1 == name).foreach { p => selectedPreset = p; selectBox.setSelected(p._1); slotLabel.setText(slotSummary(p._2)) }
                  }
                  game.setPendingGamePlanJson(Some(detail.gamePlanJson))
                case Left(_) =>
              }
            }
        })
        table.add(loadBtn).left().padBottom(12).row()
      }
    }
    val saveNameField = new TextField("", skin)
    saveNameField.setMessageText("Nazwa planu...")
    table.add(new Label("Zapisz jako:", skin)).left().padTop(8).row()
    table.add(saveNameField).width(220).padBottom(4).row()
    val saveBtn = new TextButton("Zapisz plan", skin)
    saveBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
        val name = saveNameField.getText.trim
        if (name.nonEmpty) {
          val json = FormationPresets.toGamePlanJson(selectedPreset._1, selectedPreset._3)
          game.gameApi.saveGamePlan(teamId, userId, name, json) match {
            case Right(_) => game.setScreen(new FormationEditorScreen(game, leagueId, matchId, teamId))
            case Left(_) =>
          }
        }
      }
    })
    table.add(saveBtn).left().padBottom(12).row()
  }

  def parseFormationName(json: String): Option[String] = {
    val i = json.indexOf("\"formationName\":\"")
    if (i < 0) None else {
      val start = i + 18
      val end = json.indexOf('"', start)
      if (end < 0) None else Some(json.substring(start, end))
    }
  }

  def buildGamePlanJson(): String = {
    val defName = selectedDefensePreset.map(_._1)
    val defPos = selectedDefensePreset.map(_._3)
    FormationPresets.toGamePlanJson(selectedPreset._1, selectedPreset._3, defName, defPos)
  }

  val applyBtn = new TextButton("Zastosuj i wróć do składu", skin)
  applyBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
      val json = buildGamePlanJson()
      game.setPendingGamePlanJson(Some(json))
      game.setScreen(new SquadScreen(game, matchId, leagueId))
    }
  })
  table.add(applyBtn).padTop(8).row()

  val backBtn = new TextButton("Wstecz (bez zmian)", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new SquadScreen(game, matchId, leagueId))
  })
  table.add(backBtn).padTop(8).row()

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
