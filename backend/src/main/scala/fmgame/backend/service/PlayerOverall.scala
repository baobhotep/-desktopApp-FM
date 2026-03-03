package fmgame.backend.service

import fmgame.backend.domain.*

/**
 * Obliczanie overall zawodnika (1–20) z atrybutów physical/technical/mental.
 * Używane przez BotSquadBuilder do wyboru składu po sile oraz przez transfery.
 */
object PlayerOverall {
  /** Średnia wszystkich atrybutów, skalowana do 1–20. */
  def overall(p: Player): Double = {
    val all = p.physical.values.toList ::: p.technical.values.toList ::: p.mental.values.toList
    if (all.isEmpty) 10.0 else all.sum.toDouble / all.size
  }

  /** Overall zaokrąglony do Int (do sortowania/porównań). */
  def overallInt(p: Player): Int = math.round(overall(p)).toInt
}
