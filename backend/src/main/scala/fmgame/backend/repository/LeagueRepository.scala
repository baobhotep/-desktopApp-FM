package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

trait LeagueRepository {
  def create(league: League): ConnectionIO[Unit]
  def findById(id: LeagueId): ConnectionIO[Option[League]]
  def update(league: League): ConnectionIO[Unit]
  def listByUser(userId: UserId): ConnectionIO[List[League]]
  def listBySeasonPhase(phase: SeasonPhase): ConnectionIO[List[League]]
  def listByLeagueSystemName(systemName: String): ConnectionIO[List[League]]
}

object LeagueRepository {
  def impl: LeagueRepository = new LeagueRepository {
    def create(league: League): ConnectionIO[Unit] =
      sql"""
        INSERT INTO leagues (id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone, league_system_name, tier)
        VALUES (${league.id.value}, ${league.name}, ${league.teamCount}, ${league.currentMatchday},
          ${league.totalMatchdays}, ${league.seasonPhase.toString}, ${league.homeAdvantage},
          ${league.startDate}, ${league.createdByUserId.value}, ${league.createdAt}, ${league.timezone.getId},
          ${league.leagueSystemName}, ${league.tier})
      """.update.run.map(_ => ())

    def findById(id: LeagueId): ConnectionIO[Option[League]] =
      sql"""
        SELECT id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone, league_system_name, tier
        FROM leagues WHERE id = ${id.value}
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String, Option[String], Option[Int])].option.map {
        _.map { case (id, name, tc, cm, tm, phase, ha, start, creator, at, tz, sysName, tierOpt) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(phase), ha, start, UserId(creator), at, ZoneId.of(tz),
            leagueSystemName = sysName, tier = tierOpt
          )
        }
      }

    def update(league: League): ConnectionIO[Unit] =
      sql"""
        UPDATE leagues SET name = ${league.name}, current_matchday = ${league.currentMatchday},
          total_matchdays = ${league.totalMatchdays}, season_phase = ${league.seasonPhase.toString},
          home_advantage = ${league.homeAdvantage}, start_date = ${league.startDate},
          league_system_name = ${league.leagueSystemName}, tier = ${league.tier}
        WHERE id = ${league.id.value}
      """.update.run.map(_ => ())

    def listByUser(userId: UserId): ConnectionIO[List[League]] =
      sql"""
        SELECT l.id, l.name, l.team_count, l.current_matchday, l.total_matchdays, l.season_phase,
          l.home_advantage, l.start_date, l.created_by_user_id, l.created_at, l.timezone, l.league_system_name, l.tier
        FROM leagues l
        JOIN teams t ON t.league_id = l.id
        WHERE t.owner_user_id = ${userId.value}
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String, Option[String], Option[Int])].to[List].map {
        _.map { case (id, name, tc, cm, tm, phase, ha, start, creator, at, tz, sysName, tierOpt) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(phase), ha, start, UserId(creator), at, ZoneId.of(tz),
            leagueSystemName = sysName, tier = tierOpt
          )
        }
      }

    def listBySeasonPhase(phase: SeasonPhase): ConnectionIO[List[League]] =
      sql"""
        SELECT id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone, league_system_name, tier
        FROM leagues WHERE season_phase = ${phase.toString}
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String, Option[String], Option[Int])].to[List].map {
        _.map { case (id, name, tc, cm, tm, ph, ha, start, creator, at, tz, sysName, tierOpt) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(ph), ha, start, UserId(creator), at, ZoneId.of(tz),
            leagueSystemName = sysName, tier = tierOpt
          )
        }
      }

    def listByLeagueSystemName(systemName: String): ConnectionIO[List[League]] =
      sql"""
        SELECT id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone, league_system_name, tier
        FROM leagues WHERE league_system_name = ${systemName}
        ORDER BY tier ASC
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String, Option[String], Option[Int])].to[List].map {
        _.map { case (id, name, tc, cm, tm, ph, ha, start, creator, at, tz, sysName, tierOpt) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(ph), ha, start, UserId(creator), at, ZoneId.of(tz),
            leagueSystemName = sysName, tier = tierOpt
          )
        }
      }
  }
}
