package fmgame.backend.repository

import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import fmgame.shared.domain.*
import java.time.Instant

case class ScoutingReport(
  id: String,
  teamId: TeamId,
  playerId: PlayerId,
  rating: Double,
  notes: String,
  createdAt: Instant
)

trait ScoutingReportRepository {
  def listByTeam(teamId: TeamId): ConnectionIO[List[ScoutingReport]]
  def create(r: ScoutingReport): ConnectionIO[Unit]
}

object ScoutingReportRepository {
  def impl: ScoutingReportRepository = new ScoutingReportRepository {
    def listByTeam(teamId: TeamId): ConnectionIO[List[ScoutingReport]] =
      sql"""
        SELECT id, team_id, player_id, rating, notes, created_at
        FROM scouting_reports
        WHERE team_id = ${teamId.value}
        ORDER BY created_at DESC
      """.query[(String, String, String, Double, String, Instant)].to[List].map(_.map { case (id, tid, pid, rating, notes, at) =>
        ScoutingReport(id, TeamId(tid), PlayerId(pid), rating, notes, at)
      })

    def create(r: ScoutingReport): ConnectionIO[Unit] =
      sql"""
        INSERT INTO scouting_reports (id, team_id, player_id, rating, notes, created_at)
        VALUES (${r.id}, ${r.teamId.value}, ${r.playerId.value}, ${r.rating}, ${r.notes}, ${r.createdAt})
      """.update.run.map(_ => ())
  }
}

