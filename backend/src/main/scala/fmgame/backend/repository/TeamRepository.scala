package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait TeamRepository {
  def create(team: Team): ConnectionIO[Unit]
  def findById(id: TeamId): ConnectionIO[Option[Team]]
  def listByLeague(leagueId: LeagueId): ConnectionIO[List[Team]]
  def countByLeague(leagueId: LeagueId): ConnectionIO[Int]
  def update(team: Team): ConnectionIO[Unit]
  def updateLeagueId(teamId: TeamId, newLeagueId: LeagueId): ConnectionIO[Unit]
  def updateElo(teamId: TeamId, eloRating: Double): ConnectionIO[Unit]
}

object TeamRepository {
  def impl: TeamRepository = new TeamRepository {
    def create(team: Team): ConnectionIO[Unit] =
      sql"""
        INSERT INTO teams (id, league_id, name, owner_type, owner_user_id, owner_bot_id, budget, default_game_plan_id, created_at, elo_rating, manager_name)
        VALUES (${team.id.value}, ${team.leagueId.value}, ${team.name}, ${team.ownerType.toString},
          ${team.ownerUserId.map(_.value)}, ${team.ownerBotId.map(_.value)}, ${team.budget}, ${team.defaultGamePlanId.map(_.value)}, ${team.createdAt}, ${team.eloRating}, ${team.managerName})
      """.update.run.map(_ => ())

    def findById(id: TeamId): ConnectionIO[Option[Team]] =
      sql"""
        SELECT id, league_id, name, owner_type, owner_user_id, owner_bot_id, budget, created_at, COALESCE(elo_rating, 1500.0), manager_name
        FROM teams WHERE id = ${id.value}
      """.query[(String, String, String, String, Option[String], Option[String], BigDecimal, Instant, Double, Option[String])].option.map {
        _.map { case (id, lid, name, ot, ou, ob, budget, at, elo, mng) =>
          Team(
            TeamId(id), LeagueId(lid), name,
            EnumParse.teamOwnerType(ot),
            ou.map(UserId.apply), ob.map(BotId.apply),
            budget, None, at, elo, mng
          )
        }
      }

    def listByLeague(leagueId: LeagueId): ConnectionIO[List[Team]] =
      sql"""
        SELECT id, league_id, name, owner_type, owner_user_id, owner_bot_id, budget, created_at, COALESCE(elo_rating, 1500.0), manager_name
        FROM teams WHERE league_id = ${leagueId.value}
      """.query[(String, String, String, String, Option[String], Option[String], BigDecimal, Instant, Double, Option[String])].to[List].map {
        _.map { case (id, lid, name, ot, ou, ob, budget, at, elo, mng) =>
          Team(
            TeamId(id), LeagueId(lid), name,
            EnumParse.teamOwnerType(ot),
            ou.map(UserId.apply), ob.map(BotId.apply),
            budget, None, at, elo, mng
          )
        }
      }

    def countByLeague(leagueId: LeagueId): ConnectionIO[Int] =
      sql"SELECT COUNT(*) FROM teams WHERE league_id = ${leagueId.value}".query[Int].unique

    def update(team: Team): ConnectionIO[Unit] =
      sql"""
        UPDATE teams SET name = ${team.name}, league_id = ${team.leagueId.value}, budget = ${team.budget}, elo_rating = ${team.eloRating}, manager_name = ${team.managerName}
        WHERE id = ${team.id.value}
      """.update.run.map(_ => ())

    def updateLeagueId(teamId: TeamId, newLeagueId: LeagueId): ConnectionIO[Unit] =
      sql"UPDATE teams SET league_id = ${newLeagueId.value} WHERE id = ${teamId.value}".update.run.map(_ => ())

    def updateElo(teamId: TeamId, eloRating: Double): ConnectionIO[Unit] =
      sql"UPDATE teams SET elo_rating = $eloRating WHERE id = ${teamId.value}".update.run.map(_ => ())
  }
}
