# Running the backend in Docker

The backend — the schema, the data, and all the business logic — runs as a container that
works the same on any machine with Docker. It is the portable, "runs anywhere" half of the
system.

## What is not in the image, and why

The **terminal is not containerized**. It is a JavaFX desktop application that needs a
display and a barcode scanner plugged into the physical machine. Running a GUI in a container
means X11 forwarding, which is fragile and different on every platform — the opposite of what
a container is meant to give you. The terminal stays a desktop app and connects to the
backend over the network.

For the terminal itself to be portable across machines, the plan is a native installer per
operating system, built with `jpackage`. That is separate from this and not built yet.

## Run it

```sh
docker compose up -d --build      # build and start, in the background
docker compose logs -f            # watch it come up and migrate the schema
```

It is ready when the log says `Started BackendApplication` and the health check passes:

```sh
curl http://localhost:8080/api/health
```

Stop it — the data survives:

```sh
docker compose down
```

## Where the data lives

In a Docker named volume, `bahikhaata-data`, mounted at `/data` inside the container. This is
the shop's entire books in one SQLite file, and the volume is what keeps it across restarts
and image upgrades.

**Only one container may run against it.** SQLite is a single file, and two writers would
corrupt it. This is a single-shop system, so that is a fit rather than a limit — but it does
mean the backend does not scale horizontally, by design.

Back it up by copying the volume:

```sh
docker run --rm -v bahikhaata-data:/data -v "$PWD":/backup eclipse-temurin:21-jre \
    cp /data/bahi-khaata.db /backup/bahi-khaata-backup.db
```

## Pointing the terminal at it

The terminal reads its backend address from a system property. Left unset it uses
`http://127.0.0.1:8080`, which is correct when the container runs on the same machine.

To reach a backend on another machine on the network:

```sh
./gradlew :terminal:run -Dbahikhaata.backend.uri=http://<that-machine>:8080
```

## Price lookups (optional)

The Keepa key, if you use one, goes in the environment and never in the image. Put it in a
`.env` file beside `compose.yaml`:

```
BAHIKHAATA_KEEPA_KEY=your-key
```

Absent is a supported state — price lookups simply do not run, and nothing else is affected.

## What has and has not been tested

Verified without Docker installed: the jar builds under the same isolated file set the image
copies, and the exact command the container runs boots a fresh database, migrates it, and
serves health. **Not yet verified against a real `docker build`**: that the curl health check
works in the running image, the non-root user's permissions on the volume, and that the
SQLite native driver loads under the container's Debian glibc — all expected to work, none
confirmed on this machine because Docker is not installed here. Run `docker compose up --build`
once on a machine that has Docker and watch the health check before trusting it.
