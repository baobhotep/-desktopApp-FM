package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import java.time.{Instant, LocalDate, LocalTime, ZoneId}
import scala.util.Random

/** Generates round-robin fixture list (MODELE §2.3): two rounds, each pair plays home and away. */
object FixtureGenerator {

  /** Generate all matches for the league. Teams are indexed 0..n-1; teamIds(0) is first team, etc.
   * Referees are assigned per matchday (random permutation so each referee does one match per round).
   * scheduledAt: startDate at 17:00 in zone, then alternate Wed/Sat for subsequent matchdays.
   */
  def generate(
    leagueId: LeagueId,
    teamIds: List[TeamId],
    referees: List[Referee],
    startDate: LocalDate,
    zoneId: ZoneId,
    rng: Random
  ): List[Match] = {
    val n = teamIds.size
    if (n < 2 || n % 2 != 0) return List.empty
    val totalMatchdays = 2 * (n - 1)
    val matchesPerRound = n / 2
    def at17(d: LocalDate): Instant = d.atTime(LocalTime.of(17, 0)).atZone(zoneId).toInstant
    def nextMatchdayDate(d: LocalDate, matchdayIndex: Int): LocalDate = {
      if (matchdayIndex == 0) d
      else {
        val weekOffset = (matchdayIndex / 2) * 7
        val midWeek = (matchdayIndex % 2) * 3
        d.plusDays(weekOffset + midWeek)
      }
    }
    val round1 = roundRobinPairs(n, rng)
    val round2 = round1.map { case (a, b) => (b, a) }
    val allRounds = round1 ++ round2
    val matchdays = allRounds.grouped(matchesPerRound).toList
    val refereePool = referees.toVector
    var matches = List.empty[Match]
    for ((pairs, matchdayIndex) <- matchdays.zipWithIndex) {
      val matchday = matchdayIndex + 1
      val scheduledAt = at17(nextMatchdayDate(startDate, matchdayIndex))
      val refPerm = rng.shuffle(refereePool.indices.toList).take(pairs.size)
      for (((homeIdx, awayIdx), refSlot) <- pairs.zip(refPerm)) {
        val ref = refereePool(refSlot)
        val m = Match(
          id = IdGen.matchId,
          leagueId = leagueId,
          matchday = matchday,
          homeTeamId = teamIds(homeIdx),
          awayTeamId = teamIds(awayIdx),
          scheduledAt = scheduledAt,
          status = MatchStatus.Scheduled,
          homeGoals = None,
          awayGoals = None,
          refereeId = ref.id,
          resultLogId = None
        )
        matches = m :: matches
      }
    }
    matches.reverse
  }

  /** Circle method round-robin: fix team 0, rotate others. Returns list of (homeIndex, awayIndex) for round 1 only (n-1 rounds, each n/2 matches). */
  private def roundRobinPairs(n: Int, rng: Random): List[(Int, Int)] = {
    val others = (1 until n).toList
    (0 until n - 1).flatMap { round =>
      val rotated = rotateRight(others, round)
      val first = 0 :: rotated.take(n/2 - 1)
      val second = rotated.drop(n/2 - 1).reverse
      first.zip(second).map { case (a, b) => (math.min(a, b), math.max(a, b)) }
    }.toList
  }

  private def rotateRight[A](list: List[A], k: Int): List[A] = {
    val n = list.size
    val r = k % n
    if (r == 0) list else list.drop(n - r) ++ list.take(n - r)
  }
}
