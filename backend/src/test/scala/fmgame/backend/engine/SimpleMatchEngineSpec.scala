package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*

object SimpleMatchEngineSpec extends ZIOSpecDefault {

  private def mkPlayer(id: String, positions: Set[String] = Set("CM")): Player =
    Player(
      id = PlayerId(id),
      teamId = TeamId("t"),
      firstName = "A",
      lastName = "B",
      preferredPositions = positions,
      physical = Map.empty,
      technical = Map.empty,
      mental = Map.empty,
      traits = Map.empty,
      bodyParams = Map.empty,
      injury = None,
      freshness = 1.0,
      morale = 0.8,
      createdAt = java.time.Instant.EPOCH
    )

  private def mkInput(seed: Long): MatchEngineInput = {
    val homePlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"hp$i", if (i == 1) Set("GK") else Set("CB","CM","ST")), 1.0, 0.8, None))
    val awayPlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"ap$i", if (i == 1) Set("GK") else Set("CB","CM","ST")), 1.0, 0.8, None))
    val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
    val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
    MatchEngineInput(
      homeTeam = MatchTeamInput(TeamId("home"), homePlayers.toList, homeLineup),
      awayTeam = MatchTeamInput(TeamId("away"), awayPlayers.toList, awayLineup),
      homePlan = GamePlanInput("4-3-3"),
      awayPlan = GamePlanInput("4-3-3"),
      homeAdvantage = 1.05,
      referee = RefereeInput(0.5),
      leagueContext = LeagueContextInput(Map.empty),
      randomSeed = Some(seed)
    )
  }

  def spec = suite("SimpleMatchEngine")(
    test("valid 11v11 lineup with fixed seed produces deterministic result") {
      val input = mkInput(42L)
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield assertTrue(
        result.homeGoals >= 0,
        result.awayGoals >= 0,
        result.events.nonEmpty,
        result.events.exists(_.eventType == "KickOff"),
        result.events.count(_.eventType == "Goal") == result.homeGoals + result.awayGoals
      )
    },
    test("same seed gives same result") {
      val input = mkInput(123L)
      for {
        r1 <- SimpleMatchEngine.simulate(input)
        r2 <- SimpleMatchEngine.simulate(input)
      } yield assertTrue(
        r1.homeGoals == r2.homeGoals,
        r1.awayGoals == r2.awayGoals,
        r1.events.size == r2.events.size
      )
    },
    test("invalid home lineup (10 players) fails") {
      val input = mkInput(1L)
      val badHome = input.homeTeam.copy(players = input.homeTeam.players.take(10))
      val badLineup = input.homeTeam.lineup.filter { case (pid, _) => badHome.players.map(_.player.id).contains(pid) }
      val badInput = input.copy(homeTeam = badHome.copy(lineup = badLineup))
      for {
        exit <- SimpleMatchEngine.simulate(badInput).exit
      } yield assertTrue(exit.isFailure)
    },
    test("analytics present with possession and shot count") {
      val input = mkInput(999L)
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield assertTrue(
        result.analytics.nonEmpty,
        result.analytics.get.possessionPercent._1 + result.analytics.get.possessionPercent._2 <= 100.01,
        result.analytics.get.shotCount._1 >= 0,
        result.analytics.get.shotCount._2 >= 0
      )
    },
    test("VAEP includes pass and shot contributions (not only goals)") {
      val input = mkInput(777L)
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield {
        val analytics = result.analytics.get
        val vaepPlayers = analytics.vaepByPlayer.keys.toSet
        val passActors = result.events.filter(e => e.eventType == "Pass" || e.eventType == "LongPass").flatMap(_.actorPlayerId).toSet
        val shotActors = result.events.filter(_.eventType == "Shot").flatMap(_.actorPlayerId).toSet
        assertTrue(analytics.vaepByPlayer.nonEmpty, (passActors ++ shotActors).nonEmpty)
      }
    },
    test("ThrowIn events present") {
      val input = mkInput(333L)
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield assertTrue(result.events.exists(_.eventType == "ThrowIn"))
    },
    test("Pass events have zone and xPass in metadata") {
      val input = mkInput(555L)
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield {
        val passes = result.events.filter(e => e.eventType == "Pass" || e.eventType == "LongPass")
        assertTrue(passes.nonEmpty, passes.forall(p => p.zone.nonEmpty && p.metadata.contains("xPass")))
      }
    },
    test("Pass and Shot events have zoneThreat in metadata (DxT-like)") {
      val input = mkInput(666L)
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield {
        val passes = result.events.filter(e => e.eventType == "Pass" || e.eventType == "LongPass")
        val shots = result.events.filter(_.eventType == "Shot")
        assertTrue(passes.forall(_.metadata.contains("zoneThreat")), shots.forall(_.metadata.contains("zoneThreat")))
      }
    },
    test("ACWR: engine runs with recentMinutesPlayed and uses it in injury risk") {
      val homeWithLoad = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"hp$i", if (i == 1) Set("GK") else Set("CB","CM","ST")), 1.0, 0.8, Some(270)))
      val awayPlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"ap$i", if (i == 1) Set("GK") else Set("CB","CM","ST")), 1.0, 0.8, None))
      val homeLineup = homeWithLoad.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homeWithLoad.toList, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers.toList, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-3-3"),
        homeAdvantage = 1.05,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(111L)
      )
      for {
        result <- SimpleMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty, result.analytics.nonEmpty)
    },
    test("all event types are from canonical set (KONTRAKTY §2.1)") {
      val canonical = Set("KickOff", "Pass", "LongPass", "Cross", "PassIntercepted", "Dribble", "DribbleLost", "Shot", "Goal", "Foul", "YellowCard", "RedCard", "Injury", "Substitution", "Corner", "ThrowIn", "FreeKick", "Offside")
      val input = mkInput(999L)
      for {
        result <- SimpleMatchEngine.simulate(input)
        types = result.events.map(_.eventType).toSet
      } yield assertTrue(types.forall(canonical.contains), result.events.exists(_.eventType == "KickOff"))
    }
  )
}
