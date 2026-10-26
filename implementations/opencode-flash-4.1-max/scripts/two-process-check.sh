#!/usr/bin/env bash
#
# Verifies the concurrency invariants with TWO application processes sharing one
# PostgreSQL database (not one JVM with threads):
#   1. Two attendees race for the last place in session 2 -> exactly one booking.
#   2. Two processes replay the same attendee-scoped request key -> one booking.
#
# Uses an isolated Compose project and disposable data. All waits are bounded.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_PROJECT="ai-a-la-carte-twoprocess"
DB_PORT="${DB_PORT:-55432}"
PORT_A=18081
PORT_B=18082
WORK="$(mktemp -d)"
PID_A=""
PID_B=""

cleanup() {
    [[ -n "$PID_A" ]] && kill "$PID_A" 2>/dev/null || true
    [[ -n "$PID_B" ]] && kill "$PID_B" 2>/dev/null || true
    wait "$PID_A" 2>/dev/null || true
    wait "$PID_B" 2>/dev/null || true
    DB_PORT="$DB_PORT" docker compose -p "$COMPOSE_PROJECT" down -v --remove-orphans >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

wait_health() {
    local port="$1"
    for _ in $(seq 1 80); do
        if curl -fsS "http://localhost:$port/health" >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.5
    done
    return 1
}

post_booking_bg() {
    local port="$1" attendee="$2" key="$3" session="$4" out="$5"
    curl -sS -o "${out}.json" -w "%{http_code}" -X POST "http://localhost:$port/api/bookings" \
        -H "X-Demo-Attendee: $attendee" \
        -H "Idempotency-Key: $key" \
        -H "Content-Type: application/json" \
        -d "{\"sessionId\":$session}" > "${out}.status" &
}

echo "== building bootJar =="
(cd "$ROOT" && ./gradlew --console=plain -q bootJar)

JAR="$(ls "$ROOT"/build/libs/conference-planner-*.jar | grep -v plain | head -1)"

echo "== starting disposable PostgreSQL (project $COMPOSE_PROJECT, host port $DB_PORT) =="
DB_PORT="$DB_PORT" docker compose -p "$COMPOSE_PROJECT" up -d db

DB_CONTAINER=""
for _ in $(seq 1 40); do
    DB_CONTAINER="$(docker compose -p "$COMPOSE_PROJECT" ps -q db 2>/dev/null || true)"
    if [[ -n "$DB_CONTAINER" ]] && docker exec "$DB_CONTAINER" pg_isready -U conference -d conference >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
done
[[ -n "$DB_CONTAINER" ]] || fail "database container did not start"
docker exec "$DB_CONTAINER" pg_isready -U conference -d conference >/dev/null || fail "database not ready within bound"

start_app() {
    local port="$1"
    java -jar "$JAR" \
        --server.port="$port" \
        --spring.profiles.active=local-demo \
        --spring.datasource.url="jdbc:postgresql://localhost:$DB_PORT/conference" \
        --spring.datasource.username=conference \
        --spring.datasource.password=conference \
        > "$WORK/app-$port.log" 2>&1 &
    echo $!
}

echo "== starting two app processes on ports $PORT_A and $PORT_B =="
PID_A="$(start_app "$PORT_A")"
PID_B="$(start_app "$PORT_B")"
wait_health "$PORT_A" || { tail -30 "$WORK/app-$PORT_A.log"; fail "app A did not become healthy"; }
wait_health "$PORT_B" || { tail -30 "$WORK/app-$PORT_B.log"; fail "app B did not become healthy"; }

curl -fsS -X POST "http://localhost:$PORT_A/api/demo/reset" >/dev/null

echo "== invariant 1: Alice and Bob race for the last place (session 2, 19/20) =="
post_booking_bg "$PORT_A" "Alice" "tp-last-place-alice" 2 "$WORK/race-alice"
post_booking_bg "$PORT_B" "Bob" "tp-last-place-bob" 2 "$WORK/race-bob"
wait

STATUS_A="$(cat "$WORK/race-alice.status")"
STATUS_B="$(cat "$WORK/race-bob.status")"
echo "  process A -> $STATUS_A, process B -> $STATUS_B"

CREATED=0
CONFLICTS=0
for status in "$STATUS_A" "$STATUS_B"; do
    case "$status" in
        201) CREATED=$((CREATED + 1)) ;;
        409) CONFLICTS=$((CONFLICTS + 1)) ;;
        *) fail "unexpected status $status for last-place race" ;;
    esac
done
[[ "$CREATED" -eq 1 ]] || fail "expected exactly one created booking, got $CREATED"
[[ "$CONFLICTS" -eq 1 ]] || fail "expected exactly one conflict, got $CONFLICTS"

SESSION_JSON="$(curl -fsS "http://localhost:$PORT_B/api/sessions?page=1&size=1")"
ACTIVE="$(python3 -c "import json,sys; print(json.load(sys.stdin)['items'][0]['activeBookings'])" <<<"$SESSION_JSON")"
AVAILABLE="$(python3 -c "import json,sys; print(json.load(sys.stdin)['items'][0]['availablePlaces'])" <<<"$SESSION_JSON")"
[[ "$ACTIVE" -eq 20 ]] || fail "expected 20 active bookings after race, got $ACTIVE"
[[ "$AVAILABLE" -eq 0 ]] || fail "expected 0 available places after race, got $AVAILABLE"
echo "  session 2 is full with activeBookings=$ACTIVE availablePlaces=$AVAILABLE (no overbooking across processes)"

echo "== invariant 2: the same request key replayed against both processes =="
curl -fsS -X POST "http://localhost:$PORT_A/api/demo/reset" >/dev/null
post_booking_bg "$PORT_A" "Alice" "tp-cross-process-replay" 1 "$WORK/replay-a"
post_booking_bg "$PORT_B" "Alice" "tp-cross-process-replay" 1 "$WORK/replay-b"
wait

REPLAY_STATUSES="$(sort "$WORK/replay-a.status" "$WORK/replay-b.status" | tr '\n' ' ')"
ID_A="$(python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" < "$WORK/replay-a.json")"
ID_B="$(python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" < "$WORK/replay-b.json")"
echo "  replay statuses: $REPLAY_STATUSES, booking ids: $ID_A and $ID_B"
[[ "$REPLAY_STATUSES" == "200 201 " ]] || fail "expected one 201 and one 200 for cross-process replay, got '$REPLAY_STATUSES'"
[[ "$ID_A" == "$ID_B" ]] || fail "replays returned different booking ids: $ID_A vs $ID_B"

BOOKINGS="$(curl -fsS "http://localhost:$PORT_B/api/bookings" -H 'X-Demo-Attendee: Alice')"
COUNT="$(python3 -c "import json,sys; print(len(json.load(sys.stdin)))" <<<"$BOOKINGS")"
[[ "$COUNT" -eq 1 ]] || fail "expected exactly one active booking after cross-process replay, got $COUNT"
echo "  replay returned the original booking in both processes; Alice has $COUNT active booking"

echo
echo "TWO-PROCESS CHECKS PASSED"
