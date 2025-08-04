# PowerShell script to build APK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Write-Host "Setting JAVA_HOME to: $env:JAVA_HOME"
Write-Host "Building Debug APK..."
.\gradlew.bat assembleDebug --no-daemon
if ($LASTEXITCODE -eq 0) {
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "APK location: app\build\outputs\apk\debug\app-debug.apk"
} else {
    Write-Host "BUILD FAILED!" -ForegroundColor Red
}