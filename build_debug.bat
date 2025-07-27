@echo off
echo ===============================================
echo Building ClipCraft Debug APK
echo ===============================================

REM Сборка Debug APK
echo Building Debug APK...
call gradlew assembleDebug

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
echo Debug APK location:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on phone:
echo 1. Enable "Install from unknown sources" in phone settings
echo 2. Copy APK to phone via USB or cloud
echo 3. Open APK file on phone to install
echo ===============================================
pause