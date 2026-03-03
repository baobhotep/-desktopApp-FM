# Przegląd kodu: spójność backend–frontend, jakość i wydajność

**Data:** 2026-02-23  
**Zakres:** Backend (ZIO HTTP, Doobie, silnik meczu), frontend (Laminar, ApiClient), shared (DTO), logika biznesowa.

---

## 1. Mapowanie API: Backend ↔ Frontend

### 1.1 Endpointy backendu (Routes.scala) i wywołania frontendu (ApiClient.scala)

| Backend (metoda + ścieżka) | Frontend (ApiClient) | Uwagi |
|----------------------------|----------------------|--------|
| GET /metrics | — | Celowo bez wywołania (monitoring z zewnątrz). |
| POST /auth/register | `register` | ✓ |
| POST /auth/login | `login` | ✓ |
| GET /auth/me | `me` | ✓ |
| GET /leagues | `listLeagues` | ✓ |
| GET /invitations | `listInvitations` | ✓ |
| GET /leagues/:id | `getLeague` | ✓ |
| GET /leagues/:id/table | `getTable` | ✓ |
| GET /leagues/:id/player-stats | `getLeaguePlayerStats` | ✓ |
| GET /leagues/:id/player-stats-advanced | `getLeaguePlayerAdvancedStats` | ✓ |
| GET /teams/:id/training-plan | `getTrainingPlan` | ✓ |
| PUT /teams/:id/training-plan | `upsertTrainingPlan` | ✓ |
| GET /leagues/:id/teams | `getTeams` | ✓ |
| GET /leagues/:id/players | `listLeaguePlayers` | ✓ (query: pos, minOverall, q) |
| GET /teams/:id/shortlist | `getShortlist` | ✓ |
| POST /teams/:id/shortlist | `addToShortlist` | ✓ |
| DELETE /teams/:id/shortlist/:playerId | `removeFromShortlist` | ✓ |
| GET /teams/:id/scouting-reports | `getScoutingReports` | ✓ |
| POST /teams/:id/scouting-reports | `createScoutingReport` | ✓ |
| GET /leagues/:id/fixtures | `getFixtures` (limit, offset) | ✓ |
| POST /leagues/:id/invite | `inviteToLeague` | ✓ |
| POST /invitations/accept | `acceptInvitation` | ✓ |
| POST /leagues/:id/start | `startSeason` | ✓ |
| POST /leagues/:id/add-bots | `addBots` | ✓ |
| POST /leagues/:id/matchdays/current/play | `playMatchday` | ✓ |
| GET /matches/:id | `getMatch` | ✓ |
| GET /matches/:id/log | `getMatchLog` (limit, offset) | ✓ |
| POST /matches/:id/press-conference | `submitPressConference` (query teamId) | ✓ |
| GET /matches/:id/squads | `getMatchSquads` | ✓ |
| PUT /matches/:id/squads/:teamId | `submitMatchSquad` | ✓ |
| GET /teams/:id | `getTeam` | ✓ |
| GET /teams/:id/players | `getTeamPlayers` | ✓ |
| GET /teams/:id/game-plans | `getGamePlans` | ✓ |
| GET /teams/:id/game-plans/:snapshotId | `getGamePlanSnapshot` | ✓ |
| POST /teams/:id/game-plans | `saveGamePlan` | ✓ |
| GET /leagues/:id/transfer-windows | `getTransferWindows` | ✓ |
| GET /transfer-offers (leagueId, teamId) | `getTransferOffers` | ✓ |
| POST /leagues/:id/transfer-offers | `createTransferOffer` | ✓ |
| POST /transfer-offers/:id/accept | `acceptTransferOffer` | ✓ |
| POST /transfer-offers/:id/reject | `rejectTransferOffer` | ✓ |
| PATCH /players/:id | `updatePlayer` | ✓ |
| POST /export/match-logs | `exportMatchLogs` | ✓ (zob. sekcja 2.2) |
| POST /admin/models/:kind | — | Tylko backend (X-Admin-Secret). |

**Wnioski:** Wszystkie endpointy używane przez UI mają odpowiadające metody w `ApiClient`. Brakuje tylko wywołania GET /metrics w aplikacji (celowo – monitoring).

---

## 2. Niespójności i błędy

### 2.1 Eksport logów meczów – format odpowiedzi i uprawnienia

- **Backend:** Zwraca surowy tekst z `Content-Type: text/csv; charset=utf-8` lub `application/json; charset=utf-8` i `body = Body.fromString(out)` – użytkownik otrzymuje poprawny CSV/JSON. **Zrobione.**
- **Export:** Maks. 50 meczów na jedno żądanie; sprawdzane jest, że każdy mecz należy do ligi, do której użytkownik ma dostęp (`ensureUserHasAccessToLeague`). Logi pobierane jednym wywołaniem `findByMatchIds(matchIds)`.
- **Frontend:** `exportMatchLogs` zwraca `Right(text)` – surowe body; przy poprawnym Content-Type wyświetlanie jest poprawne.

- **Frontend:** `exportMatchLogs` zwraca `Right(text)` – surowe body; przy poprawnym Content-Type wyświetlanie jest poprawne. W UI: podpowiedź „Maks. 50 meczów” i walidacja przed wysłaniem (więcej niż 50 ID → błąd „Maks. 50 meczów”).

### 2.2a UI – sterowanie planem meczu i eksportem

- **MatchSquadPage:** Wszystkie parametry game planu obsługiwane w formularzu: formacja (w tym „Własna” z custom pozycjami), strefy pressu i strefa kontrataku, role slotów, instrukcje per slot (press/tackle/mark), tempo/szerokość/podania/pressing drużynowe, stałe fragmenty (wykonawca rożnych, wolnych, karnych, **wrzutów z autu**, rutyny rożnych i wolnych), instrukcje na rywala (2 cele). Zapis przez `submitMatchSquad` z `buildGamePlanJson()`; backend parsuje m.in. `throwInConfig.defaultTakerPlayerId` i przekazuje do silnika.
- **Eksport:** LeaguePage – pole ID meczów z hintem „Maks. 50 meczów”, walidacja po stronie klienta; błędy z API (np. „Too many match IDs”, „Forbidden”) wyświetlane w `exportError`.

### 2.2 LeaguePage – ładowanie

- Martwy branch `None` w wywołaniach statystyk usunięty. **Zrobione.**
- Strona ligi używa lazy loadingu: `loadPrimary()` ładuje od razu (liga, tabela, terminarz, drużyny), `loadSecondary()` z krótkim opóźnieniem (statystyki zawodników, okna transferowe, oferty) – szybsze pierwsze wyświetlenie.

### 2.3 Game plan JSON – klucze a silnik

- **Backend** (`parseGamePlan`): Oczekuje `teamInstructions` z kluczami: `tempo`, `width`, `passingDirectness`, `pressingIntensity`.
- **Frontend** (`buildGamePlanJson`): Wysyła te klucze; sloty w formularzu to nazwy z `FormationPresets.slots(formation)` (np. `"GK"`, `"LB"`, `"ST"`), więc `slotRoles` i `playerInstructions` mają klucze = nazwy slotów. **Spójne z backendem.**
- **Silnik** (`FullMatchEngine`): Wykorzystuje `teamInstructions`, `slotRoles` i `playerInstructions` (role: bonus podań/strzałów/xG; instrukcje: pressing, passing, shooting; ważony wybór aktora i przechwytującego). **Zrobione.**

### 2.4 slotRoles – klucze w UI vs backend

- **Frontend:** W pętli po `FormationPresets.slots(formation).zipWithIndex` kluczem do `slotRoles` i `playerInstructions` jest `pos` – nazwa slotu (np. `"GK"`, `"LB"`, `"ST"`). **Zgodne z backendem i silnikiem.**

### 2.5 FullMatchEngine – co wpływa na symulację, a co nie

| Element (GamePlanInput / powiązane) | Wpływ na symulację | Uwagi |
|------------------------------------|---------------------|--------|
| **formationName** | Tak | Pozycje 22 graczy (PitchModel), Pitch Control, DxT – kształt formacji wpływa na kontrolę stref i zagrożenie. |
| **customPositions** | Tak | Nadpisuje szablon formacji; pozycje 11 slotów (x,y 0–1) używane do ustawienia i kontroli. |
| **triggerConfig.pressZones** | Tak | W strefach z listy drużyna bez piłki dostaje +15% do szansy przechwytu (interceptBonus). |
| **triggerConfig.counterTriggerZone** | Tak | Po odzyskaniu piłki w tej strefie ustawiane jest `justRecoveredInCounterZone` → 35% szans na szybki strzał (eventTypeRoll = 0.44). |
| **teamInstructions.tempo** | Tak | Wyższe tempo → szybsze zmęczenie (mnożnik 1.22); niższe → wolniejsze (0.88). |
| **teamInstructions.pressingIntensity** | Tak | Wyższy pressing → szybsze zmęczenie (1.18); niższy → wolniejsze (0.88). |
| **teamInstructions.width** | Tak | Szerokość ustawienia: narrow 0.8, wide 1.2 (PositionGenerator – rozstawienie wzdłuż linii). |
| **teamInstructions.passingDirectness** | Tak | shorter +2% szansy na udane podanie, direct −1.5%. |
| **slotRoles** | Tak | Role typu playmaker → +2% udanych podań; advanced_forward/poacher → +0.8% strzału i xG×1.05; inside_forward/winger → mniejszy bonus. |
| **playerInstructions** (per slot) | Częściowo | Używane: `pressIntensity` (more_urgent → +3% przechwytu u obrońców), `passing`, `shooting`. Nie używane w logice: `tackle`, `mark` (tylko w oppositionInstructions przy celu). |
| **setPieces.cornerTakerPlayerId / freeKickTakerPlayerId** | Tak | Określają wykonawcę rogu/wolnego. |
| **setPieces.cornerRoutine / freeKickRoutine** | Tak | Następny strzał po rogu/wolnym: near_post +5% xG, far_post +2%, short −5%, direct (FK) +8%. |
| **setPieces.penaltyTakerPlayerId** | Tak | Przy faulu w strefie 10–12 z ~18% szansą generowany jest rzut karny; wykonawca z setPieces lub actor. |
| **throwInConfig** | Tak | defaultTakerPlayerId wybiera wykonawcę wrzutu z autu w FullMatchEngine. |
| **oppositionInstructions** | Tak | Gdy aktor jest celem OI: more_urgent/harder/tighter dają łącznie do +12% szansy przechwytu dla obrońcy. |

**Podsumowanie:** Wszystkie elementy GamePlanInput (formacja, pozycje, triggery, tempo/pressing/width/passing, role, instrukcje, setPieces włącznie z penalty i routine, throwInConfig, oppositionInstructions) są używane w FullMatchEngine i wpływają na symulację.

### 2.6 Mapowanie atrybutów zawodników i ról na symulację (głębia realizmu)

Aby formacje, taktyki, strategie i role były **kluczowe** dla wyniku, silnik mapuje atrybuty i kontekst tak:

| Mechanika | Co jest używane | Wpływ |
|-----------|------------------|--------|
| **Kto dostaje piłkę (aktor)** | Strefa piłki + `slotRoles` + dopasowanie pozycji | W strefach ataku (9–12) gracze z rolą napastnika/skrzydłowego mają wyższą wagę; w budowaniu (1–6) playmakerzy/anchor. Gracz „na pozycji” (slot ∈ preferredPositions) dostaje bonus wagi. Wybór ważony → realizm (napastnicy częściej kończą akcje, pomocnicy budują). |
| **Kto przechwytuje / wygrywa tackle** | `slotRoles` obrońcy + atrybuty `tackling`, `positioning` | Role ball_winner, anchor, full_back, wing_back, no_nonsense_centre_back zwiększają wagę; wyższe tackling/positioning też. Przechwyt i DribbleLost wybierają obrońcę ważonego tymi czynnikami. |
| **Sukces podania** | `decisions`, `vision`, `passing`, `firstTouch`, rola (playmaker), dopasowanie pozycji, tempo/passingDirectness | Mentalne i techniczne atrybuty podnoszą bazę sukcesu; gra „poza pozycją” (slot ∉ preferredPositions) daje −2% do podania. |
| **xG strzału** | `finishing`, `composure`, rola (forward bonus), routine set piece | Modyfikator xG: 0.90 + (finishing/20)×0.10 + (composure/20)×0.04; role napastnika dodatkowo ×1.02–1.05; routine rogu/wolnego (near_post, direct) podbija następny strzał. |
| **Sukces crossu** | Atrybut `crossing`, zmęczenie | Baza szansy udanego crossu z crossing (np. 0.32 + (crossing−10)×0.012), cap 0.55; zmęczenie obniża. |
| **Duel / AerialDuel** | `strength` (Duel), `jumpingReach` + `strength` (AerialDuel) | Zwycięzca wybierany ważonym losem: wyższy strength / (jumpingReach+strength) = większa szansa wygrania. |
| **Zmęczenie** | `stamina`, `workRate`, teamInstructions (tempo, pressingIntensity), **freshness** | Wyższe tempo/pressing i niższa stamina → szybsze zmęczenie; workRate lekko podbija; **wyższa freshness** spowalnia narastanie zmęczenia (mnożnik 1.25−0.5×freshness). Zmęczenie obniża szansę podania i strzału (saved prob). |
| **Przewaga gospodarzy** | `MatchEngineInput.homeAdvantage` (np. 1.05) | Gdy gospodarze mają piłkę: +2% szansy na udane podanie na każde 0.05 przewagi. Gdy goście mają piłkę: wyższa szansa przechwytu (gospodarze lepiej „dopressują”). |
| **Morale** | `PlayerMatchInput.morale` (0–1) | Modyfikator podań i xG: 0.85+0.15×morale (niska morale = gorsze podania i strzały). |
| **Bramkarz (obrona)** | Atrybuty GK: `reflexes`, `handling` | Przy strzale (nie gol): szansa na Save rośnie z jakością bramkarza (0.75 + (reflexes+handling)/80, cap 1.25). Lepszy GK = więcej obronionych strzałów. |
| **Strategia (tempo / pressing)** | `teamInstructions.tempo`, `teamInstructions.pressingIntensity` | **Tempo „higher”** w strefie 8+ → +0.4% szansy na strzał (bardziej ofensywnie). **Tempo „lower”** → −0.2% (więcej cierpliwości). **Pressing „lower”** w strefach 1–5 → +1.2% szansy na udane podanie (utrzymanie piłki przy własnej bramce). |

**Podsumowanie:** Formacje i pozycje ustalają rozstawienie i Pitch Control; role i instrukcje sterują **kto** wykonuje akcje i z jakim bonusem; atrybuty fizyczne/techniczne/mentalne oraz **morale, freshness, jakość bramkarza, przewaga gospodarzy i ustawiona strategia (tempo/pressing)** bezpośrednio wpływają na sukces podań, strzałów, obron i pojedynków. Dzięki temu taktyka, jakość kadry i decyzje taktyczne gracza są kluczowe dla wyniku symulacji.

---

## 3. Jakość i spójność kodu

### 3.1 DTO i kodery

- **Shared (`ApiDto.scala`):** Jedna definicja DTO; używane po stronie backendu (Circe generic) i frontendu.
- **Frontend:** `PlayerDtoCodec` i `MatchSummaryDtoCodec` – lenient decodery (brakujące opcjonalne pola → puste mapy/listy). Zapobiega to błędom przy starszym API lub niepełnych odpowiedziach.
- **Backend:** `MatchSummaryDtoCodec` – osobna definicja; warto mieć jedną współdzieloną wersję w `shared` lub jawnie zsynchronizować z frontem.

### 3.2 Autoryzacja

- Wszystkie endpointy API (poza GET /metrics i statykami SPA) wymagają tokena; `Routes` używa `extractUserId(req)` i zwraca 401 przy braku/nieprawidłowym tokenie.
- Frontend przy operacjach wymagających logowania używa `withToken` lub `AppState.token.now()`; przy braku tokena nie wywołuje API lub pokazuje błąd. **Spójne.**

### 3.3 Obsługa błędów

- Backend: `runZio` mapuje błędy na `Either` i loguje; handler zwraca 4xx z `ErrorBody(code, message)`.
- Frontend: `fetchEither` czyta body, przy `!r.ok` próbuje decode’ować `ErrorBody` i zwraca `Left(err.message)`; w UI błędy są wyświetlane z `Var[Option[String]]`. **Spójne.**

### 3.4 Konwencje nazw i typy

- Ścieżki API: `/api/v1/...` – spójne.
- Identyfikatory (UUID/string) w DTO jako `String` – wspólne dla JS i JVM.
- Timestampy jako `Long` (epoch millis) – zgodne z komentarzem w `ApiDto`.

---

## 4. Wydajność

### 4.1 Backend – baza danych

- **Transakcje:** Wiele operacji to pojedyncze `.transact(xa).orDie`; przy złożonych flow (np. `playMatchday`) używana jest jedna transakcja `writeMatchdayInTransaction` – **dobrze**.
- **getLeaguePlayerStatsForUser / getLeaguePlayerAdvancedStatsForUser:** Używane jest jedno zapytanie `findByMatchIds(playedIds)` (batch) oraz agregacja z tabeli `league_player_match_stats` z fallbackiem do logów. **Zrobione.**
- **exportMatchLogs:** Jedno wywołanie `findByMatchIds(matchIds)` zamiast N×`findByMatchId`. **Zrobione.**

### 4.2 Backend – pamięć

- `getLeaguePlayerStatsForUser` ładuje wszystkie zdarzenia wszystkich rozegranych meczów do pamięci i agreguje. Przy bardzo dużej liczbie meczów i zdarzeń może to być kosztowne.
- **Rekomendacja:** Dla bardzo dużych lig rozważyć agregację w SQL (np. widok/materialized view) lub stronicowanie/limit liczby meczów.

### 4.3 Frontend

- Brak wirtualizacji długich list (np. terminarz, zdarzenia meczu); przy setkach elementów lista może być ciężka.
- Odpytywanie API: przy wejściu na stronę ligi wywoływanych jest wiele requestów równolegle (league, table, fixtures, teams, player stats, advanced stats, transfer windows, offers) – akceptowalne przy małej/średniej skali; przy słabym łączu można rozważyć kolejkowanie lub ładowanie „above the fold” w pierwszej kolejności.

### 4.4 Silnik meczu

- Symulacja w `FullMatchEngine` jest synchroniczna; przy `playMatchday` mecze są wykonywane sekwencyjnie (`ZIO.foreach(toPlay)(runMatchOnly)`). Dla typowej kolejki (np. 5 meczów) jest OK; równoległość mogłaby skrócić czas tylko przy bardzo ciężkim silniku.

---

## 5. Logika biznesowa

### 5.1 Składy i taktyka

- Walidacja składu: 11 zawodników, dokładnie 1 GK, wszyscy z drużyny, bez kontuzji – po stronie backendu w `submitMatchSquad`. **Spójne z UI.**
- Boty: przy braku ręcznego składu używany jest `DefaultSquadBuilder` / `ensureSquad`; `gamePlanJson` z domyślnym `parseGamePlan("{}")` → `GamePlanInput("4-3-3")`. **Spójne.**

### 5.2 Kolejka i sezon

- `playMatchday` gra tylko mecze **bieżącej** kolejki; po zapisie zwiększany jest `currentMatchday`. Regeneracja, kontuzje, okna transferowe aktualizowane w tej samej transakcji. **Logika spójna.**

### 5.3 Konferencja prasowa

- Backend: zmiana morale drużyny w zależności od `tone` (praise/criticize/calm); tylko właściciel drużyny; mecz musi istnieć, drużyna musi być stroną. Frontend wywołuje po meczu z `phase="post"`. **Spójne.**

### 5.4 Transfery

- Backend: limity 16–20 zawodników, budżet, status oferty. Frontend pokazuje listę ofert (wchodzące/wychodzące), nazwy drużyn i zawodników, przyciski akceptuj/odrzuć. **Spójne.**
- **Oferty bot → gracz:** Zaimplementowane: po rozegraniu kolejki boty mogą składać oferty na zawodników drużyn gracza (do 2 ofert na okno); UI wyświetla oferty przychodzące z `fromTeamName` / `toTeamName` / `playerName`. **Zrobione.**

---

## 6. Podsumowanie rekomendacji i status

| Priorytet | Obszar | Działanie | Status |
|-----------|--------|-----------|--------|
| Wysoki | Eksport logów | Zmienić odpowiedź eksportu: surowy tekst z Content-Type. | **Zrobione:** backend zwraca `Response(Status.Ok, headers = Headers(Header.Custom("Content-Type", text/csv | application/json)), body = Body.fromString(out))`. |
| Średni | LeaguePage | Usunąć martwy branch `None` w wywołaniach statystyk. | **Zrobione:** wywołania używają bezpośrednio `tok`. |
| Średni | Wydajność | Batch `findByMatchIds` zamiast N×`findByMatchId`. | **Zrobione:** `MatchResultLogRepository.findByMatchIds(playedIds)`; używane w `getLeaguePlayerStatsForUser` i `getLeaguePlayerAdvancedStatsForUser`. |
| Niski | Game plan | Udokumentować `slotRoles` / `playerInstructions` w silniku. | **Zrobione:** komentarze w `EngineTypes.scala` (slotRoles/playerInstructions zarezerwowane, teamInstructions używane). |
| Niski | Frontend | Przy bardzo długich listach rozważyć wirtualizację. | Do rozważenia w przyszłości. |

---

## 7. Spis plików objętych przeglądem

- **Backend:** `Routes.scala`, `LeagueService.scala` (trait + implementacja), `FullMatchEngine.scala`, `EngineTypes.scala`, `LeagueService.parseGamePlan`, `ExportFormats.scala`, `Main.scala`
- **Frontend:** `ApiClient.scala`, `MatchSquadPage.scala` (buildGamePlanJson, submit), `LeaguePage.scala` (load, export), `MatchDetailPage.scala`, `TeamPage.scala`, `PlayerDtoCodec.scala`
- **Shared:** `ApiDto.scala`

Ogólna ocena: **backend i frontend są ze sobą zgodne**; główne do poprawy to format odpowiedzi eksportu, drobne uproszczenie kodu w LeaguePage oraz optymalizacja zapytań do logów meczów przy statystykach sezonowych.
