# build_zip_installer_v33.ps1 — Empaqueta IEDNavigator para distribución Windows
#
# Uso:
#   .\build_zip_installer_v33.ps1                  # versión 3.4 (default)
#   .\build_zip_installer_v33.ps1 -Version 3.5

param([string]$Version = "3.4")

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$OutputName  = "IEDNavigator_v${Version}_Setup"
$TempDir     = "$ProjectRoot\temp_installer_build"
$AppDir      = "$TempDir\IEDNavigator"
$OutputDir   = "$ProjectRoot\installer\output"
$OutputZip   = "$OutputDir\${OutputName}.zip"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  IED Navigator v$Version - Build Installer ZIP" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

# Step 1: Compile
Write-Host "[1/4] Compilando..." -ForegroundColor Yellow
& "$ProjectRoot\compile.ps1"
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: Compilacion fallida" -ForegroundColor Red; exit 1 }
$classCount = (Get-ChildItem "$ProjectRoot\classes" -Recurse -Filter '*.class').Count
Write-Host "OK - $classCount clases" -ForegroundColor Green

# Step 2: Create structure
Write-Host "[2/4] Creando estructura..." -ForegroundColor Yellow
Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$AppDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$AppDir\lib"     | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir        | Out-Null

Copy-Item -Path "$ProjectRoot\classes\*" -Destination "$AppDir\classes" -Recurse
Copy-Item -Path "$ProjectRoot\lib\*.jar" -Destination "$AppDir\lib"
$dlls = Get-ChildItem "$ProjectRoot\lib\*.dll" -ErrorAction SilentlyContinue
if ($dlls) { Copy-Item -Path "$ProjectRoot\lib\*.dll" -Destination "$AppDir\lib" }
Write-Host "OK" -ForegroundColor Green

# Step 3: Create scripts
Write-Host "[3/4] Creando scripts..." -ForegroundColor Yellow

# IEDNavigator.bat - wrapper minimo
@"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0IEDNavigator.ps1"
"@ | Out-File -FilePath "$AppDir\IEDNavigator.bat" -Encoding ASCII

# IEDNavigator.ps1 - launcher real con elevacion UAC via PowerShell
@"
`$ScriptDir = Split-Path -Parent `$MyInvocation.MyCommand.Path
Set-Location `$ScriptDir

# Auto-elevar a Administrador si es necesario
# Requerido para: puerto 102 (MMS IEC 61850) y captura GOOSE/Npcap
`$id = [Security.Principal.WindowsIdentity]::GetCurrent()
`$pr = [Security.Principal.WindowsPrincipal]`$id
if (-not `$pr.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Solicitando permisos de administrador...'
    Write-Host '(Requerido para puerto 102 y captura GOOSE)'
    `$psi = New-Object System.Diagnostics.ProcessStartInfo 'powershell.exe'
    `$psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -File ```"`$(`$MyInvocation.MyCommand.Path)```""
    `$psi.Verb = 'runas'
    `$psi.WorkingDirectory = `$ScriptDir
    try { [System.Diagnostics.Process]::Start(`$psi) | Out-Null }
    catch { Write-Host "No se pudo elevar: `$_" -ForegroundColor Yellow }
    exit
}

# Buscar Java
`$java = `$null
`$candidates = @(
    "`$env:JAVA_HOME\bin\java.exe",
    'C:\Program Files\Eclipse Adoptium\jdk-*\bin\java.exe',
    'C:\Program Files\Java\jdk-*\bin\java.exe',
    'C:\Program Files\Microsoft\jdk-*\bin\java.exe'
)
foreach (`$p in `$candidates) {
    `$hit = Get-Item `$p -ErrorAction SilentlyContinue | Select-Object -First 1
    if (`$hit) { `$java = `$hit.FullName; break }
}
if (-not `$java) { try { `$java = (Get-Command java -ErrorAction Stop).Source } catch {} }

if (-not `$java) {
    Write-Host ''
    Write-Host 'ERROR: Java no encontrado.' -ForegroundColor Red
    Write-Host 'Ejecute INSTALAR.bat para instalar los requisitos.'
    Write-Host 'O instale Java 11+ desde: https://adoptium.net/'
    Read-Host 'Presione Enter para salir'
    exit 1
}

# Construir classpath
`$classes = Join-Path `$ScriptDir 'classes'
`$jars    = (Get-ChildItem (Join-Path `$ScriptDir 'lib') -Filter '*.jar').FullName
`$cp      = (,`$classes + `$jars) -join ';'

Write-Host "Iniciando IED Navigator v$Version..." -ForegroundColor Cyan
Write-Host "Java: `$java" -ForegroundColor Gray

# Invocar Java directamente (hereda consola -> stdout/stderr visibles)
& `$java '--enable-native-access=ALL-UNNAMED' "-Djna.library.path=`$ScriptDir\lib" '-cp' `$cp 'com.iednavigator.IEDNavigatorApp'

if (`$LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "La aplicacion termino con codigo de error: `$LASTEXITCODE" -ForegroundColor Red
    Read-Host 'Presione Enter para salir'
}
"@ | Out-File -FilePath "$AppDir\IEDNavigator.ps1" -Encoding UTF8

# INSTALAR.bat
@"
@echo off
setlocal enabledelayedexpansion

title IED Navigator - Instalador v$Version

echo.
echo ================================================
echo   IED Navigator v$Version - Instalador
echo   Desarrollado por Emilio Medina
echo ================================================
echo.

cd /d "%~dp0"

:: Auto-elevar a Administrador
net session >nul 2>&1
if %errorlevel% equ 0 goto :run_elevated
if "%1"=="ELEVATED" (
    echo [!] La elevacion fallo. Ejecute manualmente como Administrador.
    pause
    exit /b 1
)
echo Solicitando permisos de administrador...
set "ELEV_PS1=%TEMP%\ied_install_elev.ps1"
echo Start-Process 'cmd.exe' -ArgumentList ('/c "%~f0" ELEVATED') -Verb RunAs -WorkingDirectory '%~dp0' > "%ELEV_PS1%"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ELEV_PS1%"
del "%ELEV_PS1%" >nul 2>&1
exit /b

:run_elevated

echo [1/4] Verificando Java...
set "JAVA_FOUND=0"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" ( set "JAVA_FOUND=1" & set "JAVA_PATH=%JAVA_HOME%\bin\java.exe" )
)
if "!JAVA_FOUND!"=="0" (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if exist "%%d\bin\java.exe" ( set "JAVA_FOUND=1" & set "JAVA_PATH=%%d\bin\java.exe" )
    )
)
if "!JAVA_FOUND!"=="0" ( where java >nul 2>&1 && set "JAVA_FOUND=1" && set "JAVA_PATH=java" )

if "!JAVA_FOUND!"=="1" (
    echo   [OK] Java encontrado: !JAVA_PATH!
) else (
    echo   Descargando Java JDK 21...
    set "JDK_MSI=%TEMP%\adoptium-jdk21.msi"
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/installer/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '!JDK_MSI!' -UseBasicParsing" 2>nul
    if exist "!JDK_MSI!" (
        msiexec /i "!JDK_MSI!" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome INSTALLDIR="C:\Program Files\Eclipse Adoptium\jdk-21" /quiet /norestart
        echo   [OK] Java 21 instalado
        del "!JDK_MSI!" 2>nul
    ) else (
        echo   [ERROR] No se pudo descargar Java. Instale desde: https://adoptium.net/
    )
)

echo [2/4] Verificando Npcap...
set "NPCAP_FOUND=0"
if exist "%SystemRoot%\System32\Npcap\NPFInstall.exe" set "NPCAP_FOUND=1"
if exist "%SystemRoot%\System32\wpcap.dll" set "NPCAP_FOUND=1"
reg query "HKLM\SOFTWARE\Npcap" >nul 2>&1 && set "NPCAP_FOUND=1"

if "!NPCAP_FOUND!"=="1" (
    echo   [OK] Npcap ya instalado
) else (
    echo   Descargando Npcap desde npcap.com...
    set "NPCAP_EXE=%TEMP%\npcap-setup.exe"
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest -Uri 'https://npcap.com/dist/npcap-1.79.exe' -OutFile '!NPCAP_EXE!' -UseBasicParsing" 2>nul
    if exist "!NPCAP_EXE!" (
        "!NPCAP_EXE!" /S /winpcap_mode=yes
        del "!NPCAP_EXE!" 2>nul
        echo   [OK] Npcap instalado
    ) else (
        echo   [!!] No se pudo descargar Npcap. Instale desde: https://npcap.com/
    )
)

echo [3/4] Configurando Firewall (puerto 102)...
netsh advfirewall firewall delete rule name="IED Navigator MMS 102" >nul 2>&1
netsh advfirewall firewall add rule name="IED Navigator MMS 102" protocol=TCP dir=in localport=102 action=allow >nul 2>&1
echo   [OK] Regla firewall TCP 102

echo [4/4] Creando acceso directo...
set "SHORTCUT=%USERPROFILE%\Desktop\IED Navigator.lnk"
powershell -Command "`$ws = New-Object -ComObject WScript.Shell; `$s = `$ws.CreateShortcut('%SHORTCUT%'); `$s.TargetPath = '%~dp0IEDNavigator.bat'; `$s.WorkingDirectory = '%~dp0'; `$s.Description = 'IED Navigator v$Version'; `$s.Save()" 2>nul
if exist "%USERPROFILE%\Desktop\IED Navigator.lnk" ( echo   [OK] Acceso directo creado ) else ( echo   [AVISO] No se pudo crear acceso directo )

echo.
echo ================================================
echo   LISTO. Ejecute IEDNavigator.bat para iniciar.
echo ================================================
echo.
pause
"@ | Out-File -FilePath "$AppDir\INSTALAR.bat" -Encoding ASCII

# LEAME.txt
@"
IED Navigator v$Version
Desarrollado por: Emilio Medina

INSTALACION RAPIDA:
1. Extraiga el ZIP a una carpeta
2. Ejecute INSTALAR.bat  (se auto-eleva a Administrador)
3. Doble clic en IEDNavigator.bat para iniciar

REQUISITOS:
- Windows 10/11 (64-bit)
- Java 11+ (INSTALAR.bat lo descarga automaticamente)
- Npcap para captura GOOSE/SV (INSTALAR.bat lo descarga)

FUNCIONALIDADES:
- Cliente/Servidor IEC 61850 MMS
- GOOSE Subscriber y Publisher
- Parsing SCL/ICD/CID/SCD
- Reportes URCB/BRCB
- Bridge GOOSE sobre UDP
"@ | Out-File -FilePath "$AppDir\LEAME.txt" -Encoding UTF8

Write-Host "OK" -ForegroundColor Green

# Step 4: ZIP
Write-Host "[4/4] Creando ZIP..." -ForegroundColor Yellow
Remove-Item -Path $OutputZip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path "$AppDir" -DestinationPath $OutputZip -Force
Remove-Item -Path $TempDir -Recurse -Force

$zipSizeMB = [math]::Round((Get-Item $OutputZip).Length / 1MB, 2)
Write-Host "================================================" -ForegroundColor Green
Write-Host "  ZIP CREADO: $OutputZip" -ForegroundColor Green
Write-Host "  Tamano: $zipSizeMB MB" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
