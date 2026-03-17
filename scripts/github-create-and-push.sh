#!/usr/bin/env bash
# Tworzy repozytorium na GitHubie i pushuje gałąź main.
# Wymaga: GITHUB_TOKEN (GitHub → Settings → Developer settings → Personal access tokens).
# Użycie: GITHUB_TOKEN=ghp_xxx ./scripts/github-create-and-push.sh [nazwa-repo]

set -e
REPO_NAME="${1:-analize}"
cd "$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "Ustaw GITHUB_TOKEN (np. export GITHUB_TOKEN=ghp_xxxx) i uruchom ponownie."
  echo "Token: GitHub → Settings → Developer settings → Personal access tokens (scope: repo)."
  exit 1
fi

echo "Tworzenie repozytorium: $REPO_NAME ..."
RESP=$(curl -s -S -X POST \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO_NAME\",\"private\":false,\"description\":\"FM Game – symulacja piłkarska (Football Manager–style)\"}")

if echo "$RESP" | jq -e '.message' >/dev/null 2>&1; then
  echo "Błąd API: $(echo "$RESP" | jq -r '.message')"
  exit 1
fi

OWNER=$(echo "$RESP" | jq -r '.owner.login')
CLONE_URL="https://github.com/$OWNER/$REPO_NAME.git"
PUSH_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${OWNER}/${REPO_NAME}.git"

if git remote get-url origin 2>/dev/null; then
  echo "Remote origin już ustawiony. Aktualizuję..."
  git remote set-url origin "$CLONE_URL"
else
  git remote add origin "$CLONE_URL"
fi

echo "Push do $CLONE_URL ..."
git push "$PUSH_URL" main -u origin main

echo "Gotowe. Repo: https://github.com/$OWNER/$REPO_NAME"
