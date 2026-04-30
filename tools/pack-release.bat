@echo off
setlocal
REM ============================================================
REM  Watcher Release Packer
REM  Builds release APK, re-aligns, signs, and generates update metadata.
REM ============================================================

set PROJECT_DIR=%~dp0..
set VERSION_FILE=%PROJECT_DIR%\version.properties
set BUILD_DIR=%PROJECT_DIR%\app\build\outputs\apk\release
set APK_UNSIGNED=%BUILD_DIR%\app-release-unsigned.apk
set APK_ALIGNED=%BUILD_DIR%\app-release-aligned.apk
set WEBSITE_DOWNLOAD_PAGE=http://shokz-watcher.cn/download.html
set WEBSITE_DOWNLOAD_APP_BASE=http://shokz-watcher.cn/download/app
set METADATA_FILE=%BUILD_DIR%\latest.json

REM Step 1: Bump release version explicitly
echo [1/5] Bumping release version...
cd /d "%PROJECT_DIR%"
call gradlew.bat bumpReleaseVersion
if %ERRORLEVEL% neq 0 (
    echo Version bump failed!
    pause
    exit /b 1
)

set VERSION_CODE=
set VERSION_NAME_BASE=
for /f "usebackq tokens=1,2 delims==" %%A in ("%VERSION_FILE%") do (
    if /I "%%A"=="VERSION_CODE" set VERSION_CODE=%%B
    if /I "%%A"=="VERSION_NAME_BASE" set VERSION_NAME_BASE=%%B
)

if "%VERSION_CODE%"=="" (
    echo ERROR: VERSION_CODE not found in %VERSION_FILE%
    pause
    exit /b 1
)
if "%VERSION_NAME_BASE%"=="" (
    echo ERROR: VERSION_NAME_BASE not found in %VERSION_FILE%
    pause
    exit /b 1
)

set VERSION_NAME=%VERSION_NAME_BASE%.%VERSION_CODE%
set APK_VERSIONED_NAME=watcher-v%VERSION_NAME%-%VERSION_CODE%-release.apk
set APK_FINAL=%BUILD_DIR%\%APK_VERSIONED_NAME%
set APK_URL=%WEBSITE_DOWNLOAD_APP_BASE%/%APK_VERSIONED_NAME%

REM Step 2: Build release APK
echo [2/5] Building release APK...
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

REM Step 3: Zipalign
echo [3/5] Zipaligning...
where zipalign >nul 2>nul
if %ERRORLEVEL% equ 0 (
    zipalign -f 4 "%APK_SRC%" "%APK_ALIGNED%"
) else (
    echo WARNING: zipalign not found in PATH. Skipping alignment.
    copy /Y "%APK_SRC%" "%APK_ALIGNED%" >nul
)

REM Step 4: Sign with debug key (for testing)
echo [4/5] Signing with debug key...
where apksigner >nul 2>nul
if %ERRORLEVEL% equ 0 (
    apksigner sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-alias androiddebugkey --key-pass pass:android --out "%APK_FINAL%" "%APK_ALIGNED%"
) else (
    echo WARNING: apksigner not found in PATH. Using jarsigner...
    copy /Y "%APK_ALIGNED%" "%APK_FINAL%" >nul
    jarsigner -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android -keypass android "%APK_FINAL%" androiddebugkey
)

for /f "usebackq delims=" %%I in (`powershell -NoProfile -Command "(Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')"`) do set UPDATED_AT=%%I

echo [5/5] Generating update metadata...
> "%METADATA_FILE%" (
    echo {
    echo   "version": "%VERSION_NAME%",
    echo   "versionCode": %VERSION_CODE%,
    echo   "apkUrl": "%APK_URL%",
    echo   "updatedAt": "%UPDATED_AT%"
    echo }
)

echo.
echo ============================================================
echo   SUCCESS! Release APK:
echo   %APK_FINAL%
echo   Update metadata:
echo   %METADATA_FILE%
echo ============================================================
echo.
echo Upload order:
echo   1. Upload "%APK_FINAL%" to %WEBSITE_DOWNLOAD_APP_BASE%
echo   2. Upload "%METADATA_FILE%" to %WEBSITE_DOWNLOAD_APP_BASE%/latest.json
echo   3. Keep %WEBSITE_DOWNLOAD_PAGE% reachable for the in-app update button
echo.
echo To install on device:
echo   adb install -r "%APK_FINAL%"
echo.
pause
