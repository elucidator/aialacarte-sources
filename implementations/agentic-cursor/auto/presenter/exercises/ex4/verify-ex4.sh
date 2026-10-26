#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WS="${1:?usage: verify-ex4.sh <ex4-workspace>}"

echo "== Candidate shows fresh attempt keys =="
rg -n "attemptKey" "$WS/candidate/src/main/kotlin/com/conference/planner/notify/Notification.kt" >/dev/null
test -f "$WS/candidate.diff"

echo "== Reproduce two accepts on candidate =="
mkdir -p "$WS/candidate/src/test/kotlin/com/conference/planner"
cp "$ROOT/presenter/exercises/ex4/FreshKeyDefectIT.kt" \
  "$WS/candidate/src/test/kotlin/com/conference/planner/FreshKeyDefectIT.kt"
(
  cd "$WS/candidate"
  ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.FreshKeyDefectIT'
)

echo "== Reference correction yields one accept =="
python3 "$ROOT/presenter/exercises/ex4/apply_reference.py" "$WS/candidate"
(
  cd "$WS/candidate"
  ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.NotificationIT'
)

echo "EX4 ACCEPTANCE OK"
