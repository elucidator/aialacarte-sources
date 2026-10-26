# Multi-stage build: works on Apple Silicon (arm64) and ordinary Linux (amd64) because
# the eclipse-temurin and postgres images are multi-architecture. No arch-specific binaries.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar && cp "$(ls build/libs/*.jar | grep -v plain | head -1)" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
# curl is installed on purpose: the Compose health check probes /health with it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
