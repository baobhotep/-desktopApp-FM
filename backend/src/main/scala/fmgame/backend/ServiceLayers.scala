package fmgame.backend

import doobie.*
import fmgame.backend.engine.{EngineModels, FullMatchEngine, MatchEngine}
import fmgame.backend.repository.*
import fmgame.backend.service.*
import zio.*

object ServiceLayers {
  def make(
    xa: Transactor[zio.Task],
    engine: MatchEngine,
    engineModelsRef: Ref[EngineModels]
  ): ZLayer[Any, Nothing, UserService & LeagueService] = {
    val userRepo = UserRepository.impl
    val leagueRepo = LeagueRepository.impl
    val teamRepo = TeamRepository.impl
    val invitationRepo = InvitationRepository.impl
    val playerRepo = PlayerRepository.impl
    val refereeRepo = RefereeRepository.impl
    val matchRepo = MatchRepository.impl
    val matchSquadRepo = MatchSquadRepository.impl
    val matchResultLogRepo = MatchResultLogRepository.impl
    val transferWindowRepo = TransferWindowRepository.impl
    val transferOfferRepo = TransferOfferRepository.impl
    val leagueContextRepo = LeagueContextRepository.impl
    val gamePlanSnapshotRepo = GamePlanSnapshotRepository.impl
    val trainingPlanRepo = TrainingPlanRepository.impl
    val shortlistRepo = ShortlistRepository.impl
    val scoutingReportRepo = ScoutingReportRepository.impl
    val leaguePlayerMatchStatsRepo = LeaguePlayerMatchStatsRepository.impl
    val contractRepo = ContractRepository.impl

    val userSvc = UserServiceLive(userRepo, xa)
    val leagueSvc = LeagueServiceLive(
      leagueRepo, teamRepo, userRepo, invitationRepo, playerRepo, refereeRepo,
      matchRepo, matchSquadRepo, matchResultLogRepo, transferWindowRepo, transferOfferRepo,
      leagueContextRepo, gamePlanSnapshotRepo, trainingPlanRepo, shortlistRepo, scoutingReportRepo,
      leaguePlayerMatchStatsRepo, contractRepo, engine, engineModelsRef, xa
    )
    ZLayer.succeed[UserService](userSvc) ++ ZLayer.succeed[LeagueService](leagueSvc)
  }
}
