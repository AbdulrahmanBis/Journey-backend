# syntax=docker/dockerfile:1
#
# Journey API. Two stages: Maven builds the jar, a slim JRE runs it as a non-root user.
# Configuration comes from environment variables (see docker-compose.yml), which Spring maps onto
# application.properties: APP_JWT_SECRET overrides app.jwt.secret, and so on.

# ── Build ────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first, so a code change doesn't re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package && cp target/*.jar app.jar

# ── Run ──────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system journey \
 && useradd --system --gid journey --home-dir /app journey \
 && mkdir -p /app/storage \
 && chown journey:journey /app/storage

COPY --from=build /build/app.jar app.jar
# Default templates baked in; compose mounts the repo copy over them so wording edits apply live.
COPY notifications/templates ./notifications/templates

ENV APP_STORAGE_LOCAL_ROOT=/app/storage \
    APP_NOTIFICATIONS_TEMPLATES_ROOT=/app/notifications/templates \
    SPRING_JPA_SHOW_SQL=false \
    LOGGING_LEVEL_ORG_HIBERNATE_SQL=WARN \
    LOGGING_LEVEL_COM_JOURNEY=INFO

USER journey
EXPOSE 3000

# Healthy once the port accepts connections (the image has no curl; bash can open a socket).
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/3000' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
