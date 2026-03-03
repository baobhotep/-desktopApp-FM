package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*

/**
 * Aggregates MatchEventRecord list into MatchSummary (KONTRAKTY §2.3).
 * Odniesienie do realnych statystyk (Premier League / top ligi): ~10–16 strzałów na drużynę na mecz,
 * ~400–550 podań na drużynę, posiadanie 45–55%, xG łącznie ~2.5–3.5 na mecz. Silnik (FullMatchEngine)
 * generuje ~25–35 strzałów i ~2.5–3.5 bramki łącznie po korekcie częstości strzałów (eventTypeRoll < 0.445).
 */
object MatchSummaryAggregator {

  def fromEvents(
    events: List[MatchEventRecord],
    homeTeamId: TeamId,
    awayTeamId: TeamId,
    homeGoals: Int,
    awayGoals: Int
  ): MatchSummary = {
    def isHome(tid: Option[TeamId]) = tid.contains(homeTeamId)
    def isAway(tid: Option[TeamId]) = tid.contains(awayTeamId)
    def add(acc: (Int, Int), tid: Option[TeamId], n: Int = 1): (Int, Int) =
      if (isHome(tid)) (acc._1 + n, acc._2) else if (isAway(tid)) (acc._1, acc._2 + n) else acc

    var shotsTotal = (0, 0)
    var shotsOnTarget = (0, 0)
    var xgHome = 0.0
    var xgAway = 0.0
    var passesTotal = (0, 0)
    var passesCompleted = (0, 0)
    var crossesTotal = (0, 0)
    var crossesSuccessful = (0, 0)
    var fouls = (0, 0)
    var yellowCards = (0, 0)
    var redCards = (0, 0)
    var corners = (0, 0)
    var throwIns = (0, 0)
    var freeKicksWon = (0, 0)
    var offsides = (0, 0)
    var longBallsTotal = (0, 0)
    var longBallsSuccessful = (0, 0)
    var interceptions = (0, 0)
    var shotsBlocked = (0, 0)
    var passesInFinalThirdH, passesInFinalThirdA = 0
    var bigChancesH, bigChancesA = 0
    var savesH, savesA = 0
    var possessionLostH, possessionLostA = 0
    var tacklesTotalH, tacklesTotalA = 0
    var tacklesWonH, tacklesWonA = 0
    var clearancesH, clearancesA = 0
    var duelsWonH, duelsWonA = 0
    var aerialDuelsWonH, aerialDuelsWonA = 0
    var injuriesH, injuriesA = 0

    events.foreach { e =>
      val tid = e.teamId
      val zone = e.zone.getOrElse(0)
      e.eventType match {
        case "Goal" =>
          shotsTotal = add(shotsTotal, tid)
          shotsOnTarget = add(shotsOnTarget, tid)
          val xg = e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.5)
          if (isHome(tid)) { xgHome += xg; bigChancesH += 1 } else if (isAway(tid)) { xgAway += xg; bigChancesA += 1 }
        case "Shot" =>
          shotsTotal = add(shotsTotal, tid)
          if (e.outcome.contains("Saved") || e.outcome.contains("Blocked")) shotsOnTarget = add(shotsOnTarget, tid)
          if (e.outcome.contains("Blocked")) shotsBlocked = add(shotsBlocked, tid)
          if (e.outcome.contains("Saved")) {
            if (isAway(tid)) savesH += 1 else if (isHome(tid)) savesA += 1
          }
          val xg = e.metadata.get("xG").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.0)
          if (isHome(tid)) { xgHome += xg; if (xg >= 0.3) bigChancesH += 1 } else if (isAway(tid)) { xgAway += xg; if (xg >= 0.3) bigChancesA += 1 }
        case "Pass" | "LongPass" =>
          passesTotal = add(passesTotal, tid)
          if (e.outcome.contains("Success")) passesCompleted = add(passesCompleted, tid)
          if (zone >= 9) { if (isHome(tid)) passesInFinalThirdH += 1 else if (isAway(tid)) passesInFinalThirdA += 1 }
          if (e.eventType == "LongPass") {
            longBallsTotal = add(longBallsTotal, tid)
            if (e.outcome.contains("Success")) longBallsSuccessful = add(longBallsSuccessful, tid)
          }
        case "PassIntercepted" =>
          interceptions = add(interceptions, tid)
          if (tid.contains(awayTeamId)) possessionLostH += 1 else if (tid.contains(homeTeamId)) possessionLostA += 1
        case "DribbleLost" =>
          if (isHome(tid)) possessionLostH += 1 else if (isAway(tid)) possessionLostA += 1
        case "Tackle" =>
          if (isHome(tid)) { tacklesTotalH += 1; if (e.outcome.contains("Won")) tacklesWonH += 1 }
          else if (isAway(tid)) { tacklesTotalA += 1; if (e.outcome.contains("Won")) tacklesWonA += 1 }
        case "Clearance" =>
          if (isHome(tid)) clearancesH += 1 else if (isAway(tid)) clearancesA += 1
        case "Duel" =>
          if (isHome(tid)) duelsWonH += 1 else if (isAway(tid)) duelsWonA += 1
        case "AerialDuel" =>
          if (isHome(tid)) aerialDuelsWonH += 1 else if (isAway(tid)) aerialDuelsWonA += 1
        case "Cross" =>
          crossesTotal = add(crossesTotal, tid)
          if (e.outcome.contains("Success")) crossesSuccessful = add(crossesSuccessful, tid)
        case "Foul" => fouls = add(fouls, tid)
        case "YellowCard" => yellowCards = add(yellowCards, tid)
        case "RedCard" => redCards = add(redCards, tid)
        case "Corner" => corners = add(corners, tid)
        case "ThrowIn" => throwIns = add(throwIns, tid)
        case "FreeKick" => freeKicksWon = add(freeKicksWon, tid)
        case "Offside" => offsides = add(offsides, tid)
        case "Injury" => if (isHome(tid)) injuriesH += 1 else if (isAway(tid)) injuriesA += 1
        case _ =>
      }
    }

    val blocksMadeByHome = shotsBlocked._2
    val blocksMadeByAway = shotsBlocked._1
    val blocks = (blocksMadeByHome, blocksMadeByAway)
    val saves = (savesH, savesA)

    val totalShots = shotsTotal._1 + shotsTotal._2
    val passAcc = if (passesTotal._1 + passesTotal._2 > 0) {
      val pctH = if (passesTotal._1 > 0) 100.0 * passesCompleted._1 / passesTotal._1 else 0.0
      val pctA = if (passesTotal._2 > 0) 100.0 * passesCompleted._2 / passesTotal._2 else 0.0
      (pctH, pctA)
    } else (0.0, 0.0)
    val poss = if (totalShots + passesTotal._1 + passesTotal._2 > 0) {
      val homeAct = passesTotal._1 + shotsTotal._1
      val awayAct = passesTotal._2 + shotsTotal._2
      val tot = homeAct + awayAct
      if (tot > 0) (100.0 * homeAct / tot, 100.0 * awayAct / tot) else (50.0, 50.0)
    } else (50.0, 50.0)

    MatchSummary(
      possessionPercent = poss,
      homeGoals = homeGoals,
      awayGoals = awayGoals,
      shotsTotal = shotsTotal,
      shotsOnTarget = shotsOnTarget,
      shotsOffTarget = (shotsTotal._1 - shotsOnTarget._1, shotsTotal._2 - shotsOnTarget._2),
      shotsBlocked = shotsBlocked,
      bigChances = (bigChancesH, bigChancesA),
      xgTotal = (xgHome, xgAway),
      passesTotal = passesTotal,
      passesCompleted = passesCompleted,
      passAccuracyPercent = passAcc,
      passesInFinalThird = (passesInFinalThirdH, passesInFinalThirdA),
      crossesTotal = crossesTotal,
      crossesSuccessful = crossesSuccessful,
      longBallsTotal = longBallsTotal,
      longBallsSuccessful = longBallsSuccessful,
      tacklesTotal = (tacklesTotalH, tacklesTotalA),
      tacklesWon = (tacklesWonH, tacklesWonA),
      interceptions = interceptions,
      clearances = (clearancesH, clearancesA),
      blocks = blocks,
      saves = saves,
      goalsConceded = (awayGoals, homeGoals),
      fouls = fouls,
      yellowCards = yellowCards,
      redCards = redCards,
      foulsSuffered = (fouls._2, fouls._1),
      corners = corners,
      cornersWon = corners,
      throwIns = throwIns,
      freeKicksWon = freeKicksWon,
      offsides = offsides,
      duelsWon = Some((duelsWonH, duelsWonA)),
      aerialDuelsWon = Some((aerialDuelsWonH, aerialDuelsWonA)),
      possessionLost = Some((possessionLostH, possessionLostA)),
      vaepTotal = None,
      wpaFinal = None,
      injuries = (injuriesH, injuriesA)
    )
  }
}
