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

  def botDifficulty(teamId: TeamId): BotDifficulty = {
    val i = math.abs(teamId.value.hashCode % 3)
    BotDifficulty.values(i)
  }

  def botStyle(teamId: TeamId): BotStyle =
    if ((math.abs(teamId.value.hashCode) >> 4) % 2 == 0) BotStyle.Defensive else BotStyle.Attacking

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
    ("3-5-2", List("GK", "LCB", "CB", "RCB", "LWB", "LCM", "CDM", "RCM", "RWB", "LS", "RS"))
  )

  /** Wagi atrybutów per slot (BOTY §3.1). Atrybut w physical/technical/mental; suma wag = 1. */
  private val slotAttributeWeights: Map[String, List[(String, Double)]] = Map(
    "GK"   -> List("reflexes" -> 0.28, "handling" -> 0.26, "positioning" -> 0.12, "agility" -> 0.1, "strength" -> 0.08, "anticipation" -> 0.08, "concentration" -> 0.08),
    "LCB"  -> List("tackling" -> 0.2, "marking" -> 0.2, "heading" -> 0.15, "strength" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.1, "bravery" -> 0.05, "jumpingReach" -> 0.05),
    "RCB"  -> List("tackling" -> 0.2, "marking" -> 0.2, "heading" -> 0.15, "strength" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.1, "bravery" -> 0.05, "jumpingReach" -> 0.05),
    "CB"   -> List("tackling" -> 0.2, "marking" -> 0.2, "heading" -> 0.15, "strength" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.1, "bravery" -> 0.05, "jumpingReach" -> 0.05),
    "LB"   -> List("tackling" -> 0.14, "marking" -> 0.12, "pace" -> 0.14, "stamina" -> 0.1, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.08, "acceleration" -> 0.1),
    "RB"   -> List("tackling" -> 0.14, "marking" -> 0.12, "pace" -> 0.14, "stamina" -> 0.1, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.08, "acceleration" -> 0.1),
    "LWB"  -> List("tackling" -> 0.12, "pace" -> 0.16, "stamina" -> 0.12, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.1, "acceleration" -> 0.12, "marking" -> 0.06),
    "RWB"  -> List("tackling" -> 0.12, "pace" -> 0.16, "stamina" -> 0.12, "crossing" -> 0.12, "positioning" -> 0.1, "workRate" -> 0.1, "acceleration" -> 0.12, "marking" -> 0.06),
    "CDM"  -> List("tackling" -> 0.16, "positioning" -> 0.14, "passing" -> 0.12, "stamina" -> 0.1, "marking" -> 0.1, "anticipation" -> 0.1, "strength" -> 0.08, "decisions" -> 0.1),
    "LCM"  -> List("passing" -> 0.16, "vision" -> 0.12, "decisions" -> 0.12, "stamina" -> 0.1, "firstTouch" -> 0.1, "technique" -> 0.1, "positioning" -> 0.08, "workRate" -> 0.1),
    "RCM"  -> List("passing" -> 0.16, "vision" -> 0.12, "decisions" -> 0.12, "stamina" -> 0.1, "firstTouch" -> 0.1, "technique" -> 0.1, "positioning" -> 0.08, "workRate" -> 0.1),
    "LM"   -> List("pace" -> 0.14, "dribbling" -> 0.12, "crossing" -> 0.12, "acceleration" -> 0.1, "passing" -> 0.1, "stamina" -> 0.08, "offTheBall" -> 0.08),
    "RM"   -> List("pace" -> 0.14, "dribbling" -> 0.12, "crossing" -> 0.12, "acceleration" -> 0.1, "passing" -> 0.1, "stamina" -> 0.08, "offTheBall" -> 0.08),
    "LW"   -> List("pace" -> 0.14, "dribbling" -> 0.14, "crossing" -> 0.1, "acceleration" -> 0.1, "finishing" -> 0.1, "offTheBall" -> 0.1, "technique" -> 0.08),
    "RW"   -> List("pace" -> 0.14, "dribbling" -> 0.14, "crossing" -> 0.1, "acceleration" -> 0.1, "finishing" -> 0.1, "offTheBall" -> 0.1, "technique" -> 0.08),
    "ST"   -> List("finishing" -> 0.22, "composure" -> 0.14, "offTheBall" -> 0.12, "shooting" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.08, "strength" -> 0.08, "heading" -> 0.08),
    "LS"   -> List("finishing" -> 0.2, "composure" -> 0.14, "offTheBall" -> 0.12, "shooting" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.08, "strength" -> 0.08, "heading" -> 0.08),
    "RS"   -> List("finishing" -> 0.2, "composure" -> 0.14, "offTheBall" -> 0.12, "shooting" -> 0.1, "positioning" -> 0.1, "anticipation" -> 0.08, "strength" -> 0.08, "heading" -> 0.08)
  )

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
      (slot == "ST" && preferred.contains("CF")) ||
      (slot == "GK" && preferred.contains("GK")) ||
      (Set("LCB", "RCB", "CB").contains(slot) && preferred.exists(s => s == "CB" || s == "LCB" || s == "RCB")) ||
      (Set("LB", "RB", "LWB", "RWB").contains(slot) && preferred.exists(s => s.contains("B") || s == "WB"))
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
    val base = if (!withTriggers) s"""{"formationName":"$formationName"}"""
               else s"""{"formationName":"$formationName","triggerConfig":{"pressZones":[7,8,9],"counterTriggerZone":4}}"""
    val ti = opponentStrength match {
      case Some(s) if s > 0.6 => """"teamInstructions":{"tempo":"lower","pressingIntensity":"normal","width":"normal"}"""
      case Some(s) if s < 0.4 => """"teamInstructions":{"tempo":"higher","pressingIntensity":"higher","width":"wide"}"""
      case _ =>
        style match {
          case BotStyle.Defensive => """"teamInstructions":{"tempo":"lower","pressingIntensity":"normal","width":"normal"}"""
          case BotStyle.Attacking => """"teamInstructions":{"tempo":"higher","pressingIntensity":"higher","width":"wide"}"""
        }
    }
    if (ti.isEmpty) base else base.dropRight(1) + "," + ti + "}"
  }

  /** Wybór formacji: opponentStrength + style (Defensive → 4-4-2, Attacking → 4-3-3). */
  def pickFormation(teamId: TeamId, opponentStrength: Option[Double], style: BotStyle = BotStyle.Defensive): String = {
    val idx = math.abs(teamId.value.hashCode % 3)
    val baseIdx = opponentStrength match {
      case Some(s) if s > 0.6 => if (idx == 0) 1 else idx
      case Some(s) if s < 0.4 => if (idx == 2) 1 else idx
      case _ => idx
    }
    style match {
      case BotStyle.Defensive => formations(if (baseIdx == 0) 1 else baseIdx)._1
      case BotStyle.Attacking => formations(if (baseIdx == 2) 0 else math.min(baseIdx, 1))._1
    }
  }

  def useTriggers(teamId: TeamId): Boolean = (math.abs(teamId.value.hashCode) % 2) == 0

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
}
