# syntax=docker/dockerfile:1.7
# Production image for the Apsonic backend (Spring Boot 3.4, Java 21).
# The jar is split into layers (dependencies / loader / application) so a code-only release changes one small layer.

# ---- build -----------------------------------------------------------------------------------
# Runs on the machine doing the build, whatever its CPU: the jar is plain Java bytecode and identical on every
# architecture. Only the runtime stage below is built for the target CPU. (Emulating a foreign CPU for the Maven
# build fails on Apple-silicon Macs and is much slower.)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN ./mvnw -B -q -DskipTests package \
 && cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- runtime ---------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app/uploads \
 && chown -R app:app /app \
 # The base image ships a Go service manager (pebble) that this container never runs; it carries its own CVEs.
 && rm -f /usr/bin/pebble
WORKDIR /app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app
# Size the heap from the container's memory limit; die cleanly (and be restarted) instead of limping on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
# Default to the hardened profile; development opts in with SPRING_PROFILES_ACTIVE=local.
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
VOLUME ["/app/uploads"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
