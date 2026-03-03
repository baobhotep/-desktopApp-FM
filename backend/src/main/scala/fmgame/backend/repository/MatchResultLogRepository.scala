package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.fragments
import cats.data.NonEmptyList
import java.time.Instant
import io.circe.parser.parse
import io.circe.syntax.*
import MatchSummaryCodec.{encodeMatchSummary, decodeMatchSummary}

object MatchResultLogJson {
  private def eventToJson(e: MatchEventRecord): (Int, String, Option[String], Option[String], Option[String], Option[Int], Option[String], Map[String, String]) =
    (e.minute, e.eventType, e.actorPlayerId.map(_.value), e.secondaryPlayerId.map(_.value), e.teamId.map(_.value), e.zone, e.outcome, e.metadata)
  private def jsonToEvent(t: (Int, String, Option[String], Option[String], Option[String], Option[Int], Option[String], Map[String, String])): MatchEventRecord =
    MatchEventRecord(t._1, t._2, t._3.map(PlayerId.apply), t._4.map(PlayerId.apply), t._5.map(TeamId.apply), t._6, t._7, t._8)

  def encodeEvents(events: List[MatchEventRecord]): String = events.map(eventToJson).asJson.noSpaces
  def decodeEvents(s: String): List[MatchEventRecord] =
    if (s == null || s.isEmpty) Nil
    else parse(s).flatMap(_.as[List[(Int, String, Option[String], Option[String], Option[String], Option[Int], Option[String], Map[String, String])]]).toOption.getOrElse(Nil).map(jsonToEvent)

  def encodeSummary(o: Option[MatchSummary]): String =
    o.fold("null")(s => s.asJson.noSpaces)
  def decodeSummary(s: String): Option[MatchSummary] =
    if (s == null || s.isEmpty || s == "null") None
    else parse(s).flatMap(_.as[MatchSummary]).toOption
}

trait MatchResultLogRepository {
  def create(log: MatchResultLog): ConnectionIO[Unit]
  def findByMatchId(matchId: MatchId): ConnectionIO[Option[MatchResultLog]]
  /** Batch: wszystkie logi dla podanych matchId (jedno zapytanie zamiast N). */
  def findByMatchIds(matchIds: List[MatchId]): ConnectionIO[List[MatchResultLog]]
}

object MatchResultLogRepository {
  def impl: MatchResultLogRepository = new MatchResultLogRepository {
    import MatchResultLogJson.*

    override def create(log: MatchResultLog): ConnectionIO[Unit] =
      sql"""
        INSERT INTO match_result_logs (id, match_id, events, summary, created_at)
        VALUES (${log.id.value}, ${log.matchId.value}, ${encodeEvents(log.events)}, ${encodeSummary(log.summary)}, ${log.createdAt})
      """.update.run.map(_ => ())

    override def findByMatchId(matchId: MatchId): ConnectionIO[Option[MatchResultLog]] =
      sql"""
        SELECT id, match_id, events, summary, created_at
        FROM match_result_logs WHERE match_id = ${matchId.value}
      """.query[(String, String, String, String, Instant)].option.map {
        _.map { case (id, mid, ev, sum, at) =>
          MatchResultLog(MatchResultLogId(id), MatchId(mid), decodeEvents(ev), decodeSummary(sum), at)
        }
      }

    override def findByMatchIds(matchIds: List[MatchId]): ConnectionIO[List[MatchResultLog]] =
      NonEmptyList.fromList(matchIds.map(_.value)) match {
        case None => doobie.free.connection.pure(Nil)
        case Some(nel) =>
          (fr"SELECT id, match_id, events, summary, created_at FROM match_result_logs WHERE " ++
            fragments.in(fr"match_id", nel))
            .query[(String, String, String, String, Instant)]
            .to[List]
            .map(_.map { case (id, mid, ev, sum, at) =>
              MatchResultLog(MatchResultLogId(id), MatchId(mid), decodeEvents(ev), decodeSummary(sum), at)
            })
      }
  }
}
