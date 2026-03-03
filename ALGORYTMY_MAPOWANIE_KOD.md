# Mapowanie algorytmów z dokumentacji na kod

**Cel**: Powiązanie algorytmów z `ALGORYTMY_ANALITYKI_PILKARSKIEJ.md` oraz atrybutów/statystyk z dokumentów (KONTRAKTY §2.3, MODELE, ATRYBUTY) z implementacją w backendzie i frontendzie.

---

## 1. Gdzie co jest liczone

### 1.1 Silnik meczu — `FullMatchEngine.scala` (domyślny), `SimpleMatchEngine.scala`

**FullMatchEngine** (używany w Main): maszyna stanów zdarzenie po zdarzeniu; pozycje 22 graczy (`PositionGenerator`, `PitchModel`); Pitch Control per strefa (`PitchControl.controlByZone`); DxT (`DxT.adjustedThreat`, `threatMap`); formułowe xG (`FormulaBasedxG`, `ShotContext`) i VAEP (`FormulaBasedVAEP`); `TriggerConfig` (pressZones, counterTriggerZone) zwiększa szansę na przechwyt w strefach pressingu. Bramki z prawdopodobieństwa xG przy zdarzeniu Shot.

### 1.1b SimpleMatchEngine (testy / zapasowy)

| Algorytm / pojęcie | Dokument | Gdzie w kodzie | Jak działa |
|--------------------|----------|----------------|------------|
| **Poisson (bramki)** | SILNIK §8, KONTRAKTY §1 | `buildResult`: `poisson(lambdaHome, rng)`, `poisson(lambdaAway, rng)` | λ = 1.2 × homeAdvantage × moraleMod; bramki = Poisson(λ). |
| **Morale → composure / decisions** | MODELE §7 | `effectiveComposure`, `effectiveDecisions` | effective = base×(0.85 + 0.15×morale); base = atrybut/20. Używane przy wyniku strzału (composure) i przy ryzyku kartki (decisions). |
| **xG (uproszczony)** | ALGORYTMY §1.1 | Zdarzenia `Shot`/`Goal`: metadata `xG` (0.2 dla strzału, 0.5 dla gola) | Nie ma modelu odległości/kątu; stałe wartości w metadata. Agregacja w MatchSummaryAggregator i MatchAnalytics. |
| **xPass** | SILNIK | Zdarzenia `Pass`/`LongPass`: metadata `xPass` (0.70–0.95) | Losowe w zakresie; fatigue po 70. min może dać outcome Missed. |
| **zoneThreat (DxT-like)** | ALGORYTMY §2.2 (uproszczony) | `zoneThreat(zone)`: 0.04 + 0.012×zone (zone 1–12) | Strefa w metadata Pass/Shot; wyższa strefa = bliżej bramki = wyższe zagrożenie. |
| **VAEP (uproszczony)** | ALGORYTMY §2.3 | `computeAnalyticsFromEvents`: Pass/LongPass ±0.02/−0.03, Shot (Saved/Missed/Blocked), Goal +0.25, ThrowIn/Cross/PassIntercepted/Dribble/DribbleLost — stałe przyrosty per typ | V(a) = ΔP_scores − ΔP_concedes; u nas przybliżone stałymi wartościami per typ zdarzenia. |
| **WPA (Win Probability Added)** | ALGORYTMY (win probability) | `wpaTimeline`: start 0.5; po każdym Goal +0.15 / −0.15 (cap 0–1) | Timeline co 10 min; skok po golach. |
| **IWP (Injury Wypadkowe Prawdopodobieństwo)** | SILNIK | Zdarzenia `Foul`: metadata `IWP` (0.35–0.70) | Losowe w zakresie; nie wpływa na dalszą symulację kontuzji. |
| **ACWR / injury risk** | ALGORYTMY §14, WYMAGANIA | `acwrFactor(p)`: 1.0 + 0.4×min(1, recentMinutes/270); `injuryProneFactor(p)` z traits | Ryzyko Injury = baseInjuryProb × injuryProne × acwrFactor. |
| **Sędzia (strictness)** | KONTRAKTY, SILNIK §6 | `strictness` → liczba fauli (1 + strictness×4), cardRiskPerFoul = (1−effectiveDecisions)×strictness, RedCard (SecondYellow / Direct) | Więcej strictness → więcej fauli i kartek. |

### 1.2 Agregacja statystyk meczu — `MatchSummaryAggregator.scala`

| Statystyka (MatchSummary) | Źródło w zdarzeniach | Zasada |
|---------------------------|----------------------|--------|
| possessionPercent | Pass, LongPass, Shot, Goal — po stronie (home/away) | (pasy+strzały) / suma × 100; domyślnie 50–50 przy braku zdarzeń. |
| shotsTotal, shotsOnTarget | Goal, Shot | Goal zawsze on target; Shot: Saved/Blocked = on target. |
| xgTotal | Goal (xG 0.5), Shot (xG z metadata lub 0.0) | Suma xG po stronie. |
| passesTotal, passesCompleted | Pass, LongPass | outcome Success = completed. |
| passAccuracyPercent | passesCompleted / passesTotal × 100 | Per drużyna. |
| crossesTotal, crossesSuccessful | Cross | outcome Success = successful. |
| longBallsTotal, longBallsSuccessful | LongPass | outcome Success = successful. |
| interceptions | PassIntercepted | Liczba zdarzeń. |
| fouls | Foul | Liczba. |
| yellowCards, redCards | YellowCard, RedCard | Liczba. |
| corners | Corner | Liczba. |
| throwIns | ThrowIn | Liczba. |
| freeKicksWon | FreeKick | Liczba. |
| offsides | Offside | Liczba. |
| shotsOffTarget | shotsTotal − shotsOnTarget | Wyprowadzane. |
| goalsConceded | (awayGoals, homeGoals) | Odwrotnie do drużyn. |
| foulsSuffered | (fouls.away, fouls.home) | Odwrotnie do fauli. |

Pola **tacklesTotal**, **clearances**, **blocks**, **saves**, **passesInFinalThird**, **bigChances**, **duelsWon**, **aerialDuelsWon**, **possessionLost** — w obecnym silniku nie generujemy zdarzeń typu Tackle/Clearance/Block/Save; w MatchSummary są ustawione na (0,0) lub None. **vaepTotal**, **wpaFinal** w MatchSummary są None; VAEP/WPA są w MatchAnalytics (vaepByPlayer, wpaTimeline).

### 1.3 Analityka w silniku — `MatchAnalytics` (SimpleMatchEngine)

| Pole | Obliczenie |
|------|------------|
| possessionPercent | (passHome+shotHome)/total × 100, (passAway+shotAway)/total × 100 |
| shotCount | Liczba Shot + Goal per drużyna |
| xgTotal | Suma xG z metadata zdarzeń Shot/Goal |
| vaepByPlayer | Suma przyrostów VAEP per typ zdarzenia (Pass, Shot, Goal, ThrowIn, Cross, PassIntercepted, Dribble, DribbleLost) |
| wpaTimeline | Lista (minuta, WPA) co 10 min; WPA zmienia się po Goal |

### 1.4 Atrybuty zawodników — `PlayerGenerator.scala`, `Domain.scala`

| Źródło | Gdzie | Zasada |
|--------|--------|--------|
| ATRYBUTY (30+6, 1–20) | Player: physical, technical, mental, traits (Map[String, Int]) | PlayerGenerator: listy kluczy (outfieldPhysicalKeys, outfieldTechnicalKeys, outfieldMentalKeys, traitKeys); GK ma gkTechnicalKeys. Wartości 1–20, clamp po generacji. |
| Balans drużyn | PlayerGenerator.generateBalancedSquad | targetTeamSum = 5400; suma wszystkich atrybutów 18 zawodników jest skalowana do targetTeamSum, żeby drużyny miały zbliżoną siłę. |
| overall | `PlayerOverall.scala` | Średnia physical/technical/mental; używane w BotSquadBuilder do wyboru najlepszej 11 (sortowanie po overall). |

---

## 2. Algorytmy z dokumentów — co jest zaimplementowane

| Algorytm (ALGORYTMY_ANALITYKI) | Zastosowanie w kodzie |
|--------------------------------|------------------------|
| **xG (§1.1)** | Uproszczony: stałe xG w metadata (0.2 / 0.5). Brak modelu odległości/kątu. |
| **xT / DxT (§2.1, §2.2)** | zoneThreat(zone) — jedna wartość na strefę (1–12), liniowa; brak macierzy przejść i pozycji zawodników. |
| **VAEP (§2.3)** | Uproszczony: stałe przyrosty VAEP per typ zdarzenia (Pass, Shot, Goal, Cross, …). Brak modeli P_scores/P_concedes. |
| **WPA** | Timeline 0–90 min, skok ±0.15 przy golach. |
| **ACWR / obciążenie (§14)** | acwrFactor z recentMinutesPlayed; używany w prawdopodobieństwie Injury. |
| **Poisson wyniku (§6)** | SimpleMatchEngine: rozkład Poissona na liczbę bramek; lambda z morale i home advantage. FullMatchEngine: bramki z prawdopodobieństwa xG przy każdym Shot. |
| **Pitch Control (§3.1)** | FullMatchEngine: `PitchControl.controlByZone(homePositions, awayPositions)` — wpływ odległości 22 graczy do środków stref; (homeControl, awayControl) per strefa. |
| **DxT z pozycjami 22 (§2.2)** | FullMatchEngine: `DxT.adjustedThreat(zone, homeControl, awayControl, possessionHome)` — zagrożenie strefy × (1 − opponentControl). |
| **overall zawodnika** | `PlayerOverall.overall(p)` — średnia wszystkich atrybutów; używane w BotSquadBuilder do sortowania składu. |

---

## 3. Poprawność obliczeń

- **Posiadanie**: spójne z definicją (udział w akcjach Pass+Shot); totalAct = passHome+passAway+shotHome+shotAway.
- **Strzały / xG**: Goal dodaje 1 do shots i 0.5 do xG; Shot dodaje 1 do shots i xG z metadata. shotsOnTarget: Goal + Shot z outcome Saved/Blocked.
- **Podania**: Pass i LongPass; completed = outcome Success. LongPass dodatkowo w longBallsTotal/Successful.
- **Kartki**: YellowCard i RedCard zliczane; RedCard po drugiej żółtej (yellowedPlayers) lub z małym prawdopodobieństwem Direct.
- **VAEP**: sumowanie per playerId po typach zdarzeń; wartości stałe (0.25 dla gola, 0.02/−0.03 dla podania itd.) — spójne z uproszczonym modelem.

---

**Szczegółowe zestawienie**: które algorytmy z encyklopedii ALGORYTMY_ANALITYKI_PILKARSKIEJ.md są zaimplementowane (pełnie / częściowo / nie), co można dopracować oraz **hipotetyczny przebieg meczu** krok po kroku (gdzie w kodzie, co się dzieje, jaki output) — patrz **docs/PRZEBIEG_MECZU_ALGORYTMY_DOPRACOWANIA.md**.

**Analiza algorytmów i atrybutów vs kod**: Pełna weryfikacja algorytmów z ALGORYTMY_ANALITYKI_PILKARSKIEJ.md oraz atrybutów z ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md — które są zaimplementowane, które używane w silniku, rozjazdy nazw kluczy (finishing/shooting, reflexes/gkReflexes, handling/gkHandling) i rekomendowane poprawki — **docs/ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md**. W FullMatchEngine dodano mapowanie: `finishingAttr` (finishing ∨ shooting), `gkReflexesAttr` (reflexes ∨ gkReflexes), `gkHandlingAttr` (handling ∨ gkHandling), aby wartości z PlayerGenerator (baza) miały wpływ na strzały, karne i obronę bramkarza.
