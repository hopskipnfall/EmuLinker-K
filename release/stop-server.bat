@echo off
setlocal

:: 1. CONFIGURATION
set "MAIN_CLASS=org.emulinker.kaillera.pico.ServerMainKt"

:: 2. IDENTIFY PROCESS
echo Finding EmuLinker-K process...

:: We use PowerShell to get the PID of the specific Java class.
:: We store it in a temporary file because capturing variable output in batch is messy.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$proc = Get-WmiObject Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*%MAIN_CLASS%*' }; if ($proc) { $proc.ProcessId } else { }" > pid.tmp

set /p PID=<pid.tmp
del pid.tmp

if "%PID%"=="" (
    echo [INFO] No running EmuLinker-K instance found.
    pause
    exit /b 0
)

:: 3. SEND GRACEFUL STOP SIGNAL
echo [INFO] Found Process ID: %PID%
echo [SHUTDOWN] Sending stop signal (graceful)...

:: taskkill without /F requests a nice shutdown.
taskkill /PID %PID% >nul 2>&1

:: 4. WAIT AND VERIFY
echo Waiting for server to cleanup and exit...

:: Check every second for up to 10 seconds
for /L %%i in (1,1,10) do (
    timeout /t 1 /nobreak >nul

    :: Check if process still exists
    tasklist /FI "PID eq %PID%" 2>nul | find "%PID%" >nul
    if errorlevel 1 (
        echo [SUCCESS] Server stopped gracefully.
        goto :End
    )
)

:: 5. FORCE KILL (Fallback)
echo [WARNING] Server did not stop in time. Forcing shutdown...
taskkill /F /PID %PID% >nul 2>&1

echo [VERIFIED] Server process terminated.

:End
pause
