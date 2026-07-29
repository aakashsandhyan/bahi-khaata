#!/usr/bin/env bash
#
# Build a Windows-ready release of Bachat Baazar POS.
#
# Run this on the Mac (or any dev machine), where building is fast. It produces a self-contained
# `deploy/release/` folder you copy to the shop's Windows machine. That machine then runs a single
# `java -jar` — no Node, no Gradle, no Vite, no compiling. That is what makes it fast on Windows:
# the slow part (building) happens here, once.
#
# What it does:
#   1. builds the React frontend (vite build -> dashboard/web/dist)
#   2. bakes that bundle into the backend so Spring serves it alongside the API on one port
#   3. builds the backend fat jar (backend.jar) with the frontend inside it
#   4. assembles deploy/release/ with the jar + the Windows scripts + the runbook
#
# The frontend is staged into the backend's static folder only for the build, then removed, so the
# source tree stays clean.

set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

ROOT="$(pwd)"
STATIC="$ROOT/backend/src/main/resources/static"
RELEASE="$ROOT/deploy/release"

echo "==> 1/4  Building the frontend (vite build)"
( cd dashboard/web && npm ci --no-audit --no-fund && npx vite build )

echo "==> 2/4  Baking the frontend into the backend (served on :8080 with the API)"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -R dashboard/web/dist/. "$STATIC/"
# Clean the staged static back out whatever happens, so the source tree is never left polluted.
trap 'rm -rf "$STATIC"' EXIT

echo "==> 3/4  Building the backend fat jar (this bundles the frontend in)"
./gradlew :backend:bootJar -q

echo "==> 4/4  Assembling deploy/release/"
rm -rf "$RELEASE"
mkdir -p "$RELEASE"
cp backend/build/libs/backend.jar "$RELEASE/backend.jar"
cp deploy/windows/*.bat deploy/windows/*.ps1 "$RELEASE/"
cp deploy/README.md "$RELEASE/README.md"

echo
echo "Done. Release is in: deploy/release/"
echo "  - backend.jar        the whole app (frontend + API), one file"
echo "  - install.ps1        run once on Windows to install Java"
echo "  - start-bachat.bat   double-click to start the shop"
echo "  - backup-db.bat      copies the database aside"
echo "  - README.md          the setup runbook"
echo
echo "Copy the deploy/release/ folder to the Windows machine and follow README.md."
