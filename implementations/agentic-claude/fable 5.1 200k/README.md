# Conference Planner

A small session-booking application used as the demo system for the talk *AI Coding à la Carte*.
Attendees book places in conference sessions; the application enforces capacity, prevents
duplicate bookings, makes booking requests idempotent and records confirmation notifications
through a simulated (fake) notification gateway.

Stack: Kotlin 2.2 on JVM 21, Spring Boot 4, plain SQL over JDBC with explicit transaction
boundaries, Flyway migrations, PostgreSQL 17, plain HTML/CSS/JS served by the backend, Gradle
(Kotlin DSL, wrapper included), Docker Compose. Exact versions: `BUILD-ENVIRONMENT.md`.

## Prerequisites

- JDK 21 (host workflow and tests) — the Gradle toolchain requires exactly Java 21.
- Docker with Docker Compose v2 (Compose workflow, integration tests via Testcontainers).
- Google Chrome (optional, only for the browser tests).

Behind a corporate network, point Gradle at a Maven mirror: `export MAVEN_REPOSITORY_URL=https://your.mirror/maven`.
When unset, Gradle uses Maven Central and the Gradle plugin portal.

## Run with Docker Compose

```bash
./gradlew bootJar                 # builds build/libs/conference-planner.jar
docker compose up -d --build --wait
open http://localhost:8080        # session list;  http://localhost:8080/demo.html = demo panel
```

The Compose stack runs the app with the `local-demo` profile, so the demo panel is active.
Stop with `docker compose down`; add `-v` to discard the database volume.

## Run on the host JDK

```bash
docker compose up -d db           # PostgreSQL only (port 5432, user/password/database: conference)
./gradlew bootRun --args='--spring.profiles.active=local-demo'
```

Without the `local-demo` profile the `/api/demo/*` endpoints do not exist (404) and the demo
panel page reports itself as disabled. The database is seeded automatically on first start.

## Reset the demo data

- Demo panel: **Reset seed data** button, or `curl -X POST localhost:8080/api/demo/reset`.
- Compose: `docker compose down -v && docker compose up -d --wait` recreates everything.

Reset truncates bookings, requests, confirmations, delivery attempts, gateway messages and the
activity log, then restores the exact seed below.

## Seed data

| id | Session                         | Room   | Capacity | Active bookings |
|----|---------------------------------|--------|----------|-----------------|
| 1  | Debugging Distributed Systems   | Room A | 30       | 22              |
| 2  | Practical Kotlin                | Room B | 20       | 19              |
| 3  | Understanding Databases         | Room C | 40       | 40              |

Seed bookings belong to fictional attendees `seed-01` … `seed-40`. The demo identities `alice`
and `bob` start without bookings. IDs are deterministic (bookings 1–81 are seed; new bookings
start at 1000). Seed bookings create no pending confirmations.

## Tests

```bash
./gradlew test                 # API, concurrency, notification, health tests (Testcontainers PostgreSQL)
<!-- SECTION:two-process -->
./gradlew twoProcessTest       # two app processes on one database, last-place race, 3 rounds
<!-- /SECTION:two-process -->
./gradlew uiTest -Pbrowser=chrome   # Playwright browser flow; screenshots in build/ui-screenshots
```

`uiTest` uses the locally installed Google Chrome (`-Pbrowser=chrome`); without the flag it
uses Playwright's bundled Chromium, which needs a one-off download.

## HTTP API

All requests that act on behalf of an attendee carry `X-Demo-Attendee: alice|bob`. This is a
demo identity selector, not authentication.

| Method | Path                        | Notes |
|--------|-----------------------------|-------|
| GET    | `/api/sessions?page=0&size=20` | `{items, page, size, totalElements}`; `size` 1..100, `page` ≥ 0, otherwise 400. Items: `id, title, room, capacity, activeBookings, availablePlaces`, ordered by the session's fixed sort order. |
| POST   | `/api/bookings`             | Body `{"sessionId": 2}`, headers `X-Demo-Attendee`, `Idempotency-Key`. 201 with the booking; replaying the same key returns 200 with the original booking. |
| GET    | `/api/bookings`             | Active bookings of the attendee. |
| DELETE | `/api/bookings/{id}`        | 204; repeating the call is also 204. 404 when the booking does not exist or belongs to someone else. |
| GET    | `/health`                   | 200 `{"status":"UP","database":"UP"}` only when the database answers; otherwise 503. |
| `/api/demo/*`                        | Only with profile `local-demo`: `GET status`, `POST reset`, `POST gateway {"loseAcknowledgements": true}`, `POST dispatch`, `GET activity`. |

Errors are always `{"code": "...", "message": "...", "status": n}`:

| Status | Codes |
|--------|-------|
| 400 | `VALIDATION_ERROR`, `MISSING_ATTENDEE`, `UNKNOWN_ATTENDEE`, `MISSING_REQUEST_KEY` |
| 404 | `SESSION_NOT_FOUND`, `BOOKING_NOT_FOUND`, `NOT_FOUND` |
| 409 | `SESSION_FULL`, `ALREADY_BOOKED`, `REQUEST_KEY_REUSED` (same key, different session) |
| 500 | `INTERNAL_ERROR` |

## Design notes

### Transactions

Every write goes through `TransactionTemplate` in `BookingService`, `ConfirmationDispatcher`
or `FakeGateway`; there is no declarative transaction management. A booking is one
transaction: claim the idempotency key, check the session, insert the booking, record the
pending confirmation. Cancellation is one transaction. Dispatching a confirmation uses three
short transactions (pick a batch, gateway acceptance, record the attempt) so that a slow or
failing gateway never holds a booking lock.

<!-- SECTION:concurrency -->
### Invariants under concurrency

The application may run as several processes against one database, so all coordination lives
in PostgreSQL:

1. **Capacity is never exceeded.** `book()` first locks the session row with
   `SELECT … FOR UPDATE`, then counts active bookings and inserts inside the same transaction.
   Concurrent bookings for one session serialize on that row lock.
2. **One active booking per attendee and session.** Partial unique index
   `bookings_one_active_per_attendee` on `(session_id, attendee_id) WHERE status = 'ACTIVE'`,
   checked in code first for a clean 409 and enforced by the index as the last line of defence.
3. **Cancellation releases the place exactly once.** The booking row is locked and only an
   `ACTIVE` row is flipped to `CANCELLED`; repeated calls are 204 no-ops.
4. **Replays never rebook.** The `(attendee_id, request_key)` row in `booking_requests` is
   inserted with `ON CONFLICT DO NOTHING` at the start of the transaction. A concurrent replay
   blocks on that insert until the first transaction commits, then reads the completed
   request and returns the original booking. A replay after cancellation also returns the
   original (now cancelled) booking rather than booking again.
5. **Accepted bookings survive confirmation failure.** The pending confirmation is written in
   the booking transaction; delivery happens later and separately.
<!-- /SECTION:concurrency -->

<!-- SECTION:notifications -->
### Confirmation notifications

Booking inserts a `PENDING` row in `confirmations` with a stable delivery key
`confirmation-booking-<id>`. The manual dispatcher (demo panel **Dispatch pending
confirmations**) takes at most 20 pending rows with fewer than 5 attempts, calls the fake
gateway once per row with that same key and records the attempt (`ACKNOWLEDGED`,
`DUPLICATE_ACKNOWLEDGED`, `TIMEOUT`). The gateway records acceptance in its own transaction
and deduplicates by delivery key; when *simulate lost acknowledgement* is on it still records
acceptance but times out towards the caller. The retry then receives `DUPLICATE_ACKNOWLEDGED`
and the confirmation is marked delivered without a second message. There is no backoff;
retries are bounded to 5 attempts.
<!-- /SECTION:notifications -->

## Troubleshooting

- **`./gradlew` cannot download dependencies** — set `MAVEN_REPOSITORY_URL` to a reachable
  mirror (see Prerequisites) and rerun.
- **Testcontainers or Compose fails with `toomanyrequests` from Docker Hub** — pull the images
  through a registry mirror you have access to and retag them locally, for example
  `docker pull <mirror>/library/postgres:17.9-alpine && docker tag <mirror>/library/postgres:17.9-alpine postgres:17.9-alpine`.
  The project references the canonical names only.
- **`/health` returns 503** — the database is not reachable; `docker compose ps` and
  `docker compose logs db`.
- **Port 8080 or 5432 in use** — `PORT=8081 ./gradlew bootRun …` for the app on the host; edit
  the port mappings in `docker-compose.yml` for Compose.
- **App container never becomes healthy** — `docker compose logs app`; the health check calls
  `wget http://localhost:8080/health` inside the container.
- **Demo panel says disabled** — the app is not running with `--spring.profiles.active=local-demo`.

<!-- EXPORT-CUT -->

## Exercises (presenter workflow)

The five talk exercises are generated from this baseline. Everything below the
`EXPORT-CUT` marker in this file, plus `exercises/`, `presenter/` and `tools/`, is never part
of an export; the export copies only an explicit allowlist.

```bash
tools/export-exercise.sh ex1 /tmp/ex1     # generation: availableOnly filter
tools/export-exercise.sh ex2 /tmp/ex2     # debugging: overbooking under concurrency
tools/export-evidence.sh  /tmp/ex2-evidence
tools/export-exercise.sh ex3 /tmp/ex3     # refactoring: extract BookingRepository interface
tools/export-exercise.sh ex4 /tmp/ex4     # review: per-attempt delivery keys
tools/export-exercise.sh ex5 /tmp/ex5     # devops: Compose health check
```

The destination must be new or empty. Each export contains the buildable workspace, a
neutral `README.md` (with the concurrency section stripped for ex2), `TASK.md`, review
material where applicable (`review/`), and `MANIFEST.sha256`. The sha256 of the manifest
is the workspace hash for the run ledger.

Presenter-only material: `presenter/RUNBOOK.md`, `presenter/notes/`, reference patches in
`presenter/reference-patches/`, acceptance checks in `presenter/acceptance/`, and
`presenter/tools/verify-exercises.sh`, which exports each exercise, asserts that the acceptance
check fails on the exercise state and passes after the reference patch.
