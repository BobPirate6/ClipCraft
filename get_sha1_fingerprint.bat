@echo off
echo Getting SHA-1 fingerprint for Firebase...
echo.

REM Check if gradlew exists
if not exist gradlew.bat (
    echo Error: gradlew.bat not found in current directory
    echo Please run this script from your project root directory
    pause
    exit /b 1
)

echo Running gradle signingReport...
echo.
call gradlew.bat signingReport

echo.
echo ========================================
echo SHA-1 fingerprints displayed above
echo ========================================
echo.
echo Copy the SHA-1 fingerprint from the debug variant
echo and add it to your Firebase project settings
echo.
pause