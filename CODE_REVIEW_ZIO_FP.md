# Krytyczny przegląd jakości kodu – ZIO i programowanie funkcyjne w Scali

**Data:** 2026-02-26  
**Zakres:** Cały projekt (backend, shared, frontend), z naciskiem na zgodność z ZIO, unikanie mieszania Cats/ZIO oraz dobre praktyki FP.

**Uwaga:** Dokument „ScalaPedia” (PDF) dotyczący ZIO nie został znaleziony w repozytorium. Przegląd oparto o oficjalne [ZIO Coding Guidelines](https://zio.dev/coding-guidelines), [Basic Operations](https://zio.dev/overview/basic-operations) oraz dobre praktyki FP w Scali i zalecenia unikania mieszania Cats Effect z ZIO.

---

## 1. Mieszanie ZIO i Cats Effect – uwagi krytyczne

### 1.1 Gdzie występuje mieszanie

- **Main.scala** – używa `cats.effect.IO`, `cats.effect.unsafe.implicits.global`, `EmberServerBuilder.default[IO]`, `Router[IO]`, `serverRes.use(_ => IO.never).unsafeToFuture()`.
- **Routes.scala** – cała warstwa HTTP to `HttpRoutes[IO]`, `Request[IO]`, `Response[IO]`, `IO.pure(...)`, `runZIO` konwertuje ZIO → `IO(Either[String, A])` przez `runtime.unsafe.run(...).getOrThrowFiberFailure()`.
- **Database.scala** – `Resource[F]`, `Async[F]`, `cats.syntax.functor.*`, `cats.syntax.applicativeError.*`.
- **LeagueService.scala** – `cats.MonadError[ConnectionIO, Throwable]`, `cats.syntax.traverse.*`, `cats.syntax.applicative.*`, `.pure[ConnectionIO]`.
- **Testy (ApiIntegrationSpec, UserServiceSpec, LeagueServiceSpec)** – `cats.effect.IO`, `cats.effect.unsafe.implicits.global`, `io.unsafeRunSync()`.

### 1.2 Dlaczego to problem

- Dwa systemy efektów w jednym stosie: logika biznesowa w ZIO, HTTP i część infrastruktury w Cats IO.
- Ryzyko błędów przy przekazywaniu błędów i anulowaniu (ZIO fibers vs Cats fibers).
- Trudniejszy refaktoring i onboarding – developer musi znać i ZIO, i Cats.
- Zgodnie z dobrymi praktykami: **wybrać jeden system efektów w projekcie i go konsekwentnie używać**.

### 1.3 Rekomendacja

- **Opcja A (preferowana przy „wszystko w ZIO”):** Przejście warstwy HTTP na ZIO (np. **zio-http** lub **http4s-zio** / adapter ZIO dla http4s). Wtedy cały backend: `ZIO` + Doobie `Transactor[Task]` + serwer HTTP oparty na ZIO. Usunąć `cats.effect.IO` z Main/Routes i testów.
- **Opcja B:** Zostawić http4s na Cats IO, ale ograniczyć granicę do minimum: jedna funkcja „runZIO → IO” w jednym miejscu, reszta w ZIO; nadal niepożądane używanie Cats w LeagueService (MonadError, traverse) – tam użyć ZIO/Doobie po stronie ConnectionIO bez importów Cats (patrz sekcja LeagueService).

---

## 2. Main.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 16–17 | `import cats.effect.IO` i `cats.effect.unsafe.implicits.global` – bezpośrednia zależność od Cats Effect. | Wysoki |
| Linia 50 | `app = Router[IO](...)` – cała aplikacja HTTP jest w IO. | Wysoki |
| Linia 55–56 | `EmberServerBuilder.default[IO]`, `.build` – serwer Cats. | Wysoki |
| Linia 63–65 | `ZIO.fromFuture(_ => serverRes.use(_ => IO.never).unsafeToFuture())` – łączenie ZIO z IO przez `unsafeToFuture` i `IO.never`; kruche i nieidiomatyczne dla ZIO. | Wysoki |
| Linia 36 | `Unsafe.unsafe(implicit u => runtime.unsafe.run(Database.initSchema(xa)).getOrThrowFiberFailure())` – wywołanie „unsafe” w środku ZIO; w porządku jako granica, ale `Database.initSchema` zwraca `F[Unit]` (Cats), co wymaga konwersji. | Średni |
| Linia 41 | `ZIO.succeed(Unsafe.unsafe(... Ref.make(...)))` – tworzenie Ref poza ZIO przez unsafe; lepiej `Ref.make(initialModels)` bezpośrednio w for-comprehension. | Średni |
| Linia 66 | `.provide(ZLayer.empty)` – ZIO zaleca używanie najmniej rozbudowanego aliasu; jeśli nie ma zależności środowiska, można rozważyć uproszczenie. | Niski |

**Dodatkowe:** Brak jawnego obsłużenia sygnału zakończenia (np. SIGINT) do graceful shutdown – serwer jest „zawieszony” na `IO.never`. W czystym ZIO można by użyć `ZIO.addInterruptHook` lub odpowiedniego API serwera ZIO.

---

## 3. Routes.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 9, 14 | `cats.effect.IO`, `cats.syntax.semigroupk._` – cała warstwa w Cats IO. | Wysoki |
| Linia 25–26 | `runZIO`: `IO(Unsafe.unsafe(implicit u => runtime.unsafe.run(zio.tapError(...).either).getOrThrowFiberFailure()))` – każdy request uruchamia ZIO synchronicznie i rzuca w przypadku błędu fiber; blokuje wątki IO. Lepsze byłoby uruchomienie ZIO w puli wątków ZIO i przekazanie wyniku do IO (np. przez Promise), albo przejście na ZIO end-to-end. | Wysoki |
| Linia 29 | `Request[IO]` – typ z Cats. | Wysoki |
| Linia 38–39 | `unauthorized: IO[Response[IO]]`, `IO.pure(Response[IO](...))` – używanie `IO.pure` do wartości już obliczonych jest poprawne semantycznie, ale utrwala zależność od IO. | Średni |
| Linia 40 | `HttpRoutes.of[IO]` – wszystkie handlery w IO. | Wysoki |
| Linia 114 | `<+>` (semigroupk) – łączenie rout; składnia Cats. | Średni |
| Linia 207 | `IO.pure(Response[IO](Status.Created))` – można uprościć do `Created` jeśli DSL to zwraca. | Niski |
| Linia 412 | `req.body.compile.toVector` – API fs2/Cats; przy migracji na ZIO streamy byłyby ZStream. | Średni |

**Spójność:** Wszystkie endpointy używają tego samego wzorca `runZIO(service.metoda(...)).flatMap(_.fold(..., ...))` – to jest konsekwentne, ale całość jest w świecie IO.

---

## 4. Database.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 5–7 | `cats.effect.*`, `cats.syntax.functor.*`, `cats.syntax.applicativeError.*` – moduł w całości oparty o typeclass Cats. | Wysoki |
| Linia 12 | `Resource[F, Transactor[F]]` – Resource jest z Cats; w ZIO odpowiednikiem jest `ZLayer`/`ZManaged` (np. `ZIO.acquireRelease`). | Wysoki |
| Linia 20 | `initSchema[F[_]: Async](xa: Transactor[F]): F[Unit]` – Async z Cats. Doobie wspiera ZIO przez `Transactor[zio.Task]`; można mieć `initSchema(xa: Transactor[Task]): Task[Unit]` i nie używać Async w tym pliku. | Wysoki |
| Linia 213 | `addEloColumn = ... .attempt.as(())` – `attempt` z applicativeError; przy przejściu na ZIO można użyć `ZIO.attempt` lub zostawić Doobie `ConnectionIO` i tylko transact z Task. | Średni |
| Linia 231 | `steps.transact(xa).map(_ => ())` – Doobie; po stronie wywołującej (Main) przekazywane jest już `Transactor[Task]`, więc tu pozostaje tylko spójność z jednym F. | Niski |

**Rekomendacja:** Przepisać `Database` na używanie wyłącznie `Transactor[zio.Task]` i `Task` (bez `Resource` z Cats, jeśli inicjalizacja schematu jest wykonywana raz w Main przez ZIO).

---

## 5. LeagueService.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 9, 12–14 | `zio.interop.catz.*`, `cats.MonadError`, `cats.syntax.traverse.*`, `cats.syntax.applicative.*` – bezpośrednie użycie Cats w serwisie biznesowym. | Wysoki |
| Linia 76 | `xa: doobie.Transactor[zio.Task]` – poprawne: Doobie z Task jako F. | — |
| Linia 378 | `val me = MonadError[ConnectionIO, Throwable]` – używane do `raiseError` i `pure` wewnątrz ConnectionIO. W czystym ZIO/Doobie można: `Option` obsłużyć przez `ZIO.fromOption` po `transact`, albo wewnątrz ConnectionIO użyć tylko `flatMap` i `map` oraz np. `ApplicativeError[ConnectionIO, Throwable].raiseError` bez importu Cats (Doobie dostarcza instancje); albo przepisać fragment na for bez MonadError, używając `fromOption` w ZIO po transact. | Wysoki |
| Linia 380, 396–398, 447–458, 469–478, 487–492 | `results.traverse`, `homePlayers.traverse`, `.pure[ConnectionIO]` – składnia Cats. W Scali 3 / Doobie można użyć `traverse` z biblioteki standardowej (np. `List#traverse` jeśli jest w scope) lub napisać pętlę for bez `traverse` (np. `foldLeft` z `flatMap`). Doobie dla `ConnectionIO` ma instancje Cats; jeśli chcemy uniknąć importów Cats, trzeba zastąpić `traverse` przez np. `ZIO.foreach` po stronie ZIO (wtedy każda operacja w osobnej transakcji – gorzej) albo dodać własną funkcję pomocniczą `traverseConnectionIO` bez importu `cats.syntax.traverse`. | Wysoki |
| Linia 424 | `case _ => ().pure[ConnectionIO]` – można zastąpić `ConnectionIO.pure(())` jeśli Doobie to udostępnia, albo `Applicative[ConnectionIO].pure(())` z jednym importem; najlepiej sprowadzić do jednego systemu. | Średni |
| Linia 430–431 | `mOpt.fold(me.raiseError[Match](...))(a => me.pure(a))` – typowe użycie MonadError; przy usunięciu Cats – użyć np. `ZIO.fromOption` po transact lub wewnątrz ConnectionIO tylko flatMap z Option. | Wysoki |

**Uwaga ogólna:** Serwis zwraca wszędzie `ZIO[Any, String, A]` – to dobra praktyka (typ błędu biznesowego jako String). Logowanie przez `tapError` i `ZIO.logInfo`/`ZIO.logWarning` jest spójne z ZIO.

---

## 6. UserService.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 9 | `zio.interop.catz.*` – używane prawdopodobnie dla Doobie (implicits dla transact); przy pełnym ZIO można zostawić tylko doobie + zio-interop-catz dla Transactor[Task]. | Średni |
| Linia 22 | `xa: doobie.Transactor[zio.Task]` – poprawne. | — |
| Linia 33, 43, 47 | `.tapError(err => ZIO.logWarning(...))` – dobra praktyka ZIO. | — |
| Linia 46 | `getById`: `ZIO.logWarning(...) *> ZIO.fail(...)` – poprawne łączenie efektów. | — |

Brak bezpośredniego użycia Cats (MonadError, traverse); tylko transact z Doobie. Jakość kodu dobra.

---

## 7. AuthService.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Całość | Obiekt statyczny, metody czyste (hash, verify, createToken, verifyToken) – brak ZIO. To akceptowalne dla małego modułu narzędziowego. | Niski |
| Linia 13–14 | `password.bcryptBounded(12)` – operacja blokująca (CPU); w wysokim ruchu warto rozważyć `ZIO.attempt(...).blocking` lub wykonanie w puli blocking. | Niski |
| Linia 31 | `io.circe.parser.parse(claim.content)` – Circe jest neutralny względem efektów; OK. | — |
| Linia 21 | Ręczne budowanie JSON w `content` – wrażliwe na injection (np. cudzysłowy w email); bezpieczniej użyć `io.circe.syntax` i kodować obiekt. | Średni |

---

## 8. FullMatchEngine.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 12–17 | `simulate` zwraca `ZIO[Any, MatchEngineError, MatchEngineResult]`, używa `ZIO.attempt`, `mapError`, `flatMap`, `ZIO.fail`/`ZIO.succeed` – idiomatyczne ZIO. | — |
| Linia 14 | `ZIO.attempt(buildResult(input))` – `buildResult` jest czysto imperatywna (var, while, mutable buffer). ZIO.attempt opakowuje ją w efekt; jeśli `buildResult` może długo liczyć, rozważyć `ZIO.attempt(...).blocking` lub `ZIO.blocking(ZIO.attempt(...))`. | Średni |
| Linia 19–58 | `buildResult` – używa `return Left(...)`, `var state`, `while`, `mutable.ArrayBuffer` – zgodnie z wytycznymi ZIO unika się nazwy „effect” w konstruktorach; po stronie logiki silnika można zostawić dla czytelności, ale całość jest poza ZIO (czysta funkcja + ZIO.attempt). | Niski |
| Linia 42 | `eventsAcc` – mutable; w czystym FP można by budować listę rekurencyjnie lub przez fold, kosztem złożoności. Dla wydajności mutable w tym miejscu jest często akceptowalne. | Niski |
| Linia 254–341 | `computeAnalyticsFromEvents` – dużo `var` i mutable Map; to ten sam kompromis co wyżej. | Niski |

Ogólnie silnik jest dobrze odseparowany (ZIO na granicy, logika wewnętrzna deterministyczna).

---

## 9. EngineConfig.scala i EngineModelFactory

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 17–22 | `EngineConfig.fromEnv` – odczyt `System.getenv` poza efektem. Zgodnie z ZIO lepiej: `ZIO.succeed(EngineConfig(...))` lub `ZIO.config(…)` przy użyciu ZIO Config, żeby testy mogły wstrzykiwać konfigurację. | Średni |
| Linia 42–43, 61–62 | `scala.io.Source.fromFile(path).mkString` – I/O w trybie synchronicznym, poza ZIO. Powinno być np. `ZIO.attempt(Source.fromFile(path).mkString)` lub `ZIO.readFile(path)` (jeśli dostępne), żeby błędy i blokowanie były w efekcie. | Wysoki |
| Linia 45–46, 52 | `OnnxXGModel.load(path)`, `OnnxVAEPModel.load(path)` – jeśli to operacje I/O, powinny zwracać `ZIO` lub być opakowane w `ZIO.attempt`. | Średni |

---

## 10. Repozytoria (Doobie)

- **UserRepository, LeagueRepository, TeamRepository, itd.:** Używają wyłącznie `ConnectionIO` i Doobie; brak importów Cats w repozytoriach. `Transactor[zio.Task]` jest przekazywany z zewnątrz – to dobra granica.
- **Database.scala** – jak w sekcji 4; to jedyny moduł „infrastruktury DB” zależny od Cats.

---

## 11. Domain.scala i shared/domain/Ids.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| IdGen (Domain.scala) | `UserId(java.util.UUID.randomUUID().toString)` itd. – generowanie ID poza efektem. W ZIO zaleca się np. `ZIO.randomUUID.map(u => UserId(u.toString))` lub przynajmniej `ZIO.succeed(IdGen.userId)` przy użyciu, żeby testy mogły nadpisać generator. Dla prostoty obecne podejście jest akceptowalne. | Niski |
| Ids.scala | `opaque type` + obiekty towarzyszące – dobra praktyka; brak efektów. | — |
| Domain.scala | Case classy i enumy – czyste modele danych; brak uwag. | — |

---

## 12. MatchSummaryAggregator.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Całość | Funkcja `fromEvents` jest czysta (tylko `var` wewnątrz dla wydajności). Brak ZIO – OK dla czystej agregacji. | — |
| Linia 22–48 | Wiele `var` – można rozważyć jedną strukturę (np. case class) i `foldLeft` dla czytelności i łatwiejszych testów. | Niski |

---

## 13. LoadablexGModel.scala

| Lokalizacja | Uwaga | Priorytet |
|-------------|--------|-----------|
| Linia 49–56 | `fromJson(json: String)` – parsowanie JSON; brak efektu. Circe `parse` może rzucić; można opakować w `ZIO.attempt` przy wywołaniu z warstwy wyżej. | Niski |

---

## 14. Testy

| Plik | Uwaga | Priorytet |
|------|--------|-----------|
| ApiIntegrationSpec | Użycie `cats.effect.IO`, `implicits.global`, `io.unsafeRunSync()` – testy uruchamiają świat Cats i ZIO równolegle; przy migracji na ZIO end-to-end testy powinny używać tylko ZIO (np. `unsafeRun` w ramach ZIOSpec). | Wysoki |
| UserServiceSpec, LeagueServiceSpec | `ZIO.attempt(zio.Unsafe.unsafe(implicit u => runtime.unsafe.run(Database.initSchema(xa)).getOrThrowFiberFailure()))` – powielony wzorzec; warto wyciągnąć do helpera `initDb(xa)` w testach. | Średni |
| AuthServiceSpec, EngineConfigSpec, FixtureGeneratorSpec, MatchSummaryAggregatorSpec | Czyste ZIO testy, bez Cats – dobra praktyka. | — |

---

## 15. Pozostałe pliki

- **Routes.scala (linia 27):** W `runZIO` wywołanie `zio.tapError(...)` – poprawne logowanie przed konwersją do IO.
- **build.sbt:** Zależność `zio-interop-catz` – potrzebna dopóki Doobie i http4s używają Cats; przy pełnej migracji na zio-http można dążyć do usunięcia.
- **Circe (io.circe):** Używany wszędzie do JSON; Circe jest niezależny od efektów – OK.
- **Frontend (Laminar, Scala.js):** Brak ZIO/Cats; poza zakresem przeglądu backendu.

---

## 16. Zgodność z ZIO Coding Guidelines (skrót)

- **Least powerful type:** Część metod zwraca `ZIO[Any, String, A]` – można rozważyć `IO[String, A]` (alias dla ZIO z Any) dla spójności.
- **Final/private:** Brak wymogu „wszystkie metody final” w kodzie użytkownika; głównie dotyczy kodu biblioteki ZIO.
- **Naming:** Brak uwag krytycznych; ewentualnie `runZIO` → `runToIO` lub `unsafeRunToIO` dla jasności, że to granica unsafe.
- **Lazy parameters:** Nie dotyczy wprost; API serwisów jest poprawne.

---

## 17. Podsumowanie i rekomendacje

### Wnioski krytyczne

1. **Mieszanie ZIO i Cats** – warstwa HTTP (Main, Routes) i inicjalizacja DB (Database) są w Cats; logika w LeagueService/UserService w ZIO z użyciem Cats (MonadError, traverse, .pure) wewnątrz ConnectionIO. To narusza zasadę „jeden system efektów”.
2. **runZIO** – synchroniczne `runtime.unsafe.run` w każdym requeście blokuje wątki IO; lepiej ZIO end-to-end lub async bridge.
3. **EngineConfig / EngineModelFactory** – I/O (System.getenv, Source.fromFile) poza efektem; powinno być w ZIO.
4. **Testy integracyjne** – używają `unsafeRunSync()` i Cats IO; przy migracji na ZIO należy je przepisać na ZIO.

### Rekomendacje priorytetowe

1. **Wysoki:** Podjąć decyzję architektoniczną: albo (A) migracja na ZIO w całym backendzie (http4s-zio lub zio-http, usunięcie Cats z Main/Routes/Database/LeagueService), albo (B) wyraźna granica „ZIO wewnątrz, IO na zewnątrz” z minimalizacją Cats w LeagueService (zastąpienie MonadError/traverse przez Doobie/ZIO bez importów Cats gdzie to możliwe).
2. **Wysoki:** Zamienić w LeagueService `MonadError` i `traverse` na wersje bez importu Cats (np. tylko Doobie implicits + własna pętla/helper) albo przenieść fragmenty do ZIO.foreach po transact, jeśli dopuszczalne semantycznie.
3. **Średni:** Przenieść `EngineConfig.fromEnv` i ładowanie plików w EngineModelFactory do ZIO (ZIO.attempt / ZIO.config).
4. **Średni:** Refaktoryzacja testów integracyjnych do czystego ZIO (bez `IO.unsafeRunSync`).
5. **Niski:** Rozważyć `ZIO.blocking` dla `buildResult` w FullMatchEngine i dla bcrypt w AuthService; zabezpieczenie przed blokowaniem wątków ZIO.

### Drobne uwagi

- Main: Ref.make lepiej tworzyć w for-comprehension (ZIO), nie przez Unsafe.unsafe.
- Routes: wydzielić helper `runZIO` i ewentualnie zmienić nazwę na `unsafeRunZIOToIO` lub podobną.
- Database: po decyzji o ZIO – przepisać na Task/ZManaged lub ZLayer.
- IdGen: opcjonalnie generatory w ZIO dla testowalności.
- MatchSummaryAggregator / FullMatchEngine: opcjonalnie redukcja `var` na fold/immutable dla czystszego FP.

---

## 18. Stan po zmianach (zrealizowane uwagi)

**Data aktualizacji:** 2026-02-23

### Zrealizowane

- **Database.scala:** Usunięto `Resource` i metodę `transactor`; `initSchema(xa: Transactor[Task]): Task[Unit]`; importy ograniczone do `zio.Task`, `zio.interop.catz`, `cats.syntax.applicativeError`, `cats.syntax.functor`.
- **LeagueService.scala:** Usunięto `cats.syntax.traverse` i `applicative`; dodano helpery `connUnit` i `traverseConn`; `traverse`/`.pure[ConnectionIO]` zastąpione tymi helperami; używane `implicitly[MonadError[ConnectionIO, Throwable]]` i `implicitly[Applicative[ConnectionIO]]`.
- **Main.scala:** `Ref.make(initialModels)` w for-comprehension; `Database.initSchema(xa)` jako ZIO; `EngineConfig.fromEnvZIO`, `EngineModelFactory.fromConfigZIO`, `initialModels <- ... Ref.make`; usunięty import `Unsafe` z inicjalizacji Ref.
- **EngineConfig / EngineModelFactory:** `fromEnvZIO`; `fromConfigZIO`, `xGModelFromConfigZIO`, `vaepModelFromConfigZIO` z `ZIO.attempt` i try/finally przy `Source.fromFile`.
- **AuthService.scala:** Treść JWT przez `Json.obj`/`Json.fromString`; `hashPasswordZIO` z `ZIO.blocking`.
- **UserService.scala:** W `register` używane `AuthService.hashPasswordZIO(password)`.
- **FullMatchEngine / SimpleMatchEngine:** Zamiast `return Left(...)` używane if/else; symulacja w `ZIO.blocking(ZIO.attempt(buildResult(...)))`.
- **Routes.scala:** `runZIO` przemianowane na `unsafeRunZIOToIO`, parametr efektu nazwany `eff`.
- **Testy:** `TestDbHelper.initSchemaForTest(xa)` wywołuje `Database.initSchema(xa)`; UserServiceSpec, LeagueServiceSpec, ApiIntegrationSpec inicjalizują DB przez ten helper; w LeagueServiceSpec i ApiIntegrationSpec `engineModelsRef` budowany przez `EngineConfig.fromEnvZIO` + `EngineModelFactory.fromConfigZIO` + `Ref.make`.
- **ApiIntegrationSpec:** Wprowadzono helper `runIO(io: IO[A]): ZIO[Any, Throwable, A]` = `ZIO.blocking(ZIO.attempt(io.unsafeRunSync()))`; wszystkie wywołania HTTP (client.expect, client.run) uruchamiane przez `runIO(...)` zamiast rozproszonego `ZIO.attempt(...unsafeRunSync())` – jedna granica i brak blokowania puli ZIO (wykonanie w `ZIO.blocking`).

### Pozostało (bez zmiany zachowania)

- **Warstwa HTTP:** Nadal http4s + `cats.effect.IO` (Main, Routes); pełna migracja na ZIO (np. zio-http lub http4s-zio) wymagałaby większej refaktoryzacji.
- **Testy integracyjne:** Nadal używają `cats.effect.IO` (klient http4s) i jednej granicy `unsafeRunSync` wewnątrz `runIO` (z `cats.effect.unsafe.implicits.global`). Usunięcie globala wymagałoby własnego `IORuntime` zbudowanego z ZIO Runtime (scheduler, compute/blocking EC) – nie zrealizowane.
- **Dodatek A (checklist):** Tabela w sekcji „Dodatek A” opisuje stan przed zmianami; po powyższych poprawkach Database, LeagueService, Main, EngineConfig, AuthService, FullMatchEngine, Routes i testy są zaktualizowane zgodnie z listą w tej sekcji.

---

**Koniec dokumentu.**

---

## Dodatek A: Przegląd plik po pliku (checklist)

| Plik | ZIO | Cats/IO | Uwagi |
|------|-----|---------|--------|
| Main.scala | tak (run) | IO, EmberServer, global | Mieszanie; Ref przez Unsafe |
| Routes.scala | runZIO tylko | HttpRoutes[IO], IO.pure | Cała warstwa w IO |
| Database.scala | nie | Resource, Async, applicativeError | W całości Cats |
| LeagueService.scala | tak | MonadError, traverse, .pure | Mieszanie w ConnectionIO |
| UserService.scala | tak | zio.interop.catz | OK |
| AuthService.scala | nie | — | Czyste; bcrypt blokujące |
| FullMatchEngine.scala | tak | — | return Left, var |
| SimpleMatchEngine.scala | tak | — | return Left |
| EngineConfig / EngineModelFactory | nie | — | fromEnv, fromFile poza efektem |
| Repozytoria | nie | ConnectionIO | Brak importów Cats |
| MatchSummaryAggregator, BotSquadBuilder, FixtureGenerator | nie | — | Czyste |
| ApiIntegrationSpec | ZIO + IO | unsafeRunSync, IO | Mieszanie |
| UserServiceSpec, LeagueServiceSpec | ZIO | initSchema przez Unsafe | Powielony wzorzec |

---

## Dodatek B: Unsafe i return

- **return Left(...)** w FullMatchEngine i SimpleMatchEngine: idiomatyczniej bez `return` (if/else lub Either.cond).
- **getOrThrowFiberFailure() / getOrThrow()**: tylko na granicy ZIO; w testach lepiej ZIO Test `run`.
- **Inicjalizacja DB w testach**: wyciągnąć do helpera `initSchemaForTest(xa)`.
