@echo off
setlocal enabledelayedexpansion

echo ====================================================================
echo  FlickLAN Full Build Pipeline (React Frontend + Java + Native .EXE)
echo ====================================================================

set ROOT_DIR=%~dp0..
set FRONTEND_DIR=%ROOT_DIR%\frontend
set BACKEND_DIR=%ROOT_DIR%\backend
set DIST_DIR=%FRONTEND_DIR%\dist
set STATIC_TARGET=%BACKEND_DIR%\src\main\resources\static
set JPACKAGE_CMD="C:\Program Files\Java\jdk-21.0.12\bin\jpackage.exe"

:: 1. Check Prerequisites
echo [1/4] Checking environment...
where mvn >nul 2>nul
if "%ERRORLEVEL%" neq "0" (
    echo [ERROR] Maven (mvn) was not found in your PATH!
    echo Please install Apache Maven (e.g. 'winget install Apache.Maven' or download from maven.apache.org)
    echo and ensure 'mvn' is accessible from the command line.
    pause
    exit /b 1
)

if not exist %JPACKAGE_CMD% (
    where jpackage >nul 2>nul
    if "%ERRORLEVEL%" neq "0" (
        echo [ERROR] jpackage.exe was not found in JDK or PATH!
        pause
        exit /b 1
    ) else (
        set JPACKAGE_CMD=jpackage
    )
)

:: 2. Build React Frontend
echo [2/4] Building React frontend bundle...
cd /d "%FRONTEND_DIR%"
call npm install
if "%ERRORLEVEL%" neq "0" (
    echo [ERROR] npm install failed.
    pause
    exit /b 1
)

call npm run build
if "%ERRORLEVEL%" neq "0" (
    echo [ERROR] React build failed.
    pause
    exit /b 1
)

:: Copy React static assets to backend
echo Copying static files to Spring Boot static resources...
if exist "%STATIC_TARGET%" rmdir /s /q "%STATIC_TARGET%"
mkdir "%STATIC_TARGET%"
xcopy /E /I /Y "%DIST_DIR%\*" "%STATIC_TARGET%\" >nul

:: 3. Build Spring Boot Fat JAR
echo [3/4] Building Java backend executable JAR...
cd /d "%BACKEND_DIR%"
call mvn clean package -DskipTests
if "%ERRORLEVEL%" neq "0" (
    echo [ERROR] Maven package failed.
    pause
    exit /b 1
)

:: 4. Package Native Windows Executable (.exe)
echo [4/4] Generating native Windows standalone application via jpackage...
set OUT_DIR=%ROOT_DIR%\dist-app
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

%JPACKAGE_CMD% ^
    --type app-image ^
    --name FlickLAN ^
    --input "%BACKEND_DIR%\target" ^
    --main-jar flicklan-app.jar ^
    --main-class com.flicklan.FlickLanApplication ^
    --dest "%OUT_DIR%" ^
    --win-shortcut ^
    --win-menu

if "%ERRORLEVEL%" equ "0" (
    echo.
    echo ====================================================================
    echo  SUCCESS! Native FlickLAN application generated at:
    echo  %OUT_DIR%\FlickLAN\FlickLAN.exe
    echo  Users can run this .exe without installing Java!
    echo ====================================================================
) else (
    echo [ERROR] jpackage execution failed.
)

pause
exit /b 0

