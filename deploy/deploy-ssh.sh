#!/usr/bin/env bash
#
# Deploy Bachat Bazaar POS to the Windows shop machine over SSH, from the Mac.
#
# It builds the release here (fast), copies it to Windows with scp, installs Java there, and either
# starts the app now or registers it to run on boot. Nothing is compiled on Windows.
#
# Prerequisites:
#   - SSH already works to the Windows box (you said it does). OpenSSH Server on Windows.
#   - Set WINHOST to your ssh target, e.g. an ~/.ssh/config alias or user@ip:
#       export WINHOST=shop-pc            # or:  export WINHOST=aakash@192.168.1.10
#
# Usage:
#   export WINHOST=shop-pc
#   ./deploy/deploy-ssh.sh                 # build + copy + install + set to auto-start on boot
#   ./deploy/deploy-ssh.sh --no-build      # skip the build, just copy what's in deploy/release/
#   ./deploy/deploy-ssh.sh --run-now       # also start it immediately (detached), don't wait for a reboot
#
# WINDIR is where it lands on Windows (default C:/BachatBaazar). OpenSSH accepts forward slashes.

set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

WINHOST="${WINHOST:-}"
WINDIR="${WINDIR:-C:/BachatBaazar}"
BUILD=1
RUN_NOW=0
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --run-now)  RUN_NOW=1 ;;
    *) echo "unknown option: $arg"; exit 2 ;;
  esac
done

if [ -z "$WINHOST" ]; then
  echo "Set WINHOST first, e.g.:  export WINHOST=user@192.168.1.10"
  exit 1
fi

# run a command on Windows through cmd.exe (OpenSSH's default shell there)
winrun() { ssh "$WINHOST" "$@"; }

if [ "$BUILD" -eq 1 ]; then
  echo "==> Building the release"
  ./deploy/build-release.sh
fi
[ -f deploy/release/backend.jar ] || { echo "deploy/release/backend.jar missing — run without --no-build"; exit 1; }

echo "==> Making $WINDIR on $WINHOST"
# mkdir via cmd; harmless if it already exists.
winrun "if not exist \"${WINDIR//\//\\}\" mkdir \"${WINDIR//\//\\}\"" || true

echo "==> Copying the release (scp) — NOT overwriting data\\"
# Copy everything except the data folder, so the shop's records are never clobbered.
scp deploy/release/backend.jar deploy/release/*.bat deploy/release/*.ps1 deploy/release/README.md \
    "$WINHOST:$WINDIR/"

echo "==> Installing Java on Windows (once; skips if already present)"
winrun "powershell -ExecutionPolicy Bypass -File \"${WINDIR//\//\\}\\install.ps1\"" || {
  echo "install.ps1 failed — you may need to run it by hand on the machine once."; }

echo "==> Registering auto-start on boot (Task Scheduler)"
# Runs the headless script at startup, as the machine's user, highest privileges. /f overwrites.
winrun "schtasks /create /tn BachatBaazar /tr \"${WINDIR//\//\\}\\run-service.bat\" /sc onstart /rl highest /f" \
  || echo "(could not register the task — set it up by hand, see README)"

if [ "$RUN_NOW" -eq 1 ]; then
  echo "==> Starting it now (detached, survives this SSH session)"
  # schtasks /run launches it under the scheduler, so it keeps running after we disconnect.
  winrun "schtasks /run /tn BachatBaazar" || \
    winrun "powershell -Command \"Start-Process -FilePath '${WINDIR}/run-service.bat' -WindowStyle Hidden\""
  echo "Give it ~10s, then open  http://<the Windows machine's IP>"
fi

echo
echo "Done. On the Windows machine the app is at  http://localhost (port 80)"
echo "Counter 2 uses  http://<that machine's LAN IP>"
echo "Logs: $WINDIR/server.log   |   check state over ssh:  ssh $WINHOST type \"${WINDIR//\//\\}\\server.log\""
