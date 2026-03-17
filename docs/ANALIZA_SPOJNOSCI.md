# Analiza spójności backend ↔ UI

**Data:** 2025-03  
**Cel:** Weryfikacja 100% spójności między backendem a UI oraz wewnętrznej spójności kodu.

---

## 1. Backend – silnik i analityka (REVIEW_DOGLEBNE)

| # | Element | Status | Uwagi |
|---|---------|--------|--------|
| 1.1 | SimpleMatchEngine – zoneThreat / Cross | **OK** | `zoneThreat(zone, isHome)` używa `DxT.baseZoneThreat(zone, isHome)`; Cross używa `PitchModel.isAttackingThird(z, isHome)`. |
| 1.2 | FullMatchAnalytics – xT / OBSO | **OK** | `baseThreatHome = (z: Int) => DxT.baseZoneThreat(z, true)`; xT i OBSO w perspektywie gospodarzy, udokumentowane w komentarzu. |
| 1.3 | MatchSummaryAggregator – passes in final third | **OK** | `isAttackingThird(zone, isHome(tid))` – perspektywa drużyny. |
| 1.4 | LeagueService – traverseConn | **OK** | Używa `cats.Applicative[ConnectionIO].pure(List.empty[B])` – brak zbędnego zapytania. |
| 1.6 | AdvancedAnalyticsSpec | **OK** | Testy używają `DxT.baseZoneThreat(z, true)`. |

---

## 2. Integracja desktop ↔ backend

| Element | Status | Uwagi |
|---------|--------|--------|
| DesktopLauncher | **OK** | `DesktopBootstrap.bootstrap(runtime)` → `GameFacadeAdapter(facade)` lub fallback do `StubGameAPI`. |
| GameAPI ↔ GameFacade | **OK** | Wszystkie wywołania `gameApi.*` z ekranów mają odpowiadające metody w GameFacade i GameFacadeAdapter. |
| LeagueViewScreen | **OK** | Terminarz (getFixtures), nazwy drużyn (listTeams), „Obejrzyj mecz” (getMatchLog → MatchSummaryScreen/MatchPlaybackScreen), „Ustaw skład” (SquadScreen), odświeżenie po „Rozegraj kolejkę”. |
| Session / getMe | **OK** | FMGame przy starcie wywołuje getMe(userId) przy przywracaniu sesji (SessionPersistence). |

---

## 3. Spójność UI – odstępy i style

| Zmiana | Pliki |
|--------|--------|
| Stałe `Assets.padSection` (16px) i `Assets.padControl` (8px) | Zdefiniowane w Assets.scala. |
| Użycie w ekranach | LeagueViewScreen, MainMenuScreen, LeagueListScreen, CreateEnglishLeagueScreen, InvitationsScreen – padding między sekcjami i przyciskami ujednolicony (padSection między sekcjami, padControl między kontrolkami). |

Dzięki temu:
- Między sekcjami: `padSection` (16).
- Między przyciskami / polami: `padControl` (8).
- Tło i tytuły: spójny styl (title, screenBackgroundColor).

---

## 4. Lista lig – grupowanie po systemie

- LeagueListScreen grupuje ligi po `leagueSystemName`.
- Ligi bez systemu na górze; ligi w systemie (np. „English”) w sekcji z nagłówkiem „English (4 szczeble)”, posortowane wg `tier`.

---

## 5. Rekomendacje na później

- **Awans/spadek** – po zakończeniu sezonu przenoszenie drużyn między ligami w systemie (np. English).
- **Play-offy** – 3.–6. miejsce (Championship, League One, League Two).
- **build.sbt** – opcjonalnie dodać `-Wunused:all` w scalacOptions.
- **Rate limit / timeout** – dokumentacja lub middleware dla API HTTP (jeśli używane).

---

## 6. Overall zawodnika i paski atrybutów (kategorie)

- **PlayerOverall (backend):** overall pozycyjny (wagi atrybutów zależne od pozycji: GK, CB, LB/RB, DM, CM, AM, LW/RW, ST, CDM, CAM), plus `physicalAvg`, `technicalAvg`, `mentalAvg`, `defenseAvg` (1–20).
- **PlayerDto:** pola `overall`, `physicalAvg`, `technicalAvg`, `mentalAvg`, `defenseAvg`; backend uzupełnia w `toPlayerDto(Player)` w getTeamPlayers, updatePlayer, comparePlayers.
- **UI:** TeamViewScreen – dla każdego zawodnika: OVR + paski Fiz / Tech / Men / Obr (wartość/20 + ProgressBar). PlayerEditorScreen – na górze podsumowanie: Overall + 4 paski.

Szczegóły porównania z FM i lista brakujących funkcji: **docs/FM_BRAKI_ANALIZA.md**.

---

## 7. Podsumowanie

- Backend: perspektywa drużyny (isHome) w SimpleMatchEngine, FullMatchAnalytics i MatchSummaryAggregator jest spójna.
- Desktop używa GameFacade przez GameFacadeAdapter; bootstrap z fallbackiem do Stuba.
- Przepływ: login → menu → lista lig (z grupowaniem) → widok ligi → terminarz / „Obejrzyj mecz” / „Ustaw skład” / „Rozegraj kolejkę” – zaimplementowany i spójny.
- Odstępy w kluczowych ekranach ujednolicone przez `Assets.padSection` i `Assets.padControl`.
- Overall i paski kategorii (Fiz/Tech/Men/Obr) spójne między backendem a UI.
