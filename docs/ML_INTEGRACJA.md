# Integracja modeli ML (XGBoost/LightGBM) pod xG i VAEP

**Cel**: Podmiana formułowych modeli (`FormulaBasedxG`, `FormulaBasedVAEP`) na modele trenowane (XGBoost, LightGBM) bez zmiany kontraktu silnika.

---

## 1. Kontrakt w kodzie

- **xG**: `trait xGModel { def xGForShot(ctx: ShotContext): Double }`  
  Kontekst: `zone`, `distanceToGoal`, `isHeader`, `minute`, `scoreDiff`, `pressureCount`.
- **VAEP**: `trait VAEPModel { def valueForEvent(ctx: VAEPContext): Double }`  
  Kontekst: `eventType`, `zone`, `outcome`, `minute`, `scoreHome`, `scoreAway`, `isPossessionTeam`.

Implementacje są używane w `FullMatchEngine` (xG przy zdarzeniu Shot). Podmiana = nowa implementacja traitu + wstrzyknięcie (np. konfiguracja lub ZLayer).

---

## 2. Ścieżka A: ONNX Runtime (XGBoost/LightGBM z Pythona)

1. **Trenowanie w Pythonie**  
   Zbiór: strzały z cechami (zone, distance_to_goal, is_header, minute, score_diff, pressure_count) i etykietą (gol 0/1).  
   Trenowanie: `xgboost` lub `lightgbm`; ewentualnie `sklearn.linear_model.LogisticRegression`.

2. **Eksport do ONNX**  
   - XGBoost: `onnxmltools` + `skl2onnx` lub `hummingbird-ml` (konwersja drzewa do ONNX).  
   - LightGBM: `onnxmltools.convert_lightgbm`.  
   - Wynik: plik `.onnx` (np. `xg_model.onnx`).

3. **Backend (JVM)**  
   - Zależność: `com.microsoft.onnxruntime:onnxruntime` (lub `onnxruntime-gpu`).  
   - Klasa `OnnxXGModel(path: String) extends xGModel`:  
     - przy starcie: `OrtEnvironment.loadModel(path)`.  
     - w `xGForShot(ctx)`: budowa wektora cech z `ShotContext` (kolejność jak przy trenowaniu), wywołanie `session.run()`, odczyt wyniku (prawdopodobieństwo klasy 1).  
   - Cechy muszą być w tej samej kolejności i normalizacji co w Pythonie (np. standardowy scaler zapisany obok modelu).

4. **Uruchomienie**  
   Plik `.onnx` w `src/main/resources` lub ścieżka z konfiguracji; w Main/assemblerze tworzysz `OnnxXGModel(path)` i przekazujesz tam, gdzie dziś używany jest `FormulaBasedxG`.

**Uwaga**: ONNX Runtime może wymagać natywnych bibliotek (JNI). W razie problemów z Cursor/IDE można uruchamiać tylko przez `sbt run`.

---

## 3. Ścieżka B: Smile (pure JVM, Java 21+)

[Smile](https://haifengl.github.io/) (Statistical Machine Intelligence and Learning Engine) działa w JVM bez Pythona.

1. **Zależność** (Java 21+):  
   `libraryDependencies += "com.github.haifengl" %% "smile-scala" % "3.0.2"`  
   (dla Java 11 sprawdź wersje 2.x Smile.)

2. **Trenowanie w JVM**  
   - Zbierz dane w formacie `Array[Array[Double]]` (cechy) + `Array[Int]` (gol 0/1).  
   - `LogisticRegression.fit(x, y)` (lub `RandomForest`, `GradientTreeBoost`).  
   - Zapisz model: `smile.write(model, Paths.get("xg.smile"))`.

3. **Implementacja**  
   - `SmileXGModel(modelPath: String) extends xGModel`:  
     - `val model = smile.read(Paths.get(modelPath)).asInstanceOf[LogisticRegression]`.  
     - W `xGForShot(ctx)` budujesz wektor z `ShotContext`, wywołujesz `model.predictProba(ctx.toFeatures)` (lub odpowiednik) i zwracasz prawdopodobieństwo gola.

4. **Dane treningowe**  
   Można generować syntetycznie z obecnego silnika (np. FullMatchEngine + FormulaBasedxG jako etykiety) albo importować z CSV po meczach.

---

## 4. Przykład: model xG z pliku konfiguracyjnego (bez zewnętrznych bibliotek)

W repozytorium: **`backend/engine/LoadablexGModel.scala`**. Wczytuje współczynniki z JSON (regresja logistyczna: `xG = sigmoid(intercept + dot(coefs, features))`).  
Kolejność cech: `[zone, distanceToGoal, isHeader, minute/90, scoreDiff, pressureCount]`.  
Format JSON: `{"intercept": -1.5, "coefs": [-0.08, -0.04, 0.0, 0.2, 0.0, -0.1]}`.

- `LoadablexGModel.fromJson(json)` zwraca `xGModel`; przy błędzie parsowania używa `FormulaBasedxG`.
- `LoadablexGModel.fromCoefficients(intercept, coefs)` — to samo z tablicy (np. po odczycie z bazy).
- Aby użyć w silniku: w miejscu gdzie dziś jest `FormulaBasedxG.xGForShot(...)` wstrzyknąć implementację (np. z konfiguracji: `engine.xg.model=formula|loadable`, `engine.xg.path=/path/to/coefs.json`).

Aby użyć własnego modelu XGBoost/LightGBM:
- albo eksport do ONNX i implementacja `OnnxXGModel`,
- albo wyciągnięcie współczynników (liniowe przybliżenie drzew) i JSON dla `LoadablexGModel`.

---

## 5. VAEP

VAEP to dwa modele: P_scores (bramka dla nas w ciągu N akcji) i P_concedes (bramka stracona).  
Każdy z nich można zastąpić osobnym modele ONNX/Smile; `valueForEvent` = kombinacja przewidywań przed/po akcji (np. delta P_scores minus delta P_concedes), zgodnie z dokumentacją VAEP.

---

## 6. Co jeszcze można zrobić (rozszerzenia)

| Obszar | Możliwe rozszerzenie |
|--------|----------------------|
| **ML** | Wdrożyć `OnnxXGModel` / `OnnxVAEPModel` (ONNX Runtime) lub `SmileXGModel`; pipeline trenowania w Pythonie/JVM i zapis modeli do pliku. |
| **Konfiguracja silnika** | Wybór implementacji `xGModel`/`VAEPModel` z konfiguracji (np. `engine.xg.model=formula|loadable|onnx`, ścieżka do pliku). |
| **Trigger kontrataku** | W FullMatchEngine: gdy piłka w `counterTriggerZone` i następuje odzyskanie – zwiększyć szansę na szybki strzał / zdarzenie w ostatniej tercji. |
| **Matchup Matrix** | Macierz „kto kogo kryje” (SILNIK plan); wpływ na szansę przechwytu / udanego podania w `generateNextEvent`. |
| **Zmęczenie w stanie** | W `MatchState` trzymać `fatigueByPlayer`; aktualizować po minutach; wpływać na Pitch Control / prędkość i szansę błędu. |
| **PSxG (Post-Shot xG)** | Osobny model dla oceny bramkarzy: xG po oddaniu strzału (placement piłki); zapisywać w metadata zdarzenia. |
| **Eksport danych** | Eksport logów meczów do CSV/StatsBomb JSON do analizy w Pythonie (trenowanie xG/VAEP na danych z gry). |
| **Wiślanie modeli w aplikację** | Endpoint lub job: wgranie pliku `.onnx` / `.smile` i przełączenie silnika na nowy model bez redeployu. |

Dokumentację kontraktu (ShotContext, VAEPContext) i miejsca użycia w silniku można znaleźć w `ALGORYTMY_MAPOWANIE_KOD.md` oraz w `engine/AnalyticsModels.scala`.

---

## 7. Zaimplementowane rozszerzenia (stan bieżący)

- **Konfiguracja**: `EngineConfig.fromEnv` (zmienne `ENGINE_XG_MODEL`, `ENGINE_XG_PATH`, `ENGINE_VAEP_MODEL`, `ENGINE_VAEP_PATH`). `EngineModelFactory.fromConfig` buduje `EngineModels` (formula / loadable / onnx). Modele są wstrzykiwane przez `Ref[EngineModels]` w `LeagueService` i przekazywane do `MatchEngineInput.xgModelOverride` / `vaepModelOverride`.
- **OnnxXGModel / OnnxVAEPModel**: Klasy w `engine/OnnxXGModel.scala` i `engine/OnnxVAEPModel.scala`; `load(path)` zwraca `Option[xGModel]` / `Option[VAEPModel]`. Bez zależności ONNX Runtime implementacja jest stubem (zwraca `None`). Aby włączyć: dodać `com.microsoft.onnxruntime:onnxruntime` i zaimplementować `loadImpl` (zob. komentarze w kodzie).
- **Trigger kontrataku**: W `FullMatchEngine` po zdarzeniu PassIntercepted/DribbleLost w strefie `counterTriggerZone` ustawiane jest `justRecoveredInCounterZone`; przy następnym zdarzeniu z ~35% szansą wymuszany jest branch „Shot” (szybki strzał po odzyskaniu).
- **Matchup Matrix**: `MatchupMatrix.pressureInZone` w `PitchModel.scala` – liczba przeciwników w strefie piłki zwiększa `passInterceptProb` w `generateNextEvent`.
- **Zmęczenie**: `MatchState.fatigueByPlayer` aktualizowane co zdarzenie (`updateFatigue`); wpływa na Pitch Control (`PitchControl.controlByZoneWithFatigue`), na szansę udanego podania/crossu i na wyniki strzałów.
- **PSxG**: W zdarzeniach Shot/Goal w `metadata` dodawane jest pole `PSxG` (post-shot xG: Goal ≈ xG×1.15, Saved ≈ xG×0.92, inaczej ≈ xG×0.7).
- **Eksport**: `LeagueService.exportMatchLogs(matchIds, format)` zwraca CSV, StatsBomb-like JSON lub pełny JSON z analityką. Format: `csv` | `json` (statsbomb) | `json-full` (zdarzenia + pełny MatchSummaryDto per mecz). Endpoint: `POST /api/v1/export/match-logs` (body: `{ "matchIds": ["..."], "format": "csv" | "json" | "json-full" }`, wymaga autoryzacji).
- **Wgrywanie modeli**: `LeagueService.uploadEngineModel(kind, contentType, body)` aktualizuje `Ref[EngineModels]`. Endpoint: `POST /api/v1/admin/models/:kind` (kind = `xg` | `vaep`), body = raw (JSON współczynników dla xG lub plik .onnx), Content-Type: `application/json` lub `application/octet-stream`, wymaga autoryzacji).
- **Pipeline trenowania (Python)**: Skrypt `scripts/train_xg_export_onnx.py` – trenowanie regresji logistycznej na CSV (lub danych syntetycznych), eksport do JSON współczynników i opcjonalnie do ONNX (`skl2onnx`). Użycie: `python scripts/train_xg_export_onnx.py --synthetic 5000 --output-dir data/models` lub `--input events.csv`.
