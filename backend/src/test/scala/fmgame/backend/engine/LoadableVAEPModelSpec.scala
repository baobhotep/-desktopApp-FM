package fmgame.backend.engine

import fmgame.shared.domain.*
import zio.test.*

object LoadableVAEPModelSpec extends ZIOSpecDefault {

  def spec = suite("LoadableVAEPModel")(
    test("fromJson with valid JSON returns model that gives non-zero for known keys") {
      val model = LoadableVAEPModel.fromJson(LoadableVAEPModel.exampleWeightsJson)
      val ctxPass = VAEPContext("Pass", 5, Some("Success"), 10, 0, 0, Some(TeamId("h")), true)
      val ctxPassFail = VAEPContext("Pass", 5, Some("Intercepted"), 10, 0, 0, Some(TeamId("h")), true)
      assertTrue(model.valueForEvent(ctxPass) == 0.02, model.valueForEvent(ctxPassFail) == -0.03)
    },
    test("fromJson with invalid JSON falls back to FormulaBasedVAEP") {
      val model = LoadableVAEPModel.fromJson("{}")
      val ctx = VAEPContext("Pass", 5, Some("Success"), 10, 0, 0, Some(TeamId("h")), true)
      val v = model.valueForEvent(ctx)
      assertTrue(v != 0.0)
    }
  )
}
