# Ex5 – DevOps: Compose health check (PRESENTER-ONLY)

**The fault.** `docker-compose.yml` in the export checks `http://localhost:8080/actuator/health`.
Actuator is not on the classpath, so the path is 404, `wget -q` exits non-zero, and the app
container flips to `unhealthy` after `start_period 15s + 12 × 5s`; `up --wait` fails.
The application itself is fine (logs look normal). Correct path: `/health`.

**Isolation.** Project name `conference-planner-ex5`, ports 8085/5435, image
`conference-planner-ex5-app:local`, own volume; `docker compose down -v` removes only those.

**Things to watch:** solutions that delete the health check instead of fixing it; changing
the check to `pg_isready` or to a TCP probe (loses the "DB ready" semantics); adding actuator
as a dependency (works, but changes the app for an ops problem); using `curl`, which is not in
the JRE Alpine image.

**Reference fix**: `reference-patches/ex5.patch`. **Acceptance**: in `verify-exercises.sh`,
`up --wait` must fail on the export and succeed after the patch, and `/health` must answer UP.
