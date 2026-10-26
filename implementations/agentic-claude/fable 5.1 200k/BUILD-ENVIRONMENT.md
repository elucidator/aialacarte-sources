# Build environment

All versions are pinned in `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`,
`Dockerfile` and `docker-compose.yml`. Resolved versions were read from `./gradlew dependencies`.

## Toolchain

| Component | Version | Where pinned |
|-----------|---------|--------------|
| JDK (host build and tests) | 21 (Gradle toolchain `languageVersion = 21`) | `build.gradle.kts` |
| Gradle wrapper | 9.2.0 (`gradle-9.2.0-bin.zip`) | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin (jvm, plugin.spring) | 2.2.21 | `build.gradle.kts` |
| Spring Boot plugin / BOM | 4.0.8 | `build.gradle.kts` |
| Spring dependency-management plugin | 1.1.7 | `build.gradle.kts` |

## Resolved runtime dependencies

| Artifact | Version |
|----------|---------|
| spring-boot, spring-boot-starter-webmvc/jdbc/flyway | 4.0.8 |
| spring-core / spring-jdbc / spring-webmvc | 7.0.9 |
| kotlin-stdlib, kotlin-reflect | 2.2.21 |
| tools.jackson.core:jackson-databind, jackson-module-kotlin | 3.1.5 |
| flyway-core, flyway-database-postgresql | 11.14.1 |
| org.postgresql:postgresql | 42.7.13 |
| HikariCP | 7.0.2 |
| tomcat-embed-core | 11.0.24 |

## Resolved test dependencies

| Artifact | Version |
|----------|---------|
| junit-jupiter | 6.0.3 |
| testcontainers, testcontainers-postgresql | 2.0.5 |
| com.microsoft.playwright:playwright | 1.63.0 |
| assertj-core | 3.27.7 |
| mockito-core (via spring-boot-starter-test) | 5.20.0 |

## Container images

| Image | Use | Digest observed at build time |
|-------|-----|-------------------------------|
| `eclipse-temurin:21-jre-alpine` (JRE 21.0.12.1+1) | app runtime image base | `sha256:51ab5e33…4555` |
| `postgres:17.9-alpine` | database in Compose and Testcontainers | `sha256:c7526c0f…8609` |
| `testcontainers/ryuk:0.14.0` | Testcontainers resource reaper | pulled by Testcontainers 2.0.5 |

Both images are multi-arch (linux/amd64 and linux/arm64); no architecture-specific binaries
are added. The app image contains only the JRE and `build/libs/conference-planner.jar`; the
Compose health check uses `wget`, which BusyBox provides in the Alpine base image.

## Local software required

- JDK 21 on `PATH` or discoverable by the Gradle toolchain resolver.
- Docker Engine with Compose v2 (`docker compose`).
- Google Chrome for `./gradlew uiTest -Pbrowser=chrome` (Playwright 1.63.0 drives it via the `chrome` channel).
- `bash`, `diff`, `patch`, `python3` and `shasum`/`sha256sum` for the exercise export and verification scripts.

## Network notes

- `MAVEN_REPOSITORY_URL` overrides both the plugin and the dependency repositories in
  `settings.gradle.kts`; unset means Maven Central and the Gradle plugin portal.
- Image names in the project are canonical Docker Hub names. If Docker Hub rate-limits
  anonymous pulls, pull through a mirror and `docker tag` to the canonical name.
