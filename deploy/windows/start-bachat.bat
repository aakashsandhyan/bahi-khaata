@echo off
REM ============================================================================
REM  Bachat Baazar POS - start the shop (double-click this).
REM
REM  Runs the whole app (frontend + backend) from one jar on port 80, then
REM  opens the browser. Fast: no build, no Node, no Vite - just java -jar.
REM
REM  This machine is the "brain": it holds the database and serves both counters.
REM  Counter 2 opens a browser to  http://THIS-PC-IP:80  over the shop network.
REM ============================================================================

setlocal
cd /d "%~dp0"

set "DBPATH=%~dp0data\bahi-khaata.db"
set "PORT=80"

echo ============================================================
echo   Bachat Baazar POS
echo   Database: %DBPATH%
echo   Address:  http://localhost:%PORT%
echo ============================================================
echo.
echo   Keep this window OPEN while the shop is trading.
echo   Closing it stops billing on BOTH counters.
echo.

REM Open the till in the default browser a few seconds after the server starts.
start "" cmd /c "timeout /t 6 >nul & start http://localhost:%PORT%"

REM Prefer Java 21 explicitly (an older Java may also be installed on this machine).
set "JAVA=java"
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA=%%d\bin\java.exe"

REM Run the app. -Dbahikhaata.db.path pins the database to this folder's data\ dir,
REM so it is the same file every time no matter where the shortcut is launched from.
"%JAVA%" -Dbahikhaata.db.path="%DBPATH%" -Dserver.port=%PORT% -jar "%~dp0backend.jar"

echo.
echo Server stopped. Press any key to close.
pause >nul
endlocal
