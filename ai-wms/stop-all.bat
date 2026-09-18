@echo off
chcp 65001 >nul
setlocal

echo ============================================================
echo   AI-WMS - 停止所有服务
echo ============================================================
echo.

REM ---------- 停止 Java 后端（8080） ----------
echo [1/3] 停止 Java 后端（8080）...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080 " ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   已停止 PID %%a
)

REM ---------- 停止 Python Agent（8000） ----------
echo.
echo [2/3] 停止 Python Agent（8000）...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8000 " ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   已停止 PID %%a
)

REM ---------- 停止前端（5173） ----------
echo.
echo [3/3] 停止前端（5173）...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173 " ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
    echo   已停止 PID %%a
)

echo.
echo ============================================================
echo   所有服务已停止
echo ============================================================
echo.
pause
