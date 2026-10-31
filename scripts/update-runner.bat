@echo off
setlocal enabledelayedexpansion

:: ====================================================================
:: FlickLAN Atomic Auto-Updater Script for Windows
:: Argument 1: Old Process ID (PID) to wait for
:: Argument 2: New downloaded executable path
:: ====================================================================

set OLD_PID=%~1
set NEW_EXE=%~2
set APP_DIR=%~dp0..
set TARGET_EXE=%APP_DIR%\FlickLAN.exe

echo [FlickLAN Updater] Starting update sequence...
echo [FlickLAN Updater] Waiting for old process (%OLD_PID%) to terminate...

:WAIT_PROCESS
tasklist /FI "PID eq %OLD_PID%" 2>NUL | find /I /N "%OLD_PID%">NUL
if "%ERRORLEVEL%"=="0" (
    timeout /t 1 /nobreak >NUL
    goto WAIT_PROCESS
)

echo [FlickLAN Updater] Old process terminated. Replacing executable...
timeout /t 1 /nobreak >NUL

:: Replace old executable with new one
if exist "%NEW_EXE%" (
    copy /Y "%NEW_EXE%" "%TARGET_EXE%" >NUL
    if "%ERRORLEVEL%"=="0" (
        echo [FlickLAN Updater] Successfully replaced %TARGET_EXE%
        del /Q "%NEW_EXE%" >NUL
    ) else (
        echo [FlickLAN Updater] Failed to overwrite %TARGET_EXE%. Retrying in 2 seconds...
        timeout /t 2 /nobreak >NUL
        copy /Y "%NEW_EXE%" "%TARGET_EXE%" >NUL
        del /Q "%NEW_EXE%" >NUL
    )
) else (
    echo [FlickLAN Updater] Error: New executable not found at %NEW_EXE%
    pause
    exit /b 1
)

echo [FlickLAN Updater] Launching updated FlickLAN...
start "" "%TARGET_EXE%"

echo [FlickLAN Updater] Update complete. Exiting.
exit /b 0

