@echo off
echo =================================
echo IEC 61850 Explorer - Build
echo =================================

echo.
echo Compilando con Maven...
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: La compilacion fallo
    pause
    exit /b 1
)

echo.
echo =================================
echo Compilacion exitosa!
echo =================================
echo.
echo Para ejecutar:
echo   java -jar target\iec61850-explorer-1.0.0-jar-with-dependencies.jar
echo.
echo O ejecutar: run.bat
echo.
pause
