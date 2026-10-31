@echo off
echo Starting FlickLAN Development Environment...
echo 1. Starting React Frontend dev server on http://localhost:65000
cd /d "%~dp0..\frontend"
start "FlickLAN Frontend Dev (Vite)" cmd /k "npm run dev"

echo 2. Starting Java Backend server on http://localhost:65005
cd /d "%~dp0..\backend"
start "FlickLAN Backend Dev (Spring Boot)" cmd /k "mvn spring-boot:run"

echo Both development servers launched!

