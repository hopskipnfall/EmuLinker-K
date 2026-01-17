@echo off
setlocal

:: 1. CONFIGURATION
set "MAIN_CLASS=org.emulinker.kaillera.pico.ServerMainKt"

:: 2. IDENTIFY AND KILL PROCESS
echo Stopping EmuLinker-K...

:: Use PowerShell to find the specific java process and kill it
powershell -NoProfile -ExecutionPolicy Bypass -Command "$proc = Get-WmiObject Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*%MAIN_CLASS%*' }; if ($proc) { $proc.Terminate(); exit 0 } else { exit 1 }"

if %errorlevel%==0 (
    echo [SUCCESS] Server stop command sent.
) else (
    echo [INFO] No running EmuLinker-K instance found.
)

:: 3. VERIFY SHUTDOWN
timeout /t 2 /nobreak >nul

:: Check if it is really gone
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-WmiObject Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*%MAIN_CLASS%*' }).Count -gt 0) { exit 1 }"

if %errorlevel%==1 (
    echo [ERROR] Server is still running. You may need to close it manually via Task Manager.
) else (
    echo [VERIFIED] Server is stopped.
)

pause
