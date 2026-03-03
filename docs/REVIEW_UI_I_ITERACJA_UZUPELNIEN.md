# Review UI i iteracja uzupełnień (2026-03)

## Zakres review

Przeprowadzono pełny przegląd: czy wszystkie dane z backendu są prezentowane w UI, czy formacje/taktyki są edytowalne, czy boty mają sensowne nazwy, czy eksport i tabele są interaktywne.

## Wdrożone uzupełnienia

### 1. **Team Elo w API i UI**
- **Backend:** W `TeamDto` (shared) dodano pole `eloRating: Double = 1500.0`. W `LeagueService.toTeamDto` zwracane jest `t.eloRating`.
- **UI:** Na stronie drużyny (TeamPage) wyświetlane: „Budget: X · Elo: Y”. Na liście drużyn w lidze (Setup) przy każdej drużynie: „Gracz/Bot · Elo <wartość>”.

### 2. **Podsumowanie meczu – brakujące pola**
- W sekcji „Podsumowanie meczu” (MatchDetailPage) dodano wyświetlanie:
  - **duelsWon** – „Pojedynki wygrane: H – A”
  - **aerialDuelsWon** – „Pojedynki w powietrzu: H – A”
  - **possessionLost** – „Utracone piłki: H – A”
  - **wpaFinal** – „WPA (końcowe prawd. wygranej gosp.): X%”
  - **setPiecePatternH** – informacja o komponentach NMF stałych fragmentów (H)

### 3. **Sędzia na stronie meczu**
- W nagłówku szczegółów meczu (status) dodano: „ · Sędzia: &lt;refereeId&gt;”, gdy `refereeId` jest niepusty (z `MatchDto`).

### 4. **Atrybuty „traits” gracza w UI**
- **TeamPage:** W rozwijanym bloku atrybutów zawodnika (fizyczne, techniczne, mentalne) dodano wiersz **Cechy** (`traits`), gdy niepuste.
- **MatchSquadPage:** W sekcji „Pokaż atrybuty” przy slocie dodano **Cechy** obok fizycznych/technicznych/mentalnych.

### 5. **Nazwy drużyn botów (presety)**
- W `LeagueService.addBots` zamiast stałego „Bot 1”, „Bot 2” używana jest lista presetów nazw drużyn, np.:
  - „FC Bot United”, „Bot City”, „Bot Athletic”, „Bot Wanderers”, „Bot Rovers”, „Bot FC”, „United Bots”, „Bot Dynamo”, „Bot Rangers”, „Bot Albion”, „Bot Hotspur”, „Bot Villa”, „Bot Hammers”, „Bot Eagles”, „Bot Saints”.
- Przypisanie: `(current + i) % presets.size`, więc kolejne boty dostają różne, powtarzające się nazwy z listy.

### 6. **Przycisk „Pobierz plik” przy eksporcie**
- W sekcji eksportu logów meczów (LeaguePage), gdy wynik eksportu jest dostępny, dodano przycisk **„Pobierz plik”**.
- Klik powoduje wygenerowanie pliku do pobrania (data URL: CSV lub JSON z odpowiednim MIME) i pobranie jako `match-logs-export.csv` / `match-logs-export.json`.

### 7. **Zakładki na stronie meczu**
- Na stronie szczegółów meczu (MatchDetailPage) dodano pasek zakładek: **Podsumowanie | Analityka | Zdarzenia**.
- Tylko jedna sekcja jest widoczna naraz (display block/none). Domyślnie „Podsumowanie”.

### 8. **Sortowanie tabel**
- **Tabela ligi (LeaguePage):** Nagłówki kolumn (#, Drużyna, Pkt, M, W-R-P, GZ-GS, Bilans) są klikalne; wybór kolumny i kierunku (rosnąco/malejąco) sortuje wiersze po stronie klienta.
- **Data Hub (zaawansowane):** Dodano dropdown „Sortuj po:” (Gole, Asysty, xG, Minuty, Zawodnik, Drużyna) oraz checkbox „Malejąco”; lista wierszy jest sortowana przed wyświetleniem (max 50).

### 9. **Eksport – wybór meczów z terminarza**
- W terminarzu przy każdym meczu dodano **checkbox** do zaznaczenia meczu.
- Dodano przycisk **„Dodaj wybrane do eksportu”**: zaznaczone mecze (do limitu 50 łącznie z już wpisanymi ID) są dopisywane do pola „ID meczów” w sekcji eksportu.
- Krótka instrukcja nad terminarzem wyjaśnia, że można zaznaczyć mecze i użyć tego przycisku zamiast ręcznego wklejania ID.

## Stan UI po zmianach (skrót)

| Obszar | Stan |
|--------|------|
| **Formacje / taktyka** | Ręczna edycja: MatchSquadPage (formacja, strefy, instrukcje, stałe fragmenty, OI). Zapisane taktyki na TeamPage i w MatchSquadPage (dropdown). Presety stylu: Gegenpress, Low block, Tiki-taka. |
| **Boty** | Nazwy z presetów (np. „Bot City”). Formacje/taktyki botów wyliczane w backendzie (BotSquadBuilder), nie pokazywane osobno w UI. |
| **Statystyki / atrybuty** | Podsumowanie meczu: posiadanie, strzały, xG, podania, dośrodkowania, przechwyty, faule, karty, kontuzje, rożne, wrzuty, rzuty wolne, spalone, duelsWon, aerialDuelsWon, possessionLost, wpaFinal, setPiecePatternH, Field Tilt, PPDA, VAEP. Analityka zaawansowana w osobnej zakładce. Wszystkie grupy atrybutów gracza (physical, technical, mental, traits) na TeamPage i w MatchSquadPage. |
| **Elo** | Widoczne przy drużynie (TeamPage) i na liście drużyn w lidze (Setup). |
| **Sędzia** | Wyświetlany na stronie meczu (refereeId). |
| **Eksport** | Pole ID meczów (ręcznie lub z checkboxów), format csv/json/json-full, przycisk „Pobierz plik” po eksporcie. |
| **Tabele** | Tabela ligi i Data Hub z sortowaniem po wybranej kolumnie. |

## Pliki zmienione (główne)

- `shared/src/main/scala/fmgame/shared/api/ApiDto.scala` – `TeamDto.eloRating`
- `backend/src/main/scala/fmgame/backend/service/LeagueService.scala` – `toTeamDto` z Elo, presety nazw botów w `addBots`
- `frontend/.../TeamPage.scala` – Elo, traits w atrybutach
- `frontend/.../LeaguePage.scala` – Elo w liście drużyn, sortowanie tabeli i Data Hub, eksport (checkboxy, „Pobierz plik”, `downloadExportAsFile`, `addSelectedToExport`)
- `frontend/.../MatchDetailPage.scala` – sędzia, brakujące pola podsumowania, zakładki (Podsumowanie | Analityka | Zdarzenia), `styleAttr` dla widoczności sekcji
- `frontend/.../MatchSquadPage.scala` – traits w „Pokaż atrybuty”

## Opcjonalne dalsze kroki (zrealizowane 2026-03)

- **Manager / nazwa trenera:** Zaimplementowano. W domenie `Team` i `TeamDto` dodano pole `managerName: Option[String]`. Boty dostają nazwę trenera z presetów (np. „Trener Kowalski”, „Trener Nowak”). Wyświetlanie: strona drużyny (TeamPage), lista drużyn w lidze (Setup) – przy nazwie drużyny w nawiasie.
- **Formacja/taktyka botów w UI:** W sekcji „Zapisane składy” na stronie meczu (MatchDetailPage) dla drużyny-bota wyświetlana jest linia „Formacja rywala: X-Y-Z” (np. 4-3-3), wyliczana z slotów składu (`inferFormationFromSlots`).
- **RefereeId → nazwa:** W backendzie sędziowie mają nazwę (tabela `referees`, pole `name`). W `MatchDto` dodano `refereeName: Option[String]`. W `getMatch` i `getFixtures` nazwa sędziego jest uzupełniana z `RefereeRepository`. W UI (MatchDetailPage) wyświetlane jest `refereeName` (gdy jest), w przeciwnym razie `refereeId`.
- **Wirtualizacja długich list:** Zdarzenia meczu: domyślnie pokazywane jest 50 zdarzeń, przycisk „Pokaż kolejne 50” powiększa widoczny fragment (bez ponownego ładowania). Terminarz: domyślnie 15 meczów, przycisk „Pokaż kolejne 15 z listy” oraz „Załaduj więcej meczów z serwera” (paginacja API).

### 10. Wykresy i tooltips (2026-03)

- **Wykresy na stronie meczu (Analityka):** Timeline xG (co 15') – wizualne słupki skumulowanego xG gospodarzy (niebieski) i gości (szary). VAEP per zawodnik – wykres słupkowy (poziome paski, do 12 graczy). EPV/xT strefy 1–12 – wykres słupkowy (12 słupków).
- **Tooltips przy taktyce (MatchSquadPage):** Krótkie opisy przy polach: Formacja, Styl gry, Strefy pressingu, Strefa kontry, Tempo, Szerokość, Podejście do podań, Intensywność pressingu (ikona ⓘ z `title`).
- **Błędy API:** Komunikaty 401/403/500 z backendu są przekazywane przez `ApiClient.runZio` do zmiennych `error` w komponentach i wyświetlane użytkownikowi (np. MatchDetailPage, LeaguePage, MatchSquadPage).
