# Conference Planner

A session booking application for conference attendees.

## Setup

### Prerequisites
- JDK 21
- Docker (for PostgreSQL)

### Start PostgreSQL
```bash
docker compose up -d db
```

### Run the application
```bash
SPRING_PROFILES_ACTIVE=local-demo ./gradlew bootRun
```

Open http://localhost:8080 in a browser.

### Run tests
```bash
./gradlew test
```

Tests use Testcontainers and require Docker to be running.

### Reset seed data
POST http://localhost:8080/api/demo/reset (or use the Demo Panel in the UI).

## Task

See [TASK.md](TASK.md) for this exercise's requirements.
