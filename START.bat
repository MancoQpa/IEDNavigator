@echo off
setlocal enabledelayedexpansion

echo ===================================
echo IED Navigator - IEC 61850 Explorer
echo Desarrollado por Emilio Medina
echo ===================================
echo.

cd /d "%~dp0"

set "LIBDIR=%~dp0lib"
if not exist "%LIBDIR%" mkdir "%LIBDIR%"

:: Verificar dependencias basicas
echo Verificando dependencias...

set "JAR1=%LIBDIR%\iec61850bean-1.9.0.jar"
set "JAR2=%LIBDIR%\jasn1-1.11.3.jar"
set "JAR3=%LIBDIR%\flatlaf-3.2.jar"
set "JAR4=%LIBDIR%\pcap4j-core-1.8.2.jar"
set "JAR5=%LIBDIR%\jna-5.14.0.jar"

set NEED_DOWNLOAD=0
if not exist "%JAR1%" set NEED_DOWNLOAD=1
if not exist "%JAR2%" set NEED_DOWNLOAD=1
if not exist "%JAR3%" set NEED_DOWNLOAD=1
if not exist "%JAR4%" set NEED_DOWNLOAD=1
if not exist "%JAR5%" set NEED_DOWNLOAD=1

if %NEED_DOWNLOAD%==1 (
    echo.
    echo Descargando dependencias...
    powershell -ExecutionPolicy Bypass -File download_deps.ps1
    echo Dependencias descargadas.
) else (
    echo Dependencias OK.
)

:: Compilar con PowerShell script
echo.
echo Compilando...
powershell -ExecutionPolicy Bypass -File compile.ps1

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Compilacion fallida.
    pause
    exit /b 1
)

:: Ejecutar
echo.
echo Iniciando aplicacion...
echo.

set "JAVA_EXE=java"

:: Check JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)

:: Build classpath
set "CP=classes"
for %%j in (lib\*.jar) do set "CP=!CP!;%%j"

:: Run
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -Djna.library.path="%~dp0lib" -cp "%CP%" com.iednavigator.IEDNavigatorApp

if %ERRORLEVEL% neq 0 (
    echo.
    echo La aplicacion termino con errores.
    pause
)
