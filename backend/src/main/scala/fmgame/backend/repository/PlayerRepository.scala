package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant
import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}

object PlayerJson {
  def encodeMapInt(m: Map[String, Int]): String = m.asJson.noSpaces
  def encodeMapDouble(m: Map[String, Double]): String = m.asJson.noSpaces
  def decodeMapInt(s: String): Map[String, Int] =
    parse(s).flatMap(_.as[Map[String, Int]]).getOrElse(Map.empty)
  def decodeMapDouble(s: String): Map[String, Double] =
    parse(s).flatMap(_.as[Map[String, Double]]).getOrElse(Map.empty)

  implicit val injuryStatusEncoder: Encoder[InjuryStatus] = Encoder.forProduct3("sinceMatchday", "returnAtMatchday", "severity")(s => (s.sinceMatchday, s.returnAtMatchday, s.severity))
  implicit val injuryStatusDecoder: Decoder[InjuryStatus] = Decoder.forProduct3("sinceMatchday", "returnAtMatchday", "severity")(InjuryStatus.apply)
  def encodeInjury(o: Option[InjuryStatus]): String = o.asJson.noSpaces
  def decodeInjury(s: String): Option[InjuryStatus] = {
    if (s == null || s.isEmpty || s.trim == "null") None
    else parse(s).flatMap(_.as[InjuryStatus]).toOption
  }
}

trait PlayerRepository {
  def create(player: Player): ConnectionIO[Unit]
  def listByTeam(teamId: TeamId): ConnectionIO[List[Player]]
  def countByTeam(teamId: TeamId): ConnectionIO[Int]
  def findById(id: PlayerId): ConnectionIO[Option[Player]]
  def updateTeamId(playerId: PlayerId, newTeamId: TeamId): ConnectionIO[Unit]
  def updateName(playerId: PlayerId, firstName: String, lastName: String): ConnectionIO[Unit]
  def updateFreshnessMorale(playerId: PlayerId, freshness: Double, morale: Double): ConnectionIO[Unit]
  def updateInjury(playerId: PlayerId, injury: Option[InjuryStatus]): ConnectionIO[Unit]
  def updateAttributes(playerId: PlayerId, physical: Map[String, Int], technical: Map[String, Int], mental: Map[String, Int]): ConnectionIO[Unit]
}

object PlayerRepository {
  def impl: PlayerRepository = new PlayerRepository {
    import PlayerJson.*

    private def rowToPlayer(id: String, tid: String, fn: String, ln: String, pp: String, ph: String, te: String, me: String, tr: String, bp: String, inj: String, fr: Double, mo: Double, at: Instant): Player = {
      val positions = if (pp == null || pp.isEmpty) Set.empty[String] else pp.split(",").map(_.trim).filter(_.nonEmpty).toSet
      Player(
        PlayerId(id), TeamId(tid), fn, ln, positions,
        decodeMapInt(if (ph == null) "{}" else ph),
        decodeMapInt(if (te == null) "{}" else te),
        decodeMapInt(if (me == null) "{}" else me),
        decodeMapInt(if (tr == null) "{}" else tr),
        decodeMapDouble(if (bp == null) "{}" else bp),
        decodeInjury(if (inj == null) "" else inj),
        fr, mo, at
      )
    }

    def create(player: Player): ConnectionIO[Unit] =
      sql"""
        INSERT INTO players (id, team_id, first_name, last_name, preferred_positions, physical, technical, mental, traits, body_params, injury, freshness, morale, created_at)
        VALUES (${player.id.value}, ${player.teamId.value}, ${player.firstName}, ${player.lastName},
          ${player.preferredPositions.mkString(",")}, ${encodeMapInt(player.physical)}, ${encodeMapInt(player.technical)},
          ${encodeMapInt(player.mental)}, ${encodeMapInt(player.traits)}, ${encodeMapDouble(player.bodyParams)},
          ${encodeInjury(player.injury)}, ${player.freshness}, ${player.morale}, ${player.createdAt})
      """.update.run.map(_ => ())

    def listByTeam(teamId: TeamId): ConnectionIO[List[Player]] =
      sql"""
        SELECT id, team_id, first_name, last_name, preferred_positions, physical, technical, mental, traits, body_params, injury, freshness, morale, created_at
        FROM players WHERE team_id = ${teamId.value}
      """.query[(String, String, String, String, String, String, String, String, String, String, String, Double, Double, Instant)].to[List].map {
        _.map { case (id, tid, fn, ln, pp, ph, te, me, tr, bp, inj, fr, mo, at) =>
          rowToPlayer(id, tid, fn, ln, pp, ph, te, me, tr, bp, inj, fr, mo, at)
        }
      }

    def countByTeam(teamId: TeamId): ConnectionIO[Int] =
      sql"SELECT COUNT(*) FROM players WHERE team_id = ${teamId.value}".query[Int].unique

    def findById(id: PlayerId): ConnectionIO[Option[Player]] =
      sql"""
        SELECT id, team_id, first_name, last_name, preferred_positions, physical, technical, mental, traits, body_params, injury, freshness, morale, created_at
        FROM players WHERE id = ${id.value}
      """.query[(String, String, String, String, String, String, String, String, String, String, String, Double, Double, Instant)].option.map {
        _.map { case (pid, tid, fn, ln, pp, ph, te, me, tr, bp, inj, fr, mo, at) =>
          rowToPlayer(pid, tid, fn, ln, pp, ph, te, me, tr, bp, inj, fr, mo, at)
        }
      }

    def updateTeamId(playerId: PlayerId, newTeamId: TeamId): ConnectionIO[Unit] =
      sql"UPDATE players SET team_id = ${newTeamId.value} WHERE id = ${playerId.value}".update.run.map(_ => ())

    def updateName(playerId: PlayerId, firstName: String, lastName: String): ConnectionIO[Unit] =
      sql"UPDATE players SET first_name = ${firstName}, last_name = ${lastName} WHERE id = ${playerId.value}".update.run.map(_ => ())

    def updateFreshnessMorale(playerId: PlayerId, freshness: Double, morale: Double): ConnectionIO[Unit] =
      sql"UPDATE players SET freshness = ${freshness}, morale = ${morale} WHERE id = ${playerId.value}".update.run.map(_ => ())

    def updateInjury(playerId: PlayerId, injury: Option[InjuryStatus]): ConnectionIO[Unit] = {
      val json = encodeInjury(injury)
      sql"UPDATE players SET injury = ${json} WHERE id = ${playerId.value}".update.run.map(_ => ())
    }

    def updateAttributes(playerId: PlayerId, physical: Map[String, Int], technical: Map[String, Int], mental: Map[String, Int]): ConnectionIO[Unit] =
      sql"""
        UPDATE players
        SET physical = ${encodeMapInt(physical)},
            technical = ${encodeMapInt(technical)},
            mental = ${encodeMapInt(mental)}
        WHERE id = ${playerId.value}
      """.update.run.map(_ => ())
  }
}
