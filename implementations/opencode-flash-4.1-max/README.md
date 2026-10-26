# Conference Planner ("AI Coding à la Carte")

A small, runnable conference booking application used for a 40-minute talk comparing
AI-assisted code generation, debugging, refactoring, code review and DevOps automation.

- Kotlin/JVM 21, Spring Boot, Gradle Kotlin DSL with a complete wrapper.
- PostgreSQL 16, straightforward SQL/JDBC with explicit transaction boundaries.
- Plain HTML/CSS/JavaScript served by the backend (no Node build, no frontend framework).
- Dockerfile and Docker Compose for Apple Silicon and ordinary Linux.
- Five isolated exercise exports plus presenter-only fixes, reproducers and notes under
  `presenter/`.

## Quick start (Docker Compose)

```bash
docker compose up --build -d
docker compose ps                 # wait for the app to become healthy
curl -fsS http://localhost:8080/health
open http://localhost:8080        # or a browser of your choice
```

Compose starts PostgreSQL (`postgres:16-alpine`), builds the app, enables the
`local-demo` profile and seeds the three sessions on first start.

Stop everything and delete the local database volume:

```bash
docker compose down --volumes
```

## Quick start (host JDK)

```bash
docker run --rm -d --name conference-db \
  -e POSTGRES_DB=conference -e POSTGRES_USER=conference -e POSTGRES_PASSWORD=conference \
  -p 5432:5432 postgres:16-alpine

./gradlew bootRun --args='--spring.profiles.active=local-demo'

# later
docker stop conference-db
```

Without `local-demo` the app still serves sessions and bookings, but the demo control
routes (`/api/demo/**`) do not exist (404).

## Tests

```bash
./gradlew test                       # all checks, requires Docker (Testcontainers)
./scripts/two-process-check.sh       # two JVMs, one database, bounded waits
```

`./gradlew test` starts one throwaway `postgres:16-alpine` container and runs API,
transaction, concurrency, notification-dedup, health and static-asset checks against it.
The two-process script builds the boot jar, starts a disposable Compose database on an
isolated project, launches two app processes on ports 18081/18082 and checks:

1. Alice and Bob racing for the last place in session 2 produce exactly one booking;
2. replaying one request key against both processes returns the same booking.

Presenter checks (reference patches and deterministic reproducers) live under
`presenter/` and are never part of an exercise export:

```bash
./presenter/acceptance/run-acceptance.sh all   # documents pre-patch failure, post-patch pass
./presenter/ui-check/run-ui-check.sh baseline # Playwright browser checks
./presenter/build-patches.sh                  # rebuild reference patches, verify they apply
```

## Demo identities

The UI has a selector for the **demo identities Alice and Bob**. It is labelled as a demo
identity, not authentication, and only exists for the talk. The selection is sent as the
`X-Demo-Attendee` header on every API request; the backend enforces all booking and
cancellation rules from that header.

## Local demo panel

The third UI tab is a local demo panel. Its endpoints mutate or inspect demo state and
therefore **require the explicit `local-demo` `Spring` profile**; outside the profile the
routes are absent. The panel can:

- reset all demo data to the deterministic seed;
- view confirmations (status, attempts, accepted deliveries) and demo activity;
- arm a one-shot simulated notification failure (the gateway records the message, then
  the acknowledgement is lost);
- dispatch pending confirmations manually;
- repeat the last booking request with the same `Idempotency-Key` to show replay.

## Seeding and reset

Seed data is deterministic:

| id | Session                       | Room   | Capacity | Active bookings |
|----|-------------------------------|--------|----------|-----------------|
| 1  | Debugging Distributed Systems | Room A | 30       | 22              |
| 2  | Practical Kotlin              | Room B | 20       | 19              |
| 3  | Understanding Databases       | Room C | 40       | 40              |

Booking ids are assigned `1..81` in stable order; the next booking is always id `82`
after a reset. Alice and Bob have no bookings. Seed bookings never create pending
notifications and are inserted while holding exclusive table locks, so a reset cannot
race an in-flight booking transaction.

Reset from the demo panel or:

```bash
curl -X POST http://localhost:8080/api/demo/reset
```

A reset restores the counts above and clears bookings, request keys, confirmations,
delivery attempts, gateway messages and demo activity.

## API contract

All responses are JSON. Errors use a stable shape:

```json
{ "code": "SESSION_FULL", "message": "Session 2 'Practical Kotlin' is full (20/20)" }
```

### `GET /api/sessions?page=0&size=20`

Returns `{ items, page, size, totalElements }`. `page` is zero-based (`>= 0`), `size` is
`1..100`; anything else is a 400. Items are ordered by session `id` ascending (the
documented stable order). Each item has `id`, `title`, `room`, `capacity`,
`activeBookings`, `availablePlaces` where `availablePlaces = capacity - activeBookings`.

The baseline intentionally does **not** implement `availableOnly`; adding it is
exercise 1.

### `POST /api/bookings`

Headers: `X-Demo-Attendee`, `Idempotency-Key`. Body: `{ "sessionId": 1 }`.

- `201` with the new booking.
- `200` with the original booking when a completed successful request is replayed
  (same attendee, same key, same payload).
- `409` when the session is full, when the attendee already has an active booking for
  the session, or when the key was used with a different payload.
- `404` for an unknown session, `400` for missing identity/key or malformed input.

A replayed request after cancellation returns the original (cancelled) booking with
`200` and never rebooks; a fresh key is required for a new booking.

### `GET /api/bookings`

Active bookings for `X-Demo-Attendee`, newest id first, including session title/room.

### `DELETE /api/bookings/{id}`

Cancels the attendee's booking and returns `204`. Repeating it returns `204` without
freeing another place. A missing booking or another attendee's booking returns `404`.

### `GET /health`

`200 {"status":"UP","database":"UP"}` when the app and required database are ready,
`503` otherwise. The Compose health check probes this endpoint with `curl`, which is
installed in the runtime image.

### Error codes

| Code                    | HTTP | Meaning                                            |
|-------------------------|------|----------------------------------------------------|
| `MISSING_ATTENDEE`      | 400  | `X-Demo-Attendee` missing or blank                 |
| `MISSING_IDEMPOTENCY_KEY` | 400 | `Idempotency-Key` missing or blank                |
| `INVALID_REQUEST`       | 400  | body missing/malformed or required field absent    |
| `INVALID_SESSION_ID`    | 400  | `sessionId` not a positive number                  |
| `INVALID_PARAMETER`     | 400  | query parameter has an invalid type/value          |
| `INVALID_PAGE`          | 400  | `page < 0`                                         |
| `INVALID_SIZE`          | 400  | `size` outside `1..100`                            |
| `INVALID_LIMIT`         | 400  | demo `limit` outside `1..200`                      |
| `SESSION_NOT_FOUND`     | 404  | unknown session id                                 |
| `BOOKING_NOT_FOUND`     | 404  | missing booking or not owned by the attendee       |
| `NOT_FOUND`             | 404  | unknown route                                      |
| `SESSION_FULL`          | 409  | active bookings would exceed capacity              |
| `DUPLICATE_BOOKING`     | 409  | attendee already active in that session            |
| `IDEMPOTENCY_KEY_REUSED`| 409  | same key, different payload                        |
| `INTERNAL_ERROR`        | 500  | unexpected server error                            |

## Transaction and concurrency design

- **Booking** (`BookingService.book`, one `TransactionTemplate` transaction):
  1. claim the attendee-scoped request key with `INSERT ... ON CONFLICT DO NOTHING`;
  2. `SELECT ... FOR UPDATE` the session row (serialises last-place decisions across
     processes sharing the database);
  3. reject a second active booking for the same attendee/session;
  4. count active bookings and reject when full;
  5. insert the booking and its `PENDING` confirmation together;
  6. attach the booking to the request key and log demo activity.
  Concurrent replays of one key block on the primary key, then read the committed
  original booking and return it with `200`.
- **Cancellation** (`BookingService.cancel`): lock the booking row `FOR UPDATE`, verify
  ownership, mark `CANCELLED` once. Repeats see `CANCELLED` and return `204`.
  Cancellation only ever decreases the active count, so it cannot overbook.
- **Reset**: `LOCK TABLE sessions/bookings IN EXCLUSIVE MODE` around delete + reseed.
- **Database backstops**: a partial unique index on `(session_id, attendee) WHERE
  status = 'ACTIVE'` and the notifications' unique booking reference hold even if an
  application-level check is bypassed.
- The invariants are verified with one JVM (`ConcurrencyIT`) and with two processes
  sharing one database (`scripts/two-process-check.sh`).

## Notification simulator

An accepted booking stores a `PENDING` confirmation in the same transaction, with a
stable delivery key `booking:<bookingId>`. Dispatch is manual and bounded: one pass
over pending confirmations, one gateway call per confirmation, no background retries and
no backoff.

The fake gateway owns a **separate connection pool** against the same database, so its
`INSERT ... ON CONFLICT (delivery_key) DO NOTHING` is committed independently of the
caller. Armed via the demo panel, it accepts and durably records a message, then throws a
simulated timeout before acknowledging. A failed attempt stays `PENDING`; the next
dispatch reuses the same key, the gateway reports a duplicate, and the confirmation is
acknowledged. Repeated attempts therefore produce exactly one accepted message, and a
crash after acceptance recovers the same way.

Attempts (`delivery_attempts`), accepted deliveries (`gateway_messages`) and
acknowledgements (`notifications.status`) are visible separately in the demo panel.

This is a local simulation of a deduplicating gateway contract, not a claim of
exactly-once real email delivery. Real providers need the same stable idempotency key
plus their own durable contract.

## Docker notes

- `Dockerfile`: multi-stage build on `eclipse-temurin:21-jdk-jammy` /
  `eclipse-temurin:21-jre-jammy`; `curl` is installed for the health check. All base
  images (including `postgres:16-alpine`) are multi-architecture, so no architecture-
  specific binaries are required.
- `docker compose up --build` runs the full stack; `DB_PORT` and `APP_PORT` can override
  the host ports. Normal demos work offline once dependencies and images are cached.
- Exercise 5 deliberately ships a wrong health-check path in its own Compose project for
  a DevOps debugging session.

## Exercise exports

```bash
scripts/export-exercise.sh <1|2|3|4|5> <new-or-empty-destination>
```

The script refuses non-empty destinations and copies an explicit allowlist: sources,
tests where appropriate, Gradle wrapper, configuration, a neutral README and the task
brief, plus `MANIFEST.sha256` (verify with `shasum -a 256 -c MANIFEST.sha256` inside the
destination). Exercise 4 additionally receives the base/candidate diff and the gateway
contract. Presenter material under `presenter/` is never copied. Reference patches in
`presenter/reference-fixes/` apply with `patch -p1` from an export root and are verified
by `presenter/build-patches.sh` and `presenter/acceptance/run-acceptance.sh`.

## Troubleshooting

- **Docker build cannot reach the network**: the first build downloads Gradle
  dependencies and base images; retry when online. Afterwards everything is cached.
- **`./gradlew test` fails starting PostgreSQL**: ensure the Docker daemon is running.
- **Port already in use**: `APP_PORT=8081 DB_PORT=55432 docker compose up --build -d`.
- **Demo routes return 404**: start with `--spring.profiles.active=local-demo`.
- **Compose app stays unhealthy**: `docker compose logs app`; check the database
  container is healthy and `/health` returns 200 from inside the app container.
- **Reset needed after a messy demo**: `curl -X POST http://localhost:8080/api/demo/reset`.

Pinned dependency versions, verified tooling and resolved versions are recorded in
[BUILD-ENVIRONMENT.md](BUILD-ENVIRONMENT.md).
