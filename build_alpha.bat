@echo off
echo ===============================================
echo Building ClipCraft Alpha APK
echo ===============================================

REM Очистка предыдущих сборок
echo Cleaning previous builds...
call gradlew clean

REM Сборка Alpha APK
echo Building Alpha APK...
call gradlew assembleAlpha

REM Проверка успешности сборки
if %ERRORLEVEL% NEQ 0 (
    echo ===============================================
    echo BUILD FAILED!
    echo ===============================================
    pause
    exit /b 1
)

echo ===============================================
echo BUILD SUCCESSFUL!
echo ===============================================
echo.
echo Alpha APK location:
echo app\build\outputs\apk\alpha\
echo.
echo IMPORTANT SECURITY NOTES:
echo 1. Before first build, get certificate SHA256 hash
echo 2. Update AppSecurity.kt with the hash
echo 3. Test the APK on a device to verify security checks
echo 4. Share APK only with trusted testers
echo.
echo To get certificate hash, run the app once and check logs for:
echo "CURRENT CERTIFICATE SHA256: ..."
echo ===============================================
pause