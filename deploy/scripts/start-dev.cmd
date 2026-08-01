@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM 本机开发模式启动 - 同时启动后端和前端开发服务器
REM ============================================================
REM 前提：
REM   1. 已安装 Java 17 和 Maven
REM   2. 已安装 Node.js 并在 frontend 目录执行过 npm install
REM
REM 用法：
REM   deploy\scripts\start-dev.cmd
REM ============================================================

pushd "%~dp0..\.."

REM 读取 .env
if exist "deploy\.env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in ("deploy\.env") do (
        set "%%a=%%b"
    )
)

echo ============================================================
echo  无人机巡检系统 - 开发模式启动
echo ============================================================
echo  后端: http://127.0.0.1:8080  （Maven 直接运行）
echo  前端: http://127.0.0.1:5173  （Vite 热更新）
echo ============================================================
echo.

REM 启动后端（新窗口）
start "UAV Backend" cmd /k "cd backend && mvn spring-boot:run"

REM 等待后端启动
echo 等待后端启动...
timeout /t 5 /nobreak >nul

REM 启动前端（当前窗口）
echo 启动前端开发服务器...
cd frontend
npm run dev -- --host 127.0.0.1

popd
