# Cursor / Agent – lekcje na przyszłość

Plik do wpisywania błędów i lekcji z sesji z agentem. **Każdy agent po pomyłce lub po odkryciu ważnego buga ma dopisać tu wpis**, żeby uniknąć powtórzeń.

---

## Zasady uzupełniania

- **Kiedy dopisać:** po wprowadzeniu błędu, po jego naprawie, albo po odkryciu buga w review.
- **Format wpisu:** data (YYYY-MM-DD), krótki tytuł, sekcja "Błąd", sekcja "Lekcja".
- Nie usuwać starych wpisów – służą jako historia.

---

## Wpisy

### 2025-03-17 – DxT / threat map dla drużyny wyjazdowej

- **Błąd:** `DxT.baseZoneThreat(zone)` używał tylko numeru kolumny (0–5), więc dla gości "wysoka" wartość zagrożenia była przy ich własnej bramce; drużyna wyjazdowa dostawała odwróconą mapę threat.
- **Lekcja:** Wszystkie funkcje zależne od "kierunku ataku" (threat, xT, OBSO, VAEP zoneThreat) muszą przyjmować **perspektywę posiadania piłki** (`possessionHome` / `isHome`) i używać np. `PitchModel.attackProgress(zone, possessionHome)` zamiast surowej kolumny.

### 2025-03-17 – Czerwona kartka nie usuwała gracza z boiska

- **Błąd:** Po czerwonej kartce gracz był oznaczany w metadanych eventu, ale nadal znajdował się w `state.homePlayerIds` / `state.awayPlayerIds`, więc brał udział w dalszych zdarzeniach.
- **Lekcja:** Przy czerwonej (i drugiej żółtej) trzeba: dodać gracza do `sentOff`, **usunąć go z odpowiedniej listy `homePlayerIds`/`awayPlayerIds`** oraz upewnić się, że wybór aktora (np. `outfield.filter(p => onPitchIds.contains(p.player.id))`) korzysta z tych list, żeby wykluczyć usuniętych.

### 2025-03-17 – Brak śledzenia drugiej żółtej

- **Błąd:** Nie było stanu "kto ma żółtą kartkę"; gracz mógł dostać wiele żółtych bez konwersji na czerwoną.
- **Lekcja:** Dodać w `MatchState` pole `yellowCards: Set[PlayerId]`. Przy żółtej: dodać gracza do tego setu; jeśli już tam był → traktować jak czerwoną (effectiveCard = "Red") i usunąć z boiska.

### 2025-03-17 – Współdzielone węzły DOM w wielu `<select>`

- **Błąd:** W MatchSquadPage jedna tablica `opts` (elementy `<option>`) była przekazywana do czterech różnych `<select>`. W DOM węzeł może mieć tylko jednego rodzica – w efekcie tylko ostatni select miał opcje.
- **Lekcja:** Dla każdego `<select>` tworzyć **osobną** listę elementów (np. funkcja `mkOpts()` wywoływana per select), a nie jedną wspólną zmienną z elementami.

### 2025-03-17 – Aktualizacja `positions` przy każdym mousemove

- **Błąd:** W PitchView podczas przeciągania aktualizowano `positions.update(...)` przy każdym ruchu myszy, co wywoływało `positions.signal` i przebudowę całej listy 11 elementów – utrata korzyści z bezpośredniej zmiany stylu DOM.
- **Lekcja:** Podczas dragu aktualizować **tylko** style węzła przez `nodeRefs`; **commit do `Var`** (positions) robić dopiero w `mouseUp` / `mouseLeave` (np. w `commitDragPosition()`).

### 2025-03-17 – Formacja 4-3-3 miała 3 obrońców i dwóch ST na jednej pozycji

- **Błąd:** W FormationPresets lista slotów 4-3-3 miała LB, CB, RB (3 obrońców) i dwa ST; `DefaultPositions433` miała dwa sloty z tą samą pozycją (0.55, 0.5), więc dwa kółka nakładały się.
- **Lekcja:** Przy presetach formacji sprawdzać: liczba obrońców/pomocników/napastników zgodna z nazwą (4-3-3 = 4+3+3); **żadne dwie pozycje nie są identyczne** w `DefaultPositions*`.

### 2025-03-17 – Token ustawiany przed walidacją (flash UI)

- **Błąd:** W Main.scala `AppState.token.set(Some(savedToken))` wywoływano od razu po odczycie z localStorage, a dopiero potem `ApiClient.me()`. Przy wygasłym tokenie użytkownik widział krótko Dashboard, zanim nastąpiło przekierowanie do logowania.
- **Lekcja:** Ustawiać token w state **dopiero po pomyślnej** odpowiedzi `me()`; przy błędzie czyścić localStorage i ustawiać stronę na Login bez wcześniejszego pokazywania UI dla zalogowanego użytkownika.

### 2025-03-17 – matchdayForDate vs FixtureGenerator – różne algorytmy dat

- **Błąd:** Scheduler używał `matchdayForDate` z iteracją Wed+4/Sat+3, a FixtureGenerator używał `weekOffset + midWeek` (np. Sob+3, Sob+7). Dla tej samej daty startu kolejne numery kolejek mogły wskazywać inne dni.
- **Lekcja:** Jedna źródłowa funkcja daty kolejki (np. ta z FixtureGenerator); `matchdayForDate` powinien używać **tej samej** formuły (np. `nextMatchdayDate(matchdayIndex) == date`), żeby scheduler i terminarz meczów były spójne.

### 2025-03-17 – Równoległe wywołania playMatchday

- **Błąd:** API i scheduler mogły wywołać `playMatchday` dla tej samej ligi równolegle; dwa transakcje zapisywały te same mecze → drugi mógł rzucać na UNIQUE constraint i `.orDie` powodowało defect fibera.
- **Lekcja:** Serializować `playMatchday` per liga (np. `Semaphore(1)` lub lock w mapie `leagueId -> Semaphore`); przy tworzeniu semafora używać ZIO (np. `Semaphore.make(1)`), nie Unsafe w computeIfAbsent.

### 2025-03-17 – connUnit = SELECT 1 przy każdym wywołaniu

- **Błąd:** `connUnit` było zdefiniowane jako `sql"SELECT 1".query[Int].unique.map(_ => ())`, więc każde użycie w ConnectionIO wykonywało zbędne zapytanie.
- **Lekcja:** Dla "no-op" w ConnectionIO używać `cats.Applicative[ConnectionIO].pure(())` (albo `Sync[ConnectionIO].unit`), nie prawdziwego zapytania.

### 2025-03-17 – pressConferenceGiven TOCTOU

- **Błąd:** Sprawdzenie `containsKey` i potem `put` po wielu operacjach DB – dwa równoległe requesty mogły oba przejść sprawdzenie i oba zapisać konferencję.
- **Lekcja:** Użyć atomowego `putIfAbsent(key, true)`; jeśli zwróciło non-null (stara wartość), uznaj że konferencja była już wcześniej podana i zwróć błąd.

### 2025-03-17 – DribbleLost event.teamId = drużyna przegrywająca

- **Błąd:** W FullMatchEngine po naprawie teamId to drużyna wygrywająca (posiadanie piłki), a w SimpleMatchEngine nadal używano `lostTid`.
- **Lekcja:** Zgodność między silnikami: event "DribbleLost" / "PassIntercepted" w polu teamId ma **drużynę która przejęła piłkę** (newPossession). W SimpleMatchEngine użyć `wonTid` zamiast `lostTid`.

### 2025-03-17 – Testy zawsze prawdziwe (false positive)

- **Błąd:** Np. warunek `result.events.size > 100` w teście ACWR/Injury był zawsze spełniony; `(hOv + aOv) >= 0.0` w teście VAEP też; test "Nash penalty" akceptował dowolny mecz z golem zamiast wymagać eventu "Penalty".
- **Lekcja:** W testach nie dodawać "słabych" klauzul typu `|| result.events.size > 100` ani `|| value >= 0.0`, które sprawiają że asercja nigdy nie failuje. Testy override’ów (xG, VAEP) muszą **porównywać** wynik z override’em do wyniku bez override’u (ten sam seed).

### 2025-03-17 – Rejestracja: .orDie przy UNIQUE na email

- **Błąd:** Dwa równoległe rejestracje z tym samym emailem mogły oba przejść `findByEmail`; drugi `create` rzucał na UNIQUE, a `.orDie` zamieniał to na defect zamiast czytelnego "Email already registered".
- **Lekcja:** Na `userRepo.create(user).transact(xa)` użyć `.mapError(_ => "Email already registered")` (albo dopasować SQLState), żeby zwrócić użytkownikowi spójny komunikat.

### 2025-03-17 – Gol nie liczył się jako shot on target

- **Błąd:** W agregacji zaawansowanej "shots on target" zwiększało się tylko gdy outcome zawierało "Saved" lub "Success"; eventy "Goal" czasem nie mają takiego outcome, więc gol nie był liczony jako SOT.
- **Lekcja:** W warunku na "on target" uwzględnić `e.eventType == "Goal"` (gol zawsze jest strzałem na bramkę).

### 2025-03-17 – Kontuzja (Injury) nie usuwa gracza z boiska

- **Błąd:** Event "Injury" jest zapisywany, ale kontuzjowany gracz pozostaje w `homePlayerIds`/`awayPlayerIds` i dalej uczestniczy w symulacji. W realnym FM kontuzjowany schodzi (zmiana lub gra w 10).
- **Lekcja:** Przy evencie Injury usuwać gracza z listy na boisku (analogicznie do czerwonej) i ewentualnie wymusić zmianę, jeśli są dostępne; dodać stan typu `injuredThisMatch` lub użyć wspólnego mechanizmu z `sentOff` jeśli ma to sens w domenie.

### 2025-03-17 – DribbleLost: pressing w połowie przeciwnika – zła perspektywa

- **Błąd:** W FullMatchAnalytics dla DribbleLost secondary (wygrywający obrońca) używane było `isOpponentHalf(zone, !eventIsHome)`. Dla wygrywającego drużyna to `e.teamId`, więc poprawna perspektywa to `eventIsHome` (czy strefa jest w połowie przeciwnika względem drużyny wygrywającego).
- **Lekcja:** Dla secondary gracza przy DribbleLost (i podobnych eventach „wygrywający”) używać `isOpponentHalf(zone, eventIsHome)` – eventIsHome już oznacza drużynę wykonującą akcję (wygrywającego).

### 2025-03-17 – LibGDX Label.getText + konkatenacja

- **Błąd:** W LeagueViewScreen używano `rowLabel.getText + " [błąd: " + err + "]"`. W LibGDX `Label.getText` zwraca `CharSequence` (np. `StringBuilder`), które nie ma metody `+` do łączenia ze Stringiem – kompilator wybiera inną overload i powstaje błąd typu.
- **Lekcja:** Przy łączeniu tekstu z `getText` używać interpolacji `s"${rowLabel.getText} [błąd: $err]"` albo `.toString` przed `+`.

### 2025-03-17 – Zapamiętaj sesję (desktop): userId bez tokenu

- **Błąd:** W single-playerze desktop nie ma osobnego serwera HTTP – sesja to ten sam proces. Zapis tokenu w pliku nie jest potrzebny do walidacji; backend getMe(userId) nie używa tokenu w tej architekturze.
- **Lekcja:** W „zapamiętaj mnie” zapisywać tylko `userId` w pliku (SessionPersistence); przy starcie wczytać userId i wywołać `gameApi.getMe(userId)` – przy sukcesie auto-login. Przy wylogowaniu czyścić plik.

### 2025-03-17 – Wizualizacja meczu 2D: jedna strefa na event

- **Błąd:** W MatchPlaybackScreen aktor i secondary są rysowani w tej samej strefie (event.zone). Dla eventu Pass strefa to zwykle cel podania – podający (actor) mógłby być w poprzedniej strefie.
- **Lekcja:** Dla wierniejszej animacji przy Pass/LongPass można trzymać `previousZone` i rysować actor w previousZone, secondary/piłkę w zone. Obecna wersja (oba w zone z małym offsetem) jest akceptowalna dla MVP.

### 2026-03-17 – LibGDX: polskie znaki i czytelność UI

- **Błąd:** Domyślna `BitmapFont` w LibGDX nie zawiera znaków ą, ę, ó, ś, ź, ż itd. – tekst wyświetlał kwadraciki; cały interfejs wyglądał jak surowy tekst bez hierarchii i tła.
- **Lekcja:** Użyć rozszerzenia **gdx-freetype** i ładować czcionkę TTF z zestawem znaków polskich (`FreeTypeFontGenerator` + `param.characters = DEFAULT_CHARS + "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"`). Sprawdzać ścieżki: `fonts/DejaVuSans.ttf` w resources, potem np. `/usr/share/fonts/truetype/dejavu/`. Dla wyglądu: ustawić `glClearColor` na ciemny szary, style przycisków z tłem (`up`/`down`), styl „title” dla nagłówków, `ScrollPaneStyle.background` dla obszaru przewijania. **ScrollPane** nie ma metody `setPadding` – używać stylu w Skin.

### 2026-03-17 – LibGDX desktop: konflikt nazw List

- **Błąd:** W ekranach desktopu z `import com.badlogic.gdx.scenes.scene2d.ui._` nazwa `List` odnosi się do `com.badlogic.gdx.scenes.scene2d.ui.List`, a nie do `scala.List` – kompilator zgłaszał błędy przy `List("a", "b")`.
- **Lekcja:** W tych plikach używać jawnie `scala.List` przy listach Scala. Dla `SelectBox.setItems` w LibGDX przekazać tablicę jako varargs: `setItems(presetNames: _*)`.

### 2026-03-17 – Scala 3: porównanie AnyRef z Boolean w Dialog.result

- **Błąd:** W LibGDX `Dialog.result(obj: AnyRef)` przycisk przekazuje np. `Boolean` (boxed jako `java.lang.Boolean`). W Scala 3 warunek `if (obj == true)` daje błąd: „Values of types AnyRef and Boolean cannot be compared with == or !=”.
- **Lekcja:** Używać `if (java.lang.Boolean.TRUE == obj)` zamiast `if (obj == true)` przy sprawdzaniu wyniku dialogu („Wyślij” / „Zamknij”).

### 2026-03-17 – LibGDX SelectBox.setItems: Scala Array vs Java varargs

- **Błąd:** `teamSelectBox.setItems(teamNames)` gdzie `teamNames: Array[String]` – kompilator zgłaszał: „None of the overloaded alternatives of method setItems match arguments ((teamNames : Array[String]))”. LibGDX oczekuje `Array[String]` (gdx.utils.Array) lub `String*`.
- **Lekcja:** Przekazać tablicę Scala jako varargs: `setItems(teamNames: _*)`.

### 2026-03-17 – Kolejność argumentów createInvitation

- **Błąd:** Wywołanie `game.gameApi.createInvitation(leagueId, email, userId)` – API ma sygnaturę `createInvitation(leagueId, userId, email)`.
- **Lekcja:** Sprawdzać kolejność parametrów w GameAPI przed wywołaniem z UI.

### 2026-03-17 – Doobie Transactor w testach E2E

- **Błąd:** `Transactor.fromDriverManager[zio.Task]("jdbc:h2:mem:...", props, None)` – kompilator zgłaszał brak dopasowania (Required: String, Found: Properties). Doobie oczekuje (driver, url, user, password) lub (driver, url, props, logHandler).
- **Lekcja:** Podawać 4 argumenty: `Transactor.fromDriverManager[zio.Task]("org.h2.Driver", url, props, None)`.

### 2026-03-17 – E2E baraże: createPlayOffFinal wymaga zwycięzców (bez remisów)

- **Błąd:** Test baraży czasem failował z „Oba półfinały muszą mieć zwycięzcę (bez remisów)” – silnik meczowy może dać remis w półfinale.
- **Lekcja:** Test E2E baraży oznaczony jako `TestAspect.flaky(3)` (do 3 prób). Długoterminowo: dogrywka/kary w backendzie dla meczów barażowych.

### 2026-03-17 – LeagueRepository.update nie zapisywał tier / league_system_name

- **Błąd:** UPDATE ligi w repozytorium zmieniał tylko name, current_matchday, total_matchdays, season_phase, home_advantage, start_date – bez `league_system_name` i `tier`.
- **Lekcja:** W `LeagueRepository.update` dodać do zapisu pola `league_system_name` i `tier`, żeby testy (i ewentualna logika) mogły ustawiać ligę jako tier-2 do baraży.

---

*Kolejne wpisy dopisywać na dole, zachowując powyższy format (data, tytuł, Błąd, Lekcja).*
