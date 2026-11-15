@echo off
chcp 65001 >nul
setlocal

set SDK=C:/Android/Sdk
set REPO=git@github.com:xibei-wolf/HeartSync.git
set HTTPS=https://github.com/xibei-wolf/HeartSync.git
REM %~dp0 ends with a backslash; strip it so paths like "%PROJ%" are valid
for %%I in ("%~dp0.") do set "BASE=%%~fI"
set "PROJ=%BASE%\HeartSync"

echo ============================================
echo   HeartSync  -  One-Click Restore
echo   Re-clone the project and restore local config
echo ============================================
echo.

if exist "%PROJ%app\build.gradle" goto :skip_clone
echo [1/4] Cloning from GitHub ...
git clone %REPO% "%PROJ%" 2>nul
if not errorlevel 1 goto :clone_ok
echo       SSH failed, retrying with HTTPS ...
git clone %HTTPS% "%PROJ%"
if errorlevel 1 goto :clone_fail
:clone_ok
echo       Clone done.
goto :step2
:clone_fail
echo.
echo [ERROR] Clone failed. Check your network, or run manually:
echo         git clone %HTTPS%
pause
exit /b 1
:skip_clone
echo [1/4] Existing project found, skip clone.

:step2
echo [2/4] Checking local SDK config ...
if exist "%PROJ%\local.properties" goto :lp_ok
if not exist "%SDK%\platform-tools\adb.exe" goto :lp_missing
echo sdk.dir=%SDK%> "%PROJ%\local.properties"
echo       Generated local.properties
goto :step3
:lp_ok
echo       local.properties already exists, skip.
:lp_missing
echo       [WARN] Android SDK not found at %SDK%
echo       Please create local.properties manually:
echo         sdk.dir=C:/Android/Sdk

:step3
echo [3/4] Checking server config ...
if exist "%PROJ%\server\.env" goto :env_ok
if not exist "%PROJ%\server\.env.example" goto :env_missing
copy /y "%PROJ%\server\.env.example" "%PROJ%\server\.env" >nul
echo       Generated server\.env from .env.example
echo       [TIP] Fill in AI_API_KEY in .env for real AI replies.
goto :step4
:env_ok
echo       server\.env already exists, skip.
:env_missing
echo       [WARN] .env.example not found.

:step4
echo [4/4] Verifying build environment ...
where git >nul 2>nul && echo       git       OK || echo       git       MISSING
where java >nul 2>nul && echo       java      OK || echo       java      MISSING (need JDK 17+)
if exist "%SDK%\platform-tools\adb.exe" (echo       adb       OK) else (echo       adb       NOT FOUND)
echo.

echo ============================================
echo   Next steps:
echo.
echo   1) Build APK
echo        cd "%PROJ%"
echo        gradlew assembleDebug
echo.
echo   2) Or open this folder in Android Studio
echo        %PROJ%
echo.
echo   3) Start the optional backend server
echo        cd server ^&^& node server.js
echo.
echo   Full guide in Chinese: docs / 二次开发指南.md
echo ============================================
echo.
pause
