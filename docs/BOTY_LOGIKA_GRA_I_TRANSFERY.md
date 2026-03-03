# Boty: logika gry, transfery i organizacja — jak je ogarnąć, żeby gra była ciekawa

**Źródła:** przegląd sieci (FM AI, transfery, squad building), analiza kodu (BotSquadBuilder, LeagueService, FullMatchEngine, GamePlanInput).

---

## 1. Stan obecny w kodzie

### 1.1 Tworzenie botów i budżet

- **addBots** (LeagueService): tworzy drużyny z `ownerType = Bot`, stały budżet **1 000 000**, `name = "Bot 1"`, … Brak różnicowania „siły” czy „agresywności” bota.

### 1.2 Skład i formacja (BotSquadBuilder)

- **buildBotLineup**: dostępni (bez kontuzji) → 1 bramkarz + 10 z pola. Pole **sortowane po `PlayerOverall.overall(p)`** (średnia wszystkich atrybutów). Pierwszych 11 przypisanych do slotów wybranej formacji **po kolei** — bez dopasowania „najlepszy CB na slot CB”, tylko globalny ranking.
- **pickFormation(teamId, opponentStrength)**: formacja z hasha `teamId` (indeks 0/1/2 → 4-3-3, 4-4-2, 3-5-2). **opponentStrength w ensureSquad nigdy nie jest przekazywane** (zawsze `None`), więc logika „słaby rywal → ofensywa, silny → defensywa” nie działa.
- **useTriggers(teamId)**: 50% botów ma pressing (7–9) + kontratak (4), 50% nie — deterministycznie z hasha.

### 1.3 Game plan bota

- Tylko **formationName** + opcjonalnie **triggerConfig**. Brak: **teamInstructions** (tempo, width, pressing), **slotRoles**, **playerInstructions**, **setPieces**, **oppositionInstructions**. Silnik te pola wykorzystuje (np. bonusy do podań/strzałów, zmęczenie), więc boty grają „płasko” taktycznie.

### 1.4 Transfery: oferty botów (kupno od gracza)

- **generateBotOffersForOpenWindows**: dla każdego okna Open i każdej drużyny gracza (≥16 zawodników) losuje **2 zawodników** i dla każdego — jeśli bot ma budżet i &lt;20 w kadrze — losowy bot składa ofertę **0.9–1.1 × estimatedPlayerPrice(player)**.  
- **Brak:** celowania w pozycje („potrzebujemy napastnika”), oceny „fit” do taktyki bota, formy/morale, wieku/potencjału. Cena tylko z overall (estimatedPlayerPrice).

### 1.5 Transfery: akceptacja ofert (gracz kupuje od bota)

- Bot **akceptuje**, gdy `req.amount >= estimatedPlayerPrice(player)`.  
- **Brak:** „czy mamy zastępstwo”, „czy to kluczowy zawodnik”, „czy budżet nam się opłaca”, odmowy przy zbyt niskiej ofercie (negocjacja).

### 1.6 Brak zachowań „osobowych”

- Wszystkie boty mają ten sam styl (poza formacją i triggerami z hasha). Brak poziomu trudności, „agresywności” na rynku, preferencji formacyjnych czy taktycznych.

---

## 2. Wnioski z sieci i dobrych praktyk

- **FM:** AI wybiera transfery pod **braki w kadrze**, **styl taktyczny** (np. gegenpress → stamina/work rate), **potential vs current** przy rezerwach, **development** przy wypożyczeniach. Oferty nie są losowe.
- **Gry sportowe (FC):** trudność = „inteligencja” w decyzjach (wybór składu, taktyka, presing). Można to uprościć do 2–3 poziomów (łatwy/średni/hard) wpływających na jakość składu i agresywność transferów.
- **Gra ciekawa:** boty powinny **dopasowywać skład do formacji** (najlepszy GK na GK, najlepszy CB na CB), **reagować na siłę rywala** (formacja/taktyka), **kupować pod braki** i **nie sprzedawać kluczowych za byle co**.

---

## 3. Propozycja: co dodać i w jakiej kolejności

### 3.1 Skład: dopasowanie do pozycji (wysoki priorytet)

**Cel:** Bot wystawia **najlepszych na dane sloty**, a nie „top 10 overall” na dowolne sloty.

- Dla każdego **slotu** formacji (GK, LCB, RCB, LB, …) zdefiniować **preferowane atrybuty** (np. CB: tackling, marking, heading, strength; ST: finishing, composure, offTheBall).
- **Funkcja:** `bestPlayerForSlot(availablePlayers, slot): Player` — np. scoring = średnia ważona atrybutów dla slotu (wagi w configu lub stałe per slot). Dla GK: reflexes, handling, positioning.
- **buildBotLineup:** dla każdego slotu wybrać `bestPlayerForSlot(remaining, slot)`, usunąć go z `remaining`. Efekt: sensowny skład nawet przy zróżnicowanej kadrze.

**Wysiłek:** średni (mapa slot → wagi atrybutów, jedna funkcja wyboru, testy).

### 3.2 Formacja i taktyka zależna od rywala (wysoki priorytet)

**Cel:** Bot **wiedzieć**, czy gra z silnym czy słabym przeciwnikiem, i dostosować formację / defensywność.

- W **ensureSquad** (lub w miejscu, gdzie budowany jest MatchSquad dla bota) mieć **opponentTeamId**. Pobrać ostatnie wyniki / tabelę lub **średni overall przeciwnika** (z kadry przeciwnika) i przekazać jako `opponentStrength` (np. 0.0–1.0) do `BotSquadBuilder.pickFormation(teamId, Some(opponentStrength))`.
- **pickFormation:** przy silnym rywalu (np. &gt; 0.6) — częściej 4-4-2 / 3-5-2 (bardziej defensywnie), przy słabym (&lt; 0.4) — częściej 4-3-3. Obecna logika już to przewiduje, wystarczy **przekazać** opponentStrength.
- **Game plan:** dodać **teamInstructions** zależne od „nastawienia”: np. przy silnym rywalu `tempo = "lower"`, `pressingIntensity = "normal"`, przy słabym `tempo = "higher"`, `pressingIntensity = "higher"`. Opcjonalnie **slotRoles** (np. CDM → anchor przy defensywie).

**Wysiłek:** niski–średni (obliczenie siły przeciwnika, rozszerzenie defaultGamePlanJson o teamInstructions).

### 3.3 Rozszerzenie game planu bota (średni priorytet)

**Cel:** Bot korzysta z tych samych „dźwigni” co gracz: tempo, pressing, role, ewentualnie proste set pieces.

- **defaultGamePlanJson** (lub osobna funkcja `botGamePlan(formation, withTriggers, style: Defensive | Balanced | Attacking)`) zwraca JSON z:
  - `teamInstructions`: tempo, width, pressingIntensity, passingDirectness (zależnie od style / opponentStrength).
  - `slotRoles`: mapowanie slot → rola (np. CDM → anchor, ST → advanced_forward) — stałe per formacja lub uproszczone.
- Parsowanie po stronie LeagueService/Engine bez zmian — **parseGamePlan** już obsługuje te pola.

**Wysiłek:** niski (kilka stałych profili JSON lub generator w kodzie).

### 3.4 Transfery: bot kupuje pod braki (średni priorytet)

**Cel:** Bot **celuje w pozycje**, w których ma słabość, zamiast losować 2 zawodników z kadry gracza.

- Dla bota: **analiza kadry** — liczba zawodników per „grupa pozycji” (GK, CB, FB, CM, W, ST) i **średni overall per grupa**. Zdefiniować „brak”: np. &lt; 2 zawodników na pozycji lub średni overall w grupie &lt; 10.
- **generateBotOffersForOpenWindows:** zamiast losowych 2 graczy od człowieka:
  - Dla każdego bota wyznaczyć **priorytetowe pozycje do wzmocnienia** (braki).
  - Dla każdej drużyny gracza: filtrować zawodników **po pozycjach priorytetowych** (preferredPositions), sortować np. po overall lub „fit” do formacji bota, wybrać 1–2 **najbardziej wartościowych** i złożyć oferty (cena np. 0.9–1.15 × estimatedPrice, z małym losowaniem).
- Ograniczenia: budżet, max 20 w kadrze, nie kupować drugiego bramkarza jeśli już mamy 2 itd.

**Wysiłek:** średni (analiza kadry bota, priorytety pozycji, zmiana pętli w generateBotOffersForOpenWindows).

### 3.5 Transfery: bot sprzedaje mądrzej (średni priorytet)

**Cel:** Bot **nie akceptuje** każdej oferty ≥ estimatedPrice; odrzuca lub „trzyma” kluczowych.

- **Kluczowość zawodnika:** np. czy jest w pierwszej 11 (według ostatniego składu lub aktualnego bestPlayerForSlot), czy to jedyny silny na danej pozycji.
- **acceptTransferOffer** (gdy gracz kupuje od bota): zamiast `amount >= estimatedPlayerPrice` dodać warunek np. `!isKeyPlayer(player, teamId)` **lub** `amount >= estimatedPlayerPrice * 1.2` (premium za kluczowego). Jeśli jest kluczowy i oferta niska — nie akceptować (gracz dostanie Pending, może podbić).
- Opcjonalnie: bot **sam składa oferty sprzedaży** (np. rezerwowy na pozycji, gdzie mamy nadmiar) — wtedy osobna funkcja „bot put player on transfer list / make offer to human”.

**Wysiłek:** średni (definicja „key player”, integracja w accept flow).

### 3.6 Różnicowanie botów: budżet, „osoba”, trudność (niski priorytet)

**Cel:** Nie wszyscy boty są identyczni; część agresywniejsza na rynku, część defensywna w grze.

- Przy **addBots**: losowy (lub z seedu ligi) **budżet** w zakresie np. 500k–1.5M, ewentualnie **trudność** (Easy / Normal / Hard) zapisana w Team (nowe pole `botDifficulty` lub w domyślnym game planie).
- **Trudność** wpływa na: (1) jakość wyboru składu (Easy = prostszy overall, Hard = pełne dopasowanie do slotów + opponentStrength), (2) agresywność ofert (Hard = wyższy mnożnik ceny przy kupnie, mniej chętna sprzedaż kluczowych).
- **Osoba:** np. „DefensiveBot” (zawsze 4-4-2, lower tempo), „AttackingBot” (4-3-3, higher tempo) — zapisane w team name lub w defaultGamePlanId / metadane. Użyte w pickFormation i defaultGamePlanJson.

**Wysiłek:** średni (rozszerzenie modelu Team lub konwencja nazw/metadanych, przekazywanie do BotSquadBuilder i transferów).

---

## 4. Gdzie w kodzie to podłączyć

| Zadanie | Gdzie | Uwagi |
|--------|--------|--------|
| Skład per slot (bestPlayerForSlot) | **BotSquadBuilder** | Nowa funkcja `bestPlayerForSlot(players, slot, slotWeights)`; `buildBotLineup` iteruje po slotach i wybiera najlepszego. |
| opponentStrength | **LeagueService.ensureSquad** | Przed wywołaniem `BotSquadBuilder.pickFormation` pobrać kadrę przeciwnika, obliczyć średni overall → `opponentStrength` (znormalizować 0–1 np. przez ligę). Przekazać do `pickFormation(team.id, Some(strength))`. |
| teamInstructions / style | **BotSquadBuilder.defaultGamePlanJson** | Dodać parametr np. `style: String` (defensive/balanced/attacking) lub wyznaczyć z opponentStrength; zwracać JSON z `teamInstructions` i ewentualnie `slotRoles`. |
| Transfery: braki w kadrze | **LeagueService** | Nowa funkcja `botTransferNeeds(teamId, players): List[String]` (lista pozycji do wzmocnienia). W `generateBotOffersForOpenWindows` dla bota wywołać needs, dla gracza filtrować players po preferredPositions ∈ needs. |
| Sprzedaż: key player | **LeagueService** (accept / createOffer) | Funkcja `isKeyPlayer(leagueId, teamId, playerId): ZIO` (np. ostatni skład lub „w top 11 po overall per slot”). Przy akceptacji oferty **od** bota: jeśli key i amount < premium → nie akceptować. |

---

## 5. Podsumowanie: co zrobić, żeby gra była ciekawsza

1. **Skład:** Dopasowanie zawodników do slotów (najlepszy GK na GK, najlepszy CB na CB) zamiast „top 11 overall” — **BotSquadBuilder + mapa slot → wagi atrybutów**.
2. **Rywal:** Przekazywanie **opponentStrength** do `pickFormation` i **teamInstructions** zależne od siły rywala (defensywa/ofensywa) — **LeagueService.ensureSquad + BotSquadBuilder**.
3. **Taktyka:** Rozszerzenie **defaultGamePlanJson** o **teamInstructions** (tempo, pressing) i opcjonalnie **slotRoles** — bot gra „pełniejszym” game planem.
4. **Kupno:** Bot **celuje w pozycje**, w których ma braki (analiza kadry, priorytet pozycji) — **generateBotOffersForOpenWindows + analiza kadry bota**.
5. **Sprzedaż:** Bot **nie sprzedaje kluczowych** za minimalną cenę (premium lub blokada) — **acceptTransferOffer + isKeyPlayer**.
6. **Różnicowanie** (później): Budżet / trudność / „osoba” przy addBots i użycie w składzie/transferach.

Rekomendowana **kolejność wdrożenia:** (1) skład per slot, (2) opponentStrength + teamInstructions, (3) transfery pod braki, (4) sprzedaż kluczowych, (5) rozróżnienie botów (budżet/trudność).
