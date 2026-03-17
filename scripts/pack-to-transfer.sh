#!/usr/bin/env bash
# Pakuje projekt do przeniesienia (bez target/, .idea/, .bsp).
# Archiwum: ~/analize-do-przeniesienia.tar.gz (ok. 4–5 MB).
# Na drugim komputerze: tar -xzf analize-do-przeniesienia.tar.gz -C /ścieżka/ && cd /ścieżka/analize && sbt compile

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$HOME/analize-do-przeniesienia.tar.gz}"
cd "$ROOT"
echo "Pakowanie (bez target, .idea, .bsp) -> $OUT"
tar --exclude='target' --exclude='.idea' --exclude='.bsp' -czf "$OUT" .
echo "Gotowe: $(ls -lh "$OUT" | awk '{print $5}')"
