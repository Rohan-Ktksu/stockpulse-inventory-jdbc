@echo off
setlocal
cd /d "%~dp0"

if not exist out mkdir out

echo Compiling StockPulse Core Java source files...
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
set ERR=%ERRORLEVEL%
del sources.txt

if %ERR% neq 0 (
    echo [ERROR] Compilation failed.
    exit /b %ERR%
)

echo [OK] Compiled successfully into out\
