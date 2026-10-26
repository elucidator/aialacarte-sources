# Build Environment

## Required Local Software

| Tool              | Version   | Notes                              |
|-------------------|-----------|------------------------------------|
| JDK               | 21        | Any distribution (Temurin, GraalVM)|
| Docker            | 24+       | Docker Desktop or Docker Engine    |
| Docker Compose    | 2.20+     | Included with Docker Desktop       |

## Pinned Dependency Versions

| Dependency                          | Version  |
|-------------------------------------|----------|
| Gradle                              | 8.10.2   |
| Kotlin                              | 2.1.20   |
| Spring Boot                         | 3.4.5    |
| Spring Dependency Management Plugin | 1.1.7    |
| Flyway                              | (managed by Spring Boot 3.4.5) |
| PostgreSQL Driver                   | (managed by Spring Boot 3.4.5) |
| Jackson Kotlin Module               | (managed by Spring Boot 3.4.5) |
| Testcontainers                      | 1.20.3   |

## Docker Images

| Image              | Tag        | Notes                          |
|--------------------|------------|--------------------------------|
| postgres           | 16-alpine  | Database                       |
| eclipse-temurin    | 21-jdk     | Build stage                    |
| eclipse-temurin    | 21-jre     | Runtime stage                  |

## Maven Repository

The build resolves from Maven Central via `mavenCentral()` (configured in `settings.gradle.kts` and `build.gradle.kts`). During the run, a private Maven mirror was used because Central was unreachable; its URL was removed for publication.

## Architecture Support

- Docker images use multi-architecture tags supporting both `linux/amd64` and `linux/arm64` (Apple Silicon).
- No architecture-specific binaries are required.
