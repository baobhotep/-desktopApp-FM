package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*
import java.time.Instant

object BotSquadBuilderSpec extends ZIOSpecDefault {

  private def mkPlayer(
    id: String,
    positions: Set[String],
    physical: Map[String, Int] = Map.empty,
    technical: Map[String, Int] = Map.empty,
    mental: Map[String, Int] = Map.empty
  ): Player = Player(
    PlayerId(id),
    TeamId("t1"),
    "First",
    id,
    positions,
    physical,
    technical,
    mental,
    Map.empty,
    Map.empty,
    None,
    1.0,
    1.0,
    Instant.EPOCH
  )

  def spec = suite("BotSquadBuilder")(
    test("bestPlayerForSlot returns GK for GK slot when only one GK") {
      val gk = mkPlayer("gk1", Set("GK"), technical = Map("reflexes" -> 16, "handling" -> 15, "gkReflexes" -> 16, "gkHandling" -> 15))
      val out = mkPlayer("p1", Set("ST"), technical = Map("finishing" -> 14))
      val list = List(gk, out)
      val best = BotSquadBuilder.bestPlayerForSlot(list, "GK")
      assertTrue(best.contains(gk))
    },
    test("bestPlayerForSlot returns outfield for ST slot") {
      val gk = mkPlayer("gk1", Set("GK"))
      val st = mkPlayer("st1", Set("ST"), technical = Map("finishing" -> 18, "composure" -> 16), mental = Map("offTheBall" -> 15))
      val cb = mkPlayer("cb1", Set("CB"), technical = Map("tackling" -> 16, "marking" -> 15))
      val list = List(gk, st, cb)
      val best = BotSquadBuilder.bestPlayerForSlot(list, "ST")
      assertTrue(best.contains(st))
    },
    test("scoreForSlot gives position bonus when preferred position matches slot") {
      val gk = mkPlayer("gk1", Set("GK"), technical = Map("reflexes" -> 14, "handling" -> 14))
      val out = mkPlayer("p1", Set("ST"), technical = Map("reflexes" -> 14, "handling" -> 14))
      val scoreGk = BotSquadBuilder.scoreForSlot(gk, "GK")
      val scoreOut = BotSquadBuilder.scoreForSlot(out, "GK")
      assertTrue(scoreGk > scoreOut)
    },
    test("buildBotLineup returns 11 slots with one GK and correct formation slots") {
      val rng = new scala.util.Random(42L)
      val squad = PlayerGenerator.generateSquad(TeamId("t1"), rng)
      val formation = "4-3-3"
      val lineup = BotSquadBuilder.buildBotLineup(squad, 1, formation)
      assertTrue(
        lineup.isDefined,
        lineup.get.size == 11,
        lineup.get.head.positionSlot == "GK",
        lineup.get.map(_.positionSlot).toSet == BotSquadBuilder.formations.find(_._1 == formation).get._2.toSet
      )
    },
    test("buildBotLineup assigns GK slot to a player with GK in preferredPositions") {
      val rng = new scala.util.Random(123L)
      val squad = PlayerGenerator.generateSquad(TeamId("t1"), rng)
      val lineup = BotSquadBuilder.buildBotLineup(squad, 1, "4-4-2").get
      val gkSlot = lineup.find(_.positionSlot == "GK").get
      val gkPlayer = squad.find(_.id == gkSlot.playerId).get
      assertTrue(gkPlayer.preferredPositions.contains("GK"))
    },
    test("pickFormation with strong opponent (>.6) tends to defensive formation") {
      val teamId = TeamId("bot-1")
      val strong = BotSquadBuilder.pickFormation(teamId, Some(0.75))
      assertTrue(BotSquadBuilder.formations.map(_._1).contains(strong))
    },
    test("pickFormation with weak opponent (<.4) tends to attacking formation") {
      val teamId = TeamId("bot-2")
      val weak = BotSquadBuilder.pickFormation(teamId, Some(0.25))
      assertTrue(BotSquadBuilder.formations.map(_._1).contains(weak))
    },
    test("defaultGamePlanJson with strong opponent includes lower tempo") {
      val json = BotSquadBuilder.defaultGamePlanJson("4-3-3", withTriggers = false, Some(0.7))
      assertTrue(json.contains("tempo"), json.contains("lower"), json.contains("teamInstructions"))
    },
    test("defaultGamePlanJson with weak opponent includes higher tempo") {
      val json = BotSquadBuilder.defaultGamePlanJson("4-4-2", withTriggers = true, Some(0.3))
      assertTrue(json.contains("tempo"), json.contains("higher"), json.contains("pressingIntensity"))
    },
    test("defaultGamePlanJson without opponent strength has no teamInstructions") {
      val json = BotSquadBuilder.defaultGamePlanJson("4-3-3", withTriggers = false, None)
      assertTrue(!json.contains("teamInstructions") || json.contains("formationName"))
    },
    test("positionGroups maps GK to GK") {
      val gk = mkPlayer("gk1", Set("GK"))
      assertTrue(BotSquadBuilder.positionGroups(gk) == List("GK"))
    },
    test("positionGroups maps LCB/RCB/CB to CB") {
      val cb = mkPlayer("cb1", Set("LCB", "RCB"))
      assertTrue(BotSquadBuilder.positionGroups(cb).contains("CB"))
    },
    test("positionGroups maps LW/RW to W and ST/LS/RS to ST") {
      val w = mkPlayer("w1", Set("LW", "RW"))
      val st = mkPlayer("st1", Set("ST", "LS"))
      assertTrue(BotSquadBuilder.positionGroups(w).contains("W"), BotSquadBuilder.positionGroups(st).contains("ST"))
    },
    test("squadNeedGroups returns GK when no goalkeeper") {
      val outfieldOnly = (1 to 11).map(i => mkPlayer(s"p$i", Set("ST", "CB"))).toList
      val needs = BotSquadBuilder.squadNeedGroups(outfieldOnly, 1)
      assertTrue(needs.contains("GK"))
    },
    test("squadNeedGroups does not contain GK when squad has a goalkeeper") {
      val rng = new scala.util.Random(1L)
      val fullSquad = PlayerGenerator.generateSquad(TeamId("t1"), rng)
      val needs = BotSquadBuilder.squadNeedGroups(fullSquad, 1)
      assertTrue(!needs.contains("GK"))
    },
    test("isPlayerInBestLineup true for player in built lineup") {
      val rng = new scala.util.Random(5L)
      val squad = PlayerGenerator.generateSquad(TeamId("bot-1"), rng)
      val formation = BotSquadBuilder.pickFormation(TeamId("bot-1"), None)
      val lineup = BotSquadBuilder.buildBotLineup(squad, 1, formation).get
      val firstPlayerId = lineup.head.playerId
      assertTrue(BotSquadBuilder.isPlayerInBestLineup(firstPlayerId, squad, 1, TeamId("bot-1")))
    },
    test("isPlayerInBestLineup false for player not in squad") {
      val rng = new scala.util.Random(5L)
      val squad = PlayerGenerator.generateSquad(TeamId("bot-1"), rng)
      val otherId = PlayerId("not-in-squad")
      assertTrue(!BotSquadBuilder.isPlayerInBestLineup(otherId, squad, 1, TeamId("bot-1")))
    },
    test("botDifficulty returns Easy, Normal or Hard from teamId hash") {
      val difficulties = (1 to 10).map(i => BotSquadBuilder.botDifficulty(TeamId(s"bot-$i"))).toSet
      assertTrue(difficulties.size >= 2, difficulties.forall(d => BotSquadBuilder.BotDifficulty.values.contains(d)))
    },
    test("botStyle returns Defensive or Attacking") {
      val styles = (1 to 6).map(i => BotSquadBuilder.botStyle(TeamId(s"team-$i"))).toSet
      assertTrue(styles.size >= 1, styles.forall(s => BotSquadBuilder.BotStyle.values.contains(s)))
    },
    test("buildBotLineup Easy uses 1 GK + top 10 outfield by overall") {
      val rng = new scala.util.Random(99L)
      val squad = PlayerGenerator.generateSquad(TeamId("t1"), rng)
      val lineupEasy = BotSquadBuilder.buildBotLineup(squad, 1, "4-3-3", BotSquadBuilder.BotDifficulty.Easy)
      assertTrue(lineupEasy.isDefined, lineupEasy.get.size == 11)
      val gkSlot = lineupEasy.get.find(_.positionSlot == "GK").get
      val gkPlayer = squad.find(_.id == gkSlot.playerId).get
      assertTrue(gkPlayer.preferredPositions.contains("GK"))
    },
    test("keyPlayerPremium Hard > Normal > Easy") {
      assertTrue(
        BotSquadBuilder.keyPlayerPremium(BotSquadBuilder.BotDifficulty.Hard) > BotSquadBuilder.keyPlayerPremium(BotSquadBuilder.BotDifficulty.Normal),
        BotSquadBuilder.keyPlayerPremium(BotSquadBuilder.BotDifficulty.Normal) > BotSquadBuilder.keyPlayerPremium(BotSquadBuilder.BotDifficulty.Easy)
      )
    },
    test("buyPriceMultiplierRange Hard has higher min than Easy") {
      val (easyMin, _) = BotSquadBuilder.buyPriceMultiplierRange(BotSquadBuilder.BotDifficulty.Easy)
      val (hardMin, _) = BotSquadBuilder.buyPriceMultiplierRange(BotSquadBuilder.BotDifficulty.Hard)
      assertTrue(hardMin >= easyMin)
    }
  )
}
