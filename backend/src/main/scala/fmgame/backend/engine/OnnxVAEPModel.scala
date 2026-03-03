package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Model VAEP ładowany z pliku ONNX (P_scores / P_concedes lub pojedynczy model wartości).
 * Aby włączyć: dodaj zależność onnxruntime i zaimplementuj loadImpl.
 * Bez zależności zwraca None (użyj FormulaBasedVAEP).
 */
object OnnxVAEPModel {

  def load(path: String): Option[VAEPModel] =
    try loadImpl(path)
    catch { case _: Throwable => None }

  private def loadImpl(path: String): Option[VAEPModel] = None
}
