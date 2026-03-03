package fmgame.backend.domain

import fmgame.shared.domain.*
import zio.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Re-export shared IDs and add random generators for backend. Prefer ZIO generators (gen*) for testability.
object IdGen {
  def userId: UserId = UserId(java.util.UUID.randomUUID().toString)
  def leagueId: LeagueId = LeagueId(java.util.UUID.randomUUID().toString)
  def teamId: TeamId = TeamId(java.util.UUID.randomUUID().toString)
  def playerId: PlayerId = PlayerId(java.util.UUID.randomUUID().toString)
  def matchId: MatchId = MatchId(java.util.UUID.randomUUID().toString)
  def refereeId: RefereeId = RefereeId(java.util.UUID.randomUUID().toString)
  def invitationId: InvitationId = InvitationId(java.util.UUID.randomUUID().toString)
  def matchSquadId: MatchSquadId = MatchSquadId(java.util.UUID.randomUUID().toString)
  def matchResultLogId: MatchResultLogId = MatchResultLogId(java.util.UUID.randomUUID().toString)
  def gamePlanSnapshotId: GamePlanSnapshotId = GamePlanSnapshotId(java.util.UUID.randomUUID().toString)
  def transferWindowId: TransferWindowId = TransferWindowId(java.util.UUID.randomUUID().toString)
  def transferOfferId: TransferOfferId = TransferOfferId(java.util.UUID.randomUUID().toString)
  def botId: BotId = BotId(java.util.UUID.randomUUID().toString)
  def leagueContextId: LeagueContextId = LeagueContextId(java.util.UUID.randomUUID().toString)

  def genUserId: ZIO[Any, Nothing, UserId] = ZIO.succeed(UserId(java.util.UUID.randomUUID().toString))
  def genLeagueId: ZIO[Any, Nothing, LeagueId] = ZIO.succeed(LeagueId(java.util.UUID.randomUUID().toString))
  def genTeamId: ZIO[Any, Nothing, TeamId] = ZIO.succeed(TeamId(java.util.UUID.randomUUID().toString))
  def genPlayerId: ZIO[Any, Nothing, PlayerId] = ZIO.succeed(PlayerId(java.util.UUID.randomUUID().toString))
  def genMatchId: ZIO[Any, Nothing, MatchId] = ZIO.succeed(MatchId(java.util.UUID.randomUUID().toString))
  def genRefereeId: ZIO[Any, Nothing, RefereeId] = ZIO.succeed(RefereeId(java.util.UUID.randomUUID().toString))
  def genInvitationId: ZIO[Any, Nothing, InvitationId] = ZIO.succeed(InvitationId(java.util.UUID.randomUUID().toString))
  def genMatchSquadId: ZIO[Any, Nothing, MatchSquadId] = ZIO.succeed(MatchSquadId(java.util.UUID.randomUUID().toString))
  def genMatchResultLogId: ZIO[Any, Nothing, MatchResultLogId] = ZIO.succeed(MatchResultLogId(java.util.UUID.randomUUID().toString))
  def genGamePlanSnapshotId: ZIO[Any, Nothing, GamePlanSnapshotId] = ZIO.succeed(GamePlanSnapshotId(java.util.UUID.randomUUID().toString))
  def genTransferWindowId: ZIO[Any, Nothing, TransferWindowId] = ZIO.succeed(TransferWindowId(java.util.UUID.randomUUID().toString))
  def genTransferOfferId: ZIO[Any, Nothing, TransferOfferId] = ZIO.succeed(TransferOfferId(java.util.UUID.randomUUID().toString))
  def genBotId: ZIO[Any, Nothing, BotId] = ZIO.succeed(BotId(java.util.UUID.randomUUID().toString))
  def genLeagueContextId: ZIO[Any, Nothing, LeagueContextId] = ZIO.succeed(LeagueContextId(java.util.UUID.randomUUID().toString))
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
  timezone: ZoneId
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
  /** Field Tilt: udział w kontaktach w tercji ataku (strefy 9–12). (homeShare, awayShare). */
  fieldTilt: Option[(Double, Double)] = None,
  /** PPDA w strefie budowania. (homePPDA, awayPPDA). */
  ppda: Option[(Double, Double)] = None,
  /** Tortuosity ścieżki piłki (gBRI). */
  ballTortuosity: Option[Double] = None,
  /** Metabolic load (przybliżenie). */
  metabolicLoad: Option[Double] = None,
  /** xT wartości stref 1–12 (value iteration). */
  xtByZone: Option[List[Double]] = None,
  /** Kontuzje w meczu (gospodarze, goście). */
  injuries: (Int, Int) = (0, 0),
  /** Voronoi-like: udział gospodarzy w akcjach w strefach 1–12 (lista 12 elem.). */
  homeShareByZone: Option[List[Double]] = None,
  /** I-VAEP: VAEP per typ zdarzenia per zawodnik (playerId -> eventType -> value). */
  vaepBreakdownByPlayer: Option[Map[String, Map[String, Double]]] = None,
  /** Pressing: liczba akcji defensywnych per zawodnik. */
  pressingByPlayer: Option[Map[String, Int]] = None,
  /** Szacowany dystans (m) per zawodnik. */
  estimatedDistanceByPlayer: Option[Map[String, Double]] = None,
  /** Player Influence: akcje per strefa per zawodnik (playerId -> zone -> count). */
  influenceByPlayer: Option[Map[String, Map[String, Int]]] = None,
  /** C-OBSO: średnia liczba obrońców w stożku per strefa 1–12. */
  avgDefendersInConeByZone: Option[List[Double]] = None,
  /** C-OBSO: średnia odległość GK od bramki przy strzałach per strefa 1–12. */
  avgGkDistanceByZone: Option[List[Double]] = None,
  /** Stałe fragmenty: aktywność stref per routine (klucz np. Corner:default -> lista 12 liczb). */
  setPieceZoneActivity: Option[Map[String, List[Int]]] = None,
  /** Pressing w połowie przeciwnika (strefy 7–12) per zawodnik. */
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
  /** Voronoi z centrum aktywności: strefa → udział gosp. (0 lub 1), lista 12. */
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
  influenceScoreByPlayer: Option[Map[String, Double]] = None
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
  respondedAt: Option[Instant]
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
