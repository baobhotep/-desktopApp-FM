package fmgame.desktop

import fmgame.shared.api.*
import fmgame.shared.domain.*

/** Stub GameAPI do F2.1 – prawdziwa implementacja (A1) w F1.3. */
object StubGameAPI extends GameAPI {

  override def login(email: String, password: String): Either[String, (UserDto, String)] =
    if (email.nonEmpty && password.length >= 8)
      Right((
        UserDto("stub-user-id", email, email.takeWhile(_ != '@'), System.currentTimeMillis()),
        "stub-token"
      ))
    else
      Left("Invalid email or password")

  override def register(email: String, password: String, displayName: String): Either[String, UserDto] =
    if (email.nonEmpty && displayName.nonEmpty && password.length >= 8)
      Right(UserDto(UserId.random().value, email, displayName, System.currentTimeMillis()))
    else
      Left("Invalid input")

  override def getMe(userId: UserId): Either[String, UserDto] = Left("Stub: no persistent session")

  override def listLeagues(userId: UserId): Either[String, List[LeagueDto]] = Right(Nil)
  override def createLeague(name: String, teamCount: Int, myTeamName: String, timezone: String, userId: UserId): Either[String, (LeagueDto, TeamDto)] = Left("Stub: use real backend")
  override def createEnglishLeagueSystem(userId: UserId, myTeamName: String): Either[String, (List[LeagueDto], LeagueDto, TeamDto)] = Left("Stub: use real backend")
  override def startSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def applyPromotionRelegation(systemName: String, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def startNextSeasonForSystem(systemName: String, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def createPlayOffSemiFinals(leagueId: LeagueId, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def createPlayOffFinal(leagueId: LeagueId, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def addBots(leagueId: LeagueId, userId: UserId, count: Int): Either[String, Unit] = Left("Stub: use real backend")
  override def startSeason(leagueId: LeagueId, userId: UserId, startDateOpt: Option[String]): Either[String, LeagueDto] = Left("Stub: use real backend")
  override def getLeague(leagueId: LeagueId, userId: UserId): Either[String, LeagueDto] = Left("Not implemented")
  override def getTable(leagueId: LeagueId, userId: UserId): Either[String, List[TableRowDto]] = Right(Nil)
  override def getFixtures(leagueId: LeagueId, userId: UserId, limitOpt: Option[Int], offsetOpt: Option[Int]): Either[String, List[MatchDto]] = Right(Nil)

  override def playMatchday(leagueId: LeagueId, userId: UserId): Either[String, Unit] = Right(())

  override def listTeams(leagueId: LeagueId, userId: UserId): Either[String, List[TeamDto]] = Right(Nil)
  override def getTeam(teamId: TeamId, userId: UserId): Either[String, TeamDto] = Left("Not implemented")
  override def getTeamPlayers(teamId: TeamId, userId: UserId): Either[String, List[PlayerDto]] = Right(Nil)
  override def getTeamContracts(teamId: TeamId, userId: UserId): Either[String, List[ContractDto]] = Right(Nil)
  override def updatePlayer(playerId: PlayerId, userId: UserId, req: UpdatePlayerRequest): Either[String, PlayerDto] = Left("Stub: use real backend")

  override def listGamePlanSnapshots(teamId: TeamId, userId: UserId): Either[String, List[GamePlanSnapshotDto]] = Right(Nil)
  override def getGamePlanSnapshot(teamId: TeamId, snapshotId: String, userId: UserId): Either[String, GamePlanSnapshotDetailDto] = Left("Stub: use real backend")
  override def saveGamePlan(teamId: TeamId, userId: UserId, name: String, gamePlanJson: String): Either[String, GamePlanSnapshotDto] = Left("Stub: use real backend")

  override def getLeaguePlayerStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerStatsDto] =
    Right(LeaguePlayerStatsDto(topScorers = Nil, topAssists = Nil))
  override def getLeaguePlayerAdvancedStats(leagueId: LeagueId, userId: UserId): Either[String, LeaguePlayerAdvancedStatsDto] =
    Right(LeaguePlayerAdvancedStatsDto(rows = Nil))
  override def getMatchdayPrognosis(leagueId: LeagueId, userId: UserId, matchdayOpt: Option[Int]): Either[String, List[MatchPrognosisDto]] = Right(Nil)
  override def getAssistantTip(matchId: MatchId, teamId: TeamId, userId: UserId): Either[String, AssistantTipDto] = Left("Stub: use real backend")
  override def getComparePlayers(leagueId: LeagueId, playerId1: PlayerId, playerId2: PlayerId, userId: UserId): Either[String, ComparePlayersDto] = Left("Stub: use real backend")

  override def getMatch(matchId: MatchId, userId: UserId): Either[String, MatchDto] =
    Right(MatchDto(matchId.value, "", 0, "", "", 0L, "Played", Some(2), Some(1), "", None))
  override def getMatchLog(matchId: MatchId, userId: UserId, limitOpt: Option[Int], offsetOpt: Option[Int]): Either[String, MatchLogDto] = {
    val summary = MatchSummaryDto(
      possessionPercent = List(58.0, 42.0),
      homeGoals = 2, awayGoals = 1,
      shotsTotal = List(14, 8), shotsOnTarget = List(6, 3), shotsOffTarget = List(5, 3), shotsBlocked = List(3, 2),
      bigChances = List(3, 1), xgTotal = List(2.1, 0.9),
      passesTotal = List(420, 310), passesCompleted = List(380, 270), passAccuracyPercent = List(90.5, 87.0),
      passesInFinalThird = List(85, 45), crossesTotal = List(12, 8), crossesSuccessful = List(4, 2),
      longBallsTotal = List(25, 35), longBallsSuccessful = List(15, 20),
      tacklesTotal = List(18, 22), tacklesWon = List(12, 14), interceptions = List(10, 8),
      clearances = List(15, 22), blocks = List(4, 6), saves = List(2, 5), goalsConceded = List(1, 2),
      fouls = List(10, 12), yellowCards = List(2, 1), redCards = List(0, 0), foulsSuffered = List(12, 10),
      corners = List(5, 3), cornersWon = List(3, 2), throwIns = List(8, 10), freeKicksWon = List(12, 14), offsides = List(2, 1)
    )
    Right(MatchLogDto(fmgame.desktop.screens.MatchPlaybackScreen.dummyEvents, Some(summary), None))
  }
  override def getMatchSquads(matchId: MatchId, userId: UserId): Either[String, List[MatchSquadDto]] =
    Right(List(
      MatchSquadDto(s"stub-squad-home-${matchId.value}", matchId.value, "team-home", (1 to 11).map(i => LineupSlotDto(s"player-home-$i", s"slot-$i")).toList, "stub"),
      MatchSquadDto(s"stub-squad-away-${matchId.value}", matchId.value, "team-away", (1 to 11).map(i => LineupSlotDto(s"player-away-$i", s"slot-$i")).toList, "stub")
    ))
  override def submitMatchSquad(matchId: MatchId, teamId: TeamId, userId: UserId, req: SubmitMatchSquadRequest): Either[String, MatchSquadDto] = Left("Not implemented")

  override def getH2H(leagueId: LeagueId, teamId1: TeamId, teamId2: TeamId, limit: Int, userId: UserId): Either[String, List[MatchDto]] = Right(Nil)
  override def getTrainingPlan(teamId: TeamId, userId: UserId): Either[String, TrainingPlanDto] = Left("Stub: use real backend")
  override def upsertTrainingPlan(teamId: TeamId, userId: UserId, week: List[String]): Either[String, TrainingPlanDto] = Left("Stub: use real backend")
  override def listLeaguePlayers(leagueId: LeagueId, userId: UserId, posOpt: Option[String], minOverallOpt: Option[Double], qOpt: Option[String]): Either[String, LeaguePlayersDto] = Right(LeaguePlayersDto(Nil))
  override def getShortlist(teamId: TeamId, userId: UserId): Either[String, List[ShortlistEntryDto]] = Right(Nil)
  override def addToShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit] = Left("Stub: use real backend")
  override def removeFromShortlist(teamId: TeamId, userId: UserId, playerId: PlayerId): Either[String, Unit] = Left("Stub: use real backend")
  override def listScoutingReports(teamId: TeamId, userId: UserId): Either[String, List[ScoutingReportDto]] = Right(Nil)
  override def createScoutingReport(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): Either[String, ScoutingReportDto] = Left("Stub: use real backend")
  override def getTransferWindows(leagueId: LeagueId, userId: UserId): Either[String, List[TransferWindowDto]] = Right(Nil)
  override def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): Either[String, List[TransferOfferDto]] = Right(Nil)
  override def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): Either[String, TransferOfferDto] = Left("Stub: use real backend")
  override def acceptTransferOffer(offerId: String, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def rejectTransferOffer(offerId: String, userId: UserId): Either[String, Unit] = Left("Stub: use real backend")
  override def counterTransferOffer(offerId: String, userId: UserId, counterAmount: Double): Either[String, TransferOfferDto] = Left("Stub: use real backend")
  override def listPendingInvitations(userId: UserId): Either[String, List[InvitationDto]] = Right(Nil)
  override def createInvitation(leagueId: LeagueId, userId: UserId, email: String): Either[String, InvitationDto] = Left("Stub: use real backend")
  override def acceptInvitation(userId: UserId, token: String, teamName: String): Either[String, AcceptInvitationResponse] = Left("Stub: use real backend")
}
