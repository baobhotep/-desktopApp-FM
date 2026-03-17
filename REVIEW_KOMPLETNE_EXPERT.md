# Kompleksowe review techniczne projektu (backend + frontend + testy)

Data: 2025-03-17.  
Zakres: silnik meczu, analityka, serwisy, API, baza, frontend Laminar, testy, konfiguracja.  
Uwzględniono lekcje z `cursor.md`.

---

## 1. Backend – silnik i analityka

### 1.1 SimpleMatchEngine: strefy ataku dla gości (Cross, zoneThreat)

- **Severity:** HIGH  
- **Lokalizacja:**  
  - `backend/src/main/scala/fmgame/backend/engine/SimpleMatchEngine.scala` ok. 163, 269  
- **Opis:**  
  - `zoneThreat(zone)` używa przestarzałego `DxT.baseZoneThreat(zone)` bez `possessionHome`. Dla drużyny wyjazdowej wartość zagrożenia strefy w metadanych (Pass/Shot) jest odwrócona (cursor.md: DxT dla drużyny wyjazdowej).  
  - W Cross `attackZones = (1 to TotalZones).filter(PitchModel.isAttackingThird)` używa wersji 1-arg (perspektywa gospodarzy). Dla `tid == awayTeamId` dośrodkowania są losowane ze stref 19–24 (kolumna ≥ 4), czyli z **obronnej** tercji gości – goście „crossują” ze swojej połowy obrony.  
- **Sugerowana poprawka:**  
  - `zoneThreat(zone, isHome: Boolean)`: wywoływać `DxT.baseZoneThreat(zone, isHome)`; w `buildEvents` przekazywać `tid == input.homeTeam.teamId` tam, gdzie znane jest `tid`.  
  - Cross: `val isHome = tid == input.homeTeam.teamId`; `attackZones = (1 to TotalZones).filter(z => PitchModel.isAttackingThird(z, isHome)).toArray`.  
- **Kontekst:** W realnym FM/statystykach „final third” i „attacking third” są zawsze z perspektywy drużyny atakującej.

### 1.2 FullMatchAnalytics: xT i OBSO tylko z perspektywy gospodarzy

- **Severity:** HIGH  
- **Lokalizacja:**  
  - `backend/src/main/scala/fmgame/backend/engine/FullMatchAnalytics.scala` 119–120  
- **Opis:**  
  - `AdvancedAnalytics.xTValueIteration(transitionCounts, DxT.baseZoneThreat)` i `obsByZone(DxT.baseZoneThreat)` używają sygnatury `Int => Double` (przestarzały `baseZoneThreat`). Macierz przejść zawiera ruchy **obu** drużyn; bazowy threat jest liczony jak dla gospodarzy (kolumna 0 = własna bramka). Dla przejść gości xT/OBSO są więc semantycznie błędne.  
- **Sugerowana poprawka:**  
  - Rozszerzyć dane wejściowe (np. transitionCounts z informacją `isHome` per przejście) albo liczyć xT/OBSO osobno dla home i away i łączyć (np. uśrednić lub trzymać dwie mapy).  
  - Na szybko: przekazać do value iteration funkcję „neutralną” (np. `z => (DxT.baseZoneThreat(z, true) + DxT.baseZoneThreat(z, false)) / 2`) albo tylko przejścia gospodarzy, jeśli macierz to na to pozwala – z dokumentacją ograniczeń.  
- **Kontekst:** xT w literaturze jest z perspektywy posiadania piłki; agregacja obu drużyn bez perspektywy zniekształca interpretację.

### 1.3 MatchSummaryAggregator: passes in final third dla gości

- **Severity:** MEDIUM  
- **Lokalizacja:**  
  - `backend/src/main/scala/fmgame/backend/service/MatchSummaryAggregator.scala` 77  
- **Opis:**  
  - `PitchModel.isAttackingThird(zone)` bez `isHome` – zawsze perspektywa gospodarzy (kolumna ≥ 4). Dla zdarzeń gości (`isAway(tid)`) uznajemy „final third” za strefy 19–24, czyli **obronną** tercję gości; `passesInFinalThirdA` jest więc błędnie liczony.  
- **Sugerowana poprawka:**  
  - `if (zone >= 1 && PitchModel.isAttackingThird(zone, isHome(tid))) { if (isHome(tid)) passesInFinalThirdH += 1 else ... }`.  
- **Kontekst:** Statystyki typu „passes in final third” w optyce drużyny muszą być w **jej** tercji ataku.

### 1.4 AdvancedAnalytics / testy: użycie przestarzałego DxT.baseZoneThreat

- **Severity:** LOW (techniczny / spójność API)  
- **Lokalizacja:**  
  - `backend/src/main/scala/fmgame/backend/engine/AdvancedAnalytics.scala` – `xTValueIteration`, `obsByZone` przyjmują `Int => Double`.  
  - `AdvancedAnalyticsSpec.scala` 26, 39 – wywołania z `DxT.baseZoneThreat` (1-arg).  
  - `PropertyBasedSpec.scala` 172–174 – test „base zone threat” używa `DxT.baseZoneThreat(z1)` / `baseZoneThreat(z2)`.  
- **Opis:**  
  - API analityki i testy utrwalają przestarzałą wersję bez perspektywy; test property-based weryfikuje monotoniczność względem kolumny, co nadal jest prawdą dla `baseZoneThreat(z, true)`, ale nie dla mieszanki.  
- **Sugerowana poprawka:**  
  - W testach używać jawnego `(z: Int) => DxT.baseZoneThreat(z, true)` i ewentualnie dodać testy dla `false`.  
  - Długoterminowo: rozważyć sygnaturę `(Int, Boolean) => Double` w xT/OBSO (z rozdzieleniem home/away jak w §1.2).

### 1.5 LeagueService.traverseConn: zbędne zapytanie w base case

- **Severity:** LOW  
- **Lokalizacja:**  
  - `backend/src/main/scala/fmgame/backend/service/LeagueService.scala` 117  
- **Opis:**  
  - `traverseConn` w base case używa `sql"SELECT 1".query[Int].unique.map(_ => List.empty[B])`, więc każdy fold wykonuje zbędne `SELECT 1` (cursor.md: connUnit).  
- **Sugerowana poprawka:**  
  - Base case: `cats.Applicative[ConnectionIO].pure(List.empty[B])` (albo `Sync[ConnectionIO].pure(Nil)`).  
- **Kontekst FP:** Unikamy niepotrzebnych efektów I/O w „pustym” przypadku.

---

## 2. Backend – serwisy i repozytoria

### 2.1 Rejestracja: obsługa błędu UNIQUE (email)

- **Status:** naprawione  
- **Lokalizacja:** `UserService.scala` 37  
- **Opis:** `userRepo.create(user).transact(xa).mapError(_ => "Email already registered")` – zgodnie z cursor.md, konflikt na emailu nie idzie w `.orDie`.  
- **Uwaga:** `findByEmail(...).transact(xa).orDie` (linia 33) nadal zamienia błędy DB na defect; do rozważenia mapowanie na „Service unavailable” zamiast orDie, jeśli chcemy łagodniejszą obsługę.

### 2.2 PlayMatchday / konferencja prasowa

- **Status:** zgodne z cursor.md  
- **Opis:**  
  - `playMatchday`: serializacja per liga przez `Semaphore` i `putIfAbsent` (LeagueService 104).  
  - Konferencja prasowa: `putIfAbsent(pcKey, true)` i fail gdy zwrócona wartość jest zdefiniowana (linia 1879) – brak TOCTOU.

---

## 3. Backend – API i baza

- **Routes / AppError:** Routes mapują błędy serwisów przez `AppError.fromServiceError` i zwracają odpowiednie statusy (Forbidden, NotFound, Validation, Conflict).  
- **CORS / JWT:** Konfiguracja CORS i weryfikacja tokena w Routes – bez nowych uwag.  
- **Database:** Schemat H2/Postgres, connection pooling (Hikari) – standardowo.  
- **Sugestia (LOW):** Rozważyć rate limiting na endpointy auth/register (ograniczenie brute-force i nadużyć).

---

## 4. Frontend

### 4.1 Main.scala – token dopiero po udanym me()

- **Status:** naprawione  
- **Opis:** Token i `currentUser` ustawiane są dopiero w `Right(user)` po `ApiClient.me(savedToken)`; przy błędzie czyścimy localStorage i ustawiamy Login (cursor.md: unikanie flash UI przy wygasłym tokenie).

### 4.2 PitchView – commit pozycji w mouseUp/mouseLeave

- **Status:** naprawione  
- **Opis:** Podczas dragu aktualizowane są tylko style przez `nodeRefs`; zapis do `Var` (positions) w `commitDragPosition()` przy `mouseUp` / `mouseLeave` (cursor.md: unikanie przebudowy 11 elementów przy każdym mousemove).

### 4.3 MatchSquadPage – opcje w selectach

- **Status:** w porządku  
- **Opis:** Każdy `<select>` buduje własną listę opcji wewnątrz `children <-- ... .map { ... (0 until 11).map { idx => ... select( (option(...) +: list.map(p => option(...))).toSeq ) } }` – brak współdzielonej jednej tablicy `opts` między wieloma selectami (cursor.md: jeden węzeł DOM – jeden rodzic).

### 4.4 FormationPresets

- **Status:** w porządku  
- **Opis:** 4-3-3 ma 4 obrońców, 3 pomocników, 3 napastników; `DefaultPositions433` – 11 różnych par (x,y). Brak duplikatów pozycji (cursor.md: 4-3-3 dwa ST na jednej pozycji).

---

## 5. Testy

### 5.1 AdvancedAnalyticsSpec – xT / baseZoneThreat

- **Severity:** LOW  
- **Lokalizacja:** `AdvancedAnalyticsSpec.scala` 26  
- **Opis:** Test przekazuje `DxT.baseZoneThreat` (1-arg); asercja używa `DxT.baseZoneThreat(..., true)`. Dla spójności i wycofania deprecation lepiej jawnie użyć `(z: Int) => DxT.baseZoneThreat(z, true)`.

### 5.2 PropertyBasedSpec – DxT

- **Severity:** LOW  
- **Lokalizacja:** `PropertyBasedSpec.scala` 172–174  
- **Opis:** Test weryfikuje monotoniczność względem kolumny przez `DxT.baseZoneThreat(z)` (deprecated). Dla perspektywy home to nadal poprawne; można przejść na `baseZoneThreat(z, true)` i ewentualnie dodać analogiczny test dla `false`.

### 5.3 FullMatchEngineSpec

- **Opis:** Testy nie zawierają oczywistych „always true” (np. `|| true`). Sprawdzane są: KickOff, minuty, typy zdarzeń, liczba goli vs eventy Goal, analityka, atrybuty (finishing/shooting, GK), possession.  
- **Sugestia:** Test „Nash penalty” (jeśli istnieje) powinien wymagać wystąpienia eventu „Penalty” i weryfikować rozkład kierunków / payoff, a nie dowolny mecz z golem (cursor.md: false positive).

---

## 6. Konfiguracja (build.sbt)

- **Scala 3.3.3,** ZIO 2.0.21, Doobie RC5, ZIO HTTP RC6, Circe, Laminar 17.2.1 – spójne.  
- **scalacOptions:** `-deprecation`, `-feature`, `-unchecked` – sensowne.  
- **Sugestia (LOW):** Włączyć `-Xlint` lub `-Wunused` tam, gdzie nie generuje zbyt wielu fałszywych alarmów.

---

## 7. Podsumowanie – tabela ustaleń

| # | Severity   | Obszar        | Krótki opis |
|---|------------|---------------|-------------|
| 1 | HIGH       | SimpleMatchEngine | zoneThreat i Cross bez perspektywy isHome → błędne strefy/metadane dla gości |
| 2 | HIGH       | FullMatchAnalytics | xT i OBSO używają baseZoneThreat(zone) → zła perspektywa dla drużyny wyjazdowej |
| 3 | MEDIUM     | MatchSummaryAggregator | passesInFinalThird z isAttackingThird(zone) → błędne dla away |
| 4 | LOW        | AdvancedAnalytics / testy | Użycie deprecated baseZoneThreat; testy i API bez isHome |
| 5 | LOW        | LeagueService | traverseConn base case wykonuje SELECT 1 zamiast pure(Nil) |
| 6 | LOW        | Testy         | AdvancedAnalyticsSpec / PropertyBasedSpec – jawnie baseZoneThreat(_, true) |
| 7 | LOW        | API           | Opcjonalnie rate limiting na auth/register |
| 8 | LOW        | build.sbt     | Opcjonalnie -Xlint / -Wunused |

---

## 8. Rekomendowana kolejność napraw

1. **CRITICAL** – brak (wszystkie krytyczne z cursor.md są zaadresowane).  
2. **HIGH**  
   - Najpierw **SimpleMatchEngine**: Cross (attacking zones z `isHome`) i `zoneThreat(zone, isHome)` w buildEvents dla Pass/Shot.  
   - Potem **FullMatchAnalytics**: xT i OBSO z perspektywą (rozszerzenie transitionCounts lub osobne mapy home/away / funkcja neutralna z dokumentacją).  
3. **MEDIUM**  
   - **MatchSummaryAggregator**: `isAttackingThird(zone, isHome(tid))` przy passesInFinalThird.  
4. **LOW**  
   - LeagueService: base case w `traverseConn` na `pure(List.empty[B])`.  
   - Testy: jawne `baseZoneThreat(z, true)` i ewentualne testy dla `false`.  
   - Opcjonalnie: rate limiting, scalacOptions.

Po wprowadzeniu zmian warto dodać wpis do `cursor.md` (np. „Strefy ataku / DxT zawsze z perspektywą posiadania (isHome) w SimpleMatchEngine, FullMatchAnalytics i agregatorze”).
