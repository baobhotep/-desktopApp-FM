# Football Manager AI Strategy Game — Dokument Projektowy

## Wykorzystanie Algorytmów Analityki Piłkarskiej w Grze Strategicznej AI vs AI

**Rola**: Analityk danych / Architekt Scala / Ekspert futbolowy  
**Data**: Luty 2026  
**Typ gry**: Turowa gra strategiczna — "Football Manager" dla AI agentów i ludzkich trenerów  
**Spójność z**: `ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md`, `SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md`, `FORMACJE_ROLE_TAKTYKA.md`, `WYMAGANIA_GRY.md`, `MODELE_I_PRZEPLYWY_APLIKACJI.md`. Kontrakt silnika, API, schemat bazy, auth, UI, testy → `KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md`.

---

## Spis treści

1. [Wizja Gry](#1-wizja-gry)
2. [Architektura Systemu (Scala)](#2-architektura-systemu-scala)
3. [Warstwa 1: Silnik Symulacji Meczu](#3-warstwa-1-silnik-symulacji-meczu)
4. [Warstwa 2: Taktyczny System Decyzyjny](#4-warstwa-2-taktyczny-system-decyzyjny)
5. [Warstwa 3: Analityka i Feedback](#5-warstwa-3-analityka-i-feedback)
6. [Mapowanie Algorytmów na Mechaniki Gry](#6-mapowanie-algorytmów-na-mechaniki-gry)
7. [System Atrybutów Zawodników](#7-system-atrybutów-zawodników)
8. [Stałe Fragmenty Gry](#8-stałe-fragmenty-gry)
9. [System Fizyczny i Zmęczenie](#9-system-fizyczny-i-zmęczenie)
10. [AI Agenci — Przeciwnicy i Uczenie](#10-ai-agenci--przeciwnicy-i-uczenie)
11. [Pętla Rozgrywki i Uczenie się na Błędach](#11-pętla-rozgrywki-i-uczenie-się-na-błędach)
12. [Stos Technologiczny (Scala Ecosystem)](#12-stos-technologiczny-scala-ecosystem)
13. [Przykładowy Przebieg Meczu — Krok po Kroku](#13-przykładowy-przebieg-meczu--krok-po-kroku)

---

## 1. Wizja Gry

### Koncepcja

Gra strategiczna, w której **gracze (ludzie lub agenci AI) pełnią rolę trenerów** — ustalają formacje, taktykę, podejście do gry, instrukcje indywidualne i stałe fragmenty. Następnie ich zespoły mierzą się w symulowanych meczach, gdzie wynik jest obliczany na podstawie zaawansowanych algorytmów analityki piłkarskiej.

### Kluczowe założenia

1. **Zero losowości bez przyczyny**: Każdy element losowości ma matematyczne uzasadnienie (rozkład Poissona, Monte Carlo, prawdopodobieństwa z modeli ML). Gracz rozumie, DLACZEGO przegrał.

2. **Głębokość taktyczna**: Gracz może kontrolować:
   - Formację bazową i warianty (z/bez piłki)
   - Styl budowania (krótki/długi, przez środek/boki)
   - Pressing (wysokość, intensywność, triggery)
   - Linia obrony i pułapka ofsajdowa
   - Instrukcje indywidualne dla każdego zawodnika
   - Stałe fragmenty gry (rożne, rzuty wolne)
   - Plany B/C i triggery zmian taktycznych w trakcie meczu

3. **Transparentność algorytmów**: Po meczu gracz widzi DOKŁADNIE, jak algorytmy oceniły jego taktykę — mapy Pitch Control, wartości xT, VAEP akcji, Field Tilt, sieci podań.

4. **Uczenie na błędach**: System analityczny post-match wskazuje konkretne błędy taktyczne (Ghosting) i sugeruje poprawki.

---

## 2. Architektura Systemu (Scala)

### Warstwy systemu

```
┌─────────────────────────────────────────────────┐
│           UI / API Layer (REST/WebSocket)         │
│  Gracz ustala taktykę → wysyła GamePlan           │
├─────────────────────────────────────────────────┤
│        WARSTWA TAKTYCZNA (Tactical Engine)         │
│  Interpretacja GamePlan → parametry symulacji      │
│  Algorytmy: Field Tilt, Pressing Model, Formation │
├─────────────────────────────────────────────────┤
│        WARSTWA SYMULACJI (Match Engine)            │
│  Symulacja akcja-po-akcji (Event-Based Simulation) │
│  Algorytmy: xG, xT, VAEP, Pitch Control,          │
│  Dixon-Coles, Monte Carlo, Poisson                 │
├─────────────────────────────────────────────────┤
│        WARSTWA FIZYCZNA (Physical Layer)           │
│  Zmęczenie, obciążenie, prędkość, kontuzje        │
│  Algorytmy: Metabolic Power, ACWR                  │
├─────────────────────────────────────────────────┤
│        WARSTWA ANALITYCZNA (Post-Match)            │
│  Feedback dla gracza: raporty, mapy, grafy         │
│  Algorytmy: Ghosting, Passing Networks, WPA        │
├─────────────────────────────────────────────────┤
│        WARSTWA DANYCH (Data Layer)                 │
│  Baza zawodników, historie meczów, statystyki      │
│  Scala: Slick / Doobie + PostgreSQL                │
└─────────────────────────────────────────────────┘
```

### Kluczowe typy domenowe (Scala 3)

> **Uwaga**: Pełna definicja Player i atrybutów → patrz `SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md` §3.1  
> **Uwaga**: Pełna definicja formacji, ról i pozycji → patrz `FORMACJE_ROLE_TAKTYKA.md`  
> Poniższy model domenowy jest uproszczoną wersją referencyjną.

```scala
// --- MODELE DOMENOWE (referencja → SILNIK doc §3.1 + ATRYBUTY doc) ---

case class Player(
  id: PlayerId,
  name: String,
  preferredPositions: Set[Position],
  physical: PhysicalAttributes,     // 8 atrybutów, skala 1-20
  technical: TechnicalAttributes,   // 10 atrybutów, skala 1-20 (lub GKAttributes dla bramkarza)
  mental: MentalAttributes,         // 12 atrybutów, skala 1-20
  traits: PlayerTraits,             // 9 modyfikatorów
  bodyParams: BodyParams,           // wzrost, waga, wiek
  matchState: MatchState            // dynamiczny stan w trakcie meczu
)

// Pełne definicje PhysicalAttributes, TechnicalAttributes, MentalAttributes,
// GKAttributes, PlayerTraits, BodyParams, MatchState
// → patrz SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md §3.1

// --- TAKTYKA (GamePlan) — ŹRÓDŁO PRAWDY: FORMACJE_ROLE_TAKTYKA.md §15 ---
//
// Pełna definicja typów (Formation, GamePlan, BuildUpStyle, PressingStrategy,
// DefensiveLine, AttackingApproach, PlayerInstruction, SetPieceConfig,
// TacticalTrigger, TriggerCondition, TacticalChange, Zone, Position, PositionSlot)
// oraz katalog ról i presetów formacji znajduje się w FORMACJE_ROLE_TAKTYKA.md.
//
// GamePlan (skrót): formation, mentality, buildUpStyle, pressingStrategy,
// defensiveLine, attackingApproach, playerInstructions, setPieces: SetPieceConfig,
// inGameTriggers, alternatePlans (Plan B/C).
// Formation: positions (10× slot + x,y), positionsInPossession, positionsOutOfPossession, roles.
// BuildUpStyle: progressionPreference, tempo, riskTolerance, widthInPossession, directness (Double).
// Triggery: ChangeFormation, ChangeMentality, ChangePressingLine, ChangeRole,
// MakeSubstitution, AddInstruction, SwitchToGamePlan.
```

---

## 3. Warstwa 1: Silnik Symulacji Meczu

### Przebieg symulacji

Mecz jest symulowany jako **sekwencja zdarzeń (Event-Based Simulation)** — nie klatka po klatce, ale zdarzenie po zdarzeniu. Każde zdarzenie (podanie, drybling, strzał, odbiór) jest rozstrzygane probabilistycznie.

### Algorytmy w silniku meczu i ich zastosowanie:

#### A) Generowanie akcji — xT i DxT

**Zastosowanie w grze**: Decyduje, GDZIE zespół próbuje grać piłką.

```
Mechanika:
1. Oblicz macierz xT dla formacji obu zespołów
2. Dostosuj dynamicznie (DxT) na podstawie pozycji zawodników
3. Zespół z niskim riskTolerance / wysokim tempo (budowanie krótkie) wybiera ścieżki
   przez strefy o najwyższym gradiencie xT (małe, ale pewne postępy)
4. Zespół z wysokim directness (gra bezpośrednia) szuka skoków xT (długie podania
   w strefy wysokiego zagrożenia)
```

**Parametry z GamePlan wpływające na xT**:
- `progressionPreference` → wagi stref (centralny vs boczny xT)
- `riskTolerance` → czy wybiera ścieżki o wyższym xT ale niższym xPass
- `tempo` → szybkość przesuwania piłki przez strefy (wpływa na DxT — szybki atak utrudnia obrońcom zajęcie pozycji)

#### B) Rozstrzyganie podań — xPass + Pitch Control

**Zastosowanie**: Czy podanie dochodzi do adresata.

```
Mechanika:
1. Oblicz Pitch Control w punkcie docelowym podania
   - Zależy od pozycji obrońców (formacja rywala!)
   - Zależy od prędkości/przyspieszenia atakujących i broniących
2. Oblicz xPass na podstawie:
   - Odległość i kąt podania
   - Atrybut 'shortPassing' / 'longPassing' podającego (zależnie od dystansu)
   - Presja na podającym (Dynamic Pressure Model)
   - Pitch Control w punkcie docelowym
3. Losuj wynik z rozkładu Bernoulliego z p = xPass
```

**Kluczowe**: Formacja rywala BEZPOŚREDNIO wpływa na xPass — jeśli rywale grają wysoki pressing, podania w budowaniu mają niższe xPass. Jeśli grają w niskim bloku, podania w tercji ataku mają niższe xPass.

#### C) Rozstrzyganie strzałów — xG Next-Gen

**Zastosowanie**: Czy strzał kończy się bramką.

```
Mechanika:
1. Oblicz xG na podstawie:
   - Pozycja strzału (odległość, kąt)
   - Angular Pressure (obrońcy w stożku)
   - Goalkeeper Distance (pozycja bramkarza)
   - Typ akcji poprzedzającej (kontra +bonus, stały fragment +bonus)
   - Atrybut 'finishing' (bliski dystans) / 'longShots' (daleki) strzelca
   - Atrybut 'composure' × presja sytuacyjna, 'technique' (woleje)
2. Modyfikatory z taktyki:
   - shootingRange z GamePlan → częstość strzałów z dystansu (niższe xG)
   - tempoInFinalThird → szybki tempo = mniej czasu na ustawienie = niższe xG
     ale więcej szans na bramkę z kontry
3. Post-shot model: jeśli strzał celny, PSxG decyduje o bramce
   - Atrybut bramkarza modyfikuje PSxG
```

#### D) Rozstrzyganie 1v1 — Individual Win Probability

**Zastosowanie**: Dryblingi, walki o piłkę, pojedynki w powietrzu.

```
Mechanika:
1. Z-Score Profiling obu zawodników w kontekście pojedynku:
   - Drybling: dribbling+agility+pace vs tackling+agility+anticipation
   - Powietrze: jumping+heading+strength vs jumping+heading+strength
   - Sprint: pace+acceleration vs pace+acceleration
   - Fizyczny: strength+balance vs strength+balance
2. IWP = σ(Z_att - Z_def + kontekst)
   - σ = funkcja logistyczna
   - kontekst = zmęczenie, pozycja na boisku, momentum, composure
3. Losuj wynik z Bernoulli(IWP)
```

#### E) Generowanie bramek — Dixon-Coles na poziomie meczu

**Zastosowanie**: "Sanity check" — ogólna oczekiwana liczba bramek w meczu.

```
Mechanika:
1. Na podstawie GamePlanów obu zespołów oblicz:
   - Siła ataku zespołu A (α_A) = f(jakość zawodników, taktyka ofensywna)
   - Siła obrony zespołu B (β_B) = f(jakość obrońców, taktyka defensywna)
2. λ_A = α_A × β_B × (home_advantage jeśli dotyczy)
3. Symuluj mecz zdarzenie po zdarzeniu
4. Weryfikuj: czy łączna liczba bramek jest spójna z λ (Poisson)
   - Jeśli odchylenie zbyt duże → korekcja (anti-snowball mechanism)
```

#### F) Pressing — PPDA i Dynamic Pressure

**Zastosowanie**: Jak pressing jednego zespołu wpływa na budowanie drugiego.

```
Mechanika:
1. PressingStrategy gracza → PPDA_target
   - pressingLine=90, intensity=90 → PPDA ~7 (Klopp-level pressing)
   - pressingLine=40, intensity=40 → PPDA ~15 (niski blok)
2. Faktyczny PPDA zależy też od:
   - Atrybuty pressujących (workRate, pace, stamina)
   - Zmęczenie (PPDA rośnie z czasem — pressing słabnie)
3. Dynamic Pressure na każdym podającym:
   - P_pressure = 1 - Π(1 - p_i) dla każdego pressującego obrońcy
   - Wpływa na xPass, xT dostępnych stref, ryzyko straty
```

---

## 4. Warstwa 2: Taktyczny System Decyzyjny

### A) Formacja → Pitch Control

**Mechanika**: Formacja obu zespołów generuje mapę Pitch Control — kto kontroluje jakie strefy boiska.

```
Algorytm:
1. Umieść 10 polowych zawodników na pozycjach wynikających z formacji
2. Dla każdego punktu boiska (siatka 120×80) oblicz:
   - Time-to-intercept dla każdego gracza obu zespołów
   - P_control = Σ P(intercept_i) dla zespołu / Σ obu
3. Wynik: mapa 120×80 z wartościami 0-1

Formacja 4-3-3 vs 5-3-2:
- 4-3-3 kontroluje więcej przestrzeni na skrzydłach
- 5-3-2 kontroluje więcej w środku pola i blisko własnej bramki
- → xT stref bocznych wyższy dla 4-3-3, 
    stref centralnych wyższy dla 5-3-2
```

**Jak gracz to odczuwa**: Wybór formacji bezpośrednio wpływa na to, GDZIE jego zespół tworzy szanse i GDZIE jest podatny. 4-3-3 z szeroką grą generuje dośrodkowania, 3-5-2 ze wąskim środkiem generuje podania prostopadłe.

### B) Matchup Matrix — Klucz do Strategii

**Mechanika**: Dla każdej pary zawodnik atakujący ↔ zawodnik broniący oblicz IWP.

```
Matchup_Matrix[att_i][def_j] = IWP(att_i, def_j)

Przykład:
                    CB1(slow,strong)  CB2(fast,weak)   LB(fast,avg)
Fast_Winger         0.65              0.35             0.45
Strong_Striker      0.40              0.70             0.55
Tricky_AM           0.55              0.50             0.50
```

**Taktyczna implikacja**: Gracz widzi (lub dedukuje z analizy), że jego szybki skrzydłowy ma 65% szans na wygranie 1v1 z wolnym środkowym obrońcą rywala, ale tylko 35% z szybkim. Powinien kierować ataki TAM.

**Algorytm z GamePlan**: `playerInstructions` z `cutInsideOutside` i `runFrequency` kierują ataki w stronę korzystnych matchupów.

### C) Passing Network Quality — Wpływ na posiadanie

**Mechanika**: Jakość sieci podań zespołu determinuje efektywność posiadania.

```
Algorytm:
1. Zbuduj graf podań na podstawie formacji i atrybutów
2. Oblicz metryki sieciowe:
   - Graph Density → ile opcji podań ma zespół pod presją
   - Clustering Coefficient → stabilność trójkątów podań
   - Betweenness Centrality → identyfikacja "wąskich gardeł"
3. Network_Quality modyfikuje:
   - Szybkość progresji piłki (ile zdarzeń do przejścia z tercji na tercję)
   - Ryzyko straty (niski clustering = więcej strat)
   - Odporność na pressing (wysoka density = więcej opcji pod presją)
```

**Taktyczna implikacja**: Jeśli rywale man-markują Twojego kluczowego rozgrywającego (wysoki betweenness centrality), Twoja sieć podań się rozpada. Rozwiązanie: zmiana formacji, dodanie gracza do środka, instrukcja "grać przez boki".

### D) Field Tilt — Dominacja w trakcie meczu

```
Algorytm (aktualizowany co zdarzenie):
1. Field_Tilt = kontakty_A_w_tercji_ataku / 
   (kontakty_A + kontakty_B w tercjach ofensywnych)
2. Field_Tilt > 0.65 + niskie xG → sygnał: dominacja bez efektu
   → AI rywal (lub gracz) powinien zmienić podejście
3. Gwałtowna zmiana Field_Tilt → zmiana momentum
   → TacticalTrigger: MomentumCondition
```

---

## 5. Warstwa 3: Analityka i Feedback

### A) Post-Match Report — Ghosting Analysis

**Zastosowanie**: Po meczu gracz otrzymuje analizę, gdzie jego zawodnicy BYLI vs gdzie POWINNI być.

```
Algorytm:
1. Dla każdego kluczowego momentu (bramka, szansa, strata):
   - Wygeneruj "ghost positions" — optymalne pozycje z modelu
   - Porównaj z faktycznymi pozycjami
   - Oblicz deviation_score = Σ |pos_real - pos_ghost|
2. Identyfikuj wzorce:
   - "Lewy obrońca systematycznie za wysoko o 3m → luka za plecami"
   - "Środkowy pomocnik nie wraca do pozycji po pressing → luka w 6"
3. Prezentuj graczowi jako konkretne sugestie taktyczne
```

**Jak gracz się uczy**: Widzi, że jego formacja 4-3-3 z instrukcją "fullbacks overlap" tworzy lukę, którą rywale wykorzystują. Następnym razem: zmniejszy overlap, doda "stay back" jednym z obrońców, lub zmieni formację na 3-4-3.

### B) Win Probability Timeline

**Zastosowanie**: Wykres WPA przez cały mecz — kiedy szanse rosły/malały.

```
Algorytm:
1. Przed meczem: WP obliczone z Elo/siły zespołów
2. Po każdym zdarzeniu: Bayesian update
   - Bramka: duży skok WP
   - Szansa (wysoki xG): mały skok
   - Czerwona kartka: skok
   - Zmiana formacji (gracz): potencjalna zmiana trendu
3. Post-match: gracz widzi, w których momentach zmiany taktyczne
   zadziałały lub nie
```

### C) VAEP Breakdown per Player

**Zastosowanie**: Ranking akcji — które akcje którego zawodnika miały największy wpływ.

```
Raport:
1. Top 5 akcji o najwyższym VAEP:
   - "Min 34: Podanie prostopadłe AM → Striker: VAEP = +0.18"
   - "Min 67: Odbiór CDM w 6: VAEP = +0.15"
2. Top 5 akcji o najniższym VAEP:
   - "Min 23: Strata LB w budowaniu: VAEP = -0.22"
3. Średni VAEP per gracz per 90 minut
```

### D) Passing Network Visualization

```
Raport:
1. Graf sieci podań z grubością krawędzi = częstotliwość
2. Betweenness centrality podświetlona — kto był hubem
3. Clustering coefficient per strefa — stabilność posiadania
4. Porównanie z planem taktycznym: 
   "Chciałeś grać przez środek, ale 70% podań szło bokami"
```

### E) xT Flow Map

```
Raport:
1. Mapa cieplna: skąd-dokąd piłka się przemieszczała
2. xT gained vs lost per strefa
3. "Twój zespół zyskał 4.5 xT przez lewą stronę, ale stracił 3.2 xT
    przez środek — rywale exploitowali przestrzeń między 6 a CB"
```

---

## 6. Mapowanie Algorytmów na Mechaniki Gry

### Tabela: Algorytm → Mechanika → Co gracz kontroluje

| Algorytm | Mechanika w grze | Co gracz ustala | Wpływ na wynik |
|----------|------------------|----------------|-----------------|
| **xG** | Jakość szans | Instrukcje strzałów, formacja w polu karnym | Bezpośrednio: bramki |
| **xT / DxT** | Ścieżki progresji | BuildUpStyle, progressionPreference | Gdzie tworzysz szanse |
| **VAEP** | Wycena każdej akcji | Instrukcje indywidualne, skład | Ranking zawodników |
| **EPV** | Wartość posiadania w real-time | Tempo, riskTolerance | Jakość decyzji zespołu |
| **xPass** | Sukces podań | Styl podań, tempo, pressing rywala | Utrzymanie piłki, straty |
| **Pitch Control** | Mapa dominacji boiska | Formacja, pozycje, prędkości | Kto kontroluje przestrzeń |
| **OBSO** | Biegi bez piłki | runFrequency, overlapUnderlap | Kreowanie szans off-ball |
| **gBRI** | Wartość biegów | Instrukcje biegów zawodników | Otwieranie przestrzeni |
| **PPDA** | Intensywność pressingu | PressingStrategy | Wymuszanie błędów rywala |
| **Dynamic Pressure** | Presja na piłce | Pressing, formacja, intensywność | Jakość podań rywala |
| **IWP** | Rozstrzyganie 1v1 | Matchupy, man-marking, styl obrony | Dryblingi, walki |
| **Z-Score** | Profil zawodnika | Skład, pozycje, dopasowanie | Wybór optymalnego 11 |
| **Passing Network** | Jakość cyrkulacji | Formacja, role, podania | Posiadanie, opcje |
| **Field Tilt** | Dominacja terytorialna | Pressing, linia, agresja | Kontrola meczu |
| **Dixon-Coles** | Oczekiwane bramki | Siła ataku/obrony w kontekście | Bazowe prawdopodobieństwo |
| **Monte Carlo** | Symulacja wyniku | Cała taktyka | Rozkład możliwych wyników |
| **Elo / Glicko-2** | Rating zespołu | Historia wyników, progres | Siła zespołu w rankingu |
| **WPA** | Momentum w meczu | Triggery in-game, zmiany | Timing decyzji |
| **Ghosting** | Analiza post-match | Feedback → korekta taktyki | Uczenie na błędach |
| **NMF / GMM** | Stałe fragmenty | Schematy rożnych, rzutów wolnych | ~30% bramek |
| **Genetic Algorithm** | Optymalizacja składu | Skład + formacja | Dopasowanie zawodników |
| **Nash Equilibrium** | Rzuty karne | Strategia rzutów karnych | Wynik serii rzutów |
| **ACWR / Metabolic** | Zmęczenie, kontuzje | Rotacja, zmiany, obciążenie | Forma fizyczna |
| **GNN** | Relacje systemowe | Formacja, role, instrukcje | Synergia zespołowa |
| **Cosine Similarity** | Skauting | Rekrutacja, zastępstwa | Jakość transferów |
| **Bayesian Inference** | Update sił w sezonie | Historia wyników | Evolucja ratingu |

---

## 7. System Atrybutów Zawodników

> **Pełna specyfikacja**: `ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md` (30 atrybutów, skala 1-20)  
> **Implementacja techniczna**: `SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md` §3.1  
> Poniżej skrócone podsumowanie z mapowaniem na algorytmy.

### 30 atrybutów → algorytmy (podsumowanie)

```
FIZYCZNE (8): Pace, Acceleration, Agility, Stamina, Strength, Jumping, 
              Natural Fitness, Balance — skala 1-20
  → Pitch Control, IWP, Metabolic Power, ACWR, Counter-Attack Speed

TECHNICZNE (10): Finishing, Long Shots, Short Passing, Long Passing, 
                 Crossing, Dribbling, First Touch, Heading, Tackling, Technique — skala 1-20
  → xG, PSxG, xPass, IWP, xT, VAEP, Set Pieces

MENTALNE (12): Vision, Composure, Off The Ball, Positioning, Anticipation,
               Decisions, Work Rate, Aggression, Concentration, Teamwork, 
               Bravery, Flair — skala 1-20
  → Scanning, EPV, Ghosting, OBSO, PPDA, Dynamic Pressure, GamePlan Fidelity

BRAMKARZ (6): Reflexes, GK Positioning, Handling, Distribution, 
              Command of Area, One on Ones — skala 1-20
  → PSxG, xG Save, Claim Cross, GK Distribution

Formuła presji: effective_attr = base_attr × (1 - pressure × (1 - composure/20))
```

### Synergy System — Komplementarność Graczy

```scala
def calculateSynergy(p1: Player, p2: Player, roles: (TacticalRole, TacticalRole)): Double = {
  val cosineSim = cosineSimilarity(p1.toAttrVector, p2.toAttrVector)
  val complementarity = roles match {
    case (Winger, Fullback) =>
      (p1.physical.pace + p2.mental.workRate) / 40.0  // skala 1-20
    case (DeepPlaymaker, BoxToBox) =>
      (p1.mental.vision + p2.mental.workRate) / 40.0
    case (CentreBack, CentreBack) =>
      complementaryBalance(p1.physical.pace, p2.physical.pace,
                          p1.physical.jumping, p2.physical.jumping)
    case (InsideForward, InvertedFullback) =>
      (p1.technical.dribbling + p2.technical.shortPassing) / 40.0
    case _ => 0.5
  }
  0.4 * cosineSim + 0.6 * complementarity
}
```

---

## 8. Stałe Fragmenty Gry

### Rożne — System GMM + NMF

```
Mechanika:
1. Gracz wybiera schemat rożnego z biblioteki lub tworzy własny:
   - Near Post Flick: ruch na bliski słupek, flicknięcie dalej
   - Far Post Attack: biegi 3+ zawodników na dalszy słupek
   - Short Corner: krótki rożny z overlapem
   - Trained Routine: pełna choreografia (NMF template)

2. System GMM oblicza strefy aktywne:
   - 15 stref w polu karnym
   - Prawdopodobieństwo trafienia w każdą strefę
   - Zależy od jakości dośrodkowującego (crossing) i biegów (positioning)

3. Rozstrzygnięcie:
   - Dośrodkowanie: xPass do wybranej strefy (crossing, technique)
   - Walka w powietrzu: IWP (jumping, heading, strength, bravery)
   - Strzał/główka: xG z danej strefy (heading, composure)

4. Defensywa rożnych:
   - Gracz wybiera: man-marking vs zonal
   - Man-marking: korelacja pozycji obrońca-napastnik
   - Zonal: obrońcy stacjonarni w strefach GMM
```

### Rzuty Wolne — Model Balistyczny

```
Mechanika:
1. Gracz wybiera:
   - Bezpośredni strzał vs podanie
   - Zakręcenie (lewo/prawo) → Siła Magnusa
   - Siła strzału
   - Celowanie (bliski/daleki słupek/nad murem)

2. System oblicza:
   - Trajektoria z fizyką (Drag, Lift, Magnus)
   - Efekt muru jako okluzja bramkarza
   - Delay bramkarza (70-90ms jeśli mur zasłania)
   - xG z uwzględnieniem fizyki

3. Atrybuty wpływające: longShots, technique, trait freeKickSpecialist (1-5)
```

### Rzuty Karne — Nash Equilibrium

```
Mechanika:
1. Gracz wybiera strategię (lub Mixed Strategy):
   - Kierunek: lewo / prawo / środek
   - Siła: mocny / placement
   - Lub: "auto" → Nash Equilibrium (optymalna mieszanka)

2. Bramkarz (AI lub gracz): analogiczne decyzje
3. Rozstrzygnięcie: MSNE z modyfikatorem composure
   - Composure > 17: dokładna egzekucja zamierzonego kierunku
   - Composure < 12: odchylenie od zamierzonego → losowe przesunięcie
   - Trait penaltyTaker (1-5) daje dodatkowy bonus celności
```

---

## 9. System Fizyczny i Zmęczenie

### Metabolic Power w trakcie meczu

> **Pełna implementacja**: `SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md` §7

```scala
def metabolicCost(action: MatchAction, player: Player): Double = action match {
  case Sprint(dist)        => dist * 0.004 * (1 + player.physical.pace / 40.0)
  case HighIntensityRun(d) => d * 0.002
  case Pressing(duration)  => duration * 0.003 * player.mental.workRate / 10.0
  case Dribble(dist)       => dist * 0.003 * (1 + player.physical.agility / 40.0)
  case Walking(dist)       => dist * 0.0003
}
// Stamina decay:
//   > 60%: pełna wydajność
//   40-60%: lekki spadek (do -10% na atrybutach)
//   20-40%: mocny spadek (do -25%)
//   < 20%: krytyczny (do -35%, ryzyko kontuzji)
```

### Pressing Decay — Taktyczna implikacja zmęczenia

```
Mechanika:
1. Pressing zużywa więcej Metabolic Power niż pasywna obrona
2. Zespół grający agresywny pressing (intensity=90) zużywa ~40% więcej energii
3. Po 60-70 minutach pressing naturalnie słabnie:
   - PPDA rośnie (mniej skutecznych pressingowych akcji)
   - Dynamic Pressure spada
   - xPass rywala rośnie (łatwiej im się podawać)
4. TAKTYCZNA DECYZJA: Gracz musi zdecydować:
   - Utrzymać pressing i ryzykować "zapadnięcie" po 75 minucie
   - Zmienić na niższy blok po 60 minucie (TacticalTrigger)
   - Rotować graczy (zmiany) aby utrzymać intensywność
```

---

## 10. AI Agenci — Przeciwnicy i Uczenie

### Typy AI Trenerów

```scala
sealed trait AICoachPersonality

case object TikiTaka extends AICoachPersonality
// Wysoki pressing, krótkie podanie, niski risk tolerance
// Formacja: 4-3-3, posiadanie 60%+, PPDA < 8

case object Catenaccio extends AICoachPersonality  
// Niski blok, kontra, minimalizacja ryzyka
// Formacja: 5-4-1, posiadanie 35%, Field Tilt rywala > 60%

case object Gegenpressing extends AICoachPersonality
// Ultra-wysoki pressing, szybkie tranzycje
// Formacja: 4-3-3 / 4-2-3-1, PPDA < 7, counter-press 5s

case object Pragmatic extends AICoachPersonality
// Adaptacyjny — zmienia styl w zależności od rywala
// Analizuje matchup matrix i wybiera optymalną strategię

case object Chaotic extends AICoachPersonality
// Nieprzewidywalny — zmienia formację co 15 minut
// Trudny do przygotowania się taktycznie
```

### AI Learning Loop — Uczenie Agentów

```
Cykl uczenia AI agenta (Reinforcement Learning):
1. Obserwacja: stan meczu (wynik, minuty, Field Tilt, stamina, xG balance)
2. Akcja: zmiana taktyki (formacja, pressing, instrukcje)
3. Nagroda: zmiana WPA (Win Probability Added)
4. Polityka: optymalizacja decyzji maksymalizująca sumaryczne WPA

Algorytm: PPO (Proximal Policy Optimization) lub SAC (Soft Actor-Critic)
- State space: [score_diff, minute, field_tilt, ppda, avg_stamina, 
               xg_balance, opponent_formation, momentum]
- Action space: [change_formation, adjust_pressing, make_sub, 
                switch_style, activate_counter]
```

### Multi-Agent Competition

```
System ligowy:
1. Wielu AI agentów + ludzcy gracze w jednej lidze
2. Każdy agent uczy się z każdego meczu
3. System Glicko-2 rankuje wszystkich uczestników
4. AI agenci adaptują się:
   - Po przegranej z kontratakami → zwiększają defensywność przeciwko szybkim drużynom
   - Po dominacji terytorialnej bez bramek → zmieniają na bardziej direct play
5. Meta-game ewoluuje: jeśli wszyscy grają pressing, 
   kontra-taktyki stają się silniejsze
```

---

## 11. Pętla Rozgrywki i Uczenie się na Błędach

### Cykl Gracza (Człowieka)

```
PRE-MATCH (Przygotowanie):
│
├── 1. Analiza rywala:
│   ├── Ostatnie 5 meczów: formacje, PPDA, Field Tilt, xG
│   ├── Passing Network: kto jest hubem? (betweenness centrality)
│   ├── Matchup Matrix: twoi gracze vs ich gracze
│   └── Słabe punkty: strefy niskiego Pitch Control
│
├── 2. Ustalenie GamePlan:
│   ├── Formacja i role
│   ├── Styl budowania (pod rywala!)
│   ├── Pressing (wysoki jeśli rywal słaby w budowaniu)
│   ├── Instrukcje indywidualne (man-mark ich playmaker?)
│   └── Stałe fragmenty
│
├── 3. Triggery in-game:
│   ├── "Jeśli przegrywamy po 60 min → zmiana na 3-4-3"
│   ├── "Jeśli Field Tilt < 40% → obniż pressing"
│   └── "Jeśli PPDA > 12 i przegrywamy → zmiana CDM na AM"
│
MATCH (Symulacja):
│
├── Silnik symuluje zdarzenie po zdarzeniu
├── Triggery aktywują się automatycznie
├── Gracz widzi kluczowe momenty (highlights) z metrykami
└── Wynik: bramki, statystyki, xG, xT, VAEP
│
POST-MATCH (Analiza):
│
├── 1. Ghosting Report:
│   └── "Twoja linia obrony była za wysoka w 15 momentach → 3 kontry"
│
├── 2. VAEP Breakdown:
│   └── "Twój CDM miał VAEP -0.8 → zbyt ryzykowne podania"
│
├── 3. Passing Network:
│   └── "Prawe skrzydło = martwa strefa, 90% gry lewą stroną"
│
├── 4. Pitch Control Heatmap:
│   └── "Rywale kontrolowali strefę między linią a 6 → tam padły 2 bramki"
│
├── 5. WPA Timeline:
│   └── "Twoja zmiana formacji w 60. minucie zwiększyła WP o 12%"
│
└── 6. Sugestie:
    ├── "Rozważ man-marking ich AM (betweenness 0.35)"
    ├── "Dodaj instrukcję overlap prawemu obrońcy → zbalansuj ataki"
    └── "Obniż pressing po 70 min → stamina Twoich pomocy < 40%"
│
NEXT MATCH → Gracz koryguje taktykę na podstawie analiz
```

### Progresja i Meta-Game

```
SEZON 1 (Nauka):
- Gracz uczy się podstaw: formacja, pressing, styl
- AI rywale grają prostymi strategiami
- Feedback focus: xG, posiadanie, bramki

SEZON 2 (Zaawansowane):
- Gracz zaczyna analizować rywali
- Man-marking, pressing traps, counter-tactics
- AI rywale adaptują się → trzeba reagować
- Feedback focus: VAEP, xT, matchup matrix

SEZON 3 (Mistrzostwo):
- Meta-game: rock-paper-scissors formacji
- Mindgames: zmiana stylu aby zmylić AI analizujące Twoje wzorce
- Customowe stałe fragmenty
- Feedback focus: Ghosting, EPV, pełna analiza sieciowa

SEZON 4+ (Rywalizacja):
- Liga PvP: ludzie vs ludzie
- Turnieje: eliminacje, play-off
- Ranking Glicko-2 globalny
- Community taktyk: dzielenie się GamePlanami
```

---

## 12. Stos Technologiczny (Scala Ecosystem)

### Architektura komponentów

```
BACKEND (Scala 3):
├── Core Engine:
│   ├── Cats Effect 3 — efekty, IO monad, fiber-based concurrency
│   ├── FS2 — streaming zdarzeń meczu
│   └── Circe — JSON serialization (GamePlan, Match Events)
│
├── Match Simulation:
│   ├── Breeze — biblioteka numeryczna (algebra liniowa, rozkłady)
│   │   ├── Rozkład Poissona (Dixon-Coles)
│   │   ├── Funkcja logistyczna (Pitch Control, IWP)
│   │   ├── Macierze przejść (xT Markov)
│   │   └── Regresja (xG, xPass)
│   ├── Spire — precyzyjne obliczenia matematyczne
│   └── Custom Monte Carlo engine
│
├── ML Layer:
│   ├── DJL (Deep Java Library) — inference modeli PyTorch/ONNX w JVM
│   │   ├── GNN do xReceiver
│   │   ├── LSTM do sekwencji akcji
│   │   └── XGBoost modele (xG, VAEP, xPass)
│   ├── Smile — natywna biblioteka ML dla JVM
│   │   ├── Random Forest
│   │   ├── Gradient Boosting
│   │   └── GMM (Gaussian Mixture Models)
│   └── TensorFlow Scala (opcjonalnie) — deep learning
│
├── Graph Analysis:
│   ├── GraphX (Apache Spark) — large-scale graph computation
│   │   ├── Betweenness Centrality
│   │   ├── PageRank / Eigenvector Centrality
│   │   └── Connected Components
│   └── JGraphT — lekka biblioteka grafowa
│       ├── Clustering Coefficient
│       ├── Shortest Paths
│       └── Graph Density
│
├── Data Layer:
│   ├── Doobie — functional JDBC (PostgreSQL)
│   ├── Skunk — native Postgres protocol
│   └── Redis4Cats — caching (sesje, stany meczu)
│
├── API Layer:
│   ├── Http4s — HTTP/WebSocket server
│   ├── Tapir — API documentation / OpenAPI
│   └── gRPC (ScalaPB) — komunikacja mikroserwisów
│
└── Testing:
    ├── ScalaCheck — property-based testing
    │   └── "Dla dowolnej taktyki, xG jest nieujemne"
    ├── MUnit — unit testing
    └── Weaver-test — concurrent testing

FRONTEND:
├── Scala.js + Laminar — reaktywny UI
├── D3.js — wizualizacje (pitch maps, sieci podań, WPA)
└── Three.js / Canvas — animacja highlights

INFRASTRUCTURE:
├── Docker + Kubernetes — deployment
├── Apache Kafka — event streaming (mecze real-time)
├── Prometheus + Grafana — monitoring
└── Apache Spark — batch analytics (sezonowe statystyki)
```

### Kluczowe moduły Scala

```scala
// --- MATCH ENGINE ---
object MatchEngine {
  def simulate(homeTeam: Team, awayTeam: Team, 
               homePlan: GamePlan, awayPlan: GamePlan): IO[MatchResult] = {
    for {
      // Oblicz bazowe parametry
      pitchControl <- PitchControlModel.compute(homeTeam, awayTeam, homePlan, awayPlan)
      xTMatrix     <- ExpectedThreatModel.computeDynamic(pitchControl)
      matchupGrid  <- MatchupModel.computeIWP(homeTeam, awayTeam)
      
      // Oblicz oczekiwane bramki (Dixon-Coles)
      lambdaHome   <- DixonColesModel.expectedGoals(homeTeam, awayTeam, homePlan, awayPlan)
      lambdaAway   <- DixonColesModel.expectedGoals(awayTeam, homeTeam, awayPlan, homePlan)
      
      // Symulacja zdarzenie po zdarzeniu
      events       <- EventSimulator.simulate(
        pitchControl, xTMatrix, matchupGrid,
        lambdaHome, lambdaAway,
        homePlan, awayPlan,
        homeTeam.players, awayTeam.players
      )
      
      // Oblicz metryki post-match
      vaepScores   <- VAEPModel.evaluate(events)
      ghostReport  <- GhostingModel.analyze(events, homePlan, awayPlan)
      wpaTimeline  <- WPAModel.compute(events)
      passNetwork  <- PassingNetworkModel.build(events)
      
      result = MatchResult(events, vaepScores, ghostReport, wpaTimeline, passNetwork)
    } yield result
  }
}

// --- PITCH CONTROL ---
object PitchControlModel {
  private val gridResolution = (120, 80) // siatka boiska
  private val sigma = 0.45 // parametr niepewności czasu
  
  def compute(home: Team, away: Team, 
              homePlan: GamePlan, awayPlan: GamePlan): IO[PitchControlGrid] = IO {
    val grid = Array.ofDim[Double](gridResolution._1, gridResolution._2)
    
    for {
      x <- 0 until gridResolution._1
      y <- 0 until gridResolution._2
    } {
      val point = PitchPoint(x.toDouble, y.toDouble)
      val homeControl = home.players.map(p => 
        interceptProbability(p, point, sigma)
      ).sum
      val awayControl = away.players.map(p => 
        interceptProbability(p, point, sigma)
      ).sum
      grid(x)(y) = homeControl / (homeControl + awayControl)
    }
    
    PitchControlGrid(grid)
  }
  
  private def interceptProbability(player: Player, target: PitchPoint, 
                                    sigma: Double): Double = {
    val timeToIntercept = physicsModel.timeToReach(
      player.position, player.velocity, 
      player.attributes.pace, player.attributes.acceleration,
      target
    )
    val T = 0.0 // czas lotu piłki do punktu (parametr)
    1.0 / (1.0 + math.exp(-math.Pi / (math.sqrt(3) * sigma) * (T - timeToIntercept)))
  }
}

// --- EXPECTED THREAT (xT) ---
object ExpectedThreatModel {
  private val zones = (12, 8) // 96 stref
  
  def computeStatic(historicalData: List[MatchEvent]): IO[xTGrid] = IO {
    // 1. Oblicz macierz przejść z historycznych danych
    val transitionMatrix = buildTransitionMatrix(historicalData, zones)
    // 2. Oblicz prawdopodobieństwo strzału i gola per strefa
    val shotProb = computeShotProbability(historicalData, zones)
    val goalProb = computeGoalProbability(historicalData, zones)
    // 3. Value iteration
    val xT = valueIteration(transitionMatrix, shotProb, goalProb, iterations = 100)
    xTGrid(xT)
  }
  
  def computeDynamic(pitchControl: PitchControlGrid): IO[xTGrid] = IO {
    // Modyfikuj statyczne xT na podstawie aktualnego Pitch Control
    // Strefy kontrolowane przez rywala mają obniżone xT
    staticXT.zipWith(pitchControl)((xt, pc) => xt * pc)
  }
}
```

---

## 13. Przykładowy Przebieg Meczu — Krok po Kroku

### Scenariusz: Team A (4-3-3, High Press) vs Team B (5-3-2, Counter-Attack)

```
PRZED MECZEM:
├── Team A (gracz ludzki):
│   ├── Formacja: 4-3-3 (CF-LW-RW, CM-CM-CDM, LB-CB-CB-RB)
│   ├── Pressing: line=80, intensity=85, counter-press=5s
│   ├── Build-up: progressionPreference=0.3, tempo=0.7, riskTolerance=0.6
│   ├── Instrukcja: RW → cut inside (ich LB jest wolny)
│   └── Trigger: jeśli stamina_avg < 50 → obniż pressing do 50
│
└── Team B (AI Catenaccio):
    ├── Formacja: 5-3-2 (ST-ST, CM-CM-CM, LWB-CB-CB-CB-RWB)
    ├── Pressing: line=35, intensity=40
    ├── Build-up: directness=0.8, counterAttackPriority=0.8
    └── Trigger: jeśli prowadzenie → obniż jeszcze bardziej

PRE-MATCH OBLICZENIA:
├── Pitch Control: Team A dominuje 60% boiska (pressing + formacja)
│   ale Team B ma solidną kontrolę w swojej tercji (5 obrońców)
├── xT Map: Dla Team A najwyższe xT na bokach (LW/RW mają przestrzeń)
│   ale centralne strefy A zablokowane przez 3 środkowych B
├── Matchup Matrix:
│   A's RW (pace=18, dribbling=16) vs B's LWB (pace=14, tackling=15)
│   → IWP = 0.62 — KORZYSTNY matchup dla A!
│   A's CF (pace=14, finishing=18) vs B's CB1 (pace=16, tackling=17)
│   → IWP = 0.38 — CB1 dominuje fizycznie
├── Dixon-Coles: λ_A = 1.7, λ_B = 0.9
│   → P(A wins) = 54%, P(Draw) = 22%, P(B wins) = 24%
└── WPA_start: Team A = 54%

=== SYMULACJA ===

MIN 0-15: Team A dominuje posiadanie (68%)
├── Field Tilt: 72% (A) — ciągły pressing
├── PPDA Team A: 6.8 (bardzo intensywny)
├── xT gained by A: +3.2 (głównie lewą stroną)
├── Szanse: 2 strzały A (xG: 0.08, 0.12) = Σ xG = 0.20
├── Podania B: xPass avg = 0.62 (pod presją pressingu)
├── Dynamic Pressure na podaniach B: avg 0.45
└── Kontry B: 1 kontra (xG: 0.15) — szybka tranzycja ich ST

MIN 15-30: A próbuje przebić się przez niski blok B
├── Field Tilt: 75% (A)
├── PPDA Team A: 7.2 (lekko rośnie — zmęczenie zaczyna wpływać)
├── xT stref centralnych: NISKI (5 obrońców B blokuje)
├── A's RW exploit: 3 udane dryblingi vs LWB (IWP potwierdzone!)
│   → xT gained z prawej strony: +1.8
├── MIN 23: A's RW cut inside → strzał → xG = 0.22 → MISS
├── MIN 28: A's CM progresywne podanie → strzał CF → xG = 0.35 → GOL! 1:0
│   VAEP tego podania: +0.28 (kluczowa akcja meczu do tej pory)
│   WPA: A = 54% → 71%
└── B aktywuje trigger: prowadzenie rywali → JESZCZE NIŻSZY blok

MIN 30-45: B upada głęboko, A atakuje
├── Field Tilt: 80% (A) — totalna dominacja terytorialna
├── ALE: xG A w tym kwadransie = 0.18 (niski blok działa!)
├── Pitch Control: A ma 75% boiska ALE ostatnia tercja = B's 55%
│   → 5 obrońców + 3 pomocy B tworzą "mur"
├── Passing Network A: clustering coefficient spada (trudno o trójkąty)
│   → B skutecznie blokuje passing lanes
├── MIN 42: Kontra B! Strata A (pod presją, VAEP = -0.31)
│   → B's ST sprint (pace=17 vs A's CB pace=15)
│   → IWP sprintu: 0.68 na korzyść ST
│   → Strzał → xG = 0.42 → GOL! 1:1
│   WPA: A = 71% → 48%
└── PRZERWA: 1:1

PRZERWA — ANALIZA:
├── Ghosting: "A's fullbacks za wysoko w 8/15 minut → 
│   kontry B korzystały z przestrzeni"
├── xT flow: "87% ataku A lewą stroną → B łatwo się przesuwała"
├── Matchup insight: "A's RW dominuje ich LWB — kieruj więcej tam"
└── Stamina: A's pressing = avg stamina 62% vs B's 78%

MIN 45-60: A kontynuuje ale pressing słabnie
├── PPDA Team A: 9.1 (wyraźne pogorszenie — zmęczenie!)
├── Dynamic Pressure: spada z 0.45 na 0.32
├── B's xPass: rośnie z 0.62 na 0.71 (łatwiej się podawać)
├── Field Tilt: 65% (A) — spada!
└── MIN 57: B's kontra → strzał → xG = 0.28 → OBRONIONY

MIN 60: TRIGGER AKTYWOWANY (stamina_avg = 49%)
├── A zmienia pressing: line=50, intensity=55
├── A wprowadza zmianę: zmęczony CM → świeży CM
├── Efekt: PPDA stabilizuje się na 10.5
└── Metabolic Power nowego CM: pełna → pressing odzyskuje intensywność

MIN 60-75: Zmieniona taktyka A
├── Field Tilt: 62% (A) — bardziej zbalansowane
├── xG A: 0.31 (lepsza jakość szans — mniej forcingu)
├── A's świeży CM progresywne podania: VAEP = +0.12 per akcja
├── MIN 68: A's RW (wciąż dominuje LWB B) → dośrodkowanie → 
│   główka CF → xG = 0.18 → MISS
└── MIN 72: A's LW → podanie prostopadłe → CF strzał → xG = 0.45 → GOL! 2:1
    WPA: A = 48% → 74%

MIN 75-90: B desperacko szuka wyrównania
├── B zmienia formację: 5-3-2 → 3-4-3 (trigger: przegrywanie > 75 min)
├── Field Tilt odwraca się: B = 55%!
├── ALE: B's obrona teraz wrażliwa (3 obrońców)
├── A's kontra w 82 min → 3v2 → strzał → xG = 0.55 → GOL! 3:1
│   WPA: A = 89% → 97%
└── KONIEC: 3:1

=== POST-MATCH REPORT ===

Team A:
├── xG: 2.4 vs Gole: 3 (overperformance +0.6)
├── Field Tilt: 71% (dominacja)
├── PPDA: 8.2 avg (wysoki pressing, ale spadł po 45 min)
├── Top VAEP: CM (zmiennik) = +0.42 per 30 min
├── Ghosting: "Pressing po 45 min zbyt energochłonny — zmiana w 60 min
│   była kluczowa. Rozważ wcześniejszą zmianę stylu."
├── Matchup exploit: RW vs LWB = 7/10 dryblingów wygrane
└── Sugestia: "Zwiększ ataki prawą stroną od początku"

Team B:
├── xG: 1.1 vs Gole: 1 (lekki underperformance)
├── Counter-attacks: 4, xG z kontr = 0.85 (efektywne!)
├── Niski blok: skuteczny do 72 min (A miało xG 0.18 w Q2 1st half)
├── Ghosting: "Zmiana na 3-4-3 w 75 min była zbyt ryzykowna
│   przy -1. Rywale mieli 3v2 w kontrze → bramka na 3:1"
└── Sugestia: "Zachowaj 5-3-2 i szukaj wyrównania ze stałych 
    fragmentów zamiast otwierać grę"
```

---

## Podsumowanie: Dlaczego ta gra jest unikalna

### 1. Matematycznie uzasadniona losowość
Każdy wynik ma wyjaśnienie w algorytmach — gracz nigdy nie czuje się "oszukany" przez RNG. Przegrana = zła taktyka lub ryzyko, które się nie opłaciło.

### 2. Głębokość = prawdziwa piłka nożna
Matchup matrix, pressing traps, man-marking hubow — to są decyzje, które podejmują prawdziwi trenerzy. Gra symuluje MYŚLENIE taktyczne, nie klikanie.

### 3. Feedback loop = nauka
Post-match Ghosting, VAEP breakdown, WPA timeline — gracz widzi CO poszło nie tak i DLACZEGO. Każdy mecz to lekcja.

### 4. Ewoluujący meta-game
AI agenci uczą się, gracze adaptują, meta się zmienia. Nie ma "jednej optymalnej taktyki" — counter-play jest zawsze możliwy (Nash Equilibrium w makroskali).

### 5. Scala = wydajność + bezpieczeństwo typów
Silnik gry w Scala 3 z Cats Effect zapewnia:
- Type-safe domain modeling (algebraic data types)
- Efektywność obliczeniową (JVM + natywne biblioteki numeryczne)
- Concurrent simulation (wiele meczów jednocześnie)
- Property-based testing (ScalaCheck — "żadna taktyka nie generuje ujemnego xG")

---

*Dokument projektowy v1.0 — Luty 2026*  
*Gotowy do dalszego rozwijania: API specification, detailed data models, ML pipeline design*
