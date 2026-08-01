@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM 停止 Docker 容器
REM ============================================================
REM 用法：
REM   deploy\scripts\stop-docker.cmd        停止容器，保留数据
REM   deploy\scripts\stop-docker.cmd clean  停止容器并删除数据（慎用！）
REM ============================================================

pushd "%~dp0..\.."

if /i "%1"=="clean" (
    echo ============================================================
    echo  停止容器并删除所有数据...
    echo  （MySQL 数据将被删除！）
    echo ============================================================
    docker compose --env-file deploy/.env -f deploy/compose.yaml down --volumes
    echo.
    echo 已停止并清理完毕。
) else (
    echo ============================================================
    echo  停止容器（保留数据）...
    echo ============================================================
    docker compose --env-file deploy/.env -f deploy/compose.yaml down
    echo.
    echo 已停止。数据已保留，下次启动会自动恢复。
    echo 如需彻底清理数据，执行: deploy\scripts\stop-docker.cmd clean
)

popd
