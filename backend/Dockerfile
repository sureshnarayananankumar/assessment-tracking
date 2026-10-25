# syntax=docker/dockerfile:1
# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/assesshub-backend.jar /workspace/app.jar

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/data/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080 \
    UPLOAD_DIR=/app/data/uploads

EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
