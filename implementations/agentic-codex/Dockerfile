FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21.0.8_9-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home app
WORKDIR /app
COPY --from=build /workspace/build/libs/conference-planner.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
