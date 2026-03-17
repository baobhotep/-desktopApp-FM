package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait ContractRepository {
  def create(contract: Contract): ConnectionIO[Unit]
  def listByTeam(teamId: TeamId): ConnectionIO[List[Contract]]
  def findByPlayer(playerId: PlayerId): ConnectionIO[Option[Contract]]
}

object ContractRepository {
  def impl: ContractRepository = new ContractRepository {
    def create(contract: Contract): ConnectionIO[Unit] =
      sql"""
        INSERT INTO contracts (id, player_id, team_id, weekly_salary, start_matchday, end_matchday, signing_bonus, release_clause, created_at)
        VALUES (${contract.id.value}, ${contract.playerId.value}, ${contract.teamId.value}, ${contract.weeklySalary},
          ${contract.startMatchday}, ${contract.endMatchday}, ${contract.signingBonus}, ${contract.releaseClause}, ${contract.createdAt})
      """.update.run.map(_ => ())

    def listByTeam(teamId: TeamId): ConnectionIO[List[Contract]] =
      sql"""
        SELECT id, player_id, team_id, weekly_salary, start_matchday, end_matchday, signing_bonus, release_clause, created_at
        FROM contracts WHERE team_id = ${teamId.value}
      """.query[(String, String, String, BigDecimal, Int, Int, BigDecimal, Option[BigDecimal], Instant)].to[List].map {
        _.map { case (id, pid, tid, salary, startMd, endMd, bonus, clause, at) =>
          Contract(
            ContractId(id),
            PlayerId(pid),
            TeamId(tid),
            salary,
            startMd,
            endMd,
            bonus,
            clause,
            at
          )
        }
      }

    def findByPlayer(playerId: PlayerId): ConnectionIO[Option[Contract]] =
      sql"""
        SELECT id, player_id, team_id, weekly_salary, start_matchday, end_matchday, signing_bonus, release_clause, created_at
        FROM contracts WHERE player_id = ${playerId.value}
      """.query[(String, String, String, BigDecimal, Int, Int, BigDecimal, Option[BigDecimal], Instant)].option.map {
        _.map { case (id, pid, tid, salary, startMd, endMd, bonus, clause, at) =>
          Contract(
            ContractId(id),
            PlayerId(pid),
            TeamId(tid),
            salary,
            startMd,
            endMd,
            bonus,
            clause,
            at
          )
        }
      }
  }
}
