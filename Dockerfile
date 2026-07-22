# The backend as a portable image: the source of truth for schema, data and business
# logic, packaged to run on any machine with Docker rather than only on one shop counter.
#
# The terminal is deliberately NOT in here. It is a JavaFX desktop application that needs a
# display and a barcode scanner plugged into the physical machine, and running a GUI in a
# container means X11 forwarding — fragile, and different on every platform, which is the
# opposite of what a container is for. The terminal stays a desktop app and reaches this
# over the network; see bahikhaata.backend.uri.

# --- build ---------------------------------------------------------------------------------
# The full JDK and the Gradle wrapper, so the image builds identically wherever it is built
# and needs nothing installed on the host but Docker itself.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# The wrapper and build files first, so a change to source alone does not re-download every
# dependency. Docker caches this layer until the build definition itself changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY contracts/build.gradle ./contracts/
COPY backend/build.gradle ./backend/
RUN chmod +x gradlew && ./gradlew :backend:dependencies --no-daemon > /dev/null 2>&1 || true

# Then the source. Only contracts and backend are needed — the terminal, dashboard and
# architecture modules play no part in the running service.
COPY contracts ./contracts
COPY backend ./backend
RUN ./gradlew :backend:bootJar --no-daemon

# --- run -----------------------------------------------------------------------------------
# A JRE, not the JDK: smaller, and nothing at runtime compiles anything. Debian-based, NOT
# Alpine — the SQLite driver carries a native library built against glibc, and musl would
# make it fail to load the moment the first query ran.
FROM eclipse-temurin:21-jre

# curl, for the health check below and nothing else — a JRE image carries no HTTP client of
# its own, and a health check is worthless if the tool that runs it is missing.
RUN apt-get update && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Not root. A process that never needs to be root should never be able to act as one, and if
# the image is ever exposed beyond the shop this is the difference between a bug and a breach.
RUN useradd --system --create-home --uid 1001 bahikhaata

# The database lives here, on a volume, so it outlives the container. Everything the shop has
# recorded is in this one file; losing it with the container would lose the shop's books.
RUN mkdir -p /data && chown bahikhaata:bahikhaata /data
VOLUME /data
ENV BAHIKHAATA_DB_PATH=/data/bahi-khaata.db

COPY --from=build --chown=bahikhaata:bahikhaata /src/backend/build/libs/backend.jar /app/backend.jar

USER bahikhaata
EXPOSE 8080

# The db path is a JVM system property the application already reads; the Keepa key is
# optional and absent by default, which is a supported state.
ENTRYPOINT ["java", "-Dbahikhaata.db.path=/data/bahi-khaata.db", "-jar", "/app/backend.jar"]

# Asks the application's own health endpoint, so it reports healthy only once the schema has
# migrated and it can actually serve — an orchestrator then waits for genuine readiness
# rather than for the process merely to exist.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/api/health || exit 1
