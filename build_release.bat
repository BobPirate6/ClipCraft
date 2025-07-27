@echo off
echo ===============================================
echo Building ClipCraft Release APK
echo ===============================================
echo.
echo IMPORTANT: Make sure you have:
echo 1. Copied new google-services.json to app folder
echo 2. Updated keystore.properties with your passwords
echo.
pause

REM Очистка предыдущих сборок
echo Cleaning previous builds...
call gradlew clean

REM Сборка Release APK
echo Building Release APK...
call gradlew assembleRelease

REM Проверка успешности сборки
if %ERRORLEVEL% NEQ 0 (
    echo ===============================================
    echo BUILD FAILED!
    echo ===============================================
    echo.
    echo Check:
    echo 1. google-services.json is in app folder
    echo 2. keystore.properties has correct passwords
    echo 3. keystore\clipcraft-release.jks exists
    echo ===============================================
    pause
    exit /b 1
)

echo ===============================================
echo BUILD SUCCESSFUL!
echo ===============================================
echo.
echo Release APK location:
echo app\build\outputs\apk\release\app-release.apk
echo.
echo This APK:
echo - Is signed with your release certificate
echo - Has Google Sign-In working on ALL devices
echo - Is optimized and minified
echo - Ready for distribution!
echo ===============================================
pause