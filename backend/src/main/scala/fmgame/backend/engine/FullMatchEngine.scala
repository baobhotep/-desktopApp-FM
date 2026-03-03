package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*

/**
 * Pełny silnik meczu: maszyna stanów zdarzenie po zdarzeniu z Pitch Control, DxT, formułowymi xG/VAEP i triggerami botów.
 * Zastępuje uproszczenie Poisson+bulk zdarzeń modelem sekwencyjnym z pozycjami 22 graczy i kontrolą przestrzeni.
 */
object FullMatchEngine extends MatchEngine {

  def simulate(input: MatchEngineInput): ZIO[Any, MatchEngineError, MatchEngineResult] =
    ZIO.logDebug("FullMatchEngine.simulate start") *>
    ZIO.blocking(ZIO.attempt(buildResult(input))).mapError(EngineFault.apply).flatMap {
      case Left(msg) => ZIO.logWarning(s"FullMatchEngine invalid lineup: $msg") *> ZIO.fail(InvalidLineup(msg))
      case Right(r)  => ZIO.logDebug(s"FullMatchEngine.simulate done: ${r.events.size} events, ${r.homeGoals}-${r.awayGoals}") *> ZIO.succeed(r)
    }

  private def buildResult(input: MatchEngineInput): Either[String, MatchEngineResult] = {
    val xgModel = input.xgModelOverride.getOrElse(FormulaBasedxG)
    val vaepModel = input.vaepModelOverride.getOrElse(FormulaBasedVAEP)
    if (input.homeTeam.players.size != 11) Left("home team must have 11 players")
    else if (input.awayTeam.players.size != 11) Left("away team must have 11 players")
    else {
    val homeIds = input.homeTeam.players.map(_.player.id).toList
    val awayIds = input.awayTeam.players.map(_.player.id).toList
    if (input.homeTeam.lineup.size != 11 || !input.homeTeam.lineup.keySet.forall(homeIds.toSet)) Left("home lineup must cover exactly 11 players")
    else if (input.awayTeam.lineup.size != 11 || !input.awayTeam.lineup.keySet.forall(awayIds.toSet)) Left("away lineup must cover exactly 11 players")
    else {
    val rng = input.randomSeed.fold(new scala.util.Random)(new scala.util.Random(_))
    val homeFormation = input.homePlan.formationName
    val awayFormation = input.awayPlan.formationName
    val homeWidthScale = input.homePlan.teamInstructions.flatMap(_.width).fold(1.0)(w => if (w == "narrow") 0.8 else if (w == "wide") 1.2 else 1.0)
    val awayWidthScale = input.awayPlan.teamInstructions.flatMap(_.width).fold(1.0)(w => if (w == "narrow") 0.8 else if (w == "wide") 1.2 else 1.0)
    val paceAccMap = buildPaceAccMap(input)
    var state = MatchState.initial(
      input.homeTeam.teamId,
      input.awayTeam.teamId,
      homeFormation,
      awayFormation,
      homeIds,
      awayIds,
      input.homePlan.triggerConfig,
      input.awayPlan.triggerConfig,
      input.homePlan.customPositions,
      input.awayPlan.customPositions,
      homeWidthScale,
      awayWidthScale,
      Some(paceAccMap)
    )
    val kickOff = MatchEventRecord(0, "KickOff", None, None, Some(input.homeTeam.teamId), None, Some("Success"), Map.empty)
    val eventsAcc = scala.collection.mutable.ArrayBuffer[MatchEventRecord](kickOff)
    val homeOutfield = input.homeTeam.players.filter(_.player.preferredPositions != Set("GK"))
    val awayOutfield = input.awayTeam.players.filter(_.player.preferredPositions != Set("GK"))
    val strictness = math.max(0, math.min(1, input.referee.strictness))

    while (state.totalSeconds < 5400) {
      val (event, nextState) = generateNextEvent(state, input, homeOutfield, awayOutfield, strictness, rng, xgModel, vaepModel, paceAccMap)
      eventsAcc += event
      state = nextState
    }

    val events = eventsAcc.toList.sortBy(_.minute)
    val homeGoals = state.homeGoals
    val awayGoals = state.awayGoals
    val analytics = Some(computeAnalyticsFromEvents(events, input.homeTeam.teamId, input.awayTeam.teamId, homeGoals, awayGoals, vaepModel))
    Right(MatchEngineResult(homeGoals, awayGoals, events, analytics))
    }
    }
  }

  /** Modyfikatory z ról (slotRoles), instrukcji (playerInstructions) i teamwork dla zawodnika z piłką: (passBonus, shotTendencyBonus, xgMultiplier). */
  private def roleAndInstructionModifiers(plan: GamePlanInput, slotOpt: Option[String], actor: PlayerMatchInput): (Double, Double, Double) = {
    val role = slotOpt.flatMap(s => plan.slotRoles.flatMap(_.get(s)))
    val instr = slotOpt.flatMap(s => plan.playerInstructions.flatMap(_.get(s))).getOrElse(Map.empty)
    val teamwork = math.max(1, math.min(20, actor.player.mental.getOrElse("teamwork", 10)))
    val teamworkFactor = teamwork / 20.0
    val passBonusRaw = role match {
      case Some(r) if Set("playmaker", "deep_lying_playmaker", "advanced_playmaker", "regista").contains(r.toLowerCase) => 0.02
      case _ => 0.0
    }
    val passBonus = (passBonusRaw + (if (instr.get("passing").exists(v => v == "more_direct" || v == "more_creative")) 0.01 else 0.0)) * teamworkFactor
    val (shotTendencyBonus, xgMult) = role match {
      case Some(r) if Set("advanced_forward", "poacher", "complete_forward", "pressing_forward").contains(r.toLowerCase) => (0.008 * teamworkFactor, 1.05)
      case Some(r) if Set("inside_forward", "winger", "trequartista").contains(r.toLowerCase) => (0.004 * teamworkFactor, 1.02)
      case _ => (0.0, 1.0)
    }
    val instrShot = if (instr.get("shooting").exists(_ == "shoot_more_often")) 0.003 * teamworkFactor else 0.0
    (passBonus, shotTendencyBonus + instrShot, xgMult)
  }

  /** Wybór jednego elementu z listy z wagami (suma wag do normalizacji). */
  private def pickWeighted[T](items: List[T], weightFn: T => Double, rng: scala.util.Random): T = {
    if (items.isEmpty) throw new IllegalArgumentException("pickWeighted: empty list")
    val weights = items.map(w => math.max(0.01, weightFn(w)))
    val total = weights.sum
    var r = rng.nextDouble() * total
    val idx = items.indices.find { i => r -= weights(i); r <= 0 }.getOrElse(items.size - 1)
    items(idx)
  }

  /** Waga gracza z piłką: w strefie ataku (9–12) napastnicy/skrzydłowi + offTheBall; w budowaniu (1–6) playmakerzy; dopasowanie pozycji (slot ∈ preferredPositions) lekki bonus. */
  private def actorWeight(p: PlayerMatchInput, zone: Int, lineup: Map[PlayerId, String], plan: GamePlanInput): Double = {
    val slot = lineup.get(p.player.id)
    val role = slot.flatMap(s => plan.slotRoles.flatMap(_.get(s)))
    val inAttack = zone >= 9
    val inBuildUp = zone <= 6
    val offTheBall = math.max(1, math.min(20, p.player.mental.getOrElse("offTheBall", 10)))
    var w = 1.0
    if (inAttack && role.exists(r => Set("advanced_forward", "poacher", "complete_forward", "pressing_forward", "inside_forward", "winger", "trequartista").contains(r.toLowerCase))) w += 0.5
    if (inAttack) w += (offTheBall - 10) * 0.02
    if (inBuildUp && role.exists(r => Set("playmaker", "deep_lying_playmaker", "advanced_playmaker", "regista", "anchor", "half_back").contains(r.toLowerCase))) w += 0.35
    val positionMatch = slot.exists(s => p.player.preferredPositions.contains(s) || p.player.preferredPositions.exists(pp => s.startsWith(pp) || pp.startsWith(s)))
    if (positionMatch) w += 0.15
    w
  }

  /** Z-Score atrybutu dla pozycji (IWP §9): (value - mean) / stddev; 0 jeśli brak statystyk. */
  private def zScoreForSlot(slot: Option[String], attrName: String, value: Int, ctx: LeagueContextInput): Double = {
    val statsOpt = slot.flatMap(s => ctx.positionStats.get(s).flatMap(_.get(attrName)))
    statsOpt match {
      case Some(PositionAttrStats(mean, stddev)) if stddev > 0.01 =>
        ((value - mean) / stddev).max(-2.0).min(2.0)
      case _ => 0.0
    }
  }

  /** Waga obrońcy przy przechwycie/tackle: role + atrybuty + bonus Z-Score (IWP). */
  private def interceptorWeight(p: PlayerMatchInput, defendersPlan: GamePlanInput, defendersLineup: Map[PlayerId, String], leagueContext: LeagueContextInput): Double = {
    val slot = defendersLineup.get(p.player.id)
    val role = slot.flatMap(s => defendersPlan.slotRoles.flatMap(_.get(s)))
    val tackling = math.max(1, math.min(20, p.player.technical.getOrElse("tackling", 10)))
    val marking = math.max(1, math.min(20, p.player.technical.getOrElse("marking", 10)))
    val positioning = math.max(1, math.min(20, p.player.mental.getOrElse("positioning", 10)))
    val anticipation = math.max(1, math.min(20, p.player.mental.getOrElse("anticipation", 10)))
    val bravery = math.max(1, math.min(20, p.player.mental.getOrElse("bravery", 10)))
    val aggression = math.max(1, math.min(20, p.player.mental.getOrElse("aggression", 10)))
    var w = 1.0
    if (role.exists(r => Set("ball_winner", "anchor", "half_back", "full_back", "wing_back", "no_nonsense_centre_back").contains(r.toLowerCase))) w += 0.4
    w += (tackling - 10) * 0.02 + (marking - 10) * 0.015 + (positioning - 10) * 0.015 + (anticipation - 10) * 0.012 + (bravery - 10) * 0.008 + (aggression - 10) * 0.01
    val zBonus = 0.04 * (zScoreForSlot(slot, "tackling", tackling, leagueContext) + zScoreForSlot(slot, "positioning", positioning, leagueContext)) / 2.0
    w * (1.0 + zBonus)
  }

  /** Modyfikator szansy podania przy grze „poza pozycją” (slot nie w preferredPositions). */
  private def positionMatchPassPenalty(slot: Option[String], preferredPositions: Set[String]): Double =
    slot.fold(0.0)(s => if (preferredPositions.contains(s) || preferredPositions.exists(pp => s.startsWith(pp) || pp.startsWith(s))) 0.0 else -0.02)

  /** Trudność podania (DOPRACOWANIA §2.2): mnożnik bazy P(sukces). Wyższa presja i dystans stref → niższy. */
  private def passDifficulty(zoneFrom: Int, zoneTo: Int, receiverPressure: Int): Double = {
    val pressureNorm = (receiverPressure / 6.0).min(1.0) * EngineConstants.PassDifficultyPressureFactor
    val zoneDist = math.abs(zoneTo - zoneFrom).min(11) / 11.0 * EngineConstants.PassDifficultyDistanceFactor
    (1.0 - pressureNorm - zoneDist).max(EngineConstants.PassDifficultyMin).min(1.0)
  }

  /** xPass z modelu (DOPRACOWANIA §2.12): wartość strefy docelowej (DxT) skorygowana o presję na odbiorcy. */
  private def xPassFromModel(dxtByZone: Map[Int, Double], zoneFrom: Int, zoneTo: Int, receiverPressure: Int): Double = {
    val threatTo = dxtByZone.getOrElse(zoneTo, DxT.baseZoneThreat(zoneTo))
    val maxThreat = 0.2
    val baseXPass = 0.5 + 0.45 * (threatTo / maxThreat).min(1.0)
    val pressurePenalty = (receiverPressure / 6.0).min(1.0) * 0.25
    (baseXPass - pressurePenalty).max(0.5).min(0.95)
  }

  /** Finishing: silnik używa "finishing"; PlayerGenerator zapisuje "shooting" — akceptujemy oba. */
  private def finishingAttr(t: Map[String, Int]): Int = math.max(1, math.min(20, t.getOrElse("finishing", t.getOrElse("shooting", 10))))
  /** GK atrybuty: silnik używa "reflexes"/"handling"; PlayerGenerator zapisuje "gkReflexes"/"gkHandling". */
  private def gkReflexesAttr(t: Map[String, Int]): Int = math.max(1, math.min(20, t.getOrElse("reflexes", t.getOrElse("gkReflexes", 10))))
  private def gkHandlingAttr(t: Map[String, Int]): Int = math.max(1, math.min(20, t.getOrElse("handling", t.getOrElse("gkHandling", 10))))

  /** Współczynnik podatności na kontuzję (cecha injuryProne 1–20). §14 ACWR. */
  private def injuryProneFactor(p: fmgame.backend.domain.Player): Double =
    0.7 + 0.3 * (p.traits.getOrElse("injuryProne", 5) / 20.0)

  /** ACWR: im więcej minut w ostatnich meczach, tym wyższe ryzyko kontuzji. */
  private def acwrFactor(p: PlayerMatchInput): Double =
    p.recentMinutesPlayed.fold(1.0)(m => 1.0 + 0.4 * math.min(1.0, m / 270.0))

  /** Mapa pace/acceleration (1–20) dla wszystkich 22 graczy; używana w Pitch Control (time-to-intercept). */
  private def buildPaceAccMap(input: MatchEngineInput): Map[PlayerId, (Int, Int)] = {
    val all = input.homeTeam.players ++ input.awayTeam.players
    all.map { pmi =>
      val ph = pmi.player.physical
      val pace = math.max(1, math.min(20, ph.getOrElse("pace", 10)))
      val acc = math.max(1, math.min(20, ph.getOrElse("acceleration", 10)))
      pmi.player.id -> (pace, acc)
    }.toMap
  }

  private def defenderPressBonusFromInstructions(plan: GamePlanInput): Double =
    plan.playerInstructions.fold(0.0) { m =>
      if (m.values.exists(_.get("pressIntensity").contains("more_urgent"))) 0.03 else 0.0
    }

  /** Zmęczenie rośnie z tempo/pressing (teamInstructions) i maleje przy wysokiej stamina; workRate lekko podbija. DOPRACOWANIA §2.7: isHighIntensity (strefy 7–12, kontratak) zwiększa przyrost. */
  private def updateFatigue(state: MatchState, deltaMinutes: Double, input: MatchEngineInput, isHighIntensity: Boolean = false): Map[PlayerId, Double] = {
    val homeMap = input.homeTeam.players.map(p => p.player.id -> p).toMap
    val awayMap = input.awayTeam.players.map(p => p.player.id -> p).toMap
    def teamMult(plan: GamePlanInput): (Double, Double) = {
      val ti = plan.teamInstructions.getOrElse(TeamInstructions())
      val tempo = ti.tempo.getOrElse("normal")
      val pressing = ti.pressingIntensity.getOrElse("normal")
      val t = if (tempo == "higher") 1.22 else if (tempo == "lower") 0.88 else 1.0
      val p = if (pressing == "higher") 1.18 else if (pressing == "lower") 0.88 else 1.0
      (t, p)
    }
    val (homeT, homeP) = teamMult(input.homePlan)
    val (awayT, awayP) = teamMult(input.awayPlan)
    val intensityMult = if (isHighIntensity) EngineConstants.FatigueIntensityMultiplier else 1.0
    val allIds = state.homePlayerIds ++ state.awayPlayerIds
    allIds.map { id =>
      val (tMult, pMult) = if (state.homePlayerIds.contains(id)) (homeT, homeP) else (awayT, awayP)
      val pmi = homeMap.get(id).orElse(awayMap.get(id)).get
      val pl = pmi.player
      val stamina = math.max(1, math.min(20, pl.physical.getOrElse("stamina", 10)))
      val workRate = math.max(1, math.min(20, pl.mental.getOrElse("workRate", 10)))
      val naturalFitness = math.max(1, math.min(20, pl.physical.getOrElse("naturalFitness", 10)))
      val staminaFactor = 0.85 + (stamina / 20.0) * 0.3
      val nfFactor = 0.92 + (naturalFitness / 20.0) * 0.16
      val wrFactor = 0.98 + (workRate - 10) * 0.004
      val freshnessFactor = 1.25 - 0.5 * math.max(0, math.min(1, pmi.freshness))
      val delta = deltaMinutes * EngineConstants.FatigueBaseRate * tMult * pMult * intensityMult / (staminaFactor * nfFactor) * wrFactor * freshnessFactor
      id -> (state.fatigueByPlayer.getOrElse(id, 0.0) + delta).min(1.0)
    }.toMap
  }

  private def generateNextEvent(
    state: MatchState,
    input: MatchEngineInput,
    homeOutfield: List[PlayerMatchInput],
    awayOutfield: List[PlayerMatchInput],
    strictness: Double,
    rng: scala.util.Random,
    xgModel: xGModel,
    vaepModel: VAEPModel,
    paceAccMap: Map[PlayerId, (Int, Int)]
  ): (MatchEventRecord, MatchState) = {
    val secondDelta = 2 + rng.nextInt(7)
    val nextTotalSeconds = (state.totalSeconds + secondDelta).min(5400)
    val nextMinute = nextTotalSeconds / 60
    val deltaMinutes = secondDelta / 60.0
    val possTeamId = state.possession.getOrElse(input.homeTeam.teamId)
    val isHome = possTeamId == input.homeTeam.teamId
    val outfield = if (isHome) homeOutfield else awayOutfield
    val plan = if (isHome) input.homePlan else input.awayPlan
    val lineup = if (isHome) input.homeTeam.lineup else input.awayTeam.lineup
    val zone = state.ballZone
    val actor = pickWeighted(outfield, p => actorWeight(p, zone, lineup, plan), rng)
    val (homePos, awayPos) = (state.homePositions, state.awayPositions)
    val control = state.pitchControlByZone.getOrElse(zone, (0.5, 0.5))
    val opponentControl = if (isHome) control._2 else control._1
    val pressActive = (if (isHome) state.awayTriggerConfig else state.homeTriggerConfig).exists { tc =>
      tc.pressZones.contains(zone)
    }
    val interceptBonus = if (pressActive) 0.15 else 0.0
    val matchupPressure = MatchupMatrix.pressureInZone(homePos, awayPos, zone, isHome) * 0.08
    val actorFatigue = state.fatigueByPlayer.getOrElse(actor.player.id, 0.0)
    val fatigueMissBonus = actorFatigue * 0.06

    var eventTypeRoll = rng.nextDouble()
    if (state.justRecoveredInCounterZone && rng.nextDouble() < 0.35)
      eventTypeRoll = 0.44
    // Stałe fragmenty: po rogu/wolnym częściej pojawia się strzał.
    if (state.lastEventType.contains("Corner") && rng.nextDouble() < 0.35) eventTypeRoll = 0.43
    if (state.lastEventType.contains("FreeKick") && rng.nextDouble() < 0.22) eventTypeRoll = 0.435
    val slot = lineup.get(actor.player.id)
    val strategyShotBonus = plan.teamInstructions.flatMap(_.tempo).fold(0.0)(t => if (t == "higher" && zone >= 8) 0.004 else if (t == "lower") -0.002 else 0.0)
    val strategyPassBonus = plan.teamInstructions.flatMap(_.pressingIntensity).fold(0.0)(p => if (p == "lower" && zone <= 5) 0.012 else 0.0)
    val (passBonus, shotTendencyBonusBase, xgMultiplier) = roleAndInstructionModifiers(plan, slot, actor)
    val shotTendencyBonus = shotTendencyBonusBase + strategyShotBonus
    val defendersPlan = if (isHome) input.awayPlan else input.homePlan
    val defenderPressBonus = defenderPressBonusFromInstructions(defendersPlan)
    val oiBonus = defendersPlan.oppositionInstructions.flatMap(_.find(_.targetPlayerId == actor.player.id)).map { oi =>
      val bPress = if (oi.pressIntensity.contains("more_urgent")) 0.04 else 0.0
      val bTackle = if (oi.tackle.contains("harder")) 0.05 else 0.0
      val bMark = if (oi.mark.contains("tighter")) 0.05 else 0.0
      (bPress + bTackle + bMark).min(0.12)
    }.getOrElse(0.0)
    val homeAdvantage = math.max(1.0, math.min(1.25, input.homeAdvantage))
    val interceptHomeBonus = if (!isHome) (homeAdvantage - 1.0) * 0.025 else 0.0
    val defenderPositions = if (isHome) state.awayPositions else state.homePositions
    val defenders = if (isHome) awayOutfield else homeOutfield
    val tacklingByPlayer = defenders.map(p => p.player.id -> math.max(1, math.min(20, p.player.technical.getOrElse("tackling", 10)))).toMap
    val accelerationByPlayer = defenders.map(p => p.player.id -> math.max(1, math.min(20, p.player.physical.getOrElse("acceleration", 10)))).toMap
    val dynamicP = MatchupMatrix.dynamicPressureTotal(zone, defenderPositions, tacklingByPlayer, accelerationByPlayer)
    // Strzały: ~2.5% zdarzeń → ~25–35 strzałów/mecz (progi z EngineConstants).
    if (eventTypeRoll < EngineConstants.EventPassThreshold) {
      val targetZone = pickTargetZone(state.dxtByZone, zone, isHome, rng)
      val receiverPressure = receiverPressureCount(targetZone, isHome, state)
      val zoneDistance = math.abs(targetZone - zone)
      val passInterceptProb = (EngineConstants.InterceptBase + opponentControl * EngineConstants.InterceptControlFactor + interceptBonus + matchupPressure + oiBonus + defenderPressBonus + interceptHomeBonus + dynamicP * 0.08 + zoneDistance * EngineConstants.InterceptPerZoneDistance).min(EngineConstants.InterceptCap)
      val intercepted = rng.nextDouble() < passInterceptProb
      val decisions = math.max(1, math.min(20, actor.player.mental.getOrElse("decisions", 10)))
      val vision = math.max(1, math.min(20, actor.player.mental.getOrElse("vision", 10)))
      val passing = math.max(1, math.min(20, actor.player.technical.getOrElse("passing", 10)))
      val firstTouch = math.max(1, math.min(20, actor.player.technical.getOrElse("firstTouch", 10)))
      val ballControl = math.max(1, math.min(20, actor.player.technical.getOrElse("ballControl", 10)))
      val technique = math.max(1, math.min(20, actor.player.technical.getOrElse("technique", 10)))
      val leadership = math.max(1, math.min(20, actor.player.mental.getOrElse("leadership", 10)))
      val mentalBonus = (decisions - 10) * 0.004 + (vision - 10) * 0.003 + (passing - 10) * 0.003 + (firstTouch - 10) * 0.002 + (ballControl - 10) * 0.002 + (technique - 10) * 0.005
      val leadershipMod = if (nextMinute > 75) (1.0 + (leadership - 10) * 0.002) else 1.0
      val passingMod = plan.teamInstructions.flatMap(_.passingDirectness).fold(0.0)(p => if (p == "shorter") 0.02 else if (p == "direct") -0.015 else 0.0)
      val posPenalty = positionMatchPassPenalty(slot, actor.player.preferredPositions)
      val concentration = math.max(1, math.min(20, actor.player.mental.getOrElse("concentration", 10)))
      val lateGameConcentration = if (nextMinute > 75) (0.9 + 0.1 * (concentration / 20.0)) else 1.0
      val moraleMod = 0.85 + 0.15 * math.max(0, math.min(1, actor.morale))
      val homePassBonus = if (isHome) (homeAdvantage - 1.0) * 0.02 else 0.0
      val difficultyMult = passDifficulty(zone, targetZone, receiverPressure)
      val passSuccessBase = (EngineConstants.PassSuccessBase - fatigueMissBonus + mentalBonus + passBonus + passingMod + posPenalty + strategyPassBonus + homePassBonus) * moraleMod * lateGameConcentration * leadershipMod * difficultyMult
      val passSuccessBaseClamped = passSuccessBase.max(EngineConstants.PassSuccessMin).min(EngineConstants.PassSuccessMax)
      val outcome = if (intercepted) "Intercepted" else (if (rng.nextDouble() < passSuccessBaseClamped) "Success" else "Missed")
      val (newPossession, newZone) = if (intercepted) {
        (Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), zone)
      } else (state.possession, targetZone)
      val eventType = if (rng.nextDouble() < 0.22) "LongPass" else "Pass"
      val dist = 15 + rng.nextInt(40)
      val xPass = xPassFromModel(state.dxtByZone, zone, targetZone, receiverPressure)
      val threat = state.dxtByZone.getOrElse(targetZone, DxT.baseZoneThreat(targetZone))
      val (eventTypeName, actorId, secondaryId, eventZone, eventOutcome, meta) = if (outcome == "Intercepted") {
        val defendersOutfield = if (isHome) awayOutfield else homeOutfield
        val defendersLineup = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
        val interceptor = pickWeighted(defendersOutfield, p => interceptorWeight(p, defendersPlan, defendersLineup, input.leagueContext), rng)
        val isTackle = rng.nextDouble() < 0.3
        if (isTackle) ("Tackle", interceptor.player.id, Some(actor.player.id), zone, Some("Won"), Map("zone" -> zone.toString))
        else ("PassIntercepted", interceptor.player.id, Some(actor.player.id), zone, None: Option[String], Map("zone" -> zone.toString))
      } else {
        (eventType, actor.player.id, None, targetZone, Some(outcome), Map("zone" -> targetZone.toString, "xPass" -> f"$xPass%.2f", "zoneThreat" -> f"$threat%.3f", "distance" -> dist.toString, "receiverPressure" -> receiverPressure.toString))
      }
      val eventTeamId = if (outcome == "Intercepted") newPossession else Some(possTeamId)
      val event = MatchEventRecord(nextMinute, eventTypeName, Some(actorId), secondaryId, eventTeamId, Some(eventZone), eventOutcome, meta)
      val newPositions = updatePositions(state, newZone, newPossession.contains(input.homeTeam.teamId), input)
      val highIntensity = zone >= 7 || state.justRecoveredInCounterZone
      val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = highIntensity)
      val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
      val newDxt = DxT.threatMap(newControl, newPossession.contains(input.homeTeam.teamId))
      val recoveryInCounterZone = outcome == "Intercepted" && (if (newPossession.contains(input.homeTeam.teamId)) state.homeTriggerConfig else state.awayTriggerConfig).flatMap(_.counterTriggerZone).contains(zone)
      val newState = state.copy(
        minute = nextMinute,
        totalSeconds = nextTotalSeconds,
        ballZone = newZone,
        possession = newPossession,
        homePositions = newPositions._1,
        awayPositions = newPositions._2,
        pitchControlByZone = newControl,
        dxtByZone = newDxt,
        lastEventType = Some(event.eventType),
        fatigueByPlayer = newFatigue,
        justRecoveredInCounterZone = recoveryInCounterZone,
        lastSetPieceRoutine = None
      )
      (event, newState)
    } else if (eventTypeRoll < (EngineConstants.EventShotThresholdBase + shotTendencyBonus)) {
      val isHeader = state.lastEventType.contains("Cross")
      // xG Next-Gen: Angular Pressure + odległość GK od bramki.
      val (attPosList, defPosList, defGoalX, defGkIds) =
        if (isHome)
          (
            homePos,
            awayPos,
            0.0,
            input.awayTeam.players.filter(_.player.preferredPositions.contains("GK")).map(_.player.id).toSet
          )
        else
          (
            awayPos,
            homePos,
            PitchModel.PitchLength,
            input.homeTeam.players.filter(_.player.preferredPositions.contains("GK")).map(_.player.id).toSet
          )
      val shooterPosOpt = attPosList.find(_.playerId == actor.player.id)
      val goalY = PitchModel.PitchWidth / 2.0
      val (pressureCount, angularPressure, gkDistance) = shooterPosOpt match {
        case Some(sp) =>
          val goalVecX = defGoalX - sp.x
          val goalVecY = goalY - sp.y
          val goalNorm = math.hypot(goalVecX, goalVecY).max(1e-6)
          val goalUx = goalVecX / goalNorm
          val goalUy = goalVecY / goalNorm

          var count = 0
          var angularAgg = 0.0
          defPosList.foreach { dp =>
            val dx = dp.x - sp.x
            val dy = dp.y - sp.y
            val dist = math.hypot(dx, dy)
            if (dist <= 12.0) {
              val dot = (dx * goalUx + dy * goalUy) / dist
              if (dot > 0.3) { // obrońca przed strzelcem, w stożku między piłką a bramką
                count += 1
                angularAgg += (1.0 - math.min(1.0, dist / 12.0)) * dot
              }
            }
          }
          val gkPosOpt = defPosList.find(dp => defGkIds.contains(dp.playerId))
          val gkDist = gkPosOpt match {
            case Some(gp) => math.hypot(gp.x - defGoalX, gp.y - goalY)
            case None     => 0.0
          }
          (count, angularAgg, gkDist)
        case None =>
          (0, 0.0, 0.0)
      }

      val xgCtx = ShotContext(
        zone,
        8 + (12 - zone) * 4,
        isHeader = isHeader,
        nextMinute,
        state.scoreDiff,
        pressureCount = pressureCount,
        angularPressure = angularPressure,
        gkDistance = gkDistance
      )
      var xg = xgModel.xGForShot(xgCtx)
      val routineXgMult = state.lastSetPieceRoutine.fold(1.0) {
        case "near_post" => 1.05
        case "far_post"  => 1.02
        case "short"     => 0.95
        case "direct"    => 1.08
        case _           => 1.0
      }
      val finishing = finishingAttr(actor.player.technical)
      val composure = math.max(1, math.min(20, actor.player.mental.getOrElse("composure", 10)))
      val heading = math.max(1, math.min(20, actor.player.technical.getOrElse("heading", 10)))
      val longShots = math.max(1, math.min(20, actor.player.technical.getOrElse("longShots", 10)))
      val technique = math.max(1, math.min(20, actor.player.technical.getOrElse("technique", 10)))
      val moraleModShot = 0.88 + 0.12 * math.max(0, math.min(1, actor.morale))
      val leadershipShot = if (nextMinute > 75) (1.0 + (actor.player.mental.getOrElse("leadership", 10) - 10) * 0.005) else 1.0
      var attrXgMult = (0.90 + (finishing / 20.0) * 0.10 + (composure / 20.0) * 0.04) * moraleModShot * leadershipShot
      attrXgMult *= (1.0 + (technique - 10) * 0.01)
      if (isHeader) attrXgMult *= (0.85 + (heading / 20.0) * 0.15)
      if (zone <= 7) attrXgMult *= (0.75 + (longShots / 20.0) * 0.25)
      xg = (xg * xgMultiplier * routineXgMult * attrXgMult).min(0.99)
      xg = (xg * input.leagueContext.xgCalibration.getOrElse(1.0)).min(0.99).max(0.001)
      val isGoal = rng.nextDouble() < xg
      val gk = if (isHome) input.awayTeam.players.find(_.player.preferredPositions.contains("GK"))
               else input.homeTeam.players.find(_.player.preferredPositions.contains("GK"))
      val gkReflexes = gk.map(p => gkReflexesAttr(p.player.technical)).getOrElse(10)
      val gkHandling = gk.map(p => gkHandlingAttr(p.player.technical)).getOrElse(10)
      val gkDiving = gk.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkDiving", p.player.technical.getOrElse("gkReflexes", 10))))).getOrElse(10)
      val gkPositioning = gk.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkPositioning", 10)))).getOrElse(10)
      val gkOneOnOnes = gk.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkOneOnOnes", 10)))).getOrElse(10)
      var gkSaveMult = (0.75 + (gkReflexes + gkHandling) / 80.0).min(1.25)
      gkSaveMult *= (0.9 + (gkPositioning / 20.0) * 0.2)
      gkSaveMult *= (0.92 + (gkDiving / 20.0) * 0.12)
      if (zone >= 11) gkSaveMult *= (0.95 + (gkOneOnOnes / 20.0) * 0.1)
      val composureShot = math.max(1, math.min(20, actor.player.mental.getOrElse("composure", 10)))
      val techniqueShot = math.max(1, math.min(20, actor.player.technical.getOrElse("technique", 10)))
      val centerProb = (EngineConstants.ShotPlacementCenterBase + 0.2 * (composureShot + techniqueShot) / 40.0).min(0.7).max(0.25)
      val placementRoll = rng.nextDouble()
      val placement = if (placementRoll < (1.0 - centerProb) * 0.5) "left" else if (placementRoll < centerProb + (1.0 - centerProb) * 0.5) "center" else "right"
      val placementFactor = placement match { case "center" => 0.92; case _ => 1.08 }
      val savedProbBase = (0.35 - actorFatigue * 0.05).max(0.2)
      val savedProb = (savedProbBase * gkSaveMult).min(0.5).max(0.12)
      val blockedProb = (1.0 - EngineConstants.ShotMissedVsBlockedBase) + EngineConstants.ShotBlockedPressureBonus * (pressureCount / 3.0).min(1.0) + 0.1 * (angularPressure / 2.0).min(1.0)
      val outcome = if (isGoal) "Goal" else (if (rng.nextDouble() < savedProb) "Saved" else if (rng.nextDouble() < (1.0 - blockedProb)) "Missed" else "Blocked")
      val psxg = (xg * placementFactor).min(0.99).max(0.01)
      val shotMeta = Map("xG" -> f"$xg%.3f", "PSxG" -> f"$psxg%.3f", "placement" -> placement, "zone" -> zone.toString, "zoneThreat" -> f"${state.dxtByZone.getOrElse(zone, 0.1)}%.3f", "defendersInCone" -> pressureCount.toString, "angularPressure" -> f"$angularPressure%.2f", "gkDistance" -> f"$gkDistance%.1f")
      val event = MatchEventRecord(
        nextMinute,
        if (isGoal) "Goal" else "Shot",
        Some(actor.player.id),
        None,
        Some(possTeamId),
        Some(zone),
        Some(if (isGoal) "Success" else outcome),
        shotMeta
      )
      val newHomeGoals = if (isGoal && isHome) state.homeGoals + 1 else state.homeGoals
      val newAwayGoals = if (isGoal && !isHome) state.awayGoals + 1 else state.awayGoals
      val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = zone >= 7)
      val newState = state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, homeGoals = newHomeGoals, awayGoals = newAwayGoals, lastEventType = Some(event.eventType), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None)
      (event, newState)
    } else if (eventTypeRoll < EngineConstants.EventFoulPenaltyThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      if (zone >= 10 && rng.nextDouble() < 0.18) {
        val setPieces = if (isHome) input.homePlan.setPieces else input.awayPlan.setPieces
        val takerPmi = setPieces.flatMap(_.penaltyTakerPlayerId).flatMap(pid => outfield.find(_.player.id == pid)).getOrElse(actor)
        val takerId = takerPmi.player.id
        val composure = math.max(1, math.min(20, takerPmi.player.mental.getOrElse("composure", 10)))
        val finishing = finishingAttr(takerPmi.player.technical)
        val penXgBase = 0.76
        val penXgMod = 0.92 + (composure / 20.0) * 0.04 + (finishing / 20.0) * 0.04
        val penXg = (penXgBase * penXgMod).min(0.92)
        val gkPen = if (isHome) input.awayTeam.players.find(_.player.preferredPositions.contains("GK"))
                    else input.homeTeam.players.find(_.player.preferredPositions.contains("GK"))
        val gkReflexes = gkPen.map(p => gkReflexesAttr(p.player.technical)).getOrElse(10)
        val gkHandling = gkPen.map(p => gkHandlingAttr(p.player.technical)).getOrElse(10)
        val gkDivingPen = gkPen.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkDiving", p.player.technical.getOrElse("gkReflexes", 10))))).getOrElse(10)
        val gkPositioningPen = gkPen.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkPositioning", 10)))).getOrElse(10)
        val gkOneOnOnesPen = gkPen.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkOneOnOnes", 10)))).getOrElse(10)
        var gkSaveMultPen = (0.75 + (gkReflexes + gkHandling) / 80.0).min(1.25)
        gkSaveMultPen *= (0.9 + (gkPositioningPen / 20.0) * 0.2)
        gkSaveMultPen *= (0.92 + (gkDivingPen / 20.0) * 0.1)
        gkSaveMultPen *= (0.95 + (gkOneOnOnesPen / 20.0) * 0.1)
        val saveProbSame = (0.45 * gkSaveMultPen).min(0.7).max(0.25)
        val saveProbDiff = (0.18 * gkSaveMultPen).min(0.35).max(0.08)
        val payoffLL = 1.0 - saveProbSame
        val payoffLR = 1.0 - saveProbDiff
        val payoffRL = 1.0 - saveProbDiff
        val payoffRR = 1.0 - saveProbSame
        val (nashShooterL, nashGkL) = AdvancedAnalytics.nashPenalty2x2(payoffLL, payoffLR, payoffRL, payoffRR)
        val shootLeft = rng.nextDouble() < nashShooterL
        val gkDiveLeft = rng.nextDouble() < nashGkL
        val saveProb = if (shootLeft == gkDiveLeft) saveProbSame else saveProbDiff
        val isGoal = rng.nextDouble() >= saveProb
        val outcome = if (isGoal) "Goal" else (if (rng.nextDouble() < saveProb) "Saved" else "Missed")
        val event = MatchEventRecord(nextMinute, if (isGoal) "Goal" else "Penalty", Some(takerId), None, Some(possTeamId), Some(zone), Some(outcome), Map("xG" -> f"$penXg%.3f", "penalty" -> "true"))
        val newHomeGoals = if (isGoal && isHome) state.homeGoals + 1 else state.homeGoals
        val newAwayGoals = if (isGoal && !isHome) state.awayGoals + 1 else state.awayGoals
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, homeGoals = newHomeGoals, awayGoals = newAwayGoals, lastEventType = Some("Penalty"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
      } else {
        val baseInjuryProb = 0.015
        val injuryProb = baseInjuryProb * injuryProneFactor(actor.player) * acwrFactor(actor)
        val isInjury = rng.nextDouble() < injuryProb
        val event = if (isInjury)
          MatchEventRecord(nextMinute, "Injury", Some(actor.player.id), None, Some(possTeamId), Some(zone), None, Map("severity" -> "Light", "returnMatchday" -> "2"))
        else
          MatchEventRecord(nextMinute, "Foul", Some(actor.player.id), None, Some(possTeamId), Some(zone), Some("Success"), Map("IWP" -> f"${0.35 + rng.nextDouble() * 0.35}%.2f"))
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some(event.eventType), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
      }
    } else if (eventTypeRoll < EngineConstants.EventClearanceThreshold && zone <= 4) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val gkOpt = if (isHome) input.homeTeam.players.find(_.player.preferredPositions.contains("GK")) else input.awayTeam.players.find(_.player.preferredPositions.contains("GK"))
      val useGkClearance = zone <= 2 && gkOpt.isDefined && rng.nextDouble() < 0.28
      val clearer = if (useGkClearance) gkOpt.get else pickWeighted(outfield, p => interceptorWeight(p, plan, lineup, input.leagueContext), rng)
      val (outcome, meta, newPossession) = if (useGkClearance) {
        val gk = clearer
        val isKick = rng.nextDouble() < 0.7
        val gkKicking = math.max(1, math.min(20, gk.player.technical.getOrElse("gkKicking", 10)))
        val gkThrowing = math.max(1, math.min(20, gk.player.technical.getOrElse("gkThrowing", 10)))
        val successProb = if (isKick) 0.5 + (gkKicking - 10) * 0.02 else 0.5 + (gkThrowing - 10) * 0.02
        val success = rng.nextDouble() < successProb.max(0.35).min(0.85)
        val losePossession = !success && rng.nextDouble() < 0.5
        val out = if (success) "Success" else "Cleared"
        val m = Map("zone" -> zone.toString, "distributionType" -> (if (isKick) "kick" else "throw"))
        val poss = if (losePossession) Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId) else Some(possTeamId)
        (Some(out), m, poss)
      } else {
        (Some("Success"), Map("zone" -> zone.toString), Some(possTeamId))
      }
      val event = MatchEventRecord(nextMinute, "Clearance", Some(clearer.player.id), None, newPossession, Some(zone), outcome, meta)
      val newState = state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Clearance"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, possession = newPossession)
      (event, newState)
    } else if (eventTypeRoll < EngineConstants.EventCornerThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val setPiecesCorner = if (isHome) input.homePlan.setPieces else input.awayPlan.setPieces
      val taker = setPiecesCorner.flatMap(_.cornerTakerPlayerId).getOrElse(actor.player.id)
      val routine = setPiecesCorner.flatMap(_.cornerRoutine).getOrElse("default")
      val event = MatchEventRecord(nextMinute, "Corner", Some(taker), None, Some(possTeamId), None, Some("Success"), Map("routine" -> routine))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Corner"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = Some(routine)))
    } else if (eventTypeRoll < EngineConstants.EventThrowInThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val taker = plan.throwInConfig.flatMap(c => c.defaultTakerPlayerId).flatMap(pidStr => outfield.find(_.player.id.value == pidStr)).getOrElse(actor)
      val event = MatchEventRecord(nextMinute, "ThrowIn", Some(taker.player.id), None, Some(possTeamId), Some(3 + rng.nextInt(8)), Some("Success"), Map("xPass" -> f"${0.72 + rng.nextDouble() * 0.22}%.2f"))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("ThrowIn"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else if (eventTypeRoll < EngineConstants.EventCrossThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val crossing = math.max(1, math.min(20, actor.player.technical.getOrElse("crossing", 10)))
      val crossBase = (0.32 + (crossing - 10) * 0.012).max(0.2).min(0.55)
      val success = rng.nextDouble() < (crossBase - actorFatigue * 0.05).max(0.22)
      val crossZone = 8 + rng.nextInt(4)
      val defendingGk = if (isHome) input.awayTeam.players.find(_.player.preferredPositions.contains("GK")) else input.homeTeam.players.find(_.player.preferredPositions.contains("GK"))
      val gkCommand = defendingGk.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkCommandOfArea", 10)))).getOrElse(10)
      val claimed = success && crossZone >= 10 && rng.nextDouble() < (gkCommand / 20.0) * 0.12
      val outcome = if (claimed) "Claimed" else if (success) "Success" else "Missed"
      val event = MatchEventRecord(nextMinute, "Cross", Some(actor.player.id), None, Some(if (claimed) (if (isHome) input.awayTeam.teamId else input.homeTeam.teamId) else possTeamId), Some(crossZone), Some(outcome), Map("xPass" -> f"${0.3 + rng.nextDouble() * 0.4}%.2f"))
      val (newPossession, newPositions, newControl, newDxt) = if (claimed) {
        val np = updatePositions(state, crossZone, !isHome, input)
        val nc = PitchControl.controlByZoneWithFatigue(np._1, np._2, Some(newFatigue), Some(paceAccMap))
        val nd = DxT.threatMap(nc, !isHome)
        (Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), np, nc, nd)
      } else (state.possession, (state.homePositions, state.awayPositions), state.pitchControlByZone, state.dxtByZone)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, possession = newPossession, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("Cross"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else if (eventTypeRoll < EngineConstants.EventInterceptThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = zone >= 7 || state.justRecoveredInCounterZone)
      val defendersOutfield2 = if (isHome) awayOutfield else homeOutfield
      val defendersLineup2 = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
      val interceptor = pickWeighted(defendersOutfield2, p => interceptorWeight(p, defendersPlan, defendersLineup2, input.leagueContext), rng)
      val isTackle = rng.nextDouble() < 0.3
      val (evType, evOutcome) = if (isTackle) ("Tackle", Some("Won")) else ("PassIntercepted", None: Option[String])
      val event = MatchEventRecord(nextMinute, evType, Some(interceptor.player.id), Some(actor.player.id), Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), Some(zone), evOutcome, Map("zone" -> zone.toString))
      val newPossession = Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId)
      val newPositions = updatePositions(state, zone, !isHome, input)
      val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
      val newDxt = DxT.threatMap(newControl, !isHome)
      val recoveryInCounterZone = (if (newPossession.contains(input.homeTeam.teamId)) state.homeTriggerConfig else state.awayTriggerConfig).flatMap(_.counterTriggerZone).contains(zone)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, possession = newPossession, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some(event.eventType), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = recoveryInCounterZone, lastSetPieceRoutine = None))
    } else if (eventTypeRoll < EngineConstants.EventDribbleThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = zone >= 7)
      val dribbling = math.max(1, math.min(20, actor.player.technical.getOrElse("dribbling", 10)))
      val agility = math.max(1, math.min(20, actor.player.physical.getOrElse("agility", 10)))
      val flair = math.max(1, math.min(20, actor.player.mental.getOrElse("flair", 10)))
      val defendersOutfieldD = if (isHome) awayOutfield else homeOutfield
      val defendersLineupD = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
      val (ballCx, ballCy) = PitchModel.zoneCenters.getOrElse(zone, (52.5, 34.0))
      val defenderPositionsD = if (isHome) state.awayPositions else state.homePositions
      val nearestDef = defenderPositionsD.minByOption(p => PitchModel.distance(p.x, p.y, ballCx, ballCy))
      val (avgDefTackling, avgDefAgility) = if (defendersOutfieldD.isEmpty) (10.0, 10.0) else {
        val avgT = defendersOutfieldD.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("tackling", 10)))).sum.toDouble / defendersOutfieldD.size
        val avgA = defendersOutfieldD.map(p => math.max(1, math.min(20, p.player.physical.getOrElse("agility", 10)))).sum.toDouble / defendersOutfieldD.size
        val (nearT, nearA) = nearestDef.flatMap { np =>
          defendersOutfieldD.find(_.player.id == np.playerId).map { p =>
            (math.max(1, math.min(20, p.player.technical.getOrElse("tackling", 10))).toDouble,
             math.max(1, math.min(20, p.player.physical.getOrElse("agility", 10))).toDouble)
          }
        }.getOrElse((10.0, 10.0))
        val w = EngineConstants.DribbleNearestDefenderWeight
        ((1.0 - w) * avgT + w * nearT, (1.0 - w) * avgA + w * nearA)
      }
      var dribbleSuccessProb = EngineConstants.DribbleSuccessBase + (dribbling - 10) * EngineConstants.DribbleSuccessDribblingCoef + (agility - 10) * EngineConstants.DribbleSuccessAgilityCoef - (avgDefTackling - 10) * EngineConstants.DribbleSuccessDefTacklingCoef - (avgDefAgility - 10) * EngineConstants.DribbleSuccessDefAgilityCoef
      val actorSlot = lineup.get(actor.player.id)
      dribbleSuccessProb += 0.03 * zScoreForSlot(actorSlot, "dribbling", dribbling, input.leagueContext)
      dribbleSuccessProb += (rng.nextDouble() - 0.5) * (flair / 20.0) * 0.06
      dribbleSuccessProb = dribbleSuccessProb.max(0.25).min(0.88)
      val dribbleSuccess = rng.nextDouble() < dribbleSuccessProb
      if (dribbleSuccess) {
        val event = MatchEventRecord(nextMinute, "Dribble", Some(actor.player.id), None, Some(possTeamId), Some(zone), Some("Success"), Map("zone" -> zone.toString))
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Dribble"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
      } else {
        val winner = pickWeighted(defendersOutfieldD, p => interceptorWeight(p, defendersPlan, defendersLineupD, input.leagueContext), rng)
        val event = MatchEventRecord(nextMinute, "DribbleLost", Some(actor.player.id), Some(winner.player.id), Some(possTeamId), Some(zone), None, Map.empty)
        val newPossession = Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId)
        val newPositions = updatePositions(state, zone, !isHome, input)
        val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
        val newDxt = DxT.threatMap(newControl, !isHome)
        val recoveryInCounterZone = (if (newPossession.contains(input.homeTeam.teamId)) state.homeTriggerConfig else state.awayTriggerConfig).flatMap(_.counterTriggerZone).contains(zone)
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, possession = newPossession, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("DribbleLost"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = recoveryInCounterZone, lastSetPieceRoutine = None))
      }
    } else if (eventTypeRoll < EngineConstants.EventDuelThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      def duelWeight(p: PlayerMatchInput): Double = {
        val strength = math.max(1, math.min(20, p.player.physical.getOrElse("strength", 10)))
        val balance = math.max(1, math.min(20, p.player.physical.getOrElse("balance", 10)))
        val base = math.max(0.5, strength / 10.0 + balance / 40.0)
        val slot = if (homeOutfield.exists(_.player.id == p.player.id)) input.homeTeam.lineup.get(p.player.id) else input.awayTeam.lineup.get(p.player.id)
        val zBonus = 1.0 + 0.05 * zScoreForSlot(slot, "strength", strength, input.leagueContext)
        base * zBonus
      }
      val all20 = homeOutfield ++ awayOutfield
      val winner = pickWeighted(all20, duelWeight, rng)
      val winnerTid = if (homeOutfield.exists(_.player.id == winner.player.id)) input.homeTeam.teamId else input.awayTeam.teamId
      val event = MatchEventRecord(nextMinute, "Duel", Some(winner.player.id), None, Some(winnerTid), Some(zone), Some("Won"), Map("zone" -> zone.toString))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Duel"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else if (eventTypeRoll < EngineConstants.EventAerialDuelThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      def aerialWeight(p: PlayerMatchInput): Double = math.max(0.5, (p.player.physical.getOrElse("jumpingReach", 10) + p.player.physical.getOrElse("strength", 10)) / 20.0)
      val all20Aerial = homeOutfield ++ awayOutfield
      val winnerAerial = pickWeighted(all20Aerial, aerialWeight, rng)
      val winnerTidAerial = if (homeOutfield.exists(_.player.id == winnerAerial.player.id)) input.homeTeam.teamId else input.awayTeam.teamId
      val event = MatchEventRecord(nextMinute, "AerialDuel", Some(winnerAerial.player.id), None, Some(winnerTidAerial), Some(zone), Some("Won"), Map("zone" -> zone.toString))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("AerialDuel"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else if (eventTypeRoll < EngineConstants.EventFreeKickThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val setPiecesFk = if (isHome) input.homePlan.setPieces else input.awayPlan.setPieces
      val taker = setPiecesFk.flatMap(_.freeKickTakerPlayerId).getOrElse(actor.player.id)
      val routine = setPiecesFk.flatMap(_.freeKickRoutine).getOrElse("default")
      val event = MatchEventRecord(nextMinute, "FreeKick", Some(taker), None, Some(possTeamId), Some(5 + rng.nextInt(6)), Some("Success"), Map("routine" -> routine))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("FreeKick"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = Some(routine)))
    } else if (eventTypeRoll < EngineConstants.EventOffsideThreshold) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val event = MatchEventRecord(nextMinute, "Offside", Some(actor.player.id), None, Some(possTeamId), None, None, Map.empty)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Offside"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else if (nextMinute >= 60 && eventTypeRoll < EngineConstants.EventSubThreshold && rng.nextDouble() < 0.2) {
      val newFatigue = updateFatigue(state, deltaMinutes, input)
      val other = outfield.filter(_.player.id != actor.player.id)
      if (other.size >= 2) {
        val outPlayer = actor
        val inPlayer = other(rng.nextInt(other.size))
        val event = MatchEventRecord(nextMinute, "Substitution", Some(inPlayer.player.id), Some(outPlayer.player.id), Some(possTeamId), None, Some("Success"), Map("minute" -> nextMinute.toString))
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Substitution"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
      } else {
        val pressure = receiverPressureCount(zone, isHome, state)
        val event = MatchEventRecord(nextMinute, "Pass", Some(actor.player.id), None, Some(possTeamId), Some(zone), Some("Success"), Map("zone" -> zone.toString, "xPass" -> "0.85", "receiverPressure" -> pressure.toString))
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Pass"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
      }
    } else {
      val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = false)
      val targetZone = pickTargetZone(state.dxtByZone, zone, isHome, rng)
      val pressure = receiverPressureCount(targetZone, isHome, state)
      val xPass = xPassFromModel(state.dxtByZone, zone, targetZone, pressure)
      val event = MatchEventRecord(nextMinute, "Pass", Some(actor.player.id), None, Some(possTeamId), Some(targetZone), Some("Success"), Map("zone" -> targetZone.toString, "xPass" -> f"$xPass%.2f", "zoneThreat" -> f"${state.dxtByZone.getOrElse(targetZone, 0.1)}%.3f", "receiverPressure" -> pressure.toString))
      val newPositions = updatePositions(state, targetZone, isHome, input)
      val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
      val newDxt = DxT.threatMap(newControl, isHome)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = targetZone, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("Pass"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    }
  }

  private def receiverPressureCount(targetZone: Int, isHome: Boolean, state: MatchState): Int = {
    val (cx, cy) = PitchModel.zoneCenters.getOrElse(targetZone, (52.5, 34.0))
    val defenders = if (isHome) state.awayPositions else state.homePositions
    val radius = 18.0
    defenders.count { p =>
      PitchModel.distance(p.x, p.y, cx, cy) <= radius
    }.min(6)
  }

  private def pickTargetZone(dxtByZone: Map[Int, Double], currentZone: Int, homeAttackingRight: Boolean, rng: scala.util.Random): Int = {
    val candidates = (1 to 12).filter { z => (homeAttackingRight && z >= currentZone) || (!homeAttackingRight && z <= currentZone) }
    val zones = if (candidates.isEmpty) (1 to 12).toList else candidates.toList
    val weights = zones.map(z => dxtByZone.getOrElse(z, 0.1) + 0.05)
    val total = weights.sum
    val r = rng.nextDouble() * total
    def pick(zs: List[Int], ws: List[Double], rem: Double): Int = (zs, ws) match {
      case (z :: zrest, w :: wrest) => if (rem <= w) z else pick(zrest, wrest, rem - w)
      case _ => zones.last
    }
    pick(zones, weights, r)
  }

  private def updatePositions(state: MatchState, ballZone: Int, possessionHome: Boolean, input: MatchEngineInput): (List[PlayerPosition], List[PlayerPosition]) =
    PositionGenerator.all22Positions(
      state.homeFormation,
      state.homePlayerIds,
      state.awayFormation,
      state.awayPlayerIds,
      ballZone,
      possessionHome,
      input.homePlan.customPositions,
      input.awayPlan.customPositions,
      state.homeWidthScale,
      state.awayWidthScale
    )

  private def computeAnalyticsFromEvents(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId, homeGoals: Int, awayGoals: Int, vaepModel: VAEPModel): MatchAnalytics = {
    def isHome(tid: Option[TeamId]) = tid.contains(homeTeamId)
    def isAway(tid: Option[TeamId]) = tid.contains(awayTeamId)
    var passHome, passAway = 0
    var shotHome, shotAway = 0
    var xgHome, xgAway = 0.0
    val vaepMutable = scala.collection.mutable.Map.empty[PlayerId, Double]
    val vaepByType = scala.collection.mutable.Map.empty[PlayerId, scala.collection.mutable.Map[String, Double]]
    val defensiveActions = scala.collection.mutable.Map.empty[PlayerId, Int].withDefaultValue(0)
    val pressingInOppHalf = scala.collection.mutable.Map.empty[PlayerId, Int].withDefaultValue(0)
    val iwpMutable = scala.collection.mutable.Map.empty[PlayerId, Double].withDefaultValue(0.0)
    val actorZones = scala.collection.mutable.Map.empty[PlayerId, List[Int]]
    val activityByZone = scala.collection.mutable.Map.empty[PlayerId, scala.collection.mutable.Map[Int, Int]]
    def addIwp(pid: PlayerId, v: Double): Unit = iwpMutable(pid) = iwpMutable(pid) + v
    def addActivity(pid: PlayerId, z: Int): Unit = {
      if (z >= 1 && z <= 12) {
        actorZones(pid) = actorZones.getOrElse(pid, Nil) :+ z
        val m = activityByZone.getOrElseUpdate(pid, scala.collection.mutable.Map.empty.withDefaultValue(0))
        m(z) = m(z) + 1
      }
    }
    def addVaep(pid: PlayerId, eventType: String, v: Double): Unit = {
      vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + v
      val m = vaepByType.getOrElseUpdate(pid, scala.collection.mutable.Map.empty.withDefaultValue(0.0))
      m(eventType) = m(eventType) + v
    }
    var scoreH, scoreA = 0
    var contactsAttThirdHome, contactsAttThirdAway = 0
    var passesBuildUpAway, passesBuildUpHome = 0
    var defActionsBuildUpHome, defActionsBuildUpAway = 0
    val passStatsByPlayer = scala.collection.mutable.Map.empty[PlayerId, (Int, Int)]
    events.foreach { e =>
      val zone = e.zone.getOrElse(0)
      if (zone >= 9 && zone <= 12) {
        if (isHome(e.teamId)) contactsAttThirdHome += 1 else if (isAway(e.teamId)) contactsAttThirdAway += 1
      }
      if (zone >= 1 && zone <= 6) {
        if (e.eventType == "Pass" || e.eventType == "LongPass") {
          if (isAway(e.teamId)) passesBuildUpAway += 1 else if (isHome(e.teamId)) passesBuildUpHome += 1
        }
        if (e.eventType == "Tackle" || e.eventType == "PassIntercepted") {
          if (isHome(e.teamId)) defActionsBuildUpHome += 1 else if (isAway(e.teamId)) defActionsBuildUpAway += 1
        }
      }
      val possessionTeamId = e.eventType match {
        case "PassIntercepted" => if (e.teamId.contains(homeTeamId)) Some(awayTeamId) else Some(homeTeamId)
        case _ => e.teamId
      }
      val isPossessionTeam = e.teamId == possessionTeamId
      val vaepCtx = VAEPContext(e.eventType, zone, e.outcome, e.minute, scoreH, scoreA, possessionTeamId, isPossessionTeam)
      val vaepValue = vaepModel.valueForEvent(vaepCtx)
      e.eventType match {
        case "Pass" | "LongPass" =>
          if (isHome(e.teamId)) passHome += 1 else if (isAway(e.teamId)) passAway += 1
          e.actorPlayerId.foreach { pid =>
            val (att, comp) = passStatsByPlayer.getOrElse(pid, (0, 0))
            passStatsByPlayer(pid) = (att + 1, comp + (if (e.outcome.contains("Success")) 1 else 0))
          }
          e.actorPlayerId.foreach { pid => addVaep(pid, e.eventType, vaepValue); addActivity(pid, zone) }
        case "Shot" =>
          if (isHome(e.teamId)) shotHome += 1 else if (isAway(e.teamId)) shotAway += 1
          val xg = e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.2)
          if (isHome(e.teamId)) xgHome += xg else if (isAway(e.teamId)) xgAway += xg
          e.actorPlayerId.foreach { pid => addVaep(pid, "Shot", vaepValue); addActivity(pid, zone) }
        case "Goal" =>
          if (isHome(e.teamId)) { shotHome += 1; xgHome += 0.5; scoreH += 1 } else if (isAway(e.teamId)) { shotAway += 1; xgAway += 0.5; scoreA += 1 }
          e.actorPlayerId.foreach { pid => addVaep(pid, "Goal", vaepValue); addActivity(pid, zone) }
        case "ThrowIn" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "ThrowIn", vaepValue); addActivity(pid, zone) }
        case "Cross" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "Cross", vaepValue); addActivity(pid, zone) }
        case "PassIntercepted" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "PassIntercepted", vaepValue); addIwp(pid, 0.02); defensiveActions(pid) += 1; if (zone >= 7) pressingInOppHalf(pid) += 1; addActivity(pid, zone) }
        case "Tackle" =>
          val iwpT = if (e.outcome.contains("Won")) 0.025 else 0.01
          e.actorPlayerId.foreach { pid => addVaep(pid, "Tackle", vaepValue); addIwp(pid, iwpT); defensiveActions(pid) += 1; if (zone >= 7) pressingInOppHalf(pid) += 1; addActivity(pid, zone) }
        case "Clearance" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "Clearance", vaepValue); addActivity(pid, zone) }
        case "Duel" | "AerialDuel" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, e.eventType, vaepValue); addIwp(pid, 0.012); addActivity(pid, zone) }
        case "Dribble" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "Dribble", vaepValue); addIwp(pid, 0.01); addActivity(pid, zone) }
        case "DribbleLost" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "DribbleLost", vaepValue); addActivity(pid, zone) }
          e.secondaryPlayerId.foreach { pid => addIwp(pid, 0.02); defensiveActions(pid) += 1; if (zone >= 7) pressingInOppHalf(pid) += 1 }
        case _ =>
      }
    }
    val totalAttThird = contactsAttThirdHome + contactsAttThirdAway
    val fieldTilt = if (totalAttThird > 0) Some((contactsAttThirdHome.toDouble / totalAttThird, contactsAttThirdAway.toDouble / totalAttThird)) else None
    val ppdaHome = if (defActionsBuildUpHome > 0) Some((passesBuildUpAway.toDouble / defActionsBuildUpHome).min(25.0)) else None
    val ppdaAway = if (defActionsBuildUpAway > 0) Some((passesBuildUpHome.toDouble / defActionsBuildUpAway).min(25.0)) else None
    val ppda = (ppdaHome, ppdaAway) match {
      case (Some(h), Some(a)) => Some((h, a))
      case (Some(h), None) => Some((h, 0.0))
      case (None, Some(a)) => Some((0.0, a))
      case _ => None
    }
    val receivedByPlayer = computePassesReceivedByPlayer(events, homeTeamId, awayTeamId)
    val (xgChainByPlayer, xgBuildupByPlayer) = computeXgChainAndBuildup(events, homeTeamId, awayTeamId)
    val passingNodeStats = passStatsByPlayer.map { case (pid, (att, comp)) => pid -> PassingNodeStats(att, comp, receivedByPlayer.getOrElse(pid, 0)) }.toMap
    val (betweennessByPlayer, pageRankByPlayer, clusteringByPlayer) = computePassNetworkCentrality(events, homeTeamId, awayTeamId)
    val transitionCounts = AdvancedAnalytics.transitionCountsFromEvents(events)
    val xtValueByZone = AdvancedAnalytics.xTValueIteration(transitionCounts, DxT.baseZoneThreat)
    val obsoByZone = AdvancedAnalytics.obsByZone(DxT.baseZoneThreat)
    val ballTortuosityOpt = AdvancedAnalytics.ballTortuosity(events)
    val metabolicLoad = AdvancedAnalytics.metabolicLoadFromZonePath(events)
    val homeShareByZone = AdvancedAnalytics.zoneDominanceFromEvents(events, homeTeamId, awayTeamId)
    val shotContextByZone = AdvancedAnalytics.shotContextByZoneFromEvents(events)
    val setPieceZoneActivity = AdvancedAnalytics.setPieceZoneActivityFromEvents(events)
    val totalAct = passHome + passAway + shotHome + shotAway
    val (possH, possA) = if (totalAct > 0) (100.0 * (passHome + shotHome) / totalAct, 100.0 * (passAway + shotAway) / totalAct) else (50.0, 50.0)
    val wpaSamples = (0 to 90 by 10).map { min =>
      var wpa = 0.5
      events.filter(_.minute <= min).foreach { e =>
        if (e.eventType == "Goal") {
          if (isHome(e.teamId)) wpa = math.min(1.0, wpa + 0.15)
          else if (isAway(e.teamId)) wpa = math.max(0.0, wpa - 0.15)
        }
      }
      (min, wpa)
    }.toList
    val playerToTeam = scala.collection.mutable.Map.empty[PlayerId, TeamId]
    events.foreach { e => e.teamId.foreach { tid => e.actorPlayerId.foreach { pid => playerToTeam.put(pid, tid) } } }
    val vaepHome = vaepMutable.filter { case (pid, _) => playerToTeam.get(pid).contains(homeTeamId) }.values.sum
    val vaepAway = vaepMutable.filter { case (pid, _) => playerToTeam.get(pid).contains(awayTeamId) }.values.sum
    val wpaFinal = if (wpaSamples.nonEmpty) wpaSamples.last._2 else 0.5
    def zoneDist(z1: Int, z2: Int): Double = {
      val (a, b) = PitchModel.zoneCenters.getOrElse(z1, (52.5, 34.0))
      val (c, d) = PitchModel.zoneCenters.getOrElse(z2, (52.5, 34.0))
      PitchModel.distance(a, b, c, d)
    }
    val estimatedDistanceByPlayer = actorZones.iterator.map { case (pid, zones) =>
      val dist = if (zones.size >= 2) zones.sliding(2).map { w => if (w.size == 2) zoneDist(w.head, w.last) else 0.0 }.sum else 0.0
      pid -> dist
    }.toMap
    val vaepByPlayerByEventTypeMap = vaepByType.iterator.map { case (pid, m) => pid -> m.toMap }.toMap
    val playerActivityByZoneMap = activityByZone.iterator.map { case (pid, m) => pid -> m.toMap }.toMap
    val actorZonesList = actorZones.view.mapValues(_.toList).toMap
    val playerTortuosityByPlayer = AdvancedAnalytics.playerTortuosityFromZoneSequences(actorZonesList)
    val metabolicLoadByPlayer = estimatedDistanceByPlayer.iterator.map { case (pid, dist) =>
      val byZone = activityByZone.get(pid).map(_.toMap).getOrElse(Map.empty[Int, Int])
      val total = byZone.values.sum
      val attacking = (9 to 12).map(z => byZone.getOrElse(z, 0)).sum
      val ratio = if (total > 0) attacking.toDouble / total else 0.0
      pid -> (dist * (1.0 + 0.15 * ratio))
    }.toMap
    val (setPiecePatternW, setPiecePatternH) = AdvancedAnalytics.setPiecePatternsNMF(setPieceZoneActivity)
    val setPieceRoutineCluster = AdvancedAnalytics.setPieceRoutineClusters(setPieceZoneActivity)
    val poissonPrognosisOpt = Some(AdvancedAnalytics.poissonPrognosis(xgHome, xgAway))
    val voronoiCentroidByZone = AdvancedAnalytics.voronoiZoneFromCentroids(events, homeTeamId, awayTeamId)
    val (passValueByPlayer, passValueTotal, passValueUnderPressureTotal, passValueUnderPressureByPlayer) = AdvancedAnalytics.xPassValueFromEvents(events, xtValueByZone, homeTeamId, awayTeamId)
    val influenceScoreByPlayer = playerActivityByZoneMap.iterator.map { case (pid, byZone) =>
      val score = (1 to 12).map(z => byZone.getOrElse(z, 0) * xtValueByZone.getOrElse(z, 0.0)).sum
      pid -> score
    }.toMap
    MatchAnalytics(
      vaepByPlayer = vaepMutable.toMap,
      wpaTimeline = wpaSamples,
      possessionPercent = (possH, possA),
      shotCount = (shotHome, shotAway),
      xgTotal = (xgHome, xgAway),
      vaepTotal = (vaepHome, vaepAway),
      wpaFinal = wpaFinal,
      fieldTilt = fieldTilt,
      ppda = ppda,
      xgChainByPlayer = xgChainByPlayer,
      xgBuildupByPlayer = xgBuildupByPlayer,
      passingNodeStats = passingNodeStats,
      betweennessByPlayer = betweennessByPlayer,
      pageRankByPlayer = pageRankByPlayer,
      clusteringByPlayer = clusteringByPlayer,
      xtValueByZone = xtValueByZone,
      obsoByZone = obsoByZone,
      ballTortuosity = ballTortuosityOpt,
      metabolicLoad = metabolicLoad,
      homeShareByZone = homeShareByZone,
      vaepByPlayerByEventType = vaepByPlayerByEventTypeMap,
      defensiveActionsByPlayer = defensiveActions.toMap,
      estimatedDistanceByPlayer = estimatedDistanceByPlayer,
      playerActivityByZone = playerActivityByZoneMap,
      shotContextByZone = shotContextByZone,
      setPieceZoneActivity = setPieceZoneActivity,
      pressingInOppHalfByPlayer = pressingInOppHalf.toMap,
      playerTortuosityByPlayer = playerTortuosityByPlayer,
      metabolicLoadByPlayer = metabolicLoadByPlayer,
      iwpByPlayer = iwpMutable.toMap,
      setPiecePatternW = setPiecePatternW,
      setPiecePatternH = setPiecePatternH,
      setPieceRoutineCluster = setPieceRoutineCluster,
      poissonPrognosis = poissonPrognosisOpt,
      voronoiCentroidByZone = voronoiCentroidByZone,
      passValueByPlayer = passValueByPlayer,
      passValueTotal = passValueTotal,
      passValueUnderPressureTotal = passValueUnderPressureTotal,
      passValueUnderPressureByPlayer = passValueUnderPressureByPlayer,
      influenceScoreByPlayer = influenceScoreByPlayer
    )
  }

  /** Graf podań: betweenness, PageRank, clustering. */
  private def computePassNetworkCentrality(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId): (Map[PlayerId, Double], Map[PlayerId, Double], Map[PlayerId, Double]) = {
    val edges = scala.collection.mutable.ArrayBuffer.empty[(PlayerId, PlayerId)]
    for (i <- 0 until events.size - 1) {
      val e = events(i)
      if ((e.eventType == "Pass" || e.eventType == "LongPass") && e.outcome.contains("Success") && e.teamId.isDefined && e.actorPlayerId.isDefined) {
        val next = events(i + 1)
        if (next.teamId == e.teamId && next.actorPlayerId.isDefined && (next.eventType == "Pass" || next.eventType == "LongPass" || next.eventType == "Dribble" || next.eventType == "Shot" || next.eventType == "Goal" || next.eventType == "Cross")) {
          val from = e.actorPlayerId.get
          val to = next.actorPlayerId.get
          if (from != to) edges += ((from, to))
        }
      }
    }
    val edgeList = edges.toList
    if (edgeList.isEmpty) return (Map.empty[PlayerId, Double], Map.empty[PlayerId, Double], Map.empty[PlayerId, Double])
    val nodes = edgeList.flatMap(e => List(e._1, e._2)).distinct
    val adj = edgeList.groupBy(_._1).view.mapValues(_.map(_._2).toList).toMap
    val outDegree = edgeList.groupBy(_._1).view.mapValues(_.size).toMap
    val inEdges = edgeList.groupBy(_._2).view.mapValues(_.map(_._1).toList).toMap

    def bfsShortestPaths(s: PlayerId): (Map[PlayerId, Int], Map[PlayerId, List[PlayerId]]) = {
      val dist = scala.collection.mutable.Map(s -> 0)
      val pred = scala.collection.mutable.Map.empty[PlayerId, List[PlayerId]]
      val q = scala.collection.mutable.Queue(s)
      while (q.nonEmpty) {
        val u = q.dequeue()
        val d = dist(u)
        adj.getOrElse(u, Nil).foreach { v =>
          if (!dist.contains(v)) {
            dist(v) = d + 1
            pred(v) = u :: pred.getOrElse(v, Nil)
            q.enqueue(v)
          } else if (dist(v) == d + 1) pred(v) = u :: pred.getOrElse(v, Nil)
        }
      }
      (dist.toMap, pred.toMap)
    }

    val betweenness = scala.collection.mutable.Map.empty[PlayerId, Double]
    nodes.foreach(n => betweenness(n) = 0.0)
    def pathFromPred(s: PlayerId, t: PlayerId, pred: Map[PlayerId, List[PlayerId]]): List[PlayerId] = {
      var path = List(t)
      var cur = t
      while (cur != s && pred.get(cur).exists(_.nonEmpty)) {
        val p = pred(cur).head
        path = p :: path
        cur = p
      }
      if (cur == s) path else Nil
    }
    nodes.foreach { s =>
      val (dist, pred) = bfsShortestPaths(s)
      nodes.filter(_ != s).foreach { t =>
        if (dist.get(t).exists(_ < 1000)) {
          val path = pathFromPred(s, t, pred)
          path.foreach { v => if (v != s && v != t) betweenness(v) = betweenness.getOrElse(v, 0.0) + 1.0 }
        }
      }
    }
    val bNorm = if (betweenness.values.exists(_ > 0)) betweenness.values.max else 1.0
    val betweennessNorm = betweenness.view.mapValues(_ / bNorm).toMap

    var pr = nodes.map(n => n -> (1.0 / nodes.size)).toMap
    val d = 0.85
    for (_ <- 1 to 30) {
      val prNew = nodes.map { v =>
        val inSum = inEdges.getOrElse(v, Nil).map { u => pr.getOrElse(u, 1.0 / nodes.size) / math.max(1, outDegree.getOrElse(u, 1)) }.sum
        v -> ((1.0 - d) / nodes.size + d * inSum)
      }.toMap
      pr = prNew
    }
    val clustering = AdvancedAnalytics.clusteringByNode(edgeList, nodes)
    (betweennessNorm, pr, clustering)
  }

  /** Dla każdego udanego Pass/LongPass odbiorcą jest actor następnego zdarzenia tej samej drużyny (jeśli to zdarzenie posiadania). */
  private def computePassesReceivedByPlayer(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId): Map[PlayerId, Int] = {
    val possessionEventTypes = Set("Pass", "LongPass", "Dribble", "Shot", "Goal", "Cross", "ThrowIn")
    val received = scala.collection.mutable.Map.empty[PlayerId, Int]
    for (i <- 0 until events.size - 1) {
      val e = events(i)
      if ((e.eventType == "Pass" || e.eventType == "LongPass") && e.outcome.contains("Success") && e.teamId.isDefined) {
        val next = events(i + 1)
        if (next.teamId == e.teamId && next.actorPlayerId.isDefined && possessionEventTypes.contains(next.eventType)) {
          next.actorPlayerId.foreach { pid => received(pid) = received.getOrElse(pid, 0) + 1 }
        }
      }
    }
    received.toMap
  }

  /** Dla każdego strzału/bramki cofa się po sekwencji posiadania i przypisuje xG łańcuchowi (wszyscy) i buildup (bez ostatnich 2 kontaktów). */
  private def computeXgChainAndBuildup(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId): (Map[PlayerId, Double], Map[PlayerId, Double]) = {
    def isPossessionEvent(et: String) = et == "Pass" || et == "LongPass" || et == "Dribble" || et == "Cross" || et == "ThrowIn"
    val chainMutable = scala.collection.mutable.Map.empty[PlayerId, Double]
    val buildupMutable = scala.collection.mutable.Map.empty[PlayerId, Double]
    var i = 0
    while (i < events.size) {
      val e = events(i)
      val (shotXg, isGoal) = e.eventType match {
        case "Shot" => (e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.2), false)
        case "Goal" => (0.5, true)
        case _ => (0.0, false)
      }
      if ((e.eventType == "Shot" || e.eventType == "Goal") && shotXg > 0) {
        val shotTeamId = e.teamId
        val chain = scala.collection.mutable.ArrayBuffer.empty[PlayerId]
        var j = i - 1
        var stop = false
        while (j >= 0 && chain.size < 15 && !stop) {
          val prev = events(j)
          if (prev.teamId == shotTeamId && prev.actorPlayerId.isDefined) {
            if (isPossessionEvent(prev.eventType) || prev.eventType == "Shot" || prev.eventType == "Goal") {
              prev.actorPlayerId.foreach { pid => if (!chain.contains(pid)) chain.prepend(pid) }
            }
          } else if (prev.teamId != shotTeamId && prev.teamId.isDefined) stop = true
          j -= 1
        }
        e.actorPlayerId.foreach { pid => if (!chain.contains(pid)) chain.append(pid) }
        chain.foreach { pid => chainMutable(pid) = chainMutable.getOrElse(pid, 0.0) + shotXg }
        chain.dropRight(2).foreach { pid => buildupMutable(pid) = buildupMutable.getOrElse(pid, 0.0) + shotXg }
      }
      i += 1
    }
    (chainMutable.toMap, buildupMutable.toMap)
  }
}
