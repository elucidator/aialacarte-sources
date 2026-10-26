#!/usr/bin/env bash
set -euo pipefail

# Export an exercise workspace to a destination directory.
# Usage: ./exercises/export.sh <exercise-number> <destination>
# Example: ./exercises/export.sh 1 /tmp/exercise-1

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <exercise-number> <destination>"
    echo "  exercise-number: 1-5"
    echo "  destination: path to empty directory for export"
    exit 1
fi

EXERCISE="$1"
DEST="$(cd "$(dirname "$2")" 2>/dev/null && pwd)/$(basename "$2")" || DEST="$2"

if [[ ! "$EXERCISE" =~ ^[1-5]$ ]]; then
    echo "Error: exercise-number must be 1-5"
    exit 1
fi

if [[ -e "$DEST" ]] && [[ "$(ls -A "$DEST" 2>/dev/null)" ]]; then
    echo "Error: destination '$DEST' exists and is not empty"
    exit 1
fi

mkdir -p "$DEST"

# Allowlist of baseline files to export
ALLOWLIST=(
    "build.gradle.kts"
    "settings.gradle.kts"
    "gradlew"
    "gradlew.bat"
    "gradle/wrapper/gradle-wrapper.jar"
    "gradle/wrapper/gradle-wrapper.properties"
    "Dockerfile"
    "docker-compose.yml"
    "src/main/kotlin/com/conferenceplanner/ConferencePlannerApplication.kt"
    "src/main/kotlin/com/conferenceplanner/model/Models.kt"
    "src/main/kotlin/com/conferenceplanner/repository/BookingRepository.kt"
    "src/main/kotlin/com/conferenceplanner/service/BookingService.kt"
    "src/main/kotlin/com/conferenceplanner/service/NotificationService.kt"
    "src/main/kotlin/com/conferenceplanner/service/SeedService.kt"
    "src/main/kotlin/com/conferenceplanner/controller/SessionController.kt"
    "src/main/kotlin/com/conferenceplanner/controller/BookingController.kt"
    "src/main/kotlin/com/conferenceplanner/controller/DemoController.kt"
    "src/main/kotlin/com/conferenceplanner/controller/HealthController.kt"
    "src/main/kotlin/com/conferenceplanner/config/AppConfig.kt"
    "src/main/resources/db/migration/V1__initial_schema.sql"
    "src/main/resources/application.yml"
    "src/main/resources/static/index.html"
    "src/main/resources/static/style.css"
    "src/main/resources/static/app.js"
    "src/test/kotlin/com/conferenceplanner/IntegrationTest.kt"
    "src/test/kotlin/com/conferenceplanner/ConcurrencyTest.kt"
)

copy_baseline() {
    for f in "${ALLOWLIST[@]}"; do
        local src="$PROJECT_DIR/$f"
        local dst="$DEST/$f"
        if [[ -f "$src" ]]; then
            mkdir -p "$(dirname "$dst")"
            cp "$src" "$dst"
        fi
    done
    chmod +x "$DEST/gradlew" 2>/dev/null || true
}

generate_manifest() {
    local manifest="$DEST/MANIFEST.txt"
    echo "# File manifest generated at $(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$manifest"
    echo "# Exercise $EXERCISE" >> "$manifest"
    (cd "$DEST" && find . -type f ! -name MANIFEST.txt | sort | while read -r f; do
        shasum -a 256 "$f" >> "$manifest"
    done)
}

EXERCISE_DIR="$SCRIPT_DIR/exercise-$EXERCISE"

case "$EXERCISE" in
    1)
        copy_baseline
        cp "$EXERCISE_DIR/TASK.md" "$DEST/TASK.md"
        cp "$EXERCISE_DIR/README.md" "$DEST/README.md"
        ;;
    2)
        copy_baseline
        # Apply faulty variant
        cp "$EXERCISE_DIR/BookingService_faulty.kt" \
           "$DEST/src/main/kotlin/com/conferenceplanner/service/BookingService.kt"
        cp "$EXERCISE_DIR/TASK.md" "$DEST/TASK.md"
        cp "$EXERCISE_DIR/README.md" "$DEST/README.md"
        ;;
    3)
        copy_baseline
        cp "$EXERCISE_DIR/TASK.md" "$DEST/TASK.md"
        cp "$EXERCISE_DIR/README.md" "$DEST/README.md"
        ;;
    4)
        copy_baseline
        # Copy the base version and diff for review
        cp "$EXERCISE_DIR/NotificationService_candidate.kt" "$DEST/candidate/"  2>/dev/null || {
            mkdir -p "$DEST/candidate"
            cp "$EXERCISE_DIR/NotificationService_candidate.kt" "$DEST/candidate/"
        }
        cp "$EXERCISE_DIR/change.diff" "$DEST/candidate/change.diff"
        cp "$EXERCISE_DIR/TASK.md" "$DEST/TASK.md"
        cp "$EXERCISE_DIR/README.md" "$DEST/README.md"
        ;;
    5)
        copy_baseline
        # Apply faulty docker-compose
        cp "$EXERCISE_DIR/docker-compose.yml" "$DEST/docker-compose.yml"
        cp "$EXERCISE_DIR/TASK.md" "$DEST/TASK.md"
        cp "$EXERCISE_DIR/README.md" "$DEST/README.md"
        ;;
esac

generate_manifest

echo "Exercise $EXERCISE exported to: $DEST"
echo "Files: $(wc -l < "$DEST/MANIFEST.txt") entries in manifest"
