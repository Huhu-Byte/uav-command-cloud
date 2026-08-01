@echo off
REM ============================================================
REM Push project to GitHub
REM Repo: https://github.com/Huhu-Byte/uav-command-cloud
REM ============================================================

setlocal
set "PROJECT_DIR=%~dp0..\.."
pushd "%PROJECT_DIR%"

echo ============================================================
echo  Step 1/6: Check directory
echo ============================================================
echo Current dir: %CD%
if not exist ".gitignore" (
    echo [ERROR] .gitignore not found, wrong directory
    pause
    popd
    exit /b 1
)
echo OK
echo.

echo ============================================================
echo  Step 2/6: Check git
echo ============================================================
where git >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] git not found, install Git for Windows first
    echo Download: https://git-scm.com/download/win
    pause
    popd
    exit /b 1
)
git --version
echo OK
echo.

echo ============================================================
echo  Step 3/6: Config git user
echo ============================================================
for /f "tokens=*" %%i in ('git config --global user.name 2^>nul') do set "GIT_NAME=%%i"
for /f "tokens=*" %%i in ('git config --global user.email 2^>nul') do set "GIT_EMAIL=%%i"
if "%GIT_NAME%"=="" (
    git config --global user.name "Huhu-Byte"
)
if "%GIT_EMAIL%"=="" (
    git config --global user.email "2111585760@qq.com"
)
echo  user.name:
git config --global user.name
echo  user.email:
git config --global user.email
echo OK
echo.

echo ============================================================
echo  Step 4/6: git init + add + commit
echo ============================================================
if not exist ".git" (
    echo  Running: git init -b main
    git init -b main
    if %errorlevel% neq 0 (
        echo [ERROR] git init failed
        pause
        popd
        exit /b 1
    )
) else (
    echo  .git already exists, skip init
)

echo  Running: git add .
git add .
if %errorlevel% neq 0 (
    echo [ERROR] git add failed
    pause
    popd
    exit /b 1
)

echo  Running: git commit
git commit -m "Init project: DJI Dock cloud API private deployment - Spring Boot backend + Vue3 frontend"
if %errorlevel% neq 0 (
    echo  [INFO] Maybe nothing to commit, continue...
)
echo OK
echo.

echo ============================================================
echo  Step 5/6: Add remote origin
echo ============================================================
set "REMOTE_URL=https://github.com/Huhu-Byte/uav-command-cloud.git"
set "EXISTING_ORIGIN="
for /f "tokens=*" %%i in ('git remote get-url origin 2^>nul') do set "EXISTING_ORIGIN=%%i"
if "%EXISTING_ORIGIN%"=="" (
    echo  Adding origin: %REMOTE_URL%
    git remote add origin "%REMOTE_URL%"
) else (
    echo  Origin exists: %EXISTING_ORIGIN%
    if /i not "%EXISTING_ORIGIN%"=="%REMOTE_URL%" (
        echo  Updating origin to: %REMOTE_URL%
        git remote set-url origin "%REMOTE_URL%"
    )
)
echo OK
echo.

echo ============================================================
echo  Step 6/6: Push to GitHub
echo ============================================================
echo  A login window may popup, sign in to GitHub
echo  If asked for password, use Personal Access Token
echo.

set "BRANCH=main"
for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do set "BRANCH=%%i"
echo  Current branch: %BRANCH%

git push -u origin %BRANCH%
if %errorlevel% neq 0 (
    echo.
    echo [FAILED] Push failed. Common reasons:
    echo   1. Not logged in - run: gh auth login
    echo   2. Use Token instead of password
    echo   3. Install Git Credential Manager
    echo.
    pause
    popd
    exit /b 1
)

echo.
echo ============================================================
echo  SUCCESS! Pushed to GitHub!
echo  Repo: https://github.com/Huhu-Byte/uav-command-cloud
echo ============================================================

popd
endlocal
pause
