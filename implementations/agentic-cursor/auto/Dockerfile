# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties init-mirror.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew \
 && ./gradlew --init-script init-mirror.gradle.kts bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
# curl is used by the Compose HEALTHCHECK against /health
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
