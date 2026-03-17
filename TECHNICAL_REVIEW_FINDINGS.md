# Technical Review Findings – Football Manager Backend

**Review date:** 2025-03-16  
**Scope:** LeagueService, UserService, TransferOfferRepository, PlayerRepository, Database, Main, MatchSummaryDtoCodec, FixtureGenerator, build.sbt

---

## CRITICAL

### 1. HikariCP DataSource never closed on shutdown (Main.scala)

**Location:** `Main.scala` lines 31–42

**Issue:** `HikariDataSource` is created and passed to `Transactor.fromDataSource`, but the DataSource is never closed when the application shuts down. Doobie’s `Transactor.fromDataSource` does not manage the DataSource lifecycle. This causes:

- Connection pool threads to remain active
- Possible unclean JVM shutdown
- Resource leaks in long-running deployments

**Fix:** Use `ZIO.acquireRelease` so the DataSource is closed when the scope ends:

```scala
(ds, xa) <- ZIO.acquireRelease(
  ZIO.succeed {
    implicit val r: zio.Runtime[Any] = runtime
    val hikariConfig = new com.zaxxer.hikari.HikariConfig()
    // ... config ...
    val ds = new com.zaxxer.hikari.HikariDataSource(hikariConfig)
    val xa = Transactor.fromDataSource[zio.Task](ds, scala.concurrent.ExecutionContext.global)
    (ds, xa)
  }
)({ case (ds, _) => ZIO.succeed(ds.close()) })
```

---

### 2. `pressConferenceGiven` LinkedHashMap is not thread-safe (LeagueService.scala)

**Location:** `LeagueService.scala` lines 99–104, 1867, 1888

**Issue:** `java.util.LinkedHashMap` is not thread-safe. It is used from `applyPressConference`, which can be called concurrently from multiple HTTP requests. The `containsKey` and `put` calls are not atomic, so two concurrent requests can both pass the check and both record a press conference (TOCTOU race).

**Fix:** Wrap with `Collections.synchronizedMap`:

```scala
private val pressConferenceGiven = java.util.Collections.synchronizedMap(
  new java.util.LinkedHashMap[(String, String, String), Boolean](256, 0.75f, true) {
    override def removeEldestEntry(eldest: java.util.Map.Entry[(String, String, String), Boolean]): Boolean = size > maxEntries
  }
)
```

---

## HIGH

### 3. `acceptInvitation` does not validate `teamName` (LeagueService.scala)

**Location:** `LeagueService.scala` line 278

**Issue:** `create` validates team names (1–100 chars, non-empty after trim), but `acceptInvitation` uses `teamName` directly. This allows empty, whitespace-only, or very long names, which can break UI and DB constraints.

**Fix:** Add validation before creating the team:

```scala
_ <- guard(teamName.trim.isEmpty || teamName.trim.length > 100, "Team name must be 1-100 characters")
```

---

### 4. H2H limit mismatch: API allows 50, service caps at 20 (LeagueService.scala, Routes.scala)

**Location:** `LeagueService.scala` line 1699; `Routes.scala` line 318

**Issue:** Routes caps the limit at 50 (`limit.min(50)`), but `getH2HForUser` uses `math.min(limit, 20)`. Users can request up to 50 but only receive 20 results.

**Fix:** Align the cap. Either:

- Change LeagueService to `math.min(limit, 50)` if 50 is intended, or
- Change Routes to `limit.min(20)` if 20 is intended.

---

## MEDIUM

### 5. `Database.initSchema` – ALTER and index failures are swallowed (Database.scala)

**Location:** `Database.scala` lines 298–303

**Issue:** `addEloColumn`, `addManagerNameColumn`, `addCounterAmountColumn`, and `createIndexes` use `.catchAll(_ => ZIO.unit)`, so failures are ignored. On a fresh DB, ALTERs may fail if columns already exist (e.g. from a previous run), which is acceptable. But index creation failures (e.g. syntax errors, permission issues) are also hidden, making schema problems hard to diagnose.

**Recommendation:** Log failures instead of silently ignoring:

```scala
addCounterAmountColumn.transact(xa).unit.catchAll(e => ZIO.logWarning(s"ALTER counter_amount (may already exist): $e"))
createIndexes.transact(xa).unit.catchAll(e => ZIO.logWarning(s"Index creation: $e"))
```

---

### 6. `startSeason` batch transaction – correct structure (LeagueService.scala)

**Location:** `LeagueService.scala` lines 318–330

**Status:** The batch write is correctly wrapped in a single `.transact(xa)`. All operations (players, referees, matches, transfer windows, league context, league update) run in one transaction and roll back on failure. No change needed.

---

### 7. Missing index for `listByTeam` on `transfer_offers` (TransferOfferRepository.scala)

**Location:** `TransferOfferRepository.scala` lines 46–57; `Database.scala` indexes

**Issue:** `listByTeam` filters on `from_team_id = X OR to_team_id = X`. Existing indexes are `idx_offers_window` and `idx_offers_player`. There is no index on `from_team_id` or `to_team_id`, so this query may perform full table scans for leagues with many offers.

**Recommendation:** Add indexes such as:

```sql
CREATE INDEX IF NOT EXISTS idx_offers_from_team ON transfer_offers(from_team_id);
CREATE INDEX IF NOT EXISTS idx_offers_to_team ON transfer_offers(to_team_id);
```

---

### 8. UserService email validation – no trimming before DB (UserService.scala)

**Location:** `UserService.scala` lines 26, 31

**Issue:** `validateEmail` checks `email.trim.isEmpty` but the stored value is the original `email`. Trailing/leading spaces can cause `findByEmail` to miss existing users and allow duplicate-looking registrations.

**Fix:** Normalize before use:

```scala
val normalizedEmail = email.trim.toLowerCase
// use normalizedEmail for validation and DB operations
```

---

## LOW

### 9. `build.sbt` – version variables and dependencies (build.sbt)

**Location:** `build.sbt` lines 1–6, 38

**Status:** Version variables are used consistently. `doobie-hikari` is declared; Main uses `HikariDataSource` directly. HikariCP is pulled in transitively by `doobie-hikari`. No change needed.

---

### 10. FixtureGenerator – referee assignment when referees < matches (FixtureGenerator.scala)

**Location:** `FixtureGenerator.scala` lines 45–46

**Issue:** `refPerm = rng.shuffle(refereePool.indices.toList).take(pairs.size)` – if `refereePool.size < pairs.size`, some matches get duplicate referee indices (e.g. same referee twice). For `numReferees = league.teamCount / 2` and `matchesPerRound = n/2`, we have `referees.size == matchesPerRound`, so this is fine. Only a concern if the number of referees is reduced in the future.

---

### 11. MatchSummaryDtoCodec – backward compatibility defaults (MatchSummaryDtoCodec.scala)

**Location:** `MatchSummaryDtoCodec.scala` lines 16–19

**Status:** `decodeListIntOrDefault` and `decodeListDoubleOrDefault` use `List(0, 0)` and `List(0.0, 0.0)` for missing fields. This is appropriate for backward compatibility. No change needed.

---

### 12. PlayerRepository `listByTeamIds` – empty list handling (PlayerRepository.scala)

**Location:** `PlayerRepository.scala` lines 116–118

**Status:** Returns `List.empty` when `teamIds.isEmpty` before using `NonEmptyList.fromListUnsafe`, avoiding a runtime error. Correct.

---

### 13. TransferOfferRepository – `counter_amount` in all queries (TransferOfferRepository.scala)

**Location:** `TransferOfferRepository.scala`

**Status:** `counter_amount` is included in INSERT, UPDATE, and all SELECTs. Row mapping uses the correct tuple order. No issues found.

---

## Summary Table

| # | Severity  | Component        | Issue                                      |
|---|-----------|------------------|--------------------------------------------|
| 1 | CRITICAL  | Main.scala       | HikariCP DataSource not closed on shutdown |
| 2 | CRITICAL  | LeagueService    | pressConferenceGiven not thread-safe       |
| 3 | HIGH      | LeagueService    | acceptInvitation missing teamName validation |
| 4 | HIGH      | LeagueService/Routes | H2H limit API vs service mismatch (50 vs 20) |
| 5 | MEDIUM    | Database.scala   | ALTER/index failures swallowed             |
| 6 | MEDIUM    | LeagueService    | startSeason transaction – verified correct  |
| 7 | MEDIUM    | TransferOfferRepo | Missing indexes for listByTeam             |
| 8 | MEDIUM    | UserService      | Email not trimmed before DB                |
| 9 | LOW       | build.sbt        | Config – OK                                |
| 10| LOW       | FixtureGenerator | Referee count edge case                    |
| 11| LOW       | MatchSummaryDtoCodec | Defaults – OK                          |
| 12| LOW       | PlayerRepository | Empty list – OK                             |
| 13| LOW       | TransferOfferRepo | counter_amount in all queries – OK       |
