package fmgame.shared.api

import fmgame.shared.domain.*

// DTOs for API — use Long for timestamps (epoch millis) for cross-platform compatibility

case class UserDto(
  id: String,
  email: String,
  displayName: String,
  createdAt: Long
)

case class RegisterRequest(email: String, password: String, displayName: String)
case class LoginRequest(email: String, password: String)
case class LoginResponse(token: String, user: UserDto)

case class LeagueDto(
  id: String,
  name: String,
  teamCount: Int,
  currentMatchday: Int,
  totalMatchdays: Int,
  seasonPhase: String,
  homeAdvantage: Double,
  startDate: Option[String],
  createdByUserId: String,
  createdAt: Long,
  timezone: String,
  leagueSystemName: Option[String] = None,
  tier: Option[Int] = None
)

case class CreateLeagueRequest(
  name: String,
  teamCount: Int,
  myTeamName: String,
  timezone: Option[String]
)

case class CreateLeagueResponse(league: LeagueDto, team: TeamDto)

case class TeamDto(
  id: String,
  leagueId: String,
  name: String,
  ownerType: String,
  ownerUserId: Option[String],
  ownerBotId: Option[String],
  budget: Double,
  /** Rating Elo (1500 domyślnie), aktualizowany po meczach. */
  eloRating: Double = 1500.0,
  /** Nazwa trenera/managera (dla botów z presetu). */
  managerName: Option[String] = None,
  createdAt: Long
)

case class PlayerDto(
  id: String,
  teamId: String,
  firstName: String,
  lastName: String,
  preferredPositions: List[String],
  injury: Option[String],
  freshness: Double,
  morale: Double,
  /** Atrybuty fizyczne (np. pace, stamina) – wartości 1–20. */
  physical: Map[String, Int] = Map.empty,
  /** Atrybuty techniczne (np. passing, shooting). */
  technical: Map[String, Int] = Map.empty,
  /** Atrybuty mentalne (np. composure, decisions). */
  mental: Map[String, Int] = Map.empty,
  /** Cechy (np. injuryProne) – wartości 1–20. */
  traits: Map[String, Int] = Map.empty,
  /** Overall 1–20 (pozycyjny). */
  overall: Double = 0.0,
  /** Średnia atrybutów fizycznych 1–20 (do pasków). */
  physicalAvg: Double = 0.0,
  /** Średnia atrybutów technicznych 1–20. */
  technicalAvg: Double = 0.0,
  /** Średnia atrybutów mentalnych 1–20. */
  mentalAvg: Double = 0.0,
  /** Średnia atrybutów defensywnych 1–20. */
  defenseAvg: Double = 0.0,
  /** Kondycja 0–1 (spada w meczu, regeneruje). */
  condition: Double = 1.0,
  /** Ostrość meczowa 0–1. */
  matchSharpness: Double = 1.0
)

case class GamePlanSnapshotDto(id: String, teamId: String, name: String, createdAt: Long)
case class GamePlanSnapshotDetailDto(id: String, teamId: String, name: String, gamePlanJson: String, createdAt: Long)

case class TableRowDto(
  teamId: String,
  teamName: String,
  position: Int,
  points: Int,
  played: Int,
  won: Int,
  drawn: Int,
  lost: Int,
  goalsFor: Int,
  goalsAgainst: Int,
  goalDifference: Int
)

/** Zbiorcze statystyki sezonu dla zawodnika w lidze. */
case class PlayerSeasonStatsRowDto(
  playerId: String,
  playerName: String,
  teamId: String,
  teamName: String,
  goals: Int,
  assists: Int
)

/** Statystyki zawodników w lidze – król strzelców, lider asyst itp. */
case class LeaguePlayerStatsDto(
  topScorers: List[PlayerSeasonStatsRowDto],
  topAssists: List[PlayerSeasonStatsRowDto]
)

/** Zaawansowane statystyki sezonowe zawodnika (Data Hub light). */
case class PlayerSeasonAdvancedStatsRowDto(
  playerId: String,
  playerName: String,
  teamId: String,
  teamName: String,
  matches: Int,
  minutes: Int,
  goals: Int,
  assists: Int,
  shots: Int,
  shotsOnTarget: Int,
  xg: Double,
  keyPasses: Int,
  passes: Int,
  passesCompleted: Int,
  tackles: Int,
  interceptions: Int
)

case class LeaguePlayerAdvancedStatsDto(
  rows: List[PlayerSeasonAdvancedStatsRowDto]
)

case class TrainingPlanDto(
  teamId: String,
  /** Lista 7 sesji: Pon..Niedz. */
  week: List[String],
  updatedAt: Long
)

case class UpsertTrainingPlanRequest(week: List[String])

// --- Scouting / Recruitment (MVP) ---

case class LeaguePlayerRowDto(
  playerId: String,
  playerName: String,
  teamId: String,
  teamName: String,
  preferredPositions: List[String],
  overall: Double
)

case class LeaguePlayersDto(players: List[LeaguePlayerRowDto])

case class ShortlistEntryDto(teamId: String, playerId: String, playerName: String, fromTeamName: String, createdAt: Long)
case class AddToShortlistRequest(playerId: String)

case class ScoutingReportDto(
  id: String,
  teamId: String,
  playerId: String,
  playerName: String,
  rating: Double,
  notes: String,
  createdAt: Long
)
case class CreateScoutingReportRequest(playerId: String, rating: Double, notes: String)

case class ContractDto(
  id: String,
  playerId: String,
  teamId: String,
  playerName: String,
  weeklySalary: Double,
  startMatchday: Int,
  endMatchday: Int,
  releaseClause: Option[Double]
)

case class MatchDto(
  id: String,
  leagueId: String,
  matchday: Int,
  homeTeamId: String,
  awayTeamId: String,
  scheduledAt: Long,
  status: String,
  homeGoals: Option[Int],
  awayGoals: Option[Int],
  refereeId: String,
  /** Nazwa sędziego (z słownika ligi), gdy dostępna. */
  refereeName: Option[String] = None
)

case class InvitationDto(
  id: String,
  leagueId: String,
  invitedUserId: String,
  invitedByUserId: Option[String] = None,
  token: String,
  status: String,
  expiresAt: Long
)

case class AcceptInvitationRequest(token: String, teamName: String)
case class AcceptInvitationResponse(league: LeagueDto, team: TeamDto)

case class InviteRequest(email: String)

case class StartSeasonRequest(startDate: Option[String])

case class AddBotsRequest(count: Int)

case class MatchEventDto(minute: Int, eventType: String, actorPlayerId: Option[String], secondaryPlayerId: Option[String], teamId: Option[String], zone: Option[Int], outcome: Option[String], metadata: Map[String, String])
/** Pełne statystyki meczu (KONTRAKTY §2.3). Wszystkie listy to [home, away]. */
case class MatchSummaryDto(
  possessionPercent: List[Double],
  homeGoals: Int,
  awayGoals: Int,
  shotsTotal: List[Int],
  shotsOnTarget: List[Int],
  shotsOffTarget: List[Int],
  shotsBlocked: List[Int],
  bigChances: List[Int],
  xgTotal: List[Double],
  passesTotal: List[Int],
  passesCompleted: List[Int],
  passAccuracyPercent: List[Double],
  passesInFinalThird: List[Int],
  crossesTotal: List[Int],
  crossesSuccessful: List[Int],
  longBallsTotal: List[Int],
  longBallsSuccessful: List[Int],
  tacklesTotal: List[Int],
  tacklesWon: List[Int],
  interceptions: List[Int],
  clearances: List[Int],
  blocks: List[Int],
  saves: List[Int],
  goalsConceded: List[Int],
  fouls: List[Int],
  yellowCards: List[Int],
  redCards: List[Int],
  foulsSuffered: List[Int],
  corners: List[Int],
  cornersWon: List[Int],
  throwIns: List[Int],
  freeKicksWon: List[Int],
  offsides: List[Int],
  duelsWon: Option[List[Int]] = None,
  aerialDuelsWon: Option[List[Int]] = None,
  possessionLost: Option[List[Int]] = None,
  vaepTotal: Option[List[Double]] = None,
  wpaFinal: Option[Double] = None,
  fieldTilt: Option[List[Double]] = None,
  ppda: Option[List[Double]] = None,
  /** Tortuosity ścieżki piłki (gBRI). */
  ballTortuosity: Option[Double] = None,
  /** Metabolic load (przybliżenie). */
  metabolicLoad: Option[Double] = None,
  /** xT wartości stref 1–24. */
  xtByZone: Option[List[Double]] = None,
  /** Kontuzje w meczu [gospodarze, goście]. */
  injuries: Option[List[Int]] = None,
  /** Voronoi-like: udział gospodarzy w akcjach w strefach 1–24 (EPV/dominacja). */
  homeShareByZone: Option[List[Double]] = None,
  /** I-VAEP: VAEP per typ zdarzenia per zawodnik (playerId -> eventType -> value). */
  vaepBreakdownByPlayer: Option[Map[String, Map[String, Double]]] = None,
  /** Pressing: liczba akcji defensywnych per zawodnik. */
  pressingByPlayer: Option[Map[String, Int]] = None,
  /** Szacowany dystans (m) per zawodnik. */
  estimatedDistanceByPlayer: Option[Map[String, Double]] = None,
  /** Player Influence: akcje per strefa per zawodnik (playerId -> zone -> count). */
  influenceByPlayer: Option[Map[String, Map[String, Int]]] = None,
  /** C-OBSO: średnia liczba obrońców w stożku per strefa 1–24. */
  avgDefendersInConeByZone: Option[List[Double]] = None,
  /** C-OBSO: średnia odległość GK per strefa 1–24. */
  avgGkDistanceByZone: Option[List[Double]] = None,
  /** Stałe fragmenty: aktywność stref per routine (np. Corner:default -> 12 liczb). */
  setPieceZoneActivity: Option[Map[String, List[Int]]] = None,
  /** Pressing w połowie przeciwnika per zawodnik. */
  pressingInOppHalfByPlayer: Option[Map[String, Int]] = None,
  /** Tortuosity biegów zawodników per zawodnik. */
  playerTortuosityByPlayer: Option[Map[String, Double]] = None,
  /** Metabolic load per zawodnik. */
  metabolicLoadByPlayer: Option[Map[String, Double]] = None,
  /** IWP (pojedynki) per zawodnik. */
  iwpByPlayer: Option[Map[String, Double]] = None,
  /** NMF: wagi 2 komponentów per routine. */
  setPiecePatternW: Option[Map[String, List[Double]]] = None,
  /** NMF: 2 wektory stref (strefa -> wartość). */
  setPiecePatternH: Option[List[Map[String, Double]]] = None,
  /** GMM: klaster (0/1) per routine. */
  setPieceRoutineCluster: Option[Map[String, Int]] = None,
  /** Prognoza Poisson: [P(wygrana gosp.), P(remis), P(wygrana gości)]. */
  poissonPrognosis: Option[List[Double]] = None,
  /** Voronoi z centrum: lista 24 (udział gosp. 0/1 per strefa). */
  voronoiCentroidByZone: Option[List[Double]] = None,
  /** xPass per zawodnik. */
  passValueByPlayer: Option[Map[String, Double]] = None,
  /** xPass suma [home, away]. */
  passValueTotal: Option[List[Double]] = None,
  /** xPass under pressure suma [home, away]. */
  passValueUnderPressureTotal: Option[List[Double]] = None,
  /** xPass under pressure per zawodnik. */
  passValueUnderPressureByPlayer: Option[Map[String, Double]] = None,
  /** Influence score per zawodnik. */
  influenceScoreByPlayer: Option[Map[String, Double]] = None,
  /** Najważniejsze momenty meczu (10–15). */
  highlights: Option[List[Map[String, String]]] = None
)
/** Posession w czasie: udział gosp. w posiadaniu w 6 segmentach 15-min (0–15, …, 75–90). */
case class MatchLogDto(
  events: List[MatchEventDto],
  summary: Option[MatchSummaryDto] = None,
  total: Option[Int] = None,
  matchReport: Option[String] = None,
  /** Udział gospodarzy w posiadaniu per segment 15 min (6 wartości 0.0–1.0). */
  possessionBySegment: Option[List[Double]] = None,
  /** Pressing: akcje defensywne (Tackle, PassIntercepted) gosp. per strefa 1–24. */
  pressByZoneHome: Option[List[Int]] = None,
  /** Pressing: akcje defensywne gości per strefa 1–24. */
  pressByZoneAway: Option[List[Int]] = None
)
case class CreatePressConferenceRequest(phase: String, tone: String)
case class LineupSlotDto(playerId: String, positionSlot: String)
case class MatchSquadDto(id: String, matchId: String, teamId: String, lineup: List[LineupSlotDto], source: String)

case class TransferWindowDto(id: String, leagueId: String, openAfterMatchday: Int, closeBeforeMatchday: Int, status: String)
case class TransferOfferDto(
  id: String,
  windowId: String,
  fromTeamId: String,
  toTeamId: String,
  playerId: String,
  amount: Double,
  status: String,
  createdAt: Long,
  respondedAt: Option[Long],
  /** Kwota kontroferty (gdy status = Countered). */
  counterAmount: Option[Double] = None,
  /** Nazwa drużyny kupującej (kto składa ofertę). */
  fromTeamName: Option[String] = None,
  /** Nazwa drużyny sprzedającej (właściciel zawodnika). */
  toTeamName: Option[String] = None,
  /** Imię i nazwisko zawodnika. */
  playerName: Option[String] = None
)
case class CreateTransferOfferRequest(windowId: String, toTeamId: String, playerId: String, amount: Double)
case class CounterTransferOfferRequest(counterAmount: Double)

case class SubmitMatchSquadRequest(lineup: List[LineupSlotDto], gamePlanJson: String)
case class UpdatePlayerRequest(
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  preferredPositions: Option[List[String]] = None,
  physical: Option[Map[String, Int]] = None,
  technical: Option[Map[String, Int]] = None,
  mental: Option[Map[String, Int]] = None
)
case class SaveGamePlanRequest(name: String, gamePlanJson: String)

case class MetricsDto(matchCount: Int, totalGoals: Int, averageGoalsPerMatch: Double)
/** Odpowiedź z radą asystenta przed meczem: analiza rywala + sugestie taktyczne. */
case class AssistantTipDto(
  tip: String,
  opponentFormation: Option[String] = None,
  opponentStrengths: Option[List[String]] = None,
  opponentWeaknesses: Option[List[String]] = None,
  tacticalSuggestions: Option[List[String]] = None,
  keyPlayersToWatch: Option[List[String]] = None,
  suggestedFormations: Option[List[String]] = None
)
/** Prognoza meczu (Elo): P(wygrana gosp.), P(remis), P(wygrana gości). */
case class MatchPrognosisDto(
  matchId: String,
  homeTeamId: String,
  awayTeamId: String,
  homeName: String,
  awayName: String,
  pHome: Double,
  pDraw: Double,
  pAway: Double
)
/** Porównanie dwóch zawodników (atrybuty + statystyki sezonu). */
case class ComparePlayersDto(
  player1: PlayerDto,
  player2: PlayerDto,
  stats1: Option[PlayerSeasonAdvancedStatsRowDto],
  stats2: Option[PlayerSeasonAdvancedStatsRowDto]
)
case class ErrorBody(code: String, message: String)
