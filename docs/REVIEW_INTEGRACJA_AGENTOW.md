# Review integracji pracy agentów (Steam / desktop)

**Data:** 2025-03  
**Autor:** Agent 4 (koordynacja, integracja)

## 1. Status zadań (z PLAN_STEAM_GAME.md)

| Faza | Zadania | Status w planie |
|------|---------|------------------|
| **F1** | F1.1–F1.4 | ✅ DONE (A1) |
| **F2** | F2.1–F2.4 | F2.1–F2.3 DONE, F2.4 IN_PROGRESS (zapamiętaj sesję – TODO) |
| **F3** | F3.1–F3.6 | F3.1–F3.3 DONE; F3.4–F3.6 TODO |
| **F4** | F4.1–F4.3 | TODO |
| **F5** | F5.1–F5.5 | ✅ DONE (A3) |
| **F6** | F6.1–F6.5 | TODO |

---

## 2. Przegląd zmian po stronie kodu

### 2.1 Backend (A1)

- **GameConfig** – ścieżka H2 w katalogu użytkownika (`userDataDir`, `jdbcUrl`), env: `GAME_DATA_DIR`, `DATABASE_URL`.
- **DesktopBootstrap** – inicjalizacja DB, Transactor, serwisów, `Database.initSchema`, silnik (FullMatchEngine), zwraca **GameFacade**.
- **DesktopMain** – punkt wejścia ZIO bez HTTP; uruchomienie: `sbt "backend/runMain fmgame.backend.DesktopMain"`.
- **GameFacade** – fasada synchroniczna (`runSync` przez `Unsafe.run`): login, register, getMe, listLeagues, getLeague, getTable, getFixtures, playMatchday, getMatch, getMatchLog, getSquads, submitSquad, getTeam, getTeamPlayers, listTeams.
- **Main.scala** – bez zmian (HTTP nadal dostępny).

**Uwaga:** GameFacade zwraca `LoginResponse(token, user)`; kontrakt **GameAPI** (shared) oczekuje `(UserDto, String)`. W desktop używany jest **StubGameAPI**, więc brak podłączenia prawdziwego backendu w launcherze.

### 2.2 Desktop – moduł i ekrany (A2)

- **Moduł desktop** – zależności: sharedJVM, backend; LibGDX (gdx, gdx-backend-lwjgl3, gdx-platform natives-desktop); mainClass: `fmgame.desktop.DesktopLauncher`.
- **FMGame** – trzyma `GameAPI`, `currentUser: Option[(UserId, UserDto)]`, `setPreviousScreen` / `returnToPreviousScreen`.
- **DesktopLauncher** – tworzy `FMGame(StubGameAPI)` – **brak integracji z GameFacade**.
- **LoginScreen** – email/hasło, `gameApi.login` → zapis użytkownika, przejście do MainMenuScreen.
- **RegisterScreen** – rejestracja, powrót do LoginScreen.
- **MainMenuScreen** – Wybierz ligę → LeagueListScreen, Wyloguj → LoginScreen, Opcje = placeholder.
- **LeagueListScreen** – `listLeagues`, przyciski lig → LeagueViewScreen(leagueId).
- **LeagueViewScreen** – getTable (top 5), przycisk „Rozegraj kolejkę” (playMatchday). **Brak:** terminarza (getFixtures), przycisku „Obejrzyj mecz” i przejścia do MatchPlaybackScreen.
- **Assets** – minimalna skórka (font, Label, TextButton, TextField).

### 2.3 Wizualizacja meczu 2D (A3)

- **MatchPlaybackScreen** – konstruktor `(game: FMGame, matchLogDto: MatchLogDto)`.
- Boisko 105×68 (PitchModel), siatka 6×4, strefy z `PitchModel.zoneCenters`.
- Piłka (biała), aktor (niebieski), secondary (czerwony); pozycje z `event.zone`.
- Odtwarzanie eventów (eventDuration 1.5s), po Goal pauza 2s.
- Sterowanie: Spacja play/pause, 1/2 prędkość, ESC/Wstecz = powrót; HUD: minuta, typ eventu, PLAY/PAUSE.

**Uwaga:** Ekran jest gotowy na wywołanie `game.setScreen(new MatchPlaybackScreen(game, matchLogDto))`, ale **żaden ekran nie wywołuje getMatchLog ani nie otwiera MatchPlaybackScreen** – brak przepływu „Obejrzyj mecz”.

---

## 3. Luki integracyjne (do „zepnięcia”)

1. **Desktop nie używa GameFacade**  
   Launcher tworzy `FMGame(StubGameAPI)`. Trzeba: przy starcie uruchomić `DesktopBootstrap.bootstrap(runtime)`, otrzymać `GameFacade`, opakować go w implementację **GameAPI** (adapter: login → `(user, token)`), przekazać do `FMGame(api)`. W razie błędu bootstrapu (np. brak DB) – fallback do StubGameAPI lub komunikat.

2. **Brak przepływu „Obejrzyj mecz”**  
   W LeagueViewScreen: brak listy meczów (getFixtures) i przycisku „Obejrzyj” wywołującego `getMatchLog(matchId, userId, None, None)` oraz `game.setPreviousScreen(this)` i `game.setScreen(new MatchPlaybackScreen(game, dto))`.

3. **Odświeżenie widoku ligi po „Rozegraj kolejkę”**  
   Po udanym playMatchday warto przeładować widok (np. `setScreen(new LeagueViewScreen(game, leagueId))`), żeby tabela i terminarz pokazały zaktualizowane dane.

4. **F3.4–F3.6, F4.x**  
   Świadomie pozostawione jako TODO (widok drużyny, edycja składu, lista meczów z wynikami); minimalny „zepnięty” flow to: login → menu → liga → rozegraj kolejkę → terminarz → „Obejrzyj mecz” → MatchPlaybackScreen.

---

## 4. Wykonane kroki integracji (Agent 4)

- **GameFacadeAdapter** (desktop) – klasa implementująca `GameAPI`, delegująca do `GameFacade`; `login` mapuje `LoginResponse` na `(UserDto, String)`; `getMatchSquads` → `getSquads`, `submitMatchSquad` → `submitSquad`.
- **DesktopLauncher** – przed startem LibGDX: `Runtime.default`, `DesktopBootstrap.bootstrap(runtime)` (Unsafe.run); przy sukcesie `FMGame(new GameFacadeAdapter(facade))`, przy błędzie fallback do `StubGameAPI` i log.
- **LeagueViewScreen** – ładowanie terminarza (`getFixtures(leagueId, userId, None, None)`), wyświetlenie listy meczów z przyciskiem „Obejrzyj”; przy „Obejrzyj” wywołanie `getMatchLog(matchId, userId, None, None)`, `setPreviousScreen(this)`, `setScreen(new MatchPlaybackScreen(game, dto))`; po „Rozegraj kolejkę” odświeżenie: `setScreen(new LeagueViewScreen(game, leagueId))`.

---

## 5. Rekomendacje na później

- F6: pakowanie (jlink/jpackage), testy E2E, dopracowanie UX, Steamworks (opcjonalnie).

---

## 6. Wykonane pozostałości i drugi review (Agent 4)

### 6.1 Zrealizowane zadania

- **F2.4** – SessionPersistence: zapis `userId` w `GameConfig.userDataDir()/session.txt`; przy starcie `getMe(userId)` → auto-login; checkbox „Zapamiętaj mnie” na LoginScreen; przy wylogowaniu `SessionPersistence.clear()`. W GameAPI dodano `getMe(userId)`.
- **F3.4** – TeamViewScreen: `listTeams(leagueId, userId)` → drużyna użytkownika (ownerUserId); getTeam, getTeamPlayers; przycisk „Moja drużyna” w LeagueViewScreen. W GameAPI dodano `listTeams`.
- **F3.5** – SquadScreen: dla meczów ze statusem „Scheduled” przycisk „Ustaw skład”; getMatchSquads, wybór składu swojej drużyny, submitMatchSquad z bieżącym lineupem i `gamePlanJson: "{}"`.
- **F3.6** – Odświeżenie po „Rozegraj kolejkę” już było (setScreen(new LeagueViewScreen(...))); w planie oznaczono DONE.
- **F4.3** – MatchSummaryScreen: wynik, posiadanie, xG, strzały z `matchLog.summary`; przycisk „Odtwórz mecz” → MatchPlaybackScreen. Przy „Obejrzyj mecz” jeśli `log.summary.isDefined` → najpierw MatchSummaryScreen.
- **F6.2** – Oznaczono DONE (GameConfig już używa katalogu użytkownika).

### 6.2 Poprawki UX i kodu

- **Terminarz:** nazwy drużyn zamiast UUID – `listTeams` → mapa `teamId -> name`; w wierszu meczu: `teamName(homeTeamId)` i `teamName(awayTeamId)`.
- **ScrollPane** w LeagueViewScreen – długi terminarz w ScrollPane, przycisk „Wstecz” pod spodem.
- **CheckBox** „Zapamiętaj mnie” – dodany styl w Assets (CheckBoxStyle z białymi drawable).

### 6.3 Review ekspercki (piłka + kod)

- **PitchModel:** 105×68 m, 6×4 strefy, zoneCenters spójne z backendem – OK.
- **MatchPlaybackScreen:** sterowanie Spacja/1/2/ESC działa przy stage jako InputProcessor (Gdx.input.isKeyJustPressed czyta stan globalnie). Aktor i secondary w tej samej strefie – dopisane do cursor.md jako lekcja na przyszłość (dla Pass można by poprzednią strefę).
- **MatchSummaryScreen:** possessionPercent i xgTotal to [home, away] – wyświetlanie poprawne.
- **cursor.md:** dopisane 3 wpisy (LibGDX getText + konkatenacja; zapamiętaj userId; wizualizacja jedna strefa).

---

## 7. F6 – Pakowanie, E2E, UX (wykonane)

- **F6.1** – sbt-native-packager (JavaAppPackaging) w projekcie desktop; `sbt desktop/stage` → `target/universal/stage`; dokumentacja w `docs/PAKOWANIE_DESKTOP.md` (stage + jpackage).
- **F6.4** – Test E2E: `DesktopE2ESpec` w backendzie – pełny flow przez GameFacade (register, login, utworzenie ligi przez LeagueService, listLeagues, getTable, playMatchday, getMatchLog) na H2 in-memory.
- **F6.5** – UX: w LeagueViewScreen wyświetlana jest nazwa ligi i numer kolejki z `getLeague` (np. „E2E League (kolejka 1/18)”).
