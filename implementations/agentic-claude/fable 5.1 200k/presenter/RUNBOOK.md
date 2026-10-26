# Presenter runbook – AI Coding à la Carte (40 min)

Everything here is presenter-only. Prepare on the day: `docker compose up -d --build --wait`
in the baseline, open `http://localhost:8080` (projector tab 1) and
`http://localhost:8080/demo.html` (tab 2), set identity to Alice.

## Preparation checklist (T-60 min)

```bash
export MAVEN_REPOSITORY_URL=...      # only when Maven Central is unreachable
./gradlew test                         # ~1 min, 34 tests
./gradlew bootJar && docker compose up -d --build --wait
curl -s localhost:8080/health          # {"status":"UP","database":"UP"}
for ex in ex1 ex2 ex3 ex4 ex5; do tools/export-exercise.sh $ex ~/talk/$ex; done
tools/export-evidence.sh ~/talk/ex2-evidence
```

Record the workspace hash printed by each export in `RUN-LEDGER-TEMPLATE.md` copies.
If the live demo may be unreliable (network, Docker Hub limits) run
`presenter/tools/verify-exercises.sh` the evening before and keep `build/verify-logs/`
plus `build/ui-screenshots/` as the fallback material.

## Talk mapping

| Minute | Segment | Screen state | Reveal / reset |
|-------:|---------|--------------|----------------|
| 0–4 | Intro, the app | Tab 1: seed list 22/30, 19/20, 40/40; Book on Practical Kotlin → 20/20; My bookings shows it | Cancel it again before moving on, or demo panel → Reset seed data |
| 4–11 | Ex1 generation | Exported `ex1` workspace in the tool; brief `TASK.md` | Show acceptance run: `AvailableOnlyFilterTest` fails before, passes after; checkbox in tab 1 (restart app from ex1 workspace on port 8081 if you want it live) |
| 11–20 | Ex2 debugging | Exported `ex2` + evidence bundle; tool asked to reproduce | Reveal: the plain `SELECT` where the row lock was; run `LastPlaceInterleavingTest` before/after; mention two-process test |
| 20–26 | Ex3 refactoring | Exported `ex3`; diff of the result | `ex3-structure.sh` + `test twoProcessTest` |
| 26–32 | Ex4 review | Exported `ex4`: `review/CHANGE.md`, `candidate.diff`, gateway contract | Tab 2: book as Alice, "Gateway: simulate lost acknowledgement", Dispatch, restore, Dispatch → with candidate two gateway messages; with baseline one message + `DUPLICATE_ACKNOWLEDGED` |
| 32–37 | Ex5 devops | Exported `ex5`: `docker compose up --wait` hanging then failing; `docker compose ps` shows unhealthy | `docker inspect` health log → 404 on `/actuator/health`; fix path; healthy in ~10 s; `down -v` |
| 37–40 | Wrap-up, ledger | Filled run ledgers side by side | — |

## Reset between segments

- Booking state: demo panel **Reset seed data** (also clears confirmations and activity).
- Whole stack: `docker compose down -v && docker compose up -d --wait` (≈15 s).
- Ex5 leftovers: `docker compose -p conference-planner-ex5 down -v`.

## Fallbacks

- No network: all Gradle dependencies are in `~/.gradle` after the checklist run; images are
  local after the first Compose start. Do not run `--build` with `--pull`.
- Browser demo broken: `build/ui-screenshots/01–06*.png` from `./gradlew uiTest`.
- Verification broken: `build/verify-logs/*.log` from the evening-before run.
- Docker Hub `toomanyrequests`: pull `postgres:17.9-alpine` and `eclipse-temurin:21-jre-alpine`
  through a mirror and `docker tag` them to the canonical names.

## Never do on stage

Run `presenter/` scripts inside an exported workspace shown to the audience (the guard in the
export script only protects exports, not what you type), or open `presenter/notes/`.
