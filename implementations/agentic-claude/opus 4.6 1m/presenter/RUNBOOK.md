# Presenter Runbook — AI Coding à la Carte

## Pre-Talk Setup

1. Start Docker Desktop
2. Start PostgreSQL: `docker compose up -d db`
3. Start the app: `SPRING_PROFILES_ACTIVE=local-demo ./gradlew bootRun`
4. Open http://localhost:8080 in a browser
5. Verify seed data shows correctly (3 sessions with expected counts)
6. Reset if needed: click Demo Panel → Reset to Seed
7. Export each exercise to a temp directory if not already done

## Exercise 1: Code Generation (≈8 min)

**Setup**: Export baseline → `./exercises/export.sh 1 /tmp/ex1`

**Screen state**: Show the sessions list. Note there's no filter for available sessions.

**Task**: Add `availableOnly` parameter to `GET /api/sessions` and a checkbox in the UI.

**Reveal**: The correct implementation adds:
- Query parameter parsing with validation in `SessionController`
- Filtered SQL query in `BookingRepository` (WHERE clause with available_places > 0)
- `totalElements` computed from the filtered count
- A checkbox in the UI that toggles the parameter

**Reference**: `presenter/reference-patches/exercise-1.patch`

**Verify**: Run `./gradlew test` — all tests pass. Check UI: with checkbox, "Understanding Databases" (40/40) disappears.

**Reset**: `curl -X POST http://localhost:8080/api/demo/reset`

---

## Exercise 2: Debugging (≈8 min)

**Setup**: Export faulty variant → `./exercises/export.sh 2 /tmp/ex2`

**Screen state**: Show "Practical Kotlin" with 19/20 booked (1 place left).

**Task**: The booking service has a concurrency bug. Two simultaneous requests for the last place can both succeed.

**Reveal**: The faulty code reads `activeCount` without locking the session row. Two concurrent transactions both read count=19, both insert, resulting in 21 active bookings.

**Fix**: Add `SELECT ... FOR UPDATE` on the session row before checking capacity. This serializes concurrent booking attempts.

**Reference**: `presenter/reference-patches/exercise-2.patch`

**Reproducer**: `presenter/reproducers/exercise-2-reproducer.sh` (uses two parallel curl requests)

**Reset**: `curl -X POST http://localhost:8080/api/demo/reset`

---

## Exercise 3: Refactoring (≈8 min)

**Setup**: Export baseline → `./exercises/export.sh 3 /tmp/ex3`

**Screen state**: Show `BookingService.kt` — note it depends directly on `BookingRepository` (concrete class).

**Task**: Extract a `BookingRepositoryInterface` and make the service depend on it.

**Reveal**: Extract interface with all methods used by `BookingService`. The concrete class implements it. Spring injects the same bean. All tests pass unchanged.

**Reference**: `presenter/reference-patches/exercise-3.patch`

**Verify**: `./gradlew test`

---

## Exercise 4: Code Review (≈8 min)

**Setup**: Export with candidate diff → `./exercises/export.sh 4 /tmp/ex4`

**Screen state**: Show `candidate/change.diff`. Show the gateway contract in TASK.md.

**Task**: Review whether the change to use fresh delivery keys on each retry is correct.

**Reveal**: The change breaks deduplication. When a timeout occurs after the gateway accepts, the retry uses a new key → gateway accepts again → two accepted confirmations for one booking.

The baseline uses a stable key (`confirm-{bookingId}`), so retries after timeout hit the same gateway record.

**Reproducer**: Enable timeout simulation, dispatch, disable timeout, dispatch again. With the candidate code, activity shows 2 gateway deliveries. With the baseline, only 1.

**Reference**: `presenter/reference-patches/exercise-4.patch`

---

## Exercise 5: DevOps (≈8 min)

**Setup**: Export with faulty compose → `./exercises/export.sh 5 /tmp/ex5`

**Screen state**: Run `docker compose -p exercise5 up --build -d`, then `docker compose -p exercise5 ps` — show the app container as "unhealthy".

**Task**: The health check path is wrong. The app exposes `/health`, not `/actuator/health`.

**Reveal**: The `docker-compose.yml` health check uses `http://localhost:8080/actuator/health` but the app serves health at `http://localhost:8080/health`. Fix: change the path.

**Reference**: `presenter/reference-patches/exercise-5.patch`

**Clean up**: `docker compose -p exercise5 down -v`

---

## Fallback: Screenshots/Recording

If live demo fails:
- Screenshots of each exercise's expected screen state are in `presenter/screenshots/` (to be captured during rehearsal)
- The sequence of curl commands above can demonstrate each scenario without a browser

## Reset Between Exercises

```bash
curl -X POST http://localhost:8080/api/demo/reset
```

This restores seed data (3 sessions, 22/19/40 bookings, no alice/bob bookings).
