@echo off
echo ===============================================
echo Creating Release Keystore for ClipCraft
echo ===============================================
echo.
echo IMPORTANT: Remember the passwords you enter!
echo You will need them for every release.
echo.

REM Создаем папку для хранения ключей
if not exist "keystore" mkdir keystore

REM Генерируем release keystore
keytool -genkey -v -keystore keystore\clipcraft-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias clipcraft-release

echo.
echo ===============================================
echo Keystore created successfully!
echo ===============================================
echo.
echo Now getting SHA-1 and SHA-256 fingerprints...
echo.
keytool -list -v -keystore keystore\clipcraft-release.jks -alias clipcraft-release

echo.
echo ===============================================
echo NEXT STEPS:
echo 1. Copy the SHA-1 and SHA-256 fingerprints above
echo 2. Add them to Firebase Console
echo 3. Download updated google-services.json
echo 4. Configure signing in build.gradle
echo ===============================================
pause