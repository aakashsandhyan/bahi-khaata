@echo off
REM ============================================================================
REM  Bachat Baazar POS - back up the database.
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

REM Date stamp YYYY-MM-DD (locale-independent, via wmic).
for /f %%i in ('wmic os get localdatetime ^| find "."') do set DT=%%i
set STAMP=%DT:~0,4%-%DT:~4,2%-%DT:~6,2%_%DT:~8,2%%DT:~10,2%

copy /y "%DB%" "%OUT%\bahi-khaata_%STAMP%.db" >nul
echo Backed up to %OUT%\bahi-khaata_%STAMP%.db

REM Keep the last 30 copies, delete older ones.
for /f "skip=30 delims=" %%f in ('dir /b /o-d "%OUT%\bahi-khaata_*.db"') do del "%OUT%\%%f"

endlocal
