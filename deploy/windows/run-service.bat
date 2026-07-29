@echo off
REM ============================================================================
REM  Bachat Baazar POS - headless run (for a scheduled task / remote start).
REM
REM  Same as start-bachat.bat but with NO browser pop-up and NO pause, so it can
REM  run unattended: launched by Task Scheduler at boot, or started over SSH.
REM  Logs to server.log next to the jar.
REM ============================================================================

setlocal
cd /d "%~dp0"

set "DBPATH=%~dp0data\bahi-khaata.db"
set "PORT=8080"

java -Dbahikhaata.db.path="%DBPATH%" -Dserver.port=%PORT% -jar "%~dp0backend.jar" >> "%~dp0server.log" 2>&1

endlocal
