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
set "PORT=80"

REM Prefer Java 21 explicitly: the app needs it, and an older Java may also be on this machine.
call :findjava
"%JAVA%" -Dbahikhaata.db.path="%DBPATH%" -Dserver.port=%PORT% -jar "%~dp0backend.jar" >> "%~dp0server.log" 2>&1

endlocal
goto :eof

:findjava
set "JAVA=java"
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA=%%d\bin\java.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" if not exist "%ProgramFiles%\Eclipse Adoptium\jdk-21*" set "JAVA=%JAVA_HOME%\bin\java.exe"
goto :eof
