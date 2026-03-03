# Analiza: Football Manager vs nasz projekt — pomysły na rozwój

**Data:** 2026-02-23  
**Źródła:** przegląd sieci (FM25/FM26, FM Scout, poradniki) + dokładna analiza obecnego kodu (backend, frontend, shared, docs).

---

## 1. Co mamy w projekcie (skrót)

### 1.1 Silnik meczu i analityka
- **Symulacja:** FullMatchEngine — zdarzenie po zdarzeniu (Pass, Shot, Dribble, Tackle, Foul, Corner, itd.), Pitch Control (pace/acceleration, zmęczenie), DxT, xG (formuła + model 8-coef, xgCalibration), VAEP z modelu, xT (value iteration), xPass / xPass under pressure.
- **Taktyka:** Jedna formacja (`formationName`), TriggerConfig (pressZones, counterTriggerZone), TeamInstructions (tempo, width, passingDirectness, pressingIntensity), slotRoles, playerInstructions, stałe fragmenty (rożne, wolne, karne, wrzuty), Opposition Instructions.
- **Pozycjonowanie:** `PitchModel.positionsForTeam(..., inPossession)` — jedna formacja z lekkim przesunięciem w ataku (shift od ballZone). **Brak** oddzielnych formacji „in possession” vs „out of possession” jak w FM26.
- **Morale / freshness:** Player.morale, Player.freshness; wpływ na passSuccess, xG (moraleMod); po meczu aktualizacja freshness; konferencja prasowa (tone → delta morale).
- **Kontuzje:** InjuryStatus (since/return matchday), ACWR w gałęzi Foul, brak gry do returnAtMatchday.

### 1.2 Transfery i rynek
- **Okna transferowe:** TransferWindow (openAfterMatchday, closeBeforeMatchday, status). Lista okien, otwieranie/zamykanie wg kolejek.
- **Oferty:** TransferOffer (fromTeamId, toTeamId, playerId, amount, status). Boty generują oferty (kupno pod braki, sprzedaż); gracz akceptuje/odrzuca. **Brak:** kontraktów (długość, pensja, klauzule), negocjacji, agentów.

### 1.3 Skauting i shortlist
- **ScoutingReport:** teamId, playerId, rating (0–10), notes. Lista raportów, tworzenie raportu (gracz wybiera zawodnika i ocenę).
- **Shortlist:** lista playerId dla drużyny; dodawanie/usuwanie. **Brak:** automatycznego skautingu, zasięgu skautów, „recruitment focus”, porównań z obecną kadrą.

### 1.4 Trening
- **TrainingPlan:** tydzień (7 dni) z typem sesji: Balanced, Attacking, Defending, Physical, Mental, Recovery, Rest. Zapisywany per drużyna, wyświetlany w TeamPage. **Brak:** wpływu treningu na rozwój atrybutów; jakości sztabu (Working with Youngsters); mentoringu; „additional focus” na słabe atrybuty.

### 1.5 Zarządzanie ligą i drużyną
- **Liga:** League (teamCount, currentMatchday, totalMatchdays, seasonPhase, homeAdvantage, startDate). Start sezonu, rozgrywanie kolejki, zaproszenia, boty.
- **Drużyna:** Team (budget, eloRating, managerName). Skład (11 + rezerwa), zapisane taktyki (GamePlanSnapshot), skład na mecz (MatchSquad).
- **Brak:** zarządu (board), oczekiwań („awans”, „środek tabeli”), confidence; obiektów (stadion, ośrodek treningowy, akademii); pożyczek (loans); wieloletnich kontraktów.

### 1.6 UI
- **Strony:** Dashboard, League (tabela, terminarz, eksport, Data Hub, transfery), Team (kadra, taktyki, trening, transfery, skauting, shortlist), MatchSquad (skład + cały game plan), MatchDetail (wynik, podsumowanie, analityka zaawansowana, zdarzenia, konferencja prasowa), Login/Register, AcceptInvitation.
- **Eksport:** csv, json (statsbomb), json-full; przycisk „Pobierz plik”; checkboxy przy meczach do eksportu.

---

## 2. Funkcje Football Managera (sieć) — co jest w FM, a czego u nas nie ma

| Obszar FM | Opis (FM) | U nas |
|-----------|-----------|--------|
| **Dual formations (FM26)** | Osobna formacja „in possession” i „out of possession”; wizualizacja przejść. | Jedna formacja + parametr `inPossession` tylko do przesunięcia pozycji. |
| **Role per faza** | Role „in possession” i „out of possession” (np. Pressing DM vs Screening DM). | Jedna rola per slot (slotRoles), bez podziału na fazę. |
| **Tactical visualiser** | Widok pozycji w trzech tercjach (obrona/środek/atak) w zależności od fazy. | Brak; mamy tylko PitchView z ustawieniem startowym. |
| **Recruitment hub / TransferRoom** | „Requirements” (ogłoszenie potrzeb), „Pitch opportunities” (oferty innych), integracja z squad plannerem. | Proste oferty kupna/sprzedaży; brak ogłoszeń potrzeb, brak „kto szuka napastnika”. |
| **Kontrakty** | Długość, pensja, bonusy, klauzule (release, buyback), wygaśnięcie za 18 miesięcy → negocjacje. | Brak; Player nie ma contractEnd, salary. |
| **Board / oczekiwania** | Oczekiwania ligowe (awans, play-off, środek tabeli), confidence, prośby o transfer/facilities. | Brak. |
| **Trening → rozwój** | Sesje treningowe wpływają na atrybuty; role-specific training; mentoring (personality, determination). | Plan tygodnia (Balanced/Attacking/…) jest zapisywany, ale **nie ma** wpływu na zmiany atrybutów. |
| **Skauting zaawansowany** | Recruitment focuses (wiele równoległych), raport „czy pasuje do formacji”, zasięg skautów, czas raportu. | Jedna lista raportów (rating + notatki); brak „czy pasuje do naszej taktyki”. |
| **Wypożyczenia (loans)** | Wypożyczenie z/do innego klubu, czas, opłata, opcja wykupu, recall. | Brak. |
| **Obiekty / facilities** | Stadion (pojemność), ośrodek treningowy, akademii (junior coaching, youth recruitment). | Brak. |
| **Youth / newgens** | Młodzi z akademii, generowani co sezon; potencjał, wiek. | PlayerGenerator przy tworzeniu drużyny; brak akademii i „nowych” zawodników w trakcie gry. |
| **Media / wiadomości** | Inbox, wiadomości o transferach, kontraktach, konferencje przed/po meczu, plotki. | Tylko konferencja **po** meczu (wybór tonu → morale). Brak inboxu, newsów. |
| **Assistant advice** | Rady asystenta (skład, taktyka, transfery) w trakcie gry. | Brak. |
| **FMPedia / search** | Wyszukiwanie po nawigacji, obiektach, encjach; glosariusz w grze. | Brak; zwykła nawigacja SPA. |
| **Women's football / licencje** | Osobna baza, ligi kobiece (FM26). | Nie dotyczy (jeden typ rozgrywki). |

---

## 3. Pomysły na to, co moglibyśmy wprowadzić

Pogrupowane wg nakładu i wpływu na „feel” gry w stylu FM.

### 3.1 Stosunkowo niskie nakłady, duży efekt „FM”

| Pomysł | Opis | Gdzie |
|--------|------|--------|
| **Dual formation (uproszczone)** | Drugie pole w GamePlan: `formationNameOutOfPossession` (opcjonalne). Silnik: przy `possessionHome == false` używać OOP formacji dla gosp., przy `true` dla gości. PositionGenerator już ma `inPossession` — wystarczy wybór drugiej formacji w UI i przekazanie do silnika. | GamePlanInput, MatchSquadPage (dropdown „Formacja w obronie”), FullMatchEngine (wybór szablonu pozycji wg fazy). |
| **Kontrakt: data końca** | Dodać do Player (lub osobna tabela Contract): `contractEndSeasonMatchday: Option[Int]` lub `contractEndDate`. UI: lista „wygasające w tym sezonie”; bot przy ofercie może sprawdzać „czy przedłużyć”. | Domain.Player lub Contract; migracja; TeamPage — zakładka „Kontrakty”. |
| **Trening → lekki wpływ na rozwój** | Co kolejkę (lub co N kolejek): dla każdego gracza który grał, mały losowy przyrost wybranego atrybutu w zależności od `TrainingPlan.week` (np. Attacking → +1 do passing z małą szansą). Prosty wzór bez pełnego modelu FM. | LeagueService (po kolejce lub w „regeneracja”); PlayerRepository (update atrybutu); opcjonalnie TrainingPlanRepository. |
| **Board expectations (uproszczone)** | Dodać do League lub Team: `expectedFinish: Option[String]` („top 3”, „mid-table”, „avoid relegation”). Po zakończeniu sezonu: komunikat „Oczekiwania spełnione / nie”. Bez confidence slider — tylko tekst. | Domain (pole w League/Team lub nowa tabela BoardExpectation); jedna linijka w UI po sezonie. |
| **Inbox / wiadomości (minimum)** | Tabela `user_messages`: id, userId, title, body, readAt, createdAt. Endpoint: list, mark read. UI: ikona dzwonka z liczbą nieprzeczytanych; po kliku lista. Wiadomości: „Oferta transferowa przyjęta”, „Kontrakt X wygasa za 6 miesięcy” (gdy dodamy kontrakty). | Repository, Routes, frontend (komponent Inbox). |

### 3.2 Średnie nakłady

| Pomysł | Opis | Gdzie |
|--------|------|--------|
| **Recruitment focus (jeden)** | Jedno „focus” per drużyna: np. „szukam napastnika 18–24”, „obrońca do 500k”. Boty przy generowaniu ofert biorą to pod uwagę (już częściowo: kupują pod braki). UI: formularz na TeamPage (pozycja, przedział wieku, max cena). | Domain (RecruitmentFocus lub pola w Team); LeagueService.generateBotOffers; TeamPage. |
| **Skauting: „pasuje do formacji”** | Przy zapisie ScoutingReport liczyć podobieństwo pozycji zawodnika do domyślnej formacji drużyny (np. preferredPositions vs slots 4-3-3) i zapisać w raporcie lub w odpowiedzi API. Wyświetlać w UI „Dopasowanie: wysoki/średni/niski”. | LeagueService.createScoutingReport (wywołanie helpera); ScoutingReportDto (pole fit); TeamPage. |
| **Wypożyczenia (minimalne)** | Nowy typ „transferu”: Loan (fromTeamId, toTeamId, playerId, endMatchday, optionalFee). Gracz może „wypożyczyć” zawodnika do innej drużyny w lidze; po endMatchday wraca. Boty mogą oferować wypożyczenie. | Domain (Loan lub flag w TransferOffer); okno transferowe obsługuje typ „loan”; logika „zwrot” po kolejce. |
| **Konferencja prasowa przed meczem** | Obecnie tylko „po”. Dodać fazę „pre”: wybór tonu (np. „spokojnie” / „presja”) przed meczem; zapis w MatchState lub osobno; lekki modyfikator morale/freshness na start meczu. | MatchSquadPage lub osobny ekran „przed meczem”; backend: submitPressConference(..., "pre", tone). |
| **Assistant tips (tekstowe)** | Przy wejściu na MatchSquadPage: GET `/api/v1/matches/:id/assistant-tip` zwracający jeden losowy lub deterministyczny tip (np. „Rywal gra 4-4-2; rozważ pressing w strefach 7–8”). Serwer: prosty zestaw reguł (formacja rywala → tekst). | Routes; LeagueService (metoda tip(formationAway)); MatchSquadPage (blok z tipem). |

### 3.3 Większe nakłady (długoterminowe)

| Pomysł | Opis |
|--------|------|
| **Pełne kontrakty** | Pensja, długość, bonusy, klauzule (release, buyback); negocjacje (kilka rund); wpływ na morale („niezadowolony z kontraktu”). |
| **Board confidence** | Wskaźnik 0–100; spada przy słabych wynikach, rośnie przy spełnianiu oczekiwań; prośby o budżet/facilities. |
| **Youth intake / newgens** | Co sezon generowanie N młodych zawodników do akademii (lub do kadry); potencjał, wiek; rozwój w czasie. |
| **Facilities** | Stadion (pojemność → przychody?), poziom ośrodka treningowego (modyfikator rozwoju), akademii. |
| **Tactical visualiser** | Widok 3 tercji × 2 fazy (in/out possession) z pozycjami; wymaga albo precomputed positions albo uproszczonego wizualizera z PitchModel. |
| **Portal / Dashboard w stylu FM26** | Jedna strona „Portal”: kalendarz 2 tygodni, wyniki ligi, news (z Inbox), szybkie akcje. |

### 3.4 Ulepszenia bez „kopiowania FM”

| Pomysł | Opis |
|--------|------|
| **Więcej analityki w UI** | Wykres „xG timeline” już jest; dodać np. „possession by 15-min segment”, „press intensity by zone” z logów. |
| **Eksport do CSV z filtrami** | Filtrowanie po dacie, drużynie, typie zdarzenia przed eksportem. |
| **Porównanie dwóch zawodników** | Strona lub modal: atrybuty A vs B (słupki), statystyki sezonu. |
| **Historia spotkań H2H** | Dla pary drużyn: ostatnie N meczów, suma bramek, forma. |
| **Prognoza kolejki** | Na podstawie Elo/xG: „P(wygrana) 45%, P(remis) 28%” per mecz w nadchodzącej kolejce. |

---

## 4. Podsumowanie rekomendacji

- **Szybkie „wow” (1–2 dni robocze każdy):** dual formation (uproszczone), kontrakt data końca + lista wygasających, inbox (minimum), board expectations (tekst).
- **Średnio (tydzień każdy):** trening → rozwój (prosty model), recruitment focus (jeden), skauting „pasuje do formacji”, konferencja przed meczem, assistant tip.
- **Długoterminowo:** pełne kontrakty i negocjacje, board confidence, youth intake, facilities, tactical visualiser, Portal.

Dokument można traktować jako backlog: wybór konkretnych punktów zależy od priorytetu (realizm FM vs prostota vs analityka).
