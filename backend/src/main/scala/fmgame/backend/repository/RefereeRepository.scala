package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait RefereeRepository {
  def create(referee: Referee): ConnectionIO[Unit]
  def findById(id: RefereeId): ConnectionIO[Option[Referee]]
  def listByLeague(leagueId: LeagueId): ConnectionIO[List[Referee]]
}

object RefereeRepository {
  def impl: RefereeRepository = new RefereeRepository {
    def create(referee: Referee): ConnectionIO[Unit] =
      sql"""
        INSERT INTO referees (id, league_id, name, strictness)
        VALUES (${referee.id.value}, ${referee.leagueId.value}, ${referee.name}, ${referee.strictness})
      """.update.run.map(_ => ())

    def findById(id: RefereeId): ConnectionIO[Option[Referee]] =
      sql"SELECT id, league_id, name, strictness FROM referees WHERE id = ${id.value}"
        .query[(String, String, String, Double)].option.map {
          _.map { case (rid, lid, name, s) => Referee(RefereeId(rid), LeagueId(lid), name, s) }
        }

    def listByLeague(leagueId: LeagueId): ConnectionIO[List[Referee]] =
      sql"""
        SELECT id, league_id, name, strictness
        FROM referees WHERE league_id = ${leagueId.value}
      """.query[(String, String, String, Double)].to[List].map {
        _.map { case (id, lid, name, s) =>
          Referee(RefereeId(id), LeagueId(lid), name, s)
        }
      }
  }
}
