# Liga angielska (4 szczeble) i poprawki UX

## Zrealizowane

### UX (łatwa, przyjemna, elegancka obsługa)
- **Zamknij grę** – przycisk w menu głównym zamykający aplikację (`Gdx.app.exit()`).
- **Spójne odstępy** – stałe `Assets.padSection` (16px) i `Assets.padControl` (8px) do używania w ekranach.
- **Overall i paski atrybutów** – overall zawodnika (1–20) liczony z pozycji i kluczowych atrybutów; w widoku drużyny i edytorze: OVR + paski Fiz / Tech / Men / Obr (wartość/20). Szczegóły w docs/FM_BRAKI_ANALIZA.md.
- Zgodne z praktykami: czytelność, hierarchia wizualna, spójność (ciemny motyw, style).

### Backend: system ligi angielskiej
- **Schemat**: W tabeli `leagues` dodano kolumny `league_system_name` (VARCHAR 64, opcjonalnie) i `tier` (INT, opcjonalnie). Migracja przez `ALTER TABLE` przy starcie.
- **Struktura**: 4 szczeble jak w rzeczywistości:
  - **Tier 1**: Premier League – 20 drużyn
  - **Tier 2**: Championship – 24 drużyny
  - **Tier 3**: League One – 24 drużyny
  - **Tier 4**: League Two – 24 drużyny
- **createEnglishLeagueSystem(userId, myTeamName)** – tworzy 4 ligi, 92 drużyny (nazwy z presetu `EnglishLeaguePreset`), gracz dostaje drużynę w Premier League, dla każdej drużyny generowani są piłkarze (`PlayerGenerator`).
- **startSeasonForSystem(systemName, userId)** – uruchamia sezon we wszystkich ligach danego systemu (np. `"English"`).
- **teamCount** w zwykłym `create` rozszerzone do 10–24 (parzyste), żeby obsłużyć 20 i 24.

### UI
- **Menu główne**: przycisk „Nowa liga angielska (4 szczeble)”.
- **CreateEnglishLeagueScreen**: jedno pole „Nazwa Twojej drużyny”, przycisk „Utwórz ligę angielską i rozpocznij sezon” wywołuje `createEnglishLeagueSystem` → `startSeasonForSystem("English")` i przechodzi do widoku Premier League (ligi użytkownika).
- Lista lig („Wybierz ligę”) grupuje ligi po `league_system_name`: ligi bez systemu na górze, potem sekcje typu „English (4 szczeble)” z ligami posortowanymi wg `tier` (Premier League, Championship, League One, League Two).

## Do zrobienia w przyszłości (np. względem FM)

Z researchu (Football Manager):
- **Dual formations** – oddzielna formacja w ataku i w obronie (FM26).
- **Awans/spadek** – po zakończeniu sezonu przenoszenie drużyn między ligami w systemie (Premier ↔ Championship ↔ League One ↔ League Two).
- **Play-offy** – 3.–6. miejsce w Championship/League One/League Two, zwycięzca play-offów awansuje.
- **Finanse i budżet** – głębszy model (np. FM26 Smarter Transfers).
- **Widok „Data Hub”** – rozbudowane statystyki i wizualizacje (częściowo: mamy zaawansowane statystyki ligowe i porównanie zawodników).

## Jak testować

1. Uruchom desktop: `sbt "desktop/run"`.
2. Zaloguj się (lub zarejestruj).
3. Kliknij **„Nowa liga angielska (4 szczeble)”**.
4. Wpisz nazwę drużyny (np. „My FC”) i **„Utwórz ligę angielską i rozpocznij sezon”**.
5. Po chwili (tworzenie 92 drużyn i graczy) powinieneś trafić do widoku Premier League – tabela, terminarz, „Rozegraj kolejkę”, „Moja drużyna”.
6. W „Wybierz ligę” w menu widzisz Premier League; w bazie są też Championship, League One, League Two (na razie dostęp przez listę lig, jeśli użytkownik ma tam inne ligi – docelowo można dodać widok „System angielski” z 4 ligami).
