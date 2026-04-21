@echo off
setlocal enabledelayedexpansion

echo ===================================
echo IEC 61850 Explorer - Compilacion
echo ===================================

cd /d "%~dp0"

set "LIBDIR=%~dp0lib"
set "CLASSDIR=%~dp0classes"
set "SRCDIR=%~dp0src\main\java"

:: Crear directorios
if not exist "%LIBDIR%" mkdir "%LIBDIR%"
if not exist "%CLASSDIR%" mkdir "%CLASSDIR%"

:: Buscar Java
set "JAVA_HOME="
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JAVA_HOME=%%i"
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Java\jdk-*") do set "JAVA_HOME=%%i"
if not defined JAVA_HOME for /d %%i in ("C:\Program Files\Java\jdk*") do set "JAVA_HOME=%%i"

if not defined JAVA_HOME (
    echo ERROR: Java no encontrado
    echo Instale Java 11+ desde https://adoptium.net/
    pause
    exit /b 1
)

echo Java encontrado: %JAVA_HOME%
set "JAVAC=%JAVA_HOME%\bin\javac.exe"
set "JAVA=%JAVA_HOME%\bin\java.exe"

:: Verificar que los JAR existen
if not exist "%LIBDIR%\iec61850bean-1.9.0.jar" (
    echo.
    echo ERROR: Faltan dependencias JAR
    echo Ejecute primero START.bat para descargarlas
    pause
    exit /b 1
)

:: Construir classpath
set "CP="
for %%j in ("%LIBDIR%\*.jar") do (
    if defined CP (
        set "CP=!CP!;%%j"
    ) else (
        set "CP=%%j"
    )
)

echo.
echo Compilando...

:: Limpiar directorio de clases
del /q "%CLASSDIR%\*.class" 2>nul
rd /s /q "%CLASSDIR%\com" 2>nul

:: Compilar
"%JAVAC%" -d "%CLASSDIR%" -cp "%CP%" -encoding UTF-8 "%SRCDIR%\com\iedexplorer\IEC61850Client.java" "%SRCDIR%\com\iedexplorer\IEC61850Server.java" "%SRCDIR%\com\iedexplorer\IEDExplorerApp.java"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR en compilacion
    pause
    exit /b 1
)

echo Compilacion exitosa!
echo.
echo ===================================
echo Iniciando aplicacion...
echo ===================================
echo.

:: Ejecutar
"%JAVA%" -cp "%CLASSDIR%;%CP%" com.iedexplorer.IEDExplorerApp

pause
