# Dokumentacja techniczna — Football Manager Game (backend)

## 1. Przegląd

Aplikacja jest grą strategiczną typu „football manager” opisaną w dokumentach: `WYMAGANIA_GRY.md`, `MODELE_I_PRZEPLYWY_APLIKACJI.md`, `KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md`. Niniejszy dokument opisuje **zaimplementowany backend** (Scala 3, ZIO, Doobie, Http4s) oraz sposób uruchomienia i testów.

## 2. Struktura projektu

```
analize/
├── shared/           # Współdzielone typy (ID, enumy, DTO API)
│   └── src/main/scala/fmgame/shared/
│       ├── domain/   # Ids.scala, Enums.scala
│       └── api/      # ApiDto.scala
├── backend/          # Aplikacja serwerowa
│   └── src/main/scala/fmgame/backend/
│       ├── domain/   # Domain.scala (User, League, Team, Player, Match, …)
│       ├── repository/ # Doobie: User, League, Team, Invitation, Player, Referee, Match, MatchSquad, MatchResultLog, TransferWindow; Database, EnumParse
│       ├── auth/     # AuthService (bcrypt, JWT)
│       ├── engine/   # MatchEngine: FullMatchEngine (domyślny), SimpleMatchEngine; AnalyticsModels (xG/VAEP), PitchModel (PositionGenerator, PitchControl, DxT), MatchState
│       ├── service/  # UserService, LeagueService, PlayerGenerator, FixtureGenerator, DefaultSquadBuilder, BotSquadBuilder, PlayerOverall
│       ├── api/      # Routes (Http4s)
│       └── Main.scala
├── frontend/        # Aplikacja Scala.js (Laminar, ZIO)
│   ├── src/main/scala/app/
│   │   ├── Main.scala, App.scala, AppState
│   │   ├── ApiClient.scala, ApiDto.scala (DTO dla API)
│   │   ├── LoginPage.scala, RegisterPage.scala, DashboardPage.scala
│   │   └── index.html
│   └── (target: frontend-fastopt.js)
├── build.sbt
└── DOKUMENTACJA_TECHNICZNA.md (ten plik)
```

## 3. Stack technologiczny

| Warstwa      | Technologia |
|-------------|-------------|
| Język       | Scala 3.3.3 |
| Efekty      | ZIO 2.0.21  |
| Baza danych | H2 (dev) / PostgreSQL (prod), Doobie 1.0.0-RC5 |
| HTTP        | Http4s 0.23 (Ember server) |
| JSON        | Circe       |
| Auth        | JWT (jwt-circe), bcrypt (scala-bcrypt) |
| Interop     | zio-interop-cats (Async[Task] dla Doobie) |

## 4. Uruchomienie

### Wymagania

- JDK 11+
- SBT 1.9.x

### Kompilacja

```bash
sbt "backend/compile"
```

### Uruchomienie serwera

```bash
sbt "backend/runMain fmgame.backend.Main"
```

Serwer startuje na domyślnym porcie Ember (zależnie od wersji http4s; często 8080). Baza H2 w pamięci: `jdbc:h2:mem:fmgame;DB_CLOSE_DELAY=-1`.

### Testy

```bash
sbt "backend/test"
```

### Frontend (Scala.js + Laminar)

```bash
sbt "frontend/compile"
```

Plik JS: `frontend/target/scala-3.3.3/frontend-fastopt.js`. Aby przetestować: uruchom backend (`sbt backend/runMain fmgame.backend.Main`), w `frontend/index.html` ustaw ścieżkę do skryptu (np. `./target/scala-3.3.3/frontend-fastopt.js`), otwórz plik w przeglądarce lub serwuj katalog frontendu przez HTTP (np. `python3 -m http.server 3000` w katalogu frontend). Domyślny adres API w aplikacji: `http://localhost:8080`.

## 5. API HTTP (zaimplementowane)

Wszystkie ścieżki są pod prefixem **`/api/v1`** (np. `POST /api/v1/auth/login`).

- `POST /auth/register` — Body: `{ "email", "password", "displayName" }` → 201 + UserDto  
- `POST /auth/login` — Body: `{ "email", "password" }` → 200 + `{ "token", "user" }`  
- `GET /auth/me` — Header: `Authorization: Bearer <token>` → 200 + UserDto (401 bez tokena)  
- `GET /leagues` — Header: Bearer; 200 + List[LeagueDto] (liga użytkownika)  
- `GET /invitations` — Header: Bearer; 200 + List[InvitationDto] (oczekujące zaproszenia użytkownika)  
- `POST /leagues` — Header: Bearer; Body: `{ "name", "teamCount", "myTeamName", "timezone?" }` → 201 + CreateLeagueResponse(league, team)  
- `GET /leagues/:id` — 200 + LeagueDto  
- `GET /leagues/:id/table` — 200 + List[TableRowDto]  
- `GET /leagues/:id/teams` — 200 + List[TeamDto]  
- `GET /leagues/:id/fixtures` — Query: `limit`, `offset` (opcjonalne); 200 + List[MatchDto]  
- `POST /leagues/:id/invite` — Header: Bearer; Body: `{ "email" }` → 201 + InvitationDto  
- `POST /invitations/accept` — Header: Bearer; Body: `{ "token", "teamName" }` → 200 + AcceptInvitationResponse(league, team)  
- `POST /leagues/:id/start` — Header: Bearer; Body: `{ "startDate"?: "YYYY-MM-DD" }` → 200 + LeagueDto (start sezonu: generator graczy, sędziowie, terminarz, okna transferowe)  
- `POST /leagues/:id/add-bots` — Header: Bearer; Body: `{ "count" }` → 201 (dodanie botów do ligi w Setup)  
- `POST /leagues/:id/matchdays/current/play` — Header: Bearer; rozegranie bieżącej kolejki (idempotentne: jeśli kolejka już rozegrana, brak działania); dla każdego meczu: domyślne składy, silnik, zapis wyników; po meczach: aktualizacja freshness/morale graczy (MODELE §7–§8), zdarzenia Injury → Player.injury, regeneracja +0.15 freshness, czyszczenie wygasłych kontuzji, aktualizacja statusu okien transferowych (Open/Closed); zwiększenie currentMatchday → 200  
- `GET /matches/:id` — 200 + MatchDto  
- `GET /matches/:id/log` — Query: `limit`, `offset` (opcjonalne); 200 + MatchLogDto(events, summary?, total?). Pole `summary` to **pełny** MatchSummaryDto (KONTRAKTY §2.3): possessionPercent, homeGoals, awayGoals, shotsTotal, shotsOnTarget, shotsBlocked, passesTotal, fouls, corners, crosses, longBalls, interceptions, redCards, throwIns, freeKicksWon, offsides itd. — listy jako [home, away]. Gdy silnik zwraca analitykę: **vaepTotal**, **wpaFinal** także w summary.
- `GET /transfer-offers` — Query: **leagueId** (wymagany; brak → 400 Bad Request), teamId (opcjonalny). Lista ofert.  
- `GET /matches/:id/squads` — 200 + List[MatchSquadDto]  
- `GET /teams/:id` — 200 + TeamDto  
- `GET /teams/:id/players` — 200 + List[PlayerDto]  
- `GET /teams/:id/game-plans` — 200 + List[GamePlanSnapshotDto]
- `GET /teams/:id/game-plans/:snapshotId` — 200 + GamePlanSnapshotDetailDto (z gamePlanJson)
- `POST /teams/:id/game-plans` — Header: Bearer; Body: SaveGamePlanRequest (name, gamePlanJson) → 201 + GamePlanSnapshotDto
- `GET /leagues/:id/transfer-windows` — 200 + List[TransferWindowDto]  
- `POST /leagues/:id/transfer-offers` — Header: Bearer; Body: CreateTransferOfferRequest → 201 + TransferOfferDto  
- `POST /transfer-offers/:id/accept` — Header: Bearer; 200 (tylko właściciel drużyny kupującej)  
- `POST /transfer-offers/:id/reject` — Header: Bearer; 200 (tylko właściciel drużyny kupującej)  
- `PUT /matches/:id/squads/:teamId` — Header: Bearer; Body: SubmitMatchSquadRequest (lineup, gamePlanJson) → 200 + MatchSquadDto  
- `PATCH /players/:id` — Header: Bearer; Body: UpdatePlayerRequest (firstName?, lastName?) → 200 + PlayerDto  

Pełna specyfikacja API i flow jest w `KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md`.

## 6. Baza danych

Schemat tworzony przy starcie w `Database.initSchema` (H2): tabele `users`, `leagues`, `teams`, `invitations`, `players`, `referees`, `matches`, `match_result_logs`, `match_squads`, `transfer_windows`, `transfer_offers`, `league_contexts`, `game_plan_snapshots`. Dla PostgreSQL należy podmienić URL i driver w konfiguracji (oraz ewentualnie typy kolumn JSON/JSONB).

## 7. Co jest zaimplementowane, a co pozostało

- **Zaimplementowane**: rejestracja, logowanie (JWT), GET /auth/me; tworzenie ligi (POST /leagues z JWT); zaproszenia (POST /leagues/:id/invite, POST /invitations/accept); start sezonu (POST /leagues/:id/start — generator 18 zawodników na drużynę z balansem, terminarz round-robin, sędziowie teamCount/2, okna transferowe co 2 kolejki); POST /leagues/:id/add-bots; **MatchEngine** (SimpleMatchEngine: walidacja 11v11, Poisson bramki, **pełna lista typów zdarzeń** z KONTRAKTY §2.1 — KickOff, Pass, LongPass, Cross, PassIntercepted, Dribble, DribbleLost, Shot, Goal, Foul, YellowCard, RedCard, Injury, Substitution, Corner, ThrowIn, FreeKick, Offside); **MatchSquad** i **MatchResultLog** (repozytoria, domyślny skład 4-3-3); **play matchday** (POST /leagues/:id/matchdays/current/play — domyślne składy, wywołanie silnika, zapis logu i wyniku, aktualizacja tabeli); GET /matches/:id, /matches/:id/log, /matches/:id/squads; GET /teams/:id, /teams/:id/players, /teams/:id/game-plans (lista zapisanych taktyk); GET /leagues/:id/table (obliczana z rozegranych meczów); modele domenowe i repozytoria (w tym MatchSquad, MatchResultLog, Referee.findById); test flow: create league → add-bots → start season → GET fixtures → play matchday → GET table.
- **Zaimplementowane (dodatkowo)**: API transferów (GET transfer-windows, GET transfer-offers z query leagueId/teamId, POST /leagues/:id/transfer-offers, accept/reject z walidacją 16–20 graczy i budżetu); PUT /matches/:id/squads/:teamId (zapis składu 11 graczy + 1 GK, walidacja dostępności); PATCH /players/:id (edycja imienia/nazwiska); repozytorium TransferOffer, PlayerRepository.findById/updateTeamId/updateName/updateFreshnessMorale/updateInjury, TeamRepository.update, TransferWindowRepository.update.
- **Zaimplementowane (post-match i okna)**: Po rozegraniu kolejki: aktualizacja freshness graczy (−0.25 za 90 min dla grających), morale (±0.05 za wygraną/porażkę, cała kadra), zdarzenia Injury z logu → Player.injury; regeneracja +0.15 freshness dla wszystkich w lidze; czyszczenie kontuzji gdy currentMatchday >= returnAtMatchday; aktualizacja statusu okien transferowych (Open/Closed według currentMatchday). Po rozegraniu **ostatniej** kolejki (currentMatchday = totalMatchdays) liga przechodzi w **seasonPhase = Finished**. Idempotencja „Rozegraj kolejkę”: jeśli wszystkie mecze kolejki mają status Played, wywołanie nie wykonuje symulacji ani inkrementacji. **Jedna transakcja JDBC** na operację „Rozegraj kolejkę”: wszystkie zapisy (logi meczów, aktualizacje meczów, post-match freshness/morale/injury, regeneracja, okna transferowe, currentMatchday, seasonPhase) wykonywane są w jednym `ConnectionIO` i jednym `.transact(xa)`.
- **Frontend (Scala.js + Laminar)**: Projekt `frontend` w sbt; Laminar 17.2.1, ZIO 2.0.21, Circe, scalajs-dom; ekrany: Logowanie, Rejestracja, Dashboard (wyloguj, formularz „Utwórz ligę”); ApiClient (Fetch API) do POST /auth/login, /auth/register, POST /leagues; DTO w `app.ApiDto` (zgodne z backendem). Kompilacja: `sbt frontend/compile`; wynik w `frontend/target/scala-3.3.3/frontend-fastopt.js`; do testów: serwować `frontend/index.html` i skrypt JS, backend na localhost:8080.
- **Zaimplementowane (dalsze)**: Tie-break tabeli z H2H (mecz bezpośredni) i stabilnym sortem; MatchSummary agregowane z events i zapisywane w MatchResultLog; GET /leagues (lista lig użytkownika); LeagueContext (tabela league_contexts, obliczanie mean/stddev przy starcie sezonu, przekazywanie do silnika); GamePlanSnapshot (repozytorium, POST /teams/:id/game-plans, GET lista + GET /teams/:id/game-plans/:snapshotId z gamePlanJson); silnik rozszerzony o strictness (faule, kartki), morale → modyfikator lambda, zdarzenia Pass, Shot, Foul, YellowCard, Corner, Injury; scheduler co 5 min wywołujący runScheduledMatchdays() (rozgrywka o 17:00 w timezone ligi); frontend: lista lig na dashboardzie, widok ligi (tabela, terminarz, „Rozegraj kolejkę”, okna transferowe i oferty), widok kadry (TeamPage), nawigacja liga → drużyna.
- **Zaimplementowane (frontend uzupełnienie)**: Ekran zaproszeń (link z tokenem, prefill z URL ?invitation=); ustawienia meczu (skład 11, wybór zapisanej taktyki); szczegóły meczu z pełnym logiem; pełny flow transferów (składanie oferty z UI, akceptacja/odrzucenie); sesja (localStorage JWT + GET /auth/me przy starcie); widok ligi w fazie Setup (lista slotów, zaproszenie e-mail, dodaj boty, start sezonu); edycja imienia/nazwiska gracza (TeamPage); zapisane taktyki (lista, zapis, wybór przy ustawianiu składu). Backend: jedna aktywna oferta na (playerId, windowId, fromTeamId) — przy składaniu nowej poprzednie Pending od tego samego kupującego są odrzucane.
- **Zaimplementowane (sesja 2026)**: Prefix `/api/v1` dla wszystkich tras; paginacja logu meczu (`GET /matches/:id/log?limit=&offset=`); MatchLogDto z opcjonalnym `summary` (pełny MatchSummaryDto, w tym shotsBlocked, vaepTotal, wpaFinal gdy silnik zwraca analitykę) i `total`; **GET /transfer-offers** bez query **leagueId** → 400 Bad Request (test w ApiIntegrationSpec); **SeasonPhase.Finished** — po rozegraniu ostatniej kolejki liga przechodzi w Finished; **Bot auto-akceptacja oferty transferowej** — gdy oferta jest do drużyny bota i amount ≥ estimatedPrice (basePrice×(overall/10)²), oferta jest od razu akceptowana; **Pula imion/nazwisk** w PlayerGenerator (losowy wybór z list firstNames/lastNames); testy jednostkowe silnika (SimpleMatchEngineSpec) i use case (LeagueServiceSpec); frontend: filtr zdarzeń po typie na stronie meczu, blok „Podsumowanie meczu” (posiadanie, strzały, xG, podania, kartki, dośrodkowania, przechwyty itd.); formularz „Utwórz ligę” z walidacją (liczba drużyn 10–20 parzysta, strefa czasowa); lista zaproszeń na dashboardzie (GET /invitations) z przyciskiem „Dołącz”; spójna obsługa błędów API (ErrorBody.message w ApiClient przy 4xx/5xx); analityka w silniku: possession/shotCount/xG z zdarzeń, VAEP dla strzelców (+0.25), WPA timeline (0–90 min, skok po golach), vaepTotal i wpaFinal w MatchSummary.
- **Konfiguracja**: JWT_SECRET i DATABASE_URL odczytywane ze zmiennych środowiskowych (fallback: `change-me-in-production`, H2 in-memory); w produkcji ustawić zmienne env.
- **Testy integracyjne API**: ApiIntegrationSpec (backend) — rejestracja, logowanie, GET /auth/me przez Client.fromHttpApp (bez uruchamiania serwera).
- **Paginacja w UI**: Strona szczegółów meczu ładuje log partiami (50 zdarzeń), przycisk „Pokaż więcej” gdy `total` > załadowanych.
- **Zaimplementowane (dokończenie)**: Silnik: effective composure/decisions z morale (MODELE §7) — używane przy wyniku strzału i ryzyku kartki; injury prone (traits) w prawdopodobieństwie kontuzji. ThrowInConfig w shared/domain i GamePlanInput (FORMACJE §13.4). Paginacja terminarza: GET /leagues/:id/fixtures?limit=&offset=, UI „Pokaż więcej”.
- **VAEP/WPA i ThrowIn w silniku**: VAEP rozszerzone o Pass (+0.02/–0.03), Shot (Saved/Missed/Blocked), ThrowIn (+0.01); WPA timeline co 10 min. Zdarzenia ThrowIn (2–4 na mecz), opcjonalnie defaultTaker z ThrowInConfig. Analityka: strefa (zone 1–12), xPass i zoneThreat (wartość zagrożenia strefy w stylu DxT) w metadanych Pass/Shot, IWP przy Foul, zmęczenie (Pass po 70. min).
- **ACWR**: recentMinutesPlayed w PlayerMatchInput; ryzyko kontuzji = injuryProne × acwrFactor(minuty z ostatnich 3 meczów); LeagueService oblicza minuty z lineupów.
- **Zaimplementowane (FullMatchEngine + boty)**: Maszyna stanów zdarzenie po zdarzeniu; Pitch Control; DxT; formułowe xG (FormulaBasedxG) i VAEP (FormulaBasedVAEP); TriggerConfig; BotSquadBuilder; parseGamePlan. **Model xG z pliku**: `LoadablexGModel` (współczynniki z JSON, regresja logistyczna) — dowód podpinania zewnętrznego modelu; szczegóły i ścieżka do XGBoost/LightGBM (ONNX, Smile): **`docs/ML_INTEGRACJA.md`**.

## 8. Spójność z dokumentacją

- Modele w `backend/domain/Domain.scala` są zgodne z `MODELE_I_PRZEPLYWY_APLIKACJI.md` i `KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md` (MatchSummary, MatchEventRecord, tie-break tabeli itd.).
- API i flow opisane w `KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md` §5–§8 stanowią docelową specyfikację; obecny backend realizuje ich podzbiór.
- Mapowanie algorytmów (xG, VAEP, WPA, zoneThreat, ACWR, atrybuty, statystyki) na kod: **ALGORYTMY_MAPOWANIE_KOD.md**.

---

*Dokumentacja techniczna v1 — luty 2026*
