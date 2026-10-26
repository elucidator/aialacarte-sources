#!/usr/bin/env bash
#
# Browser checks with Playwright (presenter-only).
#
#   presenter/ui-check/run-ui-check.sh baseline   # correct baseline UI flows
#   presenter/ui-check/run-ui-check.sh e1         # exercise 1 reference solution checkbox
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:?usage: run-ui-check.sh <baseline|e1>}"
WORK="$(mktemp -d)"
PROJECT="ai-a-la-carte-ui-check"

cleanup() {
    if [[ "$MODE" == "baseline" ]]; then
        (cd "$ROOT" && docker compose -p ai-a-la-carte down -v >/dev/null 2>&1) || true
    else
        docker compose -p "$PROJECT" down -v >/dev/null 2>&1 || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

wait_healthy() {
    local project="$1" deadline=$((SECONDS + 180)) container status
    while (( SECONDS < deadline )); do
        container="$(docker compose -p "$project" ps -q app 2>/dev/null || true)"
        if [[ -n "$container" ]]; then
            status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo none)"
            [[ "$status" == "healthy" ]] && return 0
        fi
        sleep 2
    done
    return 1
}

cd "$ROOT/presenter/ui-check"

if [[ "$MODE" == "baseline" ]]; then
    echo "== starting baseline Compose stack =="
    (cd "$ROOT" && docker compose -p ai-a-la-carte up -d --build) > "$WORK/up.log" 2>&1 \
        || { tail -20 "$WORK/up.log"; exit 1; }
    wait_healthy ai-a-la-carte || { docker compose -p ai-a-la-carte logs app | tail -30; exit 1; }
    echo "== running baseline browser checks =="
    BASE_URL=http://localhost:8080 node ui-baseline.mjs
    docker compose -p ai-a-la-carte down -v > /dev/null 2>&1
else
    echo "== exporting and patching exercise 1 =="
    "$ROOT/scripts/export-exercise.sh" 1 "$WORK/ws" > /dev/null
    (cd "$WORK/ws" && patch -p1 --silent < "$ROOT/presenter/reference-fixes/e1.patch")
    echo "== starting patched exercise 1 stack =="
    (cd "$WORK/ws" && docker compose -p "$PROJECT" up -d --build) > "$WORK/up.log" 2>&1 \
        || { tail -20 "$WORK/up.log"; exit 1; }
    wait_healthy "$PROJECT" || { docker compose -p "$PROJECT" logs app | tail -30; exit 1; }
    echo "== running checkbox browser check =="
    BASE_URL=http://localhost:8080 node ui-e1.mjs
    docker compose -p "$PROJECT" down -v > /dev/null 2>&1
fi
