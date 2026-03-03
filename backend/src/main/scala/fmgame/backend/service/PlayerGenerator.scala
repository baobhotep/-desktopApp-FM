package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import java.time.Instant
import scala.util.Random

/** Generates 18 players per team (1 GK + 17 outfield) with balanced total strength (MODELE §4). */
object PlayerGenerator {

  val outfieldPhysicalKeys = List("pace", "acceleration", "agility", "stamina", "strength", "jumpingReach", "balance", "naturalFitness")
  val outfieldTechnicalKeys = List("passing", "firstTouch", "dribbling", "crossing", "shooting", "tackling", "marking", "heading", "longShots", "ballControl", "technique")
  val outfieldMentalKeys = List("composure", "decisions", "vision", "concentration", "workRate", "positioning", "anticipation", "flair", "aggression", "bravery", "leadership", "teamwork", "offTheBall")
  val gkTechnicalKeys = List("gkReflexes", "gkHandling", "gkKicking", "gkPositioning", "gkDiving", "gkThrowing", "gkCommandOfArea", "gkOneOnOnes")
  val traitKeys = List("injuryProne", "divesIntoTackles", "playsWithBackToGoal", "runsWithBallOften", "staysBack")
  val bodyParamKeys = List("height", "weight", "preferredFoot")

  /** Pula imion (MODELE §4.4, WYMAGANIA v1 — rozszerzenie). */
  val firstNames: List[String] = List("Adam", "Bartosz", "Damian", "Filip", "Jakub", "Kacper", "Marcin", "Mateusz", "Michał", "Piotr", "Tomasz", "Kamil", "Łukasz", "Paweł", "Rafał", "Szymon", "Jan", "Mikołaj", "Wiktor", "Dominik")
  /** Pula nazwisk (MODELE §4.4). */
  val lastNames: List[String] = List("Kowalski", "Nowak", "Wiśniewski", "Wójcik", "Kowalczyk", "Kamiński", "Lewandowski", "Zieliński", "Szymański", "Woźniak", "Dąbrowski", "Kozłowski", "Mazur", "Król", "Kaczmarek", "Piotrowski", "Grabowski", "Pawłowski", "Michalski", "Krawczyk")

  /** Target sum of all attribute values per team (18 players × ~30 attributes × mean 10 = 5400). Balance so all teams have same total. */
  val targetTeamSum = 5400

  def generateSquad(teamId: TeamId, rng: Random): List[Player] = {
    val (gk, outfield) = generateBalancedSquad(rng)
    val now = Instant.now()
    (gk :: outfield).zipWithIndex.map { case (attrs, i) =>
      val isGK = i == 0
      val firstName = firstNames(rng.nextInt(firstNames.size))
      val lastName = lastNames(rng.nextInt(lastNames.size))
      Player(
        id = IdGen.playerId,
        teamId = teamId,
        firstName = firstName,
        lastName = lastName,
        preferredPositions = if (isGK) Set("GK") else Set("CB", "CM", "ST"),
        physical = attrs.physical,
        technical = attrs.technical,
        mental = attrs.mental,
        traits = attrs.traits,
        bodyParams = attrs.bodyParams,
        injury = None,
        freshness = 1.0,
        morale = 0.8,
        createdAt = now
      )
    }
  }

  case class PlayerAttrs(physical: Map[String, Int], technical: Map[String, Int], mental: Map[String, Int], traits: Map[String, Int], bodyParams: Map[String, Double])

  private def clamp(v: Int): Int = math.max(1, math.min(20, v))
  private def clampD(v: Double): Double = math.max(0.0, math.min(1.0, v))

  /** Generate 18 players: 1 GK + 17 outfield. Same total attribute sum per team for balance. */
  private def generateBalancedSquad(rng: Random): (PlayerAttrs, List[PlayerAttrs]) = {
    val meanPerAttr = 10.0
    val stddev = 3.0
    def nextVal(): Int = clamp((rng.nextGaussian() * stddev + meanPerAttr).round.toInt)
    val attrsPerOutfield = outfieldPhysicalKeys.size + outfieldTechnicalKeys.size + outfieldMentalKeys.size + traitKeys.size
    val totalAttrs = 18 * attrsPerOutfield
    var values = List.fill(totalAttrs)(nextVal())
    val sum = values.sum.toDouble
    if (sum > 0) {
      val scale = targetTeamSum / sum
      values = values.map(v => clamp(math.round(v * scale).toInt))
    }
    var idx = 0
    def take(n: Int): List[Int] = {
      val chunk = values.slice(idx, idx + n)
      idx += n
      chunk
    }
    def toMap(keys: List[String], vals: List[Int]): Map[String, Int] = keys.zip(vals).toMap
    val players = (0 until 18).map { _ =>
      val ph = toMap(outfieldPhysicalKeys, take(outfieldPhysicalKeys.size))
      val teRaw = toMap(outfieldTechnicalKeys, take(outfieldTechnicalKeys.size))
      val te = teRaw + ("finishing" -> teRaw.getOrElse("shooting", 10))
      val me = toMap(outfieldMentalKeys, take(outfieldMentalKeys.size))
      val tr = toMap(traitKeys, take(traitKeys.size))
      val body = bodyParamKeys.zip(List(185.0 + rng.nextGaussian() * 5, 78.0 + rng.nextGaussian() * 6, if (rng.nextBoolean()) 0.0 else 1.0)).toMap
      PlayerAttrs(ph, te, me, tr, body)
    }.toList
    val gkTechRaw = gkTechnicalKeys.map(k => k -> clamp((rng.nextGaussian() * 2 + 10).round.toInt)).toMap
    val gkTech = gkTechRaw + ("reflexes" -> gkTechRaw.getOrElse("gkReflexes", 10)) + ("handling" -> gkTechRaw.getOrElse("gkHandling", 10))
    val gkAttrs = PlayerAttrs(players(0).physical, gkTech, players(0).mental, players(0).traits, players(0).bodyParams)
    (gkAttrs, players.tail)
  }
}
