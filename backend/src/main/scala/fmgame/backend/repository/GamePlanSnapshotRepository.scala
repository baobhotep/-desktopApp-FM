package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait GamePlanSnapshotRepository {
  def create(snap: GamePlanSnapshot): ConnectionIO[Unit]
  def listByTeam(teamId: TeamId): ConnectionIO[List[GamePlanSnapshot]]
  def findById(id: GamePlanSnapshotId): ConnectionIO[Option[GamePlanSnapshot]]
}

object GamePlanSnapshotRepository {
  def impl: GamePlanSnapshotRepository = new GamePlanSnapshotRepository {
    override def create(snap: GamePlanSnapshot): ConnectionIO[Unit] =
      sql"""
        INSERT INTO game_plan_snapshots (id, team_id, name, game_plan, created_at)
        VALUES (${snap.id.value}, ${snap.teamId.value}, ${snap.name}, ${snap.gamePlanJson}, ${snap.createdAt})
      """.update.run.map(_ => ())

    override def listByTeam(teamId: TeamId): ConnectionIO[List[GamePlanSnapshot]] =
      sql"""
        SELECT id, team_id, name, game_plan, created_at
        FROM game_plan_snapshots WHERE team_id = ${teamId.value} ORDER BY created_at DESC
      """.query[(String, String, String, String, Instant)].to[List].map {
        _.map { case (id, tid, name, gp, at) =>
          GamePlanSnapshot(GamePlanSnapshotId(id), TeamId(tid), name, gp, at)
        }
      }

    override def findById(id: GamePlanSnapshotId): ConnectionIO[Option[GamePlanSnapshot]] =
      sql"""
        SELECT id, team_id, name, game_plan, created_at
        FROM game_plan_snapshots WHERE id = ${id.value}
      """.query[(String, String, String, String, Instant)].option.map {
        _.map { case (id, tid, name, gp, at) =>
          GamePlanSnapshot(GamePlanSnapshotId(id), TeamId(tid), name, gp, at)
        }
      }
  }
}
