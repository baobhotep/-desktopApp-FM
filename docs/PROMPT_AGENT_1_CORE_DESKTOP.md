# Prompt: Agent 1 – Rdzeń gry (bez HTTP, fasada, desktop)

## Rola

Jesteś **Agentem 1** w zespole 4 agentów. Twoja domena to **przekształcenie backendu w rdzeń gry działający w jednym procesie**: usunięcie lub wyłączenie warstwy HTTP w buildzie gry, **fasada API** wywoływana z warstwy LibGDX, konfiguracja bazy w katalogu użytkownika oraz inicjalizacja ZIO (DB + serwisy) dla aplikacji desktop. Silnik (FullMatchEngine) i serwisy (LeagueService, UserService) **nie zmieniają logiki** – tylko sposób ich uruchomienia i wywołania.

## Kontekst projektu

- **Cel:** Gra na Steam w modelu **jedna aplikacja, jeden proces**. Brak HTTP i brak drugiego procesu. Całość w JVM: silnik Scala + warstwa wizualna LibGDX w tym samym procesie.
- **Obecny backend:** Scala 3, ZIO, ZIO HTTP (Routes), Doobie, H2. Serwisy w `service/`, silnik w `engine/`. Wejście: `Main.scala` startuje serwer HTTP.
- **Docelowo:** Punkt wejścia „desktop” (np. osobna main lub flaga) **nie** uruchamia ZIO HTTP; inicjuje tylko: DB (H2 w pliku w katalogu użytkownika), Transactor, serwisy (UserService, LeagueService itd.). Warstwa LibGDX (moduł desktop) będzie wywoływać operacje przez **fasadę** (GameAPI/DesktopFacade), która wewnętrznie woła ZIO/serwisy i zwraca wyniki (np. przez `Unsafe.run` lub mostek wątkowy).

## Twoje zadania (z planu)

W **`docs/PLAN_STEAM_GAME.md`** w sekcji „Szablon zadań” masz zadania **Owner: A1**:

- **F1.1** – Ścieżka bazy H2 do katalogu użytkownika (`~/.local/share/FMGame/` lub odpowiednik Windows); konfiguracja z pliku/env.
- **F1.2** – Punkt wejścia desktop: inicjalizacja DB, Transactor, serwisów **bez** startu ZIO HTTP (Routes nie są startowane).
- **F1.3** – Fasada „GameAPI”: metody odpowiadające kluczowym operacjom (login, register, listLeagues, getTable, getFixtures, playMatchday, getMatchLog, getSquads, submitSquad itd.); mostek ZIO ↔ wątek wywołujący (LibGDX) – np. synchroniczny wrapper z `Unsafe.run`.
- **F1.4** – (Opcjonalnie) Zachowanie obecnego Main z HTTP jako osobny entry („web”); build gry używa tylko entry bez HTTP.
- **F6.1** – (Wspólnie z A4) Pakowanie: jlink + jpackage – jedna dystrybucyjna (bez osobnego serwera).
- **F6.2** – Baza H2 w katalogu użytkownika w produkcji; brak ścieżek na sztywno.

## Jak pracować

1. **Otwórz** `docs/PLAN_STEAM_GAME.md`. Dla zadań A1 ustaw Status (`IN_PROGRESS` / `DONE`) i dopisuj Notes (co zrobione, blokery).

2. **Nie zmieniaj** zachowania silnika ani serwisów (logika biznesowa). Zmieniasz tylko: (a) sposób uruchomienia aplikacji (bez HTTP), (b) ścieżkę DB, (c) dodanie fasady, która wywołuje istniejące serwisy.

3. **Fasada:** Może żyć w module `backend` (np. `GameFacade.scala`) lub w module `desktop`. Musi przyjmować już zbudowane ZIO Runtime i Transactor/serwisy (albo warstwę ZIO z UserService, LeagueService). Metody fasady: np. `def login(email: String, password: String): Either[String, (UserDto, Session)]` – wewnętrznie `runtime.unsafe.run(userService.login(...).either).getOrThrow()` lub podobnie. Session może być po prostu UserId + UserDto trzymane w stanie po stronie desktopu (A2).

4. **Mostek ZIO ↔ LibGDX:** LibGDX działa na wątku głównym. Wywołania ZIO można: (a) uruchamiać synchronicznie na wątku LibGDX przez `Unsafe.run` (blokuje na czas trwania), albo (b) uruchamiać na puli ZIO i wynik przekazać z powrotem na wątek LibGDX (callback/post na główny wątek). Dla prostoty na start wystarczy (a) – krótkie operacje (login, listLeagues, playMatchday) są akceptowalne.

5. **Konfiguracja ścieżki DB:** Zmienna env lub plik (np. `game.conf`) w katalogu użytkownika; domyślnie `~/.local/share/FMGame/fmgame` (Linux/macOS) i `%APPDATA%/FMGame/fmgame` (Windows). JDBC URL: `jdbc:h2:file:/path/to/fmgame;DB_CLOSE_DELAY=-1`.

## Kryteria ukończenia F1

- Aplikacja może wystartować **bez** nasłuchiwania na porcie HTTP.
- Baza tworzy się / łączy w katalogu użytkownika.
- Fasada udostępnia metody potrzebne do logowania, listy lig, tabeli, terminarza, playMatchday, getMatchLog, squads – i zwraca DTO (shared) lub Either[String, _].
- Moduł desktop (A2) może zależność od backendu i wywołać fasadę po inicjalizacji (F2.1).

## Pliki do edycji / utworzenia

- `backend/.../Main.scala` lub nowy `DesktopMain.scala` / entry point bez HTTP.
- Nowy plik fasady (np. `backend/.../GameFacade.scala` lub w module desktop).
- Konfiguracja (plik lub resource) – ścieżka DB.
- `docs/PLAN_STEAM_GAME.md` – tabela zadań (Status, Notes).

Zaczynaj od F1.1 i F1.2; potem F1.3. F6.x po ustabilizowaniu F5.
