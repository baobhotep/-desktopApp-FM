package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Model xG ładowany z pliku ONNX (np. wyeksportowany z XGBoost/LightGBM w Pythonie).
 * Aby włączyć: dodaj do build.sbt backend: libraryDependencies += "com.microsoft.onnxruntime" % "onnxruntime" % "1.18.1",
 * i zaimplementuj loadImpl (zob. docs/ML_INTEGRACJA.md). Bez zależności zwraca None.
 */
object OnnxXGModel {

  def load(path: String): Option[xGModel] =
    try loadImpl(path)
    catch { case _: Throwable => None }

  private def loadImpl(path: String): Option[xGModel] = None
}
