# build_linux_installer.ps1
# Construye un paquete ZIP instalador para Linux Mint / Ubuntu / Debian
# Los scripts de Linux estan en installer/linux/
# Ejecutar desde el directorio raiz del proyecto:
#   .\build_linux_installer.ps1
#   .\build_linux_installer.ps1 -SkipCompile   (usa clases ya compiladas)

param(
    [string]$Version = "3.0",
    [switch]$SkipCompile
)

$ErrorActionPreference = "Stop"

$ProjectRoot  = $PSScriptRoot
$ClassDir     = Join-Path $ProjectRoot "classes"
$LibDir       = Join-Path $ProjectRoot "lib"
$LinuxSrcDir  = Join-Path $ProjectRoot "installer\linux"
$OutputDir    = Join-Path $ProjectRoot "installer\output"
$TempDir      = Join-Path $ProjectRoot "temp_linux_build"
$PkgName      = "IEDNavigator_v${Version}_Linux"
$PkgDir       = Join-Path $TempDir $PkgName
$ZipOutput    = Join-Path $OutputDir "${PkgName}.zip"

function Write-Step { param([string]$m) Write-Host "`n[*] $m" -ForegroundColor Cyan }
function Write-OK   { param([string]$m) Write-Host "    OK: $m" -ForegroundColor Green }
function Write-Warn { param([string]$m) Write-Host "    AVISO: $m" -ForegroundColor Yellow }
function Fail       { param([string]$m) Write-Host "`nERROR: $m" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  IEDNavigator v$Version -- Instalador Linux" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# Compilar si es necesario
if (-not $SkipCompile) {
    Write-Step "Compilando proyecto..."
    $CompileScript = Join-Path $ProjectRoot "compile.ps1"
    if (Test-Path $CompileScript) {
        & $CompileScript
        if ($LASTEXITCODE -ne 0) { Fail "La compilacion fallo." }
        Write-OK "Compilacion exitosa"
    } else {
        Write-Warn "compile.ps1 no encontrado. Usando clases existentes."
    }
}

# Verificar clases compiladas
$MainClass = Join-Path $ClassDir "com\iedexplorer\IEDExplorerApp.class"
if (-not (Test-Path $MainClass)) {
    Fail "No se encontraron clases compiladas en '$ClassDir'.`nEjecute primero compile.ps1 o use -SkipCompile si ya estan compiladas."
}

# Verificar scripts de Linux
if (-not (Test-Path $LinuxSrcDir)) {
    Fail "No se encontro el directorio de scripts Linux: $LinuxSrcDir"
}
foreach ($f in @("iednavigator.sh", "instalar.sh", "INSTALAR.txt")) {
    if (-not (Test-Path (Join-Path $LinuxSrcDir $f))) {
        Fail "Falta el archivo: installer\linux\$f"
    }
}

# Preparar directorio temporal
Write-Step "Preparando estructura del paquete..."
if (Test-Path $TempDir) { Remove-Item $TempDir -Recurse -Force }
New-Item -ItemType Directory -Path $PkgDir | Out-Null
New-Item -ItemType Directory -Path "$PkgDir\lib" | Out-Null
Write-OK "Directorio temporal: $PkgDir"

# Copiar clases compiladas (preservar estructura com\iedexplorer\)
Write-Step "Copiando clases compiladas..."
New-Item -ItemType Directory -Path "$PkgDir\classes\com" | Out-Null
Copy-Item (Join-Path $ClassDir "com\iedexplorer") "$PkgDir\classes\com\iedexplorer" -Recurse
$ClassCount = (Get-ChildItem "$PkgDir\classes" -Recurse -Filter "*.class").Count
Write-OK "$ClassCount archivos .class copiados"

# Copiar JARs (excluir .dll de Windows -- no sirve en Linux)
Write-Step "Copiando librerias Java..."
$Jars = Get-ChildItem (Join-Path $LibDir "*.jar")
foreach ($jar in $Jars) {
    Copy-Item $jar.FullName "$PkgDir\lib\"
}
Write-OK "$($Jars.Count) JARs copiados (iec61850.dll excluido)"

# Copiar icono si existe
$ResDir = Join-Path $ProjectRoot "resources"
if (Test-Path $ResDir) {
    New-Item -ItemType Directory -Path "$PkgDir\resources" | Out-Null
    $Icons = Get-ChildItem $ResDir -Filter "icon.*" -ErrorAction SilentlyContinue
    if ($Icons.Count -gt 0) {
        foreach ($icon in $Icons) { Copy-Item $icon.FullName "$PkgDir\resources\" }
        Write-OK "Icono copiado: $($Icons[0].Name)"
    } else {
        Write-Warn "No se encontro archivo de icono en resources\"
    }
}

# Copiar scripts de Linux (convirtiendo line endings a LF)
Write-Step "Copiando scripts de Linux..."
foreach ($script in @("iednavigator.sh", "instalar.sh", "INSTALAR.txt")) {
    $src = Join-Path $LinuxSrcDir $script
    $dst = Join-Path $PkgDir $script
    # Leer y convertir CRLF -> LF para compatibilidad Linux
    $content = [System.IO.File]::ReadAllText($src) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($dst, $content, [System.Text.Encoding]::UTF8)
    Write-OK "$script (LF)"
}

# Copiar README
$ReadmeSrc = Join-Path $LinuxSrcDir "README_LINUX.txt"
if (Test-Path $ReadmeSrc) {
    $content = [System.IO.File]::ReadAllText($ReadmeSrc) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText("$PkgDir\README_LINUX.txt", $content, [System.Text.Encoding]::UTF8)
    Write-OK "README_LINUX.txt"
}

# Preparar directorio de salida
Write-Step "Creando paquete ZIP..."
if (-not (Test-Path $OutputDir)) { New-Item -ItemType Directory -Path $OutputDir | Out-Null }
if (Test-Path $ZipOutput) { Remove-Item $ZipOutput -Force }

Compress-Archive -Path $PkgDir -DestinationPath $ZipOutput
Write-OK "ZIP creado: $ZipOutput"

# Limpiar temporal
Remove-Item $TempDir -Recurse -Force
Write-OK "Directorio temporal eliminado"

# Resumen
$ZipSize = [math]::Round((Get-Item $ZipOutput).Length / 1MB, 2)
Write-Host ""
Write-Host "=================================================" -ForegroundColor Green
Write-Host "  Instalador Linux generado correctamente" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Archivo : $ZipOutput"
Write-Host "  Tamanio : ${ZipSize} MB"
Write-Host ""
Write-Host "  Instrucciones para el usuario Linux:" -ForegroundColor Cyan
Write-Host "    1. Copiar/transferir el ZIP a la maquina Linux"
Write-Host "    2. Extraer:  unzip $PkgName.zip"
Write-Host "    3. Instalar: cd $PkgName && sudo ./instalar.sh"
Write-Host ""
Write-Host "  O sin instalar (modo portatil):"
Write-Host "    cd $PkgName && chmod +x iednavigator.sh && ./iednavigator.sh"
Write-Host ""
