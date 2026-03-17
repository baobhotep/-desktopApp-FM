package fmgame.backend.repository

import fmgame.backend.domain.MatchSummary
import io.circe.{Decoder, Encoder, HCursor, Json}

/** Manual codec for MatchSummary to avoid generic derivation inlining limits. */
object MatchSummaryCodec {

  implicit val encodeMatchSummary: Encoder[MatchSummary] = s =>
    Json.obj(
      "possessionPercent" -> Json.arr(Json.fromDouble(s.possessionPercent._1).getOrElse(Json.Null), Json.fromDouble(s.possessionPercent._2).getOrElse(Json.Null)),
      "homeGoals" -> Json.fromInt(s.homeGoals),
      "awayGoals" -> Json.fromInt(s.awayGoals),
      "shotsTotal" -> Json.arr(Json.fromInt(s.shotsTotal._1), Json.fromInt(s.shotsTotal._2)),
      "shotsOnTarget" -> Json.arr(Json.fromInt(s.shotsOnTarget._1), Json.fromInt(s.shotsOnTarget._2)),
      "shotsOffTarget" -> Json.arr(Json.fromInt(s.shotsOffTarget._1), Json.fromInt(s.shotsOffTarget._2)),
      "shotsBlocked" -> Json.arr(Json.fromInt(s.shotsBlocked._1), Json.fromInt(s.shotsBlocked._2)),
      "bigChances" -> Json.arr(Json.fromInt(s.bigChances._1), Json.fromInt(s.bigChances._2)),
      "xgTotal" -> Json.arr(Json.fromDouble(s.xgTotal._1).getOrElse(Json.Null), Json.fromDouble(s.xgTotal._2).getOrElse(Json.Null)),
      "passesTotal" -> Json.arr(Json.fromInt(s.passesTotal._1), Json.fromInt(s.passesTotal._2)),
      "passesCompleted" -> Json.arr(Json.fromInt(s.passesCompleted._1), Json.fromInt(s.passesCompleted._2)),
      "passAccuracyPercent" -> Json.arr(Json.fromDouble(s.passAccuracyPercent._1).getOrElse(Json.Null), Json.fromDouble(s.passAccuracyPercent._2).getOrElse(Json.Null)),
      "passesInFinalThird" -> Json.arr(Json.fromInt(s.passesInFinalThird._1), Json.fromInt(s.passesInFinalThird._2)),
      "crossesTotal" -> Json.arr(Json.fromInt(s.crossesTotal._1), Json.fromInt(s.crossesTotal._2)),
      "crossesSuccessful" -> Json.arr(Json.fromInt(s.crossesSuccessful._1), Json.fromInt(s.crossesSuccessful._2)),
      "longBallsTotal" -> Json.arr(Json.fromInt(s.longBallsTotal._1), Json.fromInt(s.longBallsTotal._2)),
      "longBallsSuccessful" -> Json.arr(Json.fromInt(s.longBallsSuccessful._1), Json.fromInt(s.longBallsSuccessful._2)),
      "tacklesTotal" -> Json.arr(Json.fromInt(s.tacklesTotal._1), Json.fromInt(s.tacklesTotal._2)),
      "tacklesWon" -> Json.arr(Json.fromInt(s.tacklesWon._1), Json.fromInt(s.tacklesWon._2)),
      "interceptions" -> Json.arr(Json.fromInt(s.interceptions._1), Json.fromInt(s.interceptions._2)),
      "clearances" -> Json.arr(Json.fromInt(s.clearances._1), Json.fromInt(s.clearances._2)),
      "blocks" -> Json.arr(Json.fromInt(s.blocks._1), Json.fromInt(s.blocks._2)),
      "saves" -> Json.arr(Json.fromInt(s.saves._1), Json.fromInt(s.saves._2)),
      "goalsConceded" -> Json.arr(Json.fromInt(s.goalsConceded._1), Json.fromInt(s.goalsConceded._2)),
      "fouls" -> Json.arr(Json.fromInt(s.fouls._1), Json.fromInt(s.fouls._2)),
      "yellowCards" -> Json.arr(Json.fromInt(s.yellowCards._1), Json.fromInt(s.yellowCards._2)),
      "redCards" -> Json.arr(Json.fromInt(s.redCards._1), Json.fromInt(s.redCards._2)),
      "foulsSuffered" -> Json.arr(Json.fromInt(s.foulsSuffered._1), Json.fromInt(s.foulsSuffered._2)),
      "corners" -> Json.arr(Json.fromInt(s.corners._1), Json.fromInt(s.corners._2)),
      "cornersWon" -> Json.arr(Json.fromInt(s.cornersWon._1), Json.fromInt(s.cornersWon._2)),
      "throwIns" -> Json.arr(Json.fromInt(s.throwIns._1), Json.fromInt(s.throwIns._2)),
      "freeKicksWon" -> Json.arr(Json.fromInt(s.freeKicksWon._1), Json.fromInt(s.freeKicksWon._2)),
      "offsides" -> Json.arr(Json.fromInt(s.offsides._1), Json.fromInt(s.offsides._2)),
      "duelsWon" -> s.duelsWon.fold(Json.Null)(p => Json.arr(Json.fromInt(p._1), Json.fromInt(p._2))),
      "aerialDuelsWon" -> s.aerialDuelsWon.fold(Json.Null)(p => Json.arr(Json.fromInt(p._1), Json.fromInt(p._2))),
      "possessionLost" -> s.possessionLost.fold(Json.Null)(p => Json.arr(Json.fromInt(p._1), Json.fromInt(p._2))),
      "vaepTotal" -> s.vaepTotal.fold(Json.Null)(p => Json.arr(Json.fromDouble(p._1).getOrElse(Json.Null), Json.fromDouble(p._2).getOrElse(Json.Null))),
      "wpaFinal" -> s.wpaFinal.fold(Json.Null)(d => Json.fromDouble(d).getOrElse(Json.Null)),
      "fieldTilt" -> s.fieldTilt.fold(Json.Null)(p => Json.arr(Json.fromDouble(p._1).getOrElse(Json.Null), Json.fromDouble(p._2).getOrElse(Json.Null))),
      "ppda" -> s.ppda.fold(Json.Null)(p => Json.arr(Json.fromDouble(p._1).getOrElse(Json.Null), Json.fromDouble(p._2).getOrElse(Json.Null))),
      "ballTortuosity" -> s.ballTortuosity.fold(Json.Null)(Json.fromDoubleOrNull),
      "metabolicLoad" -> s.metabolicLoad.fold(Json.Null)(Json.fromDoubleOrNull),
      "xtByZone" -> s.xtByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "injuries" -> Json.arr(Json.fromInt(s.injuries._1), Json.fromInt(s.injuries._2)),
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
      "poissonPrognosis" -> s.poissonPrognosis.fold(Json.Null)(t => Json.arr(Json.fromDoubleOrNull(t._1), Json.fromDoubleOrNull(t._2), Json.fromDoubleOrNull(t._3))),
      "voronoiCentroidByZone" -> s.voronoiCentroidByZone.fold(Json.Null)(l => Json.arr(l.map(Json.fromDoubleOrNull)*)),
      "passValueByPlayer" -> s.passValueByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "passValueTotal" -> s.passValueTotal.fold(Json.Null)(t => Json.arr(Json.fromDoubleOrNull(t._1), Json.fromDoubleOrNull(t._2))),
      "passValueUnderPressureTotal" -> s.passValueUnderPressureTotal.fold(Json.Null)(t => Json.arr(Json.fromDoubleOrNull(t._1), Json.fromDoubleOrNull(t._2))),
      "passValueUnderPressureByPlayer" -> s.passValueUnderPressureByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "influenceScoreByPlayer" -> s.influenceScoreByPlayer.fold(Json.Null)(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromDoubleOrNull(v) })),
      "highlights" -> s.highlights.fold(Json.Null)(list => Json.arr(list.map(m => Json.fromFields(m.map { case (k, v) => k -> Json.fromString(v) }))*))
    )

  private def decodePairInt(c: HCursor, key: String): Decoder.Result[(Int, Int)] =
    c.downField(key).as[Vector[Int]].map(v => (v.lift(0).getOrElse(0), v.lift(1).getOrElse(0)))
  private def decodePairDouble(c: HCursor, key: String): Decoder.Result[(Double, Double)] =
    c.downField(key).as[Vector[Double]].map(v => (v.lift(0).getOrElse(0.0), v.lift(1).getOrElse(0.0)))

  implicit val decodeMatchSummary: Decoder[MatchSummary] = (c: HCursor) =>
    for {
      possessionPercent <- decodePairDouble(c, "possessionPercent").orElse(Right((50.0, 50.0)))
      homeGoals <- c.get[Int]("homeGoals").orElse(Right(0))
      awayGoals <- c.get[Int]("awayGoals").orElse(Right(0))
      shotsTotal <- decodePairInt(c, "shotsTotal").orElse(Right((0, 0)))
      shotsOnTarget <- decodePairInt(c, "shotsOnTarget").orElse(Right((0, 0)))
      shotsOffTarget <- decodePairInt(c, "shotsOffTarget").orElse(Right((0, 0)))
      shotsBlocked <- decodePairInt(c, "shotsBlocked").orElse(Right((0, 0)))
      bigChances <- decodePairInt(c, "bigChances").orElse(Right((0, 0)))
      xgTotal <- decodePairDouble(c, "xgTotal").orElse(Right((0.0, 0.0)))
      passesTotal <- decodePairInt(c, "passesTotal").orElse(Right((0, 0)))
      passesCompleted <- decodePairInt(c, "passesCompleted").orElse(Right((0, 0)))
      passAccuracyPercent <- decodePairDouble(c, "passAccuracyPercent").orElse(Right((0.0, 0.0)))
      passesInFinalThird <- decodePairInt(c, "passesInFinalThird").orElse(Right((0, 0)))
      crossesTotal <- decodePairInt(c, "crossesTotal").orElse(Right((0, 0)))
      crossesSuccessful <- decodePairInt(c, "crossesSuccessful").orElse(Right((0, 0)))
      longBallsTotal <- decodePairInt(c, "longBallsTotal").orElse(Right((0, 0)))
      longBallsSuccessful <- decodePairInt(c, "longBallsSuccessful").orElse(Right((0, 0)))
      tacklesTotal <- decodePairInt(c, "tacklesTotal").orElse(Right((0, 0)))
      tacklesWon <- decodePairInt(c, "tacklesWon").orElse(Right((0, 0)))
      interceptions <- decodePairInt(c, "interceptions").orElse(Right((0, 0)))
      clearances <- decodePairInt(c, "clearances").orElse(Right((0, 0)))
      blocks <- decodePairInt(c, "blocks").orElse(Right((0, 0)))
      saves <- decodePairInt(c, "saves").orElse(Right((0, 0)))
      goalsConceded <- decodePairInt(c, "goalsConceded").orElse(Right((0, 0)))
      fouls <- decodePairInt(c, "fouls").orElse(Right((0, 0)))
      yellowCards <- decodePairInt(c, "yellowCards").orElse(Right((0, 0)))
      redCards <- decodePairInt(c, "redCards").orElse(Right((0, 0)))
      foulsSuffered <- decodePairInt(c, "foulsSuffered").orElse(Right((0, 0)))
      corners <- decodePairInt(c, "corners").orElse(Right((0, 0)))
      cornersWon <- decodePairInt(c, "cornersWon").orElse(Right((0, 0)))
      throwIns <- decodePairInt(c, "throwIns").orElse(Right((0, 0)))
      freeKicksWon <- decodePairInt(c, "freeKicksWon").orElse(Right((0, 0)))
      offsides <- decodePairInt(c, "offsides").orElse(Right((0, 0)))
      duelsWon <- Right(c.downField("duelsWon").as[Vector[Int]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      aerialDuelsWon <- Right(c.downField("aerialDuelsWon").as[Vector[Int]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      possessionLost <- Right(c.downField("possessionLost").as[Vector[Int]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      vaepTotal <- Right(c.downField("vaepTotal").as[Vector[Double]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      wpaFinal <- Right(c.downField("wpaFinal").as[Double].toOption)
      fieldTilt <- Right(c.downField("fieldTilt").as[Vector[Double]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      ppda <- Right(c.downField("ppda").as[Vector[Double]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      ballTortuosity <- Right(c.downField("ballTortuosity").as[Double].toOption)
      metabolicLoad <- Right(c.downField("metabolicLoad").as[Double].toOption)
      xtByZone <- Right(c.downField("xtByZone").as[List[Double]].toOption.filter(_.nonEmpty))
      injuries <- decodePairInt(c, "injuries").orElse(Right((0, 0)))
      homeShareByZone <- Right(c.downField("homeShareByZone").as[List[Double]].toOption.filter(_.nonEmpty))
      vaepBreakdownByPlayer <- Right(c.downField("vaepBreakdownByPlayer").as[Map[String, Map[String, Double]]].toOption)
      pressingByPlayer <- Right(c.downField("pressingByPlayer").as[Map[String, Int]].toOption)
      estimatedDistanceByPlayer <- Right(c.downField("estimatedDistanceByPlayer").as[Map[String, Double]].toOption)
      influenceByPlayer <- Right(c.downField("influenceByPlayer").as[Map[String, Map[String, Int]]].toOption)
      avgDefendersInConeByZone <- Right(c.downField("avgDefendersInConeByZone").as[List[Double]].toOption.filter(_.nonEmpty))
      avgGkDistanceByZone <- Right(c.downField("avgGkDistanceByZone").as[List[Double]].toOption.filter(_.nonEmpty))
      setPieceZoneActivity <- Right(c.downField("setPieceZoneActivity").as[Map[String, List[Int]]].toOption)
      pressingInOppHalfByPlayer <- Right(c.downField("pressingInOppHalfByPlayer").as[Map[String, Int]].toOption)
      playerTortuosityByPlayer <- Right(c.downField("playerTortuosityByPlayer").as[Map[String, Double]].toOption)
      metabolicLoadByPlayer <- Right(c.downField("metabolicLoadByPlayer").as[Map[String, Double]].toOption)
      iwpByPlayer <- Right(c.downField("iwpByPlayer").as[Map[String, Double]].toOption)
      setPiecePatternW <- Right(c.downField("setPiecePatternW").as[Map[String, List[Double]]].toOption)
      setPiecePatternH <- Right(c.downField("setPiecePatternH").as[List[Map[String, Double]]].toOption)
      setPieceRoutineCluster <- Right(c.downField("setPieceRoutineCluster").as[Map[String, Int]].toOption)
      poissonPrognosis <- Right(c.downField("poissonPrognosis").as[Vector[Double]].toOption.flatMap(v => if (v.size >= 3) Some((v(0), v(1), v(2))) else None))
      voronoiCentroidByZone <- Right(c.downField("voronoiCentroidByZone").as[List[Double]].toOption.filter(_.nonEmpty))
      passValueByPlayer <- Right(c.downField("passValueByPlayer").as[Map[String, Double]].toOption)
      passValueTotal <- Right(c.downField("passValueTotal").as[Vector[Double]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      passValueUnderPressureTotal <- Right(c.downField("passValueUnderPressureTotal").as[Vector[Double]].toOption.flatMap(v => if (v.size >= 2) Some((v(0), v(1))) else None))
      passValueUnderPressureByPlayer <- Right(c.downField("passValueUnderPressureByPlayer").as[Map[String, Double]].toOption)
      influenceScoreByPlayer <- Right(c.downField("influenceScoreByPlayer").as[Map[String, Double]].toOption)
      highlights <- Right(c.downField("highlights").as[List[Map[String, String]]].toOption)
    } yield MatchSummary(
      possessionPercent, homeGoals, awayGoals, shotsTotal, shotsOnTarget, shotsOffTarget, shotsBlocked, bigChances, xgTotal,
      passesTotal, passesCompleted, passAccuracyPercent, passesInFinalThird, crossesTotal, crossesSuccessful, longBallsTotal, longBallsSuccessful,
      tacklesTotal, tacklesWon, interceptions, clearances, blocks, saves, goalsConceded,
      fouls, yellowCards, redCards, foulsSuffered, corners, cornersWon, throwIns, freeKicksWon, offsides,
      duelsWon, aerialDuelsWon, possessionLost, vaepTotal, wpaFinal, fieldTilt, ppda, ballTortuosity, metabolicLoad, xtByZone, injuries, homeShareByZone,
      vaepBreakdownByPlayer, pressingByPlayer, estimatedDistanceByPlayer, influenceByPlayer,
      avgDefendersInConeByZone, avgGkDistanceByZone, setPieceZoneActivity, pressingInOppHalfByPlayer,
      playerTortuosityByPlayer, metabolicLoadByPlayer, iwpByPlayer, setPiecePatternW, setPiecePatternH, setPieceRoutineCluster, poissonPrognosis,
      voronoiCentroidByZone, passValueByPlayer, passValueTotal, passValueUnderPressureTotal, passValueUnderPressureByPlayer, influenceScoreByPlayer, highlights
    )
}
