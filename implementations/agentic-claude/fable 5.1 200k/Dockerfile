# Runtime image only. Build the jar on the host first: ./gradlew bootJar
FROM eclipse-temurin:21-jre-alpine

RUN adduser -D -u 10001 app
WORKDIR /app
COPY build/libs/conference-planner.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
