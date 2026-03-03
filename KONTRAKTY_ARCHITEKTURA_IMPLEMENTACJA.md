# Kontrakty, architektura i specyfikacja implementacji

**Cel**: Jedno źródło prawdy dla kontraktu silnika, API, schematu bazy, autentykacji, ekranów UI, edge case’ów i strategii testów — umożliwiające implementację w Scala 3 + ZIO bez domysłów.  
**Perspektywa**: Analityk programu, architekt oprogramowania, specjalista Scala 3 / ZIO.  
**Spójność z**: SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md, MODELE_I_PRZEPLYWY_APLIKACJI.md, WYMAGANIA_GRY.md, FORMACJE_ROLE_TAKTYKA.md, ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md

---

## Spis treści

1. [Kontrakt wejścia/wyjścia silnika meczu](#1-kontrakt-wejściawyjścia-silnika-meczu)
2. [Kanoniczna lista typów zdarzeń i format logu](#2-kanoniczna-lista-typów-zdarzeń-i-format-logu)
3. [MatchSquad — encja i reguły](#3-matchsquad--encja-i-reguły)
4. [Kolejność w tabeli (tie-break)](#4-kolejność-w-tabeli-tie-break)
5. [Specyfikacja API (REST)](#5-specyfikacja-api-rest)
6. [Ekrany i przepływy UI](#6-ekrany-i-przepływy-ui)
7. [Schemat bazy danych](#7-schemat-bazy-danych)
8. [Autentykacja i flow zaproszeń](#8-autentykacja-i-flow-zaproszeń)
9. [Edge cases i reguły biznesowe](#9-edge-cases-i-reguły-biznesowe)
10. [Strategia testów](#10-strategia-testów)
11. [Mapowanie dokumentów i warstw ZIO](#11-mapowanie-dokumentów-i-warstw-zio)
12. [Architektura Scala 3 i ZIO — szczegóły implementacyjne](#12-architektura-scala-3-i-zio--szczegóły-implementacyjne)
13. [Ustalenia architektoniczne (stack aplikacji)](#13-ustalenia-architektoniczne-stack-aplikacji)

---

## 1. Kontrakt wejścia/wyjścia silnika meczu

### 1.1 Po co kontrakt

Warstwa aplikacji (use case „Rozegraj kolejkę”) musi wiedzieć **dokładnie**, co przekazać do silnika i co z niego otrzymać. Silnik jest **czystym modułem obliczeniowym**: nie czyta bazy, nie zna League ani Match — dostaje tylko dane wejściowe i zwraca wynik. Kontrakt zapobiega rozjazdom między implementacją silnika a orkiestracją w ZIO.

### 1.2 Wejście (MatchEngineInput)

Wszystkie parametry potrzebne do jednej symulacji meczu w jednej strukturze. Źródło `home_advantage`, `referee`, `leagueContext` to **aplikacja** (np. z League, Match, Referee, LeagueContext); silnik tylko je przyjmuje.

```scala
/** Kontekst meczu — przekazywany przez warstwę aplikacji do silnika. */
case class MatchEngineInput(
  homeTeam: MatchTeamInput,
  awayTeam: MatchTeamInput,
  homePlan: GamePlan,
  awayPlan: GamePlan,
  homeAdvantage: Double,        // np. 1.05 (League.homeAdvantage)
  referee: RefereeInput,
  leagueContext: LeagueContextInput,
  randomSeed: Option[Long]      // None = losowy; Some(n) = deterministyczny (testy)
)

/** Zespół w kontekście pojedynczego meczu: 11 graczy + przypisanie do slotów. */
case class MatchTeamInput(
  teamId: TeamId,
  players: List[PlayerMatchInput],  // dokładnie 11 elementów
  lineup: Map[PlayerId, PositionSlot]  // który gracz na jakim slocie (klucz = player.id)
)

/** Zawodnik z danymi potrzebnymi w symulacji. Freshness i morale aplikacja uzupełnia z bazy. */
case class PlayerMatchInput(
  player: Player,               // pełny model z atrybutami, traits, bodyParams
  freshness: Double,            // 0.0–1.0 (Player.freshness)
  morale: Double                // 0.0–1.0 (Player.morale)
)

/** Sędzia — tylko to, czego silnik potrzebuje. */
case class RefereeInput(
  strictness: Double            // 0.0–1.0 (Referee.strictness)
)

/** Kontekst ligi do Z-Score (IWP). Aplikacja wypełnia z LeagueContext. */
case class LeagueContextInput(
  positionStats: Map[Position, Map[String, PositionAttrStats]]
)
case class PositionAttrStats(mean: Double, stddev: Double)
```

**Uwagi**:
- `Player` wewnątrz `PlayerMatchInput` to ten sam model co w SILNIK §3.1 (physical, technical, mental, traits, bodyParams). Pola `injury`, `freshness`, `morale` w modelu domenowym Player są używane przez aplikację; do silnika przekazujemy **osobno** `freshness` i `morale` w `PlayerMatchInput`, żeby silnik nie zależał od encji bazy (np. `Player` z repo może nie mieć pól runtime).
- Silnik **nie** przyjmuje `MatchId` ani `LeagueId` — są niepotrzebne do symulacji. Identyfikatory zwracane w zdarzeniach to `TeamId` i `PlayerId` z wejścia.

### 1.3 Wyjście (MatchEngineResult)

```scala
case class MatchEngineResult(
  homeGoals: Int,
  awayGoals: Int,
  events: List[MatchEventRecord],  // chronologicznie; §2
  analytics: Option[MatchAnalytics]  // VAEP, WPA, sieci — jeśli silnik je oblicza
)

case class MatchAnalytics(
  vaepByPlayer: Map[PlayerId, Double],
  wpaTimeline: List[(Int, Double)],  // (minuta, cumulative WPA)
  possessionPercent: (Double, Double), // (home, away)
  shotCount: (Int, Int),
  xgTotal: (Double, Double)
)
```

**Zasady**:
- `events` to **kanoniczna** lista zdarzeń do zapisu w `MatchResultLog` i do wyświetlenia w UI (pełny log meczu). Format zdarzenia — §2.
- Silnik **nie** zapisuje do bazy; zwraca tylko wynik. Zapis wyniku, aktualizacja tabeli, injury/freshness/morale to odpowiedzialność warstwy aplikacji.

### 1.4 Sygnatura w kodzie (ZIO)

```scala
trait MatchEngine {
  def simulate(input: MatchEngineInput): ZIO[Any, MatchEngineError, MatchEngineResult]
}

sealed trait MatchEngineError
case class InvalidLineup(msg: String) extends MatchEngineError
case class EngineFault(cause: Throwable) extends MatchEngineError
```

**Wymagania walidacji wejścia (silnik lub aplikacja)**:
- `homeTeam.players.size == 11`, `awayTeam.players.size == 11`.
- `lineup` pokrywa wszystkich 11 playerId (każdy slot formacji ma przypisanego gracza; brak duplikatów).
- `homeAdvantage > 0` (np. clamp 1.0–1.2).
- `referee.strictness` ∈ [0, 1].

Aplikacja przed wywołaniem może walidować skład (np. brak kontuzjowanych); silnik może dodatkowo zwrócić `InvalidLineup`, jeśli np. brakuje bramkarza w lineupie.

### 1.5 Zależność od dokumentacji

- **GamePlan**, **PositionSlot**, **Position**, **Player** (atrybuty) — FORMACJE_ROLE_TAKTYKA.md, ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md, SILNIK §3.
- **Użycie homeAdvantage, leagueContext, referee** w obliczeniach — SILNIK §4 (Dixon-Coles, Matchup Matrix, foul_risk/card_risk).

---

## 2. Kanoniczna lista typów zdarzeń i format logu

### 2.1 Typ zdarzenia (eventType)

Każde zdarzenie w `events` ma pole `eventType: String`. Wartości **kanoniczne** (silnik emituje tylko te):

| eventType       | Opis | actorPlayerId | secondaryPlayerId | typowe metadata |
|-----------------|------|---------------|-------------------|------------------|
| KickOff         | Rozpoczęcie meczu / wznowienie | posiadający | — | — |
| Pass            | Podanie krótkie/średnie | podający | odbiorca | distance?, xPass? |
| LongPass        | Podanie długie | podający | odbiorca | distance?, xPass? |
| Cross           | Dośrodkowanie | podający | odbiorca / — | xPass? |
| PassIntercepted | Przechwyt | przechwytujący | podający | — |
| Dribble         | Drybling (sukces) | dryblujący | obrońca (opcjonalnie) | zone? |
| DribbleLost     | Utrata po dryblingu | atakujący | odbierający | — |
| Shot            | Strzał (nie bramka) | strzelający | — | xG?, outcome (Saved/Missed/Blocked) |
| Goal            | Bramka | strzelający | asystent? | xG? |
| Foul            | Fauł | faulujący | sfaulowany | zone? |
| YellowCard      | Żółta kartka | zawodnik | — | — |
| RedCard         | Czerwona kartka | zawodnik | — | — |
| Injury          | Kontuzja w meczu | kontuzjowany | — | returnMatchday?, severity? |
| Substitution    | Zmiana | wchodzący | schodzący | minute? |
| Corner          | Różny (rozpoczęcie) | wykonujący | — | — |
| ThrowIn         | Wrzut (rozpoczęcie) | wykonujący | — | — |
| FreeKick        | Rzut wolny (rozpoczęcie) | wykonujący | — | — |
| Offside         | Spalony | zawodnik | — | — |

W implementacji można użyć `enum MatchEventType` w Scala; w logu (JSON/DB) — stringi powyżej.

### 2.2 Format pojedynczego zdarzenia (MatchEventRecord)

Spójny z MODELE_I_PRZEPLYWY §12; jeden typ do zapisu i do API.

```scala
case class MatchEventRecord(
  minute: Int,                  // 0–90+
  eventType: String,            // wartości z tabeli wyżej
  actorPlayerId: Option[PlayerId],
  secondaryPlayerId: Option[PlayerId],
  teamId: Option[TeamId],      // zespół „sprawczy” (np. przy Golu = zespół strzelca)
  zone: Option[Int],            // 1–96 (strefa boiska)
  outcome: Option[String],      // np. Success, Miss, Saved, Blocked
  metadata: Map[String, String] // xG, distance, returnMatchday, itd.
)
```

**Przykłady**:
- Gol: `eventType = "Goal"`, `actorPlayerId = strzelca`, `teamId = zespół strzelca`, `metadata = ("xG" -> "0.42")`.
- Kontuzja: `eventType = "Injury"`, `actorPlayerId = kontuzjowany`, `metadata = ("returnMatchday" -> "3", "severity" -> "Medium")`.

Silnik w trakcie symulacji buduje zdarzenia wewnętrzne (np. `MatchEvent(minute, action, outcome, zone)`); warstwa eksportu (w silniku lub w aplikacji) mapuje je na `MatchEventRecord` z powyższą listą typów.

### 2.3 MatchSummary — pełne statystyki meczu

**MatchSummary** to zestaw wszystkich statystyk meczu (jak w raportach TV/portalach). Wypełniany po zakończeniu symulacji — z listy `events` i opcjonalnie z `MatchAnalytics` zwróconego przez silnik. Zapis w `MatchResultLog.summary: Option[MatchSummary]`. Wartości zawsze w kolejności (home, away).

```scala
/** Pełne statystyki meczu — do wyświetlania i analizy. Wszystkie pary (home, away). */
case class MatchSummary(
  // === Posiadanie i wynik ===
  possessionPercent: (Double, Double),        // % posiadania (home, away); suma = 100
  homeGoals: Int,
  awayGoals: Int,

  // === Strzały ===
  shotsTotal: (Int, Int),                    // łącznie (w tym na bramkę + obok + zablokowane)
  shotsOnTarget: (Int, Int),
  shotsOffTarget: (Int, Int),
  shotsBlocked: (Int, Int),
  bigChances: (Int, Int),                    // opcjonalnie: „wielkie szanse”
  xgTotal: (Double, Double),                 // Expected Goals łącznie

  // === Prowadzenie piłki / podania ===
  passesTotal: (Int, Int),
  passesCompleted: (Int, Int),               // udane podania
  passAccuracyPercent: (Double, Double),     // passesCompleted / passesTotal * 100
  passesInFinalThird: (Int, Int),
  crossesTotal: (Int, Int),
  crossesSuccessful: (Int, Int),
  longBallsTotal: (Int, Int),
  longBallsSuccessful: (Int, Int),

  // === Obrona i odbiór ===
  tacklesTotal: (Int, Int),
  tacklesWon: (Int, Int),
  interceptions: (Int, Int),
  clearances: (Int, Int),
  blocks: (Int, Int),                        // zablokowane strzały
  saves: (Int, Int),                         // interwencje bramkarza (home, away)
  goalsConceded: (Int, Int),                 // = (awayGoals, homeGoals); dla spójności

  // === Faule i karny ===
  fouls: (Int, Int),
  yellowCards: (Int, Int),
  redCards: (Int, Int),
  foulsSuffered: (Int, Int),                 // ile razy sfaulowano (dla kontekstu)

  // === Stałe fragmenty ===
  corners: (Int, Int),
  cornersWon: (Int, Int),                    // alias do corners (dla czytelności API)
  throwIns: (Int, Int),
  freeKicksWon: (Int, Int),
  offsides: (Int, Int),

  // === Opcjonalne / zaawansowane ===
  duelsWon: Option[(Int, Int)],              // pojedynki ogółem wygrane
  aerialDuelsWon: Option[(Int, Int)],
  possessionLost: Option[(Int, Int)],       // utrata piłki (np. złe podanie, drybling)
  vaepTotal: Option[(Double, Double)],      // suma VAEP drużyny (jeśli silnik zwraca)
  wpaFinal: Option[Double]                   // WPA na koniec meczu (np. 1.0 dla wygranej)
)
```

**Źródło danych**: Aplikacja po zakończeniu symulacji agreguje `events` (np. liczba zdarzeń Pass → passesTotal; Shot/Goal → shots, xG z metadata; Foul → fouls; YellowCard/RedCard → kartki; Corner, ThrowIn, Offside itd.) i uzupełnia `MatchSummary`. Pola opcjonalne (`duelsWon`, `vaepTotal`, …) — jeśli silnik ich nie zwraca, pozostawiamy `None`. Składanie do `MatchResultLog.summary = Some(summary)`.

**Spójność z MatchAnalytics**: Silnik może zwracać `MatchAnalytics` (possessionPercent, shotCount, xgTotal); te wartości wchodzą w `MatchSummary`. Reszta (passes, tackles, fouls, corners, …) pochodzi z agregacji `events` po typach zdarzeń.

---

## 3. MatchSquad — encja i reguły

### 3.1 Definicja

**MatchSquad** to złożony zestaw danych: który mecz, która drużyna, jakie 11 zawodników, na jakich slotach, i jaki GamePlan. Jedna encja per (matchId, teamId).

```scala
case class MatchSquad(
  id: MatchSquadId,
  matchId: MatchId,
  teamId: TeamId,
  lineup: List[LineupSlot],     // 11 elementów: (playerId, positionSlot)
  gamePlan: GamePlan,           // pełna taktyka (lub odwołanie do snapshot — patrz niżej)
  submittedAt: Instant,
  source: MatchSquadSource      // Manual | Default | Bot
)

case class LineupSlot(playerId: PlayerId, positionSlot: PositionSlot)

enum MatchSquadSource:
  case Manual   // gracz ustawił przed deadline
  case Default  // system wziął ostatni zapisany / domyślny (brak ustawienia)
  case Bot      // bot v0 (lub przyszły AI)
```

**Alternatywa**: Zamiast przechowywać pełny `GamePlan` w `MatchSquad`, można trzymać `gamePlanSnapshotId: Option[GamePlanSnapshotId]` i przy „Rozegraj kolejkę” ładować snapshot; jeśli brak — domyślny 4-3-3. Dla prostoty pierwszej wersji: `gamePlan: GamePlan` (serializowany JSON w kolumnie lub osobna tabela) pozwala nie ciągnąć snapshotów przy każdym meczu.

### 3.2 Reguły biznesowe

- Dla meczu **Scheduled** aplikacja pozwala zapisać/aktualizować MatchSquad dla każdej z dwóch drużyn do **deadline’u** (np. do 17:00 w dniu meczu lub do momentu „Rozegraj kolejkę”).
- Zawodnicy w `lineup` muszą należeć do `teamId` i nie być kontuzjowani (injury.returnAtMatchday > currentMatchday → niedostępni). Walidacja po stronie aplikacji.
- Dokładnie **jeden** bramkarz (slot GK) i **10** polowych; sloty zgodne z jedną formacją (np. 4-3-3: LCB, RCB, LB, RB, CDM, LCM, RCM, LW, RW, ST). Lista slotów zależy od wybranego GamePlan.formation.positions (10 slotów).
- Jeśli do deadline’u **nie ma** MatchSquad dla drużyny: aplikacja przy „Rozegraj kolejkę” tworzy MatchSquad z `source = Default`, lineup = domyślny (np. pierwsze 11 z kadry po overall, z jednym GK), gamePlan = domyślny (np. preset 4-3-3). Zapisuje go i używa w wywołaniu silnika.

### 3.3 Relacja do innych encji

- Match 1 — 2 MatchSquad (jeden dla home, jeden dla away).
- MatchSquad N — 1 Match, N — 1 Team. Lineup odnosi się do PlayerId; Player należy do Team (przed meczem).

---

## 4. Kolejność w tabeli (tie-break)

Tabela ligowa sortowana jest według **kolejności kryteriów** (zgodnie z praktyką ligową). Wszystkie kryteria malejąco (więcej = lepiej), oprócz „stracone”, gdzie mniej = lepiej.

1. **Punkty** (points) — malejąco.
2. **Różnica bramek** (goalDifference = goalsFor - goalsAgainst) — malejąco.
3. **Bramki zdobyte** (goalsFor) — malejąco.
4. **Mecz bezpośredni (H2H)** — stosowany **tylko gdy dokładnie dwie drużyny mają tę samą liczbę punktów**. Wtedy: punkty zdobyte w dwóch meczach między tymi dwoma drużynami — malejąco. (W sezonie są dwa mecze: u siebie i na wyjeździe; sumujemy punkty z tych dwóch.)
5. **Przy remisie 3+ drużyn** — H2H nie stosujemy; sortowanie wyłącznie: punkty → różnica bramek → bramki zdobyte. Przy pełnym remisie wszystkich kryteriów: **losowanie** (stabilny sort po ID lub generator z seedem np. leagueId).

**Implementacja**: Dla każdej pary drużyn ex aequo (po punktach, różnicy, bramkach) sprawdzić, czy jest ich dokładnie 2 — jeśli tak, dodać do klucza sortowania `headToHeadPoints(opponent)`; jeśli 3 lub więcej, nie używać H2H. Na końcu przy pełnym remisie: `sortBy(..., team.id)` lub `Random.shuffle` z seedem.

Wymaganie produktowe (WYMAGANIA_GRY): „tabela klasyczna” — powyższa kolejność to doprecyzowanie.

---

## 5. Specyfikacja API (REST)

API obsługuje frontend (web); autentykacja JWT w nagłówku `Authorization: Bearer <token>` dla endpointów chronionych. Base URL np. `/api/v1`. Identyfikatory w ścieżce (UUID lub slug).

**Dostęp publiczny (bez nagłówka Authorization):** GET `/leagues/:id`, GET `/leagues/:id/table`, GET `/leagues/:id/teams`, GET `/leagues/:id/fixtures` — odczyt danych ligi, tabeli, drużyn i terminarza jest publiczny. Pozostałe endpointy (tworzenie ligi, zaproszenia, rozegranie kolejki, transfery, składy itd.) wymagają tokena; brak lub nieprawidłowy token → **401 Unauthorized**.

### 5.1 Autentykacja i użytkownik

| Metoda | Ścieżka | Opis | Body / Response |
|--------|---------|------|------------------|
| POST   | /auth/register | Rejestracja | Body: `{ "email", "password", "displayName" }` → 201 + User (bez hasła) |
| POST   | /auth/login    | Logowanie   | Body: `{ "email", "password" }` → 200 + `{ "token", "user" }` |
| GET    | /auth/me       | Bieżący użytkownik | Header Authorization → 200 + User |

### 5.2 Ligi

| Metoda | Ścieżka | Opis | Body / Response |
|--------|---------|------|------------------|
| POST   | /leagues | Tworzenie ligi | Body: `{ "name", "teamCount", "myTeamName", "timezone?" }` → 201 + League (createdByUserId = bieżący user; startDate = null do momentu „Start sezonu”) + Team (właściciela) |
| GET    | /leagues/:id | Szczegóły ligi | → League, lista Team (bez pełnych kadr), currentMatchday, table? |
| GET    | /leagues/:id/table | Tabela | → Lista pozycji (teamId, name, points, played, won, drawn, lost, goalsFor, goalsAgainst, goalDifference) posortowana według §4 |
| GET    | /leagues/:id/fixtures | Terminarz | → Lista Match (wszystkie lub filtrowane po matchday) |
| POST   | /leagues/:id/invite | Zaproszenie | Body: `{ "email" }` lub generacja linku → 201 + Invitation (token w linku) |
| POST   | /invitations/accept | Akceptacja zaproszenia | Body: `{ "token": "...", "teamName": "Nazwa mojego zespołu" }`. Wymaga zalogowanego użytkownika → tworzy Team, dopisuje do ligi, Invitation.status = Accepted → 200 + League + Team |
| POST   | /leagues/:id/add-bots | Uzupełnienie botami | Body: `{ "count" }` → 201, tworzy Bot teams |
| POST   | /leagues/:id/start   | Start sezonu (generator + terminarz) | Body: `{ "startDate"?: "YYYY-MM-DD" }` (opcjonalnie: pierwsza środa/sobota; brak = najbliższa). Wymaga założyciela; sloty wypełnione → generator, sędziowie, TransferWindow, LeagueContext, terminarz, League.startDate, seasonPhase = InProgress |

### 5.3 Drużyny i zawodnicy

| Metoda | Ścieżka | Opis | Body / Response |
|--------|---------|------|------------------|
| GET    | /teams/:id | Drużyna (nazwa, owner, budget, playerIds) | → Team + lista Player (atrybuty, injury, freshness, morale) |
| GET    | /teams/:id/players | Kadra | → Lista Player |
| PATCH  | /players/:id | Edycja imienia/nazwiska | Body: `{ "firstName", "lastName" }` → 200 + Player |
| GET    | /teams/:id/game-plans | Zapisane taktyki | → Lista GamePlanSnapshot |
| POST   | /teams/:id/game-plans | Zapis taktyki | Body: GamePlan + name → 201 + GamePlanSnapshot |

### 5.4 Mecze i skład

| Metoda | Ścieżka | Opis | Body / Response |
|--------|---------|------|------------------|
| GET    | /matches/:id | Mecz (szczegóły, wynik jeśli Played) | → Match, optional MatchSquad (oba zespoły) |
| GET    | /matches/:id/log | Pełny log meczu | → MatchResultLog (events + summary?) |
| GET    | /matches/:id/squads | Składy na mecz | → MatchSquad home, MatchSquad away |
| PUT    | /matches/:id/squads/:teamId | Zapis składu i taktyki | Body: `{ "lineup": [{ "playerId", "positionSlot" }], "gamePlan": GamePlan }` → 200 + MatchSquad |

### 5.5 Rozegranie kolejki

| Metoda | Ścieżka | Opis | Body / Response |
|--------|---------|------|------------------|
| POST   | /leagues/:id/matchdays/current/play | Rozegraj bieżącą kolejkę | Wymaga uprawnień (np. założyciel); dla każdego meczu w kolejce: ładuje MatchSquad (lub Default), wywołuje silnik, zapisuje wynik i log, aktualizuje injury/freshness/morale, tabelę; zwiększa currentMatchday → 200 + lista Match (z wynikami) |

### 5.6 Transfery

| Metoda | Ścieżka | Opis | Body / Response |
|--------|---------|------|------------------|
| GET    | /leagues/:id/transfer-windows | Okna transferowe | → Lista TransferWindow (status, openAfterMatchday, closeBeforeMatchday) |
| GET    | /transfer-offers | Oferty transferowe w lidze | Query: **leagueId** (wymagany; brak → 400 Bad Request), teamId (opcjonalny filtr) → Lista TransferOffer. Wymaga tokena. |
| POST   | /leagues/:id/transfer-offers | Składanie oferty w ramach ligi | Body: `{ "windowId", "toTeamId", "playerId", "amount" }` → 201 + TransferOffer |
| POST   | /transfer-offers/:id/accept | Akceptacja oferty | → 200, wykonanie transferu (zmiana teamId, budżety, limity) |
| POST   | /transfer-offers/:id/reject | Odrzucenie | → 200 |

### 5.7 Konwencje

- **Identyfikatory**: UUID w ścieżce i w ciele (np. `leagueId`, `matchId`).
- **Błędy**: 4xx/5xx, body np. `{ "code": "INVALID_LINEUP", "message": "..." }`.
- **Paginacja**: Gdy listy długie (np. events w logu): `?limit=100&offset=0` lub cursor; dla logu meczu zwykle zwracamy całość.
- **Wersjonowanie**: Prefix `/api/v1`; w przyszłości v2 bez łamania v1.

---

## 6. Ekrany i przepływy UI

Minimalna lista ekranów pozwalająca zrealizować wszystkie wymagania z WYMAGANIA_GRY i MODELE_I_PRZEPLYWY (user flows). Bez wireframe’ów — tylko nazwa, cel i główne akcje. Stack technologiczny frontendu i spójność z backendem → **§13**.

### 6.1 Ekrany globalne

- **Logowanie** — email + hasło, przycisk „Zaloguj”, link „Zarejestruj się”.
- **Rejestracja** — email, hasło, displayName, submit → przekierowanie do dashboard.
- **Dashboard** — lista „Moje ligi” (gdzie użytkownik ma drużynę), lista „Zaproszenia” (Pending), przycisk „Utwórz ligę”.

### 6.2 Tworzenie i konfiguracja ligi

- **Utwórz ligę** — formularz: nazwa ligi, liczba drużyn (10–20), nazwa swojego zespołu, timezone (opcjonalnie). Submit → liga w Setup, użytkownik widzi ekran ligi.
- **Widok ligi (Setup)** — tabela slotów drużyn (wypełnione: nazwa + owner; puste: „Zaproszenie” / „Dodaj bota”). Akcje: „Zaprosić” (email lub kopiuj link), „Dodaj boty” (uzupełnij puste sloty), „Start sezonu” (aktywny gdy wszystkie sloty pełne).

### 6.3 Akceptacja zaproszenia

- **Link zaproszenia** (np. /invite?token=…) — jeśli niezalogowany: przekierowanie do logowania, potem powrót do akceptacji. Jeśli zalogowany: formularz „Dołącz do ligi X” z polem **Nazwa zespołu** (teamName) i przyciskiem „Dołącz”. Front wywołuje **POST** /invitations/accept z body `{ "token", "teamName" }` → Team tworzony z name = teamName, redirect do widoku ligi.

### 6.4 Sezon w toku

- **Widok ligi (InProgress)** — tabela (pozycja, drużyna, punkty, mecze, W/R/P, bramki, różnica), terminarz (kolejki, kto z kim, wyniki). Akcja: „Rozegraj kolejkę” (dla założyciela / uprawnionego).
- **Kadra** — lista zawodników (imię, nazwisko, pozycja, atrybuty skrótowo, injury, freshness, morale). Akcje: edycja imienia/nazwiska, (później) domyślna taktyka. Link do „Zapisane taktyki”.
- **Zapisane taktyki** — lista GamePlanSnapshot (nazwa, data), podgląd/edycja, „Użyj w meczu” (przekierowanie do ustawień meczu).
- **Przed meczem (ustawienia meczu)** — wybór 11 z kadry (kontuzjowani niedostępni), przeciągnij-i-upuść na sloty formacji (lub lista slotów + dropdown gracz), wybór taktyki (zapisana lub edycja), zapis. Deadline do 17:00 / do rozegrania kolejki.
- **Po meczu / Szczegóły meczu** — wynik, minuty, link „Pełny log meczu”. Lista zdarzeń (chronologicznie), opcjonalnie podsumowanie (posiadanie, strzały, xG). Możliwość filtrowania po typie zdarzenia (z listy §2).

### 6.5 Transfery

- **Okno transferowe** — informacja „Otwarte” / „Zamknięte”. Gdy otwarte: lista zawodników z innych drużyn (liga), wyszukiwanie/filtry, przycisk „Złóż ofertę” (kwota), „Moje oferty” (wychodzące: status; przychodzące: akceptuj/odrzuć).

### 6.6 Mapowanie na user flows

- Tworzenie ligi (MODELE §13.1) → ekrany: Utwórz ligę, Widok ligi (Setup), Zaproszenie, Akceptacja, Start sezonu.
- Przed meczem (§13.2) → Kadra, Zapisane taktyki, Przed meczem (ustawienia meczu).
- Rozegranie (§13.3) → Widok ligi (przycisk „Rozegraj kolejkę”).
- Po meczu (§13.4) → Widok ligi (wyniki), Szczegóły meczu, Log meczu.
- Transfery (§13.5) → Okno transferowe, Oferty.

---

## 7. Schemat bazy danych

PostgreSQL (lub inny SQL) — tabele zgodne z encjami z MODELE_I_PRZEPLYWY. Kolumny w notacji snake_case; klucze obce z przyrostkiem `_id`.

### 7.1 Tabele

- **users** — id (PK, UUID), email (UNIQUE), password_hash, display_name, created_at.
- **leagues** — id (PK), name, team_count, current_matchday, total_matchdays, season_phase (enum: setup, in_progress, finished), home_advantage, timezone, start_date (date, nullable: ustawiane przy Start sezonu — pierwsza środa/sobota), created_by_user_id (FK: założyciel), created_at.
- **teams** — id (PK), league_id (FK), name, owner_type (enum: human, bot), owner_user_id (FK, nullable), owner_bot_id (nullable), budget, default_game_plan_id (FK, nullable), created_at.
- **players** — id (PK), team_id (FK), first_name, last_name, preferred_positions (JSONB lub array), physical (JSONB), technical (JSONB), mental (JSONB), traits (JSONB), body_params (JSONB), injury (JSONB, nullable), freshness, morale, created_at. (Atrybuty jako JSON dla elastyczności; alternatywa: kolumny per atrybut.)
- **invitations** — id (PK), league_id (FK), invited_user_id (FK), invited_by_user_id (FK), token (UNIQUE), status (enum), created_at, expires_at.
- **referees** — id (PK), league_id (FK), name, strictness. Pula sędziów per liga (§9.6).
- **matches** — id (PK), league_id (FK), matchday, home_team_id (FK), away_team_id (FK), scheduled_at, status (enum), home_goals (nullable), away_goals (nullable), referee_id (FK), result_log_id (FK, nullable).
- **match_squads** — id (PK), match_id (FK), team_id (FK), lineup (JSONB: list of { player_id, position_slot }), game_plan (JSONB), submitted_at, source (enum). UNIQUE (match_id, team_id).
- **match_result_logs** — id (PK), match_id (FK, UNIQUE), events (JSONB: array of MatchEventRecord), summary (JSONB, nullable), created_at.
- **league_contexts** — id (PK), league_id (FK, UNIQUE), position_stats (JSONB: Map[Position, Map[String, {mean, stddev}]]), created_at.
- **game_plan_snapshots** — id (PK), team_id (FK), name, game_plan (JSONB), created_at.
- **transfer_windows** — id (PK), league_id (FK), open_after_matchday, close_before_matchday, status (enum).
- **transfer_offers** — id (PK), window_id (FK), from_team_id (FK), to_team_id (FK), player_id (FK), amount, status (enum), created_at, responded_at (nullable).
- **bots** — id (PK), league_id (FK), team_id (FK), np. dla przyszłego rozszerzenia (Bot v0 może nie mieć wiersza; owner_bot_id w teams jako UUID bez FK).

### 7.2 Indeksy (propozycja)

- leagues: (season_phase), (id).
- teams: (league_id), (owner_user_id).
- players: (team_id).
- matches: (league_id, matchday), (home_team_id), (away_team_id).
- match_squads: (match_id), (team_id).
- referees: (league_id).
- transfer_offers: (window_id), (to_team_id), (from_team_id).

### 7.3 Tabela ligi (materialized lub view)

Tabela „pozycje” może być **widokiem** lub materialized view: dla każdego team_id w lidze: suma points, goalsFor, goalsAgainst, goalDifference, played, won, drawn, lost (obliczane z matches). Sortowanie według §4 w zapytaniu (ORDER BY points DESC, goal_difference DESC, goals_for DESC, …).

---

## 8. Autentykacja i flow zaproszeń

### 8.1 Autentykacja

- **Rejestracja**: POST /auth/register — email (unikalny), hasło (hash bcrypt/argon2), displayName. Tworzenie User, zwrot 201 + User (bez hasła).
- **Logowanie**: POST /auth/login — email + hasło. Weryfikacja hasła, generacja JWT (payload: userId, email, exp). Zwrot 200 + `{ token, user }`.
- **Chronione endpointy**: Nagłówek `Authorization: Bearer <jwt>`. Middleware weryfikuje JWT, wyciąga userId, przekazuje do warstwy aplikacji. Brak/nieprawidłowy token → 401.

### 8.2 Flow zaproszenia

1. Założyciel ligi: „Zaprosić” → podaje email lub generuje link. System tworzy Invitation (token = UUID), status Pending. Link: `https://<front>/invite?token=<token>`.
2. Zaproszony otwiera link. Jeśli **niezalogowany** → przekierowanie na /login (returnUrl = /invite?token=…). Po logowaniu (lub rejestracji) powrót do /invite?token=….
3. Aplikacja: **POST** /invitations/accept z body `{ "token": "<token>", "teamName": "Nazwa zespołu" }` (użytkownik zalogowany). Backend: weryfikacja tokena, wygaśnięcia, czy liga nadal w Setup i ma wolny slot. Tworzy Team (owner = invitedUserId, name = teamName z body), dopisuje team do ligi, ustawia Invitation.status = Accepted. Zwraca 200 + League + Team. Front przekierowuje do widoku ligi.
4. Jeśli token nieważny / wygasły / liga pełna / brak teamName → 400 z komunikatem.

---

## 9. Edge cases i reguły biznesowe

### 9.1 Liga i start sezonu

- **Niepełna obsada**: „Start sezonu” dostępny tylko gdy liczba drużyn = teamCount (wszystkie sloty wypełnione). W przeciwnym razie przycisk nieaktywny lub 400 z komunikatem.
- **Boty**: „Dodaj boty” uzupełnia puste sloty drużynami Bot (owner_bot_id = nowy UUID, name = np. „Bot 1”, …). Nie wymaga zaproszeń dla tych slotów.

### 9.2 Skład i mecz

- **Brak MatchSquad do deadline’u**: Aplikacja przy „Rozegraj kolejkę” dla każdego meczu sprawdza MatchSquad dla obu drużyn. Brak → tworzy Default (pierwsze 11 z kadry: 1 GK, 10 polowych według preferredPositions/overall), gamePlan = domyślny 4-3-3, source = Default, zapisuje do bazy. Następnie wywołuje silnik.
- **Kontuzjowany w lineupie**: Walidacja przy zapisie MatchSquad (PUT): jeśli playerId ma injury i returnAtMatchday > matchday tego meczu → 400. Przy Default lineup nie wybieramy kontuzjowanych.

### 9.3 Transfery

- **Sprzedaż prowadząca do < 16 zawodników**: Przy akceptacji oferty sprawdzenie: toTeamId po sprzedaży P ma (playerCount - 1) ≥ 16. Jeśli nie → 400 „Sprzedaż nie dozwolona: minimalna liczba zawodników to 16”.
- **Kupno prowadzące do > 20**: fromTeamId po zakupie ma (playerCount + 1) ≤ 20. Jeśli nie → 400.
- **Budżet**: fromTeamId.budget ≥ amount. W przeciwnym razie 400.
- **Oferta na tego samego zawodnika wielokrotnie**: Decyzja: jedna aktywna oferta (Pending) per (playerId, windowId) od tego samego fromTeamId — przy składaniu nowej anulować poprzednią lub zwracać 409.

### 9.4 Rozegranie kolejki

- **Kolejka już rozegrana**: currentMatchday wskazuje na ostatnią rozegraną. „Rozegraj kolejkę” = rozegranie meczów dla currentMatchday + 1. Jeśli currentMatchday + 1 > totalMatchdays → 400 „Sezon zakończony” (opcjonalnie ustaw seasonPhase = Finished).
- **Duplikat rozegrania**: Idempotencja: przed symulacją sprawdzić status każdego Match w kolejce. Jeśli wszystkie Played → nic nie robić, 200 + lista meczów. Jeśli część Scheduled — rozgrywamy tylko Scheduled (teoretycznie wszystkie w jednej kolejce są Scheduled przy pierwszym uruchomieniu).

### 9.5 Kontuzje i freshness/morale

- **Aktualizacja po meczu**: Dla każdego gracza z lineupu: minutesPlayed (90 lub mniej przy zmianie/ kontuzji). Freshness: zastosować wzór z MODELE §8. Morale: zastosować reguły z MODELE §7 (wynik meczu, seria). Injury: jeśli w events jest zdarzenie Injury, ustawić Player.injury = InjuryStatus(sinceMatchday, returnMatchday, severity).
- **Kadra < 16 po kontuzjach**: Nie blokujemy (kontuzje są do powrotu). Gracz musi mieć w kadrze ≥ 16 dostępnych do gry w dłuższej perspektywie; jeśli chwilowo wielu kontuzjowanych, skład Default i tak wybiera tylko dostępnych. (Opcjonalnie: ostrzeżenie w UI.)

### 9.6 Sędziowie — tworzenie i przypisanie

- **Liczba sędziów**: Przy „Start sezonu” tworzone jest **teamCount / 2** sędziów dla ligi (dzielenie całkowite). Np. 10 drużyn → 5 sędziów, 12 drużyn → 6 sędziów. Każda kolejka ma **teamCount / 2** meczów, więc każdy sędzia prowadzi **dokładnie jeden mecz** w danej kolejce.
- **Przypisanie**: Dla każdej kolejki mecze są **losowo** przypisywane do sędziów (każdy mecz — jeden sędzia, bez powtórzeń w ramach kolejki). Np. lista meczów kolejki K, lista sędziów ligi → shuffle lub random permutation → match.refereeId = referees(permutation(i)).
- **Strictness**: Każdy sędzia dostaje losowy `strictness` z zakresu np. 0.3–0.8 (albo stały 0.5) przy tworzeniu. Szczegóły w MODELE §5.

### 9.7 Automatyczna symulacja o 17:00

- Mecze rozgrywane są **w środy i soboty o 17:00** w timezone ligi (League.timezone). **Symulacja uruchamiana jest automatycznie** o 17:00: job/scheduler (cron lub ZIO Schedule) sprawdza, czy w dany dzień (środa lub sobota) przypada kolejka dla którejś ligi; jeśli tak, dla każdej takiej ligi wywołuje „Rozegraj kolejkę” (use case) dla kolejki zaplanowanej na ten dzień.
- **Mapowanie dzień → kolejka**: Dla lig z seasonPhase = InProgress i startDate ustawionym: kolejka 1 = startDate, kolejka 2 = następna sobota/środa, itd. Scheduler ustala, która kolejka wypada w dany dzień (środa/sobota); jeśli currentMatchday < ta kolejka i mecze tej kolejki są Scheduled, wykonuje rozegranie.
- **Manual**: Założyciel ligi (createdByUserId) może nadal wywołać „Rozegraj kolejkę” ręcznie (POST …/matchdays/current/play) — np. do testów lub gdy automat nie zdążył.

### 9.8 Właściciel ligi (createdByUserId)

- **League.createdByUserId** (created_by_user_id w bazie): użytkownik, który utworzył ligę. Tylko on może: wywołać „Start sezonu”, „Dodaj boty”, ręcznie „Rozegraj kolejkę”. Zaproszenia wysyła założyciel (lub w rozszerzeniu — każdy właściciel drużyny; w v1 tylko założyciel).

---

## 10. Strategia testów

### 10.1 Silnik (MatchEngine)

- **Determinizm**: Dla tego samego `MatchEngineInput` i `randomSeed = Some(n)` wynik (homeGoals, awayGoals, liczba i typy zdarzeń) jest identyczny przy każdym uruchomieniu. Test: 2 wywołania z tym samym seed → assert result1 == result2.
- **Zakres wyników**: Property-based (ScalaCheck): dla losowych wejść (sensowne zakresy atrybutów) suma goli w pojedynczym meczu zwykle w [0, 10]; średnia z N meczów (N=1000) bliska oczekiwanej (np. ~2.5–2.7) przy domyślnych parametrach Dixon-Coles.
- **Brak wyjątków**: Dla dowolnego poprawnego wejścia (11 graczy, poprawne lineup, homeAdvantage ∈ [1, 1.2], strictness ∈ [0, 1]) symulacja kończy się bez wyjątku. InvalidLineup tylko przy złym lineupie (np. brak GK).
- **Kontuzje w logu**: Test z wymuszonym zdarzeniem (np. mock lub seed znany z kontuzją) — w events występuje zdarzenie Injury z oczekiwanymi polami.

### 10.2 Generator zawodników

- **Balans**: Dla L drużyn po wygenerowaniu: suma overall (lub wybrana metryka) w każdej drużynie = T (z tolerancją). Test jednostkowy generatora.
- **Wielkość kadry**: Każda drużyna ma dokładnie 18 zawodników (przed transferami), 1 bramkarz, 17 polowych (przy założeniu jednego GK na drużynę).

### 10.3 Integracja (jedna kolejka)

- **Scenariusz**: Liga z 2 drużynami, 1 kolejka (1 mecz). Zapisz MatchSquad dla obu, wywołaj „Rozegraj kolejkę”. Asercje: Match.status = Played, homeGoals/awayGoals ustawione, MatchResultLog istnieje i events.size > 0, tabela zaktualizowana (points, goalsFor, goalsAgainst), Player.freshness/morale zaktualizowane.
- **Scenariusz bez MatchSquad**: Ten sam setup, brak zapisanych składów. Po rozegraniu: MatchSquad Default zapisane, mecz rozegrany poprawnie.

### 10.4 API (integracja HTTP)

- **Smoke**: POST /auth/register, POST /auth/login, GET /auth/me z tokenem. POST /leagues (tworzenie), GET /leagues/:id. Wymaga działającego backendu i bazy (testcontainers lub test DB).
- **Autoryzacja**: Endpoint chroniony bez tokena → 401. Z tokenem innego użytkownika (np. zmiana cudzej kadry) → 403, jeśli takie reguły są.

### 10.5 Priorytety

- Najpierw: determinizm silnika i walidacja wejścia/wyjścia (kontrakt).
- Następnie: generator (balans, 18 na zespół).
- Potem: use case „Rozegraj kolejkę” (jedna kolejka, zapis wyniku i logu).
- Na końcu: API (rejestracja, liga, mecz, log) i edge case’y transferów.

---

## 11. Mapowanie dokumentów i warstw ZIO

### 11.1 Który dokument odpowiada za co

| Obszar | Dokument | Uwaga |
|--------|----------|--------|
| Atrybuty, traits, skala 1–20 | ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md | Źródło prawdy dla Player |
| Formacje, role, GamePlan, Zone, Position | FORMACJE_ROLE_TAKTYKA.md | Źródło prawdy dla taktyki |
| Przepływ silnika, formuły xPass/xG/IWP, Phase, MatchMoment | SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md | Źródło prawdy dla symulacji |
| Kontrakt wejścia/wyjścia silnika, typy zdarzeń | **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §1–2 | Aplikacja i silnik |
| Encje (League, Team, Match, Player, Referee, Injury, Transfer, …), terminarz, generator, morale, freshness, flow | MODELE_I_PRZEPLYWY_APLIKACJI.md | Źródło prawdy dla domeny aplikacji |
| MatchSquad, tie-break tabeli | **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §3–4 | Uzupełnienie MODELE |
| Wymagania produktowe (liga, kadra, transfery, logi, PvP/PvAI) | WYMAGANIA_GRY.md | Product |
| API, UI, schemat bazy, auth, edge cases, testy | **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §5–10 | Implementacja |

### 11.2 Warstwy ZIO (przypomnienie)

- **Domain**: Modele (Player, League, Match, GamePlan, …) + algebry (interfejsy bez ZIO w sygnaturach domenowych).
- **Engine**: `MatchEngine` — implementacja symulacji; zależna tylko od domeny i ZIO (Random, Clock jeśli potrzebne).
- **Core / Use cases**: Serwisy (np. `LeagueService`, `MatchdayService`) — orkiestracja: load z repo, budowa `MatchEngineInput`, wywołanie `MatchEngine.simulate`, zapis wyniku i logu, aktualizacja injury/freshness/morale, tabeli. Zależne od repo, MatchEngine, config.
- **Infrastructure**: Repozytoria (Doobie/Skunk), auth (JWT), ewentualnie cache.
- **API**: Tapir/Http4s — endpointy z §5, wywołanie use case’ów, zwrot DTO.

Wszystkie zależności wstrzykiwane przez **ZLayer**; testy z `TestLayer` i mockami (np. `MatchEngine` stub zwracający stały wynik).

---

## 12. Architektura Scala 3 i ZIO — szczegóły implementacyjne

Sekcja dla architekta: jak przełożyć powyższe kontrakty na konkretną strukturę kodu w Scala 3 z ZIO 2.x — typy, warstwy, błędy, transakcje.

### 12.1 Identyfikatory (type safety)

Zamiast „gołych” UUID w całej aplikacji stosujemy **opaque type aliases** (Scala 3), żeby nie pomylić `LeagueId` z `TeamId` w sygnaturach i zapytaniach.

```scala
object Ids:
  opaque type LeagueId = UUID
  opaque type TeamId = UUID
  opaque type UserId = UUID
  opaque type PlayerId = UUID
  opaque type MatchId = UUID
  opaque type MatchSquadId = UUID
  opaque type RefereeId = UUID
  opaque type InvitationId = UUID
  opaque type GamePlanSnapshotId = UUID
  opaque type MatchResultLogId = UUID

  def leagueId(u: UUID): LeagueId = u
  def teamId(u: UUID): TeamId = u
  // ... oraz extension methods .value: UUID tam gdzie potrzebne (np. Doobie)
```

W zapytaniach SQL i JSON używamy `id.value`. Domena nigdy nie przyjmuje „surowych” UUID w miejscach, gdzie semantyka to np. `PlayerId`.

### 12.2 Warstwy ZIO — zależności i konstrukcja

- **Domain** (moduł `domain`): tylko case classy, enumy, typy. Bez zależności od ZIO ani od bazy. Tutaj żyją `League`, `Team`, `Player`, `GamePlan`, `MatchEngineInput`, `MatchEngineResult`, `MatchEventRecord` itd.
- **Engine** (moduł `engine`): `MatchEngine` z metodą `simulate`. Zależność od `zio.Random` (dla seeda i rozstrzygnięć losowych). Implementacja może używać `ZIO.serviceWithZIO[Random]` lub przyjmować `Random` w konstruktorze i budować `ZLayer[Random, Nothing, MatchEngine]`.
- **Repozytoria** (moduł `repository`): interfejsy w domenie (np. `LeagueRepository`, `TeamRepository`, `PlayerRepository`, `MatchRepository`, `MatchSquadRepository`, `MatchResultLogRepository`), implementacje w `repository.postgres` z Doobie/Skunk. Każda implementacja zależy od `DataSource` (ZIO JDBC) lub `Session` (Skunk). Zwracane typy: `ZIO[Env, RepositoryError, A]` — błędy domenowe (NotFound, Conflict) jako sealed hierarchy.
- **Use cases (aplikacja)** (moduł `core` lub `application`): np. `PlayMatchday`, `CreateLeague`, `SubmitMatchSquad`, `AcceptTransfer`. Każdy use case zależy od repozytoriów + `MatchEngine` + ewentualnie `LeagueContextService`. Zależności wstrzykiwane przez konstruktor; warstwa budowana jako `ZLayer` złożony z repo + engine.
- **API** (moduł `api`): Tapir endpointy definiowane jako wartości; serwer Http4s budowany z `ZHttp.fromZIO` (ZIO HTTP) lub `Http4sZIOClient`/stub. Endpointy wywołują use case’y i mapują błędy na kody HTTP (np. `NotFound` → 404, `InvalidLineup` → 400).
- **Aplikacja główna**: `ZIO.app` łączy wszystkie warstwy: `program.provide(liveLayers, apiLayer)` — gdzie `liveLayers` = DB transactor + repo implementations + engine + use cases; `apiLayer` = HTTP server z endpointami.

Przykład zależności use case’u „Rozegraj kolejkę”:

```scala
// Zależność: potrzebuje MatchRepository, MatchSquadRepository, PlayerRepository, MatchResultLogRepository,
// LeagueRepository, MatchEngine, LeagueContextService (lub bezpośrednio LeagueContext load).
class PlayMatchday(
  matchRepo: MatchRepository,
  matchSquadRepo: MatchSquadRepository,
  playerRepo: PlayerRepository,
  resultLogRepo: MatchResultLogRepository,
  leagueRepo: LeagueRepository,
  engine: MatchEngine,
  leagueContext: LeagueContextService
) {
  def run(leagueId: LeagueId): ZIO[Any, PlayMatchdayError, List[Match]] = ...
}
```

`PlayMatchdayError` = sealed trait (LeagueNotFound | SeasonFinished | Unauthorized | …). Warstwa API mapuje je na 404/400/403.

### 12.3 Błędy (typed errors)

- **Silnik**: `MatchEngineError` = `InvalidLineup(String)` | `EngineFault(Throwable)`. Nie mieszamy z błędami biznesowymi API.
- **Repozytoria**: `RepositoryError` = `NotFound(id)` | `Conflict(msg)` | `DbError(Throwable)`. Konwersja wyjątków JDBC/Skunk do `DbError`.
- **Use case’y**: każdy use case ma swój typ błędów (np. `CreateLeagueError`, `PlayMatchdayError`), żeby API wiedziało, co zwrócić. Mapowanie: `RepositoryError.NotFound` → np. `LeagueNotFound(leagueId)` w `PlayMatchdayError`.

Dzięki temu testy mogą asercjonować konkretny wariant błędu (`assertTrue(result == Left(InvalidLineup(...)))` przy użyciu `ZIO.exit` lub `either`).

### 12.4 Transakcje (jedna kolejka = jedna transakcja)

Operacja „Rozegraj kolejkę” powinna być **atomowa**: odczyt meczów i składów, N symulacji (czyste obliczenia), zapis wyników, logów, aktualizacja tabeli i freshness/morale/injury. Jeśli zapis się nie powiedzie, nie zostawiamy ligi w stanie „część meczów rozegrana”.

Rekomendacja: jeden use case `PlayMatchday` wykonuje całość wewnątrz `ZIO.transaction` (ZIO JDBC) lub jednej sesji Skunk w transakcji. Silnik `simulate` jest **czysty** w sensie „bez I/O” — tylko ZIO z Random; transakcja obejmuje wyłącznie odczyty i zapisy do bazy po wywołaniu `engine.simulate` dla każdego meczu.

### 12.5 Konfiguracja i środowisko

- **Config**: port serwera, URL bazy, JWT secret, home_advantage (domyślny) — ładowane z env lub pliku (np. ZIO Config). Warstwa API i use case’y otrzymują konfigurację przez `ZLayer` (np. `ZLayer.fromZIO(ZIO.config(ServerConfig.descriptor))`).
- **Środowiska**: `dev` / `staging` / `prod` — osobne profile (np. inna baza); nie zmienia to kontraktów między warstwami.

### 12.6 Podsumowanie stosu backendu (przypomnienie)

- **Język**: Scala 3 (opaque types, enums, given/using jeśli potrzebne).
- **Effects**: ZIO 2.x (ZIO[R, E, A], ZLayer).
- **HTTP**: Tapir (definicja endpointów) + ZIO HTTP lub Http4s (backend).
- **Baza**: PostgreSQL; dostęp przez Doobie (ZIO JDBC) lub Skunk (natywny ZIO).
- **JSON**: Circe (kodowanie/dekodowanie DTO i encji do logów).
- **Testy**: ZIO Test; dla silnika — determinizm z `TestRandom` i `setSeed`; dla use case’ów — mockowane repozytoria i `MatchEngine` stub.

Ta sekcja uzupełnia §11: dokumenty określają **co** (kontrakty, modele, API); ta sekcja określa **jak** to ułożyć w kodzie w Scala 3 i ZIO. **Stack frontendowy** → §13.

---

## 13. Ustalenia architektoniczne (stack aplikacji)

Ustalenia dotyczące technologii i wzorców dla backendu i frontendu — spójny stack Scala 3 + ZIO w warstwie logiki, jeden język i współdzielone typy na styku API.

### 13.1 Backend (serwer aplikacji)

| Obszar | Technologia | Uwagi |
|--------|-------------|--------|
| Język | Scala 3 | Opaque types dla ID, enums, given/using |
| Efekty i warstwy | ZIO 2.x | ZIO[R, E, A], ZLayer, transakcje |
| API | Tapir + ZIO HTTP / Http4s | Definicja endpointów Tapir; serwer ZIO HTTP lub Http4s |
| Baza | PostgreSQL | Doobie (ZIO JDBC) lub Skunk |
| JSON | Circe | DTO, encje, logi meczu |
| Auth | JWT | Payload: userId, email, exp; middleware weryfikacja |

Szczegóły warstw, błędów i transakcji → §12.

### 13.2 Frontend — rekomendacja: Scala.js + Laminar + ZIO

**Główna ścieżka:** frontend w **Scala.js** z **Laminar** (komponenty reaktywne) i **ZIO** (efekty, wywołania API). Spójność z backendem: jeden język, możliwość współdzielenia typów przez moduł `shared`.

| Warstwa | Technologia | Uwagi |
|---------|-------------|--------|
| **UI (komponenty)** | **Laminar** (Scala.js) | Reaktywne Var / Signal / EventStream; aktualizacje bezpośrednio do DOM (bez virtual DOM). Komponenty = funkcje od stanu; styl funkcyjny. |
| **Efekty i API** | **ZIO** (Scala.js) | HTTP (np. sttp), stan globalny, nawigacja. Opcjonalnie: zio-laminar-tapir (typowo bezpieczne wywołania Tapir), jeśli kompatybilne z wersjami. |
| **Typy współdzielone** | **Moduł `shared`** (sbt crossProject) | DTO, League, MatchSummary, GamePlan (fragmenty) — jedna definicja kompilowana do JVM i JS. Full-stack type safety na styku API. |
| **Stylowanie** | **Tailwind CSS** | Utility-first; klasy w Laminarze np. `cls := "flex items-center gap-4 p-4 dark:bg-gray-900"`. Dark mode przez zmienne CSS i `data-theme`. |
| **Build i dev** | **Vite** + wtyczka Scala.js | Szybki HMR, serwowanie zbudowanego JS. Sbt: projekt `frontend` (Scala.js) lub multi-project z `jvm` / `js` / `shared`. |

**Dlaczego Laminar:** jeden język z backendem, współdzielone typy (shared), model reaktywny (Var/Signal) zamiast imperatywnego state; brak virtual DOM — przewidywalna wydajność dla tabel, list, logów meczu. ZIO po stronie klienta do fetchów i logiki; Laminar tylko renderuje i emituje zdarzenia.

**Alternatywa (na później):** Lazagna (Scala.js + ZIO-first UI) — głębsza integracja ZIO w warstwie widoku; mniejsza społeczność i mniej materiałów niż Laminar.

### 13.3 Wzorce UI (obowiązujące)

- **Mobile-first i responsywność** — projektowanie od małych ekranów; Tailwind breakpointy; tabele i listy czytelne na mobile (np. karty zamiast szerokich tabel).
- **Dark mode** — domyślnie wspierany: zmienne CSS (`--bg`, `--text`), przełącznik (Laminar Var + atrybut `data-theme` na kontenerze).
- **Progressive disclosure** — zaawansowane opcje (taktyka, log meczu, oferty) za przyciskami „Więcej”, akordeonami lub zakładkami; ograniczenie przeciążenia poznawczego.
- **Dostępność (WCAG)** — semantyczny HTML, role ARIA gdzie potrzeba, sensowne focus i kontrast (Tailwind); testowanie pod kątem czytników ekranu.

### 13.4 Komponenty i narzędzia pomocnicze

- **Storybook** — opcjonalnie; dla Laminara nie ma standardowej integracji. Na start: wewnętrzna strona „showcase” (np. `/dev/components`) z listą komponentów lub pominięcie.
- **Komponenty** — budowa z małych, reużywalnych bloków (przycisk, karta, tabela, formularz, listy); spójna paleta i typografia (Tailwind + ewentualnie design tokens w CSS).

### 13.5 Podsumowanie ustaleń

- **Backend:** Scala 3, ZIO, Tapir, PostgreSQL, Circe, JWT (§12, §13.1).
- **Frontend:** Scala.js, Laminar, ZIO, moduł shared, Tailwind, Vite (§13.2).
- **Wzorce UI:** mobile-first, dark mode, progressive disclosure, dostępność (§13.3).

Ekrany i przepływy opisane w §6 są realizowane w tym stacku; API z §5 jest konsumowane przez klienta ZIO/sttp z typami ze współdzielonego modułu.

---

*Kontrakty i architektura v1.0 — Luty 2026*  
*Spójne z: SILNIK, MODELE_I_PRZEPLYWY_APLIKACJI, WYMAGANIA_GRY, FORMACJE_ROLE_TAKTYKA*
