@echo off
setlocal

:: 1. ESTABLISH CONTEXT
cd /d "%~dp0"

:: 2. CONFIGURATION
set "JAR_FILE=lib\emulinker-k-1.0.0-beta.jar"
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

:: 3.b UPDATE CHECK
echo.
echo Checking for updates...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference = 'SilentlyContinue';" ^
    "$prodUrl = 'https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/prod.txt';" ^
    "try {" ^
        "$t = Invoke-RestMethod -Uri $prodUrl -TimeoutSec 3;" ^
        "if ($t) {" ^
            "$ver = [regex]::Match($t, 'version=(.+)').Groups[1].Value;" ^
            "$note = [regex]::Match($t, 'releaseNotes=(.+)').Groups[1].Value;" ^
            "$jar = '%JAR_FILE%';" ^
            "$local = [regex]::Match($jar, 'emulinker-k-(.+)\.jar').Groups[1].Value;" ^
            "if ($ver -and $local) {" ^
                "if ($ver -ne $local) {" ^
                    "Write-Host '';" ^
                    "Write-Host '⚠️  Update Available!' -ForegroundColor Yellow;" ^
                    "Write-Host ('   Current: ' + $local);" ^
                    "Write-Host ('   Latest:  ' + $ver);" ^
                    "if ($note) { Write-Host ('   Release Notes: ' + $note) };" ^
                    "Write-Host '   To upgrade, run:';" ^
                    "Write-Host '   curl -fsSL https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/setup.sh | bash' -ForegroundColor White;" ^
                    "Write-Host '';" ^
                    "Write-Host '   Starting server with current version in 5 seconds...';" ^
                    "Start-Sleep -Seconds 5;" ^
                "} else {" ^
                    "Write-Host ('✅ Server is up to date (' + $local + ').');" ^
                "}" ^
            "}" ^
        "}" ^
    "} catch {" ^
        "Write-Host '⚠️  Could not fetch update info. Skipping check.' -ForegroundColor Yellow;" ^
    "}"

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
