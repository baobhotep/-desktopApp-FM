package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant
import io.circe.parser.parse
import io.circe.syntax.*

object LeagueContextJson {
  def encodePositionStats(m: Map[String, Map[String, (Double, Double)]]): String = {
    val obj = io.circe.JsonObject.fromIterable(m.map { case (pos, attrs) =>
      pos -> io.circe.Json.fromJsonObject(io.circe.JsonObject.fromIterable(attrs.map { case (attr, (mean, std)) =>
        attr -> io.circe.Json.obj("mean" -> io.circe.Json.fromDouble(mean).getOrElse(io.circe.Json.Null), "stddev" -> io.circe.Json.fromDouble(std).getOrElse(io.circe.Json.Null))
      }.toIterable))
    }.toIterable)
    io.circe.Json.fromJsonObject(obj).noSpaces
  }

  def decodePositionStats(s: String): Map[String, Map[String, (Double, Double)]] =
    if (s == null || s.isEmpty) Map.empty
    else parse(s).toOption.flatMap { json =>
      json.asObject.map { obj =>
        obj.toMap.flatMap { case (pos, v) =>
          v.asObject.map { attrs =>
            pos -> attrs.toMap.flatMap { case (attr, v2) =>
              v2.asObject.flatMap { o =>
                val mean = o("mean").flatMap(_.asNumber).map(_.toDouble).getOrElse(0.0)
                val std  = o("stddev").flatMap(_.asNumber).map(_.toDouble).getOrElse(0.0)
                Some(attr -> (mean, std))
              }
            }.toMap
          }.toList.headOption.toMap
        }.toMap
      }
    }.getOrElse(Map.empty)
}

trait LeagueContextRepository {
  def create(ctx: LeagueContext): ConnectionIO[Unit]
  def findByLeagueId(leagueId: LeagueId): ConnectionIO[Option[LeagueContext]]
}

object LeagueContextRepository {
  def impl: LeagueContextRepository = new LeagueContextRepository {
    import LeagueContextJson._
    override def create(ctx: LeagueContext): ConnectionIO[Unit] =
      sql"""
        INSERT INTO league_contexts (id, league_id, position_stats, created_at)
        VALUES (${ctx.id.value}, ${ctx.leagueId.value}, ${encodePositionStats(ctx.positionStats)}, ${ctx.createdAt})
      """.update.run.map(_ => ())

    override def findByLeagueId(leagueId: LeagueId): ConnectionIO[Option[LeagueContext]] =
      sql"""
        SELECT id, league_id, position_stats, created_at
        FROM league_contexts WHERE league_id = ${leagueId.value}
      """.query[(String, String, String, Instant)].option.map {
        _.map { case (id, lid, ps, at) =>
          LeagueContext(LeagueContextId(id), LeagueId(lid), decodePositionStats(ps), at)
        }
      }
  }
}
