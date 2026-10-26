#!/usr/bin/env bash
#
# Presenter acceptance checks for all five exercises.
#
#   presenter/acceptance/run-acceptance.sh <1|2|3|4|5>
#   presenter/acceptance/run-acceptance.sh all
#
# Each check exports the exercise snapshot into a temporary workspace, asserts the
# documented starting condition, applies the reference patch and asserts the corrected
# behaviour. Exercise 5 starts and stops its own isolated Compose project.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIXES="$ROOT/presenter/reference-fixes"
REPRO="$ROOT/presenter/reproductions"
EVIDENCE="$ROOT/presenter/evidence"
EXERCISE="${1:?usage: run-acceptance.sh <1|2|3|4|5|all>}"
mkdir -p "$EVIDENCE"

TARGETS=()
if [[ "$EXERCISE" == "all" ]]; then
    TARGETS=(1 2 3 4 5)
else
    case "$EXERCISE" in
        1|2|3|4|5) TARGETS=("$EXERCISE") ;;
        *) echo "unknown exercise: $EXERCISE" >&2; exit 2 ;;
    esac
fi

WORK="$(mktemp -d)"
E5_PROJECT="ai-a-la-carte-e5-check"

cleanup() {
    docker compose -p "$E5_PROJECT" down -v --remove-orphans >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() { echo "ACCEPTANCE FAILED: $*" >&2; exit 1; }

export_to() {
    local exercise="$1" dest="$2"
    "$ROOT/scripts/export-exercise.sh" "$exercise" "$dest" > /dev/null
}

apply_patch() {
    local exercise="$1" workspace="$2"
    (cd "$workspace" && patch -p1 --silent < "$FIXES/e$exercise.patch")
}

run_test() {
    local workspace="$1" pattern="$2" log="$3"
    (cd "$workspace" && ./gradlew cleanTest test --tests "$pattern" --no-build-cache --console=plain) \
        > "$log" 2>&1 || true
}

expect_pre_failure() {
    local log="$1" pattern="$2" name="$3"
    grep -q "FAILED" "$log" || fail "$name: expected the check to fail before the patch (see $log)"
    grep -q "$pattern" "$log" || fail "$name: failure was not in $pattern (see $log)"
    echo "  pre-patch: $name fails as documented"
}

expect_post_success() {
    local log="$1" name="$2"
    grep -q "BUILD SUCCESSFUL" "$log" || { tail -30 "$log"; fail "$name: patch did not make the check pass"; }
    echo "  post-patch: $name passes"
}

acceptance_1() {
    local ws="$WORK/e1"
    echo "== exercise 1: availableOnly generation =="
    export_to 1 "$ws"
    cp "$REPRO/e1/AvailableOnlyIT.kt" "$ws/src/test/kotlin/com/example/conferenceplanner/"
    run_test "$ws" '*AvailableOnlyIT*' "$EVIDENCE/e1-pre.log"
    expect_pre_failure "$EVIDENCE/e1-pre.log" "availableOnly" "availability filter"
    apply_patch 1 "$ws"
    run_test "$ws" '*AvailableOnlyIT*' "$EVIDENCE/e1-post.log"
    expect_post_success "$EVIDENCE/e1-post.log" "availability filter"
}

acceptance_2() {
    local ws="$WORK/e2"
    echo "== exercise 2: deterministic overbooking reproducer =="
    export_to 2 "$ws"
    cp "$REPRO/e2/OverbookingReproducerIT.kt" "$ws/src/test/kotlin/com/example/conferenceplanner/"
    run_test "$ws" '*OverbookingReproducerIT*' "$EVIDENCE/e2-pre.log"
    expect_pre_failure "$EVIDENCE/e2-pre.log" "OverbookingReproducerIT" "last-place race"
    grep -q "exactly one attendee wins the last place" "$EVIDENCE/e2-pre.log" \
        || fail "reproducer failure was not the overbooking assertion"
    apply_patch 2 "$ws"
    run_test "$ws" '*OverbookingReproducerIT*' "$EVIDENCE/e2-post.log"
    expect_post_success "$EVIDENCE/e2-post.log" "last-place race"

    # Independent correctness check (threaded, no coordination instrumentation) on the
    # repaired snapshot; it is part of the presenter checks, not of the exercise export.
    cp "$ROOT/src/test/kotlin/com/example/conferenceplanner/ConcurrencyIT.kt" \
        "$ws/src/test/kotlin/com/example/conferenceplanner/"
    run_test "$ws" '*ConcurrencyIT*' "$EVIDENCE/e2-independent-concurrency.log"
    expect_post_success "$EVIDENCE/e2-independent-concurrency.log" "independent concurrency checks"
}

acceptance_3() {
    local ws="$WORK/e3"
    echo "== exercise 3: repository interface extraction =="
    export_to 3 "$ws"
    local repo_dir="$ws/src/main/kotlin/com/example/conferenceplanner/repository"
    grep -q "class BookingRepository(private val jdbc" "$repo_dir/BookingRepository.kt" \
        || fail "starting snapshot does not contain the concrete repository"
    [[ ! -f "$repo_dir/PostgresBookingRepository.kt" ]] || fail "starting snapshot already has the refactor"
    echo "  pre-patch: BookingService depends on the concrete PostgreSQL repository"
    apply_patch 3 "$ws"
    grep -q "interface BookingRepository" "$repo_dir/BookingRepository.kt" \
        || fail "patched snapshot has no repository interface"
    grep -q "class PostgresBookingRepository" "$repo_dir/PostgresBookingRepository.kt" \
        || fail "patched snapshot has no PostgreSQL implementation"
    (cd "$ws" && ./gradlew cleanTest test --no-build-cache --console=plain) > "$EVIDENCE/e3-post.log" 2>&1 \
        || { tail -30 "$EVIDENCE/e3-post.log"; fail "full test suite fails after the refactor"; }
    grep -q "BUILD SUCCESSFUL" "$EVIDENCE/e3-post.log" || fail "full test suite fails after the refactor"
    echo "  post-patch: interface present and the full suite (API, transactions, concurrency) passes"
}

acceptance_4() {
    local ws="$WORK/e4"
    echo "== exercise 4: review-only delivery-key reproducer =="
    export_to 4 "$ws"
    grep -q "attemptDeliveryKey" "$ws/src/main/kotlin/com/example/conferenceplanner/service/NotificationDispatcher.kt" \
        || fail "candidate snapshot does not contain the proposed change"
    cp "$REPRO/e4/DeliveryKeyReproducerIT.kt" "$ws/src/test/kotlin/com/example/conferenceplanner/"
    run_test "$ws" '*DeliveryKeyReproducerIT*' "$EVIDENCE/e4-pre.log"
    expect_pre_failure "$EVIDENCE/e4-pre.log" "DeliveryKeyReproducerIT" "delivery-key reproducer"
    grep -q "accepted gateway messages for one booking" "$EVIDENCE/e4-pre.log" \
        || fail "reproducer failure was not the duplicate-acceptance assertion"
    apply_patch 4 "$ws"
    run_test "$ws" '*DeliveryKeyReproducerIT*' "$EVIDENCE/e4-post.log"
    expect_post_success "$EVIDENCE/e4-post.log" "delivery-key reproducer"
}

wait_for_app_health() {
    local expected="$1" timeout="$2"
    local deadline=$((SECONDS + timeout)) container status
    while (( SECONDS < deadline )); do
        container="$(docker compose -p "$E5_PROJECT" ps -q app 2>/dev/null || true)"
        if [[ -n "$container" ]]; then
            status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo none)"
            [[ "$status" == "$expected" ]] && return 0
        fi
        sleep 2
    done
    return 1
}

acceptance_5() {
    local ws="$WORK/e5"
    echo "== exercise 5: Compose health check =="
    export_to 5 "$ws"
    (cd "$ws" && docker compose -p "$E5_PROJECT" up -d --build) > "$EVIDENCE/e5-pre-up.log" 2>&1 \
        || { tail -20 "$EVIDENCE/e5-pre-up.log"; fail "starting the faulty stack failed"; }
    wait_for_app_health unhealthy 240 \
        || fail "faulty snapshot never became unhealthy within the bound"
    echo "  pre-patch: app container is unhealthy, as documented"
    docker compose -p "$E5_PROJECT" ps > "$EVIDENCE/e5-pre-health.txt"

    apply_patch 5 "$ws"
    (cd "$ws" && docker compose -p "$E5_PROJECT" up -d --build) > "$EVIDENCE/e5-post-up.log" 2>&1 \
        || { tail -20 "$EVIDENCE/e5-post-up.log"; fail "restarting the corrected stack failed"; }
    wait_for_app_health healthy 240 \
        || fail "corrected snapshot did not become healthy within the bound"
    docker compose -p "$E5_PROJECT" ps > "$EVIDENCE/e5-post-health.txt"
    echo "  post-patch: app container is healthy"

    docker compose -p "$E5_PROJECT" down -v > /dev/null 2>&1
    echo "  cleanup: isolated project containers and volumes removed"
}

for target in "${TARGETS[@]}"; do
    "acceptance_$target"
    echo
done
echo "ACCEPTANCE CHECKS PASSED"
