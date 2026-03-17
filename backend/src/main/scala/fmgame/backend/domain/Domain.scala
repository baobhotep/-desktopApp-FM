package fmgame.backend.domain

import fmgame.shared.domain.*
import zio.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object IdGen {
  private def gen[A](f: () => A): ZIO[Any, Nothing, A] = ZIO.succeed(f())

  def userId: UserId                         = UserId.random()
  def leagueId: LeagueId                     = LeagueId.random()
  def teamId: TeamId                         = TeamId.random()
  def playerId: PlayerId                     = PlayerId.random()
  def matchId: MatchId                       = MatchId.random()
  def refereeId: RefereeId                   = RefereeId.random()
  def invitationId: InvitationId             = InvitationId.random()
  def matchSquadId: MatchSquadId             = MatchSquadId.random()
  def matchResultLogId: MatchResultLogId     = MatchResultLogId.random()
  def gamePlanSnapshotId: GamePlanSnapshotId = GamePlanSnapshotId.random()
  def transferWindowId: TransferWindowId     = TransferWindowId.random()
  def transferOfferId: TransferOfferId       = TransferOfferId.random()
  def contractId: ContractId                 = ContractId.random()
  def botId: BotId                           = BotId.random()
  def leagueContextId: LeagueContextId       = LeagueContextId.random()
  def token: String                          = java.util.UUID.randomUUID().toString

  def genUserId: ZIO[Any, Nothing, UserId]                         = gen(() => UserId.random())
  def genLeagueId: ZIO[Any, Nothing, LeagueId]                     = gen(() => LeagueId.random())
  def genTeamId: ZIO[Any, Nothing, TeamId]                         = gen(() => TeamId.random())
  def genPlayerId: ZIO[Any, Nothing, PlayerId]                     = gen(() => PlayerId.random())
  def genMatchId: ZIO[Any, Nothing, MatchId]                       = gen(() => MatchId.random())
  def genRefereeId: ZIO[Any, Nothing, RefereeId]                   = gen(() => RefereeId.random())
  def genInvitationId: ZIO[Any, Nothing, InvitationId]             = gen(() => InvitationId.random())
  def genMatchSquadId: ZIO[Any, Nothing, MatchSquadId]             = gen(() => MatchSquadId.random())
  def genMatchResultLogId: ZIO[Any, Nothing, MatchResultLogId]     = gen(() => MatchResultLogId.random())
  def genGamePlanSnapshotId: ZIO[Any, Nothing, GamePlanSnapshotId] = gen(() => GamePlanSnapshotId.random())
  def genTransferWindowId: ZIO[Any, Nothing, TransferWindowId]     = gen(() => TransferWindowId.random())
  def genTransferOfferId: ZIO[Any, Nothing, TransferOfferId]       = gen(() => TransferOfferId.random())
  def genBotId: ZIO[Any, Nothing, BotId]                           = gen(() => BotId.random())
  def genLeagueContextId: ZIO[Any, Nothing, LeagueContextId]       = gen(() => LeagueContextId.random())
  def genContractId: ZIO[Any, Nothing, ContractId]                 = gen(() => ContractId.random())
}

case class User(
  id: UserId,
  email: String,
  passwordHash: String,
  displayName: String,
  createdAt: Instant
)

case class League(
  id: LeagueId,
  name: String,
  teamCount: Int,
  currentMatchday: Int,
  totalMatchdays: Int,
  seasonPhase: SeasonPhase,
  homeAdvantage: Double,
  startDate: Option[LocalDate],
  createdByUserId: UserId,
  createdAt: Instant,
  timezone: ZoneId,
  /** Np. "English" – grupowanie lig w system (4 szczeble). */
  leagueSystemName: Option[String] = None,
  /** 1 = Premier League, 2 = Championship, itd. */
  tier: Option[Int] = None
)

case class Team(
  id: TeamId,
  leagueId: LeagueId,
  name: String,
  ownerType: TeamOwnerType,
  ownerUserId: Option[UserId],
  ownerBotId: Option[BotId],
  budget: BigDecimal,
  defaultGamePlanId: Option[GamePlanSnapshotId],
  createdAt: Instant,
  /** Rating Elo (domyślnie 1500). Aktualizowany po każdym meczu. */
  eloRating: Double = 1500.0,
  /** Opcjonalna nazwa trenera/ managera (dla botów z presetu, dla gracza do uzupełnienia). */
  managerName: Option[String] = None
)

enum TeamOwnerType:
  case Human
  case Bot

case class Player(
  id: PlayerId,
  teamId: TeamId,
  firstName: String,
  lastName: String,
  preferredPositions: Set[String],
  physical: Map[String, Int],
  technical: Map[String, Int],
  mental: Map[String, Int],
  traits: Map[String, Int],
  bodyParams: Map[String, Double],
  injury: Option[InjuryStatus],
  freshness: Double,
  morale: Double,
  /** Kondycja 0–1 (spada w meczu, regeneruje się). */
  condition: Double = 1.0,
  /** Ostrość meczowa 0–1 (rośnie przy grze, spada przy braku minut). */
  matchSharpness: Double = 1.0,
  createdAt: Instant
)

case class InjuryStatus(
  sinceMatchday: Int,
  returnAtMatchday: Int,
  severity: String
)

case class Referee(
  id: RefereeId,
  leagueId: LeagueId,
  name: String,
  strictness: Double
)

case class Match(
  id: MatchId,
  leagueId: LeagueId,
  matchday: Int,
  homeTeamId: TeamId,
  awayTeamId: TeamId,
  scheduledAt: Instant,
  status: MatchStatus,
  homeGoals: Option[Int],
  awayGoals: Option[Int],
  refereeId: RefereeId,
  resultLogId: Option[MatchResultLogId]
)

case class Invitation(
  id: InvitationId,
  leagueId: LeagueId,
  invitedUserId: UserId,
  invitedByUserId: UserId,
  token: String,
  status: InvitationStatus,
  createdAt: Instant,
  expiresAt: Instant
)

case class MatchResultLog(
  id: MatchResultLogId,
  matchId: MatchId,
  events: List[MatchEventRecord],
  summary: Option[MatchSummary],
  createdAt: Instant
)

case class MatchEventRecord(
  minute: Int,
  eventType: String,
  actorPlayerId: Option[PlayerId],
  secondaryPlayerId: Option[PlayerId],
  teamId: Option[TeamId],
  zone: Option[Int],
  outcome: Option[String],
  metadata: Map[String, String]
)

case class MatchSummary(
  possessionPercent: (Double, Double),
  homeGoals: Int,
  awayGoals: Int,
  shotsTotal: (Int, Int),
  shotsOnTarget: (Int, Int),
  shotsOffTarget: (Int, Int),
  shotsBlocked: (Int, Int),
  bigChances: (Int, Int),
  xgTotal: (Double, Double),
  passesTotal: (Int, Int),
  passesCompleted: (Int, Int),
  passAccuracyPercent: (Double, Double),
  passesInFinalThird: (Int, Int),
  crossesTotal: (Int, Int),
  crossesSuccessful: (Int, Int),
  longBallsTotal: (Int, Int),
  longBallsSuccessful: (Int, Int),
  tacklesTotal: (Int, Int),
  tacklesWon: (Int, Int),
  interceptions: (Int, Int),
  clearances: (Int, Int),
  blocks: (Int, Int),
  saves: (Int, Int),
  goalsConceded: (Int, Int),
  fouls: (Int, Int),
  yellowCards: (Int, Int),
  redCards: (Int, Int),
  foulsSuffered: (Int, Int),
  corners: (Int, Int),
  cornersWon: (Int, Int),
  throwIns: (Int, Int),
  freeKicksWon: (Int, Int),
  offsides: (Int, Int),
  duelsWon: Option[(Int, Int)],
  aerialDuelsWon: Option[(Int, Int)],
  possessionLost: Option[(Int, Int)],
  vaepTotal: Option[(Double, Double)],
  wpaFinal: Option[Double],
  /** Field Tilt: udział w kontaktach w tercji ataku. (homeShare, awayShare). */
  fieldTilt: Option[(Double, Double)] = None,
  /** PPDA w strefie budowania. (homePPDA, awayPPDA). */
  ppda: Option[(Double, Double)] = None,
  /** Tortuosity ścieżki piłki (gBRI). */
  ballTortuosity: Option[Double] = None,
  /** Metabolic load (przybliżenie). */
  metabolicLoad: Option[Double] = None,
  /** xT wartości stref 1–TotalZones (value iteration). */
  xtByZone: Option[List[Double]] = None,
  /** Kontuzje w meczu (gospodarze, goście). */
  injuries: (Int, Int) = (0, 0),
  /** Voronoi-like: udział gospodarzy w akcjach per strefa. */
  homeShareByZone: Option[List[Double]] = None,
  /** I-VAEP: VAEP per typ zdarzenia per zawodnik (playerId -> eventType -> value). */
  vaepBreakdownByPlayer: Option[Map[String, Map[String, Double]]] = None,
  /** Pressing: liczba akcji defensywnych per zawodnik. */
  pressingByPlayer: Option[Map[String, Int]] = None,
  /** Szacowany dystans (m) per zawodnik. */
  estimatedDistanceByPlayer: Option[Map[String, Double]] = None,
  /** Player Influence: akcje per strefa per zawodnik (playerId -> zone -> count). */
  influenceByPlayer: Option[Map[String, Map[String, Int]]] = None,
  /** C-OBSO: średnia liczba obrońców w stożku per strefa. */
  avgDefendersInConeByZone: Option[List[Double]] = None,
  /** C-OBSO: średnia odległość GK od bramki przy strzałach per strefa. */
  avgGkDistanceByZone: Option[List[Double]] = None,
  /** Stałe fragmenty: aktywność stref per routine. */
  setPieceZoneActivity: Option[Map[String, List[Int]]] = None,
  /** Pressing w połowie przeciwnika per zawodnik. */
  pressingInOppHalfByPlayer: Option[Map[String, Int]] = None,
  /** Tortuosity biegów zawodników (ścieżka/odcinek) per zawodnik. */
  playerTortuosityByPlayer: Option[Map[String, Double]] = None,
  /** Metabolic load (dystans × intensywność) per zawodnik. */
  metabolicLoadByPlayer: Option[Map[String, Double]] = None,
  /** IWP (Individual Worth – pojedynki) per zawodnik. */
  iwpByPlayer: Option[Map[String, Double]] = None,
  /** NMF: wagi 2 komponentów per routine. */
  setPiecePatternW: Option[Map[String, List[Double]]] = None,
  /** NMF: 2 wektory stref (komponent -> strefa -> wartość). */
  setPiecePatternH: Option[List[Map[String, Double]]] = None,
  /** GMM/K-means: klaster (0 lub 1) per routine. */
  setPieceRoutineCluster: Option[Map[String, Int]] = None,
  /** Prognoza Poisson z xG: (P(wygrana gosp.), P(remis), P(wygrana gości)). */
  poissonPrognosis: Option[(Double, Double, Double)] = None,
  /** Voronoi z centrum aktywności: strefa → udział gosp. (0 lub 1). */
  voronoiCentroidByZone: Option[List[Double]] = None,
  /** xPass: wartość podań (xT odbiorcy − xT nadawcy) per zawodnik. */
  passValueByPlayer: Option[Map[String, Double]] = None,
  /** xPass suma (home, away). */
  passValueTotal: Option[(Double, Double)] = None,
  /** xPass under pressure (receiverPressure ≥ 2) suma (home, away). */
  passValueUnderPressureTotal: Option[(Double, Double)] = None,
  /** xPass under pressure per zawodnik. */
  passValueUnderPressureByPlayer: Option[Map[String, Double]] = None,
  /** Player Influence score (aktywność×xT) per zawodnik. */
  influenceScoreByPlayer: Option[Map[String, Double]] = None,
  /** Najważniejsze momenty meczu (10–15) do wyświetlenia. */
  highlights: Option[List[Map[String, String]]] = None
)

case class TransferWindow(
  id: TransferWindowId,
  leagueId: LeagueId,
  openAfterMatchday: Int,
  closeBeforeMatchday: Int,
  status: TransferWindowStatus
)

case class TransferOffer(
  id: TransferOfferId,
  windowId: TransferWindowId,
  fromTeamId: TeamId,
  toTeamId: TeamId,
  playerId: PlayerId,
  amount: BigDecimal,
  status: TransferOfferStatus,
  createdAt: Instant,
  respondedAt: Option[Instant],
  counterAmount: Option[BigDecimal] = None
)

case class Contract(
  id: ContractId,
  playerId: PlayerId,
  teamId: TeamId,
  weeklySalary: BigDecimal,
  startMatchday: Int,
  endMatchday: Int,
  signingBonus: BigDecimal = BigDecimal(0),
  releaseClause: Option[BigDecimal] = None,
  createdAt: Instant
)

case class MatchSquad(
  id: MatchSquadId,
  matchId: MatchId,
  teamId: TeamId,
  lineup: List[LineupSlot],
  gamePlanJson: String,
  submittedAt: Instant,
  source: MatchSquadSource
)

case class LineupSlot(playerId: PlayerId, positionSlot: String)

case class LeagueContext(
  id: LeagueContextId,
  leagueId: LeagueId,
  positionStats: Map[String, Map[String, (Double, Double)]],
  createdAt: Instant
)

case class GamePlanSnapshot(
  id: GamePlanSnapshotId,
  teamId: TeamId,
  name: String,
  gamePlanJson: String,
  createdAt: Instant
)
