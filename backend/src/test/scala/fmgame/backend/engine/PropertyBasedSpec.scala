package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*

/**
 * Property-based tests for engine subsystems.
 * Uses ZIO Test Gen to generate random inputs and verify invariants.
 */
object PropertyBasedSpec extends ZIOSpecDefault {

  private val genZone: Gen[Any, Int] = Gen.int(1, PitchModel.TotalZones)
  private val genPositive: Gen[Any, Double] = Gen.double(0.01, 100.0)
  private val genNormalized: Gen[Any, Double] = Gen.double(0.0, 1.0)
  private val genAttribute: Gen[Any, Int] = Gen.int(1, 20)
  private val genSeed: Gen[Any, Long] = Gen.long(1L, 999999L)

  private def mkPlayer(id: String, positions: Set[String] = Set("CM")): Player =
    Player(
      id = PlayerId(id), teamId = TeamId("t"), firstName = "A", lastName = "B",
      preferredPositions = positions,
      physical = Map("pace" -> 12, "stamina" -> 12, "acceleration" -> 12),
      technical = Map("passing" -> 12, "shooting" -> 12),
      mental = Map("composure" -> 12, "decisions" -> 12),
      traits = Map.empty, bodyParams = Map.empty,
      injury = None, freshness = 1.0, morale = 0.8,
      createdAt = java.time.Instant.EPOCH
    )

  private def mkInput(seed: Long): MatchEngineInput = {
    val homePlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"hp$i", if (i == 1) Set("GK") else Set("CB", "CM", "ST")), 1.0, 0.8, None))
    val awayPlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"ap$i", if (i == 1) Set("GK") else Set("CB", "CM", "ST")), 1.0, 0.8, None))
    val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
    val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
    MatchEngineInput(
      homeTeam = MatchTeamInput(TeamId("home"), homePlayers.toList, homeLineup),
      awayTeam = MatchTeamInput(TeamId("away"), awayPlayers.toList, awayLineup),
      homePlan = GamePlanInput("4-3-3"),
      awayPlan = GamePlanInput("4-4-2"),
      homeAdvantage = 1.05,
      referee = RefereeInput(0.5),
      leagueContext = LeagueContextInput(Map.empty),
      randomSeed = Some(seed)
    )
  }

  def spec = suite("PropertyBased")(

    suite("PitchModel zone invariants")(
      test("every zone has column in [0, Cols) and row in [0, Rows)") {
        check(genZone) { z =>
          assertTrue(
            PitchModel.column(z) >= 0,
            PitchModel.column(z) < PitchModel.Cols,
            PitchModel.row(z) >= 0,
            PitchModel.row(z) < PitchModel.Rows
          )
        }
      },
      test("isAttackingThird(z, home) == isDefensiveThird(z, !home) for all zones") {
        check(genZone) { z =>
          assertTrue(
            PitchModel.isAttackingThird(z, true) == PitchModel.isDefensiveThird(z, false),
            PitchModel.isAttackingThird(z, false) == PitchModel.isDefensiveThird(z, true)
          )
        }
      },
      test("attackProgress(z, home) + attackProgress(z, !home) == 1.0 for all zones") {
        check(genZone) { z =>
          val sum = PitchModel.attackProgress(z, true) + PitchModel.attackProgress(z, false)
          assertTrue(math.abs(sum - 1.0) < 1e-9)
        }
      },
      test("zoneFromXY always returns a valid zone in [1, TotalZones]") {
        check(Gen.double(0.0, PitchModel.PitchLength), Gen.double(0.0, PitchModel.PitchWidth)) { (x, y) =>
          val z = PitchModel.zoneFromXY(x, y)
          assertTrue(z >= 1, z <= PitchModel.TotalZones)
        }
      },
      test("each zone has a center inside the pitch") {
        check(genZone) { z =>
          val (cx, cy) = PitchModel.zoneCenters(z)
          assertTrue(cx >= 0.0, cx <= PitchModel.PitchLength, cy >= 0.0, cy <= PitchModel.PitchWidth)
        }
      }
    ),

    suite("FormulaBasedxG invariants")(
      test("xG always in [0.01, 0.95] for any ShotContext") {
        check(genZone, genPositive, Gen.boolean, Gen.int(0, 90)) { (zone, dist, isHeader, minute) =>
          val ctx = ShotContext(zone, dist, isHeader, minute, 0, pressureCount = 2, goalAngle = 0.3, isHome = true)
          val xg = FormulaBasedxG.xGForShot(ctx)
          assertTrue(xg >= 0.01, xg <= 0.95)
        }
      },
      test("xG decreases with distance (monotonicity at fixed zone)") {
        check(genZone) { zone =>
          val close = FormulaBasedxG.xGForShot(ShotContext(zone, 5.0, false, 50, 0, isHome = true))
          val far = FormulaBasedxG.xGForShot(ShotContext(zone, 35.0, false, 50, 0, isHome = true))
          assertTrue(close >= far)
        }
      },
      test("weak foot reduces xG") {
        check(genZone) { zone =>
          val strong = FormulaBasedxG.xGForShot(ShotContext(zone, 15.0, false, 50, 0, isWeakFoot = false, isHome = true))
          val weak = FormulaBasedxG.xGForShot(ShotContext(zone, 15.0, false, 50, 0, isWeakFoot = true, isHome = true))
          assertTrue(strong >= weak)
        }
      },
      test("header reduces xG") {
        check(genZone) { zone =>
          val foot = FormulaBasedxG.xGForShot(ShotContext(zone, 10.0, false, 50, 0, isHome = true))
          val head = FormulaBasedxG.xGForShot(ShotContext(zone, 10.0, true, 50, 0, isHome = true))
          assertTrue(foot >= head)
        }
      }
    ),

    suite("PitchControl invariants")(
      test("home + away control sums to 1.0 for every zone") {
        val homePos = (1 to 11).map(i => PlayerPosition(PlayerId(s"h$i"), 30.0 + i * 5, 20.0 + i * 3, 1)).toList
        val awayPos = (1 to 11).map(i => PlayerPosition(PlayerId(s"a$i"), 70.0 - i * 4, 50.0 - i * 3, 12)).toList
        val control = PitchControl.controlByZone(homePos, awayPos)
        check(genZone) { z =>
          val (h, a) = control.getOrElse(z, (0.5, 0.5))
          assertTrue(math.abs(h + a - 1.0) < 1e-9)
        }
      },
      test("control values are in [0, 1]") {
        val homePos = (1 to 11).map(i => PlayerPosition(PlayerId(s"h$i"), 20.0 + i * 7, 10.0 + i * 5, 1)).toList
        val awayPos = (1 to 11).map(i => PlayerPosition(PlayerId(s"a$i"), 80.0 - i * 5, 55.0 - i * 4, 12)).toList
        val control = PitchControl.controlByZone(homePos, awayPos)
        check(genZone) { z =>
          val (h, a) = control.getOrElse(z, (0.5, 0.5))
          assertTrue(h >= 0.0, h <= 1.0, a >= 0.0, a <= 1.0)
        }
      }
    ),

    suite("Nash equilibrium invariants")(
      test("Nash penalty probabilities in [0, 1]") {
        check(genNormalized, genNormalized, genNormalized, genNormalized) { (ll, lr, rl, rr) =>
          val (ps, pg) = AdvancedAnalytics.nashPenalty2x2(ll, lr, rl, rr)
          assertTrue(ps >= 0.0, ps <= 1.0, pg >= 0.0, pg <= 1.0)
        }
      }
    ),

    suite("Poisson prognosis invariants")(
      test("probabilities sum to ~1.0 for realistic xG range") {
        check(Gen.double(0.1, 3.5), Gen.double(0.1, 3.5)) { (xgH, xgA) =>
          val (pH, pD, pA) = AdvancedAnalytics.poissonPrognosis(xgH, xgA)
          val sum = pH + pD + pA
          assertTrue(sum > 0.99, sum < 1.01, pH >= 0.0, pD >= 0.0, pA >= 0.0)
        }
      },
      test("equal xG yields symmetric home/away probabilities") {
        check(Gen.double(0.5, 3.0)) { lambda =>
          val (pH, pD, pA) = AdvancedAnalytics.poissonPrognosis(lambda, lambda)
          assertTrue(math.abs(pH - pA) < 0.001, pD >= 0.0, pH >= 0.0, pA >= 0.0)
        }
      }
    ),

    suite("DxT invariants")(
      test("base zone threat increases with column across all zones") {
        check(genZone, genZone) { (z1, z2) =>
          val c1 = PitchModel.column(z1)
          val c2 = PitchModel.column(z2)
          if (c1 < c2) assertTrue(DxT.baseZoneThreat(z2, true) >= DxT.baseZoneThreat(z1, true))
          else if (c1 > c2) assertTrue(DxT.baseZoneThreat(z1, true) >= DxT.baseZoneThreat(z2, true))
          else assertTrue(DxT.baseZoneThreat(z1, true) == DxT.baseZoneThreat(z2, true))
        }
      }
    ),

    suite("FullMatchEngine simulation invariants")(
      test("goals count == Goal event count for any seed") {
        check(genSeed) { seed =>
          for {
            result <- FullMatchEngine.simulate(mkInput(seed))
          } yield assertTrue(
            result.events.count(_.eventType == "Goal") == result.homeGoals + result.awayGoals
          )
        }
      },
      test("events are sorted by minute for any seed") {
        check(genSeed) { seed =>
          for {
            result <- FullMatchEngine.simulate(mkInput(seed))
          } yield {
            val minutes = result.events.map(_.minute)
            assertTrue(minutes == minutes.sorted)
          }
        }
      },
      test("analytics always present and xG non-negative") {
        check(genSeed) { seed =>
          for {
            result <- FullMatchEngine.simulate(mkInput(seed))
          } yield {
            assertTrue(result.analytics.isDefined) &&
            (result.analytics match {
              case Some(a) => assertTrue(a.xgTotal._1 >= 0.0, a.xgTotal._2 >= 0.0)
              case None    => assertTrue(false)
            })
          }
        }
      },
      test("possession percentages sum to ~100%") {
        check(genSeed) { seed =>
          for {
            result <- FullMatchEngine.simulate(mkInput(seed))
          } yield {
            assertTrue(result.analytics.isDefined) &&
            (result.analytics match {
              case Some(a) =>
                val sum = a.possessionPercent._1 + a.possessionPercent._2
                assertTrue(sum > 90.0, sum < 110.0)
              case None => assertTrue(false)
            })
          }
        }
      },
      test("first event is always KickOff") {
        check(genSeed) { seed =>
          for {
            result <- FullMatchEngine.simulate(mkInput(seed))
          } yield assertTrue(result.events.nonEmpty, result.events.headOption.map(_.eventType).contains("KickOff"))
        }
      }
    ),

    suite("FormulaBasedVAEP invariants")(
      test("Goal always has positive VAEP") {
        check(genZone, genAttribute) { (zone, minute) =>
          val ctx = VAEPContext("Goal", zone, Some("Success"), minute, 0, 0, Some(TeamId("h")), true)
          val v = FormulaBasedVAEP.valueForEvent(ctx)
          assertTrue(v > 0.0)
        }
      },
      test("Successful pass has positive VAEP, failed pass negative") {
        check(genZone) { zone =>
          val success = FormulaBasedVAEP.valueForEvent(VAEPContext("Pass", zone, Some("Success"), 50, 0, 0, Some(TeamId("h")), true))
          val fail = FormulaBasedVAEP.valueForEvent(VAEPContext("Pass", zone, Some("Miss"), 50, 0, 0, Some(TeamId("h")), true))
          assertTrue(success > 0.0, fail < 0.0)
        }
      }
    ),

    suite("MatchupMatrix invariants")(
      test("dynamicPressureTotal in [0, 1]") {
        val defenders = (1 to 5).map(i => PlayerPosition(PlayerId(s"d$i"), 50.0 + i * 5, 34.0, i)).toList
        val tackling = defenders.map(d => d.playerId -> 12).toMap
        val acceleration = defenders.map(d => d.playerId -> 12).toMap
        check(genZone) { zone =>
          val p = MatchupMatrix.dynamicPressureTotal(zone, defenders, tackling, acceleration)
          assertTrue(p >= 0.0, p <= 1.0)
        }
      },
      test("pressureInZone in [0, 1]") {
        val homePos = (1 to 11).map(i => PlayerPosition(PlayerId(s"h$i"), 50.0, 34.0, 12)).toList
        val awayPos = (1 to 11).map(i => PlayerPosition(PlayerId(s"a$i"), 50.0, 34.0, 1)).toList
        check(genZone) { zone =>
          val p = MatchupMatrix.pressureInZone(homePos, awayPos, zone, true)
          assertTrue(p >= 0.0, p <= 1.0)
        }
      }
    )
  ) @@ TestAspect.withLiveClock @@ TestAspect.timed
}
