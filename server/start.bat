@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo   正在启动 心晴校园 HeartSync 后端服务...
echo   (零依赖，无需 npm install；按 Ctrl+C 停止服务)
echo.

node server.js

pause
