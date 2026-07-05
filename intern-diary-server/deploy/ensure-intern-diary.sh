#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
install -d -m 700 "$ROOT/data" "$ROOT/data/templates" "$ROOT/data/workdays"
if [ ! -f "$ROOT/.env" ]; then
  umask 077
  cat > "$ROOT/.env" <<'ENV'
API_TOKEN=change-me
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-5.5
VISION_ENABLED=0
CODEX_ENABLED=0
CODEX_COMMAND=codex
CODEX_TIMEOUT_SECONDS=300
ENV
fi
docker compose -f "$ROOT/docker-compose.yml" up -d
curl -fsS http://127.0.0.1:8088/health
