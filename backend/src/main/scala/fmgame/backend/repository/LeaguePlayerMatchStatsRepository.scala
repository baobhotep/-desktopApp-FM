package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*

/** Agregacja statystyk sezonowych per mecz (gole, asysty, minuty) – odczyt z tabeli zamiast ładowania wszystkich zdarzeń. */
trait LeaguePlayerMatchStatsRepository {
  /** Zapisuje statystyki per zawodnik dla jednego meczu (po rozegraniu). */
  def insertForMatch(leagueId: LeagueId, matchId: MatchId, rows: List[(PlayerId, TeamId, Int, Int, Int)]): ConnectionIO[Unit]
  /** Sumuje gole i asysty per (player_id, team_id) w lidze. Zwraca (playerId, teamId, goals, assists). */
  def sumByLeague(leagueId: LeagueId): ConnectionIO[List[(PlayerId, TeamId, Int, Int)]]
  /** Usuwa wszystkie wpisy dla ligi (przed startem nowego sezonu). */
  def deleteByLeague(leagueId: LeagueId): ConnectionIO[Unit]
}

object LeaguePlayerMatchStatsRepository {
  def impl: LeaguePlayerMatchStatsRepository = new LeaguePlayerMatchStatsRepository {
    override def insertForMatch(leagueId: LeagueId, matchId: MatchId, rows: List[(PlayerId, TeamId, Int, Int, Int)]): ConnectionIO[Unit] =
      if (rows.isEmpty) doobie.free.connection.pure(())
      else {
        val insertOne = Update[(String, String, String, String, Int, Int, Int)](
          "INSERT INTO league_player_match_stats (league_id, match_id, player_id, team_id, goals, assists, minutes_played) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        val params = rows.map { case (pid, tid, g, a, m) => (leagueId.value, matchId.value, pid.value, tid.value, g, a, m) }
        insertOne.updateMany(params).map(_ => ())
      }

    override def sumByLeague(leagueId: LeagueId): ConnectionIO[List[(PlayerId, TeamId, Int, Int)]] =
      sql"""
        SELECT player_id, team_id, SUM(goals), SUM(assists)
        FROM league_player_match_stats
        WHERE league_id = ${leagueId.value}
        GROUP BY player_id, team_id
      """.query[(String, String, Int, Int)].to[List].map(_.map { case (pid, tid, g, a) =>
        (PlayerId(pid), TeamId(tid), g, a)
      })

    override def deleteByLeague(leagueId: LeagueId): ConnectionIO[Unit] =
      sql"DELETE FROM league_player_match_stats WHERE league_id = ${leagueId.value}".update.run.map(_ => ())
  }
}
