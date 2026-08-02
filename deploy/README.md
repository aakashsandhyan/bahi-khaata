# Bachat Bazaar POS — shop setup (Windows)

Two billing counters. **Machine A** is the *brain* (holds the database, serves the app) **and** is
Counter 1. **Machine B** is Counter 2 — just a browser pointing at A. There is one database, on A,
so the two counters never double-sell the same item.

Why this is fast: the shop runs one pre-built `backend.jar`. No Node, no Vite, no Gradle, nothing
compiled on the Windows machine — that slow work happens once on the dev Mac. `java -jar` starts in
a couple of seconds and serves already-built pages.

---

## Step 1 — Build the release (on the Mac, once per update)

```bash
cd ~/Development/Projects/bahi-khaata
./deploy/build-release.sh
```

This produces **`deploy/release/`** containing:

| File | What it is |
|------|-----------|
| `backend.jar` | the whole app — frontend + API in one file |
| `install.ps1` | run once on Windows to install Java |
| `start-bachat.bat` | double-click to start the shop |
| `backup-db.bat` | copies the database aside |
| `README.md` | this file |

Copy the whole `deploy/release/` folder to **Machine A**, e.g. to `C:\BachatBaazar`.

---

## Step 2 — Machine A (brain + Counter 1)

1. Put the folder at `C:\BachatBaazar`.
2. **Install Java** — right-click `install.ps1` → *Run with PowerShell*.
   (If Windows blocks it: open PowerShell in the folder and run
   `powershell -ExecutionPolicy Bypass -File install.ps1`.)
3. **Bring your stock over** — copy your existing `bahi-khaata.db` into `C:\BachatBaazar\data\`.
   Skip this to start with an empty shop (the database builds itself on first start).
4. **Start it** — double-click `start-bachat.bat`. A window opens (keep it open while trading) and
   the browser opens to `http://localhost:8080`.
5. **Fix Machine A's network address** so Counter 2 can always find it. On the router, give A a
   reserved IP such as `192.168.1.10` (or set a static IP in Windows). Write it down.

> Keep the `start-bachat.bat` window open while the shop is trading — closing it stops **both**
> counters.

## Step 3 — Machine B (Counter 2)

Nothing to install. Open a browser and go to **`http://192.168.1.10:8080`** (Machine A's address).
Bookmark it / set it as the home page. Plug in the scanner. Done.

---

## Peripherals

- **Barcode scanner** — USB, at each counter. Reads the BBZ Code 128.
- **Label printer (TSC)** — belongs at **pricing / back-office**, not the till. USB printing is not
  finished yet in the software; for now use a network-capable printer or wire it up later.
- **Receipt printer / cash drawer** — only if you print bills / take cash; one set per counter.

---

## Keep the data safe (important — it lives on one machine)

- **UPS on Machine A.** A power cut mid-sale can corrupt the database. This is the cheapest, most
  important thing to buy for A.
- **Back up daily.** Double-click `backup-db.bat`, or schedule it:
  - Task Scheduler → Create Basic Task → Daily → *Start a program* → `C:\BachatBaazar\backup-db.bat`.
  - It keeps the last 30 copies in `C:\BachatBaazar\backups\`. Copy that folder to a USB stick or to
    Machine B now and then, so a dead disk on A doesn't lose everything.

---

## Deploying from the Mac over SSH (instead of walking a USB stick over)

If SSH to the Windows machine works, push and run it all from the Mac:

```bash
export WINHOST=user@192.168.1.10      # your ssh target (or an ~/.ssh/config alias)
./deploy/deploy-ssh.sh                # build + copy + install Java + register auto-start on boot
./deploy/deploy-ssh.sh --run-now      # ...and start it right now, detached
./deploy/deploy-ssh.sh --no-build     # just re-copy an already-built release/ (faster updates)
```

What it does on the Windows side:
- copies the release to `C:\BachatBaazar` (never touches `data\`, so records are safe),
- installs Java 21 if missing,
- registers a **Task Scheduler** job `BachatBaazar` that runs `run-service.bat` **on every boot** —
  so after a power cut the shop comes back on its own, no one double-clicking anything,
- `--run-now` starts it immediately via the scheduler, so it keeps running after you disconnect.

`run-service.bat` is the headless runner: no browser pop-up, no pause, logs to `server.log`.
`start-bachat.bat` is still there for a manual double-click on the machine itself.

Handy over SSH:
```bash
ssh $WINHOST type "C:\BachatBaazar\server.log"          # see the log
ssh $WINHOST schtasks /run  /tn BachatBaazar            # start it
ssh $WINHOST schtasks /end  /tn BachatBaazar            # stop it
ssh $WINHOST schtasks /query /tn BachatBaazar           # is it set to auto-start?
```

> A raw `ssh host "java -jar backend.jar"` dies the moment you disconnect — that is why this goes
> through Task Scheduler, which keeps it running independently of your SSH session.

## Try a new build safely — the sandbox

Before a new `backend.jar` touches the live shop, try it on a **throwaway copy of the real data**.
`start-sandbox.bat` runs the same app on **`http://localhost:8081`**, badged **SANDBOX**, against
`data\sandbox.db` — a fresh copy of `data\bahi-khaata.db` made on every launch. The live shop keeps
running on `:8080`, untouched; anything you do in the sandbox is discarded next launch.

**Test a new build without disturbing the live shop** — the running shop holds `backend.jar` open
(Windows locks it), so you cannot overwrite it while trading. Instead stage the new jar beside it:

```bash
# from the Mac — copy the new jar under the SANDBOX name (does not overwrite the live jar)
scp deploy/release/backend.jar "$WINHOST:C:/BachatBaazar/backend-sandbox.jar"
```

On the shop, double-click `start-sandbox.bat`: if `backend-sandbox.jar` is present it runs **that**
(otherwise it falls back to the live `backend.jar`). So you can exercise a new build — new screens,
and **any migrations, against a copy of live data** — on `:8081` while the shop bills on `:8080`. If
it starts clean and behaves, promote it (see below). If not, just delete `backend-sandbox.jar`.

## Check which version the shop's database is on (migrations)

An update only rewrites data if it carries **new migrations**. To know whether it will, compare the
highest `V##` the shop has applied with the highest `V##` in the new release.

- **Highest migration in the release** — the largest number in
  `backend/src/main/resources/db/migration/V##__*.sql` (in the repo / the build you're shipping).
- **Highest migration the shop has applied** — read its Flyway history:

  ```bash
  # if sqlite3 is on the shop:
  ssh $WINHOST "sqlite3 C:/BachatBaazar/data/bahi-khaata.db \"SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history;\""

  # or pull the DB to the Mac and read it there (also grab -wal in case of an un-checkpointed row):
  scp "$WINHOST:C:/BachatBaazar/data/bahi-khaata.db" /tmp/live.db
  sqlite3 /tmp/live.db "SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history;"
  ```

  The startup line in `server.log` also names the schema version Flyway migrated to.

If the two numbers **match**, the update runs **no** migrations — it is a pure jar swap (new
frontend/code only), low risk. If the release is **higher**, it will migrate the live DB on first
start: **back up first** and test in the sandbox before going live.

## Updating the software later

1. On the Mac: `./deploy/build-release.sh`.
2. **If the update carries new migrations** (see the version check above), **back up the live DB
   first** — double-click `backup-db.bat` on the shop, or `ssh $WINHOST "C:/BachatBaazar/backup-db.bat"`.
   A migration rewrites live records and is not casually reversible; the backup is the rollback.
3. **Test in the sandbox first** — stage the new jar as `backend-sandbox.jar` and run
   `start-sandbox.bat` (see *Try a new build safely* above). Confirm it starts clean and works on a
   copy of the real data.
4. **Go live** — the running shop **locks `backend.jar`**, so **stop it first** (close the
   `start-bachat.bat` window, or `ssh $WINHOST schtasks /end /tn BachatBaazar`). Then copy the new
   jar over the old one (**never** touch `data\` — that is the shop's records) and start it again
   (`start-bachat.bat`, or `./deploy/deploy-ssh.sh --no-build --run-now` from the Mac). Migrations,
   if any, run automatically on start.

## If something's wrong

- **Counter 2 can't connect** → is Machine A on and `start-bachat.bat` running? Is A's IP still
  `192.168.1.10`? (A reserved/static IP prevents this.) Same shop network / cable in?
- **"java is not recognized"** → Java didn't install or PATH didn't refresh. Close and reopen the
  window, or re-run `install.ps1`.
- **Port 8080 in use** → something else grabbed it. Edit `start-bachat.bat`, change `set PORT=8080`
  to another port (e.g. `8090`), and use that in Counter 2's address.
