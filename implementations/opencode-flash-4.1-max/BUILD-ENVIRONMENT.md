# Build environment and resolved versions

This file records the pinned toolchain and the dependency versions actually resolved by
the build on the verification machine. The bundled Gradle wrapper is the only build tool
needed; no local Gradle installation is required.

## Required local software

| Software        | Version used for verification           | Notes                                                        |
|-----------------|-----------------------------------------|--------------------------------------------------------------|
| JDK             | 21 (Oracle GraalVM 21.0.10+8.1, LTS)    | Toolchain fixed to Java 21 in `build.gradle.kts`             |
| Gradle          | wrapper 8.14.3 (distribution `-bin`)    | Downloaded by `gradlew`; nothing else needed                 |
| Docker Engine   | 28.0.4 (Docker Desktop 4.40.0)          | Needed for Compose and Testcontainers                        |
| Docker Compose  | v2.34.0                                 | `docker compose` v2 syntax                                   |
| OS              | macOS 26.6.2 (Apple Silicon, arm64)     | Linux amd64 expected to work; all images are multi-arch      |
| Node.js (only for presenter UI checks) | v26.3.1 with Playwright 1.63.0 | Exported workspaces never need Node |

## Pinned build plugins

| Plugin                               | Version | Source of truth |
|--------------------------------------|---------|-----------------|
| `org.jetbrains.kotlin.jvm`           | 2.2.20  | `build.gradle.kts` |
| `org.jetbrains.kotlin.plugin.spring` | 2.2.20  | `build.gradle.kts` |
| `org.springframework.boot`           | 3.5.16  | `build.gradle.kts` (latest stable 3.5.x line) |
| `io.spring.dependency-management`    | 1.1.7   | `build.gradle.kts` |

Library versions are pinned through the Spring Boot 3.5.16 dependency-management BOM,
which is the officially supported compatible set for that Spring Boot release. Kotlin
plugin 2.2.20 aligns the Kotlin standard library; Gradle 8.14.3 is within the versions
supported by Spring Boot 3.5.x and the Kotlin Gradle plugin.

## Resolved runtime versions (measured)

Obtained with `./gradlew dependencies --configuration runtimeClasspath` on 2026-09-29:

| Dependency                         | Resolved |
|------------------------------------|----------|
| `org.springframework.boot:spring-boot` | 3.5.16 |
| `org.springframework:spring-core` / `spring-jdbc` / `spring-tx` / `spring-webmvc` | 6.2.19 |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.55 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.21.4 |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | 2.21.4 |
| `org.jetbrains.kotlin:kotlin-stdlib` / `kotlin-reflect` | 2.2.20 |
| `com.zaxxer:HikariCP` | 6.3.3 |
| `org.flywaydb:flyway-core` / `flyway-database-postgresql` | 11.7.2 |
| `org.postgresql:postgresql` | 42.7.11 |
| `org.slf4j:slf4j-api` | 2.0.18 |
| `ch.qos.logback:logback-classic` | 1.5.34 |
| `org.yaml:snakeyaml` | 2.4 |

## Resolved test versions (measured)

Obtained with `./gradlew dependencies --configuration testRuntimeClasspath`:

| Dependency                       | Resolved |
|----------------------------------|----------|
| `org.junit.jupiter:junit-jupiter` | 5.12.2 |
| `org.junit.platform:junit-platform-launcher` | 1.12.2 |
| `org.assertj:assertj-core`       | 3.27.7 |
| `org.mockito:mockito-core`       | 5.17.0 |
| `org.testcontainers:testcontainers` / `junit-jupiter` / `postgresql` | 1.21.4 |

## Container images

| Image                          | Pin                          |
|--------------------------------|------------------------------|
| `postgres:16-alpine`           | tag; local digest `sha256:cf78e76683b9ca8c5733cbbdce6c9262b45b6767934dd0a95e671f9a0fc20685` (arm64 pull) |
| `eclipse-temurin:21-jdk-jammy` | tag (multi-arch), build stage |
| `eclipse-temurin:21-jre-jammy` | tag (multi-arch), runtime stage |

The runtime image installs `curl` (used by the Compose health check); no other runtime
packages are required.

## Reproducing these numbers

```bash
./gradlew --version
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencies --configuration testRuntimeClasspath
docker --version && docker compose version
java -version
```

The dependency-report output shows requested and selected versions (for example
`kotlin-reflect:2.1.21 -> 2.2.20`), which is why the resolved column above lists the
post-conflict versions.
