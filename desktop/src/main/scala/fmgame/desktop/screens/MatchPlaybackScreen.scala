package fmgame.desktop.screens

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.{Color, GL20}
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.{Table, TextButton}
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.{FitViewport, Viewport}
import com.badlogic.gdx.{Gdx, Input, Screen}
import fmgame.backend.engine.PitchModel
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.api.{MatchEventDto, MatchLogDto}

/** Ekran odtwarzania meczu 2D (F5). Konstruktor: MatchPlaybackScreen(game: FMGame, matchLogDto: MatchLogDto). */
class MatchPlaybackScreen(val game: FMGame, val matchLogDto: MatchLogDto) extends Screen {

  private val worldWidth  = PitchModel.PitchLength.toFloat
  private val worldHeight = PitchModel.PitchWidth.toFloat

  private var viewport: Viewport = _
  private var shapeRenderer: ShapeRenderer = _
  private var batch: SpriteBatch = _
  private var font: BitmapFont = _
  private var hudStage: Stage = _

  private var currentEventIndex: Int = 0
  private var eventTimeAccumulator: Float = 0f
  private var playing: Boolean = true
  private var speedMultiplier: Float = 1f

  private var ballX: Float = 0f
  private var ballY: Float = 0f
  private var actorX: Float = 0f
  private var actorY: Float = 0f
  private var secondaryX: Float = 0f
  private var secondaryY: Float = 0f
  private var goalPauseRemaining: Float = 0f

  override def show(): Unit = {
    viewport = new FitViewport(worldWidth, worldHeight)
    shapeRenderer = new ShapeRenderer
    batch = new SpriteBatch
    font = new BitmapFont()
    hudStage = new Stage(new com.badlogic.gdx.utils.viewport.ScreenViewport())
    val skin = Assets.getSkin
    val table = new Table(skin)
    table.setFillParent(true)
    table.top().right().pad(10)
    val backBtn = new TextButton("Wstecz", skin)
    backBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
        game.returnToPreviousScreen()
    })
    table.add(backBtn)
    hudStage.addActor(table)
    Gdx.input.setInputProcessor(hudStage)
    syncPositionsFromCurrentEvent()
  }

  override def resize(width: Int, height: Int): Unit = {
    viewport.update(width, height)
    hudStage.getViewport.update(width, height, true)
  }

  override def render(delta: Float): Unit = {
    Gdx.gl.glClearColor(0.2f, 0.5f, 0.2f, 1f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

    handleInput()
    if (goalPauseRemaining > 0) {
      goalPauseRemaining -= delta
    } else if (playing && currentEventIndex < matchLogDto.events.size) {
      eventTimeAccumulator += delta * speedMultiplier
      val eventDuration = 1.5f
      if (eventTimeAccumulator >= eventDuration) {
        eventTimeAccumulator = 0f
        currentEventIndex += 1
        if (currentEventIndex < matchLogDto.events.size) {
          val e = matchLogDto.events(currentEventIndex)
          if (e.eventType == "Goal") goalPauseRemaining = 2f
        }
        syncPositionsFromCurrentEvent()
      }
    }

    viewport.apply()
    shapeRenderer.setProjectionMatrix(viewport.getCamera.combined)

    drawPitch()
    drawBallAndPlayers()
    drawHud()

    hudStage.act(delta)
    hudStage.draw()
  }

  private def handleInput(): Unit = {
    if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) togglePlayPause()
    if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) setSpeed(1f)
    if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) setSpeed(2f)
    if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) game.returnToPreviousScreen()
  }

  private def zoneToWorld(zone: Int): (Float, Float) = {
    val (x, y) = PitchModel.zoneCenters.getOrElse(zone, (52.5, 34.0))
    (x.toFloat, y.toFloat)
  }

  private def syncPositionsFromCurrentEvent(): Unit = {
    val events = matchLogDto.events
    if (events.isEmpty) {
      ballX = worldWidth / 2
      ballY = worldHeight / 2
      actorX = ballX
      actorY = ballY
      secondaryX = ballX
      secondaryY = ballY
      return
    }
    val idx = currentEventIndex.min(events.size - 1)
    val e = events(idx)
    val (bx, by) = e.zone.fold((worldWidth / 2, worldHeight / 2))(zoneToWorld)
    ballX = bx
    ballY = by
    actorX = bx
    actorY = by
    val (sx, sy) = e.zone.fold((bx, by))(zoneToWorld)
    secondaryX = sx
    secondaryY = sy
  }

  private def drawPitch(): Unit = {
    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
    shapeRenderer.setColor(0.25f, 0.55f, 0.25f, 1f)
    shapeRenderer.rect(0, 0, worldWidth, worldHeight)
    shapeRenderer.end()

    shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
    shapeRenderer.setColor(Color.WHITE)
    shapeRenderer.rect(0, 0, worldWidth, worldHeight)
    shapeRenderer.setColor(0.35f, 0.6f, 0.35f, 1f)
    for (c <- 1 until PitchModel.Cols) {
      val x = (c * (worldWidth / PitchModel.Cols)).toFloat
      shapeRenderer.line(x, 0, x, worldHeight)
    }
    for (r <- 1 until PitchModel.Rows) {
      val y = (r * (worldHeight / PitchModel.Rows)).toFloat
      shapeRenderer.line(0, y, worldWidth, y)
    }
    shapeRenderer.end()
  }

  private def drawBallAndPlayers(): Unit = {
    val events = matchLogDto.events
    if (events.isEmpty) return
    val idx = currentEventIndex.min(events.size - 1)
    val e = events(idx)

    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
    shapeRenderer.setColor(1f, 1f, 1f, 1f)
    shapeRenderer.circle(ballX, ballY, 1.2f)
    shapeRenderer.setColor(0.2f, 0.3f, 0.8f, 1f)
    shapeRenderer.circle(actorX, actorY, 2f)
    if (e.secondaryPlayerId.isDefined) {
      shapeRenderer.setColor(0.8f, 0.2f, 0.2f, 1f)
      shapeRenderer.circle(secondaryX + 2.5f, secondaryY, 2f)
    }
    shapeRenderer.end()
  }

  private def drawHud(): Unit = {
    batch.setProjectionMatrix(viewport.getCamera.combined)
    batch.begin()
    val events = matchLogDto.events
    val minute = if (events.isEmpty) 0 else events(currentEventIndex.min(events.size - 1)).minute
    val eventType = if (events.isEmpty) "-" else events(currentEventIndex.min(events.size - 1)).eventType
    font.draw(batch, s"Min: $minute  |  $eventType  |  ${if (playing) "PLAY" else "PAUSE"}  |  ${speedMultiplier}x", 2f, worldHeight - 2f)
    batch.end()
  }

  def setPlaying(p: Boolean): Unit = playing = p
  def setSpeed(s: Float): Unit = speedMultiplier = s
  def togglePlayPause(): Unit = playing = !playing

  override def hide(): Unit = ()
  override def pause(): Unit = ()
  override def resume(): Unit = ()
  override def dispose(): Unit = {
    if (shapeRenderer != null) shapeRenderer.dispose()
    if (batch != null) batch.dispose()
    if (font != null) font.dispose()
    if (hudStage != null) hudStage.dispose()
  }
}

object MatchPlaybackScreen {
  /** Dummy events do testów A3 (F5.x). */
  val dummyEvents: List[MatchEventDto] = List(
    MatchEventDto(1, "KickOff", Some("p1"), None, Some("home"), Some(12), Some("Success"), Map.empty),
    MatchEventDto(2, "Pass", Some("p2"), Some("p3"), Some("home"), Some(14), Some("Success"), Map.empty),
    MatchEventDto(5, "Pass", Some("p3"), Some("p4"), Some("home"), Some(18), Some("Success"), Map.empty),
    MatchEventDto(8, "Shot", Some("p5"), None, Some("home"), Some(23), Some("Success"), Map.empty),
    MatchEventDto(9, "Goal", Some("p5"), None, Some("home"), Some(24), Some("Success"), Map.empty),
    MatchEventDto(15, "Foul", Some("pa1"), Some("ph1"), Some("away"), Some(10), None, Map.empty),
    MatchEventDto(20, "Pass", Some("p4"), Some("p5"), Some("home"), Some(20), Some("Success"), Map.empty),
    MatchEventDto(45, "LongPass", Some("pa2"), Some("pa3"), Some("away"), Some(16), Some("Success"), Map.empty),
  )
}
