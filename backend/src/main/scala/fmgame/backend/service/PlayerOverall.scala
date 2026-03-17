package fmgame.backend.service

import fmgame.backend.domain.*

/**
 * Obliczanie overall zawodnika (1–20) z atrybutów physical/technical/mental.
 * Overall pozycyjny: wagi kluczowych atrybutów zależne od pozycji (jak w FM).
 * Kategorie: fizyczne, technika, mentalne, obrona (średnie do pasków w UI).
 */
object PlayerOverall {

  private def avg(values: Iterable[Int], default: Double = 10.0): Double =
    if (values.isEmpty) default else values.sum.toDouble / values.size

  /** Średnia atrybutów fizycznych (1–20). */
  def physicalAvg(p: Player): Double = avg(p.physical.values)

  /** Średnia atrybutów technicznych (1–20). */
  def technicalAvg(p: Player): Double = avg(p.technical.values)

  /** Średnia atrybutów mentalnych (1–20). */
  def mentalAvg(p: Player): Double = avg(p.mental.values)

  /** Średnia atrybutów defensywnych: tackling, marking, positioning, anticipation, concentration (techniczne + mentalne). */
  def defenseAvg(p: Player): Double = {
    val keys = List("tackling", "marking", "positioning", "anticipation", "concentration")
    val values = keys.flatMap(k => p.technical.get(k).orElse(p.mental.get(k)))
    avg(values)
  }

  /** Wagi atrybutów dla overall pozycyjnego (pozycja -> lista (klucz, waga)). */
  private val positionWeights: Map[String, List[(String, Double)]] = Map(
    "GK" -> List("gkReflexes" -> 1.4, "gkHandling" -> 1.3, "gkPositioning" -> 1.2, "gkKicking" -> 1.0, "positioning" -> 1.0, "strength" -> 0.8),
    "CB" -> List("tackling" -> 1.3, "marking" -> 1.3, "heading" -> 1.2, "strength" -> 1.2, "positioning" -> 1.1, "anticipation" -> 1.0),
    "LB" -> List("pace" -> 1.2, "stamina" -> 1.1, "crossing" -> 1.2, "tackling" -> 1.1, "positioning" -> 1.0),
    "RB" -> List("pace" -> 1.2, "stamina" -> 1.1, "crossing" -> 1.2, "tackling" -> 1.1, "positioning" -> 1.0),
    "DM" -> List("tackling" -> 1.2, "passing" -> 1.2, "positioning" -> 1.1, "stamina" -> 1.1, "decisions" -> 1.0),
    "CM" -> List("passing" -> 1.2, "stamina" -> 1.1, "decisions" -> 1.1, "vision" -> 1.0, "ballControl" -> 1.0),
    "AM" -> List("passing" -> 1.2, "vision" -> 1.2, "firstTouch" -> 1.1, "decisions" -> 1.0, "longShots" -> 1.0),
    "LW" -> List("pace" -> 1.2, "dribbling" -> 1.2, "crossing" -> 1.1, "offTheBall" -> 1.0),
    "RW" -> List("pace" -> 1.2, "dribbling" -> 1.2, "crossing" -> 1.1, "offTheBall" -> 1.0),
    "ST" -> List("shooting" -> 1.4, "offTheBall" -> 1.2, "composure" -> 1.2, "pace" -> 1.0, "heading" -> 1.0),
    "CDM" -> List("tackling" -> 1.2, "passing" -> 1.2, "positioning" -> 1.1, "stamina" -> 1.1, "decisions" -> 1.0),
    "CAM" -> List("passing" -> 1.2, "vision" -> 1.2, "firstTouch" -> 1.1, "decisions" -> 1.0, "longShots" -> 1.0)
  )

  private def attr(p: Player, key: String): Double = {
    val v = p.physical.get(key).orElse(p.technical.get(key)).orElse(p.mental.get(key)).getOrElse(10)
    math.max(1, math.min(20, v)).toDouble
  }

  /** Overall (1–20) z wagami zależnymi od pozycji; przy braku dopasowania – średnia wszystkich atrybutów. */
  def overall(p: Player): Double = {
    val pos = p.preferredPositions.headOption.getOrElse("")
    val weights = positionWeights.get(pos).orElse(positionWeights.get("CM"))
    val (sumW, sumV) = weights match {
      case Some(pairs) =>
        pairs.foldLeft((0.0, 0.0)) { case ((sw, sv), (key, w)) =>
          (sw + w, sv + attr(p, key) * w)
        }
      case None =>
        val all = p.physical.values.toList ::: p.technical.values.toList ::: p.mental.values.toList
        if (all.isEmpty) return 10.0
        val a = all.sum.toDouble / all.size
        (1.0, a)
    }
    if (sumW <= 0) 10.0 else math.max(1.0, math.min(20.0, sumV / sumW))
  }

  /** Overall zaokrąglony do Int (do sortowania/porównań). */
  def overallInt(p: Player): Int = math.round(overall(p)).toInt
}
