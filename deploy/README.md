# Bachat Baazar POS — shop setup (Windows)

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

## Updating the software later

1. On the Mac: `./deploy/build-release.sh`.
2. Copy the new `backend.jar` over the old one on Machine A. **Do not** touch `data\` — that is the
   shop's records. Restart `start-bachat.bat`. Database migrations run automatically on start.

## If something's wrong

- **Counter 2 can't connect** → is Machine A on and `start-bachat.bat` running? Is A's IP still
  `192.168.1.10`? (A reserved/static IP prevents this.) Same shop network / cable in?
- **"java is not recognized"** → Java didn't install or PATH didn't refresh. Close and reopen the
  window, or re-run `install.ps1`.
- **Port 8080 in use** → something else grabbed it. Edit `start-bachat.bat`, change `set PORT=8080`
  to another port (e.g. `8090`), and use that in Counter 2's address.
