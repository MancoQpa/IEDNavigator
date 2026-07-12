# IEC 61850 Explorer - Setup y Ejecucion

$ErrorActionPreference = "Continue"

Write-Host "==================================="
Write-Host "IEC 61850 Explorer - Setup"
Write-Host "==================================="

# Directorio base (usando comillas para espacios)
$baseDir = $PSScriptRoot
Set-Location $baseDir

$libDir = "lib"
$classDir = "classes"
$srcDir = "src\main\java"

# Crear directorios
if (-not (Test-Path $libDir)) { New-Item -ItemType Directory -Path $libDir | Out-Null }
if (-not (Test-Path $classDir)) { New-Item -ItemType Directory -Path $classDir | Out-Null }

# Descargar dependencias
Write-Host ""
Write-Host "Descargando dependencias..."

$deps = @(
    @{name="iec61850bean-1.9.0.jar"; url="https://repo1.maven.org/maven2/com/beanit/iec61850bean/1.9.0/iec61850bean-1.9.0.jar"},
    @{name="jasn1-1.11.3.jar"; url="https://repo1.maven.org/maven2/com/beanit/jasn1/1.11.3/jasn1-1.11.3.jar"},
    @{name="jasn1-compiler-1.11.3.jar"; url="https://repo1.maven.org/maven2/com/beanit/jasn1-compiler/1.11.3/jasn1-compiler-1.11.3.jar"},
    @{name="antlr-2.7.7.jar"; url="https://repo1.maven.org/maven2/antlr/antlr/2.7.7/antlr-2.7.7.jar"},
    @{name="flatlaf-3.2.jar"; url="https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2/flatlaf-3.2.jar"}
)

foreach ($dep in $deps) {
    $jarPath = "$libDir\$($dep.name)"
    if (-not (Test-Path $jarPath)) {
        Write-Host "  Descargando $($dep.name)..."
        try {
            Invoke-WebRequest -Uri $dep.url -OutFile $jarPath -UseBasicParsing
        } catch {
            Write-Host "    ERROR: $_"
        }
    } else {
        Write-Host "  $($dep.name) OK"
    }
}

# Buscar Java
Write-Host ""
Write-Host "Buscando Java..."

$javaHome = $null
$searchPaths = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft",
    "C:\Program Files\Zulu"
)

foreach ($searchPath in $searchPaths) {
    if (Test-Path $searchPath) {
        $jdkDirs = Get-ChildItem -Path $searchPath -Directory -Filter "jdk*" -ErrorAction SilentlyContinue
        foreach ($jdk in $jdkDirs) {
            $javaExe = Join-Path $jdk.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                $javaHome = $jdk.FullName
                break
            }
        }
    }
    if ($javaHome) { break }
}

if (-not $javaHome) {
    Write-Host "ERROR: Java no encontrado. Instale Java 11+ desde https://adoptium.net/"
    Read-Host "Enter para salir"
    exit 1
}

Write-Host "  Java: $javaHome"

$javac = "`"$javaHome\bin\javac.exe`""
$java = "`"$javaHome\bin\java.exe`""

# Compilar
Write-Host ""
Write-Host "Compilando..."

# Construir classpath
$cp = (Get-ChildItem "$libDir\*.jar" | ForEach-Object { $_.FullName }) -join ";"

# Obtener archivos fuente
$srcFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java"

Write-Host "  Fuentes: $($srcFiles.Count)"

# Escribir archivo de argumentos para javac (maneja espacios correctamente)
$argsFile = "javac_args.txt"
"-d" | Out-File $argsFile -Encoding ASCII
"`"$baseDir\$classDir`"" | Out-File $argsFile -Append -Encoding ASCII
"-cp" | Out-File $argsFile -Append -Encoding ASCII
"`"$cp`"" | Out-File $argsFile -Append -Encoding ASCII
"-encoding" | Out-File $argsFile -Append -Encoding ASCII
"UTF-8" | Out-File $argsFile -Append -Encoding ASCII

foreach ($src in $srcFiles) {
    "`"$($src.FullName)`"" | Out-File $argsFile -Append -Encoding ASCII
}

# Compilar usando archivo de argumentos
$javacCmd = "& $javac `"@$argsFile`""
Write-Host "  Ejecutando javac..."

try {
    Invoke-Expression $javacCmd 2>&1 | ForEach-Object { Write-Host "    $_" }
    $compileResult = $LASTEXITCODE
} catch {
    Write-Host "  ERROR: $_"
    $compileResult = 1
}

Remove-Item $argsFile -ErrorAction SilentlyContinue

if ($compileResult -ne 0) {
    Write-Host ""
    Write-Host "ERROR en compilacion"
    Read-Host "Enter para salir"
    exit 1
}

Write-Host "  Compilacion OK!"

# Ejecutar
Write-Host ""
Write-Host "==================================="
Write-Host "Iniciando aplicacion..."
Write-Host "==================================="

$runCp = "`"$baseDir\$classDir;$cp`""
$javaCmd = "& $java -cp $runCp com.iednavigator.IEDNavigatorApp"

Invoke-Expression $javaCmd
