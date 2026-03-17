package fmgame.backend.api

import fmgame.shared.api.MatchSummaryDto
import io.circe.parser.decode
import io.circe.syntax._
import zio.test._

object MatchSummaryDtoCodecSpec extends ZIOSpecDefault {

  private val minimalDto = MatchSummaryDto(
    possessionPercent = List(55.0, 45.0),
    homeGoals = 2,
    awayGoals = 1,
    shotsTotal = List(12, 8),
    shotsOnTarget = List(5, 3),
    shotsOffTarget = List(4, 3),
    shotsBlocked = List(3, 2),
    bigChances = List(2, 1),
    xgTotal = List(1.8, 1.0),
    passesTotal = List(450, 380),
    passesCompleted = List(400, 320),
    passAccuracyPercent = List(89.0, 84.0),
    passesInFinalThird = List(80, 60),
    crossesTotal = List(15, 10),
    crossesSuccessful = List(5, 3),
    longBallsTotal = List(20, 18),
    longBallsSuccessful = List(12, 10),
    tacklesTotal = List(18, 20),
    tacklesWon = List(12, 14),
    interceptions = List(10, 8),
    clearances = List(15, 12),
    blocks = List(4, 5),
    saves = List(2, 4),
    goalsConceded = List(1, 2),
    fouls = List(10, 12),
    yellowCards = List(2, 3),
    redCards = List(0, 0),
    foulsSuffered = List(12, 10),
    corners = List(5, 4),
    cornersWon = List(3, 2),
    throwIns = List(12, 10),
    freeKicksWon = List(8, 9),
    offsides = List(2, 1),
    duelsWon = Some(List(45, 40)),
    aerialDuelsWon = Some(List(10, 12)),
    possessionLost = Some(List(50, 60)),
    vaepTotal = Some(List(0.5, 0.3)),
    wpaFinal = Some(0.65),
    fieldTilt = Some(List(0.55, 0.45)),
    ppda = Some(List(8.2, 10.5)),
    ballTortuosity = Some(1.25),
    metabolicLoad = Some(5200.0),
    xtByZone = Some((1 to 12).map(_ * 0.05).toList),
    injuries = Some(List(0, 0)),
    homeShareByZone = Some((1 to 12).map(_ => 0.5).toList),
    vaepBreakdownByPlayer = Some(Map("p1" -> Map("Pass" -> 0.1), "p2" -> Map("Shot" -> 0.2))),
    pressingByPlayer = Some(Map("p1" -> 5, "p2" -> 3)),
    estimatedDistanceByPlayer = Some(Map("p1" -> 9800.0, "p2" -> 10200.0)),
    influenceByPlayer = Some(Map("p1" -> Map("1" -> 3), "p2" -> Map("2" -> 4))),
    avgDefendersInConeByZone = Some((1 to 12).map(_ => 1.5).toList),
    avgGkDistanceByZone = Some((1 to 12).map(_ => 9.0).toList),
    setPieceZoneActivity = Some(Map("Corner:default" -> List.fill(12)(1))),
    pressingInOppHalfByPlayer = Some(Map("p1" -> 7, "p2" -> 4)),
    playerTortuosityByPlayer = Some(Map("p1" -> 1.3, "p2" -> 1.1)),
    metabolicLoadByPlayer = Some(Map("p1" -> 5200.0, "p2" -> 5100.0)),
    iwpByPlayer = Some(Map("p1" -> 0.8, "p2" -> 0.4)),
    setPiecePatternW = Some(Map("Corner:default" -> List(0.6, 0.4))),
    setPiecePatternH = Some(List(Map("1" -> 0.2, "2" -> 0.1))),
    setPieceRoutineCluster = Some(Map("Corner:default" -> 1)),
    poissonPrognosis = Some(List(0.45, 0.28, 0.27)),
    voronoiCentroidByZone = Some((1 to 12).map(z => if (z <= 6) 1.0 else 0.0).toList),
    passValueByPlayer = Some(Map("p1" -> 0.1, "p2" -> 0.05)),
    passValueTotal = Some(List(0.8, 0.5)),
    passValueUnderPressureTotal = Some(List(0.2, 0.1)),
    passValueUnderPressureByPlayer = Some(Map("p2" -> 0.15)),
    influenceScoreByPlayer = Some(Map("p1" -> 1.2, "p2" -> 0.9)),
    highlights = Some(List(Map("minute" -> "12", "type" -> "Goal", "player" -> "p1")))
  )

  def spec = suite("MatchSummaryDtoCodec")(
    test("round-trip encode/decode preserves optional fields") {
      import MatchSummaryDtoCodec._
      val json = minimalDto.asJson.noSpaces
      val decoded = decode[MatchSummaryDto](json)
      assertTrue(decoded.isRight) &&
      assertTrue(decoded.exists(d =>
        d.homeGoals == minimalDto.homeGoals &&
        d.awayGoals == minimalDto.awayGoals &&
        d.possessionPercent == minimalDto.possessionPercent &&
        d.xgTotal == minimalDto.xgTotal &&
        d.fouls == minimalDto.fouls &&
        d.poissonPrognosis == minimalDto.poissonPrognosis &&
        d.passValueByPlayer == minimalDto.passValueByPlayer &&
        d.passValueUnderPressureTotal == minimalDto.passValueUnderPressureTotal &&
        d.passValueUnderPressureByPlayer == minimalDto.passValueUnderPressureByPlayer &&
        d.influenceScoreByPlayer == minimalDto.influenceScoreByPlayer &&
        d.highlights == minimalDto.highlights
      ))
    }
  )
}

