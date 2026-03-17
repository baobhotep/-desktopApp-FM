package fmgame.backend

import fmgame.backend.service.{LeagueService, UserService}
import fmgame.shared.api.*
import fmgame.shared.domain.*
import zio.*

/** Fasada API gry dla modułu desktop (LibGDX). Wywołania synchroniczne z wątku głównego
  * przez Unsafe.run – mostek ZIO ↔ wątek wywołujący. F1.3.
  */
final class GameFacade(
  runtime: zio.Runtime[Any],
  userService: UserService,
  leagueService: LeagueService,
  jwtSecret: String
) {

  private def runSync[A](eff: ZIO[Any, String, A]): Either[String, A] = {
    import zio.Unsafe
    Unsafe.unsafe { implicit u =>
      val exit = runtime.unsafe.run(eff.either)
      exit match {
        case zio.Exit.Success(either) => either
        case zio.Exit.Failure(cause)   => Left(cause.toString)
      }
    }
  }

  def login(email: String, password: String): Either[String, LoginResponse] =
    runSync(userService.login(email, password, jwtSecret))

  def register(email: String, password: String, displayName: String): Either[String, UserDto] =
    runSync(userService.register(email, password, displayName))

  def getMe(userId: UserId): Either[String, UserDto] =
    runSync(userService.getById(userId))

  def listLeagues(userId: UserId): Either[String, List[LeagueDto]] =
    runSync(leagueService.listLeagues(userId))

  def createLeague(name: String, teamCount: Int, myTeamName: String, timezone: String, userId: UserId): Either[String, (LeagueDto, TeamDto)] =
    runSync(leagueService.create(name, teamCount, myTeamName, timezone, userId))

  def createEnglishLeagueSystem(userId: UserId, myTeamName: String): Either[String, (List[LeagueDto], LeagueDto, TeamDto)] =
    runSync(leagueService.createEnglishLeagueSystem(userId, myTeamName))

  def startSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit] =
    runSync(leagueService.startSeasonForSystem(systemName, userId))

  def applyPromotionRelegation(systemName: String, userId: UserId): Either[String, Unit] =
    runSync(leagueService.applyPromotionRelegation(systemName, userId))

  def startNextSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit] =
    runSync(leagueService.startNextSeasonForSystem(systemName, userId))

  def createPlayOffSemiFinals(leagueId: LeagueId, userId: UserId): Either[String, Unit] =
    runSync(leagueService.createPlayOffSemiFinals(leagueId, userId))

  def createPlayOffFinal(leagueId: LeagueId, userId: UserId): Either[String, Unit] =
    runSync(leagueService.createPlayOffFinal(leagueId, userId))

  def addBots(leagueId: LeagueId, userId: UserId, count: Int): Either[String, Unit] =
    runSync(leagueService.addBots(leagueId, userId, count))

  def startSeason(leagueId: LeagueId, userId: UserId, startDateOpt: Option[String]): Either[String, LeagueDto] =
    runSync(leagueService.startSeason(leagueId, userId, startDateOpt))

  def getLeague(leagueId: LeagueId, userId: UserId): Either[String, LeagueDto] =
    runSync(leagueService.getLeagueForUser(leagueId, userId))

  def getTable(leagueId: LeagueId, userId: UserId): Either[String, List[TableRowDto]] =
    runSync(leagueService.getTableForUser(leagueId, userId))

  def getFixtures(leagueId: LeagueId, userId: UserId, limitOpt: Option[Int] = None, offsetOpt: Option[Int] = None): Either[String, List[MatchDto]] =
    runSync(leagueService.getFixturesForUser(leagueId, limitOpt, offsetOpt, userId))

  def playMatchday(leagueId: LeagueId, userId: UserId): Either[String, Unit] =
    runSync(leagueService.playMatchday(leagueId, userId))

  def getMatch(matchId: MatchId, userId: UserId): Either[String, MatchDto] =
    runSync(leagueService.getMatchForUser(matchId, userId))

  def getMatchLog(matchId: MatchId, userId: UserId, limitOpt: Option[Int] = None, offsetOpt: Option[Int] = None): Either[String, MatchLogDto] =
    runSync(leagueService.getMatchLogForUser(matchId, limitOpt, offsetOpt, userId))

  def getSquads(matchId: MatchId, userId: UserId): Either[String, List[MatchSquadDto]] =
    runSync(leagueService.getMatchSquadsForUser(matchId, userId))

  def submitSquad(matchId: MatchId, squadTeamId: TeamId, userId: UserId, body: SubmitMatchSquadRequest): Either[String, MatchSquadDto] =
    runSync(leagueService.submitMatchSquad(matchId, squadTeamId, userId, body))

  def getTeam(teamId: TeamId, userId: UserId): Either[String, TeamDto] =
    runSync(leagueService.getTeamForUser(teamId, userId))

  def getTeamPlayers(teamId: TeamId, userId: UserId): Either[String, List[PlayerDto]] =
    runSync(leagueService.getTeamPlayersForUser(teamId, userId))

  def getTeamContracts(teamId: TeamId, userId: UserId): Either[String, List[ContractDto]] =
    runSync(leagueService.getTeamContractsForUser(teamId, userId))

  def listTeams(leagueId: LeagueId, userId: UserId): Either[String, List[TeamDto]] =
    runSync(leagueService.listTeamsForUser(leagueId, userId))

  def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): Either[String, PlayerDto] =
    runSync(leagueService.updatePlayer(playerId, userId, req))

  def listGamePlanSnapshots(teamId: TeamId, userId: UserId): Either[String, List[GamePlanSnapshotDto]] =
    runSync(leagueService.getTeamGamePlansForUser(teamId, userId))

  def getGamePlanSnapshot(teamId: TeamId, snapshotId: GamePlanSnapshotId, userId: UserId): Either[String, GamePlanSnapshotDetailDto] =
    runSync(leagueService.getGamePlanSnapshotForUser(teamId, snapshotId, userId))

  def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): Either[String, GamePlanSnapshotDto] =
    runSync(leagueService.saveGamePlan(teamId, userId, name, gamePlanJson))

  def getLeaguePlayerStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerStatsDto] =
    runSync(leagueService.getLeaguePlayerStatsForUser(leagueId, userId))

  def getLeaguePlayerAdvancedStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerAdvancedStatsDto] =
    runSync(leagueService.getLeaguePlayerAdvancedStatsForUser(leagueId, userId))

  def getMatchdayPrognosis(leagueId: LeagueId, userId: UserId, matchdayOpt: Option[Int]): Either[String, List[MatchPrognosisDto]] =
    runSync(leagueService.getMatchdayPrognosisForUser(leagueId, matchdayOpt, userId))

  def getAssistantTip(matchId: MatchId, teamId: TeamId, userId: UserId): Either[String, AssistantTipDto] =
    runSync(leagueService.getAssistantTipForUser(matchId, teamId, userId))

  def getComparePlayers(leagueId: LeagueId, playerId1: PlayerId, playerId2: PlayerId, userId: UserId): Either[String, ComparePlayersDto] =
    runSync(leagueService.getComparePlayersForUser(leagueId, playerId1, playerId2, userId))

  def getH2H(leagueId: LeagueId, teamId1: TeamId, teamId2: TeamId, limit: Int, userId: UserId): Either[String, List[MatchDto]] =
    runSync(leagueService.getH2HForUser(leagueId, teamId1, teamId2, limit, userId))

  def getTrainingPlan(teamId: TeamId, userId: UserId): Either[String, TrainingPlanDto] =
    runSync(leagueService.getTrainingPlanForUser(teamId, userId))

  def upsertTrainingPlan(teamId: TeamId, userId: UserId, week: List[String]): Either[String, TrainingPlanDto] =
    runSync(leagueService.upsertTrainingPlanForUser(teamId, userId, week))

  def listLeaguePlayers(leagueId: LeagueId, userId: UserId, posOpt: Option[String], minOverallOpt: Option[Double], qOpt: Option[String]): Either[String, LeaguePlayersDto] =
    runSync(leagueService.listLeaguePlayersForUser(leagueId, userId, posOpt, minOverallOpt, qOpt))

  def getShortlist(teamId: TeamId, userId: UserId): Either[String, List[ShortlistEntryDto]] =
    runSync(leagueService.getShortlistForUser(teamId, userId))

  def addToShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit] =
    runSync(leagueService.addToShortlistForUser(teamId, userId, playerId))

  def removeFromShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit] =
    runSync(leagueService.removeFromShortlistForUser(teamId, userId, playerId))

  def listScoutingReports(teamId: TeamId, userId: UserId): Either[String, List[ScoutingReportDto]] =
    runSync(leagueService.listScoutingReportsForUser(teamId, userId))

  def createScoutingReport(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): Either[String, ScoutingReportDto] =
    runSync(leagueService.createScoutingReportForUser(teamId, userId, playerId, rating, notes))

  def getTransferWindows(leagueId: LeagueId, userId: UserId): Either[String, List[TransferWindowDto]] =
    runSync(leagueService.getTransferWindowsForUser(leagueId, userId))

  def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): Either[String, List[TransferOfferDto]] =
    runSync(leagueService.getTransferOffersForUser(leagueId, teamIdOpt, userId))

  def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): Either[String, TransferOfferDto] =
    runSync(leagueService.createTransferOffer(leagueId, userId, req))

  def acceptTransferOffer(offerId: String, userId: UserId): Either[String, Unit] =
    runSync(leagueService.acceptTransferOffer(TransferOfferId(offerId), userId))

  def rejectTransferOffer(offerId: String, userId: UserId): Either[String, Unit] =
    runSync(leagueService.rejectTransferOffer(TransferOfferId(offerId), userId))

  def counterTransferOffer(offerId: String, userId: UserId, counterAmount: Double): Either[String, TransferOfferDto] =
    runSync(leagueService.counterTransferOffer(TransferOfferId(offerId), userId, BigDecimal(counterAmount)))

  def listPendingInvitations(userId: UserId): Either[String, List[InvitationDto]] =
    runSync(leagueService.listPendingInvitations(userId))

  def createInvitation(leagueId: LeagueId, userId: UserId, email: String): Either[String, InvitationDto] =
    runSync(leagueService.createInvitation(leagueId, email, userId))

  def acceptInvitation(userId: UserId, token: String, teamName: String): Either[String, AcceptInvitationResponse] =
    runSync(leagueService.acceptInvitation(token, teamName, userId).map { case (league, team) => AcceptInvitationResponse(league, team) })
}
