# Analiza: co jeszcze zrobić, dopracowania i krytyczny przegląd

**Data:** 2026-03-03  
**Zakres:** Dokumentacja (5 plików), historia czatu, kod (backend, frontend, shared) — podwójna weryfikacja.

---

## 1. Stan zaimplementowany (potwierdzony w kodzie)

### 1.1 Silnik i analityka

- **FullMatchEngine**: xG (FormulaBased + Loadable 8-coef, xgCalibration), PSxG z placement, VAEP **z modelu** (`computeAnalyticsFromEvents(..., vaepModel)` → `vaepModel.valueForEvent(VAEPContext)`), xT (value iteration), DxT, Pitch Control (pace/acceleration, zmęczenie), PPDA, Field Tilt, xG Chain/Buildup, Betweenness/PageRank/Clustering, IWP (Z-Score z positionStats), ACWR/kontuzje, Nash karne, Voronoi z centrum aktywności, xPass + xPass under pressure (receiverPressure w metadata, suma i per gracz), Player Influence score, stałe fragmenty (NMF, K-means), Poisson, WPA.
- **LeagueService.writeMatchdayInTransaction**: summary budowane z `result.analytics`; wszystkie pola zaawansowane (vaepTotal, xtByZone, voronoiCentroidByZone, passValueByPlayer, passValueUnderPressureTotal, passValueUnderPressureByPlayer, influenceScoreByPlayer, itd.) są mapowane do `MatchSummary`.
- **Eksport**: formaty `csv`, `json` (statsbomb), `json-full` (zdarzenia + pełny summary per mecz). Limit 50 meczów, sprawdzanie uprawnień.

### 1.2 UI i API

- **MatchDetailPage**: sekcja „Analityka zaawansowana” z EPV/xT (słupki tekstowe), xG timeline (kumulatywny co 15 min), xPass i xPass under pressure (suma + top 5), VAEP per zawodnik (słupki), filtr drużyny w tabeli per zawodnik, przycisk „Pobierz analitykę (JSON)” (json-full dla 1 meczu).
- **LeaguePage**: eksport z opcją „json (z pełną analityką)” (json-full).
- **ApiClient**: `exportMatchLogs` z formatem; wszystkie endpointy z CODE_REVIEW są obsłużone.

### 1.3 Testy

- **Backend**: ~100 testów (FullMatchEngineSpec, AdvancedAnalyticsSpec, LeagueServiceSpec, MatchSummaryCodecSpec, MatchSummaryDtoCodecSpec, itd.). Testy: xgModelOverride/vaepModelOverride zmieniają wyniki, positionStats (IWP), własnościowe (Poisson suma ≈ 1, xT nieujemne/skończone, Voronoi 0/1).

---

## 2. Niespójności w dokumentacji (do poprawy)

### 2.1 ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md

| Miejsce | Problem | Rekomendacja |
|--------|---------|--------------|
| §2 „mental” (linia ~59) | „brak **teamwork** w generatorze” | **Nieaktualne.** Teamwork jest dodany do generatora (outfieldMentalKeys). Zmienić na: „teamwork dodany do generatora i używany w silniku (roleAndInstructionModifiers)”. |
| §1 tabela algorytmów, VAEP (linia ~19) | „Stałe przyrosty per typ zdarzenia” | **Nieaktualne.** VAEP jest liczony z `vaepModel.valueForEvent(ctx)` (FormulaBasedVAEP lub override). Uzupełnić: „VAEP z modelu (FormulaBasedVAEP lub vaepModelOverride); wartości per zdarzenie z VAEPContext”. |

### 2.2 PRZEBIEG_MECZU_ALGORYTMY_DOPRACOWANIA.md

**Dokument jest w dużej mierze nieaktualny** względem obecnego kodu. Tabela §1.1 mówi m.in.:

- xT: „Częściowo” / „Brak macierzy przejść” → **obecnie**: pełna value iteration, `transitionCountsFromEvents`, `xTValueIteration`.
- I-VAEP: „Nie” → **obecnie**: `vaepByPlayerByEventType`, UI.
- OBV: „Nie” → **obecnie**: `vaepTotal`, UI „OBV (VAEP)”.
- gBRI / Tortuosity: „Nie” → **obecnie**: `ballTortuosity`, `playerTortuosityByPlayer`, UI.
- Voronoi: „Nie” → **obecnie**: `zoneDominanceFromEvents`, `voronoiZoneFromCentroids`, `homeShareByZone`, UI.
- Metabolic: „Nie” → **obecnie**: `metabolicLoad`, `metabolicLoadByPlayer`.
- Nash karne: „Brak” → **obecnie**: zaimplementowane w FullMatchEngine.
- Ghosting/xPass: „Brak” → **obecnie**: xPass, xPass under pressure.

**Rekomendacja:** Zaktualizować tabelę w PRZEBIEG_MECZU_ALGORYTMY_DOPRACOWANIA.md (sekcja 1.1) tak, aby odzwierciedlała stan z ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md, albo dodać na górze dokumentu uwagę: „Stan algorytmów aktualny według ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md; tabela poniżej historyczna”.

### 2.3 REKOMENDACJE_ULEPSZEN.md i CODE_REVIEW.md

- **Format eksportu (REKOMENDACJE §3.1):** „Frontend akceptuje csv i json; backend dla json obsługuje statsbomb” — **brak wzmianki o json-full**. Dodać: format `json-full` (zdarzenia + pełny summary) w backendzie i UI (MatchDetailPage, LeaguePage).
- **ML_INTEGRACJA.md (linia ~114):** „format: csv | statsbomb” — dodać `json-full`.

---

## 3. Co zostało do zrobienia (z dokumentów + kod)

### 3.1 Wysoki priorytet (dokładność / spójność)

1. **Aktualizacja dokumentacji**
   - ANALIZA: poprawka opisu VAEP i teamwork w generatorze (jak w §2).
   - PRZEBIEG_MECZU: zaktualizowanie tabeli algorytmów lub wyraźna adnotacja o „źródle prawdy” (ANALIZA).
   - REKOMENDACJE / ML_INTEGRACJA: uzupełnienie o format `json-full`.

### 3.2 Średni priorytet (rozszerzenia opcjonalne)

2. **Voronoi z (x,y)**  
   Prawdziwy diagram Voronoi wymaga śledzenia pozycji (x,y) graczy w czasie. Obecnie: Voronoi z centrum aktywności stref. Rozszerzenie: opcjonalne zapisywanie pozycji w zdarzeniach i osobny pipeline Voronoi 2D.

3. **EPV/xT w czasie**  
   Wykres wartości stref w funkcji minuty (np. xT per przedział czasowy). Wymaga albo stanu xT per minuta w silniku, albo przybliżenia z zdarzeń (np. rolling window).

4. **Drobne UX**
   - Przycisk „Pobierz .csv” z `download` (blob URL) zamiast tylko wyświetlania tekstu (REKOMENDACJE).
   - Ewentualna wirtualizacja bardzo długich list (terminarz, zdarzenia) — „do rozważenia” (CODE_REVIEW).

5. **Jakość kodu**
   - **FullMatchEngine** (linia ~821): ostrzeżenie kompilatora „Pattern match may not be exhaustive” dla `zones.sliding(2).map { case Seq(a, b) => ... }`. Dodać obsługę np. `case seq if seq.size == 2 => zoneDist(seq(0), seq(1))` lub `.collect { case Seq(a, b) => zoneDist(a, b) }.sum`.
   - **LeagueService.parseGamePlan**: przy nieprawidłowym JSON logować ostrzeżenie (REKOMENDACJE §2.3).

### 3.3 Niski priorytet

6. **Pipeline trenowania xG/VAEP**  
   Zewnętrzny (Python/ONNX); interfejsy Loadable w aplikacji gotowe. Wgrywanie modeli: endpoint admin, `Ref[EngineModels]`.

7. **Dokumenty projektowe**  
   README / .env.example — w REKOMENDACJE oznaczone „Zrobione”. Sprawdzić, czy w repozytorium są aktualne.

8. **Offside / formacja vs formacja / mentality**  
   Wymienione w REKOMENDACJE jako propozycje głębi symulacji; brak w kodzie — opcjonalnie w przyszłości.

---

## 4. Krytyczny przegląd (jakość, ryzyka, luki)

### 4.1 Mocne strony

- **Spójność backend ↔ frontend:** Endpointy z Routes mają odzwierciedlenie w ApiClient; DTO (MatchSummaryDto) spójne między shared, backend codec i frontend decoder.
- **Analityka:** Jedno źródło prawdy — `FullMatchEngine.computeAnalyticsFromEvents`; summary w LeagueService składa się z MatchSummaryAggregator (podstawowe zliczenia) + result.analytics (zaawansowane). Brak duplikacji logiki VAEP/xT.
- **Testy:** Override modeli (xg, vaep) są testowane pod kątem wpływu na wyniki; testy własnościowe dla Poisson i xT; round-trip codeków MatchSummary i MatchSummaryDto.
- **Bezpieczeństwo eksportu:** Limit 50 meczów, sprawdzenie uprawnień do ligi.

### 4.2 Ryzyka i słabe miejsca

- **Wydajność:** `getLeaguePlayerStatsForUser` / `getLeaguePlayerAdvancedStatsForUser` przy fallbacku ładują wszystkie zdarzenia wielu meczów do pamięci (CODE_REVIEW §4.2). Przy bardzo dużej liczbie meczów warto limitować liczbę kolejek lub dodać agregację po stronie bazy.
- **MatchSummaryAggregator vs result.analytics:** Dwa źródła „summary” — agregator liczy podstawowe statystyki z zdarzeń, a zaawansowane pochodzą z silnika. Przy ewentualnej zmianie formatu zdarzeń trzeba pilnować, żeby oba miejsca były zaktualizowane (np. nowy typ zdarzenia).
- **Dokumentacja rozproszona:** Stan algorytmów jest rzetelnie opisany w ANALIZA; PRZEBIEG_MECZU i fragmenty REKOMENDACJE są nieaktualne. Ryzyko: ktoś czyta tylko PRZEBIEG_MECZU i wnioskuje, że I-VAEP/xT/Voronoi „nie są”.
- **Ostrzeżenie kompilatora:** Niewyeliminowane ostrzeżenie exhaustivity w FullMatchEngine (sliding(2)) — drobny dług techniczny.

### 4.3 Luki (braki nieblokujące)

- **Strength w interceptorWeight:** W ANALIZA tabela 2.3 mówi, że strength jest w „Duel, AerialDuel, interceptorWeight (nie wprost)”. W kodzie `interceptorWeight` używa tackling, marking, positioning, anticipation, bravery, aggression — **bez strength**. Strength jest tylko w Duel/AerialDuel. Tabela jest myląca; poprawka: „interceptorWeight: tylko tackling, positioning, marking, anticipation, bravery, aggression (+ Z-Score)”.
- **Frontend:** Brak wirtualizacji długich list; przy setkach meczów/zdarzeń UI może być ciężkie (uznane za niski priorytet w dokumentach).

---

## 5. Podsumowanie: co zrobić w kolejnej iteracji

| Priorytet | Działanie | Pliki |
|-----------|-----------|--------|
| **1** | Poprawić opis VAEP i teamwork w ANALIZA | ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md |
| **1** | Zaktualizować lub zaanotować tabelę algorytmów w PRZEBIEG_MECZU | PRZEBIEG_MECZU_ALGORYTMY_DOPRACOWANIA.md |
| **1** | Dodać format json-full w REKOMENDACJE i ML_INTEGRACJA | REKOMENDACJE_ULEPSZEN.md, ML_INTEGRACJA.md |
| **2** | Usunąć ostrzeżenie exhaustivity w FullMatchEngine (sliding) | FullMatchEngine.scala |
| **2** | Doprecyzować w ANALIZA: interceptorWeight bez strength | ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md |
| **2** | Opcjonalnie: logowanie przy błędzie parseGamePlan | LeagueService.scala |
| **3** | Opcjonalnie: EPV/xT w czasie, Voronoi 2D, przycisk „Pobierz CSV” | Zależnie od decyzji produktowej |

---

*Dokument powstał na podstawie: ANALIZA_ALGORYTMY_ATRYBUTY_VS_KOD.md, CODE_REVIEW.md, REKOMENDACJE_ULEPSZEN.md, PRZEBIEG_MECZU_ALGORYTMY_DOPRACOWANIA.md, ML_INTEGRACJA.md oraz przeglądu kodu (FullMatchEngine, LeagueService, MatchSummaryAggregator, Routes, MatchDetailPage, LeaguePage, ApiClient, DTO/codeki).*
