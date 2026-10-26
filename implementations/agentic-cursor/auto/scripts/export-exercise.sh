#!/usr/bin/env bash
# Export one Conference Planner exercise into an empty destination directory.
# Usage: scripts/export-exercise.sh <1|2|3|4|5> <destination-dir>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EX="${1:-}"
DEST="${2:-}"

if [[ -z "$EX" || -z "$DEST" ]]; then
  echo "Usage: $0 <1|2|3|4|5> <destination-dir>" >&2
  exit 2
fi

if [[ -e "$DEST" ]]; then
  if [[ -d "$DEST" ]] && [[ -n "$(ls -A "$DEST" 2>/dev/null || true)" ]]; then
    echo "Refusing to overwrite non-empty destination: $DEST" >&2
    exit 1
  fi
  if [[ ! -d "$DEST" ]]; then
    echo "Destination exists and is not a directory: $DEST" >&2
    exit 1
  fi
else
  mkdir -p "$DEST"
fi

DEST="$(cd "$DEST" && pwd)"
STAGING="$DEST/.export-staging-$$"
mkdir -p "$STAGING"

cleanup() { rm -rf "$STAGING"; }
trap cleanup EXIT

copy_allowlisted_baseline() {
  local target="$1"
  mkdir -p "$target"
  # Explicit allowlist — never rely on ignore files to hide presenter material.
  local paths=(
    "build.gradle.kts"
    "settings.gradle.kts"
    "gradle.properties"
    "gradlew"
    "gradlew.bat"
    "gradle/wrapper/gradle-wrapper.jar"
    "gradle/wrapper/gradle-wrapper.properties"
    "Dockerfile"
    "docker-compose.yml"
    "docker-compose.db.yml"
    ".gitignore"
    "init-mirror.gradle.kts"
    "src/main"
    "src/test"
  )
  for p in "${paths[@]}"; do
    if [[ -e "$ROOT/$p" ]]; then
      mkdir -p "$target/$(dirname "$p")"
      cp -R "$ROOT/$p" "$target/$p"
    fi
  done
  # Neutral README is replaced per exercise below; strip baseline docs that are not allowlisted.
}

write_manifest() {
  local dir="$1"
  (
    cd "$dir"
    find . -type f ! -path './EXERCISE-MANIFEST.sha256' ! -path './.export-staging-*' \
      | LC_ALL=C sort \
      | while read -r f; do
          if command -v sha256sum >/dev/null 2>&1; then
            sha256sum "$f"
          else
            shasum -a 256 "$f"
          fi
        done
  ) > "$dir/EXERCISE-MANIFEST.sha256"
}

case "$EX" in
  1)
    copy_allowlisted_baseline "$STAGING"
    cp "$ROOT/exercises/ex1/TASK.md" "$STAGING/TASK.md"
    cp "$ROOT/exercises/ex1/README.md" "$STAGING/README.md"
    ;;
  2)
    copy_allowlisted_baseline "$STAGING"
    python3 "$ROOT/exercises/ex2/apply_faulty.py" "$STAGING"
    cp "$ROOT/exercises/ex2/TASK.md" "$STAGING/TASK.md"
    cp "$ROOT/exercises/ex2/README.md" "$STAGING/README.md"
    ;;
  3)
    copy_allowlisted_baseline "$STAGING"
    cp "$ROOT/exercises/ex3/TASK.md" "$STAGING/TASK.md"
    cp "$ROOT/exercises/ex3/README.md" "$STAGING/README.md"
    ;;
  4)
    mkdir -p "$STAGING/base" "$STAGING/candidate"
    copy_allowlisted_baseline "$STAGING/base"
    copy_allowlisted_baseline "$STAGING/candidate"
    python3 "$ROOT/exercises/ex4/apply_candidate.py" "$STAGING/candidate"
    (
      cd "$STAGING"
      diff -ruN base candidate > candidate.diff || true
    )
    cp "$ROOT/exercises/ex4/TASK.md" "$STAGING/TASK.md"
    cp "$ROOT/exercises/ex4/README.md" "$STAGING/README.md"
    cp "$ROOT/exercises/ex4/GATEWAY-CONTRACT.md" "$STAGING/GATEWAY-CONTRACT.md"
    ;;
  5)
    copy_allowlisted_baseline "$STAGING"
    python3 - <<'PY' "$STAGING/docker-compose.yml"
import pathlib, sys
p = pathlib.Path(sys.argv[1])
text = p.read_text()
text = text.replace(
    "http://localhost:8080/health",
    "http://localhost:8080/actuator/healthz",
)
if not text.lstrip().startswith("name:"):
    text = "name: conference-planner-ex5\n\n" + text
p.write_text(text)
PY
    cp "$ROOT/exercises/ex5/TASK.md" "$STAGING/TASK.md"
    cp "$ROOT/exercises/ex5/README.md" "$STAGING/README.md"
    ;;
  *)
    echo "Unknown exercise: $EX" >&2
    exit 2
    ;;
esac

shopt -s dotglob nullglob
for item in "$STAGING"/*; do
  mv "$item" "$DEST"/
done
shopt -u dotglob nullglob
write_manifest "$DEST"
echo "Exported exercise $EX to $DEST"
echo "Manifest: $DEST/EXERCISE-MANIFEST.sha256"
