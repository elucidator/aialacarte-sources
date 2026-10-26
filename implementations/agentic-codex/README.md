# Conference Planner

A local conference demo for “AI Coding à la Carte”: browse sessions, book, view your bookings and cancel. Kotlin/JVM 21, Spring Boot, explicit JDBC transactions and PostgreSQL; plain browser files, no Node build. All attendees and notifications are fictional. Demo identity is not authentication.

## Start with Docker

Install Docker with Compose v2. No architecture override is needed on Apple Silicon or Linux. From this directory:

```sh
APP_PORT=18080 DB_PORT=54329 docker compose -p conference-baseline up -d --build --wait
curl --fail http://127.0.0.1:18080/health
```

Open http://127.0.0.1:18080 . Select Alice or Bob. Visit the separate **Local demo panel** link to reset or dispatch notifications. Port 8080 is deliberately avoided in these examples. If either port is occupied, select unused values and keep the same values for subsequent Compose commands. Never stop an unrelated container to free a port.

Stop this project (retains data):

```sh
APP_PORT=18080 DB_PORT=54329 docker compose -p conference-baseline stop
```

Remove only this project's disposable containers and data when finished:

```sh
APP_PORT=18080 DB_PORT=54329 docker compose -p conference-baseline down -v
```

Never run global Docker prune or target another project's containers. Use a unique Compose project name for every exercise.

## Host JDK workflow

JDK 21 and Python 3 are required for verification. Gradle is supplied by the complete wrapper. Dependencies are downloaded from Maven Central; internet access is needed on the first build. See BUILD-ENVIRONMENT.md for versions and repository setup. The implementation run itself used a private Maven mirror supplied by the operator (URL removed for publication).

```sh
docker compose -p conference-baseline up -d db --wait
./gradlew bootJar
PORT=18080 SPRING_PROFILES_ACTIVE=local-demo java -jar build/libs/conference-planner.jar
```

If you choose a different database port, set `DB_URL=jdbc:postgresql://127.0.0.1:YOUR_PORT/conference` on Java and test commands. Defaults: DB port 54329, database/user/password `conference`. Host Java binds to loopback; Docker sets `SERVER_ADDRESS=0.0.0.0` inside the container and publishes on loopback.

Stop host Java with Ctrl+C. To run without demo endpoints, omit `SPRING_PROFILES_ACTIVE`. `/api/demo/*` then returns 404, including inspection. `/demo.html` can still display that controls are disabled. The app needs its database at startup; Flyway migrates and seeds only once. Restarting does not reset data or retry confirmations automatically.

## Reset and notification demo

```sh
curl --fail -X POST http://127.0.0.1:18080/api/demo/reset
curl --fail -H 'Content-Type: application/json' -H 'X-Demo-Attendee: alice' -H 'Idempotency-Key: example-1' -d '{"sessionId":2}' http://127.0.0.1:18080/api/bookings
curl --fail -H 'Content-Type: application/json' -d '{"mode":"lost-ack"}' http://127.0.0.1:18080/api/demo/mode
curl --fail -X POST http://127.0.0.1:18080/api/demo/dispatch
curl --fail http://127.0.0.1:18080/api/demo/activity
curl --fail -H 'Content-Type: application/json' -d '{"mode":"normal"}' http://127.0.0.1:18080/api/demo/mode
curl --fail -X POST http://127.0.0.1:18080/api/demo/dispatch
```

Expect two attempts, one gateway acceptance and one sender acknowledgement. `fail` rejects before acceptance; `lost-ack` records acceptance in the gateway's **REQUIRES_NEW** transaction and throws only after that transaction commits. The caller cannot roll it back. Each dispatch attempts at most 20 pending confirmations once, without backoff; failures remain pending for a later manual dispatch. A persisted delivery key is reused after restart. Concurrent dispatchers claim different rows using `FOR UPDATE SKIP LOCKED`.

The guarantee relies on this fake gateway deduplicating the supplied key durably. It does not promise universal exactly-once real email delivery. Cancelling a booking does not erase its original confirmation; confirmation concerns the accepted request. Seed bookings have no confirmations.

Reset takes an exclusive database advisory lock. Booking, cancellation, mode changes, activity inspection and dispatch take the matching shared lock, so reset waits for in-flight mutations across processes. It restores session IDs 1/2/3 with 22/19/40 active fictional bookings, clears request keys, confirmations, attempts, acceptances and activity, and restores normal gateway mode. Alice and Bob have no seed bookings. Seed IDs use `seed-SESSION-N`; user booking IDs are UUIDs.

## API

Session ordering is ascending numeric session ID, before pagination. `GET /api/sessions?page=0&size=20` returns `{items,page,size,totalElements}`. Pages are zero-based, size is 1..100. Negative/non-integer pages and invalid sizes return 400. Empty pages retain the correct total. Availability counts only active bookings. There is no baseline availability filter.

`POST /api/bookings` needs JSON `{ "sessionId": 2 }`, `X-Demo-Attendee` and `Idempotency-Key`. Returns the booking (id, sessionId, title, attendee, status), 201 initially and 200 for successful replay. Keys are attendee scoped; a key cannot be reused with a different session. Failed requests do not reserve a key. A replay after cancellation returns the same booking with status CANCELLED and does not rebook. A new booking requires a fresh key.

`GET /api/bookings` requires identity and lists that attendee's active bookings. `DELETE /api/bookings/{id}` requires identity and returns 204, including repeat cancellation of an owned booking; missing and foreign bookings return 404. Headers represent local fictional identities, not trustworthy production identity claims. Identity format: lowercase letter followed by up to 63 lowercase letters/digits/hyphens. Key format: 1..128 letters/digits/colons/underscores/hyphens.

Errors use `{ "code": "SESSION_FULL", "message": "This session has no available places." }`.

| Code | Status | Meaning |
|---|---|---|
| INVALID_INPUT | 400 | Missing/invalid identity, key, JSON or query value |
| NOT_FOUND | 404 | Unknown session, missing/foreign booking or disabled resource |
| SESSION_FULL | 409 | No place left |
| ALREADY_BOOKED | 409 | Another request for an active attendee/session booking |
| KEY_REUSED | 409 | Same attendee/key with a different session |
| CONFLICT | 409 | Database uniqueness/consistency conflict |
| INTERNAL_ERROR | 500 | Unexpected failure; details stay in server logs |

`GET /health` queries a required table and returns 200/ready or 503/unavailable. If startup cannot migrate/connect, the process exits and the endpoint is unreachable, also non-success. Docker uses the installed curl binary against `/health`. Demo-only endpoints: `POST /api/demo/reset`, `POST /api/demo/mode` with `normal|fail|lost-ack`, `POST /api/demo/dispatch`, `GET /api/demo/activity`. Activity returns attempts, acceptances and ordered events including acknowledgements; displays are limited to 100 recent rows.

## Transaction boundaries

The concrete `PostgresBookingRepository` owns explicit `TransactionTemplate` boundaries. Book: shared reset lock → attendee/key advisory lock → replay lookup → session row `FOR UPDATE` → duplicate/capacity check → booking + request record + confirmation + activity → commit. PostgreSQL READ COMMITTED gives a fresh count after acquiring the session lock. The partial unique index additionally prevents duplicate active bookings. Database locks coordinate separate JVMs; no local mutex carries correctness.

Cancel: shared reset lock → owned booking lookup → session row lock → conditional ACTIVE-to-CANCELLED update → commit. Conditional update changes the count once. The same session lock orders bookings and cancellations. Replay reads current original booking state and never inserts. Listing uses one SQL statement so items and total share a snapshot. Dispatcher owns a sender transaction and outbox row locks; the gateway owns an independent committed transaction. Reset cannot interleave with this operation.

## Checks

Only run checks against a disposable database: they reset application data. Stop this project's app first if a person is actively demoing. Run suites **sequentially**, because they share seed data.

```sh
docker compose -p conference-baseline up -d db --wait
./gradlew test bootJar
python3 scripts/process-check.py
```

JUnit covers listing/pagination, input, booking, capacity, duplicates, replay, cancellation, notification retry/deduplication and healthy DB readiness against PostgreSQL. The process check starts two real JVMs on unused loopback ports, checks 12 last-place races, concurrent replay/cancel/dispatch, restart-safe delivery and disabled demo endpoints. It terminates only its own JVM children. Requests, concurrency coordination and readiness have deadlines. See completion report for database-outage and Compose health checks actually performed.

Browser checks need a disposable Python environment and installed Chrome (or set `BROWSER_CHANNEL=chromium` and install Playwright's Chromium):

```sh
python3 -m venv /tmp/conference-browser
/tmp/conference-browser/bin/pip install --index-url https://pypi.org/simple playwright==1.55.0
/tmp/conference-browser/bin/python scripts/browser-check.py
```

Screenshots go to `verification-output/screenshots`. Browser checks assert visible list, booking, identity switching, cancellation, loading/error/success states, a lost booking response followed by cancellation and a fresh booking, and notification activity. These are separate from API checks.

After warming wrapper/dependencies, `./gradlew --offline test bootJar` works without downloads. Prebuild the app image and pull the database image before the talk; run Compose without `--build` on stage.

## Exercises and presenter files

After verifying the baseline, create an exercise in a new or empty directory:

```sh
python3 presenter/export.py 1 /tmp/conference-generation
python3 presenter/export.py 2 /tmp/conference-debugging
python3 presenter/export.py 3 /tmp/conference-refactoring
python3 presenter/export.py 4 /tmp/conference-review
python3 presenter/export.py 5 /tmp/conference-devops
```

Exports use an explicit file allowlist, include a task brief and SHA-256 manifest, and refuse non-empty destinations. Each workspace has its own copy of source/configuration/wrapper. Presenter files, generation code, fixes, private checks, build caches and Git history are excluded. Reference patches and validation commands live only in `presenter/`; read `presenter/RUNBOOK.md` before the talk. Exercise 2 evidence is supplied separately from its starting workspace.

## Troubleshooting

Port occupied: choose another `APP_PORT`/`DB_PORT`, do not kill existing services. Check only your project's resources with `docker compose -p YOUR_PROJECT ps` and `logs`. A host-JDK app cannot use hostname `db`; use the published loopback database port. Database unavailable: verify Docker is running and `pg_isready` is healthy. Health fails despite a running Java process: inspect `/health` and the configured probe URL, using curl inside the runtime container. Never repair exercises by changing the baseline.

Maven Central unreachable: dependency downloads require access to Maven Central. On networks that reset connections to Central, point the repository in both Gradle files at a reachable Maven mirror; the locked versions remain the same. Wrapper distributions come from official Gradle services. Missing browser support: perform the manual checks in the completion report; do not call API coverage browser verification.
