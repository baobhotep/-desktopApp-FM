# Kompletna Lista Atrybutów Zawodników

## Precyzyjne mapowanie atrybutów na algorytmy silnika gry

**Perspektywa**: Analityk danych / Architekt Scala / Ekspert futbolowy  
**Data**: Luty 2026  
**Źródła porównawcze**: Football Manager (38 attr), EA FC 25 (34 attr), algorytmy z dokumentacji  
**Skala**: 1-20 (jak FM) — bardziej granularna niż 0-100 przy mniejszym zakresie, łatwiejsza do balansu  
**Spójność z**: `SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md` (implementacja techniczna), `FORMACJE_ROLE_TAKTYKA.md` (jak role korzystają z atrybutów)

---

## Spis treści

- [Podsumowanie systemu](#podsumowanie-systemu)
- [A. Atrybuty fizyczne (8)](#a-atrybuty-fizyczne-8)
- [B. Atrybuty techniczne (10)](#b-atrybuty-techniczne-10)
- [C. Atrybuty mentalne (12)](#c-atrybuty-mentalne-12)
- [D. Atrybuty bramkarza (6)](#d-atrybuty-bramkarza-6)
- [E. Cechy dodatkowe (Traits)](#e-cechy-dodatkowe-traits)
- [F. Parametry fizyczne (nie-atrybuty)](#f-parametry-fizyczne-nie-atrybuty)
- [G. Macierz: Algorytm × Atrybut](#g-macierz-algorytm--atrybut)
- [H. Porównanie z FM i FIFA](#h-porównanie-z-fm-i-fifa)

---

## Podsumowanie systemu

| Kategoria | Ilość | Uwagi |
|-----------|-------|-------|
| Fizyczne | 8 | Parametryzują modele fizyczne (Pitch Control, Metabolic Power) |
| Techniczne | 10 | Parametryzują modele zdarzeń (xG, xPass, IWP) |
| Mentalne | 12 | Parametryzują modele decyzyjne (Ghosting, Scanning, VAEP) |
| Bramkarz | 6 | Osobny zestaw zastępujący techniczne dla GK |
| Traits | 9 | Binarne/małe modyfikatory dla niszowych sytuacji |
| Parametry fizyczne | 3 | Cechy ciała (wzrost, waga, noga) — nie umiejętności |
| **RAZEM** | **36 atrybutów + 9 traits + 3 parametry** | |

**Zasada projektowa**: Każdy atrybut musi spełniać WSZYSTKIE trzy warunki:
1. Jest bezpośrednim wejściem do co najmniej jednego algorytmu silnika meczu
2. Tworzy unikalny trade-off z innymi atrybutami (nie jest pochodną)
3. Generuje sensowną decyzję taktyczną dla gracza (trenera)

---

## A. Atrybuty fizyczne (8)

---

### A1. Pace (Szybkość maksymalna)

**Definicja**: Maksymalna prędkość biegowa zawodnika na otwartej przestrzeni.

**Odpowiednik w FM**: Pace | **W FIFA**: Sprint Speed

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Pitch Control** | Czas dotarcia do punktu (time-to-intercept) — KLUCZOWY parametr fizycznego modelu ruchu Spearmana. Wyższy pace = szybsze dotarcie = większa kontrola przestrzeni. | `time_to_point = f(distance, current_velocity, max_speed=pace, acceleration)` |
| **IWP (sprint race)** | Główna zmienna w biegach na otwartej przestrzeni. Decyduje kto pierwszy dobiegnie do luźnej piłki. | `IWP_sprint = σ(Z_pace_att - Z_pace_def + context)` |
| **Counter-Attack Speed** | Szybkość tranzycji ofensywnej. Wyższy pace napastników = szybsze kontrataki = mniej czasu dla obrony na ustawienie. | `transition_time = distance / avg_pace_attackers` |
| **Pressing Effectiveness (PPDA)** | Szybkość zamykania przestrzeni. Szybsi gracze pressują efektywniej — pokrywają więcej boiska w tym samym czasie. | `pressing_reach = pace × time_window` |
| **Voronoi / Player Influence Area** | Kształt komórki Voronoi. Szybszy gracz ma większą potencjalną komórkę (kontroluje więcej przestrzeni). Z modelem Fernándeza — elipsa rozciąga się w kierunku ruchu proporcjonalnie do pace. | `influence_radius ∝ pace` |
| **Metabolic Power** | Koszt energetyczny biegania. Sprint z pace=18 kosztuje WIĘCEJ energii niż bieg z pace=12 (wyższa prędkość = wyższy koszt). Jest to naturalny trade-off: szybcy gracze szybciej się męczą przy sprintach. | `metabolic_cost_sprint = distance × pace_factor` |

**Trade-off**: Wysoki pace + niski positioning = szybki, ale biega w złe miejsca. Wysoki pace + niska stamina = rewelacyjny przez 60 minut, potem katastrofa.

---

### A2. Acceleration (Przyspieszenie)

**Definicja**: Tempo osiągania prędkości maksymalnej ze stanu spoczynku. Kluczowe na krótkich dystansach (5-15m), które dominują w piłce.

**Odpowiednik w FM**: Acceleration | **W FIFA**: Acceleration

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Pitch Control** | Faza startowa time-to-intercept. Na krótkich dystansach (do 15m) acceleration dominuje nad pace. Obrońca z acc=18 zamknie 5-metrową lukę szybciej niż ten z acc=10 i pace=19. | `time_to_reach(short_dist) = √(2 × distance / acceleration)` |
| **Dynamic Pressure Model** | Czas zamknięcia linii podania (closing speed). Bezpośrednio wpływa na indywidualne p_i w modelu presji. Wysoka acc = wyższe p_i = większa presja na posiadającym piłkę. | `p_i = f(time_to_close) ← f(acceleration, distance)` |
| **Counter-Pressing** | Szybkość pierwszej reakcji po stracie piłki. Acc determinuje, ile metrów gracz pokona w pierwszych 2-3 sekundach po stracie — kluczowe dla gegenpressingu. | `recovery_distance_3s = f(acceleration)` |
| **IWP (starty)** | Przewaga w "wyścigach do piłki" na krótki dystans — odbiory, przechwycenia, reakcje na długie podania. | `IWP_start = σ(Z_acc_att - Z_acc_def)` |
| **Metabolic Power** | Starty do sprintu kosztują WIĘCEJ energii niż bieg ze stałą prędkością. Wysoka acc oznacza dużą moc wyjściową = szybsze zużycie energii. | `metabolic_cost_start = acceleration² × mass × distance` |

**Dlaczego osobno od Pace**: Na dystansie 5m gracz z acc=18/pace=14 pokona gracza z acc=10/pace=19. Na dystansie 40m będzie odwrotnie. To dwa różne parametry fizyki ruchu — rozdzielenie tworzy archetypową różnicę: "szybki ale wolno startuje" vs "błyskawiczny start ale niska prędkość max".

---

### A3. Agility (Zwinność)

**Definicja**: Zdolność do szybkiej zmiany kierunku biegu, hamowania i manewrowania z piłką przy nodze.

**Odpowiednik w FM**: Agility | **W FIFA**: Agility

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP (drybling 1v1)** | Modyfikator atakujący — zwinny gracz skuteczniej zmienia kierunek i omija obrońcę. Wpływa na zdolność do zwodów i uników. | `IWP_dribble = σ(Z_dribbling + 0.3×Z_agility - Z_tackling_def + context)` |
| **IWP (obrona 1v1)** | Zdolność obrońcy do reagowania na zwody napastnika. Bez agility nawet dobry tackle nie pomoże, jeśli obrońca nie nadąży za zmianą kierunku. | `IWP_defend = σ(Z_tackling + 0.3×Z_agility - Z_dribbling_att)` |
| **Pitch Control (dynamiczny)** | W dynamicznym Pitch Control zmiana kierunku biegu wpływa na time-to-intercept. Zwinny gracz szybciej koryguje trajektorię. | `direction_change_penalty = base_time × (1 - agility/max)` |
| **Ball Carry (xT)** | Efektywność niesienia piłki — wartość xT zrealizowana przy dryblingu. Zwinni gracze tracą mniej prędkości przy zmianie kierunku z piłką. | `carry_xT_loss = base_loss × (1 - agility/max)` |
| **Deceleration** | Agility w dużej mierze determinuje zdolność hamowania — kluczowe w nowoczesnym futbolu. Badania pokazują, że deceleration bywa ważniejsza od prędkości max. | `deceleration_ability ≈ 0.7 × agility + 0.3 × strength` |

**Dlaczego osobny atrybut (a nie trait)**: FM i FIFA mają go jako pełny atrybut. W naszym silniku Agility rozstrzyga sytuacje 1v1 na równi z Dribbling/Tackling. Bez niego dryblingi byłyby zbyt uproszczone. Tworzy archetypowy trade-off: silny ale sztywny CB vs zwinny ale słaby skrzydłowy.

**Pochłania z wcześniejszej analizy**: "Deceleration" (Tier 3) — obliczana jako f(agility, strength).

---

### A4. Stamina (Wytrzymałość)

**Definicja**: Pojemność energetyczna — ile łącznej pracy zawodnik może wykonać przez 90 minut bez krytycznego spadku wydajności.

**Odpowiednik w FM**: Stamina | **W FIFA**: Stamina

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Metabolic Power (budżet)** | Określa łączny "bak paliwa" na mecz. Każda akcja (sprint, pressing, bieg) odejmuje z tego budżetu. | `energy_budget = stamina × base_coefficient` |
| **Fatigue Curve** | Determinuje krzywą spadku wydajności. Przy stamina < 50%: pace, acceleration, vision, composure zaczynają spadać. | `effective_attr = base_attr × fatigue_multiplier(current_stamina)` |
| **PPDA Sustainability** | Jak długo zespół utrzyma intensywny pressing. Gracze z niską stamina "wypadają" z pressingu po 50-60 minutach → PPDA rośnie. | `ppda_minute_70 = f(avg_team_stamina)` |
| **ACWR** | Wrażliwość na przetrenowanie. Niska stamina + wysoki ACWR = wyższe ryzyko kontuzji. | `injury_risk = f(acwr, stamina, natural_fitness)` |
| **In-Game Triggers** | Średnia stamina drużyny jest zmienną wejściową do TacticalTrigger (StaminaCondition). Gracz ustala: "jeśli avg stamina < X → zmień taktykę". | `trigger = avg_stamina < threshold` |

**Trade-off z Work Rate**: Stamina = "ile MOŻE biegać" (fizyczna pojemność). Work Rate = "ile CHCE biegać" (mentalność). Gracz z WR=18 i stamina=8 biegnie ile może, ale szybko się wykańcza. Gracz z WR=8 i stamina=18 oszczędza energię — stały ale pasywny.

---

### A5. Strength (Siła fizyczna)

**Definicja**: Siła mięśniowa — zdolność do wygrywania kontaktu fizycznego, trzymania rywala na dystans, utrzymania pozycji.

**Odpowiednik w FM**: Strength (ukryty atrybut fizyczny) | **W FIFA**: Strength

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP (fizyczne starcia)** | Główna zmienna w walkach "barki w barki" — obrońca wypychający napastnika, napastnik trzymający obrońcę na dystans tyłem do bramki. | `IWP_physical = σ(Z_strength_att - Z_strength_def + Z_balance)` |
| **IWP (powietrze)** | Komponent fizyczny główek — kto wygra pozycję w powietrzu. Siła pozwala na wypchnięcie rywala przed skokiem. | `IWP_aerial = σ(Z_jumping + 0.4×Z_strength + 0.3×Z_heading - Z_opp)` |
| **Ball Retention (kontakt)** | Zdolność do utrzymania piłki przy kontakcie fizycznym. Silny napastnik plecami do bramki utrzymuje piłkę pod presją obrońców. | `retention_p = f(strength, balance, first_touch, pressure)` |
| **Pressing Resistance** | Odporność posiadającego piłkę na fizyczny pressing. Słaby technik pod presją silnego obrońcy traci piłkę częściej. | `xPass_under_physical_pressure = xPass × strength_modifier` |

**Dlaczego osobno od Tackling**: Tackling = TECHNIKA odbioru (timing, nogi). Strength = FIZYCZNOŚĆ (ramiona, pozycja ciała). Lekki, techniczny defensywny pomocnik może mieć tackling=17 ale strength=9 — wygrywa odbiory z dystansu (timing), ale przegrywa kontakt barki-w-barki. To fundamentalnie różne wymiary.

---

### A6. Jumping (Skok)

**Definicja**: Zdolność do wyskoku pionowego — jak wysoko zawodnik może się wznieść.

**Odpowiednik w FM**: Jumping Reach | **W FIFA**: Jumping

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP (powietrze)** | Główna zmienna decydująca o tym, kto wygra walkę w powietrzu. Współdziała ze Strength (pozycja) i Heading (celność). | `IWP_aerial = σ(Z_jumping + Z_heading + 0.4×Z_strength)` |
| **Set Pieces (GMM)** | W modelu stref aktywnych: jumping determinuje, do których stref GMM zawodnik realnie sięga. Gracz z jumping=6 nie wygra główki na dalszym słupku. | `zone_reachability = f(jumping, height, positioning)` |
| **Defensive Clearances** | Zdolność do odprowadzenia piłki z pola karnego głową. Kluczowe przy dośrodkowaniach i rożnych rywala. | `clearance_success = f(jumping, heading, positioning)` |

**Dlaczego osobno od Heading**: Jumping = jak WYSOKO sięga. Heading = jak CELNIE i MOCNO uderza głową. Gracz z jumping=18 i heading=6 wygrywa pozycję w powietrzu, ale nie trafia celnie. Gracz z jumping=6 i heading=18 jest celny, ale nie dosięga piłki. Razem tworzą "aerial ability".

---

### A7. Natural Fitness (Kondycja bazowa)

**Definicja**: Wrodzona sprawność fizyczna — determinuje tempo regeneracji między meczami, bazową odporność na kontuzje i "podłogę" wydajności fizycznej.

**Odpowiednik w FM**: Natural Fitness | **W FIFA**: brak bezpośrednika (implicite w stamina)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **ACWR Recovery** | Tempo regeneracji obciążenia. Wyższy NF = szybszy powrót ACWR do normy po intensywnym tygodniu meczowym. | `recovery_rate = natural_fitness × base_recovery` |
| **Injury Risk Model** | Modyfikator w modelu prognozowania kontuzji. NF obniża bazowe ryzyko kontuzji. | `injury_risk = base_risk × (1 - natural_fitness/40)` |
| **Season Stamina Floor** | Przy niskiej formie (wiele meczów z rzędu) stamina nie spada poniżej progu zależnego od NF. Gracz z NF=18 nawet zmęczony ma "rezerwę". | `stamina_floor = natural_fitness × 2.5 (percentile of max)` |
| **Inter-Match Recovery** | Ile staminy odzyskuje zawodnik między meczami. Kluczowe przy gęstym kalendarzu (2 mecze/tydzień). | `stamina_recovered_per_day = f(natural_fitness, age)` |

**Dlaczego osobny (a nie trait)**: FM ma go jako pełny atrybut i dobrze działa. W naszej grze zarządzanie kadrą między meczami (rotacja, odpoczynek) jest strategiczną decyzją. NF determinuje, kto "wytrzyma" gęsty kalendarz — bez niego rotacja nie ma sensu.

---

### A8. Balance (Równowaga)

**Definicja**: Zdolność utrzymania pozycji i kontroli ciała po kontakcie fizycznym z rywalem.

**Odpowiednik w FM**: Balance | **W FIFA**: Balance

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP (drybling po kontakcie)** | Czy atakujący utrzyma się na nogach po szarży obrońcy. Wysoki balance = kontynuacja biegu mimo kontaktu. | `post_contact_retention = f(balance, strength, dribbling)` |
| **IWP (fizyczne starcia)** | Modyfikator w walkach ramię-w-ramię. Balance pomaga mniejszym/słabszym graczom kompensować brak siły. | `IWP_physical += balance_bonus × (strength_diff < 0 ? 1 : 0.3)` |
| **Ball Retention Under Pressure** | Utrzymanie piłki pod fizycznym pressingiem. Balance pozwala na obracanie się z piłką przy kontakcie obrońcy. | `retention = f(first_touch, balance, strength, pressure)` |
| **Foul Drawing** | Gracz z wysokim balance, który zostaje powalony, prawidłowo sygnalizuje to sędziemu (bo normalnie się utrzymuje). Wysokie balance = częściej faul na korzyść przy kontakcie. | `foul_probability = f(contact_force, balance, position)` |

**Dlaczego osobny (a nie w Agility)**: Agility = zmiana kierunku BEZ kontaktu. Balance = zachowanie kontroli PRZY kontakcie. Messi ma Agility=20 i Balance=18 — zmienia kierunek błyskawicznie I utrzymuje się na nogach po kontakcie. Duży napastnik może mieć Agility=8 ale Balance=16 — nie kręci piruetów, ale nikt go nie przewróci.

---

## B. Atrybuty techniczne (10)

---

### B1. Finishing (Wykończenie)

**Definicja**: Jakość strzału z bliskiej i średniej odległości — precyzja placement'u piłki w bramce.

**Odpowiednik w FM**: Finishing | **W FIFA**: Finishing

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xG (modyfikator zawodnika)** | Bazowy xG mówi "jakie szanse z tej pozycji dla przeciętnego gracza". Finishing modyfikuje: gracz z finishing=18 konwertuje POWYŻEJ xG, z finishing=6 poniżej. | `personal_xG = base_xG × (1 + (finishing - 10) × 0.03)` |
| **PSxG (Post-Shot xG)** | Determinuje GDZIE w bramce trafia piłka. Wyższe finishing = bliżej rogów = wyższe PSxG = trudniejsze do obrony. | `psxg_placement_quality = f(finishing, technique)` |
| **Set Pieces (strzały)** | Jakość strzału po stałym fragmencie — wykończenie z krótkiego dystansu po rożnym/rzucie wolnym. | `set_piece_xG_modifier = finishing_factor` |

**Dlaczego osobno od Long Shots**: W danych xG istnieje wyraźna RÓŻNICA STATYSTYCZNA w konwersji strzałów z <18m vs >18m. Wielu napastników z finishing=18 jest kiepskich z dystansu (brak siły, krzywizny). To oddzielna umiejętność — fizyka strzału z 5m jest inna niż z 25m.

---

### B2. Long Shots (Strzały z dystansu)

**Definicja**: Jakość i siła strzałów z dużej odległości (poza polem karnym).

**Odpowiednik w FM**: Long Shots | **W FIFA**: Long Shots

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xG (dystansowy modyfikator)** | Strzały z dystansu >20m mają bazowe xG ~0.02-0.06. Long Shots podnosi to — gracz z long_shots=18 zamienia strzał z 25m w realną szansę. | `distant_xG = base_distant_xG × (1 + (long_shots - 10) × 0.05)` |
| **Shot Viability Decision** | Determinuje, czy silnik symulacji w ogóle GENERUJE strzał z dystansu. Gracz z long_shots=5 w taktyce z shootingRange=0.8 i tak nie strzeli z 30m. | `shot_attempt_prob = f(long_shots, shootingRange, xT_current_zone)` |
| **Free Kick Power** | Komponent siłowy przy bezpośrednich rzutach wolnych (fizyka: Drag, prędkość, trajektoria). | `free_kick_power = f(long_shots, technique)` |

**Taktyczny trade-off**: Gracz z GamePlan `shootingRange=0.8` i napastnikiem z long_shots=8 → dużo strzałów z dystansu o niskim xG = marnowanie posiadania. Ten sam plan z long_shots=17 → realne zagrożenie z 25m.

---

### B3. Short Passing (Krótkie podania)

**Definicja**: Precyzja i waga podań na krótki i średni dystans (do ~25m).

**Odpowiednik w FM**: Passing | **W FIFA**: Short Passing

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xPass (krótki dystans)** | Bezpośredni parametr: prawdopodobieństwo sukcesu podania do 25m. | `xPass_short = base_xPass × (1 + (short_passing - 10) × 0.025)` |
| **Passing Network (Graph Density)** | Wyższe short_passing → więcej zrealizowanych podań → wyższa Graph Density → więcej opcji pod presją. | `graph_density ∝ avg_short_passing` |
| **Passing Network (Clustering Coefficient)** | Trójkąty podań (np. LB↔LM↔LW) stabilniejsze, gdy wszyscy mają wysoki short_passing. | `clustering ∝ min(passing_of_triangle_members)` |
| **xT Realization (short moves)** | Wartość progresji piłki przez krótkie podania. Zespół budujący Short Passing polega na tym atrybucie. | `xT_short_pass = xT_delta × xPass_short_success` |
| **Build-up Quality** | Przy BuildUpStyle=ShortPassing jakość budowania zależy wprost od short_passing zespołu. | `buildup_efficiency = f(avg_short_passing, formation, opponent_pressing)` |

**Dlaczego rozdzielenie Short/Long Passing**: FM ma jedno "Passing" + "Long Passing" (2 atrybuty). FIFA ma "Short Passing" + "Long Passing" (2 atrybuty). Rozdzielenie jest KLUCZOWE dla taktyki: zespół grający ShortPassing (tiki-taka) potrzebuje short_passing. Zespół grający DirectPlay potrzebuje long_passing. Gdyby był jeden atrybut, nie da się odróżnić playmakerów od "catapult" obrońców.

---

### B4. Long Passing (Długie podania)

**Definicja**: Precyzja i zasięg podań na duży dystans (>25m) — przerzuty, switching play, podania prostopadłe na 30m+.

**Odpowiednik w FM**: Long Passing (osobny atrybut) | **W FIFA**: Long Passing

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xPass (długi dystans)** | Prawdopodobieństwo sukcesu podania >25m. Podania długie mają naturalnie niższe bazowe xPass — long_passing podnosi to. | `xPass_long = base_xPass_long × (1 + (long_passing - 10) × 0.03)` |
| **xT Realization (duże skoki)** | Przy DirectPlay — wartość progresji przez duże przerzuty. Przeskoczenie 3-4 stref xT jednym podaniem. | `xT_long_pass = xT_delta_large × xPass_long_success` |
| **Switch of Play** | Zmiana strony ataku — kluczowa taktyka przeciw niskim blokom. Long_passing determinuje, czy zmiana strony dochodzi. | `switch_success = f(long_passing, technique, distance)` |
| **Counter-Attack Initiation** | Pierwsze podanie kontrataku — najczęściej długie, z obrony do ataku. | `counter_launch_xPass = f(long_passing, vision, pressure)` |

**Taktyczny trade-off**: CB z long_passing=17 ale short_passing=8 jest idealny do DirectPlay (długie piłki na napastnika) ale katastrofalny w tiki-taka. I odwrotnie.

---

### B5. Crossing (Dośrodkowania)

**Definicja**: Jakość podań dośrodkowujących — z biegu, z głębi, ze stojącej pozycji — piłka podana w powietrzu do pola karnego.

**Odpowiednik w FM**: Crossing | **W FIFA**: Crossing

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xPass (dośrodkowania)** | Osobna kategoria xPass — dośrodkowania mają inną fizykę (lot w powietrzu, kąt, zakręcenie). Crossing jest główną zmienną. | `xPass_cross = f(crossing, technique, angle, distance, pressure)` |
| **Set Pieces (rożne — delivery)** | Jakość dośrodkowania z rożnego. Crossing determinuje, czy piłka trafia w wybraną strefę GMM. | `corner_delivery_accuracy = f(crossing, technique)` |
| **Set Pieces (rzuty wolne — podanie)** | Podanie ze stałego fragmentu do pola karnego (nie strzał bezpośredni). | `fk_delivery = f(crossing, technique)` |
| **Wing Play Effectiveness** | Przy AttackingApproach z crossingPreference=High — efektywność ataków bokiem. Bez dobrego crossing, dośrodkowania nie dochodzą. | `wing_attack_xG = f(crossing_quality, aerial_IWP_in_box)` |

**Dlaczego osobno od Passing**: FM i FIFA mają crossing jako osobny atrybut. Powód algorytmiczny: podanie po ziemi (passing) i podanie w powietrzu (crossing) to ZUPEŁNIE inna fizyka. Rozgrywający z passing=18 może mieć crossing=6 (nigdy nie dośrodkowuje) — i odwrotnie dla bocznego obrońcy. Rozdzielenie tworzy kluczowy wybór: gra przez środek (passing) vs gra bokami (crossing).

---

### B6. Dribbling (Drybling)

**Definicja**: Kontrola piłki przy biegu i zdolność do omijania rywali w bezpośrednim starciu 1v1.

**Odpowiednik w FM**: Dribbling | **W FIFA**: Dribbling

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP (drybling 1v1)** | GŁÓWNA zmienna atakująca w starciu 1v1. Dribbling vs Tackling obrońcy to rdzeń pojedynku. | `IWP_dribble = σ(Z_dribbling + Z_agility×0.3 + Z_pace×0.2 - Z_tackling - Z_agility_def×0.3)` |
| **Ball Carry (xT)** | Wartość niesienia piłki — xT gained through carries. Dribbling determinuje, czy gracz może efektywnie przesuwać piłkę driblując przez strefy. | `carry_success = f(dribbling, agility, pace, opponent_pressure)` |
| **Ball Retention Under Pressure** | Utrzymanie piłki pod pressingiem — drybling to jeden ze sposobów na ucieczkę spod presji. | `escape_probability = f(dribbling, agility, composure, pressure_level)` |
| **VAEP (akcje ofensywne)** | Udany drybling generuje dodatnią wartość VAEP — szczególnie w tercji ataku, gdzie omija obrońcę. | `vaep_dribble = xT_gained × IWP_success` |

---

### B7. First Touch (Przyjęcie piłki)

**Definicja**: Jakość kontroli piłki w momencie jej odbioru — "jak czysto" gracz przyjmuje podanie.

**Odpowiednik w FM**: First Touch | **W FIFA**: Ball Control

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Spatial Gain Post-Scanning** | KLUCZOWE połączenie: badania pokazują, że gracze ze świetnym first touch w połączeniu ze skanowaniem zyskują więcej metrów w pierwszym kontakcie. First touch zamienia "widzenie opcji" (vision) w faktyczne działanie. | `spatial_gain = f(first_touch, scanning_frequency)` |
| **xPass Chain** | Jakość podań w sekwencji zależy od first touch poprzedniego odbiorcy — jeśli przyjął brudno, następne podanie jest opóźnione (mniej czasu, więcej presji). | `xPass_chain_modifier = receiver_first_touch / max_ft` |
| **Ball Retention (presja)** | Pod presją: jeśli first touch jest czysty, gracz ma czas na decyzję (podanie, obrót). Jeśli brudny — piłka się oddala, obrońca zamyka. | `retention_on_receipt = f(first_touch, pressure, composure)` |
| **Counter-Attack Execution** | W kontrze liczy się tempo — perfekcyjny first touch pozwala na natychmiastowy atak. Brudny first touch traci sekundę, obrona wraca. | `counter_tempo_loss = (20 - first_touch) × 0.05 seconds` |
| **Tempo realizacji** | Przy taktyce z Tempo=Fast — cały zespół potrzebuje wysokiego first touch, bo piłka krąży szybko. Jedno brudne przyjęcie hamuje tempo. | `team_tempo = min(avg_first_touch, tactical_tempo)` |

**Dlaczego osobno od Dribbling**: Dribbling = bieganie z piłką przy nodze, ogrywanie rywali. First Touch = JEDNO dotknięcie przy przyjmowaniu podania. Napastnik-bramkostrzelec może mieć finishing=19 ale first_touch=10 — trafia z pozycji, ale gubi piłkę przy przyjęciu. To fundamentalnie różne umiejętności.

---

### B8. Heading (Główki)

**Definicja**: Celność i siła uderzeń głową — zarówno strzały, jak i podania głową.

**Odpowiednik w FM**: Heading | **W FIFA**: Heading Accuracy

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xG (główki)** | Strzały głową mają inny profil xG niż strzały nogą. Heading modyfikuje bazowe xG główek. | `xG_header = base_xG_header × (1 + (heading - 10) × 0.04)` |
| **IWP (powietrze — ofensywa)** | Komponent celności w walce w powietrzu. Jumping = kto dosięgnie, Heading = kto celnie uderzy. | `IWP_aerial_att = σ(Z_jumping + Z_heading + Z_strength×0.4)` |
| **Set Pieces (rożne/rzuty wolne)** | Kluczowy atrybut w strefach GMM — czy zawodnik celnie uderzy główką po dośrodkowaniu z rożnego. | `set_piece_xG_header = zone_xG × heading_modifier` |
| **Defensive Clearances** | Odprowadzenie piłki głową — kluczowe przy obronie dośrodkowań. Heading wpływa na KIERUNEK clearance (dobry heading = bezpiecznie z pola karnego, zły = "na kolano" w strefie zagrożenia). | `clearance_quality = f(heading, jumping, composure)` |

---

### B9. Tackling (Odbiory)

**Definicja**: Technika i timing odbioru piłki — wślizg, standing tackle, wchodzenie w posiadanie.

**Odpowiednik w FM**: Tackling | **W FIFA**: Standing Tackle + Sliding Tackle (połączone)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP (defensywne 1v1)** | GŁÓWNA zmienna defensywna w starciu z dryblingiem. Tackling vs Dribbling to rdzeń pojedynku. | `IWP_defend = σ(Z_tackling + Z_agility×0.3 + Z_anticipation×0.2 - Z_dribbling_att)` |
| **Dynamic Pressure p_i** | Indywidualne prawdopodobieństwo przejęcia piłki przez obrońcę w modelu presji. Wyższe tackling = wyższe p_i = większa presja na posiadającym. | `p_i = base_intercept × (1 + (tackling - 10) × 0.03)` |
| **PPDA Effectiveness** | Ile faktycznych odbiorów generuje pressujący gracz. Tackling konwertuje pressing (bieganie za rywalem) w wynik (odbiór). | `ppda_conversion = f(tackling, anticipation, aggression)` |
| **Counter-Pressing Success** | Efektywność odbioru w ciągu 5 sekund po stracie piłki. | `counterpress_recovery = f(tackling, acceleration, work_rate)` |
| **Foul Risk** | Niski tackling + wysoka aggression = ryzyko faulu przy próbie odbioru. Model rozstrzyga: odbiór czysty vs faul. | `foul_prob = f(aggression / tackling, ball_position)` |

---

### B10. Technique (Technika)

**Definicja**: Ogólna jakość operowania piłką — kontrola, krzywizna podań/strzałów, woleje, flicki.

**Odpowiednik w FM**: Technique | **W FIFA**: brak bezpośrednika (rozproszone w Curve, Volleys, Ball Control)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **xG (modyfikator jakości strzału)** | Technique wpływa na jakość uderzenia — woleje, strzały z półwoleja, strzały w biegu mają bonus od technique. | `xG_volley = base_xG × technique_modifier` |
| **Free Kick Physics (Magnus, Curve)** | Zdolność do zakręcenia piłki. Technique determinuje siłę efektu Magnusa (banana kick). | `magnus_force = f(technique, spin_rate)` |
| **xPass (krzywizna podań)** | Podania z zakręceniem (za plecy obrony, podkręcone dośrodkowania) wymagają technique. | `curved_pass_accuracy = f(passing, technique)` |
| **PSxG Enhancement** | Wyższe technique = większy zakres opcji strzału (podkręcone, z efektem) = celowanie w trudniejsze dla bramkarza miejsca. | `psxg_ceiling = f(finishing, technique)` |
| **General Execution** | "Jakość dotknięcia" — modyfikator generalny na CZYSTOŚĆ wykonania dowolnej akcji technicznej. | `execution_quality = base × (1 + (technique - 10) × 0.01)` |

**Dlaczego osobny atrybut**: FM ma Technique jako pełny atrybut. Jest to "meta-techniczny" atrybut — nie determinuje CO gracz robi (to Passing, Shooting, Dribbling), ale JAK CZYSTO to robi. Gracz z finishing=15 i technique=8 strzela prosto i mocno. Z finishing=15 i technique=18 strzela z efektem, pod kątem, wolejem — szerszy arsenał.

---

## C. Atrybuty mentalne (12)

---

### C1. Vision (Wizja gry)

**Definicja**: Świadomość sytuacyjna — ile opcji podań i przestrzeni gracz "widzi" przed kontaktem z piłką.

**Odpowiednik w FM**: Vision | **W FIFA**: Vision

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Scanning Frequency** | Bazowa częstotliwość skanowania otoczenia. Vision determinuje, ile informacji gracz zbiera przed otrzymaniem piłki. | `scanning_rate = vision × position_base_rate` |
| **xReceiver (GNN)** | Ile opcji podań "widzi" zawodnik. Gracz z vision=18 analizuje 5-6 opcji, z vision=8 widzi 2-3. | `passing_options_considered = f(vision, scanning_frequency)` |
| **Progressive Passing Tendency** | Tendencja do podań progresywnych (w głąb, ryzykownych). Wysoka vision = większa szansa na "kluczowe" podanie. | `progressive_pass_prob = f(vision, risk_tolerance)` |
| **EPV Decision Quality** | Jakość decyzji o WYBORZE akcji (podanie vs drybling vs strzał). Vision pozwala wybrać opcję o najwyższym EPV. | `action_EPV = max(available_options) where options = f(vision)` |
| **Spatial Gain Post-Scanning** | Połączenie z First Touch — vision determinuje, ILE informacji gracz zebrał, first touch — jak na nie reaguje. | `spatial_gain = f(vision × first_touch)` |

**Trade-off z Passing**: Vision = "widzi genialne podanie". Passing = "potrafi je wykonać". Gracz z vision=18 i passing=8 widzi idealne podanie prostopadłe, ale nie potrafi go zagrać. Gracz z vision=8 i passing=18 doskonale wykonuje proste podania, ale nigdy nie zagra klucza.

---

### C2. Composure (Opanowanie)

**Definicja**: Zdolność do utrzymania poziomu pod presją — im wyższy composure, tym mniej atrybuty degradują się pod presją rywala.

**Odpowiednik w FM**: Composure | **W FIFA**: Composure

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Pressure Modifier (META-ATRYBUT)** | Modyfikuje WSZYSTKIE inne atrybuty w zależności od poziomu presji (Dynamic Pressure Model). | `effective_attr = base_attr × (1 - pressure_level × (1 - composure/20))` |
| **xG Under Pressure** | Strzał pod presją obrońcy/bramkarza. Niski composure = niższe xG w kluczowych momentach. | `xG_pressured = xG × composure_modifier` |
| **xPass Under Pressing** | Podanie pod presją pressingu. Niski composure = częstsze błędy w budowaniu. | `xPass_pressed = xPass × composure_modifier` |
| **Penalty (Nash Equilibrium)** | Egzekucja zamierzonego kierunku w rzucie karnym. Composure > 15: dokładna egzekucja. Composure < 8: duże odchylenie. | `penalty_accuracy = composure / 20 × base_accuracy` |
| **WPA Moments** | W kluczowych momentach (wynik bliski, końcówka meczu) presja psychiczna rośnie. Composure chroni przed "choking". | `pressure_factor = f(score_diff, minute, match_importance)` |
| **Decision Quality Under Pressure** | Nawet jeśli vision=18, pod presją gracz z composure=6 panikuje i wybiera złą opcję. | `effective_decision = decision × composure_modifier` |

**Dlaczego KLUCZOWY**: Composure to jedyny atrybut, który modyfikuje WSZYSTKIE inne. Bez niego wszyscy gracze zachowują się identycznie niezależnie od presji. A presja (Dynamic Pressure) jest centralną mechaniką gry — pressing, defensywa, kluczowe momenty.

---

### C3. Off The Ball (Ruch bez piłki — atakujący)

**Definicja**: Inteligencja ruchu ofensywnego bez piłki — znajdowanie wolnych stref, biegi za obronę, tworzenie opcji podań.

**Odpowiednik w FM**: Off The Ball | **W FIFA**: Positioning (Attack)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **OBSO (Off-Ball Scoring Opportunity)** | KLUCZOWY atrybut: jak dobrze zawodnik zajmuje strefy o wysokim OBSO — pozycje, z których mógłby strzelić, gdyby dostał piłkę. | `obso_realization = obso_value × (off_the_ball / 20)` |
| **gBRI (Goal-Based Run Impact)** | Wartość biegów bez piłki. Wyższy OTB = biegi w cenniejsze strefy = wyższy gBRI. | `gBRI = f(off_the_ball, pace, run_direction)` |
| **xReceiver (dostępność)** | Gracz z wysokim OTB częściej jest "otwarty" do odbioru podania w groźnych pozycjach. | `availability_in_danger_zone = f(off_the_ball, positioning_context)` |
| **Counter-Attack Positioning** | Przy kontrataku — jak szybko napastnik zajmuje optymalną pozycję do odbioru długiego podania. | `counter_position_quality = f(off_the_ball, pace, anticipation)` |
| **Set Piece Runs (rożne)** | Jakość biegów w polu karnym przy rożnych — trafianie w strefy GMM. | `set_piece_run_quality = f(off_the_ball, anticipation)` |

**Dlaczego osobno od Positioning**: FM rozdziela Off The Ball (ofensywny ruch) od Positioning (defensywne ustawienie). To KRYTYCZNE rozdzielenie:
- Off The Ball = "gdzie biec, żeby dostać piłkę i strzelić" (OBSO, gBRI)
- Positioning = "gdzie stać, żeby bronić formację" (Ghosting, Compactness)
- Napastnik z OTB=18 i Positioning=4 = genialne biegi w ataku, katastrofalna obrona
- Defensywny pomocnik z OTB=8 i Positioning=17 = nie biega za obronę, ale doskonale trzyma linię

---

### C4. Positioning (Ustawienie defensywne)

**Definicja**: Inteligencja pozycjonowania defensywnego — trzymanie formacji, zamykanie stref, linia obrony.

**Odpowiednik w FM**: Positioning | **W FIFA**: Defensive Awareness

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Ghosting** | KLUCZOWY atrybut: odchylenie od "idealnej" pozycji. Gracz z positioning=18 jest bliżej pozycji "ducha" (optymalnej). | `ghosting_deviation = base_deviation × (20 - positioning) / 10` |
| **Defensive Shape (Compactness, Convex Hull)** | Wkład zawodnika w zwartość zespołu. Wysoki positioning = mała komórka Voronoi w odpowiednim miejscu. | `compactness_contribution = f(positioning, teamwork)` |
| **DxT (korekta defensywna)** | Dynamiczny xT stref, które ten obrońca pokrywa, maleje proporcjonalnie do positioning (lepsza pozycja = mniej zagrożenia). | `DxT_zone_modifier = 1 - positioning / 40` |
| **Interception Positioning** | Prawdopodobieństwo, że obrońca jest w WŁAŚCIWYM miejscu do przechwycenia podania (oprócz anticipation, które jest o CZYTANIU, positioning jest o BYCIU TAM). | `intercept_position_bonus = positioning / 20` |

---

### C5. Anticipation (Antycypacja)

**Definicja**: Czytanie gry — zdolność do przewidywania następnego ruchu rywala, kierunku podania, momentu uderzenia.

**Odpowiednik w FM**: Anticipation | **W FIFA**: Interceptions

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Interception Probability** | Główna zmienna. Anticipation ≠ Tackling: tackling = "wygram walkę o piłkę", anticipation = "WYCZUJĘ, że podanie pójdzie tutaj i znajdę się tam ZANIM piłka dotrze". | `interception_prob = f(anticipation, positioning, pace)` |
| **Pressing Trap Awareness** | Zdolność do rozpoznawania i wykonywania pułapek pressingowych (celowe wystawienie opcji podania, potem zamknięcie). | `trap_execution = f(anticipation, teamwork, acceleration)` |
| **Counter-Attack Reading** | Obrońca czytający kontrę rywala — wie, kiedy się wycofać, kiedy zostać wysoko (ofsajd). | `counter_defense_positioning = f(anticipation, pace, positioning)` |
| **Set Piece Defense** | Antycypacja ruchu atakujących w polu karnym — "wyczuwanie", kto biegnie na bliski słupek. | `set_piece_defense = f(anticipation, jumping, marking_style)` |

**Dlaczego osobno od Positioning i Tackling**: Trzypoziomowa defensywa:
1. Positioning = "stoję we WŁAŚCIWYM miejscu"
2. Anticipation = "WYCZUWAM, że muszę przesunąć się w lewo za chwilę"
3. Tackling = "ODBIERAM piłkę, gdy jest blisko"
Klasyczny Busquets: pace=8, strength=8, tackling=14, ALE anticipation=19, positioning=18 → wolny i słaby, ale ZAWSZE we właściwym miejscu, ZAWSZE wyczuwa podanie.

---

### C6. Decisions (Decyzje)

**Definicja**: Szybkość i trafność podejmowania decyzji — jak szybko gracz wybiera WŁAŚCIWĄ akcję po zebraniu informacji.

**Odpowiednik w FM**: Decisions | **W FIFA**: Reactions

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **EPV Decision Optimization** | Po zebraniu opcji (Vision) — Decisions determinuje, czy gracz wybiera opcję o NAJWYŻSZYM EPV. Niska decisions = wybiera suboptymalne podanie/strzał. | `chosen_EPV = max_EPV × (decisions / 20) + random_EPV × (1 - decisions/20)` |
| **Action Execution Speed** | Czas od przyjęcia piłki do akcji. Szybkie decisions = natychmiastowe podanie/strzał. Wolne = opóźnienie → presja rośnie → spadek composure_effect. | `action_delay = base_delay × (20 - decisions) / 10` |
| **Counter-Attack Tempo** | W kontrze liczy się szybkość decyzji — sekundowe wahanie daje obronie czas na ustawienie (DxT rośnie dla obrony). | `counter_efficiency = f(decisions, first_touch, pace)` |
| **Pressing Decision** | Kiedy pressować, kiedy odpuścić, kogo zamknąć. Złe decisions w pressingu = otwieranie przestrzeni rywalom. | `pressing_decision_quality = f(decisions, anticipation, teamwork)` |

**Dlaczego osobno od Vision**: Vision = "ILE widzisz". Decisions = "jak szybko i trafnie REAGUJESZ na to, co widzisz". Gracz z vision=18 i decisions=8 widzi genialne opcje, ale zastanawia się sekundę za długo i okno się zamyka. Gracz z vision=8 i decisions=18 widzi mniej, ale błyskawicznie reaguje na to, co widzi.

---

### C7. Work Rate (Etyka pracy)

**Definicja**: Gotowość do biegania i wysiłku bez piłki — jak chętnie gracz uczestniczy w pressingu, wracaniu do obrony, biegach wspierających.

**Odpowiednik w FM**: Work Rate | **W FIFA**: brak bezpośrednika (częściowo w Stamina/Aggression)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **PPDA Participation** | Ile graczy FAKTYCZNIE angażuje się w pressing. Work Rate determinuje, czy gracz bierze udział. | `pressing_participation = work_rate > threshold` |
| **Distance Covered** | Dystans pokonany per mecz. Work Rate = CHĘĆ biegania (mentalność). Stamina = ZDOLNOŚĆ biegania (fizyka). | `distance = f(work_rate × stamina)` |
| **Counter-Pressing Engagement** | Czy gracz natychmiast reaguje po stracie (jak Gegenpressing wymaga), czy odpuszcza. | `counterpress_engage = work_rate > 12` |
| **Defensive Transitions** | Wracanie do obrony po ataku. Gracz z work_rate=6 "spaceruje" wracając — tworzy lukę w formacji. | `recovery_run_probability = f(work_rate, team_tactic)` |
| **Metabolic Power Expenditure** | Work Rate wpływa na to, ile energii gracz WYDAJE per minutę. Wysoki WR = więcej biegania = szybsze zużycie staminy. | `energy_expenditure_rate = work_rate × base_rate` |

**Trade-off ze Stamina**: WR=18, Stamina=8 → biegnie jak opętany przez 50 minut, potem pada. WR=6, Stamina=18 → stały, pasywny 90 minut. WR=16, Stamina=16 → idealny box-to-box midfielder.

---

### C8. Aggression (Agresja)

**Definicja**: Intensywność w fizycznych starciach, wchodzeniu w walki, inicjowaniu kontaktu.

**Odpowiednik w FM**: Aggression | **W FIFA**: Aggression

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Pressing Trigger** | Czy gracz INICJUJE pressing, gdy piłka jest w jego strefie. Wysoka aggression = natychmiastowa reakcja. Niska = czeka. | `press_trigger_probability = f(aggression, tactical_instruction)` |
| **Dynamic Pressure p_i (bonus)** | Agresywny pressing jest INTENSYWNIEJSZY — wyższa aggression podnosi p_i (większa szansa na odbiór), ale... | `p_i_aggressive = p_i × (1 + aggression × 0.02)` |
| **Foul Probability** | ...zwiększa ryzyko faulu. Aggression/Tackling ratio determinuje, czy próba odbioru jest czysta. | `foul_risk = (aggression / tackling) × contact_intensity` |
| **Card Risk (żółte/czerwone)** | Faul w strefie niebezpiecznej + wysoka aggression = wyższe ryzyko kartki. | `card_risk = foul_risk × aggression_factor × zone_factor` |
| **Physical Duel Intensity** | Siła wchodzenia w kontakt fizyczny. Wysoka aggression = silniejsze starcia = wyższe IWP fizyczne ale wyższe ryzyko faulu. | `duel_intensity = f(aggression, strength)` |

**Taktyczny trade-off**: Agresywny pressing jest SKUTECZNIEJSZY (niższe PPDA), ale RYZYKOWNIEJSZY (faule, kartki, rzuty wolne generujące ~10% bramek).

---

### C9. Concentration (Koncentracja)

**Definicja**: Zdolność do utrzymania stałego poziomu uwagi i wydajności przez 90 minut bez "lapsusów".

**Odpowiednik w FM**: Concentration | **W FIFA**: brak bezpośrednika

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Lapse Probability** | Szansa na "lapsus" — moment, gdy gracz odchodzi od formacji, gubi pozycję, nie śledzi rywala. Niska concentration = losowe momenty obniżonej wydajności. | `lapse_prob_per_event = (20 - concentration) × 0.003` |
| **Ghosting Consistency** | Concentration wpływa na STABILNOŚĆ ghosting deviation. Gracz z positioning=16 ale concentration=6 → zwykle dobrze, ale raz na 10 minut "zasypia" i deviation skacze. | `ghosting_variance = f(1 / concentration)` |
| **Late-Game Performance** | Utrzymanie poziomu w końcówce meczu (80-90+ min). Niska concentration = spadek po 75 minucie (nie fizyczny, MENTALNY). | `late_game_modifier = concentration / 20 (for mins > 75)` |
| **Set Piece Alertness** | Reakcja na krótko rozegrany stały fragment — niska concentration = "zasypia" na stałym fragmencie. | `set_piece_alert = f(concentration, anticipation)` |

**Dlaczego osobno od Composure**: Composure = "jak dobrze grasz POD PRESJĄ". Concentration = "jak STABILNY jesteś PRZEZ CAŁY MECZ". Gracz z composure=18 i concentration=6 → rewelacyjny w kluczowych momentach, ale raz na 10 minut robi absurdalny błąd z niczego. I odwrotnie.

---

### C10. Teamwork (Praca zespołowa)

**Definicja**: Zdolność i chęć do wykonywania instrukcji taktycznych trenera — granie w ramach systemu.

**Odpowiednik w FM**: Teamwork | **W FIFA**: brak bezpośrednika

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **GamePlan Execution Fidelity** | KLUCZOWE: Teamwork determinuje, NA ILE gracz realizuje taktykę ustaloną przez gracza (trenera). Niski teamwork = gracz "improwizuje" zamiast wykonywać plan. | `instruction_compliance = teamwork / 20` |
| **Ghosting (system deviation)** | Ghosting mierzy odchylenie od optymalnej pozycji. Teamwork wpływa na to, czy gracz W OGÓLE PRÓBUJE trzymać się systemu. | `system_adherence = f(teamwork, positioning)` |
| **Passing Network (structural)** | Teamwork wpływa na to, czy gracz gra do partnerów sugerowanych przez taktykę (np. "graj przez środek") czy według własnego uznania. | `tactical_pass_prob = f(teamwork, tactical_instruction)` |
| **Pressing Coordination** | Czy gracz koordynuje pressing z kolegami (zamykanie stref jednocześnie) czy pressuje samodzielnie (otwieranie luk). | `press_coordination = f(teamwork, anticipation)` |
| **Defensive Shape (Compactness)** | Wkład w zwartość zespołu — gracz z teamwork=18 trzyma linię, z teamwork=6 "gubi się". | `shape_contribution = f(teamwork, positioning, concentration)` |

**Dlaczego KLUCZOWY dla gry**: W naszej grze CAŁA MECHANIKA polega na tym, że trener ustala taktykę, a zawodnicy ją wykonują. Teamwork = "jak dokładnie Twoi wirtualni gracze słuchają Twoich instrukcji". Bez tego atrybutu gracz nie ma kontroli nad realizacją swoich planów.

---

### C11. Bravery (Odwaga)

**Definicja**: Gotowość do podejmowania ryzykownych fizycznie akcji — blokowanie strzałów, walki w powietrzu w zatłoczonym polu karnym, wchodzenie pod lecącą piłkę.

**Odpowiednik w FM**: Bravery | **W FIFA**: brak bezpośrednika (częściowo w Aggression)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **Shot Blocking Probability** | Prawdopodobieństwo, że obrońca postawi blok na strzale rywala — wymaga fizycznego narażenia się. | `block_prob = f(bravery, positioning, anticipation)` |
| **Aerial Engagement (box)** | Czy gracz wejdzie w walkę w powietrzu w zatłoczonym polu karnym (ryzyko kontuzji, łokcie, kolana). | `aerial_box_engagement = bravery > threshold` |
| **xG Reduction (defensive)** | Strzał z Angular Pressure: bravery obrońców determinuje, ilu z nich FAKTYCZNIE stanie w stożku strzału. | `angular_pressure_bodies = count(defenders where bravery > 10)` |
| **Tackle Commitment** | Czy obrońca wchodzi w trudny odbiór (ryzyko kontuzji, kartki) w krytycznym momencie. | `tackle_commit = f(bravery, aggression, match_context)` |

**Dlaczego osobno od Aggression**: Aggression = INTENSYWNOŚĆ (jak mocno wchodzi). Bravery = GOTOWOŚĆ do narażenia się (czy w ogóle wchodzi). Agresywny ale tchórzliwy gracz: mocno pressuje na otwartej przestrzeni, ale unika blokowania strzałów. Odważny ale nieagresywny: blokuje strzały, ale nie inicjuje pressingu.

---

### C12. Flair (Fantazja)

**Definicja**: Kreatywność i nieprzewidywalność — zdolność do niestandardowych rozwiązań (rabony, no-look pass, elastico).

**Odpowiednik w FM**: Flair | **W FIFA**: brak (rozproszone w Dribbling, Vision)

**Algorytmy i połączenia**:

| Algorytm | Rola atrybutu | Formuła/mechanika |
|----------|---------------|-------------------|
| **IWP Variance (drybling)** | Flair dodaje WARIANCJĘ do wyniku dryblingów. Wysoki flair = czasem genialne, czasem katastrofalne. Niski = przewidywalny. | `IWP_flair = IWP_base + flair_variance × random(-1, 1)` |
| **xPass (kreatywne podania)** | Podania, które "nie powinny dojść" ale dochodzą — no-look, backheel, rabona. Flair otwiera opcje podań niedostępne dla "zwykłych" graczy. | `creative_pass_option = flair > 14` |
| **EPV (niestandardowe decyzje)** | Flair pozwala wybrać akcję POZA standardowym EPV maximum — czasem "genialna głupota" daje efekt, którego rywal nie przewidział. | `surprise_factor = f(flair, context)` |
| **Opponent Model Disruption** | Rywale analizujący Motif Analysis / Passing Networks nie mogą przewidzieć gracza z flair — jego akcje nie pasują do wzorców. | `predictability_reduction = flair / 20` |

**Taktyczny trade-off**: Flair=18 w zdyscyplinowanej tiki-taka = problematyczny (nie gra "systemowo"). Flair=18 w roli wolnej "10-ki" = genialny (kreatywność jest pożądana). Trener musi zdecydować: czy daje mu wolność (instrukcja "roam from position") czy dyscyplinuje (instruction "hold position").

---

## D. Atrybuty bramkarza (6)

Bramkarz używa INNEGO zestawu atrybutów technicznych. Atrybuty mentalne i fizyczne są wspólne z polowymi.

---

### D1. Reflexes (Refleks)

**Algorytmy**: PSxG (główna zmienna obrony), xG conversion (GK modifier), shot stopping probability.
```
save_probability = f(reflexes, shot_speed, shot_placement, gk_positioning)
```

### D2. GK Positioning (Ustawienie bramkarza)

**Algorytmy**: xG Next-Gen (Goalkeeper Distance — odległość od optymalnej pozycji), Angular Pressure (zasłanianie kątów).
```
gk_optimal_distance = f(gk_positioning, shot_location)
xG_modifier = f(gk_distance_from_optimal)
```

### D3. Handling (Chwytanie)

**Algorytmy**: Czy bramkarz ŁAPIE piłkę (koniec akcji) czy ODBIJA (kontynuacja — dobijanka). Handling determinuje "czystość" interwencji.
```
catch_vs_parry = f(handling, shot_power)
second_ball_danger = 1 - handling / 20
```

### D4. Distribution (Dystrybucja)

**Algorytmy**: xPass dla wyrzutów/wykopów bramkarza, Counter-Attack Initiation (czy GK może rozpocząć kontrę precyzyjnym podaniem).
```
gk_xPass = f(distribution, distance, target_availability)
```

### D5. Command of Area (Kontrola pola karnego)

**Algorytmy**: Set Pieces defense (wychodzenie na dośrodkowania), claiming crosses probability.
```
claim_cross_prob = f(command_of_area, jumping, bravery, aerial_congestion)
```

### D6. One on Ones (Gra 1 na 1)

**Algorytmy**: IWP bramkarz vs napastnik w sytuacji sam na sam. Oddzielna od Reflexes — inne umiejętności (wychodzenie, spread, timing).
```
IWP_1v1_gk = f(one_on_ones, pace, bravery, anticipation)
```

---

## E. Cechy dodatkowe (Traits)

Traits to binarne lub małoskalowe (1-5) modyfikatory dla niszowych sytuacji, które nie uzasadniają pełnego atrybutu 1-20.

| Trait | Typ | Algorytm | Wpływ |
|-------|-----|----------|-------|
| **Free Kick Specialist** | 1-5 | Free Kick Physics (Magnus, ballistics) | Modyfikuje jakość strzałów bezpośrednich z rzutów wolnych |
| **Penalty Taker** | 1-5 | Nash Equilibrium (penalty) | Modyfikuje celność i spokój przy rzutach karnych |
| **Long Throw** | Boolean | Set Pieces (wrzut z autu) | Umożliwia long throw jako quasi-rożny |
| **Leadership** | 1-5 | Team Morale, nearby composure boost | Podnosi composure bliskich zawodników o +1/+2 |
| **Injury Prone** | 1-5 | ACWR Injury Model | Podnosi base_risk w modelu kontuzji |
| **Clutch Performer** | Boolean | WPA high-leverage moments | +2 composure w momentach o WPA > 0.15 |
| **Versatility** | Pozycje | Hungarian Algorithm | Ile pozycji gracz może grać bez kary |
| **Preferred Foot** | L/R/Both | xG/xPass angle modifier | Strzały/podania słabszą nogą mają penalty |
| **Weak Foot Quality** | 1-5 | xG/xPass weak foot | Jak mały jest penalty za słabszą nogę |

---

## F. Parametry fizyczne (nie-atrybuty)

To cechy ciała, NIE umiejętności — nie trenuje się ich, nie rozwijają się.

| Parametr | Typ | Algorytm | Wpływ |
|----------|-----|----------|-------|
| **Height** | cm | IWP aerial (reach), GK (reach), Set Pieces | Wyższy = lepsza gra w powietrzu, większy reach |
| **Weight** | kg | Metabolic Power (koszt energii), IWP physical (inercja) | Cięższy = więcej energii na sprint, trudniej go odepchnąć |
| **Age** | lata | Natural Fitness recovery (maleje z wiekiem), Development | Młodsi regenerują szybciej, starsi mają niższy ceiling |

---

## G. Macierz: Algorytm × Atrybut

Tabela pokazująca, które atrybuty wchodzą do którego algorytmu. **●** = główne wejście, **○** = modyfikator.

| Algorytm | Pac | Acc | Agi | Sta | Str | Jmp | NF | Bal | Fin | LS | SP | LP | Crs | Dri | FT | Hea | Tac | Tec | Vis | Com | OTB | Pos | Ant | Dec | WR | Agg | Con | TW | Bra | Fla |
|----------|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|
| Pitch Control | ● | ● | ○ | | | | | | | | | | | | | | | | | | | | | | | | | | | |
| Dynamic Pressure | ○ | ● | | | | | | | | | | | | | | | ● | | | | | | | | ○ | ○ | | | | |
| xG Next-Gen | | | | | | | | | ● | ● | | | | | | ● | | ○ | | ○ | | | | | | | | | | |
| PSxG | | | | | | | | | ● | | | | | | | | | ● | | | | | | | | | | | | |
| xPass | | | | | | | | | | | ● | ● | ● | | | | | ○ | ● | ○ | | | | | | | | | | |
| xT / DxT | | | | | | | | | | | ● | ● | | ● | | | | | ● | | | | | ○ | | | | | | |
| VAEP | | | | | | | | | | | | | | | | | | | | | | | | | | | | | | |
| EPV | | | | | | | | | | | | | | | | | | | ● | | | | | ● | | | | | | |
| IWP drybling | ○ | | ● | | | | | ○ | | | | | | ● | | | ● | | | ○ | | | | | | | | | | ○ |
| IWP aerial | | | | | ● | ● | | | | | | | | | | ● | | | | | | | | | | | | | ○ | |
| IWP physical | | | | | ● | | | ● | | | | | | | | | | | | | | | | | | | | | | |
| IWP sprint | ● | ● | | | | | | | | | | | | | | | | | | | | | | | | | | | | |
| PPDA | ● | ● | | ● | | | | | | | | | | | | | ● | | | | | | ○ | | ● | ● | | | | |
| Counter-Press | | ● | | | | | | | | | | | | | | | ● | | | | | | | | ● | | | | | |
| Ghosting | | | | | | | | | | | | | | | | | | | | | | ● | | | | | ● | ● | | |
| Passing Network | | | | | | | | | | | ● | ○ | | | | | | | ● | | | | | | | | | ○ | | |
| OBSO | ● | | | | | | | | | | | | | | | | | | | | ● | | ○ | | | | | | | |
| gBRI | ● | ○ | | | | | | | | | | | | | | | | | | | ● | | | | | | | | | |
| Set Pieces (offense) | | | | | ○ | ● | | | ○ | | | | ● | | | ● | | ○ | | ○ | ● | | ○ | | | | | | ○ | |
| Set Pieces (defense) | | | | | ● | ● | | | | | | | | | | ● | | | | | | ● | ● | | | | ○ | | ● | |
| Metabolic Power | ● | ● | | ● | | | ● | | | | | | | | | | | | | | | | | | ● | | | | | |
| ACWR / Injury | | | | ● | | | ● | | | | | | | | | | | | | | | | | | | | | | | |
| WPA / Bayesian | | | | | | | | | | | | | | | | | | | | ● | | | | | | | | | | |
| Nash (penalties) | | | | | | | | | ● | | | | | | | | | | | ● | | | | | | | | | | |
| Scanning | | | | | | | | | | | | | | | ○ | | | | ● | | | | | ○ | | | ○ | | | |
| Spatial Gain | | | | | | | | | | | | | | | ● | | | | ● | | | | | | | | | | | |
| Fatigue Decay | ● | ● | | ● | | | ● | | | | | | | | | | | | ○ | ○ | | | | | ● | | ○ | | | |
| GamePlan Fidelity | | | | | | | | | | | | | | | | | | | | | | ○ | | | | | | ● | | ○ |

*VAEP nie ma bezpośrednich wejść atrybutowych — jest metryką WYNIKOWĄ obliczaną na podstawie wyników xG, xPass, xT.*

---

## H. Porównanie z FM i FIFA

### Atrybuty z FM, które MAMY (28/38):
Pace, Acceleration, Agility, Stamina, Strength, Jumping, Natural Fitness, Balance, Finishing, Long Shots, Crossing, Dribbling, First Touch, Heading, Tackling, Technique, Vision, Composure, Off The Ball, Positioning, Anticipation, Decisions, Work Rate, Aggression, Concentration, Teamwork, Bravery, Flair

### Atrybuty z FM, które POMINĘLIŚMY (10/38) i dlaczego:
| Atrybut FM | Dlaczego pominięty | Gdzie się znalazł |
|------------|-------------------|-------------------|
| Passing | Rozdzielony na Short Passing + Long Passing (ważniejsze taktycznie) | B3 + B4 |
| Corners | Zbyt niszowy na pełny atrybut (<3% akcji) | Trait: Free Kick Specialist + Crossing |
| Free Kicks | Zbyt niszowy (<2% akcji, rzuty wolne) | Trait: Free Kick Specialist |
| Penalties | Ultra-niszowy (<0.5% meczów) | Trait: Penalty Taker |
| Long Throws | Ultra-niszowy | Trait: Long Throw |
| Marking | Pochodna Positioning + Anticipation + Teamwork | C4 + C5 + C10 |
| Determination | Rozmyty koncept, trudno algorytmizować | Częściowo w Composure (presja przegrywania) |
| Leadership | Trudno algorytmizować | Trait: Leadership |

### Atrybuty z FIFA, które MAMY ale FM nie ma:
Żaden — FM jest bardziej szczegółowe od FIFA.

### Nasze UNIKALNE podejście vs FM i FIFA:
| Nasza innowacja | FM | FIFA | Dlaczego lepiej |
|----------------|-----|------|----------------|
| Short/Long Passing rozdzielone | Jedno "Passing" + osobne "Long Passing" (niejasne co Short) | Short Passing + Long Passing | Czyste rozdzielenie z algorytmicznym uzasadnieniem (xPass_short vs xPass_long) |
| Każdy atrybut → konkretny algorytm | Atrybuty wpływają na "ukryty" match engine | Atrybuty wpływają na "ukryty" gameplay engine | Pełna transparentność: gracz widzi DOKŁADNIE jak atrybut wpływa na wynik |
| Traits zamiast niszowych atrybutów | 38 pełnych atrybutów (w tym niszowe jak Corners) | ~34 pełnych atrybutów | Mniej szumu, każdy pełny atrybut jest ISTOTNY |

---

## Finalna statystyka

```
Zawodnik polowy:  30 atrybutów (8 fizycznych + 10 technicznych + 12 mentalnych)
Bramkarz:         24 atrybutów (8 fizycznych + 6 bramkarskich  + 12 mentalnych - 2 nieużywane*)
Traits:           9 modyfikatorów
Parametry ciała:  3 (wzrost, waga, wiek)

* Bramkarz nie używa: Off The Ball, Flair (zamienione na GK-specific)
```

Każdy z 30 atrybutów jest **bezpośrednim wejściem do co najmniej 2 algorytmów** silnika meczu, tworzy **unikalny trade-off** z innymi atrybutami i generuje **sensowną decyzję taktyczną** dla gracza.
