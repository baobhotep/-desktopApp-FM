# FM-style Backend/Frontend Feature Report

Concise structured report for comparison with Football Manager features.

---

## 1. Backend packages (`backend/src/main/scala/fmgame/backend/`)

| Package | Contents (1–2 lines) |
|--------|-----------------------|
| **domain** | `Domain.scala`: entities (User, League, Team, Player, Match, Referee, Invitation, MatchResultLog, MatchSummary, TransferWindow, TransferOffer, MatchSquad, LeagueContext, GamePlanSnapshot); `IdGen` for UUIDs. Re-exports shared IDs. |
| **service** | LeagueService, UserService, BotSquadBuilder, DefaultSquadBuilder, PlayerGenerator, PlayerOverall, FixtureGenerator, MatchSummaryAggregator, LeagueContextComputer, ExportFormats, AuthService (auth/). |
| **repository** | DB access: Database, TeamRepository, PlayerRepository, MatchRepository, LeagueRepository, MatchResultLogRepository, MatchSquadRepository, GamePlanSnapshotRepository, TransferWindowRepository, TransferOfferRepository, ScoutingReportRepository, ShortlistRepository, TrainingPlanRepository, InvitationRepository, RefereeRepository, LeagueContextRepository, LeaguePlayerMatchStatsRepository, UserRepository. Codecs: MatchSummaryCodec, EnumParse. |
| **engine** | Match simulation: FullMatchEngine, SimpleMatchEngine, MatchState, PitchModel (PitchControl, DxT, PositionGenerator, MatchupMatrix), AdvancedAnalytics, AnalyticsModels. xG/VAEP: EngineConfig, EngineTypes (GamePlanInput, ShotContext, VAEPContext), FormulaBasedxG, LoadablexGModel, OnnxXGModel, LoadableVAEPModel, OnnxVAEPModel, EngineConstants. |
| **api** | Routes, MatchSummaryDtoCodec. |

---

## 2. Frontend routes/pages (`frontend/src/main/scala/app/`)

| File | Role |
|------|------|
| **App.scala** | Root router: token → LoginPage or RegisterPage; invitationToken → AcceptInvitationPage; lineupContext → MatchSquadPage; selectedMatchId → MatchDetailPage; (leagueId, teamId) → TeamPage; leagueId only → LeaguePage; else → DashboardPage. |
| **LoginPage.scala** | Login form (email, password). |
| **RegisterPage.scala** | Registration form. |
| **DashboardPage.scala** | Main dashboard (create league, list leagues). |
| **LeaguePage.scala** | Single league: table, fixtures, start season, add bots, invite. |
| **TeamPage.scala** | Single team: squad, tactics (game plans), training plan, transfers, scouting/shortlist. |
| **MatchSquadPage.scala** | Pre-match: lineup + formation + game plan submission. |
| **MatchDetailPage.scala** | Match result and report (events, summary stats). |
| **AcceptInvitationPage.scala** | Accept league invite (token, team name). |

Support (not pages): ApiClient, UIComponents, PitchView, FormationPresets, *Codec.scala.

---

## 3. Shared API DTOs and domain-like types (`shared/src/main/scala/fmgame/shared/api/ApiDto.scala`)

| Type | One-line description |
|------|------------------------|
| **UserDto** | id, email, displayName, createdAt. |
| **RegisterRequest / LoginRequest / LoginResponse** | Auth payloads. |
| **LeagueDto** | id, name, teamCount, currentMatchday, totalMatchdays, seasonPhase, homeAdvantage, startDate, createdByUserId, timezone. |
| **CreateLeagueRequest / CreateLeagueResponse** | League creation + initial team. |
| **TeamDto** | id, leagueId, name, ownerType, ownerUserId, ownerBotId, budget, eloRating, managerName, createdAt. |
| **PlayerDto** | id, teamId, firstName, lastName, preferredPositions, injury, freshness, morale, physical/technical/mental/traits maps. |
| **GamePlanSnapshotDto / GamePlanSnapshotDetailDto** | Snapshot id, teamId, name, (gamePlanJson), createdAt. |
| **TableRowDto** | League table row: teamId, teamName, position, points, played, won, drawn, lost, goalsFor, goalsAgainst, goalDifference. |
| **PlayerSeasonStatsRowDto / LeaguePlayerStatsDto** | Season totals: goals, assists; top scorers / top assists. |
| **PlayerSeasonAdvancedStatsRowDto / LeaguePlayerAdvancedStatsDto** | Advanced season stats: matches, minutes, goals, assists, shots, xg, keyPasses, passes, tackles, interceptions. |
| **TrainingPlanDto / UpsertTrainingPlanRequest** | Weekly training (7 sessions), updatedAt. |
| **LeaguePlayerRowDto / LeaguePlayersDto** | Scouting: playerId, name, teamId, teamName, positions, overall. |
| **ShortlistEntryDto / AddToShortlistRequest** | Shortlist entry and add-by-playerId. |
| **ScoutingReportDto / CreateScoutingReportRequest** | Report id, teamId, playerId, rating, notes. |
| **MatchDto** | id, leagueId, matchday, home/awayTeamId, scheduledAt, status, homeGoals, awayGoals, refereeId, refereeName. |
| **MatchEventDto** | minute, eventType, actorPlayerId, secondaryPlayerId, teamId, zone, outcome, metadata. |
| **MatchSummaryDto** | Full match stats (possession, shots, xG, passes, tackles, cards, VAEP, PPDA, field tilt, etc.). |
| **MatchLogDto** | events, summary, total, matchReport. |
| **LineupSlotDto / MatchSquadDto** | Lineup slot (playerId, positionSlot); squad id, matchId, teamId, lineup, source. |
| **TransferWindowDto** | id, leagueId, openAfterMatchday, closeBeforeMatchday, status. |
| **TransferOfferDto / CreateTransferOfferRequest** | Offer fields + fromTeamName, toTeamName, playerName. |
| **SubmitMatchSquadRequest / SaveGamePlanRequest / UpdatePlayerRequest** | Lineup+gamePlanJson; save plan; update player name. |
| **InvitationDto / AcceptInvitationRequest|Response / InviteRequest** | Invites and accept flow. |
| **StartSeasonRequest / AddBotsRequest** | Start season (optional startDate); add bot count. |
| **MetricsDto / ErrorBody** | Aggregates (matchCount, totalGoals, avgGoals); error code + message. |

Shared domain (non-DTO): **ThrowInConfig** (defaultTaker, longThrowTaker, targetZones, runners, useLongThrow); **Ids** (UserId, LeagueId, TeamId, PlayerId, MatchId, etc.); **Enums** (SeasonPhase, MatchStatus, InvitationStatus, TransferWindowStatus, TransferOfferStatus, MatchSquadSource).

---

## 4. Backend engine main classes (`backend/engine/`)

| Class / Object | One-sentence role |
|----------------|--------------------|
| **FullMatchEngine** | Full match simulator: event-by-event state machine with Pitch Control, DxT, formula/loadable/ONNX xG and VAEP, set pieces, triggers (press/counter), injuries. |
| **SimpleMatchEngine** | Simplified engine: Poisson-based goals + generated events (Pass, Shot, Foul, etc.); effective composure/decisions from morale; injury risk (injuryProne, ACWR). |
| **MatchState** | Per-event state: minute, totalSeconds, score, ballZone, possession, home/away positions, pitchControlByZone, dxtByZone, fatigueByPlayer, trigger/counter flags, set-piece routine. |
| **PitchModel** | Pitch 105×68 m; 12 zones (3×4); zoneFromXY, zoneCenters, distance. |
| **PositionGenerator** | Computes 22 players’ positions from formations (4-3-3, 4-4-2, 3-5-2, etc.) and ball zone. |
| **PitchControl** | Zone control (home/away share) from time-to-intercept (pace/acceleration) or distance; optional fatigue. |
| **DxT** | Dynamic Expected Threat: zone threat from base threat and opponent control (adjustedThreat, threatMap). |
| **MatchupMatrix** | Pressure in ball zone (opponents count) and dynamic pressure for intercept/pass success. |
| **AdvancedAnalytics** | xT (value iteration), pass-network clustering, OBSO-style, tortuosity (gBRI), metabolic load, Nash penalties, etc. |
| **AnalyticsModels** | ShotContext, VAEPContext; FormulaBasedxG, FormulaBasedVAEP; xGModel, VAEPModel traits. |
| **EngineConfig** | xG/VAEP model type and path (formula | loadable | onnx) from env (ENGINE_XG_MODEL, ENGINE_VAEP_MODEL, …). |
| **EngineTypes** | GamePlanInput (formation, triggers, team/player instructions, set pieces, opposition), MatchEngineInput, MatchTeamInput, PlayerMatchInput, RefereeInput, LeagueContextInput. |
| **LoadablexGModel / LoadableVAEPModel** | xG from JSON coefficients (logistic); VAEP from JSON weights per event type. |
| **OnnxXGModel / OnnxVAEPModel** | Placeholders for ONNX xG/VAEP (loadImpl = None without onnxruntime). |

---

## 5. Backend domain entities (`backend/domain/Domain.scala`) — key fields

| Entity | Key fields |
|--------|------------|
| **User** | id (UserId), email, passwordHash, displayName, createdAt. |
| **League** | id (LeagueId), name, teamCount, currentMatchday, totalMatchdays, seasonPhase, homeAdvantage, startDate, createdByUserId, createdAt, timezone. |
| **Team** | id (TeamId), leagueId, name, ownerType (Human/Bot), ownerUserId, ownerBotId, budget, defaultGamePlanId, createdAt, eloRating (default 1500), managerName. |
| **Player** | id (PlayerId), teamId, firstName, lastName, preferredPositions, physical/technical/mental/traits (Map), bodyParams, injury (InjuryStatus), freshness, morale, createdAt. |
| **InjuryStatus** | sinceMatchday, returnAtMatchday, severity. |
| **Referee** | id (RefereeId), leagueId, name, strictness. |
| **Match** | id (MatchId), leagueId, matchday, homeTeamId, awayTeamId, scheduledAt, status (Scheduled/Played/Postponed), homeGoals, awayGoals, refereeId, resultLogId. |
| **Invitation** | id (InvitationId), leagueId, invitedUserId, invitedByUserId, token, status, createdAt, expiresAt. |
| **MatchResultLog** | id (MatchResultLogId), matchId, events (List[MatchEventRecord]), summary (Option[MatchSummary]), createdAt. |
| **MatchEventRecord** | minute, eventType, actorPlayerId, secondaryPlayerId, teamId, zone, outcome, metadata. |
| **MatchSummary** | possessionPercent, home/away goals, shots (total, on target, off, blocked), bigChances, xgTotal, passes (total, completed, accuracy, final third), crosses, longBalls, tackles, interceptions, clearances, blocks, saves, fouls, cards, corners, duels, VAEP, fieldTilt, PPDA, ballTortuosity, metabolicLoad, xtByZone, injuries, homeShareByZone, vaepBreakdownByPlayer, pressingByPlayer, estimatedDistanceByPlayer, influenceByPlayer, set pieces, Poisson prognosis, xPass, etc. |
| **TransferWindow** | id (TransferWindowId), leagueId, openAfterMatchday, closeBeforeMatchday, status (Open/Closed). |
| **TransferOffer** | id (TransferOfferId), windowId, fromTeamId, toTeamId, playerId, amount, status (Pending/Accepted/Rejected/Withdrawn), createdAt, respondedAt. |
| **MatchSquad** | id (MatchSquadId), matchId, teamId, lineup (List[LineupSlot]), gamePlanJson, submittedAt, source (Manual/Default/Bot). |
| **LineupSlot** | playerId, positionSlot. |
| **LeagueContext** | id (LeagueContextId), leagueId, positionStats (per-slot (mean, stddev)), createdAt. |
| **GamePlanSnapshot** | id (GamePlanSnapshotId), teamId, name, gamePlanJson, createdAt. |

---

*End of report. Use for gap analysis vs Football Manager (tactics, training, transfers, scouting, match engine, analytics).*
