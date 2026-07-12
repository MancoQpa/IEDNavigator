# build_linux_installer_v31.ps1
# Construye IEDNavigator v3.1 para Ubuntu Desktop
# Correcciones vs Linux v3.0:
#   - Clase principal: com.iednavigator.IEDNavigatorApp (corregida)
#   - Soporte puerto 102 via authbind (instalado automaticamente)
#   - Launcher con deteccion authbind + fallback a sudo
#   - Leyenda FC/CDC y nuevos iconos incluidos en clases

param([switch]$SkipCompile)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Version     = "3.1"
$PkgName     = "IEDNavigator_v${Version}_Linux"
$TempDir     = "$ProjectRoot\temp_linux_v31"
$PkgDir      = "$TempDir\$PkgName"
$OutputDir   = "$ProjectRoot\installer\output"
$OutputZip   = "$OutputDir\${PkgName}.zip"

function step { param([string]$m) Write-Host "`n[*] $m" -ForegroundColor Cyan }
function ok   { param([string]$m) Write-Host "    OK: $m" -ForegroundColor Green }
function warn { param([string]$m) Write-Host "    AVISO: $m" -ForegroundColor Yellow }
function fail { param([string]$m) Write-Host "`nERROR: $m" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  IED Navigator v$Version -- Instalador Linux"    -ForegroundColor Cyan
Write-Host "  Ubuntu Desktop / Debian / Mint"                  -ForegroundColor Cyan
Write-Host "  Desarrollado por Emilio Medina"                  -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# Compilar si es necesario
if (-not $SkipCompile) {
    step "Compilando proyecto..."
    & "$ProjectRoot\compile.ps1"
    if ($LASTEXITCODE -ne 0) { fail "La compilacion fallo." }
    ok "Compilacion exitosa"
}

$MainClass = "$ProjectRoot\classes\com\iednavigator\IEDNavigatorApp.class"
if (-not (Test-Path $MainClass)) { fail "No se encontraron clases en classes\com\iednavigator\. Ejecute compile.ps1." }

# Preparar estructura
step "Preparando estructura del paquete..."
if (Test-Path $TempDir) { Remove-Item $TempDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$PkgDir\lib" | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir    | Out-Null

# Clases (solo com/iednavigator)
New-Item -ItemType Directory -Force -Path "$PkgDir\classes\com" | Out-Null
Copy-Item "$ProjectRoot\classes\com\iednavigator" "$PkgDir\classes\com\iednavigator" -Recurse
$cc = (Get-ChildItem "$PkgDir\classes" -Recurse -Filter "*.class").Count
ok "$cc archivos .class copiados (com.iednavigator)"

# JARs (excluir .dll de Windows)
$jars = Get-ChildItem "$ProjectRoot\lib\*.jar"
foreach ($j in $jars) { Copy-Item $j.FullName "$PkgDir\lib\" }
ok "$($jars.Count) JARs copiados (iec61850.dll excluido)"

# Helper para escribir scripts con fin de linea LF (Linux)
function Write-LinuxScript {
    param([string]$Path, [string]$Content)
    $lf = $Content -replace "`r`n", "`n"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($lf)
    [System.IO.File]::WriteAllBytes($Path, $bytes)
}

# --- iednavigator.sh ---
$launcherContent = @'
#!/bin/bash
# iednavigator.sh -- Lanzador IED Navigator v3.1 para Linux
# Puerto 102: usa authbind si esta instalado (sin necesidad de sudo)

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
    DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
    SOURCE="$(readlink "$SOURCE")"
    [[ "$SOURCE" != /* ]] && SOURCE="$DIR/$SOURCE"
done
INSTALL_DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"

find_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"; return
    fi
    if command -v java &>/dev/null; then
        echo "java"; return
    fi
    for d in /usr/lib/jvm/java-21-openjdk-amd64 \
              /usr/lib/jvm/java-17-openjdk-amd64 \
              /usr/lib/jvm/java-11-openjdk-amd64 \
              /usr/lib/jvm/temurin-21-amd64 \
              /usr/lib/jvm/temurin-17-amd64; do
        [ -x "$d/bin/java" ] && echo "$d/bin/java" && return
    done
    echo ""
}

JAVA_CMD=$(find_java)
if [ -z "$JAVA_CMD" ]; then
    echo "ERROR: No se encontro Java 11+ en el sistema."
    echo "Instalar:  sudo apt install openjdk-21-jdk"
    exit 1
fi

JAVA_VER=$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
[ "$JAVA_VER" = "1" ] && JAVA_VER=$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f2)
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -lt 11 ] 2>/dev/null; then
    echo "ERROR: Se requiere Java 11+ (encontrado: $JAVA_VER)"
    exit 1
fi

CP="$INSTALL_DIR/classes"
for jar in "$INSTALL_DIR/lib/"*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar"
done

JAVA_ARGS=(
    --enable-native-access=ALL-UNNAMED
    -Djna.library.path="$INSTALL_DIR/lib"
    -Djava.awt.headless=false
    -cp "$CP"
    com.iednavigator.IEDNavigatorApp
    "$@"
)

# Puerto 102: intentar authbind primero, luego lanzar normalmente
# authbind permite usar puertos < 1024 sin sudo si esta configurado
if command -v authbind &>/dev/null && [ -f /etc/authbind/byport/102 ]; then
    echo "Lanzando con authbind (puerto 102 disponible sin sudo)..."
    exec authbind --deep "$JAVA_CMD" "${JAVA_ARGS[@]}"
elif [ "$EUID" -eq 0 ]; then
    exec "$JAVA_CMD" "${JAVA_ARGS[@]}"
else
    exec "$JAVA_CMD" "${JAVA_ARGS[@]}"
fi
'@
Write-LinuxScript "$PkgDir\iednavigator.sh" $launcherContent

# --- instalar.sh ---
$installerContent = @'
#!/bin/bash
# instalar.sh -- Instalador desatendido IED Navigator v3.1 para Ubuntu Desktop
# Uso normal:     sudo ./instalar.sh
# Desinstalar:    sudo ./instalar.sh --desinstalar

set -e

INSTALL_DIR="/opt/iednavigator"
BIN_LINK="/usr/local/bin/iednavigator"
DESKTOP_FILE="/usr/share/applications/iednavigator.desktop"
APP_VERSION="3.1"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'
YELLOW='\033[1;33m'; NC='\033[0m'
step() { echo -e "\n${CYAN}[*] $1${NC}"; }
ok()   { echo -e "    ${GREEN}OK: $1${NC}"; }
warn() { echo -e "    ${YELLOW}AVISO: $1${NC}"; }
fail() { echo -e "\n${RED}ERROR: $1${NC}"; exit 1; }

[ "$EUID" -ne 0 ] && fail "Requiere administrador.\nEjecute:  sudo ./instalar.sh"

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

# Desinstalacion
if [ "$1" = "--desinstalar" ]; then
    step "Desinstalando IED Navigator..."
    [ -f "$BIN_LINK" ]     && rm -f "$BIN_LINK"     && ok "$BIN_LINK eliminado"
    [ -f "$DESKTOP_FILE" ] && rm -f "$DESKTOP_FILE" && ok ".desktop eliminado"
    [ -d "$INSTALL_DIR" ]  && rm -rf "$INSTALL_DIR" && ok "$INSTALL_DIR eliminado"
    rm -f /etc/authbind/byport/102 2>/dev/null && ok "authbind puerto 102 revocado" || true
    command -v update-desktop-database &>/dev/null && update-desktop-database
    echo -e "\n${GREEN}IED Navigator desinstalado correctamente.${NC}"
    exit 0
fi

echo ""
echo "================================================="
echo "  Instalador IED Navigator v$APP_VERSION"
echo "  IEC 61850 Explorer para Ubuntu Desktop"
echo "  Desarrollado por Emilio Medina"
echo "================================================="

if command -v apt-get &>/dev/null; then PKG="apt-get"
elif command -v apt &>/dev/null;   then PKG="apt"
else PKG=""; warn "No se encontro apt/apt-get. Instale dependencias manualmente."; fi

# [1/6] Java
step "[1/6] Verificando Java 11+..."
JAVA_VER=""
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
    [ "$JAVA_VER" = "1" ] && JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f2)
fi
if command -v java &>/dev/null && [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -ge 11 ] 2>/dev/null; then
    ok "Java $JAVA_VER encontrado en $(which java)"
elif [ -n "$PKG" ]; then
    $PKG update -qq
    $PKG install -y openjdk-21-jdk 2>/dev/null || \
    $PKG install -y openjdk-17-jdk 2>/dev/null || \
    $PKG install -y openjdk-11-jdk || \
    fail "No se pudo instalar Java.\n  Manual: sudo apt install openjdk-21-jdk"
    ok "Java instalado: $(java -version 2>&1 | head -1)"
else
    fail "Java no encontrado. Instale Java 11+: https://adoptium.net/"
fi

# [2/6] libpcap
step "[2/6] Verificando libpcap (captura GOOSE)..."
if ldconfig -p 2>/dev/null | grep -q libpcap; then
    ok "libpcap ya instalada"
elif [ -n "$PKG" ]; then
    $PKG install -y libpcap0.8 libpcap-dev 2>/dev/null || \
    $PKG install -y libpcap-dev 2>/dev/null || \
    warn "No se pudo instalar libpcap."
    ok "libpcap instalada"
else
    warn "Instale manualmente: sudo apt install libpcap-dev"
fi

# [3/6] authbind para puerto 102
step "[3/6] Configurando puerto 102 (authbind)..."
if [ -n "$PKG" ]; then
    $PKG install -y authbind 2>/dev/null || warn "No se pudo instalar authbind."
fi
if command -v authbind &>/dev/null; then
    touch /etc/authbind/byport/102
    chmod 777 /etc/authbind/byport/102
    ok "authbind configurado: puerto 102 disponible sin sudo"
else
    warn "authbind no disponible. Para puerto 102: sudo iednavigator"
    warn "Alternativa sin privilegios: usar puerto 10102 en la UI"
fi

# [4/6] Copiar archivos
step "[4/6] Instalando en $INSTALL_DIR..."
[ -d "$INSTALL_DIR" ] && rm -rf "$INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
cp -r "$SRC_DIR/classes"         "$INSTALL_DIR/"
cp -r "$SRC_DIR/lib"             "$INSTALL_DIR/"
cp    "$SRC_DIR/iednavigator.sh" "$INSTALL_DIR/"
chmod +x "$INSTALL_DIR/iednavigator.sh"
[ -d "$SRC_DIR/resources" ] && cp -r "$SRC_DIR/resources" "$INSTALL_DIR/" || true
ok "Archivos instalados en $INSTALL_DIR"

# [5/6] Enlace global
step "[5/6] Creando comando global 'iednavigator'..."
[ -f "$BIN_LINK" ] && rm -f "$BIN_LINK"
ln -s "$INSTALL_DIR/iednavigator.sh" "$BIN_LINK"
ok "Comando disponible: iednavigator"

# [6/6] .desktop + setcap para GOOSE
step "[6/6] Menu de aplicaciones y permisos de red (GOOSE)..."

ICON_PATH="$INSTALL_DIR/resources/icon.png"
[ ! -f "$ICON_PATH" ] && ICON_PATH="applications-engineering"

cat > "$DESKTOP_FILE" << DESK_EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=IED Navigator
GenericName=IEC 61850 IED Explorer
Comment=Explorador y simulador de IEDs IEC 61850 v${APP_VERSION}
Terminal=false
Categories=Network;Science;Engineering;
Keywords=IEC61850;IED;GOOSE;MMS;Substation;
StartupWMClass=com-iednavigator-IEDNavigatorApp
Exec=$INSTALL_DIR/iednavigator.sh
Icon=$ICON_PATH
DESK_EOF

chmod 644 "$DESKTOP_FILE"
command -v update-desktop-database &>/dev/null && update-desktop-database
ok "Menu de aplicaciones creado"

if ! command -v setcap &>/dev/null && [ -n "$PKG" ]; then
    $PKG install -y libcap2-bin 2>/dev/null || true
fi
JAVA_BIN=$(readlink -f "$(which java)" 2>/dev/null || which java)
if command -v setcap &>/dev/null; then
    if setcap cap_net_raw,cap_net_admin=eip "$JAVA_BIN" 2>/dev/null; then
        ok "setcap aplicado: captura GOOSE sin root"
    else
        warn "setcap fallo. Para GOOSE: sudo setcap cap_net_raw,cap_net_admin=eip \$(readlink -f \$(which java))"
    fi
else
    warn "setcap no disponible. Para GOOSE ejecute: sudo iednavigator"
fi

echo ""
echo -e "${GREEN}=================================================${NC}"
echo -e "${GREEN}  IED Navigator v$APP_VERSION instalado OK${NC}"
echo -e "${GREEN}=================================================${NC}"
echo ""
echo "  Lanzar:"
echo "    Menu de aplicaciones  ->  IED Navigator"
echo "    Terminal               ->  iednavigator"
echo ""
echo "  Puerto 102 (MMS estandar IEC 61850):"
if command -v authbind &>/dev/null && [ -f /etc/authbind/byport/102 ]; then
    echo "    Habilitado via authbind (sin sudo)"
else
    echo "    Requiere: sudo iednavigator"
    echo "    O usar puerto 10102 en la UI (sin privilegios)"
fi
echo ""
echo "  Desinstalar:"
echo "    sudo $INSTALL_DIR/instalar.sh --desinstalar"
echo ""
'@
Write-LinuxScript "$PkgDir\instalar.sh" $installerContent

# --- LEAME_LINUX.txt ---
$readmeContent = @'
================================================
IED Navigator v3.1 - Linux Ubuntu Desktop
Desarrollado por: Emilio Medina
================================================

INSTALACION RAPIDA:
-------------------
1. Descomprimir el ZIP en cualquier carpeta
2. En una terminal:
     cd IEDNavigator_v3.1_Linux
     sudo ./instalar.sh

El instalador realiza automaticamente:
  [1] Instala OpenJDK 21 (si no esta instalado)
  [2] Instala libpcap (para captura GOOSE)
  [3] Instala authbind y habilita puerto 102 sin sudo
  [4] Copia archivos a /opt/iednavigator
  [5] Crea comando global: iednavigator
  [6] Entrada en menu de aplicaciones
      + setcap para GOOSE sin root

EJECUCION:
----------
  Desde menu      : buscar "IED Navigator"
  Desde terminal  : iednavigator
  Modo portatil   : chmod +x iednavigator.sh && ./iednavigator.sh

PUERTO 102 (MMS estandar IEC 61850):
-------------------------------------
  Con instalador  : habilitado via authbind (sin sudo)
  Sin instalador  : sudo ./iednavigator.sh
  Sin privilegios : cambiar a puerto 10102 en la UI

CAPTURA GOOSE:
--------------
  El instalador aplica setcap al binario Java para
  captura de paquetes sin root.
  Si falla: sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))

DESINSTALACION:
---------------
  sudo /opt/iednavigator/instalar.sh --desinstalar

NOVEDADES v3.1 vs v3.0 Linux:
------------------------------
  - Clase principal corregida: com.iednavigator.IEDNavigatorApp
  - Soporte puerto 102 via authbind
  - Leyenda Functional Constraints y CDC en Ayuda
  - Iconos diferenciados por grupo de Nodo Logico (LN)

REQUISITOS:
-----------
  - Ubuntu 20.04+ / Debian 11+ / Linux Mint 20+
  - Java 11+ (instalado automaticamente)
  - libpcap (instalada automaticamente)
  - authbind (instalado automaticamente)
'@
Write-LinuxScript "$PkgDir\LEAME_LINUX.txt" $readmeContent

# Crear ZIP
step "Creando ZIP..."
if (Test-Path $OutputZip) { Remove-Item $OutputZip -Force }
Compress-Archive -Path $PkgDir -DestinationPath $OutputZip
ok "ZIP creado"

Remove-Item $TempDir -Recurse -Force
ok "Directorio temporal eliminado"

$zipSize = [math]::Round((Get-Item $OutputZip).Length / 1MB, 2)

Write-Host ""
Write-Host "=================================================" -ForegroundColor Green
Write-Host "  Instalador Linux generado correctamente"         -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Archivo : $OutputZip"   -ForegroundColor Cyan
Write-Host "  Tamano  : ${zipSize} MB" -ForegroundColor Cyan
Write-Host ""
Write-Host "  En Ubuntu:"                                       -ForegroundColor White
Write-Host "    unzip IEDNavigator_v3.1_Linux.zip"             -ForegroundColor White
Write-Host "    cd IEDNavigator_v3.1_Linux"                    -ForegroundColor White
Write-Host "    sudo ./instalar.sh"                             -ForegroundColor White
Write-Host ""
