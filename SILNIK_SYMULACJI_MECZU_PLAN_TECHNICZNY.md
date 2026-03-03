# Silnik Symulacji Meczu — Kompletny Plan Techniczny

## Jak 660 atrybutów, 2 plany taktyczne i 50+ algorytmów zamieniają się w 90 minut piłki nożnej

**Perspektywa**: Architekt systemu Scala / Analityk danych / Ekspert futbolowy  
**Data**: Luty 2026  
**Wersja**: 1.2 (kontrakt wejścia/wyjścia i typy zdarzeń → KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md)

---

## Spis treści

1. [Zasady architektoniczne](#1-zasady-architektoniczne)
2. [Cztery warstwy agregacji](#2-cztery-warstwy-agregacji)
3. [Warstwa 1: Model danych — atrybuty i taktyka](#3-warstwa-1-model-danych)
4. [Warstwa 2: Modele pośrednie — pre-match compute](#4-warstwa-2-modele-pośrednie)
5. [Warstwa 3: Maszyna stanów meczu](#5-warstwa-3-maszyna-stanów-meczu)
6. [Warstwa 4: Rozstrzyganie zdarzeń](#6-warstwa-4-rozstrzyganie-zdarzeń)
7. [System zmęczenia i dynamicznego przeliczania](#7-system-zmęczenia-i-dynamicznego-przeliczania)
8. [Kotwica realizmu — Dixon-Coles jako soft constraint](#8-kotwica-realizmu)
9. [Stałe fragmenty gry](#9-stałe-fragmenty-gry)
10. [Triggery in-game i zmiany taktyczne](#10-triggery-in-game)
11. [Analityka post-match](#11-analityka-post-match)
12. [Zarządzanie sezonem: między meczami](#12-zarządzanie-sezonem)
13. [Wydajność i skalowanie](#13-wydajność-i-skalowanie)
14. [Errata i korekty do poprzednich dokumentów](#14-errata)

---

## 1. Zasady architektoniczne

### 1.1 Trzy nienaruszalne reguły

**Reguła 1 — Separacja warstw**: Surowe atrybuty zawodników (1-20) NIGDY nie są odczytywane bezpośrednio przez silnik meczu. Zawsze są transformowane w modele pośrednie (Pitch Control, DxT, Matchup Matrix itd.) przed użyciem. Dzięki temu każdy model można testować i wymieniać niezależnie.

**Reguła 2 — Probabilistyczna rozdzielczość**: Każde zdarzenie w meczu jest rozstrzygane przez losowanie z rozkładu prawdopodobieństwa obliczonego przez konkretny model. Jedyne źródło losowości to te losowania — nie ma "ukrytych" współczynników losowych.

**Reguła 3 — Hybrydowe makro/mikro**: Warstwa makro (Dixon-Coles, Poisson) ustala oczekiwane "ramy" wyniku. Warstwa mikro (xG, xPass, IWP) generuje zdarzenia emergentnie. Makro działa jako soft constraint — chroni przed nierealistycznymi wynikami (8:7) bez blokowania emergencji.

### 1.2 Spójność z dokumentem atrybutów

Niniejszy dokument jest w pełni spójny z `ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md`:

- **30 atrybutów polowych** (8 fizycznych + 10 technicznych + 12 mentalnych), skala 1-20
- **6 atrybutów bramkarza** (zamiast technicznych)
- **9 traits** (modyfikatory 1-5 lub boolean)
- **3 parametry fizyczne** (wzrost, waga, wiek)

### 1.3 Korekta vs dokument GRA_FOOTBALL_MANAGER_DESIGN.md

Wcześniejszy dokument projektowy używał skali 0-100 i innego zestawu 12 atrybutów. Ten plan techniczny **zastępuje** sekcje 7 (Atrybuty) i 3 (Silnik symulacji) tamtego dokumentu. Reszta (GamePlan, AI agenci, pętla rozgrywki, stos technologiczny) pozostaje aktualna.

### 1.4 Kontrakt wejścia/wyjścia silnika

Silnik jest **czystym modułem obliczeniowym**: przyjmuje jedną strukturę wejściową i zwraca wynik bez dostępu do bazy. Pełna specyfikacja typów wejścia/wyjścia, walidacji i sygnatury ZIO → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §1. Skrót:

- **Wejście**: `MatchEngineInput` — dwa `MatchTeamInput` (po 11 graczy z `PlayerMatchInput`: player + freshness + morale), dwa `GamePlan`, `homeAdvantage`, `RefereeInput`, `LeagueContextInput` (Z-Score), opcjonalny `randomSeed`.
- **Wyjście**: `MatchEngineResult` — `homeGoals`, `awayGoals`, `events: List[MatchEventRecord]`, opcjonalnie `MatchAnalytics`.
- **Sygnatura**: `def simulate(input: MatchEngineInput): ZIO[Any, MatchEngineError, MatchEngineResult]`.

Źródło `homeAdvantage`, `referee`, `leagueContext` i budowa lineupów to warstwa aplikacji (use case „Rozegraj kolejkę”); silnik tylko je konsumuje. Kanoniczna lista typów zdarzeń (`eventType`) i format `MatchEventRecord` → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §2.

---

## 2. Cztery warstwy agregacji

```
WARSTWA 1: DANE
  30 atrybutów × 22 graczy = 660 wartości
  + 2 GamePlany (formacja, pressing, build-up, instrukcje, triggery)
  + 9 traits × 22 = 198 modyfikatorów
  + 3 parametry × 22 = 66 wartości fizycznych
       │
       ▼
WARSTWA 2: MODELE POŚREDNIE (pre-match compute, odświeżane co ~5 min)
  Pitch Control Grid         →  9600 wartości (120×80)
  DxT Matrix                 →  96 wartości (12×8 stref)
  Matchup Matrix             →  ~100 par IWP
  Pressing Parameters        →  6 wartości per zespół
  Passing Network Baseline   →  macierz sąsiedztwa 10×10
  Dixon-Coles λ              →  2 wartości (λ_home, λ_away)
  Stamina Budgets            →  22 wartości
       │
       ▼
WARSTWA 3: MASZYNA STANÓW
  Stan meczu = (kto_ma_piłkę, strefa, faza, minuta, wynik, staminy)
  Tranzycja: Stan → Wybierz_akcję(GamePlan, DxT, Pitch Control) → Akcja
       │
       ▼
WARSTWA 4: ROZSTRZYGANIE
  Akcja → Model probabilistyczny (xPass/xG/IWP) → Bernoulli(p) → Wynik
  Wynik → Nowy stan → Powrót do Warstwy 3
```

**Kluczowy przepływ**: Atrybuty → Modele pośrednie → Wybór akcji → Rozstrzygnięcie → Nowy stan → (pętla)

---

## 3. Warstwa 1: Model danych

### 3.1 Zawodnik (Scala 3 — zaktualizowany)

```scala
case class Player(
  id: PlayerId,
  name: String,
  preferredPositions: Set[Position],
  physical: PhysicalAttributes,
  technical: TechnicalAttributes,  // lub GKAttributes dla bramkarza
  mental: MentalAttributes,
  traits: PlayerTraits,
  bodyParams: BodyParams,
  matchState: MatchState           // dynamiczny — zmienia się w trakcie meczu
)

case class PhysicalAttributes(
  pace: Int,            // 1-20
  acceleration: Int,
  agility: Int,
  stamina: Int,
  strength: Int,
  jumping: Int,
  naturalFitness: Int,
  balance: Int
)

case class TechnicalAttributes(
  finishing: Int,       // 1-20
  longShots: Int,
  shortPassing: Int,
  longPassing: Int,
  crossing: Int,
  dribbling: Int,
  firstTouch: Int,
  heading: Int,
  tackling: Int,
  technique: Int
)

case class MentalAttributes(
  vision: Int,          // 1-20
  composure: Int,
  offTheBall: Int,
  positioning: Int,
  anticipation: Int,
  decisions: Int,
  workRate: Int,
  aggression: Int,
  concentration: Int,
  teamwork: Int,
  bravery: Int,
  flair: Int
)

case class GKAttributes(
  reflexes: Int,        // 1-20
  gkPositioning: Int,
  handling: Int,
  distribution: Int,
  commandOfArea: Int,
  oneOnOnes: Int
)

case class PlayerTraits(
  freeKickSpecialist: Int,  // 0-5
  penaltyTaker: Int,        // 0-5
  longThrow: Boolean,
  leadership: Int,          // 0-5
  injuryProne: Int,         // 0-5
  clutchPerformer: Boolean,
  versatility: Set[Position],
  preferredFoot: Foot,      // Left, Right, Both
  weakFootQuality: Int      // 1-5
)

case class BodyParams(
  heightCm: Int,
  weightKg: Int,
  age: Int
)

// Stan dynamiczny — zmienia się w trakcie meczu
case class MatchState(
  currentStamina: Double,     // 0.0-1.0 (procent pozostałej staminy)
  metabolicLoad: Double,      // kumulatywne obciążenie
  injuryRisk: Double,         // 0.0-1.0
  yellowCards: Int,           // 0, 1, lub 2 (czerwona)
  isOnPitch: Boolean,
  minuteEntered: Int          // kiedy wszedł (0 = od początku)
)
```

### 3.2 Efektywne atrybuty (po uwzględnieniu zmęczenia i presji)

```scala
def effectiveAttribute(base: Int, player: Player, pressureLevel: Double): Double = {
  val staminaFactor = staminaDecay(player.matchState.currentStamina)
  val composureFactor = 1.0 - pressureLevel * (1.0 - player.mental.composure / 20.0)
  val concentrationFactor = concentrationLapse(player.mental.concentration, minute)
  
  base.toDouble * staminaFactor * composureFactor * concentrationFactor
}

def staminaDecay(currentStamina: Double): Double = currentStamina match {
  case s if s > 0.6 => 1.0                      // pełna wydajność
  case s if s > 0.4 => 1.0 - (0.6 - s) * 0.5   // lekki spadek: do -10%
  case s if s > 0.2 => 0.9 - (0.4 - s) * 1.25   // mocny spadek: do -25%
  case s            => 0.65 - (0.2 - s) * 1.5    // krytyczny: do -35%
}

def concentrationLapse(concentration: Int, minute: Int): Double = {
  val baseLapseProb = (20 - concentration) * 0.003
  val lateGameMultiplier = if (minute > 75) 1.5 else 1.0
  if (Random.nextDouble() < baseLapseProb * lateGameMultiplier) 0.7 else 1.0
}
```

### 3.3 GamePlan

> **Pełna specyfikacja formacji, ról i taktyki**: `FORMACJE_ROLE_TAKTYKA.md`

GamePlan zawiera Formation, BuildUpStyle, PressingStrategy, DefensiveLine, AttackingApproach, PlayerInstructions, SetPieces, InGameTriggers, Mentality. Kluczowa zmiana vs wcześniejszy dokument projektowy: Formation zawiera teraz pełne koordynaty (x,y) dla każdego gracza, role behawioralne per slot, oraz warianty pozycji z piłką/bez piłki. Pełna definicja typów `PositionSlot`, `TacticalRole`, `PositionAssignment` oraz ~35 predefiniowanych ról → patrz `FORMACJE_ROLE_TAKTYKA.md`.

---

## 4. Warstwa 2: Modele pośrednie

### 4.1 Pitch Control Grid

**Kiedy obliczany**: Pre-match, potem co 50 zdarzeń symulacji (lub po zmianie zawodnika / taktyki).

**Dane wejściowe**: Pozycje 22 graczy (z formacji), Pace, Acceleration, Agility każdego.

**Algorytm**:
```
Dla każdego punktu (x,y) w siatce 120×80:
  Dla każdego gracza g:
    time_to_reach(g, x, y) = fizyczny_model_ruchu(
      g.position, g.velocity_vector,
      effective(g.physical.pace), 
      effective(g.physical.acceleration),
      effective(g.physical.agility)  // korekta kierunku
    )
    P_intercept(g) = logistic(time_to_reach, σ=0.45)
  
  PC_teamA(x,y) = Σ P_intercept(g) for g in teamA
  PC_teamB(x,y) = Σ P_intercept(g) for g in teamB
  PitchControl(x,y) = PC_teamA / (PC_teamA + PC_teamB)
```

**Wynik**: Macierz 120×80 z wartościami 0.0-1.0. Wartość 0.7 = 70% kontroli przez zespół A.

**Koszt obliczeniowy**: 9600 punktów × 22 graczy × kilka operacji = ~200K operacji. Na JVM: <10ms.

### 4.2 DxT Matrix (Dynamic Expected Threat)

**Kiedy obliczany**: Razem z Pitch Control (zależy od niego).

**Algorytm**:
```
1. Załaduj statyczny xT (pre-computed z historycznych danych, 96 stref)
2. Dla każdej strefy z (12×8):
   avg_PC(z) = średni Pitch Control w strefie z (dla zespołu atakującego)
   DxT(z) = staticXT(z) × avg_PC(z)
   // Strefy kontrolowane przez rywali mają obniżone zagrożenie
```

**Wynik**: 96 wartości — ile warta jest piłka w każdej strefie boiska w AKTUALNEJ konfiguracji.

### 4.3 Matchup Matrix

**Kiedy obliczany**: Pre-match (atrybuty się nie zmieniają, ale efektywne wartości tak — odświeżaj z fatigą).

**Algorytm**:
```
Dla każdej pary (attacker_i, defender_j) w potencjalnych starciach:
  IWP_dribble = σ(
    Z(att.dribbling) + 0.3*Z(att.agility) + 0.2*Z(att.pace) + flair_variance(att)
    - Z(def.tackling) - 0.3*Z(def.agility) - 0.2*Z(def.anticipation)
    + composure_modifier(att) - composure_modifier(def)
  )
  
  IWP_aerial = σ(
    Z(att.jumping) + Z(att.heading) + 0.4*Z(att.strength)
    - Z(def.jumping) - 0.5*Z(def.heading) - 0.4*Z(def.strength)
    + bravery_bonus(att) - bravery_bonus(def)
  )
  
  IWP_physical = σ(
    Z(att.strength) + 0.5*Z(att.balance)
    - Z(def.strength) - 0.5*Z(def.balance)
  )
  
  IWP_sprint = σ(
    Z(att.pace) + 0.5*Z(att.acceleration)
    - Z(def.pace) - 0.5*Z(def.acceleration)
  )
  
  MatchupMatrix[i][j] = {dribble: IWP_dribble, aerial: IWP_aerial, 
                          physical: IWP_physical, sprint: IWP_sprint}
```

**Z(attr)** = (attr - league_mean) / league_stddev — standaryzacja wg pozycji (np. dla "CB" osobna średnia/odchylenie niż dla "ST").

**Źródło league_mean i league_stddev**: Kontekst rozgrywek (Competition / League). Dla każdej pozycji (lub grupy slotów: CB, FB, DM, CM, AM, W, ST) należy przechowywać średnią i odchylenie standardowe atrybutów w **puli referencyjnej** — np. wszystkich zawodników danej ligi na tej pozycji, albo wartości domyślne (np. mean=10, stddev=3 dla skali 1–20). W implementacji: `LeagueContext(positionId).mean(attr)`, `LeagueContext(positionId).stddev(attr)`. Bez tego IWP używa wartości domyślnych (np. mean=10, stddev=3).

### 4.4 Pressing Parameters

**Kiedy obliczany**: Pre-match + co 50 zdarzeń (zmęczenie wpływa na pressing).

```
Dane wejściowe: PressingStrategy z GamePlan + atrybuty pressujących

PPDA_target = f(pressingLine, intensity)
  // pressingLine=90, intensity=90 → PPDA_target ≈ 7
  // pressingLine=40, intensity=40 → PPDA_target ≈ 15

PPDA_actual = PPDA_target × fatigue_modifier × attribute_modifier
  fatigue_modifier = avg(currentStamina of midfielders and forwards)
  attribute_modifier = avg(workRate, pace, acceleration, tackling, aggression 
                           of pressing players) / 12.0

Dynamic_Pressure_per_zone = for each zone z:
  pressers = players participating in pressing in zone z
  p_total = 1 - Π(1 - p_i) for each presser
  where p_i = f(
    distance_to_ball_carrier,
    effective(presser.acceleration),
    effective(presser.tackling),
    effective(presser.aggression) * 0.02 bonus
  )

Counter_Press_Intensity = f(
  avg(workRate of nearby_players),
  avg(acceleration of nearby_players),
  tactical_counterPressDuration
)
```

### 4.5 Passing Network Baseline

```
Dla każdej pary (player_i, player_j) w formacji:
  distance_ij = odległość między pozycjami w formacji
  pass_quality_ij = min(
    effective(i.shortPassing) if distance < 25m else effective(i.longPassing),
    effective(j.firstTouch)
  ) / 20.0
  
  tactical_weight_ij = f(
    formation_adjacency(i, j),       // czy sąsiadują w formacji
    buildUpStyle_preference,          // centralne vs boczne
    teamwork(i), teamwork(j)          // czy grają "w systemie"
  )
  
  PassingNetwork[i][j] = pass_quality_ij × tactical_weight_ij

Graph_Density = count(edges > threshold) / max_edges
Clustering_Coefficient = per triangle analysis
Betweenness_Centrality = shortest path analysis per node
```

### 4.6 Dixon-Coles λ

```
α_A (siła ataku A) = f(
  avg finishing of attackers,
  avg vision of midfielders,
  avg offTheBall of forwards,
  formation_offensive_potential,
  buildUpStyle_xT_expected
)

β_B (siła obrony B) = f(
  avg tackling of defenders,
  avg positioning of defenders,
  avg anticipation of defenders,
  pressing_effectiveness,
  defensive_line_height
)

λ_A = α_A × β_B_weakness × home_advantage
λ_B = α_B × β_A_weakness

// Typowe wartości: λ ∈ [0.5, 3.0]
// λ=1.5 → oczekiwane 1.5 bramki
```

**home_advantage**: Mnożnik przewagi gospodarza (typowo 1.0–1.15). Źródło: stała globalna (np. 1.05) lub parametr kontekstu meczu (Competition/League). Np. 1.05 = gospodarz ma ~5% wyższą oczekiwaną liczbę bramek. Spójne z literaturą Dixon-Coles.

---

## 5. Warstwa 3: Maszyna stanów meczu

### 5.1 Definicja stanu

```scala
case class MatchMoment(
  possessionTeam: TeamId,         // kto ma piłkę
  zone: Int,                      // 1-96 (siatka 12×8); typ Zone = Int — FORMACJE_ROLE_TAKTYKA.md §10.2
  phase: Phase,                   // BuildUp, Progression, FinalThird, Transition, SetPiece
  ballCarrier: PlayerId,          // kto ma piłkę przy nodze
  minute: Int,                    // 0-90+
  score: (Int, Int),              // aktualny wynik
  momentum: Double,               // -1.0 do 1.0 (Field Tilt derived)
  playerStates: Map[PlayerId, MatchState],  // staminy, kartki
  activeTactics: (GamePlan, GamePlan),       // po triggerach
  eventsThisPossession: List[MatchEvent]     // historia posiadania
)

sealed trait Phase
case object BuildUp extends Phase       // tercja defensywna
case object Progression extends Phase   // środek boiska
case object FinalThird extends Phase    // tercja ataku
case object Transition extends Phase    // kontratak / utrata
case object SetPiece extends Phase      // stały fragment
case object DeadBall extends Phase      // przerwa w grze
```

### 5.2 Pętla symulacji — główny algorytm

```scala
def simulateMatch(home: Team, away: Team, 
                  homePlan: GamePlan, awayPlan: GamePlan): IO[MatchResult] = {
  for {
    // === PRE-MATCH COMPUTE ===
    intermediates <- computeIntermediateModels(home, away, homePlan, awayPlan)
    
    // === MATCH LOOP ===
    initialState = MatchMoment(
      possessionTeam = home.id,  // kickoff
      zone = 48,                  // centrum
      phase = BuildUp,
      ballCarrier = findKickoffPlayer(home),
      minute = 0,
      score = (0, 0),
      momentum = 0.0,
      playerStates = initializeStaminas(home, away),
      activeTactics = (homePlan, awayPlan),
      eventsThisPossession = Nil
    )
    
    finalState <- simulationLoop(initialState, intermediates, Nil)
    
    // === POST-MATCH ANALYTICS ===
    analytics <- computePostMatch(finalState.allEvents, intermediates)
    
  } yield MatchResult(finalState.score, finalState.allEvents, analytics)
}

def simulationLoop(
  state: MatchMoment, 
  intermediates: IntermediateModels,
  allEvents: List[MatchEvent]
): IO[SimulationResult] = {
  
  if (state.minute >= 90 + addedTime(state)) {
    IO.pure(SimulationResult(state, allEvents))
  } else {
    for {
      // 1. Sprawdź triggery obu drużyn
      updatedTactics <- checkTriggers(state)
      
      // 2. Co N zdarzeń: odśwież modele pośrednie (zmęczenie!)
      updatedIntermediates <- 
        if (allEvents.length % 50 == 0)
          recomputeIntermediates(state, updatedTactics)
        else IO.pure(intermediates)
      
      // 3. Wybierz następną akcję
      action <- selectAction(state, updatedIntermediates, updatedTactics)
      
      // 4. Rozstrzygnij akcję
      outcome <- resolveAction(action, state, updatedIntermediates)
      
      // 5. Zaktualizuj stan
      newState <- updateState(state, action, outcome, updatedIntermediates)
      
      // 6. Zaktualizuj zmęczenie
      stateWithFatigue <- updateFatigue(newState, action)
      
      // 7. Zapisz zdarzenie
      event = MatchEvent(state.minute, action, outcome, state.zone)
      
      // 8. Rekurencja
      _ <- simulationLoop(stateWithFatigue, updatedIntermediates, event :: allEvents)
    } yield ()
  }
}
```

### 5.3 Algorytm wyboru akcji (selectAction)

To jest "mózg" silnika — decyduje CO SIĘ DZIEJE.

```scala
def selectAction(
  state: MatchMoment, 
  models: IntermediateModels,
  tactics: (GamePlan, GamePlan)
): IO[MatchAction] = IO {
  
  val attPlan = if (state.possessionTeam == homeId) tactics._1 else tactics._2
  val carrier = getPlayer(state.ballCarrier)
  val zone = state.zone
  val phase = state.phase
  
  // --- Zbierz opcje ---
  val options: List[WeightedOption] = List(
    evaluateShortPass(state, carrier, models, attPlan),
    evaluateLongPass(state, carrier, models, attPlan),
    evaluateCross(state, carrier, models, attPlan),
    evaluateDribble(state, carrier, models),
    evaluateShot(state, carrier, models, attPlan),
    evaluateBackPass(state, carrier, models)
  ).flatten  // Some opcje mogą być None (np. strzał z własnej połowy)
  
  // --- Vision filtr: ile opcji gracz "widzi" ---
  val visibleOptions = {
    val optionsCount = (carrier.mental.vision / 4.0).ceil.toInt  // vision 20 → 5 opcji
    options.sortBy(-_.score).take(optionsCount)
  }
  
  // --- Decisions filtr: czy wybiera najlepszą ---
  val chosenOption = {
    val decisionQuality = carrier.mental.decisions / 20.0
    if (Random.nextDouble() < decisionQuality)
      visibleOptions.maxBy(_.score)  // optymalny wybór
    else
      visibleOptions(Random.nextInt(visibleOptions.size))  // losowy z widocznych
  }
  
  // --- Teamwork modyfikator: czy wykonuje plan trenera ---
  val finalOption = {
    val compliance = carrier.mental.teamwork / 20.0
    if (Random.nextDouble() < compliance)
      applyTacticalPreference(chosenOption, attPlan)  // modyfikuj wg instrukcji
    else
      chosenOption  // gra po swojemu
  }
  
  finalOption.action
}

def evaluateShortPass(state: MatchMoment, carrier: Player,
                       models: IntermediateModels, plan: GamePlan): Option[WeightedOption] = {
  // Znajdź sąsiednie strefy z wyższym DxT
  val targetZones = adjacentZones(state.zone)
    .filter(z => models.dxt(z) > models.dxt(state.zone) * 0.8)  // nie cofaj się za bardzo
  
  targetZones.flatMap { targetZone =>
    val receiver = findBestReceiver(targetZone, state, models)
    val xPass = computeXPass(
      carrier, receiver, state.zone, targetZone,
      models.pitchControl, models.pressingParams,
      isShort = true
    )
    val dxtGain = models.dxt(targetZone) - models.dxt(state.zone)
    val riskAdjusted = dxtGain * xPass * plan.buildUp.riskTolerance
    
    Some(WeightedOption(ShortPass(carrier.id, receiver.id, targetZone), riskAdjusted))
  }.maxByOption(_.score)
}

def evaluateShot(state: MatchMoment, carrier: Player,
                  models: IntermediateModels, plan: GamePlan): Option[WeightedOption] = {
  val distanceToGoal = euclideanDistance(state.zone, goalZone)
  
  if (distanceToGoal > 35) return None  // za daleko
  
  val isCloseRange = distanceToGoal < 18
  val shootingAttr = if (isCloseRange) carrier.technical.finishing 
                     else carrier.technical.longShots
  
  // Czy taktyka pozwala na strzał z tej odległości?
  val shootingRangeOk = if (isCloseRange) true 
                        else Random.nextDouble() < plan.attacking.shootingRange
  
  if (!shootingRangeOk) return None
  
  val xg = computeXG(state.zone, carrier, models)
  
  // Strzał jest opcją gdy xG jest wystarczająco wysokie
  // lub gdy gracz ma wysokie longShots i jest w dobrej pozycji
  val shotValue = xg * 10  // bramka jest warta 10× więcej niż progresja
  
  Some(WeightedOption(Shot(carrier.id, state.zone, isCloseRange), shotValue))
}
```

### 5.4 Zarządzanie posiadaniem i utratami

```scala
// Po każdym zdarzeniu — czy posiadanie się zmienia?
def updatePossession(state: MatchMoment, action: MatchAction, 
                      outcome: ActionOutcome): PossessionChange = {
  outcome match {
    case PassSuccess(receiver, zone) => 
      SamePossession(newCarrier = receiver, newZone = zone)
      
    case PassIntercepted(interceptor, zone) =>
      // Utrata! Sprawdź counter-pressing
      val counterPressProb = computeCounterPress(state)
      if (Random.nextDouble() < counterPressProb)
        CounterPressRecovery(newCarrier = nearestTeammate(zone))
      else
        TurnoverTo(opponent, zone, phase = Transition)
        
    case DribbleSuccess(zone) =>
      SamePossession(newZone = zone)
      
    case DribbleFailed(tackler, zone) =>
      TurnoverTo(opponent, zone, phase = Transition)
      
    case ShotGoal =>
      Goal(scorer = state.ballCarrier) // → restart z centrum
      
    case ShotSaved(gk) =>
      // Handling check — łapie czy wybija?
      val catchProb = gk.gkAttributes.handling / 20.0
      if (Random.nextDouble() < catchProb)
        PossessionToGK(gk)  // GK trzyma piłkę
      else
        SecondBall(zone)     // luźna piłka — Pitch Control decyduje kto ją zbierze
        
    case ShotMissed =>
      GoalKickTo(opponent)
      
    case ShotBlocked(blocker) =>
      SecondBall(zone)       // odbita piłka → Pitch Control
      
    case FoulCommitted(fouler, zone) =>
      SetPiece(opponent, zone, foulType)
  }
}
```

### 5.5 Przebieg jednego posiadania — pełny przykład

```
Posiadanie #127: Team A, strefa 22 (własna połowa, centrum), minuta 34

Zdarzenie 1: BuildUp
├── Opcje: shortPass(do strefy 33), shortPass(do 23), longPass(do 56)
├── DxT scores: 33→0.015, 23→0.008, 56→0.035
├── xPass: 33→0.85, 23→0.88, 56→0.52
├── Risk-adjusted (risk=0.6): 33→0.0077, 23→0.0042, 56→0.0109
├── Vision (16): widzi 4 opcje → wszystkie widoczne
├── Decisions (14): 70% szans na wybranie najlepszej → wybiera 56 (long pass)
├── Teamwork (12): 60% compliance → sprawdza BuildUpStyle
│   BuildUpStyle=ShortPassing → penalizuje long pass o 40%
│   Final score 56: 0.0109 × 0.6 = 0.0065 → teraz shortPass(33) wygrywa!
├── WYNIK: ShortPass do strefy 33
├── xPass=0.85 → Bernoulli(0.85) → SUKCES
└── Stan: Team A, strefa 33, phase=Progression, ballCarrier=CM

Zdarzenie 2: Progression
├── Opcje: shortPass(44), shortPass(34), dribble(44), longPass(67)
├── DxT: 44→0.025, 34→0.020, 67→0.050
├── Matchup check: CM vs opponent CM → IWP_dribble = 0.48 (niekorzystne)
├── Decisions (14): wybiera shortPass(44) — bezpieczniejsze niż drybling
├── xPass=0.79 → Bernoulli(0.79) → SUKCES
└── Stan: Team A, strefa 44, phase=Progression, ballCarrier=RW

Zdarzenie 3: Progression → FinalThird
├── RW ma piłkę, strefa 44 (prawe skrzydło, środek boiska)
├── Opcje: dribble(55), cross(67), shortPass(45)
├── Matchup: RW(dri=16,agi=17) vs LB(tac=14,agi=12) → IWP_dribble=0.64
├── Drybling atrakcyjny! RW ma instrukcję "cut inside" → bonus
├── WYNIK: Dribble do strefy 55
├── IWP=0.64 → Bernoulli(0.64) → SUKCES (RW mija LB!)
└── Stan: Team A, strefa 55, phase=FinalThird, ballCarrier=RW

Zdarzenie 4: FinalThird — opcja strzału
├── RW w strefie 55 (bok pola karnego, po cut inside)
├── Opcje: shot(close), cross(78), shortPass(56)
├── xG strzału: distance=16m, angle=35°, 1 obrońca w stożku
│   base_xG=0.08 × finishing_mod(15)=1.15 × composure_mod=0.95
│   angular_pressure: 1 obrońca (bravery>10) → -0.01
│   → xG = 0.08 × 1.15 × 0.95 - 0.01 = 0.077
├── Cross do pola karnego: CF ma offTheBall=17 → dobra pozycja
│   xPass_cross=0.55, gdyby doszło → aerial IWP CF vs CB...
├── WYNIK: Cross (wyższy expected value niż strzał z 0.077 xG)
├── xPass_cross = f(crossing=14, technique=13) = 0.55 → Bernoulli(0.55) → SUKCES
└── Stan: piłka w powietrzu → AERIAL DUEL

Zdarzenie 5: Aerial Duel (stały mikro-moduł)
├── CF (jumping=14, heading=16, strength=13) vs CB (jumping=16, heading=12, strength=17)
├── IWP_aerial = σ(Z_CF_jmp + Z_CF_head + 0.4*Z_CF_str 
│                   - Z_CB_jmp - 0.5*Z_CB_head - 0.4*Z_CB_str)
├── IWP = 0.43
├── Bernoulli(0.43) → PRZEGRANA (CB wygrywa)
├── CB heading=12 → clearance quality = mediocre
│   → piłka odpada na ~20m od bramki
└── Stan: SecondBall → Pitch Control decyduje kto zbierze

Zdarzenie 6: Second Ball
├── Pitch Control w strefie gdzie piłka spadła:
│   Team A: 0.55 (CM i RW w pobliżu)
│   Team B: 0.45
├── Bernoulli(0.55) → Team A odzyskuje!
└── Stan: Team A, nowe posiadanie, strefa 46

... (posiadanie kontynuuje lub kończy się strzałem/utratą)
```

---

## 6. Warstwa 4: Rozstrzyganie zdarzeń

### 6.1 Tabela: Zdarzenie → Model → Atrybuty

| Zdarzenie | Model rozstrzygający | Atrybuty wejściowe (podającego/atakującego) | Atrybuty wejściowe (odbierającego/broniącego) | Losowanie |
|-----------|---------------------|---------------------------------------------|-----------------------------------------------|-----------|
| Short Pass | xPass_short | shortPassing, technique, composure | firstTouch, positioning (receiver) | Bernoulli(xPass) |
| Long Pass | xPass_long | longPassing, technique, vision | firstTouch, offTheBall (receiver) | Bernoulli(xPass) |
| Cross | xPass_cross | crossing, technique | offTheBall, jumping (receiver) | Bernoulli(xPass) |
| Dribble 1v1 | IWP_dribble | dribbling, agility, pace, balance, flair | tackling, agility, anticipation | Bernoulli(IWP) |
| Aerial Duel | IWP_aerial | jumping, heading, strength, bravery | jumping, heading, strength, bravery | Bernoulli(IWP) |
| Physical Duel | IWP_physical | strength, balance | strength, balance | Bernoulli(IWP) |
| Sprint Race | IWP_sprint | pace, acceleration | pace, acceleration | Bernoulli(IWP) |
| Shot (close) | xG + PSxG | finishing, technique, composure | GK: reflexes, gkPositioning | Bernoulli(xG) |
| Shot (far) | xG + PSxG | longShots, technique, composure | GK: reflexes, gkPositioning | Bernoulli(xG) |
| Header Shot | xG_header | heading, jumping, composure | GK: reflexes, commandOfArea | Bernoulli(xG) |
| Interception | intercept_prob | — | anticipation, positioning, pace | Bernoulli(p) |
| Shot Block | block_prob | — | bravery, positioning, anticipation | Bernoulli(p) |
| Foul | foul_risk | — | aggression / tackling ratio | Bernoulli(p) |
| Card | card_risk | — | aggression, foul_zone | Bernoulli(p) |
| GK Save | save_prob | finishing, technique (strzał) | reflexes, gkPositioning, handling | Bernoulli(p) |
| GK 1v1 | IWP_1v1_gk | pace, finishing, composure | oneOnOnes, pace, bravery | Bernoulli(IWP) |
| GK Claim Cross | claim_prob | — | commandOfArea, jumping, bravery | Bernoulli(p) |
| Penalty | penalty_model | composure, finishing, penaltyTaker trait | GK: reflexes, gkPositioning | Nash + Bernoulli |

### 6.2 Formuły kluczowych modeli

**xPass (short)**:
```
base_xPass = 0.92 - 0.003 × distance_meters  // bazowo ~85-92% dla 5-25m
pass_skill = 1 + (effective(shortPassing) - 10) × 0.025
pressure_mod = 1 - dynamicPressure × (1 - effective(composure)/20)
pitch_control = pitchControlGrid(target_zone)  // 0-1
curve_bonus = if (requires_curve) effective(technique) × 0.005 else 0

xPass = base_xPass × pass_skill × pressure_mod × pitch_control + curve_bonus
xPass = clamp(xPass, 0.05, 0.98)
```

**xG (close range)**:
```
base_xG = lookup_table(distance, angle)  // z historycznych danych, np. 0.08-0.50
finishing_mod = 1 + (effective(finishing) - 10) × 0.03
composure_mod = 1 - matchPressure × (1 - effective(composure)/20)
angular_pressure = count(brave_defenders_in_cone) × 0.015
gk_distance_penalty = max(0, (optimal_gk_pos - actual_gk_pos) × 0.02)
body_part = if (header) × 0.7 else if (weak_foot) × weakFootQuality/5 else 1.0
technique_bonus = if (volley || half_volley) effective(technique) × 0.005 else 0

xG = base_xG × finishing_mod × composure_mod × body_part + technique_bonus
     - angular_pressure + gk_distance_penalty
xG = clamp(xG, 0.01, 0.95)
```

**IWP (dribble)**:
```
Z_att = (effective(dribbling) - μ_dri) / σ_dri
Z_agi_att = (effective(agility) - μ_agi) / σ_agi
Z_pac_att = (effective(pace) - μ_pac) / σ_pac
Z_def = (effective(tackling) - μ_tac) / σ_tac  // obrońca
Z_agi_def = (effective(agility) - μ_agi) / σ_agi
Z_ant_def = (effective(anticipation) - μ_ant) / σ_ant

logit = Z_att + 0.3×Z_agi_att + 0.2×Z_pac_att
      - Z_def - 0.3×Z_agi_def - 0.2×Z_ant_def
      + balance_bonus  // if attacker balance > defender strength: +0.1
      + flair_variance  // if flair > 14: random(-0.15, +0.15)
      
IWP = 1 / (1 + exp(-logit))
IWP = clamp(IWP, 0.10, 0.90)  // nigdy nie jest pewne
```

---

## 7. System zmęczenia i dynamicznego przeliczania

### 7.1 Metabolic Power per zdarzenie

```scala
def metabolicCost(action: MatchAction, player: Player): Double = action match {
  case Sprint(dist) => 
    dist * 0.004 * (1 + player.physical.pace / 40.0)
    // Szybcy gracze zużywają WIĘCEJ energii przy sprincie
    
  case HighIntensityRun(dist) => 
    dist * 0.002
    
  case Pressing(duration) => 
    duration * 0.003 * player.mental.workRate / 10.0
    // Wysoki work rate = więcej biegania = więcej kosztu
    
  case Dribble(dist) => 
    dist * 0.003 * (1 + player.physical.agility / 40.0)
    
  case Walking(dist) => 
    dist * 0.0003  // minimalny koszt
    
  case AerialDuel => 
    0.005 * (player.physical.jumping / 20.0)
    
  case PhysicalDuel => 
    0.004 * (player.physical.strength / 20.0)
}
```

### 7.2 Update staminy i przeliczanie modeli

```scala
def updateFatigue(state: MatchMoment, action: MatchAction): MatchMoment = {
  val updatedPlayers = state.playerStates.map { case (pid, ms) =>
    val player = getPlayer(pid)
    val cost = metabolicCost(actionForPlayer(pid, action), player)
    
    val lateGameMultiplier = if (state.minute > 70) 1.3 else 1.0
    val newStamina = ms.currentStamina - cost * lateGameMultiplier
    val newLoad = ms.metabolicLoad + cost
    val newInjuryRisk = computeInjuryRisk(player, newStamina, newLoad)
    
    pid -> ms.copy(
      currentStamina = max(0.0, newStamina),
      metabolicLoad = newLoad,
      injuryRisk = newInjuryRisk
    )
  }
  state.copy(playerStates = updatedPlayers)
}

// Co 50 zdarzeń: PEŁNE przeliczenie modeli pośrednich
// Bo zmęczenie zmienia effective pace/acceleration → zmienia Pitch Control!
def recomputeIntermediates(state: MatchMoment, 
                           tactics: (GamePlan, GamePlan)): IO[IntermediateModels] = {
  // Recalc z efektywnymi atrybutami (po fatygu)
  for {
    pc  <- PitchControl.compute(state)   // nowy Pitch Control ze zmęczonymi graczami
    dxt <- DxT.compute(pc)               // nowy DxT
    mm  <- MatchupMatrix.compute(state)  // nowe IWP (zmęczeni gracze mają niższe)
    pp  <- PressingParams.compute(state, tactics) // nowy PPDA (pressing słabnie)
  } yield IntermediateModels(pc, dxt, mm, pp, ...)
}
```

### 7.3 Emergentna dynamika zmęczenia

Efekt zmęczenia propaguje się automatycznie:

```
Minuta 1-45:
  Stamina: 100% → ~65% (pressing team) / ~80% (low block team)
  Effective Pace: 100% → 100% (stamina > 60%)
  PPDA: 7 → 8 (minimalna zmiana)
  Pitch Control: stabilny
  → Pressing team dominuje

Minuta 45-65:
  Stamina: 65% → 45% (pressing) / 80% → 70% (low block)
  Effective Pace: 100% → 92.5% (stamina 45%, spadek -7.5%)
  PPDA: 8 → 11 (wyraźne pogorszenie!)
  Pitch Control: zaczyna się przesuwać
  → Pressing słabnie, rywal zyskuje przestrzeń

Minuta 65+:
  Stamina: 45% → 30% (pressing) / 70% → 60% (low block)
  Effective Pace: 92.5% → 82.5% (stamina 30%, spadek -17.5%)
  Effective Vision: spada o 10% (stamina < 40%)
  PPDA: 11 → 14 (prawie pasywna obrona)
  Pitch Control: wyraźna zmiana
  → Kontrataki rywala stają się groźne
  → TRIGGER: gracz zmienia taktykę lub robi zmianę
```

---

## 8. Kotwica realizmu

### 8.1 Dixon-Coles jako soft constraint

```scala
def realismCheck(state: MatchMoment, lambdaHome: Double, lambdaAway: Double): Double = {
  val minuteFraction = state.minute / 90.0
  val expectedGoalsNow_H = lambdaHome * minuteFraction
  val expectedGoalsNow_A = lambdaAway * minuteFraction
  val actualGoals_H = state.score._1
  val actualGoals_A = state.score._2
  
  // Jak daleko jesteśmy od oczekiwań?
  val deviationH = (actualGoals_H - expectedGoalsNow_H).abs
  val deviationA = (actualGoals_A - expectedGoalsNow_A).abs
  
  // Soft modifier: jeśli drużyna strzeliła dużo więcej niż oczekiwano,
  // subtelnie obniż jej xG (modeluje: zespół wygrywający 4:0 zwalnia tempo)
  val modifier = 1.0 - 0.03 * max(0, deviationH + deviationA - 1.5)
  
  clamp(modifier, 0.85, 1.0)  // max 15% korekta
}
```

**Kiedy działa**: Jeśli przy λ=1.5 zespół ma 4 gole po 60 minutach (deviation = 3.0), modifier = 0.85. xG tego zespołu jest obniżone o 15%. To modeluje realne zjawisko: drużyny wygrywające wysoko grają bardziej zachowawczo.

**Kiedy NIE działa**: Jeśli wynik jest bliski oczekiwaniom (deviation < 1.5), modifier = 1.0. Mecz przebiega naturalnie.

---

## 9. Stałe fragmenty gry

### 9.1 Kiedy stały fragment?

```
Faul → Free Kick (pozycja faulu)
  ├── Bezpośredni (w pobliżu bramki): Free Kick Shot
  └── Pośredni (daleko): Free Kick Cross/Pass
  
Piłka za linię końcową po obrońcy → Corner Kick
Piłka za linię końcową po atakującym → Goal Kick
Piłka za linię boczną → Throw-In (lub Long Throw jeśli trait)
```

### 9.2 Rożny — pełny algorytm

```
1. Delivery: xPass_cross = f(crossing, technique) dośrodkowującego
   Target zone: wybrana z GMM (15 stref) wg schematu gracza
   
2. Jeśli delivery sukces:
   a. GK claim check: commandOfArea vs aerial congestion → Bernoulli
   b. Jeśli GK nie wychodzi → Aerial Duel: atak vs obrona
      IWP_aerial z uwzględnieniem:
      - Man-marking vs Zonal (z GamePlan)
      - offTheBall atakujących (jakość biegów w strefy GMM)
      - positioning obrońców (trzymanie stref)
      - bravery (wchodzenie w zatłoczony box)
   c. Jeśli atakujący wygra:
      - Strzał/główka: xG_header × heading_modifier
      
3. Jeśli delivery fail:
   Koniec akcji → nowe posiadanie dla obrony lub nowy rożny
```

### 9.3 Rzut karny — Nash Equilibrium

```
1. Strzelec wybiera kierunek (L/C/R) i typ (power/placement)
   - AI gracz: Mixed Strategy Nash Equilibrium
   - Ludzki gracz: decyzja manualna lub "auto"
   
2. Bramkarz wybiera kierunek (L/C/R)
   
3. Rozstrzygnięcie:
   base_accuracy = if (matching_direction) 0.3 else 0.8
   composure_mod = composure / 20
   penaltyTaker_mod = penaltyTaker_trait / 5
   
   goal_prob = base_accuracy × composure_mod × penaltyTaker_mod
   if (Random.nextDouble() < goal_prob) → GOL else → MISS/SAVE
```

---

## 10. Triggery in-game

```scala
def checkTriggers(state: MatchMoment): IO[(GamePlan, GamePlan)] = IO {
  val (homePlan, awayPlan) = state.activeTactics
  
  val newHomePlan = homePlan.inGameTriggers.foldLeft(homePlan) { (plan, trigger) =>
    if (evaluateCondition(trigger.condition, state, home)) 
      applyChange(plan, trigger.action)
    else plan
  }
  
  val newAwayPlan = awayPlan.inGameTriggers.foldLeft(awayPlan) { (plan, trigger) =>
    if (evaluateCondition(trigger.condition, state, away))
      applyChange(plan, trigger.action)
    else plan
  }
  
  (newHomePlan, newAwayPlan)
}

def evaluateCondition(cond: TriggerCondition, state: MatchMoment, 
                       team: TeamId): Boolean = cond match {
  case ScoreCondition(minDiff, maxDiff) =>
    val diff = scoreDiff(state, team)
    diff >= minDiff && diff <= maxDiff
    
  case TimeCondition(afterMinute) =>
    state.minute >= afterMinute
    
  case MomentumCondition(fieldTiltBelow) =>
    computeFieldTilt(state, team) < fieldTiltBelow
    
  case StaminaCondition(avgBelow) =>
    avgStamina(state, team) < avgBelow
    
  case PressureCondition(ppdaAbove) =>
    currentPPDA(state, team) > ppdaAbove
}
```

Kiedy trigger aktywuje zmianę formacji lub pressingu → modele pośrednie są natychmiast przeliczane (nowe pozycje z formacji → nowy Pitch Control → nowy DxT).

---

## 11. Analityka post-match

Po zakończeniu symulacji, z listy ~1000-2000 zdarzeń obliczamy:

### 11.1 Metryki meczowe
```
xG per team = Σ xG of all shots
xT gained per team = Σ DxT_delta of all successful passes/dribbles
Possession % = events_team_A / total_events
Field Tilt = contacts_in_final_third per team
PPDA per team = opponent_passes / defensive_actions
```

### 11.2 VAEP per player
```
Dla każdego zdarzenia e_i:
  P_scores_before = xG probability before event
  P_scores_after = xG probability after event  
  P_concedes_before, P_concedes_after similarly
  
  VAEP(e_i) = (P_scores_after - P_scores_before) - (P_concedes_after - P_concedes_before)
  
Agreguj per player → ranking wkładu
```

### 11.3 Ghosting report
```
Dla każdego kluczowego momentu (bramka, szansa xG > 0.15, strata):
  ghost_positions = optimal_positions_from_model(formation, ball_position)
  actual_positions = positions_at_moment
  
  deviation_per_player = |actual - ghost|
  
  Raport: "LB był 4m za wysoko w momencie kontry w 42. minucie.
           Strefa za jego plecami miała Pitch Control 0.3 (niekontrolowana).
           Rywale to wykorzystali: kontra → xG 0.42 → bramka."
```

### 11.4 WPA Timeline
```
WPA_pre_match = f(Elo_diff, home_advantage)

Po każdym zdarzeniu:
  WPA_new = bayesian_update(WPA_old, event_type, minute, score)
  
  Bramka w 28 min przy 0:0 → WPA skacze z 0.54 na 0.71
  Zmiana taktyki w 60 min → WPA zmienia trend (widoczne w wykresie)
```

### 11.5 Passing Network
```
Z listy zdarzeń → buduj graf (who passed to whom)
  Edge weight = pass_count
  Node position = average position during match
  
Oblicz: Betweenness Centrality, Clustering Coefficient, Graph Density
Porównaj z planem: "Chciałeś grać centralnie, ale 72% podań szło lewą stroną"
```

---

## 12. Zarządzanie sezonem: między meczami

### 12.1 Regeneracja

```
Po meczu:
  Dla każdego gracza:
    stamina_recovery_per_day = naturalFitness / 20 × age_modifier
    days_until_next_match = schedule lookup
    recovered_stamina = stamina_recovery_per_day × days_until_next_match
    
    match_start_stamina = min(1.0, post_match_stamina + recovered_stamina)
```

### 12.2 ACWR tracking

```
acute_load = Σ metabolicLoad z ostatnich 7 dni
chronic_load = avg(metabolicLoad z ostatnich 28 dni)
ACWR = acute_load / chronic_load

Bezpieczne okno: 0.8 - 1.3
Poza oknem: injuryRisk rośnie
```

### 12.3 Rating (Glicko-2)

```
Po każdym meczu:
  rating_update = f(result, expected_result, rating_deviation)
  
Ranking ligi: sortuj po ratingu
Ranking graczy: sortuj po avg VAEP per 90
```

---

## 13. Wydajność i skalowanie

### 13.1 Koszt jednego meczu

| Operacja | Częstotliwość | Koszt | Łącznie |
|----------|---------------|-------|---------|
| Pitch Control compute | ~30 razy/mecz | ~200K ops | ~6M ops |
| DxT compute | ~30 razy | ~10K ops | ~300K ops |
| Matchup Matrix | ~30 razy | ~5K ops | ~150K ops |
| Action selection | ~1500 zdarzeń | ~100 ops | ~150K ops |
| Event resolution | ~1500 zdarzeń | ~50 ops | ~75K ops |
| **ŁĄCZNIE** | | | **~7M ops** |

Na JVM (Scala, single thread): **< 500ms per mecz**.

### 13.2 Skalowanie

```
1 mecz:       <500ms
10 meczów:    <5s (parallel z Cats Effect fibers)
380 meczów (sezon):  <30s
10 000 Monte Carlo symulacji:  <60s
Liga 20 zespołów × 38 kolejek:  <30s
Turniej 64 zespołów:  <10s
```

### 13.3 Multiplayer real-time

```
WebSocket: gracz wysyła GamePlan → serwer symuluje mecz → 
           streamuje highlights (FS2 stream) → gracz widzi kluczowe momenty
           
Latencja: <1s od wysłania GamePlan do pierwszych wyników
Full match result: <2s
Post-match analytics: +3-5s (VAEP, Ghosting, networks)
```

---

## 14. Errata i korekty do poprzednich dokumentów

### 14.1 GRA_FOOTBALL_MANAGER_DESIGN.md — korekty

| Sekcja | Problem | Korekta |
|--------|---------|---------|
| §7 PlayerAttributes | Skala 0-100, 12 atrybutów | Zastąpione: 30 atrybutów, skala 1-20 (patrz ATRYBUTY doc) |
| §7 PlayerAttributes | `deceleration` jako osobny atrybut | Usunięte: pochłonięte przez Agility |
| §7 PlayerAttributes | `aerialAbility` jako jeden atrybut | Rozdzielone na Jumping + Heading |
| §7 PlayerAttributes | `passing` jako jeden atrybut | Rozdzielone na Short Passing + Long Passing |
| §7 CognitiveProfile | Osobny case class z 3 polami | Usunięte: wchłonięte przez MentalAttributes (Vision, Decisions, Composure) |
| §7 PhysicalState | `sprintCapacityRemaining` | Usunięte: stamina już to obsługuje |
| §9 Metabolic Power | Formuła `pace / 200` | Poprawione: `pace / 40.0` (skala 1-20) |
| §8 Stałe fragmenty | `aerialAbility` w IWP | Zastąpione: Jumping + Heading + Strength |
| §3C xG | `shooting` jako jedyny modyfikator | Rozdzielone: Finishing (close) + Long Shots (distance) |
| §13 Przykład meczu | `pace=90` (skala 0-100) | Przeliczone: `pace=18` (skala 1-20) |

### 14.2 ALGORYTMY_ANALITYKI_PILKARSKIEJ.md — bez zmian

Dokument encyklopedyczny nie wymaga korekt — opisuje algorytmy niezależnie od implementacji gry.

### 14.3 ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md — bez zmian

Dokument atrybutów jest ŹRÓDŁEM PRAWDY. Niniejszy plan techniczny jest z nim w pełni spójny.

### 14.4 Uzupełnienia w niniejszym dokumencie (spójność z review)

| Element | Dodanie |
|--------|---------|
| Z-Score (Matchup Matrix) | Źródło `league_mean` i `league_stddev`: kontekst rozgrywek (Competition/League), pula referencyjna per pozycja; domyślnie np. mean=10, stddev=3. |
| Dixon-Coles | `home_advantage`: mnożnik 1.0–1.15, stała lub parametr ligi. |
| Stan meczu | `zone: Int` (1–96) — spójne z typem `Zone` w FORMACJE_ROLE_TAKTYKA.md §10.2. |

---

## Podsumowanie architektoniczne

```
                 GRACZ (trener)
                      │
                GamePlan + Skład
                      │
                      ▼
        ┌──────────────────────────┐
        │   WARSTWA 1: DANE        │   660 atrybutów + taktyka
        └────────────┬─────────────┘
                     │ transformacja (pre-match)
                     ▼
        ┌──────────────────────────┐
        │   WARSTWA 2: MODELE      │   Pitch Control, DxT, IWP matrix,
        │   POŚREDNIE              │   Pressing params, Pass Network, λ
        └────────────┬─────────────┘
                     │ informuje wybór (per zdarzenie)
                     ▼
        ┌──────────────────────────┐
        │   WARSTWA 3: MASZYNA     │   Stan → Wybór akcji → Akcja
        │   STANÓW                 │   (Vision, Decisions, Teamwork)
        └────────────┬─────────────┘
                     │ rozstrzygnięcie (losowanie)
                     ▼
        ┌──────────────────────────┐
        │   WARSTWA 4:             │   xPass → Bernoulli(0.79) → sukces
        │   ROZSTRZYGANIE          │   xG → Bernoulli(0.14) → pudło
        │                          │   IWP → Bernoulli(0.64) → drybling!
        └────────────┬─────────────┘
                     │ wynik → nowy stan
                     ▼
              ┌──────────────┐
              │ Nowy stan    │───→ (powrót do Warstwy 3)
              │ + fatigue    │
              │ + triggery   │
              └──────────────┘
                     │
              po 90+ minutach
                     ▼
        ┌──────────────────────────┐
        │   POST-MATCH             │   VAEP, Ghosting, WPA, Networks
        │   ANALYTICS              │   → Feedback do GRACZA
        └──────────────────────────┘
```

Każda warstwa jest niezależna, testowalna i wymienialna.  
Cały mecz: **< 500ms** na JVM.  
Gracz widzi DOKŁADNIE dlaczego wygrał lub przegrał.

---

*Plan techniczny v1.0 — Luty 2026*  
*Spójny z: ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md (źródło prawdy atrybutów)*  
*Zastępuje sekcje 3 i 7 z: GRA_FOOTBALL_MANAGER_DESIGN.md*
