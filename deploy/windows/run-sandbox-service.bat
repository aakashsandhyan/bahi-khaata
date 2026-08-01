@echo off
REM ============================================================================
REM  Bachat Baazar POS - SANDBOX headless run (for a scheduled task / SSH start).
REM
REM  Same throwaway sandbox as start-sandbox.bat, but with NO browser pop-up and
REM  NO pause, so it survives an SSH disconnect when launched by Task Scheduler.
REM  Runs the STAGED backend-sandbox.jar against a fresh copy of the live DB on
REM  port 8081, badged SANDBOX. The live shop on :8080 is never touched.
REM  Logs to sandbox-server.log next to the jar.
REM ============================================================================

setlocal
cd /d "%~dp0"

set "SRC=%~dp0data\bahi-khaata.db"
set "SBX=%~dp0data\sandbox.db"
set "PORT=8081"

REM Refresh from the live DB each start (committed .db only — the live -wal is mid-write and would
REM hand a torn file). Clear any stale wal/shm so SQLite rebuilds them on the copy.
if exist "%SRC%" (
  copy /y "%SRC%" "%SBX%" >nul
  if exist "%SBX%-wal" del "%SBX%-wal"
  if exist "%SBX%-shm" del "%SBX%-shm"
)

call :findjava
"%JAVA%" -Dbahikhaata.db.path="%SBX%" -Dserver.port=%PORT% -Dbahikhaata.sandbox=true -jar "%~dp0backend-sandbox.jar" >> "%~dp0sandbox-server.log" 2>&1

endlocal
goto :eof

:findjava
set "JAVA=java"
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA=%%d\bin\java.exe"
goto :eof
