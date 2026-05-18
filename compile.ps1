$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot'
$JAVAC = "$env:JAVA_HOME\bin\javac.exe"
$SRCDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\src\main\java'
$LIBDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\lib'
$CLASSDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\classes'

# Create classes dir if not exists
if (!(Test-Path $CLASSDIR)) { New-Item -ItemType Directory -Path $CLASSDIR }

# Build classpath
$jars = Get-ChildItem -Path $LIBDIR -Filter '*.jar' | ForEach-Object { $_.FullName }
$CP = $jars -join ';'

# Collect .java files from com.iednavigator only (com.iedexplorer is the old renamed package)
$sources = Get-ChildItem -Path "$SRCDIR\com\iednavigator" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }

Write-Host "Compiling Java files..."
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Classpath has $($jars.Count) jars"
Write-Host "Source files: $($sources.Count)"

# Write @argfile to avoid command-line length limits (ASCII sin BOM — javac lo requiere)
$argfile = "$env:TEMP\ied_sources.txt"
$sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } | Out-File -FilePath $argfile -Encoding ascii

& $JAVAC -d $CLASSDIR -cp $CP -encoding UTF-8 --release 11 "@$argfile" 2>&1

Remove-Item $argfile -ErrorAction SilentlyContinue

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful!"
} else {
    Write-Host "Compilation FAILED with exit code $LASTEXITCODE"
}
