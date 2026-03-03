# Analiza: algorytmy i atrybuty vs implementacja

**Cel**: Weryfikacja, czy wszystko z dokumentów ALGORYTMY_ANALITYKI_PILKARSKIEJ.md, ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md i ALGORYTMY_MAPOWANIE_KOD.md jest zaimplementowane i ma wpływ na symulację.

**Data**: 2026-02-23

---

## 1. Algorytmy z ALGORYTMY_ANALITYKI_PILKARSKIEJ.md — stan w kodzie

| Sekcja / Algorytm                                           | Zaimplementowane        | Gdzie                                                                   | Uwagi                                                                                   |
| ----------------------------------------------------------- | ----------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| **§1.1 xG (odległość, kąt, kontekst)**           | Tak (formułowo)        | `FormulaBasedxG`, `FullMatchEngine` (Shot)                          | Odległość, strefa, głowa, presja, minuta; brak ML.                                  |
| **§1.2 xG Next-Gen (Angular Pressure, GK distance)** | Tak (formuła + kalibracja + ML-ready) | `ShotContext`, `FormulaBasedxG`, `xgCalibration`; `LoadablexGModel.fromCoefficientsExtended` (8 cech: zone, dist, isHeader, minute, scoreDiff, pressure, angularPressure, gkDistance) | Model 8-coef ładowany z JSON (trenowany zewnętrznie); 6-coef jak wcześniej.              |
| **§1.3 PSxG**                                        | Tak (proxy)             | Shot/Goal metadata PSxG                                                 | Wzór z wyniku; brak modelu placementu.                                                 |
| **§2.1 xT (łańcuchy Markowa)**                     | Tak                     | `AdvancedAnalytics.transitionCountsFromEvents`, `xTValueIteration`, `MatchAnalytics.xtValueByZone` | Macierz przejść T(z→z') z zdarzeń; value iteration V(z); zapis w analityce.          |
| **§2.2 DxT (dynamiczny)**                            | Tak                     | `DxT.adjustedThreat`, `threatMap`, Pitch Control                    | Zagrożenie × (1 − opponentControl).                                                  |
| **§2.3 VAEP**                                        | Tak (z modelu)         | `FormulaBasedVAEP` / override, `computeAnalyticsFromEvents(..., vaepModel)` | Wartości z `vaepModel.valueForEvent(VAEPContext)`; override zmienia vaepTotal/vaepByPlayer. |
| **§2.4 I-VAEP**                                      | Tak                     | `MatchAnalytics.vaepByPlayerByEventType`, UI                           | Rozbicie VAEP per typ zdarzenia per zawodnik.                                          |
| **§2.5 EPV / SoccerMap**                             | Tak                     | `xtValueByZone` = EPV stref; `epvFromxT`; UI: „EPV / xT strefy 1–12”   | EPV = wartość posiadania w strefie (value iteration xT).                                |
| **§2.6 xGChain / xGBuildup**                         | Tak                     | `computeXgChainAndBuildup`, `MatchAnalytics`                        | Łańcuch od Shot/Goal; buildup bez 2 ostatnich.                                        |
| **§2.7 OBV / PV**                                    | Tak                     | `vaepByPlayer`, `vaepTotal`; UI: „OBV (VAEP)” w podsumowaniu           | OBV = wartość akcji z piłką (VAEP sum per team).                                      |
| **§2.8 gBRI / Tortuosity**                           | Tak                   | `ballTortuosity` (piłka); `playerTortuosityFromZoneSequences`, `MatchAnalytics.playerTortuosityByPlayer` (biegi zawodników) | Tortuosity piłki i zawodników (ścieżka stref / odcinek start–koniec); UI: top 5.   |
| **§3.1 Pitch Control**                               | Tak                     | `PitchControl.controlByZoneWithFatigue`                               | Odległość + zmęczenie + **pace/acceleration** (time-to-intercept); `buildPaceAccMap`, stan początkowy z `paceAccByPlayer`. |
| **§3.2 Voronoi**                                     | Tak (przybliżenie)      | `AdvancedAnalytics.zoneDominanceFromEvents`, `MatchAnalytics.homeShareByZone`, UI | Udział gosp. w akcjach (Pass/Dribble/Shot/Cross) w strefach 1–12; zapis w summary.   |
| **§3.3 OBSO / C-OBSO**                               | Tak (C-OBSO)          | `obsByZone`, `shotContextByZoneFromEvents`, metadata Shot: defendersInCone, gkDistance | Kontekst strzału w metadata; średni kontekst per strefa w analityce i UI.          |
| **§3.4 Player Influence Area**                       | Tak (przybliżenie)     | `MatchAnalytics.playerActivityByZone`, UI                              | Aktywność per strefa per gracz (z zdarzeń).                                            |
| **§4.1 PPDA**                                        | Tak                     | `MatchAnalytics.ppda`, strefy 1–6                                    | —                                                                                      |
| **§4.2 Dynamic Pressure (P_total)**                  | Tak                     | `MatchupMatrix.dynamicPressureTotal`, `passInterceptProb`            | P_total = 1−Π(1−p_i) z odległości i atrybutów (tackling, acceleration).             |
| **§4.3 Pressing Intensity (time-to-intercept)**      | Tak                   | Pitch Control (pace/acc); `pressingInOppHalfByPlayer` (akcje defensywne w strefach 7–12) | Pressing w połowie przeciwnika per gracz w analityce i UI.                               |
| **§4.4 Counter-pressing**                            | Tak                     | `counterTriggerZone`, `justRecoveredInCounterZone`                  | Większa szansa na strzał po odzyskaniu.                                               |
| **§5 Sieci podań (BC, PageRank, Clustering)**       | Tak                     | `computePassNetworkCentrality` (betweenness, PageRank, **clustering**), `MatchAnalytics.clusteringByPlayer` | Clustering coefficient per węzeł (AdvancedAnalytics.clusteringByNode).              |
| **§5.2 Field Tilt**                                  | Tak                     | `MatchAnalytics.fieldTilt` (strefy 9–12)                             | —                                                                                      |
| **§6.1 Poisson**                                     | Tak                     | `AdvancedAnalytics.poissonPrognosis(xgHome, xgAway)`, `MatchAnalytics.poissonPrognosis` | P(wygrana gosp.), P(remis), P(wygrana gości) z xG; UI: prognoza Poisson.               |
| **§6.5 WPA**                                         | Tak                     | `wpaTimeline`, `wpaFinal`                                           | ±0.15 przy golach.                                                                     |
| **§7 Elo**                                           | Tak                     | `teams.elo_rating`, `updateEloAfterMatchConnectionIO`               | —                                                                                      |
| **§8 Stałe fragmenty (NMF, GMM, rożne)**           | Tak                   | `setPieceZoneActivityFromEvents`; `setPiecePatternsNMF` (W, H); `setPieceRoutineClusters` (K-means) | Aktywność stref; NMF 2 komponenty (W per routine, H wektory stref); klaster 0/1 per routine; UI. |
| **§9 IWP (pojedynki)**                               | Tak                   | Z-Score w Duel/Dribble; **jawna metryka** `MatchAnalytics.iwpByPlayer` (suma wkładu z Tackle, PassIntercepted, Dribble, DribbleLost, Duel) | IWP per gracz w analityce i UI (top 5).                                                 |
| **§14 ACWR**                                         | Tak                     | `acwrFactor`, `injuryProneFactor`; FullMatchEngine: roll kontuzji w gałęzi Foul | Ryzyko kontuzji = baseInjuryProb × injuryProne × acwr(recentMinutes).                 |
| **§14 Metabolic Power**                              | Tak                   | `metabolicLoad` (piłka); `MatchAnalytics.metabolicLoadByPlayer` (dystans × (1 + 0.15×udział stref ataku)); `estimatedDistanceByPlayer` | Metabolic per zawodnik; UI: top 5.                                                    |
| **§16 Nash (karne)**                                 | Tak                     | Gałąź Penalty: payoff 2×2 (saveProbSame/saveProbDiff), `nashPenalty2x2`, losowanie L/R strzelca i bramkarza | Kierunek strzału i nurkowania z mieszanych strategii Nash; save zależy od zgodności L/R.   |

---

## 2. Atrybuty z ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md — stan w kodzie

### 2.1 Źródło danych: PlayerGenerator vs Domain

- **Domain.Player**: `physical`, `technical`, `mental` (Map[String, Int]), `traits`, `bodyParams`.
- **PlayerGenerator** ustawia klucze przy generacji zawodników (i zapisie do bazy). Silnik odczytuje te same mapy.

### 2.2 Klucze w PlayerGenerator

| Kategoria                      | Klucze w kodzie                                                                                                                      | Zgodność z ATRYBUTY                                                                                                                                                 |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **physical**             | pace, acceleration, agility, stamina, strength, jumpingReach, balance, naturalFitness                                                | Zgodne (nazwy camelCase; ATRYBUTY: Jumping → jumpingReach).                                                                                                          |
| **technical (outfield)** | passing, firstTouch, dribbling, crossing, shooting, tackling, marking, heading, longShots, ballControl, **technique** | Zgodne (silnik: finishing∨shooting); technique w generatorze i silniku (podania, xG). |
| **technical (GK)**       | gkReflexes, gkHandling, gkKicking, gkPositioning, gkDiving, gkThrowing                                                               | Mapowanie reflexes/gkReflexes, handling/gkHandling; gkDiving w Save/Penalty; gkKicking/gkThrowing przy Clearance (GK w strefie 1–2).              |
| **mental**               | composure, decisions, vision, concentration, workRate, positioning, anticipation, flair, aggression, bravery, leadership, offTheBall, **teamwork** | Zgodne; **teamwork** dodany do generatora i używany w silniku (roleAndInstructionModifiers).                                                                  |

### 2.3 Użycie atrybutów w FullMatchEngine

| Atrybut (doc)          | Klucz w silniku           | Używany?     | Gdzie                                                                           |
| ---------------------- | ------------------------- | ------------- | ------------------------------------------------------------------------------- |
| Pace                   | pace                      | Tak           | `buildPaceAccMap`; Pitch Control (time-to-intercept).                          |
| Acceleration           | acceleration              | Tak           | `buildPaceAccMap`, Dynamic Pressure (interceptorWeight); Pitch Control.        |
| Agility                | agility                   | Tak           | Dribble/DribbleLost (atakujący i obrońcy), waga sukcesu dryblingu.            |
| Stamina                | stamina                   | Tak           | updateFatigue                                                                   |
| Strength               | strength                  | Tak           | Duel (waga strength/10 + balance/40), AerialDuel (jumpingReach+strength). Nie w interceptorWeight (tam: tackling, positioning, anticipation, bravery, aggression). |
| Jumping                | jumpingReach              | Tak           | AerialDuel                                                                      |
| Natural Fitness        | naturalFitness            | Tak           | updateFatigue (nfFactor spowalnia narastanie zmęczenia).                       |
| Balance                | balance                   | Tak           | Duel (waga = strength/10 + balance/40).                                         |
| Finishing              | **finishing**       | Tak           | Shot, Penalty; mapowanie finishing ∨ shooting.                                 |
| Long Shots             | longShots                 | Tak           | Strzał ze strefy ≤7: attrXgMult × (0.75 + longShots/20×0.25).               |
| Short/Long Passing     | **passing** (jeden) | Tak           | Pass success                                                                    |
| Crossing               | crossing                  | Tak           | Cross success                                                                   |
| Dribbling              | dribbling                 | Tak           | Dribble vs DribbleLost (P(sukces) z dribbling, agility vs def tackling).        |
| First Touch            | firstTouch                | Tak           | Pass success                                                                    |
| Heading                | heading                   | Tak           | Strzał głową (po Cross): attrXgMult × (0.85 + heading/20×0.15).            |
| Tackling               | tackling                  | Tak           | interceptorWeight                                                               |
| Technique              | technique                 | Tak     | Pass success (mentalBonus); xG (attrXgMult).                                   |
| Vision                 | vision                    | Tak           | Pass success                                                                    |
| Composure              | composure                 | Tak           | Shot, Penalty                                                                   |
| Off The Ball           | offTheBall                | Tak     | actorWeight (w strefie ataku: bonus od offTheBall).                              |
| Positioning            | positioning               | Tak           | interceptorWeight                                                               |
| Anticipation           | anticipation              | Tak           | interceptorWeight                                                               |
| Decisions              | decisions                 | Tak           | Pass success                                                                    |
| Work Rate              | workRate                  | Tak           | updateFatigue                                                                   |
| Aggression             | aggression                | Tak           | interceptorWeight                                                               |
| Concentration          | concentration             | Tak           | Podania po 75. min: lateGameConcentration.                                      |
| Teamwork               | teamwork                  | Tak           | roleAndInstructionModifiers (bonusy ról × teamwork/20); dodany do generatora. |
| Bravery                | bravery                   | Tak           | interceptorWeight                                                               |
| Flair                  | flair                     | Tak           | Dribble: wariancja P(sukces) ± (flair/20)×0.06.                               |
| **GK: Reflexes** | **reflexes**        | Tak           | Save; mapowanie reflexes ∨ gkReflexes.                                         |
| **GK: Handling** | **handling**        | Tak           | Save; mapowanie handling ∨ gkHandling.                                         |

**Uwaga (interceptorWeight):** W Dynamic Pressure / przechwycie (`interceptorWeight`) używane są **wyłącznie**: tackling, positioning, anticipation, bravery, aggression (+ Z-Score z positionStats). **Strength nie wchodzi** w interceptorWeight (jest tylko w Duel i AerialDuel).

### 2.4 Krytyczne rozjazdy nazw (bugi)

1. **Finishing vs shooting**Silnik: `technical.getOrElse("finishing", 10)`. Generator: tylko `shooting`.**Efekt**: Wszyscy gracze mają efektywne finishing = 10 w silniku.
2. **GK: reflexes / handling vs gkReflexes / gkHandling**
   Silnik: `technical.getOrElse("reflexes", 10)` i `handling`. Generator dla GK: `gkReflexes`, `gkHandling`.
   **Efekt**: Bramkarze mają efektywne reflexes/handling = 10 (domyślnie).

---

## 3. Podsumowanie: co jest, czego brakuje

### Zaimplementowane i działające

- xG (formułowy), PSxG (proxy), DxT z Pitch Control, VAEP (uproszczony), WPA.
- Pitch Control (odległość + zmęczenie), PPDA, Field Tilt, xG Chain/Buildup, passingNodeStats.
- TriggerConfig (pressZones, counterTriggerZone), teamInstructions (tempo, width, passingDirectness, pressingIntensity), slotRoles, playerInstructions, setPieces, throwInConfig, oppositionInstructions.
- Atrybuty używane w silniku: stamina, workRate, strength, jumpingReach, **tackling**, **positioning**, **passing**, **firstTouch**, **decisions**, **vision**, **finishing** (nazwa!), **composure**, **crossing**, morale, freshness; dla GK **reflexes**, **handling** (nazwy!).

### Zaimplementowane (stan po 2026-03-03)

- **Algorytmy**: xG (formuła + kalibracja + model 8-coef), xT (macierz przejść + value iteration), I-VAEP, EPV (xT stref), gBRI (tortuosity piłki + biegów), Voronoi (zone dominance + centrum aktywności), OBSO/C-OBSO, Dynamic Pressure P_total, Betweenness/PageRank/Clustering, Metabolic (piłka + per zawodnik), Poisson (prognoza), NMF/GMM stałe fragmenty, IWP (jawna metryka), Nash karne, ACWR/kontuzje. **xPass/xReceiver** (wartość podania z xT), **xPass under pressure** (receiverPressure ≥ 2: suma i per zawodnik w `passValueUnderPressureTotal`, `passValueUnderPressureByPlayer`), **PSxG** (placement + model), **Voronoi z centrum aktywności**, **LoadableVAEP** (ładowanie wag z JSON), **Player Influence score** (activity×xT).
- **Atrybuty w silniku**: Wszystkie z tabeli 2.3 używane (pace, acceleration, technique, offTheBall, GK: gkPositioning, gkOneOnOnes, gkCommandOfArea, gkKicking, gkThrowing, gkDiving; mapowanie finishing/shooting, reflexes/gkReflexes, handling/gkHandling). Generator uzupełnia **finishing** = shooting, GK **reflexes**/**handling** = gkReflexes/gkHandling.

### Częściowe / rozszerzenia (opcjonalne)

- **Ghosting / xPass 360**: przybliżenie przez xPass (wartość podania z EPV stref) oraz **xPass under pressure** (proxy presji: receiverPressure w metadata podania, suma i per gracz w analityce i UI); pełny model 360° wymaga pozycji odbiorcy i presji 360°.
- **xG/VAEP trenowany ML**: interfejs ładowania współczynników (LoadablexGModel 8-coef, LoadableVAEP) jest; pełny pipeline trenowania (Python/ONNX) poza aplikacją.
- **Voronoi z (x,y)**: prawdziwy diagram Voronoi wymaga śledzenia pozycji graczy w czasie; obecnie Voronoi z centrum aktywności (strefy).

### Rekomendowane minimalne poprawki — ZREALIZOWANE (2026-02-23)

1. **Mapowanie kluczy w silniku** — zrobione:
   - `finishingAttr(technical)`: finishing ∨ shooting.
   - `gkReflexesAttr` / `gkHandlingAttr`: reflexes ∨ gkReflexes, handling ∨ gkHandling.
2. **Użycie dodatkowych atrybutów w FullMatchEngine** — zrobione:
   - **interceptorWeight**: dodane anticipation, bravery, aggression (obok tackling, positioning).
   - **Dribble vs DribbleLost**: jedna gałąź [0.67, 0.74); sukces z prawdopodobieństwa zależnego od dribbling, agility (atakujący) vs średni tackling, agility (obrońcy); wariancja od flair.
   - **Strzał**: isHeader gdy lastEventType == "Cross"; przy headerze attrXgMult × (0.85 + heading/20×0.15); przy strefie ≤7 (long shot) attrXgMult × (0.75 + longShots/20×0.25).
   - **Zmęczenie (updateFatigue)**: naturalFitness w mianowniku (nfFactor 0.92 + naturalFitness/20×0.16).
   - **Duel (fizyczny)**: waga = strength/10 + balance/40.
   - **Podanie**: przy minucie > 75 mnożnik lateGameConcentration = 0.9 + 0.1×(concentration/20).
   - **roleAndInstructionModifiers**: przyjmuje actor; passBonus i shotTendencyBonus skalują przez teamwork/20.
3. **PlayerGenerator**: dodany klucz **teamwork** do outfieldMentalKeys.

---

## 4. Odniesienie do ALGORYTMY_MAPOWANIE_KOD.md

- Sekcja 1.1 (FullMatchEngine) i 1.2 (agregacja, analityka) są aktualne.
- Tabela w §2 „Algorytmy z dokumentów — co jest zaimplementowane” pozostaje w mocy; uzupełniono ją o powyższą tabelę algorytmów i atrybutów oraz o rozjazdy nazw kluczy (finishing/shooting, GK).

Po wprowadzeniu mapowania kluczy oraz użyciu anticipation, bravery, aggression, dribbling, agility, heading, longShots, naturalFitness, balance, concentration, teamwork i flair w silniku, wartości atrybutów z bazy mają realny wpływ na symulację (przechwyty, drybling, strzały, zmęczenie, pojedynki, podania w końcówce, realizacja ról).

### Uzupełnienia (kontynuacja)

- **Pitch Control z pace/acceleration**: `buildPaceAccMap(input)` buduje mapę (pace, acc) dla 22 graczy; przekazywana do `generateNextEvent` i do `MatchState.initial(..., paceAccByPlayer)`. `PitchControl.controlByZoneWithFatigue(..., paceAccByPlayer)` używa time-to-intercept (krótki dystans: sqrt(2*d/acc), dłuższy: d/pace). Naprawiono brak definicji `paceAccMap` w pętli zdarzeń oraz typ w gałęzi Cross (`(state.homePositions, state.awayPositions)` gdy nie claimed).
- **Technique, offTheBall, GK**: technique w generatorze i w silniku (podania, xG); offTheBall w `actorWeight`; GK: gkPositioning, gkOneOnOnes, gkCommandOfArea (Cross Claimed) — już wcześniej zaimplementowane.
- **Betweenness / PageRank**: `computePassNetworkCentrality` i `MatchAnalytics.betweennessByPlayer`, `pageRankByPlayer` — już zaimplementowane.
- **Dynamic Pressure P_total**: `MatchupMatrix.dynamicPressureTotal` i użycie w `passInterceptProb` — już zaimplementowane.

### Wdrożenie zaawansowanych algorytmów (2026-03-03)

- **AdvancedAnalytics.scala**: moduł z algorytmami post-match:
  - **xT (value iteration)**: `transitionCountsFromEvents` buduje macierz przejść T(z→z') z Pass/LongPass/Dribble; `xTValueIteration` liczy V(z) = baseThreat(z) + γ·E[V(z')]; wynik w `MatchAnalytics.xtValueByZone`.
  - **Clustering sieci podań**: `clusteringByNode(edges, nodes)` — współczynnik clusteringu 2·E_sąsiedzi/(k(k−1)); zwracany z `computePassNetworkCentrality` jako `clusteringByPlayer`.
  - **OBSO**: `obsByZone(baseZoneThreat)` — prawdopodobieństwo strzału ze strefy; `MatchAnalytics.obsoByZone`.
  - **Tortuosity (gBRI)**: `ballTortuosity(events)` — stosunek długości ścieżki stref do odległości liniowej; `MatchAnalytics.ballTortuosity`.
  - **Metabolic load**: `metabolicLoadFromZonePath(events)` — suma odległości między kolejnymi strefami (m); `MatchAnalytics.metabolicLoad`.
  - **Nash karne**: `nashPenalty2x2(payoffLL, payoffLR, payoffRL, payoffRR)` — mieszane strategie 2×2 (helper do przyszłego użycia).
- **MatchAnalytics**: nowe pola: `clusteringByPlayer`, `xtValueByZone`, `obsoByZone`, `ballTortuosity`, `metabolicLoad`, `nashPenaltyShooterLeft`, `nashPenaltyGkLeft` (opcjonalne).
- **MatchSummary / MatchSummaryDto**: opcjonalne `ballTortuosity`, `metabolicLoad`, `xtByZone` (lista 12) — zapis w logu meczu, ekspozycja w API.
- **UI (MatchDetailPage)**: sekcja „Analityka zaawansowana” z tortuosity, metabolic load, xT strefy 1–12; oraz Field Tilt i PPDA w podsumowaniu meczu.
- **Testy**: `AdvancedAnalyticsSpec` (transitionCounts, xTValueIteration, clustering, obsByZone, ballTortuosity, metabolicLoad, nashPenalty2x2); `FullMatchEngineSpec` — test że analityka zawiera clustering, xtValueByZone, obsoByZone, ballTortuosity, metabolicLoad.

### Iteracja: ACWR, Nash karne, IWP Z-Score (2026-03-03)

- **ACWR w FullMatchEngine**: W gałęzi zdarzenia Foul (gdy nie wybrano Penalty) wykonywany jest roll kontuzji: `baseInjuryProb * injuryProneFactor(actor) * acwrFactor(actor)`. Przy sukcesie zwracane jest zdarzenie `Injury` zamiast `Foul`. `injuryProneFactor` z cechy `injuryProne` (traits), `acwrFactor` z `recentMinutesPlayed` (PlayerMatchInput).
- **Nash karne**: W gałęzi Penalty (strefa ≥10): macierz payoff 2×2 (strzelec L/R, bramkarz L/R), `saveProbSame` gdy ten sam kierunek, `saveProbDiff` gdy przeciwny. `AdvancedAnalytics.nashPenalty2x2` zwraca mieszane strategie; kierunek strzału i nurkowania bramkarza losowane z tych prawdopodobieństw; wynik (gol/obroniony) z `saveProb` zależnej od zgodności.
- **IWP Z-Score**: `zScoreForSlot(slot, attrName, value, leagueContext)` — (value − mean) / stddev z `positionStats` dla pozycji; ograniczone do ±2. Użycie: `interceptorWeight` — bonus od Z-Score dla tackling/positioning; Dribble — bonus dla dribbling atakującego; Duel — bonus dla strength. Wymaga `leagueContext.positionStats` (Map[slot, Map[attr, PositionAttrStats]]).
- **Testy**: ACWR/Injury (recentMinutesPlayed, injuryProne, symulacja); Nash penalty (kilka seedów, sprawdzenie Penalty/Goal); Z-Score IWP (positionStats dla CM/CB, symulacja).

### Iteracja: EPV/OBV, Voronoi, UI, audyt (2026-03-03)

- **EPV (Expected Possession Value)**: Wartość stref = `xtValueByZone` (xT po value iteration). Ekspozycja w UI jako „EPV / xT strefy 1–12”. `AdvancedAnalytics.epvFromxT` zwraca mapę stref (alias xT).
- **OBV (On-Ball Value)**: VAEP per drużyna = `vaepTotal`; w UI w podsumowaniu meczu: „OBV (VAEP): X.XX – X.XX”.
- **Voronoi (przybliżenie)**: `zoneDominanceFromEvents` — dla każdej strefy 1–12 udział gospodarzy w akcjach (Pass, LongPass, Dribble, Shot, Goal, Cross). Wynik w `MatchAnalytics.homeShareByZone` → `MatchSummary.homeShareByZone` → DTO → UI: „Voronoi (udział gosp. w strefach 1–12)”.
- **Kontuzje w UI**: Agregator zlicza zdarzenia `Injury` per drużyna → `MatchSummary.injuries` → DTO `injuries`; w podsumowaniu: „Kontuzje: X – Y”.
- **UI (MatchDetailPage)**: Podsumowanie: Kontuzje, OBV (VAEP). Sekcja „Analityka zaawansowana”: opis EPV/OBV/Nash, tortuosity, metabolic load, EPV/xT strefy, Voronoi (homeShareByZone).
- **Audyt atrybutów**: Pace i Acceleration — **Tak** (buildPaceAccMap, Pitch Control time-to-intercept, Dynamic Pressure). Wszystkie atrybuty z tabeli 2.3 mają realny wpływ na symulację (podania, strzały, drybling, pojedynki, zmęczenie, karne, przechwyty, kontuzje).

### Iteracja: gkKicking/gkThrowing, C-OBSO (następna)

- **gkKicking / gkThrowing**: Gdy strefa ≤ 2 i wybrany zostanie GK jako wykonawca Clearance (ok. 28% szans), używane są atrybuty gkKicking (wyrzut nogą) lub gkThrowing (ręką) do P(sukces dystrybucji); przy niepowodzeniu 50% szans na utratę posiadania. W metadata Clearance: `distributionType` = "kick" lub "throw".
- **C-OBSO**: W zdarzeniach Shot/Goal dopisane metadata: `defendersInCone`, `angularPressure`, `gkDistance`. `AdvancedAnalytics.shotContextByZoneFromEvents` liczy średni kontekst per strefa; `MatchAnalytics.shotContextByZone`; w summary/DTO: `avgDefendersInConeByZone`, `avgGkDistanceByZone` (listy 12 elem.); UI: „C-OBSO (kontekst strzałów)” w sekcji Analityka zaawansowana.
- **Testy**: GK clearance (Clearance z opcjonalnym distributionType); Shot (nie-karne) z defendersInCone i gkDistance; `shotContextByZoneFromEvents` w AdvancedAnalyticsSpec.

### Iteracja: stałe fragmenty, pressing w połowie, xG kalibracja

- **Stałe fragmenty (§8)**: `AdvancedAnalytics.setPieceZoneActivityFromEvents` — dla Corner i FreeKick (z metadata `routine`) zbiera strefę *następnego* zdarzenia; wynik `Map[routineKey, Map[zone, count]]` w `MatchAnalytics.setPieceZoneActivity`; w summary/DTO/UI: `setPieceZoneActivity` (np. Corner:default → lista 12 liczb).
- **Pressing w połowie przeciwnika**: przy zliczaniu akcji defensywnych (Tackle, PassIntercepted, DribbleLost) dodane zliczanie w strefach 7–12 → `pressingInOppHalfByPlayer`; ekspozycja w summary/DTO i UI (top 5).
- **xG Next-Gen (kalibracja)**: `LeagueContextInput.xgCalibration: Option[Double]` — gdy podany (np. 1.05), końcowe xG jest mnożone przed losowaniem gola; umożliwia korektę ligową bez modelu ML.
- **Testy**: `setPieceZoneActivityFromEvents` w AdvancedAnalyticsSpec; FullMatchEngineSpec: analityka zawiera setPieceZoneActivity i xgCalibration przy podanym leagueContext.

### Następne kroki (kolejne iteracje)

- **VAEP z modelu w analityce** — zrobione: `computeAnalyticsFromEvents` przyjmuje `vaepModel` i wywołuje `vaepModel.valueForEvent(VAEPContext(...))` dla każdego zdarzenia; override zmienia `vaepTotal` i `vaepByPlayer` w wynikach.
- **Voronoi z (x,y)**: opcjonalne rozszerzenie przy śledzeniu pozycji graczy w czasie (obecnie Voronoi z centrum aktywności stref).
- **EPV/xT w czasie**: wykres wartości stref w funkcji minuty (wymaga stanu xT per minuta lub przybliżenia z zdarzeń).
- **Pipeline trenowania xG/VAEP**: zewnętrzny (Python/ONNX); interfejsy LoadablexGModel / LoadableVAEP w aplikacji gotowe.
