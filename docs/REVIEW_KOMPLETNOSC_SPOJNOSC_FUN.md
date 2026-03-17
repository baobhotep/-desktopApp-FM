# Przegląd: kompletność, spójność, logika, fun

**Data:** 2026-03  
**Zakres:** Backend + desktop UI, dokumenty FM_BRAKI_ANALIZA i FM_CO_ZOSTALO_NA_FUN.

---

## 1. Wdrożone w tej sesji

| Element | Opis |
|--------|------|
| **Wpływ taktyki na mecz** | W MatchSummaryScreen po meczu: wyświetlane **Przechwyty (odzyskania)** i **Wślizgi (wygrane)** z MatchSummaryDto – gracz widzi statystyki defensywne (odzyskania piłki, wślizgi). |
| **Aktualizacja FM_BRAKI_ANALIZA** | Sekcja 2.4 Play-offy oznaczona jako ✅ Zaimplementowane (createPlayOffSemiFinals, createPlayOffFinal, rozgrywka przez „Rozegraj kolejkę”). |

---

## 2. Mapowanie GameAPI ↔ UI (desktop)

Wszystkie metody GameAPI mają implementację w GameFacade / GameFacadeAdapter i StubGameAPI. Poniżej **użycie w ekranach desktop**.

| Metoda | Gdzie używana |
|--------|----------------|
| login, register | LoginScreen, RegisterScreen |
| getMe | FMGame (przywracanie sesji) |
| listLeagues | LeagueListScreen |
| createLeague, addBots, startSeason | CreateLeagueScreen |
| createEnglishLeagueSystem, startSeasonForSystem | CreateEnglishLeagueScreen |
| applyPromotionRelegation, startNextSeasonForSystem | LeagueViewScreen (przycisk „Zastosuj awans/spadek i rozpocznij nowy sezon”) |
| createPlayOffSemiFinals, createPlayOffFinal | LeagueViewScreen (przyciski baraży dla tier 2–4) |
| getLeague, getTable, getFixtures, playMatchday | LeagueViewScreen, MatchdayResultsScreen |
| listTeams | LeagueViewScreen, TeamViewScreen, SquadScreen, MatchdayResultsScreen |
| getTeam, getTeamPlayers, getTeamContracts | TeamViewScreen, SquadDepthScreen, ContractsScreen, ComparePlayersScreen |
| updatePlayer | PlayerEditorScreen |
| listGamePlanSnapshots, getGamePlanSnapshot, saveGamePlan | FormationEditorScreen |
| getLeaguePlayerStats, getLeaguePlayerAdvancedStats, getMatchdayPrognosis | LeagueStatsScreen |
| getAssistantTip | SquadScreen (wyświetlanie tipu + przycisk „Rada asystenta”) |
| getComparePlayers | ComparePlayersScreen |
| getMatch, getMatchLog, getMatchSquads, submitMatchSquad | LeagueViewScreen (Obejrzyj mecz, Ustaw skład), SquadScreen |
| getH2H | SquadScreen |
| getTrainingPlan, upsertTrainingPlan | TrainingPlanScreen |
| listLeaguePlayers, getShortlist, addToShortlist, removeFromShortlist, listScoutingReports, createScoutingReport | ScoutingScreen (lista + przycisk „Raport” → dialog ocena/notatki) |
| getTransferWindows, getTransferOffers, acceptTransferOffer, rejectTransferOffer, createTransferOffer, counterTransferOffer | TransfersScreen, CreateOfferScreen |
| createInvitation | LeagueViewScreen (przycisk „Zaproś gracza (e-mail)” w fazie Setup) |
| listPendingInvitations, acceptInvitation | InvitationsScreen |

**Metody API bez przycisku/formularza w desktop:**  
- Brak – wszystkie metody mają odpowiadające akcje w UI (createScoutingReport: przycisk „Raport” przy zawodniku w ScoutingScreen → dialog ocena 0–10 + notatki).

---

## 3. Przepływy użytkownika (logika)

### 3.1 Nowa gra (liga angielska)

1. Rejestracja / logowanie → MainMenu.  
2. „Utwórz ligę angielską” → CreateEnglishLeagueScreen → createEnglishLeagueSystem → startSeasonForSystem("English").  
3. Lista lig → grupowanie „English (4 szczeble)”, wybór ligi.  
4. Widok ligi: tabela (pusta przed pierwszą kolejką), terminarz, „Rozegraj kolejkę”, „Moja drużyna”.  
5. „Rozegraj kolejkę” → playMatchday → przekierowanie na MatchdayResultsScreen (wyniki kolejki, minitabela, komentarz, „Pierwsza wygrana w sezonie!” gdy 1. wygrana).  
6. „Dalej” → powrót do LeagueViewScreen.  
7. Terminarz: „Ustaw skład” → SquadScreen (rada asystenta na górze), FormationEditor, „Zatwierdź skład”; „Obejrzyj mecz” → MatchSummaryScreen (wynik, posiadanie, xG, przechwyty, wślizgi) → Odtwórz / Wstecz.

**Spójność:** League.tier i leagueSystemName używane do: cel sezonu w TeamView (tier 1 vs 2–4), przycisk „Zastosuj awans/spadek i rozpocznij nowy sezon” (gdy sezon zakończony + system), przyciski baraży (Finished + tier 2–4), „Kolejka X – decydująca…” (ostatnie 2 kolejki).

### 3.2 Zakończenie sezonu i nowy sezon

1. Rozegranie wszystkich kolejek (currentMatchday = totalMatchdays) → liga w stanie Finished.  
2. Dla lig w systemie (np. English): przycisk „Zastosuj awans/spadek i rozpocznij nowy sezon” → applyPromotionRelegation → startNextSeasonForSystem.  
3. Backend: przeniesienie drużyn (3 ostatnie / 3 pierwsze), dla każdej ligi usunięcie meczów i ofert, wygenerowanie nowych meczów, currentMatchday = 0, seasonPhase = InProgress.  
4. Po odświeżeniu: tabela zresetowana, terminarz nowy, „Rozegraj kolejkę” od kolejki 1.

**Logika:** Tabela i terminarz odzwierciedlają stan po awansie/spadku (teamy mają zaktualizowane league_id). „Moja drużyna” pokazuje drużynę użytkownika w nowej lidze.

### 3.3 Baraże (tier 2–4)

1. Sezon zakończony (Finished), liga tier 2, 3 lub 4.  
2. „Przygotuj półfinały baraży” → createPlayOffSemiFinals (2 mecze: 3. vs 6., 4. vs 5.).  
3. „Rozegraj kolejkę” → rozgrywka półfinałów (playMatchday dopuszcza matchday 39/40).  
4. „Przygotuj finał baraży” → createPlayOffFinal (zwycięzcy półfinałów).  
5. „Rozegraj kolejkę” → finał.

**Uwaga:** Remisy w półfinałach nie są obsługiwane (wymagany wynik; w FM dogrywka/kary). Obecna logika: oba półfinały muszą mieć zwycięzcę.

---

## 4. Spójność danych i edge case’y

| Obszar | Stan | Uwagi |
|--------|------|--------|
| **Tabela po awansie/spadku** | OK | getTable(leagueId) – drużyny mają zaktualizowane league_id, więc tabela jest po stronie nowej ligi. |
| **Terminarz po nowym sezonie** | OK | Nowe mecze dla tej samej ligi; getFixtures zwraca nowy terminarz. |
| **Moja drużyna po spadku/awansie** | OK | Team.leagueId zaktualizowane; TeamView i listTeams pokazują drużynę w nowej lidze. |
| **Pusta tabela / brak meczów** | OK | UI obsługuje puste listy (np. „Brak tabeli”, „Brak wyników”). |
| **Liga bez tier (własna liga)** | OK | Cel sezonu nie jest wyświetlany; przyciski baraży i „decydująca kolejka” nie pojawiają się dla takich lig. |
| **StubGameAPI** | OK | Używany gdy brak backendu; ekrany nie crashują, zwracają Left/Empty. |

---

## 5. Kompletność względem FM i „fun”

### Zaimplementowane (parity + fun)

- Overall pozycyjny, paski Fiz/Tech/Men/Obr, condition/matchSharpness, głębokość kadry, kontrakty (expiry, pensje), koszty kadry vs budżet, alerty kontraktowe (do kol. X).  
- Liga angielska 4 szczeble, awans/spadek, **nowy sezon w jednym kroku**, play-offy (półfinały + finał).  
- Wyniki kolejki + minitabela, komentarz po kolejce, kamienie milowe (pierwsza wygrana), cel sezonu, asystent przed meczem, ostatnie kolejki – podkreślenie decydującej.  
- Podsumowanie meczu: wynik, posiadanie, xG, strzały, **przechwyty, wślizgi** (wpływ taktyki).

### Celowo pominięte / na później

- **Dualne formacje** (in/out of possession) – duży zakres.  
- **Recruitment hub** (wymagania innych klubów, pitch opportunities) – duży zakres.  
- **Wizualizacja taktyki** (drugi widok formacji) – opcjonalne.  
- **Licencje / stroje** – assety zewnętrzne.  
- **Remisy w barażach** (dogrywka/kary) – uproszczenie.

### Luki UI (zaktualizowane)

- ~~Brak formularza „Złóż ofertę” i „Skontruj” w TransfersScreen~~ → **Zaimplementowane** (CreateOfferScreen, przycisk „Skontruj” przy ofertach do nas).
- ~~Brak „Zaproś gracza” (createInvitation) w widoku ligi (Setup)~~ → **Zaimplementowane** (przycisk „Zaproś gracza (e-mail)” + dialog).
- ~~Brak formularza raportu scoutingowego~~ → **Zaimplementowane** (przycisk „Raport” przy zawodniku w ScoutingScreen).

---

## 6. Podsumowanie i rekomendacje

- **Kompletność:** Większość funkcji z FM_BRAKI_ANALIZA i FM_CO_ZOSTALO_NA_FUN jest wdrożona. Backend i UI są ze sobą zsynchronizowane (GameAPI ↔ ekrany, tier/system/season/play-off).  
- **Spójność:** Przepływ sezon → awans/spadek → nowy sezon oraz baraże są logicznie spójne; tabela, terminarz i „Moja drużyna” odzwierciedlają aktualny stan.  
- **Fun:** Cel sezonu, wyniki kolejki z komentarzem i kamieniami milowymi, asystent przed meczem, decydujące kolejki, przechwyty/wślizgi w podsumowaniu meczu – wzmacniają odczucie wpływu i kontekstu ligi.

**Rekomendacje kolejnych kroków (opcjonalnie):**

1. ~~Dodać w TransfersScreen: „Złóż ofertę” i „Skontruj”~~ – zrobione.  
2. ~~Dodać w widoku ligi (Setup): „Zaproś gracza”~~ – zrobione.  
3. ~~Testy E2E~~ – zrobione: **DesktopE2ESpec** – (1) pełny flow: register → liga → playMatchday → getMatchLog; (2) liga angielska: createEnglishLeagueSystem → startSeasonForSystem → rozegranie wszystkich kolejek we wszystkich 4 ligach → applyPromotionRelegation → startNextSeasonForSystem; (3) baraże: jedna liga tier-2 z drużyną użytkownika → pełny sezon → createPlayOffSemiFinals → playMatchday → createPlayOffFinal → playMatchday (test z retry 3× ze względu na możliwe remisy w półfinałach).
