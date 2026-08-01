@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM 本机启动脚本 - 直接用 Java 运行后端 JAR
REM ============================================================
REM 前提：
REM   1. 已安装 Java 17（JDK 或 JRE）
REM   2. 已在 backend 目录执行过 mvn package -DskipTests
REM   3. 已复制 deploy/.env.example 为 deploy/.env 并按需修改
REM
REM 用法：
REM   deploy\scripts\start-local.cmd
REM ============================================================

REM 切换到项目根目录（脚本的上级的上级目录）
pushd "%~dp0..\.."

REM 读取 .env 文件中的环境变量（如果存在）
if exist "deploy\.env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in ("deploy\.env") do (
        set "%%a=%%b"
    )
)

REM 查找 Java
set "JAVA_CMD=java"
where java >nul 2>&1
if %errorlevel% neq 0 (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17*\bin\java.exe" (
        for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_CMD=%%i\bin\java.exe"
    ) else if exist "%USERPROFILE%\.tools\jdk-17*\bin\java.exe" (
        for /d %%i in ("%USERPROFILE%\.tools\jdk-17*") do set "JAVA_CMD=%%i\bin\java.exe"
    ) else (
        echo [错误] 未找到 Java 17，请先安装或加入 PATH。
        popd
        exit /b 1
    )
)

REM 查找后端 JAR
set "JAR_FILE=backend\target\uav-command-backend-0.0.1-SNAPSHOT.jar"
if not exist "%JAR_FILE%" (
    echo [错误] 未找到后端 JAR 文件：%JAR_FILE%
    echo        请先在 backend 目录执行：mvn package -DskipTests
    popd
    exit /b 1
)

echo ============================================================
echo  无人机巡检系统 - 本机启动
echo ============================================================
echo  Java:    %JAVA_CMD%
echo  JAR:     %JAR_FILE%
echo  端口:    8080
echo  MQTT:    %UAV_DJI_MQTT_ENABLED%
echo  握手:    %UAV_DJI_MQTT_HANDSHAKE_ENABLED%
echo  网关SN:  %UAV_DJI_MQTT_GATEWAY_SN%
echo ============================================================
echo.
echo  按 Ctrl+C 停止服务
echo.

REM 启动后端
"%JAVA_CMD%" -jar "%JAR_FILE%"

popd
