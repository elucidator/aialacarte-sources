# Exercise 1 — Presenter notes

## Intent
Generation exercise: add `availableOnly` filter + UI checkbox.

## Reveal
After attempts, show filtering before pagination and `totalElements` on the filtered set.

## Reference
Apply `presenter/exercises/ex1/reference.patch` to an exercise-1 export.

## Acceptance
```bash
./scripts/verify-ex1.sh /path/to/ex1-workspace
```

## Screen state
Seed sessions visible; checkbox off by default; with filter on, session 3 disappears.
