import { defineConfig } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// Everything here is resolved to an absolute path rather than left relative to whatever directory
// `npm run e2e` happens to be invoked from. Flyway's `filesystem:` migration location in particular
// resolves relative to the java process's cwd, which is not reliably this directory unless pinned —
// so the seed location, the backend jar, and the scratch database path are all computed here and
// handed to the child processes as absolutes.
const projectRoot = path.dirname(fileURLToPath(import.meta.url))
const backendJar = path.resolve(projectRoot, '../../backend/build/libs/backend.jar')
const scratchDb = path.resolve(projectRoot, '.e2e/e2e.db')
const seedSqlDir = path.resolve(projectRoot, 'e2e/sql')

// 8081, never 8080 — a real dev backend may be running on 8080, and this must never collide with
// or be mistaken for it. 5299 is a dedicated, unlikely-to-collide port for the e2e frontend, kept
// separate from the ordinary dev server on 5173.
const BACKEND_PORT = 8081
const FRONTEND_PORT = 5299

export default defineConfig({
  testDir: 'e2e',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      // The real Spring Boot backend, fat jar already built by the ordinary Gradle build — this
      // harness never compiles or touches backend source. A scratch SQLite database is deleted and
      // recreated on every run so the suite always starts from identical state; every versioned
      // migration runs first, then the deterministic e2e seed as a Flyway repeatable migration
      // loaded from a second `filesystem:` location alongside the classpath one (repeatable
      // migrations always run after every versioned migration, so the seed lands on a fully
      // migrated schema). All backend configuration is command-line args — no production file is
      // touched.
      command:
        `rm -rf .e2e && mkdir -p .e2e && ` +
        `java -jar "${backendJar}" ` +
        `--server.port=${BACKEND_PORT} ` +
        `"--bahikhaata.db.path=${scratchDb}" ` +
        `"--spring.flyway.locations=classpath:db/migration,filesystem:${seedSqlDir}"`,
      cwd: projectRoot,
      url: `http://localhost:${BACKEND_PORT}/api/instance`,
      timeout: 180_000,
      reuseExistingServer: false,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      // The dashboard's Vite dev server, on a dedicated port so it can never be a real dev session.
      // E2E_BACKEND_URL retargets the dev proxy (see vite.config.ts) at the e2e backend above, so
      // the browser's requests stay same-origin through the proxy — a direct cross-origin fetch
      // would run into the backend's CORS policy, which only allows :5173 and only GET/POST.
      command: `npx vite --port ${FRONTEND_PORT} --strictPort`,
      cwd: projectRoot,
      url: `http://localhost:${FRONTEND_PORT}`,
      timeout: 60_000,
      reuseExistingServer: false,
      env: {
        E2E_BACKEND_URL: `http://localhost:${BACKEND_PORT}`,
      },
    },
  ],
})
