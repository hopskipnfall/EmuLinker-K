@echo off
setlocal

:: 1. ESTABLISH CONTEXT
cd /d "%~dp0"

:: 2. CONFIGURATION
set "JAR_FILE=lib\emulinker-k-0.15.1.jar"
set "MAIN_CLASS=org.emulinker.kaillera.pico.ServerMainKt"
set "LOG_FILE=emulinker.log"
set "JAVA_OPTS=-Xms64m -Xmx256m -XX:+UseSerialGC -XX:+AlwaysPreTouch"
set "CLASSPATH=conf;%JAR_FILE%"

:: 3. PRE-FLIGHT CHECKS

:: Check if Java is installed
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not found in your PATH.
    echo Please install Java to run this server.
    pause
    exit /b 1
)

:: Verify Jar exists
if not exist "%JAR_FILE%" (
    echo [ERROR] Jar file not found at: %JAR_FILE%
    echo Please check the path or filename in the script configuration.
    pause
    exit /b 1
)

:: Check for existing instance (Strict check for java.exe ONLY)
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-WmiObject Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*%MAIN_CLASS%*' }).Count -gt 0) { exit 1 }"
if %errorlevel%==1 (
    echo [ABORTED] An instance of EmuLinker-K is already running.
    pause
    exit /b 1
)

:: 4. START THE SERVER
echo Starting EmuLinker-K...
start "EmuLinker-K Server" /MIN java %JAVA_OPTS% -cp "%CLASSPATH%" %MAIN_CLASS%

:: 5. VERIFY AND INSPECT LOGS
echo Waiting for server to initialize...
timeout /t 3 /nobreak >nul

if exist "%LOG_FILE%" (
    echo.
    echo -------------------------------------------------------------------------------
    echo Reading startup logs...
    echo -------------------------------------------------------------------------------
    powershell -NoProfile -Command "Get-Content '%LOG_FILE%' -Tail 20"
) else (
    echo [WARNING] %LOG_FILE% was not found yet.
)

echo.
echo -------------------------------------------------------------------------------
echo Server launched. You can close this window.
pause
