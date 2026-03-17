package fmgame.backend.service

import fmgame.shared.domain.*
import fmgame.shared.api.*
import zio.*

trait ScoutingService {
  def getShortlistForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ShortlistEntryDto]]
  def addToShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit]
  def removeFromShortlistForUser(teamId: TeamId, userId: UserId, playerId: PlayerId): ZIO[Any, String, Unit]
  def listScoutingReportsForUser(teamId: TeamId, userId: UserId): ZIO[Any, String, List[ScoutingReportDto]]
  def createScoutingReportForUser(teamId: TeamId, userId: UserId, playerId: PlayerId, rating: Double, notes: String): ZIO[Any, String, ScoutingReportDto]
}
