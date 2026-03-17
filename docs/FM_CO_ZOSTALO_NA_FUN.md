# Co zostało względem FM, żeby gra przynosiła fun

**Cel:** Lista brakujących elementów (FM + „fun factor”) – co zrobić, żeby gra była wciągająca, a nie tylko kompletna funkcjonalnie.

---

## 1. Z dokumentu FM_BRAKI_ANALIZA – co jeszcze nie mamy

| Element | Opis | Wpływ na fun |
|--------|------|----------------|
| **Play-offy** | Miejsca 3.–6. w Championship / League One / League Two grają play-offy o awans (półfinały, finał). | Duży – napięcie „gramy o awans” zamiast tylko tabeli. |
| **Dualne formacje** | Oddzielna formacja w ataku i w obronie (FM26). | Średni – głębsza taktyka, ale wymaga dużo pracy. |
| **Recruitment hub** | „Wymagania” (wiek, pozycja, rola), pitch opportunities – widok, czego szukają inne kluby. | Średni – ciekawsze transfery i planowanie. |
| **Alerty kontraktowe** | W widoku kadry / głębokości: „Kontrakt do kolejki X” lub „&lt; 12 miesięcy”. | Niski–średni – mniej zapominania o przedłużeniach. |
| **Wizualizacja taktyki** | Drugi widok formacji (np. „w obronie”) albo Tactical Visualiser. | Niski – ładniej, ale jedna formacja już daje fun. |
| **Licencje / stroje** | Loga, kit drużyn (opcjonalnie). | Niski – immersion, nie core gameplay. |

---

## 2. „Fun factor” – co oprócz parity z FM

To rzeczy, które w FM sprawiają, że **chce się grać kolejny sezon**, a nie tylko „odhaczyć” funkcje.

### 2.1 Progresja i cel

- **Wielosezonowość:** Po awansie/spadku – **automatyczne rozpoczęcie nowego sezonu** (reset kolejek, nowe terminarze, ewentualnie „Rok 2”) zamiast ręcznego „zastosuj awans” i brak kolejnego sezonu.
- **Widoczny cel:** Jedno miejsce z podsumowaniem: „Sezon 1”, „Cel: utrzymanie / awans / mistrzostwo” (np. wybór na start sezonu lub domyślny wg siły).
- **Proste osiągnięcia / kamienie milowe:** Np. „Pierwsza wygrana”, „Awans”, „Mistrzostwo” – nawet jako tekst w menu lub po meczu.

### 2.2 Feedback i poczucie wpływu

- **Wpływ taktyki na mecz:** Gracz musi **widzieć**, że zmiana formacji / składu coś zmienia (komunikaty po meczu, statystyki, komentarz asystenta). Silnik już to robi – warto to wyeksponować w UI (np. „Twój pressing dał X odzyskań”).
- **Komentarz / podsumowanie po kolejkach:** Krótki tekst: „Wygrana 2–0, awans na 5. miejsce”, „Przegrana – spadek na strefę spadkową”. Buduje narrację.
- **Asystent (AssistantTip):** Już jest – warto go pokazywać **przed** meczem w jednym miejscu (np. „Słaby bramkarz rywala – strzały z dystansu”) i ewentualnie po meczu („Sprawdziło się / nie sprawdziło”).

### 2.3 Napięcie i wyzwanie

- **Play-offy** (jak wyżej) – dodatkowe mecze „o wszystko”.
- **Ostatnie kolejki:** W UI można podkreślić „Kolejka 38 – decydująca o utrzymaniu / awansie” gdy tabela jest tight.
- **Budżet ma znaczenie:** Już jest; fun wzrośnie, jeśli **koszty kadry** (pensje z kontraktów) będą widoczne i będą ograniczać budżet na transfery – wtedy decyzje „kogo sprzedać / przedłużyć” stają się ważne.

### 2.4 Szybkie „wygrane” i rutyna

- **Szybki dostęp do „Rozegraj kolejkę”:** Już jest; warto, żeby po rozegraniu od razu było widać **wyniki innych meczów** (np. minitabela lub lista wyników kolejek) – to buduje poczucie ligi.
- **Wyniki kolejek z innych lig** (w systemie angielskim): Np. „W Championship: X wygrał z Y 2–1” – opcjonalnie, na później.

### 2.5 Spójność i polish

- **Reset sezonu po awansie/spadku:** Jedna akcja „Zakończ sezon i rozpocznij nowy” (awans/spadek + nowe kolejki + ewentualnie nowy rok) zamiast dwóch osobnych kroków.
- **Błędy / brudne rogi:** Sprawdzenie, czy po awansie/spadku tabela, terminarz i „Moja drużyna” pokazują właściwe ligi; brak crashy przy pustych danych.

---

## 3. Proponowana kolejność (na fun)

| # | Zadanie | Efekt |
|---|--------|--------|
| 1 | **Nowy sezon po awansie/spadku** (jedna akcja: apply + start nowego sezonu w systemie) | Gracz od razu gra dalej, bez „ręcznego” startu. |
| 2 | **Wyniki kolejek / minitabela** po „Rozegraj kolejkę” (nasza liga) | Widać kontekst ligi, nie tylko swój mecz. |
| 3 | **Krótki komentarz po kolejkach** („Wygrana, 5. miejsce”) | Prosta narracja, cel. |
| 4 | **Play-offy** (3.–6. miejsce, półfinały, finał) | Więcej napięcia i „meczów o awans”. |
| 5 | **Koszty kadry vs budżet** (suma pensji w widoku drużyny / kontraktów) | Decyzje transferowe i kontraktowe mają konsekwencje. |
| 6 | **Alerty kontraktowe** („Kontrakt do kolejki X” w głębokości kadry / liście) | Mniej frustracji, więcej planowania. |
| 7 | Dualne formacje, recruitment hub, licencje | Gdy podstawy „fun” są już ok. |

---

## 4. Zaimplementowane w tej iteracji

- **Koszty kadry vs budżet** – suma pensji/tydz. w TeamView i na ekranie Kontrakty.
- **Alerty kontraktowe** – w Głębokości kadry: „do kol.X” przy zawodnikach (z kontraktów).
- **Wyniki kolejki + minitabela** – po „Rozegraj kolejkę” przejście na `MatchdayResultsScreen` (wyniki tej kolejki, minitabela, przycisk „Dalej”).
- **Komentarz po kolejce** – na ekranie wyników: „Wygrana 2–0. 5. miejsce” (wynik + pozycja).
- **Nowy sezon po awansie/spadku** – jeden przycisk „Zastosuj awans/spadek i rozpocznij nowy sezon” (applyPromotionRelegation + startNextSeasonForSystem).
- **Play-offy** – dla lig tier 2–4: przyciski „Przygotuj półfinały baraży” i „Przygotuj finał baraży”; rozgrywanie baraży przez „Rozegraj kolejkę”.
- **Widok Setup** – w LeagueViewScreen przy fazie Setup: informacja „Drużyny: X / Y” (aktualna liczba / docelowa) oraz przycisk „Zaproś gracza (e-mail)”.

### Kolejna iteracja (następne etapy)

- **Cel sezonu** – w widoku drużyny (TeamView): „Cel: mistrzostwo / Europa” (tier 1) lub „Cel: awans (lub baraże)” (tier 2–4), na podstawie `league.tier`.
- **Asystent przed meczem** – na ekranie składu (SquadScreen) rada asystenta (pole `tip`) wyświetlana od razu pod tytułem „Skład na mecz”, bez konieczności klikania „Rada asystenta”.
- **Ostatnie kolejki** – w widoku ligi (LeagueViewScreen), gdy kolejka jest w ostatnich 2 (np. 37., 38.): pod tytułem pojawia się komunikat „Kolejka X – decydująca o utrzymanie / awansie”.
- **Kamienie milowe** – po rozegraniu kolejki (MatchdayResultsScreen): przy pierwszej wygranej „🎉 Pierwsza wygrana w sezonie!”; przy pierwszej przegranej „😔 Pierwsza przegrana w sezonie.”.

---

## 5. Podsumowanie

**Żeby gra przynosiła fun, warto:**

- **Dokończyć „sezon → nowy sezon”** (awans/spadek + start następnego sezonu w jednym flow).
- **Pokazać kontekst ligi** (wyniki kolejek, minitabela po rozegraniu).
- **Dać prosty feedback** (komentarz po kolejkach, czytelne podsumowanie asystenta).
- **Dodać play-offy** – więcej decydujących meczów.
- **Uczynić budżet i kontrakty „odczuwalnymi”** (koszty kadry, alerty o kończących się kontraktach).

Reszta (dualne formacje, recruitment hub, licencje) to rozbudowa w kierunku FM, ale fundamentem funu jest **progresja sezon → sezon**, **kontekst ligi** i **czytelny feedback** po meczach i kolejkach.
