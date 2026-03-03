package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*
import java.time.{LocalDate, ZoneId}
import scala.util.Random

object FixtureGeneratorSpec extends ZIOSpecDefault {

  def spec = suite("FixtureGenerator")(
    test("generate returns empty for odd number of teams") {
      val leagueId = LeagueId("l1")
      val teamIds = (1 to 5).map(i => TeamId(s"t$i")).toList
      val refs = List(Referee(RefereeId("r1"), leagueId, "Ref1", 0.5))
      val matches = FixtureGenerator.generate(leagueId, teamIds, refs, LocalDate.of(2025, 8, 1), ZoneId.of("Europe/Warsaw"), new Random(42))
      assertTrue(matches.isEmpty)
    },
    test("generate returns empty for single team") {
      val leagueId = LeagueId("l1")
      val teamIds = List(TeamId("t1"))
      val refs = List(Referee(RefereeId("r1"), leagueId, "Ref1", 0.5))
      val matches = FixtureGenerator.generate(leagueId, teamIds, refs, LocalDate.of(2025, 8, 1), ZoneId.of("Europe/Warsaw"), new Random(42))
      assertTrue(matches.isEmpty)
    },
    test("generate with 4 teams produces 12 matches (2 rounds, 2 matches per matchday)") {
      val leagueId = LeagueId("l1")
      val teamIds = (1 to 4).map(i => TeamId(s"t$i")).toList
      val refs = List(
        Referee(RefereeId("r1"), leagueId, "Ref1", 0.5),
        Referee(RefereeId("r2"), leagueId, "Ref2", 0.5)
      )
      val rng = new Random(123)
      val matches = FixtureGenerator.generate(leagueId, teamIds, refs, LocalDate.of(2025, 8, 1), ZoneId.of("Europe/Warsaw"), rng)
      assertTrue(
        matches.size == 12,
        matches.map(_.matchday).distinct.sorted == (1 to 6).toList,
        matches.groupBy(_.matchday).forall { case (_, mdMatches) => mdMatches.size == 2 }
      )
    },
    test("each team plays exactly (n-1)*2 matches") {
      val leagueId = LeagueId("l1")
      val teamIds = (1 to 4).map(i => TeamId(s"t$i")).toList
      val refs = (1 to 2).map(i => Referee(RefereeId(s"r$i"), leagueId, s"Ref$i", 0.5)).toList
      val matches = FixtureGenerator.generate(leagueId, teamIds, refs, LocalDate.of(2025, 8, 1), ZoneId.of("UTC"), new Random(999))
      val perTeam = teamIds.map { tid =>
        val played = matches.filter(m => m.homeTeamId == tid || m.awayTeamId == tid)
        tid -> played.size
      }.toMap
      assertTrue(perTeam.values.forall(_ == 6))
    }
  )
}
