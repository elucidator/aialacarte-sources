# Conference Planner

Session booking application for the "AI Coding à la Carte" conference talk. Attendees browse sessions, book a place, view bookings, and cancel them.

## Quick Start

### 1. Start PostgreSQL
```bash
docker compose up -d db
```

### 2. Run the application
```bash
SPRING_PROFILES_ACTIVE=local-demo ./gradlew bootRun
```

### 3. Open the UI
http://localhost:8080

### 4. Run tests
```bash
./gradlew test
```

## Docker Compose (Full Stack)
```bash
docker compose up --build -d
docker compose ps    # verify both containers are healthy
docker compose down -v  # stop and clean up
```

## API

### Sessions

`GET /api/sessions?page=0&size=20`

Returns paginated sessions ordered by `sort_order`. Response:
```json
{
  "items": [{"id": "...", "title": "...", "room": "...", "capacity": 30, "activeBookings": 22, "availablePlaces": 8}],
  "page": 0,
  "size": 20,
  "totalElements": 3
}
```
- `page`: zero-based, must be >= 0
- `size`: 1–100
- Invalid values return 400

### Bookings

`POST /api/bookings` — Create a booking
- Headers: `X-Demo-Attendee`, `Idempotency-Key`, `Content-Type: application/json`
- Body: `{"sessionId": "uuid"}`
- Returns 201 (new) or 200 (replay of same key)

`GET /api/bookings` — List active bookings
- Header: `X-Demo-Attendee`

`DELETE /api/bookings/{id}` — Cancel a booking
- Header: `X-Demo-Attendee`
- Returns 204 (cancelled or already cancelled for own booking)
- Returns 404 (not found or another attendee's booking)

### Error Codes

| Code                | HTTP | Description                                    |
|---------------------|------|------------------------------------------------|
| INVALID_PAGE        | 400  | Page parameter < 0                             |
| INVALID_SIZE        | 400  | Size not in 1–100                              |
| MISSING_ATTENDEE    | 400  | X-Demo-Attendee header missing                 |
| MISSING_IDEMPOTENCY_KEY | 400 | Idempotency-Key header missing              |
| MISSING_SESSION_ID  | 400  | sessionId not provided                         |
| SESSION_NOT_FOUND   | 404  | Unknown session ID                             |
| SESSION_FULL        | 409  | No available places                            |
| ALREADY_BOOKED      | 409  | Attendee already has active booking for session|
| KEY_CONFLICT        | 409  | Idempotency key used with different parameters |
| BOOKING_NOT_FOUND   | 404  | Booking not found or not owned by attendee     |

### Health

`GET /health` — Returns 200 when app and database are ready.

### Demo Panel (local-demo profile only)

- `POST /api/demo/reset` — Reset to seed data
- `GET /api/demo/activity` — View notification/gateway activity
- `POST /api/demo/dispatch` — Deliver pending confirmations
- `POST /api/demo/simulate-timeout?enabled=true|false` — Toggle timeout simulation

## Concurrency Model

Booking creation uses `SELECT ... FOR UPDATE` on the session row to serialize concurrent attempts. This ensures:

1. **Active bookings never exceed capacity** — the session row lock prevents two transactions from both reading the same count and inserting.
2. **Idempotency key uniqueness** — the `UNIQUE` constraint on `idempotency_key` prevents duplicate bookings from concurrent replays.
3. **Unique active booking per attendee/session** — the partial unique index `uq_active_booking_attendee_session` enforces this at the database level.

This design works with multiple app processes sharing the same PostgreSQL database.

## Notification Simulator

When a booking is accepted, a pending confirmation is created in the same transaction. The dispatcher delivers pending confirmations to a fake gateway:

- The gateway deduplicates on the `delivery_key` (stable: `confirm-{bookingId}`).
- A simulated timeout can occur after the gateway accepts but before the sender acknowledges.
- Retries use the same delivery key, so the gateway returns the existing acceptance.
- This guarantees at most one accepted confirmation per booking, regardless of retries.

**Note**: This guarantee depends on the fake gateway's deduplication contract. Real email delivery does not offer universal exactly-once semantics.

## Seed Data

After reset, the database contains:

| Session                        | Room   | Capacity | Booked | Available |
|-------------------------------|--------|----------|--------|-----------|
| Debugging Distributed Systems | Room A | 30       | 22     | 8         |
| Practical Kotlin              | Room B | 20       | 19     | 1         |
| Understanding Databases       | Room C | 40       | 40     | 0         |

Alice and Bob start with no bookings. Seed bookings use fictional attendees.

## Exercise Exports

```bash
./exercises/export.sh <1-5> <destination>
```

See the presenter runbook for exercise descriptions.
