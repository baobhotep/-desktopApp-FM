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
}

object LeagueRepository {
  def impl: LeagueRepository = new LeagueRepository {
    def create(league: League): ConnectionIO[Unit] =
      sql"""
        INSERT INTO leagues (id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone)
        VALUES (${league.id.value}, ${league.name}, ${league.teamCount}, ${league.currentMatchday},
          ${league.totalMatchdays}, ${league.seasonPhase.toString}, ${league.homeAdvantage},
          ${league.startDate}, ${league.createdByUserId.value}, ${league.createdAt}, ${league.timezone.getId})
      """.update.run.map(_ => ())

    def findById(id: LeagueId): ConnectionIO[Option[League]] =
      sql"""
        SELECT id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone
        FROM leagues WHERE id = ${id.value}
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String)].option.map {
        _.map { case (id, name, tc, cm, tm, phase, ha, start, creator, at, tz) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(phase), ha, start, UserId(creator), at, ZoneId.of(tz)
          )
        }
      }

    def update(league: League): ConnectionIO[Unit] =
      sql"""
        UPDATE leagues SET name = ${league.name}, current_matchday = ${league.currentMatchday},
          total_matchdays = ${league.totalMatchdays}, season_phase = ${league.seasonPhase.toString},
          home_advantage = ${league.homeAdvantage}, start_date = ${league.startDate}
        WHERE id = ${league.id.value}
      """.update.run.map(_ => ())

    def listByUser(userId: UserId): ConnectionIO[List[League]] =
      sql"""
        SELECT l.id, l.name, l.team_count, l.current_matchday, l.total_matchdays, l.season_phase,
          l.home_advantage, l.start_date, l.created_by_user_id, l.created_at, l.timezone
        FROM leagues l
        JOIN teams t ON t.league_id = l.id
        WHERE t.owner_user_id = ${userId.value}
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String)].to[List].map {
        _.map { case (id, name, tc, cm, tm, phase, ha, start, creator, at, tz) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(phase), ha, start, UserId(creator), at, ZoneId.of(tz)
          )
        }
      }

    def listBySeasonPhase(phase: SeasonPhase): ConnectionIO[List[League]] =
      sql"""
        SELECT id, name, team_count, current_matchday, total_matchdays, season_phase,
          home_advantage, start_date, created_by_user_id, created_at, timezone
        FROM leagues WHERE season_phase = ${phase.toString}
      """.query[(String, String, Int, Int, Int, String, Double, Option[LocalDate], String, Instant, String)].to[List].map {
        _.map { case (id, name, tc, cm, tm, ph, ha, start, creator, at, tz) =>
          League(
            LeagueId(id), name, tc, cm, tm,
            EnumParse.seasonPhase(ph), ha, start, UserId(creator), at, ZoneId.of(tz)
          )
        }
      }
  }
}
