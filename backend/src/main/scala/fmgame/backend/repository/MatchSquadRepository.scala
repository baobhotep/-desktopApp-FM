package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant
import io.circe.parser.parse
import io.circe.syntax.*

object MatchSquadJson {
  def encodeLineup(lineup: List[LineupSlot]): String =
    lineup.map(s => (s.playerId.value, s.positionSlot)).asJson.noSpaces
  def decodeLineup(s: String): List[LineupSlot] =
    if (s == null || s.isEmpty) Nil
    else parse(s).flatMap(_.as[List[(String, String)]]).toOption.getOrElse(Nil).map { case (pid, slot) => LineupSlot(PlayerId(pid), slot) }
}

trait MatchSquadRepository {
  def create(squad: MatchSquad): ConnectionIO[Unit]
  def update(squad: MatchSquad): ConnectionIO[Unit]
  def findByMatchAndTeam(matchId: MatchId, teamId: TeamId): ConnectionIO[Option[MatchSquad]]
  def listByMatch(matchId: MatchId): ConnectionIO[List[MatchSquad]]
}

object MatchSquadRepository {
  def impl: MatchSquadRepository = new MatchSquadRepository {
    import MatchSquadJson.*

    def create(squad: MatchSquad): ConnectionIO[Unit] =
      sql"""
        INSERT INTO match_squads (id, match_id, team_id, lineup, game_plan, submitted_at, source)
        VALUES (${squad.id.value}, ${squad.matchId.value}, ${squad.teamId.value}, ${encodeLineup(squad.lineup)}, ${squad.gamePlanJson}, ${squad.submittedAt}, ${squad.source.toString})
      """.update.run.map(_ => ())

    def update(squad: MatchSquad): ConnectionIO[Unit] =
      sql"""
        UPDATE match_squads SET lineup = ${MatchSquadJson.encodeLineup(squad.lineup)}, game_plan = ${squad.gamePlanJson}, submitted_at = ${squad.submittedAt}, source = ${squad.source.toString}
        WHERE id = ${squad.id.value}
      """.update.run.map(_ => ())

    def findByMatchAndTeam(matchId: MatchId, teamId: TeamId): ConnectionIO[Option[MatchSquad]] =
      sql"""
        SELECT id, match_id, team_id, lineup, game_plan, submitted_at, source
        FROM match_squads WHERE match_id = ${matchId.value} AND team_id = ${teamId.value}
      """.query[(String, String, String, String, String, Instant, String)].option.map {
        _.map { case (id, mid, tid, lineup, gp, sub, src) =>
          MatchSquad(
            MatchSquadId(id), MatchId(mid), TeamId(tid),
            decodeLineup(lineup), gp, sub, EnumParse.matchSquadSource(src)
          )
        }
      }

    def listByMatch(matchId: MatchId): ConnectionIO[List[MatchSquad]] =
      sql"""
        SELECT id, match_id, team_id, lineup, game_plan, submitted_at, source
        FROM match_squads WHERE match_id = ${matchId.value}
      """.query[(String, String, String, String, String, Instant, String)].to[List].map {
        _.map { case (id, mid, tid, lineup, gp, sub, src) =>
          MatchSquad(
            MatchSquadId(id), MatchId(mid), TeamId(tid),
            decodeLineup(lineup), gp, sub, EnumParse.matchSquadSource(src)
          )
        }
      }
  }
}
