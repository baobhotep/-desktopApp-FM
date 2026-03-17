package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.test.*

object AdvancedAnalyticsSpec extends ZIOSpecDefault {

  private def ev(min: Int, et: String, zone: Option[Int] = None): MatchEventRecord =
    MatchEventRecord(min, et, Some(PlayerId("p1")), None, Some(TeamId("h")), zone, Some("Success"), Map.empty)

  def spec = suite("AdvancedAnalytics")(
    test("transitionCountsFromEvents counts Pass/LongPass/Dribble zone transitions") {
      val events = List(
        ev(1, "Pass", Some(3)),
        ev(2, "Pass", Some(6)),
        ev(3, "Dribble", Some(8)),
        ev(4, "Pass", Some(5))
      )
      val counts = AdvancedAnalytics.transitionCountsFromEvents(events)
      assertTrue(counts.get((3, 6)).contains(1), counts.get((6, 8)).contains(1), counts.get((8, 5)).contains(1))
    },
    test("xTValueIteration returns values per zone and base threat increases with zone") {
      val counts = Map((1, 2) -> 1, (2, 3) -> 1, (3, 4) -> 1, (4, 5) -> 1, (5, 6) -> 1, (6, 7) -> 1, (7, 8) -> 1, (8, 9) -> 1, (9, 10) -> 2, (10, 11) -> 1, (11, 12) -> 1)
      val xt = AdvancedAnalytics.xTValueIteration(counts, z => DxT.baseZoneThreat(z, true))
      assertTrue(xt.size == PitchModel.TotalZones, xt.values.forall(_ >= 0.0), DxT.baseZoneThreat(PitchModel.TotalZones, true) > DxT.baseZoneThreat(1, true))
    },
    test("clusteringByNode returns 0 for no edges, 1 for triangle") {
      val pid1 = PlayerId("a")
      val pid2 = PlayerId("b")
      val pid3 = PlayerId("c")
      val edges = List((pid1, pid2), (pid2, pid3), (pid3, pid1))
      val nodes = List(pid1, pid2, pid3)
      val cl = AdvancedAnalytics.clusteringByNode(edges, nodes)
      val clNoEdges = AdvancedAnalytics.clusteringByNode(List.empty, nodes)
      assertTrue(cl(pid1) == 1.0, cl(pid2) == 1.0, cl(pid3) == 1.0, clNoEdges.values.forall(_ == 0.0))
    },
    test("obsByZone returns map of all zones with values in [0,1]") {
      val obs = AdvancedAnalytics.obsByZone(z => DxT.baseZoneThreat(z, true))
      assertTrue(obs.size == PitchModel.TotalZones, obs.values.forall(v => v >= 0 && v <= 1))
    },
    test("ballTortuosity returns None for single zone, ~1 for straight, >1 for zigzag") {
      val single = List(ev(1, "Pass", Some(5)))
      val tortSingle = AdvancedAnalytics.ballTortuosity(single)
      val straight = List(ev(1, "Pass", Some(1)), ev(2, "Pass", Some(12)))
      val tortStraight = AdvancedAnalytics.ballTortuosity(straight)
      val zigzag = List(ev(1, "Pass", Some(1)), ev(2, "Pass", Some(4)), ev(3, "Pass", Some(7)), ev(4, "Pass", Some(12)))
      val tortZigzag = AdvancedAnalytics.ballTortuosity(zigzag)
      assertTrue(tortSingle.isEmpty, tortStraight.isDefined, tortStraight.forall(v => math.abs(v - 1.0) < 0.1), tortZigzag.isDefined, tortZigzag.get >= 1.0)
    },
    test("metabolicLoadFromZonePath sums distances") {
      val events = List(ev(1, "Pass", Some(1)), ev(2, "Pass", Some(12)))
      val load = AdvancedAnalytics.metabolicLoadFromZonePath(events)
      assertTrue(load > 0.0)
    },
    test("nashPenalty2x2 returns probabilities in [0,1]") {
      val (pS, pG) = AdvancedAnalytics.nashPenalty2x2(0.7, 0.9, 0.8, 0.6)
      assertTrue(pS >= 0.0 && pS <= 1.0, pG >= 0.0 && pG <= 1.0)
    },
    test("zoneDominanceFromEvents returns home share per zone") {
      val homeId = TeamId("h")
      val awayId = TeamId("a")
      val events = List(
        MatchEventRecord(1, "Pass", Some(PlayerId("p1")), None, Some(homeId), Some(5), Some("Success"), Map.empty),
        MatchEventRecord(2, "Pass", Some(PlayerId("p2")), None, Some(awayId), Some(5), Some("Success"), Map.empty),
        MatchEventRecord(3, "Shot", Some(PlayerId("p1")), None, Some(homeId), Some(10), None, Map.empty)
      )
      val dom = AdvancedAnalytics.zoneDominanceFromEvents(events, homeId, awayId)
      assertTrue(dom.size == PitchModel.TotalZones, dom(5) == 0.5, dom(10) == 1.0)
    },
    test("shotContextByZoneFromEvents returns avg defenders and gkDistance per zone") {
      val events = List(
        MatchEventRecord(1, "Shot", Some(PlayerId("p1")), None, Some(TeamId("h")), Some(10), Some("Saved"), Map("defendersInCone" -> "2", "gkDistance" -> "3.5")),
        MatchEventRecord(2, "Goal", Some(PlayerId("p2")), None, Some(TeamId("h")), Some(10), Some("Success"), Map("defendersInCone" -> "0", "gkDistance" -> "1.0"))
      )
      val ctx = AdvancedAnalytics.shotContextByZoneFromEvents(events)
      assertTrue(ctx.size == PitchModel.TotalZones, ctx(10)._1 == 1.0, ctx(10)._2 == 2.25)
    },
    test("setPieceZoneActivityFromEvents returns zone counts per Corner/FreeKick routine") {
      val events = List(
        MatchEventRecord(1, "Corner", Some(PlayerId("p1")), None, Some(TeamId("h")), None, Some("Success"), Map("routine" -> "default")),
        MatchEventRecord(2, "Pass", Some(PlayerId("p2")), None, Some(TeamId("h")), Some(10), Some("Success"), Map.empty),
        MatchEventRecord(3, "Corner", Some(PlayerId("p1")), None, Some(TeamId("h")), None, Some("Success"), Map("routine" -> "short")),
        MatchEventRecord(4, "Shot", Some(PlayerId("p3")), None, Some(TeamId("h")), Some(11), None, Map.empty)
      )
      val act = AdvancedAnalytics.setPieceZoneActivityFromEvents(events)
      assertTrue(act.contains("Corner:default"), act("Corner:default").getOrElse(10, 0) >= 1, act.contains("Corner:short"), act("Corner:short").getOrElse(11, 0) >= 1)
    },
    test("playerTortuosityFromZoneSequences returns tortuosity per player") {
      val pid = PlayerId("p1")
      val straight = Map(pid -> List(1, 2, 3, 4))
      val tort = AdvancedAnalytics.playerTortuosityFromZoneSequences(straight)
      assertTrue(tort.contains(pid), tort(pid) >= 1.0)
      val zigzag = Map(pid -> List(1, 6, 12))
      val tort2 = AdvancedAnalytics.playerTortuosityFromZoneSequences(zigzag)
      assertTrue(tort2(pid) >= 1.0)
    },
    test("setPiecePatternsNMF returns W and H for activity map") {
      val activity = Map("Corner:default" -> (1 to PitchModel.TotalZones).map(z => z -> (if (z <= 12) 2 else 0)).toMap, "FreeKick:default" -> (1 to PitchModel.TotalZones).map(z => z -> (if (z >= 13) 2 else 0)).toMap)
      val (w, h) = AdvancedAnalytics.setPiecePatternsNMF(activity, 2, 20)
      assertTrue(w.nonEmpty, h.size == 2, w.keys.forall(activity.contains))
    },
    test("setPieceRoutineClusters returns cluster id per routine") {
      val activity = Map("Corner:a" -> Map(1 -> 5, 2 -> 3), "Corner:b" -> Map(10 -> 4, 11 -> 5), "FreeKick:x" -> Map(3 -> 2))
      val clusters = AdvancedAnalytics.setPieceRoutineClusters(activity, 2)
      assertTrue(clusters.size == activity.size, clusters.values.forall(v => v == 0 || v == 1))
    },
    test("poissonPrognosis returns probabilities summing to ~1") {
      val (pH, pD, pA) = AdvancedAnalytics.poissonPrognosis(1.5, 1.2)
      assertTrue(pH >= 0.0 && pH <= 1.0, pD >= 0.0 && pD <= 1.0, pA >= 0.0 && pA <= 1.0, (pH + pD + pA) > 0.95 && (pH + pD + pA) < 1.05)
    },
    test("poissonPrognosis sum equals 1.0 within tolerance (property)") {
      val lambdas = List((0.5, 0.5), (1.0, 1.0), (2.0, 1.5), (0.1, 0.1))
      val sums = lambdas.map { case (h, a) =>
        val (pH, pD, pA) = AdvancedAnalytics.poissonPrognosis(h, a)
        pH + pD + pA
      }
      assertTrue(sums.forall(s => s > 0.999 && s < 1.001))
    },
    test("voronoiZoneFromCentroids returns 0 or 1 per zone") {
      val homeId = TeamId("h")
      val awayId = TeamId("a")
      val events = List(
        MatchEventRecord(1, "Pass", Some(PlayerId("p1")), None, Some(homeId), Some(1), Some("Success"), Map.empty),
        MatchEventRecord(2, "Pass", Some(PlayerId("p2")), None, Some(homeId), Some(1), Some("Success"), Map.empty),
        MatchEventRecord(3, "Pass", Some(PlayerId("p3")), None, Some(awayId), Some(12), Some("Success"), Map.empty)
      )
      val vor = AdvancedAnalytics.voronoiZoneFromCentroids(events, homeId, awayId)
      assertTrue(vor.size == PitchModel.TotalZones, vor.values.forall(v => v == 0.0 || v == 1.0 || v == 0.5))
    },
    test("xTValueIteration values are non-negative and finite (property)") {
      val nz = PitchModel.TotalZones
      val baseThreatBounded = (z: Int) => (z.toDouble / nz).min(1.0).max(0.0)
      val counts = Map((1, 2) -> 2, (2, 3) -> 2, (3, 1) -> 1, (nz - 1, nz) -> 3, (nz, nz - 1) -> 1)
      val xt = AdvancedAnalytics.xTValueIteration(counts, baseThreatBounded, gamma = 0.95)
      assertTrue(xt.size == nz, xt.values.forall(v => v >= 0.0 && v.isFinite))
    },
    test("xPassValueFromEvents returns passValueByPlayer, totals, under-pressure totals and byPlayerUnder") {
      val homeId = TeamId("h")
      val awayId = TeamId("a")
      val nz = PitchModel.TotalZones
      val xt = (1 to nz).map(z => z -> (z.toDouble / nz)).toMap
      val events = List(
        MatchEventRecord(1, "Pass", Some(PlayerId("p1")), None, Some(homeId), Some(3), Some("Success"), Map("receiverPressure" -> "0")),
        MatchEventRecord(2, "Pass", Some(PlayerId("p2")), None, Some(homeId), Some(8), Some("Success"), Map("receiverPressure" -> "3")),
        MatchEventRecord(3, "Dribble", Some(PlayerId("p3")), None, Some(homeId), Some(9), Some("Success"), Map.empty)
      )
      val (byPlayer, (homeT, awayT), (homeUnder, awayUnder), byPlayerUnder) = AdvancedAnalytics.xPassValueFromEvents(events, xt, homeId, awayId)
      assertTrue(byPlayer.nonEmpty, homeUnder <= homeT + 1e-6, awayUnder == 0.0)
      assertTrue(byPlayerUnder.get(PlayerId("p2")).exists(_ >= 0.0), byPlayerUnder.get(PlayerId("p1")).forall(_ == 0.0))
    }
  )
}
