# Przebieg meczu, algorytmy i możliwe dopracowania

**Uwaga (stan na 2026-03):** Stan algorytmów (co jest zintegrowane) jest aktualnie opisany w **ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md**. Tabela w §1.1 poniżej ma charakter historyczny; wiele pozycji oznaczonych „Nie” lub „Częściowo” jest już zaimplementowanych (xT value iteration, I-VAEP, OBV, gBRI/tortuosity, Voronoi, metabolic, Nash karne, xPass/Ghosting itd.). Dla aktualnego stanu zob. ANALIZA.

**Aktualizacja (zintegrowane brakujące/niepełne algorytmy):**
- **MatchSummary**: pełne zliczanie `passesInFinalThird`, `bigChances` (xG≥0.3), `saves` (z Shot Saved), `blocks` (z shots blocked), `possessionLost` (PassIntercepted/DribbleLost), `tacklesTotal`/`tacklesWon`, `clearances`, `duelsWon`, `aerialDuelsWon` — agregator + nowe typy zdarzeń w FullMatchEngine (Tackle, Clearance, Duel, AerialDuel).
- **Field Tilt**: kontakt w tercji ataku (strefy 9–12) → `MatchAnalytics.fieldTilt`, zapis w `MatchSummary.fieldTilt`.
- **PPDA**: strefa budowania 1–6, podania vs akcje defensywne → `MatchAnalytics.ppda`, zapis w `MatchSummary.ppda`.
- **xG Chain / xG Buildup**: cofanie sekwencji od Shot/Goal, przypisanie xG łańcuchowi i buildup (bez ostatnich 2) → `MatchAnalytics.xgChainByPlayer`, `xgBuildupByPlayer`.
- **Sieć podań**: statystyki per zawodnik (passesAttempted, passesCompleted) → `MatchAnalytics.passingNodeStats`, typ `PassingNodeStats`.
- **Elo**: kolumna `teams.elo_rating`, aktualizacja po każdym meczu w `updateEloAfterMatchConnectionIO` (K=32).

## 1. Czy wszystkie algorytmy z dokumentów są wyliczane i zintegrowane?

### 1.1 Z dokumentu ALGORYTMY_ANALITYKI_PILKARSKIEJ.md — stan w aplikacji

| Sekcja / Algorytm | Zintegrowane? | Gdzie w kodzie | Uwagi |
|-------------------|---------------|----------------|-------|
| **§1.1 xG** | Tak (uproszczony/formułowy) | `FormulaBasedxG`, `ShotContext`, `FullMatchEngine` (strzał → xG) | Odległość, strefa, głowa, minuta, presja; brak pełnego modelu ML. |
| **§1.2 xG Next-Gen (tracking)** | Nie | — | Wymagałoby pozycji 22 graczy w momencie strzału i modelu presji kątowej; mamy pozycje, ale nie używane przy xG. |
| **§1.3 PSxG** | Tak (proxy) | `FullMatchEngine` — metadata `PSxG` przy Shot/Goal | Wzór: Goal ≈ xG×1.15, Saved ≈ xG×0.92; brak modelu placementu. |
| **§2.1 xT** | Częściowo | `DxT.baseZoneThreat`, strefy 1–12 | Brak macierzy przejść T(z→z'); mamy stałe zagrożenie strefy. |
| **§2.2 DxT** | Tak | `DxT.adjustedThreat`, `DxT.threatMap`, `FullMatchEngine` | Zagrożenie × (1 − opponentControl); pozycje 22 graczy → Pitch Control → DxT. |
| **§2.3 VAEP** | Tak (uproszczony) | `FormulaBasedVAEP`, `computeAnalyticsFromEvents` | Stałe przyrosty per typ zdarzenia (Pass ±0.02/−0.03, Goal +0.25 itd.); brak P_scores/P_concedes. |
| **§2.4 I-VAEP** | Nie | — | Wymagałoby intencji vs wyniku. |
| **§2.5 EPV / SoccerMap** | Nie | — | Głęboki model na pełnym trackingu. |
| **§2.6 xGChain / xGBuildup** | Tak | `FullMatchEngine.computeXgChainAndBuildup`, `MatchAnalytics.xgChainByPlayer`, `xgBuildupByPlayer` | Cofanie sekwencji od Shot/Goal; buildup = bez ostatnich 2 kontaktów. |
| **§2.7 OBV / PV** | Nie | — | StatsBomb; nie zaimplementowane. |
| **§2.8 gBRI / Tortuosity** | Nie | — | Biegi bez piłki; brak danych trajektorii. |
| **§3.1 Pitch Control** | Tak | `PitchControl.controlByZone`, `controlByZoneWithFatigue` | Wpływ odległości 22 graczy do stref; zmęczenie skaluje wpływ. |
| **§3.2 Voronoi** | Nie | — | Alternatywa do Pitch Control; nie używana. |
| **§3.3 OBSO / C-OBSO** | Nie | — | Pitch Control × xG × P(piłka); nie liczone. |
| **§3.4 Player Influence Area** | Nie | — | Elipsoidy wpływu; nie. |
| **§4.1 PPDA** | Tak | `FullMatchEngine.computeAnalyticsFromEvents` (strefy 1–6, passes vs Tackle/PassIntercepted), `MatchAnalytics.ppda` | (homePPDA, awayPPDA); zapis w MatchSummary. |
| **§4.2 Dynamic Pressure** | Częściowo | `MatchupMatrix.pressureInZone`, `interceptBonus` (pressZones) | Presja w strefie piłki i bonus przy pressingu; nie pełny model P_total. |
| **§4.3 Pressing Intensity** | Częściowo | `TriggerConfig.pressZones` → interceptBonus | Trigger strefowy; brak time-to-intercept per klatka. |
| **§4.4 Counter-pressing** | Częściowo | `counterTriggerZone` → `justRecoveredInCounterZone` → większa szansa na strzał | Odzyskanie w strefie kontry → wymuszenie szybkiego strzału. |
| **§5 Sieci podań (BC, PageRank, clustering)** | Częściowo | `MatchAnalytics.passingNodeStats` (PassingNodeStats: passesAttempted, passesCompleted) | Brak betweenness/PageRank; podstawowe statystyki podań per zawodnik. |
| **§5.2 Field Tilt** | Tak | `FullMatchEngine.computeAnalyticsFromEvents` (kontakty w strefach 9–12), `MatchAnalytics.fieldTilt` | (homeShare, awayShare); zapis w MatchSummary. |
| **§6.1 Poisson (wynik)** | Tak (tylko SimpleMatchEngine) | `SimpleMatchEngine`: `poisson(lambda)` | FullMatchEngine nie używa Poissona; bramki z xG per strzał. |
| **§6.2 Dixon-Coles** | Nie | — | Brak. |
| **§6.5 WPA** | Tak | `computeAnalyticsFromEvents` → `wpaTimeline`, `wpaFinal` | Start 0.5; po Goal ±0.15; timeline co 10 min. |
| **§7 Elo / Glicko** | Tak | `Team.eloRating`, `TeamRepository.updateElo`, `LeagueService.updateEloAfterMatchConnectionIO` | K=32; aktualizacja po każdym meczu; kolumna `teams.elo_rating`. |
| **§8 Stałe fragmenty (NMF, GMM, rożne)** | Nie | — | Zdarzenia Corner/FreeKick są generowane, ale bez modeli jakości. |
| **§9 IWP, Z-Score** | Częściowo | Foul → metadata `IWP`; `LeagueContextComputer` (Z-Score) | IWP losowe w Foul; Z-Score w kontekście ligowym (IWP). |
| **§10 Kontrataki** | Częściowo | `justRecoveredInCounterZone` + większa szansa na strzał | Wykrycie „odzyskania w strefie kontry”; brak pełnego frameworku tranzycji. |
| **§11 Centroid, Convex Hull, Stretch** | Nie | — | Pozycje są, ale te metryki nie są liczone. |
| **§12 Algorytm genetyczny / Hungarian** | Nie | — | Skład/formacja: BotSquadBuilder po overall, bez optymalizacji. |
| **§14 ACWR** | Tak | `SimpleMatchEngine`: `acwrFactor`, injury risk | recentMinutesPlayed → ryzyko Injury. |
| **§14 Metabolic Power** | Nie | — | Brak. |
| **§15 XGBoost/LightGBM** | Interfejsy + Loadable/Onnx stub | `xGModel`, `VAEPModel`, `LoadablexGModel`, `OnnxXGModel` | Można podpiąć modele; domyślnie formuły. |
| **§16 Nash (karne)** | Nie | — | Brak dogrywki/karnych. |
| **§17 Ghosting, xReceiver, xPass 360** | Nie | — | Brak. |

**Podsumowanie**: Zintegrowane w sensie „użyte w symulacji lub agregacji” są: xG (formułowy), PSxG (proxy), DxT z Pitch Control, VAEP (uproszczony), WPA, Pitch Control (ze zmęczeniem), presja/matchup, triggery kontrataku, ACWR (w SimpleMatchEngine). Pozostałe algorytmy z dokumentu są albo nieobecne, albo tylko częściowo (np. xT jako stałe zagrożenie strefy).

**Źródło prawdy:** Aktualna lista algorytmów i atrybutów zintegrowanych z kodem znajduje się w **ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md** (tabele §1 i §2.3).

---

## 2. Co jeszcze można dopracować lub uzupełnić

- **Statystyki MatchSummary** (zrobione): Tackle, Clearance, Duel, AerialDuel w FullMatchEngine; pełne zliczanie w MatchSummaryAggregator.
- **VAEP pełny**: Zastąpić stałe przyrosty modelem P_scores/P_concedes (np. ONNX/Smile) i liczyć ΔP przed/po akcji.
- **xGChain / xGBuildup** (zrobione): `computeXgChainAndBuildup` w FullMatchEngine.
- **Pressing / PPDA** (zrobione): strefa 1–6, MatchAnalytics.ppda i MatchSummary.ppda.
- **Field Tilt** (zrobione): MatchAnalytics.fieldTilt, MatchSummary.fieldTilt.
- **Sieci podań** (częściowo): passingNodeStats (attempted/completed); rozszerzyć o Betweenness, PageRank, odbiorców.
- **Elo/Glicko** (zrobione): teams.elo_rating, updateEloAfterMatchConnectionIO.
- **Stałe fragmenty**: Dla Corner/FreeKick dodać wynik (gol/strzał/odbiór) i ewentualnie prosty model jakości (strefa dośrodkowania).
- **Dogrywka i karne**: Rozszerzyć pętlę do 120 min i seria karnych z prostym modelem (Nash optional); osobny tryb „puchar”.
- **Pozycje w zdarzeniach**: Zapisując `MatchEventRecord`, opcjonalnie dołączać `homePositions`/`awayPositions` (lub snapshot stref) przy strzałach, żeby później trenować xG Next-Gen.
- **Eksport/trenowanie**: Pipeline już jest (eksport CSV/StatsBomb, skrypt Python, wgrywanie modeli); można dodać automatyczny job okresowo eksportujący i trenujący oraz przełączający model.

---

## 3. Hipotetyczny przebieg meczu — krok po kroku (gdzie w kodzie, co się dzieje, jaki output)

### 3.1 Wejście: kto wywołuje symulację

- **Miejsce**: `LeagueService.runMatchOnly(m, league)` (`LeagueService.scala`).
- **Kolejność**: `ensureSquad` dla obu drużyn → `squadToTeamInput` → `parseGamePlan` → `engineModelsRef.get` → budowa `MatchEngineInput` (składy, plany, sędzia, `randomSeed`, `xgModelOverride`, `vaepModelOverride`) → `engine.simulate(input)`.

Dla domyślnego silnika `engine = FullMatchEngine`, wywołanie to `FullMatchEngine.simulate(input)` → wewnętrznie `buildResult(input)`.

### 3.2 Inicjalizacja stanu (minuta 0)

- **Kod**: `FullMatchEngine.buildResult` → `MatchState.initial(...)` (`MatchState.scala`).
- **Co się dzieje**:
  - `ballZone = 6` (środek boiska), `possession = Some(homeTeamId)` (gospodarze rozpoczynają).
  - `PositionGenerator.all22Positions` zwraca pozycje 22 graczy (formacje z `homePlan.formationName`, `awayPlan.formationName`).
  - `PitchControl.controlByZone(homePos, awayPos)` daje kontrolę (home, away) per strefa 1–12.
  - `DxT.threatMap(control, possessionHome = true)` daje zagrożenie stref dla gospodarzy.
  - `fatigueByPlayer` = 0 dla wszystkich; `justRecoveredInCounterZone = false`.
- **Output**: Pierwsze zdarzenie to `KickOff` (minuta 0), dopisane do `eventsAcc`. Stan `state` jest gotowy do pętli.

### 3.3 Pętla zdarzeń (minuta 0 → 90)

- **Kod**: `while (state.minute < 90) { (event, nextState) = generateNextEvent(...); eventsAcc += event; state = nextState }`.
- **generateNextEvent** (`FullMatchEngine.scala`):

  - **Krok 1 — czas i posiadanie**  
    `minuteDelta = 1 + rng.nextInt(3)` (1–3 minuty), `nextMinute = state.minute + minuteDelta`.  
    Zespół przy piłce: `possTeamId = state.possession.getOrElse(homeTeamId)`. Losowany jest `actor` z 10 graczy polowych tej drużyny, `zone = state.ballZone`.

  - **Krok 2 — presja i matchup**  
    Z `state.pitchControlByZone` i `state.homeTriggerConfig`/`awayTriggerConfig` liczone są: `opponentControl`, `pressActive` (czy strefa w `pressZones`), `interceptBonus`, `matchupPressure` (`MatchupMatrix.pressureInZone`).  
    `actorFatigue` i `fatigueMissBonus` pochodzą z `state.fatigueByPlayer`.  
    Jeśli `state.justRecoveredInCounterZone` i los < 0.35, ustawiane jest `eventTypeRoll = 0.44`, co później kieruje do gałęzi **Shot**.

  - **Krok 3 — wybór typu zdarzenia**  
    Jedna liczba `eventTypeRoll` (0–1) decyduje o gałęzi, np.:
    - `< 0.42` → **Pass/LongPass** (z możliwym PassIntercepted),
    - `< 0.48` → **Shot/Goal**,
    - `< 0.52` → Foul,
    - dalej Corner, ThrowIn, Cross, PassIntercepted (osobna gałąź), Dribble, DribbleLost, FreeKick, Offside, Substitution (po 60.), w przeciwnym razie Pass.

  - **Przykład: Pass**  
    `targetZone = pickTargetZone(state.dxtByZone, zone, isHome, rng)` — strefy w kierunku ataku ważone DxT.  
    `passInterceptProb = 0.05 + opponentControl*0.12 + interceptBonus + matchupPressure` (capped).  
    Los: przechwyt tak/nie; przy nie‑przechwycie: sukces z prawdopodobieństwem `passSuccessBase` (obniżanym przez zmęczenie).  
    Tworzone jest zdarzenie np. `MatchEventRecord(nextMinute, "Pass", Some(actorId), None, Some(teamId), Some(targetZone), Some("Success"), Map("zone" -> ..., "xPass" -> ..., "zoneThreat" -> ...))`.  
    Pozycje są aktualizowane (`updatePositions`), `newFatigue = updateFatigue(state, minuteDelta)`, `newControl = PitchControl.controlByZoneWithFatigue(..., Some(newFatigue))`, `newDxt = DxT.threatMap(...)`.  
    Dla przechwytu w strefie `counterTriggerZone` drużyny odbierającej: `justRecoveredInCounterZone = true` w `newState`.

  - **Przykład: Shot/Goal**  
    `ShotContext(zone, distance, isHeader, nextMinute, scoreDiff, pressureCount)` → `xg = xgModel.xGForShot(ctx)`.  
    `isGoal = rng.nextDouble() < xg`.  
    Dla nie-gola: outcome Saved/Missed/Blocked z uwzględnieniem `actorFatigue`.  
    `PSxG` w metadata (np. Goal → xG×1.15).  
    Zdarzenie: `MatchEventRecord(..., "Goal" lub "Shot", ..., Map("xG" -> ..., "PSxG" -> ..., "zone" -> ..., "zoneThreat" -> ...))`.  
    `newHomeGoals`/`newAwayGoals` aktualizowane przy golu; `newFatigue` i `justRecoveredInCounterZone = false`.

- **Output po pętli**: `events = eventsAcc.toList.sortBy(_.minute)` — lista zdarzeń od KickOff do ostatniego przed 90. minutą; `state.homeGoals`, `state.awayGoals`; `MatchEngineResult(homeGoals, awayGoals, events, analytics)`.

### 3.4 Analityka i agregacja

- **Kod**: `computeAnalyticsFromEvents(events, homeTeamId, awayTeamId, homeGoals, awayGoals)` w `FullMatchEngine` → zwraca `MatchAnalytics` (possessionPercent, shotCount, xgTotal, vaepByPlayer, wpaTimeline, vaepTotal, wpaFinal).  
  Następnie w `LeagueService.writeMatchdayInTransaction`: `MatchSummaryAggregator.fromEvents(result.events, ...)` → `MatchSummary` (possessionPercent, shotsTotal, xgTotal, passesTotal, itd.).

### 3.5 Przykładowy output (skrót)

- **MatchEngineResult**:
  - `homeGoals`, `awayGoals`: np. 2, 1.
  - `events`: lista np. 80–120 zdarzeń, np.  
    `KickOff(0)` → `Pass(2, Success, zone=7, xPass=0.85)` → `Pass(4, Success, zone=9)` → `Shot(7, Saved, zone=10, xG=0.21, PSxG=0.19)` → … → `PassIntercepted(23, zone=5)` (jeśli strefa 5 = counterTriggerZone gości, następne zdarzenie ma podniesioną szansę na strzał) → `Shot(25, Goal, zone=9, xG=0.31, PSxG=0.36)` → … → ostatnie zdarzenie z `minute` ≤ 90.
  - `analytics`: np. `MatchAnalytics(vaepByPlayer = Map(...), wpaTimeline = [(0,0.5), (10,0.5), ..., (90,0.62)], possessionPercent = (54.2, 45.8), shotCount = (12, 8), xgTotal = (1.85, 0.92), vaepTotal = (2.1, 1.0), wpaFinal = 0.62)`.

- **MatchSummary** (po `MatchSummaryAggregator.fromEvents`):  
  possessionPercent, homeGoals/awayGoals, shotsTotal, shotsOnTarget, xgTotal, passesTotal, passesCompleted, passAccuracyPercent, crossesTotal/Successful, fouls, yellowCards, redCards, corners, throwIns, freeKicksWon, offsides, interceptions, itd.  
  Pola bez zdarzeń (tackles, clearances, saves, bigChances itd.) pozostają 0 lub None.

### 3.6 Zapis do bazy i API

- **Kod**: `writeMatchdayInTransaction` tworzy `MatchResultLog(logId, matchId, result.events, Some(summary), createdAt)` i zapisuje przez `matchResultLogRepo.create`.  
  Klient: `GET /api/v1/matches/:id/log` → `getMatchLog` → zwraca `MatchLogDto(events, summaryOpt, totalOpt)` (zdarzenia z metadata, w tym xG, PSxG, zoneThreat, xPass).

---

## 4. Gdzie w kodzie — szybki indeks

| Co | Plik / miejsce |
|----|-----------------|
| Wejście do symulacji | `LeagueService.runMatchOnly` → `engine.simulate(input)` |
| Stan początkowy | `MatchState.initial` w `MatchState.scala` |
| Pozycje 22 graczy | `PositionGenerator.all22Positions` w `PitchModel.scala` |
| Pitch Control | `PitchControl.controlByZone` / `controlByZoneWithFatigue` w `PitchModel.scala` |
| DxT | `DxT.threatMap`, `DxT.adjustedThreat` w `PitchModel.scala` |
| xG na strzał | `xgModel.xGForShot(ShotContext(...))` w `FullMatchEngine.generateNextEvent` (gałąź Shot) |
| PSxG w metadata | Ta sama gałąź Shot/Goal: `Map("xG" -> ..., "PSxG" -> ...)` |
| Presja / przechwyt | `MatchupMatrix.pressureInZone`, `interceptBonus` (pressZones), `passInterceptProb` |
| Kontratak | `justRecoveredInCounterZone`, `eventTypeRoll = 0.44` przy następnym zdarzeniu |
| Zmęczenie | `updateFatigue`, `fatigueByPlayer` w `MatchState`, `controlByZoneWithFatigue` |
| Agregacja statystyk | `MatchSummaryAggregator.fromEvents` w `MatchSummaryAggregator.scala` |
| VAEP/WPA | `computeAnalyticsFromEvents` w `FullMatchEngine.scala` |
| Zapis logu | `MatchResultLogRepository.create` z `encodeEvents` / `encodeSummary` |

---

*Dokument powiązany z: ALGORYTMY_ANALITYKI_PILKARSKIEJ.md, ALGORYTMY_MAPOWANIE_KOD.md, DOKUMENTACJA_TECHNICZNA.md, docs/ML_INTEGRACJA.md.*
