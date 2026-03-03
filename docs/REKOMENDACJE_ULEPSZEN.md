# Rekomendacje: co jeszcze można wprowadzić, zmodyfikować i poprawić

**Data:** 2026-02-23  
**Na podstawie:** przeglądu kodu (backend, frontend, silnik), dokumentacji CODE_REVIEW.md oraz kontekstu symulacji w stylu Football Manager.

---

## 1. Silnik meczu i realizm symulacji

### 1.1 Głębia symulacji (do rozważenia)

| Propozycja | Opis | Trudność |
|------------|------|----------|
| **Rzuty karne: jakość wykonawcy i bramkarza** | **Zrobione:** xG karnego z composure/finishing wykonawcy; szansa Save z reflexes/handling bramkarza. | — |
| **Clearance: ważony wykonawca** | **Zrobione:** wykonawca clearu wybierany przez `pickWeighted(..., interceptorWeight)` (rola defensywna + tackling/positioning). | — |
| **Dribble: atrybuty dribbling / balance** | Szansa na udany Dribble lub DribbleLost zależna od dribbling (atakujący) vs tackling (obrońca). | Średnia |
| **Strzały z głowy (isHeader)** | ShotContext ma `isHeader`; formuła xG go uwzględnia (headerPenalty). W `generateNextEvent` przy strzale po crossie można losowo ustawiać `isHeader = true` i ewentualnie modyfikować xG przez atrybut heading. | Średnia |
| **Offside: defensive line / tempo** | Częstotliwość lub skuteczność offside’ów zależna od teamInstructions (np. wyższa linia obrony + pressing). | Średnia |
| **Formacja vs formacja (matchup)** | Prosty mnożnik do kontroli stref lub szansy przechwytu w zależności od pary formacji (np. 3-5-2 vs 4-4-2 w środku boiska). | Średnia |
| **Mentality (attacking/balanced/defensive)** | Rozszerzyć `TeamInstructions` o pole `mentality`; w silniku przesuwać rozkład zdarzeń (więcej strzałów przy attacking, więcej przechwytów przy defensive). | Średnia |

### 1.2 Spójność i poprawność

| Element | Status / rekomendacja |
|---------|------------------------|
| **Slot names w UI** | Frontend używa nazw slotów z `FormationPresets.slots(formation)` (np. "GK", "LB", "ST") jako kluczy do `slotRoles` i `playerInstructions`. Backend w silniku używa `lineup.get(actor.player.id)` → nazwa slotu. **Spójne.** |
| **Eksport logów** | Backend zwraca surowy tekst. **Formaty:** `csv` (zdarzenia), `json` (StatsBomb-like), **`json-full`** (zdarzenia + pełny MatchSummary per mecz). Content-Type: `text/csv` lub `application/json`. Frontend: wyświetlanie + przycisk „Pobierz plik” (zrobione). | — |

---

## 2. Backend: logika, API, baza

### 2.1 Wydajność

| Miejsce | Rekomendacja |
|---------|--------------|
| **exportMatchLogs** | **Zrobione:** jedno `findByMatchIds(matchIds)`, limit 50 meczów, sprawdzenie uprawnień (`ensureUserHasAccessToLeague`). |
| **getLeaguePlayerStatsForUser** | Już używa SQL agregacji z `league_player_match_stats` z fallbackiem do logów; batch `findByMatchIds` dla fallbacku – **zrobione**. |
| **Duże ligi** | Przy bardzo dużej liczbie meczów/zdarzeń rozważyć stronicowanie lub limit w zapytaniach do logów (np. ostatnie N kolejek). |

### 2.2 Walidacja i bezpieczeństwo

| Obszar | Rekomendacja |
|--------|--------------|
| **Export match-logs** | Ograniczyć liczbę `matchIds` w jednym żądaniu (np. max 50) oraz sprawdzać, że mecze należą do lig użytkownika (żeby nie eksportować cudzych danych). |
| **submitMatchSquad** | **Zrobione:** limit `gamePlanJson` 20 KB; przy przekroczeniu zwracany błąd „Game plan JSON too long (max 20 KB)”. |
| **createTransferOffer** | Sprawdzenie budżetu i limitu składu po stronie kupującego jest; po stronie sprzedającego – czy zawodnik nie jest już w trakcie transferu (np. inna oczekująca oferta). |

### 2.3 Jakość kodu

| Element | Rekomendacja |
|---------|--------------|
| **Routes.scala – uploadEngineModel** | W bloku `req.body.asChunk.flatMap { chunk =>` zmienna `body` jest używana w `runZio(leagueService.uploadEngineModel(..., body))`; w poprzedniej wersji bywało `body` niezdefiniowane – sprawdzić, że `val body = chunk.toArray` jest przed `runZio`. (Obecnie wygląda poprawnie.) |
| **LeagueService – parseGamePlan** | Dla nieprawidłowego JSON zwracany jest domyślny `GamePlanInput("4-3-3")`. Warto logować ostrzeżenie przy błędzie parsowania. |

---

## 3. Frontend i UX

### 3.1 Spójność z backendem

| Element | Status / rekomendacja |
|---------|------------------------|
| **Format eksportu** | Frontend i backend obsługują: "csv", "json" (statsbomb), "json-full" (zdarzenia + pełny summary per mecz). Etykiety w UI (LeaguePage, MatchDetailPage „Pobierz analitykę (JSON)”) zgodne z backendem. |
| **Eksport – wyświetlanie** | Przy CSV długi tekst z nowymi liniami – w części przeglądarek może być czytelny; opcjonalnie: przycisk „Pobierz .csv” z blob URL i `download` attribute. |

### 3.2 UX i dostępność

| Propozycja | Opis |
|------------|------|
| **Wirtualizacja długich list** | Terminarz, zdarzenia meczu, lista zawodników – przy setkach elementów listę można wirtualizować (np. renderować tylko widoczne wiersze). |
| **Tooltips / podpowiedzi** | W formularzu składu/taktyki już są placeholdery (np. "more_urgent", "harder"); można dodać krótkie opisy pod polami (np. „Pressing: bardziej agresywny przy odbiorze”). |
| **Komunikaty błędów** | Sprawdzić, że błędy API (401, 400, 500) są wszędzie wyświetlane w jednym miejscu (np. `error.set(Some(msg))`) i że użytkownik widzi konkretny powód (np. „Nieprawidłowy token” vs „Brak uprawnień”). |
| **Ładowanie strony ligi** | loadPrimary / loadSecondary z opóźnieniem (setTimeout) – **zaimplementowane**; można dostroić opóźnienie lub dodać skeleton/loader dla sekcji „Pobieranie…”. |

### 3.3 Dane wyświetlane

| Propozycja | Opis |
|------------|------|
| **Statystyki meczu (MatchDetailPage)** | Wyświetlać skrótowe statystyki (posiadanie, strzały, xG) z `analytics` z logu meczu, jeśli backend je zwraca. |
| **Król strzelców / lider asyst** | Strona ligi ładuje `getLeaguePlayerStats` – upewnić się, że tabela „Król strzelców” i „Lider asyst” są widoczne i zrozumiale podpisane. |

---

## 4. Dokumentacja i utrzymanie

### 4.1 CODE_REVIEW.md

| Sekcja | Rekomendacja |
|--------|--------------|
| **§2.1 Eksport logów** | Zaktualizować: backend już zwraca surowy tekst z Content-Type (zrobione). |
| **§2.2 LeaguePage** | Zaktualizować: martwy branch `None` usunięty; można dopisać, że jest lazy loading (loadPrimary/loadSecondary). |
| **§2.3 / §2.4 slotRoles, playerInstructions** | Zaktualizować: silnik **wykorzystuje** slotRoles i playerInstructions (role, instrukcje per slot); frontend wysyła klucze = nazwy slotów (np. "LB", "ST") – spójne. |
| **§4.1 getLeaguePlayerStats** | Zaktualizować: używany jest batch `findByMatchIds` i agregacja SQL (`league_player_match_stats`) z fallbackiem. |
| **§5.4 Transfery** | Dopisać: zaimplementowano oferty bot → gracz (bot składa ofertę do drużyny gracza). |

### 4.2 Nowe / brakujące dokumenty

| Dokument | Zawartość |
|----------|-----------|
| **README.md** | Krótki opis projektu, wymagania (JVM, Node/sbt), uruchomienie (backend, frontend, ewentualnie `./run-dev.sh`), zmienne środowiskowe. |
| **.env.example** | Przykładowe zmienne: `PORT`, `JWT_SECRET`, `DB_URL`, `FRONTEND_DIR`, `X_ADMIN_SECRET` (opcjonalnie). |
| **ALGORYTMY_SILNIKA.md** (opcjonalnie) | Jednostronicowe podsumowanie: jakie wejścia (formacja, role, atrybuty, morale, itd.) wpływają na jakie zdarzenia i prawdopodobieństwa. |

---

## 5. Podsumowanie priorytetów

| Priorytet | Działanie | Efekt |
|-----------|-----------|--------|
| **Wysoki** | Export: użyć `findByMatchIds` w `exportMatchLogs` | Mniej zapytań do DB przy eksporcie wielu meczów. |
| **Wysoki** | Export: limit `matchIds` + sprawdzenie uprawnień do meczów | Bezpieczeństwo i ochrona przed nadużyciami. |
| **Średni** | Penalty: xG z composure wykonawcy i reflexes GK | Większy realizm stałych fragmentów. |
| **Średni** | Clearance: ważony wykonawca (rola + atrybuty) | Spójność z resztą silnika (ważone wybory). |
| **Średni** | Aktualizacja CODE_REVIEW.md (eksport, LeaguePage, slotRoles, batch, transfery) | Dokumentacja odzwierciedla aktualny stan. |
| **Niski** | Dribble / heading / offside w silniku | Więcej głębi bez przebudowy. |
| **Niski** | README + .env.example | **Zrobione** (README.md, .env.example). |
| **Niski** | Frontend: przycisk „Pobierz CSV”, wirtualizacja list, tooltips | Lepszy UX. |

---

## 6. Szybki przegląd linii krytycznych (wybór)

- **FullMatchEngine:** wybór aktora, przechwyt, podanie, strzał, cross, duel, karne, zmęczenie – wszystkie używają już wag/atrybutów/roli; brakuje tylko penalty (jakość wykonawcy/GK), clearance (ważony) i ewentualnie dribble/header.
- **LeagueService.exportMatchLogs:** jedna pętla `findByMatchId` – warto zamienić na `findByMatchIds`.
- **LeagueService.submitMatchSquad / playMatchday:** walidacja składu i transakcje w porządku; gamePlanJson bez limitu długości.
- **Routes:** export zwraca `Body.fromString(out)` z poprawnym Content-Type; admin models – `body` zdefiniowane przed `runZio`.
- **Frontend MatchSquadPage:** sloty z `FormationPresets.slots(formation)` – nazwy slotów; buildGamePlanJson wysyła slotRoles i playerInstructions z tymi kluczami – **zgodne z backendem**.

Jeśli wskażesz, które punkty chcesz wdrożyć w pierwszej kolejności (np. tylko silnik, tylko backend, tylko dokumentacja), mogę zaproponować konkretne zmiany w plikach krok po kroku.
