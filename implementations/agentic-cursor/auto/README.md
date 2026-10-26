# Conference Planner

Small Kotlin/Spring Boot app for the talk **AI Coding à la Carte**.
Attendees browse sessions, book a place, view bookings, and cancel them.
A local demo panel drives seed reset, activity inspection, and confirmation dispatch.

## Requirements

- JDK 21
- Docker (PostgreSQL 16 and optional full Compose stack)
- See [BUILD-ENVIRONMENT.md](BUILD-ENVIRONMENT.md) for pinned versions

## Quick start (host JDK + Compose DB)

```bash
docker compose -f docker-compose.db.yml up -d
./gradlew bootRun --args='--spring.profiles.active=local-demo'
```

If Maven Central TLS fails on your network, use the mirror init script:

```bash
./gradlew --init-script init-mirror.gradle.kts bootRun --args='--spring.profiles.active=local-demo'
```

Open http://localhost:8080

### Full stack (app + DB)

```bash
docker compose up --build -d
docker compose ps
curl -fsS http://localhost:8080/health
```

### Stop

```bash
# host-JDK workflow
docker compose -f docker-compose.db.yml down

# full stack
docker compose down
```

## Demo identity

The UI **Demo identity** selector chooses Alice (`X-Demo-Attendee: 1`) or Bob (`2`).
This is not authentication. The backend enforces booking and cancellation rules for the header value.

## Seed data

Stable session order (`sort_order`, then `id`):

| # | Title | Room | Capacity | Active bookings |
|---|-------|------|----------|-----------------|
| 1 | Debugging Distributed Systems | Room A | 30 | 22 |
| 2 | Practical Kotlin | Room B | 20 | 19 |
| 3 | Understanding Databases | Room C | 40 | 40 (full) |

Alice and Bob start with no bookings. Reset (demo panel / `POST /api/demo/reset` with `local-demo`) restores these counts and clears requests, outbox, gateway rows, and activity. Seed bookings do not create pending notifications.

## API

### Sessions

`GET /api/sessions?page=0&size=20`

Returns `{ items, page, size, totalElements }`. Item fields: `id`, `title`, `room`, `capacity`, `activeBookings`, `availablePlaces`.

- Page is zero-based; `size` is 1..100; invalid values → `400`
- Ordering: `sort_order ASC`, `id ASC`
- Baseline does **not** implement `availableOnly` (exercise 1)

### Bookings

- `POST /api/bookings` JSON `{ "sessionId": N }` with headers `X-Demo-Attendee`, `Idempotency-Key`
  - `201` new booking; `200` replay of a completed successful request key
- `GET /api/bookings` — active bookings for the demo attendee
- `DELETE /api/bookings/{id}` — cancel; `204` (idempotent for an already-cancelled owned booking). Missing/other attendee → `404`

Conflicts (`409`):

- `SESSION_FULL`
- `DUPLICATE_ACTIVE_BOOKING`
- `IDEMPOTENCY_KEY_REUSED`

Other common codes: `MISSING_ATTENDEE`, `MISSING_IDEMPOTENCY_KEY`, `MISSING_SESSION_ID`, `INVALID_*`, `SESSION_NOT_FOUND`, `BOOKING_NOT_FOUND`, `MALFORMED_JSON`

Error JSON shape: `{ "code": "...", "message": "..." }`

### Health

`GET /health` → `200` `{ "status": "UP" }` when the DB answers; otherwise non-success (e.g. `503`). Compose healthcheck uses `curl` against this path (installed in the runtime image).

### Local demo panel (`local-demo` profile only)

- `POST /api/demo/reset`
- `GET /api/demo/status`
- `POST /api/demo/simulate-failure` `{ "enabled": true|false }`
- `POST /api/demo/dispatch`

Disabled outside `local-demo` (status returns 404).

## Transaction boundaries

`BookingService` uses `TransactionTemplate` (not process-local locks):

**createBooking** (one TX):

1. Load prior idempotency row for `(attendee, key)`
2. `pg_advisory_xact_lock(sessionId)` + `SELECT … FOR UPDATE` on the session, then recount active bookings
3. Reject if full or duplicate active booking
4. Insert booking + request key + pending confirmation outbox together

**cancelBooking** (one TX): transition `ACTIVE` → `CANCELLED` once.

Capacity coordination works with two app processes sharing one PostgreSQL database.

Replay after cancellation returns the original booking (`CANCELLED`) and does not rebook. A new booking needs a fresh idempotency key.

## Notification simulator

- Pending confirmation is written in the same TX as the accepted booking
- Manual dispatch from the demo panel delivers to a fake gateway
- Gateway acceptance is committed in a **separate** transaction before any ack delay; a simulated timeout cannot roll back acceptance
- Delivery key is stable: `booking-{id}` on every attempt (including after restart)
- Deduplication is the gateway’s contract — not universal exactly-once email

## Tests

```bash
./gradlew --init-script init-mirror.gradle.kts test

# Optional: two JVM processes sharing one DB (needs bootJar)
./gradlew --init-script init-mirror.gradle.kts bootJar
RUN_TWO_PROCESS_IT=true ./gradlew --init-script init-mirror.gradle.kts test --tests 'com.conference.planner.TwoProcessCapacityIT'
```

Tests use Testcontainers PostgreSQL 16 (not an in-memory DB).

## Exercise exports

```bash
./scripts/export-exercise.sh 1 /tmp/cp-ex1
./scripts/export-exercise.sh 2 /tmp/cp-ex2
./scripts/export-exercise.sh 3 /tmp/cp-ex3
./scripts/export-exercise.sh 4 /tmp/cp-ex4
./scripts/export-exercise.sh 5 /tmp/cp-ex5
```

Refuses a non-empty destination. Writes `EXERCISE-MANIFEST.sha256`. Presenter notes and reference fixes stay under `presenter/` and are not copied.

## Troubleshooting

| Symptom | Check |
|---------|--------|
| App unhealthy in Compose | `curl -v http://localhost:8080/health`; DB `pg_isready`; logs `docker compose logs app` |
| Cannot download dependencies | Use `--init-script init-mirror.gradle.kts` (Huawei/Aliyun mirrors) |
| Port 5432/8080 busy | Stop other stacks or change published ports |
| Demo panel missing | Ensure `spring.profiles.active=local-demo` |
| Reset didn’t restore counts | Call reset only via demo API; confirm seed attendees still present |
