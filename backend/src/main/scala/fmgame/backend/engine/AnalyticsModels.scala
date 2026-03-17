package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Kontekst strzału do modelu xG (odległość, strefa, presja, czas).
 * Zastępuje pełny model ML (XGBoost/LightGBM) formułą deterministyczną.
 */
case class ShotContext(
  zone: Int,
  distanceToGoal: Double,
  isHeader: Boolean,
  minute: Int,
  scoreDiff: Int,
  pressureCount: Int = 0,
  angularPressure: Double = 0.0,
  gkDistance: Double = 0.0,
  goalAngle: Double = 0.3,
  isWeakFoot: Boolean = false,
  isHome: Boolean = true
)

/**
 * Kontrakt modelu xG. Można podmienić na model ML (XGBoost/LightGBM) ładowany z pliku.
 * ALGORYTMY_ANALITYKI §1.1: xG z odległości, kąta, części ciała, kontekstu.
 */
trait xGModel {
  def xGForShot(ctx: ShotContext): Double
}

/**
 * Formułowy xG bez ML: odległość + strefa + głowa + presja.
 * Wartość w [0, 1]. Im bliżej bramki (niższa strefa 1–12, mniejsza odległość), tym wyższe xG.
 */
object FormulaBasedxG extends xGModel {
  private val baseDistanceFactor = EngineConstants.XGBaseDistanceFactor
  private val zoneBonus = EngineConstants.XGZoneBonus

  def xGForShot(ctx: ShotContext): Double = {
    val distFactor = math.exp(-ctx.distanceToGoal / baseDistanceFactor)
    val attackProgress = PitchModel.attackProgress(math.max(1, math.min(PitchModel.TotalZones, ctx.zone)), ctx.isHome)
    val zoneFactor = attackProgress * zoneBonus
    val headerPenalty = if (ctx.isHeader) 0.75 else 1.0

    val goalAngleFactor = {
      val normAngle = (ctx.goalAngle / math.Pi).min(1.0).max(0.0)
      0.6 + 0.4 * normAngle
    }

    val pressurePenalty = 1.0 - (ctx.pressureCount * 0.06).min(0.35)

    val angularPenalty = {
      val ang = math.max(0.0, ctx.angularPressure)
      1.0 - (ang * 0.08).min(0.4)
    }

    val gkFactor = {
      val d = math.max(0.0, ctx.gkDistance)
      if (d <= 1e-6) 1.0
      else {
        val norm = (d / 11.0).min(2.0)
        (0.9 + (norm - 1.0) * 0.2).max(0.7).min(1.3)
      }
    }

    val weakFootFactor = if (ctx.isWeakFoot) 0.82 else 1.0

    val timeFactor = if (ctx.minute >= 85 && ctx.scoreDiff != 0) 1.05 else 1.0
    (0.02 + 0.5 * distFactor + 0.25 * zoneFactor) *
      headerPenalty * goalAngleFactor * pressurePenalty * angularPenalty * gkFactor * weakFootFactor * timeFactor min 0.95 max 0.01
  }
}

/**
 * Kontekst akcji do VAEP: minuta, wynik, posiadanie, strefa.
 * ALGORYTMY_ANALITYKI §2.3: VAEP = ΔP_scores − ΔP_concedes; kontekst (czas, wynik, sekwencja).
 */
case class VAEPContext(
  eventType: String,
  zone: Int,
  outcome: Option[String],
  minute: Int,
  scoreHome: Int,
  scoreAway: Int,
  possessionTeamId: Option[TeamId],
  isPossessionTeam: Boolean,
  isHome: Boolean = true
)

/**
 * Kontrakt modelu VAEP. Można podmienić na model ML (dwa klasyfikatory P_scores / P_concedes).
 * ALGORYTMY_ANALITYKI §2.3: V(a) = ΔP_scores − ΔP_concedes.
 */
trait VAEPModel {
  def valueForEvent(ctx: VAEPContext): Double
}

/**
 * Formułowy VAEP bez ML: typ akcji + strefa + wynik + czas do końca + wynik meczu.
 */
object FormulaBasedVAEP extends VAEPModel {
  private def zoneThreat(z: Int, isHome: Boolean): Double = DxT.baseZoneThreat(math.max(1, math.min(PitchModel.TotalZones, z)), isHome)
  private val maxThreat: Double = DxT.baseZoneThreat(PitchModel.TotalZones, true)
  private def timeWeight(minute: Int): Double = if (minute >= 80) 1.2 else if (minute >= 60) 1.05 else 1.0

  def valueForEvent(ctx: VAEPContext): Double = {
    val inAttack = ctx.zone >= 1 && PitchModel.isAttackingThird(ctx.zone, ctx.isHome)
    val base = ctx.eventType match {
      case "Pass" | "LongPass" =>
        if (ctx.outcome.contains("Success")) 0.015 + 0.008 * zoneThreat(ctx.zone, ctx.isHome) else -0.025
      case "Shot" =>
        ctx.outcome match {
          case Some("Saved") => 0.04
          case Some("Missed") => -0.015
          case Some("Blocked") => -0.008
          case _ => 0.0
        }
      case "Goal" => 0.28
      case "ThrowIn" => 0.008
      case "Cross" =>
        val v = if (ctx.outcome.contains("Success")) 0.018 else -0.018
        if (inAttack) v * (1.0 + 0.2 * zoneThreat(ctx.zone, ctx.isHome) / maxThreat) else v
      case "PassIntercepted" => if (ctx.isPossessionTeam) -0.02 else 0.022
      case "Dribble" =>
        val v = 0.01
        if (inAttack) v * (1.0 + 0.2 * zoneThreat(ctx.zone, ctx.isHome) / maxThreat) else v
      case "DribbleLost" => if (ctx.isPossessionTeam) -0.018 else 0.015
      case "Tackle" =>
        val v = 0.015
        if (inAttack) v * (1.0 + 0.2 * zoneThreat(ctx.zone, ctx.isHome) / maxThreat) else v
      case "Foul" => -0.012
      case "FreeKick" => 0.012
      case _ => 0.0
    }
    val scoreTight = math.abs(ctx.scoreHome - ctx.scoreAway) <= 1
    val tightBonus = if (scoreTight) 1.15 else 1.0
    base * timeWeight(ctx.minute) * tightBonus
  }
}
