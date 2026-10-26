#!/usr/bin/env bash
set -euo pipefail
WS="${1:?usage: verify-ex5.sh <ex5-workspace>}"
cd "$WS"

echo "== Starting healthcheck path is wrong =="
rg -n "actuator/healthz" docker-compose.yml >/dev/null

PROJECT="conference-planner-ex5"
echo "== Bring up isolated compose project =="
docker compose -p "$PROJECT" up --build -d
cleanup() { docker compose -p "$PROJECT" down -v >/dev/null 2>&1 || true; }
trap cleanup EXIT

sleep 5
APP_ID="$(docker compose -p "$PROJECT" ps -q app)"
STATUS="starting"
for i in $(seq 1 24); do
  STATUS="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$APP_ID")"
  echo "Health status: $STATUS"
  if [[ "$STATUS" == "unhealthy" ]]; then
    break
  fi
  if [[ "$STATUS" == "healthy" ]]; then
    echo "FAIL: expected unhealthy starting snapshot" >&2
    exit 1
  fi
  sleep 5
done
if [[ "$STATUS" != "unhealthy" ]]; then
  echo "FAIL: expected unhealthy starting snapshot, got $STATUS" >&2
  exit 1
fi

echo "== Apply fix =="
python3 - <<'PY'
from pathlib import Path
p = Path("docker-compose.yml")
text = p.read_text().replace(
    "http://localhost:8080/actuator/healthz",
    "http://localhost:8080/health",
)
p.write_text(text)
PY

docker compose -p "$PROJECT" up -d --force-recreate app
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8080/health >/dev/null 2>&1; then
    H="$(docker inspect --format='{{.State.Health.Status}}' "$(docker compose -p "$PROJECT" ps -q app)")"
    echo "App health: $H"
    if [[ "$H" == "healthy" ]]; then
      echo "EX5 ACCEPTANCE OK"
      exit 0
    fi
  fi
  sleep 3
done
echo "FAIL: app did not become healthy" >&2
exit 1
