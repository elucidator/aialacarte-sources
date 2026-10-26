# Exercise 5: DevOps — Fix Container Health Check

## Symptom

After running `docker compose up --build`, the `app` container starts but its health check fails repeatedly. Docker reports the container as unhealthy. The application itself is running and serving requests on port 8080.

## Your Task

1. Inspect the running containers: `docker compose ps`
2. Check the health check logs: `docker inspect <container> --format '{{json .State.Health}}'`
3. Identify why the health check is failing.
4. Fix the issue in `docker-compose.yml`.
5. Validate the fix: rebuild and verify the container becomes healthy.

## Constraints

- Use an isolated Compose project name to avoid affecting other environments:
  ```bash
  docker compose -p exercise5 up --build -d
  ```
- Clean up when done:
  ```bash
  docker compose -p exercise5 down -v
  ```
- Do not change the application code. The fix is in the Compose configuration only.
- The health check must verify that the application can serve requests and connect to its database.
