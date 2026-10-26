#!/usr/bin/env bash
# Verifies every exercise end to end: export it, assert that the acceptance check FAILS on the
# exercise state, apply the reference patch, assert that the acceptance check PASSES (plus the
# regular suites). Nothing is weakened: an unexpected pass on the faulty state is a failure.
#
#   presenter/tools/verify-exercises.sh [ex1 ex2 ex3 ex4 ex5]
#   WRITE_EVIDENCE=1   also regenerate exercises/ex2-debugging/evidence from the faulty ex2 run
#   BROWSER=chrome     browser channel for the ex1 UI acceptance (BROWSER=none skips it)
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOGS="$ROOT/build/verify-logs"; mkdir -p "$LOGS"
WORK="$(mktemp -d)"
BROWSER="${BROWSER:-chrome}"
EXERCISES=("$@"); [[ ${#EXERCISES[@]} -gt 0 ]] || EXERCISES=(ex1 ex2 ex3 ex4 ex5)
failures=0

step() {  # step <expect:pass|fail> <name> <command...>
  local expect="$1" name="$2"; shift 2
  local log="$LOGS/${CURRENT}-${name}.log"
  "$@" >"$log" 2>&1; local rc=$?
  local outcome="pass"; [[ $rc -ne 0 ]] && outcome="fail"
  if [[ "$outcome" == "$expect" ]]; then
    printf '  %-48s %s (expected %s)\n' "$name" "$outcome" "$expect"
  else
    printf '  %-48s %s (EXPECTED %s)  <-- see %s\n' "$name" "$outcome" "$expect" "$log"; tail -n 30 "$log" | sed 's/^/      /'; failures=$((failures + 1))
  fi
}
gradle() { (cd "$WS" && ./gradlew --console=plain "$@"); }
add_tests() { mkdir -p "$WS/src/test/kotlin/dev/conferenceplanner/acceptance"; cp "$@" "$WS/src/test/kotlin/dev/conferenceplanner/acceptance/"; }
restore_test() { for f in "$@"; do cp "$ROOT/src/test/kotlin/dev/conferenceplanner/$f" "$WS/src/test/kotlin/dev/conferenceplanner/$f"; done; }
apply_patch() { (cd "$WS" && patch -p1 --no-backup-if-mismatch < "$ROOT/presenter/reference-patches/$CURRENT.patch"); }

for CURRENT in "${EXERCISES[@]}"; do
  WS="$WORK/$CURRENT"
  echo "== $CURRENT  ($WS)"
  "$ROOT/tools/export-exercise.sh" "$CURRENT" "$WS" >"$LOGS/$CURRENT-export.log" 2>&1 || { echo "  export failed"; failures=$((failures + 1)); continue; }
  case "$CURRENT" in
    ex1)
      add_tests "$ROOT"/presenter/acceptance/ex1/*.kt
      step fail acceptance-on-exercise-state  gradle test --tests 'dev.conferenceplanner.acceptance.*'
      step pass apply-reference-patch          apply_patch
      step pass acceptance-after-patch         gradle test --tests 'dev.conferenceplanner.acceptance.*'
      step pass default-suite-after-patch      gradle test
      [[ "$BROWSER" != "none" ]] && step pass ui-acceptance-after-patch gradle uiTest "-Pbrowser=$BROWSER" --tests 'dev.conferenceplanner.acceptance.AvailableOnlyUiTest'
      ;;
    ex2)
      add_tests "$ROOT"/presenter/acceptance/ex2/*.kt
      if [[ "${WRITE_EVIDENCE:-0}" == "1" ]]; then
        mkdir -p "$ROOT/exercises/ex2-debugging/evidence"; find "$ROOT/exercises/ex2-debugging/evidence" -type f ! -name README.md -delete
        export EVIDENCE_DIR="$ROOT/exercises/ex2-debugging/evidence"
      fi
      step fail reproducer-on-faulty-state     gradle test --tests 'dev.conferenceplanner.acceptance.*'
      unset EVIDENCE_DIR
      step pass apply-reference-patch          apply_patch
      step pass reproducer-after-patch         gradle test --tests 'dev.conferenceplanner.acceptance.*'
      restore_test ConcurrencyTest.kt TwoProcessLastPlaceTest.kt
      step pass concurrency-and-two-process    gradle test twoProcessTest
      ;;
    ex3)
      step fail structure-on-exercise-state    bash -c "cd '$WS' && '$ROOT/presenter/acceptance/ex3-structure.sh'"
      step pass apply-reference-patch          apply_patch
      step pass structure-after-patch          bash -c "cd '$WS' && '$ROOT/presenter/acceptance/ex3-structure.sh'"
      step pass suites-after-patch             gradle test twoProcessTest
      ;;
    ex4)
      add_tests "$ROOT"/presenter/acceptance/ex4/*.kt
      step fail reproducer-on-candidate        gradle test --tests 'dev.conferenceplanner.acceptance.*'
      step pass apply-reference-patch          apply_patch
      step pass reproducer-after-patch         gradle test --tests 'dev.conferenceplanner.acceptance.*'
      restore_test NotificationTest.kt
      step pass default-suite-after-patch      gradle test
      ;;
    ex5)
      compose() { (cd "$WS" && docker compose -p conference-planner-ex5 "$@"); }
      step pass build-jar                      gradle bootJar
      step fail compose-up-on-broken-stack     compose up -d --build --wait --wait-timeout 150
      step pass compose-down-broken            compose down -v
      step pass apply-reference-patch          apply_patch
      step pass compose-up-after-patch         compose up -d --build --wait --wait-timeout 150
      step pass health-answers-up              bash -c "curl -fsS http://localhost:8085/health | grep -q '\"status\":\"UP\"'"
      step pass compose-down-fixed             compose down -v
      ;;
  esac
done
echo
if [[ $failures -eq 0 ]]; then echo "ALL EXERCISE VERIFICATIONS PASSED  (logs: $LOGS)"; rm -rf "$WORK"; else echo "$failures STEP(S) DID NOT BEHAVE AS EXPECTED  (workspaces kept in $WORK, logs: $LOGS)"; exit 1; fi
