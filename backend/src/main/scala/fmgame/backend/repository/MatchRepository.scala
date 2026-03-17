package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait MatchRepository {
  def create(m: Match): ConnectionIO[Unit]
  def update(m: Match): ConnectionIO[Unit]
  def findById(id: MatchId): ConnectionIO[Option[Match]]
  def listByLeague(leagueId: LeagueId): ConnectionIO[List[Match]]
  def listByLeagueAndMatchday(leagueId: LeagueId, matchday: Int): ConnectionIO[List[Match]]
  /** Usuwa wszystkie mecze ligi i powiązane dane (match_squads, match_result_logs) – przed startem nowego sezonu. */
  def deleteByLeague(leagueId: LeagueId): ConnectionIO[Unit]
  /** Dla endpointu /metrics: liczba rozegranych meczów i suma bramek. */
  def countPlayedAndTotalGoals: ConnectionIO[(Int, Int)]
}

object MatchRepository {
  def impl: MatchRepository = new MatchRepository {
    def create(m: Match): ConnectionIO[Unit] =
      sql"""
        INSERT INTO matches (id, league_id, matchday, home_team_id, away_team_id, scheduled_at, status, home_goals, away_goals, referee_id, result_log_id)
        VALUES (${m.id.value}, ${m.leagueId.value}, ${m.matchday}, ${m.homeTeamId.value}, ${m.awayTeamId.value},
          ${m.scheduledAt}, ${m.status.toString}, ${m.homeGoals}, ${m.awayGoals}, ${m.refereeId.value}, ${m.resultLogId.map(_.value)})
      """.update.run.map(_ => ())

    def update(m: Match): ConnectionIO[Unit] =
      sql"""
        UPDATE matches SET status = ${m.status.toString}, home_goals = ${m.homeGoals}, away_goals = ${m.awayGoals}, result_log_id = ${m.resultLogId.map(_.value)}
        WHERE id = ${m.id.value}
      """.update.run.map(_ => ())

    def findById(id: MatchId): ConnectionIO[Option[Match]] =
      sql"""
        SELECT id, league_id, matchday, home_team_id, away_team_id, scheduled_at, status, home_goals, away_goals, referee_id, result_log_id
        FROM matches WHERE id = ${id.value}
      """.query[(String, String, Int, String, String, Instant, String, Option[Int], Option[Int], String, Option[String])].option.map {
        _.map { case (id, lid, md, ht, at, sch, st, hg, ag, rid, rlid) =>
          Match(
            MatchId(id), LeagueId(lid), md, TeamId(ht), TeamId(at), sch,
            EnumParse.matchStatus(st), hg, ag, RefereeId(rid), rlid.map(MatchResultLogId.apply)
          )
        }
      }

    def listByLeague(leagueId: LeagueId): ConnectionIO[List[Match]] =
      sql"""
        SELECT id, league_id, matchday, home_team_id, away_team_id, scheduled_at, status, home_goals, away_goals, referee_id, result_log_id
        FROM matches WHERE league_id = ${leagueId.value} ORDER BY matchday, scheduled_at
      """.query[(String, String, Int, String, String, Instant, String, Option[Int], Option[Int], String, Option[String])].to[List].map {
        _.map { case (id, lid, md, ht, at, sch, st, hg, ag, rid, rlid) =>
          Match(
            MatchId(id), LeagueId(lid), md, TeamId(ht), TeamId(at), sch,
            EnumParse.matchStatus(st), hg, ag, RefereeId(rid), rlid.map(MatchResultLogId.apply)
          )
        }
      }

    def listByLeagueAndMatchday(leagueId: LeagueId, matchday: Int): ConnectionIO[List[Match]] =
      sql"""
        SELECT id, league_id, matchday, home_team_id, away_team_id, scheduled_at, status, home_goals, away_goals, referee_id, result_log_id
        FROM matches WHERE league_id = ${leagueId.value} AND matchday = $matchday ORDER BY scheduled_at
      """.query[(String, String, Int, String, String, Instant, String, Option[Int], Option[Int], String, Option[String])].to[List].map {
        _.map { case (id, lid, md, ht, at, sch, st, hg, ag, rid, rlid) =>
          Match(
            MatchId(id), LeagueId(lid), md, TeamId(ht), TeamId(at), sch,
            EnumParse.matchStatus(st), hg, ag, RefereeId(rid), rlid.map(MatchResultLogId.apply)
          )
        }
      }

    def deleteByLeague(leagueId: LeagueId): ConnectionIO[Unit] = {
      val lid = leagueId.value
      for {
        _ <- sql"DELETE FROM match_squads WHERE match_id IN (SELECT id FROM matches WHERE league_id = $lid)".update.run
        _ <- sql"DELETE FROM match_result_logs WHERE match_id IN (SELECT id FROM matches WHERE league_id = $lid)".update.run
        _ <- sql"DELETE FROM matches WHERE league_id = $lid".update.run
      } yield ()
    }

    def countPlayedAndTotalGoals: ConnectionIO[(Int, Int)] =
      sql"""
        SELECT COUNT(*), COALESCE(SUM(COALESCE(home_goals, 0) + COALESCE(away_goals, 0)), 0)
        FROM matches WHERE status = 'Played'
      """.query[(Int, Long)].unique.map { case (c, g) => (c, g.toInt) }
  }
}
