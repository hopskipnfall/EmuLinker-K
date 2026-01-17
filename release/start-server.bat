@echo off
setlocal

:: 1. ESTABLISH CONTEXT
:: Change to the directory where this script is located
cd /d "%~dp0"

:: 2. CONFIGURATION
set "JAR_FILE=lib\emulinker-k-0.15.0-DEV.jar"
set "MAIN_CLASS=org.emulinker.kaillera.pico.ServerMainKt"
set "LOG_FILE=emulinker.log"
set "JAVA_OPTS=-Xms64m -Xmx256m -XX:+UseSerialGC -XX:+AlwaysPreTouch"
set "CLASSPATH=conf;%JAR_FILE%"

:: 3. PRE-FLIGHT CHECKS
if not exist "%JAR_FILE%" (
    echo [ERROR] Jar file not found at: %JAR_FILE%
    echo Please check the path or filename in the script configuration.
    pause
    exit /b 1
)

:: Check for existing instance using wmic to look for the command line
wmic process where "CommandLine like '%%org.emulinker.kaillera.pico.ServerMainKt%%'" get ProcessId 2>nul | findstr [0-9] >nul
if %errorlevel%==0 (
    echo [ABORTED] An instance of EmuLinker-K is already running.
    pause
    exit /b 1
)

:: 4. START THE SERVER
echo Starting EmuLinker-K...

:: Start Java in a separate window minimized (/MIN) so it runs in the background
start "EmuLinker-K Server" /MIN java %JAVA_OPTS% -cp "%CLASSPATH%" %MAIN_CLASS%

:: 5. VERIFY AND INSPECT LOGS
echo Waiting for server to initialize...
timeout /t 3 /nobreak >nul

:: Check if the log exists
if exist "%LOG_FILE%" (
    echo.
    echo -------------------------------------------------------------------------------
    echo Reading startup logs...
    echo -------------------------------------------------------------------------------

    :: Print the last 20 lines of the log file
    :: PowerShell is used here because 'tail' is not native to Windows cmd
    powershell -Command "Get-Content '%LOG_FILE%' -Tail 20"
) else (
    echo [WARNING] %LOG_FILE% was not found yet.
)

echo.
echo -------------------------------------------------------------------------------
echo Server launched. You can close this window.
pause
