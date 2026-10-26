# Exercise 5 – DevOps

The Docker Compose stack in this workspace (project name `conference-planner-ex5`, app on
host port 8085, database on host port 5435) is reported as broken: `docker compose up`
starts both containers, the application logs look normal, but the stack never becomes
healthy and `docker compose up --wait` eventually fails.

## Task

1. Reproduce the problem locally:
   ```bash
   ./gradlew bootJar
   docker compose up -d --build --wait
   docker compose ps
   ```
2. Inspect the containers and find the cause.
3. Correct it, validate the correction, and demonstrate recovery: from the broken state to a
   healthy stack that answers `curl -s http://localhost:8085/health`.
4. Leave the machine clean: `docker compose down -v` removes only this exercise's containers,
   network and volume.

## Constraints

- Work only inside this workspace and its Compose project. Do not touch other Compose projects,
  images or volumes on the machine.
- No remote or production environment is involved.
