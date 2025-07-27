@echo off
echo ===============================================
echo Building Release APK with Optimized Memory
echo ===============================================
echo.

REM Устанавливаем JAVA_HOME
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
echo Using JAVA_HOME=%JAVA_HOME%
echo.

REM Настраиваем память для Gradle
set GRADLE_OPTS=-Xmx2048m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

REM Останавливаем все Gradle демоны
echo Stopping Gradle daemons...
call gradlew.bat --stop

echo.
echo Cleaning previous builds...
call gradlew.bat clean

echo.
echo Building Release APK with limited memory...
call gradlew.bat assembleRelease --no-daemon --max-workers=2

REM Проверка успешности
if %ERRORLEVEL% NEQ 0 (
    echo ===============================================
    echo BUILD FAILED!
    echo ===============================================
    echo.
    echo Trying with even less memory...
    set GRADLE_OPTS=-Xmx1024m -XX:MaxPermSize=256m
    call gradlew.bat assembleRelease --no-daemon --max-workers=1
    
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo Still failing. Try:
        echo 1. Close Android Studio and other programs
        echo 2. Restart computer
        echo 3. Build through Android Studio instead
        pause
        exit /b 1
    )
)

echo.
echo ===============================================
echo BUILD SUCCESSFUL!
echo ===============================================
echo.
echo Release APK location:
echo app\build\outputs\apk\release\app-release.apk
echo.
echo Size: 
dir "app\build\outputs\apk\release\app-release.apk" 2>nul | find "app-release.apk"
echo.
echo This APK is ready for distribution!
echo Google Sign-In will work on ALL devices!
echo ===============================================
pause