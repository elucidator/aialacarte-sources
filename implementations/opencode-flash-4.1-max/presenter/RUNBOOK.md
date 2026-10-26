# Presenter runbook - AI Coding à la Carte

Presenter-only material. Never exported into exercise workspaces.

## Talk budget (40 minutes)

| Minutes | Segment | Exercise |
|---------|---------|----------|
| 0-3     | Intro: run the baseline, show sessions, book, cancel, demo dedup retry | baseline |
| 3-10    | Generation: add `availableOnly` + UI checkbox | 1 |
| 10-20   | Debugging: last-place overbooking, evidence-first | 2 |
| 20-27   | Refactoring: extract the repository interface | 3 |
| 27-34   | Review: fresh delivery key per attempt | 4 |
| 34-40   | DevOps: fix the Compose health check and show recovery | 5 |

## Pre-flight (do before the talk, online)

```bash
docker compose build                       # warm the app image build
docker compose pull db
./gradlew build -x test                    # warm Gradle caches and bootJar
cd presenter/ui-check && npx playwright install chromium && cd ../..
./presenter/build-patches.sh               # regenerate + verify patches
mkdir -p /tmp/talk && for n in 1 2 3 4 5; do ./scripts/export-exercise.sh $n /tmp/talk/e$n; done
```

After pre-flight, disconnect from the network if desired: Compose, Testcontainers
(cached image), the wrapper and dependencies all work offline.

## Baseline segment

```bash
docker compose up --build -d
docker compose ps        # wait for healthy
open http://localhost:8080
```

Screen state: three sessions with 22/8, 19/1, 40/0. Show: demo identity selector,
Book (Alice, session 1), My bookings, Cancel. Demo panel: arm failure, Dispatch (toast
`1 timed out`, confirmation PENDING with 1 accepted delivery), Dispatch again
(`1 acknowledged`, attempts 2, accepted 1). Reset.

Reset between segments: demo panel `Reset demo data` or
`curl -X POST localhost:8080/api/demo/reset`.

## Exercise 1 - generation (7 min)

```bash
./scripts/export-exercise.sh 1 /tmp/talk/e1
cd /tmp/talk/e1 && ./gradlew bootRun --args='--spring.profiles.active=local-demo'
```

Screen: no checkbox in the sessions header; `availableOnly` absent from `SessionController`.
Prompt suggestion: paste `TASK.md` and ask for the parameter, filtered pagination,
400 on invalid booleans and the checkbox.

Reveal: `./presenter/acceptance/run-acceptance.sh 1` (documents pre-patch failure and
post-patch pass) and `./presenter/ui-check/run-ui-check.sh e1` for the real checkbox.
Reference patch: `presenter/reference-fixes/e1.patch`.

Fallback if live coding stalls: apply the patch and run the two commands above.

## Exercise 2 - debugging (10 min)

```bash
./scripts/export-exercise.sh 2 /tmp/talk/e2
```

Start from the symptom-only `TASK.md`; supply
`presenter/evidence/e2-overbooking-evidence.md` as the separately handed evidence.
The exported workspace has no concurrency check, so the failure is not visible until
the reproducer is run:

```bash
cp presenter/reproductions/e2/OverbookingReproducerIT.kt \
   /tmp/talk/e2/src/test/kotlin/com/example/conferenceplanner/
cd /tmp/talk/e2 && ./gradlew test --tests '*OverbookingReproducerIT*'
```

Expected: `expected: 1 but was: 2`. After the fix:
`presenter/reference-fixes/e2.patch` -> same command passes, then
`./scripts/two-process-check.sh` for the two-process proof.

Screen state to narrate: `findSession` without `FOR UPDATE`, the count-then-insert gap,
the partial unique index still protecting duplicate attendees, and why a process-local
lock would not survive two JVMs.

## Exercise 3 - refactoring (7 min)

```bash
./scripts/export-exercise.sh 3 /tmp/talk/e3
```

Show `BookingService` depending on the concrete `BookingRepository`. Ask for the
interface extraction. Keep the exported test suite as the safety net:

```bash
cd /tmp/talk/e3 && ./gradlew test        # green before and after
```

Reference patch: `presenter/reference-fixes/e3.patch` (interface + renamed
`PostgresBookingRepository`; the full suite must stay green, including `ConcurrencyIT`).

## Exercise 4 - review (7 min)

```bash
./scripts/export-exercise.sh 4 /tmp/talk/e4
```

Screen: `review/base-to-candidate.diff` and `review/GATEWAY-CONTRACT.md`. The candidate
uses a fresh delivery key per attempt. Ask for a written review; do not run the app.
Reveal with `presenter/evidence/e4-delivery-key-evidence.md` (one booking, two accepted
confirmations) and `./presenter/acceptance/run-acceptance.sh 4`.
Reference patch: `presenter/reference-fixes/e4.patch`.

## Exercise 5 - DevOps (6 min)

```bash
./scripts/export-exercise.sh 5 /tmp/talk/e5
cd /tmp/talk/e5 && docker compose -p ai-a-la-carte-e5-talk up -d --build
docker compose -p ai-a-la-carte-e5-talk ps     # app unhealthy, process running
```

Ask to inspect, fix, validate and clean up. Reveal:

```bash
./presenter/acceptance/run-acceptance.sh 5
```

Reference patch: `presenter/reference-fixes/e5.patch` (health-check path). Recovery must
touch only this project:

```bash
docker compose -p ai-a-la-carte-e5-talk down -v
```

## Global fallbacks

- **Docker unavailable live**: use the host-JDK workflow (`scripts/two-process-check.sh`
  needs Docker; `./gradlew bootRun` with a local PostgreSQL does not).
- **No network**: pre-flight completes the caches; exports build offline.
- **Time overrun**: skip exercise 5; its patch and acceptance logs are already captured.
- **Screenshot/recording fallback**: capture the screens listed below with
  `screencapture -x presenter/screenshots/<name>.png` (macOS) or any recorder, keep them
  in `presenter/screenshots/` (presenter-only, not exported), and replay the recording
  if a live demo fails. Screens worth capturing: baseline session table + demo panel
  after dedup retry, E1 checkbox filtered list, E2 failing reproducer console, E3 two
  file names in the repository package, E4 diff hunk, E5 `unhealthy` -> `healthy`
  transition in `docker compose ps`.

## Cleanup after the talk

```bash
docker compose down -v
docker compose -p ai-a-la-carte-e5-talk down -v
docker compose -p ai-a-la-carte-ui-check down -v
rm -rf /tmp/talk
```
