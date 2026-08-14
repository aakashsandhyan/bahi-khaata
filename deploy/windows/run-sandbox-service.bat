@echo off
REM ============================================================================
REM  Bachat Bazaar POS - BETA/SANDBOX, headless (for a scheduled task).
REM
REM  Same contract as start-sandbox.bat but with no window interaction: no
REM  browser pop-up, no pause, output to sandbox.log — so a scheduled task can
REM  keep a beta build running on :8081 unattended, beside the live shop.
REM
REM  Every launch RE-COPIES the live database, so the beta always starts from
REM  current real data and can never touch it. Prefers backend-sandbox.jar
REM  (the staged beta build); falls back to the live backend.jar.
REM ============================================================================

setlocal
cd /d "%~dp0"

set "SRC=%~dp0data\bahi-khaata.db"
set "SBX=%~dp0data\sandbox.db"
set "PORT=8081"

if not exist "%SRC%" exit /b 1

REM Copy ONLY the committed database file — the live app's -wal is mid-write and
REM copying it can hand the sandbox a torn file. Clear stale wal/shm so SQLite
REM rebuilds them for the copy.
copy /y "%SRC%" "%SBX%" >nul
if exist "%SBX%-wal" del "%SBX%-wal"
if exist "%SBX%-shm" del "%SBX%-shm"

call :findjava

set "SBXJAR=%~dp0backend.jar"
if exist "%~dp0backend-sandbox.jar" set "SBXJAR=%~dp0backend-sandbox.jar"

"%JAVA%" -Dbahikhaata.db.path="%SBX%" -Dserver.port=%PORT% -Dbahikhaata.sandbox=true -jar "%SBXJAR%" >> "%~dp0sandbox.log" 2>&1

endlocal
goto :eof

:findjava
set "JAVA=java"
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA=%%d\bin\java.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" if not exist "%ProgramFiles%\Eclipse Adoptium\jdk-21*" set "JAVA=%JAVA_HOME%\bin\java.exe"
goto :eof
