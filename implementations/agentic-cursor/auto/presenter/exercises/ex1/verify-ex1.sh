#!/usr/bin/env bash
# Verify exercise 1: faulty starting snapshot lacks availableOnly; reference adds it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WS="${1:?usage: verify-ex1.sh <ex1-workspace>}"

echo "== Assert starting snapshot has no availableOnly =="
if rg -n "availableOnly" "$WS/src" >/dev/null; then
  echo "FAIL: starting snapshot already mentions availableOnly" >&2
  exit 1
fi

echo "== Apply reference =="
python3 "$ROOT/presenter/exercises/ex1/apply_reference.py" "$WS"

echo "== Run tests including availableOnly checks =="
mkdir -p "$WS/src/test/kotlin/com/conference/planner"
cp "$ROOT/presenter/exercises/ex1/AvailableOnlyIT.kt" \
  "$WS/src/test/kotlin/com/conference/planner/AvailableOnlyIT.kt"
(
  cd "$WS"
  ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.AvailableOnlyIT' --tests 'com.conference.planner.BookingApiIT'
)

echo "EX1 ACCEPTANCE OK"
