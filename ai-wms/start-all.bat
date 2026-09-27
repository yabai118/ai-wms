@echo off
chcp 65001 >nul
setlocal

echo ============================================================
echo   AI-WMS 智能仓储管理系统 - 一键启动
echo ============================================================
echo.

cd /d "%~dp0"

REM ---------- 加载本地环境变量 ----------
REM  数据库密码、JWT 密钥都放在 .env.local 里（已被 gitignore，不会提交）。
REM  三个服务（后端 / Agent / 前端）共用这一份。
echo [0/5] 加载本地环境变量...
if not exist ".env.local" (
    echo   [X] 缺少 .env.local
    echo       请从 .env.example 复制一份并填入数据库密码与 JWT 密钥：
    echo         copy .env.example .env.local
    goto :error
)
REM  eol=# 让 # 开头的行当注释跳过；空行 for /f 默认跳过
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env.local") do set "%%a=%%b"
if "%DB_PASSWORD%"=="" (
    echo   [X] .env.local 里 DB_PASSWORD 为空
    goto :error
)
if "%JWT_SECRET%"=="" (
    echo   [X] .env.local 里 JWT_SECRET 为空
    goto :error
)
echo   [OK] 已加载 .env.local

REM ---------- 检查前置服务 ----------
echo.
echo [1/5] 检查前置服务...

netstat -an | findstr ":3306 " | findstr LISTENING >nul
if errorlevel 1 (
    echo   [X] MySQL 未启动（3306）—— 请先启动 MySQL
    goto :error
) else (
    echo   [OK] MySQL (3306)
)

netstat -an | findstr ":6379 " | findstr LISTENING >nul
if errorlevel 1 (
    echo   [!] Redis 未启动（6379）—— 缓存功能将不可用，但服务仍可启动
) else (
    echo   [OK] Redis (6379)
)

REM ---------- 启动 Java 后端 ----------
echo.
echo [2/5] 启动 Java 后端（8080）...
start "WMS-Backend" cmd /k "cd /d %~dp0wms-backend && mvn spring-boot:run"

echo       等待后端启动（约 40 秒）...
timeout /t 40 /nobreak >nul

curl -s --max-time 5 http://localhost:8080/api/health >nul 2>&1
if errorlevel 1 (
    echo   [!] 后端可能还在启动中，继续...
) else (
    echo   [OK] Java 后端已就绪
)

REM ---------- 启动 Python Agent ----------
echo.
echo [3/5] 启动 Python Agent（8000）...
start "WMS-Agent" cmd /k "cd /d %~dp0wms-agent && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"

echo       等待 Agent 启动（约 8 秒）...
timeout /t 8 /nobreak >nul

curl -s --max-time 5 http://localhost:8000/health >nul 2>&1
if errorlevel 1 (
    echo   [!] Agent 可能未启动（检查是否安装了依赖：pip install -r requirements.txt）
) else (
    echo   [OK] Python Agent 已就绪
)

REM ---------- 启动前端 ----------
echo.
echo [4/5] 启动前端（5173）...
start "WMS-Frontend" cmd /k "cd /d %~dp0wms-frontend && npm run dev"

echo       等待前端启动（约 8 秒）...
timeout /t 8 /nobreak >nul

REM ---------- 打开浏览器 ----------
echo.
echo [5/5] 打开浏览器...
start "" "http://localhost:5173"

echo.
echo ============================================================
echo   启动完成！
echo ============================================================
echo   前端：      http://localhost:5173
echo   Java 接口： http://localhost:8080/api
echo   Agent 文档：http://localhost:8000/docs
echo ============================================================
echo.
echo   关闭服务：运行 stop-all.bat
echo.
pause
goto :eof

:error
echo.
echo 启动失败，请先解决上述问题。
pause
