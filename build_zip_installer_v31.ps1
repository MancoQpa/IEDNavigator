# IED Navigator - Build Unattended ZIP Installer v3.1
# Developed by Emilio Medina
# Cambios vs 3.0:
#   - Launcher con auto-elevacion UAC (necesaria para puerto 102 y captura GOOSE)
#   - Leyenda FC/CDC en dialogo de ayuda
#   - Iconos diferenciados por grupo de LN

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Version     = "3.1"
$OutputName  = "IEDNavigator_v${Version}_Setup"
$TempDir     = "$ProjectRoot\temp_installer_v31"
$AppDir      = "$TempDir\IEDNavigator"
$OutputDir   = "$ProjectRoot\installer\output"
$OutputZip   = "$OutputDir\$OutputName.zip"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  IED Navigator v$Version - Build Installer ZIP" -ForegroundColor Cyan
Write-Host "  Desarrollado por Emilio Medina" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Compile
Write-Host "[1/4] Compilando proyecto..." -ForegroundColor Yellow
& "$ProjectRoot\compile.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Compilacion fallida" -ForegroundColor Red
    exit 1
}
Write-Host "OK" -ForegroundColor Green

# Step 2: Create structure
Write-Host "[2/4] Creando estructura..." -ForegroundColor Yellow
Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$AppDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$AppDir\lib"     | Out-Null
New-Item -ItemType Directory -Force -Path "$AppDir\src"     | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir        | Out-Null

# Copy compiled classes
Copy-Item -Path "$ProjectRoot\classes\*" -Destination "$AppDir\classes" -Recurse
Write-Host "  classes copiadas" -ForegroundColor Gray

# Copy JARs
Copy-Item -Path "$ProjectRoot\lib\*.jar" -Destination "$AppDir\lib"
Write-Host "  JARs copiadas ($((Get-ChildItem "$AppDir\lib" -Filter '*.jar').Count))" -ForegroundColor Gray

# Copy DLLs
$dlls = Get-ChildItem "$ProjectRoot\lib\*.dll" -ErrorAction SilentlyContinue
if ($dlls) {
    Copy-Item -Path "$ProjectRoot\lib\*.dll" -Destination "$AppDir\lib"
    Write-Host "  DLLs copiadas ($($dlls.Count))" -ForegroundColor Gray
}

# Copy Npcap installer if available
$npcapSrc = "$ProjectRoot\installer\prereq\npcap-1.79.exe"
if (Test-Path $npcapSrc) {
    Copy-Item -Path $npcapSrc -Destination "$AppDir\npcap-1.79.exe"
    Write-Host "  Npcap installer incluido" -ForegroundColor Gray
} else {
    Write-Host "  AVISO: Npcap no encontrado en prereq\, el usuario debera instalarlo manualmente" -ForegroundColor Yellow
}

# Copy source code for reference
Copy-Item -Path "$ProjectRoot\src\*"      -Destination "$AppDir\src" -Recurse
Copy-Item -Path "$ProjectRoot\compile.ps1" -Destination "$AppDir\"
Write-Host "  Codigo fuente copiado" -ForegroundColor Gray
Write-Host "OK" -ForegroundColor Green

# Step 3: Create launcher and installer scripts
Write-Host "[3/4] Creando scripts..." -ForegroundColor Yellow

# ── Main launcher: IEDNavigator.bat ─────────────────────────────────────────
# NOVEDAD v3.1: bloque de auto-elevacion UAC al inicio
#   - Permite escuchar en puerto 102 (privilegiado en Windows)
#   - Necesario para captura GOOSE con Npcap
@"
@echo off
setlocal enabledelayedexpansion

:: IED Navigator v$Version - Launcher
:: Desarrollado por Emilio Medina
:: NOTA: Se ejecuta como Administrador para permitir puerto 102 y captura GOOSE

title IED Navigator v$Version

cd /d "%~dp0"

:: ── Auto-elevar a Administrador ────────────────────────────────────────────
:: Necesario para: puerto 102 (MMS estandar IEC 61850) y captura GOOSE/Npcap
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando permisos de administrador...
    echo (Requerido para puerto 102 y captura GOOSE)
    echo.
    powershell -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs -WorkingDirectory '%~dp0'"
    exit /b
)
:: ──────────────────────────────────────────────────────────────────────────

:: Check bundled JRE first
if exist "%~dp0jre\bin\java.exe" (
    set "JAVA_EXE=%~dp0jre\bin\java.exe"
    goto :run
)

:: Check JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        goto :run
    )
)

:: Search common Java locations
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_EXE=%%d\bin\java.exe"
        goto :run
    )
)
for /d %%d in ("C:\Program Files\Java\jdk-*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_EXE=%%d\bin\java.exe"
        goto :run
    )
)
for /d %%d in ("C:\Program Files\Microsoft\jdk-*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_EXE=%%d\bin\java.exe"
        goto :run
    )
)

:: Try PATH
where java >nul 2>&1
if %errorlevel% equ 0 (
    set "JAVA_EXE=java"
    goto :run
)

:: Not found
echo.
echo ERROR: Java no encontrado.
echo.
echo Ejecute INSTALAR.bat para instalar los requisitos,
echo o instale Java 11+ manualmente desde: https://adoptium.net/
echo.
pause
exit /b 1

:run
echo Iniciando IED Navigator v$Version...

:: Build classpath
set "CP=classes"
for %%j in (lib\*.jar) do set "CP=!CP!;%%j"

:: Run
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -Djna.library.path="%~dp0lib" -cp "%CP%" com.iednavigator.IEDNavigatorApp %*

if %errorlevel% neq 0 (
    echo.
    echo La aplicacion termino con errores.
    pause
)
"@ | Out-File -FilePath "$AppDir\IEDNavigator.bat" -Encoding ASCII

# ── Unattended installer: INSTALAR.bat ───────────────────────────────────────
@"
@echo off
setlocal enabledelayedexpansion

:: ================================================
:: IED Navigator v$Version - Instalador Desatendido
:: Desarrollado por Emilio Medina
:: ================================================

title IED Navigator - Instalador

echo.
echo ================================================
echo   IED Navigator v$Version
echo   Instalador Desatendido
echo   Desarrollado por Emilio Medina
echo ================================================
echo.

:: Check admin privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Se requieren privilegios de administrador.
    echo     Haga clic derecho en INSTALAR.bat y seleccione
    echo     "Ejecutar como administrador"
    echo.
    pause
    exit /b 1
)

cd /d "%~dp0"

echo [1/5] Verificando Java...
echo.

set "JAVA_FOUND=0"
set "JAVA_PATH="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_FOUND=1"
        set "JAVA_PATH=%JAVA_HOME%\bin\java.exe"
    )
)

if "!JAVA_FOUND!"=="0" (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if exist "%%d\bin\java.exe" (
            set "JAVA_FOUND=1"
            set "JAVA_PATH=%%d\bin\java.exe"
        )
    )
)

if "!JAVA_FOUND!"=="0" (
    for /d %%d in ("C:\Program Files\Java\jdk-*") do (
        if exist "%%d\bin\java.exe" (
            set "JAVA_FOUND=1"
            set "JAVA_PATH=%%d\bin\java.exe"
        )
    )
)

if "!JAVA_FOUND!"=="0" (
    where java >nul 2>&1
    if !errorlevel! equ 0 (
        set "JAVA_FOUND=1"
        set "JAVA_PATH=java"
    )
)

if "!JAVA_FOUND!"=="1" (
    echo   [OK] Java encontrado: !JAVA_PATH!
) else (
    echo   [!!] Java NO encontrado.
    echo.
    echo   Descargando Java JDK 21 (Adoptium)...
    echo.

    set "JDK_MSI=%TEMP%\adoptium-jdk21.msi"
    set "JDK_URL=https://api.adoptium.net/v3/installer/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"

    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '!JDK_URL!' -OutFile '!JDK_MSI!' -UseBasicParsing" 2>nul

    if exist "!JDK_MSI!" (
        echo   Instalando Java silenciosamente...
        msiexec /i "!JDK_MSI!" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome INSTALLDIR="C:\Program Files\Eclipse Adoptium\jdk-21" /quiet /norestart
        echo   [OK] Java instalado
        del "!JDK_MSI!" 2>nul
    ) else (
        echo   [ERROR] No se pudo descargar Java.
        echo   Por favor instale manualmente desde: https://adoptium.net/
        echo.
        set "INSTALL_ERROR=1"
    )
)

echo.
echo [2/5] Verificando Npcap...
echo.

set "NPCAP_FOUND=0"
if exist "%SystemRoot%\System32\Npcap\NPFInstall.exe" set "NPCAP_FOUND=1"
if exist "%SystemRoot%\System32\wpcap.dll" set "NPCAP_FOUND=1"
reg query "HKLM\SOFTWARE\Npcap" >nul 2>&1 && set "NPCAP_FOUND=1"
reg query "HKLM\SOFTWARE\WOW6432Node\Npcap" >nul 2>&1 && set "NPCAP_FOUND=1"

if "!NPCAP_FOUND!"=="1" (
    echo   [OK] Npcap/WinPcap ya instalado
) else (
    if exist "%~dp0npcap-1.79.exe" (
        echo   Instalando Npcap silenciosamente...
        "%~dp0npcap-1.79.exe" /S /winpcap_mode=yes
        echo   [OK] Npcap instalado
    ) else (
        echo   [!!] Npcap no incluido en el paquete.
        echo   Por favor instale desde: https://npcap.com/
        echo   (Necesario para captura GOOSE/SV)
        echo.
    )
)

echo.
echo [3/5] Habilitando puerto 102 en el Firewall de Windows...
echo.

netsh advfirewall firewall delete rule name="IED Navigator MMS 102" >nul 2>&1
netsh advfirewall firewall add rule name="IED Navigator MMS 102" protocol=TCP dir=in localport=102 action=allow >nul 2>&1
if %errorlevel% equ 0 (
    echo   [OK] Regla de firewall creada (TCP 102 entrante)
) else (
    echo   [AVISO] No se pudo crear la regla de firewall. Puede hacerlo manualmente.
)

echo.
echo [4/5] Creando acceso directo en Escritorio...
echo.

set "SHORTCUT=%USERPROFILE%\Desktop\IED Navigator.lnk"
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%SHORTCUT%'); $s.TargetPath = '%~dp0IEDNavigator.bat'; $s.WorkingDirectory = '%~dp0'; $s.Description = 'IED Navigator v$Version - IEC 61850 Explorer'; $s.Save()" 2>nul

if exist "!SHORTCUT!" (
    echo   [OK] Acceso directo creado en el Escritorio
) else (
    echo   [AVISO] No se pudo crear acceso directo
)

echo.
echo [5/5] Verificacion final...
echo.

set "JAVA_OK=0"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_OK=1"
)
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
    if exist "%%d\bin\java.exe" set "JAVA_OK=1"
)
where java >nul 2>&1 && set "JAVA_OK=1"

set "NPCAP_OK=0"
if exist "%SystemRoot%\System32\Npcap\NPFInstall.exe" set "NPCAP_OK=1"
if exist "%SystemRoot%\System32\wpcap.dll" set "NPCAP_OK=1"
reg query "HKLM\SOFTWARE\Npcap" >nul 2>&1 && set "NPCAP_OK=1"

echo   Java:  !JAVA_OK! (1=OK, 0=Falta)
echo   Npcap: !NPCAP_OK! (1=OK, 0=Falta)
echo.

if "!JAVA_OK!"=="1" if "!NPCAP_OK!"=="1" (
    echo ================================================
    echo   INSTALACION COMPLETADA EXITOSAMENTE
    echo ================================================
    echo.
    echo   Ejecute IEDNavigator.bat para iniciar
    echo   o use el acceso directo en el Escritorio.
    echo.
    echo   Puerto 102 habilitado en firewall.
    echo   El launcher se auto-eleva a Administrador.
    echo.
) else (
    echo ================================================
    echo   INSTALACION PARCIAL
    echo ================================================
    echo.
    if "!JAVA_OK!"=="0" echo   [!] Instale Java desde: https://adoptium.net/
    if "!NPCAP_OK!"=="0" echo   [!] Instale Npcap desde: https://npcap.com/
    echo.
)

echo Presione una tecla para salir...
pause >nul
"@ | Out-File -FilePath "$AppDir\INSTALAR.bat" -Encoding ASCII

# ── LEAME.txt ────────────────────────────────────────────────────────────────
@"
================================================
IED Navigator v$Version
Desarrollado por: Emilio Medina
================================================

NOVEDADES v3.1 respecto a v3.0:
---------------------------------
- Launcher auto-elevado a Administrador (UAC)
  Permite usar puerto 102 (MMS estandar IEC 61850)
  y captura GOOSE con Npcap sin configuracion extra
- INSTALAR.bat agrega regla de Firewall para TCP 102
- Leyenda de Functional Constraints y CDC en Ayuda
- Iconos diferenciados por grupo de Nodo Logico

INSTALACION RAPIDA (Desatendida):
---------------------------------
1. Extraiga todo el contenido del ZIP a una carpeta
2. Haga clic derecho en INSTALAR.bat > "Ejecutar como administrador"
3. El instalador verifica e instala automaticamente:
   - Java JDK 21 (si no esta instalado)
   - Npcap (si se incluye el instalador)
   - Regla de Firewall para TCP 102
   - Acceso directo en el Escritorio

EJECUCION:
----------
- Doble clic en IEDNavigator.bat
  (solicita UAC automaticamente)
- O use el acceso directo "IED Navigator" en el Escritorio

REQUISITOS:
-----------
- Windows 10/11 (64-bit)
- Java 11 o superior (se instala automaticamente)
- Npcap para captura GOOSE/SV
- Puerto 102 debe estar libre (MMS estandar IEC 61850)

PUERTOS:
--------
- 102  : MMS estandar IEC 61850 (requiere Administrador)
- 10102: Alternativa sin privilegios (simulador local)
El campo "Puerto" en la pantalla Servidor acepta cualquier valor.

FUNCIONALIDADES:
----------------
- Cliente/Servidor IEC 61850 MMS (puerto 102 y otros)
- GOOSE Subscriber y Publisher (pcap4j)
- Publicacion simultanea de multiples GoCBs
- Simulacion IED-to-IED (dos instancias)
- Carga de archivos SCL/SCD/CID/ICD
- Sampled Values (requiere libiec61850.dll)
- GOOSE sobre UDP (para WiFi/Hotspot)
- Leyenda FC y CDC integrada en Ayuda

MODO IED-TO-IED:
-----------------
1. Abra dos instancias de IED Navigator
2. En cada una, cargue el mismo archivo SCD
3. Seleccione un IED diferente en cada instancia
4. Use "Publicar Todos" para publicar todos los GoCBs
5. Cada instancia vera los GOOSE de la otra
"@ | Out-File -FilePath "$AppDir\LEAME.txt" -Encoding UTF8

Write-Host "OK" -ForegroundColor Green

# Step 4: Create ZIP
Write-Host "[4/4] Creando ZIP..." -ForegroundColor Yellow

Remove-Item -Path $OutputZip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path "$AppDir" -DestinationPath $OutputZip -Force

$zipSize = (Get-Item $OutputZip).Length / 1MB

# Cleanup temp
Remove-Item -Path $TempDir -Recurse -Force

Write-Host "OK" -ForegroundColor Green
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  ZIP CREADO EXITOSAMENTE!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Archivo: $OutputZip" -ForegroundColor Cyan
Write-Host "  Tamano:  $([math]::Round($zipSize, 1)) MB" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Para instalar en otra maquina:" -ForegroundColor White
Write-Host "  1. Extraer el ZIP" -ForegroundColor White
Write-Host "  2. Ejecutar INSTALAR.bat como Administrador" -ForegroundColor White
Write-Host ""
