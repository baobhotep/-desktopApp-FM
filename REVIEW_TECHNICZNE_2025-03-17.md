# Kompleksowe review techniczne projektu FM-Game

**Data:** 2025-03-17  
**Zakres:** Backend (silnik, analityka, serwisy, API, baza), frontend (Laminar), testy, konfiguracja.  
**Uwzględniono:** `cursor.md` (lekcje z poprzednich błędów).

---

## 1. Backend – silnik i analityka

### 1.1 Kontuzja nie usuwa gracza z boiska (FullMatchEngine)

- **Severity:** HIGH
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/FullMatchEngine.scala`, ok. 703–706 (gałąź `generateFoulEvent` przy `isInjury`).
- **Opis:** Po evencie `Injury` stan jest aktualizowany tylko o `minute`, `totalSeconds`, `lastEventType`, `fatigueByPlayer`. Gracz **nie** jest usuwany z `homePlayerIds` / `awayPlayerIds` ani dodawany do `sentOff`. W efekcie kontuzjowany zawodnik nadal bierze udział w losowaniu aktora i w zdarzeniach do końca meczu. W realnym FM (i zgodnie z logiką czerwonych kartek w tym samym silniku) gracz po kontuzji schodzi z boiska (wymuszona zmiana lub gra w 10).
- **Sugerowana poprawka:**  
  - Dodać opcjonalnie `injured: Set[PlayerId]` w `MatchState` lub traktować kontuzję jak „sent off” (usunąć z listy graczy na boisku).  
  - Przy evencie `Injury`: dodać `actor.player.id` do `sentOff` (albo do osobnego `injured`), usunąć go z `homePlayerIds`/`awayPlayerIds` (jak przy czerwonej).  
  - Jeśli chcesz symulować wymuszoną zmianę: po `Injury` można wywołać logikę substytucji (jeśli `subsUsed < 5` i jest rezerwowy), w przeciwnym razie po prostu usunąć gracza z listy.
- **Kontekst:** Zgodność z `cursor.md` (czerwona kartka usuwa gracza z list) i realizmem FM.

---

### 1.2 SimpleMatchEngine: zoneThreat / DxT bez perspektywy drużyny

- **Severity:** HIGH
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/SimpleMatchEngine.scala`, linia 162 (`zoneThreat(zone)`), oraz linia 261 (`PitchModel.isAttackingThird`).
- **Opis:**  
  - `zoneThreat(zone)` wywołuje `DxT.baseZoneThreat(zone)` – wersję **deprecated** (jednoargumentową), która używa tylko numeru kolumny (perspektywa gospodarzy). Dla akcji gości „strefa ataku” i wartość strefy są odwrócone.  
  - W linii 261: `PitchModel.isAttackingThird` wywołane z jednym argumentem (strefa) to wersja „home-only”. Dla crossów gości `attackZones` to strefy ataku gospodarzy, nie gości.
- **Sugerowana poprawka:**  
  - W `zoneThreat` przekazywać `isHome` (np. z kontekstu drużyny przy budowaniu eventu) i używać `DxT.baseZoneThreat(zone, isHome)`.  
  - Przy crossach: wyznaczać `isHome` dla drużyny wykonującej cross i używać `PitchModel.isAttackingThird(zone, isHome)`.
- **Kontekst:** Zgodne z lekcją z `cursor.md` (DxT / threat map dla drużyny wyjazdowej).

---

### 1.3 MatchSummaryAggregator: passesInFinalThird bez isHome

- **Severity:** HIGH
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/service/MatchSummaryAggregator.scala`, linia 75.
- **Opis:** `PitchModel.isAttackingThird(zone)` bez drugiego argumentu zawsze oznacza „trzecia ataku gospodarzy” (kolumny 4–5). Dla podań gości „final third” powinno być ich tercją ataku (kolumny 0–1), więc `passesInFinalThirdA` jest liczony w złych strefach.
- **Sugerowana poprawka:** Dla każdego eventu Pass/LongPass wyznaczyć `eventIsHome = isHome(e.teamId)` i użyć `PitchModel.isAttackingThird(zone, eventIsHome)` przy inkrementacji `passesInFinalThirdH` / `passesInFinalThirdA`.
- **Kontekst:** Spójność statystyk z kierunkiem ataku (jak w DxT/OBSO).

---

### 1.4 FullMatchAnalytics: xT i OBSO na deprecated baseZoneThreat (bez isHome)

- **Severity:** MEDIUM
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/FullMatchAnalytics.scala`, linie 120–122 (`xTValueIteration`, `obsByZone`).
- **Opis:** `AdvancedAnalytics.xTValueIteration(transitionCounts, DxT.baseZoneThreat)` i `obsByZone(DxT.baseZoneThreat)` używają sygnatury `Int => Double`, więc wybierana jest **deprecated** wersja `DxT.baseZoneThreat(zone)`. Przejścia w `transitionCounts` są z obu drużyn, a zagrożenie strefy jest liczone tylko z perspektywy jednej (gospodarze). Wartości xT i OBSO są wtedy niespójne interpretacyjnie.
- **Sugerowana poprawka:**  
  - Rozważyć dwie mapy xT: dla perspektywy gosp. i gości (np. `baseZoneThreat(_, true)` i `baseZoneThreat(_, false)`), albo jedną uśrednioną.  
  - Dla OBSO: albo OBSO per (strefa, kierunek ataku), albo jawnie uśredniona perspektywa i dokumentacja.  
  - Długoterminowo: w `AdvancedAnalytics` dodać overloady przyjmujące `(Int, Boolean) => Double` i używać `DxT.baseZoneThreat(zone, isHome)`.
- **Kontekst:** Spójność analityki z modelem DxT (cursor.md).

---

### 1.5 AdvancedAnalytics.obsByZone – API bez perspektywy

- **Severity:** LOW (w połączeniu z 1.4)
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/AdvancedAnalytics.scala`, linia 87.
- **Opis:** `obsByZone(baseZoneThreat: Int => Double)` nie przyjmuje `isHome`. Wszystkie wywołania używają deprecated `DxT.baseZoneThreat`.
- **Sugerowana poprawka:** Dodać wersję przyjmującą `(Int, Boolean) => Double` lub kontekst `isHome` i używać `DxT.baseZoneThreat(z, isHome)` wewnętrznie.

---

### 1.6 Test Nash penalty zbyt słaby (FullMatchEngineSpec)

- **Severity:** MEDIUM
- **Lokalizacja:** `backend/src/test/scala/fmgame/backend/engine/FullMatchEngineSpec.scala`, test „Nash penalty…” (ok. 414–421).
- **Opis:** Asercja wymaga tylko `hasPenalty || results.exists(r => r.events.exists(_.eventType == "Goal"))`. Dowolny mecz z golem (np. z gry) spełnia test; nie weryfikuje się, że karne są rozgrywane według Nash (np. że istnieje event `Penalty` lub `Goal` po faulu w polu karnym).
- **Sugerowana poprawka:** Dla seedów, które dają faul w polu karnym, wymagać eventu `Penalty` lub `Goal` z metadanych `penalty -> true`; opcjonalnie porównać prawdopodobieństwa strzału w lewo/prawo z wynikami Nash z analityki.
- **Kontekst:** Zgodne z wpisem w `cursor.md` (testy false positive – Nash penalty).

---

### 1.7 Inne (silnik)

- **pickWeighted** przy pustej liście: rzuca `IllegalArgumentException` (linia 105 FullMatchEngine). Teoretycznie możliwe przy pustym `outfield` po wielu czerwonych/kontuzjach – warto zabezpieczyć (np. `Option` lub wcześniejsze sprawdzenie).
- **Pozycje po Injury:** Jeśli wprowadzisz usuwanie gracza z boiska (pkt 1.1), upewnij się, że `updatePositions` i wszystkie miejsca używające `homePlayerIds`/`awayPlayerIds` są spójne (już są, bo używają list z state).

---

## 2. Backend – serwisy i repozytoria

### 2.1 playMatchday – serializacja

- **Severity:** Sprawdzone / OK  
- **Lokalizacja:** `LeagueService.scala` – `matchdayLocks`, `getOrCreateSemaphore`, `playMatchday` z `withPermit`.
- **Opis:** Serializacja per liga przez `Semaphore` i `putIfAbsent` jest zaimplementowana zgodnie z lekcją z `cursor.md`. Semafor tworzony w ZIO.

---

### 2.2 connUnit i pressConferenceGiven

- **Severity:** Sprawdzone / OK  
- **Lokalizacja:** `LeagueService.scala` – `connUnit = cats.Applicative[ConnectionIO].pure(())`, `pressConferenceGiven.putIfAbsent(pcKey, true)`.
- **Opis:** Brak zbędnego `SELECT 1`; atomowe `putIfAbsent` – zgodnie z `cursor.md`.

---

### 2.3 Rejestracja – błąd przy UNIQUE na email

- **Severity:** Sprawdzone / OK  
- **Lokalizacja:** `UserService.scala`, ok. linia 38.
- **Opis:** `userRepo.create(user).transact(xa).mapError(_ => "Email already registered")` – konwersja błędu UNIQUE na czytelny komunikat. Zgodne z `cursor.md`.

---

## 3. Backend – API i baza

### 3.1 Routes i obsługa błędów

- **Severity:** OK  
- **Opis:** `classifyError` mapuje komunikaty na `AppError` i status HTTP. CORS, JWT w headerze. Brak rażących braków w zakresie przejrzanego kodu.

### 3.2 Database

- **Severity:** OK  
- **Opis:** Indeksy na `teams(league_id)`, `matches(league_id, matchday)`, `invitations`, `transfer_offers`, `league_player_match_stats`. Migracje ALTER TABLE z `catchAll`. Brak oczywistych N+1 w przejrzanych fragmentach.

---

## 4. Frontend

### 4.1 Token i walidacja sesji (Main.scala)

- **Severity:** Sprawdzone / OK  
- **Lokalizacja:** `frontend/src/main/scala/app/Main.scala`, linie 16–26.
- **Opis:** Token ustawiany jest **dopiero po** pomyślnej odpowiedzi `ApiClient.me(savedToken)` (case `Right(user)`). Przy błędzie – czyszczenie localStorage i przekierowanie do Login. Zgodne z `cursor.md`.

### 4.2 PitchView – aktualizacja pozycji przy dragu

- **Severity:** Sprawdzone / OK  
- **Lokalizacja:** `PitchView.scala` – `handleMouseMove` (tylko style przez `nodeRefs`), `commitDragPosition()` na `mouseUp` / `mouseLeave`.
- **Opis:** Commit do `Var` (positions) tylko przy puszczeniu myszy. Zgodne z `cursor.md`.

### 4.3 MatchSquadPage – wspólne opcje w selectach

- **Severity:** Sprawdzone / OK  
- **Lokalizacja:** `MatchSquadPage.scala` – każdy `select` buduje własną listę opcji (np. `list.map(p => option(...))`).
- **Opis:** Nie ma jednej współdzielonej listy węzłów DOM dla wielu selectów. Zgodne z lekcją z `cursor.md`.

### 4.4 FormationPresets 4-3-3

- **Severity:** Sprawdzone / OK  
- **Opis:** Sloty 4-3-3: GK, LB, LCB, RCB, RB, LCM, CDM, RCM, LW, RW, ST (4+3+3). `DefaultPositions433` ma 11 różnych par (x,y). Brak zduplikowanych pozycji.

---

## 5. Testy

### 5.1 FullMatchEngineSpec – ACWR/Injury

- **Severity:** LOW  
- **Lokalizacja:** Test „ACWR/Injury…” – asercja `result.events.exists(_.eventType == "Foul") || result.events.exists(_.eventType == "Injury")`.
- **Opis:** Warunek jest sensowny (wymaga Foul lub Injury), ale test nie weryfikuje, że po evencie Injury gracz jest usunięty z boiska. Po naprawie pkt 1.1 warto dodać asercję: jeśli jest event `Injury` dla gracza X, to w kolejnych zdarzeniach X nie jest aktorem (albo jest w `sentOff`/analogicznym).

### 5.2 AdvancedAnalyticsSpec – baseZoneThreat

- **Severity:** LOW  
- **Opis:** Testy używają `DxT.baseZoneThreat` w wersji jednoargumentowej (deprecated). Po ewentualnym usunięciu deprecated warto zaktualizować testy na `(z, true)` i osobno przetestować perspektywę gości.

---

## 6. Konfiguracja (build.sbt)

### 6.1 scalacOptions

- **Severity:** LOW
- **Lokalizacja:** `build.sbt` – backend ma `-deprecation`, `-feature`, `-unchecked`. Brak np. `-Wunused:params`, `-Xlint`.
- **Sugerowana poprawka:** Dodać np. `-Wunused:params` (Scala 3) lub odpowiedniki z `-Xlint`, aby wyłapać nieużywane parametry i deprecated w nowym kodzie.

---

## 7. Podsumowanie tabelaryczne

| # | Severity   | Obszar        | Krótki opis                                                                 |
|---|------------|---------------|-----------------------------------------------------------------------------|
| 1.1 | HIGH    | FullMatchEngine | Kontuzja nie usuwa gracza z boiska (homePlayerIds/awayPlayerIds, sentOff)   |
| 1.2 | HIGH    | SimpleMatchEngine | zoneThreat / isAttackingThird bez isHome (DxT i crossy dla gości)           |
| 1.3 | HIGH    | MatchSummaryAggregator | passesInFinalThird bez isHome (final third gości źle liczony)               |
| 1.4 | MEDIUM  | FullMatchAnalytics | xT i OBSO na deprecated baseZoneThreat (brak perspektywy drużyny)           |
| 1.5 | LOW     | AdvancedAnalytics | obsByZone tylko (Int => Double)                                            |
| 1.6 | MEDIUM  | FullMatchEngineSpec | Test Nash penalty zbyt słaby (dowolny gol zamiast karnego)                 |
| 1.7 | LOW     | FullMatchEngine | pickWeighted przy pustej liście – zabezpieczenie                            |
| 5.1 | LOW     | FullMatchEngineSpec | Test ACWR/Injury nie weryfikuje usunięcia gracza po Injury                  |
| 5.2 | LOW     | AdvancedAnalyticsSpec | Testy na deprecated baseZoneThreat                                          |
| 6.1 | LOW     | build.sbt     | Rozszerzyć scalacOptions (unused, lint)                                    |

Pozostałe przejrzane elementy (playMatchday, connUnit, pressConference, rejestracja, token w Main, PitchView, MatchSquadPage, FormationPresets, API, baza) są zgodne z oczekiwaniami lub z lekcjami z `cursor.md`.

---

## 8. Rekomendowana kolejność napraw

1. **CRITICAL** – brak w tej rundzie (w razie wykrycia np. wycieku danych lub security – najpierw to).
2. **HIGH (w dowolnej kolejności):**
   - 1.1: Kontuzja – usuwanie gracza z boiska (sentOff lub injured + aktualizacja list).
   - 1.2: SimpleMatchEngine – DxT/crossy z perspektywą `isHome` (zoneThreat, isAttackingThird).
   - 1.3: MatchSummaryAggregator – passesInFinalThird z `isAttackingThird(zone, eventIsHome)`.
3. **MEDIUM:**
   - 1.4: FullMatchAnalytics – xT/OBSO z perspektywą (baseZoneThreat(_, isHome) lub dwa zestawy).
   - 1.6: FullMatchEngineSpec – zaostrzenie testu Nash (wymóg Penalty/Goal z karnego).
4. **LOW:**
   - 1.5: AdvancedAnalytics – obsByZone z opcją isHome.
   - 1.7: Zabezpieczenie pickWeighted przy pustej liście.
   - 5.1, 5.2: Uściślenie testów po zmianach w silniku/analityce.
   - 6.1: scalacOptions w build.sbt.

Po każdej istotnej zmianie (szczególnie 1.1, 1.2, 1.3) warto dopisać wpis do `cursor.md` (data, błąd, lekcja), zgodnie z zasadami projektu.
