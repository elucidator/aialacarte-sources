# Conference Planner

A small Kotlin/Spring Boot application for browsing conference sessions and booking
places. Plain HTML/CSS/JavaScript is served by the backend; PostgreSQL stores all
state. See `TASK.md` for the current assignment.

## Prerequisites

- JDK 21 (`java -version`)
- Docker with Compose (for PostgreSQL and for the Docker workflow)
- No Node.js or frontend build is required

## Run with Docker Compose

```bash
docker compose up --build -d
docker compose ps
curl -fsS http://localhost:8080/health
```

Open <http://localhost:8080>. The Compose setup starts PostgreSQL and the app, seeds
the three demo sessions on first start and enables the local demo panel.

Stop and remove the local data:

```bash
docker compose down --volumes
```

## Run on a host JDK

Start a disposable PostgreSQL first:

```bash
docker run --rm -d --name conference-db \
  -e POSTGRES_DB=conference -e POSTGRES_USER=conference -e POSTGRES_PASSWORD=conference \
  -p 5432:5432 postgres:16-alpine
```

Then:

```bash
./gradlew bootRun --args='--spring.profiles.active=local-demo'
```

The app is then on <http://localhost:8080>.

## Tests

```bash
./gradlew test
```

Integration tests use Testcontainers, so Docker must be running. They exercise the API
against a real PostgreSQL 16 database, including concurrent booking scenarios.

## Demo identities and demo panel

The UI has a *demo identity* selector (Alice / Bob). This is a local demo aid, not
authentication. Requests send the identity in the `X-Demo-Attendee` header.

The local demo panel (reset, activity, failure simulation, dispatch) is only available
while the `local-demo` Spring profile is active. Without that profile the `/api/demo/**`
routes do not exist.

## Reset

From the demo panel (`Reset demo data`) or:

```bash
curl -X POST http://localhost:8080/api/demo/reset
```

A reset restores the deterministic seed (three sessions with 22/19/40 active bookings)
and clears bookings, request keys, confirmations and demo activity made during a demo.

## Stop

```bash
docker compose down --volumes   # Docker workflow
# Ctrl-C for bootRun, then stop the database container
```

## Troubleshooting

- `./gradlew test` fails to start PostgreSQL: make sure Docker Desktop/daemon is running
  and that Testcontainers can pull `postgres:16-alpine`.
- Port 8080 or 5432 already in use: override with `APP_PORT=... DB_PORT=... docker compose up`.
- Demo panel endpoints return 404: start with `--spring.profiles.active=local-demo`
  (the Compose setup already does).
- `/health` returns 503: the database is not reachable; check `docker compose ps`.

## API summary

- `GET /api/sessions?page=0&size=20` - page of sessions ordered by id, with
  `capacity`, `activeBookings` and `availablePlaces`.
- `POST /api/bookings` - headers `X-Demo-Attendee` and `Idempotency-Key`, body
  `{"sessionId": 1}`; 201 on creation, 200 on replay of a completed request.
- `GET /api/bookings` - active bookings for `X-Demo-Attendee`.
- `DELETE /api/bookings/{id}` - cancels an owned booking; 204 (idempotent).
- `GET /health` - 200 when app and database are ready, 503 otherwise.

Errors use `{"code": "MACHINE_CODE", "message": "human readable"}`; see the baseline
project documentation for the full code list.
