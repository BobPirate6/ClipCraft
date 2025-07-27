Write-Host "Getting SHA-1 fingerprint for Firebase..." -ForegroundColor Green
Write-Host ""

# Check if gradlew exists
if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "Error: gradlew.bat not found in current directory" -ForegroundColor Red
    Write-Host "Please run this script from your project root directory"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Running gradle signingReport..." -ForegroundColor Yellow
Write-Host ""
& .\gradlew.bat signingReport

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SHA-1 fingerprints displayed above" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Copy the SHA-1 fingerprint from the debug variant" -ForegroundColor Yellow
Write-Host "and add it to your Firebase project settings" -ForegroundColor Yellow
Write-Host ""
Read-Host "Press Enter to exit"