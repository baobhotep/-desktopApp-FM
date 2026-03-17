package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/** Contract types for match simulation (KONTRAKTY §1). Silnik jest czystym modułem obliczeniowym. */

/** Strefy w których drużyna włącza pressing; strefa kontrataku (odzyskanie piłki). Zaawansowana logika botów. */
case class TriggerConfig(
  pressZones: List[Int] = Nil,
  counterTriggerZone: Option[Int] = None,
  /** Obrona stałych fragmentów: "zonal" (-15% xG rogu), "man" (-10% xG, +5% faul), "mixed"/None = bez zmian. */
  setPieceDefense: Option[String] = None
)

/** Instrukcje drużynowe (FM-style: tempo, szerokość, podania, pressing). */
case class TeamInstructions(
  tempo: Option[String] = None,           // "lower", "normal", "higher"
  width: Option[String] = None,          // "narrow", "normal", "wide"
  passingDirectness: Option[String] = None, // "shorter", "normal", "direct"
  pressingIntensity: Option[String] = None  // "lower", "normal", "higher"
)

/** Instrukcje per pozycja/slot (FM-style: pressing, tackle, mark). Key = slot name, value = map instruction key -> value. */
case class PlayerInstructionsValue(
  pressIntensity: Option[String] = None,  // "more_urgent", "less_urgent"
  tackle: Option[String] = None,          // "harder", "ease_off"
  mark: Option[String] = None            // "tighter", "specific_player"
)

case class SetPiecesInput(
  cornerTakerPlayerId: Option[PlayerId] = None,
  freeKickTakerPlayerId: Option[PlayerId] = None,
  penaltyTakerPlayerId: Option[PlayerId] = None,
  cornerRoutine: Option[String] = None,   // "near_post", "far_post", "short"
  freeKickRoutine: Option[String] = None  // "direct", "cross"
)

case class OppositionInstruction(
  targetPlayerId: PlayerId,
  pressIntensity: Option[String] = None, // "more_urgent"
  tackle: Option[String] = None,         // "harder"
  mark: Option[String] = None            // "tighter"
)

/** Minimal GamePlan placeholder for engine input (full GamePlan from FORMACJE can replace later). FORMACJE §13.4: ThrowInConfig. */
case class GamePlanInput(
  formationName: String = "4-3-3",
  /** W FullMatchEngine: wykonawca wrzutu z autu (defaultTakerPlayerId). */
  throwInConfig: Option[fmgame.shared.domain.ThrowInConfig] = None,
  triggerConfig: Option[TriggerConfig] = None,
  /** Własne pozycje 11 slotów: (x, y) znormalizowane 0–1. */
  customPositions: Option[List[(Double, Double)]] = None,
  /** Role per slot (np. "CDM" -> "anchor"). Używane w FullMatchEngine: bonus do podań (playmaker), strzałów/xG (forward). */
  slotRoles: Option[Map[String, String]] = None,
  /** Instrukcje drużynowe (tempo, width, passing, pressing). Używane: zmęczenie (tempo, pressing), rozstawienie (width), szansa podania (passingDirectness). */
  teamInstructions: Option[TeamInstructions] = None,
  /** Instrukcje per slot (slot name -> map instruction key -> value). Używane: passBonus (passing), shotTendency (shooting), defenderPressBonus (pressIntensity more_urgent). */
  playerInstructions: Option[Map[String, Map[String, String]]] = None,
  /** Stałe fragmenty: wykonawcy rogów/wolnych/karnych; routine (corner/freeKick) wpływa na xG następnego strzału. */
  setPieces: Option[SetPiecesInput] = None,
  /** Instrukcje na rywala (MVP). */
  oppositionInstructions: Option[List[OppositionInstruction]] = None,
  /** Formacja w obronie (bez piłki). Gdy brak – silnik używa formacji ataku. */
  defenseFormationName: Option[String] = None,
  /** Pozycje 11 slotów w obronie (x,y 0–1). */
  defenseCustomPositions: Option[List[(Double, Double)]] = None
)

case class MatchTeamInput(
  teamId: TeamId,
  players: List[PlayerMatchInput],
  lineup: Map[PlayerId, String]
)

case class PlayerMatchInput(
  player: Player,
  freshness: Double,
  morale: Double,
  /** Minuty rozegrane w ostatnich meczach (np. ostatnie 3); do ACWR w ryzyku kontuzji. */
  recentMinutesPlayed: Option[Int] = None
)

case class RefereeInput(strictness: Double)

case class PositionAttrStats(mean: Double, stddev: Double)

/** League context for Z-Score (IWP). Key = position slot string e.g. "GK", "CB". */
case class LeagueContextInput(
  positionStats: Map[String, Map[String, PositionAttrStats]],
  /** Mnożnik kalibracji xG (np. 1.05 = liga z wyższą realizacją). xG Next-Gen: opcjonalna korekta ligowa. */
  xgCalibration: Option[Double] = None
)

case class MatchEngineInput(
  homeTeam: MatchTeamInput,
  awayTeam: MatchTeamInput,
  homePlan: GamePlanInput,
  awayPlan: GamePlanInput,
  homeAdvantage: Double,
  referee: RefereeInput,
  leagueContext: LeagueContextInput,
  randomSeed: Option[Long],
  xgModelOverride: Option[xGModel] = None,
  vaepModelOverride: Option[VAEPModel] = None
)

case class MatchEngineResult(
  homeGoals: Int,
  awayGoals: Int,
  events: List[MatchEventRecord],
  analytics: Option[MatchAnalytics]
)

/** Statystyki węzła w sieci podań (Passing Network). */
case class PassingNodeStats(
  passesAttempted: Int,
  passesCompleted: Int,
  received: Int
)

case class MatchAnalytics(
  vaepByPlayer: Map[PlayerId, Double],
  wpaTimeline: List[(Int, Double)],
  possessionPercent: (Double, Double),
  shotCount: (Int, Int),
  xgTotal: (Double, Double),
  vaepTotal: (Double, Double) = (0.0, 0.0),
  wpaFinal: Double = 0.5,
  /** Field Tilt: udział drużyny w kontaktach w tercji ataku (strefy 9–12). (homeShare, awayShare). */
  fieldTilt: Option[(Double, Double)] = None,
  /** PPDA (Passes per Defensive Action) w strefie budowania (1–6). (homePPDA, awayPPDA). */
  ppda: Option[(Double, Double)] = None,
  /** xG Chain: suma xG strzałów, w których zawodnik uczestniczył w łańcuchu. */
  xgChainByPlayer: Map[PlayerId, Double] = Map.empty,
  /** xG Buildup: jak xG Chain, ale bez ostatnich dwóch kontaktów przed strzałem. */
  xgBuildupByPlayer: Map[PlayerId, Double] = Map.empty,
  /** Sieć podań: statystyki per zawodnik. */
  passingNodeStats: Map[PlayerId, PassingNodeStats] = Map.empty,
  /** Betweenness centrality w sieci podań (udział w najkrótszych ścieżkach). */
  betweennessByPlayer: Map[PlayerId, Double] = Map.empty,
  /** PageRank w sieci podań (ważone znaczenie w przepływie piłki). */
  pageRankByPlayer: Map[PlayerId, Double] = Map.empty,
  /** Współczynnik clusteringu w sieci podań (0–1). */
  clusteringByPlayer: Map[PlayerId, Double] = Map.empty,
  /** xT (Expected Threat) wartości stref po value iteration. */
  xtValueByZone: Map[Int, Double] = Map.empty,
  /** OBSO (prawdopodobieństwo strzału ze strefy) per strefa. */
  obsoByZone: Map[Int, Double] = Map.empty,
  /** Tortuosity ścieżki piłki (stosunek drogi do linii prostej). */
  ballTortuosity: Option[Double] = None,
  /** Metabolic load (przybliżenie: suma odległości przejść stref). */
  metabolicLoad: Double = 0.0,
  /** Nash karne: prawdopodobieństwo strzelca w lewo (jeśli było karne). */
  nashPenaltyShooterLeft: Option[Double] = None,
  /** Nash karne: prawdopodobieństwo bramkarza w lewo. */
  nashPenaltyGkLeft: Option[Double] = None,
  /** Voronoi-like: udział gospodarzy w akcjach w strefach 1–12 (0.0..1.0). */
  homeShareByZone: Map[Int, Double] = Map.empty,
  /** I-VAEP: rozbicie VAEP per typ zdarzenia per zawodnik. */
  vaepByPlayerByEventType: Map[PlayerId, Map[String, Double]] = Map.empty,
  /** Pressing: liczba akcji defensywnych (Tackle, PassIntercepted, DribbleLost jako wygrany) per gracz. */
  defensiveActionsByPlayer: Map[PlayerId, Int] = Map.empty,
  /** Szacowany dystans (m) per gracz z sekwencji stref, w których był aktorem. */
  estimatedDistanceByPlayer: Map[PlayerId, Double] = Map.empty,
  /** Player Influence: liczba akcji per strefa per gracz (strefy 1–12). */
  playerActivityByZone: Map[PlayerId, Map[Int, Int]] = Map.empty,
  /** C-OBSO: średni kontekst strzałów per strefa (avgDefendersInCone, avgGkDistance). */
  shotContextByZone: Map[Int, (Double, Double)] = Map.empty,
  /** Stałe fragmenty: aktywność stref per typ+routine (Corner:default itd.). */
  setPieceZoneActivity: Map[String, Map[Int, Int]] = Map.empty,
  /** Pressing w połowie przeciwnika: akcje defensywne w strefach 7–12 per gracz. */
  pressingInOppHalfByPlayer: Map[PlayerId, Int] = Map.empty,
  /** Tortuosity biegów zawodników (ścieżka stref / odcinek start–koniec) per gracz. */
  playerTortuosityByPlayer: Map[PlayerId, Double] = Map.empty,
  /** Metabolic load per gracz (dystans szac. × (1 + udział stref ataku)); metry. */
  metabolicLoadByPlayer: Map[PlayerId, Double] = Map.empty,
  /** IWP (Individual Worth/Pojedynki) – suma wkładu z Tackle, PassIntercepted, Dribble, Duel per gracz. */
  iwpByPlayer: Map[PlayerId, Double] = Map.empty,
  /** NMF: wagi 2 komponentów per routine (Corner:default -> [w1, w2]). */
  setPiecePatternW: Map[String, List[Double]] = Map.empty,
  /** NMF: 2 wektory stref (komponent -> strefa -> wartość). */
  setPiecePatternH: List[Map[Int, Double]] = Nil,
  /** GMM/K-means: klaster (0 lub 1) per routine. */
  setPieceRoutineCluster: Map[String, Int] = Map.empty,
  /** Prognoza Poisson z xG: (P(wygrana gosp.), P(remis), P(wygrana gości)). */
  poissonPrognosis: Option[(Double, Double, Double)] = None,
  /** Voronoi z centrum aktywności: strefa należy do gosp. (1.0) lub gości (0.0). */
  voronoiCentroidByZone: Map[Int, Double] = Map.empty,
  /** xPass: suma wartości podań (xT(strefa odbiorcy) − xT(strefa podania)) per gracz. */
  passValueByPlayer: Map[PlayerId, Double] = Map.empty,
  /** xPass suma per drużyna (home, away). */
  passValueTotal: (Double, Double) = (0.0, 0.0),
  /** xPass under pressure (receiverPressure ≥ 2) suma per drużyna (home, away). */
  passValueUnderPressureTotal: (Double, Double) = (0.0, 0.0),
  /** xPass under pressure per gracz. */
  passValueUnderPressureByPlayer: Map[PlayerId, Double] = Map.empty,
  /** Player Influence score: suma po strefach (aktywność × xT(strefa)) per gracz. */
  influenceScoreByPlayer: Map[PlayerId, Double] = Map.empty
)

sealed trait MatchEngineError
case class InvalidLineup(msg: String) extends MatchEngineError
case class EngineFault(cause: Throwable) extends MatchEngineError
