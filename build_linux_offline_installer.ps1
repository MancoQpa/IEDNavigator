# build_linux_offline_installer.ps1
# Construye un paquete ZIP instalador OFFLINE para Ubuntu / Debian
# Incluye: clases Java + JARs + Eclipse Temurin JRE 21 + libpcap + setcap
# El destino puede instalar SIN internet ni apt.
#
# Uso:
#   .\build_linux_offline_installer.ps1
#   .\build_linux_offline_installer.ps1 -SkipCompile
#   .\build_linux_offline_installer.ps1 -SkipCompile -SkipDownload   (reusa cache)

param(
    [string]$Version     = "3.0",
    [switch]$SkipCompile,
    [switch]$SkipDownload    # omite descargas si los archivos ya estan en download_cache
)

$ErrorActionPreference = "Stop"

$ProjectRoot    = $PSScriptRoot
$ClassDir       = Join-Path $ProjectRoot "classes"
$LibDir         = Join-Path $ProjectRoot "lib"
$OfflineSrcDir  = Join-Path $ProjectRoot "installer\linux_offline"
$LinuxSrcDir    = Join-Path $ProjectRoot "installer\linux"
$DownloadCache  = Join-Path $ProjectRoot "installer\download_cache"
$OutputDir      = Join-Path $ProjectRoot "installer\output"
$TempDir        = Join-Path $ProjectRoot "temp_offline_build"
$PkgName        = "IEDNavigator_v${Version}_Linux_Offline"
$PkgDir         = Join-Path $TempDir $PkgName
$ZipOutput      = Join-Path $OutputDir "${PkgName}.zip"

function Write-Step { param([string]$m) Write-Host "`n[*] $m" -ForegroundColor Cyan }
function Write-OK   { param([string]$m) Write-Host "    OK: $m" -ForegroundColor Green }
function Write-Warn { param([string]$m) Write-Host "    AVISO: $m" -ForegroundColor Yellow }
function Fail       { param([string]$m) Write-Host "`nERROR: $m" -ForegroundColor Red; exit 1 }

function Download-File {
    param([string]$Url, [string]$Dest, [string]$Label)
    if ((Test-Path $Dest) -and $SkipDownload) {
        $sz = [math]::Round((Get-Item $Dest).Length / 1MB, 1)
        Write-OK "$Label (cache, $sz MB): $(Split-Path $Dest -Leaf)"
        return
    }
    Write-Host "    Descargando $Label ..." -NoNewline
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $wc = New-Object System.Net.WebClient
        $wc.DownloadFile($Url, $Dest)
        $sz = [math]::Round((Get-Item $Dest).Length / 1MB, 1)
        Write-Host " $sz MB" -ForegroundColor Green
    } catch {
        Fail "No se pudo descargar $Label`nURL: $Url`nError: $_"
    }
}

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  IEDNavigator v$Version -- Instalador OFFLINE"  -ForegroundColor Cyan
Write-Host "  Ubuntu / Debian -- sin internet en destino"     -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# ── Compilar ──────────────────────────────────────────────────────────────────
if (-not $SkipCompile) {
    Write-Step "Compilando proyecto..."
    $cs = Join-Path $ProjectRoot "compile.ps1"
    if (Test-Path $cs) {
        & $cs
        if ($LASTEXITCODE -ne 0) { Fail "La compilacion fallo." }
        Write-OK "Compilacion exitosa"
    } else {
        Write-Warn "compile.ps1 no encontrado. Usando clases existentes."
    }
}

# ── Verificar clases ──────────────────────────────────────────────────────────
$MainClass = Join-Path $ClassDir "com\iedexplorer\IEDExplorerApp.class"
if (-not (Test-Path $MainClass)) {
    Fail "Clases no encontradas en '$ClassDir'.`nEjecute compile.ps1 o use -SkipCompile."
}

# ── Verificar scripts offline ──────────────────────────────────────────────────
foreach ($f in @("instalar_offline.sh", "iednavigator_offline.sh")) {
    if (-not (Test-Path (Join-Path $OfflineSrcDir $f))) {
        Fail "Falta: installer\linux_offline\$f"
    }
}

# ── Cache de descargas ────────────────────────────────────────────────────────
if (-not (Test-Path $DownloadCache)) { New-Item -ItemType Directory -Path $DownloadCache | Out-Null }

# ── Descargar dependencias ────────────────────────────────────────────────────
Write-Step "Descargando dependencias (JRE 21 + libpcap + libcap2)..."

# Eclipse Temurin JRE 21 LTS - Linux x64 (~40 MB)
# Headless JRE (sin GUI de Java, la GUI es Swing propia de la app)
$JreFile = Join-Path $DownloadCache "jre-21-linux-x64.tar.gz"
$JreUrl  = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_linux_hotspot_21.0.5_11.tar.gz"
Download-File -Url $JreUrl -Dest $JreFile -Label "Eclipse Temurin JRE 21 (Linux x64)"

# libpcap0.8 - Ubuntu 22.04 Jammy amd64
$PcapFile = Join-Path $DownloadCache "libpcap0.8_jammy_amd64.deb"
$PcapUrl  = "http://archive.ubuntu.com/ubuntu/pool/main/libp/libpcap/libpcap0.8_1.10.1-4build1_amd64.deb"
Download-File -Url $PcapUrl -Dest $PcapFile -Label "libpcap0.8 (Ubuntu 22.04 Jammy)"

# libcap2 (biblioteca requerida por setcap en tiempo de ejecucion)
$Cap2File = Join-Path $DownloadCache "libcap2_jammy_amd64.deb"
$Cap2Url  = "http://archive.ubuntu.com/ubuntu/pool/main/libc/libcap2/libcap2_2.44-1ubuntu0.22.04.2_amd64.deb"
Download-File -Url $Cap2Url -Dest $Cap2File -Label "libcap2 (Ubuntu 22.04 Jammy)"

# libcap2-bin (contiene setcap y getcap)
$Cap2BinFile = Join-Path $DownloadCache "libcap2-bin_jammy_amd64.deb"
$Cap2BinUrl  = "http://archive.ubuntu.com/ubuntu/pool/main/libc/libcap2/libcap2-bin_2.44-1ubuntu0.22.04.2_amd64.deb"
Download-File -Url $Cap2BinUrl -Dest $Cap2BinFile -Label "libcap2-bin (setcap/getcap)"

Write-OK "Dependencias listas en: $DownloadCache"

# ── Preparar directorio temporal ──────────────────────────────────────────────
Write-Step "Preparando estructura del paquete..."
if (Test-Path $TempDir) { Remove-Item $TempDir -Recurse -Force }
New-Item -ItemType Directory -Path "$PkgDir\lib"          | Out-Null
New-Item -ItemType Directory -Path "$PkgDir\offline_deps" | Out-Null
Write-OK "Directorio temporal: $PkgDir"

# ── Copiar clases compiladas ──────────────────────────────────────────────────
Write-Step "Copiando clases compiladas..."
New-Item -ItemType Directory -Path "$PkgDir\classes\com" | Out-Null
Copy-Item (Join-Path $ClassDir "com\iedexplorer") "$PkgDir\classes\com\iedexplorer" -Recurse
$ClassCount = (Get-ChildItem "$PkgDir\classes" -Recurse -Filter "*.class").Count
Write-OK "$ClassCount archivos .class"

# ── Copiar JARs (excluir .dll de Windows) ────────────────────────────────────
Write-Step "Copiando librerias Java..."
$Jars = Get-ChildItem (Join-Path $LibDir "*.jar")
foreach ($jar in $Jars) { Copy-Item $jar.FullName "$PkgDir\lib\" }
Write-OK "$($Jars.Count) JARs copiados (iec61850.dll excluido -- no necesario en Linux)"

# ── Copiar icono ──────────────────────────────────────────────────────────────
$ResDir = Join-Path $ProjectRoot "resources"
if (Test-Path $ResDir) {
    New-Item -ItemType Directory -Path "$PkgDir\resources" | Out-Null
    $Icons = Get-ChildItem $ResDir -Filter "icon.*" -ErrorAction SilentlyContinue
    if ($Icons.Count -gt 0) {
        foreach ($icon in $Icons) { Copy-Item $icon.FullName "$PkgDir\resources\" }
        Write-OK "Icono: $($Icons[0].Name)"
    } else {
        Write-Warn "Icono no encontrado en resources\"
    }
}

# ── Copiar dependencias offline (JRE + .deb) ─────────────────────────────────
Write-Step "Incluyendo dependencias offline..."
# JRE: nombre generico para que instalar_offline.sh lo encuentre con glob jre-*.tar.gz
Copy-Item $JreFile     "$PkgDir\offline_deps\jre-21-linux-x64.tar.gz"
Copy-Item $PcapFile    "$PkgDir\offline_deps\libpcap0.8.deb"
Copy-Item $Cap2File    "$PkgDir\offline_deps\libcap2.deb"
Copy-Item $Cap2BinFile "$PkgDir\offline_deps\libcap2-bin.deb"
$DepsSize = [math]::Round(
    (Get-ChildItem "$PkgDir\offline_deps" | Measure-Object -Property Length -Sum).Sum / 1MB, 1)
Write-OK "Dependencias bundled: $DepsSize MB"

# ── Copiar scripts offline (convertir CRLF -> LF) ────────────────────────────
Write-Step "Copiando scripts de instalacion (Linux LF)..."
foreach ($script in @("instalar_offline.sh", "iednavigator_offline.sh")) {
    $src = Join-Path $OfflineSrcDir $script
    $dst = Join-Path $PkgDir $script
    $content = [System.IO.File]::ReadAllText($src) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($dst, $content, [System.Text.Encoding]::UTF8)
    Write-OK "$script (LF)"
}

# Copiar documentacion desde installer\linux\
foreach ($doc in @("README_LINUX.txt", "INSTALAR.txt")) {
    $src = Join-Path $LinuxSrcDir $doc
    if (Test-Path $src) {
        $dst = Join-Path $PkgDir $doc
        $content = [System.IO.File]::ReadAllText($src) -replace "`r`n", "`n"
        [System.IO.File]::WriteAllText($dst, $content, [System.Text.Encoding]::UTF8)
        Write-OK "$doc"
    }
}

# Copiar guia de instalacion offline
$OfflineDoc = Join-Path $OfflineSrcDir "INSTALAR_OFFLINE.txt"
if (Test-Path $OfflineDoc) {
    $content = [System.IO.File]::ReadAllText($OfflineDoc) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText("$PkgDir\INSTALAR_OFFLINE.txt", $content, [System.Text.Encoding]::UTF8)
    Write-OK "INSTALAR_OFFLINE.txt"
}

# ── Crear ZIP ─────────────────────────────────────────────────────────────────
Write-Step "Creando paquete ZIP..."
if (-not (Test-Path $OutputDir)) { New-Item -ItemType Directory -Path $OutputDir | Out-Null }
if (Test-Path $ZipOutput) { Remove-Item $ZipOutput -Force }
Compress-Archive -Path $PkgDir -DestinationPath $ZipOutput
Write-OK "ZIP creado: $ZipOutput"

# ── Limpiar temporal ──────────────────────────────────────────────────────────
Remove-Item $TempDir -Recurse -Force
Write-OK "Directorio temporal eliminado"

# ── Resumen ───────────────────────────────────────────────────────────────────
$ZipSize = [math]::Round((Get-Item $ZipOutput).Length / 1MB, 1)
Write-Host ""
Write-Host "=================================================" -ForegroundColor Green
Write-Host "  Instalador OFFLINE generado correctamente"      -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Archivo : $ZipOutput"
Write-Host "  Tamano  : ${ZipSize} MB"
Write-Host ""
Write-Host "  Contenido bundled:" -ForegroundColor Cyan
Write-Host "    * Eclipse Temurin JRE 21 (Linux x64)"
Write-Host "    * libpcap0.8 (captura GOOSE via pcap4j)"
Write-Host "    * libcap2-bin (setcap -- GOOSE sin root)"
Write-Host "    * libcap2 (biblioteca de setcap)"
Write-Host ""
Write-Host "  Instrucciones para Ubuntu SIN internet:" -ForegroundColor Cyan
Write-Host "    1. Transferir el ZIP:"
Write-Host "         scp ${PkgName}.zip usuario@ip:~/Downloads/"
Write-Host "    2. En la maquina Ubuntu:"
Write-Host "         cd ~/Downloads"
Write-Host "         unzip ${PkgName}.zip"
Write-Host "         cd ${PkgName}"
Write-Host "         chmod +x instalar_offline.sh"
Write-Host "         sudo ./instalar_offline.sh"
Write-Host "    3. Lanzar:"
Write-Host "         iednavigator"
Write-Host ""
Write-Host "  Desinstalar (en Ubuntu):" -ForegroundColor Cyan
Write-Host "         sudo /opt/iednavigator/instalar.sh --desinstalar"
Write-Host ""
Write-Host "  Nota: las descargas quedan en installer\download_cache\" -ForegroundColor DarkGray
Write-Host "        Use -SkipDownload para reutilizarlas en proximas ejecuciones."  -ForegroundColor DarkGray
Write-Host ""
