package fmgame.desktop

import fmgame.backend.GameFacade
import fmgame.shared.api.*
import fmgame.shared.domain.*

/** Adapter: GameFacade → GameAPI. Umożliwia przekazanie prawdziwego backendu do FMGame (LibGDX).
  * Mapuje login: LoginResponse → (UserDto, String); getSquads/submitSquad → getMatchSquads/submitMatchSquad.
  */
final class GameFacadeAdapter(facade: GameFacade) extends GameAPI {

  override def login(email: String, password: String): Either[String, (UserDto, String)] =
    facade.login(email, password).map(r => (r.user, r.token))

  override def register(email: String, password: String, displayName: String): Either[String, UserDto] =
    facade.register(email, password, displayName)

  override def getMe(userId: UserId): Either[String, UserDto] =
    facade.getMe(userId)

  override def listLeagues(userId: UserId): Either[String, List[LeagueDto]] =
    facade.listLeagues(userId)

  override def createLeague(name: String, teamCount: Int, myTeamName: String, timezone: String, userId: UserId): Either[String, (LeagueDto, TeamDto)] =
    facade.createLeague(name, teamCount, myTeamName, timezone, userId)

  override def createEnglishLeagueSystem(userId: UserId, myTeamName: String): Either[String, (List[LeagueDto], LeagueDto, TeamDto)] =
    facade.createEnglishLeagueSystem(userId, myTeamName)

  override def startSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit] =
    facade.startSeasonForSystem(systemName, userId)

  override def applyPromotionRelegation(systemName: String, userId: UserId): Either[String, Unit] =
    facade.applyPromotionRelegation(systemName, userId)

  override def startNextSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit] =
    facade.startNextSeasonForSystem(systemName, userId)

  override def createPlayOffSemiFinals(leagueId: LeagueId, userId: UserId): Either[String, Unit] =
    facade.createPlayOffSemiFinals(leagueId, userId)

  override def createPlayOffFinal(leagueId: LeagueId, userId: UserId): Either[String, Unit] =
    facade.createPlayOffFinal(leagueId, userId)

  override def addBots(leagueId: LeagueId, userId: UserId, count: Int): Either[String, Unit] =
    facade.addBots(leagueId, userId, count)

  override def startSeason(leagueId: LeagueId, userId: UserId, startDateOpt: Option[String]): Either[String, LeagueDto] =
    facade.startSeason(leagueId, userId, startDateOpt)

  override def getLeague(leagueId: LeagueId, userId: UserId): Either[String, LeagueDto] =
    facade.getLeague(leagueId, userId)

  override def getTable(leagueId: LeagueId, userId: UserId): Either[String, List[TableRowDto]] =
    facade.getTable(leagueId, userId)

  override def getFixtures(leagueId: LeagueId, userId: UserId, limitOpt: Option[Int], offsetOpt: Option[Int]): Either[String, List[MatchDto]] =
    facade.getFixtures(leagueId, userId, limitOpt, offsetOpt)

  override def playMatchday(leagueId: LeagueId, userId: UserId): Either[String, Unit] =
    facade.playMatchday(leagueId, userId)

  override def listTeams(leagueId: LeagueId, userId: UserId): Either[String, List[TeamDto]] =
    facade.listTeams(leagueId, userId)

  override def getTeam(teamId: TeamId, userId: UserId): Either[String, TeamDto] =
    facade.getTeam(teamId, userId)

  override def getTeamPlayers(teamId: TeamId, userId: UserId): Either[String, List[PlayerDto]] =
    facade.getTeamPlayers(teamId, userId)

  override def getTeamContracts(teamId: TeamId, userId: UserId): Either[String, List[ContractDto]] =
    facade.getTeamContracts(teamId, userId)

  override def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): Either[String, PlayerDto] =
    facade.updatePlayer(playerId, userId, req)

  override def listGamePlanSnapshots(teamId: TeamId, userId: UserId): Either[String, List[GamePlanSnapshotDto]] =
    facade.listGamePlanSnapshots(teamId, userId)

  override def getGamePlanSnapshot(teamId: TeamId, snapshotId: String, userId: UserId): Either[String, GamePlanSnapshotDetailDto] =
    facade.getGamePlanSnapshot(teamId, GamePlanSnapshotId(snapshotId), userId)

  override def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): Either[String, GamePlanSnapshotDto] =
    facade.saveGamePlan(teamId, userId, name, gamePlanJson)

  override def getLeaguePlayerStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerStatsDto] =
    facade.getLeaguePlayerStats(leagueId, userId)

  override def getLeaguePlayerAdvancedStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerAdvancedStatsDto] =
    facade.getLeaguePlayerAdvancedStats(leagueId, userId)

  override def getMatchdayPrognosis(leagueId: LeagueId, userId: UserId, matchdayOpt: Option[Int]): Either[String, List[MatchPrognosisDto]] =
    facade.getMatchdayPrognosis(leagueId, userId, matchdayOpt)

  override def getAssistantTip(matchId: MatchId, teamId: TeamId, userId: UserId): Either[String, AssistantTipDto] =
    facade.getAssistantTip(matchId, teamId, userId)

  override def getComparePlayers(leagueId: LeagueId, playerId1: PlayerId, playerId2: PlayerId, userId: UserId): Either[String, ComparePlayersDto] =
    facade.getComparePlayers(leagueId, playerId1, playerId2, userId)

  override def getMatch(matchId: MatchId, userId: UserId): Either[String, MatchDto] =
    facade.getMatch(matchId, userId)

  override def getMatchLog(matchId: MatchId, userId: UserId, limitOpt: Option[Int], offsetOpt: Option[Int]): Either[String, MatchLogDto] =
    facade.getMatchLog(matchId, userId, limitOpt, offsetOpt)

  override def getMatchSquads(matchId: MatchId, userId: UserId): Either[String, List[MatchSquadDto]] =
    facade.getSquads(matchId, userId)

  override def submitMatchSquad(matchId: MatchId, teamId: TeamId, userId: UserId, req: SubmitMatchSquadRequest): Either[String, MatchSquadDto] =
    facade.submitSquad(matchId, teamId, userId, req)

  override def getH2H(leagueId: LeagueId, teamId1: TeamId, teamId2: TeamId, limit: Int, userId: UserId): Either[String, List[MatchDto]] =
    facade.getH2H(leagueId, teamId1, teamId2, limit, userId)
  override def getTrainingPlan(teamId: TeamId, userId: UserId): Either[String, TrainingPlanDto] =
    facade.getTrainingPlan(teamId, userId)
  override def upsertTrainingPlan(teamId: TeamId, userId: UserId, week: List[String]): Either[String, TrainingPlanDto] =
    facade.upsertTrainingPlan(teamId, userId, week)
  override def listLeaguePlayers(leagueId: LeagueId, userId: UserId, posOpt: Option[String], minOverallOpt: Option[Double], qOpt: Option[String]): Either[String, LeaguePlayersDto] =
    facade.listLeaguePlayers(leagueId, userId, posOpt, minOverallOpt, qOpt)
  override def getShortlist(teamId: TeamId, userId: UserId): Either[String, List[ShortlistEntryDto]] =
    facade.getShortlist(teamId, userId)
  override def addToShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit] =
    facade.addToShortlist(teamId, userId, playerId)
  override def removeFromShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit] =
    facade.removeFromShortlist(teamId, userId, playerId)
  override def listScoutingReports(teamId: TeamId, userId: UserId): Either[String, List[ScoutingReportDto]] =
    facade.listScoutingReports(teamId, userId)
  override def createScoutingReport(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): Either[String, ScoutingReportDto] =
    facade.createScoutingReport(teamId, userId, playerId, rating, notes)
  override def getTransferWindows(leagueId: LeagueId, userId: UserId): Either[String, List[TransferWindowDto]] =
    facade.getTransferWindows(leagueId, userId)
  override def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): Either[String, List[TransferOfferDto]] =
    facade.getTransferOffers(leagueId, teamIdOpt, userId)
  override def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): Either[String, TransferOfferDto] =
    facade.createTransferOffer(leagueId, userId, req)
  override def acceptTransferOffer(offerId: String, userId: UserId): Either[String, Unit] =
    facade.acceptTransferOffer(offerId, userId)
  override def rejectTransferOffer(offerId: String, userId: UserId): Either[String, Unit] =
    facade.rejectTransferOffer(offerId, userId)
  override def counterTransferOffer(offerId: String, userId: UserId, counterAmount: Double): Either[String, TransferOfferDto] =
    facade.counterTransferOffer(offerId, userId, counterAmount)
  override def listPendingInvitations(userId: UserId): Either[String, List[InvitationDto]] =
    facade.listPendingInvitations(userId)
  override def createInvitation(leagueId: LeagueId, userId: UserId, email: String): Either[String, InvitationDto] =
    facade.createInvitation(leagueId, userId, email)
  override def acceptInvitation(userId: UserId, token: String, teamName: String): Either[String, AcceptInvitationResponse] =
    facade.acceptInvitation(userId, token, teamName)
}
