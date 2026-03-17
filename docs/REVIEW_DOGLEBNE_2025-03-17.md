# Dogłębne review projektu – Scala 3, FP, analityka piłkarska, FM

**Data:** 2025-03-17  
**Zakres:** backend (silnik, analityka, serwisy, API, baza), frontend (Laminar), testy, konfiguracja.  
**Uwzględniono:** plik `cursor.md` (lekcje z poprzednich błędów).

---

## 1. Backend – silnik i analityka

### 1.1 SimpleMatchEngine – zoneThreat / Cross bez perspektywy drużyny

- **Severity:** HIGH
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/SimpleMatchEngine.scala`, linie 161, 258–259
- **Opis:**  
  - `zoneThreat(zone)` wywołuje `DxT.baseZoneThreat(zone)` (przestarzały 1-arg). Dla gości strefa np. 2 (obrona gospodarzy) to ich tercja ataku – zagrożenie powinno być wysokie, a dostają niskie (kolumna 2).  
  - W `buildEvents` przy Cross: `attackZones = (1 to PitchModel.TotalZones).filter(PitchModel.isAttackingThird).toArray` – zawsze tercja ataku gospodarzy (kolumny 4–5). Gdy cross wykonuje drużyna wyjazdowa, piłka trafia w „ich” tercję ataku (kolumny 0–1), a nie w strefy 4–5.
- **Sugerowana poprawka:**  
  - Dla `zoneThreat`: przyjmować `isHome: Boolean` (np. z kontekstu zdarzenia) i używać `DxT.baseZoneThreat(zone, isHome)`.  
  - Dla Cross: przy generowaniu zdarzenia mieć `tid`; `attackZones = (1 to PitchModel.TotalZones).filter(z => PitchModel.isAttackingThird(z, tid == input.homeTeam.teamId)).toArray`.
- **Kontekst:** Zgodnie z `cursor.md` (DxT/threat) wszystkie funkcje zależne od kierunku ataku muszą brać perspektywę posiadania piłki (`possessionHome` / `isHome`).

---

### 1.2 FullMatchAnalytics – xT i OBSO na przestarzałym baseZoneThreat

- **Severity:** MEDIUM
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/FullMatchAnalytics.scala`, linie 121–122
- **Opis:**  
  `AdvancedAnalytics.xTValueIteration(transitionCounts, DxT.baseZoneThreat)` i `AdvancedAnalytics.obsByZone(DxT.baseZoneThreat)` używają sygnatury `Int => Double` (deprecated). W efekcie xT i OBSO są liczone w jednej perspektywie (atak gospodarzy). Dla mieszanych przejść (obie drużyny) jedna mapa jest akceptowalna jako „kanoniczna”, ale API powinno być spójne z `baseZoneThreat(zone, possessionHome)`.
- **Sugerowana poprawka:**  
  - Dla spójności: przekazać `z => DxT.baseZoneThreat(z, true)` (np. „home attack” jako kanon) i w komentarzach/doc uściślić, że xT/OBSO są w perspektywie gospodarzy.  
  - Albo rozszerzyć value iteration o dwie mapy (home/away) – większa zmiana.
- **Kontekst:** FP – używanie jawnego parametru zamiast deprecated overloadu.

---

### 1.3 MatchSummaryAggregator – passes in final third (perspektywa strefy)

- **Severity:** MEDIUM
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/service/MatchSummaryAggregator.scala`, linia 75
- **Opis:**  
  `PitchModel.isAttackingThird(zone)` bez `isHome` – zawsze kolumny 4–5. Dla drużyny wyjazdowej „passes in final third” powinny dotyczyć stref 1–2 (ich atak), a nie 4–5.
- **Sugerowana poprawka:**  
  `if (zone >= 1 && fmgame.backend.engine.PitchModel.isAttackingThird(zone, isHome(tid))) { if (isHome(tid)) passesInFinalThirdH += 1 else ... passesInFinalThirdA += 1 }`
- **Kontekst:** Spójność z resztą kodu (PitchModel z parametrem `isHome`).

---

### 1.4 LeagueService – traverseConn wykonuje zbędne zapytanie

- **Severity:** LOW
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/service/LeagueService.scala`, linie 117–118
- **Opis:**  
  `traverseConn` w `foldRight` używa `sql"SELECT 1".query[Int].unique.map(_ => List.empty[B])` jako wartości początkowej. Każde wywołanie `traverseConn` wykonuje więc zbędne zapytanie tylko po to, żeby uzyskać `ConnectionIO[List[B]]` z pustą listą.
- **Sugerowana poprawka:**  
  Zamienić na `as.foldRight(cats.Applicative[ConnectionIO].pure(List.empty[B]))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))`.
- **Kontekst:** Zgodnie z `cursor.md` (connUnit) – unikać prawdziwego zapytania tam, gdzie wystarczy `pure`.

---

### 1.5 FullMatchEngineSpec – test Nash penalty i VAEP override

- **Severity:** LOW (jakościowy)
- **Lokalizacja:** `backend/src/test/scala/fmgame/backend/engine/FullMatchEngineSpec.scala`, linie 357–364, 386–396
- **Opis:**  
  - Test „Nash penalty” wymaga tylko `hasGoalOrPenalty` (goal lub penalty); nie weryfikuje, że przy karnej używany jest model Nash (np. metadane `penalty -> true`, rozkład L/R).  
  - Test „vaepModelOverride changes VAEP”: asercja `math.abs((hOv + aOv) - (hDef + aDef)) > 0.001` jest sensowna (różnica sum), ale warto dodać, że przy tym samym seedzie override faktycznie zmienia wartości w oczekiwanym kierunku (np. wyższy weight na Goal → wyższa suma VAEP przy golach).
- **Sugerowana poprawka:**  
  - W teście Nash: np. wybrać seedy znane z tego, że generują faul w polu karnym, albo sprawdzić, że istnieje event typu `Penalty` lub `Goal` z `metadata("penalty") == "true"`.  
  - Dla VAEP: opcjonalnie asercja na kierunek zmiany (np. wyższy weight Goal → suma VAEP nie niższa).
- **Kontekst:** Zgodnie z `cursor.md` (testy false positive) – testy muszą weryfikować to, co deklarują.

---

### 1.6 AdvancedAnalyticsSpec – użycie deprecated DxT.baseZoneThreat

- **Severity:** LOW
- **Lokalizacja:** `backend/src/test/scala/fmgame/backend/engine/AdvancedAnalyticsSpec.scala`, linie 24, 37
- **Opis:**  
  Testy wywołują `DxT.baseZoneThreat` w wersji 1-arg (deprecated). Dla spójności z kodem produkcyjnym lepiej użyć wersji z perspektywą.
- **Sugerowana poprawka:**  
  Np. `DxT.baseZoneThreat(_, true)` (perspektywa gospodarzy) w `xTValueIteration` i `obsByZone` w testach.

---

## 2. Backend – API i baza

### 2.1 Brak jawnego limitu rate / timeoutów

- **Severity:** LOW
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/api/Routes.scala`, całość
- **Opis:**  
  Nie widać middleware’u limitu zapytań (rate limit) ani globalnych timeoutów na request. W produkcji może to ułatwić nadużycia lub przeciążenie.
- **Sugerowana poprawka:**  
  Dodać (np. w Main / ServerConfig) opcjonalny rate limiter i timeout na handler (np. ZIO HTTP / middleware). W pierwszej iteracji wystarczy dokumentacja i konfiguracja timeoutu.

---

### 2.2 AppError.fromServiceError – dopasowanie „Email already registered”

- **Severity:** LOW
- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/domain/AppError.scala`, linie 32–38
- **Opis:**  
  `fromServiceError` mapuje „already” / „duplicate” na `Conflict`. Komunikat z UserService to „Email already registered” – zostanie poprawnie zmapowany na Conflict. Warto upewnić się, że wszystkie błędy rejestracji (np. UNIQUE w DB) są przekładane na ten sam komunikat w serwisie (obecnie jest – `mapError` w UserService).
- **Sugerowana poprawka:**  
  Brak konieczności – tylko świadomość: przy dodawaniu nowych błędów DB (np. inne UNIQUE) w UserService zwracać ten sam komunikat lub dodać go do wzorca w `fromServiceError`.

---

## 3. Frontend

### 3.1 Main.scala – kolejność token / me()

- **Severity:** (już poprawione)
- **Lokalizacja:** `frontend/src/main/scala/app/Main.scala`, linie 17–27
- **Opis:**  
  Zgodnie z `cursor.md`: token jest ustawiany w state dopiero po pomyślnej odpowiedzi `me()`; przy błędzie czyścimy localStorage i ustawiamy Login. Obecna implementacja to robi (token.set(Some(savedToken)) w Right(user), a w Left – removeItem i Login). OK.

---

### 3.2 PitchView – commit tylko na mouseUp/mouseLeave

- **Severity:** (już poprawione)
- **Lokalizacja:** `frontend/src/main/scala/app/PitchView.scala`, linie 45–56, 64–65
- **Opis:**  
  Zgodnie z `cursor.md`: podczas dragu aktualizowane są tylko style przez `nodeRefs`; commit do `Var` (positions) w `commitDragPosition()` jest wywoływany w `mouseUp` i `mouseLeave`. OK.

---

### 3.3 FormationPresets – 4-3-3 i unikalność pozycji

- **Severity:** (już poprawione)
- **Lokalizacja:** `frontend/src/main/scala/app/FormationPresets.scala`
- **Opis:**  
  Zgodnie z `cursor.md`: 4-3-3 ma 4 obrońców, 3 pomocników, 3 napastników; `DefaultPositions433` nie ma dwóch identycznych par (x,y). Obecny stan jest poprawny.

---

## 4. Konfiguracja (build.sbt)

### 4.1 scalacOptions – brak -Wunused

- **Severity:** LOW
- **Lokalizacja:** `build.sbt`, linia 29
- **Opis:**  
  Są `-deprecation`, `-feature`, `-unchecked`. Brak ostrzeżeń o nieużywanych zmiennych/importach (`-Wunused:all` lub odpowiednik w Scala 3), co pomaga w utrzymaniu kodu.
- **Sugerowana poprawka:**  
  Dodać np. `-Wunused:all` (Scala 3) lub sprawdzić doc dla danej wersji; ewentualnie `-Xlint` jeśli dostępne.

---

### 4.2 Zależności – wersje

- **Severity:** INFORMATIONAL
- **Lokalizacja:** `build.sbt`
- **Opis:**  
  Scala 3.3.3, ZIO 2.0.21, Doobie 1.0.0-RC5, zio-http 3.0.0-RC6, Circe 0.14.6 – wersje spójne. Warto okresowo sprawdzać CVE i aktualizacje (szczególnie H2, PostgreSQL driver).

---

## 5. Inne (weryfikacja lekcji z cursor.md)

- **Czerwona kartka / sentOff / homePlayerIds–awayPlayerIds:** W FullMatchEngine przy effectiveCard == "Red" gracz jest usuwany z `homePlayerIds`/`awayPlayerIds` i dodawany do `sentOff`. OK.  
- **Żółte kartki (druga żółta):** `yellowCards: Set[PlayerId]` i konwersja na czerwoną przy drugiej żółtej są zaimplementowane. OK.  
- **DribbleLost teamId:** W FullMatchEngine w DribbleLost `eventTeamId = newPossession` (drużyna wygrywająca). W SimpleMatchEngine używane jest `Some(wonTid)`. OK.  
- **Gol a shots on target:** W MatchSummaryAggregator case "Goal" dodaje do `shotsOnTarget`. OK.  
- **Rejestracja – mapError na UNIQUE:** UserService przy `userRepo.create(user).transact(xa)` ma `.mapError(_ => "Email already registered")`. OK.  
- **playMatchday – semafor per liga:** `withMatchdayLock(leagueId)` z `putIfAbsent` na semafor. OK.  
- **pressConferenceGiven – putIfAbsent:** Użyte atomowo `putIfAbsent(pcKey, true)`; przy istniejącym kluczu zwracany błąd. OK.  
- **connUnit:** Zastąpione przez `cats.Applicative[ConnectionIO].pure(())`. OK.

---

## 6. Podsumowanie – tabela ustaleń

| # | Severity   | Obszar        | Krótki opis                                                                 |
|---|------------|---------------|-----------------------------------------------------------------------------|
| 1 | HIGH       | SimpleMatchEngine | zoneThreat i Cross bez perspektywy drużyny (DxT / isAttackingThird)      |
| 2 | MEDIUM     | FullMatchAnalytics | xT/OBSO używają deprecated baseZoneThreat (jedna perspektywa)           |
| 3 | MEDIUM     | MatchSummaryAggregator | passes in final third – isAttackingThird(zone, isHome(tid))            |
| 4 | LOW        | LeagueService | traverseConn – zamiast SELECT 1 użyć Applicative.pure(List.empty[B])     |
| 5 | LOW        | FullMatchEngineSpec | Test Nash penalty i VAEP override – silniejsza weryfikacja             |
| 6 | LOW        | AdvancedAnalyticsSpec | Testy na deprecated baseZoneThreat – przejście na 2-arg                |
| 7 | LOW        | API/Routes   | Brak rate limit / timeoutów (dokumentacja lub middleware)                 |
| 8 | LOW        | build.sbt    | Dodać -Wunused (lub odpowiednik) w scalacOptions                          |

---

## 7. Rekomendowana kolejność napraw

1. **HIGH:** SimpleMatchEngine – perspektywa drużyny w `zoneThreat` i w Cross (attackZones z `isAttackingThird(z, isHome)`).
2. **MEDIUM:** MatchSummaryAggregator – `isAttackingThird(zone, isHome(tid))` przy passes in final third.
3. **MEDIUM:** FullMatchAnalytics – jawne użycie `baseZoneThreat(_, true)` (lub udokumentowanie perspektywy) dla xT/OBSO.
4. **LOW:** LeagueService – `traverseConn` z `pure(List.empty[B])`.
5. **LOW:** Testy – Nash penalty (wymóg eventu Penalty / metadata), VAEP override (opcjonalnie kierunek), AdvancedAnalyticsSpec – baseZoneThreat 2-arg.
6. **LOW:** build.sbt – scalacOptions; API – rate limit / timeout (dokumentacja lub implementacja).

Po każdej istotnej zmianie warto dodać wpis do `cursor.md` (data, błąd, lekcja), zgodnie z zasadami projektu.
