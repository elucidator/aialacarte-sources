#!/usr/bin/env bash
# Export one exercise into a new, empty destination as a self-contained workspace.
#
#   tools/export-exercise.sh <ex1|ex2|ex3|ex4|ex5> <destination-directory>
#
# Only files on the explicit allowlist below are copied. presenter/, exercises/ (except the
# selected exercise's brief, overlay and extras) and tools/ are never part of an export.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXERCISE="${1:-}"
DEST="${2:-}"
[[ -n "$EXERCISE" && -n "$DEST" ]] || { echo "usage: $0 <ex1|ex2|ex3|ex4|ex5> <destination>" >&2; exit 2; }

case "$EXERCISE" in
  ex1) EX_DIR="exercises/ex1-generation" ;;
  ex2) EX_DIR="exercises/ex2-debugging" ;;
  ex3) EX_DIR="exercises/ex3-refactoring" ;;
  ex4) EX_DIR="exercises/ex4-review" ;;
  ex5) EX_DIR="exercises/ex5-devops" ;;
  *) echo "unknown exercise '$EXERCISE'" >&2; exit 2 ;;
esac

if [[ -e "$DEST" ]]; then
  [[ -d "$DEST" ]] || { echo "destination exists and is not a directory: $DEST" >&2; exit 1; }
  [[ -z "$(ls -A "$DEST")" ]] || { echo "destination is not empty, refusing to overwrite: $DEST" >&2; exit 1; }
fi
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"

# ---- 1. Allowlisted baseline files ----------------------------------------------------------
ALLOW_FILES=(
  build.gradle.kts settings.gradle.kts gradle.properties
  gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties
  .gitignore .dockerignore Dockerfile docker-compose.yml BUILD-ENVIRONMENT.md
)
ALLOW_DIRS=( src/main src/test )

copy_file() { mkdir -p "$DEST/$(dirname "$1")"; cp -p "$ROOT/$1" "$DEST/$1"; }
for f in "${ALLOW_FILES[@]}"; do copy_file "$f"; done
for d in "${ALLOW_DIRS[@]}"; do
  (cd "$ROOT" && find "$d" -type f) | while read -r f; do copy_file "$f"; done
done

# ---- 2. README: everything above the EXPORT-CUT marker, minus stripped sections; marker lines dropped ----
STRIP_SECTIONS=""
[[ -f "$ROOT/$EX_DIR/export.conf" ]] && source "$ROOT/$EX_DIR/export.conf"
python3 - "$ROOT/README.md" "$DEST/README.md" "$STRIP_SECTIONS" <<'PY'
import re, sys
src, dst, strip = sys.argv[1], sys.argv[2], set(sys.argv[3].split())
out, skipping = [], False
for line in open(src, encoding="utf-8"):
    if "<!-- EXPORT-CUT -->" in line: break
    m = re.search(r"<!-- SECTION:([a-z-]+) -->", line)
    if m and m.group(1) in strip: skipping = True
    if not skipping and "<!-- SECTION:" not in line and "<!-- /SECTION:" not in line: out.append(line)
    m = re.search(r"<!-- /SECTION:([a-z-]+) -->", line)
    if m and m.group(1) in strip: skipping = False
open(dst, "w", encoding="utf-8").write("".join(out))
PY

# ---- 3. Exercise overlay, excludes, extras, brief --------------------------------------------
if [[ -d "$ROOT/$EX_DIR/overlay" ]]; then
  (cd "$ROOT/$EX_DIR/overlay" && find . -type f) | while read -r f; do
    mkdir -p "$DEST/$(dirname "$f")"; cp -p "$ROOT/$EX_DIR/overlay/$f" "$DEST/$f"
  done
fi
if [[ -f "$ROOT/$EX_DIR/exclude.txt" ]]; then
  while read -r f; do [[ -n "$f" ]] && rm -f "$DEST/$f"; done < "$ROOT/$EX_DIR/exclude.txt"
fi
if [[ -f "$ROOT/$EX_DIR/diff-files.txt" ]]; then
  mkdir -p "$DEST/review/base"
  while read -r f; do
    [[ -n "$f" ]] || continue
    cp -p "$ROOT/$f" "$DEST/review/base/$(basename "$f")"
    diff -u --label "a/$f" --label "b/$f" "$ROOT/$f" "$ROOT/$EX_DIR/overlay/$f" >> "$DEST/review/candidate.diff" || true
  done < "$ROOT/$EX_DIR/diff-files.txt"
fi
if [[ -d "$ROOT/$EX_DIR/extra" ]]; then
  (cd "$ROOT/$EX_DIR/extra" && find . -type f) | while read -r f; do
    mkdir -p "$DEST/$(dirname "$f")"; cp -p "$ROOT/$EX_DIR/extra/$f" "$DEST/$f"
  done
fi
cp -p "$ROOT/$EX_DIR/TASK.md" "$DEST/TASK.md"

# ---- 4. Guard: nothing presenter-only may leak ------------------------------------------------
if grep -rIl -E "PRESENTER-ONLY|presenter/|reference-patch|/acceptance/" "$DEST" >/dev/null; then
  echo "export aborted: presenter-only marker found in $DEST" >&2
  grep -rIl -E "PRESENTER-ONLY|presenter/|reference-patch|/acceptance/" "$DEST" >&2
  exit 1
fi

# ---- 5. Manifest with file hashes -------------------------------------------------------------
if command -v sha256sum >/dev/null; then SHA="sha256sum"; else SHA="shasum -a 256"; fi
(cd "$DEST" && find . -type f ! -name MANIFEST.sha256 | sed 's|^\./||' | LC_ALL=C sort | xargs $SHA) > "$DEST/MANIFEST.sha256"
WORKSPACE_HASH=$($SHA "$DEST/MANIFEST.sha256" | cut -d' ' -f1)

echo "exported $EXERCISE to $DEST"
echo "files: $(wc -l < "$DEST/MANIFEST.sha256" | tr -d ' ')   workspace hash (sha256 of MANIFEST.sha256): $WORKSPACE_HASH"
