@echo off
setlocal

:: 1. CONFIGURATION
set "MAIN_CLASS=org.emulinker.kaillera.pico.ServerMainKt"

:: 2. IDENTIFY AND KILL PROCESS
echo Stopping EmuLinker-K...

:: We use WMIC to find the process with the specific Java class in its command line and delete it.
wmic process where "CommandLine like '%%%MAIN_CLASS%%%'" call terminate >nul 2>&1

if %errorlevel%==0 (
    echo [SUCCESS] Server stop command sent.
) else (
    echo [INFO] No running EmuLinker-K instance found or access denied.
)

:: 3. VERIFY SHUTDOWN
:: Wait a moment to ensure it's gone
timeout /t 2 /nobreak >nul

:: Check if it's still there
wmic process where "CommandLine like '%%%MAIN_CLASS%%%'" get ProcessId 2>nul | findstr [0-9] >nul
if %errorlevel%==0 (
    echo [ERROR] Server is still running. You may need to close it manually via Task Manager.
) else (
    echo [VERIFIED] Server is stopped.
)

pause
