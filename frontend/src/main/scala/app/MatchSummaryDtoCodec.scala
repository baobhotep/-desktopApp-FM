package app

import fmgame.shared.api._
import io.circe.{Decoder, HCursor}

/** Ręczny Decoder dla MatchSummaryDto (pełna struktura), aby uniknąć limitu inliningu przy generic.auto. */
object MatchSummaryDtoCodec {
  private def decodeListInt(c: HCursor, key: String): Decoder.Result[List[Int]] =
    c.downField(key).as[List[Int]]
  private def decodeListDouble(c: HCursor, key: String): Decoder.Result[List[Double]] =
    c.downField(key).as[List[Double]]

  implicit val decodeMatchSummaryDto: Decoder[MatchSummaryDto] = (c: HCursor) =>
    for {
      possessionPercent <- decodeListDouble(c, "possessionPercent")
      homeGoals <- c.get[Int]("homeGoals")
      awayGoals <- c.get[Int]("awayGoals")
      shotsTotal <- decodeListInt(c, "shotsTotal")
      shotsOnTarget <- decodeListInt(c, "shotsOnTarget")
      shotsOffTarget <- decodeListInt(c, "shotsOffTarget")
      shotsBlocked <- decodeListInt(c, "shotsBlocked")
      bigChances <- decodeListInt(c, "bigChances")
      xgTotal <- decodeListDouble(c, "xgTotal")
      passesTotal <- decodeListInt(c, "passesTotal")
      passesCompleted <- decodeListInt(c, "passesCompleted")
      passAccuracyPercent <- decodeListDouble(c, "passAccuracyPercent")
      passesInFinalThird <- decodeListInt(c, "passesInFinalThird")
      crossesTotal <- decodeListInt(c, "crossesTotal")
      crossesSuccessful <- decodeListInt(c, "crossesSuccessful")
      longBallsTotal <- decodeListInt(c, "longBallsTotal")
      longBallsSuccessful <- decodeListInt(c, "longBallsSuccessful")
      tacklesTotal <- decodeListInt(c, "tacklesTotal")
      tacklesWon <- decodeListInt(c, "tacklesWon")
      interceptions <- decodeListInt(c, "interceptions")
      clearances <- decodeListInt(c, "clearances")
      blocks <- decodeListInt(c, "blocks")
      saves <- decodeListInt(c, "saves")
      goalsConceded <- decodeListInt(c, "goalsConceded")
      fouls <- decodeListInt(c, "fouls")
      yellowCards <- decodeListInt(c, "yellowCards")
      redCards <- decodeListInt(c, "redCards")
      foulsSuffered <- decodeListInt(c, "foulsSuffered")
      corners <- decodeListInt(c, "corners")
      cornersWon <- decodeListInt(c, "cornersWon")
      throwIns <- decodeListInt(c, "throwIns")
      freeKicksWon <- decodeListInt(c, "freeKicksWon")
      offsides <- decodeListInt(c, "offsides")
    } yield {
      val duelsWon = c.downField("duelsWon").as[List[Int]].toOption
      val aerialDuelsWon = c.downField("aerialDuelsWon").as[List[Int]].toOption
      val possessionLost = c.downField("possessionLost").as[List[Int]].toOption
      val vaepTotal = c.downField("vaepTotal").as[List[Double]].toOption
      val wpaFinal = c.downField("wpaFinal").as[Double].toOption
      val fieldTilt = c.downField("fieldTilt").as[List[Double]].toOption
      val ppda = c.downField("ppda").as[List[Double]].toOption
      val ballTortuosity = c.downField("ballTortuosity").as[Double].toOption
      val metabolicLoad = c.downField("metabolicLoad").as[Double].toOption
      val xtByZone = c.downField("xtByZone").as[List[Double]].toOption
      val injuries = c.downField("injuries").as[List[Int]].toOption
      val homeShareByZone = c.downField("homeShareByZone").as[List[Double]].toOption
      val vaepBreakdownByPlayer = c.downField("vaepBreakdownByPlayer").as[Map[String, Map[String, Double]]].toOption
      val pressingByPlayer = c.downField("pressingByPlayer").as[Map[String, Int]].toOption
      val estimatedDistanceByPlayer = c.downField("estimatedDistanceByPlayer").as[Map[String, Double]].toOption
      val influenceByPlayer = c.downField("influenceByPlayer").as[Map[String, Map[String, Int]]].toOption
      val avgDefendersInConeByZone = c.downField("avgDefendersInConeByZone").as[List[Double]].toOption
      val avgGkDistanceByZone = c.downField("avgGkDistanceByZone").as[List[Double]].toOption
      val setPieceZoneActivity = c.downField("setPieceZoneActivity").as[Map[String, List[Int]]].toOption
      val pressingInOppHalfByPlayer = c.downField("pressingInOppHalfByPlayer").as[Map[String, Int]].toOption
      val playerTortuosityByPlayer = c.downField("playerTortuosityByPlayer").as[Map[String, Double]].toOption
      val metabolicLoadByPlayer = c.downField("metabolicLoadByPlayer").as[Map[String, Double]].toOption
      val iwpByPlayer = c.downField("iwpByPlayer").as[Map[String, Double]].toOption
      val setPiecePatternW = c.downField("setPiecePatternW").as[Map[String, List[Double]]].toOption
      val setPiecePatternH = c.downField("setPiecePatternH").as[List[Map[String, Double]]].toOption
      val setPieceRoutineCluster = c.downField("setPieceRoutineCluster").as[Map[String, Int]].toOption
      val poissonPrognosis = c.downField("poissonPrognosis").as[List[Double]].toOption
      val voronoiCentroidByZone = c.downField("voronoiCentroidByZone").as[List[Double]].toOption
      val passValueByPlayer = c.downField("passValueByPlayer").as[Map[String, Double]].toOption
      val passValueTotal = c.downField("passValueTotal").as[List[Double]].toOption
      val passValueUnderPressureTotal = c.downField("passValueUnderPressureTotal").as[List[Double]].toOption
      val passValueUnderPressureByPlayer = c.downField("passValueUnderPressureByPlayer").as[Map[String, Double]].toOption
      val influenceScoreByPlayer = c.downField("influenceScoreByPlayer").as[Map[String, Double]].toOption
      MatchSummaryDto(
        possessionPercent, homeGoals, awayGoals, shotsTotal, shotsOnTarget, shotsOffTarget, shotsBlocked, bigChances,
        xgTotal, passesTotal, passesCompleted, passAccuracyPercent, passesInFinalThird, crossesTotal, crossesSuccessful,
        longBallsTotal, longBallsSuccessful, tacklesTotal, tacklesWon, interceptions, clearances, blocks, saves, goalsConceded,
        fouls, yellowCards, redCards, foulsSuffered, corners, cornersWon, throwIns, freeKicksWon, offsides,
        duelsWon, aerialDuelsWon, possessionLost, vaepTotal, wpaFinal, fieldTilt, ppda, ballTortuosity, metabolicLoad, xtByZone, injuries, homeShareByZone,
        vaepBreakdownByPlayer, pressingByPlayer, estimatedDistanceByPlayer, influenceByPlayer,
        avgDefendersInConeByZone, avgGkDistanceByZone, setPieceZoneActivity, pressingInOppHalfByPlayer,
        playerTortuosityByPlayer, metabolicLoadByPlayer, iwpByPlayer, setPiecePatternW, setPiecePatternH, setPieceRoutineCluster, poissonPrognosis,
        voronoiCentroidByZone, passValueByPlayer, passValueTotal, passValueUnderPressureTotal, passValueUnderPressureByPlayer, influenceScoreByPlayer
      )
    }
}
