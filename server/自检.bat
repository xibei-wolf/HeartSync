@echo off
chcp 65001 >nul
setlocal

echo ============================================
echo   HeartSync 服务端自检
echo ============================================
echo.

cd /d "%~dp0"

REM ---------- 1. 检查 Node.js ----------
where node >nul 2>nul
if errorlevel 1 (
  echo [1/4] [X] 未找到 node.exe，请先安装 Node.js ^(建议 v18 以上^)
  pause
  exit /b 1
)
for /f "tokens=*" %%v in ('node -v') do echo [1/4] [OK] Node.js %%v

REM ---------- 2. 检查 .env ----------
if exist .env (
  echo [2/4] [OK] .env 存在
) else (
  echo [2/4] [X] 缺少 .env，请复制 .env.example 并填入 AI_API_KEY
  pause
  exit /b 1
)

REM ---------- 3. 启动服务并等待就绪 ----------
echo [3/4] 正在启动服务...
start "HeartSync Server" /min node server.js
timeout /t 6 /nobreak >nul

REM ---------- 4. 健康检查 ----------
curl -s --max-time 8 http://127.0.0.1:8080/api/health > health_check.tmp 2>nul
findstr /C:"\"status\":\"ok\"" health_check.tmp >nul 2>nul
if errorlevel 1 (
  echo [4/4] [X] 健康检查未通过，请查看服务端窗口的报错信息
  del health_check.tmp 2>nul
  pause
  exit /b 1
)
echo [4/4] [OK] 服务正常： http://127.0.0.1:8080/api/health
del health_check.tmp 2>nul

echo.
echo ============================================
echo   自检通过！服务端已在后台运行
echo   模拟器访问 : http://10.0.2.2:8080
echo   真机访问   : http://^<本机局域网IP^>:8080
echo   真机也可执行: adb reverse tcp:8080 tcp:8080
echo   然后在 App 设置里填 http://127.0.0.1:8080
echo ============================================
echo.
echo 按任意键关闭本窗口 ^(不影响后台服务^)...
pause >nul
