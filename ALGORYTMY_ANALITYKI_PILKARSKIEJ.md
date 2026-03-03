# Encyklopedia Algorytmów Analityki Piłkarskiej

## Kompleksowe kompendium algorytmów, modeli i metodologii stosowanych w zaawansowanej analizie taktycznej piłki nożnej

**Autor roli**: Analityk danych / Architekt Scala / Ekspert od danych w futbolu  
**Data**: Luty 2026

---

## Spis treści

1. [Modele Wartościowania Strzałów i Bramek](#1-modele-wartościowania-strzałów-i-bramek)
2. [Modele Wartościowania Akcji i Posiadania](#2-modele-wartościowania-akcji-i-posiadania)
3. [Modele Kontroli Przestrzeni](#3-modele-kontroli-przestrzeni)
4. [Analiza Pressingu i Defensywy](#4-analiza-pressingu-i-defensywy)
5. [Analiza Sieciowa i Grafowa](#5-analiza-sieciowa-i-grafowa)
6. [Modele Predykcji Wyników](#6-modele-predykcji-wyników)
7. [Systemy Rankingowe i Siła Zespołów](#7-systemy-rankingowe-i-siła-zespołów)
8. [Analiza Stałych Fragmentów Gry](#8-analiza-stałych-fragmentów-gry)
9. [Analiza Starć Bezpośrednich i Profilowanie Zawodników](#9-analiza-starć-bezpośrednich-i-profilowanie-zawodników)
10. [Analiza Tranzycji i Kontrataków](#10-analiza-tranzycji-i-kontrataków)
11. [Kształt Zespołu i Metryki Geometryczne](#11-kształt-zespołu-i-metryki-geometryczne)
12. [Optymalizacja Formacji i Składu](#12-optymalizacja-formacji-i-składu)
13. [Analityka Kognitywna i Percepcyjna](#13-analityka-kognitywna-i-percepcyjna)
14. [Modele Fizyczne i Obciążeniowe](#14-modele-fizyczne-i-obciążeniowe)
15. [Silniki Machine Learning i Architektury](#15-silniki-machine-learning-i-architektury)
16. [Teoria Gier w Piłce Nożnej](#16-teoria-gier-w-piłce-nożnej)

---

## 1. Modele Wartościowania Strzałów i Bramek

### 1.1 Expected Goals (xG) — Model Podstawowy

**Czym jest**: Model prawdopodobieństwa, który każdemu strzałowi przypisuje wartość od 0 do 1, reprezentującą szansę na zdobycie bramki z danej pozycji i w danym kontekście.

**Matematyka bazowa**:
- Odległość euklidesowa od bramki: `d = √((x_goal - x_shot)² + (y_goal - y_shot)²)`
- Kąt strzału: kąt zawarty między liniami łączącymi punkt strzału ze słupkami bramki
- Im bliżej bramki i im większy kąt — tym wyższe xG

**Zmienne wejściowe**:
| Zmienna | Opis | Wpływ |
|---------|------|-------|
| Odległość euklidesowa | Odległość od środka bramki | Odwrotnie proporcjonalny do xG |
| Kąt strzału | "Światło bramki" widziane z punktu strzału | Wyższy kąt = wyższe xG |
| Część ciała | Głowa, prawa/lewa noga | Strzały głową mają niższe xG |
| Typ akcji poprzedzającej | Podanie prostopadłe, dośrodkowanie, stały fragment | Kontekst modyfikuje prawdopodobieństwo |
| Typ ataku | Kontratak vs. atak pozycyjny | Kontrataki generują wyższe xG |

**Algorytm**: Najczęściej regresja logistyczna lub drzewiaste modele gradientowe (XGBoost, LightGBM). Model trenowany jest na dziesiątkach tysięcy historycznych strzałów z etykietą binową (gol/nie-gol).

---

### 1.2 Expected Goals Next-Gen (xG z danymi tracking)

**Czym jest**: Zaawansowana wersja xG, która wykracza poza statyczne cechy strzału i uwzględnia dynamiczny kontekst z danych pozycjonujących (tracking data) — pozycje wszystkich 22 zawodników w momencie strzału.

**Dodatkowe zmienne**:
- **Presja kątowa (Angular Pressure)**: Liczba obrońców znajdujących się w stożku widzenia między strzałem a bramką. Im więcej obrońców blokuje linię strzału, tym niższe xG.
- **Goalkeeper Distance**: Odległość bramkarza od optymalnej pozycji w momencie strzału. Bramkarz poza pozycją dramatycznie zwiększa xG.
- **Defender proximity**: Odległość najbliższego obrońcy do strzelajacego — bliski obrońca obniża xG przez presję fizyczną i psychiczną.

**Interpretowalność — SHAP Values**:
SHAP (SHapley Additive exPlanations) wywodzi się z teorii gier (wartości Shapleya). Dla każdej predykcji xG model pokazuje, **które cechy i w jakim stopniu** wpłynęły na przypisaną wartość. Np. "ten strzał miał xG = 0.35, z czego +0.15 wynikało z bliskiej odległości, -0.08 z presji obrońcy, +0.06 z błędnej pozycji bramkarza".

---

### 1.3 Post-Shot Expected Goals (PSxG)

**Czym jest**: Rozszerzenie xG, które uwzględnia **jakość samego strzału** — jego placement (gdzie piłka została skierowana w obrębie bramki). Obliczane dopiero po oddaniu strzału.

**Zastosowanie**: Służy do oceny bramkarzy. Różnica między PSxG a faktycznie straconymi golami (PSxG - GA) mówi, o ile bramkarz jest lepszy/gorszy od oczekiwań. Bramkarz z PSxG-GA = +5 obronił 5 goli więcej niż "powinien".

**Algorytm**: Model trenowany na strzałach celnych — zmienne to współrzędne (x, y) trafienia piłki w płaszczyznę bramki, prędkość strzału, odległość bramkarza.

---

## 2. Modele Wartościowania Akcji i Posiadania

### 2.1 Expected Threat (xT)

**Czym jest**: Model oparty na łańcuchach Markowa, który ocenia wartość **przesunięcia piłki** z jednej strefy boiska do drugiej, mierząc o ile wzrasta prawdopodobieństwo zdobycia bramki.

**Kluczowa idea**: Tradycyjne xG ocenia tylko strzały (~1% akcji). xT ocenia **każde przesunięcie piłki** — podanie, drybling, niesienie piłki — przez pryzmat tego, jak zmienia ono szanse na bramkę.

**Matematyka**:

```
xT(z) = (S_z × g_z) + (M_z × Σ_z' T(z→z') × xT(z'))
```

Gdzie:
- `S_z` = prawdopodobieństwo oddania strzału ze strefy z
- `g_z` = prawdopodobieństwo gola ze strzału w strefie z  
- `M_z` = prawdopodobieństwo ruchu piłki (podania/dryblingu) ze strefy z
- `T(z→z')` = macierz przejść — prawdopodobieństwo, że piłka trafi ze strefy z do z'

**Jak się oblicza**:
1. Boisko dzieli się na siatkę stref (typowo 12×8 = 96 stref)
2. Z historycznych danych oblicza się macierz przejść między strefami
3. Metodą iteracyjną (value iteration) oblicza się wartość każdej strefy
4. Wartość akcji = xT(strefa_docelowa) - xT(strefa_źródłowa)

**Ograniczenie**: Model jest **statyczny** — wartości stref nie zależą od aktualnego ustawienia zawodników.

---

### 2.2 Dynamic Expected Threat (DxT)

**Czym jest**: Rozwinięcie xT, które koryguje statyczne wartości stref w oparciu o **aktualne pozycje wszystkich zawodników**.

**Kluczowa innowacja**: Tradycyjny xT może przypisywać stałą wartość strefie przed polem karnym. DxT zauważa, że jeśli trzech obrońców blokuje tę strefę, jej realne zagrożenie maleje, a zagrożenie w słabiej obsadzonych bocznych sektorach rośnie.

**Dane wejściowe**: Wymaga danych tracking (pozycje 22 zawodników + piłki w czasie rzeczywistym).

---

### 2.3 VAEP — Valuing Actions by Estimating Probabilities

**Czym jest**: Najbardziej kompleksowy framework do wyceny **każdej akcji** na boisku — ofensywnej i defensywnej. Ocenia zmianę prawdopodobieństwa zdobycia i utraty bramki po każdej akcji.

**Matematyka**:

```
V(a_i) = ΔP_scores(a_i) - ΔP_concedes(a_i)
```

Gdzie:
- `ΔP_scores(a_i)` = zmiana prawdopodobieństwa zdobycia bramki wynikająca z akcji a_i
- `ΔP_concedes(a_i)` = zmiana prawdopodobieństwa utraty bramki wynikająca z akcji a_i

**Kontekst**: Model uwzględnia:
- Sekwencję ostatnich 3 akcji (kontekst budowania ataku)
- Czas do końca meczu (akcja defensywna w 90. minucie przy +1 jest cenniejsza)
- Aktualny wynik (gol na 6:0 ma mniejszą wartość niż na 1:1)
- Pozycje na boisku i typ akcji

**Algorytm**: Dwa niezależne klasyfikatory (najczęściej XGBoost):
1. Model P_scores: przewiduje prawdopodobieństwo, że w ciągu następnych 10 akcji padnie bramka dla zespołu posiadającego
2. Model P_concedes: przewiduje prawdopodobieństwo straty bramki w tym samym oknie

**Reprezentacja danych — SPADL**:
VAEP używa ustandaryzowanego formatu akcji SPADL (Soccer Player Action Description Language), który konwertuje dane zdarzeń od różnych dostawców (StatsBomb, Opta, Wyscout) do wspólnej reprezentacji. Każda akcja jest opisana jako krotka: (typ, wynik, pozycja_start, pozycja_end, część_ciała, czas).

---

### 2.4 Intent-VAEP (I-VAEP)

**Czym jest**: Wariant VAEP, który stara się oddzielić **intencję zawodnika** od **faktycznego wyniku** akcji.

**Problem**: Standardowy VAEP ocenia to, co się faktycznie wydarzyło. Ale podanie, które przypadkowo odbija się od obrońcy i trafia do partnera w doskonałej pozycji, dostaje wysoką ocenę, choć intencja mogła być zupełnie inna.

**Rozwiązanie**: I-VAEP trenuje model na "zamierzonej" lokalizacji docelowej (estymowanej z kontekstu: pozycji ciała, kierunku biegu) zamiast na faktycznej. Pozwala to na lepszą ocenę procesu decyzyjnego.

---

### 2.5 Expected Possession Value (EPV)

**Czym jest**: Framework opracowany przez Javiera Fernándeza (FC Barcelona Innovation Hub), który szacuje prawdopodobieństwo zdobycia bramki **w każdym momencie posiadania piłki**, uwzględniając pozycje wszystkich 22 zawodników.

**Kluczowa innowacja — dekompozycja**: EPV rozkłada wartość posiadania na komponenty:
- **Wartość podania**: Jak zmieni się EPV, jeśli piłka zostanie zagrana w dane miejsce?
- **Wartość niesienia piłki (ball drive)**: Jak zmieni się EPV przy dryblingu w danym kierunku?
- **Wartość strzału**: Standardowe xG w kontekście posiadania

**SoccerMap**: Architektura deep learning, która generuje wizualne "mapy cieplne" prawdopodobieństwa na boisku — trener widzi, które obszary boiska są najcenniejsze do zagrania piłki w danym momencie.

**Architektura**: Głęboka sieć neuronowa trenowana na danych tracking z pełnymi pozycjami 22 graczy + piłki.

---

### 2.6 xGChain i xGBuildup

**Czym jest**: Metryki opracowane przez StatsBomb, które przypisują wartość xG strzału **wszystkim zawodnikom uczestniczącym w budowaniu akcji**.

**xGChain**: Każdy zawodnik, który dotknął piłkę w sekwencji zakończonej strzałem, otrzymuje pełną wartość xG tego strzału. Np. jeśli akcja kończy się strzałem o xG = 0.25, wszyscy uczestnicy dostają 0.25.

**xGBuildup**: To samo co xGChain, ale **bez dwóch ostatnich kontaktów** (kluczowe podanie i strzał). Izoluje wkład zawodników budujących atak z głębi — rozgrywających, środkowych obrońców wyprowadzających piłkę, wahadłowych.

**Zastosowanie**: Pozwala docenić graczy, którzy nigdy nie notują asyst, ale systematycznie inicjują groźne ataki (np. Rodri, Jorginho, van Dijk).

---

### 2.7 On-Ball Value (OBV) i Possession Value (PV)

**Czym jest**: Alternatywne frameworki wartościowania akcji (StatsBomb), które mierzą wpływ każdego kontaktu z piłką na szanse drużyny.

**OBV**: Przypisuje wartość każdemu zdarzeniu on-ball (podanie, drybling, strzał, odbiór) jako zmianę prawdopodobieństwa zdobycia bramki. Podobna koncepcja do VAEP, ale z inną implementacją techniczną.

**PV**: Skupia się na wartości posiadania piłki w danym punkcie boiska i czasie, integrując zarówno ofensywne jak i defensywne aspekty.

---

### 2.8 Goal-Based Run Impact (gBRI) i Tortuosity

**Czym jest**: Model oceniający wartość **biegów bez piłki** — jak ruchy zawodników z dala od piłki otwierają przestrzeń i zwiększają szanse zespołu.

**Tortuosity (Krętość biegu)**:
Mierzy krzywizną ścieżki zawodnika — stosunek długości faktycznej ścieżki biegu do odległości prostoliniowej między punktem startowym a końcowym.

```
Tortuosity = Długość_faktyczna_biegu / Odległość_prostoliniowa
```

- Tortuosity = 1.0 → bieg idealnie prosty
- Tortuosity > 1.0 → bieg kręty, ze zmianami kierunku

**Odkrycie**: Istnieje **negatywna korelacja** między wysoką krętością a wartością biegu. Najcenniejsze biegi to te o:
- Wysokiej intensywności (sprint)
- Liniowej charakterystyce (niska tortuosity)
- Agresywnym atakowaniu wolnych stref za plecami obrońców

**Algorytm gBRI**: Łączy dane tracking z modelami prawdopodobieństwa gola — dla każdego biegu oblicza, o ile zmienił się EPV zespołu w wyniku ruchu zawodnika bez piłki.

---

## 3. Modele Kontroli Przestrzeni

### 3.1 Pitch Control (Spearman)

**Czym jest**: Model definiujący **prawdopodobieństwo**, że dany zespół przejmie kontrolę nad piłką w dowolnym punkcie boiska, jeśli zostałaby ona tam zagrana w danym momencie. Opracowany przez Williama Spearmana.

**Fizyka modelu**:
Model traktuje każdego zawodnika jako obiekt fizyczny z parametrami:
- Aktualna pozycja (x, y)
- Aktualny wektor prędkości (vx, vy)
- Maksymalna prędkość
- Maksymalne przyspieszenie
- Czas reakcji

**Time-to-intercept**: Kluczowe obliczenie — ile czasu potrzebuje zawodnik, aby dotrzeć do danego punktu, uwzględniając jego aktualny ruch (może "przechwycić" piłkę szybciej, jeśli już biegnie w jej kierunku).

**Funkcja prawdopodobieństwa**:

```
P(intercept) = 1 / (1 + exp(-π/(√3·σ) · (T - T_intercept)))
```

Gdzie:
- `σ` = parametr wariancji czasu (~0.45s) — uwzględnia niepewność
- `T` = czas lotu piłki do danego punktu
- `T_intercept` = czas dotarcia zawodnika do punktu

**Wynik**: Dla każdego punktu boiska — prawdopodobieństwo od 0 do 1, że zespół A kontroluje ten punkt. Generuje "mapę cieplną" kontroli boiska.

**Zastosowanie w Liverpool FC**: Automatyczne wykrywanie momentów, w których struktura obronna przeciwnika staje się "nieszczelna".

---

### 3.2 Voronoi Tessellation (Diagram Woronoja)

**Czym jest**: Metoda geometryczna podziału boiska na komórki — każda komórka zawiera wszystkie punkty boiska najbliższe danemu zawodnikowi.

**Algorytm**:
1. Dla 22 zawodników (punktów nasiennych) na boisku
2. Oblicz dla każdego punktu boiska, który zawodnik jest najbliżej
3. Granice komórek to zbiory punktów równoodległych od dwóch zawodników

**Metryki wynikowe**:
- **Rozmiar komórki Voronoi**: Ile przestrzeni "kontroluje" zawodnik — duża komórka = dużo wolnej przestrzeni wokół
- **Zmiana komórek w czasie**: Jak kontrola przestrzeni ewoluuje podczas akcji
- **Porównanie komórek obu zespołów**: Który zespół kontroluje więcej przestrzeni w strefach kluczowych

**Ograniczenie**: Bazowy Voronoi opiera się tylko na odległości euklidesowej i nie uwzględnia prędkości/kierunku ruchu zawodników. Pitch Control jest bardziej zaawansowaną alternatywą.

---

### 3.3 Off-Ball Scoring Opportunity (OBSO)

**Czym jest**: Model opracowany przez Spearmana (2018), który kwantyfikuje jakość **pozycjonowania bez piłki** — identyfikuje zawodników i strefy, które tworzą okazje bramkowe, nawet gdy piłka nigdy tam nie trafia.

**Algorytm**: 
Kombinacja Pitch Control + xG:
1. Oblicz Pitch Control dla każdego punktu boiska
2. Oblicz xG dla hipotetycznego strzału z każdego punktu
3. OBSO = Pitch Control × xG × prawdopodobieństwo, że piłka tam dotrze

**Creating Off-Ball Scoring Opportunity (C-OBSO)**: Rozszerzenie mierzące, jak **ruchy zawodnika** tworzą okazje dla kolegów z zespołu. Używa grafowych wariacyjnych sieci rekurencyjnych do prognozowania trajektorii.

---

### 3.4 Player Influence Area (Fernández & Bornn)

**Czym jest**: Bardziej zaawansowany model kontroli przestrzeni niż Voronoi, który uwzględnia:
- Prędkość i kierunek ruchu zawodnika
- Zdolność do przyspieszenia/hamowania
- Orientację ciała

**Wynik**: Każdy zawodnik generuje "pole wpływu" na boisku — nie okrągłe (jak w Voronoi), ale eliptyczne, rozciągnięte w kierunku ruchu. Szybki skrzydłowy biegnący wzdłuż linii kontroluje elongowaną strefę przed sobą.

---

## 4. Analiza Pressingu i Defensywy

### 4.1 PPDA — Passes Allowed Per Defensive Action

**Czym jest**: Metryka intensywności pressingu — ile podań pozwala rywalowi drużyna broniąca przed podjęciem akcji defensywnej.

**Obliczenie**:

```
PPDA = Podania_rywala_w_strefie_budowania / Akcje_defensywne_w_strefie_budowania
```

Gdzie akcje defensywne = odbiory + przejęcia + wślizgi + faule

**Interpretacja**:
- PPDA < 8 → bardzo intensywny pressing (np. Liverpool Kloppa ~8.6)
- PPDA 8-12 → umiarkowany pressing
- PPDA > 12 → pasywna obrona, cofnięty blok

**Ograniczenie**: Opiera się tylko na danych zdarzeń — nie uwzględnia pozycji zawodników bez piłki.

---

### 4.2 Dynamic Pressure Model — Total Pressure on an Object

**Czym jest**: Zaawansowany model obliczający sumaryczne oddziaływanie wszystkich obrońców na zawodnika przy piłce lub potencjalnego odbiorcę podania, z danych tracking.

**Matematyka**:

```
P_total_pressure = 1 - Π_i(1 - p_i)
```

Gdzie `p_i` = indywidualne prawdopodobieństwo przejęcia piłki przez obrońcę i.

**Interpretacja**: Jeśli jest 3 obrońców z indywidualnymi prawdopodobieństwami przejęcia 0.2, 0.15, 0.1:
```
P_total = 1 - (1-0.2)(1-0.15)(1-0.1) = 1 - 0.8×0.85×0.9 = 1 - 0.612 = 0.388
```
Łączna presja = 38.8%

**Zastosowanie**: Kwantyfikacja pressingu w **każdej milisekundzie** akcji — nie tylko jako średnia meczowa, ale moment po momencie.

---

### 4.3 Pressing Intensity (Bekkers & Sahasrabudhe, 2025)

**Czym jest**: Najnowszy framework (2025) wykraczający poza PPDA, wykorzystujący dane tracking do obliczania presji na poziomie klatki.

**Algorytm**:
1. Dla każdego obrońcy oblicz czas potrzebny do przechwycenia piłki/atakującego (time-to-intercept)
2. Uwzględnij aktualną prędkość, kierunek ruchu, czas reakcji
3. Transformuj czas na prawdopodobieństwo przy użyciu funkcji logistycznej
4. Agreguj presję wszystkich obrońców

**Implementacja**: Dostępna jako open-source w pakiecie Python UnravelSports.

---

### 4.4 Counter-Pressing (Gegenpressing) Metrics

**Czym jest**: Metryki specyficzne dla pressingu po stracie piłki — jak szybko i intensywnie zespół stara się odzyskać posiadanie.

**Kluczowe zmienne**:
- **Czas do pierwszej presji**: Ile sekund mija od straty do pierwszej akcji defensywnej
- **Intensywność 5-sekundowa**: Ile graczy uczestniczy w pressingu w ciągu 5s od straty
- **Wskaźnik odzyskania**: % strat, po których piłka zostaje odzyskana w ciągu 5s
- **Strefa odzyskania**: Gdzie na boisku najczęściej dochodzi do odzyskania

---

## 5. Analiza Sieciowa i Grafowa

### 5.1 Passing Networks (Sieci Podań)

**Czym jest**: Reprezentacja zespołu jako grafu, gdzie:
- **Węzły** = zawodnicy
- **Krawędzie** = podania między nimi (ważone częstotliwością/jakością)

**Metryki sieciowe**:

#### Betweenness Centrality
**Definicja**: Procent najkrótszych ścieżek w sieci, które przechodzą przez dany węzeł.
```
BC(v) = Σ_{s≠v≠t} (σ_st(v) / σ_st)
```
Gdzie σ_st = liczba najkrótszych ścieżek z s do t, σ_st(v) = te, które przechodzą przez v.

**Interpretacja piłkarska**: Zawodnik o wysokim BC to "wąskie gardło" — kluczowy pomost w dystrybucji piłki. Odcięcie takiego gracza dezorganizuje budowanie akcji (np. man-marking na Busquetsie/Rodrim).

#### Eigenvector Centrality (PageRank)
**Definicja**: Waga zawodnika w kontekście jakości jego partnerów w podaniach. Gracz wymieniający piłkę z innymi wpływowymi zawodnikami w strefach zagrożenia jest oceniany wyżej.

**Interpretacja**: Mierzy nie tylko "ile podań", ale "z kim te podania" — pomocnik grający z kluczowymi zawodnikami ofensywnymi ma wyższy PageRank niż ten, który wymienia piłkę tylko z obrońcami.

#### Clustering Coefficient
**Definicja**: Miara lokalnej współpracy — jaka frakcja par sąsiadów danego zawodnika również wymienia między sobą piłkę (tworzą "trójkąty" podań).

**Interpretacja**: Wysoki clustering = stabilne lokalne triangualy (np. lewa strona: lewy obrońca ↔ lewy pomocnik ↔ lewe skrzydło). Niski clustering = gracz jest hubem łączącym rozłączne grupy.

#### Motif Analysis
**Definicja**: Wykrywanie powtarzalnych mikrowzorców podań (motywów grafowych) — np. trójkąt podań, sekwencja "ściana", podanie-odegranie-podanie w głąb.

**Zastosowanie taktyczne**: Na podstawie pierwszych 2-3 podań w sekwencji przewiduje kierunek ataku. Jeśli zespół ma dominujący motyw "obrońca → 6 → 8 → skrzydłowy", rywale mogą przygotować pressing celujący w tę sekwencję.

#### Graph Density
**Definicja**: Stosunek faktycznych krawędzi do maksymalnej możliwej liczby krawędzi.
```
Density = 2E / (N(N-1))
```

**Interpretacja**: Wysoka gęstość = zespół "połączony", dużo opcji podań między każdą parą. Niska gęstość = zespół hierarchiczny, piłka krąży przez konkretnych graczy.

---

### 5.2 Field Tilt

**Czym jest**: Metryka dominacji terytorialnej — udział kontaktów z piłką danego zespołu w tercji ataku w stosunku do sumy kontaktów obu zespołów w ich tercjach ofensywnych.

**Obliczenie**:

```
Field_Tilt_A = Kontakty_A_w_tercji_ataku / (Kontakty_A_w_tercji_ataku + Kontakty_B_w_tercji_ataku)
```

**Interpretacja**:
- Field Tilt > 65% + niskie xG → zespół dominuje terytorialnie, ale nie potrafi przebić się przez niski blok
- Nagła zmiana Field Tilt → sygnał zmiany momentum, często poprzedza bramkę/zmianę formacji

---

### 5.3 Off-Ball Impact Score (OBIS)

**Czym jest**: Wykorzystuje analizę sieciową podań do kwantyfikacji wkładu zawodników **bez piłki** — mierzy, jak obecność i ruch gracza wpływa na strukturę sieci podań zespołu.

---

## 6. Modele Predykcji Wyników

### 6.1 Rozkład Poissona (Podstawowy)

**Czym jest**: Model zakładający, że liczba bramek zdobytych przez zespół w meczu jest zmienną losową o rozkładzie Poissona z parametrem λ (oczekiwana liczba goli).

**Matematyka**:
```
P(X = k) = (λ^k × e^(-λ)) / k!
```

Gdzie:
- k = liczba bramek
- λ = oczekiwana liczba bramek (np. 1.5)

**Przykład**: Jeśli λ_A = 1.8 i λ_B = 1.1:
- P(A zdobywa 0) = e^(-1.8) ≈ 0.165
- P(A zdobywa 1) = 1.8 × e^(-1.8) ≈ 0.298
- P(A zdobywa 2) = (1.8² × e^(-1.8)) / 2 ≈ 0.268

**Ograniczenie**: Zakłada niezależność bramek obu drużyn — w rzeczywistości bramki są skorelowane (remisy 0:0 i 1:1 występują częściej niż przewiduje model).

---

### 6.2 Model Dixona-Colesa

**Czym jest**: Korekta rozkładu Poissona, która rozwiązuje dwa problemy:
1. **Korelacja przy niskich wynikach**: Wprowadza parametr ρ korygujący prawdopodobieństwo wyników 0:0, 1:0, 0:1, 1:1
2. **Ewolucja siły w czasie**: Parametry ataku i obrony zespołów zmieniają się w czasie — nowsze mecze mają większą wagę

**Parametry modelu**:
- `α_i` = siła ataku zespołu i
- `β_i` = siła obrony zespołu i  
- `γ` = przewaga gospodarza (home ground advantage)
- `ρ` = parametr korelacji (korekta niskich wyników)
- `ξ(t)` = funkcja zaniku czasowego (waga historycznych meczów)

**Matematyka λ**:
```
λ_home = α_home × β_away × γ  (oczekiwane gole gospodarza)
λ_away = α_away × β_home       (oczekiwane gole gościa)
```

---

### 6.3 Bivariate Poisson Regression

**Czym jest**: Zaawansowana regresja, która modeluje liczbę goli obu drużyn jako **skorelowane** zmienne losowe — jednoczesne modelowanie wyników obu stron.

**Zastosowanie**: Kluczowa przy przewidywaniu wyników remisowych w meczach o wysoką stawkę, gdzie obie drużyny wykazują awersję do ryzyka.

---

### 6.4 Symulacja Monte Carlo

**Czym jest**: Technika generowania tysięcy (zazwyczaj 10 000+) losowych symulacji sezonów/turniejów na podstawie parametrów siły zespołów.

**Algorytm**:
1. Ustal parametry siły każdego zespołu (np. z Elo lub Dixona-Colesa)
2. Dla każdego meczu w kalendarzu wygeneruj losowy wynik z odpowiedniego rozkładu
3. Oblicz końcową tabelę
4. Powtórz 10 000 razy
5. Prawdopodobieństwo mistrzostwa = % symulacji, w których zespół jest na 1. miejscu

**Zastosowanie**: Prognoza końcowej tabeli ligowej, szans na awans, prawdopodobieństwo spadku.

---

### 6.5 In-Game Win Probability (WPA)

**Czym jest**: Model prawdopodobieństwa wygranej **aktualizowany w czasie rzeczywistym** w trakcie meczu.

**Zmienne wejściowe**:
- Aktualna różnica bramek
- Czas do końca (uwzględniając nieliniowość doliczonego czasu)
- Różnica w liczbie zawodników (czerwone kartki)
- Rolling averages statystyk ofensywnych z ostatnich 10-15 minut (momentum)
- Kursy bukmacherskie jako baseline

**Algorytm**: Najczęściej model ML (XGBoost lub sieć neuronowa) trenowany na milionach historycznych stanów gry.

**Win Probability Added (WPA)**: Dla każdej akcji — o ile zmieniła prawdopodobieństwo wygranej. Gol zmieniający wynik z 0:1 na 1:1 w 85. minucie ma ogromne WPA.

---

### 6.6 Bayesian Inference (Wnioskowanie Bayesowskie)

**Czym jest**: Podejście do aktualizacji przekonań o sile zespołów w oparciu o nowe obserwacje (wyniki meczów, zdarzenia in-game).

**Twierdzenie Bayesa**:
```
P(θ|dane) ∝ P(dane|θ) × P(θ)
```

- `P(θ)` = prior (wcześniejsze przekonanie o sile zespołu)
- `P(dane|θ)` = likelihood (prawdopodobieństwo obserwowanych danych przy danej sile)
- `P(θ|dane)` = posterior (zaktualizowane przekonanie)

**Hierarchiczny model bayesowski**: Modeluje siłę ataku i obrony każdego zespołu jako zmienne losowe z rozkładami a priori, aktualizowanymi po każdym meczu. Pozwala na "dzielenie się informacją" między zespołami w lidze.

---

### 6.7 Drzewa losowe (Random Forests) — Prognoza czasu doliczonego

**Czym jest**: Ensemble drzew decyzyjnych do predykcji doliczonego czasu gry.

**Zmienne**: Liczba zmian, przerw na kontuzje, interwencji VAR, fauli, bramek w danej połowie.

---

## 7. Systemy Rankingowe i Siła Zespołów

### 7.1 System Elo

**Czym jest**: System rankingowy oparty na parach porównań, oryginalnie z szachów. Każdy zespół ma rating liczbowy; po meczu rating jest aktualizowany proporcjonalnie do "zaskoczenia" wynikiem.

**Matematyka**:

Oczekiwany wynik:
```
E_A = 1 / (1 + 10^((R_B - R_A) / 400))
```

Aktualizacja ratingu:
```
R_A_new = R_A + K × (S_A - E_A)
```

Gdzie:
- `R_A`, `R_B` = aktualne ratingi
- `K` = współczynnik aktualizacji (jak szybko reaguje na nowe wyniki)
- `S_A` = faktyczny wynik (1 = wygrana, 0.5 = remis, 0 = przegrana)
- `E_A` = oczekiwany wynik (0-1)

**Właściwości**:
- Faworyt wygrywający zyskuje mało punktów; outsider wygrywający — dużo
- System jest samoregulujący — suma punktów w systemie jest stała
- Różnica 400 punktów → 91% szans wygranej silniejszego

---

### 7.2 System Glicko-2

**Czym jest**: Rozszerzenie Elo o dwa dodatkowe parametry:
- **Rating Deviation (RD)**: Niepewność ratingu — jak pewni jesteśmy oceny zespołu
- **Volatility (σ)**: Zmienność — jak stabilne są wyniki zespołu

**Kluczowa innowacja**: Jeśli zespół nie grał przez długi czas, jego RD rośnie (mniejsza pewność), co oznacza, że kolejny wynik będzie miał większy wpływ na rating.

**Zastosowanie**: Idealne dla turniejów międzynarodowych (Mistrzostwa Świata), gdzie liczba meczów jest ograniczona i przerwy między meczami długie.

---

## 8. Analiza Stałych Fragmentów Gry

### 8.1 Non-negative Matrix Factorization (NMF) — Wzorce Rożnych

**Czym jest**: Technika rozkładu macierzy na czynniki do identyfikacji "tematów" taktycznych — powtarzalnych wzorców ruchowych kilku zawodników jednocześnie podczas stałych fragmentów.

**Algorytm**:
1. Macierz V (dane rożnych) ≈ W × H (dwa nieujemne czynniki)
2. W = "tematy" taktyczne (wzorce ruchów)
3. H = "mieszanka" tematów w każdym rożnym

**Zastosowanie**: Automatyczna identyfikacja schematów rożnych — "bliski słupek z blokiem", "daleki słupek z rajdem", "krótki rożny z overlapem" — bez ręcznego tagowania.

---

### 8.2 Gaussian Mixture Models (GMM) — Strefy Aktywne

**Czym jest**: Model mieszanin gaussowskich do podziału pola karnego na "strefy aktywne", gdzie najczęściej dochodzi do oddania strzału po stałych fragmentach.

**Odkrycie**: 15 stref aktywnych wystarcza do opisania 95% groźnych sytuacji po rożnych.

**Algorytm**: 
1. Zbierz pozycje strzałów po stałych fragmentach (rożne, rzuty wolne)
2. Dopasuj model GMM z K komponentami (strefami)
3. Każda strefa = rozkład normalny 2D z centrum i zasięgiem
4. EM algorithm (Expectation-Maximization) optymalizuje parametry

---

### 8.3 Inżynieria Markowania (Man-Marking Detection)

**Czym jest**: Algorytm automatycznie określający, czy obrońca kryje indywidualnie (man-marking) czy strefowo, analizując korelację jego pozycji z ruchem konkretnego napastnika.

**Metoda**: Korelacja Pearsona między trajektoriami obrońcy i atakującego w pierwszych 2 sekundach po dośrodkowaniu. Wysoka korelacja (>0.7) → man-marking. Niska → obrona strefowa.

---

### 8.4 TacticAI (Google DeepMind × Liverpool FC)

**Czym jest**: System AI wykorzystujący **Geometric Deep Learning** do analizy i generowania taktyk rożnych. Opublikowany w Nature Communications (2024).

**Trzy funkcje**:
1. **Predykcja**: Kto otrzyma piłkę po rożnym? Czy dojdzie do strzału?
2. **Retrieval**: Wyszukiwanie historycznie podobnych sytuacji
3. **Generacja**: Sugerowanie optymalnych ustawień

**Walidacja**: Sugestie TacticAI były preferowane przez ekspertów Liverpool FC w 90% przypadków nad istniejącymi taktykami.

---

### 8.5 Fizyka Rzutów Wolnych

**Czym jest**: Modele balistyczne uwzględniające aerodynamikę piłki.

**Siły fizyczne**:
- **Siła Magnusa**: Siła boczna wynikająca z rotacji piłki — odpowiedzialna za "banana kick"
- **Drag (opór)**: Spowalnia piłkę
- **Lift**: Siła nośna wynikająca z podkręcenia

**Odkrycie dotyczące muru**: Mur pełni funkcję "wizualnego okluzora". Jeśli zasłania początkową trajektorię piłki przez pierwsze 150-250 ms, bramkarz inicjuje ruch o ~70-90 ms później. Przy prędkości >25 m/s to daje ~1.5-3 cm błędu w pozycjonowaniu dłoni — często decydujące.

---

## 9. Analiza Starć Bezpośrednich i Profilowanie Zawodników

### 9.1 Individual Win Probability (IWP)

**Czym jest**: Model szacujący szansę wygrania pojedynku fizycznego lub technicznego (drybling, walka w powietrzu, 1v1 na szybkość).

**Dane wejściowe**: Profile fizyczne obu zawodników (prędkość, przyspieszenie, siła), profile techniczne (skuteczność dryblingów, odbiory), kontekst sytuacyjny (pozycja na boisku, zmęczenie).

---

### 9.2 Z-Score Profiling

**Czym jest**: Standaryzacja statystyk zawodnika względem średniej ligowej, mierzona w odchyleniach standardowych.

**Obliczenie**:
```
Z = (X - μ) / σ
```

Gdzie:
- X = wartość statystyki zawodnika
- μ = średnia ligowa dla danej pozycji
- σ = odchylenie standardowe

**Interpretacja**: Z-Score = +2.5 oznacza, że zawodnik jest o 2.5 odchylenia standardowego powyżej średniej — elitarny w tej metryce. Z-Score = -1.0 → poniżej średniej.

**Zastosowanie w IWP**: Jeśli obrońca ma Z-Score szybkości +2.5, a skrzydłowy +1.2, model wskazuje przewagę obrońcy w biegach na otwartej przestrzeni.

---

### 9.3 Deceleration/Acceleration Profiling

**Czym jest**: Profil zdolności zawodnika do gwałtownego hamowania i zmiany kierunku.

**Odkrycie**: W nowoczesnym futbolu umiejętność hamowania jest często ważniejsza niż prędkość maksymalna — pozwala na szybsze zamknięcie linii podania lub reakcję na zwód.

**Metryki**: Maksymalna deceleracja (m/s²), czas reakcji na zmianę kierunku, stosunek przyspieszenia do deceleracji.

---

### 9.4 Cosine Similarity — Podobieństwo Zawodników

**Czym jest**: Miara podobieństwa **profilu/stylu gry** dwóch zawodników, niewrażliwa na skalę.

**Obliczenie**:
```
cos(θ) = (A · B) / (||A|| × ||B||)
```

Gdzie A i B to wektory cech zawodników (np. [xG/90, xA/90, pressing/90, drybling/90, ...]).

**Zastosowanie**: Znajdowanie graczy o identycznym profilu (stylu gry) w słabszych ligach, gdzie liczby bezwzględne są niższe. Idealny do skautingu.

**Przykład**: Gracz w Eredivisie z cosine similarity 0.95 do Salaha ma bardzo podobny profil gry, choć bezwzględne liczby są niższe.

---

### 9.5 Euclidean Distance — Podobieństwo Zawodników

**Czym jest**: Miara różnicy w **bezwzględnych wartościach** statystyk.

```
d(A,B) = √(Σ_i (a_i - b_i)²)
```

**Zastosowanie**: Szukanie graczy o podobnej "objętości" pracy i bezwzględnych wynikach.

**Różnica od cosine**: Euclidean mierzy "jak daleko" są profile, cosine mierzy "w jakim kierunku" idą — gracz z 2× większymi wartościami we wszystkich metrykach ma ten sam cosine, ale dużą odległość euklidesową.

---

### 9.6 Moran's I — Autokorelacja Przestrzenna

**Czym jest**: Statystyka mierząca, czy wartość zmiennej w jednym miejscu jest powiązana z wartościami w sąsiednich miejscach.

**Zastosowanie piłkarskie**: Identyfikacja, czy dany zawodnik zajmuje "unikalne" strefy boiska w porównaniu do innych graczy na tej samej pozycji — np. Inverted Fullback, który zamiast grać na skrzydle, wchodzi do środka.

**Interpretacja**:
- Moran's I > 0 → pozytywna autokorelacja (klastry podobnych wartości)
- Moran's I ≈ 0 → losowy rozkład
- Moran's I < 0 → negatywna autokorelacja (szachownica)

---

## 10. Analiza Tranzycji i Kontrataków

### 10.1 Counter-Attack Detection (AI Framework)

**Czym jest**: Framework łączący Transformers i Graph Neural Networks do automatycznego wykrywania i oceny kontrataków z synchronizowanych danych zdarzeń i tracking.

**Statystyki**: ~10 kontrataków na mecz w Premier League, typowa sekwencja to 5 zdarzeń, z czego 2 kończą się próbą strzału.

---

### 10.2 Transition Speed Metrics

**Metryki szybkości tranzycji**:
- **Byline-to-byline speed**: Szybkość przejścia z jednej linii końcowej do drugiej
- **Vertical advancement speed**: Jak szybko piłka posuwa się w kierunku bramki
- **Sideline-to-sideline speed**: Szybkość zmiany strony ataku

**Odkrycie**: W topowych ligach 69% ofensywnych tranzycji trwa 9+ sekund. Ostatnie 15 minut każdej połowy ma najwyższą częstotliwość udanych tranzycji.

---

### 10.3 Off-Ball Positioning Value (OBPV)

**Czym jest**: Ocena efektywnego wykorzystania przestrzeni podczas tranzycji — mierzy, jak dobrze zawodnicy zajmują pozycje wspierające kontrataki.

---

## 11. Kształt Zespołu i Metryki Geometryczne

### 11.1 Centroid (Środek Ciężkości Zespołu)

**Czym jest**: Średnia pozycja (x, y) wszystkich zawodników polowych zespołu.

```
Centroid_x = (1/10) × Σ x_i
Centroid_y = (1/10) × Σ y_i
```

**Zastosowanie**: 
- Odległość między centroidami dwóch zespołów → kompaktowość meczu
- Przesunięcie centroidu w czasie → dominacja terytorialna
- Centroid atakujących vs broniących → linia ofsajdu, głębokość defensywy

---

### 11.2 Convex Hull (Otoczka Wypukła)

**Czym jest**: Najmniejszy wielokąt wypukły obejmujący wszystkie pozycje zawodników zespołu.

**Metryki**:
- **Pole convex hull**: Ile powierzchni boiska "zajmuje" zespół
- **Obwód**: Jak "rozciągnięty" jest zespół
- **Stosunek pola do obwodu**: Miara kompaktowości

**Zastosowanie**: Zespół z dużym polem convex hull w posiadaniu i małym bez piłki jest taktycznie dojrzały — rozciąga się w ataku, kompresuje w obronie.

---

### 11.3 Stretch Index i Compactness

**Stretch Index**: Średnia odległość wszystkich zawodników od centroidu — jak "rozciągnięty" jest zespół.

**Compactness Coefficient**: Miara szybkości, z jaką zespół się kompresuje po stracie piłki.

---

### 11.4 Shape Graphs (Delaunay Triangulation)

**Czym jest**: Najnowsza metoda (Nature, 2025) używająca triangulacji Delaunaya do analizy kształtu zespołu klatka po klatce.

**Algorytm**: 
1. Pozycje zawodników tworzą zbiór punktów
2. Triangulacja Delaunaya dzieli zbiór na trójkąty (bez przecinających się krawędzi)
3. Analiza topologii grafu — które trójkąty sąsiadują, jak zmieniają się w czasie
4. Detekcja formacji i ról taktycznych bez ręcznego tagowania

---

## 12. Optymalizacja Formacji i Składu

### 12.1 Genetic Algorithm (Algorytm Genetyczny)

**Czym jest**: Metaheurystyka optymalizacyjna inspirowana ewolucją biologiczną, zastosowana do wyboru optymalnej formacji i składu.

**Algorytm**:
1. **Populacja początkowa**: Losowo wygeneruj N formacji/składów
2. **Ocena fitness**: Oceń każdą formację (np. sumą similarity scores, komplementarnością, historyczną skutecznością)
3. **Selekcja**: Wybierz najlepsze formacje
4. **Krzyżowanie**: Łącz elementy dwóch formacji (np. linia obrony z jednej, atak z drugiej)
5. **Mutacja**: Losowe małe zmiany (zamiana jednego zawodnika, zmiana roli)
6. **Powtórz** przez G generacji

**Wynik**: Badania na danych Ekstraklasy brazylijskiej (322 zawodników, 18 kryteriów) znalazły optymalną formację 3-6-1.

---

### 12.2 Hungarian Algorithm (Algorytm Węgierski)

**Czym jest**: Algorytm optymalnego przypisania — dopasowuje zawodników do pozycji minimalizując koszt (lub maksymalizując dopasowanie).

**Zastosowanie**: Mając N zawodników i M pozycji na boisku, z macierzą "dopasowania" (jak dobrze gracz X pasuje na pozycję Y), algorytm znajduje optymalne przypisanie w czasie wielomianowym O(n³).

---

### 12.3 Bayesian Game Theory + Stochastic Games

**Czym jest**: Modelowanie piłki nożnej jako wieloetapowej gry:
- **Przed meczem**: Bayesian game — decyzje o formacji/taktyce przy niepełnej informacji o rywalu
- **W trakcie meczu**: Stochastic game — tranzycje stanów (posiadanie, pressing, kontrataki)

**Wynik**: Empiryczna walidacja na 760 meczach wykazała do 16.1% poprawy prawdopodobieństwa wygranej.

---

## 13. Analityka Kognitywna i Percepcyjna

### 13.1 Scanning Frequency (Visual Exploratory Behavior)

**Czym jest**: Częstotliwość "skanowania" — szybkich ruchów głowy w celu zebrania informacji o otoczeniu przed kontaktem z piłką.

**Ekstrakcja danych**: Pose Estimation z danych wideo — algorytm CV rozpoznaje kierunek zwrócenia twarzy.

**Benchmarki**:
| Pozycja | Skanowanie (skany/sek) | Charakterystyka |
|---------|----------------------|-----------------|
| Środkowy pomocnik | 0.6-0.8 | Najwyższe — orientacja 360° |
| Środkowy obrońca | 0.4-0.5 | Kontrola linii i partnerów |
| Skrzydłowy | 0.3-0.4 | Skupienie na 1v1 |
| Napastnik | 0.2-0.3 | Wzrok na piłce i bramce |

**Korelacja z wydajnością**: Silna dodatnia korelacja z:
- Skutecznością podań progresywnych
- Utrzymaniem piłki pod presją
- "Zyskiem przestrzennym" — gracze, którzy skanują więcej, wykonują pierwszy kontakt korzystniej

---

### 13.2 Spatial Gain Post-Scanning

**Czym jest**: Miara "zysku przestrzennego" po skanowaniu — ile metrów zyskuje zawodnik w pierwszym kontakcie po wykonaniu skanu otoczenia.

**Korelacja**: Zawodnicy klasy światowej (Xavi, Modrić) skanują 6-8 razy w 10 sekund przed otrzymaniem podania, co przekłada się na lepszą orientację i efektywniejszy pierwszy kontakt.

---

## 14. Modele Fizyczne i Obciążeniowe

### 14.1 ACWR — Acute:Chronic Workload Ratio

**Czym jest**: Stosunek obciążenia treningowego z ostatnich 7 dni do średniej z 28 dni.

```
ACWR = Obciążenie_7_dni / Obciążenie_28_dni
```

**Bezpieczne okno**: 0.8-1.3
- ACWR < 0.8 → niedotrening (ryzyko detreningu, ale też kontuzji przy nagłym wzroście)
- ACWR 0.8-1.3 → optymalne
- ACWR > 1.3 → przetrenowanie (wysokie ryzyko kontuzji)

---

### 14.2 Metabolic Power

**Czym jest**: Miara szacująca koszt energetyczny **każdej sekundy ruchu** zawodnika, uwzględniając że start do sprintu jest bardziej obciążający niż bieg z jednostajną prędkością.

**Obliczenie**: Bazuje na modelu bioenergetycznym — uwzględnia przyspieszenie, prędkość, masę ciała, opór powietrza.

**Zastosowanie taktyczne**: Połączenie Metabolic Power z modelami pressingu pozwala zidentyfikować moment, w którym "silnik" pomocnika zaczyna słabnąć — przeciwnik może to wykorzystać do przejęcia kontroli w środku pola.

---

### 14.3 Modele Prognozowania Kontuzji

**Zmienne predykcyjne**:
- Historia kontuzji (najsilniejszy predyktor)
- ACWR i jego trendy
- Metabolic Power z ostatnich 7/14/28 dni
- Asymetria obciążenia (lewa vs prawa noga)
- Dystans sprintowy na trening

**Algorytm**: Najczęściej Random Forest lub XGBoost, choć dane są silnie niezbalansowane (kontuzje są rzadkie).

---

## 15. Silniki Machine Learning i Architektury

### 15.1 XGBoost / LightGBM / CatBoost

**Czym jest**: Ensemble drzew decyzyjnych trenowanych sekwencyjnie (gradient boosting) — każde kolejne drzewo koryguje błędy poprzednich.

**Dlaczego dominują w piłce**: 
- Świetnie radzą sobie z danymi tabelarycznymi (dominujący format w event data)
- Szybkie trenowanie i inference
- Łatwa kalibracja prawdopodobieństw
- Dobre z brakującymi danymi

**Zastosowanie**: xG, VAEP, pass completion, IWP, prognoza kontuzji, prognoza wyników.

---

### 15.2 Graph Neural Networks (GNN)

**Czym jest**: Architektura neuronowa operująca na grafach — idealna dla piłki nożnej, bo dane pozycjonujące są **nieustrukturyzowane** (zawodnicy zmieniają pozycje).

**Jak działa**:
- Zawodnicy = **węzły** grafu
- Relacje przestrzenne = **krawędzie** (odległość, kierunek, linia podania)
- Propagacja informacji: każdy węzeł aktualizuje swoją reprezentację na podstawie sąsiadów
- Wynik: model systemowy — ruch prawego obrońcy wpływa na dostępność podania dla lewego skrzydłowego

**Warianty w piłce**:
- **Graph Convolutional Networks (GCN)**: Standardowa konwolucja na grafie
- **Graph Attention Networks (GAT)**: Z mechanizmem attention — różni sąsiedzi mają różne wagi
- **Temporal Graph Networks (TGN)**: Uwzględniają dynamikę czasową

---

### 15.3 Transformers i Mechanizm Attention

**Czym jest**: Architektura "attention is all you need" — model skupia zasoby obliczeniowe na najistotniejszych elementach.

**W piłce nożnej**: Przy analizie strzału model "wie", że bramkarz i najbliższy obrońca są najważniejsi, a zawodnicy na drugim końcu boiska są nieistotni — attention przypisuje im niskie wagi.

**Zastosowanie**: Analiza sekwencji akcji, predykcja następnego zdarzenia, analiza kontrataków (Transformer + GNN).

---

### 15.4 LSTM — Long Short-Term Memory

**Czym jest**: Rekurencyjna sieć neuronowa z "pamięcią" — idealna do analizy sekwencji czasowych.

**W piłce nożnej**: Rozpoznaje, że dany atak jest wynikiem powolnego budowania przez 20 podań — "pamięta" kontekst całej sekwencji, co zmienia ocenę ryzyka.

**Zastosowanie**: Analiza sekwencji podań, detekcja wzorców taktycznych w czasie, predykcja następnego ruchu.

---

### 15.5 Variational Autoencoders (VAE)

**Czym jest**: Generatywna sieć neuronowa ucząca się kompresowanej reprezentacji danych i generująca nowe przykłady.

**Zastosowanie**: Generowanie syntetycznych scenariuszy taktycznych, augmentacja danych treningowych, modelowanie alternatywnych sekwencji akcji.

---

### 15.6 Deep Reinforcement Learning (DRL)

**Czym jest**: Agent uczący się optymalnej strategii przez interakcję ze środowiskiem — metodą prób i błędów, z nagrodami za pożądane zachowania.

**W piłce**: Agent uczy się optymalnych decyzji (kiedy podać, driblować, strzelić) przez symulację tysięcy sytuacji. Używany w Ghostingu i optymalizacji taktycznej.

---

## 16. Teoria Gier w Piłce Nożnej

### 16.1 Nash Equilibrium — Rzuty Karne

**Czym jest**: Zastosowanie równowagi Nasha do analizy rzutów karnych — klasyczny problem teorii gier ("matching pennies").

**Model**: 
- Strzelec wybiera stronę (lewa/prawa/środek)
- Bramkarz wybiera stronę (jednocześnie)
- Strzelec wygrywa przy niedopasowaniu, bramkarz przy dopasowaniu

**Odkrycie empiryczne** (1417 rzutów karnych):
- Przewidywana równowaga: strzelec kopie w lewo 39%, bramkarz rzuca się w lewo 42%
- Obserwowane: strzelec 40% w lewo, bramkarz 42% w lewo
- **Profesjonalni piłkarze grają niemal dokładnie zgodnie z Nash Equilibrium**

**Mixed Strategy Nash Equilibrium (MSNE)**: Optymalna strategia to **losowanie** z odpowiednimi prawdopodobieństwami — brak przewidywalnych wzorców.

---

### 16.2 Bayesian Game Theory — Decyzje Taktyczne

**Czym jest**: Modelowanie decyzji taktycznych (formacja, pressing, styl gry) jako gry przy **niepełnej informacji** — nie wiesz dokładnie, co zrobi przeciwnik.

**Zastosowanie**: Wybór formacji przed meczem, gdy nie znasz formacji rywala — model oblicza optymalną strategię przy założeniach o rozkładzie prawdopodobieństwa formacji rywala.

---

### 16.3 Minimax i Strategie Mieszane

**Czym jest**: Optymalizacja najgorszego przypadku — wybór strategii minimalizującej maksymalną stratę.

**W piłce**: Dobór taktyki defensywnej, która minimalizuje xG przeciwnika niezależnie od jego stylu ataku.

---

## 17. Ghosting i Uczenie Imitacyjne

### 17.1 Ghosting

**Czym jest**: Metoda wykorzystująca Deep Reinforcement Learning lub Deep Imitation Learning do modelowania **idealnych zachowań taktycznych**.

**Jak działa**:
1. Model trenowany na tysiącach meczów danej ligi
2. Generuje "duchy" (ghosts) — wirtualne pozycje zawodników reprezentujące, jak zachowałby się "typowy" zespół w danej sytuacji
3. Porównanie rzeczywistych pozycji z "duchami" identyfikuje **błędy taktyczne**

**Przykład**: Jeśli lewy obrońca jest o 2m dalej od osi boiska niż sugeruje model, a w tej strefie rywale tworzą szansę — system automatycznie flaguje to jako błąd pozycjonowania.

**Zastosowanie**: 
- Analiza post-match: identyfikacja momentów, gdy zespół odszedł od optymalnego ustawienia
- Trening: pokazanie zawodnikom, gdzie "powinni" być
- Skauting: jak zawodnik zachowałby się w innym systemie taktycznym

---

### 17.2 xReceiver — Predykcja Odbiorcy Podania

**Czym jest**: Model wykorzystujący GNN do przewidzenia **najbardziej prawdopodobnego odbiorcy podania** w dowolnym momencie posiadania piłki.

**Dane wejściowe**: Geometria ustawienia, prędkości zawodników, Player Availability (dostępność — czy gracz jest otwarty do podania).

**Zastosowanie defensywne**: "Jakość obrony" mierzona jako stopień, w jakim obrońcy ograniczają opcje progresywne rywala, zmuszając do podań bezpiecznych o niskiej wartości xT.

---

### 17.3 Expected Pass (xPass) i xPass 360

**Czym jest**: Model prawdopodobieństwa realizacji podania — jakie jest prawdopodobieństwo, że dane podanie zostanie odebrane.

**xPass 360**: Rozszerzenie uwzględniające pozycje WSZYSTKICH 22 zawodników (dane 360° freeze-frame od StatsBomb), co poprawia AUC do ~93.4%.

**Zastosowanie**: 
- Ocena trudności podań — zawodnik wykonujący trudne podania z wysoką skutecznością jest cenny
- xPass completion - actual completion = miara "ponadrpzeciętności" podawania

---

## 18. Podsumowanie — Trzy Filary Analityki Piłkarskiej

### Filar 1: Wycena Procesu, nie Wyniku
Modele: VAEP, xT, EPV, gBRI, xGChain, xGBuildup  
Pozwalają docenić graczy systematycznie zwiększających szanse zespołu, nawet gdy tradycyjne statystyki ich pomijają.

### Filar 2: Kwantyfikacja Przestrzeni
Modele: Pitch Control, Voronoi, OBSO, DxT, Player Influence Areas, Pressing Intensity  
Zamieniają boisko w mapę prawdopodobieństw — precyzyjne planowanie pressingu, kontrataków i pozycjonowania.

### Filar 3: Integracja Kognitywno-Fizyczna
Modele: Scanning Frequency, Metabolic Power, ACWR, Ghosting  
Łączą percepcję z obciążeniem fizycznym — jak zmęczenie wpływa na decyzje w kluczowych fazach meczu.

---

*Dokument zawiera 50+ algorytmów i metodologii stosowanych w profesjonalnej analityce piłkarskiej na dzień luty 2026.*
