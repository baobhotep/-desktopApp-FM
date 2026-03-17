# UI Roadmap – gra komercyjna (skala światowa)

Cel: **pełna, elegancka, nowoczesna** gra menedżerska z kompletnym UI – formacje, edycja zawodników, zróżnicowani gracze z rolami i atrybutami, wszystkie statystyki i wizualizacje.

---

## 1. Zasady ogólne UI

- **Czytelność:** czcionka z polskimi znakami, hierarchia typograficzna (tytuły / nagłówki / body), kontrast.
- **Spójność:** jedna paleta kolorów, te same odstępy (np. 8/16/24 px), sekcje z tłem.
- **Interakcja:** przyciski z feedback (hover, pressed), formularze z walidacją, potwierdzenia przy nieodwracalnych akcjach.
- **Responsywność:** ScrollPane tam, gdzie lista może być długa; minimalna szerokość okna 1280×720.

---

## 2. Zawodnicy

### 2.1 Generowanie (backend – już jest)

- **Różnorodni:** szablony pozycji (CB, LB/RB, CM/CDM, CAM, skrzydłowi, ST) z różnymi wagami atrybutów (PlayerGenerator.positionTemplates).
- **Role:** sloty formacji (GK, LB, CB, CDM, CAM, LW, ST…) z wagami atrybutów pod slot (BotSquadBuilder.slotAttributeWeights).
- **Atrybuty:** physical, technical, mental, traits (1–20); nazwy po polsku w UI.

### 2.2 Wyświetlanie

- **Karta zawodnika (PlayerCard):** zdjęcie/avatar (placeholder), imię i nazwisko, pozycje, overall (obliczony), kluczowe atrybuty (np. 6–8), forma/morale (pasek lub %), kontuzja.
- **Lista zawodników:** tabela z kolumnami: #, Imię, Nazwisko, Pozycje, Overall, Kluczowe statystyki (np. gole, asysty w sezonie), Akcje (Edytuj / Skład).

### 2.3 Edycja zawodnika

- **Ekran edycji:** imię, nazwisko, pozycje (multi-select: GK, CB, LB, RB, DM, CM, AM, LW, RW, ST).
- **Atrybuty:** grupy Fizyczne / Techniczne / Mentalne / Cechy; każdy atrybut 1–20 (suwak lub pole liczbowe), z krótką etykietą (np. „Prędkość”, „Podania”).
- **Zapis:** przycisk „Zapisz”; walidacja 1–20; tylko właściciel drużyny.

---

## 3. Formacja i taktyka

### 3.1 Ustawienie formacji

- **Presety:** 4-3-3, 4-4-2, 4-2-3-1, 3-5-2, 3-4-3, 4-1-4-1, 4-4-2 diament, 5-4-1 (nazwy + domyślne sloty jak w FORMACJE_ROLE_TAKTYKA.md).
- **Widok boiska:** boisko 2D (prostokąt); 11 slotów (10 polowych + GK) jako „kropki” lub ikony; przeciąganie (drag-and-drop) zmienia pozycję slotu; współrzędne (x, y) 0–1 zapisywane w gamePlanJson.
- **Sloty:** etykiety (GK, LB, LCB, RCB, RB, CDM, LCM, RCM, LW, RW, ST itd.); po wyborze presetu pozycje ustawiane automatycznie (np. DefaultPositions433).

### 3.2 Role (opcjonalnie w MVP+)

- Per slot: wybór roli z listy (np. Anchor, Box-to-Box, Winger, Advanced Forward); zapis w gamePlanJson jako slotRoles.

### 3.3 Instrukcje drużynowe (opcjonalnie)

- Tempo, szerokość, styl podań, pressing – suwaki lub listy; zapis w gamePlanJson (teamInstructions).

### 3.4 Zapis i wczytanie planu

- **Zapisz plan:** nazwa (np. „4-3-3 ofensywny”); zapis do GamePlanSnapshot; lista zapisanych planów przy drużynie.
- **Wczytaj plan:** wybór z listy snapshotów → ustawienie formacji + opcjonalnie ról/instrukcji w UI.

---

## 4. Skład na mecz

- **Źródło:** getMatchSquads; dla „mojej” drużyny: lineup (playerId → positionSlot).
- **Widok:** lista 11 (lub 18 z rezerwowymi) z nazwiskiem zawodnika (getTeamPlayers → mapowanie playerId → imię/nazwisko), pozycja slotu, przycisk „Zmień” (wybór zawodnika z kadry na ten slot).
- **Formacja:** przycisk „Ustaw formację” → ekran formacji (presety + drag); po zatwierdzeniu generowany gamePlanJson (formationName, customPositions, slotRoles).
- **Zatwierdź skład:** submitMatchSquad(lineup, gamePlanJson).

---

## 5. Statystyki i wizualizacje

### 5.1 Tabela ligi (już jest)

- Pozycja, drużyna, mecze, W/R/P, bramki, punkty; wyróżnienie „mojej” drużyny.

### 5.2 Statystyki sezonowe ligi

- **Król strzelców / Lider asyst:** getLeaguePlayerStats → topScorers, topAssists (lista: zawodnik, drużyna, gole/asysty).
- **Zaawansowane (Data Hub):** getLeaguePlayerAdvancedStats → tabela: zawodnik, drużyna, mecze, minuty, gole, asysty, strzały, xG, kluczowe podania, podania, przechwyty, interwencje; sortowanie po kolumnie.

### 5.3 Wizualizacje

- **Wykres słupkowy:** np. gole drużyn w lidze (oś X: drużyna, Y: gole).
- **Wykres kołowy / pasek:** posiadanie w meczu (z MatchSummaryDto).
- **Heatmapa (opcjonalnie):** np. aktywność w strefach (pressByZoneHome/Away, influenceByPlayer) – uproszczona siatka 6×4.
- **Prognoza meczu:** getMatchdayPrognosis → P(wygrana 1), P(remis), P(wygrana 2) – wyświetlenie przy terminarzu lub w podglądzie meczu.
- **Rada asystenta:** getAssistantTip przed meczem – tip, formacja rywala, mocne/słabe strony, sugestie taktyczne.

### 5.4 Statystyki meczu (po meczu)

- MatchSummaryDto: posiadanie, strzały, xG, podania, faule, kartki, rogi itd. – sekcje z liczbami; opcjonalnie mini-wykresy (np. posiadanie w czasie z possessionBySegment).

---

## 6. Przepływ ekranów (desktop)

1. **Logowanie / Rejestracja** (już jest).
2. **Menu główne:** Wybierz ligę, Nowa liga, Moja drużyna (→ TeamView), Opcje, Wyloguj.
3. **Lista lig** → wybór ligi → **Widok ligi:** tabela, terminarz, Rozegraj kolejkę, Moja drużyna, Statystyki ligi (→ LeagueStatsScreen).
4. **Moja drużyna (TeamView):** nazwa, budżet, Elo; lista zawodników (karty lub tabela); przyciski: Edytuj zawodnika, Skład na mecz (→ wybór meczu → SquadScreen).
5. **Skład na mecz (SquadScreen):** lista lineup z nazwiskami; Ustaw formację (→ FormationEditorScreen); Zapisz plan (opcjonalnie); Zatwierdź skład.
6. **Edytor formacji (FormationEditorScreen):** boisko 2D, presety, drag slotów; role (opcjonalnie); Zapisz / Wczytaj plan; Przygotuj do składu (→ powrót do Squad z wypełnionym gamePlanJson).
7. **Edycja zawodnika (PlayerEditorScreen):** formularz atrybutów + imię/nazwisko/pozycje; Zapisz.
8. **Statystyki ligi (LeagueStatsScreen):** król strzelców, asysty, zaawansowane statystyki; wykresy (słupki); link do porównania zawodników (ComparePlayers) jeśli API jest.
9. **Mecz:** Terminarz → Obejrzyj mecz (podsumowanie → odtworzenie); przed meczem: Rada asystenta, Prognoza.

---

## 7. Technologie (desktop LibGDX)

- **Sceny:** Scene2D (Table, Label, TextButton, TextField, SelectBox, Slider, ScrollPane, CheckBox).
- **Boisko 2D:** własny Actor lub Stage z prostokątem boiska i 11 „SlotActor” z obsługą drag (touchDragged / mouseDragged); współrzędne 0–1 → piksele.
- **Wykresy:** rysowanie przez SpriteBatch + ShapeRenderer (proste słupki, koła); lub minimalna biblioteka (np. wykresy w formie tekstu ASCII dla MVP).
- **gamePlanJson:** struktura zgodna z backendem (GamePlanInput): formationName, customPositions, slotRoles, teamInstructions; serializacja Circe w shared/backend.

---

## 8. Kolejność wdrożenia (sprinty)

| Sprint | Zadania |
|--------|--------|
| 1 | GameAPI + GameFacade: getLeaguePlayerStats, getLeaguePlayerAdvancedStats, listGamePlanSnapshots, getGamePlanSnapshot, saveGamePlan, getMatchdayPrognosis, getAssistantTip, updatePlayer (z atrybutami). |
| 2 | PlayerEditorScreen (formularz atrybutów); rozszerzenie UpdatePlayerRequest + backend updateAttributes. |
| 3 | FormationEditorScreen (boisko 2D, presety, pozycje → gamePlanJson); integracja ze SquadScreen. |
| 4 | SquadScreen: nazwiska z getTeamPlayers, przycisk „Ustaw formację”, zapis/wczytanie gamePlan. |
| 5 | LeagueStatsScreen: król strzelców, asysty, zaawansowane statystyki, tabela; prognoza przy meczu. |
| 6 | Wizualizacje: wykres słupkowy (gole drużyn), posiadanie w meczu; Rada asystenta w SquadScreen lub przed meczem. |
| 7 | Dopracowanie: PlayerCard komponent, porównanie zawodników (ComparePlayers), więcej presetów formacji, instrukcje drużynowe w UI. |

---

## 9. Jakość komercyjna

- **Brak placeholderów „TODO”** w widocznych miejscach dla użytkownika.
- **Komunikaty błędów** po polsku; przyciski „Wstecz” tam, gdzie użytkownik może się „zgubić”.
- **Spójne nazewnictwo** (np. „Rozegraj kolejkę”, „Moja drużyna”, „Ustaw skład”, „Zapisz plan”).
- **Testy E2E** dla krytycznych ścieżek: logowanie → liga → skład → zatwierdzenie → rozegranie kolejki → statystyki.
