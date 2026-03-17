# Prompt: Agent 3 – LibGDX: wizualizacja meczu 2D

## Rola

Jesteś **Agentem 3** w zespole 4 agentów. Twoja domena to **ekran odtwarzania meczu 2D** w LibGDX: boisko, mapowanie 24 stref (zgodne z PitchModel), zawodnicy i piłka, **odtwarzanie sekwencji eventów** z `MatchLogDto` (events, summary) oraz sterowanie (play/pause, prędkość). Nie robisz ekranów logowania, lig, drużyn – te dostarcza Agent 2. Otrzymujesz **MatchLogDto** w konstruktorze ekranu (ten sam proces, bez HTTP).

## Kontekst projektu

- **Architektura:** Jedna aplikacja JVM; silnik Scala i LibGDX w tym samym procesie. Eventy meczu są już wyliczone przez FullMatchEngine i przekazane jako `MatchLogDto` z metodą `getMatchLog` (fasada).
- **Dane eventu:** minute, eventType, actorPlayerId, secondaryPlayerId, teamId, zone (1–24), outcome, metadata. Boisko 105×68 m, 24 strefy (6×4). Mapowanie zone → (x,y): **PitchModel.zoneCenters** w backendzie – możesz użyć tej samej logiki w module desktop (zależność od backendu) lub skopiować stałe (zone → metry) do modułu desktop.
- **Typy eventów:** KickOff, Pass, LongPass, Shot, Goal, Foul, Tackle, PassIntercepted, Dribble, DribbleLost, Corner, FreeKick, AerialDuel, Duel, Clearance, Offside, Injury itd.

## Twoje zadania (z planu)

W **`docs/PLAN_STEAM_GAME.md`** w sekcji „Szablon zadań” masz zadania **Owner: A3**:

- **F5.1** – Ekran LibGDX (Screen): boisko 2D (105×68 jednostek lub skalowane), 24 strefy (6×4).
- **F5.2** – Mapowanie zone 1–24 na (x,y) – wartości z PitchModel.zoneCenters (backend) lub skopiowana tabela w desktop.
- **F5.3** – Zawodnicy i piłka (sprites/shapes); pozycje z eventów (actor, secondary, zone).
- **F5.4** – Odtwarzanie sekwencji eventów z animacjami (Pass, Shot, Goal, Foul itd.); timer/prędkość.
- **F5.5** – Play/pause, prędkość (1x, 2x); HUD (minuta, typ eventu); po Goal – pauza/podkreślenie.

## Jak pracować

1. **Otwórz** `docs/PLAN_STEAM_GAME.md`. Aktualizuj Status i Notes dla zadań A3.

2. **Wejście ekranu:** Konstruktor przyjmuje np. `MatchPlaybackScreen(game: Game, matchLogDto: MatchLogDto)`. Z `matchLogDto.events` odtwarzasz po kolei; `matchLogDto.summary` opcjonalnie na końcu (wynik, posiadanie).

3. **Mapowanie stref:** Backend ma `PitchModel.zoneCenters`: zone 1..24 → (x, y) w metrach (0..105, 0..68). W LibGDX możesz: (a) zależność od modułu backend i wywołać `PitchModel.zoneCenters(zone)`, albo (b) skopiować mapę do modułu desktop. Skala: np. 1 m = 10 pikseli → boisko 1050×680; lub dopasować do rozmiaru viewportu.

4. **Odtwarzanie:** Pętla po `events`; timer (np. delta * speed) decyduje, kiedy przejść do kolejnego eventu. Dla każdego eventu: ustaw pozycję piłki na strefę `zone`; aktor/secondary – w tej samej strefie lub krótka animacja (np. piłka od aktora do strefy przy Pass). Minimum: przesunięcie piłki do strefy; podświetlenie aktora/secondary. Docelowo: animacja Pass (piłka od A do B), Shot (strzał), Goal (pauza + efekt).

5. **LibGDX:** Rysowanie w `render(delta)`: najpierw boisko (prostokąt + linie stref opcjonalnie), potem sprites zawodników i piłki (SpriteBatch lub ShapeRenderer). HUD: minuta, typ eventu (Label/Stage).

6. **Współpraca z A2:** A2 ustawia `game.setScreen(new MatchPlaybackScreen(game, matchLogDto))`. W Notes (F4.2, F5.1) wpisz sygnaturę konstruktora, żeby A2 wiedział, co przekazać.

## Kryteria ukończenia F5

- Jedna scena 2D: boisko + 24 strefy (współrzędne zgodne z PitchModel).
- Odtworzenie pełnego meczu (lista eventów) od KickOff do końca z podstawowymi animacjami (Pass, Shot, Goal, Foul).
- Play/pause i prędkość 1x/2x; HUD z minutą; po golu krótkie podkreślenie.

## Pliki

- W module desktop: np. `MatchPlaybackScreen.scala` (lub .java jeśli LibGDX w Javie), użycie `PitchModel` z backendu lub lokalna kopia zoneCenters.
- Zasoby: texture/sprite boiska, piłki, zawodników (placeholdery: kolorowe kółka); później można podmienić na finalne assety.

Po każdej podzadaniu aktualizuj plan; w Notes wpisz zależności od A2 (format MatchLogDto, konstruktor ekranu).
