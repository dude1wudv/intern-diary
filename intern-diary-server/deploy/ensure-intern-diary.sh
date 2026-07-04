#!/usr/bin/env bash
set -euo pipefail
install -d -m 700 /opt/intern-diary /opt/intern-diary-deploy /opt/intern-diary-deploy/.codex /opt/intern-diary-deploy/templates /opt/intern-diary-deploy/workdays
if [ ! -f /opt/intern-diary-deploy/.env ]; then
  umask 077
  cat > /opt/intern-diary-deploy/.env <<'ENV'
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
cp /tmp/intern-diary/docker-compose.yml /opt/intern-diary/docker-compose.yml
docker compose -f /opt/intern-diary/docker-compose.yml up -d
curl -fsS http://127.0.0.1:8088/health
