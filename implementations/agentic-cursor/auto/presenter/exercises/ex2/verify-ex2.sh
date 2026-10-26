#!/usr/bin/env bash
# Verify exercise 2: starting snapshot overbooks; reference fix does not.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WS="${1:?usage: verify-ex2.sh <ex2-workspace>}"

echo "== Assert starting snapshot uses unlocked findSession path =="
rg -n "findSession\(sessionId\)" "$WS/src/main/kotlin/com/conference/planner/booking/BookingService.kt" >/dev/null
if rg -n "findSessionForUpdate\(sessionId\)" "$WS/src/main/kotlin/com/conference/planner/booking/BookingService.kt" >/dev/null; then
  echo "FAIL: starting snapshot unexpectedly locks with findSessionForUpdate" >&2
  exit 1
fi

echo "== Reproduce overbooking with file gate =="
mkdir -p "$WS/src/test/kotlin/com/conference/planner"
cp "$ROOT/presenter/exercises/ex2/FaultReproIT.kt" \
  "$WS/src/test/kotlin/com/conference/planner/FaultReproIT.kt"
(
  cd "$WS"
  ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.FaultReproIT'
)

echo "== Apply reference fix =="
python3 "$ROOT/presenter/exercises/ex2/apply_reference.py" "$WS"
rm -f "$WS/src/test/kotlin/com/conference/planner/FaultReproIT.kt"
cp "$ROOT/presenter/exercises/ex2/FixedConcurrentIT.kt" \
  "$WS/src/test/kotlin/com/conference/planner/FixedConcurrentIT.kt"

echo "== Assert repaired code holds the last place =="
(
  cd "$WS"
  ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.FixedConcurrentIT' --tests 'com.conference.planner.BookingApiIT'
)

echo "EX2 ACCEPTANCE OK"
