# IED Navigator - Build Installer Script
# This script prepares the distribution and creates the installer

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  IED Navigator - Build Installer" -ForegroundColor Cyan
Write-Host "  Developed by Emilio Medina" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Compile the project
Write-Host "[1/5] Compilando proyecto..." -ForegroundColor Yellow
& "$ProjectRoot\compile.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Compilacion fallida" -ForegroundColor Red
    exit 1
}
Write-Host "OK: Compilacion exitosa" -ForegroundColor Green

# Step 2: Create installer directories
Write-Host "[2/5] Creando estructura de directorios..." -ForegroundColor Yellow
$installerDir = "$ProjectRoot\installer"
$prereqDir = "$installerDir\prereq"
$resourcesDir = "$ProjectRoot\resources"
$outputDir = "$installerDir\output"

New-Item -ItemType Directory -Force -Path $installerDir | Out-Null
New-Item -ItemType Directory -Force -Path $prereqDir | Out-Null
New-Item -ItemType Directory -Force -Path $resourcesDir | Out-Null
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
Write-Host "OK: Directorios creados" -ForegroundColor Green

# Step 3: Download Npcap if not present
Write-Host "[3/5] Verificando Npcap installer..." -ForegroundColor Yellow
$npcapFile = "$prereqDir\npcap-1.79.exe"
if (-not (Test-Path $npcapFile)) {
    Write-Host "Descargando Npcap..." -ForegroundColor Yellow
    try {
        # Npcap official download (user may need to download manually due to license)
        $npcapUrl = "https://npcap.com/dist/npcap-1.79.exe"
        Invoke-WebRequest -Uri $npcapUrl -OutFile $npcapFile -UseBasicParsing
        Write-Host "OK: Npcap descargado" -ForegroundColor Green
    } catch {
        Write-Host "ADVERTENCIA: No se pudo descargar Npcap automaticamente" -ForegroundColor Yellow
        Write-Host "Por favor descargue manualmente desde: https://npcap.com/" -ForegroundColor Yellow
        Write-Host "Y coloquelo en: $prereqDir\npcap-1.79.exe" -ForegroundColor Yellow

        # Create a placeholder to allow build to continue
        Write-Host "Creando placeholder..." -ForegroundColor Yellow
    }
} else {
    Write-Host "OK: Npcap ya existe" -ForegroundColor Green
}

# Step 4: Create icon if not present
Write-Host "[4/5] Verificando icono..." -ForegroundColor Yellow
$iconFile = "$resourcesDir\icon.ico"
if (-not (Test-Path $iconFile)) {
    Write-Host "Creando icono placeholder..." -ForegroundColor Yellow
    # Create a simple ICO file (placeholder - ideally use a real icon)
    # For now, we'll skip the icon requirement
    Write-Host "NOTA: No hay icono, el instalador usara icono por defecto" -ForegroundColor Yellow
}

# Step 5: Check for Inno Setup
Write-Host "[5/5] Buscando Inno Setup..." -ForegroundColor Yellow
$innoSetup = $null
$innoPaths = @(
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles(x86)}\Inno Setup 5\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 5\ISCC.exe"
)

foreach ($path in $innoPaths) {
    if (Test-Path $path) {
        $innoSetup = $path
        break
    }
}

if ($innoSetup) {
    Write-Host "OK: Inno Setup encontrado en: $innoSetup" -ForegroundColor Green

    # Build the installer
    Write-Host ""
    Write-Host "Compilando instalador..." -ForegroundColor Cyan
    & $innoSetup "$installerDir\IEDNavigator.iss"

    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "  INSTALADOR CREADO EXITOSAMENTE!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Archivo: $outputDir\IEDNavigator_Setup_2.0.exe" -ForegroundColor Cyan
    } else {
        Write-Host "ERROR: Fallo la compilacion del instalador" -ForegroundColor Red
    }
} else {
    Write-Host "ADVERTENCIA: Inno Setup no encontrado" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Para crear el instalador, instale Inno Setup desde:" -ForegroundColor Yellow
    Write-Host "https://jrsoftware.org/isdl.php" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Alternativamente, puede distribuir la aplicacion como ZIP:" -ForegroundColor Yellow

    # Create ZIP distribution instead
    Write-Host ""
    Write-Host "Creando distribucion ZIP..." -ForegroundColor Yellow

    $zipFile = "$outputDir\IEDNavigator_Portable_2.0.zip"
    $tempDist = "$ProjectRoot\temp_dist"

    # Create temp distribution folder
    Remove-Item -Path $tempDist -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $tempDist | Out-Null
    New-Item -ItemType Directory -Force -Path "$tempDist\classes" | Out-Null
    New-Item -ItemType Directory -Force -Path "$tempDist\lib" | Out-Null

    # Copy files
    Copy-Item -Path "$ProjectRoot\classes\*" -Destination "$tempDist\classes" -Recurse
    Copy-Item -Path "$ProjectRoot\lib\*.jar" -Destination "$tempDist\lib"
    Copy-Item -Path "$ProjectRoot\lib\*.dll" -Destination "$tempDist\lib" -ErrorAction SilentlyContinue
    Copy-Item -Path "$ProjectRoot\dist\IEDNavigator.bat" -Destination "$tempDist\"

    # Create README
    @"
IED Navigator v2.0 - Portable Edition
=====================================
Desarrollado por: Emilio Medina

REQUISITOS:
-----------
1. Java 11 o superior (https://adoptium.net/)
2. Npcap (https://npcap.com/) - Para captura GOOSE/SV

INSTALACION:
------------
1. Extraiga todos los archivos a una carpeta
2. Instale Java si no lo tiene
3. Instale Npcap si desea capturar GOOSE
4. Ejecute IEDNavigator.bat

USO:
----
- Doble clic en IEDNavigator.bat para iniciar
- O desde cmd: IEDNavigator.bat

SOPORTE:
--------
IEC 61850 MMS Client/Server
GOOSE Subscriber/Publisher
Sampled Values (requiere libiec61850)
"@ | Out-File -FilePath "$tempDist\README.txt" -Encoding UTF8

    # Create ZIP
    Compress-Archive -Path "$tempDist\*" -DestinationPath $zipFile -Force

    # Cleanup
    Remove-Item -Path $tempDist -Recurse -Force

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  ZIP PORTABLE CREADO!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Archivo: $zipFile" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Presione cualquier tecla para salir..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
