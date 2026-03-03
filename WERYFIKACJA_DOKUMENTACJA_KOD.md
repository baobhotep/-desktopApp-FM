# Weryfikacja spójności dokumentacja ↔ kod

**Data**: luty 2026 (zaktualizowano po weryfikacji linia po linii)  
**Zakres**: WYMAGANIA_GRY.md, MODELE_I_PRZEPLYWY_APLIKACJI.md, KONTRAKTY_ARCHITEKTURA_IMPLEMENTACJA.md, DOKUMENTACJA_TECHNICZNA.md, ALGORYTMY_MAPOWANIE_KOD.md vs kod backendu i frontendu.

---

## 1. API (KONTRAKTY §5 vs Routes)

| Endpoint w doc | Stan w kodzie | Uwagi |
|----------------|----------------|--------|
| POST /auth/register, /auth/login, GET /auth/me | ✅ | Zgodne; wszystkie pod `/api/v1` |
| **Prefix /api/v1** | ✅ | Main.scala: `Router("/api/v1" -> routes.routes).orNotFound` |
| GET /leagues (lista lig użytkownika) | ✅ | Routes: `GET -> Root / "leagues"` → listLeagues(userId) |
| GET /invitations (lista zaproszeń) | ✅ | Routes: `GET -> Root / "invitations"` → listPendingInvitations(userId) |
| POST /leagues, GET /leagues/:id, /table, /teams, /fixtures | ✅ | Zgodne |
| POST /leagues/:id/invite, POST /invitations/accept | ✅ | Zgodne |
| POST /leagues/:id/start, POST /leagues/:id/add-bots | ✅ | Zgodne |
| POST /leagues/:id/matchdays/current/play | ✅ | Zgodne |
| GET /matches/:id, /matches/:id/log, /matches/:id/squads | ✅ | GET /matches/:id/log z query limit, offset i zwrotem summary, total |
| GET /teams/:id, /teams/:id/players, /teams/:id/game-plans | ✅ | game-plans zwraca listę snapshotów (nie pustą, gdy są zapisane) |
| GET /teams/:id/game-plans/:snapshotId | ✅ | Zwraca GamePlanSnapshotDetailDto z gamePlanJson |
| POST /teams/:id/game-plans | ✅ | Routes: zapis taktyki |
| GET /leagues/:id/transfer-windows | ✅ | Zaimplementowane |
| GET /transfer-offers?leagueId=&teamId= | ✅ | leagueId wymagany (brak → 400); test ApiIntegrationSpec; teamId opcjonalny |
| POST /leagues/:id/transfer-offers | ✅ | Zgodne z kodem; KONTRAKTY §5.6 zaktualizowane do tej ścieżki |
| POST /transfer-offers/:id/accept, /reject | ✅ | Zgodne |
| PUT /matches/:id/squads/:teamId | ✅ | Zaimplementowane (body: lineup, gamePlanJson) |
| PATCH /players/:id | ✅ | Zaimplementowane |

---

## 2. Modele domenowe (MODELE / KONTRAKTY vs Domain.scala)

| Encja | Zgodność | Uwagi |
|-------|----------|--------|
| League | ✅ | currentMatchday, totalMatchdays, seasonPhase, homeAdvantage, startDate, timezone (ZoneId) |
| Team | ✅ | ownerType + ownerUserId/ownerBotId |
| Player | ✅ | physical/technical/mental/traits/bodyParams, injury, freshness, morale |
| InjuryStatus | ✅ | sinceMatchday, returnAtMatchday, severity |
| Match, MatchResultLog, MatchEventRecord | ✅ | |
| **MatchSummary** | ✅ | Agregowany z events przez MatchSummaryAggregator.fromEvents, zapisywany w MatchResultLog; encodeSummary/decodeSummary w MatchResultLogRepository (Circe + MatchSummaryCodec) działają pełnie |
| MatchSquad, LineupSlot | ✅ | gamePlanJson w kodzie |
| TransferWindow, TransferOffer | ✅ | fromTeamId = kupujący, toTeamId = sprzedający |
| Invitation | ✅ | invitedByUserId, expiresAt w kodzie i w DB |

---

## 3. Baza danych (KONTRAKTY §7 vs Database.initSchema)

Tabele: users, leagues, teams, players, invitations, referees, matches, match_result_logs, match_squads, transfer_windows, transfer_offers — wszystkie obecne.  
**league_contexts** — ✅ istnieje w Database.scala (CREATE TABLE).  
**game_plan_snapshots** — ✅ istnieje w Database.scala (CREATE TABLE).

---

## 4. Reguły biznesowe

### 4.1 Transfery (MODELE §9.2, §9.4; KONTRAKTY §9.3)

- Semantyka: fromTeamId = kupujący, toTeamId = sprzedający; akceptuje sprzedający. W kodzie: ✅ zgodne; jedna aktywna oferta na (playerId, windowId, fromTeamId) — przy nowej ofercie poprzednie Pending od tego samego kupującego są odrzucane.

### 4.2 Tabela ligowa — tie-break (KONTRAKTY §4)

- **Zaimplementowane**: sortowanie po (-pts, -(gf-ga), -gf, -h2h, t.id.value). H2H używane **gdy dokładnie 2 drużyny** ex aequo (ta sama (pts, gd, gf)); stabilny sort po team.id na końcu. LeagueService.getTable: grouped po (pts, gd, gf), h2hPoints obliczane dla pary, withH2h, sorted. ✅

### 4.3 Rozegranie kolejki

- Idempotencja: ✅. Post-match: freshness, morale, injury z logu, regeneracja, okna transferowe: ✅.

---

## 5. Silnik i logi (KONTRAKTY §1–2, SILNIK)

- MatchEngineInput/MatchEngineResult – typy zgodne z doc. ✅
- SimpleMatchEngine: Poisson, KickOff, Goal; **pełna lista typów** z KONTRAKTY §2.1: Pass, LongPass, Cross, PassIntercepted, Dribble, DribbleLost, Shot, Foul, YellowCard, RedCard, Injury, Substitution, Corner, ThrowIn, FreeKick, Offside. Strictness używane (foulCount, kartki, RedCard). Morale → modyfikator lambda (moraleMod). ✅
- **MatchSummary**: Agregacja z events przez MatchSummaryAggregator.fromEvents; zapis w MatchResultLog(logId, matchId, events, **Some(summary)**); odczyt przez decodeSummary (JSON → MatchSummary). GET /matches/:id/log zwraca MatchLogDto z summary jako **pełnym** MatchSummaryDto (wszystkie pola z KONTRAKTY §2.3). ✅

---

## 6. Frontend (KONTRAKTY §6, DOKUMENTACJA_TECHNICZNA)

- **Zaimplementowane**: Logowanie, Rejestracja, Dashboard (wyloguj, formularz „Utwórz ligę” z walidacją 10–20 parzysta, timezone); **lista lig** („Moje ligi”); **lista zaproszeń** (GET /invitations, przycisk „Dołącz”); **widok ligi** (LeaguePage: tabela, terminarz, „Rozegraj kolejkę”, Setup: sloty, zaproszenie e-mail, dodaj boty, start sezonu); **kadra** (TeamPage, edycja imienia/nazwiska); **zapisane taktyki** (lista, zapis, wybór przy składzie); **ustawienia meczu** (MatchSquadPage, skład 11, wybór taktyki); **szczegóły meczu** (MatchDetailPage: log, filtr po typie zdarzenia, podsumowanie meczu); **okno transferowe i oferty** (składanie, akceptacja/odrzucenie); **akceptacja zaproszenia** (AcceptInvitationPage, link z tokenem). DTO w app.ApiDto: MatchDto, TransferOfferDto, TransferWindowDto, MatchLogDto, MatchSummaryDto, InvitationDto, ErrorBody itd. ✅

---

## 7. Inne (aktualny stan)

| Obszar | Stan | Uwagi |
|--------|------|--------|
| LeagueContext (Z-Score) | ✅ Częściowo | Tabela league_contexts, repo, obliczanie przy starcie sezonu (LeagueContextComputer), przekazywanie do silnika (LeagueContextInput). Silnik przyjmuje leagueContext; pełne użycie Z-Score w obliczeniach (np. IWP) może być uproszczone. |
| GamePlanSnapshot | ✅ | Tabela game_plan_snapshots, POST/GET /teams/:id/game-plans, GET /teams/:id/game-plans/:snapshotId |
| Sędziowie strictness w silniku | ✅ | Używane w SimpleMatchEngine (foulCount, yellowCount) |
| Morale w silniku | ✅ | moraleMod → lambda (modyfikator bramek); doc MODELE §7 mówi też o effective_composure – w silniku uproszczenie do wpływu na lambda |
| Automatyczna symulacja o 17:00 | ✅ | runScheduledMatchdays() w LeagueService; Main.scala: scheduler co 5 min (Schedule.spaced(300s)), sprawdza środa/sobota godz. ≥ 17 w timezone ligi, wywołuje playMatchday |

---

## 8. Co nie zostało zrobione / rozbieżności

1. ~~**KONTRAKTY §5.6**~~: Zaktualizowano dokument na POST /leagues/:id/transfer-offers. ✅  
2. ~~**MODELE §7 (effective_composure / effective_decisions z morale)**~~: Zaimplementowane w SimpleMatchEngine: effectiveComposure/effectiveDecisions z base*(0.85+0.15*morale); używane przy wyniku strzału (composure) i ryzyku kartki po faulu (decisions). Kontuzje: injuryProneFactor z traits zwiększa prawdopodobieństwo zdarzenia Injury. ✅  
3. ~~**Paginacja w UI logu meczu**~~: Zaimplementowano: ApiClient.getMatchLog(matchId, limitOpt, offsetOpt), MatchDetailPage ładuje pierwszą stronę (50 zdarzeń) i przycisk „Pokaż więcej”. ✅  
4. ~~**Testy integracyjne API (HTTP)**~~: ApiIntegrationSpec — POST register, login, GET /auth/me przez Client.fromHttpApp. ✅  
5. ~~**JWT / DB z env**~~: Main.scala odczytuje JWT_SECRET i DATABASE_URL z zmiennych środowiskowych (fallback na wartości deweloperskie). ✅

---

## 9. Stan końcowy (wszystko ukończone)

- **Silnik**: effective composure/decisions z morale (MODELE §7); injury prone w prawdopodobieństwie kontuzji (WYMAGANIA §4.1).
- **ThrowInConfig**: typ w shared/domain/ThrowInConfig.scala; GamePlanInput ma opcjonalne throwInConfig (FORMACJE §13.4).
- **Paginacja**: GET /leagues/:id/fixtures?limit=&offset=; UI terminarza z przyciskiem „Pokaż więcej”.
- **DOKUMENTACJA_TECHNICZNA.md** §7: GET /teams/:id/game-plans zwraca listę snapshotów (zgodne z kodem).

---

*Ostatnia aktualizacja: luty 2026 — weryfikacja linia po linii z kodem.*
