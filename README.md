# बचत बाज़ार — Bachat Bazaar POS

An offline-first point-of-sale system for Bachat Bazaar, a liquidation and
overstock retail store. Built to run on the shop's own machine: checkout never
waits on the network, and the database is a single local file.

Licensed under [AGPL-3.0](LICENSE).

## What is here

A Gradle multi-project build with four components, kept detachable behind
explicit contracts so any can move to its own repository later:

| Module        | What it is                                                        |
|---------------|-------------------------------------------------------------------|
| `contracts`   | Wire-format types shared between the others. Depends on nothing.   |
| `backend`     | Spring Boot service. Owns the SQLite database and all business logic. |
| `terminal`    | JavaFX checkout terminal. Reaches data only through the backend.  |
| `dashboard`   | Admin and reports. Not yet built.                                 |
| `architecture`| Test-only. Enforces the boundaries between the modules.           |

## Prerequisites

- **JDK 21.** The only requirement. The build pins the toolchain to Java 21, so
  a different JDK on your `PATH` is fine as long as a 21 is installed somewhere
  Gradle can find it.

  ```
  brew install openjdk@21          # macOS
  ```

Nothing else. Gradle itself arrives through the wrapper, and every other
dependency downloads on first build. There is no separate database to install
and no schema to apply by hand — the backend creates and migrates its database
on first run.

## Build and test

```
./gradlew build
```

The first run downloads the Gradle distribution and all dependencies, so it
takes a few minutes. Later runs are seconds. This compiles every module and runs
the full test suite.

## Run the backend

```
./gradlew :backend:bootRun
```

It listens on `http://127.0.0.1:8080`, creates `data/bahi-khaata.db` if absent,
and applies any pending migrations. Confirm it is ready:

```
curl http://127.0.0.1:8080/api/health
# {"status":"UP","schemaVersion":"2"}
```

## Run the terminal

With the backend running, in a second shell:

```
./gradlew :terminal:run
```

The terminal opens, contacts the backend, and reports what it found. It points
at `http://127.0.0.1:8080` by default; override with
`-Dbahikhaata.backend.uri=...`.

## Troubleshooting

**The first `./gradlew` stalls while "Downloading" the distribution.** The
wrapper fetches Gradle from `services.gradle.org`, which redirects to a GitHub
release asset. On some networks that redirect chain stalls the wrapper's minimal
downloader. If this happens, fetch the distribution directly and let the wrapper
find it:

```
curl -L -o ~/gradle-dist.zip \
  https://services.gradle.org/distributions/gradle-9.6.1-bin.zip
```

then unzip it and run Gradle once with `--gradle-user-home`, or install Gradle
9.6.1 via your package manager and run `gradle wrapper` once. This is a network
limitation, not a project one — the URL in `gradle/wrapper/gradle-wrapper.properties`
is the canonical distribution.

## Licence

AGPL-3.0-or-later. See [LICENSE](LICENSE). The terminal bundles Noto Sans
Devanagari under the SIL Open Font License; see
`terminal/src/main/resources/fonts/OFL.txt`.
