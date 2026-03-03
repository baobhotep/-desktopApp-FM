package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/** Pozycja zawodnika na boisku (x, y w metrach; boisko 105×68). */
case class PlayerPosition(playerId: PlayerId, x: Double, y: Double, zone: Int)

/** Boisko 105×68 m. Strefy 1–12: siatka 3×4 (3 wzdłuż, 4 wszerz). Strefa 1 = przy bramce gospodarzy. */
object PitchModel {
  val PitchLength = 105.0
  val PitchWidth = 68.0

  /** Środki stref (x od 0 do 105, y od 0 do 68). Strefa 1–4 bliżej x=0, 5–8 środek, 9–12 bliżej x=105. */
  val zoneCenters: Map[Int, (Double, Double)] = {
    val rows = 4
    val cols = 3
    (1 to 12).map { z =>
      val r = (z - 1) / cols
      val c = (z - 1) % cols
      val x = (c + 0.5) * (PitchLength / cols)
      val y = (r + 0.5) * (PitchWidth / rows)
      z -> (x, y)
    }.toMap
  }

  def zoneFromXY(x: Double, y: Double): Int = {
    val c = (x / (PitchLength / 3)).min(2).toInt
    val r = (y / (PitchWidth / 4)).min(3).toInt
    (r * 3 + c + 1).min(12).max(1)
  }

  def distance(x1: Double, y1: Double, x2: Double, y2: Double): Double =
    math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
}

/**
 * Generuje pozycje 22 zawodników na podstawie formacji i strefy piłki.
 * DxT wymaga pozycji 22 graczy; tutaj używamy uproszczenia: formacja + przesunięcie w stronę piłki.
 */
object PositionGenerator {
  /** Sloty formacji 4-3-3: (x w % długości boiska 0–1, y w % szerokości 0–1). Gospodarze atakują w kierunku x=105. */
  private val formation433: List[(Double, Double)] = List(
    (0.04, 0.5),   // GK
    (0.18, 0.22), (0.18, 0.5), (0.18, 0.78),  // LB, CB, RB
    (0.35, 0.35), (0.35, 0.5), (0.35, 0.65),  // LCM, CDM, RCM
    (0.55, 0.22), (0.55, 0.78), (0.55, 0.5),  // LW, RW, ST
  )
  private val formation442: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.22), (0.18, 0.5), (0.18, 0.78),
    (0.38, 0.22), (0.38, 0.5), (0.38, 0.5), (0.38, 0.78),
    (0.55, 0.38), (0.55, 0.62),
  )
  private val formation352: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.2, 0.25), (0.2, 0.5), (0.2, 0.75),
    (0.4, 0.2), (0.4, 0.4), (0.4, 0.5), (0.4, 0.6), (0.4, 0.8),
    (0.58, 0.4), (0.58, 0.6),
  )

  private def formationTemplate(name: String): List[(Double, Double)] =
    if (name.contains("4-4-2")) formation442
    else if (name.contains("3-5-2")) formation352
    else formation433

  /**
   * Pozycje 11 graczy drużyny. homeAttackRight = true dla gospodarzy (atak w stronę x=105).
   * Gdy customPositions = Some(11 par 0–1), używane zamiast szablonu formacji.
   * widthScale: narrow &lt; 1 (zbita), wide &gt; 1 (rozciągnięta wzdłuż linii).
   */
  def positionsForTeam(
    formationName: String,
    playerIds: List[PlayerId],
    homeAttackRight: Boolean,
    ballZone: Int,
    inPossession: Boolean,
    customPositions: Option[List[(Double, Double)]] = None,
    widthScale: Double = 1.0
  ): List[PlayerPosition] = {
    val raw = customPositions.filter(_.size >= 11).map(_.take(11).padTo(11, formation433.last)).getOrElse(formationTemplate(formationName).padTo(11, formation433.last))
    val template = raw.map { case (px, py) => (px, 0.5 + (py - 0.5) * widthScale) }
    val (ballX, _) = PitchModel.zoneCenters.getOrElse(ballZone, (52.5, 34.0))
    val shift = if (inPossession) (ballZone - 6) * 3.0 else 0.0
    val xOffset = if (homeAttackRight) shift else -shift
    template.zip(playerIds.take(11)).map { case ((px, py), pid) =>
      val x = (if (homeAttackRight) px else 1.0 - px) * PitchModel.PitchLength + xOffset
      val y = py * PitchModel.PitchWidth
      val z = PitchModel.zoneFromXY(x, y)
      PlayerPosition(pid, x.max(0).min(PitchModel.PitchLength), y.max(0).min(PitchModel.PitchWidth), z)
    }
  }

  /**
   * Pełne 22 pozycje: gospodarze atakują w prawo (x→105), goście w lewo (x→0).
   * customPositions: gdy Some, używane zamiast szablonu formacji dla danej drużyny.
   * widthScale: teamInstructions.width → narrow 0.8, wide 1.2, normal 1.0.
   */
  def all22Positions(
    homeFormation: String,
    homePlayerIds: List[PlayerId],
    awayFormation: String,
    awayPlayerIds: List[PlayerId],
    ballZone: Int,
    possessionHome: Boolean,
    homeCustomPositions: Option[List[(Double, Double)]] = None,
    awayCustomPositions: Option[List[(Double, Double)]] = None,
    homeWidthScale: Double = 1.0,
    awayWidthScale: Double = 1.0
  ): (List[PlayerPosition], List[PlayerPosition]) = {
    val home = positionsForTeam(homeFormation, homePlayerIds, homeAttackRight = true, ballZone, inPossession = possessionHome, homeCustomPositions, homeWidthScale)
    val away = positionsForTeam(awayFormation, awayPlayerIds, homeAttackRight = false, ballZone, inPossession = !possessionHome, awayCustomPositions, awayWidthScale)
    (home, away)
  }
}

/**
 * Pitch Control: prawdopodobieństwo kontroli strefy przez każdą drużynę.
 * Model Spearmana: time-to-intercept z pace/acceleration; P(control) ∝ exp(-time/scale).
 * Gdy brak paceAccByPlayer: uproszczenie exp(-distance/distanceScale).
 */
object PitchControl {
  private val distanceScale = EngineConstants.PitchControlDistanceScale
  private val timeScale = EngineConstants.PitchControlTimeScale

  /** Dla każdej strefy 1–12: (kontrola gospodarzy, kontrola gości). Suma = 1. */
  def controlByZone(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition]
  ): Map[Int, (Double, Double)] =
    controlByZoneWithFatigue(homePositions, awayPositions, None, None)

  /** Jak wyżej, z uwzględnieniem zmęczenia. */
  def controlByZoneWithFatigue(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition],
    fatigueByPlayer: Option[Map[fmgame.shared.domain.PlayerId, Double]]
  ): Map[Int, (Double, Double)] =
    controlByZoneWithFatigue(homePositions, awayPositions, fatigueByPlayer, None)

  /**
   * Pełna wersja: opcjonalnie time-to-intercept z pace (1–20) i acceleration (1–20).
   * timeToReach: krótki dystans (&lt;12 m) sqrt(2*d/accNorm), dłuższy d/paceNorm; wpływ = exp(-time/timeScale).
   */
  def controlByZoneWithFatigue(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition],
    fatigueByPlayer: Option[Map[fmgame.shared.domain.PlayerId, Double]],
    paceAccByPlayer: Option[Map[fmgame.shared.domain.PlayerId, (Int, Int)]]
  ): Map[Int, (Double, Double)] = {
    (1 to 12).map { z =>
      val (cx, cy) = PitchModel.zoneCenters.getOrElse(z, (52.5, 34.0))
      def inf(positions: List[PlayerPosition]): Double =
        positions.map { p =>
          val dist = PitchModel.distance(p.x, p.y, cx, cy)
          val base = paceAccByPlayer.flatMap(_.get(p.playerId)) match {
            case Some((pace, acc)) =>
              val paceNorm = 3.0 + (math.max(1, math.min(20, pace)) / 20.0) * 5.0
              val accNorm = 2.0 + (math.max(1, math.min(20, acc)) / 20.0) * 4.0
              val timeToReach = if (dist < 12.0) math.sqrt(2.0 * dist / accNorm) else dist / (paceNorm * 0.4)
              math.exp(-timeToReach / timeScale)
            case None =>
              math.exp(-dist / distanceScale)
          }
          val f = fatigueByPlayer.flatMap(_.get(p.playerId)).getOrElse(0.0)
          base * (1.0 - EngineConstants.PitchControlFatigueFactor * f)
        }.sum
      val h = inf(homePositions)
      val a = inf(awayPositions)
      val total = h + a
      val (hNorm, aNorm) = if (total <= 0) (0.5, 0.5) else (h / total, a / total)
      z -> (hNorm, aNorm)
    }.toMap
  }
}

/**
 * Dynamic Expected Threat (DxT): wartość strefy skorygowana o kontrolę przestrzeni.
 * Pełny DxT z pozycjami 22 graczy: zagrożenie strefy maleje, gdy przeciwnik kontroluje tę strefę.
 */
object DxT {
  /** Bazowa wartość zagrożenia strefy (1–12); wyższa strefa = bliżej bramki przeciwnika. */
  def baseZoneThreat(zone: Int): Double = 0.04 + 0.012 * math.max(1, math.min(12, zone))

  /**
   * Skorygowany DxT dla strefy: baseThreat * (1 - opponentControl).
   * Gdy przeciwnik silnie kontroluje strefę, jej wartość spada.
   */
  def adjustedThreat(zone: Int, homeControl: Double, awayControl: Double, possessionHome: Boolean): Double = {
    val base = baseZoneThreat(zone)
    val opponentControl = if (possessionHome) awayControl else homeControl
    base * (1.0 - 0.6 * opponentControl)
  }

  /** Mapa strefa -> skorygowany DxT dla wszystkich 12 stref. */
  def threatMap(
    controlByZone: Map[Int, (Double, Double)],
    possessionHome: Boolean
  ): Map[Int, Double] =
    (1 to 12).map { z =>
      val (h, a) = controlByZone.getOrElse(z, (0.5, 0.5))
      z -> adjustedThreat(z, h, a, possessionHome)
    }.toMap
}

/**
 * Matchup Matrix: presja w strefie piłki (ilu przeciwników w strefie) → wpływ na przechwyt / udane podanie.
 * Dynamic Pressure: P_total = 1 − Π(1−p_i) z odległości i atrybutów obrońców.
 */
object MatchupMatrix {
  /** Przeciwnicy w strefie piłki: liczba / 4, cap 1.0. Używane jako bonus do passInterceptProb. */
  def pressureInZone(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition],
    ballZone: Int,
    possessionHome: Boolean
  ): Double = {
    val opponentsInZone = if (possessionHome) awayPositions.count(_.zone == ballZone) else homePositions.count(_.zone == ballZone)
    (opponentsInZone / 4.0).min(1.0)
  }

  /**
   * Dynamic Pressure: P_total = 1 − Π(1−p_i). p_i = wpływ pojedynczego obrońcy z odległości do strefy piłki i atrybutów (tackling, acceleration).
   * Zwraca wartość 0–1 do dodania do passInterceptProb (np. * 0.08).
   */
  def dynamicPressureTotal(
    ballZone: Int,
    defenderPositions: List[PlayerPosition],
    tacklingByPlayer: Map[fmgame.shared.domain.PlayerId, Int],
    accelerationByPlayer: Map[fmgame.shared.domain.PlayerId, Int]
  ): Double = {
    val (cx, cy) = PitchModel.zoneCenters.getOrElse(ballZone, (52.5, 34.0))
    val scale = 18.0
    val product = defenderPositions.foldLeft(1.0) { (acc, p) =>
      val dist = PitchModel.distance(p.x, p.y, cx, cy)
      val tack = math.max(1, math.min(20, tacklingByPlayer.getOrElse(p.playerId, 10)))
      val acc = math.max(1, math.min(20, accelerationByPlayer.getOrElse(p.playerId, 10)))
      val pi = (math.exp(-dist / scale) * (tack / 20.0 * 0.4 + acc / 20.0 * 0.3)).min(0.35)
      acc * (1.0 - pi)
    }
    (1.0 - product).max(0.0).min(1.0)
  }
}
