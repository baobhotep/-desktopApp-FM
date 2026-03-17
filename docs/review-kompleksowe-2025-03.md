# Kompleksowe review projektu – marzec 2025

Review przeprowadzony z uwzględnieniem **cursor.md** (lekcje z poprzednich błędów). Poniżej ustalenia w kolejności severity i rekomendowana kolejność napraw.

---

## CRITICAL

*(Brak nowych ustaleń CRITICAL – wcześniejsze naprawy z cursor.md są wdrożone.)*

---

## HIGH

### 1. Kontuzja (Injury) nie usuwa gracza z boiska

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/FullMatchEngine.scala` ok. 703–705  
- **Opis:** Przy evencie "Injury" zapisujemy zdarzenie i aktualizujemy `lastEventType`, ale **nie** usuwamy kontuzjowanego gracza z `homePlayerIds`/`awayPlayerIds`. Gracz dalej bierze udział w symulacji (wybór aktora, pozycje, pitch control). W realnym FM kontuzjowany zawodnik schodzi z boiska (zmiana lub gra w 10).  
- **Sugerowana poprawka:** Analogicznie do czerwonej kartki: dodać np. `injuredThisMatch: Set[PlayerId]` w state (albo użyć wspólnego mechanizmu z `sentOff`), przy Injury usuwać gracza z listy na boisku i ewentualnie wymusić zmianę, jeśli są jeszcze dostępne.  
- **Kontekst:** Zgodne z wpisem w cursor.md – czerwona usuwała gracza; kontuzja nadal nie.

---

### 2. FullMatchAnalytics – DribbleLost: pressing w połowie przeciwnika złą perspektywą

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/FullMatchAnalytics.scala` ok. 101  
- **Opis:** Dla `DribbleLost` `e.secondaryPlayerId` to gracz wygrywający (obrońca). `pressingInOppHalf` powinien być liczony z perspektywy **tego** gracza: czy strefa jest w połowie przeciwnika względem jego drużyny. Obecnie używane jest `PitchModel.isOpponentHalf(zone, !eventIsHome)`. Dla wygrywającego (secondary) drużyna to `e.teamId`, więc `eventIsHome` już oznacza „drużyna wygrywającego = gospodarze”. Strefa w połowie przeciwnika dla gospodarzy to `isOpponentHalf(zone, true)`, czyli `isOpponentHalf(zone, eventIsHome)`. Użycie `!eventIsHome` odwraca perspektywę.  
- **Sugerowana poprawka:** Zamienić na `PitchModel.isOpponentHalf(zone, eventIsHome)`.  
- **Kontekst FP/sport:** Spójność definicji „pressing w połowie przeciwnika” z drużyną wykonującą akcję (w tym przypadku obrońcą wygrywającym piłkę).

---

## MEDIUM

### 3. SimpleMatchEngine – Cross zawsze w „atakującej trzeciej” gospodarzy

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/SimpleMatchEngine.scala` ok. 269  
- **Opis:** `attackZones` budowane jest jako `(1 to PitchModel.TotalZones).filter(PitchModel.isAttackingThird).toArray` – używana jest 1-argumentowa wersja `isAttackingThird(zone)` (kolumny ≥ 4, perspektywa gospodarzy). Dla **gości** dośrodkowanie powinno lądować w **ich** atakującej trzeciej (kolumny 0–1). Dla gości piłka ląduje więc w strefie obrony gospodarzy, a nie ataku gości.  
- **Sugerowana poprawka:** Użyć wersji z perspektywą: `(1 to PitchModel.TotalZones).filter(z => PitchModel.isAttackingThird(z, tid == input.homeTeam.teamId)).toArray`.  
- **Kontekst:** Zgodne z lekcją z cursor.md – funkcje zależne od kierunku ataku muszą być perspektywne (isHome / possessionHome).

---

### 4. playMatchday – filtrowanie meczów do rozegrania zbyt szerokie

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/service/LeagueService.scala` ok. 425  
- **Opis:** `toPlay = matches.filter(_.status != MatchStatus.Played)` obejmuje każdy status inny niż Played (np. gdyby był `Cancelled` lub `Postponed`, też trafiłyby do rozgrywania).  
- **Sugerowana poprawka:** Zawęzić do meczów faktycznie zaplanowanych: `toPlay = matches.filter(_.status == MatchStatus.Scheduled)`.  
- **Kontekst:** Jawna intencja – rozgrywamy tylko mecze w statusie Scheduled.

---

### 5. H2H – rozjazd limitu między API a serwisem

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/api/Routes.scala` ok. 318, `LeagueService.scala` ok. 1711  
- **Opis:** W Routes limit jest kapowany do 50: `.min(50)`. W LeagueService wywołanie jest z `math.min(limit, 20)`. Klient może przekazać np. 50, a faktycznie dostanie max 20 wyników bez informacji o obcięciu.  
- **Sugerowana poprawka:** Albo w Routes ustawić max 20 (spójnie z serwisem), albo w serwisie honorować limit do 50 i zdokumentować to w API; w obu warstwach jedna, spójna wartość maksymalna.

---

### 6. Transactor na ExecutionContext.global

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/Main.scala` ok. 44  
- **Opis:** Doobie Transactor używa `scala.concurrent.ExecutionContext.global` do operacji JDBC. Blokujące wywołania JDBC konkurują z innymi zadaniami na tym samym pulu (np. obsługa HTTP), co przy obciążeniu może pogorszyć latency.  
- **Sugerowana poprawka:** Wydzielić dedykowany EC dla JDBC (np. fixed thread pool o rozmiarze zbliżonym do Hikari max pool) i przekazać go do `Transactor.fromDataSource`.  
- **Kontekst FP:** Izolacja efektów blokujących od puli obliczeniowej.

---

### 7. traverseConn – zbędne zapytanie w przypadku bazowym

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/service/LeagueService.scala` ok. 117–118  
- **Opis:** W `foldRight` przypadek bazowy to `sql"SELECT 1".query[Int].unique.map(_ => List.empty[B])`. Przy pustej liście (i przy każdej „warstwie” rekurencji) wykonywane jest prawdziwe zapytanie do bazy.  
- **Sugerowana poprawka:** Bazę rekurencji zdefiniować jako `cats.Applicative[ConnectionIO].pure(List.empty[B])`.  
- **Kontekst:** Zgodne z lekcją z cursor.md – unikać zbędnego `SELECT 1`; no-op w ConnectionIO to `pure(())` / `pure(Nil)`.

---

### 8. Test „GK strength” – zbyt duża tolerancja

- **Lokalizacja:** `backend/src/test/scala/fmgame/backend/engine/FullMatchEngineSpec.scala` ok. 181–184  
- **Opis:** Asercja `goalsVsStrong <= goalsVsWeak + 3` przy 3 seedach pozwala, by silny bramkarz (18/18) stracił nawet 3 gole więcej niż słaby (5/5). Test może przechodzić mimo zerowego wpływu atrybutów bramkarza.  
- **Sugerowana poprawka:** Zwiększyć liczbę seedów (np. 10+) i zaostrzyć warunek (np. `goalsVsStrong <= goalsVsWeak + 1` lub `goalsVsStrong <= goalsVsWeak`), ewentualnie testować istotność statystycznie.

---

## LOW

### 9. ApiClient – brak encodeURIComponent przy teamId w press-conference

- **Lokalizacja:** `frontend/src/main/scala/app/ApiClient.scala` ok. 308  
- **Opis:** URL: `s"/matches/$matchId/press-conference?teamId=$teamId"` – `teamId` (UUID) wstawiany jest bez kodowania. Dla UUID zwykle nie stanowi to problemu, ale niespójne z innymi endpointami (np. assistant-tip, h2h, compare-players), gdzie używane jest `encodeURIComponent`.  
- **Sugerowana poprawka:** Dla spójności i bezpieczeństwa: `teamId=${scala.scalajs.js.URIUtils.encodeURIComponent(teamId)}`.

---

### 10. Stała klucza JWT w wielu miejscach

- **Lokalizacja:** `frontend/src/main/scala/app/Main.scala` (TokenKey), `DashboardPage.scala` ok. 39, `LoginPage.scala` ok. 22  
- **Opis:** W Main jest `private val TokenKey = "fm-game-jwt"`, a w DashboardPage i LoginPage literał `"fm-game-jwt"` jest wpisany na sztywno. Zmiana klucza w Main nie zaktualizuje wylogowania ani zapisu tokenu.  
- **Sugerowana poprawka:** Wyeksportować stałą (np. z AppState lub osobnego obiektu `AuthConstants`) i używać jej wszędzie (Main, DashboardPage, LoginPage).

---

### 11. OBSO / baseZoneThreat – nadal używana wersja bez perspektywy

- **Lokalizacja:** `backend/src/main/scala/fmgame/backend/engine/AdvancedAnalytics.scala` (obsByZone), wywołania z `FullMatchAnalytics` przekazujące `DxT.baseZoneThreat` (deprecated).  
- **Opis:** `obsByZone(baseZoneThreat)` przyjmuje `Int => Double`; w FullMatchAnalytics przekazywany jest przestarzały `DxT.baseZoneThreat` bez perspektywy. Dla spójności z DxT i lekcjami z cursor.md warto przejść na wersję z `possessionHome` (np. OBSO liczone per drużyna lub z jawną perspektywą).  
- **Sugerowana poprawka:** Rozszerzyć sygnaturę/obliczenie OBSO o perspektywę i użyć `DxT.baseZoneThreat(zone, possessionHome)`; ewentualnie usunąć użycie deprecated `baseZoneThreat(zone)`.

---

## Podsumowanie tabelaryczne

| # | Severity   | Obszar              | Krótki opis                                              |
|---|------------|---------------------|----------------------------------------------------------|
| 1 | HIGH       | FullMatchEngine     | Injury nie usuwa gracza z boiska                         |
| 2 | HIGH       | FullMatchAnalytics  | DribbleLost pressing – zła perspektywa isOpponentHalf   |
| 3 | MEDIUM     | SimpleMatchEngine   | Cross – strefy ataku zawsze z perspektywy gospodarzy     |
| 4 | MEDIUM     | LeagueService       | toPlay: użyć status == Scheduled                         |
| 5 | MEDIUM     | Routes + LeagueService | H2H limit: spójność 50 vs 20                          |
| 6 | MEDIUM     | Main                | Transactor: dedykowany EC dla JDBC                       |
| 7 | MEDIUM     | LeagueService       | traverseConn: baza foldRight bez SELECT 1                |
| 8 | MEDIUM     | FullMatchEngineSpec | Test GK strength – za słaba asercja                      |
| 9 | LOW        | ApiClient           | press-conference: encodeURIComponent(teamId)             |
| 10| LOW        | Frontend            | Wspólna stała dla klucza JWT (Main, Dashboard, Login)    |
| 11| LOW        | AdvancedAnalytics   | OBSO / baseZoneThreat z perspektywą                     |

---

## Rekomendowana kolejność napraw

1. **HIGH:** Injury – usunięcie kontuzjowanego z listy na boisku (oraz ewent. wymuszenie zmiany).  
2. **HIGH:** FullMatchAnalytics – poprawka `isOpponentHalf(zone, eventIsHome)` przy DribbleLost.  
3. **MEDIUM:** SimpleMatchEngine – Cross z `isAttackingThird(z, tid == input.homeTeam.teamId)`.  
4. **MEDIUM:** LeagueService – `toPlay = matches.filter(_.status == MatchStatus.Scheduled)`.  
5. **MEDIUM:** H2H – uzgodnienie limitu (50 vs 20) w Routes i LeagueService.  
6. **MEDIUM:** Main – dedykowany ExecutionContext dla Transactor.  
7. **MEDIUM:** LeagueService – `traverseConn` z `pure(List.empty[B])` w przypadku bazowym.  
8. **MEDIUM:** FullMatchEngineSpec – zaostrzenie testu GK (więcej seedów, węższa tolerancja).  
9. **LOW:** ApiClient – encodeURIComponent dla teamId; frontend – wspólna stała JWT; OBSO/baseZoneThreat z perspektywą.

Po wdrożeniu zmian warto dopisać do **cursor.md** nowe lekcje (np. Injury, perspektywa przy Cross/Pressing, spójność limitów API).
