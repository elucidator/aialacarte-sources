#!/usr/bin/env bash
# Builds presenter/reference-patches/exN.patch: unified diff from the exported exercise state to the
# exercise state plus the presenter solution (presenter/solutions/exN/overlay and/or RESTORE_BASELINE).
# Apply inside an export with:  patch -p1 < exN.patch
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
for ex in ex1 ex2 ex3 ex4 ex5; do
  a="$WORK/$ex/a"; b="$WORK/$ex/b"
  "$ROOT/tools/export-exercise.sh" "$ex" "$a" >/dev/null
  rm -f "$a/MANIFEST.sha256"
  cp -Rp "$a" "$b"
  sol="$ROOT/presenter/solutions/$ex"
  if [[ -d "$sol/overlay" ]]; then
    (cd "$sol/overlay" && find . -type f) | while read -r f; do mkdir -p "$b/$(dirname "$f")"; cp -p "$sol/overlay/$f" "$b/$f"; done
  fi
  if [[ -f "$sol/RESTORE_BASELINE" ]]; then
    while read -r f; do [[ -n "$f" ]] && cp -p "$ROOT/$f" "$b/$f"; done < "$sol/RESTORE_BASELINE"
  fi
  out="$ROOT/presenter/reference-patches/$ex.patch"
  (cd "$WORK/$ex" && diff -ruN a b > "$out") && { echo "$ex: solution identical to exercise state?!" >&2; exit 1; } || true
  echo "$ex: $(grep -c '^+++ ' "$out") file(s) in $out"
done
