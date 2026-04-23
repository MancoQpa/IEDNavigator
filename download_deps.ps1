# Download correct dependencies for iec61850bean 1.9.0

$LIBDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\lib'

# URLs from Maven Central
$deps = @{
    'asn1bean-1.13.0.jar' = 'https://repo1.maven.org/maven2/com/beanit/asn1bean/1.13.0/asn1bean-1.13.0.jar'
    'iec61850bean-1.9.0.jar' = 'https://repo1.maven.org/maven2/com/beanit/iec61850bean/1.9.0/iec61850bean-1.9.0.jar'
}

Write-Host "Downloading dependencies to $LIBDIR..."

foreach ($jar in $deps.Keys) {
    $url = $deps[$jar]
    $outFile = Join-Path $LIBDIR $jar

    if (Test-Path $outFile) {
        Write-Host "  $jar already exists, skipping"
    } else {
        Write-Host "  Downloading $jar..."
        try {
            Invoke-WebRequest -Uri $url -OutFile $outFile -UseBasicParsing
            Write-Host "    Downloaded successfully"
        } catch {
            Write-Host "    ERROR: $_"
        }
    }
}

# Remove old jasn1 jars if they exist
$oldJars = @('jasn1-1.11.3.jar', 'jasn1-compiler-1.11.3.jar')
foreach ($oldJar in $oldJars) {
    $oldPath = Join-Path $LIBDIR $oldJar
    if (Test-Path $oldPath) {
        Write-Host "Removing old dependency: $oldJar"
        Remove-Item $oldPath
    }
}

Write-Host ""
Write-Host "Done! Current JARs:"
Get-ChildItem $LIBDIR -Filter '*.jar' | ForEach-Object { Write-Host "  $($_.Name)" }
