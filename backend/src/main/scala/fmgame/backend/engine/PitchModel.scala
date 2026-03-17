package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/** Pozycja zawodnika na boisku (x, y w metrach; boisko 105×68). */
case class PlayerPosition(playerId: PlayerId, x: Double, y: Double, zone: Int)

/**
 * Boisko 105×68 m. 24 strefy: siatka 6×4 (6 kolumn wzdłuż boiska, 4 rzędy wszerz).
 * Kolumny 0–1: tercja obronna, 2–3: środek, 4–5: tercja ataku (z perspektywy gospodarzy).
 * Numeracja: zone = row * Cols + col + 1 (row-major, 1-based).
 */
object PitchModel {
  val PitchLength = 105.0
  val PitchWidth = 68.0

  val Cols = 6
  val Rows = 4
  val TotalZones: Int = Cols * Rows

  def column(zone: Int): Int = (zone - 1) % Cols
  def row(zone: Int): Int = (zone - 1) / Cols

  def isDefensiveThird(zone: Int): Boolean = column(zone) <= 1
  def isMidfield(zone: Int): Boolean = { val c = column(zone); c >= 2 && c <= 3 }
  def isAttackingThird(zone: Int): Boolean = column(zone) >= 4
  def isOpponentHalf(zone: Int): Boolean = column(zone) >= 3
  def isBuildUpZone(zone: Int): Boolean = column(zone) <= 2
  def isHighIntensityZone(zone: Int): Boolean = column(zone) >= 3
  def isPenaltyArea(zone: Int): Boolean = column(zone) == 5

  def isDefensiveThird(zone: Int, isHome: Boolean): Boolean = if (isHome) column(zone) <= 1 else column(zone) >= 4
  def isAttackingThird(zone: Int, isHome: Boolean): Boolean = if (isHome) column(zone) >= 4 else column(zone) <= 1
  def isOpponentHalf(zone: Int, isHome: Boolean): Boolean = if (isHome) column(zone) >= 3 else column(zone) <= 2
  def isBuildUpZone(zone: Int, isHome: Boolean): Boolean = if (isHome) column(zone) <= 2 else column(zone) >= 3
  def isHighIntensityZone(zone: Int, isHome: Boolean): Boolean = if (isHome) column(zone) >= 3 else column(zone) <= 2
  def isPenaltyArea(zone: Int, isHome: Boolean): Boolean = if (isHome) column(zone) == 5 else column(zone) == 0

  /** Kolumna strefy znormalizowana do 0.0–1.0 (0 = bramka własna, 1 = bramka rywala). */
  def attackProgress(zone: Int): Double = column(zone).toDouble / (Cols - 1).toDouble
  def attackProgress(zone: Int, isHome: Boolean): Double =
    if (isHome) column(zone).toDouble / (Cols - 1).toDouble
    else 1.0 - column(zone).toDouble / (Cols - 1).toDouble

  val zoneCenters: Map[Int, (Double, Double)] =
    (1 to TotalZones).map { z =>
      val c = column(z)
      val r = row(z)
      val x = (c + 0.5) * (PitchLength / Cols)
      val y = (r + 0.5) * (PitchWidth / Rows)
      z -> (x, y)
    }.toMap

  def zoneFromXY(x: Double, y: Double): Int = {
    val c = (x / (PitchLength / Cols)).toInt.min(Cols - 1).max(0)
    val r = (y / (PitchWidth / Rows)).toInt.min(Rows - 1).max(0)
    (r * Cols + c + 1).min(TotalZones).max(1)
  }

  def distance(x1: Double, y1: Double, x2: Double, y2: Double): Double =
    math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
}

/**
 * Generuje pozycje 22 zawodników na podstawie formacji i strefy piłki.
 * 10 formacji: 4-3-3, 4-4-2, 3-5-2, 4-2-3-1, 3-4-3, 5-3-2, 4-1-4-1, 4-5-1, 4-4-1-1, 3-4-1-2.
 */
object PositionGenerator {
  private val formation433: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85),
    (0.38, 0.3), (0.38, 0.5), (0.38, 0.7),
    (0.6, 0.15), (0.6, 0.85), (0.7, 0.5),
  )
  private val formation442: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85),
    (0.4, 0.15), (0.4, 0.38), (0.4, 0.62), (0.4, 0.85),
    (0.62, 0.35), (0.62, 0.65),
  )
  private val formation352: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.2, 0.25), (0.2, 0.5), (0.2, 0.75),
    (0.4, 0.12), (0.4, 0.35), (0.4, 0.5), (0.4, 0.65), (0.4, 0.88),
    (0.62, 0.35), (0.62, 0.65),
  )
  private val formation4231: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85),
    (0.34, 0.35), (0.34, 0.65),
    (0.52, 0.15), (0.52, 0.5), (0.52, 0.85),
    (0.7, 0.5),
  )
  private val formation343: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.2, 0.25), (0.2, 0.5), (0.2, 0.75),
    (0.4, 0.15), (0.4, 0.38), (0.4, 0.62), (0.4, 0.85),
    (0.62, 0.2), (0.62, 0.5), (0.62, 0.8),
  )
  private val formation532: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.16, 0.1), (0.2, 0.3), (0.2, 0.5), (0.2, 0.7), (0.16, 0.9),
    (0.42, 0.25), (0.42, 0.5), (0.42, 0.75),
    (0.62, 0.35), (0.62, 0.65),
  )
  private val formation4141: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85),
    (0.32, 0.5),
    (0.48, 0.12), (0.48, 0.38), (0.48, 0.62), (0.48, 0.88),
    (0.68, 0.5),
  )
  private val formation451: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85),
    (0.38, 0.12), (0.38, 0.32), (0.38, 0.5), (0.38, 0.68), (0.38, 0.88),
    (0.65, 0.5),
  )
  private val formation4411: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85),
    (0.38, 0.15), (0.38, 0.38), (0.38, 0.62), (0.38, 0.85),
    (0.55, 0.5),
    (0.7, 0.5),
  )
  private val formation3412: List[(Double, Double)] = List(
    (0.04, 0.5),
    (0.2, 0.25), (0.2, 0.5), (0.2, 0.75),
    (0.38, 0.12), (0.38, 0.38), (0.38, 0.62), (0.38, 0.88),
    (0.55, 0.5),
    (0.68, 0.35), (0.68, 0.65),
  )

  val AllFormationNames: List[String] = List(
    "4-3-3", "4-4-2", "3-5-2", "4-2-3-1", "3-4-3", "5-3-2", "4-1-4-1", "4-5-1", "4-4-1-1", "3-4-1-2"
  )

  private def formationTemplate(name: String): List[(Double, Double)] = name match {
    case n if n.contains("4-2-3-1") => formation4231
    case n if n.contains("3-4-3")   => formation343
    case n if n.contains("5-3-2")   => formation532
    case n if n.contains("4-1-4-1") => formation4141
    case n if n.contains("4-5-1")   => formation451
    case n if n.contains("4-4-1-1") => formation4411
    case n if n.contains("3-4-1-2") => formation3412
    case n if n.contains("4-4-2")   => formation442
    case n if n.contains("3-5-2")   => formation352
    case _                          => formation433
  }

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
    val ballCol = PitchModel.column(math.max(1, math.min(PitchModel.TotalZones, ballZone)))
    val midCol = (PitchModel.Cols - 1) / 2.0
    val shift = if (inPossession) (ballCol - midCol) * 5.0 else 0.0
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

  def controlByZone(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition]
  ): Map[Int, (Double, Double)] =
    controlByZoneWithFatigue(homePositions, awayPositions, None, None)

  def controlByZoneWithFatigue(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition],
    fatigueByPlayer: Option[Map[fmgame.shared.domain.PlayerId, Double]]
  ): Map[Int, (Double, Double)] =
    controlByZoneWithFatigue(homePositions, awayPositions, fatigueByPlayer, None)

  def controlByZoneWithFatigue(
    homePositions: List[PlayerPosition],
    awayPositions: List[PlayerPosition],
    fatigueByPlayer: Option[Map[fmgame.shared.domain.PlayerId, Double]],
    paceAccByPlayer: Option[Map[fmgame.shared.domain.PlayerId, (Int, Int)]]
  ): Map[Int, (Double, Double)] = {
    (1 to PitchModel.TotalZones).map { z =>
      val (cx, cy) = PitchModel.zoneCenters.getOrElse(z, (52.5, 34.0))
      def inf(positions: List[PlayerPosition]): Double =
        positions.map { p =>
          val dist = PitchModel.distance(p.x, p.y, cx, cy)
          val base = paceAccByPlayer.flatMap(_.get(p.playerId)) match {
            case Some((pace, acceleration)) =>
              val paceNorm = 3.0 + (math.max(1, math.min(20, pace)) / 20.0) * 5.0
              val accNorm = 2.0 + (math.max(1, math.min(20, acceleration)) / 20.0) * 4.0
              val accelDist = (paceNorm * 0.4) * (paceNorm * 0.4) / (2.0 * accNorm)
              val timeToReach = if (dist <= accelDist) math.sqrt(2.0 * dist / accNorm)
                else {
                  val topSpeed = paceNorm * 0.4
                  val accelTime = topSpeed / accNorm
                  accelTime + (dist - accelDist) / topSpeed
                }
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
 * Zagrożenie strefy zależy od kolumny (odległość od bramki rywala) i kontroli przestrzeni.
 */
object DxT {
  /** Bazowa wartość zagrożenia strefy: zależy od postępu atakowego (0=obrona → 1=atak). */
  def baseZoneThreat(zone: Int, possessionHome: Boolean): Double = {
    val progress = PitchModel.attackProgress(zone, possessionHome)
    0.04 + 0.024 * (PitchModel.Cols - 1) * progress
  }

  @deprecated("Use baseZoneThreat(zone, possessionHome) instead", "")
  def baseZoneThreat(zone: Int): Double = {
    val col = PitchModel.column(zone)
    0.04 + 0.024 * col
  }

  def adjustedThreat(zone: Int, homeControl: Double, awayControl: Double, possessionHome: Boolean): Double = {
    val base = baseZoneThreat(zone, possessionHome)
    val opponentControl = if (possessionHome) awayControl else homeControl
    base * (1.0 - 0.6 * opponentControl)
  }

  def threatMap(
    controlByZone: Map[Int, (Double, Double)],
    possessionHome: Boolean
  ): Map[Int, Double] =
    (1 to PitchModel.TotalZones).map { z =>
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
   * Dynamic Pressure: P_total = 1 − Π(1−p_i). p_i = wpływ pojedynczego obrońcy z odległości
   * do strefy piłki i atrybutów (tackling, acceleration). Zwraca 0–1.
   */
  def dynamicPressureTotal(
    ballZone: Int,
    defenderPositions: List[PlayerPosition],
    tacklingByPlayer: Map[fmgame.shared.domain.PlayerId, Int],
    accelerationByPlayer: Map[fmgame.shared.domain.PlayerId, Int]
  ): Double = {
    val (cx, cy) = PitchModel.zoneCenters.getOrElse(ballZone, (52.5, 34.0))
    val scale = 18.0
    val product = defenderPositions.foldLeft(1.0) { (accumulator, p) =>
      val dist = PitchModel.distance(p.x, p.y, cx, cy)
      val tack = math.max(1, math.min(20, tacklingByPlayer.getOrElse(p.playerId, 10)))
      val accel = math.max(1, math.min(20, accelerationByPlayer.getOrElse(p.playerId, 10)))
      val pi = (math.exp(-dist / scale) * (tack / 20.0 * 0.4 + accel / 20.0 * 0.3)).min(0.35)
      accumulator * (1.0 - pi)
    }
    (1.0 - product).max(0.0).min(1.0)
  }
}
