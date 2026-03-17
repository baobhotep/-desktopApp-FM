# Plan wdrożenia: pełnoprawna gra Steam (jedna aplikacja, silnik w Scala)

**Wersja:** 2.0  
**Data:** 2025-03  
**Wizja:** Gra menedżerska na Steam w modelu **typowym dla gier single-player**: jeden proces, jedna aplikacja (.exe / JAR). Cała logika (silnik meczu, liga, baza) w **Scala/JVM**; warstwa wizualna i UI w tym samym procesie – **LibGDX** (Scala/Java). **Brak HTTP i brak drugiego procesu.**

---

## 1. Podsumowanie wykonawcze

| Aspekt | Decyzja |
|--------|--------|
| **Architektura** | **Jeden proces** – jedna aplikacja desktop (jak typowa gra na Steam) |
| **Silnik i logika** | Scala (bez zmian): FullMatchEngine, LeagueService, UserService, baza Doobie/H2 – **wywołania bezpośrednie**, bez HTTP |
| **UI i wizualizacja** | **LibGDX** (JVM): okno, menu, ekrany (login, liga, drużyna, składy), **wizualizacja meczu 2D** (boisko, eventy) – wszystko w tym samym procesie co silnik |
| **Komunikacja** | **Brak** – warstwa „klienta” wywołuje serwisy (UserService, LeagueService) jako **biblioteki** w tym samym JVM |
| **Baza** | H2 w pliku w katalogu użytkownika (np. `~/.local/share/FMGame/` lub odpowiednik Windows) |
| **Pakowanie** | Jedna dystrybucyjna (jlink + jpackage lub GraalVM native image) – jeden uruchamialny plik / instalator |

**Zespół:** 3 agenci + Agent 4 (koordynacja, integracja, testy E2E).  
**Ten dokument** jest szablonem współpracy: aktualizacja statusu zadań i Notes w tabeli.

---

## 2. Analiza stanu obecnego

### 2.1 Backend (Scala) – stanie się „rdzeniem gry”

- **Lokalizacja:** `backend/`
- **Obecne wejście:** `Main.scala` – ZIO App + **ZIO HTTP** (serwer na porcie 8080). To wejście **nie będzie używane** w buildzie gry desktop (albo pozostanie tylko do testów / ewentualnej wersji web).
- **Do zachowania:** Wszystko poza warstwą HTTP: `engine/` (FullMatchEngine, PitchModel, analityka), `service/` (LeagueService, UserService, MatchService itd.), `repository/`, `domain/`, baza (Doobie, H2). Serwisy są wywoływane przez **Routes** – w wersji desktop będą wywoływane **bezpośrednio** przez warstwę LibGDX.
- **DTO / modele:** `shared/` (ApiDto) – używane dalej; w desktopie te same klasy (UserDto, LeagueDto, MatchLogDto itd.) jako kontrakt między serwisami a ekranami.

### 2.2 Warstwa HTTP – do usunięcia lub wyłączenia w buildzie gry

- **Routes.scala** – endpointy REST. W architekturze „jedna gra” **nie są potrzebne**. Opcje: (a) usunąć moduł HTTP z buildu desktop i dodać **fasadę** (np. `GameFacade`) wywoływaną z LibGDX, albo (b) zostawić Routes dla ewentualnej wersji web, a dla desktop dodać drugi punkt wejścia, który **nie startuje** HTTP, tylko inicjuje DB + serwisy i uruchamia LibGDX.
- **Autentykacja:** Zamiast JWT przez sieć – w jednym procesie można trzymać „bieżącego użytkownika” (UserId, UserDto) w pamięci po udanym loginie (UserService.login zwraca użytkownika; sesja = stan w aplikacji).

### 2.3 Frontend (Laminar/Scala.js) – do usunięcia

- Zastąpiony przez **moduł desktop** (LibGDX) w tym samym repo. Po wdrożeniu gry frontend można usunąć lub zostawić tylko jeśli będzie wersja przeglądarkowa.

### 2.4 Co zostaje, co się zmienia

| Element | Zostaje | Zmiana |
|--------|--------|--------|
| Silnik (FullMatchEngine, analityka, PitchModel) | tak | brak |
| Serwisy (LeagueService, UserService, …) | tak | Wywołania z warstwy LibGDX (ten sam proces) |
| Repozytoria, baza, Doobie | tak | Baza w katalogu użytkownika; jeden Transactor w aplikacji |
| DTO (shared) | tak | Używane przez serwisy i ekrany LibGDX |
| ZIO HTTP, Routes | nie (dla gry) | Nie uruchamiane w buildzie desktop; opcjonalnie fasada „GameAPI” zamiast HTTP |
| Frontend Laminar | nie | Zastąpiony przez moduł desktop (LibGDX) |

---

## 3. Architektura docelowa (jedna aplikacja)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Jeden proces JVM – gra Steam (np. FMGame.exe / fm-game.jar)             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Warstwa prezentacji (LibGDX)                                    │   │
│   │  - Okno, input, pętla gry                                        │   │
│   │  - Ekrany: Login, Menu, Liga, Drużyna, Skład, Lista meczów,     │   │
│   │    Odtwarzanie meczu 2D (boisko, eventy, play/pause)             │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                              │                                           │
│                              │ wywołania bezpośrednie (bez HTTP)          │
│                              ▼                                           │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │  Warstwa domenowa (Scala) – obecny backend bez HTTP              │   │
│   │  - UserService, LeagueService, MatchService, …                   │   │
│   │  - FullMatchEngine, SimpleMatchEngine, analityka                 │   │
│   │  - Doobie Transactor, H2 w pliku (katalog użytkownika)           │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Start aplikacji:** Jeden punkt wejścia (np. `desktop.Main` lub `game.GameMain`): uruchomienie ZIO (runtime), inicjalizacja bazy (ścieżka z configu/użytkownika), utworzenie Transactor i serwisów, następnie **start LibGDX** (`Lwjgl3Application`). LibGDX dostaje referencję do „GameAPI” lub bezpośrednio do serwisów (przez konstruktor głównej klasy gry lub singleton).
- **Przepływ meczu:** Ekran „Rozegraj kolejkę” wywołuje `LeagueService.playMatchday(...)`; po zakończeniu ekran listy meczów wywołuje `LeagueService.getMatchLogForUser(...)` i przekazuje `MatchLogDto` (events, summary) do ekranu odtwarzania meczu 2D. Brak HTTP – same wywołania ZIO/serwisów (np. `runtime.unsafe.run(leagueService.playMatchday(...))` z wątku LibGDX lub przez mostek async).

---

## 4. Fazy i kamienie milowe

| Faza | Nazwa | Cel | Kryterium zakończenia |
|------|--------|-----|------------------------|
| **F1** | Rdzeń gry bez HTTP | Punkt wejścia „desktop”: inicjalizacja DB (ścieżka w katalogu użytkownika), utworzenie serwisów **bez** startu ZIO HTTP; fasada/API do wywołań z zewnątrz (np. login, listLeagues, playMatchday, getMatchLog) | Uruchomienie bez serwera HTTP; testy jednostkowe/integracyjne serwisów działają; możliwość wywołania serwisów z innego modułu (desktop) |
| **F2** | Moduł desktop (LibGDX) – szkielet i auth | Nowy moduł `desktop/` (lub `game/`), zależność od backendu; okno LibGDX, ekran logowania i rejestracji; wywołanie UserService (login/register) przez fasadę; przechowanie „bieżącego użytkownika” w stanie gry | Użytkownik może się zalogować w oknie gry; brak HTTP |
| **F3** | Ekrany menu i flow | Menu główne, lista lig, widok ligi (tabela, terminarz), widok drużyny, ekran składu przed meczem, przycisk „Rozegraj kolejkę” (wywołanie playMatchday), lista meczów z wynikami | Pełny flow od logowania do „wyniki kolejki widoczne”; wszystkie dane z serwisów w tym samym procesie |
| **F4** | Dane meczu i przekazanie do odtwarzania | Po wyborze meczu: wywołanie getMatchLog (lub odpowiednika); przechowanie events + summary; przejście do ekranu odtwarzania z przekazanym MatchLogDto | Ekran odtwarzania otrzymuje listę eventów i summary bez HTTP |
| **F5** | Wizualizacja meczu 2D (LibGDX) | Scena 2D: boisko (105×68, 24 strefy), mapowanie zone → (x,y) (PitchModel.zoneCenters), zawodnicy/piłka, odtwarzanie eventów (Pass, Shot, Goal itd.), play/pause, prędkość, HUD (minuta) | Odtworzenie meczu od KickOff do końca w tym samym procesie |
| **F6** | Dopracowanie i Steam | Pakowanie (jlink/jpackage lub GraalVM), jedna dystrybucyjna; Steamworks (achievements, cloud) jeśli potrzebne; testy E2E | Jedna „build” do dystrybucji; E2E: start → login → liga → kolejka → mecz → wizualizacja |

---

## 5. Szablon zadań (do uzupełniania przez agenty)

**Instrukcja:** Owner: A1 / A2 / A3. Status: `TODO` | `IN_PROGRESS` | `REVIEW` | `DONE`. Notes: postępy, blokery. **Agent 4** – weryfikacja E2E, uwagi integracyjne.

### Legenda Owner

- **A1** = Agent 1 (Rdzeń: wyłączenie HTTP, fasada, DB, inicjalizacja dla desktop)
- **A2** = Agent 2 (LibGDX: ekrany, menu, liga, drużyna, składy, flow)
- **A3** = Agent 3 (LibGDX: wizualizacja meczu 2D, odtwarzanie eventów)
- **A4** = Agent 4 (Integracja, testy E2E, koordynacja)

### Faza F1 – Rdzeń gry bez HTTP

| ID | Nazwa | Owner | Status | Notes | Depends on |
|----|--------|-------|--------|-------|------------|
| F1.1 | Ścieżka bazy H2 do katalogu użytkownika (np. `~/.local/share/FMGame/` lub `%APPDATA%/FMGame`); konfiguracja z pliku/env | A1 | DONE | GameConfig: userDataDir(), jdbcUrl(); env GAME_DATA_DIR, DATABASE_URL. | - |
| F1.2 | Punkt wejścia „desktop”: inicjalizacja DB, Transactor, serwisów **bez** uruchamiania ZIO HTTP (osobna main lub flaga; Routes nie są startowane) | A1 | DONE | DesktopBootstrap.bootstrap(runtime), DesktopMain (ZIO App bez HTTP). Uruchomienie: `sbt "backend/runMain fmgame.backend.DesktopMain"`. | F1.1 |
| F1.3 | Fasada „GameAPI” (lub DesktopFacade): metody odpowiadające kluczowym operacjom (login, register, listLeagues, getLeague, getTable, getFixtures, playMatchday, getMatch, getMatchLog, getSquads, submitSquad itd.) – wywołania UserService/LeagueService; zwracanie DTO (shared). Mostek ZIO ↔ wątek LibGDX (np. Unsafe.run lub dedicated pool) | A1 | DONE | GameFacade w backendzie: runSync przez Unsafe.run; login, register, getMe, listLeagues, getLeague, getTable, getFixtures, playMatchday, getMatch, getMatchLog, getSquads, submitSquad, getTeam, getTeamPlayers, listTeams. A2 może podłączyć prawdziwe API. | F1.2 |
| F1.4 | (Opcjonalnie) Zachowanie obecnego Main z HTTP jako osobny entry (np. „web”) dla testów lub przyszłej wersji web; build gry desktop używa tylko entry bez HTTP | A1 | DONE | Main.scala bez zmian (HTTP); DesktopMain = entry bez HTTP. Build desktop: mainClass = DesktopMain. | F1.2 |

### Faza F2 – Desktop szkielet i auth

| ID | Nazwa | Owner | Status | Notes | Depends on |
|----|--------|-------|--------|-------|------------|
| F2.1 | Nowy moduł `desktop/` (lub `game/`) w repo: zależność od backendu, LibGDX (Lwjgl3), Scala 3; główna klasa uruchamiająca ZIO (init DB + serwisy) i LibGDX | A2 | DONE | Moduł desktop: FMGame(gameApi). DesktopLauncher: bootstrap ZIO → GameFacadeAdapter lub StubGameAPI (A4). | F1.2 |
| F2.2 | Ekran logowania (LibGDX Screen): pola email/hasło, przycisk; wywołanie GameAPI.login; zapis „bieżącego użytkownika” (UserId, UserDto) w stanie gry; przejście do menu głównego | A2 | DONE | LoginScreen (Scene2D), doLogin() → gameApi.login; setCurrentUser; setScreen(MainMenuScreen). | F1.3, F2.1 |
| F2.3 | Ekran rejestracji: email, hasło, displayName; wywołanie GameAPI.register; po sukcesie powrót do logowania lub auto-login | A2 | DONE | RegisterScreen; po sukcesie powrót do LoginScreen. | F1.3, F2.1 |
| F2.4 | Przy starcie aplikacji: jeśli brak zapisanego użytkownika/sesji → ekran logowania; opcjonalnie „zapamiętaj” (zapis w pliku w katalogu użytkownika) | A2 | DONE | SessionPersistence w katalogu GameConfig; checkbox „Zapamiętaj mnie”; getMe() przy starcie; wylogowanie czyści plik. | F2.2 |

### Faza F3 – Menu i flow

| ID | Nazwa | Owner | Status | Notes | Depends on |
|----|--------|-------|--------|-------|------------|
| F3.1 | Menu główne: przyciski „Wybierz ligę”, „Opcje”, „Wyloguj” | A2 | DONE | MainMenuScreen: Wybierz ligę → LeagueListScreen, Wyloguj → LoginScreen. Opcje: placeholder. | F2.2 |
| F3.2 | Lista lig: wywołanie GameAPI.listLeagues(currentUserId); wyświetlenie; wybór ligi → widok ligi | A2 | DONE | LeagueListScreen; na stubie pusta lista; wybór → LeagueViewScreen(game, leagueId). | F3.1 |
| F3.3 | Widok ligi: tabela (GameAPI.getTable), terminarz (GameAPI.getFixtures), currentMatchday; przycisk „Rozegraj kolejkę” | A2 | DONE | LeagueViewScreen: getTable, getFixtures, playMatchday; po kolejce odświeżenie; terminarz + „Obejrzyj mecz” (A4 integracja). | F3.2 |
| F3.4 | Widok drużyny użytkownika: GameAPI.getTeam, getTeamPlayers; podstawowe info + lista zawodników | A2 | DONE | TeamViewScreen; przycisk „Moja drużyna” w LeagueViewScreen; listTeams w GameAPI. | F3.2 |
| F3.5 | Przed meczem: wybór meczu z terminarza; GameAPI.getSquads, submitSquad; ekran edycji składu (11 + rezerwowi) | A2 | DONE | SquadScreen: getMatchSquads, „Ustaw skład” dla Scheduled; submitMatchSquad z bieżącym lineupem. | F3.3 |
| F3.6 | Przycisk „Rozegraj kolejkę”: GameAPI.playMatchday(leagueId, userId); po zakończeniu odświeżenie tabeli i terminarza | A2 | DONE | Odświeżenie przez setScreen(new LeagueViewScreen(...)). | F3.3 |

### Faza F4 – Dane meczu i przekazanie do odtwarzania

| ID | Nazwa | Owner | Status | Notes | Depends on |
|----|--------|-------|--------|-------|------------|
| F4.1 | Lista meczów z wynikami po kolejce; wybór meczu → „Obejrzyj mecz” | A2 | DONE | Zrealizowane w LeagueViewScreen (terminarz + przycisk „Obejrzyj mecz” per mecz; A4). | F3.6 |
| F4.2 | Wywołanie GameAPI.getMatchLog(matchId); przekazanie MatchLogDto (events, summary) do ekranu odtwarzania meczu (Screen A3) | A2 | DONE | LeagueViewScreen: getMatchLog → setPreviousScreen(this), setScreen(MatchPlaybackScreen(game, dto)). | F4.1 |
| F4.3 | (Opcjonalnie) Ekran podsumowania meczu (wynik, posiadanie, xG) przed odtwarzaniem | A2 | DONE | MatchSummaryScreen: wynik, posiadanie, xG, strzały; przycisk „Odtwórz mecz”. | F4.2 |

### Faza F5 – Wizualizacja meczu 2D

| ID | Nazwa | Owner | Status | Notes | Depends on |
|----|--------|-------|--------|-------|------------|
| F5.1 | Ekran LibGDX (Screen): boisko 2D (105×68 jednostek lub skalowane), 24 strefy zgodne z PitchModel (6×4) | A3 | DONE | MatchPlaybackScreen w desktop/screens; konstruktor: (game: FMGame, matchLogDto: MatchLogDto). A2: game.setScreen(new MatchPlaybackScreen(game, dto)) | F4.2 |
| F5.2 | Mapowanie zone 1–24 na (x,y) – wartości z PitchModel.zoneCenters (współdzielone z backendem lub skopiowane do desktop) | A3 | DONE | Użycie PitchModel.zoneCenters z backendu (desktop zależy od backend) | F5.1 |
| F5.3 | Zawodnicy i piłka (sprites/shapes); pozycje z eventów (actor, secondary, zone) | A3 | DONE | Kółka: piłka biała, aktor niebieski, secondary czerwony; pozycje ze strefy eventu | F5.2 |
| F5.4 | Odtwarzanie sekwencji eventów (KickOff, Pass, Shot, Goal, Foul itd.) z animacjami; timer/prędkość | A3 | DONE | Pętla po events, eventDuration 1.5s, przejście do kolejnego eventu; po Goal pauza 2s | F5.3 |
| F5.5 | Sterowanie: play/pause, prędkość (1x, 2x); HUD: minuta, typ eventu; po Goal – pauza/podkreślenie | A3 | DONE | Spacja=play/pause, 1/2=prędkość; HUD: minuta, typ eventu, PLAY/PAUSE; przycisk Wstecz, ESC=powrót | F5.4 |

### Faza F6 – Dopracowanie i Steam

| ID | Nazwa | Owner | Status | Notes | Depends on |
|----|--------|-------|--------|-------|------------|
| F6.1 | Pakowanie: jlink + jpackage (lub GraalVM native) – jeden uruchamialny/instalator (Windows); uruchomienie = jedna JVM z LibGDX, bez osobnego serwera | A1 + A4 | DONE | sbt-native-packager (JavaAppPackaging); `sbt desktop/stage` → target/universal/stage; docs/PAKOWANIE_DESKTOP.md (jpackage). | F2.1, F5.5 |
| F6.2 | Baza H2 w katalogu użytkownika w produkcji; brak ścieżek bezwzględnych | A1 | DONE | GameConfig.userDataDir() / databasePath() (F1.1). | F1.1 |
| F6.3 | (Opcjonalnie) Steamworks (achievements, cloud) – integracja w aplikacji JVM (np. Steamworks4j lub wrapper) | A2 lub A1 | TODO | | F6.1 |
| F6.4 | Testy E2E: start → login → liga → rozegranie kolejki → wybór meczu → wizualizacja do końca | A4 | DONE | DesktopE2ESpec: GameFacade flow (register, login, create league, listLeagues, getTable, playMatchday, getMatchLog) na H2 mem. | F6.1 |
| F6.5 | Poprawki i dopracowanie UX po testach E2E | A1, A2, A3 | DONE | Nazwa ligi + kolejka w LeagueViewScreen (getLeague); dokument pakowania. | F6.4 |

---

## 6. Kontrakt „API” (fasada dla desktop – bez HTTP)

- **Nie ma REST** – ekrany LibGDX wywołują **GameAPI** (lub bezpośrednio serwisy) w tym samym procesie.
- **Przykładowe metody fasady** (zwracają ZIO lub wynik po Unsafe.run / wrapperze synchronicznym):
  - `login(email, password)` → `Either[String, (UserDto, Token/Session)]`
  - `register(email, password, displayName)` → `Either[String, UserDto]`
  - `listLeagues(userId)` → `Either[String, List[LeagueDto]]`
  - `getLeague(leagueId, userId)` → `Either[String, LeagueDto + teams]`
  - `getTable(leagueId)` → `Either[String, List[TableRowDto]]`
  - `getFixtures(leagueId)` → `Either[String, List[MatchDto]]`
  - `playMatchday(leagueId, userId)` → `Either[String, Unit]`
  - `getMatch(matchId, userId)` → `Either[String, MatchDto]`
  - `getMatchLog(matchId, userId)` → `Either[String, MatchLogDto]` (events, summary)
  - `getSquads(matchId, userId)` → `Either[String, List[MatchSquadDto]]`
  - `submitSquad(matchId, squadTeamId, userId, body)` → `Either[String, MatchSquadDto]`
  - `getTeam(teamId, userId)`, `getTeamPlayers(teamId, userId)` itd.

- **MatchLogDto / MatchEventDto:** Bez zmian względem `shared` – events (minute, eventType, actorPlayerId, secondaryPlayerId, teamId, zone, outcome, metadata), summary. Mapowanie stref: `PitchModel.zoneCenters` (backend) – ten sam model w tym samym procesie, ekran odtwarzania może użyć tej samej mapy.

---

## 7. Rola Agent 4

- **Integracja:** Spójność modułu desktop z rdzeniem (fasada, ZIO runtime, wątek LibGDX).
- **Testy E2E:** Scenariusz i wykonanie F6.4; raportowanie błędów w Notes.
- **Aktualizacja planu:** Statusy i krótkie raporty po fazach.

---

## 8. Prompty dla agentów

| Agent | Plik promptu | Domena |
|-------|----------------|--------|
| Agent 1 | `docs/PROMPT_AGENT_1_CORE_DESKTOP.md` | Rdzeń: brak HTTP w buildzie gry, fasada GameAPI, DB w katalogu użytkownika, inicjalizacja dla LibGDX |
| Agent 2 | `docs/PROMPT_AGENT_2_LIBGDX_SCREENS.md` | LibGDX: szkielet, auth, menu, liga, drużyna, składy, rozgrywanie kolejki, lista meczów, przekazanie logu do A3 |
| Agent 3 | `docs/PROMPT_AGENT_3_LIBGDX_MATCH_2D.md` | LibGDX: ekran odtwarzania meczu 2D (boisko, eventy, play/pause) |

**Sposób użycia:** Otwórz odpowiedni plik promptu i działaj zgodnie z nim; w trakcie pracy aktualizuj **ten dokument** – tabelę zadań (Status, Notes).

---

## 9. Jak prowadzić prace i koordynować (w czasie rzeczywistym)

### Czy wszyscy mogą działać równolegle?

**Tak, z ograniczeniami:**

- **Agent 1 (A1)** musi najpierw dostarczyć **F1.2** (punkt wejścia desktop) i **F1.3** (fasada GameAPI), zanim A2 zintegruje prawdziwe logowanie i wywołania. Do tego momentu A2 może: zbudować moduł desktop, dodać LibGDX, zrobić szkielet ekranów i **stub** GameAPI (interfejs z pustymi implementacjami).
- **Agent 2 (A2)** i **Agent 3 (A3)** mogą pracować **równolegle**, jeśli na starcie ustalicie **kontrakt**:
  - **GameAPI** – lista metod (jest w sekcji 6 planu). A2 może zdefiniować trait/interface w module desktop; A1 implementuje go w rdzeniu (lub rdzeń go implementuje).
  - **MatchPlaybackScreen** – konstruktor przyjmuje `MatchLogDto` (z shared). A3 implementuje ekran; A2 gdy ma dane wywołuje `setScreen(new MatchPlaybackScreen(game, matchLogDto))`. A3 może od razu pracować na **dummy** liście eventów (np. 3–4 eventy w kodzie), żeby nie czekać na A2.
- **Agent 4 (A4)** może równolegle: pisać **scenariusz E2E** (kroki testu w dokumencie), sprawdzać spójność planu, a gdy pojawi się pierwszy uruchamialny build – uruchamiać testy i wpisywać wyniki w Notes.

### Jedno źródło prawdy

- **`docs/PLAN_STEAM_GAME.md`** – wszyscy aktualizują **tabelę zadań** (Status, Notes).
- Po ukończeniu zadania: Status → `DONE`, w Notes np. „F1.3 gotowe – A2 może podłączyć prawdziwe GameAPI”.
- Gdy coś blokuje: w Notes wpisz „Blokada: czekam na F1.3” lub „Interface: MatchPlaybackScreen(game, matchLogDto: MatchLogDto) – ustalone z A3”.

### Propozycja podziału w czasie (bez sztywnego „real time”)

1. **Start (dzień 1):**
   - **A1:** F1.1, F1.2, F1.3 (ścieżka DB, entry desktop, fasada).
   - **A2:** F2.1 – nowy moduł desktop, LibGDX, główna pętla; **interface GameAPI** (trait z metodami); stub implementacji; F2.2 na stubie (ekran logowania wywołuje stub).
   - **A3:** F5.1, F5.2 – ekran MatchPlaybackScreen z **dummy events** (lista 5–10 eventów w kodzie); boisko, strefy, mapowanie zone → (x,y).
   - **A4:** Wpis w planie: „Scenariusz E2E: 1) Uruchom desktop 2) Login 3) Lista lig 4) …” (szkic); sprawdzenie, że wszystkie zadania mają Owner.

2. **Po F1.3 (A1):**
   - **A2:** Podłączenie prawdziwej implementacji GameAPI (z backendu), F2.2–F2.4, F3.x – pełny flow do „Rozegraj kolejkę”.
   - **A3:** F5.3–F5.5 – zawodnicy, odtwarzanie, play/pause; nadal na dummy lub na danych z pliku testowego.
   - **A4:** Jeśli A2 ma działający login – pierwszy smokowy test (uruchom, zaloguj, sprawdź menu).

3. **Po F4.2 (A2) + F5.5 (A3):**
   - **A2:** Przekazanie MatchLogDto do `MatchPlaybackScreen(game, dto)`; ewentualne dopasowanie sygnatury z A3.
   - **A3:** Upewnienie się, że ekran przyjmuje prawdziwy MatchLogDto (te same typy co w shared).
   - **A4:** Pełny test E2E (F6.4): start → login → liga → kolejka → mecz → wizualizacja; wyniki w Notes.

4. **F6 (pakowanie, Steam, poprawki):**
   - A1 + A4 – pakowanie; A4 – powtarzanie E2E; A2/A3 – poprawki po testach (F6.5).

### „Real time” w praktyce

- **Nie ma jednego czatu dla wszystkich** – każdy agent to osobna sesja (osobne okno Cursor / osobny agent).
- **Koordynacja = dokument:** przed startem (lub na stand-upie) każdy otwiera `PLAN_STEAM_GAME.md`, sprawdza Status zadań od innych, czyta Notes. Po swojej sesji – aktualizuje Status i Notes.
- **Ty (product owner / koordynator):** możesz raz dziennie (lub po każdej „rundzie”) przejrzeć plan, zobaczyć co DONE, co IN_PROGRESS, co zablokowane (Notes), i w następnej kolejce powiedzieć np. „A2, F1.3 jest DONE – podłącz prawdziwe API” albo „A3, ustal z A2 sygnaturę MatchPlaybackScreen w Notes”.
- **Agent 4 (ja)** w każdej sesji: przegląd planu, aktualizacja weryfikacji E2E w Notes, ewentualnie drobne poprawki w dokumencie (np. doprecyzowanie Depends on). Gdy jest build – uruchamiam test E2E i wpisuję wynik.

### Krótka ściąga

| Kto       | Może od razu (równolegle)                    | Czeka na kogo |
|-----------|-----------------------------------------------|---------------|
| A1        | F1.1, F1.2, F1.3                             | –             |
| A2        | F2.1, stub GameAPI, szkielet ekranów         | F1.3 (prawdziwa fasada) |
| A3        | F5.1–F5.5 na dummy events                     | F4.2 tylko do „podłączenia” prawdziwego dto |
| A4        | Scenariusz E2E, przegląd planu                | F2.2 (pierwszy smok), F6.1 (pełny E2E) |

---

## 10. Kolejne kroki

1. Zatwierdzenie planu.
2. Ustalenie kontraktu: GameAPI (lista metod), MatchPlaybackScreen(matchLogDto) – np. wpis w Notes F1.3 i F5.1.
3. Start: A1 → F1; A2 → F2.1 + stub; A3 → F5.1/F5.2 na dummy; A4 → scenariusz E2E.
4. Po F1.3: A2 integruje prawdziwe API; A4 pierwszy smok. Po F4.2+F5.5: A4 pełny E2E.
5. Cykliczna aktualizacja tabeli i Notes w `PLAN_STEAM_GAME.md`.

---

## 11. Raport koordynacji (Agent 4)

*Agent 4 dopisuje tu krótkie wpisy po przeglądzie stanu: data, co sprawdzone, co zalecone.*

| Data | Wpis |
|------|------|
| (start) | Stan początkowy: wszystkie zadania TODO. A1, A2, A3 odpalone; A4 śledzi plan i koordynuje. Przy następnym wezwaniu: przegląd Status/Notes w tabeli zadań, podsumowanie dla użytkownika, ewentualne zalecenia (kto co robi dalej). |
| (status) | Przegląd: wszystkie zadania nadal TODO, Notes puste. Zalecenie: A1 zacząć od F1.1→F1.3; A2 od F2.1 (moduł + stub GameAPI); A3 od F5.1/F5.2 na dummy events. Po wykonaniu pracy każdy agent ma ustawić Status (IN_PROGRESS / DONE) i wpisać w Notes co zrobione. |
| (integracja) | Agenty zakończyły prace. A4: review w docs/REVIEW_INTEGRACJA_AGENTOW.md. Zepnięcie: GameFacadeAdapter (desktop), DesktopLauncher bootstrap ZIO + fallback StubGameAPI, LeagueViewScreen – terminarz, „Obejrzyj mecz” (getMatchLog → MatchPlaybackScreen), odświeżenie po „Rozegraj kolejkę”. Kompilacja desktop OK. Flow: login → menu → liga → rozegraj kolejkę → terminarz → Obejrzyj mecz → wizualizacja 2D. |
| (pozostałości + review) | F2.4 SessionPersistence, F3.4 TeamViewScreen, F3.5 SquadScreen, F4.3 MatchSummaryScreen, F6.2 DONE. Nazwy drużyn w terminarzu, ScrollPane, getMe/listTeams w GameAPI. cursor.md: 3 wpisy. Testy backend: 159 passed. |
| (F6) | F6.1: sbt-native-packager JavaAppPackaging, `desktop/stage`; docs/PAKOWANIE_DESKTOP.md. F6.4: DesktopE2ESpec (GameFacade flow: register→login→liga→playMatchday→getMatchLog). F6.5: nazwa ligi + kolejka w LeagueViewScreen (getLeague). |

---

*Dokument żywy: przy każdej zmianie statusu zadania aktualizuj tabelę i Notes.*
