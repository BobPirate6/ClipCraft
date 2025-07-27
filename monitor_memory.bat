@echo off
echo ===============================================
echo Memory Monitoring for ClipCraft
echo ===============================================
echo.
echo This script monitors memory usage of the ClipCraft app
echo Press Ctrl+C to stop monitoring
echo.

REM Get the package name
set PACKAGE=com.example.clipcraft

REM Check if device is connected
adb devices | find "device" >nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: No Android device connected!
    echo Please connect a device and enable USB debugging
    pause
    exit /b 1
)

echo Starting memory monitoring...
echo.

:loop
echo ===============================================
echo %date% %time%
echo ===============================================

REM Get memory info
echo Memory Usage:
adb shell dumpsys meminfo %PACKAGE% | findstr "TOTAL" | head -1

echo.
echo ExoPlayer Count:
adb shell dumpsys meminfo %PACKAGE% | findstr /i "exoplayer" | find /c /v ""

echo.
echo Native Heap:
adb shell dumpsys meminfo %PACKAGE% | findstr "Native Heap"

echo.
echo Graphics:
adb shell dumpsys meminfo %PACKAGE% | findstr "Graphics"

echo.
echo -----------------------------------------------
timeout /t 5 >nul
goto loop