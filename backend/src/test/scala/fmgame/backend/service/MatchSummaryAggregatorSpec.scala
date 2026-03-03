package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*

object MatchSummaryAggregatorSpec extends ZIOSpecDefault {

  val homeId = TeamId("home")
  val awayId = TeamId("away")
  val pidHome = PlayerId("ph1")
  val pidAway = PlayerId("pa1")

  def event(minute: Int, eventType: String, teamId: Option[TeamId], zone: Option[Int] = None, outcome: Option[String] = Some("Success"), metadata: Map[String, String] = Map.empty): MatchEventRecord =
    MatchEventRecord(minute, eventType, Some(if (teamId.contains(homeId)) pidHome else pidAway), None, teamId, zone, outcome, metadata)

  def spec = suite("MatchSummaryAggregator")(
    test("empty events yields 50-50 possession and zero stats") {
      val summary = MatchSummaryAggregator.fromEvents(Nil, homeId, awayId, 0, 0)
      assertTrue(
        summary.homeGoals == 0,
        summary.awayGoals == 0,
        summary.possessionPercent == (50.0, 50.0),
        summary.shotsTotal == (0, 0),
        summary.passesTotal == (0, 0),
        summary.fouls == (0, 0)
      )
    },
    test("KickOff + two goals (home, away) set goals and shots") {
      val events = List(
        MatchEventRecord(0, "KickOff", None, None, Some(homeId), None, Some("Success"), Map.empty),
        event(12, "Goal", Some(homeId), Some(10), Some("Success"), Map("xG" -> "0.4")),
        event(67, "Goal", Some(awayId), Some(11), Some("Success"), Map("xG" -> "0.35"))
      )
      val summary = MatchSummaryAggregator.fromEvents(events, homeId, awayId, 1, 1)
      assertTrue(
        summary.homeGoals == 1,
        summary.awayGoals == 1,
        summary.shotsTotal == (1, 1),
        summary.shotsOnTarget == (1, 1),
        summary.xgTotal._1 >= 0.3,
        summary.xgTotal._2 >= 0.3
      )
    },
    test("passes and passesInFinalThird (zone >= 9)") {
      val events = List(
        event(5, "Pass", Some(homeId), Some(3), Some("Success"), Map()),
        event(6, "Pass", Some(homeId), Some(10), Some("Success"), Map()),
        event(7, "Pass", Some(awayId), Some(9), Some("Success"), Map()),
        event(8, "LongPass", Some(awayId), Some(5), Some("Missed"), Map())
      )
      val summary = MatchSummaryAggregator.fromEvents(events, homeId, awayId, 0, 0)
      assertTrue(
        summary.passesTotal == (2, 2),
        summary.passesCompleted == (2, 1),
        summary.passesInFinalThird == (1, 1),
        summary.longBallsTotal == (0, 1),
        summary.longBallsSuccessful == (0, 0)
      )
    },
    test("Tackle, Clearance, Duel, AerialDuel, PassIntercepted, DribbleLost") {
      val events = List(
        event(10, "Tackle", Some(homeId), Some(4), Some("Won"), Map()),
        event(11, "Tackle", Some(awayId), Some(7), None, Map()),
        event(12, "Clearance", Some(homeId), Some(2), Some("Success"), Map()),
        event(13, "Duel", Some(awayId), Some(5), Some("Won"), Map()),
        event(14, "AerialDuel", Some(homeId), Some(6), Some("Won"), Map()),
        event(15, "PassIntercepted", Some(awayId), Some(8), None, Map()),
        event(16, "DribbleLost", Some(homeId), Some(9), None, Map())
      )
      val summary = MatchSummaryAggregator.fromEvents(events, homeId, awayId, 0, 0)
      assertTrue(
        summary.tacklesTotal == (1, 1),
        summary.tacklesWon == (1, 0),
        summary.clearances == (1, 0),
        summary.duelsWon == Some((0, 1)),
        summary.aerialDuelsWon == Some((1, 0)),
        summary.interceptions == (0, 1),
        summary.possessionLost == Some((2, 0))
      )
    },
    test("Foul, YellowCard, Corner, ThrowIn, FreeKick, Offside, Cross") {
      val events = List(
        event(20, "Foul", Some(homeId), Some(5), Some("Success"), Map("IWP" -> "0.5")),
        event(21, "YellowCard", Some(homeId), None, None, Map.empty),
        event(25, "Corner", Some(awayId), None, Some("Success"), Map.empty),
        event(26, "ThrowIn", Some(homeId), Some(4), Some("Success"), Map.empty),
        event(30, "FreeKick", Some(awayId), Some(6), Some("Success"), Map.empty),
        event(35, "Offside", Some(homeId), None, None, Map.empty),
        event(40, "Cross", Some(awayId), Some(10), Some("Success"), Map("xPass" -> "0.4")),
        event(41, "Cross", Some(homeId), Some(9), Some("Missed"), Map.empty)
      )
      val summary = MatchSummaryAggregator.fromEvents(events, homeId, awayId, 0, 0)
      assertTrue(
        summary.fouls == (1, 0),
        summary.yellowCards == (1, 0),
        summary.corners == (0, 1),
        summary.throwIns == (1, 0),
        summary.freeKicksWon == (0, 1),
        summary.offsides == (1, 0),
        summary.crossesTotal == (1, 1),
        summary.crossesSuccessful == (0, 1)
      )
    },
    test("Injury events are counted per team") {
      val events = List(
        event(30, "Injury", Some(homeId), Some(5), None, Map("severity" -> "Light")),
        event(45, "Injury", Some(homeId), Some(8), None, Map.empty),
        event(60, "Injury", Some(awayId), Some(4), None, Map("returnMatchday" -> "2"))
      )
      val summary = MatchSummaryAggregator.fromEvents(events, homeId, awayId, 0, 0)
      assertTrue(summary.injuries == (2, 1))
    }
  )
}
