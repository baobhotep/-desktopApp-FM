# Analiza dokumentacja ↔ kod — krytyczny przegląd i stan pozostały

**Data**: luty 2026  
**Zakres**: Weryfikacja wszystkich dokumentów (WYMAGANIA, MODELE, KONTRAKTY, DOKUMENTACJA_TECHNICZNA, WERYFIKACJA, SILNIK, FORMACJE, ATRYBUTY, ALGORYTMY) oraz pełnego kodu backendu, frontendu i shared; przegląd techniczny.

---

## 1. Podsumowanie wykonawcze

- **Zgodność ogólna**: Wysoka. API, modele domenowe, baza, flow biznesowe i frontend są zgodne z KONTRAKTY i MODELE. Dokumentacja techniczna i WERYFIKACJA odzwierciedlają stan implementacji.
- **Luki**: Zlikwidowane w sesji 2026: API zwraca pełny MatchSummaryDto; silnik emituje wszystkie typy zdarzeń z kanonu §2.1; dodano test 401 i test balansu generatora; jedna transakcja JDBC na „Rozegraj kolejkę".
- **Do zrobienia**: Brak — zakres v1 z KONTRAKTY i WYMAGANIA jest zaimplementowany.

---

## 2. Dokumentacja vs kod — double-check

### 2.1 API (KONTRAKTY §5, DOKUMENTACJA_TECHNICZNA §5)

| Endpoint / aspekt | Stan | Uwagi |
|-------------------|------|--------|
| Wszystkie ścieżki pod `/api/v1` | ✅ | Routes: `Router("/api/v1" -> routes.routes)` |
| POST /auth/register, login, GET /auth/me | ✅ | Zgodne |
| GET /leagues, GET /invitations | ✅ | Zgodne |
| POST /leagues, GET /leagues/:id, /table, /teams, /fixtures (limit, offset) | ✅ | GET /leagues/:id, /table, /teams, /fixtures **bez** wymogu auth (publiczne) — KONTRAKTY nie mówią wprost o auth dla tych GET; celowo lub do rozważenia |
| POST /leagues/:id/invite, POST /invitations/accept | ✅ | Zgodne; accept wymaga inv.invitedUserId == userId |
| POST /leagues/:id/start, add-bots, matchdays/current/play | ✅ | Zgodne |
| GET /matches/:id, /log (limit, offset), /squads | ✅ | Log zwraca MatchLogDto(events, summary?, total?) |
| PUT /matches/:id/squads/:teamId | ✅ | Body: lineup, gamePlanJson |
| GET /teams/:id, /players, /game-plans, /game-plans/:snapshotId, POST /game-plans | ✅ | Zgodne |
| GET /leagues/:id/transfer-windows | ✅ | Zgodne |
| GET /transfer-offers?leagueId=&teamId= | ✅ | leagueId **wymagany** (400 bez niego), teamId opcjonalny |
| POST /leagues/:id/transfer-offers, accept, reject | ✅ | Zgodne |
| PATCH /players/:id | ✅ | Body: firstName?, lastName? |

**Rozbieżność**: KONTRAKTY §5.6 „GET /transfer-offers” — query leagueId, teamId. W kodzie brak leagueId → 400. Dokumentacja może doprecyzować, że leagueId jest wymagany.

### 2.2 Modele domenowe (MODELE, KONTRAKTY, Domain.scala)

| Encja / typ | Zgodność | Uwagi |
|--------------|----------|--------|
| League | ✅ | currentMatchday, totalMatchdays, seasonPhase, homeAdvantage, startDate, timezone, createdByUserId |
| Team | ✅ | ownerType, ownerUserId, ownerBotId, defaultGamePlanId |
| Player | ✅ | physical/technical/mental/traits/bodyParams (Map), injury, freshness, morale |
| InjuryStatus | ✅ | sinceMatchday, returnAtMatchday, severity (String w kodzie; MODELE enum) |
| Match, MatchResultLog, MatchEventRecord | ✅ | resultLogId w Match; events + summary w MatchResultLog |
| **MatchSummary** (Domain) | ✅ | Pełna struktura jak KONTRAKTY §2.3 (possession, shots, passes, tackles, fouls, corners, throwIns, vaepTotal, wpaFinal, …) |
| **MatchSummaryDto** (API) | ✅ | **Pełny** (sesja 2026): wszystkie pola z KONTRAKTY §2.3 (possessionPercent, homeGoals, awayGoals, shotsTotal, passesTotal, fouls, corners, crosses, longBalls, interceptions, redCards, throwIns, freeKicksWon, offsides itd. — listy [home, away]). GET /matches/:id/log zwraca summary jako pełny MatchSummaryDto. |
| MatchSquad | ✅ | gamePlanJson w kodzie (KONTRAKTY dopuszczały GamePlan lub snapshotId) |
| Invitation | ✅ | invitedUserId, invitedByUserId w Domain i DB; InvitationDto z invitedByUserId (Option) w API |
| TransferWindow, TransferOffer | ✅ | fromTeamId = kupujący, toTeamId = sprzedający |
| LeagueContext | ✅ | positionStats (Map) — w Domain jako Map[String, Map[String, (Double, Double)]], MODELE §1.12 Position → mean/stddev |

### 2.3 Baza danych (KONTRAKTY §7, Database.initSchema)

Wszystkie tabele z §7.1 obecne: users, leagues, teams, players, invitations, referees, matches, match_squads, match_result_logs, league_contexts, game_plan_snapshots, transfer_windows, transfer_offers. Kolumny zgodne z encjami (invited_user_id, invited_by_user_id, result_log_id itd.).

### 2.4 Silnik i typy zdarzeń (KONTRAKTY §2.1, SimpleMatchEngine)

**Kanon §2.1**: KickOff, Pass, LongPass, Cross, PassIntercepted, Dribble, DribbleLost, Shot, Goal, Foul, YellowCard, RedCard, Injury, Substitution, Corner, ThrowIn, FreeKick, Offside.

**Silnik emituje** (po rozszerzeniu 2026): KickOff, Pass, LongPass, Cross, PassIntercepted, Dribble, DribbleLost, Shot, Goal, Foul, YellowCard, RedCard, Injury, Substitution, Corner, ThrowIn, FreeKick, Offside — **pełna lista** z KONTRAKTY §2.1. MatchSummaryAggregator zlicza LongPass (longBallsTotal/Successful), PassIntercepted (interceptions), Cross, RedCard itd.

### 2.5 Reguły biznesowe

- **Tie-break tabeli** (KONTRAKTY §4): punkty → różnica bramek → bramki → H2H (tylko dla pary) → stabilny sort po team.id. ✅ Zaimplementowane w LeagueService.getTable.
- **Transfery**: jedna aktywna oferta Pending na (playerId, windowId, fromTeamId); 16–20 graczy; budżet. ✅
- **Rozegranie kolejki**: idempotencja; post-match freshness, morale, injury z logu, regeneracja +0.15, okna transferowe. ✅
- **Zaproszenie**: zaproszony musi istnieć w systemie (findByEmail); akceptacja tylko przez inv.invitedUserId. ✅

### 2.6 Frontend (KONTRAKTY §6, ekrany)

Obecne strony: LoginPage, RegisterPage, DashboardPage, LeaguePage, TeamPage, MatchSquadPage, MatchDetailPage, AcceptInvitationPage.  
Odpowiadają §6: logowanie, rejestracja, dashboard (moje ligi, zaproszenia, utwórz ligę), widok ligi (Setup/InProgress), kadra, zapisane taktyki (w TeamPage + MatchSquadPage), ustawienia meczu, szczegóły meczu (log, filtr typów, podsumowanie), akceptacja zaproszenia, transfery (w LeaguePage). ✅

---

## 3. Przegląd techniczny

### 3.1 Mocne strony

- **Separacja warstw**: Domain (backend/domain), Engine (EngineTypes, SimpleMatchEngine), Service (LeagueService, UserService), Repository (Doobie), API (Routes) — czytelny podział.
- **Typy**: opaque types dla ID (shared/domain/Ids), spójne użycie w całym backendzie.
- **MatchSummary**: pełny model w Domain i w MatchSummaryAggregator; zapis w MatchResultLog przez MatchSummaryCodec (Circe); GET /matches/:id/log zwraca summary jako podzbiór (MatchSummaryDto).
- **Testy**: SimpleMatchEngineSpec (determinizm, VAEP, ThrowIn, zone/xPass/zoneThreat, ACWR), LeagueServiceSpec (flow: create league → add-bots → start → fixtures → play → table), ApiIntegrationSpec (register, login, GET /auth/me, GET league, GET fixtures z paginacją, GET match log).

### 3.2 Uwagi techniczne / potencjalne ulepszenia

1. **GET /leagues/:id, /table, /teams, /fixtures bez auth**  
   Obecnie publiczne. Jeśli ligi mają być widoczne tylko dla uczestników, należałoby dodać weryfikację (np. czy użytkownik ma drużynę w lidze) lub doprecyzować w doc, że to API publiczne.

2. **MatchSummaryDto vs MatchSummary**  
   Rozszerzenie API o więcej pól MatchSummary (np. passesTotal, shotsOnTarget, yellowCards) ułatwiłoby zgodność z WYMAGANIA §8.2 bez zmiany Domain.

3. **InvitationDto bez invitedByUserId**  
   Celowe ograniczenie; jeśli UI będzie pokazywać „zaproszenie od X”, trzeba dodać pole lub osobny endpoint.

4. **PlayerGenerator**  
   ​18 graczy (1 GK + 17 polowych), balans targetTeamSum. ✅ Test balansu w PlayerGeneratorSpec (suma atrybutów w tolerancji ±15% od targetTeamSum).

5. **Silnik**: GamePlanInput to uproszczenie (formationName + throwInConfig). Pełny GamePlan z FORMACJE nie jest jeszcze mapowany na wejście silnika; domyślny 4-3-3 wszędzie.

6. **Błędy API**  
   Spójne ErrorBody(code, message); 401 dla braku/nieprawidłowego JWT. ✅ Test „protected endpoint without token returns 401” w ApiIntegrationSpec (KONTRAKTY §10.4).

7. **Transakcje**  
   ✅ „Rozegraj kolejkę” wykonuje wszystkie zapisy w **jednej transakcji JDBC** (writeMatchdayInTransaction).

8. **RedCard**  
   ✅ Silnik emituje RedCard (druga żółta dla tego samego gracza oraz z małym prawdopodobieństwem bezpośrednio z faułu).

---

## 4. Co jeszcze zostało do zrobienia

### 4.1 Z dokumentacji („opcjonalnie” / „na później”)

- **DOKUMENTACJA_TECHNICZNA §7**: Zaimplementowano (luty 2026): **FullMatchEngine** — maszyna stanów zdarzenie po zdarzeniu, Pitch Control (kontrola stref z pozycji 22 graczy), DxT (zagrożenie strefy skorygowane o kontrolę), formułowe xG/VAEP (interfejsy xGModel/VAEPModel + FormulaBasedxG/FormulaBasedVAEP), zaawansowana logika botów (BotSquadBuilder — skład po overall, wybór formacji 4-3-3/4-4-2/3-5-2, TriggerConfig: pressZones, counterTriggerZone). Modele ML (XGBoost/LightGBM) pozostają opcjonalnym rozszerzeniem (podmiana implementacji xGModel/VAEPModel).
- **SILNIK**: Domyślnie używany **FullMatchEngine** (Main); **SimpleMatchEngine** zachowany dla testów i kompatybilności. FullMatchEngine: stan meczu (minuta, wynik, strefa piłki, posiadanie, pozycje 22 graczy, Pitch Control per strefa, DxT), generowanie zdarzeń sekwencyjnie z uwzględnieniem triggerów (pressing w strefach).

### 4.2 Zalecane (spójność z doc i jakość)

1. **Doprecyzowanie dokumentacji**  
   - GET /transfer-offers: wymagany parametr `leagueId`.  
   - MatchSummaryDto: wpis w DOKUMENTACJA_TECHNICZNA lub WERYFIKACJA, że API zwraca „key stats” (5 pól), a pełny summary jest w DB.

2. **Testy (KONTRAKTY §10)**  
   - Test balansu generatora (suma atrybutów per drużyna w tolerancji). ✅  
   - Test API: wywołanie endpointu chronionego bez tokena → 401. ✅
   - Test GET /transfer-offers bez leagueId → 400. ✅

3. **Rozszerzenia zaimplementowane**
   - MatchSummary: shotsBlocked z Shot (Blocked); vaepTotal, wpaFinal z analytics. SeasonPhase.Finished po ostatniej kolejce. Bot auto-akceptacja oferty (amount ≥ estimatedPrice). Pula imion/nazwisk w PlayerGenerator.

### 4.3 Już zrobione (kontrola względem wcześniejszych list)

- VAEP/WPA, ThrowIn, zone, xPass, zoneThreat, IWP, zmęczenie (Pass po 70.), ACWR (recentMinutesPlayed, acwrFactor). ✅  
- Paginacja: fixtures, match log (backend + frontend „Pokaż więcej”). ✅  
- Testy integracyjne API: GET league, GET fixtures z limit/offset, GET match log. ✅  
- Dokumentacja techniczna: §7 zaktualizowany (VAEP/WPA, ThrowIn, ACWR, opcjonalnie dalej). ✅  

---

## 5. Tabela zgodności wymagań (WYMAGANIA §12)

| Wymaganie | Stan w kodzie |
|-----------|----------------|
| Atrybuty 30+6, 1–20, traits | ✅ Player (Map physical/technical/mental/traits); PlayerGenerator z listami kluczy |
| Silnik (xPass, xG, IWP, zmęczenie) | ✅ Uproszczony silnik: xPass/xG w metadata, IWP przy Foul, zmęczenie Pass po 70. |
| Formacje, GamePlan, ThrowInConfig | ✅ GamePlanInput(formationName, throwInConfig); ThrowIn w events |
| Kontuzje, ACWR, injury prone | ✅ Injury w events → Player.injury; recentMinutesPlayed, acwrFactor w ryzyku kontuzji |
| Morale → composure/decisions | ✅ effectiveComposure, effectiveDecisions z morale w silniku |
| Sędzia strictness | ✅ Referee.strictness → foul/card risk w silniku |
| 18 zawodników na zespół | ✅ PlayerGenerator: 1 GK + 17 polowych |
| 16–20 po transferach | ✅ Walidacja przy accept oferty |
| Tabela klasyczna, tie-break | ✅ getTable z H2H dla pary |
| Liga 10–20, śr+sob 17:00 | ✅ totalMatchdays, scheduler runScheduledMatchdays |
| Pełne logi, analityka | ✅ events + summary (key stats w API); VAEP/WPA w silniku |

---

## 6. Wnioski

- **Dokumentacja i kod są ze sobą zgodne** w zakresie API, modeli, bazy, flow i frontendu. Nieliczne rozbieżności (MatchSummaryDto, typy zdarzeń silnika, brak testów 401/balansu) są opisane powyżej i mają niski priorytet lub są celowymi uproszczeniami.
- **Pozostałe prace**: brak w zakresie v1. Wszystkie wcześniej zalecane punkty (pełny MatchSummaryDto, wszystkie typy zdarzeń, test 401, test balansu generatora, jedna transakcja na kolejkę, doprecyzowanie doc) są zrealizowane.

*Raport wygenerowany po przejrzeniu: DOKUMENTACJA_TECHNICZNA.md, WERYFIKACJA_DOKUMENTACJA_KOD.md, KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md, WYMAGANIA_GRY.md, MODELE_I_PRZEPLYWY_APLIKACJI.md, SILNIK_SYMULACJI_MECZU_PLAN_TECHNICZNY.md oraz kodu backend (Routes, Domain, Engine, LeagueService, MatchSummaryAggregator, PlayerGenerator, Database, Repositories), shared (ApiDto, Ids), frontend (strony, ApiClient).*
