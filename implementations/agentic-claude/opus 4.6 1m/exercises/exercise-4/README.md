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

## Task

See [TASK.md](TASK.md) for this exercise's requirements.

The candidate change is in `candidate/change.diff`.
