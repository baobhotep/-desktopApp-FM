package fmgame.shared.api

import fmgame.shared.domain.*

/** Fasada do wywołań rdzenia gry z warstwy LibGDX (ten sam proces, bez HTTP).
  * A1 implementuje w backendzie (GameAPILive); A2 używa w desktop i może mieć stub do F2.1.
  */
trait GameAPI {

  def login(email: String, password: String): Either[String, (UserDto, String)]
  def register(email: String, password: String, displayName: String): Either[String, UserDto]
  /** Dla przywracania sesji (zapamiętaj): walidacja userId bez hasła. */
  def getMe(userId: UserId): Either[String, UserDto]

  def listLeagues(userId: UserId): Either[String, List[LeagueDto]]
  /** Tworzy ligę; zwraca (liga, drużyna użytkownika). */
  def createLeague(name: String, teamCount: Int, myTeamName: String, timezone: String, userId: UserId): Either[String, (LeagueDto, TeamDto)]
  /** Tworzy system ligi angielskiej (4 szczeble, 92 drużyny). Zwraca (lista 4 lig, liga użytkownika, drużyna użytkownika). */
  def createEnglishLeagueSystem(userId: UserId, myTeamName: String): Either[String, (List[LeagueDto], LeagueDto, TeamDto)]
  /** Uruchamia sezon we wszystkich ligach systemu (np. "English"). */
  def startSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit]
  /** Awans/spadek: po zakończeniu sezonu przenosi drużyny między poziomami (3 ostatnie → niżej, 3 pierwsze → wyżej). */
  def applyPromotionRelegation(systemName: String, userId: UserId): Either[String, Unit]
  /** Po awansie/spadku: resetuje terminarze i uruchamia nowy sezon we wszystkich ligach systemu. */
  def startNextSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit]
  /** Baraże: tworzy półfinały (3. vs 6., 4. vs 5.) – ligi tier 2–4. */
  def createPlayOffSemiFinals(leagueId: LeagueId, userId: UserId): Either[String, Unit]
  /** Baraże: tworzy finał (zwycięzcy półfinałów). */
  def createPlayOffFinal(leagueId: LeagueId, userId: UserId): Either[String, Unit]
  def addBots(leagueId: LeagueId, userId: UserId, count: Int): Either[String, Unit]
  def startSeason(leagueId: LeagueId, userId: UserId, startDateOpt: Option[String]): Either[String, LeagueDto]
  def getLeague(leagueId: LeagueId, userId: UserId): Either[String, LeagueDto]
  def getTable(leagueId: LeagueId, userId: UserId): Either[String, List[TableRowDto]]
  def getFixtures(leagueId: LeagueId, userId: UserId, limitOpt: Option[Int], offsetOpt: Option[Int]): Either[String, List[MatchDto]]

  def playMatchday(leagueId: LeagueId, userId: UserId): Either[String, Unit]

  def listTeams(leagueId: LeagueId, userId: UserId): Either[String, List[TeamDto]]
  def getTeam(teamId: TeamId, userId: UserId): Either[String, TeamDto]
  def getTeamPlayers(teamId: TeamId, userId: UserId): Either[String, List[PlayerDto]]
  def getTeamContracts(teamId: TeamId, userId: UserId): Either[String, List[ContractDto]]
  def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): Either[String, PlayerDto]

  def listGamePlanSnapshots(teamId: TeamId, userId: UserId): Either[String, List[GamePlanSnapshotDto]]
  def getGamePlanSnapshot(teamId: TeamId, snapshotId: String, userId: UserId): Either[String, GamePlanSnapshotDetailDto]
  def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): Either[String, GamePlanSnapshotDto]

  def getLeaguePlayerStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerStatsDto]
  def getLeaguePlayerAdvancedStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerAdvancedStatsDto]
  def getMatchdayPrognosis(leagueId: LeagueId, userId: UserId, matchdayOpt: Option[Int]): Either[String, List[MatchPrognosisDto]]
  def getAssistantTip(matchId: MatchId, teamId: TeamId, userId: UserId): Either[String, AssistantTipDto]
  def getComparePlayers(leagueId: LeagueId, playerId1: PlayerId, playerId2: PlayerId, userId: UserId): Either[String, ComparePlayersDto]

  def getMatch(matchId: MatchId, userId: UserId): Either[String, MatchDto]
  def getMatchLog(matchId: MatchId, userId: UserId, limitOpt: Option[Int], offsetOpt: Option[Int]): Either[String, MatchLogDto]
  def getMatchSquads(matchId: MatchId, userId: UserId): Either[String, List[MatchSquadDto]]
  def submitMatchSquad(matchId: MatchId, teamId: TeamId, userId: UserId, req: SubmitMatchSquadRequest): Either[String, MatchSquadDto]

  /** Ostatnie mecze między dwiema drużynami (head-to-head). */
  def getH2H(leagueId: LeagueId, teamId1: TeamId, teamId2: TeamId, limit: Int, userId: UserId): Either[String, List[MatchDto]]
  /** Plan treningowy (7 dni). */
  def getTrainingPlan(teamId: TeamId, userId: UserId): Either[String, TrainingPlanDto]
  def upsertTrainingPlan(teamId: TeamId, userId: UserId, week: List[String]): Either[String, TrainingPlanDto]
  /** Zawodnicy w lidze (scouting): posOpt, minOverallOpt, q (wyszukiwanie). */
  def listLeaguePlayers(leagueId: LeagueId, userId: UserId, posOpt: Option[String], minOverallOpt: Option[Double], qOpt: Option[String]): Either[String, LeaguePlayersDto]
  def getShortlist(teamId: TeamId, userId: UserId): Either[String, List[ShortlistEntryDto]]
  def addToShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit]
  def removeFromShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit]
  def listScoutingReports(teamId: TeamId, userId: UserId): Either[String, List[ScoutingReportDto]]
  def createScoutingReport(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): Either[String, ScoutingReportDto]
  /** Okna transferowe w lidze. */
  def getTransferWindows(leagueId: LeagueId, userId: UserId): Either[String, List[TransferWindowDto]]
  /** Oferty transferowe (teamIdOpt = Some(moja drużyna) dla ofert do/z mojej drużyny). */
  def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): Either[String, List[TransferOfferDto]]
  def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): Either[String, TransferOfferDto]
  def acceptTransferOffer(offerId: String, userId: UserId): Either[String, Unit]
  def rejectTransferOffer(offerId: String, userId: UserId): Either[String, Unit]
  def counterTransferOffer(offerId: String, userId: UserId, counterAmount: Double): Either[String, TransferOfferDto]
  /** Zaproszenia do lig. */
  def listPendingInvitations(userId: UserId): Either[String, List[InvitationDto]]
  def createInvitation(leagueId: LeagueId, userId: UserId, email: String): Either[String, InvitationDto]
  def acceptInvitation(userId: UserId, token: String, teamName: String): Either[String, AcceptInvitationResponse]
}
