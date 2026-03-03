# Modele danych, terminarz, formuły i przepływy aplikacji

**Cel**: Jedno miejsce z definicjami wszystkich modeli danych, reguł biznesowych, formuł i przepływów potrzebnych do implementacji gry.  
**Spójność z**: WYMAGANIA_GRY.md, SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md, FORMACJE_ROLE_TAKTYKA.md, ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md.  
**Uzupełnienie**: Kontrakt silnika, API, schemat bazy, auth, UI, edge case’y, strategia testów → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md**.

---

## Spis treści

1. [Modele danych — encje i relacje](#1-modele-danych--encje-i-relacje)
2. [Terminarz ligi (dwie rundy, u siebie/wyjazd)](#2-terminarz-ligi-dwie-rundy-u-siebiewyjazd)
3. [Bonus za grę u siebie (home advantage)](#3-bonus-za-grę-u-siebie-home-advantage)
4. [Generator zawodników i balans startowy](#4-generator-zawodników-i-balans-startowy)
5. [Model sędziego](#5-model-sędziego)
6. [Kontuzje (w symulacji i między meczami)](#6-kontuzje-w-symulacji-i-między-meczami)
7. [Morale](#7-morale)
8. [Freshness (dyspozycja przed meczem)](#8-freshness-dyspozycja-przed-meczem)
9. [Transfery i negocjacje](#9-transfery-i-negocjacje)
10. [LeagueContext (Z-Score, mean/stddev)](#10-leaguecontext-z-score-meanstddev)
11. [Skład 11 i GamePlan przed meczem](#11-skład-11-i-gameplan-przed-meczem)
12. [Zapis wyniku i logów meczu](#12-zapis-wyniku-i-logów-meczu)
13. [Przepływy użytkownika (User flows)](#13-przepływy-użytkownika-user-flows)
14. [Bot v0 (minimalna wersja)](#14-bot-v0-minimalna-wersja)
15. [Podsumowanie: zależności między dokumentami](#15-podsumowanie-zależności-między-dokumentami)

---

## 1. Modele danych — encje i relacje

### 1.1 League (Liga)

```scala
case class League(
  id: LeagueId,
  name: String,
  teamCount: Int,              // 10–20 (parzyste: 10, 12, 14, 16, 18, 20)
  currentMatchday: Int,        // 0 .. totalMatchdays (0 = przed startem, potem ostatnia rozegrana kolejka)
  totalMatchdays: Int,         // 2 * (teamCount - 1) — dwie rundy
  seasonPhase: SeasonPhase,   // Setup | InProgress | Finished
  homeAdvantage: Double,       // mnożnik Dixon-Coles dla gospodarza (§3), np. 1.05
  startDate: Option[LocalDate], // ustawiane przy „Start sezonu” — pierwszy dzień kolejki 1 (środa lub sobota); mecze 17:00
  createdByUserId: UserId,     // założyciel — uprawniony do Start sezonu, Dodaj boty, Rozegraj kolejkę
  createdAt: Instant,
  timezone: ZoneId             // np. Europe/Warsaw — 17:00, auto-symulacja
)

enum SeasonPhase:
  case Setup       // trwa uzupełnianie drużyn / zaproszenia
  case InProgress  // terminarz ustalony, mecze się rozgrywają
  case Finished    // wszystkie kolejki rozegrane
```

**Relacje**: League 1 → N Team, League 1 → N Match (przez terminarz).

### 1.2 Team (Drużyna)

```scala
case class Team(
  id: TeamId,
  leagueId: LeagueId,
  name: String,
  owner: TeamOwner,            // UserId | BotId
  budget: BigDecimal,          // aktualny budżet (startowo równy dla wszystkich)
  playerIds: List[PlayerId],   // 16–20 po transferach; na start 18
  defaultGamePlanId: Option[GamePlanSnapshotId],  // domyślna taktyka (opcjonalnie)
  createdAt: Instant
)

enum TeamOwner:
  case Human(userId: UserId)
  case Bot(botId: BotId)
```

**Reguła**: `playerIds.length` ∈ [16, 20]. Przy tworzeniu ligi każda drużyna dostaje 18 zawodników (generator §4).

### 1.3 User (Użytkownik)

```scala
case class User(
  id: UserId,
  email: String,               // lub login — do logowania
  displayName: String,
  createdAt: Instant
)
```

**Uwaga**: Autentykacja (hasło, JWT) — osobny moduł; tu tylko dane użytkownika. Jeden User może mieć jedną drużynę w danej lidze (właściciel Team).

### 1.4 Invitation (Zaproszenie do ligi)

```scala
case class Invitation(
  id: InvitationId,
  leagueId: LeagueId,
  invitedUserId: UserId,
  invitedByUserId: UserId,
  token: String,               // unikalny, do linku (np. UUID)
  status: InvitationStatus,
  createdAt: Instant,
  expiresAt: Instant
)

enum InvitationStatus:
  case Pending
  case Accepted   // → tworzy Team z owner = Human(invitedUserId), dołącza do ligi
  case Declined
  case Expired
```

**Flow**: Założyciel ligi wysyła zaproszenie (email/link z tokenem). Zaproszony klika link, loguje się (lub zakłada konto), akceptuje → system tworzy drużynę z pustą kadrą (lub z placeholderami), którą użytkownik „aktywuje” (np. nadaje nazwę zespołu, po czym generator uzupełnia 18 zawodników — patrz §4). Sloty ligi (10–20) wypełniane są przez drużyny Human + ewentualnie Bot.

### 1.5 Match (Mecz)

```scala
case class Match(
  id: MatchId,
  leagueId: LeagueId,
  matchday: Int,               // numer kolejki (1 .. totalMatchdays)
  homeTeamId: TeamId,
  awayTeamId: TeamId,
  scheduledAt: Instant,        // środa/sobota 17:00 w timezone ligi
  status: MatchStatus,
  homeGoals: Option[Int],      // None dopóki status != Played
  awayGoals: Option[Int],
  refereeId: RefereeId,
  resultLogId: Option[MatchResultLogId]  // odnośnik do pełnego logu (zdarzenia)
)

enum MatchStatus:
  case Scheduled   // czeka na rozegranie
  case Played      // rozegrany (wynik i ewentualnie log zapisane)
  case Postponed   // opcjonalnie na przyszłość
```

**Relacje**: Match N → 1 League, Match N → 1 Referee. Dla każdej pary (home, away) w lidze są dokładnie dwa mecze w sezonie: jeden u siebie dla home, jeden u siebie dla away (§2).

### 1.5a MatchSquad (Skład na mecz)

Skład 11 zawodników i taktyka (GamePlan) na konkretny mecz. Jedna encja per (matchId, teamId). Pełna definicja, reguły biznesowe i relacje → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §3. Skrót:

```scala
case class MatchSquad(
  id: MatchSquadId,
  matchId: MatchId,
  teamId: TeamId,
  lineup: List[LineupSlot],     // 11 elementów: (playerId, positionSlot)
  gamePlan: GamePlan,
  submittedAt: Instant,
  source: MatchSquadSource      // Manual | Default | Bot
)
case class LineupSlot(playerId: PlayerId, positionSlot: PositionSlot)
```

Reguły: lineup musi obejmować dokładnie 11 graczy (1 GK, 10 polowych), bez kontuzjowanych. Brak MatchSquad do deadline’u → aplikacja tworzy Default (domyślny 11 + domyślny GamePlan) przy rozegraniu kolejki.

### 1.6 Player (Zawodnik)

Zgodnie z **SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md** §3.1 i **ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md**: atrybuty fizyczne, techniczne, mentalne, traits, bodyParams. W warstwie aplikacji dodajemy stan „poza meczem”:

```scala
case class Player(
  id: PlayerId,
  teamId: TeamId,
  firstName: String,
  lastName: String,
  preferredPositions: Set[Position],
  physical: PhysicalAttributes,
  technical: TechnicalAttributes,  // lub GKAttributes
  mental: MentalAttributes,
  traits: PlayerTraits,
  bodyParams: BodyParams,
  // Stan między meczami (aktualizowany po meczu / co kolejkę):
  injury: Option[InjuryStatus],
  freshness: Double,              // 0.0–1.0 (§8)
  morale: Double,                 // 0.0–1.0 lub 1–5 (§7)
  createdAt: Instant
)

case class InjuryStatus(
  sinceMatchday: Int,             // od której kolejki kontuzjowany
  returnAtMatchday: Int,          // powrót w tej kolejce (włącznie)
  severity: InjurySeverity        // do statystyk / ewentualnie UI
)

enum InjurySeverity:
  case Light    // 1–2 kolejki
  case Medium   // 3–4 kolejki
  case Severe   // 5+ kolejek
```

**Uwaga**: W trakcie meczu stan „na boisku” (stamina, kartki) to MatchState w silniku; injury/freshness/morale to wejście do silnika (modyfikatory) i wynik (po meczu: ewentualna nowa kontuzja, odświeżenie freshness, aktualizacja morale).

### 1.7 Referee (Sędzia)

```scala
case class Referee(
  id: RefereeId,
  leagueId: LeagueId,           // pula sędziów per liga
  name: String,                 // np. do wyświetlania w logach
  strictness: Double            // 0.0–1.0 (§5): 0 = bardzo łagodny, 1 = bardzo surowy
)
```

**Tworzenie**: Przy „Start sezonu” tworzone jest **teamCount / 2** sędziów dla ligi; każdy sędzia prowadzi dokładnie jeden mecz w danej kolejce. **Przypisanie**: dla każdej kolejki mecze są losowo przypisywane do sędziów (KONTRAKTY §9.6).

### 1.8 Morale (stan drużyny / zawodnika)

Morale jest **cechą drużyny lub zawodnika** (wymagania mówią o wpływie na composure i decyzje — naturalnie per zawodnik). Propozycja: **per zawodnik** (każdy gracz ma morale), aktualizowane po meczu według reguł §7. Przechowywane w `Player.morale`.

### 1.9 Transfer, Offer (Transfery i oferty)

```scala
case class TransferWindow(
  id: TransferWindowId,
  leagueId: LeagueId,
  openAfterMatchday: Int,        // okno otwiera się po rozegraniu tej kolejki
  closeBeforeMatchday: Int,      // zamyka się przed tą kolejką (np. openAfter+1, closeBefore = openAfter+2)
  status: TransferWindowStatus
)

enum TransferWindowStatus:
  case Open
  case Closed

case class TransferOffer(
  id: TransferOfferId,
  windowId: TransferWindowId,
  fromTeamId: TeamId,           // oferujący (kupujący)
  toTeamId: TeamId,             // sprzedający
  playerId: PlayerId,           // oferowany zawodnik
  amount: BigDecimal,
  status: TransferOfferStatus,
  createdAt: Instant,
  respondedAt: Option[Instant]
)

enum TransferOfferStatus:
  case Pending
  case Accepted
  case Rejected
  case Withdrawn
```

**Reguły**: Po zaakceptowaniu oferty: `playerId` zmienia `teamId` na `fromTeamId`; budżet fromTeamId -= amount; budżet toTeamId += amount. Sprawdzenie limitów: fromTeamId po zakupie ≤ 20; toTeamId po sprzedaży ≥ 16. Szczegóły flow §9.

### 1.10 GamePlanSnapshot (Zapisana taktyka)

Taktyka wysyłana do silnika to pełny GamePlan (FORMACJE_ROLE_TAKTYKA.md). W aplikacji przechowujemy **snapshot** (np. JSON lub osobne tabele), żeby drużyna mogła mieć zapisane kilka wariantów i wybrać jeden przed meczem.

```scala
case class GamePlanSnapshot(
  id: GamePlanSnapshotId,
  teamId: TeamId,
  name: String,                 // np. "4-3-3 dom", "5-3-2 wyjazd"
  gamePlan: GamePlan,           // pełna struktura z FORMACJE
  createdAt: Instant
)
```

### 1.11 MatchResultLog (Pełny log meczu)

```scala
case class MatchResultLog(
  id: MatchResultLogId,
  matchId: MatchId,
  events: List[MatchEventRecord],  // chronologicznie (§12)
  summary: Option[MatchSummary],   // pełne statystyki meczu — definicja KONTRAKTY §2.3
  createdAt: Instant
)
```

**MatchSummary** (KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md §2.3): posiadanie, strzały (total, on target, xG), podania (total, completed, %), dośrodkowania, odbiór (tackles, interceptions, clearances, saves), faule, kartki, rożne, wrzuty, spalone, opcjonalnie duels/VAEP/WPA. Agregowane z `events` po zakończeniu symulacji.

Struktura pojedynczego zdarzenia (MatchEventRecord) — §12.

### 1.12 LeagueContext (Kontekst ligi dla Z-Score)

Używane w silniku do IWP (Matchup Matrix). Mean i stddev atrybutów per pozycja — §10.

```scala
case class LeagueContext(
  leagueId: LeagueId,
  positionStats: Map[Position, Map[String, PositionAttrStats]]  // pozycja → (nazwa atrybutu → mean, stddev)
)

case class PositionAttrStats(mean: Double, stddev: Double)
```

---

## 2. Terminarz ligi (dwie rundy, u siebie/wyjazd)

### 2.1 Zasada (jak w Premier League)

- **Dwa mecze z każdą drużyną**: raz na **własnym boisku**, raz na **wyjeździe**.
- **Runda pierwsza**: każdy z każdym **raz** (N drużyn → N×(N−1)/2 meczów w rundzie 1). Dla każdej pary (A, B) w rundzie 1 ustalone jest, czy A gra u siebie z B, czy B gra u siebie z A.
- **Runda druga**: te same pary, **ze zmienionym gospodarzem**. Czyli: jeśli w rundzie 1 A–B było u A (A gospodarz), to w rundzie 2 A–B jest u B (B gospodarz).

### 2.2 Liczba kolejek i meczów

- **Liczba drużyn**: N ∈ [10, 20].
- **Meczów na drużynę w sezonie**: 2×(N−1) (każda drużyna gra z każdą dwukrotnie).
- **Meczów w kolejce**: N/2 (gdy N parzyste) — każda drużyna gra dokładnie jeden mecz w danej kolejce. Gdy N nieparzyste: w każdej kolejce jedna drużyna ma „wolny” (bye); wtedy w jednej kolejce jest (N−1)/2 meczów.
- **Liczba kolejek (matchdays)**:  
  - N parzyste: 2×(N−1) kolejek (w każdej N/2 meczów).  
  - N nieparzyste: 2×N kolejek, przy czym w każdej kolejce jedna drużyna ma bye, więc łącznie też 2×(N−1) meczów na drużynę.

**Propozycja uproszczenia**: Przyjmujemy **N parzyste** (10, 12, 14, 16, 18, 20). Wtedy:
- Kolejek = 2×(N−1).
- W każdej kolejce N/2 meczów.
- Przykład N=10: 18 kolejek, po 5 meczów = 90 meczów w sezonie; każda drużyna gra 18 meczów.

### 2.3 Algorytm generowania terminarza (round-robin)

1. **Runda 1** (kolejki 1 .. N−1):  
   Klasyczny round-robin (np. algorytm kołowy): drużyny 1..N, w kolejce k drużyna i gra z drużyną j (i < j), przy czym „gospodarz” to np. drużyna o mniejszym indeksie w parze (albo większym — ustalona konwencja). Daje to N×(N−1)/2 meczów z ustalonym gospodarzem.
2. **Runda 2** (kolejki N .. 2×(N−1)):  
   Te same pary co w rundzie 1, ale **zamiana gospodarz–gość**. Czyli dla pary (A, B) z rundy 1: jeśli w rundzie 1 A była gospodarzem, to w rundzie 2 B jest gospodarzem.

**Mapowanie na dni**: Kolejki 1, 2, … przypisane do środy/soboty **17:00** w League.timezone. **League.startDate** = pierwszy dzień kolejki 1 (środa lub sobota). Kolejka 2 = następna sobota/środa, itd. (np. środa–sobota–środa–sobota). Dla każdego Match: `scheduledAt` = data i godzina 17:00 w timezone. **Symulacja automatyczna** o 17:00 w ten dzień (KONTRAKTY §9.7).

### 2.4 Identyfikacja „u siebie” w meczu

W encji `Match` mamy `homeTeamId` i `awayTeamId`. Silnik dostaje informację „gospodarz = homeTeamId” i stosuje **home_advantage** dla tej drużyny (§3).

### 2.5 Tabela i kolejność (tie-break)

Kolejność drużyn w tabeli: 1) punkty (malejąco), 2) różnica bramek (malejąco), 3) bramki zdobyte (malejąco), 4) mecz bezpośredni — punkty z dwu meczów między danymi drużynami (malejąco), 5) losowanie. Pełna specyfikacja → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §4.

---

## 3. Bonus za grę u siebie (home advantage)

- **Źródło**: SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md §4.6 (Dixon-Coles):  
  `λ_home = α_home × β_away × home_advantage`, `λ_away = α_away × β_home` (bez mnożnika dla gościa).
- **Propozycja wartości**: `home_advantage = 1.05` (delikatny bonus: ~5% wyższa oczekiwana liczba goli gospodarza). Można uczynić to konfigurowalnym per ligę (pole `League.homeAdvantage: Double`, domyślnie 1.05).
- **Gdzie stosowane**: W pre-match compute Dixon-Coles λ oraz w trakcie symulacji wszędzie tam, gdzie silnik używa „home/away” (np. ewentualny drobny modyfikator presji lub crowd — jeśli kiedyś dodany). Minimum: mnożnik λ dla gospodarza wystarczy.

---

## 4. Generator zawodników i balans startowy

### 4.1 Cel

- Każda drużyna na start ma **18 zawodników** (zgodnie z WYMAGANIA_GRY).
- **Balans**: Wszystkie drużyny mają **tę samą łączną „siłę”**, żeby start był fair.

### 4.2 Metryka siły (do zbalansowania)

**Propozycja**: Jedna liczba „overall” per zawodnik, potem **suma overall w drużynie** ma być stała dla wszystkich drużyn.

- **Overall polowy**: ważona suma atrybutów. Wagi można wziąć z ATRYBUTY (np. równe wagi dla wszystkich 30 atrybutów, skala 1–20) → `overall = (sum of attributes) / 30` lub custom wagi. Alternatywa: suma bez wag.
- **Overall bramkarza**: analogicznie z 6 atrybutów bramkarskich (+ ewentualnie wybrane fizyczne/mentalne).
- **Stała docelowa**: T = suma 18 overall w drużynie. Dla każdej drużyny T = const (np. wybrana z góry wartość lub średnia z wygenerowanej puli).

### 4.3 Algorytm generowania

1. **Pula ligowa**: Wygeneruj tymczasowo M zawodników (M >> 18×L, np. M = 500), losując atrybuty z zadanego zakresu (np. 1–20), z rozkładem zbliżonym do normalnego (średnia ~10, odchylenie ~3) lub równomiernym. Dla każdego oblicz overall i pozycję (GK vs polowy) na podstawie preferowanych pozycji (losowo przypisanych).
2. **Balans drużynowy**:  
   - Dla każdej drużyny: wybierz 1 bramkarza i 17 polowych z puli (bez zwracania), tak aby **suma overall w drużynie = T** (dokładnie lub z małym tolerowanym odchyleniem). To można zrealizować np. zachłannie: sortuj zawodników po overall, dobieraj pary/triplety tak, żeby suma się zgadzała; albo optymalizacja (np. programowanie całkowitoliczbowe) dla dokładnego T.  
   - Alternatywa prostsza: generuj 18×L zawodników od razu z ograniczeniem, że suma atrybutów (np. suma 30 liczb) w każdej „drużynie” jest identyczna — np. najpierw losuj sumę S dla drużyny, potem losuj 18×30 wartości z ograniczeniem sumy per drużyna = S.
3. **Różnicowanie**: W obrębie tej samej sumy można losować rozkład (np. jeden zawodnik bardziej „szybki”, inny „techniczny”), żeby drużyny nie były klonami. Np. constraint: suma = T, ale wariancja overall w drużynie ≥ pewna wartość.
4. **Imiona i nazwiska (v1)**: Na start wszyscy zawodnicy mają **firstName = "Name"**, **lastName = "Surname"** (w API/UI można eksponować jako name/surname). Użytkownik może później edytować (personalizacja). Rozbudowa o pulę imion/nazwisk — na później.

### 4.4 Stałe do ustalenia w implementacji

- Docelowa suma T (np. 18 × 10 = 180 jeśli overall średnio 10, lub wyższa dla „lepszej” ligi).
- Rozkład początkowy atrybutów (średnia, stddev lub min–max).
- Czy bramkarz ma osobną „pulę” overall (np. GK i polowi osobno sumowani).

---

## 5. Model sędziego

### 5.1 Strictness → foul_risk i card_risk

SILNIK zakłada: Foul rozstrzyga Bernoulli(foul_risk), Card (żółta/czerwona) Bernoulli(card_risk). Atrybuty: aggression, tackling (foul), aggression, foul_zone (card). **Sędzia** modyfikuje te prawdopodobieństwa.

**Propozycja formuł**:

- **Bazowe foul_risk** (bez sędziego): np. `base_foul = f(aggression/tackling ratio, pressure)`. Np. `base_foul = 0.02 + (aggression/20) * 0.03 - (tackling/20) * 0.02` (im wyższa agresja, tym więcej fauli; im wyższy tackling, tym mniej „brudnych” interwencji). Wartości do kalibracji.
- **Modyfikator sędziego (strictness)**:  
  `foul_risk = base_foul * (0.6 + 0.8 * strictness)`.  
  Dla strictness = 0 (łagodny): foul_risk = base_foul * 0.6. Dla strictness = 1 (surowy): foul_risk = base_foul * 1.4.
- **card_risk**: Podobnie: `card_risk = base_card * (0.6 + 0.8 * strictness)`. base_card może zależeć od tego, czy to pierwszy faul w meczu, czy powtarzający się (np. wyższy po żółtej). Strictness w zakresie 0–1.

**Tworzenie i przypisanie sędziów** (szczegóły KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md §9.6):

- **Liczba sędziów**: Przy „Start sezonu” tworzone jest **teamCount / 2** sędziów dla ligi (dzielenie całkowite). Każda kolejka ma teamCount/2 meczów, więc każdy sędzia prowadzi dokładnie jeden mecz w danej kolejce.
- **Przypisanie**: Dla każdej kolejki mecze są **losowo** przypisywane do sędziów (permutacja: każdy mecz — jeden sędzia). Referee ma `leagueId` (pula per liga).

---

## 6. Kontuzje (w symulacji i między meczami)

### 6.1 Kontuzja w trakcie symulacji meczu

- **Zdarzenie**: W silniku możliwe zdarzenie **Injury** (zawodnik kontuzjowany w meczu). Trigger: np. przy zdarzeniach fizycznych (Foul, Physical Duel, Aerial Duel) lub z małym prawdopodobieństwem przy intensywnym ruchu (sprint, pressing).
- **Prawdopodobieństwo**: Propozycja — przy każdym **Foul** (faulowany lub faulujący): dodatkowe losowanie Bernoulli(p_injury). p_injury zależne od: `injuryProne` (traits), `ACWR` (obciążenie), ewentualnie siły zderzenia (np. aggression tacklera). Np. `p_injury = 0.02 * (1 + injuryProne/5) * acwr_factor`. Poza faulem: bardzo niskie p (np. 0.002) przy Sprint/HighIntensityRun, zależne od stamina i injuryProne.
- **Efekt**: Zawodnik oznaczany jako „injured” w tym meczu (nie może dalej grać — wymiana obowiązkowa jeśli to 11). Po zakończeniu meczu zapisujemy **InjuryStatus** przy Player: `sinceMatchday = currentMatchday`, `returnAtMatchday = currentMatchday + recoveryRounds`, `severity` z długości rekonwalescencji.

### 6.2 Długość rekonwalescencji (recoveryRounds)

- **Propozycja**: 1–2 kolejki (Light), 3–4 (Medium), 5+ (Severe). Losowanie z rozkładu zależnego od severity (np. przy Injury w meczu: severity zależy od typu zdarzenia — faul = często Light/Medium, rzadko Severe).
- **Zapis**: `Player.injury = Some(InjuryStatus(sinceMatchday, returnAtMatchday, severity))`. Po każdej rozegranej kolejce: jeśli `currentMatchday >= returnAtMatchday`, ustaw `Player.injury = None`.

### 6.3 Kontuzja „między meczami”

Opcjonalnie: bardzo niskie prawdopodobieństwo kontuzji w treningu (jeśli kiedyś trening będzie). Na razie **wystarczy kontuzja tylko w symulacji meczu**.

### 6.4 Nieuczestniczenie kontuzjowanego w grze

Przy wyborze składu 11 (§11): zawodnicy z `injury.isDefined` i `returnAtMatchday > currentMatchday` są **niedostępni** (nie można ich wystawić). Silnik nie wybiera ich do zdarzeń.

---

## 7. Morale

### 7.1 Skala i przechowywanie

- **Skala**: 0.0–1.0 (lub 1–5 w UI; w modelu 0–1). Przechowywane w `Player.morale` (albo osobna tabela per zawodnik/drużyna — propozycja: per zawodnik).
- **Wpływ na silnik**: Modyfikator na **composure** i **decisions** w rozstrzyganiu zdarzeń. Np. `effective_composure = base_composure * (0.85 + 0.15 * morale)`, `effective_decisions = base_decisions * (0.85 + 0.15 * morale)`. Przy morale = 1.0 brak zmiany; przy morale = 0.0 ok. 15% spadku.

### 7.2 Aktualizacja morale (po meczu)

- **Wynik meczu**: Wygrana: +Δ (np. +0.05), remis: 0, porażka: −Δ (np. −0.05). Kapowanie: morale ∈ [0, 1].
- **Seria**: Np. 3 wygrane z rzędu: dodatkowo +0.02; 3 porażki z rzędu: −0.02. Do ustalenia dokładne progi.
- **Miejsce w tabeli**: Opcjonalnie — np. lider +0.01, strefa spadkowa −0.01 (per kolejka). Na start można uprościć tylko do wyniku i serii.
- **Czas**: Aktualizacja po każdym rozegranym meczu drużyny (wszystkim zawodnikom z kadry tej drużyny ten sam efekt drużynowy, albo tylko tym, którzy grali — propozycja: cała kadra, żeby prostota).

---

## 8. Freshness (dyspozycja przed meczem)

### 8.1 Definicja

- **Freshness** = 0.0–1.0. 1.0 = pełna świeżość (np. nie grał przez kilka kolejek), 0.0 = maksymalne zmęczenie (grał 90 min w ostatnich 3 meczach z rzędu).
- Przechowywane w `Player.freshness`, aktualizowane **po każdym meczu** (na podstawie minut w tym meczu) i ewentualnie **przed meczem** (regeneracja między kolejkami).

### 8.2 Wzór (propozycja)

- **Zużycie po meczu**: Jeśli zawodnik rozegrał `minutes` minut w meczu:  
  `freshness_after = freshness_before - (minutes/90) * 0.25`.  
  Czyli 90 min = −0.25; 45 min = −0.125. Nie schodzimy poniżej 0.
- **Regeneracja między meczami**: Między kolejną kolejką a poprzednią: każdy zawodnik dostaje +0.15 (do max 1.0). Czyli np. po 90 min (freshness spadła o 0.25) i tydzień przerwy (+0.15) netto −0.10; po dwóch tygodniach bez gry +0.30 → szybki powrót do 1.0.
- **Wpływ na silnik**: Modyfikator **początkowej staminy** w meczu lub mnożnik na atrybuty w pierwszej połowie. Np. `starting_stamina_factor = 0.85 + 0.15 * freshness` (przy freshness=0 startujemy z 85% „baku”, przy 1.0 z 100%). Alternatywa: freshness wpływa na staminaDecay w trakcie meczu (niższa freshness = szybsze spadanie staminy).

---

## 9. Transfery i negocjacje

### 9.1 Okno transferowe

- **Częstotliwość**: Co 2 tygodnie (po 2 rozegranych kolejkach). Okno otwiera się po rozegraniu kolejki K, zamyka przed kolejką K+2 (w międzyczasie jest kolejka K+1).
- **Tworzenie okien**: Przy „Start sezonu” system tworzy rekordy `TransferWindow` dla ligi: dla K ∈ {2, 4, 6, …, totalMatchdays−2} (co drugą kolejkę): `openAfterMatchday = K`, `closeBeforeMatchday = K+2`, `status = Closed` (zmiana na Open po rozegraniu kolejki K). Dla ligi 18-kolejkowej: okna po kolejkach 2, 4, 6, 8, 10, 12, 14, 16.
- **Stan**: `TransferWindow.status` (Open/Closed). Tylko gdy Open, można składać/akceptować oferty. Po rozegraniu kolejki `openAfterMatchday` ustaw status na Open; przed kolejką `closeBeforeMatchday` ustaw na Closed.

### 9.2 Model oferty

- **Oferta**: Drużyna A (kupujący) składa ofertę do drużyny B (sprzedający) na zawodnika P. Kwota = `amount`. Drużyna B może zaakceptować lub odrzucić. Po zaakceptowaniu: P przechodzi do A, A płaci B (budżet A -= amount, budżet B += amount).
- **Limity kadry**: Przed finalizacją sprawdzenie: A po zakupie ma ≤ 20 zawodników; B po sprzedaży ma ≥ 16. W przeciwnym razie transakcja niedozwolona.
- **Budżet**: A musi mieć budget ≥ amount. Startowo wszyscy mają ten sam budżet (WYMAGANIA_GRY).

### 9.3 Cena zawodnika (szacunek)

- **Propozycja**: Cena = f(overall, wiek, ewentualnie pozycja). Np. `price = basePrice * (overall/10)^2`. basePrice z konfiguracji ligi. Gracz może oferować więcej lub mniej; drużyna B (lub bot) akceptuje, jeśli oferta ≥ minimum (np. 0.9 * price) lub po negocjacji (do ustalenia: jedna oferta vs licytacja).

### 9.4 Flow (kroki)

1. Okno otwarte. Gracz A wybiera „Kup zawodnika”, przegląda drużyny ligi i wybiera zawodnika P z drużyny B.
2. A wpisuje kwotę, wysyła ofertę → `TransferOffer(Pending)`.
3. B (gracz lub bot) widzi ofertę, akceptuje lub odrzuca. (Bot: np. akceptuje jeśli amount ≥ 1.0 * price.)
4. Przy akceptacji: zmiana teamId P na A, przesunięcie budżetów, sprawdzenie limitów 16–20. Oferta → Accepted.
5. W jednym oknie można wiele ofert (różnych graczy); każdy zawodnik może mieć tylko jedną aktywną ofertę (albo wiele — do ustalenia w implementacji).

---

## 10. LeagueContext (Z-Score, mean/stddev)

- **Cel**: Silnik (Matchup Matrix, IWP) używa Z(attr) = (attr - mean) / stddev. Mean i stddev muszą być per pozycja (lub per grupa pozycji: CB, FB, DM, CM, AM, W, ST).
- **Źródło przy tworzeniu ligi**: Po wygenerowaniu wszystkich zawodników (§4) dla danej ligi oblicz **empirycznie** mean i stddev dla każdego atrybutu w każdej pozycji (na podstawie preferredPositions). Zapisz w `LeagueContext(leagueId, positionStats)`.
- **Aktualizacja**: Przy transferach między ligami nie robimy (w tej wersji transfery wewnątrz ligi). Można raz na sezon przeliczyć LeagueContext z aktualnej kadry ligi, albo zostawić stały z momentu tworzenia ligi. Propozycja: **stały od tworzenia** (prostsze).

---

## 11. Skład 11 i GamePlan przed meczem

### 11.1 Skąd bierzemy skład i taktykę

- **Dla gracza (Human)**: Przed kolejką (do deadline’u, np. do 17:00 w dniu meczu) gracz ustawia **skład 11** (wybór z kadry 16–20, z pominięciem kontuzjowanych) i **przypisanie do slotów** (kto na LCB, kto na ST, …) oraz **GamePlan** (formacja, role, instrukcje, strategia). Może wybrać zapisany GamePlanSnapshot lub edytować ad hoc. Zapisujemy encję **MatchSquad** (definicja §1.5a, reguły i API → KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md §3, §5): matchId, teamId, lineup, gamePlan, source (Manual/Default/Bot).
- **Dla bota**: Bot v0 (§14) wybiera 11 (np. pierwsze 11 po pozycjach/overall) i jeden domyślny GamePlan (np. 4-3-3). Brak triggerów w pierwszej wersji.

### 11.2 Deadline

- **Propozycja**: Skład i taktyka muszą być zapisane **przed** momentem rozegrania kolejki (np. przed 17:00 w dniu meczu). Jeśli gracz nie ustawił: system stosuje **ostatni zapisany** skład i GamePlan (np. z poprzedniego meczu), albo domyślny (np. pierwsze 11 z listy, domyślny 4-3-3). To zapisane w regułach biznesowych (np. w tym dokumencie lub w API).

### 11.3 Wejście do silnika

Silnik dostaje:  
- Team A: lista 11 Player (z atrybutami, injury/freshness/morale), skład (playerId → PositionSlot), GamePlan A.  
- Team B: analogicznie.  
- Match: homeTeamId, awayTeamId (→ home_advantage).  
- Referee: strictness.  
- LeagueContext (mean/stddev).  

Z tego silnik oblicza modele pośrednie i uruchamia symulację.

---

## 12. Zapis wyniku i logów meczu

### 12.1 Wynik

- Po zakończeniu symulacji: `Match.homeGoals = Some(h), Match.awayGoals = Some(a)`, `Match.status = Played`. Tabela ligi aktualizowana (punkty, bramki, różnica).

### 12.2 Pełny log (zdarzenia)

- **Format zdarzenia (MatchEventRecord)**: Propozycja — wspólny typ z polami: `minute: Int`, `eventType: String` (Pass, Shot, Foul, Injury, Goal, Substitution, …), `actorPlayerId: Option[PlayerId]`, `secondaryPlayerId: Option[PlayerId]` (np. odbiorca podania, sfaulowany), `teamId: Option[TeamId]`, `zone: Option[Int]`, `outcome: Option[String]` (Success, Miss, Saved, …), `metadata: Map[String, String]` (np. xG, distance — do analizy). Szczegóły mogą być zgodne z wewnętrzną reprezentacją zdarzeń w silniku; eksport do logu = serializacja tych zdarzeń w kolejności chronologicznej.
- **Zapis**: `MatchResultLog(matchId, events, summary?, createdAt)`. `Match.resultLogId = Some(logId)`. Przechowywanie: baza (osobna tabela events lub jeden JSON/JSONB per mecz) lub plik — zależnie od skali i zapytań. Wymaganie: użytkownik może „wyciągać wnioski” — więc dostęp do listy zdarzeń i ewentualnie agregatów (posiadanie, strzały, xG) musi być możliwy (API + UI).

### 12.3 Kontuzje w logu

- Zdarzenie typu Injury w `events`: `eventType = "Injury"`, `actorPlayerId = injured`, `metadata = {"returnMatchday": N}`. Po meczu to napędza ustawienie `Player.injury` (§6).

---

## 13. Przepływy użytkownika (User flows)

### 13.1 Tworzenie ligi

1. Użytkownik wybiera „Utwórz ligę”.
2. Podaje nazwę ligi, liczbę drużyn (10–20), nazwę **swojego** zespołu, timezone (opcjonalnie, domyślnie np. Europe/Warsaw).
3. System tworzy League (Setup), tworzy Team dla użytkownika (owner = Human(userId)), rezerwuje sloty na drużyny (N−1 pustych slotów).
4. Użytkownik zaprasza znajomych (link z tokenem Invitation) lub dodaje boty (wybór „Dodaj boty” → N−1 drużyn Bot). Zaproszenia w statusie Pending.
5. Gdy zaproszony zaakceptuje: tworzy się jego Team (nazwa do podania), zaproszenie → Accepted.
6. Gdy wszystkie sloty wypełnione (Human + Bot): założyciel (lub system) klika „Start ligi” / „Wygeneruj zawodników i terminarz”.
7. System: użytkownik podaje **datę pierwszego meczu** (środa lub sobota) lub system ustawia najbliższą środę/sobotę → **League.startDate**. Następnie: dla każdej drużyny generator (§4) tworzy 18 Playerów (balans; imiona/nazwiska v1: §4.4), tworzy teamCount/2 sędziów (§5, §1.7), tworzy rekordy TransferWindow (§9.1), ustawia LeagueContext (§10), generuje terminarz (§2) z Match.scheduledAt = 17:00 w kolejne środy/soboty od startDate, ustawia League.seasonPhase = InProgress, League.currentMatchday = 0. Symulacja automatyczna o 17:00 (KONTRAKTY §9.7).

### 13.2 Przed meczem (przed 17:00)

1. Gracz widzi terminarz: „Kolejka K, sobota 17:00 — Twój mecz: Team A vs Team B (u siebie / na wyjeździe)”.
2. Wchodzi w „Ustawienia meczu” / „Skład”: wybiera 11 z kadry (kontuzjowani wyszarzeni), przypisuje do slotów (formacja), wybiera lub edytuje GamePlan (formacja, role, instrukcje, pressing, itd.).
3. Zapisuje. Zapisane jako MatchSquad + GamePlan dla tego meczu (matchId). Deadline: np. do 17:00 w dniu meczu (lub do momentu „Rozegraj kolejkę” — zależnie od tego, czy rozegranie jest automatyczne, czy manualne).

### 13.3 Rozegranie kolejki

- **Wariant A (manual)**: Administrator ligi (założyciel?) klika „Rozegraj kolejkę K”. System dla każdego meczu w kolejce K: ładuje składy i GamePlany (lub domyślne), ładuje Referee, LeagueContext, wywołuje silnik, zapisuje wynik i log, aktualizuje tabele, uaktualnia injury/freshness/morale dla zawodników. Zwiększa League.currentMatchday o 1.
- **Wariant B (automat)**: Job cron o 17:00 w timezone ligi sprawdza, czy jest kolejka zaplanowana na dziś; jeśli tak, wykonuje to samo co wariant A. Wymaga przechowywania „nextScheduledDate” dla kolejki.

### 13.4 Po meczu

1. Gracz widzi wynik (tabela, wyniki kolejki) i link „Szczegóły meczu” / „Log meczu”.
2. Otwiera pełną listę zdarzeń (chronologicznie), ewentualnie podsumowanie (posiadanie, strzały, xG, VAEP — jeśli silnik to zwraca). Może analizować i „wyciągać wnioski” (WYMAGANIA_GRY).

### 13.5 Okno transferowe

1. Po kolejce K system otwiera okno (jeśli K spełnia warunek „co 2 tygodnie”). Gracz widzi „Okno transferowe otwarte”.
2. Przegląda zawodników z innych drużyn (lista lub wyszukiwarka), wybiera zawodnika, wpisuje kwotę, wysyła ofertę.
3. Inny gracz (lub bot) widzi przychodzące oferty, akceptuje/odrzuca. Po akceptacji transfer wykonany, budżety i kadry zaktualizowane.
4. Po zamknięciu okna (np. przed kolejką K+2) nie można już składać ofert.

---

## 14. Bot v0 (minimalna wersja)

- **Skład**: Z kadry 18 (16–20) wybierz 11: 1 bramkarz (preferredPositions zawiera GK), 10 polowych. Kolejność: np. sortowanie po overall, potem dobór tak, aby pokryć formację (np. domyślna 4-3-3: 2 CB, LB, RB, CDM, 2 CM, LW, RW, ST). Prosty algorytm: wybierz najlepszego GK, potem najlepszych 2 CB, potem LB, RB, itd. według preferredPositions i overall.
- **GamePlan**: Jeden szablon 4-3-3 (lub z konfiguracji): Formation z FORMACJE presetu, domyślne role (np. BPD, Cover, FB×2, Anchor, B2B, Mezzala, W, IF, AF), BuildUpStyle/Pressing/DefensiveLine/AttackingApproach — stałe wartości (np. balanced). Bez triggerów.
- **Oferty transferowe**: Bot akceptuje ofertę, jeśli amount ≥ 1.0 * estimatedPrice (lub 1.1 dla „twardego” negocjatora). Bot nie składa ofert w v0 (opcjonalnie później).

---

## 15. Podsumowanie: zależności między dokumentami

| Element | Gdzie zdefiniowane |
|--------|---------------------|
| Atrybuty, traits, bodyParams | ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md |
| GamePlan, Formation, Role, Zone, Position | FORMACJE_ROLE_TAKTYKA.md |
| Silnik: xPass, xG, IWP, Pitch Control, DxT, fatigue, Phase | SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md |
| Dixon-Coles, home_advantage | SILNIK §4.6; uzupełnienie §3 tego docu |
| Wymagania produktowe (liga, kadra, transfery, kontuzje, morale, logi, PvP/PvAI) | WYMAGANIA_GRY.md |
| Modele danych (League, Team, Match, MatchSquad, User, Referee, Injury, Transfer, …) | **§1 tego dokumentu** (+ MatchSquad §1.5a) |
| Terminarz (dwie rundy, u siebie/wyjazd), tabela (tie-break §2.5) | **§2** |
| Generator i balans, sędzia, kontuzje w symulacji, morale, freshness, transfery, LeagueContext, skład/GamePlan, logi, flows, Bot v0 | **§4–§14 tego dokumentu** |
| Kontrakt wejścia/wyjścia silnika, typy zdarzeń, API, schemat bazy, auth, UI, edge case’y, testy | **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** |

Ten dokument uzupełnia brakujące definicje i reguły tak, aby po jego przeczytaniu wraz z WYMAGANIA_GRY, SILNIK, FORMACJE, ATRYBUTY i **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA** można było przystąpić do implementacji (Scala 3, ZIO, aplikacja webowa) bez domysłów.

---

*Modele i przepływy v1.0 — Luty 2026*
