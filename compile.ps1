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

Write-Host "Compiling Java files..."
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Classpath has $($jars.Count) jars"

& $JAVAC -d $CLASSDIR -cp $CP -encoding UTF-8 `
    "$SRCDIR\com\iedexplorer\native_lib\LibIec61850.java" `
    "$SRCDIR\com\iedexplorer\native_lib\NativeGooseSubscriber.java" `
    "$SRCDIR\com\iedexplorer\native_lib\NativeSVSubscriber.java" `
    "$SRCDIR\com\iedexplorer\GooseSubscriber.java" `
    "$SRCDIR\com\iedexplorer\GoosePublisher.java" `
    "$SRCDIR\com\iedexplorer\GooseUdpBridge.java" `
    "$SRCDIR\com\iedexplorer\IEC61850Client.java" `
    "$SRCDIR\com\iedexplorer\IEC61850Server.java" `
    "$SRCDIR\com\iedexplorer\IEDExplorerApp.java" 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful!"
} else {
    Write-Host "Compilation FAILED with exit code $LASTEXITCODE"
}
