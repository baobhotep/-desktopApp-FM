# Dogłębne review techniczne projektu (backend + frontend + testy)

Data: 2025-03-17. Uwzględniono lekcje z `cursor.md`.

---

## 1. Backend – silnik i analityka

### 1.1 CRITICAL: xT / OBSO używają deprecated `baseZoneThreat(zone)` – perspektywa tylko gospodarzy

**Lokalizacja:**  
- `backend/src/main/scala/fmgame/backend/engine/FullMatchAnalytics.scala` ok. 120–121  
- `AdvancedAnalytics.xTValueIteration` i `obsByZone` przyjmują `Int => Double` (jednoargumentowy `baseZoneThreat`).

**Opis:**  
`DxT.baseZoneThreat(zone)` (deprecated) używa tylko `column(zone)` – czyli perspektywy „atak w prawo” (gospodarze). W analityce po meczu xT i OBSO są liczone dla wszystkich zdarzeń (gospodarze + goście) tą samą mapą. Dla gości strefy ataku (kolumny 0–1 w ich kierunku) mają zaniżone zagrożenie, a „obrona” (kolumny 4–5) – zawyżone. To ten sam typ błędu co w wpisie cursor.md (DxT / threat map dla drużyny wyjazdowej).

**Sugerowana poprawka:**  
- Dla xT: albo jedna mapa „neutralna” (np. zawsze perspektywa gospodarzy) z jasną dokumentacją, albo dwie mapy (home/away) i przy agregacji (np. xPassValueFromEvents) używać `baseZoneThreat(zone, isHome(teamId))`.  
- W `FullMatchAnalytics.computeAnalyticsFromEvents`:  
  - Albo zmienić sygnatury `xTValueIteration` / `obsByZone` tak, aby przyjmowały `(Int, Boolean) => Double` (strefa + czy drużyna atakuje w prawo) i budować xT/OBSO per perspektywa;  
  - Albo tymczasowo przekazywać `z => DxT.baseZoneThreat(z, true)` i w dokumentacji zaznaczyć, że xT/OBSO są w perspektywie gospodarzy; wtedy xPass dla gości nadal będzie błędny (patrz punkt 1.2).  
- Usunąć lub ograniczyć użycie deprecated `baseZoneThreat(zone)` w całym projekcie.

**Kontekst:**  
W realnej analityce xT wartość strefy zależy od kierunku ataku; jedna mapa bez perspektywy zniekształca statystyki gości.

---

### 1.2 HIGH: xPassValueFromEvents – jedna mapa xT dla obu drużyn

**Lokalizacja:**  
`AdvancedAnalytics.xPassValueFromEvents` (użycie `xtValueByZone`), wywołane z `FullMatchAnalytics` z jedną mapą xT.

**Opis:**  
Wartość podania to xT(strefa docelowa) − xT(strefa źródłowa). Jeśli `xtValueByZone` jest w perspektywie gospodarzy (kolumna ↑ = wyższe xT), to dla gości podanie „do przodu” (w stronę bramki przeciwnika) ma malejące kolumny – czyli używana mapa daje odwrotną wartość. xPass dla drużyny wyjazdowej jest wtedy systematycznie błędny.

**Sugerowana poprawka:**  
- W `xPassValueFromEvents` dla każdego zdarzenia określić `isHome = e.teamId.contains(homeTeamId)` i używać wartości strefy z perspektywy tej drużyny.  
- To wymaga albo dwóch map xT (home/away), albo jednej mapy „względem bramki przeciwnika” i funkcji `(zone, isHome) => threat`.  
- Spójnie z poprawką 1.1: ujednolicić perspektywę (strefa + kierunek ataku) w DxT/xT i xPass.

---

### 1.3 HIGH: SimpleMatchEngine – strefy ataku i threat tylko w perspektywie gospodarzy

**Lokalizacja:**  
- `backend/src/main/scala/fmgame/backend/engine/SimpleMatchEngine.scala` ok. 158, 269.

**Opis:**  
- `zoneThreat(zone)` wywołuje `DxT.baseZoneThreat(zone)` (1 arg) – dla gości „wysoka” threat jest przy ich bramce.  
- `PitchModel.isAttackingThird` w linii 269 jest wywołane z jednym argumentem (zone). Używane do wyboru stref dla Cross: `(1 to TotalZones).filter(PitchModel.isAttackingThird)`. Dla gości to kolumny 4–5 (ich tercja obronna), więc crossy gości są losowane do stref przy własnej bramce.

**Sugerowana poprawka:**  
- Dla threat: użyć `DxT.baseZoneThreat(zone, possessionHome)` (lub odpowiednika z informacją, która drużyna atakuje); tam gdzie jest `tid`, `possessionHome = (tid == homeTeamId)`.  
- Dla Cross (i ewentualnie innych akcji „w tercji ataku”): przekazać perspektywę drużyny, np. `PitchModel.isAttackingThird(z, tid == input.homeTeam.teamId)` (albo osobna zmienna `isHome` przy generowaniu zdarzeń gości).

**Kontekst:**  
Zgodne z lekcją z cursor.md: wszystkie funkcje zależne od kierunku ataku muszą brać pod uwagę perspektywę posiadania / drużyny.

---

### 1.4 HIGH: MatchSummaryAggregator – „passes in final third” w jednej perspektywie

**Lokalizacja:**  
`backend/src/main/scala/fmgame/backend/service/MatchSummaryAggregator.scala` ok. 77.

**Opis:**  
`PitchModel.isAttackingThird(zone)` (jednoargumentowe) = `column(zone) >= 4`. Dla gospodarzy to tercja ataku; dla gości kolumny 4–5 to ich tercja obronna. W efekcie podania gości we własnej połowie są liczone jako „passes in final third” gości.

**Sugerowana poprawka:**  
Zastąpić wywołanie przez `PitchModel.isAttackingThird(zone, isHome(tid))`, np.:

```scala
if (zone >= 1 && fmgame.backend.engine.PitchModel.isAttackingThird(zone, isHome(tid))) {
  if (isHome(tid)) passesInFinalThirdH += 1 else passesInFinalThirdA += 1
}
```

**Kontekst:**  
Statystyki „passes in final third” w ligach są zawsze w perspektywie drużyny (jej tercja ataku).

---

### 1.5 MEDIUM: AdvancedAnalytics – xTValueIteration / obsByZone bez perspektywy

**Lokalizacja:**  
`backend/src/main/scala/fmgame/backend/engine/AdvancedAnalytics.scala` – sygnatury `xTValueIteration(..., baseZoneThreat: Int => Double)` i `obsByZone(baseZoneThreat: Int => Double)`.

**Opis:**  
API nie przyjmuje informacji o drużynie / kierunku ataku. Wszystkie wywołania z `DxT.baseZoneThreat` (1 arg) utrwalają perspektywę gospodarzy i uniemożliwiają poprawne xT/OBSO dla gości (oraz spójny xPass).

**Sugerowana poprawka:**  
- Długoterminowo: dodać wersje przyjmujące `(Int, Boolean) => Double` (strefa, isHome) i w FullMatchAnalytics budować np. dwie mapy xT lub jedną „neutralną” z jasną semantyką.  
- Krótkoterminowo: w testach i w FullMatchAnalytics jawnie przekazywać `z => DxT.baseZoneThreat(z, true)` i w komentarzach oznaczyć „home perspective only”.

---

### 1.6 MEDIUM: PropertyBasedSpec – test DxT używa deprecated API

**Lokalizacja:**  
`backend/src/test/scala/fmgame/backend/engine/PropertyBasedSpec.scala` ok. 172–174.

**Opis:**  
Test „base zone threat increases with column” używa `DxT.baseZoneThreat(z1)` (1 arg). To testuje zachowanie deprecated: threat rośnie z kolumną (perspektywa gospodarzy). Po usunięciu deprecated API test się nie skompiluje.

**Sugerowana poprawka:**  
Testować wersję z perspektywą, np. `DxT.baseZoneThreat(z, true)` i (opcjonalnie) symetrię `baseZoneThreat(z, true)` vs `baseZoneThreat(mirrorZone, false)`.

---

### 1.7 LOW: AdvancedAnalyticsSpec – xTValueIteration z deprecated baseZoneThreat

**Lokalizacja:**  
`backend/src/test/scala/fmgame/backend/engine/AdvancedAnalyticsSpec.scala` ok. 25, 39.

**Opis:**  
`AdvancedAnalytics.xTValueIteration(counts, DxT.baseZoneThreat)` i `obsByZone(DxT.baseZoneThreat)` – kompilator wybiera deprecated overload. Asercja w tym samym teście używa już `DxT.baseZoneThreat(..., true)`.

**Sugerowana poprawka:**  
Jawnie przekazać funkcję z perspektywą, np. `z => DxT.baseZoneThreat(z, true)`, żeby test był spójny z docelowym API.

---

## 2. Backend – serwisy i repozytoria

### 2.1 LOW: traverseConn – zbędne zapytanie przy pustej liście

**Lokalizacja:**  
`backend/src/main/scala/fmgame/backend/service/LeagueService.scala` ok. 117–118.

**Opis:**  
`traverseConn` w base case używa `sql"SELECT 1".query[Int].unique.map(_ => List.empty[B])`. Przy każdej rekurencji (w tym dla pustej listy) wykonywane jest zapytanie do bazy. Zgodnie z cursor.md: dla „no-op” w ConnectionIO lepiej używać `Applicative.pure`, nie prawdziwego zapytania.

**Sugerowana poprawka:**  

```scala
private def traverseConn[A, B](as: List[A])(f: A => ConnectionIO[B]): ConnectionIO[List[B]] =
  as.foldRight(cats.Applicative[ConnectionIO].pure(List.empty[B]))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))
```

---

### 2.2 Sprawdzone pozytywnie (cursor.md)

- **playMatchday** – serializacja per liga przez `Semaphore` i `putIfAbsent` (LeagueService) – OK.  
- **pressConferenceGiven** – użycie `putIfAbsent` do atomowej blokady – OK.  
- **connUnit** – zdefiniowane jako `Applicative.pure(())` – OK.  
- **UserService register** – `userRepo.create(...).mapError(_ => "Email already registered")` – OK.  
- **FixtureGenerator vs matchdayForDate** – ta sama formuła `weekOffset + midWeek` (Wed/Sat) – OK.

---

## 3. Backend – API i baza

### 3.1 MEDIUM: Brak rate limitingu na endpointach

**Lokalizacja:**  
`backend/src/main/scala/fmgame/backend/api/Routes.scala` – brak middleware limitującego liczbę requestów.

**Opis:**  
Brak ograniczenia liczby żądań ułatwia nadużycia (np. brute-force na login, przeciążenie symulacji meczów).

**Sugerowana poprawka:**  
Dodać middleware (np. ZIO / zio-http) z limitem per IP lub per user (po JWT) dla `/auth/login`, `/api/v1/...`, ewentualnie osobne limity dla „ciężkich” endpointów (np. play matchday). W dokumentacji opisać limity.

---

### 3.2 LOW: CORS – allowedOrigin

**Lokalizacja:**  
Routes – `allowedOrigin: Option[String]`.

**Opis:**  
Trzeba upewnić się, że w produkcji nie używane jest `*` dla credentials (jeśli front wysyła cookies/Authorization). Sprawdzić konfigurację CORS w Main / konfiguracji.

---

### 3.3 Baza i migracje

- Indeksy (matches, teams, invitations, transfer_offers, league_player_match_stats) – sensowne.  
- Migracje ALTER TABLE z `.catchAll` – bezpieczne.  
- Brak jawnego connection pool (Hikari) w fragmentach, które czytałem – warto upewnić się, że transaktor używa poolu w produkcji.

---

## 4. Frontend

### 4.1 Sprawdzone pozytywnie (cursor.md)

- **Token** – w `Main.scala` token ustawiany dopiero po pomyślnej odpowiedzi `me()`; przy błędzie czyścimy storage i ustawiamy Login – OK.  
- **PitchView** – aktualizacja `positions` (Var) tylko w `commitDragPosition()` (mouseUp/mouseLeave); podczas dragu tylko style przez `nodeRefs` – OK.  
- **FormationPresets** – 4-3-3 ma 4 obrońców (LB, LCB, RCB, RB), 3 w pomocy, 3 w ataku; `DefaultPositions433` – 11 różnych pozycji – OK.

---

### 4.2 LOW: MatchSquadPage – wspólna lista `<option>` dla wielu `<select>`

**Lokalizacja:**  
Zgodnie z cursor.md – `MatchSquadPage`: jedna tablica `opts` przekazywana do kilku `<select>`.

**Rekomendacja:**  
Jeśli w kodzie nadal jest jedna wspólna lista elementów `<option>` dla wielu selectów, każdy select powinien budować własną listę (np. `mkOpts()` wywoływane per select). Warto zweryfikować aktualny stan pliku.

---

## 5. Testy

### 5.1 Sprawdzone

- FullMatchEngineSpec – sprawdza typy zdarzeń, liczbę goli, kolejność minut, KickOff, skład, xG w metadata – sensowne.  
- Test VAEP (Dribble attack vs build-up) używa `isAttackingThird(zone)` i `isBuildUpZone(zone)` w kontekście home (ctx z `isHome = true`) – spójne.  
- AdvancedAnalyticsSpec – transitionCounts, clustering, Nash, Poisson, tortuosity, xPass – wartościowe; tylko kwestia deprecated baseZoneThreat (patrz 1.7).

### 5.2 Brak jawnego testu „Nash penalty”

**Opis:**  
W cursor.md było: test „Nash penalty” akceptował dowolny mecz z golem zamiast wymagać eventu „Penalty”. W FullMatchEngineSpec nie ma dedykowanego testu, który uruchamia symulację z faulem w polu karnym i weryfikuje wystąpienie eventu „Penalty” oraz (opcjonalnie) meta z Nash. Warto dodać test: seed lub warunek prowadzący do karnego, potem `assertTrue(result.events.exists(_.eventType == "Penalty"))` oraz sprawdzenie meta (np. `penalty` / xG).

---

## 6. Konfiguracja (build.sbt)

- Scala 3.3.3, ZIO 2.0.21, Doobie 1.0.0-RC5, zio-http 3.0.0-RC6, Circe, Laminar – spójne.  
- `scalacOptions`: `-deprecation`, `-feature`, `-unchecked` – dobre; brak `-Wunused` / `-Xlint` – opcjonalnie do rozważenia.  
- Zależności testowe (zio-test, zio-http-testkit) – OK.

---

## 7. Podsumowanie – tabela ustaleń

| Severity   | Lokalizacja / temat | Krótki opis |
|-----------|---------------------|-------------|
| CRITICAL  | FullMatchAnalytics, xT/OBSO | xT i OBSO używają deprecated `baseZoneThreat(zone)` – tylko perspektywa gospodarzy |
| HIGH      | xPassValueFromEvents | Jedna mapa xT dla obu drużyn – xPass gości błędny |
| HIGH      | SimpleMatchEngine   | zoneThreat i isAttackingThird bez perspektywy (crossy gości w złych strefach) |
| HIGH      | MatchSummaryAggregator | passes in final third: isAttackingThird(zone) bez isHome |
| MEDIUM    | AdvancedAnalytics API | xTValueIteration / obsByZone bez parametru perspektywy |
| MEDIUM    | PropertyBasedSpec   | Test DxT oparty o deprecated baseZoneThreat(zone) |
| MEDIUM    | Routes             | Brak rate limitingu na API |
| LOW       | LeagueService      | traverseConn – SELECT 1 zamiast Applicative.pure w base case |
| LOW       | AdvancedAnalyticsSpec | Testy xT/obs z deprecated overload |
| LOW       | CORS / production  | Zweryfikować CORS (np. brak * z credentials) |
| LOW       | MatchSquadPage     | Upewnić się, że każdy &lt;select&gt; ma własną listę &lt;option&gt; |
| INFO      | FullMatchEngineSpec | Opcjonalny test: Penalty event + meta (Nash) po faulu w polu karnym |

---

## 8. Rekomendowana kolejność napraw

1. **CRITICAL:** FullMatchAnalytics + AdvancedAnalytics – wprowadzić perspektywę (isHome) w xT i OBSO oraz w wywołaniach z FullMatchAnalytics; przestać używać deprecated `baseZoneThreat(zone)` w ścieżce analityki.
2. **HIGH:** MatchSummaryAggregator – poprawka `isAttackingThird(zone, isHome(tid))` dla passes in final third.
3. **HIGH:** SimpleMatchEngine – zoneThreat i strefy ataku (Cross) z perspektywą drużyny.
4. **HIGH:** xPassValueFromEvents – wartości xT per drużyna (perspektywa home/away).
5. **MEDIUM:** Rate limiting na API; dopracowanie testów DxT (PropertyBasedSpec, AdvancedAnalyticsSpec) do nowego API.
6. **LOW:** traverseConn base case; CORS; MatchSquadPage selecty; ewentualny test Penalty w FullMatchEngineSpec.

Po każdej istotnej zmianie (szczególnie w silniku i analityce) warto dodać wpis do `cursor.md` (błąd + lekcja), żeby uniknąć regresji w perspektywie stref i threat.
