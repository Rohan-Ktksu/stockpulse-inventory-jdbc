@echo off
setlocal
cd /d "%~dp0"

if not exist out\com\stockpulse\Main.class (
    call compile.bat
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

set "MYSQL_JAR="
for %%f in (lib\mysql-connector*.jar) do set "MYSQL_JAR=%%f"

if "%MYSQL_JAR%"=="" (
    echo [WARN] No mysql-connector*.jar found in lib\
    echo        Place mysql-connector-j-8.x.x.jar (or 9.x.x.jar) in the lib\ folder before running.
    pause
    exit /b 1
)

echo Starting StockPulse with %MYSQL_JAR%...
java -cp "out;lib\*" com.stockpulse.Main
