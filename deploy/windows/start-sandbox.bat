@echo off
REM ============================================================================
REM  Bachat Bazaar POS - SANDBOX (double-click this).
REM
REM  The SAME app as the shop, but pointed at a throwaway copy of the database
REM  on a different port. Only the database is different - everything else (the
REM  screens, printing, pricing) is exactly the live app. Use it to try things
REM  without any risk to the real shop data.
REM
REM    Live shop  ->  http://localhost:8080   (real DB: data\bahi-khaata.db)
REM    Sandbox    ->  http://localhost:8081   (copy:   data\sandbox.db)
REM
REM  Every launch RE-COPIES the live database into the sandbox, so it always
REM  starts from the current real data. Anything you change in a previous
REM  sandbox session is discarded - that is the point of a sandbox.
REM ============================================================================

setlocal
cd /d "%~dp0"

set "SRC=%~dp0data\bahi-khaata.db"
set "SBX=%~dp0data\sandbox.db"
set "PORT=8081"

if not exist "%SRC%" (
  echo No live database found at %SRC% - nothing to copy.
  pause & exit /b 1
)

echo Copying the live database into a fresh sandbox copy...
REM Copy ONLY the committed database file. The live shop keeps its write-ahead log (-wal)
REM open and mid-write, so copying that alongside can hand the sandbox a torn, unopenable
REM file. The committed .db is a consistent snapshot as of the last checkpoint, which is all
REM a sandbox needs. Clear any stale wal/shm from a previous sandbox so SQLite rebuilds them.
copy /y "%SRC%" "%SBX%" >nul
if exist "%SBX%-wal" del "%SBX%-wal"
if exist "%SBX%-shm" del "%SBX%-shm"

echo ============================================================
echo   Bachat Bazaar POS - SANDBOX  (throwaway data)
echo   Database: %SBX%
echo   Address:  http://localhost:%PORT%
echo ============================================================
echo.
echo   The live shop keeps running on :8080, untouched.
echo   Close this window to stop the sandbox.
echo.

REM Open the sandbox in the default browser a few seconds after the server starts.
start "" cmd /c "timeout /t 6 >nul & start http://localhost:%PORT%"

REM Prefer Java 21 explicitly (an older Java may also be installed on this machine).
set "JAVA=java"
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA=%%d\bin\java.exe"

REM Prefer a staged sandbox jar if one is present, so a new build can be tried here without
REM touching the live shop jar (which the running shop app holds open). Falls back to the shared
REM backend.jar when no staged one exists.
set "SBXJAR=%~dp0backend.jar"
if exist "%~dp0backend-sandbox.jar" set "SBXJAR=%~dp0backend-sandbox.jar"
echo   Jar: %SBXJAR%

REM Only the database path changes - same everything else, different port.
REM bahikhaata.sandbox=true makes the screen badge itself "SANDBOX" so it is never mistaken
REM for the live shop; the real shop sets no such flag and shows no badge.
"%JAVA%" -Dbahikhaata.db.path="%SBX%" -Dserver.port=%PORT% -Dbahikhaata.sandbox=true -jar "%SBXJAR%"

echo.
echo Sandbox stopped. Press any key to close.
pause >nul
endlocal
