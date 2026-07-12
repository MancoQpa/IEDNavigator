# IED Navigator - Build Dual Installer (Windows + Linux)
# Developed by Emilio Medina

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Version = "2.2"
# Note: $Version is interpolated into all script strings below via PowerShell expansion
$TempDir = "$ProjectRoot\temp_dual"
$WinDir = "$TempDir\win_installer"
$LinDir = "$TempDir\linux_installer"
$OutputZip = "$ProjectRoot\ied_navigator_installer.zip"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "  IED Navigator v$Version - Dual Installer" -ForegroundColor Cyan
Write-Host "  Windows + Linux Mint" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Compile
Write-Host "[1/5] Compilando..." -ForegroundColor Yellow
& "$ProjectRoot\compile.ps1"
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR" -ForegroundColor Red; exit 1 }
Write-Host "OK" -ForegroundColor Green

# Step 2: Clean and create structure
Write-Host "[2/5] Creando estructura..." -ForegroundColor Yellow
Remove-Item -Path $TempDir -Recurse -Force -ErrorAction SilentlyContinue
foreach ($d in @("$WinDir\classes","$WinDir\lib","$WinDir\src",
                 "$LinDir\classes","$LinDir\lib","$LinDir\src")) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}
Write-Host "OK" -ForegroundColor Green

# Step 3: Copy common files to both
Write-Host "[3/5] Copiando archivos..." -ForegroundColor Yellow
foreach ($dir in @($WinDir, $LinDir)) {
    Copy-Item -Path "$ProjectRoot\classes\*" -Destination "$dir\classes" -Recurse
    Copy-Item -Path "$ProjectRoot\lib\*.jar" -Destination "$dir\lib"
    Copy-Item -Path "$ProjectRoot\src\*" -Destination "$dir\src" -Recurse
    Copy-Item -Path "$ProjectRoot\compile.ps1" -Destination "$dir\"
}
# Windows-specific
Copy-Item -Path "$ProjectRoot\lib\*.dll" -Destination "$WinDir\lib" -ErrorAction SilentlyContinue
$npcapSrc = "$ProjectRoot\installer\prereq\npcap-1.79.exe"
if (Test-Path $npcapSrc) {
    Copy-Item -Path $npcapSrc -Destination "$WinDir\npcap-1.79.exe"
    Write-Host "  Npcap incluido" -ForegroundColor Gray
}
Write-Host "OK" -ForegroundColor Green

# Step 4: Create scripts
Write-Host "[4/5] Creando scripts..." -ForegroundColor Yellow

# ======================== WINDOWS ========================

# Windows launcher
@"
@echo off
setlocal enabledelayedexpansion
title IED Navigator v$Version
cd /d "%~dp0"

if exist "%~dp0jre\bin\java.exe" ( set "JAVA_EXE=%~dp0jre\bin\java.exe" & goto :run )
if defined JAVA_HOME ( if exist "%JAVA_HOME%\bin\java.exe" ( set "JAVA_EXE=%JAVA_HOME%\bin\java.exe" & goto :run ) )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do ( if exist "%%d\bin\java.exe" ( set "JAVA_EXE=%%d\bin\java.exe" & goto :run ) )
for /d %%d in ("C:\Program Files\Java\jdk-*") do ( if exist "%%d\bin\java.exe" ( set "JAVA_EXE=%%d\bin\java.exe" & goto :run ) )
for /d %%d in ("C:\Program Files\Microsoft\jdk-*") do ( if exist "%%d\bin\java.exe" ( set "JAVA_EXE=%%d\bin\java.exe" & goto :run ) )
where java >nul 2>&1 && ( set "JAVA_EXE=java" & goto :run )

echo ERROR: Java no encontrado. Ejecute INSTALAR.bat
pause
exit /b 1

:run
echo Iniciando IED Navigator...
set "CP=classes"
for %%j in (lib\*.jar) do set "CP=!CP!;%%j"
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -Djna.library.path="%~dp0lib" -cp "%CP%" com.iednavigator.IEDNavigatorApp %*
if %errorlevel% neq 0 ( echo La aplicacion termino con errores. & pause )
"@ | Out-File -FilePath "$WinDir\IEDNavigator.bat" -Encoding ASCII

# Windows installer
@"
@echo off
setlocal enabledelayedexpansion
title IED Navigator - Instalador
echo.
echo ================================================
echo   IED Navigator v$Version
echo   Instalador Desatendido (Windows)
echo   Desarrollado por Emilio Medina
echo ================================================
echo.

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Se requieren privilegios de administrador.
    echo     Clic derecho en INSTALAR.bat ^> "Ejecutar como administrador"
    pause & exit /b 1
)

cd /d "%~dp0"

echo [1/4] Verificando Java...
set "JAVA_FOUND=0"
if defined JAVA_HOME ( if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_FOUND=1" )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do ( if exist "%%d\bin\java.exe" set "JAVA_FOUND=1" )
for /d %%d in ("C:\Program Files\Java\jdk-*") do ( if exist "%%d\bin\java.exe" set "JAVA_FOUND=1" )
where java >nul 2>&1 && set "JAVA_FOUND=1"

if "!JAVA_FOUND!"=="1" (
    echo   [OK] Java encontrado
) else (
    echo   [..] Descargando Java JDK 21...
    set "JDK_MSI=%TEMP%\adoptium-jdk21.msi"
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/installer/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '!JDK_MSI!' -UseBasicParsing" 2>nul
    if exist "!JDK_MSI!" (
        echo   [..] Instalando Java...
        msiexec /i "!JDK_MSI!" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome INSTALLDIR="C:\Program Files\Eclipse Adoptium\jdk-21" /quiet /norestart
        echo   [OK] Java instalado
        del "!JDK_MSI!" 2>nul
    ) else (
        echo   [ERROR] No se pudo descargar. Instale desde: https://adoptium.net/
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
    if exist "%~dp0npcap-1.79.exe" (
        echo   [..] Instalando Npcap...
        "%~dp0npcap-1.79.exe" /S /winpcap_mode=yes
        echo   [OK] Npcap instalado
    ) else (
        echo   [!!] Instale Npcap desde: https://npcap.com/
    )
)

echo [3/4] Creando acceso directo...
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut([Environment]::GetFolderPath('Desktop')+'\IED Navigator.lnk'); $s.TargetPath = '%~dp0IEDNavigator.bat'; $s.WorkingDirectory = '%~dp0'; $s.Description = 'IED Navigator - IEC 61850 Explorer'; $s.Save()" 2>nul
echo   [OK] Acceso directo creado

echo [4/4] Verificacion final...
set "J=0" & set "N=0"
if defined JAVA_HOME ( if exist "%JAVA_HOME%\bin\java.exe" set "J=1" )
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do ( if exist "%%d\bin\java.exe" set "J=1" )
where java >nul 2>&1 && set "J=1"
if exist "%SystemRoot%\System32\Npcap\NPFInstall.exe" set "N=1"
if exist "%SystemRoot%\System32\wpcap.dll" set "N=1"
reg query "HKLM\SOFTWARE\Npcap" >nul 2>&1 && set "N=1"

echo.
if "!J!"=="1" if "!N!"=="1" (
    echo ================================================
    echo   INSTALACION COMPLETADA EXITOSAMENTE
    echo ================================================
    echo   Ejecute IEDNavigator.bat o use el acceso directo.
) else (
    echo   INSTALACION PARCIAL
    if "!J!"=="0" echo   [!] Falta Java - https://adoptium.net/
    if "!N!"=="0" echo   [!] Falta Npcap - https://npcap.com/
)
echo.
pause
"@ | Out-File -FilePath "$WinDir\INSTALAR.bat" -Encoding ASCII

# Windows README
@"
IED Navigator v$Version - Windows
=================================
Desarrollado por: Emilio Medina

INSTALACION:
  1. Clic derecho en INSTALAR.bat > Ejecutar como administrador
  2. Ejecutar IEDNavigator.bat

REQUISITOS (se instalan automaticamente):
  - Java 11+
  - Npcap (captura GOOSE/SV)
"@ | Out-File -FilePath "$WinDir\LEAME.txt" -Encoding UTF8

# ======================== LINUX ========================

# Linux launcher
@"
#!/bin/bash
# IED Navigator v$Version - Linux Launcher
# Developed by Emilio Medina

SCRIPT_DIR="`$(cd "`$(dirname "`$0")" && pwd)"
cd "`$SCRIPT_DIR"

# Find Java
JAVA_EXE=""
if [ -x "`$SCRIPT_DIR/jre/bin/java" ]; then
    JAVA_EXE="`$SCRIPT_DIR/jre/bin/java"
elif [ -n "`$JAVA_HOME" ] && [ -x "`$JAVA_HOME/bin/java" ]; then
    JAVA_EXE="`$JAVA_HOME/bin/java"
elif command -v java &>/dev/null; then
    JAVA_EXE="java"
else
    echo ""
    echo "ERROR: Java no encontrado."
    echo "Ejecute: sudo ./instalar.sh"
    echo "O instale manualmente: sudo apt install default-jdk"
    exit 1
fi

echo "Iniciando IED Navigator..."
echo "Java: `$JAVA_EXE"

# Build classpath
CP="classes"
for jar in lib/*.jar; do
    CP="`$CP:`$jar"
done

# Run (use sudo for raw packet capture if needed)
if [ "`$(id -u)" -eq 0 ]; then
    "`$JAVA_EXE" --enable-native-access=ALL-UNNAMED -Djna.library.path="`$SCRIPT_DIR/lib" -cp "`$CP" com.iednavigator.IEDNavigatorApp "`$@"
else
    echo ""
    echo "NOTA: Para captura GOOSE ejecute con sudo:"
    echo "  sudo ./iednavigator.sh"
    echo ""
    echo "Iniciando sin privilegios (captura GOOSE puede no funcionar)..."
    echo ""
    "`$JAVA_EXE" --enable-native-access=ALL-UNNAMED -Djna.library.path="`$SCRIPT_DIR/lib" -cp "`$CP" com.iednavigator.IEDNavigatorApp "`$@"
fi
"@ | Out-File -FilePath "$LinDir\iednavigator.sh" -Encoding UTF8 -NoNewline

# Linux installer
@"
#!/bin/bash
# IED Navigator v$Version - Instalador para Linux Mint / Ubuntu / Debian
# Developed by Emilio Medina

echo ""
echo "================================================"
echo "  IED Navigator v$Version"
echo "  Instalador para Linux Mint / Ubuntu / Debian"
echo "  Desarrollado por Emilio Medina"
echo "================================================"
echo ""

# Check root
if [ "`$(id -u)" -ne 0 ]; then
    echo "[!] Se requiere ejecutar como root:"
    echo "    sudo ./instalar.sh"
    echo ""
    exit 1
fi

SCRIPT_DIR="`$(cd "`$(dirname "`$0")" && pwd)"

echo "[1/5] Actualizando repositorios..."
apt-get update -qq
echo "  [OK]"

echo "[2/5] Verificando Java..."
if command -v java &>/dev/null; then
    JAVA_VER=`$(java -version 2>&1 | head -1)
    echo "  [OK] Java encontrado: `$JAVA_VER"
else
    echo "  [..] Instalando OpenJDK 21..."
    apt-get install -y -qq openjdk-21-jdk 2>/dev/null || apt-get install -y -qq default-jdk
    if command -v java &>/dev/null; then
        echo "  [OK] Java instalado"
    else
        echo "  [ERROR] No se pudo instalar Java"
        echo "  Intente: sudo apt install default-jdk"
    fi
fi

echo "[3/5] Verificando libpcap..."
if dpkg -l | grep -q libpcap-dev 2>/dev/null || ldconfig -p | grep -q libpcap 2>/dev/null; then
    echo "  [OK] libpcap encontrado"
else
    echo "  [..] Instalando libpcap-dev..."
    apt-get install -y -qq libpcap-dev
    echo "  [OK] libpcap instalado"
fi

echo "[4/5] Configurando permisos..."
chmod +x "`$SCRIPT_DIR/iednavigator.sh"
chmod +x "`$SCRIPT_DIR/compilar.sh"

# Allow Java to capture raw packets without root
JAVA_PATH=`$(readlink -f `$(which java) 2>/dev/null)
if [ -n "`$JAVA_PATH" ]; then
    setcap cap_net_raw,cap_net_admin=eip "`$JAVA_PATH" 2>/dev/null
    if [ `$? -eq 0 ]; then
        echo "  [OK] Permisos de captura configurados (no necesita sudo)"
    else
        echo "  [AVISO] No se pudo configurar setcap, necesitara sudo para captura GOOSE"
    fi
fi
echo "  [OK] Scripts ejecutables"

echo "[5/5] Creando acceso directo..."
DESKTOP_FILE="/usr/share/applications/iednavigator.desktop"
cat > "`$DESKTOP_FILE" << DESKTOP_EOF
[Desktop Entry]
Name=IED Navigator
Comment=IEC 61850 Explorer - Developed by Emilio Medina
Exec=`$SCRIPT_DIR/iednavigator.sh
Terminal=true
Type=Application
Categories=Development;Engineering;
DESKTOP_EOF
chmod 644 "`$DESKTOP_FILE"

# Also copy to user desktop if possible
REAL_USER=`${SUDO_USER:-`$USER}
REAL_HOME=`$(getent passwd "`$REAL_USER" | cut -d: -f6)
if [ -d "`$REAL_HOME/Desktop" ]; then
    cp "`$DESKTOP_FILE" "`$REAL_HOME/Desktop/iednavigator.desktop"
    chown "`$REAL_USER:`$REAL_USER" "`$REAL_HOME/Desktop/iednavigator.desktop"
    chmod +x "`$REAL_HOME/Desktop/iednavigator.desktop"
    echo "  [OK] Acceso directo creado en Desktop"
elif [ -d "`$REAL_HOME/Escritorio" ]; then
    cp "`$DESKTOP_FILE" "`$REAL_HOME/Escritorio/iednavigator.desktop"
    chown "`$REAL_USER:`$REAL_USER" "`$REAL_HOME/Escritorio/iednavigator.desktop"
    chmod +x "`$REAL_HOME/Escritorio/iednavigator.desktop"
    echo "  [OK] Acceso directo creado en Escritorio"
else
    echo "  [OK] Acceso directo creado en menu de aplicaciones"
fi

echo ""
echo "================================================"
echo "  INSTALACION COMPLETADA"
echo "================================================"
echo ""
echo "  Para ejecutar:"
echo "    ./iednavigator.sh"
echo ""
echo "  Para captura GOOSE (si no se configuro setcap):"
echo "    sudo ./iednavigator.sh"
echo ""
"@ | Out-File -FilePath "$LinDir\instalar.sh" -Encoding UTF8 -NoNewline

# Linux compile script
@"
#!/bin/bash
# IED Navigator - Compilar desde fuentes en Linux
# Developed by Emilio Medina

SCRIPT_DIR="`$(cd "`$(dirname "`$0")" && pwd)"
SRCDIR="`$SCRIPT_DIR/src/main/java"
LIBDIR="`$SCRIPT_DIR/lib"
CLASSDIR="`$SCRIPT_DIR/classes"

mkdir -p "`$CLASSDIR"

# Build classpath
CP=""
for jar in `$LIBDIR/*.jar; do
    if [ -n "`$CP" ]; then CP="`$CP:"; fi
    CP="`$CP`$jar"
done

echo "Compilando Java files..."

JAVA_FILES=(
    "`$SRCDIR/com/iedexplorer/native_lib/LibIec61850.java"
    "`$SRCDIR/com/iedexplorer/native_lib/NativeGooseSubscriber.java"
    "`$SRCDIR/com/iedexplorer/native_lib/NativeSVSubscriber.java"
    "`$SRCDIR/com/iedexplorer/GooseSubscriber.java"
    "`$SRCDIR/com/iedexplorer/GoosePublisher.java"
    "`$SRCDIR/com/iedexplorer/GooseUdpBridge.java"
    "`$SRCDIR/com/iedexplorer/IEC61850Client.java"
    "`$SRCDIR/com/iedexplorer/IEC61850Server.java"
    "`$SRCDIR/com/iedexplorer/IEDNavigatorApp.java"
)

javac -d "`$CLASSDIR" -cp "`$CP" -encoding UTF-8 "`${JAVA_FILES[@]}" 2>&1

if [ `$? -eq 0 ]; then
    echo "Compilacion exitosa!"
else
    echo "Compilacion FALLIDA"
    exit 1
fi
"@ | Out-File -FilePath "$LinDir\compilar.sh" -Encoding UTF8 -NoNewline

# Linux README
@"
IED Navigator v$Version - Linux Mint / Ubuntu / Debian
======================================================
Desarrollado por: Emilio Medina

INSTALACION:
  1. Extraer el contenido
  2. Abrir terminal en la carpeta
  3. sudo chmod +x instalar.sh iednavigator.sh
  4. sudo ./instalar.sh

EJECUCION:
  ./iednavigator.sh

  Para captura GOOSE (requiere permisos de red):
  sudo ./iednavigator.sh

RECOMPILAR DESDE FUENTES:
  chmod +x compilar.sh
  ./compilar.sh

REQUISITOS (se instalan automaticamente):
  - OpenJDK 21 (o default-jdk)
  - libpcap-dev (captura de paquetes)

NOTAS LINUX MINT:
  - El instalador usa apt-get (Debian/Ubuntu/Mint)
  - Si el setcap funciona, no necesita sudo para captura
  - El .desktop se crea en /usr/share/applications/
"@ | Out-File -FilePath "$LinDir\LEAME.txt" -Encoding UTF8

Write-Host "OK" -ForegroundColor Green

# Step 5: Fix line endings for Linux scripts and create ZIP
Write-Host "[5/5] Creando ZIP final..." -ForegroundColor Yellow

# Fix line endings (LF for Linux shell scripts)
foreach ($shFile in @("$LinDir\iednavigator.sh", "$LinDir\instalar.sh", "$LinDir\compilar.sh")) {
    $content = [System.IO.File]::ReadAllText($shFile)
    $content = $content.Replace("`r`n", "`n")
    [System.IO.File]::WriteAllText($shFile, $content, [System.Text.UTF8Encoding]::new($false))
}

Remove-Item -Path $OutputZip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path "$TempDir\*" -DestinationPath $OutputZip -Force

$zipSize = (Get-Item $OutputZip).Length / 1MB
Remove-Item -Path $TempDir -Recurse -Force

Write-Host "OK" -ForegroundColor Green
Write-Host ""
Write-Host "================================================" -ForegroundColor Green
Write-Host "  INSTALADOR DUAL CREADO!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Archivo: $OutputZip" -ForegroundColor Cyan
Write-Host "  Tamano:  $([math]::Round($zipSize, 1)) MB" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Contenido:" -ForegroundColor White
Write-Host "    win_installer\   -> INSTALAR.bat (como Admin)" -ForegroundColor White
Write-Host "    linux_installer\ -> sudo ./instalar.sh" -ForegroundColor White
Write-Host ""
