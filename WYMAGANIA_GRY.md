# Wymagania gry — Football Manager AI Strategy (wersja produktowa)

**Źródło**: Ustalenia z dyskusji wymagań (Luty 2026)  
**Spójność z**: GRA_FOOTBALL_MANAGER_DESIGN.md, SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md, FORMACJE_ROLE_TAKTYKA.md, ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md, MODELE_I_PRZEPLYWY_APLIKACJI.md. Kontrakt silnika, API, schemat bazy, auth, UI, edge case’y, testy → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md**.

---

## Spis treści

1. [Liga i zespoły](#1-liga-i-zespoły)
2. [Zawodnicy i kadra](#2-zawodnicy-i-kadra)
3. [Terminarz i tabela](#3-terminarz-i-tabela)
4. [Kontuzje, dyspozycja, morale](#4-kontuzje-dyspozycja-morale)
5. [Budżet i transfery](#5-budżet-i-transfery)
6. [Sędzia i warunki gry](#6-sędzia-i-warunki-gry)
7. [Stałe fragmenty i remisy](#7-stałe-fragmenty-i-remisy)
8. [Symulacja meczu i dane po meczu](#8-symulacja-meczu-i-dane-po-meczu)
9. [Multiplayer (PvP, PvAI)](#9-multiplayer-pvp-pvai)
10. [Persystencja i zapis stanu](#10-persystencja-i-zapis-stanu)
11. [Double-check: spójność z dokumentacją](#11-double-check-spójność-z-dokumentacją)

---

## 1. Liga i zespoły

### 1.1 Tworzenie ligi

- Użytkownik **sam tworzy ligę** i nadaje jej **nazwę**.
- Liga jest **głównym trybem** rozgrywki (wszystko dzieje się w kontekście jednej ligi).

### 1.2 Liczba drużyn

- Użytkownik ustawia **od 10 do 20 drużyn** w lidze.
- Sloty drużyn wypełniane przez:
  - **zaproszonych znajomych** (multiplayer), albo
  - **boty** (AI) — logika botów (taktyka, skład, triggery) do zaprojektowania później.
- Możliwa mieszanka: część drużyn to gracze, część to boty.

### 1.3 Zespół użytkownika

- Użytkownik **nazywa swój zespół** (np. nazwa klubu).
- Jeden użytkownik = jedna drużyna w danej lidze.

---

## 2. Zawodnicy i kadra

### 2.1 Wielkość kadry

- **18 zawodników** na zespół przy tworzeniu / starcie ligi (skład wyjściowy).
- W trakcie sezonu, po transferach, dopuszczalny zakres kadry to **16–20** zawodników (szczegóły §5.3).

### 2.2 Generowanie zawodników

- Zawodnicy są **losowo generowani** przy tworzeniu zespołu / ligi.
- **Balans startowy**: suma atrybutów (np. suma wybranych atrybutów lub jeden wskaźnik „siły”) musi być **taka sama dla wszystkich drużyn**, aby wszyscy mieli **równe szanse** na starcie.
- Konkretna metryka balansu (np. suma 30 atrybutów, mediana, punktowy „overall”) do zdefiniowania w implementacji.

### 2.3 Imiona i nazwiska

- **Wersja 1 (v1)**: Domyślnie wszyscy zawodnicy mają imię „Name” i nazwisko „Surname”; użytkownik może **dla każdego** ustawić **własne imię i nazwisko** (personalizacja). Pula imion/nazwisk (generator losowy) — na później (MODELE §4.4).

### 2.4 Atrybuty i model danych

- Model zawodnika zgodny z **ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md** (30 atrybutów polowych, 6 bramkarskich, traits, parametry fizyczne, skala 1–20).
- Bramkarze i gracze polowi — rozróżnienie jak w dokumentacji.

---

## 3. Terminarz i tabela

### 3.1 Terminarz meczów

- Mecze rozgrywane **dwa razy w tygodniu**: **środa i sobota**.
- **Godzina** wszystkich meczów: **17:00** w timezone ligi (środa i sobota).
- **Symulacja automatyczna**: o 17:00 w dany dzień system automatycznie uruchamia symulację kolejki zaplanowanej na ten dzień (szczegóły: MODELE_I_PRZEPLYWY §2, KONTRAKTY §9.7). Możliwy też ręczny trigger „Rozegraj kolejkę” (założyciel ligi).

### 3.2 Tabela

- **Klasyczna tabela** jak w piłce nożnej:
  - punkty (W / R / P),
  - liczba meczów,
  - bramki zdobyte / stracone,
  - różnica bramek.
- **Kolejność (tie-break)**: punkty → różnica bramek → bramki zdobyte → mecz bezpośredni (punkty z dwu meczów H2H) → losowanie. Pełna specyfikacja → **KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md** §4.

---

## 4. Kontuzje, dyspozycja, morale

### 4.1 Kontuzje

- **Wymagane**: model kontuzji z:
  - stanem **fit / injured**,
  - **czasem do powrotu** (np. w kolejkach lub dniach).
- Warstwa **zdarzeń**: zdarzenie „kontuzja” (np. w symulacji meczu lub między meczami) oraz **rekonwalescencja** (countdown do powrotu).
- Atrybuty **ACWR** i **injury prone** (z ATRYBUTY doc) pozostają w modelu zawodnika i wpływają na ryzyko kontuzji; brakuje tylko warstwy zdarzeń — ma być zaimplementowana.

### 4.2 Dyspozycja / freshness

- **Freshness** („świeżość”) przed meczem:
  - zależy od **minut rozegranych** w ostatnich meczach,
  - opcjonalnie od „treningu” (jeśli zostanie wprowadzony).
- Wpływ: np. modyfikator na atrybuty lub na staminę w symulacji (zgodnie z SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md — zmęczenie w trakcie meczu + ewentualnie stan przedmeczowy).

### 4.3 Morale

- **Wymagane**: system morale (np. poziom 1–5 lub 0–100).
- Wpływ na grę: **composure** i **decyzje** w symulacji meczu (modyfikator z morale).
- Źródła morale: m.in. **wyniki** (np. seria wygranych podnosi, seria porażek obniża); szczegóły (remisy, gole, miejsce w tabeli) do ustalenia w implementacji.
- Kontrakty i „satysfakcja pod przedłużenie kontraktu” — **na razie nie** (kontrakty pominięte).

---

## 5. Budżet i transfery

### 5.1 Budżet startowy

- **Każdy zespół ma na start ten sam budżet** (wartość do zdefiniowania w konfiguracji / balansie).

### 5.2 Okno transferowe

- **Co dwa tygodnie** (np. po dwóch kolejkach) — możliwość wykonania **transferu**.
- Wymagany **model negocjacji**: oferta, odrzucenie / akceptacja, cena (ew. warunki). Szczegóły (licytacja, deadline, limity ofert) do zaprojektowania przy implementacji.

### 5.3 Limity kadry

- **Minimum 16 zawodników**, **maksimum 20 zawodników** w zespole (po transferach także).
- W trakcie sezonu nie można zejść poniżej 16 ani przekroczyć 20 (transfery muszą to respektować).

### 5.4 Cele zarządu

- **Brak** celów zarządu (np. „awans”, „utrzymanie”). Graczom po prostu zależy na tym, żeby **być wyżej w tabeli**.

---

## 6. Sędzia i warunki gry

### 6.1 Model sędziego

- **Wymagane**: model sędziego z parametrem **strictness** (0–1) wpływającym na **foul_risk** i **card_risk** w symulacji (SILNIK, MODELE §5).
- **Liczba i przypisanie**: Dla ligi tworzone jest **teamCount / 2** sędziów (przy Start sezonu). W każdej kolejce każdy sędzia prowadzi dokładnie jeden mecz; przypisanie mecz→sędzia **losowe** (KONTRAKTY §9.6, MODELE §5).

---

## 7. Stałe fragmenty i remisy

### 7.1 Remisy

- **Remis = remis**. Wynik po 90 minutach jest końcowy.

### 7.2 Stałe fragmenty — wrzuty

- **ThrowInConfig** (wrzuty z autu) — **wymagane**: zgodnie z FORMACJE_ROLE_TAKTYKA.md §13.4 (defaultTaker, longThrowTaker, shortOption, targetZones, runners; long throw jako quasi-rożny).
- Rożne, rzuty wolne — zgodnie z istniejącą dokumentacją (GRA, SILNIK, FORMACJE).

---

## 8. Symulacja meczu i dane po meczu

### 8.1 Silnik symulacji

- Zgodność z **SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md**: zdarzenie po zdarzeniu, xPass, xG, IWP, Pitch Control, DxT, triggery, itd.
- Taktyka (formacja, role, instrukcje, strategia) zgodnie z **FORMACJE_ROLE_TAKTYKA.md**.

### 8.2 Dane po meczu (logi, analityka)

- **Wymagane**: użytkownik ma widzieć **dużo danych** po meczu: dokładny opis, **pełne logi** (zdarzenie po zdarzeniu), tak aby **wyciągać wnioski** (analiza, poprawa taktyki).
- **Statystyki meczu (MatchSummary)**: pełny zestaw jak w raportach (posiadanie %, strzały, podania, dośrodkowania, odbiór, faule, kartki, rożne, wrzuty, spalone, xG, opcjonalnie VAEP/WPA) — definicja KONTRAKTY §2.3. Agregowane z listy zdarzeń i ewentualnie z silnika.
- Chronologiczna lista zdarzeń + podsumowanie (MatchSummary); opcjonalnie mapy (Pitch Control, sieci podań) — zgodnie z możliwościami silnika i UI.

---

## 9. Multiplayer (PvP, PvAI)

### 9.1 Tryby

- **PvP** — gracz vs gracz (mecz dwóch ludzkich trenerów).
- **PvAI** — gracz vs bot.
- **Bot vs bot** — np. dla uzupełnienia ligi lub testów.

### 9.2 Moment symulacji

- Mecze **nie** rozgrywane „na żywo” w czasie rzeczywistym w jednym momencie sesji.
- **Dwa razy w tygodniu** (środa, sobota o 17:00) następuje **rozegranie kolejki**: symulacja **wszystkich** meczów zaplanowanych na ten termin.
- Dotyczy to **wszystkich** typów: PvP, PvAI, bot vs bot — jeden wspólny moment „rozegrania”.

### 9.3 Boty

- Logika botów (wybór taktyki, składu, triggerów, ewentualnie negocjacje) — **do zaprojektowania później**.

---

## 10. Persystencja i zapis stanu

### 10.1 Zasada

- **Wszystko, co potrzebne do działania gry, ma być zapisywane** na stałe (persystencja pełna w zakresie wymagań).
- Gra ma działać w czasie: między sesjami użytkowników, między kolejkami, między transferami.

### 10.2 Zakres zapisu (co ma być trwałe)

- Liga (nazwa, liczba drużyn, uczestnicy).
- Drużyny (nazwy, właściciele / boty).
- Zawodnicy (atrybuty, imiona/nazwiska, przynależność do zespołu, kontuzje, freshness, morale).
- Terminarz i wyniki meczów (kolejki, kto z kim, wynik).
- Tabela (wyprowadzana z wyników lub zapisywana).
- Pełne logi meczów (zdarzenia) — w zakresie niezbędnym do prezentacji „pełnych logów” i analizy (§8.2).
- Budżety, historia transferów (w tym model negocjacji — stan ofert/transakcji).
- Stan okna transferowego (np. „co dwa tygodnie” — który tydzień sezonu).
- Ewentualne ustawienia użytkownika (np. timezone dla 17:00, preferencje wyświetlania).

Konkretny schemat bazy i format zapisu — do ustalenia przy implementacji (np. PostgreSQL, tabele: leagues, teams, players, matches, match_events, transfers, itd.).

---

## 11. Double-check: spójność z dokumentacją

| Wymaganie | Dokument referencyjny | Uwaga |
|-----------|------------------------|--------|
| Atrybuty zawodników (30+6, skala 1–20, traits) | ATRYBUTY_ZAWODNIKOW_KOMPLETNA_LISTA.md | Zgodne |
| Silnik symulacji (xPass, xG, IWP, Pitch Control, DxT, fatigue) | SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md | Zgodne |
| Formacje, role, taktyka, GamePlan | FORMACJE_ROLE_TAKTYKA.md | Zgodne; ThrowInConfig w FORMACJE §13.4 |
| Stałe fragmenty (rożne, rzuty wolne, wrzuty) | FORMACJE §13; GRA §8 | ThrowInConfig wymagane |
| Kontuzje, ACWR, injury prone | ATRYBUTY doc (traits); SILNIK (brak warstwy zdarzeń) | Wymagana warstwa zdarzeń kontuzji + rekonwalescencja |
| Morale → composure / decyzje | SILNIK (effective attributes) | Modyfikator z morale do dodać w silniku |
| Sędzia (strictness → foul_risk, card_risk) | SILNIK §6 (foul_risk, card_risk) | Model sędziego do zdefiniowania (strictness) |
| 18 zawodników na zespół | — | Wymaganie produktowe; skład 11 + 7 rezerwowych |
| Min/max 16–20 po transferach | — | Wymaganie produktowe |
| Tabela klasyczna | — | Punkty, W/R/P, bramki, różnica |
| Liga 10–20 drużyn, śr+sob 17:00 | — | Wymaganie produktowe |
| Pełne logi meczu, wnioski | GRA §5 (analityka post-match) | VAEP, Ghosting, WPA, sieci — w zakresie możliwym |

---

*Wymagania v1.0 — Luty 2026*  
*Po double-checku spójności z GRA, SILNIK, FORMACJE, ATRYBUTY*
