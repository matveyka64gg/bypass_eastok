@echo off
setlocal
cd /d "%~dp0"

if not exist build\classes mkdir build\classes
if not exist output mkdir output

javac -encoding UTF-8 --release 21 --add-modules jdk.httpserver -d build\classes src\main\java\me\funtime\live\FunTimeLive.java
if errorlevel 1 exit /b 1

jar --create --file output\FunTimeLive.jar --main-class me.funtime.live.FunTimeLive -C build\classes .
if errorlevel 1 exit /b 1

echo Build completed: output\FunTimeLive.jar
