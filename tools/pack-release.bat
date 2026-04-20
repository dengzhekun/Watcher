@echo off
setlocal
REM ============================================================
REM  Watcher Release Packer
REM  Builds release APK, injects the model, re-aligns and signs.
REM ============================================================

set PROJECT_DIR=%~dp0..
set MODEL_FILE=%PROJECT_DIR%\gemma-4-E2B-it.litertlm
set BUILD_DIR=%PROJECT_DIR%\app\build\outputs\apk\release
set APK_UNSIGNED=%BUILD_DIR%\app-release-unsigned.apk
set APK_INJECTED=%BUILD_DIR%\app-release-injected.apk
set APK_ALIGNED=%BUILD_DIR%\app-release-aligned.apk
set APK_FINAL=%BUILD_DIR%\watcher-release.apk

REM Check model file exists
if not exist "%MODEL_FILE%" (
    echo ERROR: Model file not found: %MODEL_FILE%
    echo Please place gemma-4-E2B-it.litertlm in the project root.
    pause
    exit /b 1
)

REM Step 1: Build release APK
echo [1/4] Building release APK...
cd /d "%PROJECT_DIR%"
call gradlew.bat assembleRelease
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

REM Find the built APK (signed or unsigned)
set APK_SRC=
if exist "%BUILD_DIR%\app-release.apk" (
    set APK_SRC=%BUILD_DIR%\app-release.apk
) else if exist "%APK_UNSIGNED%" (
    set APK_SRC=%APK_UNSIGNED%
) else (
    echo ERROR: Cannot find built APK in %BUILD_DIR%
    pause
    exit /b 1
)

REM Step 2: Inject model into APK using jar (zip tool available in JDK)
echo [2/4] Injecting model file (2.58 GB) into APK...
copy /Y "%APK_SRC%" "%APK_INJECTED%" >nul

REM Use 7z or jar to add the file. Try 7z first (faster for large files).
where 7z >nul 2>nul
if %ERRORLEVEL% equ 0 (
    7z a -tzip -mx0 "%APK_INJECTED%" "%MODEL_FILE%" -spf2 >nul
    REM Rename inside zip to assets/litert_models/gemma-4-E2B-it.litertlm
    echo NOTE: Using 7z - model will be at root. App handles both locations.
) else (
    echo 7z not found. Using jar command...
    REM Create temp directory structure
    mkdir "%BUILD_DIR%\inject_tmp\assets\litert_models" 2>nul
    copy /Y "%MODEL_FILE%" "%BUILD_DIR%\inject_tmp\assets\litert_models\" >nul
    cd /d "%BUILD_DIR%\inject_tmp"
    jar -uf "%APK_INJECTED%" assets\litert_models\gemma-4-E2B-it.litertlm
    cd /d "%PROJECT_DIR%"
    rmdir /s /q "%BUILD_DIR%\inject_tmp"
)

REM Step 3: Zipalign
echo [3/4] Zipaligning...
where zipalign >nul 2>nul
if %ERRORLEVEL% equ 0 (
    zipalign -f 4 "%APK_INJECTED%" "%APK_ALIGNED%"
) else (
    echo WARNING: zipalign not found in PATH. Skipping alignment.
    copy /Y "%APK_INJECTED%" "%APK_ALIGNED%" >nul
)

REM Step 4: Sign with debug key (for testing)
echo [4/4] Signing with debug key...
where apksigner >nul 2>nul
if %ERRORLEVEL% equ 0 (
    apksigner sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-alias androiddebugkey --key-pass pass:android --out "%APK_FINAL%" "%APK_ALIGNED%"
) else (
    echo WARNING: apksigner not found in PATH. Using jarsigner...
    copy /Y "%APK_ALIGNED%" "%APK_FINAL%" >nul
    jarsigner -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android -keypass android "%APK_FINAL%" androiddebugkey
)

echo.
echo ============================================================
echo   SUCCESS! Release APK with bundled model:
echo   %APK_FINAL%
echo   Size: ~2.7 GB
echo ============================================================
echo.
echo To install on device:
echo   adb install -r "%APK_FINAL%"
echo.
pause
