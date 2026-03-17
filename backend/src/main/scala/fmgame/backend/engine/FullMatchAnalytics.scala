package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Post-match analytics aggregation extracted from FullMatchEngine.
 * Computes VAEP, xG chain/buildup, pass network centrality, WPA, field tilt, PPDA,
 * and all advanced metrics from a completed list of match events.
 */
object FullMatchAnalytics {

  def computeAnalyticsFromEvents(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId, homeGoals: Int, awayGoals: Int, vaepModel: VAEPModel): MatchAnalytics = {
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
      if (z >= 1 && z <= PitchModel.TotalZones) {
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
      val eventIsHome = isHome(e.teamId)
      val eventIsAway = isAway(e.teamId)
      if (zone >= 1 && eventIsHome && PitchModel.isAttackingThird(zone, true)) contactsAttThirdHome += 1
      if (zone >= 1 && eventIsAway && PitchModel.isAttackingThird(zone, false)) contactsAttThirdAway += 1
      val inAwayBuildUp = zone >= 1 && PitchModel.isBuildUpZone(zone, false)
      val inHomeBuildUp = zone >= 1 && PitchModel.isBuildUpZone(zone, true)
      if (e.eventType == "Pass" || e.eventType == "LongPass") {
        if (eventIsAway && inAwayBuildUp) passesBuildUpAway += 1
        if (eventIsHome && inHomeBuildUp) passesBuildUpHome += 1
      }
      if (e.eventType == "Tackle" || e.eventType == "PassIntercepted") {
        if (eventIsHome && inAwayBuildUp) defActionsBuildUpHome += 1
        if (eventIsAway && inHomeBuildUp) defActionsBuildUpAway += 1
      }
      val possessionTeamId = e.eventType match {
        case "PassIntercepted" => if (e.teamId.contains(homeTeamId)) Some(awayTeamId) else Some(homeTeamId)
        case _ => e.teamId
      }
      val isPossessionTeam = e.teamId == possessionTeamId
      val vaepCtx = VAEPContext(e.eventType, zone, e.outcome, e.minute, scoreH, scoreA, possessionTeamId, isPossessionTeam, eventIsHome)
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
          val goalXg = e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.2)
          if (isHome(e.teamId)) { shotHome += 1; xgHome += goalXg; scoreH += 1 } else if (isAway(e.teamId)) { shotAway += 1; xgAway += goalXg; scoreA += 1 }
          e.actorPlayerId.foreach { pid => addVaep(pid, "Goal", vaepValue); addActivity(pid, zone) }
        case "ThrowIn" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "ThrowIn", vaepValue); addActivity(pid, zone) }
        case "Cross" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "Cross", vaepValue); addActivity(pid, zone) }
        case "PassIntercepted" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "PassIntercepted", vaepValue); addIwp(pid, 0.02); defensiveActions(pid) += 1; if (zone >= 1 && PitchModel.isOpponentHalf(zone, eventIsHome)) pressingInOppHalf(pid) += 1; addActivity(pid, zone) }
        case "Tackle" =>
          val iwpT = if (e.outcome.contains("Won")) 0.025 else 0.01
          e.actorPlayerId.foreach { pid => addVaep(pid, "Tackle", vaepValue); addIwp(pid, iwpT); defensiveActions(pid) += 1; if (zone >= 1 && PitchModel.isOpponentHalf(zone, eventIsHome)) pressingInOppHalf(pid) += 1; addActivity(pid, zone) }
        case "Clearance" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "Clearance", vaepValue); addActivity(pid, zone) }
        case "Duel" | "AerialDuel" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, e.eventType, vaepValue); addIwp(pid, 0.012); addActivity(pid, zone) }
        case "Dribble" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "Dribble", vaepValue); addIwp(pid, 0.01); addActivity(pid, zone) }
        case "DribbleLost" =>
          e.actorPlayerId.foreach { pid => addVaep(pid, "DribbleLost", vaepValue); addActivity(pid, zone) }
          e.secondaryPlayerId.foreach { pid => addIwp(pid, 0.02); defensiveActions(pid) += 1; if (zone >= 1 && PitchModel.isOpponentHalf(zone, eventIsHome)) pressingInOppHalf(pid) += 1 }
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
    /** xT i OBSO w perspektywie gospodarzy (kolumna ↑ = wyższe zagrożenie); dla pełnej perspektywy per drużyna wymagałoby dwóch map. */
    val baseThreatHome = (z: Int) => DxT.baseZoneThreat(z, true)
    val xtValueByZone = AdvancedAnalytics.xTValueIteration(transitionCounts, baseThreatHome)
    val obsoByZone = AdvancedAnalytics.obsByZone(baseThreatHome)
    val ballTortuosityOpt = AdvancedAnalytics.ballTortuosity(events)
    val metabolicLoad = AdvancedAnalytics.metabolicLoadFromZonePath(events)
    val homeShareByZone = AdvancedAnalytics.zoneDominanceFromEvents(events, homeTeamId, awayTeamId)
    val shotContextByZone = AdvancedAnalytics.shotContextByZoneFromEvents(events)
    val setPieceZoneActivity = AdvancedAnalytics.setPieceZoneActivityFromEvents(events)
    val totalAct = passHome + passAway + shotHome + shotAway
    val (possH, possA) = if (totalAct > 0) (100.0 * (passHome + shotHome) / totalAct, 100.0 * (passAway + shotAway) / totalAct) else (50.0, 50.0)
    val wpaSamples = {
      val sorted = events.filter(_.eventType == "Goal").sortBy(_.minute)
      (0 to 90 by 10).map { min =>
        var wpa = 0.5
        sorted.filter(_.minute <= min).foreach { e =>
          val remainingFactor = 1.0 + (e.minute / 90.0) * 0.5
          val delta = 0.15 * remainingFactor
          if (isHome(e.teamId)) wpa = math.min(1.0, wpa + delta)
          else if (isAway(e.teamId)) wpa = math.max(0.0, wpa - delta)
        }
        (min, wpa)
      }.toList
    }
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
      val pidIsHome = playerToTeam.get(pid).contains(homeTeamId)
      val attacking = (1 to PitchModel.TotalZones).filter(z => PitchModel.isAttackingThird(z, pidIsHome)).map(z => byZone.getOrElse(z, 0)).sum
      val ratio = if (total > 0) attacking.toDouble / total else 0.0
      pid -> (dist * (1.0 + 0.15 * ratio))
    }.toMap
    val (setPiecePatternW, setPiecePatternH) = AdvancedAnalytics.setPiecePatternsNMF(setPieceZoneActivity)
    val setPieceRoutineCluster = AdvancedAnalytics.setPieceRoutineClusters(setPieceZoneActivity)
    val poissonPrognosisOpt = Some(AdvancedAnalytics.poissonPrognosis(xgHome, xgAway))
    val voronoiCentroidByZone = AdvancedAnalytics.voronoiZoneFromCentroids(events, homeTeamId, awayTeamId)
    val (passValueByPlayer, passValueTotal, passValueUnderPressureTotal, passValueUnderPressureByPlayer) = AdvancedAnalytics.xPassValueFromEvents(events, xtValueByZone, homeTeamId, awayTeamId)
    val influenceScoreByPlayer = playerActivityByZoneMap.iterator.map { case (pid, byZone) =>
      val score = (1 to PitchModel.TotalZones).map(z => byZone.getOrElse(z, 0) * xtValueByZone.getOrElse(z, 0.0)).sum
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

  private def computeXgChainAndBuildup(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId): (Map[PlayerId, Double], Map[PlayerId, Double]) = {
    def isPossessionEvent(et: String) = et == "Pass" || et == "LongPass" || et == "Dribble" || et == "Cross" || et == "ThrowIn"
    val chainMutable = scala.collection.mutable.Map.empty[PlayerId, Double]
    val buildupMutable = scala.collection.mutable.Map.empty[PlayerId, Double]
    var i = 0
    while (i < events.size) {
      val e = events(i)
      val (shotXg, isGoal) = e.eventType match {
        case "Shot" => (e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.2), false)
        case "Goal" => (e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.2), true)
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
