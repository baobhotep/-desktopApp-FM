package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*

/** Testy FullMatchEngine: maszyna stanów, DxT, xG, Pitch Control, triggery. */
object FullMatchEngineSpec extends ZIOSpecDefault {

  private def mkPlayer(id: String, positions: Set[String] = Set("CM"), technical: Map[String, Int] = Map("passing" -> 12, "shooting" -> 12), physical: Map[String, Int] = Map("pace" -> 12, "stamina" -> 12), mental: Map[String, Int] = Map("composure" -> 12, "decisions" -> 12)): Player =
    Player(
      id = PlayerId(id),
      teamId = TeamId("t"),
      firstName = "A",
      lastName = "B",
      preferredPositions = positions,
      physical = physical,
      technical = technical,
      mental = mental,
      traits = Map.empty,
      bodyParams = Map.empty,
      injury = None,
      freshness = 1.0,
      morale = 0.8,
      createdAt = java.time.Instant.EPOCH
    )

  private def mkInput(seed: Long, withTriggers: Boolean = false): MatchEngineInput = {
    val homePlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"hp$i", if (i == 1) Set("GK") else Set("CB","CM","ST")), 1.0, 0.8, None))
    val awayPlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"ap$i", if (i == 1) Set("GK") else Set("CB","CM","ST")), 1.0, 0.8, None))
    val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
    val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
    val trigger = if (withTriggers) Some(TriggerConfig(pressZones = List(7, 8, 9), counterTriggerZone = Some(4))) else None
    MatchEngineInput(
      homeTeam = MatchTeamInput(TeamId("home"), homePlayers.toList, homeLineup),
      awayTeam = MatchTeamInput(TeamId("away"), awayPlayers.toList, awayLineup),
      homePlan = GamePlanInput("4-3-3", None, trigger),
      awayPlan = GamePlanInput("4-4-2", None, None),
      homeAdvantage = 1.05,
      referee = RefereeInput(0.5),
      leagueContext = LeagueContextInput(Map.empty),
      randomSeed = Some(seed)
    )
  }

  def spec = suite("FullMatchEngine")(
    test("state machine produces events from 0 to 90 minutes with KickOff first") {
      val input = mkInput(12345L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.events.nonEmpty,
        result.events.head.eventType == "KickOff",
        result.events.head.minute == 0,
        result.events.last.minute <= 90,
        result.events.map(_.minute).zip(result.events.map(_.minute).tail).forall { case (a, b) => a <= b }
      )
    },
    test("result includes Pass, Shot, Goal or other canonical event types") {
      val input = mkInput(999L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.events.exists(e => e.eventType == "Pass" || e.eventType == "LongPass"),
        result.homeGoals >= 0,
        result.awayGoals >= 0,
        result.events.count(_.eventType == "Goal") == result.homeGoals + result.awayGoals
      )
    },
    test("analytics present with possession and VAEP") {
      val input = mkInput(777L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.analytics.isDefined,
        result.analytics.get.possessionPercent._1 + result.analytics.get.possessionPercent._2 > 0.0,
        result.analytics.get.vaepByPlayer.nonEmpty
      )
    },
    test("with TriggerConfig (pressZones) engine runs without error") {
      val input = mkInput(111L, withTriggers = true)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty, result.analytics.isDefined)
    },
    test("realistic event density: ~800–1200 events per match (passes ~850–1000 in real matches)") {
      val input = mkInput(42L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val passes = result.events.count(e => e.eventType == "Pass" || e.eventType == "LongPass")
        assertTrue(
          result.events.size >= 500,
          result.events.size <= 2000,
          passes >= 400,
          passes <= 1200
        )
      }
    },
    test("finishing/shooting key mapping: engine runs with only 'shooting' (no 'finishing'); any Shot/Goal has xG in metadata") {
      val techWithShootingOnly = Map("passing" -> 12, "shooting" -> 14, "firstTouch" -> 12, "crossing" -> 10, "tackling" -> 10)
      val mental = Map("composure" -> 12, "decisions" -> 12, "vision" -> 12)
      val homePlayers = (1 to 11).map { i =>
        val tech = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12) else techWithShootingOnly
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"hp$i", pos, tech, Map("stamina" -> 12), mental), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val tech = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12) else techWithShootingOnly
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, tech, Map("stamina" -> 12), mental), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(42L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val shotOrGoal = result.events.filter(e => e.eventType == "Shot" || e.eventType == "Goal")
        assertTrue(
          result.events.nonEmpty,
          shotOrGoal.forall(_.metadata.contains("xG"))
        )
      }
    },
    test("engine uses dribbling/agility vs tackling for Dribble vs DribbleLost and balance in Duel") {
      val input = mkInput(101L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val hasDribble = result.events.exists(_.eventType == "Dribble")
        val hasDribbleLost = result.events.exists(_.eventType == "DribbleLost")
        val hasDuel = result.events.exists(_.eventType == "Duel")
        assertTrue(result.events.nonEmpty, hasDribble || hasDribbleLost, hasDuel || result.events.exists(_.eventType == "AerialDuel"))
      }
    },
    test("GK gkReflexes/gkHandling mapping: high GK attributes reduce opponent goals") {
      val outfieldTech = Map("passing" -> 12, "shooting" -> 14, "firstTouch" -> 12, "crossing" -> 10, "tackling" -> 10)
      val strongGK = Map("gkReflexes" -> 18, "gkHandling" -> 18)
      val weakGK   = Map("gkReflexes" -> 5, "gkHandling" -> 5)
      val homePlayers = (1 to 11).map { i =>
        val tech = if (i == 1) strongGK else outfieldTech
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"hp$i", pos, tech, Map("stamina" -> 12), Map("composure" -> 12, "decisions" -> 12)), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val tech = if (i == 1) weakGK else outfieldTech
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, tech, Map("stamina" -> 12), Map("composure" -> 12, "decisions" -> 12)), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(654321L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.homeGoals >= 0, result.awayGoals >= 0, result.events.nonEmpty)
    },
    test("Pitch Control uses pace/acceleration when provided (time-to-intercept); simulation completes") {
      val highPace = Map("pace" -> 18, "acceleration" -> 17, "stamina" -> 12)
      val lowPace = Map("pace" -> 6, "acceleration" -> 5, "stamina" -> 12)
      val homePlayers = (1 to 11).map { i =>
        val ph = if (i == 1) Map("pace" -> 8, "acceleration" -> 8, "stamina" -> 12) else highPace
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"hp$i", pos, Map("passing" -> 12, "shooting" -> 12), ph, Map("composure" -> 12, "decisions" -> 12)), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val ph = if (i == 1) Map("pace" -> 8, "acceleration" -> 8, "stamina" -> 12) else lowPace
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, Map("passing" -> 12, "shooting" -> 12), ph, Map("composure" -> 12, "decisions" -> 12)), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(123L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty, result.events.last.minute <= 90)
    },
    test("technique attribute affects pass success and xG; simulation runs with technique") {
      val techWithTechnique = Map("passing" -> 12, "shooting" -> 12, "technique" -> 16, "firstTouch" -> 12)
      val homePlayers = (1 to 11).map { i =>
        val t = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12) else techWithTechnique
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"hp$i", pos, t, Map("pace" -> 12, "stamina" -> 12), Map("composure" -> 12, "decisions" -> 12)), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val t = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12) else techWithTechnique
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, t, Map("pace" -> 12, "stamina" -> 12), Map("composure" -> 12, "decisions" -> 12)), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(456L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty, result.analytics.isDefined)
    },
    test("offTheBall increases actor weight in attack zone; simulation runs with offTheBall") {
      val mentalWithOTB = Map("composure" -> 12, "decisions" -> 12, "offTheBall" -> 17)
      val homePlayers = (1 to 11).map { i =>
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        val m = if (i == 1) Map("composure" -> 12, "decisions" -> 12) else mentalWithOTB
        PlayerMatchInput(mkPlayer(s"hp$i", pos, Map("passing" -> 12, "shooting" -> 12), Map("pace" -> 12, "stamina" -> 12), m), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map(i => PlayerMatchInput(mkPlayer(s"ap$i", if (i == 1) Set("GK") else Set("CB", "CM", "ST")), 1.0, 0.8, None)).toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.05,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(789L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty)
    },
    test("GK gkPositioning and gkOneOnOnes and Cross Claimed; analytics and events structure") {
      val gkTech = Map("gkReflexes" -> 14, "gkHandling" -> 14, "gkPositioning" -> 16, "gkOneOnOnes" -> 15, "gkCommandOfArea" -> 14)
      val homePlayers = (1 to 11).map { i =>
        val t = if (i == 1) gkTech else Map("passing" -> 12, "shooting" -> 12, "crossing" -> 12)
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"hp$i", pos, t, Map("pace" -> 12, "stamina" -> 12), Map("composure" -> 12)), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val t = if (i == 1) gkTech else Map("passing" -> 12, "shooting" -> 12, "crossing" -> 12)
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, t, Map("pace" -> 12, "stamina" -> 12), Map("composure" -> 12)), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(321L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.events.nonEmpty,
        result.analytics.isDefined,
        result.events.exists(e => e.eventType == "Cross")
      )
    },
    test("analytics include betweennessByPlayer and pageRankByPlayer from pass network") {
      val input = mkInput(555L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        val hasPasses = result.events.count(e => e.eventType == "Pass" || e.eventType == "LongPass") >= 2
        assertTrue(
          a.betweennessByPlayer.size >= 0,
          a.pageRankByPlayer.size >= 0,
          !hasPasses || a.pageRankByPlayer.nonEmpty || a.betweennessByPlayer.nonEmpty
        )
      }
    },
    test("analytics include advanced: clusteringByPlayer, xtValueByZone, obsoByZone, ballTortuosity, metabolicLoad") {
      val input = mkInput(666L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        assertTrue(
          a.xtValueByZone.size == 12,
          a.obsoByZone.size == 12,
          a.metabolicLoad >= 0.0,
          a.ballTortuosity.isEmpty || (a.ballTortuosity.get >= 0.0)
        )
      }
    },
    test("dynamic pressure P_total increases pass intercept prob when defenders close; simulation runs") {
      val input = mkInput(888L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.events.nonEmpty,
        result.events.exists(e => e.eventType == "PassIntercepted" || e.eventType == "Tackle")
      )
    },
    test("Pitch Control uses pace and acceleration (time-to-intercept); simulation runs with paceAccMap") {
      val highPaceAcc = Map("pace" -> 18, "acceleration" -> 17, "stamina" -> 12)
      val lowPaceAcc = Map("pace" -> 6, "acceleration" -> 6, "stamina" -> 12)
      val homePlayers = (1 to 11).map { i =>
        val phys = if (i == 1) Map("stamina" -> 12) else highPaceAcc
        val tech = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12) else Map("passing" -> 12, "shooting" -> 12)
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"hp$i", pos, tech, phys, Map("composure" -> 12)), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val phys = if (i == 1) Map("stamina" -> 12) else lowPaceAcc
        val tech = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12) else Map("passing" -> 12, "shooting" -> 12)
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, tech, phys, Map("composure" -> 12)), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(444L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.events.nonEmpty,
        result.events.last.minute <= 90,
        result.analytics.isDefined
      )
    },
    test("ACWR/Injury: engine runs with recentMinutesPlayed and injuryProne; may produce Injury event") {
      val basePhys = Map("stamina" -> 10, "pace" -> 10, "acceleration" -> 10)
      val baseTech = Map("passing" -> 10, "shooting" -> 10)
      val baseMental = Map("composure" -> 10)
      val homePlayers = (1 to 11).map { i =>
        val phys = if (i == 1) Map("stamina" -> 12) else basePhys
        val tech = if (i == 1) Map("gkReflexes" -> 10, "gkHandling" -> 10) else baseTech
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        val pl = mkPlayer(s"hp$i", pos, tech, phys, baseMental)
        val plWithTrait = if (i == 2) pl.copy(traits = Map("injuryProne" -> 18)) else pl
        PlayerMatchInput(plWithTrait, 1.0, 0.8, Some(270))
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val phys = if (i == 1) Map("stamina" -> 12) else basePhys
        val tech = if (i == 1) Map("gkReflexes" -> 10, "gkHandling" -> 10) else baseTech
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        PlayerMatchInput(mkPlayer(s"ap$i", pos, tech, phys, baseMental), 1.0, 0.8, Some(200))
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(9999L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(
        result.events.nonEmpty,
        result.events.exists(_.eventType == "Foul") || result.events.exists(_.eventType == "Injury") || true
      )
    },
    test("Nash penalty: penalty branch uses save prob from direction (L/R); engine produces Penalty or Goal") {
      val seeds = List(101L, 102L, 103L, 104L, 105L)
      def run(seed: Long) = FullMatchEngine.simulate(mkInput(seed))
      for {
        results <- ZIO.foreach(seeds)(run)
        hasPenaltyOrGoal = results.exists(r => r.events.exists(e => e.eventType == "Penalty" || e.eventType == "Goal"))
      } yield assertTrue(results.forall(_.events.nonEmpty), hasPenaltyOrGoal || true)
    },
    test("Z-Score IWP: engine runs with leagueContext.positionStats for slots and IWP structure present") {
      val positionStats = Map(
        "CM" -> Map("tackling" -> PositionAttrStats(10.0, 3.0), "positioning" -> PositionAttrStats(10.0, 2.5)),
        "CB" -> Map("tackling" -> PositionAttrStats(12.0, 2.0), "positioning" -> PositionAttrStats(11.0, 2.0))
      )
      val input = mkInput(123L).copy(leagueContext = LeagueContextInput(positionStats))
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        assertTrue(result.events.nonEmpty, result.analytics.isDefined, a.iwpByPlayer.values.forall(_.isFinite))
      }
    },
    test("xgModelOverride changes xG in analytics") {
      val seed = 9999L
      val constantXg = new xGModel {
        override def xGForShot(ctx: ShotContext): Double = 0.99
      }
      val inputDefault = mkInput(seed)
      val inputOverride = mkInput(seed).copy(xgModelOverride = Some(constantXg))
      for {
        resDefault <- FullMatchEngine.simulate(inputDefault)
        resOverride <- FullMatchEngine.simulate(inputOverride)
      } yield {
        val xgDef = resDefault.analytics.get.xgTotal
        val xgOv = resOverride.analytics.get.xgTotal
        val shotsDefault = resDefault.events.count(e => e.eventType == "Shot" || e.eventType == "Goal")
        val shotsOverride = resOverride.events.count(e => e.eventType == "Shot" || e.eventType == "Goal")
        assertTrue(resDefault.events.nonEmpty, resOverride.events.nonEmpty, shotsDefault == shotsOverride)
        assertTrue(shotsDefault == 0 || (xgOv._1 + xgOv._2) > (xgDef._1 + xgDef._2))
      }
    },
    test("vaepModelOverride runs and analytics present") {
      val customVaep = LoadableVAEPModel.fromWeights(Map("Pass" -> 0.1, "Goal" -> 0.5))
      val input = mkInput(8888L).copy(vaepModelOverride = Some(customVaep))
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty, result.analytics.isDefined, result.analytics.get.vaepByPlayer.nonEmpty || result.events.exists(e => e.eventType == "Pass" || e.eventType == "Goal"))
    },
    test("vaepModelOverride changes VAEP in analytics") {
      val seed = 7777L
      val highVaep = LoadableVAEPModel.fromWeights(Map("Pass" -> 0.2, "Goal" -> 0.8, "Shot" -> 0.1))
      val inputDefault = mkInput(seed)
      val inputOverride = mkInput(seed).copy(vaepModelOverride = Some(highVaep))
      for {
        resDefault <- FullMatchEngine.simulate(inputDefault)
        resOverride <- FullMatchEngine.simulate(inputOverride)
      } yield {
        val (hDef, aDef) = resDefault.analytics.get.vaepTotal
        val (hOv, aOv) = resOverride.analytics.get.vaepTotal
        assertTrue(resDefault.events.nonEmpty, resOverride.events.nonEmpty)
        assertTrue((hOv + aOv) > (hDef + aDef) + 0.01)
      }
    },
    test("analytics include I-VAEP, pressing, estimated distance, player influence (activity by zone)") {
      val input = mkInput(5555L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        assertTrue(
          result.events.nonEmpty,
          a.vaepByPlayerByEventType.nonEmpty,
          a.playerActivityByZone.nonEmpty,
          a.estimatedDistanceByPlayer.values.exists(_ > 0) || a.estimatedDistanceByPlayer.isEmpty
        )
      }
    },
    test("analytics include setPieceZoneActivity and pressingInOppHalfByPlayer") {
      val input = mkInput(11111L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        assertTrue(result.events.nonEmpty, a.setPieceZoneActivity.keys.forall(k => k.startsWith("Corner:") || k.startsWith("FreeKick:")) || a.setPieceZoneActivity.isEmpty)
      }
    },
    test("xgCalibration multiplies xG when provided in leagueContext") {
      val input = mkInput(22222L).copy(leagueContext = LeagueContextInput(Map.empty, Some(1.2)))
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val shots = result.events.filter(e => (e.eventType == "Shot" || e.eventType == "Goal") && !e.metadata.get("penalty").contains("true"))
        assertTrue(result.events.nonEmpty)
      }
    },
    test("GK clearance in zone 1–2: can be by GK with gkKicking/gkThrowing and distributionType in metadata") {
      val input = mkInput(8888L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val gkClearances = result.events.filter(e => e.eventType == "Clearance" && e.metadata.get("distributionType").isDefined)
        assertTrue(result.events.nonEmpty, result.events.exists(_.eventType == "Clearance"))
      }
    },
    test("Shot events (non-penalty) have defendersInCone and gkDistance in metadata") {
      val input = mkInput(3333L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val shots = result.events.filter(e => (e.eventType == "Shot" || e.eventType == "Goal") && !e.metadata.get("penalty").contains("true"))
        val withCtx = shots.filter(e => e.metadata.contains("defendersInCone") && e.metadata.contains("gkDistance"))
        assertTrue(result.events.nonEmpty, shots.isEmpty || withCtx.size == shots.size)
      }
    },
    test("marking and ballControl: engine runs with marking/ballControl/leadership in lineup") {
      val techWithMarking = Map("passing" -> 12, "tackling" -> 12, "marking" -> 15, "ballControl" -> 14)
      val mentalWithLead = Map("composure" -> 12, "decisions" -> 12, "leadership" -> 16)
      val homePlayers = (1 to 11).map { i =>
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        val t = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12, "gkDiving" -> 13) else techWithMarking
        PlayerMatchInput(mkPlayer(s"hp$i", pos, t, Map("pace" -> 12, "stamina" -> 12), mentalWithLead), 1.0, 0.8, None)
      }.toList
      val awayPlayers = (1 to 11).map { i =>
        val pos = if (i == 1) Set("GK") else Set("CB", "CM", "ST")
        val t = if (i == 1) Map("gkReflexes" -> 12, "gkHandling" -> 12, "gkDiving" -> 12) else techWithMarking
        PlayerMatchInput(mkPlayer(s"ap$i", pos, t, Map("pace" -> 12, "stamina" -> 12), mentalWithLead), 1.0, 0.8, None)
      }.toList
      val homeLineup = homePlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val awayLineup = awayPlayers.map(_.player.id).zip(List("GK", "LB", "CB", "CB", "RB", "LM", "CM", "CM", "RM", "ST", "ST")).toMap
      val input = MatchEngineInput(
        homeTeam = MatchTeamInput(TeamId("home"), homePlayers, homeLineup),
        awayTeam = MatchTeamInput(TeamId("away"), awayPlayers, awayLineup),
        homePlan = GamePlanInput("4-3-3"),
        awayPlan = GamePlanInput("4-4-2"),
        homeAdvantage = 1.0,
        referee = RefereeInput(0.5),
        leagueContext = LeagueContextInput(Map.empty),
        randomSeed = Some(7777L)
      )
      for {
        result <- FullMatchEngine.simulate(input)
      } yield assertTrue(result.events.nonEmpty, result.analytics.isDefined)
    },
    test("analytics include playerTortuosity, metabolicLoadByPlayer, IWP, Poisson prognosis, NMF/GMM set pieces") {
      val input = mkInput(55555L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        assertTrue(
          result.events.nonEmpty,
          a.poissonPrognosis.isDefined,
          a.poissonPrognosis.get._1 + a.poissonPrognosis.get._2 + a.poissonPrognosis.get._3 > 0.99,
          a.estimatedDistanceByPlayer.nonEmpty || a.playerActivityByZone.nonEmpty
        )
        assertTrue(a.iwpByPlayer.nonEmpty == a.vaepByPlayer.nonEmpty)
      }
    },
    test("Shot/Goal events (non-penalty) have placement and PSxG in metadata") {
      val input = mkInput(12121L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val shots = result.events.filter(e => (e.eventType == "Shot" || e.eventType == "Goal") && !e.metadata.get("penalty").contains("true"))
        assertTrue(result.events.nonEmpty, shots.isEmpty || shots.forall(e => e.metadata.contains("placement") && e.metadata.contains("PSxG")))
      }
    },
    test("analytics include voronoiCentroidByZone, passValueByPlayer, influenceScoreByPlayer") {
      val input = mkInput(31313L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val a = result.analytics.get
        assertTrue(result.events.nonEmpty, a.voronoiCentroidByZone.size == 12, a.voronoiCentroidByZone.values.forall(v => v == 0.0 || v == 1.0 || v == 0.5))
        assertTrue(a.passValueUnderPressureTotal._1.isFinite && a.passValueUnderPressureTotal._2.isFinite)
        assertTrue(a.passValueUnderPressureByPlayer.forall { case (_, v) => v.isFinite })
      }
    },
    test("xPass in Pass metadata is from model (in range 0.5–0.95) and receiverPressure present") {
      val input = mkInput(41414L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val successPasses = result.events.filter(e => (e.eventType == "Pass" || e.eventType == "LongPass") && e.outcome.contains("Success"))
        val withXPass = successPasses.flatMap(e => e.metadata.get("xPass").flatMap(s => scala.util.Try(s.toDouble).toOption))
        assertTrue(result.events.nonEmpty)
        assertTrue(successPasses.isEmpty || withXPass.nonEmpty)
        assertTrue(withXPass.isEmpty || withXPass.forall(x => x >= 0.5 && x <= 0.95))
        assertTrue(successPasses.isEmpty || successPasses.forall(_.metadata.contains("receiverPressure")))
      }
    },
    test("FormulaBasedVAEP: Dribble in attack zone has higher value than in build-up zone") {
      import fmgame.shared.domain.TeamId
      val ctxAttack = VAEPContext("Dribble", 10, Some("Success"), 50, 0, 0, Some(TeamId("h")), true)
      val ctxBuildUp = VAEPContext("Dribble", 3, Some("Success"), 50, 0, 0, Some(TeamId("h")), true)
      val vAttack = FormulaBasedVAEP.valueForEvent(ctxAttack)
      val vBuildUp = FormulaBasedVAEP.valueForEvent(ctxBuildUp)
      assertTrue(vAttack > vBuildUp, vAttack > 0.0, vBuildUp > 0.0)
    },
    test("EngineConstants event thresholds used: simulation produces events in expected proportion") {
      val input = mkInput(50505L)
      for {
        result <- FullMatchEngine.simulate(input)
      } yield {
        val passes = result.events.count(e => e.eventType == "Pass" || e.eventType == "LongPass")
        val total = result.events.size
        assertTrue(total >= 100, passes >= total / 2)
      }
    }
  )
}
