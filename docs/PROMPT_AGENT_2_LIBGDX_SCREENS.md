# Prompt: Agent 2 – LibGDX: ekrany, menu i flow gry

## Rola

Jesteś **Agentem 2** w zespole 4 agentów. Twoja domena to **moduł desktop (LibGDX)**: okno, ekrany (login, rejestracja, menu, liga, drużyna, skład, lista meczów) oraz **wywołania rdzenia gry przez fasadę GameAPI** – wszystko w **jednym procesie**, bez HTTP. Nie implementujesz ekranu odtwarzania meczu 2D (to Agent 3) – tylko przekazujesz dane (MatchLogDto) do tego ekranu.

## Kontekst projektu

- **Architektura:** Jedna aplikacja JVM. Backend (serwisy, silnik, baza) i warstwa LibGDX w tym samym procesie. Komunikacja przez **GameAPI** (fasada od Agent 1) – wywołania metod, nie REST.
- **Cel:** Gracz widzi okno gry (LibGDX Lwjgl3), loguje się, wybiera ligę, widzi tabelę i terminarz, ustawia skład, rozgrywa kolejkę, wybiera mecz i przechodzi do ekranu odtwarzania (A3) z przekazanym MatchLogDto.

## Twoje zadania (z planu)

W **`docs/PLAN_STEAM_GAME.md`** w sekcji „Szablon zadań” masz zadania **Owner: A2**:

- **F2.1** – Moduł `desktop/` (lub `game/`): zależność od backendu, LibGDX (Lwjgl3); główna klasa uruchamia ZIO (init DB + serwisy) i LibGDX, przekazuje GameAPI do gry.
- **F2.2** – Ekran logowania: pola email/hasło, wywołanie GameAPI.login; zapis bieżącego użytkownika (UserId, UserDto); przejście do menu.
- **F2.3** – Ekran rejestracji; GameAPI.register; po sukcesie powrót lub auto-login.
- **F2.4** – Start aplikacji: brak sesji → ekran logowania; opcjonalnie „zapamiętaj” w pliku w katalogu użytkownika.
- **F3.1** – Menu główne (Wybierz ligę, Opcje, Wyloguj).
- **F3.2** – Lista lig (GameAPI.listLeagues), wybór → widok ligi.
- **F3.3** – Widok ligi: tabela (getTable), terminarz (getFixtures), przycisk „Rozegraj kolejkę”.
- **F3.4** – Widok drużyny (getTeam, getTeamPlayers).
- **F3.5** – Przed meczem: getSquads, submitSquad; ekran edycji składu.
- **F3.6** – „Rozegraj kolejkę”: GameAPI.playMatchday; odświeżenie danych.
- **F4.1** – Lista meczów z wynikami; „Obejrzyj mecz”.
- **F4.2** – GameAPI.getMatchLog(matchId); przekazanie MatchLogDto (events, summary) do ekranu odtwarzania (Screen A3).
- **F4.3** – (Opcjonalnie) Ekran podsumowania meczu.
- **F6.3** – (Opcjonalnie) Steamworks w JVM.

## Jak pracować

1. **Otwórz** `docs/PLAN_STEAM_GAME.md`. Aktualizuj Status i Notes dla zadań A2.

2. **LibGDX:** Użyj Lwjgl3 (desktop). Główna klasa (np. `GameMain`) tworzy `Lwjgl3Application` z `ApplicationListener` (Twoja klasa gry). W `create()` otrzymujesz referencję do GameAPI (przekazaną z zewnątrz po inicjalizacji ZIO w main). Ekrany = `Screen` (LibGDX); przełączanie przez `game.setScreen(new LeagueListScreen(...))`.

3. **GameAPI:** Wywołujesz metody w stylu `gameApi.login(email, password)` → `Either[String, (UserDto, Session)]`. Błędy wyświetlasz na ekranie (np. toast lub label). Sukces: zapisujesz UserId/UserDto w stanie gry (obiekt trzymany w ApplicationListener), ustawiasz ekran na MainMenuScreen.

4. **Przekazanie do ekranu meczu (A3):** Gdy użytkownik wybiera „Obejrzyj mecz”, wywołujesz `gameApi.getMatchLog(matchId)` → `MatchLogDto`. Ustawiasz ekran odtwarzania, np. `game.setScreen(new MatchPlaybackScreen(game, gameApi, matchLogDto))`. MatchPlaybackScreen (A3) otrzymuje `MatchLogDto` w konstruktorze i tylko odtwarza events – nie wywołuje API.

5. **UI w LibGDX:** Scene2D (Stage, Actor, Table, Button, Label) lub proste rysowanie. Listy (ligi, tabela, terminarz) – ScrollPane + Table z wierszami.

6. **Zależność od A1:** F2.1 i F2.2 wymagają gotowej fasady (F1.3) i punktu wejścia desktop (F1.2). W Notes wpisuj „czekam na F1.3” jeśli blokuje.

## Kryteria ukończenia (F2, F3, F4)

- **F2:** Logowanie i rejestracja w oknie gry; brak HTTP; bieżący użytkownik w pamięci.
- **F3:** Pełny flow: menu → lista lig → widok ligi (tabela, terminarz) → skład → „Rozegraj kolejkę” → odświeżenie.
- **F4:** Lista meczów z wynikami; wybór meczu → getMatchLog → przejście do ekranu odtwarzania z przekazanym MatchLogDto.

## Współpraca z Agentem 3

- Ustal interfejs ekranu odtwarzania: np. `MatchPlaybackScreen(game, matchLogDto: MatchLogDto)` – A3 implementuje ten ekran i przyjmuje events + summary. W Notes (F4.2, F5.1) wpisz sygnaturę/konstruktor, żeby A3 był spójny.

## Pliki / struktura

- Moduł: `desktop/` (lub `game/`) w repo; `build.sbt`: nowy projekt z zależnością od `backend` i LibGDX (Lwjgl3).
- Klasa main: uruchomienie ZIO (init DB, serwisy, fasada), potem `new Lwjgl3Application(new Game(api))`.
- Ekrany: np. `LoginScreen`, `MainMenuScreen`, `LeagueListScreen`, `LeagueViewScreen`, `TeamViewScreen`, `MatchSquadScreen`, `MatchListScreen`, `MatchSummaryScreen`; A3 doda `MatchPlaybackScreen`.

Aktualizuj plan na bieżąco; w Notes dopisuj zależności od A1 i interfejs z A3.
