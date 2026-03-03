# Formacje, Role i Taktyka — Kompletna Specyfikacja

## Pełna dowolność taktyczna zmapowana na algorytmy silnika

**Perspektywa**: Architekt systemu / Analityk danych / Ekspert futbolowy  
**Data**: Luty 2026  
**Spójność z**: ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md, SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md

---

## Spis treści

1. [Trzy warstwy decyzji taktycznej](#1-trzy-warstwy-decyzji-taktycznej)
2. [Pozycje i sloty — PositionSlot](#2-pozycje-i-sloty)
3. [Formacja — rozmieszczenie na boisku](#3-formacja)
4. [Role — zachowania behawioralne](#4-role)
5. [Katalog ról: Obrona](#5-katalog-ról-obrona)
6. [Katalog ról: Środek pola](#6-katalog-ról-środek-pola)
7. [Katalog ról: Atak](#7-katalog-ról-atak)
8. [Katalog ról: Bramkarz](#8-katalog-ról-bramkarz)
9. [Instrukcje indywidualne](#9-instrukcje-indywidualne)
10. [Strategia zespołowa](#10-strategia-zespołowa)
11. [Mentalność — meta-parametr](#11-mentalność)
12. [Triggery i plany alternatywne](#12-triggery-i-plany-alternatywne)
13. [Stałe fragmenty gry — taktyka](#13-stałe-fragmenty-gry)
14. [Pełny łańcuch: Taktyka → Algorytm → Wynik](#14-pełny-łańcuch)
15. [Model danych Scala 3](#15-model-danych-scala-3)
16. [Presetowe formacje — szablony startowe](#16-presetowe-formacje)

---

## 1. Trzy warstwy decyzji taktycznej

Gracz (trener) podejmuje decyzje na trzech poziomach. Każdy poziom wpływa na inne algorytmy silnika:

```
WARSTWA 1: FORMACJA (GDZIE stoją gracze)
  → Pitch Control, DxT, Passing Network, Defensive Shape
  Gracz ustawia 10 pozycji (x,y) na boisku

WARSTWA 2: ROLE (JAK się zachowują)
  → selectAction weights, OBSO/gBRI, PPDA participation, Matchup routing
  Gracz przypisuje rolę behawioralną do każdego slotu

WARSTWA 3: INSTRUKCJE INDYWIDUALNE (CO DOKŁADNIE robi ten gracz)
  → nadpisania wag per gracz, man-marking, specjalne zachowania
  Gracz daje konkretne instrukcje wybranym zawodnikom

+ META: STRATEGIA ZESPOŁOWA (pressing, build-up, mentalność)
  → PPDA, Dynamic Pressure, xPass modifiers, DxT routing, tempo
  Suwaki i parametry na poziomie drużyny
```

**Zasada**: Gracz kontroluje ZAMIAR (co zespół PRÓBUJE robić). Silnik rozstrzyga WYNIK (czy się udaje) na podstawie atrybutów + algorytmów.

---

## 2. Pozycje i sloty

### 2.1 PositionSlot — 18 możliwych slotów

Slot to "etykieta" pozycji na boisku. Gracz przypisuje zawodnika do slotu, a potem ustawia koordynaty (x,y). Sloty definiują naturalną strefę zawodnika i grupę ról, z której może wybierać.

```scala
enum PositionSlot:
  // Bramkarz
  case GK

  // Obrona
  case CB        // Środkowy obrońca
  case LCB       // Lewy środkowy obrońca (w trójce)
  case RCB       // Prawy środkowy obrońca (w trójce)
  case LB        // Lewy obrońca
  case RB        // Prawy obrońca
  case LWB       // Lewy wahadłowy
  case RWB       // Prawy wahadłowy

  // Środek pola
  case CDM       // Defensywny pomocnik
  case CM        // Środkowy pomocnik
  case LCM       // Lewy środkowy pomocnik
  case RCM       // Prawy środkowy pomocnik
  case CAM       // Ofensywny pomocnik
  case LM        // Lewy pomocnik
  case RM        // Prawy pomocnik

  // Atak
  case ST        // Napastnik
  case LW        // Lewe skrzydło
  case RW        // Prawe skrzydło
```

### 2.2 Grupowanie slotów → dostępne role

| Grupa | Sloty | Dostępne role |
|-------|-------|---------------|
| Środkowy obrońca | CB, LCB, RCB | BallPlayingDefender, Stopper, Cover, Libero |
| Boczny obrońca | LB, RB | Fullback, WingBack, InvertedFullback, CompleteWingBack |
| Wahadłowy | LWB, RWB | WingBack, CompleteWingBack, DefensiveWingBack |
| Defensywny pomocnik | CDM | AnchorMan, BallWinner, DeepLyingPlaymaker, HalfBack, Regista |
| Środkowy pomocnik | CM, LCM, RCM | BoxToBox, Mezzala, Carrilero, AdvancedPlaymaker, DeepLyingPlaymaker |
| Ofensywny pomocnik | CAM | Trequartista, ShadowStriker, Enganche, AdvancedPlaymaker |
| Boczny pomocnik | LM, RM | WideMidfielder, Winger, InsideForward, DefensiveWinger |
| Skrzydłowy | LW, RW | Winger, InsideForward, InvertedWinger, Raumdeuter |
| Napastnik | ST | AdvancedForward, TargetMan, DeepLyingForward, PressingForward, Poacher, FalseNine |

**Dowolność**: Gracz MOŻE przypisać rolę spoza sugerowanej grupy (np. CB jako Regista), ale system wyświetli ostrzeżenie o potencjalnym niedopasowaniu atrybutów.

### 2.3 Position vs PositionSlot

- **Position** — „naturalna” pozycja zawodnika (do profilu kadrowego, versatility, przypisania do slotu). Zbiór uproszczony, np. GK, CB, LB, RB, DM, CM, AM, LW, RW, ST (ok. 10 wartości). Używane w `Player.preferredPositions: Set[Position]` i w trait `versatility`.
- **PositionSlot** — konkretny slot w formacji (18 wartości, w tym LCB, RCB, LCM, RCM, CAM itd.). Każdy gracz na boisku ma dokładnie jeden PositionSlot. Mapowanie: gracz z preferredPositions = {CM, DM} może być przypisany do slotu CDM lub CM; system może ostrzegać, gdy slot nie należy do preferredPositions.

**Spójność**: Sloty LCB/RCB w formacji 4-4-2 (dwa środkowi obrońcy) należy definiować jako LCB i RCB (dwa różne klucze w `roles: Map[PositionSlot, TacticalRole]`), nie jako dwa razy CB.

---

## 3. Formacja

### 3.1 Definicja

Formacja to lista 10 przypisań: `(slot, x, y)` — gdzie (x,y) to koordynaty na boisku 120×80m. Bramkarz jest zawsze na (5, 40).

```scala
case class PositionAssignment(
  slot: PositionSlot,
  baseX: Double,    // 0-120 (0 = własna linia bramkowa, 120 = bramka rywala)
  baseY: Double     // 0-80 (0 = lewa linia boczna, 80 = prawa)
)

case class Formation(
  name: String,
  positions: List[PositionAssignment],       // 10 polowych — bazowe pozycje
  positionsInPossession: List[PositionAssignment],  // pozycje gdy ma piłkę
  positionsOutOfPossession: List[PositionAssignment], // pozycje w obronie
  roles: Map[PositionSlot, TacticalRole]     // rola per slot
)
```

### 3.2 Trzy stany formacji

Kluczowa innowacja: formacja ma TRZY warianty pozycji:

1. **Bazowa** — startowa, z której silnik generuje pozycje
2. **W posiadaniu (in possession)** — gdy zespół ma piłkę (opcjonalna; jeśli brak → bazowa)
3. **Bez piłki (out of possession)** — gdy rywal ma piłkę (opcjonalna; jeśli brak → bazowa)

To pozwala na modelowanie np.:
- Inverted Fullback: w posiadaniu LB przesuwa się z (25, 10) na (45, 30) — wchodzi do środka
- Overlapping WB: w posiadaniu RB przesuwa się z (25, 70) na (55, 75) — wysuwa się wysoko
- Dropping CDM: bez piłki CDM opada z (40, 40) na (25, 40) — tworzy trójkę z CB

**Jak wpływa na algorytmy:**

| Stan formacji | Kiedy aktywny | Wpływ na Pitch Control |
|---------------|---------------|------------------------|
| Bazowa | Restart, przejścia, stałe fragmenty | Neutralna mapa |
| W posiadaniu | Zespół ma piłkę, BuildUp/Progression/FinalThird | Ofensywna mapa (wyższe PC w ataku) |
| Bez piłki | Rywal ma piłkę, pressing/defensive shape | Defensywna mapa (wyższe PC w obronie) |

Pitch Control jest ZAWSZE przeliczany z aktualnym wariantem pozycji — to jest fundamentalne dla DxT i Passing Network.

### 3.3 Pełna dowolność

Gracz może:
- Wybrać preset (np. "4-3-3") i tweakować pozycje
- Ręcznie drag-and-drop 10 graczy na siatce
- Stworzyć asymetryczne formacje (np. 3 CB ale LB wysoko, RB nisko)
- Ustawić zupełnie niestandardowe formacje (np. 2-3-5, diamond z 6 pomocnikami)

Jedyne ograniczenie: 10 polowych + 1 GK. System NIE wymusza "sensowności" — jeśli gracz chce postawić 10 napastników na linii bramki rywala, może to zrobić (choć Pitch Control w obronie będzie katastrofalny).

---

## 4. Role — zachowania behawioralne

### 4.1 Definicja roli

Rola to zestaw modyfikatorów, które mówią silnikowi, jak zawodnik się ZACHOWUJE. Nie wpływa na atrybuty (to dane gracza), ale na WYBÓR akcji.

```scala
case class TacticalRole(
  name: String,
  actionWeights: Map[Phase, ActionWeightModifiers],
  positionShift: PositionShift,
  pressingDuty: PressingDuty,
  runPattern: RunPattern,
  roleMentality: Double,      // 0.0 (ultra defensive) - 1.0 (ultra attacking)
  requiredAttributes: List[(String, Int)]  // sugerowane minimum atrybutów
)

case class ActionWeightModifiers(
  shortPassWeight: Double,     // mnożnik na bazową wagę (1.0 = neutralny)
  longPassWeight: Double,
  crossWeight: Double,
  dribbleWeight: Double,
  shotWeight: Double,
  holdBallWeight: Double,      // trzymanie piłki (czekanie na opcję)
  throughBallWeight: Double,   // podanie prostopadłe
  switchPlayWeight: Double     // zmiana strony
)

case class PositionShift(
  inPossessionDx: Double,      // przesunięcie X w posiadaniu
  inPossessionDy: Double,      // przesunięcie Y w posiadaniu
  outOfPossessionDx: Double,   // przesunięcie X w obronie
  outOfPossessionDy: Double    // przesunięcie Y w obronie
)

enum PressingDuty:
  case Attack    // aktywnie pressuje, inicjuje pressing
  case Support   // wspiera pressing, zamyka przestrzenie
  case Defend    // nie pressuje, trzyma pozycję

enum RunPattern:
  case StayPosition    // minimalne biegi, trzymaj strefę
  case OverlapWide     // biegi szerokie, za skrzydłowym
  case RunBehindLine   // biegi za linię obrony (w głąb)
  case DriftInside     // wchodzenie do środka z boku
  case DropDeep        // opadanie po piłkę
  case FreeRoam        // niestandardowe, nieprzewidywalne
  case ChannelRun      // biegi w korytarze między obrońcami
```

### 4.2 Jak rola wpływa na selectAction

Rola modyfikuje `selectAction` z SILNIK doc §5.3 w trzech punktach:

```
1. WAGI OPCJI: actionWeights mnożą bazowe score'y opcji
   Przykład: InsideForward.dribbleWeight = 1.4
   → opcja "dribble inside" ma score × 1.4, "cross" ma score × 0.6
   → silnik częściej wybiera drybling

2. TARGET ZONES: positionShift zmienia, GDZIE gracz jest na boisku
   → zmienia które strefy DxT są "dostępne" dla niego
   → zmienia z KIM ma IWP (Matchup Matrix)

3. BEZ PIŁKI: runPattern i pressingDuty determinują zachowania off-ball
   → RunBehindLine → wysoki offTheBall × runPattern bonus → OBSO wzrasta
   → PressingDuty.Attack → gracz bierze udział w PPDA → stamina spada szybciej
```

### 4.3 Edytor ról — pełna dowolność

Gracz może:
- **Poziom 1**: Wybrać preset roli z katalogu (~35 ról)
- **Poziom 2**: Wybrać preset i tweakować parametry (suwaki na każdą wagę)
- **Poziom 3**: Stworzyć rolę od zera — ustawić wszystkie wagi ręcznie

Każda rola ma "kartkę" z sugerowanymi atrybutami — jeśli gracz przypisuje "Regista" zawodnikowi z longPassing=6, system pokazuje ostrzeżenie ale NIE blokuje.

---

## 5. Katalog ról: Obrona

### 5.1 Ball-Playing Defender (Środkowy obrońca grający piłką)

**Sloty**: CB, LCB, RCB  
**Duty**: Defend  
**Archetyp**: van Dijk, Stones, Rüdiger

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.3 | Częstsze podania z głębi → xPass_short ważniejszy |
| longPassWeight | 1.1 | Próby długich piłek do przodu |
| dribbleWeight | 1.1 | Okazjonalne wyjścia z piłką |
| positionShift (possession) | dx=+5, dy=0 | Lekko wyżej w posiadaniu |
| pressingDuty | Defend | Nie pressuje, trzyma linię |
| runPattern | StayPosition | Minimalne biegi |
| roleMentality | 0.3 | Zachowawcza |

**Kluczowe atrybuty**: shortPassing > 12, longPassing > 10, composure > 13, positioning > 14

**Wpływ na Pitch Control**: Lekko wyżej w posiadaniu → lepsza linia podań do środka pola. Passing Network: wyższy clustering z CM (tworzy trójkąty podań z głębi).

### 5.2 Stopper

**Sloty**: CB, LCB, RCB  
**Duty**: Defend  
**Archetyp**: Koulibaly, Upamecano, Skriniar

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 0.8 | Proste podania, bez ryzyka |
| longPassWeight | 0.9 | Wybijanie |
| dribbleWeight | 0.5 | Nie drybluje |
| positionShift (OoP) | dx=+3, dy=0 | Wysuwa się na rywala |
| pressingDuty | Support | Wchodzi w pressing gdy piłka blisko |
| runPattern | StayPosition | |
| roleMentality | 0.2 | |

**Kluczowe atrybuty**: tackling > 15, strength > 14, jumping > 13, aggression > 12, bravery > 13

**Wpływ na IWP**: Wysunięty → częstsze starcia 1v1 z napastnikiem → aggression bonus na Dynamic Pressure p_i w okolicy. Ryzyko: jeśli przegra duel → luka za plecami.

### 5.3 Cover

**Sloty**: CB, LCB, RCB  
**Duty**: Defend  
**Archetyp**: Maldini, Marquinhos, Kimpembe (cofnięty)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.0 | Neutralne |
| dribbleWeight | 0.5 | |
| positionShift (OoP) | dx=-3, dy=0 | Cofnięty — pokrywa za Stopperem |
| pressingDuty | Defend | |
| runPattern | StayPosition | |
| roleMentality | 0.1 | Ultra zachowawcza |

**Kluczowe atrybuty**: anticipation > 15, positioning > 16, pace > 12, concentration > 14

**Wpływ na Ghosting**: Niska ghosting deviation (positioning + concentration) → stabilna ostatnia linia. Komplement ze Stopperem: Stopper wysuwa się, Cover zabezpiecza.

### 5.4 Libero

**Sloty**: CB  
**Duty**: Support  
**Archetyp**: Beckenbauer, Bonucci (faza budowania)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.4 | Aktywny w budowaniu |
| longPassWeight | 1.3 | Długie otwarcia |
| dribbleWeight | 1.3 | Wyjścia z piłką do środka |
| positionShift (poss.) | dx=+15, dy=0 | Wysuwa się DO ŚRODKA pola |
| pressingDuty | Support | |
| runPattern | DriftInside | Wchodzi w środkową strefę |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: shortPassing > 14, dribbling > 12, vision > 13, composure > 14, pace > 12

**Wpływ na Pitch Control**: W posiadaniu tworzy dodatkowego "rozgrywającego" — PC w środku rośnie. ALE: luka w obronie gdy piłka stracona — wymaga szybkiej tranzycji.

### 5.5 Fullback (Defensywny)

**Sloty**: LB, RB  
**Duty**: Defend  
**Archetyp**: Azpilicueta, Pavard (defensywny)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| crossWeight | 0.7 | Rzadkie dośrodkowania |
| positionShift (poss.) | dx=+5, dy=0 | Minimalnie do przodu |
| pressingDuty | Defend | Trzyma bok |
| runPattern | StayPosition | |
| roleMentality | 0.2 | |

**Kluczowe atrybuty**: tackling > 14, positioning > 13, stamina > 12, pace > 12

### 5.6 Wing-Back (Ofensywny)

**Sloty**: LB, RB, LWB, RWB  
**Duty**: Attack  
**Archetyp**: Robertson, Alexander-Arnold, Hakimi

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| crossWeight | 1.5 | Częste dośrodkowania → xPass_cross |
| shortPassWeight | 1.1 | Udział w budowaniu |
| positionShift (poss.) | dx=+25, dy=±5 (szerzej) | Bardzo wysoko, szeroko |
| pressingDuty | Attack | Pressuje wysoko na boku |
| runPattern | OverlapWide | Biegi za skrzydłowym |
| roleMentality | 0.7 | Ofensywna |

**Kluczowe atrybuty**: crossing > 14, pace > 14, stamina > 15, workRate > 14

**Wpływ na Pitch Control**: Wysunięty na bok → wysoki PC na skrzydle, ALE ogromna luka za plecami (dx=+25 to 25m wyżej!). DxT na skrzydle rośnie. Metabolic Power: ciągłe biegi góra-dół = najszybsze zużycie staminy w zespole.

### 5.7 Inverted Fullback

**Sloty**: LB, RB  
**Duty**: Support  
**Archetyp**: Cancelo (Guardiola), Walker (środkowy)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.3 | Krótkie podania w środku |
| crossWeight | 0.4 | Prawie nie dośrodkowuje |
| dribbleWeight | 1.1 | Prowadzi piłkę do środka |
| positionShift (poss.) | dx=+10, dy=±20 (do środka) | Wchodzi w half-space/centrum |
| positionShift (OoP) | dx=0, dy=0 | Normalna pozycja bocznego obrońcy |
| pressingDuty | Support | Zamyka środek |
| runPattern | DriftInside | |
| roleMentality | 0.4 | |

**Kluczowe atrybuty**: shortPassing > 13, vision > 12, positioning > 13, composure > 12

**Wpływ na Pitch Control**: W posiadaniu LB z (25, 10) przesuwa się na (35, 30) — extra ciało w centrum. 4-3-3 staje się 3-4-3 / 3-2-5. Passing Network: nowy trójkąt z CM. ALE: lewy bok otwarty — wymaga LW, który pokrywa boczną przestrzeń.

### 5.8 Complete Wing-Back

**Sloty**: LB, RB, LWB, RWB  
**Duty**: Support/Attack  
**Archetyp**: Kimmich (boczny), Alexander-Arnold (hybrydowy)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.2 | |
| crossWeight | 1.2 | |
| dribbleWeight | 1.1 | |
| longPassWeight | 1.1 | |
| positionShift (poss.) | dx=+20, dy=0 | Wysoko ale na swoim boku |
| pressingDuty | Support | |
| runPattern | OverlapWide + DriftInside (hybrid) | |
| roleMentality | 0.6 | |

**Kluczowe atrybuty**: crossing > 12, shortPassing > 12, pace > 13, stamina > 15, workRate > 14, decisions > 12

---

## 6. Katalog ról: Środek pola

### 6.1 Anchor Man

**Sloty**: CDM  
**Duty**: Defend  
**Archetyp**: Casemiro, Fabinho, Ndidi

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.0 | Proste podania |
| longPassWeight | 0.7 | Unika ryzykownych |
| dribbleWeight | 0.5 | |
| positionShift | dx=0, dy=0 | STOI na pozycji. Zawsze. |
| pressingDuty | Defend | Nie pressuje — zabezpiecza |
| runPattern | StayPosition | |
| roleMentality | 0.1 | |

**Kluczowe atrybuty**: positioning > 16, anticipation > 15, tackling > 15, concentration > 14, teamwork > 14

**Wpływ na Ghosting**: Najniższa ghosting deviation w zespole (StayPosition + wysoki positioning). Wpływ na Passing Network: WYSOKI Betweenness Centrality — wszystko przechodzi przez niego. Man-marking Anchora = zniszczenie sieci podań.

### 6.2 Ball Winner

**Sloty**: CDM, CM  
**Duty**: Defend  
**Archetyp**: Kanté, Rice (agresywny), Tchouaméni

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| dribbleWeight | 0.6 | Po odbiórze — prosta piłka |
| positionShift (OoP) | dx=+5, dy=varies | Wysuwa się na piłkę |
| pressingDuty | Attack | AKTYWNIE pressuje |
| runPattern | ChannelRun | Biega za piłką |
| roleMentality | 0.3 | |

**Kluczowe atrybuty**: tackling > 16, workRate > 16, stamina > 15, acceleration > 14, aggression > 13

**Wpływ na PPDA**: Główny "executioner" pressingu. Wysoki p_i w Dynamic Pressure. ALE: opuszcza pozycję → luka w środku. Foul risk: aggression/tackling ratio → kartki.

### 6.3 Deep-Lying Playmaker

**Sloty**: CDM, CM  
**Duty**: Support  
**Archetyp**: Pirlo, Jorginho, Busquets

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.4 | Częste krótkie podania |
| longPassWeight | 1.3 | Długie otwarcia |
| throughBallWeight | 1.2 | Podania prostopadłe |
| dribbleWeight | 0.7 | |
| positionShift (poss.) | dx=-5, dy=0 | Opada po piłkę |
| pressingDuty | Defend | Nie pressuje |
| runPattern | DropDeep | |
| roleMentality | 0.3 | |

**Kluczowe atrybuty**: shortPassing > 16, longPassing > 15, vision > 17, composure > 15, decisions > 14, firstTouch > 14

**Wpływ na Passing Network**: Ekstremalnie wysoki Betweenness Centrality i Eigenvector Centrality. Sieć podań jest "gwiaździsta" — wszystko przez DLP. Odcięcie go = kolaps budowania.

### 6.4 Half-Back

**Sloty**: CDM  
**Duty**: Defend  
**Archetyp**: Fernandinho (Guardiola), Rodri (cofnięty)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.1 | |
| positionShift (poss.) | dx=-10, dy=0 | Opada MIĘDZY CB |
| positionShift (OoP) | dx=0, dy=0 | Normalny CDM |
| pressingDuty | Defend | |
| runPattern | DropDeep | |
| roleMentality | 0.2 | |

**Kluczowe atrybuty**: positioning > 15, shortPassing > 13, anticipation > 14, composure > 13

**Wpływ na formację**: W posiadaniu 4-1-4-1 → 3-2-4-1 (HB opada do trójki z CB). Pitch Control: wzmacnia obronę w budowaniu, zwalnia bocznych obrońców do wysunięcia. Passing Network: mostek CB↔CM.

### 6.5 Regista

**Sloty**: CDM  
**Duty**: Support  
**Archetyp**: Pirlo, Alonso, Beckham (głęboki)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| longPassWeight | 1.6 | Dominują długie podania |
| switchPlayWeight | 1.5 | Zmiany strony |
| throughBallWeight | 1.3 | |
| shortPassWeight | 1.0 | |
| dribbleWeight | 0.8 | |
| positionShift (poss.) | dx=-3, dy=0 | Lekko cofnięty |
| pressingDuty | Defend | |
| roleMentality | 0.4 | |

**Kluczowe atrybuty**: longPassing > 17, vision > 17, technique > 15, composure > 14, firstTouch > 14

**Wpływ na xT**: Długie podania przeskakują 3-4 stref DxT jednocześnie. Wysoki xT_gained per podanie, ale niższe xPass (podania dalekie). Passing Network: niski Clustering (nie gra trójkątów), wysoki Betweenness (hub).

### 6.6 Box-to-Box

**Sloty**: CM, LCM, RCM  
**Duty**: Support  
**Archetyp**: Kanté (ofensywny), Goretzka, Bellingham

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.0 | Neutralne |
| dribbleWeight | 1.1 | |
| shotWeight | 1.1 | Wchodzi w strefę strzału |
| positionShift (poss.) | dx=+10, dy=0 | Wysuwa się w ataku |
| positionShift (OoP) | dx=-5, dy=0 | Cofa się w obronie |
| pressingDuty | Support | |
| runPattern | ChannelRun + RunBehindLine | Biega wszędzie |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: stamina > 16, workRate > 16, tackling > 12, shortPassing > 12, finishing > 10, pace > 12

**Wpływ na Metabolic Power**: NAJWYŻSZE zużycie energii w zespole (ciągłe biegi box-to-box). gBRI: wysokie — biegi tworzą przestrzeń. Trade-off: po 70 min stamina krytyczna bez zmiany.

### 6.7 Mezzala

**Sloty**: CM, LCM, RCM  
**Duty**: Attack  
**Archetyp**: Barella, de Bruyne (boczny), Pedri

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| dribbleWeight | 1.3 | Prowadzi piłkę w half-space |
| throughBallWeight | 1.3 | |
| shotWeight | 1.2 | |
| positionShift (poss.) | dx=+15, dy=±15 (na bok) | Wchodzi w half-space |
| pressingDuty | Support | |
| runPattern | DriftInside + RunBehindLine | |
| roleMentality | 0.6 | |

**Kluczowe atrybuty**: dribbling > 14, shortPassing > 13, vision > 13, pace > 12, offTheBall > 13

**Wpływ na DxT**: Zajmuje half-space (strefy o naturalnie wysokim DxT). Tworzy overload na boku — połączenie z WB i W. Matchup: walczy z bocznym CM lub FB rywala.

### 6.8 Carrilero

**Sloty**: CM, LCM, RCM  
**Duty**: Support  
**Archetyp**: Koke, Saúl, Valverde (dyscyplinowany)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.1 | Proste, bezpieczne |
| dribbleWeight | 0.7 | |
| positionShift | dx=0, dy=±10 (shuttle) | Kursuje bokiem |
| pressingDuty | Support | Zamyka korytarze boczne |
| runPattern | StayPosition (boczny shuttle) | |
| roleMentality | 0.3 | |

**Kluczowe atrybuty**: positioning > 14, stamina > 14, workRate > 14, teamwork > 15, tackling > 12

**Wpływ na Defensive Shape**: Pokrywa boczny korytarz — Compactness rośnie. Passing Network: mostek centrum↔bok, ale niski Eigenvector (gra bezpieczne podania).

### 6.9 Advanced Playmaker

**Sloty**: CM, CAM  
**Duty**: Attack  
**Archetyp**: de Bruyne, Özil, Bruno Fernandes

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| throughBallWeight | 1.5 | Kluczowe podania |
| shortPassWeight | 1.3 | |
| switchPlayWeight | 1.2 | |
| shotWeight | 1.1 | |
| positionShift (poss.) | dx=+10, dy=0 | Wysuwa się |
| pressingDuty | Support | |
| roleMentality | 0.6 | |

**Kluczowe atrybuty**: vision > 17, shortPassing > 16, decisions > 15, technique > 14, composure > 14

### 6.10 Trequartista

**Sloty**: CAM  
**Duty**: Attack  
**Archetyp**: Messi (wolna rola), Dybala, Riquelme

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| dribbleWeight | 1.4 | Niesie piłkę |
| throughBallWeight | 1.4 | Kluczowe podania |
| shotWeight | 1.2 | |
| positionShift | dx=varies, dy=varies | **FreeRoam** — pozycja nieprzewidywalna |
| pressingDuty | Defend (!) | NIE pressuje |
| runPattern | FreeRoam | |
| roleMentality | 0.7 | |

**Kluczowe atrybuty**: dribbling > 16, vision > 16, flair > 15, technique > 16, composure > 15, decisions > 14

**Wpływ na Passing Network**: FreeRoam oznacza NISKI Betweenness (nie jest stałym hubem) ale WYSOKI VAEP per akcja. Motif Analysis: rywale nie mogą przewidzieć wzorców, bo Trequartista nie ma stałych partnerów. Teamwork gracza determinuje, jak dalece odchodzi od systemu.

### 6.11 Shadow Striker

**Sloty**: CAM  
**Duty**: Attack  
**Archetyp**: Müller, Firmino (ofensywny), Havertz

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shotWeight | 1.4 | Szuka strzału |
| dribbleWeight | 1.0 | |
| positionShift (poss.) | dx=+15, dy=0 | Wskakuje za linię ataku |
| pressingDuty | Attack | Pressuje wysoko |
| runPattern | RunBehindLine | Biegi za obronę |
| roleMentality | 0.8 | |

**Kluczowe atrybuty**: offTheBall > 16, finishing > 14, anticipation > 15, pace > 12, composure > 13

**Wpływ na OBSO**: Bardzo wysoki — ciągłe biegi w strefy o wysokim OBSO. gBRI: topowy. Matchup: walczy z CB rywala (bo wchodzi w ich strefę).

### 6.12 Enganche

**Sloty**: CAM  
**Duty**: Support  
**Archetyp**: Riquelme (klasyczny), Isco, James Rodríguez

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| throughBallWeight | 1.5 | |
| shortPassWeight | 1.4 | |
| dribbleWeight | 1.0 | |
| positionShift | dx=0, dy=0 | STOI — klasyczna 10-ka |
| pressingDuty | Defend | NIE pressuje wcale |
| runPattern | StayPosition | |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: vision > 18, shortPassing > 16, technique > 16, firstTouch > 16, composure > 15

**Trade-off**: Genialny z piłką, niewidzialny bez. pressingDuty=Defend → zespół gra 10v11 w pressingu. Decyzja trenera: czy kreacja Enganche jest warta "pustego" slotu w defensywie?

---

## 7. Katalog ról: Atak

### 7.1 Advanced Forward

**Sloty**: ST  
**Duty**: Attack  
**Archetyp**: Lewandowski, Kane, Benzema

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shotWeight | 1.3 | |
| dribbleWeight | 1.1 | |
| holdBallWeight | 1.1 | |
| throughBallWeight | 1.0 | |
| positionShift (poss.) | dx=+5, dy=0 | Wysoki, centralny |
| pressingDuty | Support | |
| runPattern | ChannelRun | |
| roleMentality | 0.7 | |

**Kluczowe atrybuty**: finishing > 15, offTheBall > 14, composure > 13, firstTouch > 13

### 7.2 Target Man

**Sloty**: ST  
**Duty**: Support/Attack  
**Archetyp**: Giroud, Dzeko, Weghorst

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| holdBallWeight | 1.6 | Trzyma piłkę plecami do bramki |
| shortPassWeight | 1.2 | Odgrywa do nadchodzących |
| crossWeight (target) | 1.0 | Cel dośrodkowań |
| dribbleWeight | 0.5 | Nie drybluje |
| positionShift | dx=0, dy=0 | Centralnie, wysoko |
| pressingDuty | Support | |
| runPattern | StayPosition | Punkt odniesienia |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: strength > 16, heading > 15, jumping > 14, firstTouch > 13, balance > 13

**Wpływ na IWP**: Dominujący w IWP_physical i IWP_aerial. holdBall → nowa akcja w selectAction: trzymaj piłkę plecami, czekaj na wsparcie → strength vs defender strength. DxT: nie zmienia stref, ale tworzy "punkt rozegrania" wysoko.

### 7.3 Deep-Lying Forward

**Sloty**: ST  
**Duty**: Support  
**Archetyp**: Firmino, Benzema (cofnięty), Totti

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.4 | Łączy grę |
| throughBallWeight | 1.2 | |
| dribbleWeight | 1.2 | |
| positionShift (poss.) | dx=-15, dy=0 | **Opada głęboko** |
| pressingDuty | Support | |
| runPattern | DropDeep | |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: shortPassing > 14, vision > 14, firstTouch > 15, dribbling > 13, decisions > 13

**Wpływ na DxT**: Opada → strefa ST jest "pusta" → DxT tam maleje, ALE strefy boczne rosną (skrzydłowi wchodzą do wolnej przestrzeni). False Nine robił to słynnie u Guardioli.

### 7.4 Pressing Forward

**Sloty**: ST  
**Duty**: Attack (pressing) / Support (posiadanie)  
**Archetyp**: Firmino (pressing), Haaland (chase), Werner

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shotWeight | 1.1 | |
| dribbleWeight | 0.8 | Po odbiórze — prosta piłka |
| positionShift (OoP) | dx=+10, dy=0 | WYSOKO — przy CB rywala |
| pressingDuty | **Attack** | **Pierwszy presser** |
| runPattern | ChannelRun | Ściga CB |
| roleMentality | 0.6 | |

**Kluczowe atrybuty**: workRate > 16, pace > 14, acceleration > 14, stamina > 14, aggression > 13

**Wpływ na PPDA**: Inicjuje pressing na CB rywala → zamyka opcje budowania. Metabolic Power: BARDZO wysoki koszt (ciągły pressing + sprinty). Trade-off: po 60 min stamina krytyczna.

### 7.5 Poacher

**Sloty**: ST  
**Duty**: Attack  
**Archetyp**: Inzaghi, Gerd Müller, Chicharito

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shotWeight | 1.6 | Wszystko o strzale |
| holdBallWeight | 0.5 | |
| dribbleWeight | 0.6 | |
| shortPassWeight | 0.7 | |
| positionShift (poss.) | dx=+10, dy=0 | Najwyżej w zespole |
| pressingDuty | Defend | NIE pressuje |
| runPattern | RunBehindLine | Biegi za obronę |
| roleMentality | 0.8 | |

**Kluczowe atrybuty**: finishing > 17, offTheBall > 17, anticipation > 16, composure > 14

**Wpływ na OBSO**: Maksymalny — ciągłe pozycjonowanie w strefach o najwyższym OBSO. xG: wysoki wolumen strzałów z bliskiego dystansu. ALE: zero wkładu w budowanie, zero pressingu → 10v11 w obronie i budowaniu.

### 7.6 False Nine

**Sloty**: ST  
**Duty**: Support  
**Archetyp**: Messi (2011), Firmino, Griezmann

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.4 | |
| throughBallWeight | 1.3 | |
| dribbleWeight | 1.3 | |
| shotWeight | 0.8 | |
| positionShift (poss.) | dx=-20, dy=0 | **Opada bardzo głęboko** |
| pressingDuty | Support | |
| runPattern | DropDeep + FreeRoam | |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: vision > 16, shortPassing > 15, dribbling > 15, firstTouch > 16, offTheBall > 14

**Wpływ na DxT + Pitch Control**: Fundamentalny. ST opada z (105, 40) na (85, 40). Strefa napastnika jest PUSTA → CB rywala nie ma kogo kryć → wychodzi z linii → luka. Skrzydłowi/Mezzale wchodzą w tę lukę → DxT w half-space eksploduje. Największa formacyjna "sztuczka" w nowoczesnym futbolu.

### 7.7 Winger

**Sloty**: LW, RW, LM, RM  
**Duty**: Attack  
**Archetyp**: Sané, Vinicius Jr (szeroki), Sterling

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| crossWeight | 1.5 | Dośrodkowania |
| dribbleWeight | 1.2 | Na boku |
| positionShift (poss.) | dx=+5, dy=±5 (szerzej) | Szeroko, wysoko |
| pressingDuty | Support | |
| runPattern | OverlapWide | Biegi bokiem |
| roleMentality | 0.6 | |

**Kluczowe atrybuty**: crossing > 14, pace > 15, dribbling > 14, agility > 13

### 7.8 Inside Forward

**Sloty**: LW, RW, LM, RM  
**Duty**: Attack  
**Archetyp**: Salah, Mané, Robben

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| dribbleWeight | 1.4 | Cut inside |
| shotWeight | 1.3 | Strzał po wejściu |
| crossWeight | 0.5 | Rzadko dośrodkowuje |
| positionShift (poss.) | dx=+10, dy=±15 (do środka) | Half-space → centrum |
| pressingDuty | Support | |
| runPattern | DriftInside + RunBehindLine | |
| roleMentality | 0.7 | |

**Kluczowe atrybuty**: finishing > 14, dribbling > 16, pace > 15, agility > 14, composure > 13

**Wpływ na Matchup**: NIE walczy z FB → walczy z CB. Jeśli preferred_foot = opposite to strony (np. Left foot na RW) → cut inside na mocną nogę → xG rośnie przy strzale.

### 7.9 Inverted Winger

**Sloty**: LW, RW  
**Duty**: Support  
**Archetyp**: Bernardo Silva (bok), Foden (szeroki)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.4 | Kreacja z half-space |
| throughBallWeight | 1.3 | |
| crossWeight | 0.6 | |
| dribbleWeight | 1.0 | |
| positionShift (poss.) | dx=0, dy=±15 (do środka) | Half-space, ale nie tak wysoko |
| pressingDuty | Support | |
| runPattern | DriftInside | |
| roleMentality | 0.5 | |

**Kluczowe atrybuty**: shortPassing > 15, vision > 14, firstTouch > 14, technique > 13

### 7.10 Raumdeuter (Interpretator przestrzeni)

**Sloty**: LW, RW  
**Duty**: Attack  
**Archetyp**: Müller (skrzydłowy), Sané (roaming)

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shotWeight | 1.3 | |
| dribbleWeight | 0.8 | |
| positionShift | **Dynamiczny** — szuka stref o najwyższym OBSO | |
| pressingDuty | Support | |
| runPattern | FreeRoam + RunBehindLine | |
| roleMentality | 0.7 | |

**Kluczowe atrybuty**: offTheBall > 17, anticipation > 16, finishing > 14, composure > 13

**Wpływ na OBSO**: Raumdeuter aktywnie szuka stref o najwyższym OBSO — jego pozycja się ZMIENIA dynamicznie w trakcie akcji. Rywale nie mogą go "pilnować" bo nie ma stałej pozycji. Traquility/Motif Analysis: rywale nie mogą go przewidzieć.

### 7.11 Wide Midfielder (Szeroki pomocnik)

**Sloty**: LM, RM  
**Duty**: Support  
**Archetyp**: Koke (boczny), Milner, Park Ji-sung

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.1 | Udział w budowaniu |
| crossWeight | 1.2 | Dośrodkowania z głębi |
| dribbleWeight | 0.8 | Rzadziej niż Winger |
| positionShift (poss.) | dx=+5, dy=0 | Lekko do przodu, na boku |
| pressingDuty | Support | Zamyka boczny korytarz |
| runPattern | OverlapWide | Biegi wzdłuż linii |
| roleMentality | 0.4 | |

**Kluczowe atrybuty**: crossing > 12, shortPassing > 12, stamina > 13, workRate > 13, positioning > 12

**Wpływ**: Mostek między obroną a atakiem na boku; niższa ofensywność niż Winger, wyższa dyscyplina pozycyjna. Passing Network: dobre połączenie z FB i CM.

### 7.12 Defensive Winger

**Sloty**: LM, RM  
**Duty**: Defend  
**Archetyp**: Willian (w Chelsea), defensive wide midfielder

| Parametr | Wartość | Efekt algorytmiczny |
|----------|---------|---------------------|
| shortPassWeight | 1.0 | Proste podania |
| crossWeight | 0.7 | Rzadkie dośrodkowania |
| dribbleWeight | 0.6 | Nie dominuje piłką |
| positionShift (OoP) | dx=-5, dy=0 | Cofnięty — pomaga obrońcy |
| pressingDuty | **Attack** (na swoim skrzydle) | Pressuje bocznego obrońcę rywala |
| runPattern | StayPosition + ChannelRun | Trzyma pas, ściga przy utracie |
| roleMentality | 0.3 | |

**Kluczowe atrybuty**: tackling > 12, workRate > 15, stamina > 14, positioning > 13, pace > 12

**Wpływ na PPDA**: Wysoki udział w pressingu na boku; odciąża bocznego obrońcę. Trade-off: mało kreacji w ataku — zespół gra praktycznie 3-4-3 w ofensywie.

---

## 8. Katalog ról: Bramkarz

### 8.1 Shot-Stopper

**Duty**: Defend. Priorytet: obrona strzałów. Minimalna dystrybucja.  
**Kluczowe atrybuty**: reflexes > 16, gkPositioning > 15

### 8.2 Sweeper Keeper

**Duty**: Support. Wysoko, wychodzi, gra piłkę nogami.  
**Kluczowe atrybuty**: distribution > 14, pace > 10, commandOfArea > 14, oneOnOnes > 13  
**Wpływ na Pitch Control**: GK na (15, 40) zamiast (5, 40) → dodatkowy gracz w budowaniu. ALE: luka za plecami przy długich piłkach.

### 8.3 Ball-Playing GK

**Duty**: Support. Jak Shot-Stopper ale z dystrybucją.  
**Kluczowe atrybuty**: distribution > 15, reflexes > 14, composure > 14  
**Wpływ na Passing Network**: GK jest częścią sieci podań — dodatkowa opcja pod pressingiem.

---

## 9. Instrukcje indywidualne

Instrukcje nadpisują domyślne zachowanie roli dla KONKRETNEGO gracza.

```scala
case class PlayerInstruction(
  role: TacticalRole,
  overrides: Set[InstructionOverride]
)

enum InstructionOverride:
  case CutInside            // dribbleWeight × 1.3, crossWeight × 0.5
  case StayWide             // crossWeight × 1.3, dribbleWeight × 0.7 do środka
  case ShootOnSight         // shotWeight × 1.5
  case PlaySimple           // riskTolerance × 0.5, longPassWeight × 0.5
  case DribbleMore          // dribbleWeight × 1.4
  case HoldPosition         // positionShift = (0,0), runPattern = StayPosition
  case RoamFromPosition     // runPattern = FreeRoam
  case StayBack             // positionShift.inPossession.dx = 0
  case GetForward           // positionShift.inPossession.dx += 15
  case MarkPlayer(target: PlayerId)  // man-marking → IWP zawsze vs ten gracz
  case StayOnFeet           // foulRisk × 0.5, ale tackleCommit × 0.7
  case DiveIntoTackles      // tackleCommit × 1.3, foulRisk × 1.4
  case CrossFromByline      // preferuj dośrodkowania z linii końcowej
  case CrossEarly           // preferuj wczesne dośrodkowania z głębi
  case MoveIntoChannels     // runPattern += ChannelRun
  case SwapPositions(with: PositionSlot) // dynamiczna zamiana pozycji w grze
```

**Wpływ na algorytmy**: Każda instrukcja to mnożnik na konkretne wagi w `selectAction`. `MarkPlayer` zmienia Matchup Matrix — ten obrońca ZAWSZE walczy z wybranym rywalem niezależnie od strefy.

---

## 10. Strategia zespołowa

### 10.1 Build-Up Style (Styl budowania)

```scala
case class BuildUpStyle(
  progressionPreference: Double,   // -1.0 (przez boki) do +1.0 (przez środek)
  tempo: Double,                   // 0.0 (wolne) do 1.0 (szybkie)
  riskTolerance: Double,           // 0.0 (bezpieczne) do 1.0 (ryzykowne)
  widthInPossession: Double,       // 0.0 (wąskie) do 1.0 (szerokie)
  directness: Double               // 0.0 (krótkie) do 1.0 (długie piłki)
)
```

| Parametr | Algorytm | Wpływ |
|----------|----------|-------|
| progressionPreference | DxT routing | Boczne strefy DxT ×1.3 (wide) lub centralne ×1.3 (central) |
| tempo | selectAction delay | Szybkie = mniej czasu na decyzję = Decisions ważniejsze |
| riskTolerance | xPass threshold | Niski = wybiera podania o xPass > 0.75; Wysoki = akceptuje xPass > 0.50 |
| widthInPossession | Formation spread | Szerokie = positionShift bocznych +10m na zewnątrz |
| directness | Long vs Short pass | Wysoki = longPassWeight ×1.5 globalnie |

### 10.2 Pressing Strategy

**Typ Zone**: Strefy boiska w siatce DxT/symulacji. Spójne z SILNIK: siatka 12×8 = 96 stref. Identyfikator strefy to liczba 1–96 (np. mapowanie: `zoneId = (x_index * 8) + y_index + 1` dla indeksów 0-based). Używane w `triggerZones` (gdzie włączyć pressing) i `PressingTrap.targetZone`. W SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md stan meczu używa `zone: Int` (1–96).

```scala
type Zone = Int  // 1-96, siatka 12×8 (kierunek X = wzdłuż boiska, Y = szerokość)

case class PressingStrategy(
  pressingLine: Double,           // 0-100 (0=własna bramka, 100=bramka rywala)
  intensity: Double,              // 0-100
  counterPressDuration: Double,   // sekundy (0-8)
  triggerZones: Set[Zone],        // strefy aktywacji (np. strefy 60-96 = tercja ataku)
  pressingTraps: List[PressingTrap]
)

case class PressingTrap(
  targetZone: Zone,               // strefa, do której "wciągamy" rywala
  triggerPlayer: PositionSlot,    // kto inicjuje pułapkę
  closingPlayers: Set[PositionSlot] // kto zamyka
)
```

| Parametr | Algorytm | Wpływ |
|----------|----------|-------|
| pressingLine | Player positions (OoP) | Wysoka = obrońcy wyżej → PC wyżej ale luka za plecami |
| intensity | PPDA target | 90 → PPDA ~7; 40 → PPDA ~15 |
| counterPressDuration | Counter-press window | 5s = agresywny gegenpress; 0s = brak |
| pressingTraps | Dynamic Pressure routing | Celowe wystawienie opcji podania, potem zamknięcie |

### 10.3 Defensive Line

```scala
case class DefensiveLine(
  height: Double,         // 0-100
  width: Double,          // 0-100
  offsideTrap: Boolean,
  markingStyle: MarkingStyle  // Zonal, Man, Hybrid
)

enum MarkingStyle:
  case Zonal   // obrońcy trzymają strefy → positioning ważniejszy
  case Man     // obrońcy śledzą konkretnych rywali → pace, tackling ważniejsze
  case Hybrid  // man-marking w polu karnym, zonal poza nim
```

| Parametr | Algorytm | Wpływ |
|----------|----------|-------|
| height | Defensive line position | Wysoka + offsideTrap = ryzyko vs kontry ale kompresja boiska |
| width | Compactness Coefficient | Wąska = wyższy Compactness → trudniej przebić środek |
| markingStyle | IWP pairing | Zonal → Pitch Control ważniejszy; Man → Matchup Matrix ważniejszy |

### 10.4 Attacking Approach

```scala
case class AttackingApproach(
  tempoInFinalThird: Double,       // 0-1
  crossingPreference: Double,      // 0-1 (0=nigdy, 1=priorytet)
  shootingRange: Double,           // 0-1 (0=tylko w polu karnym, 1=z daleka)
  overlapUnderlap: OverlapPreference,
  counterAttackPriority: Double,   // 0-1
  playmakers: Set[PositionSlot]    // kto jest głównym kreatorem
)

enum OverlapPreference:
  case Overlap     // FB biegnie ZA skrzydłowego → OverlapWide run
  case Underlap    // FB biegnie PRZED skrzydłowego → DriftInside run
  case Balanced    // sytuacyjnie
  case None        // FB nie dołącza do ataku
```

| Parametr | Algorytm | Wpływ |
|----------|----------|-------|
| tempoInFinalThird | Action delay in FinalThird | Szybkie = mniej czasu na ustawienie obrony → niższe PC rywala |
| crossingPreference | crossWeight global modifier | Wysoki = więcej dośrodkowań → heading, jumping decydują |
| shootingRange | Shot viability threshold | Wysoki = strzały z 25m+ → longShots decyduje |
| counterAttackPriority | Transition speed | Wysoki = przy odbiórze natychmiastowa progresja zamiast posiadania |

---

## 11. Mentalność — meta-parametr

Mentalność to GLOBALNY modyfikator, który przesuwa WSZYSTKIE parametry jednocześnie.

```scala
enum Mentality:
  case UltraDefensive  // modifier = 0.2
  case Defensive       // modifier = 0.35
  case Cautious        // modifier = 0.45
  case Balanced        // modifier = 0.5
  case Positive        // modifier = 0.6
  case Attacking       // modifier = 0.7
  case UltraAttacking  // modifier = 0.85
```

**Wpływ na parametry:**

```
effective_pressingLine = base_pressingLine × (0.5 + mentality_modifier)
effective_riskTolerance = base_riskTolerance × (0.5 + mentality_modifier)
effective_defensiveHeight = base_height × (0.5 + mentality_modifier)
effective_shootingRange = base_shootingRange × (0.5 + mentality_modifier)
position_shift_global_dx = (mentality_modifier - 0.5) × 10  // +5m przy Attacking
```

**Przykład**: Gracz z base pressingLine=60, mentalność Attacking (0.7):
- effective_pressingLine = 60 × 1.2 = 72 (wyższy pressing)
- position_shift = +2m do przodu dla wszystkich

**Zastosowanie taktyczne**: Przegrywam 0:1 po 75 min → zmieniam mentalność na Attacking → cały zespół wysuwa się, pressing intensywniejszy, więcej ryzyka. Jednym kliknięciem zamiast 8 suwaków.

---

## 12. Triggery i plany alternatywne

### 12.1 Triggery in-game

```scala
case class TacticalTrigger(
  condition: TriggerCondition,
  action: TacticalChange,
  oneShot: Boolean = true    // aktywuj raz czy ciągle sprawdzaj
)

sealed trait TriggerCondition
case class ScoreCondition(scoreDiff: IntRange) extends TriggerCondition
case class TimeCondition(afterMinute: Int) extends TriggerCondition
case class MomentumCondition(fieldTiltBelow: Double) extends TriggerCondition
case class StaminaCondition(avgStaminaBelow: Double) extends TriggerCondition
case class PressureCondition(ppdaAbove: Double) extends TriggerCondition
case class RedCardCondition(team: TeamRef) extends TriggerCondition
case class XGCondition(xgDiff: DoubleRange) extends TriggerCondition
case class CombinedCondition(conditions: List[TriggerCondition]) extends TriggerCondition

sealed trait TacticalChange
case class ChangeFormation(to: Formation) extends TacticalChange
case class ChangeMentality(to: Mentality) extends TacticalChange
case class ChangePressingLine(to: Double) extends TacticalChange
case class ChangeRole(slot: PositionSlot, newRole: TacticalRole) extends TacticalChange
case class MakeSubstitution(out: PlayerId, in: PlayerId) extends TacticalChange
case class AddInstruction(player: PlayerId, inst: InstructionOverride) extends TacticalChange
case class SwitchToGamePlan(planId: String) extends TacticalChange
```

### 12.2 Plany alternatywne (Plan B/C)

Gracz definiuje do 3 kompletnych GamePlanów:
- **Plan A**: Domyślny
- **Plan B**: Alternatywny (np. bardziej defensywny)
- **Plan C**: Awaryjny (np. po czerwonej kartce)

Triggery mogą przełączać między planami: `SwitchToGamePlan("B")`.

---

## 13. Stałe fragmenty gry — taktyka

### 13.1 Rożne — schemat ataku

```scala
case class CornerAttackRoutine(
  delivery: CornerDelivery,
  runners: List[CornerRunner],
  blockers: List[PositionSlot],     // kto stawia bloki
  shortOption: Option[ShortCorner]
)

enum CornerDelivery:
  case Inswing      // zakręcenie do bramki
  case Outswing     // zakręcenie od bramki  
  case Driven       // niska, mocna piłka
  case NearPost     // na bliski słupek

case class CornerRunner(
  player: PositionSlot,
  targetZone: Int,        // 1-15 (GMM zone)
  timing: RunTiming       // Early, OnDelivery, Late
)
```

### 13.2 Rożne — schemat obrony

```scala
case class CornerDefenseRoutine(
  style: MarkingStyle,     // Zonal, Man, Hybrid
  zonalPositions: List[(PositionSlot, Int)],  // slot → GMM zone
  manMarkAssignments: Map[PositionSlot, PositionSlot], // kto kogo kryje
  nearPostGuard: PositionSlot,
  farPostGuard: PositionSlot,
  edgeOfBoxPlayers: List[PositionSlot]  // kto stoi na granicy pola
)
```

### 13.3 Rzuty wolne

```scala
case class FreeKickRoutine(
  taker: PositionSlot,
  directShot: Boolean,
  curve: CurveDirection,      // Left, Right
  target: FreeKickTarget,     // NearPost, FarPost, OverWall, UnderWall
  decoyRunners: List[PositionSlot]  // biegi odciągające
)
```

### 13.4 Rzuty z autów (wrzuty)

```scala
case class ThrowInConfig(
  defaultTaker: PositionSlot,           // kto zwykle wykonuje wrzut (np. FB)
  longThrowTaker: Option[PositionSlot], // jeśli trait longThrow — kto może wykonać long throw
  shortOption: Boolean,                 // czy oferować krótką opcję (do stopy)
  targetZones: List[Int],               // preferowane strefy docelowe (1-96 lub GMM)
  runners: List[PositionSlot]            // kto szuka pozycji na odbiór
)
```

**Long Throw**: Gdy `longThrowTaker` jest ustawiony i gracz ma trait `longThrow = true`, wrzut z głębi własnej połowy może być traktowany jak quasi-rożny → te same mechaniki GMM + IWP aerial w polu karnym. W przeciwnym razie wrzut rozstrzyga xPass (krótkie podanie) lub długie podanie w strefę.

---

## 14. Pełny łańcuch: Taktyka → Algorytm → Wynik

### Przykład: Jak "Inverted Fullback" wpływa na mecz

```
DECYZJA GRACZA:
  LB = Inverted Fullback
  positionShift (poss.) = dx=+10, dy=+20 (do środka)

WPŁYW NA PITCH CONTROL:
  Bazowa pozycja LB: (25, 10) — lewy obrońca
  W posiadaniu:      (35, 30) — centralna strefa
  → PC na lewym boku SPADA (nikt tam nie jest)
  → PC w centrum ROŚNIE (dodatkowe ciało)

WPŁYW NA DxT:
  Centralne strefy progresji: DxT × 1.1 (wyższy PC)
  Boczne lewe strefy: DxT × 0.8 (niższy PC)
  → Silnik preferuje budowanie przez ŚRODEK

WPŁYW NA PASSING NETWORK:
  LB teraz sąsiaduje z CM i CDM zamiast z LW
  → nowe trójkąty podań (LB-CM-CDM)
  → Clustering Coefficient rośnie w centrum
  → Graph Density rośnie (więcej opcji w środku)
  ALE: LW jest odcięty z boku (mniej opcji podań do niego)

WPŁYW NA MATCHUP:
  LB w centrum → walczy z CM rywala (nie z RW)
  → jeśli LB ma shortPassing=14, vision=13 → dobry w centrum
  → jeśli rywale mają agresywnego CM → IWP fizyczne mogą być niekorzystne

WPŁYW NA OBRONĘ (BEZ PIŁKI):
  LB wraca na (25, 10) — normalna pozycja
  ALE: tranzycja z (35, 30) na (25, 10) trwa czas
  → jeśli rywal kontruje po stracie → LB jest 15m za daleko
  → Pitch Control na lewym boku = 0.3 (krytycznie niski)
  → kontra rywala prawym bokiem = wysoki DxT → niebezpieczeństwo

TRADE-OFF:
  + Lepsza kontrola centrum, więcej opcji podań, wyższe PC w budowaniu
  - Lewy bok otwarty na kontry, LW musi pokrywać defensywnie lub LW = Winger (defensive)
```

### Przykład: Jak "False Nine" zmienia mapę zagrożeń

```
DECYZJA GRACZA:
  ST = False Nine, LW = Inside Forward, RW = Inside Forward

WPŁYW NA DxT (FUNDAMENTALNY):
  ST opada z (105, 40) na (85, 40)
  → DxT w strefie napastnika (96-90) SPADA (nikt tam nie jest)
  → ALE: CB rywala wysuwa się za False Nine → luka za CB
  → LW i RW wchodzą w lukę → DxT w half-space (strefy 80-95, bok) EKSPLODUJE
  → Silnik kieruje ataki na LW i RW wchodzących za obronę

WPŁYW NA MATCHUP:
  False Nine walczy z CDM rywala (bo opada w jego strefę)
  → jeśli False Nine ma dribbling=16, firstTouch=17 vs CDM tackling=14 → IWP=0.58
  LW i RW walczą z CB rywala (bo wchodzą centralnie)
  → jeśli CB jest wolny (pace=12) a IF ma pace=18 → IWP_sprint=0.72

WPŁYW NA PRESSING:
  False Nine = Support duty → pressuje umiarkowanie
  → PPDA wyższy niż z Pressing Forward
  → kompensacja: LW/RW (IF) mogą mieć Support pressing
```

---

## 15. Model danych Scala 3

Kompletna definicja typów dla systemu taktycznego. Spójność: `Position` i `Zone` używane w SILNIK (Player.preferredPositions, MatchMoment.zone, PressingStrategy.triggerZones).

```scala
// === POSITION (naturalna pozycja gracza — preferredPositions, versatility) ===
enum Position:
  case GK, CB, LB, RB, DM, CM, AM, LW, RW, ST

// === ZONE (strefa boiska 1-96, siatka 12×8) — spójne z SILNIK ===
type Zone = Int  // 1-96

// === POZYCJE W FORMACJI ===
case class PositionAssignment(
  slot: PositionSlot,
  baseX: Double,
  baseY: Double
)

// === FORMACJA ===
case class Formation(
  name: String,
  positions: List[PositionAssignment],
  positionsInPossession: List[PositionAssignment],
  positionsOutOfPossession: List[PositionAssignment],
  roles: Map[PositionSlot, TacticalRole]
)

// === ROLA ===
case class TacticalRole(
  name: String,
  actionWeights: Map[Phase, ActionWeightModifiers],
  positionShift: PositionShift,
  pressingDuty: PressingDuty,
  runPattern: RunPattern,
  roleMentality: Double,
  requiredAttributes: List[(String, Int)]
)

case class ActionWeightModifiers(
  shortPassWeight: Double,
  longPassWeight: Double,
  crossWeight: Double,
  dribbleWeight: Double,
  shotWeight: Double,
  holdBallWeight: Double,
  throughBallWeight: Double,
  switchPlayWeight: Double
)

case class PositionShift(
  inPossessionDx: Double,
  inPossessionDy: Double,
  outOfPossessionDx: Double,
  outOfPossessionDy: Double
)

// === GAMEPLAN ===
case class GamePlan(
  formation: Formation,
  mentality: Mentality,
  buildUpStyle: BuildUpStyle,
  pressingStrategy: PressingStrategy,
  defensiveLine: DefensiveLine,
  attackingApproach: AttackingApproach,
  playerInstructions: Map[PlayerId, PlayerInstruction],
  setPieces: SetPieceConfig,
  inGameTriggers: List[TacticalTrigger],
  alternatePlans: Map[String, GamePlan]   // Plan B, Plan C
)

case class BuildUpStyle(
  progressionPreference: Double,
  tempo: Double,
  riskTolerance: Double,
  widthInPossession: Double,
  directness: Double
)

case class PressingStrategy(
  pressingLine: Double,
  intensity: Double,
  counterPressDuration: Double,
  triggerZones: Set[Zone],
  pressingTraps: List[PressingTrap]
)

case class PressingTrap(
  targetZone: Zone,
  triggerPlayer: PositionSlot,
  closingPlayers: Set[PositionSlot]
)

case class DefensiveLine(
  height: Double,
  width: Double,
  offsideTrap: Boolean,
  markingStyle: MarkingStyle
)

case class AttackingApproach(
  tempoInFinalThird: Double,
  crossingPreference: Double,
  shootingRange: Double,
  overlapUnderlap: OverlapPreference,
  counterAttackPriority: Double,
  playmakers: Set[PositionSlot]
)

case class SetPieceConfig(
  cornerAttack: CornerAttackRoutine,
  cornerDefense: CornerDefenseRoutine,
  freeKicks: List[FreeKickRoutine],
  penalties: PenaltyConfig,
  throwIns: ThrowInConfig
)

case class PenaltyConfig(
  taker: PositionSlot,
  strategy: PenaltyStrategy  // ChooseDirection, MixedNash, Auto
)
```

---

## 16. Presetowe formacje — szablony startowe

Presety ułatwiające start. Każdy to kompletna formacja z rolami domyślnymi i koordynatami.

### 16.1 Klasyczne formacje

| Formacja | Sloty | Domyślne role | Styl |
|----------|-------|---------------|------|
| **4-4-2 Flat** | 2CB, LB, RB, LM, 2CM, RM, 2ST | Cover+Stopper, FB×2, WM×2, B2B+DLP, AF+TM | Zbalansowany |
| **4-3-3 Hold** | LCB, RCB, LB, RB, CDM, LCM, RCM, LW, RW, ST | BPD+Cover, FB×2, Anchor, B2B+Mez, W+IF, AF | Posiadanie |
| **4-2-3-1** | 2CB, LB, RB, 2CDM, LW, CAM, RW, ST | BPD×2, WB+FB, Anchor+BWinner, W+AP+IF, PF | Pressing |
| **3-5-2** | 3CB, LWB, RWB, CDM, 2CM, 2ST | BPD+Stopper+Cover, CWB×2, DLP, B2B+Mez, AF+DLF | Centralny |
| **3-4-3** | 3CB, LWB, RWB, 2CM, LW, RW, ST | BPD+Cover+Stopper, WB×2, B2B+AP, IF×2, AF | Ofensywny |
| **5-4-1** | 3CB, LWB, RWB, LM, 2CM, RM, ST | Cover×2+Stopper, DWB×2, DWinger+Carrilero+B2B+DWinger, TM | Defensywny |
| **4-1-4-1** | 2CB, LB, RB, CDM, LM, 2CM, RM, ST | BPD×2, FB×2, Regista, W+AP+Mez+IF, AF | Kontrola |
| **4-4-2 Diamond** | 2CB, LB, RB, CDM, LCM, RCM, CAM, 2ST | BPD+Cover, WB+FB, Anchor, B2B×2, Treq, AF+Poacher | Wąski |
| **4-2-4** | 2CB, LB, RB, 2CDM, LW, RW, 2ST | BPD×2, WB×2, Anchor+BW, W×2, AF+PF | Ultra ofensywny |

### 16.2 Jak wygenerować preset

```scala
object FormationPresets {
  val f433: Formation = Formation(
    name = "4-3-3",
    positions = List(
      PositionAssignment(LCB, 25, 32),   // dwa różne sloty: LCB, RCB (nie CB×2)
      PositionAssignment(RCB, 25, 48),
      PositionAssignment(LB,  25, 10),
      PositionAssignment(RB,  25, 70),
      PositionAssignment(CDM, 40, 40),
      PositionAssignment(LCM, 50, 28),   // LCM, RCM dla dwóch różnych ról
      PositionAssignment(RCM, 50, 52),
      PositionAssignment(LW,  80, 10),
      PositionAssignment(RW,  80, 70),
      PositionAssignment(ST,  95, 40)
    ),
    positionsInPossession = /* same with shifts applied */,
    positionsOutOfPossession = /* same with defensive shifts */,
    roles = Map(
      LCB  -> BallPlayingDefender,
      RCB  -> Cover,
      LB   -> Fullback,
      RB   -> Fullback,
      CDM  -> AnchorMan,
      LCM  -> BoxToBox,
      RCM  -> Mezzala,
      LW   -> Winger,
      RW   -> InsideForward,
      ST   -> AdvancedForward
    )
  )
}
```

Gracz wybiera preset, potem dowolnie modyfikuje: przesuwa pozycje, zmienia role, dodaje instrukcje.

---

## Podsumowanie: Mapowanie kompletne

```
GRACZ KONTROLUJE:              SILNIK ROZSTRZYGA:
─────────────────              ──────────────────
Formacja (10×pozycja x,y)  →  Pitch Control (9600 wartości)
Role (35 presetów lub custom) → selectAction weights
Instrukcje indywidualne     →  nadpisania per gracz
Mentalność (7 poziomów)     →  globalny shift parametrów
Build-up (5 suwaków)        →  DxT routing, xPass thresholds
Pressing (4 suwaki + traps) →  PPDA, Dynamic Pressure
Linia obrony (3 suwaki)     →  Defensive Shape, Offside Trap
Atak (5 suwaków)            →  Crossing, Shooting, Transitions
Stałe fragmenty (schematy)  →  GMM zones, NMF routines
Triggery (if/then reguły)   →  automatyczne zmiany w trakcie
Plany B/C (kompletne)       →  przełączanie całej taktyki

ATRYBUTY ZAWODNIKÓW (30×skala 1-20) ROZSTRZYGAJĄ:
  xPass → czy podanie dochodzi
  xG → czy strzał jest bramką
  IWP → kto wygra 1v1
  PPDA → jak skuteczny jest pressing
  Stamina → jak długo to wytrzymuje
```

Każdy element taktyki ma konkretne, mierzalne połączenie z algorytmami silnika. Gracz ma PEŁNĄ DOWOLNOŚĆ w ustalaniu taktyki. Silnik ma PEŁNĄ DETERMINACJĘ w rozstrzyganiu wyników.

---

*Specyfikacja taktyczna v1.0 — Luty 2026*  
*Spójna z: ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md, SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md*
