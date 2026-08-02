# Bachat Bazaar POS - Windows install (run ONCE per machine).
#
# Installs Java 21 (the only thing the shop machine needs) and prepares the data folder.
# The app itself is backend.jar, already built - nothing is compiled here.
#
# How to run:
#   1. Copy the whole release folder to the machine, e.g. C:\BachatBaazar
#   2. Right-click install.ps1 -> "Run with PowerShell"   (or see the note at the bottom)

$ErrorActionPreference = "Stop"
$AppDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "== Bachat Bazaar POS - install ==" -ForegroundColor Cyan
Write-Host "App folder: $AppDir"

# --- 1. Java 21 -------------------------------------------------------------------------------
function Test-Java21 {
    try {
        $v = (& java -version) 2>&1 | Out-String
        return $v -match 'version "(21|2[2-9])'   # 21 or newer
    } catch { return $false }
}

if (Test-Java21) {
    Write-Host "Java 21+ already present." -ForegroundColor Green
} else {
    Write-Host "Installing Java 21 (Temurin) via winget..." -ForegroundColor Yellow
    try {
        winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
        Write-Host "Java installed. You may need to CLOSE and REOPEN PowerShell so 'java' is on PATH." -ForegroundColor Yellow
    } catch {
        Write-Host "winget failed or is unavailable." -ForegroundColor Red
        Write-Host "Install Java 21 by hand from https://adoptium.net/temurin/releases/?version=21 then re-run this." -ForegroundColor Red
        exit 1
    }
}

# --- 2. Data folder ---------------------------------------------------------------------------
$DataDir = Join-Path $AppDir "data"
if (-not (Test-Path $DataDir)) {
    New-Item -ItemType Directory -Path $DataDir | Out-Null
    Write-Host "Created data folder: $DataDir"
} else {
    Write-Host "Data folder already exists: $DataDir"
}

Write-Host ""
Write-Host "Install done." -ForegroundColor Green
Write-Host "To bring your existing stock over, copy your bahi-khaata.db into: $DataDir"
Write-Host "Otherwise the shop starts with an empty database (migrations run on first start)."
Write-Host ""
Write-Host "Start the shop by double-clicking start-bachat.bat"
Write-Host ""
Write-Host "If Windows blocks this script, open PowerShell in this folder and run:" -ForegroundColor DarkGray
Write-Host "   powershell -ExecutionPolicy Bypass -File install.ps1" -ForegroundColor DarkGray
