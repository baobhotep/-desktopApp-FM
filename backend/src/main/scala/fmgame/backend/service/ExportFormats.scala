package fmgame.backend.service

import fmgame.backend.domain.MatchEventRecord
import fmgame.shared.domain.MatchId
import io.circe.syntax._
import io.circe.Json

/**
 * Eksport logów meczów do CSV lub StatsBomb-like JSON (pod trenowanie xG/VAEP w Pythonie).
 */
object ExportFormats {

  def eventsToCsv(rows: List[(MatchId, MatchEventRecord)]): String = {
    val header = "match_id,minute,event_type,team_id,actor_id,secondary_id,zone,outcome,xG,PSxG,xPass,zoneThreat"
    val lines = rows.map { case (mid, e) =>
      val xg = e.metadata.get("xG").getOrElse("")
      val psxg = e.metadata.get("PSxG").getOrElse("")
      val xPass = e.metadata.get("xPass").getOrElse("")
      val zoneThreat = e.metadata.get("zoneThreat").getOrElse("")
      List(
        mid.value,
        e.minute.toString,
        e.eventType,
        e.teamId.fold("")(_.value),
        e.actorPlayerId.fold("")(_.value),
        e.secondaryPlayerId.fold("")(_.value),
        e.zone.fold("")(_.toString),
        e.outcome.getOrElse(""),
        xg,
        psxg,
        xPass,
        zoneThreat
      ).map(s => if (s.contains(",") || s.contains("\"")) "\"" + s.replace("\"", "\"\"") + "\"" else s).mkString(",")
    }
    (header +: lines).mkString("\n")
  }

  /** Pojedyncze zdarzenie do JSON (do eksportu pełnego). */
  def eventToJson(mid: MatchId, e: MatchEventRecord): Json = Json.obj(
    "match_id" -> Json.fromString(mid.value),
    "minute" -> Json.fromInt(e.minute),
    "type" -> Json.fromString(e.eventType),
    "team_id" -> e.teamId.fold(Json.Null)(tid => Json.fromString(tid.value)),
    "player_id" -> e.actorPlayerId.fold(Json.Null)(pid => Json.fromString(pid.value)),
    "secondary_player_id" -> e.secondaryPlayerId.fold(Json.Null)(pid => Json.fromString(pid.value)),
    "zone" -> e.zone.fold(Json.Null)(z => Json.fromInt(z)),
    "outcome" -> e.outcome.fold(Json.Null)(s => Json.fromString(s)),
    "metadata" -> Json.obj(e.metadata.toSeq.map { case (k, v) => (k, Json.fromString(v)) }: _*)
  )

  /** StatsBomb-like JSON: lista zdarzeń z polami type, minute, team_id, player_id, zone, outcome, metadata. */
  def eventsToStatsBombJson(rows: List[(MatchId, MatchEventRecord)]): String = {
    val events = rows.map { case (mid, e) => eventToJson(mid, e) }
    Json.obj("events" -> Json.arr(events: _*)).noSpaces
  }
}
