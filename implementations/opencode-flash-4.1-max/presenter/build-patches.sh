#!/usr/bin/env bash
#
# Builds presenter/reference-fixes/eN.patch for each exercise by exporting the starting
# snapshot, applying the fixed fixture files, and diffing the two trees. Then verifies
# that every patch applies cleanly to a fresh export with `patch -p1`.
#
# Run from the repository root: ./presenter/build-patches.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXES="$ROOT/presenter/reference-fixes"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

apply_fix() {
    local exercise="$1" tree="$2"
    case "$exercise" in
        1)
            cp "$FIXES/e1-files/SessionRepository.kt" \
                "$tree/src/main/kotlin/com/example/conferenceplanner/repository/SessionRepository.kt"
            cp "$FIXES/e1-files/SessionController.kt" \
                "$tree/src/main/kotlin/com/example/conferenceplanner/web/SessionController.kt"
            cp "$FIXES/e1-files/index.html" "$tree/src/main/resources/static/index.html"
            cp "$FIXES/e1-files/app.js" "$tree/src/main/resources/static/app.js"
            ;;
        2)
            cp "$FIXES/e2-files/BookingRepository.kt" \
                "$tree/src/main/kotlin/com/example/conferenceplanner/repository/BookingRepository.kt"
            ;;
        3)
            rm "$tree/src/main/kotlin/com/example/conferenceplanner/repository/BookingRepository.kt"
            cp "$FIXES/e3-files/BookingRepository.kt" \
                "$tree/src/main/kotlin/com/example/conferenceplanner/repository/BookingRepository.kt"
            cp "$FIXES/e3-files/PostgresBookingRepository.kt" \
                "$tree/src/main/kotlin/com/example/conferenceplanner/repository/PostgresBookingRepository.kt"
            ;;
        4)
            cp "$FIXES/e4-files/NotificationDispatcher.kt" \
                "$tree/src/main/kotlin/com/example/conferenceplanner/service/NotificationDispatcher.kt"
            ;;
        5)
            cp "$FIXES/e5-files/docker-compose.yml" "$tree/docker-compose.yml"
            ;;
    esac
}

for exercise in 1 2 3 4 5; do
    rm -rf "$WORK/a" "$WORK/b"
    "$ROOT/scripts/export-exercise.sh" "$exercise" "$WORK/a" > /dev/null
    cp -R "$WORK/a" "$WORK/b"
    rm -f "$WORK/a/MANIFEST.sha256" "$WORK/b/MANIFEST.sha256"
    apply_fix "$exercise" "$WORK/b"
    (cd "$WORK" && git diff --no-index --no-color --src-prefix= --dst-prefix= a b > "$FIXES/e$exercise.patch" || true)
    echo "built $FIXES/e$exercise.patch ($(grep -c '^diff --git' "$FIXES/e$exercise.patch") file(s))"

    rm -rf "$WORK/verify"
    "$ROOT/scripts/export-exercise.sh" "$exercise" "$WORK/verify" > /dev/null
    (cd "$WORK/verify" && patch -p1 --dry-run < "$FIXES/e$exercise.patch" > /dev/null) \
        || { echo "ERROR: patch e$exercise does not apply cleanly" >&2; exit 1; }
    echo "  verified: patch e$exercise applies cleanly with patch -p1"
done

echo "All reference patches built and verified."
