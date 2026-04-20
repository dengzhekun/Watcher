@echo off
REM ============================================================
REM  Watcher One-Click Installer (APK + Model)
REM  Usage: Put this script, APK, and model in the same folder.
REM         Connect device via USB, run this script.
REM ============================================================

set SCRIPT_DIR=%~dp0
set APK_FILE=%SCRIPT_DIR%app-release.apk
set MODEL_FILE=%SCRIPT_DIR%gemma-4-E2B-it.litertlm

echo ============================================================
echo   Watcher Installer
echo ============================================================
echo.

REM Check files exist
if not exist "%APK_FILE%" (
    echo ERROR: APK not found: %APK_FILE%
    pause
    exit /b 1
)
if not exist "%MODEL_FILE%" (
    echo ERROR: Model file not found: %MODEL_FILE%
    pause
    exit /b 1
)

REM Check ADB
adb devices >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: adb not found. Please add Android SDK platform-tools to PATH.
    pause
    exit /b 1
)

echo [1/2] Installing APK...
adb install -r "%APK_FILE%"
if %ERRORLEVEL% neq 0 (
    echo APK installation failed!
    pause
    exit /b 1
)
echo      APK installed successfully.
echo.

echo [2/2] Pushing model file (2.58 GB, please wait)...
adb push "%MODEL_FILE%" /data/local/tmp/gemma-4-E2B-it.litertlm
if %ERRORLEVEL% neq 0 (
    echo Model push failed!
    pause
    exit /b 1
)
echo      Model pushed successfully.
echo.

echo ============================================================
echo   DONE! Open Watcher app - model will auto-load.
echo ============================================================
echo.
pause
