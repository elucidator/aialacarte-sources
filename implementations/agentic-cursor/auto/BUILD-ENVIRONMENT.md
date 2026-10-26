# Build environment

Pinned versions for Conference Planner (resolved 2026-09-29).

## Required local software

| Tool | Version used / required |
|------|-------------------------|
| JDK | 21 (Oracle GraalVM 21.0.10 verified in this environment) |
| Docker | 28.3.3 (Engine API 1.51) |
| Docker Compose | Compose V2 via `docker compose` |
| OS arch | arm64 (Apple Silicon) and amd64 Linux both supported; images are multi-arch (`postgres:16-alpine`, `eclipse-temurin:21-*-jammy`) |

No architecture-specific native binaries are required beyond the JVM and Docker images.

## Pinned project versions

| Component | Version |
|-----------|---------|
| Gradle Wrapper | 8.13 |
| Spring Boot | 3.4.5 |
| Spring Dependency Management plugin | 1.1.7 |
| Kotlin | 2.1.20 |
| Flyway | 10.20.1 (via Boot BOM) |
| PostgreSQL JDBC | via Boot BOM (`org.postgresql:postgresql`) |
| HikariCP | 5.1.0 (via Boot BOM) |
| Jackson | 2.18.3 (via Boot BOM) |
| Tomcat embed | 10.1.40 (via Boot BOM) |
| Micrometer | 1.14.6 (via Boot BOM) |
| Testcontainers | 1.20.6 (via Boot BOM / spring-boot-testcontainers) |
| PostgreSQL server image | `postgres:16-alpine` |
| App build image | `eclipse-temurin:21-jdk-jammy` |
| App runtime image | `eclipse-temurin:21-jre-jammy` (+ `curl` for healthcheck) |

## Repository mirrors

Default Gradle repositories include Maven Central. This environment intermittently resets TLS to `repo.maven.apache.org` / `repo1.maven.org`. The repo includes `init-mirror.gradle.kts` preferring:

1. `https://repo.huaweicloud.com/repository/maven/`
2. `https://maven.aliyun.com/repository/public`
3. Maven Central / Gradle Plugin Portal

Use:

```bash
./gradlew --init-script init-mirror.gradle.kts <task>
```

## Network notes

- First dependency and image downloads need network access.
- After caches are warm, demos should run locally without internet.
- Do not publish, push, or deploy externally for this talk kit.
