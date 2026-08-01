@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM 构建前后端产物（Docker 部署前必须先执行）
REM ============================================================
REM 前提：
REM   1. 已安装 Java 17 和 Maven
REM   2. 已安装 Node.js 并在 frontend 目录执行过 npm install
REM
REM 用法：
REM   deploy\scripts\build-all.cmd
REM ============================================================

pushd "%~dp0..\.."

echo ============================================================
echo  构建后端 JAR...
echo ============================================================
cd backend
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo [错误] 后端构建失败
    popd
    exit /b 1
)
cd ..

echo.
echo ============================================================
echo  构建前端网页文件...
echo ============================================================
cd frontend
call npm run build
if %errorlevel% neq 0 (
    echo [错误] 前端构建失败
    popd
    exit /b 1
)
cd ..

echo.
echo ============================================================
echo  构建完成！
echo ============================================================
echo  后端 JAR: backend\target\uav-command-backend-0.0.1-SNAPSHOT.jar
echo  前端 dist: frontend\dist\
echo.
echo  接下来执行 deploy\scripts\start-docker.cmd 启动容器
echo ============================================================

popd
