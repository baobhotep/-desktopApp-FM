package fmgame.backend.service

import fmgame.backend.domain.Player
import fmgame.shared.domain.TeamId

/** Computes mean and stddev per position per attribute from league players (MODELE §10). */
object LeagueContextComputer {

  def computePositionStats(players: List[Player]): Map[String, Map[String, (Double, Double)]] = {
    // Group attribute values by (position, attrName). Each player contributes to each of their preferred positions.
    val byPosAttr: scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, List[Int]]] =
      scala.collection.mutable.Map.empty.withDefault(_ => scala.collection.mutable.Map.empty.withDefault(_ => List.empty))

    def addAttr(pos: String, attr: String, value: Int): Unit = {
      byPosAttr(pos)(attr) = value :: byPosAttr(pos)(attr)
    }

    players.foreach { p =>
      val allAttrs = p.physical ++ p.technical ++ p.mental
      if (p.preferredPositions.nonEmpty) {
        p.preferredPositions.foreach { pos =>
          allAttrs.foreach { case (attr, v) => addAttr(pos, attr, v) }
        }
      } else {
        allAttrs.foreach { case (attr, v) => addAttr("ANY", attr, v) }
      }
    }

    byPosAttr.toMap.view.mapValues { attrs =>
      attrs.toMap.view.mapValues { values =>
        val n = values.size.toDouble
        if (n <= 0) (0.0, 1.0)
        else {
          val mean = values.sum / n
          val variance = values.map(x => (x - mean) * (x - mean)).sum / n
          val stddev = math.sqrt(math.max(0, variance))
          (mean, if (stddev == 0) 1.0 else stddev)
        }
      }.toMap
    }.toMap
  }
}
