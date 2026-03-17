# FM Game – Agent Instructions

## Cursor Cloud specific instructions

### Project overview
Football Manager simulation game: Scala 3.3.3 + sbt 1.9.7, ZIO HTTP backend (port 8080), Scala.js + Laminar frontend, embedded H2 database. See `README.md` for full details.

### Running services

**Backend** (serves both API and frontend static files):
```bash
SBT_OPTS="-Xmx2g -XX:+UseG1GC" sbt "backend/runMain fmgame.backend.Main"
```
- There are two main classes (`Main` and `DesktopMain`); always use `runMain fmgame.backend.Main` to avoid the interactive selector which blocks in non-TTY mode.
- `-Xmx2g -XX:+UseG1GC` prevents GC thrashing during match simulation; the default 1GB heap is insufficient for E2E tests.

**Frontend build** (must run before backend serves the SPA):
```bash
sbt "frontend/fastLinkJS"
```

### Testing
- `SBT_OPTS="-Xmx4g -XX:+UseG1GC" sbt "backend/test"` — 162 tests; the `DesktopE2ESpec` English-league promotion/relegation test has a pre-existing failure (`expected 4 leagues after new season`). All other 161 tests pass.
- Tests simulate full seasons and are memory-intensive; always set `-Xmx4g`.

### Linting
- `sbt scalafmtCheckAll` runs Scalafmt across all modules. Many files have pre-existing formatting diffs.
- `sbt scalafmtAll` auto-formats.

### Key caveats
- The `desktop` module (LibGDX) has pre-existing compile errors (Scala `List` vs LibGDX `List` type conflict) and requires a GUI display. Skip it for headless cloud work.
- The backend uses an **in-memory H2 database** by default — all data is lost on restart. No external DB setup needed.
- API routes are under `/api/v1/` (see `backend/src/main/scala/fmgame/backend/api/Routes.scala`).
