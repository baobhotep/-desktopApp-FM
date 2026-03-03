# Całościowy krytyczny przegląd kodu – każdy plik, funkcja, linia

**Data:** 2026-02-23  
**Zakres:** Cały projekt (build, shared, backend, frontend) – przegląd linia po linii / funkcja po funkcji.

---

## Indeks przejrzanych plików (70 plików Scala + build)

**Build:** build.sbt, project/plugins.sbt, project/build.properties.

**Shared (4):** api/ApiDto.scala, domain/Ids.scala, domain/Enums.scala, domain/ThrowInConfig.scala.

**Backend main (41):** Main.scala; api/Routes.scala, api/MatchSummaryDtoCodec.scala; auth/AuthService.scala; domain/Domain.scala; engine/EngineConfig.scala, EngineTypes.scala, AnalyticsModels.scala, MatchState.scala, PitchModel.scala, FullMatchEngine.scala, SimpleMatchEngine.scala, LoadablexGModel.scala, OnnxXGModel.scala, OnnxVAEPModel.scala; repository/Database.scala, EnumParse.scala, UserRepository.scala, PlayerRepository.scala, LeagueRepository.scala, TeamRepository.scala, MatchRepository.scala, MatchSquadRepository.scala, MatchResultLogRepository.scala, InvitationRepository.scala, RefereeRepository.scala, TransferWindowRepository.scala, TransferOfferRepository.scala, LeagueContextRepository.scala, GamePlanSnapshotRepository.scala, MatchSummaryCodec.scala; service/UserService.scala, LeagueService.scala, MatchSummaryAggregator.scala, FixtureGenerator.scala, PlayerGenerator.scala, BotSquadBuilder.scala, DefaultSquadBuilder.scala, LeagueContextComputer.scala, PlayerOverall.scala, ExportFormats.scala.

**Backend test (11):** TestDbHelper.scala; api/ApiIntegrationSpec.scala; auth/AuthServiceSpec.scala; engine/EngineConfigSpec.scala, FullMatchEngineSpec.scala, SimpleMatchEngineSpec.scala, LoadablexGModelSpec.scala; service/UserServiceSpec.scala, LeagueServiceSpec.scala, FixtureGeneratorSpec.scala, MatchSummaryAggregatorSpec.scala, PlayerGeneratorSpec.scala.

**Frontend (13):** App.scala, ApiClient.scala, ApiDto.scala, Main.scala, MatchSummaryDtoCodec.scala; LoginPage.scala, RegisterPage.scala, DashboardPage.scala, LeaguePage.scala, TeamPage.scala, MatchDetailPage.scala, MatchSquadPage.scala, AcceptInvitationPage.scala.

Pliki repository/service/engine niewymienione w tekście sekcji po kolei mają ten sam wzorzec co opisane (ConnectionIO, ZIO, walidacja, mapowanie) – uwagi zbiorcze w sekcjach 5 i 6.

---

## 1. Build i projekt

### 1.1 build.sbt

| Linie | Uwaga | Priorytet |
|-------|--------|-----------|
| 1 | `scala3Version = "3.3.3"` – jedna wersja dla wszystkich modułów; OK. | — |
| 3–11 | **shared:** Zależności tylko Circe; brak ZIO/Cats – poprawne dla współdzielonych DTO. | — |
| 13–40 | **backend:** ZIO, Doobie, http4s, Tapir, JWT, bcrypt, H2/Postgres, zio-interop-catz. Tapir w deps, ale Routes używa tylko http4s DSL – **niewykorzystana zależność** (tapir-*, 3 linie). | Średni |
| 36 | **frontend** nie zależy od **shared** – duplikacja DTO (ApiDto, MatchSummaryDtoCodec); zmiana kontraktu wymaga zmian w 2 miejscach. | Wysoki |
| 43–54 | **frontend:** Laminar, Circe, scalajs-dom; `scalaJSUseMainModuleInitializer := true` – OK. | — |

**Rekomendacje:** Usunąć Tapir z backendu albo dokończyć migrację. Rozważyć `frontend.dependsOn(shared)` i jeden zestaw DTO.

### 1.2 project/plugins.sbt, build.properties

Standard; brak `.scalafmt.conf` w repo – Niski priorytet.

---

## 2. Shared

### 2.1 shared/…/api/ApiDto.scala

| Linie | Uwaga | Priorytet |
|-------|--------|-----------|
| 7–165 | DTO bez walidacji (email, teamCount, timezone). CreateLeagueRequest.timezone – `ZoneId.of` w backendzie może rzucić przy nieprawidłowej wartości. | Średni |
| 111 | MatchEventDto.metadata: Map[String, String] – przy wyświetlaniu wartości escapowanie (XSS). | Niski |

### 2.2 shared/…/domain/Ids.scala

Opaque types + apply/random/extension. `random()` używa UUID poza efektem – testowalność ograniczona (Niski).

### 2.3 shared/…/domain/Enums.scala, ThrowInConfig.scala

Enumy czyste; ThrowInConfig bez walidacji list – Niski.

---

## 3. Backend – Main, API, Auth, Domain

### 3.1 Main.scala

| Linie | Uwaga | Priorytet |
|-------|--------|-----------|
| 22–25 | jwtSecret getOrElse "change-me-in-production" – **ryzyko w produkcji** jeśli brak JWT_SECRET. | Wysoki |
| 51 | runScheduledMatchdays().repeat(300s).forkDaemon – brak zatrzymania przy shutdown. | Średni |
| 62–64 | ZIO.fromFuture(serverRes.use(_ => IO.never).unsafeToFuture()) – brak graceful shutdown. | Wysoki |

**Rekomendacje:** Wymuszenie JWT_SECRET w prod (fail fast); obsługa SIGINT/SIGTERM, shutdown serwera i schedulera.

### 3.2 api/Routes.scala

| Linie | Uwaga | Priorytet |
|-------|--------|-----------|
| 26–27 | unsafeRunZIOToIO – blokuje wątek IO przy każdym requeście. | Wysoki |
| 75–86, 145–154 | GET leagues/:id, GET matches/:id – **brak autoryzacji per zasób**; nie sprawdza się, czy użytkownik należy do ligi / ma dostęp do meczu. | Wysoki |
| 42–50 | POST register – brak walidacji hasła (długość, złożoność). | Średni |
| 396–410 | POST admin/models – **każdy zalogowany użytkownik** może wgrywać modele; brak roli admin. | Wysoki |

**Rekomendacje:** (1) Sprawdzać dostęp do ligi/meczu/drużyny (np. członkostwo, ownership). (2) Endpoint admin – tylko rola admin lub osobny secret. (3) Walidacja hasła przy rejestracji.

### 3.3 api/MatchSummaryDtoCodec.scala

Ręczne codec z powodu limitu inliningu. Decoder może failować przy starym JSON (brak domyślnych wartości dla opcjonalnych pól) – Średni.

### 3.4 auth/AuthService.scala

hashPasswordZIO w ZIO.blocking; createToken/verifyToken z Circe; potwierdzić, że JwtCirce.decode odrzuca wygasłe tokeny – Niski.

### 3.5 domain/Domain.scala

IdGen poza ZIO; modele domenowe spójne z shared i repozytoriami.

---

## 4. Backend – Engine

### 4.1 EngineConfig.scala

fromEnvZIO opakowuje fromEnv w ZIO.succeed – odczyt env w momencie wywołania. xGModelFromConfig (synchroniczna) i xGFromPath używają Source.fromFile bez close w części ścieżek – Średni. Dodać try/finally lub używać tylko fromConfigZIO.

### 4.2 EngineTypes.scala, AnalyticsModels.scala (PitchModel, MatchState)

TriggerConfig, GamePlanInput, MatchEngineInput/Result, MatchAnalytics. PitchModel.formation442 – dwa wpisy (0.38, 0.5) – zweryfikować pozycje – Średni.

### 4.3 FullMatchEngine.scala

| Uwaga | Priorytet |
|-------|-----------|
| generateNextEvent – bardzo długa metoda; duplikacja (newFatigue, state.copy). | Średni |
| computeAnalyticsFromEvents – passingNodeStats.received = 0, nie obliczane. | Średni |

**Rekomendacje:** Wydzielić generatePass, generateShot itd.; dodać liczenie received lub usunąć z typu.

### 4.4 SimpleMatchEngine.scala

computeAnalyticsFromEvents podobna do FullMatchEngine; brak fieldTilt/ppda. Ujednolicić z FullMatchEngine (wspólna funkcja?) – Średni.

### 4.5 LoadablexGModel.scala, OnnxXGModel.scala, OnnxVAEPModel.scala

fromJson z fallback FormulaBasedxG; Onnx placeholdery (loadImpl = None).

---

## 5. Backend – Repository

### 5.1 Database.scala

initSchema z addEloColumn.attempt.as(()); przy migracji istniejącej bazy kolejność ALTER może być wrażliwa – Niski.

### 5.2 EnumParse.scala

Wszystkie metody: getOrElse(throw new IllegalArgumentException(...)) – rzuca w ConnectionIO. Rozważyć Option i ZIO.fail w serwisach – Średni.

### 5.3 UserRepository.scala

CRUD; Doobie prepared statements – bezpieczne.

### 5.4 PlayerRepository.scala

PlayerJson.decodeInjury – `return None`; idiomatyczniej bez return. listByTeam i findById – duplikacja mapowania wiersza na Player – Średni. Wspólna funkcja rowToPlayer.

### 5.5 Pozostałe repozytoria

Wzorzec ConnectionIO + EnumParse; MatchResultLog – events/summary CLOB/JSON; błędy parsowania mogą rzucać.

---

## 6. Backend – Service

### 6.1 UserService.scala

register/login/getById – ZIO, transact, tapError. IdGen.userId poza efektem – Niski.

### 6.2 LeagueService.scala

connUnit, traverseConn; create, getById, getTable (H2H), startSeason, addBots, runScheduledMatchdays, exportMatchLogs, uploadEngineModel. Bardzo duża klasa – rozważyć wydzielenie modułów (tabela, fixtures, transfery) – Średni.

### 6.3 MatchSummaryAggregator.scala

Wiele var; events.foreach match; logika possession/blocks/saves spójna z KONTRAKTY §2.3.

---

## 7. Backend – Testy

TestDbHelper – initSchemaForTest. ApiIntegrationSpec – runIO(io), setupApp z Unsafe dla engineModelsRef; testy register→login→me, league, fixtures, match log, 401, 400. Ostrzeżenia ZLayer (ZLayer.empty) – można usunąć .provide(ZLayer.empty) – Niski.

---

## 8. Frontend

### 8.1 App.scala

AppState z Var; apiBaseUrl domyślnie localhost:8080 – w prod z env – Średni. rootElement – zagnieżdżone match; wydzielić router – Średni.

### 8.2 ApiClient.scala

fetchEither bez timeout – Średni. getFixtures/getMatchLog używają AppState.token.now() – przy braku tokena możliwy błąd – Średni.

### 8.3 Pozostałe strony

Laminar + ApiClient; duplikacja DTO (frontend nie zależy od shared).

---

## 9. Podsumowanie krytyczne

### Najwyższy priorytet

1. **Autoryzacja per zasób** – GET leagues/:id, matches/:id, teams/:id bez sprawdzenia dostępu użytkownika.
2. **admin/models** – dostępny dla każdego zalogowanego; ograniczyć do admina/secretu.
3. **JWT_SECRET** – wymuszenie w produkcji (fail start).
4. **Graceful shutdown** – serwer i scheduler.

### Wysoki

5. Walidacja hasła przy rejestracji.  
6. Walidacja timezone (CreateLeagueRequest).  
7. Frontend dependsOn(shared) – jeden kontrakt DTO.  
8. unsafeRunZIOToIO / async bridge lub ZIO HTTP.

### Średni

9. Tapir – usunąć lub migracja.  
10. Database/addEloColumn – migracje.  
11. EnumParse – Option + ZIO.fail.  
12. PlayerRepository – rowToPlayer, usunąć return.  
13. Formation 4-4-2 – zweryfikować.  
14. FullMatchEngine – wydzielić generateNextEvent; passingNodeStats.received.  
15. MatchSummaryDto decoder – domyślne wartości.  
16. ApiClient – timeout, obsługa braku tokena.  
17. App – apiBaseUrl z env, router.

### Niski

18. IdGen w ZIO.  
19. .scalafmt.conf.  
20. ThrowInConfig/metadata.  
21. ZLayer.empty w testach.

---

**Koniec dokumentu.**
