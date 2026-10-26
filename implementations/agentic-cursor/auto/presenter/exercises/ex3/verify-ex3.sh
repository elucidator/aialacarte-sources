#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WS="${1:?usage: verify-ex3.sh <ex3-workspace>}"

echo "== Starting snapshot depends on concrete repository =="
rg -n "PostgresBookingRepository" "$WS/src/main/kotlin/com/conference/planner/booking/BookingService.kt" >/dev/null
test ! -f "$WS/src/main/kotlin/com/conference/planner/booking/BookingRepository.kt"

python3 "$ROOT/presenter/exercises/ex3/apply_reference.py" "$WS"

(
  cd "$WS"
  ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.BookingApiIT' --tests 'com.conference.planner.ConcurrentBookingIT' --tests 'com.conference.planner.NotificationIT'
)
echo "EX3 ACCEPTANCE OK"
