package fmgame.backend.api

import fmgame.shared.api.MatchSummaryDto
import io.circe.{Decoder, Encoder, HCursor, Json}

/** Ręczne codec dla MatchSummaryDto, aby uniknąć przekroczenia limitu inliningu przy generic.auto. */
object MatchSummaryDtoCodec {
  private def arrD(l: List[Double]): Json = Json.arr(l.map(d => Json.fromDoubleOrNull(d))*)
  private def arrI(l: List[Int]): Json = Json.arr(l.map(Json.fromInt)*)

  private def decodeListInt(c: HCursor, key: String): Decoder.Result[List[Int]] =
    c.downField(key).as[List[Int]]
  private def decodeListDouble(c: HCursor, key: String): Decoder.Result[List[Double]] =
    c.downField(key).as[List[Double]]
  /** Domyślne listy [0, 0] dla pól wymaganych – kompatybilność wsteczna ze starym JSON. */
  private def decodeListIntOrDefault(c: HCursor, key: String, default: List[Int] = List(0, 0)): Decoder.Result[List[Int]] =
    c.downField(key).as[List[Int]].orElse(Right(default))
  private def decodeListDoubleOrDefault(c: HCursor, key: String, default: List[Double] = List(0.0, 0.0)): Decoder.Result[List[Double]] =
    c.downField(key).as[List[Double]].orElse(Right(default))

  implicit val decodeMatchSummaryDto: Decoder[MatchSummaryDto] = (c: HCursor) =>
    for {
      possessionPercent <- decodeListDoubleOrDefault(c, "possessionPercent")
      homeGoals <- c.get[Int]("homeGoals").orElse(Right(0))
      awayGoals <- c.get[Int]("awayGoals").orElse(Right(0))
      shotsTotal <- decodeListIntOrDefault(c, "shotsTotal")
      shotsOnTarget <- decodeListIntOrDefault(c, "shotsOnTarget")
      shotsOffTarget <- decodeListIntOrDefault(c, "shotsOffTarget")
      shotsBlocked <- decodeListIntOrDefault(c, "shotsBlocked")
      bigChances <- decodeListIntOrDefault(c, "bigChances")
      xgTotal <- decodeListDoubleOrDefault(c, "xgTotal")
      passesTotal <- decodeListIntOrDefault(c, "passesTotal")
      passesCompleted <- decodeListIntOrDefault(c, "passesCompleted")
      passAccuracyPercent <- decodeListDoubleOrDefault(c, "passAccuracyPercent")
      passesInFinalThird <- decodeListIntOrDefault(c, "passesInFinalThird")
      crossesTotal <- decodeListIntOrDefault(c, "crossesTotal")
      crossesSuccessful <- decodeListIntOrDefault(c, "crossesSuccessful")
      longBallsTotal <- decodeListIntOrDefault(c, "longBallsTotal")
      longBallsSuccessful <- decodeListIntOrDefault(c, "longBallsSuccessful")
      tacklesTotal <- decodeListIntOrDefault(c, "tacklesTotal")
      tacklesWon <- decodeListIntOrDefault(c, "tacklesWon")
      interceptions <- decodeListIntOrDefault(c, "interceptions")
      clearances <- decodeListIntOrDefault(c, "clearances")
      blocks <- decodeListIntOrDefault(c, "blocks")
      saves <- decodeListIntOrDefault(c, "saves")
      goalsConceded <- decodeListIntOrDefault(c, "goalsConceded")
      fouls <- decodeListIntOrDefault(c, "fouls")
      yellowCards <- decodeListIntOrDefault(c, "yellowCards")
      redCards <- decodeListIntOrDefault(c, "redCards")
      foulsSuffered <- decodeListIntOrDefault(c, "foulsSuffered")
      corners <- decodeListIntOrDefault(c, "corners")
      cornersWon <- decodeListIntOrDefault(c, "cornersWon")
      throwIns <- decodeListIntOrDefault(c, "throwIns")
      freeKicksWon <- decodeListIntOrDefault(c, "freeKicksWon")
      offsides <- decodeListIntOrDefault(c, "offsides")
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

  implicit val encodeMatchSummaryDto: Encoder[MatchSummaryDto] = s =>
    Json.obj(
      "possessionPercent" -> arrD(s.possessionPercent),
      "homeGoals" -> Json.fromInt(s.homeGoals),
      "awayGoals" -> Json.fromInt(s.awayGoals),
      "shotsTotal" -> arrI(s.shotsTotal),
      "shotsOnTarget" -> arrI(s.shotsOnTarget),
      "shotsOffTarget" -> arrI(s.shotsOffTarget),
      "shotsBlocked" -> arrI(s.shotsBlocked),
      "bigChances" -> arrI(s.bigChances),
      "xgTotal" -> arrD(s.xgTotal),
      "passesTotal" -> arrI(s.passesTotal),
      "passesCompleted" -> arrI(s.passesCompleted),
      "passAccuracyPercent" -> arrD(s.passAccuracyPercent),
      "passesInFinalThird" -> arrI(s.passesInFinalThird),
      "crossesTotal" -> arrI(s.crossesTotal),
      "crossesSuccessful" -> arrI(s.crossesSuccessful),
      "longBallsTotal" -> arrI(s.longBallsTotal),
      "longBallsSuccessful" -> arrI(s.longBallsSuccessful),
      "tacklesTotal" -> arrI(s.tacklesTotal),
      "tacklesWon" -> arrI(s.tacklesWon),
      "interceptions" -> arrI(s.interceptions),
      "clearances" -> arrI(s.clearances),
      "blocks" -> arrI(s.blocks),
      "saves" -> arrI(s.saves),
      "goalsConceded" -> arrI(s.goalsConceded),
      "fouls" -> arrI(s.fouls),
      "yellowCards" -> arrI(s.yellowCards),
      "redCards" -> arrI(s.redCards),
      "foulsSuffered" -> arrI(s.foulsSuffered),
      "corners" -> arrI(s.corners),
      "cornersWon" -> arrI(s.cornersWon),
      "throwIns" -> arrI(s.throwIns),
      "freeKicksWon" -> arrI(s.freeKicksWon),
      "offsides" -> arrI(s.offsides),
      "duelsWon" -> s.duelsWon.fold(Json.Null)(arrI),
      "aerialDuelsWon" -> s.aerialDuelsWon.fold(Json.Null)(arrI),
      "possessionLost" -> s.possessionLost.fold(Json.Null)(arrI),
      "vaepTotal" -> s.vaepTotal.fold(Json.Null)(arrD),
      "wpaFinal" -> s.wpaFinal.fold(Json.Null)(Json.fromDoubleOrNull),
      "fieldTilt" -> s.fieldTilt.fold(Json.Null)(arrD),
      "ppda" -> s.ppda.fold(Json.Null)(arrD),
      "ballTortuosity" -> s.ballTortuosity.fold(Json.Null)(Json.fromDoubleOrNull),
      "metabolicLoad" -> s.metabolicLoad.fold(Json.Null)(Json.fromDoubleOrNull),
      "xtByZone" -> s.xtByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "injuries" -> s.injuries.fold(Json.Null)(arrI),
      "homeShareByZone" -> s.homeShareByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "vaepBreakdownByPlayer" -> s.vaepBreakdownByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (pid, et) => pid -> Json.fromFields(et.map { case (k, v) => k -> Json.fromDoubleOrNull(v) }) })),
      "pressingByPlayer" -> s.pressingByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromInt(v) })),
      "estimatedDistanceByPlayer" -> s.estimatedDistanceByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "influenceByPlayer" -> s.influenceByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (pid, zones) => pid -> Json.fromFields(zones.map { case (z, c) => z -> Json.fromInt(c) }) })),
      "avgDefendersInConeByZone" -> s.avgDefendersInConeByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "avgGkDistanceByZone" -> s.avgGkDistanceByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "setPieceZoneActivity" -> s.setPieceZoneActivity.fold(Json.Null)(m => Json.fromFields(m.map { case (k, list) => k -> Json.arr(list.map(Json.fromInt)*) })),
      "pressingInOppHalfByPlayer" -> s.pressingInOppHalfByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromInt(v) })),
      "playerTortuosityByPlayer" -> s.playerTortuosityByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "metabolicLoadByPlayer" -> s.metabolicLoadByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "iwpByPlayer" -> s.iwpByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "setPiecePatternW" -> s.setPiecePatternW.fold(Json.Null)(m => Json.fromFields(m.map { case (k, list) => k -> Json.arr(list.map(Json.fromDoubleOrNull)*) })),
      "setPiecePatternH" -> s.setPiecePatternH.fold(Json.Null)(l => Json.arr(l.map(m => Json.fromFields(m.map { case (z, v) => z -> Json.fromDoubleOrNull(v) }))*)),
      "setPieceRoutineCluster" -> s.setPieceRoutineCluster.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromInt(v) })),
      "poissonPrognosis" -> s.poissonPrognosis.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "voronoiCentroidByZone" -> s.voronoiCentroidByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "passValueByPlayer" -> s.passValueByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "passValueTotal" -> s.passValueTotal.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "passValueUnderPressureTotal" -> s.passValueUnderPressureTotal.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "passValueUnderPressureByPlayer" -> s.passValueUnderPressureByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "influenceScoreByPlayer" -> s.influenceScoreByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) }))
    )
}
