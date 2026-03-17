package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.{PlayerDto, UpdatePlayerRequest}
import fmgame.shared.domain.{LeagueId, PlayerId, TeamId}

/** Ekran edycji zawodnika: imię, nazwisko, pozycje, atrybuty (fizyczne, techniczne, mentalne) 1–20. */
class PlayerEditorScreen(val game: FMGame, val leagueId: LeagueId, val teamId: TeamId, val player: PlayerDto) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(20f)

  table.add(new Label("Edycja zawodnika", skin.get("title", classOf[Label.LabelStyle]))).padBottom(Assets.padSection).row()

  def mkBar(value: Double, maxVal: Float = 20f): ProgressBar = {
    val pb = new ProgressBar(0f, maxVal, if (maxVal <= 1f) 0.01f else 0.5f, false, skin)
    pb.setValue(value.toFloat.min(maxVal).max(0f))
    pb.setAnimateDuration(0f)
    pb
  }
  val summaryRow = new Table(skin)
  summaryRow.add(new Label(s"Overall: ${f"${player.overall}%.1f"}", skin)).padRight(Assets.padControl)
  summaryRow.add(new Label(s"Kond ${f"${player.condition * 100}%.0f"}%", skin)).padRight(2)
  summaryRow.add(mkBar(player.condition, 1f)).width(60f).padRight(Assets.padControl)
  summaryRow.add(new Label(s"Ostr ${f"${player.matchSharpness * 100}%.0f"}%", skin)).padRight(2)
  summaryRow.add(mkBar(player.matchSharpness, 1f)).width(60f).padRight(Assets.padControl)
  summaryRow.add(new Label("Fiz", skin)).padRight(2)
  summaryRow.add(mkBar(player.physicalAvg)).width(60f).padRight(Assets.padControl)
  summaryRow.add(new Label("Tech", skin)).padRight(2)
  summaryRow.add(mkBar(player.technicalAvg)).width(60f).padRight(Assets.padControl)
  summaryRow.add(new Label("Men", skin)).padRight(2)
  summaryRow.add(mkBar(player.mentalAvg)).width(60f).padRight(Assets.padControl)
  summaryRow.add(new Label("Obr", skin)).padRight(2)
  summaryRow.add(mkBar(player.defenseAvg)).width(60f)
  table.add(summaryRow).left().padBottom(Assets.padSection).row()

  val firstNameField = new TextField(player.firstName, skin)
  val lastNameField = new TextField(player.lastName, skin)
  table.add(new Label("Imię:", skin)).left().padRight(8)
  table.add(firstNameField).width(200).padBottom(8).row()
  table.add(new Label("Nazwisko:", skin)).left().padRight(8)
  table.add(lastNameField).width(200).padBottom(12).row()

  val posOptions = scala.List("GK", "CB", "LB", "RB", "DM", "CM", "AM", "LW", "RW", "ST")
  val posCheckboxes = posOptions.map { pos =>
    val cb = new CheckBox(" " + pos, skin)
    cb.setChecked(player.preferredPositions.contains(pos))
    (pos, cb)
  }
  table.add(new Label("Pozycje:", skin)).left().padBottom(4).row()
  val posRow = new Table(skin)
  posCheckboxes.foreach { case (_, cb) => posRow.add(cb).padRight(12) }
  table.add(posRow).left().padBottom(16).row()

  def attrValue(map: Map[String, Int], key: String): Int = math.max(1, math.min(20, map.getOrElse(key, 10)))
  def mkSlider(map: Map[String, Int], key: String): Slider = {
    val s = new Slider(1f, 20f, 1f, false, skin)
    s.setValue(attrValue(map, key).toFloat)
    s
  }

  val physicalKeys = scala.List("pace", "acceleration", "agility", "stamina", "strength", "jumpingReach", "balance", "naturalFitness")
  val technicalKeys = scala.List("passing", "firstTouch", "dribbling", "crossing", "shooting", "tackling", "marking", "heading", "longShots", "ballControl", "technique", "gkReflexes", "gkHandling", "gkKicking", "gkPositioning", "gkDiving", "gkThrowing", "gkCommandOfArea", "gkOneOnOnes")
  val mentalKeys = scala.List("composure", "decisions", "vision", "concentration", "workRate", "positioning", "anticipation", "flair", "aggression", "bravery", "leadership", "teamwork", "offTheBall")

  val physicalSliders = physicalKeys.map(k => k -> mkSlider(player.physical, k)).toMap
  val technicalSliders = technicalKeys.map(k => k -> mkSlider(player.technical, k)).toMap
  val mentalSliders = mentalKeys.map(k => k -> mkSlider(player.mental, k)).toMap

  table.add(new Label("Atrybuty fizyczne", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(8).padBottom(4).row()
  physicalKeys.foreach { k =>
    table.add(new Label(k + ":", skin)).left().width(140).padRight(8)
    table.add(physicalSliders(k)).width(180).padBottom(2).row()
  }
  table.add(new Label("Atrybuty techniczne", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(8).padBottom(4).row()
  technicalKeys.foreach { k =>
    table.add(new Label(k + ":", skin)).left().width(140).padRight(8)
    table.add(technicalSliders(k)).width(180).padBottom(2).row()
  }
  table.add(new Label("Atrybuty mentalne", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(8).padBottom(4).row()
  mentalKeys.foreach { k =>
    table.add(new Label(k + ":", skin)).left().width(140).padRight(8)
    table.add(mentalSliders(k)).width(180).padBottom(2).row()
  }

  val errorLabel = new Label("", skin)
  errorLabel.setColor(1f, 0.4f, 0.4f, 1f)
  table.add(errorLabel).colspan(2).left().padTop(8).row()

  val saveBtn = new TextButton("Zapisz", skin)
  saveBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.currentUser match {
        case Some((userId, _)) =>
          val positions = posCheckboxes.filter(_._2.isChecked).map(_._1)
          val physical = physicalKeys.map(k => k -> physicalSliders(k).getValue.toInt).toMap
          val technical = technicalKeys.map(k => k -> technicalSliders(k).getValue.toInt).toMap
          val mental = mentalKeys.map(k => k -> mentalSliders(k).getValue.toInt).toMap
          game.gameApi.updatePlayer(
            PlayerId(player.id),
            userId,
            UpdatePlayerRequest(
              firstName = Some(firstNameField.getText.trim).filter(_.nonEmpty),
              lastName = Some(lastNameField.getText.trim).filter(_.nonEmpty),
              preferredPositions = Some(positions),
              physical = Some(physical),
              technical = Some(technical),
              mental = Some(mental)
            )
          ) match {
            case Right(_) => game.setScreen(new TeamViewScreen(game, leagueId))
            case Left(msg) => errorLabel.setText(msg)
          }
        case None => errorLabel.setText("Zaloguj się.")
      }
  })
  table.add(saveBtn).padTop(16).row()

  val backBtn = new TextButton("Wstecz", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new TeamViewScreen(game, leagueId))
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
