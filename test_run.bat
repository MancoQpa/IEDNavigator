@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set "JAVA_EXE=java"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)

set "CP=classes"
for %%j in (lib\*.jar) do set "CP=!CP!;%%j"

echo Ejecutando IED Navigator con salida de consola...
echo.
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -Djna.library.path="%~dp0lib" -cp "%CP%" com.iednavigator.IEDNavigatorApp

echo.
echo Codigo de salida: %ERRORLEVEL%
pause
