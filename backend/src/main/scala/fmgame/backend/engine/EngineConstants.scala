package fmgame.backend.engine

/**
 * Stałe silnika meczu w jednym miejscu (DOPRACOWANIA_WYLICZAN_SYMULACJI §2.10).
 * Umożliwia kalibrację, presety i testy A/B bez zmiany logiki.
 */
object EngineConstants {

  // ----- Event type thresholds (proporcje typów zdarzeń) -----
  val EventPassThreshold: Double = 0.42
  val EventShotThresholdBase: Double = 0.445
  val EventFoulPenaltyThreshold: Double = 0.52
  val EventClearanceThreshold: Double = 0.53
  val EventCornerThreshold: Double = 0.54
  val EventThrowInThreshold: Double = 0.57
  val EventCrossThreshold: Double = 0.61
  val EventInterceptThreshold: Double = 0.67
  val EventDribbleThreshold: Double = 0.74
  val EventDuelThreshold: Double = 0.75
  val EventAerialDuelThreshold: Double = 0.76
  val EventFreeKickThreshold: Double = 0.77
  val EventOffsideThreshold: Double = 0.78
  val EventSubThreshold: Double = 0.79

  // ----- Pass success -----
  val PassSuccessBase: Double = 0.88
  val PassSuccessMin: Double = 0.62
  val PassSuccessMax: Double = 0.94
  /** Współczynnik trudności podania: mnożnik bazy. passDifficulty(zoneFrom, zoneTo, pressure) w [PassDifficultyMin, 1.0]. */
  val PassDifficultyMin: Double = 0.70
  /** Wpływ presji na odbiorcy (receiverPressure 0–6) na trudność. */
  val PassDifficultyPressureFactor: Double = 0.20
  /** Wpływ dystansu podania (|zoneTo - zoneFrom| 0–11) na trudność. */
  val PassDifficultyDistanceFactor: Double = 0.12

  // ----- Intercept -----
  val InterceptBase: Double = 0.05
  val InterceptControlFactor: Double = 0.12
  val InterceptCap: Double = 0.55
  /** Bonus do P(przechwyt) na jedną strefę dystansu podania (długie podanie = więcej czasu na reakcję). */
  val InterceptPerZoneDistance: Double = 0.008

  // ----- Fatigue -----
  val FatigueBaseRate: Double = 0.008
  /** Mnożnik zmęczenia przy wysokiej intensywności (strefy 7–12, kontratak). */
  val FatigueIntensityMultiplier: Double = 1.35

  // ----- Pitch Control (PitchModel) -----
  val PitchControlTimeScale: Double = 2.5
  val PitchControlDistanceScale: Double = 12.0
  val PitchControlFatigueFactor: Double = 0.5

  // ----- xG (FormulaBasedxG) -----
  val XGBaseDistanceFactor: Double = 18.0
  val XGZoneBonus: Double = 0.08

  // ----- Shot outcome (Saved / Missed / Blocked) -----
  /** Bazowy stosunek P(Missed) do P(Blocked) gdy nie Saved: 0.6 = 60% Missed, 40% Blocked. */
  val ShotMissedVsBlockedBase: Double = 0.6
  /** Przy wysokiej presji (pressureCount, angularPressure) rośnie P(Blocked). */
  val ShotBlockedPressureBonus: Double = 0.30
  /** P(placement = center) bazowo; wyższy composure/technique → bardziej w stronę center. */
  val ShotPlacementCenterBase: Double = 0.5

  // ----- Dribble -----
  val DribbleSuccessBase: Double = 0.55
  val DribbleSuccessDribblingCoef: Double = 0.008
  val DribbleSuccessAgilityCoef: Double = 0.006
  val DribbleSuccessDefTacklingCoef: Double = 0.004
  val DribbleSuccessDefAgilityCoef: Double = 0.002
  /** Waga najbliższego obrońcy przy dryblingu (reszta to średnia). 0.6 = 60% waga najbliższego. */
  val DribbleNearestDefenderWeight: Double = 0.6

  /** Maximum substitutions allowed per team per match (modern rules: 5). */
  val MaxSubstitutions: Int = 5
}
