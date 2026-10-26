#!/usr/bin/env bash
#
# Exports one conference-talk exercise into a new, empty destination.
#
#   scripts/export-exercise.sh <1|2|3|4|5> <destination>
#
# The export is a self-contained buildable workspace (sources, Gradle wrapper,
# Docker/Compose configuration, a neutral README and the task brief) plus a
# MANIFEST.sha256 with one hash per file.
#
# Only the files enumerated below are copied (explicit allowlist). Presenter notes,
# reference fixes, reproducers, acceptance checks and variant-generation sources live
# under presenter/ and are never part of an export.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

usage() {
    echo "Usage: scripts/export-exercise.sh <1|2|3|4|5> <destination>" >&2
    exit 2
}

[[ $# -eq 2 ]] || usage
EXERCISE="$1"
DEST="$2"
case "$EXERCISE" in
    1|2|3|4|5) ;;
    *) usage ;;
esac

if [[ -e "$DEST" ]]; then
    if [[ ! -d "$DEST" ]]; then
        echo "ERROR: destination exists and is not a directory: $DEST" >&2
        exit 1
    fi
    if [[ -n "$(ls -A "$DEST")" ]]; then
        echo "ERROR: refusing to overwrite non-empty destination: $DEST" >&2
        exit 1
    fi
fi
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"

copy() {
    local source="$1" target="${2:-$1}"
    mkdir -p "$DEST/$(dirname "$target")"
    cp "$ROOT/$source" "$DEST/$target"
}

copy_tree() {
    local rel="$1"
    while IFS= read -r file; do
        copy "$file"
    done < <(cd "$ROOT" && find "$rel" -type f -print | LC_ALL=C sort)
}

overlay() {
    local source="$1" target="$2"
    mkdir -p "$DEST/$(dirname "$target")"
    cp "$ROOT/$source" "$DEST/$target"
}

# ---------------- explicit allowlists ----------------

COMMON_FILES=(
    settings.gradle.kts
    build.gradle.kts
    gradle.properties
    gradlew
    gradlew.bat
    gradle/wrapper/gradle-wrapper.jar
    gradle/wrapper/gradle-wrapper.properties
    Dockerfile
    .dockerignore
    .gitignore
)

TEST_FILES=(
    src/test/kotlin/com/example/conferenceplanner/TestDatabase.kt
    src/test/kotlin/com/example/conferenceplanner/PostgresIntegrationTest.kt
    src/test/kotlin/com/example/conferenceplanner/SessionApiIT.kt
    src/test/kotlin/com/example/conferenceplanner/BookingApiIT.kt
    src/test/kotlin/com/example/conferenceplanner/ConcurrencyIT.kt
    src/test/kotlin/com/example/conferenceplanner/NotificationIT.kt
    src/test/kotlin/com/example/conferenceplanner/DemoProfileDisabledIT.kt
    src/test/kotlin/com/example/conferenceplanner/HealthIT.kt
    src/test/kotlin/com/example/conferenceplanner/HealthDownIT.kt
    src/test/kotlin/com/example/conferenceplanner/UiAssetsIT.kt
)

copy_tests_except() {
    local skip="$1"
    for file in "${TEST_FILES[@]}"; do
        [[ "$file" == "$skip" ]] && continue
        copy "$file"
    done
}

for file in "${COMMON_FILES[@]}"; do
    copy "$file"
done
copy_tree src/main
copy presenter/exercise-common/README.md README.md

case "$EXERCISE" in
    1)
        copy docker-compose.yml
        for file in "${TEST_FILES[@]}"; do copy "$file"; done
        copy presenter/exercise-common/tasks/e1-TASK.md TASK.md
        ;;
    2)
        copy docker-compose.yml
        copy_tests_except src/test/kotlin/com/example/conferenceplanner/ConcurrencyIT.kt
        overlay presenter/exercise-sources/e2/src/main/kotlin/com/example/conferenceplanner/repository/BookingRepository.kt \
            src/main/kotlin/com/example/conferenceplanner/repository/BookingRepository.kt
        overlay presenter/exercise-sources/e2/src/main/kotlin/com/example/conferenceplanner/service/BookingService.kt \
            src/main/kotlin/com/example/conferenceplanner/service/BookingService.kt
        copy presenter/exercise-common/tasks/e2-TASK.md TASK.md
        ;;
    3)
        copy docker-compose.yml
        for file in "${TEST_FILES[@]}"; do copy "$file"; done
        copy presenter/exercise-common/tasks/e3-TASK.md TASK.md
        ;;
    4)
        copy docker-compose.yml
        copy_tests_except src/test/kotlin/com/example/conferenceplanner/NotificationIT.kt
        overlay presenter/exercise-sources/e4/src/main/kotlin/com/example/conferenceplanner/service/NotificationDispatcher.kt \
            src/main/kotlin/com/example/conferenceplanner/service/NotificationDispatcher.kt
        copy presenter/exercise-common/review/GATEWAY-CONTRACT.md review/GATEWAY-CONTRACT.md
        copy presenter/exercise-common/review/base-to-candidate.diff review/base-to-candidate.diff
        overlay src/main/kotlin/com/example/conferenceplanner/service/NotificationDispatcher.kt \
            review/base/NotificationDispatcher.kt
        overlay presenter/exercise-sources/e4/src/main/kotlin/com/example/conferenceplanner/service/NotificationDispatcher.kt \
            review/candidate/NotificationDispatcher.kt
        copy presenter/exercise-common/tasks/e4-TASK.md TASK.md
        ;;
    5)
        copy presenter/exercise-sources/e5/docker-compose.yml docker-compose.yml
        for file in "${TEST_FILES[@]}"; do copy "$file"; done
        copy presenter/exercise-common/tasks/e5-TASK.md TASK.md
        ;;
esac

# ---------------- manifest ----------------

if command -v shasum >/dev/null 2>&1; then
    hash_file() { shasum -a 256 "$1" | awk '{print $1}'; }
else
    hash_file() { sha256sum "$1" | awk '{print $1}'; }
fi

(
    cd "$DEST"
    find . -type f ! -name MANIFEST.sha256 -print \
        | sed 's|^\./||' \
        | LC_ALL=C sort > /tmp/conference-planner-export-files.$$
    : > MANIFEST.sha256
    while IFS= read -r file; do
        printf '%s  %s\n' "$(hash_file "$file")" "$file" >> MANIFEST.sha256
    done < /tmp/conference-planner-export-files.$$
    rm -f /tmp/conference-planner-export-files.$$
)

echo "Exported exercise $EXERCISE to $DEST"
echo "Files: $(wc -l < "$DEST/MANIFEST.sha256" | tr -d ' ')"
echo "Manifest: $DEST/MANIFEST.sha256"
echo "Verify with: (cd \"$DEST\" && shasum -a 256 -c MANIFEST.sha256)"
