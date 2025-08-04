@echo off
echo ===============================================
echo Finding Java and Building Debug APK
echo ===============================================
echo.

REM Проверяем Android Studio JDK
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
if exist "%JAVA_HOME%\bin\java.exe" (
    echo Found Java in Android Studio: %JAVA_HOME%
    goto :build
)

REM Проверяем альтернативные пути
set JAVA_HOME=C:\Program Files\Android\Android Studio\jre
if exist "%JAVA_HOME%\bin\java.exe" (
    echo Found Java in Android Studio JRE: %JAVA_HOME%
    goto :build
)

REM Проверяем Program Files
for /d %%i in ("C:\Program Files\Java\jdk*") do (
    if exist "%%i\bin\java.exe" (
        set JAVA_HOME=%%i
        echo Found Java: %%i
        goto :build
    )
)

echo ERROR: Java not found!
echo Please install Java JDK or Android Studio
pause
exit /b 1

:build
echo.
echo Using JAVA_HOME=%JAVA_HOME%
echo.

REM Сборка Debug APK
echo Building Debug APK...
call gradlew.bat assembleDebug --no-daemon

REM Проверка успешности
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
pause