package fmgame.backend.engine

import zio.test.*

/** Testy stałych silnika (DOPRACOWANIA §2.10): wartości w oczekiwanych zakresach. */
object EngineConstantsSpec extends ZIOSpecDefault {

  def spec = suite("EngineConstants")(
    test("event type thresholds are in [0, 1] and ordered") {
      val thresholds = List(
        EngineConstants.EventPassThreshold,
        EngineConstants.EventShotThresholdBase,
        EngineConstants.EventFoulPenaltyThreshold,
        EngineConstants.EventClearanceThreshold,
        EngineConstants.EventCornerThreshold,
        EngineConstants.EventThrowInThreshold,
        EngineConstants.EventCrossThreshold,
        EngineConstants.EventInterceptThreshold,
        EngineConstants.EventDribbleThreshold,
        EngineConstants.EventDuelThreshold,
        EngineConstants.EventAerialDuelThreshold,
        EngineConstants.EventFreeKickThreshold,
        EngineConstants.EventOffsideThreshold,
        EngineConstants.EventSubThreshold
      )
      assertTrue(
        thresholds.forall(t => t >= 0.0 && t <= 1.0),
        thresholds == thresholds.sorted
      )
    },
    test("pass success bounds: min < base < max") {
      assertTrue(
        EngineConstants.PassSuccessMin < EngineConstants.PassSuccessBase,
        EngineConstants.PassSuccessBase < EngineConstants.PassSuccessMax
      )
    },
    test("pass difficulty min in [0, 1]") {
      assertTrue(
        EngineConstants.PassDifficultyMin >= 0.0,
        EngineConstants.PassDifficultyMin <= 1.0
      )
    },
    test("intercept cap and base non-negative, cap >= base") {
      assertTrue(
        EngineConstants.InterceptBase >= 0.0,
        EngineConstants.InterceptCap >= EngineConstants.InterceptBase,
        EngineConstants.InterceptCap <= 1.0
      )
    },
    test("fatigue and pitch control constants positive") {
      assertTrue(
        EngineConstants.FatigueBaseRate > 0.0,
        EngineConstants.FatigueIntensityMultiplier >= 1.0,
        EngineConstants.PitchControlTimeScale > 0.0,
        EngineConstants.PitchControlDistanceScale > 0.0
      )
    },
    test("xG constants positive") {
      assertTrue(
        EngineConstants.XGBaseDistanceFactor > 0.0,
        EngineConstants.XGZoneBonus > 0.0
      )
    },
    test("shot missed vs blocked: base in [0, 1], pressure bonus non-negative") {
      assertTrue(
        EngineConstants.ShotMissedVsBlockedBase >= 0.0,
        EngineConstants.ShotMissedVsBlockedBase <= 1.0,
        EngineConstants.ShotBlockedPressureBonus >= 0.0
      )
    },
    test("dribble coefficients and nearest defender weight in range") {
      assertTrue(
        EngineConstants.DribbleSuccessBase >= 0.0,
        EngineConstants.DribbleNearestDefenderWeight >= 0.0,
        EngineConstants.DribbleNearestDefenderWeight <= 1.0
      )
    }
  )
}
