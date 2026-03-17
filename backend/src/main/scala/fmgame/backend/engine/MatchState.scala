package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Stan meczu zdarzenie po zdarzeniu. Maszyna stanów: każdy event zmienia stan (czas, wynik, piłka, pozycje, kontrolę).
 * Czas: totalSeconds 0–5400 (90 min); minute = totalSeconds/60 do wyświetlania.
 */
case class MatchState(
  minute: Int,
  /** Czas meczu w sekundach (0–5400). Używany do gęstego generowania zdarzeń (~1 zdarzenie na 2–8 s). */
  totalSeconds: Int,
  homeGoals: Int,
  awayGoals: Int,
  ballZone: Int,
  possession: Option[TeamId],
  homePositions: List[PlayerPosition],
  awayPositions: List[PlayerPosition],
  pitchControlByZone: Map[Int, (Double, Double)],
  dxtByZone: Map[Int, Double],
  lastEventType: Option[String],
  homeTeamId: TeamId,
  awayTeamId: TeamId,
  homeFormation: String,
  awayFormation: String,
  homePlayerIds: List[PlayerId],
  awayPlayerIds: List[PlayerId],
  homeTriggerConfig: Option[TriggerConfig],
  awayTriggerConfig: Option[TriggerConfig],
  /** Zmęczenie 0.0–1.0 per gracz; wpływa na Pitch Control i szansę błędu. */
  fatigueByPlayer: Map[PlayerId, Double] = Map.empty,
  /** True jeśli ostatnie zdarzenie to odzyskanie piłki (PassIntercepted/DribbleLost) w strefie counterTriggerZone. */
  justRecoveredInCounterZone: Boolean = false,
  /** Ostatnia routine stałego fragmentu (corner/freeKick) – używana do modyfikatora xG przy następnym strzale. */
  lastSetPieceRoutine: Option[String] = None,
  /** Obrona rogu przez drużynę defensywną ("zonal"/"man"/"mixed") – używana przy strzale po rogu. */
  lastSetPieceDefense: Option[String] = None,
  /** Szerokość ustawienia drużyny gospodarzy (1.0 = normal). */
  homeWidthScale: Double = 1.0,
  /** Szerokość ustawienia drużyny gości (1.0 = normal). */
  awayWidthScale: Double = 1.0,
  /** Liczba wykonanych zmian drużyny gospodarzy (max 5). */
  homeSubsUsed: Int = 0,
  /** Liczba wykonanych zmian drużyny gości (max 5). */
  awaySubsUsed: Int = 0,
  /** Gracze z żółtą kartką. */
  yellowCards: Set[PlayerId] = Set.empty,
  /** Gracze usunięci z boiska (czerwona/2x żółta/kontuzja bez zmiany). */
  sentOff: Set[PlayerId] = Set.empty
) {
  def possessionHome: Boolean = possession.contains(homeTeamId)
  def scoreDiff: Int = homeGoals - awayGoals
}

object MatchState {
  def initial(
    homeTeamId: TeamId,
    awayTeamId: TeamId,
    homeFormation: String,
    awayFormation: String,
    homePlayerIds: List[PlayerId],
    awayPlayerIds: List[PlayerId],
    homeTriggerConfig: Option[TriggerConfig],
    awayTriggerConfig: Option[TriggerConfig],
    homeCustomPositions: Option[List[(Double, Double)]] = None,
    awayCustomPositions: Option[List[(Double, Double)]] = None,
    homeWidthScale: Double = 1.0,
    awayWidthScale: Double = 1.0,
    paceAccByPlayer: Option[Map[PlayerId, (Int, Int)]] = None
  ): MatchState = {
    val ballZone = PitchModel.zoneFromXY(PitchModel.PitchLength / 2.0, PitchModel.PitchWidth / 2.0)
    val (homePos, awayPos) = PositionGenerator.all22Positions(
      homeFormation, homePlayerIds, awayFormation, awayPlayerIds, ballZone, possessionHome = true,
      homeCustomPositions, awayCustomPositions, homeWidthScale, awayWidthScale
    )
    val control = PitchControl.controlByZoneWithFatigue(homePos, awayPos, None, paceAccByPlayer)
    val dxt = DxT.threatMap(control, possessionHome = true)
    MatchState(
      minute = 0,
      totalSeconds = 0,
      homeGoals = 0,
      awayGoals = 0,
      ballZone = ballZone,
      possession = Some(homeTeamId),
      homePositions = homePos,
      awayPositions = awayPos,
      pitchControlByZone = control,
      dxtByZone = dxt,
      lastEventType = None,
      homeTeamId = homeTeamId,
      awayTeamId = awayTeamId,
      homeFormation = homeFormation,
      awayFormation = awayFormation,
      homePlayerIds = homePlayerIds,
      awayPlayerIds = awayPlayerIds,
      homeTriggerConfig = homeTriggerConfig,
      awayTriggerConfig = awayTriggerConfig,
      fatigueByPlayer = (homePlayerIds ++ awayPlayerIds).map(_ -> 0.0).toMap,
      justRecoveredInCounterZone = false,
      lastSetPieceRoutine = None,
      homeWidthScale = homeWidthScale,
      awayWidthScale = awayWidthScale
    )
  }
}
