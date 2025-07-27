@echo off
echo Getting SHA-1 fingerprint using keytool...
echo ==========================================
echo.

REM Try to find Java in common locations
set JAVA_PATHS="C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" "C:\Program Files\Android\Android Studio\jre\bin\keytool.exe" "C:\Program Files\Java\jdk-11\bin\keytool.exe" "C:\Program Files\Java\jdk-17\bin\keytool.exe"

set KEYTOOL_FOUND=false
for %%p in (%JAVA_PATHS%) do (
    if exist %%p (
        set KEYTOOL_PATH=%%p
        set KEYTOOL_FOUND=true
        goto :found
    )
)

:found
if %KEYTOOL_FOUND%==false (
    echo ERROR: keytool not found in standard locations
    echo Please run this from Android Studio Terminal instead
    pause
    exit /b 1
)

echo Found keytool at: %KEYTOOL_PATH%
echo.
echo Getting SHA-1 from debug keystore...
echo.

%KEYTOOL_PATH% -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

echo.
echo ==========================================
echo Look for "SHA1:" in the output above
echo Copy the SHA1 fingerprint and add it to Firebase Console
echo.
pause