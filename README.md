# FM Game – symulacja piłkarska (Football Manager–style)

Aplikacja webowa: zarządzanie ligą, drużynami, składami, taktyką i symulacja meczów z silnikiem zdarzeń (formacje, role, atrybuty zawodników, morale, przewaga gospodarzy itd.).

## Wymagania

- **JDK 11+** (Scala 3.3.3)
- **sbt** 1.9+

## Uruchomienie

### Szybki start (backend + frontend)

```bash
# Jednorazowe zbudowanie frontendu, backend w tle, frontend w watch
./run-dev.sh
```

- Backend: **http://localhost:8080**
- Frontend (statycznie): serwowany z katalogu `frontend` (lub `FRONTEND_DIR`); po `sbt frontend/fastLinkJS` otwórz np. `frontend/index.html` lub użyj serwera dev (np. `npx serve frontend`).

### Tylko backend

```bash
sbt "backend/run"
```

Domyślnie nasłuchuje na porcie **8080**. Baza H2 w pamięci (lub ustaw `DATABASE_URL`).

### Tylko frontend (dev)

```bash
sbt "~frontend/fastLinkJS"
```

Pliki w `frontend/target/...` – do testów w przeglądarce potrzebny jest serwer HTTP (np. `npx serve frontend` lub wskaż `frontend` w backendzie przez `FRONTEND_DIR`).

## Zmienne środowiskowe

Zobacz plik **`.env.example`** – kopiuj do `.env` i uzupełnij w razie potrzeby.

- `PORT` – port HTTP (domyślnie 8080)
- `JWT_SECRET` – sekret do JWT (w produkcji obowiązkowy)
- `DATABASE_URL` – URL bazy (domyślnie H2 in-memory)
- `FRONTEND_DIR` – katalog z zbudowanym frontendem (domyślnie `frontend`)
- `ENV=production` – włącza wymóg ustawienia `JWT_SECRET`
- `ADMIN_SECRET` – opcjonalnie, do endpointów admin (np. upload modeli silnika)
- `ENGINE_XG_MODEL` / `ENGINE_VAEP_MODEL` – opcjonalnie, typ modelu xG/VAEP (`formula` | inne)
- `ENGINE_XG_PATH` / `ENGINE_VAEP_PATH` – opcjonalnie, ścieżki do modeli (np. ONNX)

## Testy

```bash
sbt "backend/test"
```

## Struktura projektu

- **backend** – ZIO HTTP, Doobie, silnik meczu (FullMatchEngine), repozytoria, logika ligi/drużyn/transferów
- **frontend** – Laminar (Scala.js), SPA
- **shared** – DTO i domena współdzielona (Circe)

Dokumentacja: `docs/CODE_REVIEW.md`, `docs/REKOMENDACJE_ULEPSZEN.md`.
