@echo off
chcp 65001 >nul 2>&1
REM ============================================================
REM 查看 Docker 容器日志
REM ============================================================
REM 用法：
REM   deploy\scripts\logs-docker.cmd          查看所有容器日志
REM   deploy\scripts\logs-docker.cmd backend  只看后端日志
REM   deploy\scripts\logs-docker.cmd mysql    只看数据库日志
REM   deploy\scripts\logs-docker.cmd web      只看前端日志
REM ============================================================

pushd "%~dp0..\.."

if "%1"=="" (
    docker compose --env-file deploy/.env -f deploy/compose.yaml logs -f --tail=100
) else (
    docker compose --env-file deploy/.env -f deploy/compose.yaml logs -f --tail=100 %1
)

popd
