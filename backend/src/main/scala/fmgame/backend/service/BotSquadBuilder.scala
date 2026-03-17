package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.backend.engine.*
import fmgame.shared.domain.*

/**
 * Logika botów (BOTY_LOGIKA_GRA_I_TRANSFERY): skład, formacja, taktyka, transfery.
 * Różnicowanie (BOTY §3.6): trudność (Easy/Normal/Hard) i styl (Defensive/Attacking) z hasha teamId.
 */
object BotSquadBuilder {

  /** Trudność bota: Easy = prostszy skład (overall), Hard = pełne dopasowanie do slotów + premia przy sprzedaży. */
  enum BotDifficulty:
    case Easy, Normal, Hard

  /** Styl bota: Defensive → 4-4-2, lower tempo; Attacking → 4-3-3, higher tempo. */
  enum BotStyle:
    case Defensive, Attacking

  private def safeHash(teamId: TeamId): Int = teamId.value.hashCode & Int.MaxValue

  def botDifficulty(teamId: TeamId): BotDifficulty = {
    val i = safeHash(teamId) % 3
    BotDifficulty.values(i)
  }

  def botStyle(teamId: TeamId): BotStyle =
    if ((safeHash(teamId) >> 4) % 2 == 0) BotStyle.Defensive else BotStyle.Attacking

  /** Premium za kluczowego przy sprzedaży: Hard wymaga więcej. */
  def keyPlayerPremium(difficulty: BotDifficulty): Double = difficulty match {
    case BotDifficulty.Easy   => 1.1
    case BotDifficulty.Normal => 1.2
    case BotDifficulty.Hard   => 1.3
  }

  /** Zakres mnożnika ceny przy kupnie (min, max): Hard płaci więcej. */
  def buyPriceMultiplierRange(difficulty: BotDifficulty): (Double, Double) = difficulty match {
    case BotDifficulty.Easy   => (0.85, 1.0)
    case BotDifficulty.Normal => (0.9, 1.15)
    case BotDifficulty.Hard   => (1.0, 1.2)
  }

  val formations: List[(String, List[String])] = List(
    ("4-3-3", List("GK", "LCB", "RCB", "LB", "RB", "CDM", "LCM", "RCM", "LW", "RW", "ST")),
    ("4-4-2", List("GK", "LCB", "RCB", "LB", "RB", "LM", "LCM", "RCM", "RM", "LS", "RS")),
    ("3-5-2", List("GK", "LCB", "CB", "RCB", "LWB", "LCM", "CDM", "RCM", "RWB", "LS", "RS")),
    ("4-2-3-1", List("GK", "LCB", "RCB", "LB", "RB", "CDM", "CDM", "LW", "CAM", "RW", "ST")),
    ("3-4-3", List("GK", "LCB", "CB", "RCB", "LM", "LCM", "RCM", "RM", "LW", "ST", "RW")),
    ("5-3-2", List("GK", "LWB", "LCB", "CB", "RCB", "RWB", "LCM", "CDM", "RCM", "LS", "RS")),
    ("4-1-4-1", List("GK", "LCB", "RCB", "LB", "RB", "CDM", "LM", "LCM", "RCM", "RM", "ST")),
    ("4-5-1", List("GK", "LCB", "RCB", "LB", "RB", "LM", "LCM", "CDM", "RCM", "RM", "ST")),
    ("4-4-1-1", List("GK", "LCB", "RCB", "LB", "RB", "LM", "LCM", "RCM", "RM", "CAM", "ST")),
    ("3-4-1-2", List("GK", "LCB", "CB", "RCB", "LWB", "LCM", "RCM", "RWB", "CAM", "LS", "RS"))
  )

  /** Wagi atrybutów per slot (BOTY §3.1). Normalized to sum=1 at lookup time. */
  private val slotAttributeWeightsRaw: Map[String, List[(String, Double)]] = Map(
    "GK"   -> List("reflexes" -> 0.28, "handling" -> 0.26, "positioning" -> 0.12, "agility" -> 0.1, "strength" -> 0.08, "anticipation" -> 0.08, "concentration" -> 0.08),
    "LCB"  -> List("tackling" -> 0.2, "marking" -> 0.2, "heading" -> 0.15, "strength" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.1, "bravery" -> 0.075, "jumpingReach" -> 0.075),
    "RCB"  -> List("tackling" -> 0.2, "marking" -> 0.2, "heading" -> 0.15, "strength" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.1, "bravery" -> 0.075, "jumpingReach" -> 0.075),
    "CB"   -> List("tackling" -> 0.2, "marking" -> 0.2, "heading" -> 0.15, "strength" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.1, "bravery" -> 0.075, "jumpingReach" -> 0.075),
    "LB"   -> List("tackling" -> 0.14, "marking" -> 0.12, "pace" -> 0.14, "stamina" -> 0.1, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.08, "acceleration" -> 0.1, "agility" -> 0.05, "teamwork" -> 0.05),
    "RB"   -> List("tackling" -> 0.14, "marking" -> 0.12, "pace" -> 0.14, "stamina" -> 0.1, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.08, "acceleration" -> 0.1, "agility" -> 0.05, "teamwork" -> 0.05),
    "LWB"  -> List("tackling" -> 0.12, "pace" -> 0.16, "stamina" -> 0.12, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.1, "acceleration" -> 0.12, "marking" -> 0.06, "dribbling" -> 0.05, "agility" -> 0.05),
    "RWB"  -> List("tackling" -> 0.12, "pace" -> 0.16, "stamina" -> 0.12, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.1, "acceleration" -> 0.12, "marking" -> 0.06, "dribbling" -> 0.05, "agility" -> 0.05),
    "CDM"  -> List("tackling" -> 0.16, "positioning" -> 0.14, "passing" -> 0.12, "stamina" -> 0.1, "marking" -> 0.1, "anticipation" -> 0.1, "strength" -> 0.08, "decisions" -> 0.1, "workRate" -> 0.05, "concentration" -> 0.05),
    "LCM"  -> List("passing" -> 0.16, "vision" -> 0.12, "decisions" -> 0.12, "stamina" -> 0.1, "firstTouch" -> 0.1, "technique" -> 0.1, "positioning" -> 0.1, "workRate" -> 0.1, "teamwork" -> 0.05, "composure" -> 0.05),
    "RCM"  -> List("passing" -> 0.16, "vision" -> 0.12, "decisions" -> 0.12, "stamina" -> 0.1, "firstTouch" -> 0.1, "technique" -> 0.1, "positioning" -> 0.1, "workRate" -> 0.1, "teamwork" -> 0.05, "composure" -> 0.05),
    "LM"   -> List("pace" -> 0.15, "dribbling" -> 0.14, "crossing" -> 0.14, "acceleration" -> 0.12, "passing" -> 0.12, "stamina" -> 0.1, "offTheBall" -> 0.1, "workRate" -> 0.07, "technique" -> 0.06),
    "RM"   -> List("pace" -> 0.15, "dribbling" -> 0.14, "crossing" -> 0.14, "acceleration" -> 0.12, "passing" -> 0.12, "stamina" -> 0.1, "offTheBall" -> 0.1, "workRate" -> 0.07, "technique" -> 0.06),
    "LW"   -> List("pace" -> 0.16, "dribbling" -> 0.16, "crossing" -> 0.12, "acceleration" -> 0.12, "finishing" -> 0.12, "offTheBall" -> 0.12, "technique" -> 0.1, "flair" -> 0.05, "agility" -> 0.05),
    "RW"   -> List("pace" -> 0.16, "dribbling" -> 0.16, "crossing" -> 0.12, "acceleration" -> 0.12, "finishing" -> 0.12, "offTheBall" -> 0.12, "technique" -> 0.1, "flair" -> 0.05, "agility" -> 0.05),
    "ST"   -> List("finishing" -> 0.22, "composure" -> 0.14, "offTheBall" -> 0.12, "shooting" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.08, "strength" -> 0.08, "heading" -> 0.08, "pace" -> 0.04, "firstTouch" -> 0.04),
    "LS"   -> List("finishing" -> 0.2, "composure" -> 0.14, "offTheBall" -> 0.12, "shooting" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.08, "strength" -> 0.08, "heading" -> 0.08, "pace" -> 0.05, "firstTouch" -> 0.05),
    "RS"   -> List("finishing" -> 0.2, "composure" -> 0.14, "offTheBall" -> 0.12, "shooting" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.08, "strength" -> 0.08, "heading" -> 0.08, "pace" -> 0.05, "firstTouch" -> 0.05),
    "CAM"  -> List("passing" -> 0.14, "vision" -> 0.14, "decisions" -> 0.12, "technique" -> 0.12, "firstTouch" -> 0.1, "dribbling" -> 0.1, "composure" -> 0.1, "flair" -> 0.08, "offTheBall" -> 0.1),
    "CF"   -> List("finishing" -> 0.18, "composure" -> 0.14, "offTheBall" -> 0.12, "firstTouch" -> 0.1, "technique" -> 0.1, "vision" -> 0.1, "dribbling" -> 0.08, "passing" -> 0.1, "shooting" -> 0.08)
  )

  private val slotAttributeWeights: Map[String, List[(String, Double)]] = slotAttributeWeightsRaw.map { case (slot, weights) =>
    val sum = weights.map(_._2).sum
    slot -> (if (math.abs(sum - 1.0) < 1e-9) weights else weights.map { case (k, v) => k -> (v / sum) })
  }

  private def getAttr(p: Player, key: String): Int = {
    val v = p.physical.getOrElse(key, p.technical.getOrElse(key, p.mental.getOrElse(key, 10)))
    math.max(1, math.min(20, v))
  }

  /** GK atrybuty: silnik używa reflexes/gkReflexes, handling/gkHandling, positioning/gkPositioning. */
  private def getGkAttr(p: Player, key: String): Int = key match {
    case "reflexes"     => getAttr(p, "reflexes") max getAttr(p, "gkReflexes")
    case "handling"     => getAttr(p, "handling") max getAttr(p, "gkHandling")
    case "positioning"  => getAttr(p, "positioning") max getAttr(p, "gkPositioning")
    case _              => getAttr(p, key)
  }

  /** Ocena zawodnika na dany slot (0–20). Bonus jeśli preferredPositions pasuje do slotu. */
  def scoreForSlot(p: Player, slot: String): Double = {
    val weights = slotAttributeWeights.getOrElse(slot, List("pace" -> 0.2, "passing" -> 0.2, "stamina" -> 0.2))
    val attrSum = weights.map { case (attr, w) =>
      val v = if (slot == "GK") getGkAttr(p, attr) else getAttr(p, attr)
      v * w
    }.sum
    val positionBonus = if (slotMatchesPosition(slot, p.preferredPositions)) 1.5 else 0.0
    attrSum + positionBonus
  }

  private def slotMatchesPosition(slot: String, preferred: Set[String]): Boolean = {
    val norm = slot.replace("L", "").replace("R", "").replace("S", "T")
    preferred.contains(slot) || preferred.contains(norm) ||
      (slot == "ST" && (preferred.contains("CF") || preferred.contains("ST"))) ||
      ((slot == "LS" || slot == "RS") && (preferred.contains("ST") || preferred.contains("CF"))) ||
      (slot == "CDM" && (preferred.contains("CDM") || preferred.contains("CM") || preferred.contains("DM"))) ||
      (slot == "CAM" && (preferred.contains("CAM") || preferred.contains("CM") || preferred.contains("CF"))) ||
      (slot == "CF" && (preferred.contains("CF") || preferred.contains("ST") || preferred.contains("CAM"))) ||
      (slot == "GK" && preferred.contains("GK")) ||
      (Set("LCB", "RCB", "CB").contains(slot) && preferred.exists(s => s == "CB" || s == "LCB" || s == "RCB")) ||
      (Set("LB", "RB", "LWB", "RWB").contains(slot) && preferred.exists(s => s == "LB" || s == "RB" || s == "LWB" || s == "RWB" || s == "WB")) ||
      (Set("LCM", "RCM").contains(slot) && preferred.exists(s => s == "CM" || s == "LCM" || s == "RCM" || s == "CDM" || s == "CAM")) ||
      (Set("LM", "RM").contains(slot) && preferred.exists(s => s == "LM" || s == "RM" || s == "LW" || s == "RW")) ||
      (Set("LW", "RW").contains(slot) && preferred.exists(s => s == "LW" || s == "RW" || s == "LM" || s == "RM"))
  }

  /** Najlepszy gracz na slot z listy dostępnych. Dla GK tylko bramkarze. */
  def bestPlayerForSlot(available: List[Player], slot: String): Option[Player] = {
    val candidates = if (slot == "GK") available.filter(_.preferredPositions.contains("GK"))
                     else available.filter(!_.preferredPositions.contains("GK"))
    if (candidates.isEmpty) None
    else Some(candidates.maxBy(p => scoreForSlot(p, slot)))
  }

  /**
   * Buduje skład. Easy = sort po overall i przypisanie do slotów po kolei; Normal/Hard = najlepszy gracz per slot.
   */
  def buildBotLineup(players: List[Player], currentMatchday: Int, formationName: String, difficulty: BotDifficulty = BotDifficulty.Normal): Option[List[LineupSlot]] = {
    val available = players.filter { p =>
      p.injury match {
        case None => true
        case Some(inj) => inj.returnAtMatchday <= currentMatchday
      }
    }
    val gks = available.filter(_.preferredPositions.contains("GK"))
    val outfield = available.filter(!_.preferredPositions.contains("GK"))
    if (gks.isEmpty || outfield.size < 10) return None
    val slots = formations.find(_._1 == formationName).map(_._2).getOrElse(formations.head._2)
    val picked = difficulty match {
      case BotDifficulty.Easy =>
        val eleven = (gks.head :: outfield.sortBy(p => -PlayerOverall.overall(p)).take(10)).zip(slots)
        eleven.map { case (p, s) => LineupSlot(p.id, s) }
      case _ =>
        var remaining = available
        slots.flatMap { slot =>
          bestPlayerForSlot(remaining, slot).map { p =>
            remaining = remaining.filter(_.id != p.id)
            LineupSlot(p.id, slot)
          }
        }
    }
    if (picked.size != 11) None else Some(picked)
  }

  /**
   * Game plan JSON. opponentStrength lub style (Defensive → lower tempo, Attacking → higher) ustawia teamInstructions.
   */
  def defaultGamePlanJson(formationName: String, withTriggers: Boolean, opponentStrength: Option[Double] = None, style: BotStyle = BotStyle.Defensive): String = {
    val tiJson = opponentStrength match {
      case Some(s) if s > 0.6 => ""","teamInstructions":{"tempo":"lower","pressingIntensity":"normal","width":"normal"}"""
      case Some(s) if s < 0.4 => ""","teamInstructions":{"tempo":"higher","pressingIntensity":"higher","width":"wide"}"""
      case _ =>
        style match {
          case BotStyle.Defensive => ""","teamInstructions":{"tempo":"lower","pressingIntensity":"normal","width":"normal"}"""
          case BotStyle.Attacking => ""","teamInstructions":{"tempo":"higher","pressingIntensity":"higher","width":"wide"}"""
        }
    }
    val triggerJson = if (!withTriggers) "" else ""","triggerConfig":{"pressZones":[13,14,15,16],"counterTriggerZone":8}"""
    s"""{"formationName":"$formationName"$triggerJson$tiJson}"""
  }

  private val defensiveFormations = List("4-4-2", "5-3-2", "4-5-1", "4-1-4-1")
  private val attackingFormations = List("4-3-3", "4-2-3-1", "3-4-3", "4-4-1-1")

  def pickFormation(teamId: TeamId, opponentStrength: Option[Double], style: BotStyle = BotStyle.Defensive): String = {
    val hash = safeHash(teamId)
    val pool = style match {
      case BotStyle.Defensive => defensiveFormations
      case BotStyle.Attacking => attackingFormations
    }
    val idx = hash % pool.size
    opponentStrength match {
      case Some(s) if s > 0.6 => defensiveFormations(hash % defensiveFormations.size)
      case Some(s) if s < 0.4 => attackingFormations(hash % attackingFormations.size)
      case _ => pool(idx)
    }
  }

  def useTriggers(teamId: TeamId): Boolean = (safeHash(teamId) % 2) == 0

  // --- Transfery: bot kupuje pod braki (BOTY §3.4), bot sprzedaje mądrzej (§3.5) ---

  /** Grupy pozycji do analizy kadry: GK, CB, FB, CM, W, ST. */
  val PositionGroups: List[String] = List("GK", "CB", "FB", "CM", "W", "ST")

  /** Mapuje preferredPositions zawodnika na grupy pozycji (np. LCB → CB, LW → W). */
  def positionGroups(player: Player): List[String] = {
    val pos = player.preferredPositions
    val out = scala.collection.mutable.Set.empty[String]
    if (pos.contains("GK")) out += "GK"
    if (pos.exists(p => p == "CB" || p == "LCB" || p == "RCB")) out += "CB"
    if (pos.exists(p => p == "LB" || p == "RB" || p == "LWB" || p == "RWB" || p == "WB")) out += "FB"
    if (pos.exists(p => p == "CDM" || p == "LCM" || p == "RCM" || p == "LM" || p == "RM" || p == "CM")) out += "CM"
    if (pos.exists(p => p == "LW" || p == "RW")) out += "W"
    if (pos.exists(p => p == "ST" || p == "LS" || p == "RS" || p == "CF")) out += "ST"
    PositionGroups.filter(out.contains)
  }

  /** Grupy pozycji, w których kadra ma „brak”: dostępnych < 2 (dla GK < 1). BOTY §3.4. */
  def squadNeedGroups(players: List[Player], currentMatchday: Int): List[String] = {
    val available = players.filter { p =>
      p.injury match { case None => true; case Some(inj) => inj.returnAtMatchday <= currentMatchday }
    }
    def countInGroup(group: String): Int = available.count(p => positionGroups(p).contains(group))
    PositionGroups.flatMap { group =>
      val minRequired = if (group == "GK") 1 else 2
      if (countInGroup(group) < minRequired) Some(group) else None
    }
  }

  /** Czy zawodnik jest w „najlepszej 11” bota (kluczowy przy sprzedaży). BOTY §3.5. */
  def isPlayerInBestLineup(playerId: PlayerId, players: List[Player], currentMatchday: Int, teamId: TeamId): Boolean = {
    val style = botStyle(teamId)
    val formation = pickFormation(teamId, None, style)
    buildBotLineup(players, currentMatchday, formation, botDifficulty(teamId)).exists(_.exists(_.playerId == playerId))
  }

  /** Czy bot powinien zaakceptować kontrofertę: counter >= minAcceptableRatio * originalAmount. Easy: 80%, Normal: 85%, Hard: 90%. */
  def shouldAcceptCounter(offer: TransferOffer, counter: BigDecimal, player: Player, difficulty: BotDifficulty): Boolean = {
    val minRatio = difficulty match {
      case BotDifficulty.Easy   => 0.80
      case BotDifficulty.Normal => 0.85
      case BotDifficulty.Hard   => 0.90
    }
    val originalAmount = offer.amount
    originalAmount > 0 && counter >= (originalAmount * BigDecimal(minRatio))
  }
}
