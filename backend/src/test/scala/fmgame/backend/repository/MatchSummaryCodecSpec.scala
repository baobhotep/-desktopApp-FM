package fmgame.backend.repository

import fmgame.backend.domain.MatchSummary
import io.circe.parser.decode
import io.circe.syntax._
import zio.test._
import MatchSummaryCodec._

object MatchSummaryCodecSpec extends ZIOSpecDefault {

  private val minimalSummary = MatchSummary(
    possessionPercent = (55.0, 45.0),
    homeGoals = 2,
    awayGoals = 1,
    shotsTotal = (12, 8),
    shotsOnTarget = (5, 3),
    shotsOffTarget = (4, 3),
    shotsBlocked = (3, 2),
    bigChances = (2, 1),
    xgTotal = (1.8, 1.0),
    passesTotal = (450, 380),
    passesCompleted = (400, 320),
    passAccuracyPercent = (89.0, 84.0),
    passesInFinalThird = (80, 60),
    crossesTotal = (15, 10),
    crossesSuccessful = (5, 3),
    longBallsTotal = (20, 18),
    longBallsSuccessful = (12, 10),
    tacklesTotal = (18, 20),
    tacklesWon = (12, 14),
    interceptions = (10, 8),
    clearances = (15, 12),
    blocks = (4, 5),
    saves = (2, 4),
    goalsConceded = (1, 2),
    fouls = (10, 12),
    yellowCards = (2, 3),
    redCards = (0, 0),
    foulsSuffered = (12, 10),
    corners = (5, 4),
    cornersWon = (3, 2),
    throwIns = (12, 10),
    freeKicksWon = (8, 9),
    offsides = (2, 1),
    duelsWon = Some((45, 40)),
    aerialDuelsWon = Some((10, 12)),
    possessionLost = Some((50, 60)),
    vaepTotal = Some((0.5, 0.3)),
    wpaFinal = Some(0.65),
    ballTortuosity = Some(1.25),
    metabolicLoad = Some(5200.0),
    xtByZone = Some((1 to 12).map(_ * 0.05).toList),
    injuries = (0, 0),
    homeShareByZone = Some((1 to 12).map(_ => 0.5).toList),
    poissonPrognosis = Some((0.45, 0.28, 0.27)),
    voronoiCentroidByZone = Some((1 to 12).map(z => if (z <= 6) 1.0 else 0.0).toList),
    passValueByPlayer = Some(Map("p1" -> 0.1, "p2" -> 0.05)),
    passValueTotal = Some((0.8, 0.5)),
    passValueUnderPressureTotal = Some((0.2, 0.1)),
    passValueUnderPressureByPlayer = Some(Map("p2" -> 0.15)),
    influenceScoreByPlayer = Some(Map("p1" -> 1.2, "p2" -> 0.9))
  )

  def spec = suite("MatchSummaryCodec")(
    test("round-trip encode/decode preserves optional analytics fields") {
      val json = minimalSummary.asJson.noSpaces
      val decoded = decode[MatchSummary](json)
      assertTrue(decoded.isRight, decoded.exists(s => s.poissonPrognosis == minimalSummary.poissonPrognosis))
      assertTrue(decoded.exists(s => s.voronoiCentroidByZone == minimalSummary.voronoiCentroidByZone))
      assertTrue(decoded.exists(s => s.passValueByPlayer == minimalSummary.passValueByPlayer))
      assertTrue(decoded.exists(s => s.passValueUnderPressureTotal == minimalSummary.passValueUnderPressureTotal))
      assertTrue(decoded.exists(s => s.passValueUnderPressureByPlayer == minimalSummary.passValueUnderPressureByPlayer))
      assertTrue(decoded.exists(s => s.influenceScoreByPlayer == minimalSummary.influenceScoreByPlayer))
    }
  )
}
