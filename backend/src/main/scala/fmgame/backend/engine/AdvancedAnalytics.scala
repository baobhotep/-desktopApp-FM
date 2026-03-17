package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Zaawansowane algorytmy analityki (ANALIZA §2–§16):
 * xT (value iteration), clustering sieci podań, OBSO, tortuosity (gBRI), metabolic load, Nash karne.
 */
object AdvancedAnalytics {

  /** Macierz przejść T(z → z') z sekwencji zdarzeń (Pass/LongPass/Dribble ze strefą). Zwraca Map((zFrom,zTo) -> count). */
  def transitionCountsFromEvents(events: List[MatchEventRecord]): Map[(Int, Int), Int] = {
    val moveTypes = Set("Pass", "LongPass", "Dribble")
    val counts = scala.collection.mutable.Map.empty[(Int, Int), Int]
    var prevZone: Option[Int] = None
    events.foreach { e =>
      val zone = e.zone.filter(z => z >= 1 && z <= PitchModel.TotalZones)
      if (moveTypes.contains(e.eventType) && zone.isDefined) {
        prevZone.foreach { pz =>
          if (pz != zone.get) counts((pz, zone.get)) = counts.getOrElse((pz, zone.get), 0) + 1
        }
        prevZone = zone
      } else if (e.eventType == "PassIntercepted" || e.eventType == "DribbleLost" || e.eventType == "Shot" || e.eventType == "Goal") {
        prevZone = None
      }
    }
    counts.toMap
  }

  /** Value iteration dla xT: V(z) = baseThreat(z) + gamma * sum_z' P(z'|z)*V(z'). Converges when max delta < epsilon. */
  def xTValueIteration(
    transitionCounts: Map[(Int, Int), Int],
    baseZoneThreat: Int => Double,
    gamma: Double = 0.95,
    maxIterations: Int = 50,
    epsilon: Double = 1e-6
  ): Map[Int, Double] = {
    val zones = (1 to PitchModel.TotalZones).toSet
    val outgoing = transitionCounts.groupBy(_._1._1).view.mapValues(_.map { case (k, v) => (k._2, v) }.toMap).toMap
    val totalFrom = outgoing.view.mapValues(_.values.sum).toMap
    var V = zones.map(z => z -> baseZoneThreat(z)).toMap
    var iteration = 0
    var converged = false
    while (iteration < maxIterations && !converged) {
      val Vnew = zones.map { z =>
        val base = baseZoneThreat(z)
        val trans = totalFrom.getOrElse(z, 0)
        val expectedNext = if (trans > 0) {
          outgoing.getOrElse(z, Map.empty).map { case (zTo, cnt) => (cnt.toDouble / trans) * V.getOrElse(zTo, 0.0) }.sum
        } else 0.0
        z -> (base + gamma * expectedNext)
      }.toMap
      val maxDelta = zones.map(z => math.abs(Vnew(z) - V(z))).max
      V = Vnew
      iteration += 1
      converged = maxDelta < epsilon
    }
    V
  }

  /** Współczynnik clusteringu dla węzła: 2 * krawędzie między sąsiadami / (k*(k-1)). Graf nieskierowany (sąsiedzi = in+out). */
  def clusteringByNode(
    edges: List[(PlayerId, PlayerId)],
    nodes: List[PlayerId]
  ): Map[PlayerId, Double] = {
    val adj = scala.collection.mutable.Map.empty[PlayerId, Set[PlayerId]]
    nodes.foreach(n => adj(n) = Set.empty)
    edges.foreach { case (a, b) =>
      if (a != b) {
        adj(a) = adj.getOrElse(a, Set.empty) + b
        adj(b) = adj.getOrElse(b, Set.empty) + a
      }
    }
    nodes.map { v =>
      val neighbors = adj.getOrElse(v, Set.empty)
      val k = neighbors.size
      val edgesBetween = if (k < 2) 0.0 else {
        neighbors.toList.combinations(2).count { pair => pair.size == 2 && adj.getOrElse(pair.head, Set.empty).contains(pair.last) }.toDouble
      }
      val coef = if (k <= 1) 0.0 else (2.0 * edgesBetween) / (k * (k - 1))
      v -> coef
    }.toMap
  }

  /** OBSO (Off-Ball Scoring Opportunity): prawdopodobieństwo strzału ze strefy. Używamy baseZoneThreat z DxT. */
  def obsByZone(baseZoneThreat: Int => Double): Map[Int, Double] =
    (1 to PitchModel.TotalZones).map(z => z -> (baseZoneThreat(z) / 0.2).min(1.0)).toMap

  /** Tortuosity ścieżki piłki: stosunek sumy odległości między kolejnymi strefami do odległości liniowej (strefa start -> koniec). */
  def ballTortuosity(events: List[MatchEventRecord]): Option[Double] = {
    val zones = events.flatMap(_.zone).filter(z => z >= 1 && z <= PitchModel.TotalZones)
    if (zones.size < 2) return None
    def dist(z1: Int, z2: Int): Double = {
      val (a, b) = PitchModel.zoneCenters.getOrElse(z1, (52.5, 34.0))
      val (c, d) = PitchModel.zoneCenters.getOrElse(z2, (52.5, 34.0))
      PitchModel.distance(a, b, c, d)
    }
    val pathLength = zones.sliding(2).map { pair => if (pair.size == 2) dist(pair.head, pair.last) else 0.0 }.sum
    val straight = dist(zones.head, zones.last)
    if (straight < 1e-6) None else Some(pathLength / straight)
  }

  /** Metabolic load (przybliżenie): suma odległości między strefami przy przejściach piłki (metry). */
  def metabolicLoadFromZonePath(events: List[MatchEventRecord]): Double = {
    val zones = events.flatMap(_.zone).filter(z => z >= 1 && z <= PitchModel.TotalZones)
    if (zones.size < 2) 0.0
    else zones.sliding(2).map { pair =>
      if (pair.size == 2) {
        val (x1, y1) = PitchModel.zoneCenters.getOrElse(pair.head, (52.5, 34.0))
        val (x2, y2) = PitchModel.zoneCenters.getOrElse(pair.last, (52.5, 34.0))
        PitchModel.distance(x1, y1, x2, y2)
      } else 0.0
    }.sum
  }

  /** Nash equilibrium dla karnego 2x2: strzelec L/R, bramkarz L/R. Payoff strzelca = P(gol). Zwraca (p_strzelca_L, p_bramkarz_L). */
  def nashPenalty2x2(
    payoffLL: Double, payoffLR: Double, payoffRL: Double, payoffRR: Double
  ): (Double, Double) = {
    val denom = payoffLL - payoffLR - payoffRL + payoffRR
    if (math.abs(denom) < 1e-9) (0.5, 0.5)
    else {
      val pShooterL = (payoffRR - payoffRL) / denom
      val pGkL = (payoffRR - payoffLR) / denom
      (pShooterL.max(0).min(1), pGkL.max(0).min(1))
    }
  }

  /** EPV (Expected Possession Value) strefy = wartość xT po value iteration (do użycia w analityce). */
  def epvFromxT(xtByZone: Map[Int, Double]): Map[Int, Double] = xtByZone

  /** C-OBSO: średni kontekst strzałów per strefa (defendersInCone, gkDistance) z metadata Shot/Goal. */
  def shotContextByZoneFromEvents(events: List[MatchEventRecord]): Map[Int, (Double, Double)] = {
    val defByZone = scala.collection.mutable.Map.empty[Int, scala.collection.mutable.ListBuffer[Int]]
    val gkByZone = scala.collection.mutable.Map.empty[Int, scala.collection.mutable.ListBuffer[Double]]
    (1 to PitchModel.TotalZones).foreach { z => defByZone(z) = scala.collection.mutable.ListBuffer.empty; gkByZone(z) = scala.collection.mutable.ListBuffer.empty }
    events.foreach { e =>
      if ((e.eventType == "Shot" || e.eventType == "Goal") && e.zone.exists(z => z >= 1 && z <= PitchModel.TotalZones)) {
        val z = e.zone.get
        val defenders = e.metadata.get("defendersInCone").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
        val gkDist = e.metadata.get("gkDistance").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.0)
        defByZone(z) += defenders
        gkByZone(z) += gkDist
      }
    }
    (1 to PitchModel.TotalZones).map { z =>
      val defList = defByZone(z)
      val gkList = gkByZone(z)
      val avgDef = if (defList.isEmpty) 0.0 else defList.sum.toDouble / defList.size
      val avgGk = if (gkList.isEmpty) 0.0 else gkList.sum / gkList.size
      z -> (avgDef, avgGk)
    }.toMap
  }

  /** Stałe fragmenty (§8): aktywność stref per typ+routine (Corner/FreeKick). Klucz np. "Corner:default", wartość strefa->liczba (strefa następnego zdarzenia po rożnym/wolnym). */
  def setPieceZoneActivityFromEvents(events: List[MatchEventRecord]): Map[String, Map[Int, Int]] = {
    val result = scala.collection.mutable.Map.empty[String, scala.collection.mutable.Map[Int, Int]]
    def add(routineKey: String, zone: Int): Unit = {
      if (zone >= 1 && zone <= PitchModel.TotalZones) {
        val m = result.getOrElseUpdate(routineKey, scala.collection.mutable.Map.empty.withDefaultValue(0))
        m(zone) = m(zone) + 1
      }
    }
    val setTypes = Set("Corner", "FreeKick")
    for (i <- events.indices) {
      val e = events(i)
      if (setTypes.contains(e.eventType)) {
        val routine = e.metadata.getOrElse("routine", "default")
        val key = s"${e.eventType}:$routine"
        val nextZone = events.lift(i + 1).flatMap(_.zone).filter(z => z >= 1 && z <= PitchModel.TotalZones)
        nextZone.foreach(add(key, _))
      }
    }
    result.view.mapValues(_.toMap).toMap
  }

  /** Tortuosity biegów zawodników (§2.8): dla każdego gracza stosunek długości ścieżki (strefy) do odcinka start–koniec. */
  def playerTortuosityFromZoneSequences(zonesByPlayer: Map[PlayerId, List[Int]]): Map[PlayerId, Double] = {
    def tortuosity(zones: List[Int]): Option[Double] = {
      if (zones.size < 2) return None
      def dist(z1: Int, z2: Int): Double = {
        val (a, b) = PitchModel.zoneCenters.getOrElse(z1, (52.5, 34.0))
        val (c, d) = PitchModel.zoneCenters.getOrElse(z2, (52.5, 34.0))
        PitchModel.distance(a, b, c, d)
      }
      val pathLen = zones.sliding(2).map { pair => if (pair.size == 2) dist(pair.head, pair.last) else 0.0 }.sum
      val straight = dist(zones.head, zones.last)
      if (straight < 1e-6) None else Some(pathLen / straight)
    }
    zonesByPlayer.flatMap { case (pid, zones) => tortuosity(zones.filter(z => z >= 1 && z <= PitchModel.TotalZones)).map(pid -> _) }
  }

  /** NMF (§8): przybliżenie macierzy aktywności stałych fragmentów (routine × strefa) jako W*H, 2 komponenty. Zwraca (W: routine -> [w1,w2], H: lista 2 wektorów stref). */
  def setPiecePatternsNMF(activity: Map[String, Map[Int, Int]], numComponents: Int = 2, maxIter: Int = 50): (Map[String, List[Double]], List[Map[Int, Double]]) = {
    val routines = activity.keys.toIndexedSeq
    val nz = PitchModel.TotalZones
    if (routines.isEmpty || numComponents < 1) return (Map.empty, List.fill(numComponents)((1 to nz).map(_ -> 0.0).toMap))
    val rng = new scala.util.Random(routines.hashCode)
    val V = routines.map(r => (1 to nz).map(z => activity(r).getOrElse(z, 0).toDouble).toArray).toArray
    var W = Array.fill(routines.size)(Array.fill(numComponents)(1.0 + rng.nextDouble() * 0.5))
    var H = Array.fill(numComponents)((1 to nz).map(_ => 1.0 + rng.nextDouble() * 0.5).toArray)
    for (_ <- 1 to maxIter) {
      val WH1 = Array.ofDim[Double](routines.size, nz)
      for (i <- W.indices; j <- 0 until nz) WH1(i)(j) = (0 until numComponents).map(k => W(i)(k) * H(k)(j)).sum + 1e-10
      for (i <- W.indices; k <- 0 until numComponents)
        W(i)(k) = W(i)(k) * (0 until nz).map(j => V(i)(j) * H(k)(j) / WH1(i)(j)).sum / (0 until nz).map(j => H(k)(j)).sum.max(1e-10)
      val WH2 = Array.ofDim[Double](routines.size, nz)
      for (i <- W.indices; j <- 0 until nz) WH2(i)(j) = (0 until numComponents).map(k => W(i)(k) * H(k)(j)).sum + 1e-10
      for (k <- 0 until numComponents; j <- 0 until nz)
        H(k)(j) = H(k)(j) * (0 until routines.size).map(i => V(i)(j) * W(i)(k) / WH2(i)(j)).sum / (0 until routines.size).map(i => W(i)(k)).sum.max(1e-10)
    }
    val wMap = routines.zipWithIndex.map { case (r, i) => r -> W(i).toList }.toMap
    val hList = H.map(row => (1 to nz).zip(row).toMap).toList
    (wMap, hList)
  }

  /** GMM-like (§8): klastrowanie routine’ów po rozkładzie stref (K-means K=2). Zwraca routine -> cluster (0 lub 1). */
  def setPieceRoutineClusters(activity: Map[String, Map[Int, Int]], k: Int = 2): Map[String, Int] = {
    val routines = activity.keys.toIndexedSeq
    if (routines.size < 2 || k < 2) return routines.map(_ -> 0).toMap
    val nz = PitchModel.TotalZones
    val rng = new scala.util.Random(routines.hashCode)
    val vecs = routines.map(r => (1 to nz).map(z => activity(r).getOrElse(z, 0).toDouble).toArray)
    var centroids = (0 until k).map(_ => (1 to nz).map(_ => 2.0 + rng.nextDouble() * 2).toArray).toArray
    for (_ <- 1 to 30) {
      val assign = vecs.map(v => (0 until k).minBy(c => (0 until nz).map(j => (v(j) - centroids(c)(j)) * (v(j) - centroids(c)(j))).sum))
      for (c <- 0 until k) {
        val in = assign.zipWithIndex.filter(_._1 == c).map(_._2)
        if (in.nonEmpty)
          for (j <- 0 until nz) centroids(c)(j) = in.map(i => vecs(i)(j)).sum / in.size
      }
    }
    val assign = vecs.map(v => (0 until k).minBy(c => (0 until nz).map(j => (v(j) - centroids(c)(j)) * (v(j) - centroids(c)(j))).sum))
    routines.zip(assign).toMap
  }

  /** Prognoza Poisson: P(wygrana gosp.), P(remis), P(wygrana gości) z xG. */
  def poissonPrognosis(xgHome: Double, xgAway: Double, maxGoals: Int = 10): (Double, Double, Double) = {
    def poisson(lambda: Double)(k: Int): Double = {
      if (lambda <= 0.0) { if (k == 0) 1.0 else 0.0 }
      else {
        val logFactorial = (1 to k).foldLeft(0.0)((acc, i) => acc + math.log(i))
        math.exp(-lambda + k * math.log(lambda) - logFactorial)
      }
    }
    var pHome = 0.0
    var pDraw = 0.0
    var pAway = 0.0
    for (h <- 0 to maxGoals; a <- 0 to maxGoals) {
      val p = poisson(xgHome)(h) * poisson(xgAway)(a)
      if (h > a) pHome += p else if (h < a) pAway += p else pDraw += p
    }
    (pHome, pDraw, pAway)
  }

  /** Voronoi z centrum aktywności: środek masy akcji drużyn (strefy ważone liczbą akcji), potem dla każdej strefy przypisanie do drużyny, której środek jest bliżej. Zwraca Map(zone -> homeShare 0.0 lub 1.0). */
  def voronoiZoneFromCentroids(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId): Map[Int, Double] = {
    val actionTypes = Set("Pass", "LongPass", "Dribble", "Shot", "Goal", "Cross")
    val homeCounts = (1 to PitchModel.TotalZones).map(z => z -> 0).toMap
    val awayCounts = (1 to PitchModel.TotalZones).map(z => z -> 0).toMap
    val hMut = scala.collection.mutable.Map(homeCounts.toSeq*)
    val aMut = scala.collection.mutable.Map(awayCounts.toSeq*)
    events.foreach { e =>
      if (actionTypes.contains(e.eventType)) {
        val zone = e.zone.filter(z => z >= 1 && z <= PitchModel.TotalZones).getOrElse(1)
        e.teamId match {
          case Some(tid) if tid == homeTeamId => hMut(zone) = hMut(zone) + 1
          case Some(tid) if tid == awayTeamId => aMut(zone) = aMut(zone) + 1
          case _ =>
        }
      }
    }
    def centroid(counts: Map[Int, Int]): (Double, Double) = {
      val total = counts.values.sum
      if (total == 0) (PitchModel.PitchLength / 2, PitchModel.PitchWidth / 2)
      else {
        var sx = 0.0; var sy = 0.0
        counts.foreach { case (z, c) =>
          val (x, y) = PitchModel.zoneCenters.getOrElse(z, (52.5, 34.0))
          sx += x * c; sy += y * c
        }
        (sx / total, sy / total)
      }
    }
    val (hcx, hcy) = centroid(hMut.toMap)
    val (acx, acy) = centroid(aMut.toMap)
    (1 to PitchModel.TotalZones).map { z =>
      val (zx, zy) = PitchModel.zoneCenters.getOrElse(z, (52.5, 34.0))
      val dHome = PitchModel.distance(zx, zy, hcx, hcy)
      val dAway = PitchModel.distance(zx, zy, acx, acy)
      val share = if (dHome < dAway) 1.0 else if (dAway < dHome) 0.0 else 0.5
      z -> share
    }.toMap
  }

  /** xPass: wartość podania = xT(strefa odbiorcy) − xT(strefa podania). Zwraca (passValueByPlayer, (homeTotal, awayTotal), (homeUnderPressure, awayUnderPressure), passValueUnderPressureByPlayer). */
  def xPassValueFromEvents(events: List[MatchEventRecord], xtValueByZone: Map[Int, Double], homeTeamId: TeamId, awayTeamId: TeamId): (Map[PlayerId, Double], (Double, Double), (Double, Double), Map[PlayerId, Double]) = {
    val byPlayer = scala.collection.mutable.Map.empty[PlayerId, Double]
    val byPlayerUnderPressure = scala.collection.mutable.Map.empty[PlayerId, Double]
    var homeSum = 0.0
    var awaySum = 0.0
    var homeUnder = 0.0
    var awayUnder = 0.0
    val passTypes = Set("Pass", "LongPass")
    for (i <- events.indices) {
      val e = events(i)
      if (passTypes.contains(e.eventType) && e.outcome.contains("Success") && e.zone.exists(z => z >= 1 && z <= PitchModel.TotalZones)) {
        val next = events.lift(i + 1)
        val sameTeam = next.flatMap(_.teamId).exists(tid => e.teamId.contains(tid))
        val receiverZone = next.flatMap(_.zone).filter(z => z >= 1 && z <= PitchModel.TotalZones)
        if (sameTeam && receiverZone.isDefined) {
          val fromZ = e.zone.get
          val toZ = receiverZone.get
          val fromVal = xtValueByZone.getOrElse(fromZ, 0.0)
          val toVal = xtValueByZone.getOrElse(toZ, 0.0)
          val value = toVal - fromVal
          val pressure = e.metadata.get("receiverPressure").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          val underPressure = pressure >= 2
          e.actorPlayerId.foreach { pid =>
            byPlayer(pid) = byPlayer.getOrElse(pid, 0.0) + value
            if (underPressure) byPlayerUnderPressure(pid) = byPlayerUnderPressure.getOrElse(pid, 0.0) + value
          }
          if (e.teamId.contains(homeTeamId)) {
            homeSum += value
            if (underPressure) homeUnder += value
          } else if (e.teamId.contains(awayTeamId)) {
            awaySum += value
            if (underPressure) awayUnder += value
          }
        }
      }
    }
    (byPlayer.toMap, (homeSum, awaySum), (homeUnder, awayUnder), byPlayerUnderPressure.toMap)
  }

  /** Voronoi-like: udział gospodarzy w akcjach (Pass, LongPass, Dribble, Shot, Cross) w każdej strefie 1–12. Zwraca Map(zone -> homeShare 0.0..1.0). */
  def zoneDominanceFromEvents(events: List[MatchEventRecord], homeTeamId: TeamId, awayTeamId: TeamId): Map[Int, Double] = {
    val actionTypes = Set("Pass", "LongPass", "Dribble", "Shot", "Goal", "Cross")
    val byZone = (1 to PitchModel.TotalZones).map(z => z -> (0, 0)).toMap
    val counts = scala.collection.mutable.Map(byZone.toSeq*)
    events.foreach { e =>
      if (actionTypes.contains(e.eventType)) {
        val zone = e.zone.filter(z => z >= 1 && z <= PitchModel.TotalZones).getOrElse(1)
        e.teamId match {
          case Some(tid) if tid == homeTeamId => counts(zone) = (counts(zone)._1 + 1, counts(zone)._2)
          case Some(tid) if tid == awayTeamId => counts(zone) = (counts(zone)._1, counts(zone)._2 + 1)
          case _ =>
        }
      }
    }
    (1 to PitchModel.TotalZones).map { z =>
      val (h, a) = counts(z)
      val total = h + a
      val share = if (total > 0) h.toDouble / total else 0.5
      z -> share
    }.toMap
  }
}
