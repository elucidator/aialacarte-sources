# Build environment

Pinned compatible stable versions (chosen, not claimed to be the latest):

| Component | Version/source |
|---|---|
| JDK language/runtime target | 21 |
| Host JDK used | Oracle GraalVM 21.0.10+8.1, macOS arm64 |
| Container build/runtime | eclipse-temurin:21.0.8_9-jdk-jammy / 21.0.8_9-jre-jammy |
| Gradle wrapper | 8.14.3, official distribution SHA-256 in wrapper properties |
| Kotlin JVM/Spring plugins, stdlib/reflect | 2.1.20 |
| Spring Boot plugin and dependency BOM | 3.5.6 |
| Spring Framework (resolved BOM) | 6.2.11 |
| PostgreSQL image/server | postgres:17.6-bookworm / 17.6 |
| PostgreSQL JDBC | 42.7.7 |
| Flyway core/PostgreSQL | 11.7.2 |
| Jackson Kotlin module | 2.19.2 |
| JUnit Jupiter / platform | 5.12.2 / 1.12.2 |
| Browser automation | Playwright Python 1.55.0; locally installed Chrome |
| Docker used | Engine/CLI 28.3.3, Compose 2.39.2-desktop.1, Desktop 4.45.0 |
| Python used | 3.14.4 (export/check scripts use standard library; Python 3.10+ supported) |

Full resolved application/test/compiler coordinates are in `gradle.lockfile`. Gradle locks transitive versions too; use `./gradlew dependencies --write-locks` only when intentionally changing dependency versions. Container image tags are explicit multi-architecture tags; no platform-specific downloaded executables or forced platform setting is required. curl is explicitly installed in the runtime image and pg_isready ships in the database image. OS packages follow the pinned image's Ubuntu repositories; image digests/package patch releases are not frozen.

Official compatibility/release sources:

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html): Boot 3.5 supports JDK 21 and Gradle 8.4+.
- [Spring Boot 3.5.6 release](https://spring.io/blog/2025/09/18/spring-boot-3-5-6-available-now/).
- [Gradle 8.14.3 release notes](https://docs.gradle.org/8.14.3/release-notes.html) and [Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).
- [Kotlin releases](https://kotlinlang.org/docs/releases.html).
- [Official PostgreSQL image](https://hub.docker.com/_/postgres).
- [Temurin official image](https://hub.docker.com/_/eclipse-temurin).

During the implementation run, dependency downloads used a private Maven mirror supplied by the operator (URL removed for publication). This workspace resolves from Maven Central via `mavenCentral()` in `settings.gradle.kts` and `build.gradle.kts`, with Gradle Plugin Portal fallback for plugin markers. No credentials are committed. First builds need internet for the wrapper/container pulls. Dependencies stay locked. Once cached, normal host demos can use `--offline`; container demos run prebuilt images locally. Linux and x86 builds are supported by the source and multi-architecture images; only the available macOS/arm64 host is directly verified here.
