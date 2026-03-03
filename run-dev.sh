#!/usr/bin/env bash
# Uruchamia backend na porcie 8080 oraz frontend w trybie watch (fastLinkJS).
# Backend w tle; frontend w pierwszym planie (Ctrl+C zatrzyma tylko frontend – backend trzeba zabić ręcznie: kill $BACKEND_PID).

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "Building frontend once..."
sbt "frontend/fastLinkJS" >/dev/null 2>&1 || true

echo "Starting backend on port 8080..."
sbt "backend/run" &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID (kill $BACKEND_PID to stop backend)"

# Czekaj, aż backend się podniesie (opcjonalnie)
sleep 5

echo "Starting frontend watch (recompile on change)..."
sbt "~frontend/fastLinkJS"
