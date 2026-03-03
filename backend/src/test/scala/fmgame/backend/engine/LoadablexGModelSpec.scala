package fmgame.backend.engine

import zio.test.*

/** Testy LoadablexGModel: ładowanie z JSON, sigmoid w [0,1], fallback przy błędzie. */
object LoadablexGModelSpec extends ZIOSpecDefault {

  def spec = suite("LoadablexGModel")(
    test("fromJson with valid JSON returns model that gives xG in [0, 1]") {
      val model = LoadablexGModel.fromJson(LoadablexGModel.exampleCoefficientsJson)
      val ctx = ShotContext(zone = 10, distanceToGoal = 12.0, isHeader = false, minute = 45, scoreDiff = 0, pressureCount = 1)
      val xg = model.xGForShot(ctx)
      assertTrue(xg >= 0.0, xg <= 1.0)
    },
    test("fromJson with invalid JSON falls back to FormulaBasedxG") {
      val model = LoadablexGModel.fromJson("{}")
      val ctx = ShotContext(zone = 6, distanceToGoal = 20.0, isHeader = false, minute = 30, scoreDiff = 0)
      val xg = model.xGForShot(ctx)
      assertTrue(xg >= 0.0, xg <= 1.0)
    },
    test("fromCoefficients with 6 coefs produces different xG for different contexts") {
      val model = LoadablexGModel.fromCoefficients(-1.0, Array(0.0, -0.05, 0.0, 0.0, 0.0, 0.0))
      val close = model.xGForShot(ShotContext(10, 8.0, false, 50, 0, 0))
      val far = model.xGForShot(ShotContext(3, 35.0, false, 50, 0, 0))
      assertTrue(close > far)
    },
    test("FormulaBasedxG: angular pressure lowers xG for same distance/zone") {
      val base = FormulaBasedxG.xGForShot(
        ShotContext(zone = 10, distanceToGoal = 16.0, isHeader = false, minute = 60, scoreDiff = 0, pressureCount = 0, angularPressure = 0.0, gkDistance = 7.0)
      )
      val pressured = FormulaBasedxG.xGForShot(
        ShotContext(zone = 10, distanceToGoal = 16.0, isHeader = false, minute = 60, scoreDiff = 0, pressureCount = 2, angularPressure = 2.0, gkDistance = 7.0)
      )
      assertTrue(pressured < base)
    },
    test("FormulaBasedxG: GK further from goal increases xG") {
      val deepGk = FormulaBasedxG.xGForShot(
        ShotContext(zone = 10, distanceToGoal = 16.0, isHeader = false, minute = 60, scoreDiff = 0, pressureCount = 0, angularPressure = 0.0, gkDistance = 1.0)
      )
      val sweeperGk = FormulaBasedxG.xGForShot(
        ShotContext(zone = 10, distanceToGoal = 16.0, isHeader = false, minute = 60, scoreDiff = 0, pressureCount = 0, angularPressure = 0.0, gkDistance = 11.0)
      )
      assertTrue(sweeperGk > deepGk)
    },
    test("fromCoefficientsExtended with 8 coefs uses angularPressure and gkDistance") {
      val coefs8 = Array(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -0.1, 0.2)
      val model = LoadablexGModel.fromCoefficientsExtended(-1.0, coefs8)
      val lowAng = model.xGForShot(ShotContext(10, 12.0, false, 45, 0, 0, 0.5, 5.0))
      val highAng = model.xGForShot(ShotContext(10, 12.0, false, 45, 0, 0, 2.5, 5.0))
      val farGk = model.xGForShot(ShotContext(10, 12.0, false, 45, 0, 0, 0.5, 10.0))
      assertTrue(highAng < lowAng, farGk > lowAng)
    }
  )
}
