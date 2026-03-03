# Dopracowania wyliczań symulacji — analiza całości projektu

**Zakres:** Pełny przegląd FullMatchEngine, AnalyticsModels, PitchControl, DxT, AdvancedAnalytics i powiązanych modułów. Cel: wskazanie, **co można jeszcze dopracować** w wyliczaniu (formuły, stałe, brakujące czynniki), bez zmiany architektury.

---

## 1. Gdzie i jak liczymy — skrót

| Obszar | Gdzie | Jak (skrót) |
|--------|--------|--------------|
| **Typ zdarzenia** | `generateNextEvent` | Jeden los `eventTypeRoll` ∈ [0,1]; progi 0.42 (Pass), 0.445+ (Shot), 0.52 (Foul/Penalty), 0.53 (Clearance), … 0.79 (Pass default). |
| **Kto gra** | `actorWeight` | Ważony wybór aktora: strefa (atak/buildup), rola (playmaker, forward), offTheBall, dopasowanie pozycji. |
| **Sukces podania** | Pass branch | `passInterceptProb` (przechwyt) vs `passSuccessBaseClamped` (udane/missed). Baza 0.88 + mentalBonus (atrybuty) + morale + fatigue + …; clamp [0.62, 0.94]. |
| **xG strzału** | `FormulaBasedxG` + silnik | Odległość, strefa, głowa, presja, angularPressure, gkDistance; potem mnożnik atrybutów (finishing, composure, technique, heading, longShots) i morale/leadership. |
| **Wynik strzału** | Shot branch | `isGoal = rng < xg`; jeśli nie gol: `savedProb` (GK + placement), potem 0.6 Missed / 0.4 Blocked. |
| **Pitch Control** | `PitchModel.PitchControl` | Time-to-intercept z pace/acceleration (lub dystans), zmęczenie; P(control) ∝ exp(-time/timeScale). |
| **DxT** | `DxT.threatMap` | baseZoneThreat(z) × (1 − 0.6×opponentControl). |
| **VAEP** | `FormulaBasedVAEP` | Stałe wartości per typ zdarzenia × timeWeight × tightBonus. |

---

## 2. Propozycje dopracowań (analitycznie)

### 2.1 Rozkład typów zdarzeń (progi 0.42, 0.445, …)

**Stan:** Stałe progi w kodzie. Kolejno: Pass <0.42, Shot 0.42–(0.445+bonus), Foul/Penalty/Injury 0.52, Clearance 0.53, Corner 0.54, ThrowIn 0.57, Cross 0.61, Przechwyt 0.67, Dribble 0.74, Duel 0.75, AerialDuel 0.76, FreeKick 0.77, Offside 0.78, Sub 0.79, wreszcie Pass.

**Problem:** Brak powiązania z docelową statystyką meczu (np. ~25–35 strzałów, ~400–500 podań, ~10–15 fauli). Przy ~1 zdarzenie na 2–8 s wychodzi ~700–2700 zdarzeń; faktyczna liczba zależy od `secondDelta` i progów.

**Dopracowanie:**
- Wprowadzić **docelowe proporcje** (np. Pass ~55%, Shot ~3%, Foul ~2%, …) i **kalibrację**: albo stałe wyliczyć z tych proporcji, albo w każdym „oknie” (np. 15 min) korygować progi, żeby zbliżyć się do celu (np. mniej strzałów po serii nieudanych ataków).
- Opcjonalnie: plik konfiguracyjny (np. `event_type_weights` / `target_ratios`) zamiast na sztywno w kodzie.

**Proporcje orientacyjne (do kalibracji):** Pass ~55%, Shot ~3%, Foul ~2%, Clearance ~2%, Corner ~1%, ThrowIn ~1%, Cross ~2%, Przechwyt ~4%, Dribble ~5%, Duel ~3%, AerialDuel ~1%, FreeKick ~1%, Offside ~0.5%, Sub ~0.2%. Progi w `EngineConstants` (EventPassThreshold itd.) można dostosować pod te wartości.

---

### 2.2 Prawdopodobieństwo sukcesu podania (passSuccessBase)

**Stan:**  
`passSuccessBase = (0.88 - fatigueMissBonus + mentalBonus + passBonus + passingMod + posPenalty + …) * moraleMod * lateGameConcentration * leadershipMod`  
gdzie `mentalBonus` = liniowa kombinacja (decisions, vision, passing, firstTouch, ballControl, technique) z wagami 0.002–0.005.

**Problemy:**
- Baza 0.88 i przedział [0.62, 0.94] są arbitralne.
- Brak **trudności podania**: dystans (strefa → strefa), kąt, presja na odbiorcy — wszystko już mamy (strefa, `receiverPressure`, `opponentControl`), ale na passSuccess wpływają tylko pośrednio (przez `passInterceptProb`). Trudność mogłaby obniżać bazę lub podnosić wymagania od atrybutów.
- **xPass w metadata**: obecnie `0.72 + rng*0.22` (losowe). Powinno być **spójne z modelem**: np. ΔxT (strefa docelowa − strefa źródłowa) lub prosta formuła P(udane podanie | zoneFrom, zoneTo, pressure).

**Dopracowanie:**
- Wprowadzić **passDifficulty(zoneFrom, zoneTo, receiverPressure)** (np. 0.7–1.0) i mnożyć bazę przez ten współczynnik (albo odejmować od bazy).
- **xPass w zdarzeniu**: liczyć z xT lub z passDifficulty i atrybutów, zamiast losu; wtedy xPass w analityce i „xPass under pressure” będą spójne z tym, co symulujemy.

**Zaimplementowane (stan 2026-03):** `passDifficulty(zoneFrom, zoneTo, receiverPressure)` jest w silniku i mnoży `passSuccessBase` (współczynnik 0.7–1.0). **xPass w metadata** jest liczony z `xPassFromModel(dxtByZone, zoneFrom, zoneTo, receiverPressure)` (DxT strefy docelowej minus kara za presję), nie z losu.

---

### 2.3 Przechwyt (passInterceptProb)

**Stan:**  
`passInterceptProb = (0.05 + opponentControl*0.12 + interceptBonus + matchupPressure + oiBonus + defenderPressBonus + …).min(0.55)`.

**Problemy:**
- Stałe 0.05, 0.12, 0.55 nie są powiązane z danymi.
- Brak **trudności podania**: długie podanie (strefa 3→10) może być trudniejsze do przechwycenia niż krótkie w zatłoczonej strefie (można to odwrócić: w zatłoczeniu wyższe P(przechwyt)).

**Dopracowanie:**
- Uwzględnić **dystans podania** (np. |zoneTo − zoneFrom|): przy większym dystansie lekko podnieść passInterceptProb (więcej czasu na reakcję obrońcy).
- Opcjonalnie: osobne stałe w pliku konfiguracyjnym (np. `intercept_base`, `intercept_control_factor`, `intercept_cap`) pod kalibrację.

**Zaimplementowane (stan 2026-03):** Dystans podania `zoneDistance = |targetZone − zone|` jest uwzględniony w `passInterceptProb`: składnik `zoneDistance * EngineConstants.InterceptPerZoneDistance` (0.008 na jednostkę strefy).

---

### 2.4 xG (FormulaBasedxG)

**Stan:**  
`distFactor = exp(-distance/18)`, `zoneFactor`, `headerPenalty`, `pressurePenalty`, `angularPenalty`, `gkFactor`, `timeFactor`; wynik w [0.01, 0.95].

**Problemy:**
- `baseDistanceFactor = 18`, `zoneBonus = 0.08` — nie wynikają z kalibracji na prawdziwych xG (np. Opta/StatsBomb).
- W silniku **mnożnik atrybutów** (finishing, composure, technique, heading, longShots) jest rozsądny, ale zakres (np. 0.90 + finishing/20*0.10) jest szacunkiem.

**Dopracowanie:**
- **Kalibracja na danych:** jeśli są dostępne strzały z xG, dopasować `baseDistanceFactor` i `zoneBonus` (np. regresja lub grid search), żeby średnie xG per strefa/odległość zbliżyć do danych.
- **xgCalibration** już jest w `LeagueContextInput` — można go użyć do sezonu/ligi; dopracować dokumentację, że skaluje końcowe xG (np. dla ligi defensywnej < 1).

**Dokumentacja xgCalibration:** W `LeagueContextInput.xgCalibration: Option[Double]` — gdy ustawione (np. 1.05 dla ligi ofensywnej lub 0.95 dla defensywnej), końcowe xG strzału jest mnożone przez ten współczynnik przed losowaniem gola. Umożliwia korektę ligową bez zmiany modelu ML.

---

### 2.5 Wynik strzału (Saved / Missed / Blocked)

**Stan:**  
Jeśli nie gol: `savedProb` z GK (reflexes, handling, positioning, diving, oneOnOnes) i placement (center vs left/right); potem jedna losowa szansa na Saved; jeśli nie Saved, to 0.6 Missed / 0.4 Blocked.

**Problemy:**
- Stosunek Missed do Blocked (0.6 : 0.4) jest stały; w rzeczywistości zależy od presji (blok) vs strzały z dystansu (miss).
- **Placement** jest losowy (0.25 left, 0.5 center, 0.25 right); nie zależy od pozycji GK ani od atrybutów strzelca (technique, composure).

**Dopracowanie:**
- **Placement:** uwzględnić pozycję GK (np. jeśli GK bliżej lewego słupka, mniejsza P(placement = "left") dla strzelca) lub prosty model „celowanie” (composure/technique → mniejsza wariancja kierunku).
- **Missed vs Blocked:** zależność od `pressureCount` / `angularPressure`: przy wysokiej presji wyższy udział Blocked.

---

### 2.6 Drybling (Dribble vs DribbleLost)

**Stan:**  
`dribbleSuccessProb = 0.55 + (dribbling-10)*0.008 + (agility-10)*0.006 - (avgDefTackling-10)*0.004 - (avgDefAgility-10)*0.002 + zScore + flair*random`.

**Problemy:**
- Używamy **średniego** tacklingu i zwinności obrońców; w FM często liczy się **najbliższy** obrońca lub obrońca w strefie. Jeden silny obrońca przy piłce mógłby bardziej obniżać P(sukces).
- Stałe 0.55, 0.008, 0.006, … — do kalibracji.

**Dopracowanie:**
- **Presja w strefie:** zamiast (lub oprócz) średniej obrońców użyć np. max(tackling, agility) obrońcy najbliższego strefy piłki albo wagi od odległości (najbliższy ma największy wpływ).
- Ewentualnie konfigurowalne współczynniki (dribbling, agility, defTackling, defAgility) w configu.

---

### 2.7 Zmęczenie (updateFatigue)

**Stan:**  
`delta = deltaMinutes * 0.008 * tMult * pMult / (staminaFactor * nfFactor) * wrFactor * freshnessFactor`; clamp do 1.0.

**Problemy:**
- Stała 0.008 i mnożniki (tempo, pressing, stamina, naturalFitness, workRate) są szacunkowe; brak powiązania z np. dystansem przebieżonym w meczu (mamy `estimatedDistanceByPlayer` w analityce, ale nie w pętli symulacji).
- Zmęczenie rośnie równomiernie w czasie; w realnych meczach są okresy wysokiej intensywności (pressing, kontry).

**Dopracowanie:**
- **Intensywność chwili:** np. przy zdarzeniach w strefach 7–12 (pressing) lub po PassIntercepted (kontratak) dodać krótkotrwały mnożnik do `delta` (np. 1.2–1.5), żeby zmęczenie rosło szybciej w takich fazach.
- Opcjonalnie: w kolejnych iteracjach używać `estimatedDistanceByPlayer` z poprzedniego „okna” do korekty (bardziej zaawansowane).

---

### 2.8 Pitch Control (timeScale, distanceScale)

**Stan:**  
Time-to-intercept z pace/acceleration; wpływ = exp(-time/timeScale); przy braku pace/acc: exp(-dist/distanceScale). timeScale=2.5, distanceScale=12.

**Problemy:**
- Różnica między drużyną szybką a wolną może być niewystarczająco widoczna w wyniku (np. 2–1); współczynniki paceNorm/accNorm (3+pace/20*5, 2+acc/20*4) można by przetestować pod kątem „czułości” na pace 15 vs 10.

**Dopracowanie:**
- **Testy czułości:** symulacje z drużyną pace 15 vs 10 i sprawdzenie, czy udział kontroli w strefach ataku rośnie dla szybszej drużyny; ewentualnie lekko podbić wagę pace (np. szerszy zakres paceNorm).
- Stałe timeScale/distanceScale w jednym miejscu (np. `EngineConstants` lub config), żeby łatwo kalibrować.

---

### 2.9 VAEP (FormulaBasedVAEP)

**Stan:**  
Stałe wartości per typ zdarzenia (Pass +0.015+zoneThreat, Goal +0.28, PassIntercepted -0.02/0.022, …) × timeWeight(minute) × tightBonus(score).

**Problemy:**
- Brak zależności od **jakości strefy** (np. Pass w strefie 12 wart więcej niż w strefie 2); dla Pass jest zoneThreat, dla innych typów nie.
- Prawdziwy VAEP to model ML (P_scores, P_concedes); formuła jest przybliżeniem.

**Dopracowanie:**
- Dla zdarzeń w ataku (Dribble, Cross, Tackle w strefie 9–12) dodać mnożnik zależny od strefy (np. zoneThreat(z)), żeby akcje w „niebezpiecznej” strefie miały wyższą wartość.
- Dla przyszłego modelu ML: interfejs `VAEPModel` jest gotowy; trenowanie na wyeksportowanych zdarzeniach (json-full) i ładowanie przez `vaepModelOverride`.

---

### 2.10 Stałe w jednym miejscu

**Stan:**  
Dziesiątki „magic numbers” w FullMatchEngine (0.42, 0.88, 0.62, 0.94, 0.55, 0.06, 0.35, …), w PitchControl (2.5, 12), w FormulaBasedxG (18, 0.08), w FormulaBasedVAEP (0.015, 0.28, …).

**Dopracowanie:**
- Wydzielić **EngineConstants** (object lub config): progi typów zdarzeń, baza i granice pass success, intercept cap, współczynniki fatigue, Pitch Control (timeScale, distanceScale), ewentualnie wybrane współczynniki xG/VAEP.
- Ułatwi to: (1) kalibrację bez grzebania w logice, (2) różne „presety” (np. liga defensywna vs ofensywna), (3) testy A/B (jedna stała wyższa/niższa).

---

### 2.11 Rola i pozycja (wagi per slot)

**Stan:**  
`actorWeight` i `interceptorWeight` używają nazw ról (playmaker, poacher, ball_winner, …) i stałych bonusów (0.5, 0.35, 0.4, …). Atrybuty mają stałe wagi (np. tackling 0.02, positioning 0.015 w interceptorWeight).

**Problemy:**
- W FM wagi atrybutów zależą od **pozycji** (np. dla bramkarza reflexes/agility ważniejsze niż dla napastnika). U nas jedna waga dla wszystkich.
- Nowe role w `slotRoles` nie zmieniają wag bez zmiany kodu.

**Dopracowanie:**
- **Opcjonalnie:** konfiguracja „position/role → lista atrybutów z wagami” (np. JSON: `{"CB": {"tackling": 0.03, "heading": 0.02, ...}}`) i używanie jej w `interceptorWeight` / `actorWeight` zamiast jednego zestawu stałych. Na początek wystarczy np. osobne wagi dla „defender” vs „midfielder” vs „forward”.
- To większa zmiana; można zacząć od udokumentowania obecnych wag i dodania 1–2 profili (np. „defender”, „attacker”) w kodzie.

---

### 2.12 xPass w zdarzeniach i spójność z xPassValueFromEvents

**Stan:**  
Analityka xPass (pass value, under pressure) liczy się z `xPassValueFromEvents` na podstawie przejść stref i xT; metadatum `xPass` w pojedynczym zdarzeniu jest losowe (0.72–0.94 lub 0.3–0.7 dla Cross).

**Problem:** Rozjazd między tym, „co symulujemy” (sukces podania z passSuccessBase), a tym, „co podajemy jako xPass” (los). Analiza „xPass under pressure” jest sensowna, bo używa xT i presji; ale wartość w metadata nie odzwierciedla ani trudności podania, ani modelu xPass.

**Dopracowanie:**
- Dla każdego udanego Pass/LongPass **liczyć xPass** jako np. wartość strefy docelowej (xT) względem strefy źródłowej, ewentualnie skorygowaną o receiverPressure (presja obniża „oczekiwaną” wartość), i zapisywać w metadata.
- Wtedy: (1) metadata jest spójna z analityką, (2) eksport (json-full) ma realistyczne xPass per zdarzenie, (3) ewentualny trening modelu xPass ma sensowne etykiety.

---

## 3. Priorytetyzacja

| Priorytet | Dopracowanie | Wysiłek | Wpływ |
|-----------|--------------|--------|--------|
| **Wysoki** | xPass w metadata z modelu (strefa, xT, presja) zamiast losu | Średni | Spójność analityki i eksportu |
| **Wysoki** | Stałe w EngineConstants / config (progi zdarzeń, pass, intercept, fatigue, Pitch Control) | Niski | Kalibracja, testy, presety |
| **Średni** | passDifficulty(zoneFrom, zoneTo, pressure) i powiązanie z passSuccess | Średni | Realizm podań |
| **Średni** | Placement strzału zależny od GK/composure; Missed vs Blocked od presji | Niski–średni | Realizm strzałów |
| **Średni** | Intensywność w updateFatigue (pressing / kontra) | Niski | Realizm zmęczenia |
| **Niski** | Kalibracja FormulaBasedxG (baseDistanceFactor, zoneBonus) na danych | Zależy od danych | Trafność xG |
| **Niski** | VAEP: mnożnik strefy dla Dribble/Cross/Tackle w ataku | Niski | Lepsze VAEP bez ML |
| **Niski** | Drybling: wpływ najbliższego obrońcy zamiast średniej | Średni | Realizm dryblingu |
| **Niski** | Proporcje zdarzeń z configu / kalibracja do docelowych statystyk meczu | Średni | Realistyczne liczby strzałów/podań |

---

## 4. Podsumowanie

- **Silnik jest spójny z podejściem FM:** zdarzenie po zdarzeniu, atrybuty → prawdopodobieństwa, taktyka i kontekst (strefa, zmęczenie, morale, rola). To dobrą podstawę do dopracowań.
- **Główne kierunki dopracowania wyliczań:**  
  (1) **Spójność**: xPass w metadata z modelu (xT + presja), placement/Missed–Blocked z kontekstem.  
  (2) **Kalibrowalność**: stałe w jednym miejscu (EngineConstants/config), ewentualnie docelowe proporcje zdarzeń.  
  (3) **Kontekst**: passDifficulty, intensywność zmęczenia, wpływ najbliższego obrońcy przy dryblingu.  
  (4) **Dane**: kalibracja xG i ewentualnie pass/intercept na danych, gdy będą dostępne.
- Wprowadzanie tych zmian ma sens **iteracyjnie**: najpierw stałe + xPass w metadata, potem passDifficulty i placement/Missed–Blocked, na końcu zaawansowane (proporcje zdarzeń, role/pozycja z configu).

---

## 5. Co zrobione i co dalej

### 5.1 Zaimplementowane (stan po ostatniej iteracji)

- **EngineConstants** — wszystkie kluczowe stałe w jednym miejscu (progi zdarzeń, pass, intercept, fatigue, Pitch Control, xG, shot, dribble).
- **passDifficulty(zoneFrom, zoneTo, receiverPressure)** — mnożnik bazy P(sukces podania); presja i dystans stref obniżają sukces.
- **xPass w metadata** — liczone z modelu (DxT strefy docelowej, kara za presję), nie los; w Pass/LongPass jest też `receiverPressure`.
- **Przechwyt** — dodany składnik za dystans podania (InterceptPerZoneDistance); stałe z EngineConstants.
- **Strzał** — placement zależny od composure/technique; Missed vs Blocked zależne od pressureCount i angularPressure.
- **Zmęczenie** — parametr `isHighIntensity` (strefy 7–12, kontratak); mnożnik FatigueIntensityMultiplier.
- **Drybling** — waga najbliższego obrońcy do strefy piłki (DribbleNearestDefenderWeight) + średnia reszty; stałe w EngineConstants.
- **PitchControl / FormulaBasedxG** — używają EngineConstants (timeScale, distanceScale, XGBaseDistanceFactor, XGZoneBonus).
- **FormulaBasedVAEP** — mnożnik strefy dla Dribble, Cross, Tackle w strefach 9–12 (wyższa wartość w ataku).
- **Testy** — EngineConstantsSpec (stałe w zakresach), FullMatchEngineSpec: xPass w [0.5, 0.95], VAEP strefa, proporcje zdarzeń.

### 5.2 Co następnie warto zrobić (w kolejności)

| Kolejność | Zadanie | Źródło | Uwagi |
|-----------|---------|--------|--------|
| **1** | **Proporcje zdarzeń z configu** — docelowe % (Pass ~55%, Shot ~3%, …) albo plik config (event_type_weights / target_ratios); opcjonalnie korekta progów w „oknach” czasowych. | §2.1 | Średni wysiłek; realistyczne liczby strzałów/podań na mecz. |
| **2** | **Rola/pozycja: wagi atrybutów** — config „pozycja/rola → wagi” (np. JSON) lub proste profile defender / midfielder / forward w `actorWeight` i `interceptorWeight`. | §2.11 | Większa zmiana; na początek 2–3 profile w kodzie. |
| **3** | **Kalibracja xG na danych** — dopasowanie baseDistanceFactor i zoneBonus do prawdziwych xG (Opta/StatsBomb), gdy będą dane. | §2.4 | Zależne od dostępności danych. |
| **4** | **Inne (ANALIZA_KOLEJNA_ITERACJA)** — logowanie przy błędzie `parseGamePlan` (LeagueService); przycisk „Pobierz CSV” z `download`; opcjonalnie EPV/xT w czasie, Voronoi 2D. | Review §3.2–3.3 | Drobne UX i jakość kodu. |

**Podsumowanie:** Najbardziej naturalny następny krok to **proporcje zdarzeń z configu** (§2.1) — albo plik konfiguracyjny z docelowymi udziałami typów zdarzeń, albo wyliczenie progów z tych udziałów, żeby statystyki meczu (np. ~25–35 strzałów, ~400–500 podań) były zbliżone do docelowych. Potem można dodać **profile pozycji** (defender/midfielder/forward) w wagach aktora i obrońcy (§2.11).
