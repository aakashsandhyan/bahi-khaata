@echo off
REM ============================================================================
REM  Bachat Bazaar POS - back up the database.
REM
REM  The brain machine holds the ONLY copy of the shop's data. This copies it
REM  aside with a date stamp. Run it daily (or set it as a scheduled task).
REM
REM  Copies to a "backups" folder next to the app. For real safety, also copy
REM  that folder to a USB stick or the other counter now and then.
REM ============================================================================

setlocal
cd /d "%~dp0"

set "DB=%~dp0data\bahi-khaata.db"
set "OUT=%~dp0backups"

if not exist "%DB%" (
  echo No database found at %DB% - nothing to back up.
  pause & exit /b 1
)
if not exist "%OUT%" mkdir "%OUT%"

REM Date stamp YYYY-MM-DD_HHMM (locale-independent, via PowerShell). wmic was removed on
REM current Windows, so the old wmic date stamp produced a garbage name and no backup at all.
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmm"') do set "STAMP=%%i"

copy /y "%DB%" "%OUT%\bahi-khaata_%STAMP%.db" >nul
echo Backed up to %OUT%\bahi-khaata_%STAMP%.db

REM Keep the last 30 copies, delete older ones.
for /f "skip=30 delims=" %%f in ('dir /b /o-d "%OUT%\bahi-khaata_*.db"') do del "%OUT%\%%f"

endlocal
