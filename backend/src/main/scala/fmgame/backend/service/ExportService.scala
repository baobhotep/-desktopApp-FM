package fmgame.backend.service

import fmgame.shared.domain.*
import zio.*

trait ExportService {
  def exportMatchLogs(matchIds: List[MatchId], format: String, userId: UserId, eventTypesOpt: Option[List[String]] = None): ZIO[Any, String, String]
  def exportMatchLogsWithFilters(matchIds: List[MatchId], format: String, userId: UserId, leagueIdOpt: Option[LeagueId], fromMatchdayOpt: Option[Int], toMatchdayOpt: Option[Int], teamIdOpt: Option[TeamId], eventTypesOpt: Option[List[String]]): ZIO[Any, String, String]
  def uploadEngineModel(kind: String, contentType: String, body: Array[Byte]): ZIO[Any, String, Unit]
}
