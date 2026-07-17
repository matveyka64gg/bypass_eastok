@echo off
cd /d "%~dp0"

if not exist output\FunTimeLive.jar call build.bat
if errorlevel 1 exit /b 1

java --add-modules jdk.httpserver -jar output\FunTimeLive.jar
