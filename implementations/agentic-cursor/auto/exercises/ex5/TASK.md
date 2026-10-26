# Exercise 5 — DevOps: Compose health check

## Goal

The application process starts, but Docker Compose reports the app service as
unhealthy. Inspect, correct, validate, and demonstrate recovery locally.

## Constraints

- Use the Compose project name defined in this workspace's `docker-compose.yml`
  (`conference-planner-ex5`) so recovery only affects this exercise's containers
  and volumes.
- Work entirely on the local machine. No remote or production deployment.
- Prefer fixing the health check configuration rather than disabling health checks.

## Done when

- `docker compose ps` shows the app as healthy.
- `GET /health` returns 200 while the database is up.
- You can demonstrate stop/start recovery for this project only.
