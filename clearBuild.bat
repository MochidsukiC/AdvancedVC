@echo off
echo Stopping Gradle daemons...
call gradlew.bat --stop 2>nul

echo Waiting for processes to release files...
timeout /t 2 /nobreak >nul

echo Removing build directories...
powershell -ExecutionPolicy Bypass -Command "Remove-Item -Recurse -Force -Path 'C:\Users\dora2\IdeaProjects\AdvancedVC\build' -ErrorAction SilentlyContinue"

echo Build directories cleared successfully.