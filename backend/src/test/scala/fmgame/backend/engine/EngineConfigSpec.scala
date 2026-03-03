package fmgame.backend.engine

import fmgame.backend.engine.{FormulaBasedVAEP, FormulaBasedxG}
import zio.*
import zio.test.*

object EngineConfigSpec extends ZIOSpecDefault {

  def spec = suite("EngineConfig")(
    test("fromConfig with default config returns formula models") {
      val config = EngineConfig()
      val models = EngineModelFactory.fromConfig(config)
      assertTrue(
        models.xg == FormulaBasedxG,
        models.vaep == FormulaBasedVAEP
      )
    },
    test("xGModelFromConfig formula returns FormulaBasedxG") {
      val xg = EngineModelFactory.xGModelFromConfig("formula", None)
      assertTrue(xg == FormulaBasedxG)
    },
    test("xGModelFromConfig unknown type falls back to FormulaBasedxG") {
      val xg = EngineModelFactory.xGModelFromConfig("unknown", None)
      assertTrue(xg == FormulaBasedxG)
    },
    test("vaepModelFromConfig formula returns FormulaBasedVAEP") {
      val vaep = EngineModelFactory.vaepModelFromConfig("formula", None)
      assertTrue(vaep == FormulaBasedVAEP)
    }
  )
}
