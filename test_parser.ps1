param(
    [Parameter(Mandatory=$true)]
    [string]$SclFile
)

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot'
$JAVAC = "$env:JAVA_HOME\bin\javac.exe"
$JAVA = "$env:JAVA_HOME\bin\java.exe"
$SRCDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\src\main\java'
$LIBDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\lib'
$CLASSDIR = 'C:\Users\admin\Documents\proyectos IA\iec61850_java_explorer\classes'

# Create classes dir if not exists
if (!(Test-Path $CLASSDIR)) { New-Item -ItemType Directory -Path $CLASSDIR }

# Build classpath
$jars = Get-ChildItem -Path $LIBDIR -Filter '*.jar' | ForEach-Object { $_.FullName }
$CP = $jars -join ';'

Write-Host "Compiling test..."
& $JAVAC -d $CLASSDIR -cp $CP -encoding UTF-8 "$SRCDIR\com\iedexplorer\SclParserTest.java" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation FAILED"
    exit 1
}

Write-Host ""
Write-Host "Running SCL Parser Test..."
Write-Host ""

$CP_RUN = "$CLASSDIR;" + ($jars -join ';')
& $JAVA -cp $CP_RUN com.iedexplorer.SclParserTest $SclFile 2>&1
