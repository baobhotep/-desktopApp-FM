package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

case class Highlight(
  minute: Int,
  eventType: String,
  description: String,
  importance: Int, // 1-5, 5 = most important (Goal, Penalty, Red Card)
  teamId: Option[TeamId],
  actorPlayerId: Option[PlayerId],
  metadata: Map[String, String] = Map.empty
) {
  def toMap: Map[String, String] = {
    val base = Map(
      "minute" -> minute.toString,
      "eventType" -> eventType,
      "description" -> description,
      "importance" -> importance.toString
    ) ++ metadata
    val withTeam = teamId.fold(base)(tid => base + ("teamId" -> tid.value))
    actorPlayerId.fold(withTeam)(pid => withTeam + ("actorPlayerId" -> pid.value))
  }
}

object MatchHighlights {
  def extract(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId, maxHighlights: Int = 15): List[Highlight] = {
    if (events.isEmpty) return Nil

    val indexed = events.zipWithIndex
    val lastFirstHalfIdx = events.lastIndexWhere(_.minute <= 45)
    val firstSecondHalfIdx = events.indexWhere(_.minute >= 46)

    def isFirstOrLastOfHalf(e: MatchEventRecord, idx: Int): Boolean = {
      val isFirstFirstHalf = e.minute == 0 && idx == 0
      val isLastFirstHalf = lastFirstHalfIdx >= 0 && idx == lastFirstHalfIdx
      val isFirstSecondHalf = firstSecondHalfIdx >= 0 && idx == firstSecondHalfIdx
      val isLastEvent = idx == events.size - 1
      isFirstFirstHalf || isLastFirstHalf || isFirstSecondHalf || isLastEvent
    }

    def isKeyPass(e: MatchEventRecord, idx: Int): Boolean = {
      val next = events.drop(idx + 1).headOption
      next.exists { n =>
        (n.eventType == "Shot" || n.eventType == "Goal") &&
        n.teamId == e.teamId &&
        e.eventType == "Pass"
      }
    }

    def parseXg(meta: Map[String, String]): Double =
      meta.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.0)

    def buildDescription(e: MatchEventRecord, extra: String = ""): String = {
      val zonePart = e.zone.fold("")(z => s"zone $z")
      val xgPart = e.metadata.get("xG").fold("")(xg => s"xG: $xg")
      val parts = List(zonePart, xgPart).filter(_.nonEmpty).mkString(", ")
      val suffix = if (parts.nonEmpty) s" ($parts)" else ""
      s"$extra$suffix".trim
    }

    def scoreAndHighlight(e: MatchEventRecord, idx: Int): Option[(Int, Highlight)] = {
      val xg = parseXg(e.metadata)

      e.eventType match {
        case "Goal" =>
          val desc = buildDescription(e, "Goal!")
          Some((5, Highlight(e.minute, "Goal", desc, 5, e.teamId, e.actorPlayerId, e.metadata)))

        case "Penalty" =>
          val outcome = e.outcome.getOrElse("")
          val desc = if (outcome == "Success") "Penalty scored!" else "Penalty missed!"
          Some((5, Highlight(e.minute, "Penalty", desc, 5, e.teamId, e.actorPlayerId, e.metadata)))

        case "RedCard" =>
          val reason = e.metadata.get("reason").fold("")(r => s" ($r)")
          Some((5, Highlight(e.minute, "RedCard", s"Red card!$reason", 5, e.teamId, e.actorPlayerId, e.metadata)))

        case "Shot" =>
          val (outcome, imp) = e.outcome match {
            case Some("Saved") | Some("Blocked") =>
              val imp = if (xg > 0.5) 5 else if (xg > 0.3) 4 else 2
              (e.outcome.get, imp)
            case Some("Missed") =>
              val imp = if (xg > 0.5) 5 else if (xg > 0.3) 4 else 2
              ("Missed", imp)
            case _ => ("", 1)
          }
          val desc = if (outcome.nonEmpty) buildDescription(e, s"Shot $outcome") else buildDescription(e, "Shot")
          Some((imp, Highlight(e.minute, "Shot", desc, imp, e.teamId, e.actorPlayerId, e.metadata)))

        case "YellowCard" =>
          Some((3, Highlight(e.minute, "YellowCard", "Yellow card", 3, e.teamId, e.actorPlayerId, e.metadata)))

        case "Injury" =>
          val sev = e.metadata.get("severity").fold("")(s => s" ($s)")
          Some((3, Highlight(e.minute, "Injury", s"Injury$sev", 3, e.teamId, e.actorPlayerId, e.metadata)))

        case "Substitution" =>
          Some((2, Highlight(e.minute, "Substitution", "Substitution", 2, e.teamId, e.actorPlayerId, e.metadata)))

        case "Pass" if isKeyPass(e, idx) =>
          Some((3, Highlight(e.minute, "KeyPass", "Key pass (led to shot)", 3, e.teamId, e.actorPlayerId, e.metadata)))

        case _ if isFirstOrLastOfHalf(e, idx) =>
          val label = if (e.minute == 0) "Kick-off" else if (idx == events.size - 1) "Full-time" else "Half boundary"
          Some((2, Highlight(e.minute, e.eventType, label, 2, e.teamId, e.actorPlayerId, e.metadata)))

        case _ => None
      }
    }

    val scored = indexed.flatMap { case (e, idx) => scoreAndHighlight(e, idx) }
    val sorted = scored.sortBy { case (imp, h) => (-imp, h.minute) }
    val top = sorted.take(maxHighlights).map(_._2)
    top.sortBy(_.minute)
  }
}
