package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/** Default 4-3-3 slots (KONTRAKTY §3). Picks 1 GK + 10 outfield from available players. */
object DefaultSquadBuilder {
  val defaultSlots: List[String] = List("GK", "LCB", "RCB", "LB", "RB", "CDM", "LCM", "RCM", "LW", "RW", "ST")
  val defaultGamePlanJson: String = """{"formationName":"4-3-3"}"""

  /** Build default lineup: available players (not injured or returned), 1 GK then 10 outfield by preference. */
  def buildDefaultLineup(players: List[Player], currentMatchday: Int): Option[List[LineupSlot]] = {
    val available = players.filter { p =>
      p.injury match {
        case None => true
        case Some(inj) => inj.returnAtMatchday <= currentMatchday
      }
    }
    val gks = available.filter(_.preferredPositions.contains("GK"))
    val outfield = available.filter(!_.preferredPositions.contains("GK"))
    if (gks.isEmpty || outfield.size < 10) return None
    val sortedOutfield = outfield.sortBy(p => -(p.physical.values.sum + p.technical.values.sum + p.mental.values.sum)).take(10)
    val eleven = (gks.head :: sortedOutfield).zip(defaultSlots).map { case (p, slot) => LineupSlot(p.id, slot) }
    Some(eleven)
  }
}
