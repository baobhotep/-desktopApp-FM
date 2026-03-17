# Analiza: co ma Football Manager, czego my jeszcze nie mamy

**Data:** 2025-03  
**Źródła:** FM Scout, Guide to FM, FM26 features, FM-Arena (condition/sharpness).

---

## 1. Zaimplementowane u nas (stan po ostatnich zmianach)

| Funkcja | Backend | UI |
|---------|---------|-----|
| **Overall zawodnika (1–20)** | `PlayerOverall.overall(p)` – pozycyjny (wagi atrybutów zależne od pozycji) | OVR w liście zawodników, w edytorze |
| **Paski kategorii atrybutów** | `physicalAvg`, `technicalAvg`, `mentalAvg`, `defenseAvg` w PlayerDto | Fiz / Tech / Men / Obr (wartość/20 + pasek) w TeamView i PlayerEditor |
| **Atrybuty 1–20** (fizyczne, techniczne, mentalne) | Player.physical/technical/mental | Edycja w PlayerEditor, sliders |
| **Freshness, morale, kontuzje** | Player.freshness, morale, injury | Wyświetlane w DTO (można rozbudować UI) |
| **Liga angielska 4 szczeble** | createEnglishLeagueSystem, startSeasonForSystem | CreateEnglishLeagueScreen, grupowanie w liście lig |
| **Scouting, shortlist, raporty** | ScoutingService, shortlist, createScoutingReport | ScoutingScreen |
| **Transfery, oferty, budżet** | TransferService, budżet drużyny | TransfersScreen |
| **Statystyki zaawansowane (xG, xT, itd.)** | FullMatchEngine, MatchSummaryAggregator | LeagueStatsScreen, porównanie zawodników |
| **Formacje, plany gry** | GamePlan, sloty pozycji | SquadScreen, zapis/load planów |

---

## 2. Czego brakuje względem FM (backend i/lub UI)

### 2.1 Kondycja i forma (Condition / Match Sharpness)

- **FM:** Condition (0–100%), Match Sharpness (ukryty 0–10 000) – wpływ na skuteczność taktyczną i ryzyko kontuzji. Kondycja spada w meczu, regeneruje się przy odpoczynku.
- **U nas:** Dodano `condition: Double` (0–1) i `matchSharpness: Double` (0–1) w Player; kolumny w DB; po meczu – spadek kondycji / wzrost ostrości dla grających, regeneracja kondycji między kolejkami; w UI – paski Kond % i Ostr % w TeamView i PlayerEditor. ✅

### 2.2 Dualne formacje (in possession / out of possession)

- **FM (FM26):** Oddzielna formacja w ataku i w obronie, Tactical Visualiser.
- **U nas:** Jedna formacja (układ slotów). Brak rozróżnienia „in possession” / „out of possession”.
- **Rekomendacja:** Dłuższy projekt – rozszerzenie GamePlan o drugi układ lub osobne instrukcje.

### 2.3 Awans / spadek ✅ Zaimplementowane

- **FM:** Automatyczne przenoszenie drużyn między poziomami lig po zakończeniu sezonu.
- **U nas:** `applyPromotionRelegation(systemName, userId)` – gdy we wszystkich ligach systemu `currentMatchday >= totalMatchdays`, przenosi po 3 drużyny między poziomami (3 ostatnie → niżej, 3 pierwsze → wyżej). W UI: przycisk „Awans / spadek (zastosuj)” w widoku ligi, gdy sezon zakończony i liga ma `leagueSystemName`.

### 2.4 Play-offy ✅ Zaimplementowane

- **FM:** Miejsca 3.–6. w Championship/League One/League Two grają play-offy o awans.
- **U nas:** `createPlayOffSemiFinals(leagueId, userId)` – tworzy półfinały (3. vs 6., 4. vs 5.) z matchday = totalMatchdays+1; `createPlayOffFinal(leagueId, userId)` – finał (zwycięzcy półfinałów) z matchday = totalMatchdays+2. `playMatchday` dopuszcza rozgrywkę kolejek barażowych (liga Finished, tier 2–4). W UI: przyciski „Przygotuj półfinały baraży” i „Przygotuj finał baraży” w LeagueViewScreen gdy sezon zakończony i liga tier 2–4.

### 2.5 Squad depth / planowanie kadry ✅ Zaimplementowane

- **FM (FM26):** Squad Planner – podświetlanie braków w kadrze, kontrakty (np. „< 12 miesięcy”).
- **U nas:** Ekran **„Głębokość kadry”** (SquadDepthScreen) – przycisk w widoku drużyny; tabela: pozycja (GK, CB, …) → liczba zawodników → lista nazw z OVR; podświetlenie [BRAK] / [SŁABO] przy GK.
- **Do zrobienia:** Alerty kontraktowe (gdy dodamy kontrakty).

### 2.6 Kontrakty (długość, wygaśnięcie) ✅ Zaimplementowane

- **FM:** Długość kontraktu, data końca, negocjacje.
- **U nas:** Model Contract (playerId, teamId, weeklySalary, startMatchday, endMatchday, releaseClause); tabela `contracts` tworzona przy inicjalizacji bazy; kontrakty zapisywane przy tworzeniu ligi angielskiej i przy `startSeason`; API `getTeamContracts`; ekran „Kontrakty” w widoku drużyny (zawodnik, pensja/tydz., do kolejki, klauzula).

### 2.7 Recruitment hub / wymagania transferowe

- **FM (FM26):** Centralny hub rekrutacji, „wymagania” (wiek, pozycja, rola), pitch opportunities.
- **U nas:** Scouting, shortlist, oferty – brak „broadcast needs” i widoku wymagań innych klubów.
- **Rekomendacja:** Rozszerzenie scoutingu o filtry „potrzeby drużyny” i ewentualnie API „oferty do nas” (pitch opportunities).

### 2.8 Wizualizacja taktyki (Tactical Visualiser)

- **FM:** Cztery widoki formacji (combined, side-by-side, in/out of possession).
- **U nas:** Układ składu (SquadScreen) – jeden układ.
- **Rekomendacja:** Opcjonalnie drugi widok (np. „w obronie”) gdy wprowadzimy dualne formacje.

### 2.9 Licencje (logotypy, stroje)

- **FM:** Oficjalne ligi, logo, stroje.
- **U nas:** Generowane nazwy drużyn (EnglishLeaguePreset), brak kitów i logo.
- **Rekomendacja:** Zewnętrzne assety (obrazy) – poza zakresem logiki.

### 2.10 Kobiety / inne ligi

- **FM:** Piłka żeńska, wiele lig.
- **U nas:** Jedna płeć, konfigurowalne ligi (w tym angielski system).
- **Rekomendacja:** Długoterminowo – osobny model lub flagi.

---

## 3. Podsumowanie priorytetów

| Priorytet | Element | Trudność | Opis |
|-----------|---------|----------|------|
| 1 | Awans/spadek | ✅ | applyPromotionRelegation + przycisk w LeagueView. |
| 2 | Kontrakty (expiry) | ✅ | Tabela contracts w init, persist przy startSeason/createEnglish, ContractsScreen. |
| 3 | Condition / sharpness | Niska | Rozszerzenie Player + aktualizacja po meczach; pasek w UI. |
| 4 | Squad depth view | Niska | Ekran „głębokość kadry” po pozycjach. |
| 5 | Play-offy | ✅ | createPlayOffSemiFinals, createPlayOffFinal, rozgrywka przez „Rozegraj kolejkę”. |
| 6 | Dualne formacje | Wysoka | Rozbudowa GamePlan i silnika. |
| 7 | Recruitment hub (FM26-style) | Wysoka | Wymagania, pitch opportunities. |

---

## 4. Zrobione w tej iteracji (overall + paski + condition + squad depth)

- **PlayerOverall:** overall pozycyjny (wagi per pozycja: GK, CB, LB/RB, DM, CM, AM, LW/RW, ST, CDM, CAM), physicalAvg, technicalAvg, mentalAvg, defenseAvg (tackling, marking, positioning, anticipation, concentration).
- **PlayerDto:** pola `overall`, `physicalAvg`, `technicalAvg`, `mentalAvg`, `defenseAvg` (domyślnie 0); backend uzupełnia w `toPlayerDto(p)`.
- **UI:** TeamViewScreen – dla każdego zawodnika: OVR + paski Fiz / Tech / Men / Obr (wartość/20 + ProgressBar). PlayerEditorScreen – na górze podsumowanie: Overall + 4 paski (z DTO).
- **Frontend (API):** PlayerDtoCodec – dekodowanie nowych pól z domyślnymi 0.0.

**Kondycja i ostrość meczowa (condition / matchSharpness):**
- **Player:** pola `condition`, `matchSharpness` (0–1); migracja DB (ALTER TABLE players).
- **PlayerRepository:** odczyt/zapis condition, match_sharpness; `updateConditionSharpness`.
- **LeagueService:** po meczu – lineup: condition −0.12, matchSharpness +0.03; niegrający: sharpness −0.01; regeneracja między kolejkami: condition +0.18.
- **PlayerDto / toPlayerDto:** condition, matchSharpness; UI: paski „Kond %” i „Ostr %” w TeamView i PlayerEditor.

**Głębokość kadry:**
- **SquadDepthScreen:** tabela pozycja → liczba → zawodnicy (nazwa, OVR); przycisk „Głębokość kadry” w TeamViewScreen.
