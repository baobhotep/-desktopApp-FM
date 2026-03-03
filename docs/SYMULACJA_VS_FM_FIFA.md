# Symulacja meczu: Football Manager, FIFA i nasz projekt

**Źródła:** przegląd publicznych opisów FM, FIFA oraz dokumentacji technicznej; dokładne algorytmy FM/FIFA są zastrzeżone.

---

## 1. Football Manager (Sports Interactive)

### Na jakiej podstawie symulowane są mecze

- **Model:** Symulacja **zdarzeniowa (event-based)** — mecz to sekwencja zdarzeń (podanie, strzał, przechwyt, faul itd.), a nie fizyka w czasie rzeczywistym klatka po klatce.
- **Atrybuty:** Wynik każdej akcji zależy od **atrybutów zawodników** (passing, tackling, vision, decisions, pace, finishing itd.) oraz od **pozycji** — różne atrybuty mają różny wpływ w zależności od pozycji (np. dla bramkarza: reflexes, agility, handling; dla napastnika: finishing, composure).
- **Prawdopodobieństwa:** Zamiast „prostych” rzutów kostką stosuje się **modele matematyczne** (w analizach społeczności opisywane m.in. jako modele z wagami / współczynnikami dla atrybutów), które dają prawdopodobieństwa sukcesu/porażki danej akcji.
- **Kontekst:** Na wynik wpływ mają m.in.:
  - forma, zmęczenie, morale,
  - taktyka (formacja, instrukcje),
  - przewaga gospodarzy,
  - „momentum” (np. po strzelonym golu).
- **Szczegóły:** Pełna receptura nie jest ujawniana; społeczność (np. FM-Arena) bada, które atrybuty i w jakiej sile wpływają na oceny i wyniki (np. współczynniki ważności atrybutów per pozycja).

---

## 2. FIFA (EA Sports)

### Na jakiej podstawie symulowane są mecze

- **Tryb „Sim Match” (szybki symulowany wynik):** Działa w tle bez odtwarzania całego meczu. Prawdopodobnie używa **ocen drużyn/zawodników + elementu losowego** do wygenerowania wyniku (gole, ewentualnie kartki) — szczegóły nie są oficjalnie opisane.
- **Tryb „graj mecz”:** Pełna symulacja w czasie rzeczywistym w silniku **Frostbite**: fizyka piłki, kolizje, animacje (m.in. **HyperMotion** — ML na podstawie motion capture). To zupełnie inny model niż FM: ciągła fizyka i AI w czasie rzeczywistym, a nie dyskretna sekwencja zdarzeń.
- **Wniosek:** Dla porównania z naszym projektem istotny jest głównie **FM** — tam też mamy symulację zdarzeniową opartą na atrybutach i prawdopodobieństwach. FIFA „Sim” to raczej szybki wynik z ocen + RNG; FIFA „graj” to inna kategoria (fizyka + animacje).

---

## 3. FIFE (Fifengine)

**FIFE** (Flexible Isometric Free Engine) to **ogólny silnik do gier** (izometria, RTS, RPG), a nie symulator piłki nożnej. Nie ma tu dedykowanej „symulacji meczu” w sensie FM/FIFA. Jeśli chodzi o porównanie z naszym projektem, FIFE nie jest punktem odniesienia.

---

## 4. Odniesienie do naszego projektu (FullMatchEngine)

### Co mamy wspólnego z Football Managerem

| Aspekt | Football Manager | Nasz projekt (FullMatchEngine) |
|--------|------------------|--------------------------------|
| **Typ symulacji** | Zdarzeniowa (event-based) | Zdarzeniowa: pętla zdarzeń do ~90 min (5400 s) |
| **Stan meczu** | Pozycje / sytuacja / wynik | `MatchState`: strefa piłki, posiadanie, wynik, zmęczenie, Pitch Control, DxT |
| **Kto wykonuje akcję** | Wybór aktora zależny od taktyki i pozycji | Ważony wybór **aktora** (strefa, rola, slot, dopasowanie do pozycji) |
| **Sukces akcji** | Prawdopodobieństwo z atrybutów + kontekst | P(podanie), P(strzał→gol xG), P(przechwyt), P(duel) z atrybutów (passing, finishing, tackling, pace, agility itd.) |
| **Taktyka** | Formacja, instrukcje, role | `GamePlanInput`: formacja, `triggerConfig`, `teamInstructions`, `slotRoles`, `playerInstructions`, set pieces |
| **Kontekst** | Forma, zmęczenie, morale, dom | `fatigueByPlayer`, `morale`, `homeAdvantage`, minuta, różnica bramek |
| **Przestrzeń** | Wpływ na to, co się dzieje na boisku | Strefy 1–12, **Pitch Control** (kontrola stref), **DxT** (zagrożenie), pressing w strefach, kontratak po odzyskaniu w strefie |

Czyli: **filozoficznie i technicznie jesteśmy w tej samej „rodzinie” co FM** — zdarzenie po zdarzeniu, atrybuty → prawdopodobieństwa, taktyka i kontekst wpływają na wybór i wynik akcji.

### Różnice (na naszą korzyść / specyfika)

- **Analityka wbudowana:** U nas każdy mecz generuje nie tylko wynik i zdarzenia, ale od razu **xG, xT, VAEP, PPDA, Pitch Control, xPass, IWP** itd. FM nie eksponuje w ten sposób zaawansowanych metryk w samym silniku.
- **Struktura przestrzeni:** Jawny model **stref (1–12)** i **Pitch Control** (kto kontroluje strefę) oraz **DxT** daje czytelną, dyskretną reprezentację „gdzie co się dzieje” i gdzie jest zagrożenie.
- **Modele wymienne:** xG i VAEP można podmieniać (`xgModelOverride`, `vaepModelOverride`) — formuła domyślna lub model ładowany (np. z treningu), co zbliża nas do „ML-ready” bez zmiany kontraktu silnika.
- **Zdarzenia z metadanymi:** Każde zdarzenie może mieć `metadata` (xG, PSxG, strefa, presja, receiverPressure itd.), co ułatwia analizy i ewentualne trenowanie modeli.

### Czego u nas nie ma (a FM ma po latach rozwoju)

- **Bardzo dopracowane wagi atrybutów** per pozycja i per typ akcji (SI ma lata danych i balansu).
- **Zaawansowany model „momentum”** i wpływu tłumu / psychologii w trakcie meczu.
- **Oficjalny, szczegółowy opis** działania silnika (u nas: kod i dokumentacja projektu; u FM — reverse engineering społeczności).

---

## 5. Podsumowanie

- **Football Manager:** Symulacja meczów oparta na **zdarzeniach**, **atrybutach zawodników** (z wagami zależnymi od pozycji) i **prawdopodobieństwach** w kontekście taktyki, formy i sytuacji meczu. To jest najbliższy odpowiednik naszego podejścia.
- **FIFA:** „Sim Match” — prawdopodobnie uproszczony wynik z ocen + RNG; gra w mecz to już fizyka i animacje w czasie rzeczywistym (inna filozofia niż FM i nasz silnik).
- **FIFE:** Silnik ogólny (izometria, RTS/RPG), nie symulacja piłkarska.

**Nasz projekt** symuluje mecze w **tej samej kategorii co FM**: zdarzenie po zdarzeniu, atrybuty + taktyka + kontekst → prawdopodobieństwa, z dodatkiem jawnej struktury stref, Pitch Control, DxT i bogatej analityki (xG, xT, VAEP, xPass itd.) oraz możliwości podmiany modeli xG/VAEP.
