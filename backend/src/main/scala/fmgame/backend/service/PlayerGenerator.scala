package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import java.time.Instant
import scala.util.Random

/** Generates 18 players per team (2 GK + 16 outfield) with balanced total strength (MODELE §4). */
object PlayerGenerator {

  val outfieldPhysicalKeys = List("pace", "acceleration", "agility", "stamina", "strength", "jumpingReach", "balance", "naturalFitness")
  val outfieldTechnicalKeys = List("passing", "firstTouch", "dribbling", "crossing", "shooting", "tackling", "marking", "heading", "longShots", "ballControl", "technique")
  val outfieldMentalKeys = List("composure", "decisions", "vision", "concentration", "workRate", "positioning", "anticipation", "flair", "aggression", "bravery", "leadership", "teamwork", "offTheBall")
  val gkTechnicalKeys = List("gkReflexes", "gkHandling", "gkKicking", "gkPositioning", "gkDiving", "gkThrowing", "gkCommandOfArea", "gkOneOnOnes")
  val traitKeys = List("injuryProne", "divesIntoTackles", "playsWithBackToGoal", "runsWithBallOften", "staysBack")
  val bodyParamKeys = List("height", "weight", "preferredFoot")

  val firstNames: List[String] = List(
    "Adam", "Bartosz", "Damian", "Filip", "Jakub", "Kacper", "Marcin", "Mateusz", "Michał", "Piotr",
    "Tomasz", "Kamil", "Łukasz", "Paweł", "Rafał", "Szymon", "Jan", "Mikołaj", "Wiktor", "Dominik",
    "Sebastian", "Grzegorz", "Krzysztof", "Robert", "Artur", "Daniel", "Wojciech", "Maciej", "Marek", "Dawid",
    "Adrian", "Karol", "Patryk", "Oskar", "Igor", "Norbert", "Hubert", "Emil", "Konrad", "Aleksander"
  )
  val lastNames: List[String] = List(
    "Kowalski", "Nowak", "Wiśniewski", "Wójcik", "Kowalczyk", "Kamiński", "Lewandowski", "Zieliński", "Szymański", "Woźniak",
    "Dąbrowski", "Kozłowski", "Mazur", "Król", "Kaczmarek", "Piotrowski", "Grabowski", "Pawłowski", "Michalski", "Krawczyk",
    "Walczak", "Stępień", "Górski", "Rutkowski", "Michalak", "Sikora", "Ostrowski", "Baran", "Duda", "Szewczyk",
    "Jaworski", "Malinowski", "Adamczyk", "Dudek", "Zając", "Wieczorek", "Jabłoński", "Majewski", "Olszewski", "Urbański"
  )

  /** Target sum of all attribute values per outfield squad (16 outfield × ~32 attributes × mean 10 ≈ 5120). Used to normalize outfield players only; GKs are generated independently. */
  val targetTeamSum = 5120

  private val positionTemplates: List[(Set[String], Map[String, Double], Map[String, Double], Map[String, Double])] = List(
    (Set("CB"), Map("strength" -> 1.3, "jumpingReach" -> 1.2, "pace" -> 0.9), Map("tackling" -> 1.4, "marking" -> 1.3, "heading" -> 1.3, "passing" -> 0.9, "dribbling" -> 0.7), Map("positioning" -> 1.3, "anticipation" -> 1.2, "bravery" -> 1.2, "composure" -> 1.1, "concentration" -> 1.2)),
    (Set("CB"), Map("strength" -> 1.3, "jumpingReach" -> 1.2, "pace" -> 0.9), Map("tackling" -> 1.4, "marking" -> 1.3, "heading" -> 1.3, "passing" -> 0.9, "dribbling" -> 0.7), Map("positioning" -> 1.3, "anticipation" -> 1.2, "bravery" -> 1.2, "composure" -> 1.1, "concentration" -> 1.2)),
    (Set("LB", "RB"), Map("pace" -> 1.3, "acceleration" -> 1.2, "stamina" -> 1.2, "agility" -> 1.1), Map("crossing" -> 1.3, "tackling" -> 1.2, "marking" -> 1.1, "dribbling" -> 1.0), Map("positioning" -> 1.1, "workRate" -> 1.2, "teamwork" -> 1.1)),
    (Set("LB", "RB"), Map("pace" -> 1.3, "acceleration" -> 1.2, "stamina" -> 1.2, "agility" -> 1.1), Map("crossing" -> 1.3, "tackling" -> 1.2, "marking" -> 1.1, "dribbling" -> 1.0), Map("positioning" -> 1.1, "workRate" -> 1.2, "teamwork" -> 1.1)),
    (Set("CM", "CDM"), Map("stamina" -> 1.3, "strength" -> 1.1, "balance" -> 1.1), Map("passing" -> 1.3, "tackling" -> 1.2, "firstTouch" -> 1.1, "ballControl" -> 1.1, "technique" -> 1.1), Map("decisions" -> 1.3, "vision" -> 1.2, "positioning" -> 1.2, "composure" -> 1.2, "teamwork" -> 1.2, "workRate" -> 1.2)),
    (Set("CM", "CDM"), Map("stamina" -> 1.3, "strength" -> 1.1, "balance" -> 1.1), Map("passing" -> 1.3, "tackling" -> 1.2, "firstTouch" -> 1.1, "ballControl" -> 1.1, "technique" -> 1.1), Map("decisions" -> 1.3, "vision" -> 1.2, "positioning" -> 1.2, "composure" -> 1.2, "teamwork" -> 1.2, "workRate" -> 1.2)),
    (Set("CM", "CAM"), Map("stamina" -> 1.2, "agility" -> 1.1, "balance" -> 1.1), Map("passing" -> 1.4, "firstTouch" -> 1.2, "technique" -> 1.3, "dribbling" -> 1.1, "longShots" -> 1.1, "ballControl" -> 1.2), Map("vision" -> 1.4, "decisions" -> 1.3, "composure" -> 1.2, "flair" -> 1.2, "offTheBall" -> 1.1)),
    (Set("LM", "RM", "LW", "RW"), Map("pace" -> 1.4, "acceleration" -> 1.3, "agility" -> 1.3, "stamina" -> 1.1), Map("dribbling" -> 1.3, "crossing" -> 1.3, "technique" -> 1.2, "firstTouch" -> 1.1, "passing" -> 1.0), Map("flair" -> 1.3, "offTheBall" -> 1.2, "vision" -> 1.1, "decisions" -> 1.0, "workRate" -> 1.1)),
    (Set("LM", "RM", "LW", "RW"), Map("pace" -> 1.4, "acceleration" -> 1.3, "agility" -> 1.3, "stamina" -> 1.1), Map("dribbling" -> 1.3, "crossing" -> 1.3, "technique" -> 1.2, "firstTouch" -> 1.1, "passing" -> 1.0), Map("flair" -> 1.3, "offTheBall" -> 1.2, "vision" -> 1.1, "decisions" -> 1.0, "workRate" -> 1.1)),
    (Set("ST", "CF"), Map("pace" -> 1.2, "acceleration" -> 1.2, "strength" -> 1.1, "jumpingReach" -> 1.1, "agility" -> 1.1), Map("shooting" -> 1.4, "heading" -> 1.2, "firstTouch" -> 1.2, "technique" -> 1.1, "dribbling" -> 1.0), Map("composure" -> 1.4, "offTheBall" -> 1.4, "anticipation" -> 1.2, "decisions" -> 1.1, "flair" -> 1.1)),
    (Set("ST", "CF"), Map("pace" -> 1.2, "acceleration" -> 1.2, "strength" -> 1.1, "jumpingReach" -> 1.1, "agility" -> 1.1), Map("shooting" -> 1.4, "heading" -> 1.2, "firstTouch" -> 1.2, "technique" -> 1.1, "dribbling" -> 1.0), Map("composure" -> 1.4, "offTheBall" -> 1.4, "anticipation" -> 1.2, "decisions" -> 1.1, "flair" -> 1.1)),
    (Set("CB", "CM"), Map("strength" -> 1.2, "stamina" -> 1.1), Map("tackling" -> 1.2, "passing" -> 1.1, "marking" -> 1.1), Map("positioning" -> 1.2, "decisions" -> 1.1, "concentration" -> 1.1)),
    (Set("CM", "LM", "RM"), Map("pace" -> 1.1, "stamina" -> 1.2, "agility" -> 1.1), Map("passing" -> 1.2, "dribbling" -> 1.1, "crossing" -> 1.1, "technique" -> 1.1), Map("vision" -> 1.2, "workRate" -> 1.2, "decisions" -> 1.1)),
    (Set("LW", "RW", "ST"), Map("pace" -> 1.3, "acceleration" -> 1.2, "agility" -> 1.2), Map("shooting" -> 1.2, "dribbling" -> 1.2, "firstTouch" -> 1.1, "technique" -> 1.1), Map("offTheBall" -> 1.3, "composure" -> 1.2, "flair" -> 1.2)),
    (Set("CB", "LB", "RB"), Map("pace" -> 1.0, "strength" -> 1.2, "jumpingReach" -> 1.1), Map("tackling" -> 1.3, "marking" -> 1.2, "heading" -> 1.1), Map("positioning" -> 1.2, "bravery" -> 1.1, "concentration" -> 1.2)),
    (Set("CM", "CAM"), Map("stamina" -> 1.1, "agility" -> 1.1), Map("passing" -> 1.2, "technique" -> 1.2, "longShots" -> 1.1, "firstTouch" -> 1.1), Map("vision" -> 1.3, "decisions" -> 1.2, "composure" -> 1.1)),
    (Set("ST", "LW", "RW"), Map("pace" -> 1.3, "acceleration" -> 1.3, "agility" -> 1.1), Map("shooting" -> 1.3, "dribbling" -> 1.2, "firstTouch" -> 1.1), Map("offTheBall" -> 1.3, "composure" -> 1.3, "flair" -> 1.1)),
  )

  def generateSquad(teamId: TeamId, rng: Random): List[Player] = {
    val (gk, outfield) = generateBalancedSquad(rng)
    val now = Instant.now()
    val gkPlayer = {
      val firstName = firstNames(rng.nextInt(firstNames.size))
      val lastName = lastNames(rng.nextInt(lastNames.size))
      Player(IdGen.playerId, teamId, firstName, lastName, Set("GK"), gk.physical, gk.technical, gk.mental, gk.traits, gk.bodyParams, None, 1.0, 0.8, 1.0, 1.0, now)
    }
    val backupGk = {
      val backupAttrs = generateGkAttributes(rng, multiplier = 0.85)
      val firstName = firstNames(rng.nextInt(firstNames.size))
      val lastName = lastNames(rng.nextInt(lastNames.size))
      Player(IdGen.playerId, teamId, firstName, lastName, Set("GK"), backupAttrs.physical, backupAttrs.technical, backupAttrs.mental, backupAttrs.traits, backupAttrs.bodyParams, None, 1.0, 0.8, 1.0, 1.0, now)
    }
    val outfieldPlayers = outfield.take(16).zipWithIndex.map { case (attrs, i) =>
      val template = positionTemplates(i % positionTemplates.size)
      val firstName = firstNames(rng.nextInt(firstNames.size))
      val lastName = lastNames(rng.nextInt(lastNames.size))
      Player(IdGen.playerId, teamId, firstName, lastName, template._1, attrs.physical, attrs.technical, attrs.mental, attrs.traits, attrs.bodyParams, None, 1.0, 0.8, 1.0, 1.0, now)
    }
    gkPlayer :: backupGk :: outfieldPlayers
  }

  /** Generates initial contracts for each player. */
  def generateInitialContracts(players: List[Player], teamId: TeamId, totalMatchdays: Int): List[Contract] = {
    val rng = new Random()
    val now = Instant.now()
    players.map { player =>
      val weeklySalary = BigDecimal(500 + rng.nextDouble() * 4500)
      val endMatchday = totalMatchdays + rng.nextInt(totalMatchdays * 2 + 1)
      Contract(
        id = IdGen.contractId,
        playerId = player.id,
        teamId = teamId,
        weeklySalary = weeklySalary,
        startMatchday = 1,
        endMatchday = endMatchday,
        signingBonus = BigDecimal(0),
        releaseClause = None,
        createdAt = now
      )
    }
  }

  case class PlayerAttrs(physical: Map[String, Int], technical: Map[String, Int], mental: Map[String, Int], traits: Map[String, Int], bodyParams: Map[String, Double])

  private def clamp(v: Int): Int = math.max(1, math.min(20, v))
  private def clampD(v: Double): Double = math.max(0.0, math.min(1.0, v))

  private def generateBalancedSquad(rng: Random): (PlayerAttrs, List[PlayerAttrs]) = {
    val meanPerAttr = 10.0
    val stddev = 3.0
    def nextVal(): Int = clamp((rng.nextGaussian() * stddev + meanPerAttr).round.toInt)

    def applyBias(keys: List[String], baseVals: List[Int], biases: Map[String, Double]): List[Int] =
      keys.zip(baseVals).map { case (k, v) =>
        clamp(math.round(v * biases.getOrElse(k, 1.0)).toInt)
      }

    def correlate(base: Int, noise: Double): Int = clamp(math.round(base + rng.nextGaussian() * noise).toInt)

    val players = (0 until 16).map { i =>
      val template = positionTemplates(i % positionTemplates.size)
      val (_, phBias, teBias, meBias) = template
      val phRaw = outfieldPhysicalKeys.map(_ => nextVal())
      val phBiased = applyBias(outfieldPhysicalKeys, phRaw, phBias)
      val phMap = outfieldPhysicalKeys.zip(phBiased).toMap
      val ph = phMap ++ Map(
        "acceleration" -> correlate(phMap.getOrElse("pace", 10), 1.5),
        "agility" -> correlate(phMap.getOrElse("balance", 10), 2.0),
        "naturalFitness" -> correlate(phMap.getOrElse("stamina", 10), 2.0)
      )
      val teRaw = outfieldTechnicalKeys.map(_ => nextVal())
      val teBiased = applyBias(outfieldTechnicalKeys, teRaw, teBias)
      val teMap = outfieldTechnicalKeys.zip(teBiased).toMap
      val te = teMap ++ Map(
        "finishing" -> teMap.getOrElse("shooting", 10),
        "ballControl" -> correlate(teMap.getOrElse("firstTouch", 10), 1.5),
        "technique" -> correlate(teMap.getOrElse("dribbling", 10), 2.0)
      )
      val meRaw = outfieldMentalKeys.map(_ => nextVal())
      val meBiased = applyBias(outfieldMentalKeys, meRaw, meBias)
      val meMap = outfieldMentalKeys.zip(meBiased).toMap
      val me = meMap ++ Map(
        "anticipation" -> correlate(meMap.getOrElse("positioning", 10), 1.5),
        "decisions" -> correlate(meMap.getOrElse("composure", 10), 2.0)
      )
      val tr = traitKeys.map(k => k -> nextVal()).toMap
      val body = bodyParamKeys.zip(List(185.0 + rng.nextGaussian() * 5, 78.0 + rng.nextGaussian() * 6, if (rng.nextBoolean()) 0.0 else 1.0)).toMap
      PlayerAttrs(ph, te, me, tr, body)
    }.toList

    val allVals = players.flatMap(p => p.physical.values ++ p.technical.values ++ p.mental.values ++ p.traits.values)
    val sum = allVals.sum.toDouble
    val scale = if (sum > 0) targetTeamSum / sum else 1.0
    val scaledPlayers = players.map { p =>
      val ph = p.physical.view.mapValues(v => clamp(math.round(v * scale).toInt)).toMap
      val te = p.technical.view.mapValues(v => clamp(math.round(v * scale).toInt)).toMap
      val me = p.mental.view.mapValues(v => clamp(math.round(v * scale).toInt)).toMap
      val tr = p.traits.view.mapValues(v => clamp(math.round(v * scale).toInt)).toMap
      PlayerAttrs(ph, te, me, tr, p.bodyParams)
    }

    val gkPhRaw = outfieldPhysicalKeys.map(_ => nextVal())
    val gkPh = outfieldPhysicalKeys.zip(gkPhRaw).toMap
    val gkTechRaw = gkTechnicalKeys.map(k => k -> clamp((rng.nextGaussian() * 2 + 10).round.toInt)).toMap
    val gkTech = gkTechRaw + ("reflexes" -> gkTechRaw.getOrElse("gkReflexes", 10)) + ("handling" -> gkTechRaw.getOrElse("gkHandling", 10))
    val gkMe = outfieldMentalKeys.map(k => k -> nextVal()).toMap
    val gkTr = traitKeys.map(k => k -> nextVal()).toMap
    val gkBody = bodyParamKeys.zip(List(190.0 + rng.nextGaussian() * 4, 85.0 + rng.nextGaussian() * 5, if (rng.nextBoolean()) 0.0 else 1.0)).toMap
    val gkAttrs = PlayerAttrs(gkPh, gkTech, gkMe, gkTr, gkBody)
    (gkAttrs, scaledPlayers)
  }

  private def generateGkAttributes(rng: Random, multiplier: Double = 1.0): PlayerAttrs = {
    def nextVal(): Int = clamp((rng.nextGaussian() * 3.0 + 10.0 * multiplier).round.toInt)
    val ph = outfieldPhysicalKeys.map(k => k -> nextVal()).toMap
    val techBase = gkTechnicalKeys.map(k => k -> nextVal()).toMap
    val tech = techBase +
      ("reflexes" -> techBase.getOrElse("gkReflexes", clamp((10 * multiplier).round.toInt))) +
      ("handling" -> techBase.getOrElse("gkHandling", clamp((10 * multiplier).round.toInt)))
    val me = outfieldMentalKeys.map(k => k -> nextVal()).toMap
    val tr = traitKeys.map(k => k -> nextVal()).toMap
    val body = bodyParamKeys.zip(List(190.0 + rng.nextGaussian() * 4, 85.0 + rng.nextGaussian() * 5, if (rng.nextBoolean()) 0.0 else 1.0)).toMap
    PlayerAttrs(ph, tech, me, tr, body)
  }
}
