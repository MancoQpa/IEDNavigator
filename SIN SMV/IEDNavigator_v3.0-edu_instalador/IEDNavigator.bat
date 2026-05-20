@echo off
setlocal enabledelayedexpansion
title IED Navigator v3.0-edu
cd /d "%~dp0"

:: Buscar Java (JRE propio > JAVA_HOME > ubicaciones comunes > PATH)
if exist "%~dp0jre\bin\java.exe"                        ( set "JE=%~dp0jre\bin\java.exe"  & goto :run )
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" ( set "JE=%JAVA_HOME%\bin\java.exe" & goto :run )

for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*")  do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :run )
for /d %%d in ("C:\Program Files\Java\jdk-*")               do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :run )
for /d %%d in ("C:\Program Files\Microsoft\jdk-*")          do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :run )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jre-*")   do if exist "%%d\bin\java.exe" ( set "JE=%%d\bin\java.exe" & goto :run )

where java >nul 2>&1 && ( set "JE=java" & goto :run )

echo.
echo  ERROR: Java no encontrado.
echo  Ejecute INSTALAR.bat como Administrador para instalarlo,
echo  o descargue Java 11+ desde: https://adoptium.net/
echo.
pause
exit /b 1

:run
set "CP=classes"
for %%j in (lib\iec61850bean-1.9.0.jar lib\jasn1-1.11.3.jar lib\asn1bean-1.13.0.jar lib\flatlaf-3.2.jar lib\pcap4j-core-1.8.2.jar lib\pcap4j-packetfactory-static-1.8.2.jar lib\jna-5.14.0.jar lib\jna-platform-5.14.0.jar lib\slf4j-api-2.0.9.jar lib\slf4j-simple-2.0.9.jar) do set "CP=!CP!;%%j"
"%JE%" --enable-native-access=ALL-UNNAMED -Djna.library.path="%~dp0lib" -XX:TieredStopAtLevel=1 -cp "%CP%" com.iednavigator.IEDNavigatorApp %*
if %errorlevel% neq 0 ( echo. & echo La aplicacion termino con errores. & pause )
