@echo off
echo ===============================================
echo Searching for keytool in common locations...
echo ===============================================
echo.

REM Проверяем Android Studio JDK
set ANDROID_STUDIO_JDK=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe
if exist "%ANDROID_STUDIO_JDK%" (
    echo Found in Android Studio:
    echo %ANDROID_STUDIO_JDK%
    echo.
    echo Creating keystore with Android Studio's keytool...
    echo.
    
    REM Создаем папку для хранения ключей
    if not exist "keystore" mkdir keystore
    
    REM Генерируем release keystore
    "%ANDROID_STUDIO_JDK%" -genkey -v -keystore keystore\clipcraft-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias clipcraft-release
    
    echo.
    echo Getting SHA fingerprints...
    echo.
    "%ANDROID_STUDIO_JDK%" -list -v -keystore keystore\clipcraft-release.jks -alias clipcraft-release
    
    goto :end
)

REM Проверяем JAVA_HOME
if defined JAVA_HOME (
    set KEYTOOL=%JAVA_HOME%\bin\keytool.exe
    if exist "%KEYTOOL%" (
        echo Found in JAVA_HOME: %KEYTOOL%
        goto :found
    )
)

REM Проверяем Program Files
for %%i in (
    "C:\Program Files\Java\jdk*\bin\keytool.exe"
    "C:\Program Files\Java\jre*\bin\keytool.exe"
    "C:\Program Files (x86)\Java\jdk*\bin\keytool.exe"
    "C:\Program Files (x86)\Java\jre*\bin\keytool.exe"
) do (
    if exist "%%~i" (
        echo Found: %%~i
        set KEYTOOL=%%~i
        goto :found
    )
)

echo.
echo ERROR: keytool not found!
echo.
echo Please install Java JDK or use Android Studio's built-in JDK
echo.
echo You can also try:
echo 1. Open Android Studio
echo 2. File → Project Structure → SDK Location
echo 3. Note the JDK location
echo.
pause
exit /b 1

:found
echo Using: %KEYTOOL%
echo.

REM Создаем папку для хранения ключей
if not exist "keystore" mkdir keystore

REM Генерируем release keystore
"%KEYTOOL%" -genkey -v -keystore keystore\clipcraft-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias clipcraft-release

echo.
echo Getting SHA fingerprints...
echo.
"%KEYTOOL%" -list -v -keystore keystore\clipcraft-release.jks -alias clipcraft-release

:end
echo.
echo ===============================================
echo NEXT STEPS:
echo 1. Copy the SHA-1 and SHA-256 fingerprints above
echo 2. Add them to Firebase Console
echo 3. Download updated google-services.json
echo 4. Configure signing in build.gradle
echo ===============================================
pause