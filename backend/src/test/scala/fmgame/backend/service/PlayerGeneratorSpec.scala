package fmgame.backend.service

import fmgame.backend.domain.Player
import fmgame.shared.domain.TeamId
import zio.*
import zio.test.*

object PlayerGeneratorSpec extends ZIOSpecDefault {

  private def sumSquadAttributes(players: List[Player]): Int =
    players.flatMap { p =>
      p.physical.values ++ p.technical.values ++ p.mental.values ++ p.traits.values
    }.sum

  def spec = suite("PlayerGenerator")(
    test("generated squads have balanced total attribute sum (within tolerance of target)") {
      val rngs = (1 to 5).map(i => new scala.util.Random(i.toLong))
      val squads = rngs.map(r => PlayerGenerator.generateSquad(TeamId(s"team-$r"), r))
      val sums = squads.map(sumSquadAttributes)
      val target = PlayerGenerator.targetTeamSum
      val tolerance = (target * 0.15).toInt
      val inRange = sums.forall(s => (s - target).abs <= tolerance)
      assertTrue(sums.size == 5, inRange, sums.forall(_ > 0))
    },
    test("each squad has 18 players (1 GK + 17 outfield)") {
      val rng = new scala.util.Random(42L)
      val squad = PlayerGenerator.generateSquad(TeamId("t1"), rng)
      val gks = squad.count(_.preferredPositions == Set("GK"))
      assertTrue(squad.size == 18, gks == 1)
    },
    test("outfield players have finishing key equal to shooting; GK has reflexes and handling") {
      val rng = new scala.util.Random(123L)
      val squad = PlayerGenerator.generateSquad(TeamId("t1"), rng)
      val gk = squad.find(_.preferredPositions == Set("GK")).get
      val outfields = squad.filter(_.preferredPositions != Set("GK"))
      assertTrue(outfields.forall(p => p.technical.contains("finishing") && p.technical.get("finishing") == p.technical.get("shooting")))
      assertTrue(gk.technical.contains("reflexes"), gk.technical.contains("handling"), gk.technical.get("reflexes") == gk.technical.get("gkReflexes"), gk.technical.get("handling") == gk.technical.get("gkHandling"))
    }
  )
}
