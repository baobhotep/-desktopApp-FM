package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Model VAEP ładowany z JSON (wagi per typ zdarzenia / wynik).
 * Format: {"weights": {"Pass": 0.02, "PassFailed": -0.03, "Shot": 0.04, "Goal": 0.28, ...}}
 * Klucz = eventType lub eventType + "Failed" gdy outcome != Success.
 * Nieznany klucz → 0.0.
 */
object LoadableVAEPModel {

  def keyFor(ctx: VAEPContext): String =
    if (ctx.outcome.contains("Success")) ctx.eventType else ctx.eventType + "Failed"

  def fromWeights(weights: Map[String, Double]): VAEPModel = new VAEPModel {
    def valueForEvent(ctx: VAEPContext): Double = weights.getOrElse(keyFor(ctx), 0.0)
  }

  def fromJson(json: String): VAEPModel = {
    import io.circe.parser.parse
    import io.circe.generic.auto._
    case class WeightsDto(weights: Map[String, Double])
    parse(json).flatMap(_.as[WeightsDto]).toOption match {
      case Some(dto) => fromWeights(dto.weights)
      case _         => FormulaBasedVAEP
    }
  }

  val exampleWeightsJson: String =
    """{"weights":{"Pass":0.02,"PassFailed":-0.03,"LongPass":0.015,"LongPassFailed":-0.025,"Shot":0.04,"Goal":0.28,"Cross":0.018,"CrossFailed":-0.018,"PassIntercepted":0.02,"Tackle":0.02,"Dribble":0.01,"DribbleLost":-0.02,"ThrowIn":0.008}}"""
}
