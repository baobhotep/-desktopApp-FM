package fmgame.backend.repository

import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import fmgame.shared.domain.*
import java.time.Instant

case class ShortlistEntry(teamId: TeamId, playerId: PlayerId, createdAt: Instant)

trait ShortlistRepository {
  def listByTeam(teamId: TeamId): ConnectionIO[List[ShortlistEntry]]
  def add(teamId: TeamId, playerId: PlayerId, createdAt: Instant): ConnectionIO[Unit]
  def remove(teamId: TeamId, playerId: PlayerId): ConnectionIO[Unit]
}

object ShortlistRepository {
  def impl: ShortlistRepository = new ShortlistRepository {
    def listByTeam(teamId: TeamId): ConnectionIO[List[ShortlistEntry]] =
      sql"""
        SELECT team_id, player_id, created_at
        FROM team_shortlists
        WHERE team_id = ${teamId.value}
        ORDER BY created_at DESC
      """.query[(String, String, Instant)].to[List].map(_.map { case (tid, pid, at) =>
        ShortlistEntry(TeamId(tid), PlayerId(pid), at)
      })

    def add(teamId: TeamId, playerId: PlayerId, createdAt: Instant): ConnectionIO[Unit] =
      sql"""
        MERGE INTO team_shortlists (team_id, player_id, created_at)
        KEY (team_id, player_id)
        VALUES (${teamId.value}, ${playerId.value}, ${createdAt})
      """.update.run.map(_ => ())

    def remove(teamId: TeamId, playerId: PlayerId): ConnectionIO[Unit] =
      sql"DELETE FROM team_shortlists WHERE team_id = ${teamId.value} AND player_id = ${playerId.value}".update.run.map(_ => ())
  }
}

