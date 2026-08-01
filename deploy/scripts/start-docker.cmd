@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM Docker 一键启动 - MySQL + 后端 + Nginx 前端
REM ============================================================
REM 前提：
REM   1. 已安装并启动 Docker Desktop
REM   2. 已执行 deploy\scripts\build-all.cmd 构建前后端产物
REM   3. 已复制 deploy\.env.example 为 deploy\.env 并修改密码
REM
REM 用法：
REM   deploy\scripts\start-docker.cmd
REM ============================================================

pushd "%~dp0..\.."

REM 检查 .env 是否存在
if not exist "deploy\.env" (
    echo [错误] 未找到 deploy\.env 文件
    echo        请先复制 deploy\.env.example 为 deploy\.env 并修改密码
    popd
    exit /b 1
)

REM 检查后端 JAR 是否存在
if not exist "backend\target\uav-command-backend-0.0.1-SNAPSHOT.jar" (
    echo [错误] 未找到后端 JAR，请先执行 deploy\scripts\build-all.cmd
    popd
    exit /b 1
)

REM 检查前端 dist 是否存在
if not exist "frontend\dist\index.html" (
    echo [错误] 未找到前端构建产物，请先执行 deploy\scripts\build-all.cmd
    popd
    exit /b 1
)

echo ============================================================
echo  Docker 容器启动中...
echo ============================================================
echo  MySQL + 后端 + Nginx 前端
echo  访问地址: http://127.0.0.1:8088
echo ============================================================
echo.

docker compose --env-file deploy/.env -f deploy/compose.yaml up --build -d

if %errorlevel% equ 0 (
    echo.
    echo ============================================================
    echo  启动成功！
    echo  访问地址: http://127.0.0.1:8088
    echo  查看日志: deploy\scripts\logs-docker.cmd
    echo  停止服务: deploy\scripts\stop-docker.cmd
    echo ============================================================
) else (
    echo.
    echo [错误] Docker 启动失败，请检查 Docker Desktop 是否已启动
)

popd
