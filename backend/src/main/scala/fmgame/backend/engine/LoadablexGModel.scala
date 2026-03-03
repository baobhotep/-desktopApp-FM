package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Model xG ładowany z pliku JSON (współczynniki regresji logistycznej).
 * Dowód, że można podpiąć „zewnętrzny" model bez XGBoost/LightGBM w runtime:
 * współczynniki mogą pochodzić z modelu wytrenowanego w Pythonie i wyeksportowanego do JSON.
 *
 * Format JSON: { "intercept": double, "coefs": [zone, distanceToGoal, isHeader, minute, scoreDiff, pressureCount] }
 * xG = sigmoid(intercept + dot(coefs, features)); features w tej samej kolejności co coefs.
 */
object LoadablexGModel {

  private def sigmoid(x: Double): Double = {
    val e = math.exp(-x)
    if (e.isInfinity) 0.0 else 1.0 / (1.0 + e)
  }

  /** Wektor cech z ShotContext (6 cech: zone, distance, isHeader, minute, scoreDiff, pressureCount). */
  def features(ctx: ShotContext): Array[Double] = Array(
    ctx.zone.toDouble,
    ctx.distanceToGoal,
    if (ctx.isHeader) 1.0 else 0.0,
    ctx.minute.toDouble / 90.0,
    ctx.scoreDiff.toDouble,
    ctx.pressureCount.toDouble
  )

  /** Wektor cech rozszerzony (8 cech: + angularPressure, gkDistance) – do modelu ML. */
  def featuresExtended(ctx: ShotContext): Array[Double] = Array(
    ctx.zone.toDouble,
    ctx.distanceToGoal,
    if (ctx.isHeader) 1.0 else 0.0,
    ctx.minute.toDouble / 90.0,
    ctx.scoreDiff.toDouble,
    ctx.pressureCount.toDouble,
    ctx.angularPressure,
    ctx.gkDistance / 12.0
  )

  /**
   * Tworzy xGModel z współczynników (intercept + 6 współczynników).
   * Jeśli coefs ma inny rozmiar, zwraca fallback (FormulaBasedxG).
   */
  def fromCoefficients(intercept: Double, coefs: Array[Double]): xGModel =
    if (coefs.length != 6) fromCoefficientsExtended(intercept, coefs)
    else new xGModel {
      def xGForShot(ctx: ShotContext): Double = {
        val f = features(ctx)
        val z = intercept + (0 until 6).map(i => coefs(i) * f(i)).sum
        sigmoid(z).max(0.01).min(0.95)
      }
    }

  /**
   * Tworzy xGModel z 8 współczynników (angularPressure, gkDistance). ML-ready.
   */
  def fromCoefficientsExtended(intercept: Double, coefs: Array[Double]): xGModel =
    if (coefs.length != 8) FormulaBasedxG
    else new xGModel {
      def xGForShot(ctx: ShotContext): Double = {
        val f = featuresExtended(ctx)
        val z = intercept + (0 until 8).map(i => coefs(i) * f(i)).sum
        sigmoid(z).max(0.01).min(0.95)
      }
    }

  /**
   * Ładuje model z JSON (Circe). Oczekiwany format: {"intercept": number, "coefs": [6 lub 8 liczb]}.
   * 8 współczynników = model rozszerzony (angularPressure, gkDistance).
   * W razie błędu parsowania zwraca FormulaBasedxG.
   */
  def fromJson(json: String): xGModel = {
    import io.circe.parser.parse
    import io.circe.generic.auto._
    parse(json).flatMap(_.as[CoeffsDto]).toOption match {
      case Some(dto) =>
        val arr = dto.coefs.map(_.toDouble).toArray
        if (arr.length == 8) fromCoefficientsExtended(dto.intercept, arr)
        else fromCoefficients(dto.intercept, arr)
      case _ => FormulaBasedxG
    }
  }

  private case class CoeffsDto(intercept: Double, coefs: List[Double])

  /**
   * Przykładowe współczynniki (zbliżone do FormulaBasedxG: wyższe xG przy mniejszej odległości i niższej strefie).
   * Można zastąpić współczynnikami z modelu XGBoost/LightGBM wyeksportowanymi do JSON.
   */
  val exampleCoefficientsJson: String =
    """{"intercept":-1.5,"coefs":[-0.08,-0.04,0.0,0.2,0.0,-0.1]}"""
}
