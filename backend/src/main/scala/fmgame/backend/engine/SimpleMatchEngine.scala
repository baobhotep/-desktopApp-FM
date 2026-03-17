package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*

/** Kontrakt silnika meczu: walidacja składu, symulacja wyniku i zdarzeń, analityka (KONTRAKTY §1, SILNIK §8). */
trait MatchEngine {
  def simulate(input: MatchEngineInput): ZIO[Any, MatchEngineError, MatchEngineResult]
}

/**
 * Jedyna zaimplementowana wersja silnika meczu.
 * Nazwa „Simple” = uproszczony model względem pełnego planu (SILNIK): zamiast maszyny stanów zdarzenie-po-zdarzeniu z Pitch Control / DxT
 * używamy rozkładu Poissona na bramki + generowane zdarzenia (Pass, Shot, Foul, …). Wszystkie typy zdarzeń z KONTRAKTY §2.1 są emitowane.
 * MODELE §7: effective composure/decisions z morale.
 */
object SimpleMatchEngine extends MatchEngine {

  /** MODELE §7: effective_composure = base * (0.85 + 0.15 * morale); scale 1–20 → 0–1. */
  private def effectiveComposure(p: PlayerMatchInput): Double = {
    val base = p.player.mental.getOrElse("composure", 10) / 20.0
    (0.85 + 0.15 * math.max(0, math.min(1, p.morale))) * base
  }

  /** MODELE §7: effective_decisions = base * (0.85 + 0.15 * morale). */
  private def effectiveDecisions(p: PlayerMatchInput): Double = {
    val base = p.player.mental.getOrElse("decisions", 10) / 20.0
    (0.85 + 0.15 * math.max(0, math.min(1, p.morale))) * base
  }

  /** Injury prone trait (1–20) increases injury probability. WYMAGANIA §4.1. */
  private def injuryProneFactor(p: fmgame.backend.domain.Player): Double =
    0.7 + 0.3 * (p.traits.getOrElse("injuryProne", 5) / 20.0)

  /** ACWR: wyższe obciążenie (minuty w ostatnich meczach) zwiększa ryzyko kontuzji. 270 min = 3 pełne mecze. */
  private def acwrFactor(p: PlayerMatchInput): Double =
    p.recentMinutesPlayed.fold(1.0)(m => 1.0 + 0.4 * math.min(1.0, m / 270.0))

  def simulate(input: MatchEngineInput): ZIO[Any, MatchEngineError, MatchEngineResult] =
    ZIO.blocking(ZIO.attempt(buildResult(input))).mapError(EngineFault.apply).flatMap {
      case Left(msg) => ZIO.fail(InvalidLineup(msg))
      case Right(r)  => ZIO.succeed(r)
    }

  private def buildResult(input: MatchEngineInput): Either[String, MatchEngineResult] = {
    if (input.homeTeam.players.size < 11) Left("home team must have at least 11 players")
    else if (input.awayTeam.players.size < 11) Left("away team must have at least 11 players")
    else {
    val homeIds = input.homeTeam.players.map(_.player.id).toSet
    val awayIds = input.awayTeam.players.map(_.player.id).toSet
    if (input.homeTeam.lineup.size != 11 || !input.homeTeam.lineup.keySet.forall(homeIds)) Left("home lineup must cover exactly 11 players")
    else if (input.awayTeam.lineup.size != 11 || !input.awayTeam.lineup.keySet.forall(awayIds)) Left("away lineup must cover exactly 11 players")
    else {
    val ha = math.max(1.0, math.min(1.2, input.homeAdvantage))
    val rng = input.randomSeed.fold(new scala.util.Random)(new scala.util.Random(_))
    val homeMorale = input.homeTeam.players.map(_.morale).sum / 11.0
    val awayMorale = input.awayTeam.players.map(_.morale).sum / 11.0
    val moraleMod = (m: Double) => 0.85 + 0.15 * math.max(0, math.min(1, m))
    val lambdaHome = 1.2 * ha * moraleMod(homeMorale)
    val lambdaAway = 1.2 / ha * moraleMod(awayMorale)
    val homeGoals = poisson(lambdaHome, rng)
    val awayGoals = poisson(lambdaAway, rng)
    val strictness = math.max(0, math.min(1, input.referee.strictness))
    val events = buildEvents(input, homeGoals, awayGoals, strictness, rng)
    val analytics = Some(computeAnalyticsFromEvents(events, input.homeTeam.teamId, input.awayTeam.teamId, homeGoals, awayGoals))
    Right(MatchEngineResult(homeGoals, awayGoals, events, analytics))
    }
    }
  }

  /** VAEP/WPA według ALGORYTMY_ANALITYKI §2.3: V(a)=ΔP_scores−ΔP_concedes. Uproszczone wartości per typ akcji. */
  private def computeAnalyticsFromEvents(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId, homeGoals: Int, awayGoals: Int): MatchAnalytics = {
    def isHome(tid: Option[TeamId]) = tid.contains(homeTeamId)
    def isAway(tid: Option[TeamId]) = tid.contains(awayTeamId)
    var passHome, passAway = 0
    var shotHome, shotAway = 0
    var xgHome, xgAway = 0.0
    val vaepMutable = scala.collection.mutable.Map.empty[PlayerId, Double]
    events.foreach { e =>
      e.eventType match {
        case "Pass" | "LongPass" =>
          if (isHome(e.teamId)) passHome += 1 else if (isAway(e.teamId)) passAway += 1
          val success = e.outcome.contains("Success")
          val vaepPass = if (success) 0.02 else -0.03
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + vaepPass }
        case "Shot" =>
          if (isHome(e.teamId)) shotHome += 1 else if (isAway(e.teamId)) shotAway += 1
          val xg = e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.2)
          if (isHome(e.teamId)) xgHome += xg else if (isAway(e.teamId)) xgAway += xg
          val vaepShot = e.outcome match {
            case Some("Saved") => 0.05
            case Some("Missed") => -0.02
            case Some("Blocked") => -0.01
            case _ => 0.0
          }
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + vaepShot }
        case "Goal" =>
          if (isHome(e.teamId)) { shotHome += 1; xgHome += 0.5 } else if (isAway(e.teamId)) { shotAway += 1; xgAway += 0.5 }
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + 0.25 }
        case "ThrowIn" =>
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + 0.01 }
        case "Cross" =>
          val success = e.outcome.contains("Success")
          val v = if (success) 0.015 else -0.02
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + v }
        case "PassIntercepted" =>
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + 0.02 }
        case "Dribble" =>
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) + 0.01 }
        case "DribbleLost" =>
          e.actorPlayerId.foreach { pid => vaepMutable(pid) = vaepMutable.getOrElse(pid, 0.0) - 0.02 }
        case _ =>
      }
    }
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
    MatchAnalytics(
      vaepByPlayer = vaepMutable.toMap,
      wpaTimeline = wpaSamples,
      possessionPercent = (possH, possA),
      shotCount = (shotHome, shotAway),
      xgTotal = (xgHome, xgAway),
      vaepTotal = (vaepHome, vaepAway),
      wpaFinal = wpaFinal
    )
  }

  private def poisson(lambda: Double, rng: scala.util.Random): Int = {
    if (lambda <= 0.0) return 0
    val l = math.exp(-lambda)
    var k = 0
    var p = 1.0
    while (true) {
      if (p <= l) return math.max(0, k - 1)
      k += 1
      p *= rng.nextDouble()
    }
    0
  }

  /** Wartość zagrożenia strefy (1–12) w stylu DxT: wyższa strefa = bliżej bramki = wyższe zoneThreat. */
  private def zoneThreat(zone: Int, isHome: Boolean): Double = DxT.baseZoneThreat(math.max(1, math.min(PitchModel.TotalZones, zone)), isHome)

  private def buildEvents(input: MatchEngineInput, homeGoals: Int, awayGoals: Int, strictness: Double, rng: scala.util.Random): List[MatchEventRecord] = {
    val kickOff = MatchEventRecord(0, "KickOff", None, None, Some(input.homeTeam.teamId), None, Some("Success"), Map.empty)
    val homeOutfield = input.homeTeam.players.filter(_.player.preferredPositions != Set("GK"))
    val awayOutfield = input.awayTeam.players.filter(_.player.preferredPositions != Set("GK"))
    val homeScorers = homeOutfield.take(homeGoals).map(_.player.id)
    val awayScorers = awayOutfield.take(awayGoals).map(_.player.id)
    val homeMinutes = (1 to homeGoals).map(_ => 10 + rng.nextInt(80)).sorted
    val awayMinutes = (1 to awayGoals).map(_ => 10 + rng.nextInt(80)).sorted
    val homeGoalEvents = homeScorers.zip(homeMinutes).map { case (pid, min) =>
      MatchEventRecord(min, "Goal", Some(pid), None, Some(input.homeTeam.teamId), None, Some("Success"), Map("xG" -> "0.5"))
    }
    val awayGoalEvents = awayScorers.zip(awayMinutes).map { case (pid, min) =>
      MatchEventRecord(min, "Goal", Some(pid), None, Some(input.awayTeam.teamId), None, Some("Success"), Map("xG" -> "0.5"))
    }
    val numPassHome = 5 + rng.nextInt(11)
    val numPassAway = 5 + rng.nextInt(11)
    val passCandidatesHome = (1 to numPassHome).map(_ => (10 + rng.nextInt(80), input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size))))
    val passCandidatesAway = (1 to numPassAway).map(_ => (10 + rng.nextInt(80), input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size))))
    val passRecords = (passCandidatesHome ++ passCandidatesAway).map { case (min, tid, pmi) =>
      val zone = 1 + rng.nextInt(PitchModel.TotalZones)
      val xPass = 0.70 + 0.25 * rng.nextDouble()
      val fatigueFail = min >= 70 && rng.nextDouble() < 0.05
      val outcome = if (fatigueFail) "Missed" else "Success"
      val threat = zoneThreat(zone, tid == input.homeTeam.teamId)
      val isLongPass = rng.nextDouble() < 0.25
      val eventType = if (isLongPass) "LongPass" else "Pass"
      val meta = Map("xPass" -> f"$xPass%.2f", "zone" -> zone.toString, "zoneThreat" -> f"$threat%.3f") ++ (if (isLongPass) Map("distance" -> f"${25 + rng.nextInt(35)}") else Map.empty)
      MatchEventRecord(min, eventType, Some(pmi.player.id), None, Some(tid), Some(zone), Some(outcome), meta)
    }
    val numShotsHome = 2 + rng.nextInt(4)
    val numShotsAway = 2 + rng.nextInt(4)
    val shotCandidatesHome = (1 to numShotsHome).map(_ => (10 + rng.nextInt(80), input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size))))
    val shotCandidatesAway = (1 to numShotsAway).map(_ => (10 + rng.nextInt(80), input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size))))
    val shotRecords = (shotCandidatesHome ++ shotCandidatesAway).map { case (min, tid, pmi) =>
      val comp = effectiveComposure(pmi)
      val roll = rng.nextDouble()
      val outcome = if (roll < 0.15 + comp * 0.2) "Saved" else if (roll < 0.45 + comp * 0.15) "Missed" else "Blocked"
      val zone = 1 + rng.nextInt(PitchModel.TotalZones)
      val threat = zoneThreat(zone, tid == input.homeTeam.teamId)
      MatchEventRecord(min, "Shot", Some(pmi.player.id), None, Some(tid), Some(zone), Some(outcome), Map("xG" -> "0.2", "zone" -> zone.toString, "zoneThreat" -> f"$threat%.3f"))
    }
    val foulCount = 1 + (strictness * 4).toInt.min(4)
    val foulCandidates = (0 until foulCount).map { _ =>
      val (tid, pmi) = if (rng.nextBoolean()) (input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size))) else (input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size)))
      (15 + rng.nextInt(70), tid, pmi)
    }
    val foulRecords = foulCandidates.map { case (min, tid, pmi) =>
      val iwp = 0.35 + 0.35 * rng.nextDouble()
      MatchEventRecord(min, "Foul", Some(pmi.player.id), None, Some(tid), Some(1 + rng.nextInt(PitchModel.TotalZones)), Some("Success"), Map("IWP" -> f"$iwp%.2f"))
    }
    val cardRiskPerFoul = foulCandidates.map { case (_, _, pmi) =>
      (1.0 - effectiveDecisions(pmi)) * strictness
    }
    val yellowCardCandidates = foulCandidates.zip(cardRiskPerFoul).flatMap { case ((min, tid, pmi), risk) =>
      if (rng.nextDouble() < risk * 0.4) Some((min + rng.nextInt(5).min(88 - min), tid, pmi)) else None
    }
    val yellowedPlayers = scala.collection.mutable.Set.empty[PlayerId]
    val yellowRecords = yellowCardCandidates.flatMap { case (min, tid, pmi) =>
      if (yellowedPlayers.contains(pmi.player.id)) {
        yellowedPlayers.remove(pmi.player.id)
        Some(MatchEventRecord(min, "RedCard", Some(pmi.player.id), None, Some(tid), None, None, Map("reason" -> "SecondYellow")))
      } else {
        yellowedPlayers.add(pmi.player.id)
        Some(MatchEventRecord(min, "YellowCard", Some(pmi.player.id), None, Some(tid), None, None, Map.empty))
      }
    }
    val directRedProb = 0.02 * strictness
    val redFromFoul = foulCandidates.flatMap { case (min, tid, pmi) =>
      if (rng.nextDouble() < directRedProb) Some(MatchEventRecord(min + 1, "RedCard", Some(pmi.player.id), None, Some(tid), None, None, Map("reason" -> "Direct")))
      else None
    }.take(1).toList
    val cornerCount = 1 + rng.nextInt(3)
    val cornerEvents = (0 until cornerCount).map { _ =>
      val tid = if (rng.nextBoolean()) input.homeTeam.teamId else input.awayTeam.teamId
      val outfield = if (tid == input.homeTeam.teamId) homeOutfield else awayOutfield
      (25 + rng.nextInt(55), tid, outfield(rng.nextInt(outfield.size)).player.id)
    }
    val cornerRecords = cornerEvents.map { case (min, tid, pid) =>
      MatchEventRecord(min, "Corner", Some(pid), None, Some(tid), None, Some("Success"), Map.empty)
    }
    val baseInjuryProb = 0.10
    val injuryEvent = if (foulCount >= 2 && foulCandidates.nonEmpty) {
      val (min, tid, pmi) = foulCandidates(rng.nextInt(foulCandidates.size))
      val factor = injuryProneFactor(pmi.player) * acwrFactor(pmi)
      if (rng.nextDouble() < baseInjuryProb * factor)
        Some(MatchEventRecord(40 + rng.nextInt(40), "Injury", Some(pmi.player.id), None, Some(tid), None, None, Map("returnMatchday" -> "2", "severity" -> "Light")))
      else None
    } else None

    val throwInCount = 2 + rng.nextInt(3)
    def throwInTaker(teamId: TeamId, plan: GamePlanInput, outfield: List[PlayerMatchInput]): PlayerMatchInput =
      if (teamId == input.homeTeam.teamId)
        plan.throwInConfig.flatMap(c => c.defaultTakerPlayerId).flatMap(pid => outfield.find(_.player.id.value == pid)).getOrElse(outfield(rng.nextInt(outfield.size)))
      else
        input.awayPlan.throwInConfig.flatMap(c => c.defaultTakerPlayerId).flatMap(pid => awayOutfield.find(_.player.id.value == pid)).getOrElse(awayOutfield(rng.nextInt(awayOutfield.size)))
    val throwInEvents = (0 until throwInCount).map { _ =>
      val min = 12 + rng.nextInt(66)
      val (tid, outfield) = if (rng.nextBoolean()) (input.homeTeam.teamId, homeOutfield) else (input.awayTeam.teamId, awayOutfield)
      val taker = if (tid == input.homeTeam.teamId) throwInTaker(tid, input.homePlan, homeOutfield) else throwInTaker(tid, input.awayPlan, awayOutfield)
      MatchEventRecord(min, "ThrowIn", Some(taker.player.id), None, Some(tid), Some(1 + rng.nextInt(PitchModel.TotalZones)), Some("Success"), Map("xPass" -> f"${0.72 + rng.nextDouble() * 0.22}%.2f"))
    }

    val crossCount = 2 + rng.nextInt(3)
    val crossEvents = (0 until crossCount).map { _ =>
      val min = 20 + rng.nextInt(55)
      val tid = if (rng.nextBoolean()) input.homeTeam.teamId else input.awayTeam.teamId
      val outfield = if (tid == input.homeTeam.teamId) homeOutfield else awayOutfield
      val taker = outfield(rng.nextInt(outfield.size))
      val success = rng.nextDouble() < 0.35
      val isHome = tid == input.homeTeam.teamId
      val attackZones = (1 to PitchModel.TotalZones).filter(z => PitchModel.isAttackingThird(z, isHome)).toArray
      MatchEventRecord(min, "Cross", Some(taker.player.id), None, Some(tid), Some(attackZones(rng.nextInt(attackZones.length))), Some(if (success) "Success" else "Missed"), Map("xPass" -> f"${0.3 + rng.nextDouble() * 0.4}%.2f"))
    }

    val passInterceptedCount = 1 + rng.nextInt(3)
    val passInterceptedEvents = (0 until passInterceptedCount).map { _ =>
      val min = 15 + rng.nextInt(65)
      val (interceptingTid, interceptor, passerTid, passer) = if (rng.nextBoolean()) {
        (input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size)), input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size)))
      } else {
        (input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size)), input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size)))
      }
      MatchEventRecord(min, "PassIntercepted", Some(interceptor.player.id), Some(passer.player.id), Some(interceptingTid), Some(1 + rng.nextInt(PitchModel.TotalZones)), None, Map.empty)
    }

    val dribbleCount = 2 + rng.nextInt(4)
    val dribbleEvents = (0 until dribbleCount).map { _ =>
      val min = 10 + rng.nextInt(75)
      val tid = if (rng.nextBoolean()) input.homeTeam.teamId else input.awayTeam.teamId
      val outfield = if (tid == input.homeTeam.teamId) homeOutfield else awayOutfield
      val actor = outfield(rng.nextInt(outfield.size))
      MatchEventRecord(min, "Dribble", Some(actor.player.id), None, Some(tid), Some(1 + rng.nextInt(PitchModel.TotalZones)), Some("Success"), Map("zone" -> (1 + rng.nextInt(PitchModel.TotalZones)).toString))
    }
    val dribbleLostCount = 1 + rng.nextInt(2)
    val dribbleLostEvents = (0 until dribbleLostCount).map { _ =>
      val min = 18 + rng.nextInt(60)
      val (lostTid, loser, wonTid, winner) = if (rng.nextBoolean()) {
        (input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size)), input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size)))
      } else {
        (input.awayTeam.teamId, awayOutfield(rng.nextInt(awayOutfield.size)), input.homeTeam.teamId, homeOutfield(rng.nextInt(homeOutfield.size)))
      }
      MatchEventRecord(min, "DribbleLost", Some(loser.player.id), Some(winner.player.id), Some(wonTid), Some(1 + rng.nextInt(PitchModel.TotalZones)), None, Map.empty)
    }

    val freeKickMinutes = foulCandidates.flatMap { case (min, tid, _) =>
      if (rng.nextDouble() < 0.5) {
        val otherTid = if (tid == input.homeTeam.teamId) input.awayTeam.teamId else input.homeTeam.teamId
        val outfield = if (otherTid == input.homeTeam.teamId) homeOutfield else awayOutfield
        Some((min + 1, otherTid, outfield(rng.nextInt(outfield.size))))
      } else None
    }
    val freeKickEvents = freeKickMinutes.map { case (min, tid, taker) =>
      MatchEventRecord(min, "FreeKick", Some(taker.player.id), None, Some(tid), Some(5 + rng.nextInt(6)), Some("Success"), Map.empty)
    }

    val offsideCount = 1 + rng.nextInt(3)
    val offsideEvents = (0 until offsideCount).map { _ =>
      val min = 25 + rng.nextInt(50)
      val tid = if (rng.nextBoolean()) input.homeTeam.teamId else input.awayTeam.teamId
      val outfield = if (tid == input.homeTeam.teamId) homeOutfield else awayOutfield
      val player = outfield(rng.nextInt(outfield.size))
      MatchEventRecord(min, "Offside", Some(player.player.id), None, Some(tid), None, None, Map.empty)
    }

    val substitutionCount = rng.nextInt(4)
    val substitutionEvents = (0 until substitutionCount).flatMap { _ =>
      val min = 60 + rng.nextInt(25)
      val tid = if (rng.nextBoolean()) input.homeTeam.teamId else input.awayTeam.teamId
      val outfield = if (tid == input.homeTeam.teamId) homeOutfield else awayOutfield
      if (outfield.size >= 2) {
        val idx = rng.nextInt(outfield.size)
        val outPlayer = outfield(idx)
        val inPlayer = outfield((idx + 1 + rng.nextInt(outfield.size - 1)) % outfield.size)
        if (outPlayer.player.id != inPlayer.player.id)
          Some(MatchEventRecord(min, "Substitution", Some(inPlayer.player.id), Some(outPlayer.player.id), Some(tid), None, Some("Success"), Map("minute" -> min.toString)))
        else None
      } else None
    }

    val allEvents = kickOff :: (homeGoalEvents.toList ++ awayGoalEvents.toList ++ passRecords ++ shotRecords ++ foulRecords ++ yellowRecords ++ redFromFoul ++ cornerRecords ++ injuryEvent.toList ++ throwInEvents ++ crossEvents ++ passInterceptedEvents ++ dribbleEvents ++ dribbleLostEvents ++ freeKickEvents ++ offsideEvents ++ substitutionEvents.toList)
    allEvents.sortBy(_.minute)
  }
}
