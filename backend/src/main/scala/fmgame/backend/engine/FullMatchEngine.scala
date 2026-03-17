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
    if (input.homeTeam.players.size < 11) Left("home team must have at least 11 players")
    else if (input.awayTeam.players.size < 11) Left("away team must have at least 11 players")
    else {
    val homeIds = input.homeTeam.lineup.keys.toList
    val awayIds = input.awayTeam.lineup.keys.toList
    val homePlayerIds = input.homeTeam.players.map(_.player.id).toSet
    val awayPlayerIds = input.awayTeam.players.map(_.player.id).toSet
    if (input.homeTeam.lineup.size != 11 || !input.homeTeam.lineup.keySet.forall(homePlayerIds)) Left("home lineup must cover exactly 11 players from squad")
    else if (input.awayTeam.lineup.size != 11 || !input.awayTeam.lineup.keySet.forall(awayPlayerIds)) Left("away lineup must cover exactly 11 players from squad")
    else {
    val rng = input.randomSeed.fold(new scala.util.Random)(new scala.util.Random(_))
    val homeFormation = input.homePlan.formationName
    val awayFormation = input.awayPlan.formationName
    val homeWidthScale = input.homePlan.teamInstructions.flatMap(_.width).fold(1.0)(w => if (w == "narrow") 0.8 else if (w == "wide") 1.2 else 1.0)
    val awayWidthScale = input.awayPlan.teamInstructions.flatMap(_.width).fold(1.0)(w => if (w == "narrow") 0.8 else if (w == "wide") 1.2 else 1.0)
    val paceAccMap = buildPaceAccMap(input)
    val initialState = MatchState.initial(
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
    val homeOutfield = input.homeTeam.players.filter(_.player.preferredPositions != Set("GK"))
    val awayOutfield = input.awayTeam.players.filter(_.player.preferredPositions != Set("GK"))
    val strictness = math.max(0, math.min(1, input.referee.strictness))

    @scala.annotation.tailrec
    def loop(state: MatchState, acc: List[MatchEventRecord]): (MatchState, List[MatchEventRecord]) = {
      if (state.totalSeconds >= 5400) (state, acc)
      else {
        val (event, nextState) = generateNextEvent(state, input, homeOutfield, awayOutfield, strictness, rng, xgModel, vaepModel, paceAccMap)
        loop(nextState, event :: acc)
      }
    }

    val (finalState, reversedEvents) = loop(initialState, kickOff :: Nil)
    val events = reversedEvents.reverse
    val homeGoals = finalState.homeGoals
    val awayGoals = finalState.awayGoals
    val analytics = Some(FullMatchAnalytics.computeAnalyticsFromEvents(events, input.homeTeam.teamId, input.awayTeam.teamId, homeGoals, awayGoals, vaepModel))
    Right(MatchEngineResult(homeGoals, awayGoals, events, analytics))
    }
    }
  }

  private inline def attr(attrs: Map[String, Int], key: String, default: Int = 10): Int =
    math.max(1, math.min(20, attrs.getOrElse(key, default)))

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

  private def actorWeight(p: PlayerMatchInput, zone: Int, lineup: Map[PlayerId, String], plan: GamePlanInput, isHome: Boolean): Double = {
    val slot = lineup.get(p.player.id)
    val role = slot.flatMap(s => plan.slotRoles.flatMap(_.get(s)))
    val inAttack = PitchModel.isAttackingThird(zone, isHome)
    val inBuildUp = PitchModel.isBuildUpZone(zone, isHome)
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

  /** Modyfikator szansy podania przy grze „poza pozycją" (slot nie w preferredPositions). */
  private def positionMatchPassPenalty(slot: Option[String], preferredPositions: Set[String]): Double =
    slot.fold(0.0)(s => if (preferredPositions.contains(s) || preferredPositions.exists(pp => s.startsWith(pp) || pp.startsWith(s))) 0.0 else -0.02)

  private def passDifficulty(zoneFrom: Int, zoneTo: Int, receiverPressure: Int): Double = {
    val pressureNorm = (receiverPressure / 6.0).min(1.0) * EngineConstants.PassDifficultyPressureFactor
    val (x1, y1) = PitchModel.zoneCenters.getOrElse(zoneFrom, (52.5, 34.0))
    val (x2, y2) = PitchModel.zoneCenters.getOrElse(zoneTo, (52.5, 34.0))
    val physDist = PitchModel.distance(x1, y1, x2, y2)
    val normalizedDist = (physDist / PitchModel.PitchLength).min(1.0) * EngineConstants.PassDifficultyDistanceFactor
    (1.0 - pressureNorm - normalizedDist).max(EngineConstants.PassDifficultyMin).min(1.0)
  }

  /** xPass z modelu (DOPRACOWANIA §2.12): wartość strefy docelowej (DxT) skorygowana o presję na odbiorcy. */
  private def xPassFromModel(dxtByZone: Map[Int, Double], zoneFrom: Int, zoneTo: Int, receiverPressure: Int, possessionHome: Boolean = true): Double = {
    val threatTo = dxtByZone.getOrElse(zoneTo, DxT.baseZoneThreat(zoneTo, possessionHome))
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
    allIds.flatMap { id =>
      val (tMult, pMult) = if (state.homePlayerIds.contains(id)) (homeT, homeP) else (awayT, awayP)
      homeMap.get(id).orElse(awayMap.get(id)).map { pmi =>
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
      }
    }.toMap
  }

  private case class EventContext(
    state: MatchState,
    input: MatchEngineInput,
    zone: Int,
    nextMinute: Int,
    nextTotalSeconds: Int,
    deltaMinutes: Double,
    actor: PlayerMatchInput,
    isHome: Boolean,
    possTeamId: TeamId,
    plan: GamePlanInput,
    lineup: Map[PlayerId, String],
    outfield: List[PlayerMatchInput],
    defendersOutfieldFiltered: List[PlayerMatchInput],
    homeOutfield: List[PlayerMatchInput],
    awayOutfield: List[PlayerMatchInput],
    rng: scala.util.Random,
    xgModel: xGModel,
    vaepModel: VAEPModel,
    paceAccMap: Map[PlayerId, (Int, Int)],
    strictness: Double,
    opponentControl: Double,
    interceptBonus: Double,
    matchupPressure: Double,
    actorFatigue: Double,
    fatigueMissBonus: Double,
    slot: Option[String],
    passBonus: Double,
    shotTendencyBonus: Double,
    xgMultiplier: Double,
    strategyPassBonus: Double,
    defendersPlan: GamePlanInput,
    defenderPressBonus: Double,
    oiBonus: Double,
    homeAdvantage: Double,
    interceptHomeBonus: Double,
    dynamicP: Double
  )

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
    val onPitchIds = if (isHome) state.homePlayerIds.toSet else state.awayPlayerIds.toSet
    val defendersOnPitchIds = if (isHome) state.awayPlayerIds.toSet else state.homePlayerIds.toSet
    val outfield = (if (isHome) homeOutfield else awayOutfield).filter(p => onPitchIds.contains(p.player.id))
    val defendersOutfieldFiltered = (if (isHome) awayOutfield else homeOutfield).filter(p => defendersOnPitchIds.contains(p.player.id))
    val plan = if (isHome) input.homePlan else input.awayPlan
    val lineup = if (isHome) input.homeTeam.lineup else input.awayTeam.lineup
    val zone = state.ballZone
    val actor = pickWeighted(outfield, p => actorWeight(p, zone, lineup, plan, isHome), rng)
    val control = state.pitchControlByZone.getOrElse(zone, (0.5, 0.5))
    val opponentControl = if (isHome) control._2 else control._1
    val pressActive = (if (isHome) state.awayTriggerConfig else state.homeTriggerConfig).exists { tc =>
      tc.pressZones.contains(zone)
    }
    val interceptBonus = if (pressActive) 0.15 else 0.0
    val matchupPressure = MatchupMatrix.pressureInZone(state.homePositions, state.awayPositions, zone, isHome) * 0.08
    val actorFatigue = state.fatigueByPlayer.getOrElse(actor.player.id, 0.0)
    val fatigueMissBonus = actorFatigue * 0.06

    var eventTypeRoll = rng.nextDouble()
    if (state.justRecoveredInCounterZone && rng.nextDouble() < 0.35)
      eventTypeRoll = 0.43
    else if (state.lastEventType.contains("Corner") && rng.nextDouble() < 0.35) eventTypeRoll = 0.43
    else if (state.lastEventType.contains("FreeKick") && rng.nextDouble() < 0.22) eventTypeRoll = 0.43
    val slot = lineup.get(actor.player.id)
    val strategyShotBonus = plan.teamInstructions.flatMap(_.tempo).fold(0.0)(t => if (t == "higher" && PitchModel.isOpponentHalf(zone, isHome)) 0.004 else if (t == "lower") -0.002 else 0.0)
    val strategyPassBonus = plan.teamInstructions.flatMap(_.pressingIntensity).fold(0.0)(p => if (p == "lower" && PitchModel.isBuildUpZone(zone, isHome)) 0.012 else 0.0)
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

    val ctx = EventContext(
      state = state, input = input, zone = zone, nextMinute = nextMinute,
      nextTotalSeconds = nextTotalSeconds, deltaMinutes = deltaMinutes, actor = actor,
      isHome = isHome, possTeamId = possTeamId, plan = plan, lineup = lineup,
      outfield = outfield, defendersOutfieldFiltered = defendersOutfieldFiltered,
      homeOutfield = homeOutfield, awayOutfield = awayOutfield, rng = rng,
      xgModel = xgModel, vaepModel = vaepModel, paceAccMap = paceAccMap,
      strictness = strictness, opponentControl = opponentControl,
      interceptBonus = interceptBonus, matchupPressure = matchupPressure,
      actorFatigue = actorFatigue, fatigueMissBonus = fatigueMissBonus, slot = slot,
      passBonus = passBonus, shotTendencyBonus = shotTendencyBonus,
      xgMultiplier = xgMultiplier, strategyPassBonus = strategyPassBonus,
      defendersPlan = defendersPlan, defenderPressBonus = defenderPressBonus,
      oiBonus = oiBonus, homeAdvantage = homeAdvantage,
      interceptHomeBonus = interceptHomeBonus, dynamicP = dynamicP
    )

    if (eventTypeRoll < EngineConstants.EventPassThreshold) generatePassEvent(ctx)
    else if (eventTypeRoll < (EngineConstants.EventShotThresholdBase + shotTendencyBonus)) generateShotEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventFoulPenaltyThreshold) generateFoulEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventClearanceThreshold && PitchModel.isDefensiveThird(zone, isHome)) generateClearanceEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventCornerThreshold) generateCornerEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventThrowInThreshold) generateThrowInEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventCrossThreshold) generateCrossEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventInterceptThreshold) generateInterceptEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventDribbleThreshold) generateDribbleEvent(ctx)
    else if (eventTypeRoll < EngineConstants.EventDuelThreshold) generateDuelEvent(ctx, isAerial = false)
    else if (eventTypeRoll < EngineConstants.EventAerialDuelThreshold) generateDuelEvent(ctx, isAerial = true)
    else if (eventTypeRoll < EngineConstants.EventFreeKickThreshold) generateSetPieceEvent(ctx, isFreeKick = true)
    else if (eventTypeRoll < EngineConstants.EventOffsideThreshold) generateSetPieceEvent(ctx, isFreeKick = false)
    else if (nextMinute >= 60 && eventTypeRoll < EngineConstants.EventSubThreshold && rng.nextDouble() < 0.2) generateSubstitutionEvent(ctx)
    else generateFallbackPassEvent(ctx)
  }

  private def generatePassEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val targetZone = pickTargetZone(state.dxtByZone, zone, isHome, rng)
    val receiverPressure = receiverPressureCount(targetZone, isHome, state)
    val zoneDistance = math.abs(targetZone - zone)
    val passInterceptProb = (EngineConstants.InterceptBase + opponentControl * EngineConstants.InterceptControlFactor + interceptBonus + matchupPressure + oiBonus + defenderPressBonus + interceptHomeBonus + dynamicP * 0.08 + zoneDistance * EngineConstants.InterceptPerZoneDistance).min(EngineConstants.InterceptCap)
    val intercepted = rng.nextDouble() < passInterceptProb
    val decisions = attr(actor.player.mental, "decisions")
    val vision = attr(actor.player.mental, "vision")
    val passing = attr(actor.player.technical, "passing")
    val firstTouch = attr(actor.player.technical, "firstTouch")
    val ballControl = attr(actor.player.technical, "ballControl")
    val technique = attr(actor.player.technical, "technique")
    val leadership = attr(actor.player.mental, "leadership")
    val mentalBonus = (decisions - 10) * 0.004 + (vision - 10) * 0.003 + (passing - 10) * 0.003 + (firstTouch - 10) * 0.002 + (ballControl - 10) * 0.002 + (technique - 10) * 0.005
    val leadershipMod = if (nextMinute > 75) (1.0 + (leadership - 10) * 0.002) else 1.0
    val passingMod = plan.teamInstructions.flatMap(_.passingDirectness).fold(0.0)(p => if (p == "shorter") 0.02 else if (p == "direct") -0.015 else 0.0)
    val posPenalty = positionMatchPassPenalty(slot, actor.player.preferredPositions)
    val concentration = attr(actor.player.mental, "concentration")
    val lateGameConcentration = if (nextMinute > 75) (0.9 + 0.1 * (concentration / 20.0)) else 1.0
    val moraleMod = 0.85 + 0.15 * math.max(0, math.min(1, actor.morale))
    val homePassBonus = if (isHome) (homeAdvantage - 1.0) * 0.02 else 0.0
    val difficultyMult = passDifficulty(zone, targetZone, receiverPressure)
    val passSuccessBase = (EngineConstants.PassSuccessBase - fatigueMissBonus + mentalBonus + passBonus + passingMod + posPenalty + strategyPassBonus + homePassBonus) * moraleMod * lateGameConcentration * leadershipMod * difficultyMult
    val passSuccessBaseClamped = passSuccessBase.max(EngineConstants.PassSuccessMin).min(EngineConstants.PassSuccessMax)
    val outcome = if (intercepted) "Intercepted" else (if (rng.nextDouble() < passSuccessBaseClamped) "Success" else "Missed")
    val (newPossession, newZone) = if (intercepted) {
      (Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), zone)
    } else if (outcome == "Missed") {
      val midZone = ((zone + targetZone) / 2).max(1).min(PitchModel.TotalZones)
      (Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), midZone)
    } else (state.possession, targetZone)
    val eventType = if (rng.nextDouble() < 0.22) "LongPass" else "Pass"
    val dist = 15 + rng.nextInt(40)
    val xPass = xPassFromModel(state.dxtByZone, zone, targetZone, receiverPressure, isHome)
    val threat = state.dxtByZone.getOrElse(targetZone, DxT.baseZoneThreat(targetZone, isHome))
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
    val highIntensity = PitchModel.isHighIntensityZone(zone, isHome) || state.justRecoveredInCounterZone
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
  }

  private def generateShotEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigueShot = updateFatigue(state, deltaMinutes, input)
    if (state.lastEventType.contains("Corner") && state.lastSetPieceDefense.contains("man") && rng.nextDouble() < 0.05) {
      val defendersLineupFoul = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
      val fouler = pickWeighted(defendersOutfieldFiltered, p => interceptorWeight(p, defendersPlan, defendersLineupFoul, input.leagueContext), rng)
      val foulEvent = MatchEventRecord(nextMinute, "Foul", Some(fouler.player.id), Some(actor.player.id), Some(possTeamId), Some(zone), Some("Success"), Map("IWP" -> f"${0.35 + rng.nextDouble() * 0.35}%.2f", "setPiece" -> "corner"))
      (foulEvent, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Foul"), fatigueByPlayer = newFatigueShot, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, lastSetPieceDefense = None))
    } else {
    val isHeader = state.lastEventType.contains("Cross")
    val (homePos, awayPos) = (state.homePositions, state.awayPositions)
    val (attPosList, defPosList, defGoalX, defGkIds) =
      if (isHome)
        (
          homePos,
          awayPos,
          PitchModel.PitchLength,
          input.awayTeam.players.filter(_.player.preferredPositions.contains("GK")).map(_.player.id).toSet
        )
      else
        (
          awayPos,
          homePos,
          0.0,
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
            if (dot > 0.3) {
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

    val distToGoal = shooterPosOpt match {
      case Some(sp) => PitchModel.distance(sp.x, sp.y, defGoalX, goalY)
      case None =>
        val (cx, cy) = PitchModel.zoneCenters.getOrElse(zone, (52.5, 34.0))
        PitchModel.distance(cx, cy, defGoalX, goalY)
    }
    val goalAngle = shooterPosOpt.map { sp =>
      val postY1 = goalY - 3.66
      val postY2 = goalY + 3.66
      val a1 = math.atan2(postY1 - sp.y, defGoalX - sp.x)
      val a2 = math.atan2(postY2 - sp.y, defGoalX - sp.x)
      math.abs(a2 - a1)
    }.getOrElse(0.3)
    val isWeakFoot = if (isHeader) false else {
      val prefFoot = actor.player.bodyParams.getOrElse("preferredFoot", 1.0)
      val isRightFooted = prefFoot >= 0.5
      val row = PitchModel.row(zone)
      val onLeftSide = row < PitchModel.Rows / 2
      if (isRightFooted) onLeftSide else !onLeftSide
    }
    val xgCtx = ShotContext(
        zone,
        distToGoal,
        isHeader = isHeader,
        nextMinute,
        state.scoreDiff,
        pressureCount = pressureCount,
        angularPressure = angularPressure,
        gkDistance = gkDistance,
        goalAngle = goalAngle,
        isWeakFoot = isWeakFoot,
        isHome = isHome
      )
    var xg = xgModel.xGForShot(xgCtx)
    val routineXgMult = state.lastSetPieceRoutine.fold(1.0) {
      case "near_post" => 1.05
      case "far_post"  => 1.02
      case "short"     => 0.95
      case "direct"    => 1.08
      case _           => 1.0
    }
    val setPieceDefenseMult = state.lastSetPieceDefense match {
      case Some("zonal") => 0.85
      case Some("man")   => 0.90
      case _             => 1.0
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
    if (!PitchModel.isAttackingThird(zone, isHome)) attrXgMult *= (0.75 + (longShots / 20.0) * 0.25)
    xg = (xg * xgMultiplier * routineXgMult * setPieceDefenseMult * attrXgMult).min(0.99)
    xg = (xg * input.leagueContext.xgCalibration.getOrElse(1.0)).min(0.99).max(0.001)
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
    if (PitchModel.isPenaltyArea(zone, isHome)) gkSaveMult *= (0.95 + (gkOneOnOnes / 20.0) * 0.1)
    val gkGoalReduction = 2.0 - gkSaveMult
    xg = (xg * gkGoalReduction).min(0.99).max(0.001)
    val isGoal = rng.nextDouble() < xg
    val composureShot = math.max(1, math.min(20, actor.player.mental.getOrElse("composure", 10)))
    val techniqueShot = math.max(1, math.min(20, actor.player.technical.getOrElse("technique", 10)))
    val centerProb = (EngineConstants.ShotPlacementCenterBase + 0.2 * (composureShot + techniqueShot) / 40.0).min(0.7).max(0.25)
    val placementRoll = rng.nextDouble()
    val placement = if (placementRoll < (1.0 - centerProb) * 0.5) "left" else if (placementRoll < centerProb + (1.0 - centerProb) * 0.5) "center" else "right"
    val placementFactor = placement match { case "center" => 0.92; case _ => 1.08 }
    val savedProbBase = (0.35 - actorFatigue * 0.05).max(0.2)
    val savedProb = (savedProbBase * gkSaveMult).min(0.5).max(0.12)
    val blockedProb = (1.0 - EngineConstants.ShotMissedVsBlockedBase) + EngineConstants.ShotBlockedPressureBonus * (pressureCount / 3.0).min(1.0) + 0.1 * (angularPressure / 2.0).min(1.0)
    val outcomeRoll = rng.nextDouble()
    val outcome = if (isGoal) "Goal" else (if (outcomeRoll < savedProb) "Saved" else if (outcomeRoll < savedProb + (1.0 - savedProb) * (1.0 - blockedProb)) "Missed" else "Blocked")
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
    val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = PitchModel.isHighIntensityZone(zone, isHome))
    val (postZone, postPossession) = if (isGoal) {
      val centerZone = PitchModel.zoneFromXY(PitchModel.PitchLength / 2.0, PitchModel.PitchWidth / 2.0)
      val concedingTeamId = if (isHome) input.awayTeam.teamId else input.homeTeam.teamId
      (centerZone, Some(concedingTeamId))
    } else if (outcome == "Saved") {
      val gkZone = if (isHome) {
        (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == PitchModel.Cols - 1).head
      } else {
        (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == 0).head
      }
      (gkZone, Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId))
    } else if (outcome == "Blocked") {
      (zone, Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId))
    } else {
      val gkZone = if (isHome) {
        (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == PitchModel.Cols - 1).head
      } else {
        (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == 0).head
      }
      (gkZone, Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId))
    }
    val postPositions = updatePositions(state, postZone, postPossession.contains(input.homeTeam.teamId), input)
    val postControl = PitchControl.controlByZoneWithFatigue(postPositions._1, postPositions._2, Some(newFatigue), Some(paceAccMap))
    val postDxt = DxT.threatMap(postControl, postPossession.contains(input.homeTeam.teamId))
    val newState = state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, homeGoals = newHomeGoals, awayGoals = newAwayGoals,
      ballZone = postZone, possession = postPossession, homePositions = postPositions._1, awayPositions = postPositions._2,
      pitchControlByZone = postControl, dxtByZone = postDxt,
      lastEventType = Some(event.eventType), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, lastSetPieceDefense = None)
    (event, newState)
    }
  }

  private def generateFoulEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    if (PitchModel.isPenaltyArea(zone, isHome) && rng.nextDouble() < 0.18) {
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
      val prefFoot = takerPmi.player.bodyParams.getOrElse("preferredFoot", 1.0)
      val isRightFooted = prefFoot >= 0.5
      val naturalSideSave = saveProbSame * 0.95
      val crossSideSave = saveProbSame * 1.05
      val payoffLL = if (isRightFooted) 1.0 - crossSideSave else 1.0 - naturalSideSave
      val payoffLR = 1.0 - saveProbDiff
      val payoffRL = 1.0 - saveProbDiff
      val payoffRR = if (isRightFooted) 1.0 - naturalSideSave else 1.0 - crossSideSave
      val (nashShooterL, nashGkL) = AdvancedAnalytics.nashPenalty2x2(payoffLL, payoffLR, payoffRL, payoffRR)
      val shootLeft = rng.nextDouble() < nashShooterL
      val gkDiveLeft = rng.nextDouble() < nashGkL
      val saveProb = if (shootLeft == gkDiveLeft) saveProbSame else saveProbDiff
      val isGoal = rng.nextDouble() >= saveProb
      val outcome = if (isGoal) "Goal" else "Saved"
      val event = MatchEventRecord(nextMinute, if (isGoal) "Goal" else "Penalty", Some(takerId), None, Some(possTeamId), Some(zone), Some(outcome), Map("xG" -> f"$penXg%.3f", "penalty" -> "true"))
      val newHomeGoals = if (isGoal && isHome) state.homeGoals + 1 else state.homeGoals
      val newAwayGoals = if (isGoal && !isHome) state.awayGoals + 1 else state.awayGoals
      val (penZone, penPossession) = if (isGoal) {
        val centerZone = PitchModel.zoneFromXY(PitchModel.PitchLength / 2.0, PitchModel.PitchWidth / 2.0)
        (centerZone, Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId))
      } else {
        val gkZone = if (isHome) {
          (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == PitchModel.Cols - 1).head
        } else {
          (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == 0).head
        }
        (gkZone, Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId))
      }
      val penPositions = updatePositions(state, penZone, penPossession.contains(input.homeTeam.teamId), input)
      val penControl = PitchControl.controlByZoneWithFatigue(penPositions._1, penPositions._2, Some(newFatigue), Some(paceAccMap))
      val penDxt = DxT.threatMap(penControl, penPossession.contains(input.homeTeam.teamId))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, homeGoals = newHomeGoals, awayGoals = newAwayGoals,
        ballZone = penZone, possession = penPossession, homePositions = penPositions._1, awayPositions = penPositions._2,
        pitchControlByZone = penControl, dxtByZone = penDxt,
        lastEventType = Some("Penalty"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else {
      val defTeamId = if (isHome) input.awayTeam.teamId else input.homeTeam.teamId
      val defOutfield = if (isHome) awayOutfield else homeOutfield
      val defPlan = if (isHome) input.awayPlan else input.homePlan
      val defLineup = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
      val fouler = pickWeighted(defOutfield, p => interceptorWeight(p, defPlan, defLineup, input.leagueContext), rng)
      val baseInjuryProb = 0.015
      val injuryProb = baseInjuryProb * injuryProneFactor(actor.player) * acwrFactor(actor)
      val isInjury = rng.nextDouble() < injuryProb
      if (isInjury) {
        val event = MatchEventRecord(nextMinute, "Injury", Some(actor.player.id), Some(fouler.player.id), Some(possTeamId), Some(zone), None, Map("severity" -> "Light", "returnMatchday" -> "2"))
        val injuredId = actor.player.id
        val newSentOff = state.sentOff + injuredId
        val newHomeIds = state.homePlayerIds.filterNot(_ == injuredId)
        val newAwayIds = state.awayPlayerIds.filterNot(_ == injuredId)
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Injury"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, sentOff = newSentOff, homePlayerIds = newHomeIds, awayPlayerIds = newAwayIds))
      } else {
        val yellowProb = (0.12 + strictness * 0.15).min(0.4)
        val redProb = 0.003 + strictness * 0.005
        val rawCard = if (rng.nextDouble() < redProb) "Red"
          else if (rng.nextDouble() < yellowProb) "Yellow"
          else ""
        val isSecondYellow = rawCard == "Yellow" && state.yellowCards.contains(fouler.player.id)
        val effectiveCard = if (isSecondYellow) "Red" else rawCard
        val baseMeta = Map("IWP" -> f"${0.35 + rng.nextDouble() * 0.35}%.2f")
        val cardMeta = if (effectiveCard.nonEmpty) baseMeta + ("card" -> effectiveCard) ++ (if (isSecondYellow) Map("secondYellow" -> "true") else Map.empty) else baseMeta
        val meta = cardMeta
        val event = MatchEventRecord(nextMinute, "Foul", Some(fouler.player.id), Some(actor.player.id), Some(defTeamId), Some(zone), Some("Success"), meta)
        val newYellows = if (rawCard == "Yellow") state.yellowCards + fouler.player.id else state.yellowCards
        val (newSentOff, newHomeIds, newAwayIds) = if (effectiveCard == "Red") {
          val so = state.sentOff + fouler.player.id
          val hIds = state.homePlayerIds.filterNot(_ == fouler.player.id)
          val aIds = state.awayPlayerIds.filterNot(_ == fouler.player.id)
          (so, hIds, aIds)
        } else (state.sentOff, state.homePlayerIds, state.awayPlayerIds)
        (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Foul"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, yellowCards = newYellows, sentOff = newSentOff, homePlayerIds = newHomeIds, awayPlayerIds = newAwayIds))
      }
    }
  }

  private def generateClearanceEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    val gkOpt = if (isHome) input.homeTeam.players.find(_.player.preferredPositions.contains("GK")) else input.awayTeam.players.find(_.player.preferredPositions.contains("GK"))
    val gkCol = if (isHome) 0 else PitchModel.Cols - 1
    val useGkClearance = PitchModel.column(zone) == gkCol && gkOpt.isDefined && rng.nextDouble() < 0.28
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
    val midZones = (1 to PitchModel.TotalZones).filter(z => PitchModel.isMidfield(z)).toArray
    val clearanceTargetZone = if (midZones.nonEmpty) midZones(rng.nextInt(midZones.length)) else zone
    val event = MatchEventRecord(nextMinute, "Clearance", Some(clearer.player.id), None, newPossession, Some(clearanceTargetZone), outcome, meta)
    val clrPositions = updatePositions(state, clearanceTargetZone, newPossession.contains(input.homeTeam.teamId), input)
    val clrControl = PitchControl.controlByZoneWithFatigue(clrPositions._1, clrPositions._2, Some(newFatigue), Some(paceAccMap))
    val clrDxt = DxT.threatMap(clrControl, newPossession.contains(input.homeTeam.teamId))
    val newState = state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = clearanceTargetZone,
      homePositions = clrPositions._1, awayPositions = clrPositions._2,
      pitchControlByZone = clrControl, dxtByZone = clrDxt,
      lastEventType = Some("Clearance"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, possession = newPossession)
    (event, newState)
  }

  private def generateCornerEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    val setPiecesCorner = if (isHome) input.homePlan.setPieces else input.awayPlan.setPieces
    val taker = setPiecesCorner.flatMap(_.cornerTakerPlayerId).getOrElse(actor.player.id)
    val routine = setPiecesCorner.flatMap(_.cornerRoutine).getOrElse("default")
    val defendingPlan = if (isHome) input.awayPlan else input.homePlan
    val setPieceDef = defendingPlan.triggerConfig.flatMap(_.setPieceDefense)
    val event = MatchEventRecord(nextMinute, "Corner", Some(taker), None, Some(possTeamId), None, Some("Success"), Map("routine" -> routine))
    (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Corner"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = Some(routine), lastSetPieceDefense = setPieceDef))
  }

  private def generateThrowInEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    val taker = plan.throwInConfig.flatMap(c => c.defaultTakerPlayerId).flatMap(pidStr => outfield.find(_.player.id.value == pidStr)).getOrElse(actor)
    val throwInZone = 1 + rng.nextInt(PitchModel.TotalZones)
    val event = MatchEventRecord(nextMinute, "ThrowIn", Some(taker.player.id), None, Some(possTeamId), Some(throwInZone), Some("Success"), Map("xPass" -> f"${0.72 + rng.nextDouble() * 0.22}%.2f"))
    (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = throwInZone, lastEventType = Some("ThrowIn"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
  }

  private def generateCrossEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    val crossing = math.max(1, math.min(20, actor.player.technical.getOrElse("crossing", 10)))
    val crossBase = (0.32 + (crossing - 10) * 0.012).max(0.2).min(0.55)
    val success = rng.nextDouble() < (crossBase - actorFatigue * 0.05).max(0.22)
    val attackingZones = (1 to PitchModel.TotalZones).filter(z => PitchModel.isAttackingThird(z, isHome)).toArray
    val crossZone = attackingZones(rng.nextInt(attackingZones.length))
    val defendingGk = if (isHome) input.awayTeam.players.find(_.player.preferredPositions.contains("GK")) else input.homeTeam.players.find(_.player.preferredPositions.contains("GK"))
    val gkCommand = defendingGk.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("gkCommandOfArea", 10)))).getOrElse(10)
    val claimed = success && PitchModel.isPenaltyArea(crossZone, isHome) && rng.nextDouble() < (gkCommand / 20.0) * 0.12
    val outcome = if (claimed) "Claimed" else if (success) "Success" else "Missed"
    val event = MatchEventRecord(nextMinute, "Cross", Some(actor.player.id), None, Some(if (claimed) (if (isHome) input.awayTeam.teamId else input.homeTeam.teamId) else possTeamId), Some(crossZone), Some(outcome), Map("xPass" -> f"${0.3 + rng.nextDouble() * 0.4}%.2f"))
    val (newPossession, newPositions, newControl, newDxt) = if (claimed) {
      val np = updatePositions(state, crossZone, !isHome, input)
      val nc = PitchControl.controlByZoneWithFatigue(np._1, np._2, Some(newFatigue), Some(paceAccMap))
      val nd = DxT.threatMap(nc, !isHome)
      (Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), np, nc, nd)
    } else {
      val np = updatePositions(state, crossZone, isHome, input)
      val nc = PitchControl.controlByZoneWithFatigue(np._1, np._2, Some(newFatigue), Some(paceAccMap))
      val nd = DxT.threatMap(nc, isHome)
      (state.possession, np, nc, nd)
    }
    (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = crossZone, possession = newPossession, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("Cross"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
  }

  private def generateInterceptEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = PitchModel.isHighIntensityZone(zone, isHome) || state.justRecoveredInCounterZone)
    val defendersLineup2 = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
    val interceptor = pickWeighted(defendersOutfieldFiltered, p => interceptorWeight(p, defendersPlan, defendersLineup2, input.leagueContext), rng)
    val isTackle = rng.nextDouble() < 0.3
    val (evType, evOutcome) = if (isTackle) ("Tackle", Some("Won")) else ("PassIntercepted", None: Option[String])
    val event = MatchEventRecord(nextMinute, evType, Some(interceptor.player.id), Some(actor.player.id), Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId), Some(zone), evOutcome, Map("zone" -> zone.toString))
    val newPossession = Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId)
    val newPositions = updatePositions(state, zone, !isHome, input)
    val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
    val newDxt = DxT.threatMap(newControl, !isHome)
    val recoveryInCounterZone = (if (newPossession.contains(input.homeTeam.teamId)) state.homeTriggerConfig else state.awayTriggerConfig).flatMap(_.counterTriggerZone).contains(zone)
    (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, possession = newPossession, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some(event.eventType), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = recoveryInCounterZone, lastSetPieceRoutine = None))
  }

  private def generateDribbleEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = PitchModel.isHighIntensityZone(zone, isHome))
    val dribbling = math.max(1, math.min(20, actor.player.technical.getOrElse("dribbling", 10)))
    val agility = math.max(1, math.min(20, actor.player.physical.getOrElse("agility", 10)))
    val flair = math.max(1, math.min(20, actor.player.mental.getOrElse("flair", 10)))
    val defendersLineupD = if (isHome) input.awayTeam.lineup else input.homeTeam.lineup
    val (ballCx, ballCy) = PitchModel.zoneCenters.getOrElse(zone, (52.5, 34.0))
    val defenderPositionsD = if (isHome) state.awayPositions else state.homePositions
    val nearestDef = defenderPositionsD.minByOption(p => PitchModel.distance(p.x, p.y, ballCx, ballCy))
    val (avgDefTackling, avgDefAgility) = if (defendersOutfieldFiltered.isEmpty) (10.0, 10.0) else {
      val avgT = defendersOutfieldFiltered.map(p => math.max(1, math.min(20, p.player.technical.getOrElse("tackling", 10)))).sum.toDouble / defendersOutfieldFiltered.size
      val avgA = defendersOutfieldFiltered.map(p => math.max(1, math.min(20, p.player.physical.getOrElse("agility", 10)))).sum.toDouble / defendersOutfieldFiltered.size
      val (nearT, nearA) = nearestDef.flatMap { np =>
        defendersOutfieldFiltered.find(_.player.id == np.playerId).map { p =>
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
      val advancedZone = {
        val currentCol = PitchModel.column(zone)
        val targetCol = if (isHome) math.min(currentCol + 1, PitchModel.Cols - 1) else math.max(currentCol - 1, 0)
        val candidates = (1 to PitchModel.TotalZones).filter(z => PitchModel.column(z) == targetCol)
        if (candidates.nonEmpty) candidates.minBy(z => math.abs(PitchModel.row(z) - PitchModel.row(zone))) else zone
      }
      val event = MatchEventRecord(nextMinute, "Dribble", Some(actor.player.id), None, Some(possTeamId), Some(advancedZone), Some("Success"), Map("zone" -> advancedZone.toString))
      val dribPos = updatePositions(state, advancedZone, isHome, input)
      val dribControl = PitchControl.controlByZoneWithFatigue(dribPos._1, dribPos._2, Some(newFatigue), Some(paceAccMap))
      val dribDxt = DxT.threatMap(dribControl, isHome)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = advancedZone, homePositions = dribPos._1, awayPositions = dribPos._2, pitchControlByZone = dribControl, dxtByZone = dribDxt, lastEventType = Some("Dribble"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else {
      val winner = pickWeighted(defendersOutfieldFiltered, p => interceptorWeight(p, defendersPlan, defendersLineupD, input.leagueContext), rng)
      val newPossession = Some(if (isHome) input.awayTeam.teamId else input.homeTeam.teamId)
      val event = MatchEventRecord(nextMinute, "DribbleLost", Some(actor.player.id), Some(winner.player.id), newPossession, Some(zone), None, Map.empty)
      val newPositions = updatePositions(state, zone, !isHome, input)
      val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
      val newDxt = DxT.threatMap(newControl, !isHome)
      val recoveryInCounterZone = (if (newPossession.contains(input.homeTeam.teamId)) state.homeTriggerConfig else state.awayTriggerConfig).flatMap(_.counterTriggerZone).contains(zone)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, possession = newPossession, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("DribbleLost"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = recoveryInCounterZone, lastSetPieceRoutine = None))
    }
  }

  private def generateDuelEvent(ctx: EventContext, isAerial: Boolean): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    if (isAerial) {
      def aerialWeight(p: PlayerMatchInput): Double = math.max(0.5, (p.player.physical.getOrElse("jumpingReach", 10) + p.player.physical.getOrElse("strength", 10)) / 20.0)
      val all20Aerial = outfield ++ defendersOutfieldFiltered
      val winnerAerial = pickWeighted(all20Aerial, aerialWeight, rng)
      val winnerTidAerial = if (state.homePlayerIds.contains(winnerAerial.player.id)) input.homeTeam.teamId else input.awayTeam.teamId
      val event = MatchEventRecord(nextMinute, "AerialDuel", Some(winnerAerial.player.id), None, Some(winnerTidAerial), Some(zone), Some("Won"), Map("zone" -> zone.toString))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("AerialDuel"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, possession = Some(winnerTidAerial)))
    } else {
      def duelWeight(p: PlayerMatchInput): Double = {
        val strength = math.max(1, math.min(20, p.player.physical.getOrElse("strength", 10)))
        val balance = math.max(1, math.min(20, p.player.physical.getOrElse("balance", 10)))
        val base = math.max(0.5, strength / 10.0 + balance / 40.0)
        val slot = if (homeOutfield.exists(_.player.id == p.player.id)) input.homeTeam.lineup.get(p.player.id) else input.awayTeam.lineup.get(p.player.id)
        val zBonus = 1.0 + 0.05 * zScoreForSlot(slot, "strength", strength, input.leagueContext)
        base * zBonus
      }
      val all20 = outfield ++ defendersOutfieldFiltered
      val winner = pickWeighted(all20, duelWeight, rng)
      val winnerTid = if (state.homePlayerIds.contains(winner.player.id)) input.homeTeam.teamId else input.awayTeam.teamId
      val event = MatchEventRecord(nextMinute, "Duel", Some(winner.player.id), None, Some(winnerTid), Some(zone), Some("Won"), Map("zone" -> zone.toString))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Duel"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None, possession = Some(winnerTid)))
    }
  }

  private def generateSetPieceEvent(ctx: EventContext, isFreeKick: Boolean): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    if (isFreeKick) {
      val setPiecesFk = if (isHome) input.homePlan.setPieces else input.awayPlan.setPieces
      val taker = setPiecesFk.flatMap(_.freeKickTakerPlayerId).getOrElse(actor.player.id)
      val routine = setPiecesFk.flatMap(_.freeKickRoutine).getOrElse("default")
      val midAttackZones = (1 to PitchModel.TotalZones).filter(z =>
        if (isHome) PitchModel.column(z) >= 2 else PitchModel.column(z) <= 3
      ).toArray
      val fkZone = midAttackZones(rng.nextInt(midAttackZones.length))
      val event = MatchEventRecord(nextMinute, "FreeKick", Some(taker), None, Some(possTeamId), Some(fkZone), Some("Success"), Map("routine" -> routine))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = fkZone, lastEventType = Some("FreeKick"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = Some(routine)))
    } else {
      val defTeamId = if (isHome) input.awayTeam.teamId else input.homeTeam.teamId
      val event = MatchEventRecord(nextMinute, "Offside", Some(actor.player.id), None, Some(defTeamId), Some(zone), None, Map.empty)
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, possession = Some(defTeamId), lastEventType = Some("Offside"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    }
  }

  private def generateSubstitutionEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input)
    val subsUsed = if (isHome) state.homeSubsUsed else state.awaySubsUsed
    val squadOutfield = if (isHome) homeOutfield else awayOutfield
    val onPitchOutfield = if (isHome) state.homePlayerIds.filter(id => squadOutfield.exists(_.player.id == id)) else state.awayPlayerIds.filter(id => squadOutfield.exists(_.player.id == id))
    val bench = squadOutfield.filter(p => !(if (isHome) state.homePlayerIds else state.awayPlayerIds).contains(p.player.id))
    def isAttacker(p: PlayerMatchInput): Boolean =
      p.player.preferredPositions.exists(pp => Set("ST", "LW", "RW", "CF", "LS", "RS").contains(pp) || pp.startsWith("ST") || pp.startsWith("LW") || pp.startsWith("RW"))
    def isDefender(p: PlayerMatchInput): Boolean =
      p.player.preferredPositions.exists(pp => Set("CB", "LB", "RB", "LCB", "RCB", "LWB", "RWB").contains(pp) || pp.startsWith("CB") || pp.startsWith("LB") || pp.startsWith("RB"))
    val scoreDiff = if (isHome) state.scoreDiff else -state.scoreDiff
    val preferAttacking = scoreDiff <= -2 && nextMinute >= 60
    val preferDefensive = scoreDiff >= 2 && nextMinute >= 75
    if (subsUsed < EngineConstants.MaxSubstitutions && onPitchOutfield.nonEmpty && bench.nonEmpty) {
      val outPlayerId = onPitchOutfield.maxBy(id => state.fatigueByPlayer.getOrElse(id, 0.0))
      val outPlayer = squadOutfield.find(_.player.id == outPlayerId).getOrElse(outfield.head)
      val inPlayer = if (preferAttacking) {
        val attackers = bench.filter(isAttacker)
        if (attackers.nonEmpty) pickWeighted(attackers, _ => 1.0, rng) else pickWeighted(bench, _ => 1.0, rng)
      } else if (preferDefensive) {
        val defenders = bench.filter(isDefender)
        if (defenders.nonEmpty) pickWeighted(defenders, _ => 1.0, rng) else pickWeighted(bench, _ => 1.0, rng)
      } else {
        pickWeighted(bench, _ => 1.0, rng)
      }
      val newPlayerIds = if (isHome)
        state.homePlayerIds.map(id => if (id == outPlayerId) inPlayer.player.id else id)
      else
        state.awayPlayerIds.map(id => if (id == outPlayerId) inPlayer.player.id else id)
      val newFatigueWithSub = newFatigue.updated(inPlayer.player.id, 0.0)
      val stateWithNewIds = state.copy(
        homePlayerIds = if (isHome) newPlayerIds else state.homePlayerIds,
        awayPlayerIds = if (isHome) state.awayPlayerIds else newPlayerIds,
        homeSubsUsed = if (isHome) state.homeSubsUsed + 1 else state.homeSubsUsed,
        awaySubsUsed = if (isHome) state.awaySubsUsed else state.awaySubsUsed + 1
      )
      val newPositions = updatePositions(stateWithNewIds, zone, isHome, input)
      val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigueWithSub), Some(paceAccMap))
      val newDxt = DxT.threatMap(newControl, isHome)
      val event = MatchEventRecord(nextMinute, "Substitution", Some(inPlayer.player.id), Some(outPlayerId), Some(possTeamId), None, Some("Success"), Map("minute" -> nextMinute.toString))
      (event, stateWithNewIds.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("Substitution"), fatigueByPlayer = newFatigueWithSub, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    } else {
      val pressure = receiverPressureCount(zone, isHome, state)
      val event = MatchEventRecord(nextMinute, "Pass", Some(actor.player.id), None, Some(possTeamId), Some(zone), Some("Success"), Map("zone" -> zone.toString, "xPass" -> "0.85", "receiverPressure" -> pressure.toString))
      (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, lastEventType = Some("Pass"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
    }
  }

  private def generateFallbackPassEvent(ctx: EventContext): (MatchEventRecord, MatchState) = {
    import ctx.*
    val newFatigue = updateFatigue(state, deltaMinutes, input, isHighIntensity = false)
    val targetZone = pickTargetZone(state.dxtByZone, zone, isHome, rng)
    val pressure = receiverPressureCount(targetZone, isHome, state)
    val xPass = xPassFromModel(state.dxtByZone, zone, targetZone, pressure, isHome)
    val event = MatchEventRecord(nextMinute, "Pass", Some(actor.player.id), None, Some(possTeamId), Some(targetZone), Some("Success"), Map("zone" -> targetZone.toString, "xPass" -> f"$xPass%.2f", "zoneThreat" -> f"${state.dxtByZone.getOrElse(targetZone, 0.1)}%.3f", "receiverPressure" -> pressure.toString))
    val newPositions = updatePositions(state, targetZone, isHome, input)
    val newControl = PitchControl.controlByZoneWithFatigue(newPositions._1, newPositions._2, Some(newFatigue), Some(paceAccMap))
    val newDxt = DxT.threatMap(newControl, isHome)
    (event, state.copy(minute = nextMinute, totalSeconds = nextTotalSeconds, ballZone = targetZone, homePositions = newPositions._1, awayPositions = newPositions._2, pitchControlByZone = newControl, dxtByZone = newDxt, lastEventType = Some("Pass"), fatigueByPlayer = newFatigue, justRecoveredInCounterZone = false, lastSetPieceRoutine = None))
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
    val currentCol = PitchModel.column(currentZone)
    val allZones = (1 to PitchModel.TotalZones).toList
    val isBackward = rng.nextDouble() < 0.30
    val zones = if (isBackward) {
      val back = allZones.filter { z =>
        val col = PitchModel.column(z)
        if (homeAttackingRight) col < currentCol else col > currentCol
      }
      if (back.nonEmpty) back else allZones
    } else {
      val forward = allZones.filter { z =>
        val col = PitchModel.column(z)
        if (homeAttackingRight) col >= currentCol else col <= currentCol
      }
      if (forward.nonEmpty) forward else allZones
    }
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

}
