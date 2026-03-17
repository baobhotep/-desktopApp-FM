package fmgame.backend.service

import fmgame.shared.domain.*
import fmgame.shared.api.*
import zio.*

trait MatchService {
  def getMatch(matchId: MatchId): ZIO[Any, String, MatchDto]
  def getMatchForUser(matchId: MatchId, userId: UserId): ZIO[Any, String, MatchDto]
  def getMatchLog(matchId: MatchId, limitOpt: Option[Int], offsetOpt: Option[Int]): ZIO[Any, String, MatchLogDto]
  def getMatchLogForUser(matchId: MatchId, limitOpt: Option[Int], offsetOpt: Option[Int], userId: UserId): ZIO[Any, String, MatchLogDto]
  def getMatchSquads(matchId: MatchId): ZIO[Any, String, List[MatchSquadDto]]
  def getMatchSquadsForUser(matchId: MatchId, userId: UserId): ZIO[Any, String, List[MatchSquadDto]]
  def getAssistantTipForUser(matchId: MatchId, teamId: TeamId, userId: UserId): ZIO[Any, String, AssistantTipDto]
  def submitMatchSquad(matchId: MatchId, teamId: TeamId, userId: UserId, req: SubmitMatchSquadRequest): ZIO[Any, String, MatchSquadDto]
  def applyPressConference(matchId: MatchId, teamId: TeamId, userId: UserId, phase: String, tone: String): ZIO[Any, String, Unit]
}
