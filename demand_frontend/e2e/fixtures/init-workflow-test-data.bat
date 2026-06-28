@echo off
REM 工作流测试数据初始化脚本 (Windows 版本)
REM 用途：在运行 E2E 测试前初始化必要的测试数据

setlocal

set MYSQL_CONTAINER=mysql
set MYSQL_USER=root
set MYSQL_PASSWORD=root123
set MYSQL_DATABASE=demand_system
set SQL_FILE=%~dp0workflow-test-data.sql

echo 🔄 开始初始化工作流测试数据...
echo.

REM 检查 Docker 容器是否运行
docker ps | findstr /C:"%MYSQL_CONTAINER%" >nul
if errorlevel 1 (
  echo ❌ MySQL 容器 '%MYSQL_CONTAINER%' 未运行
  exit /b 1
)

REM 执行 SQL 脚本
docker exec -i %MYSQL_CONTAINER% mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% %MYSQL_DATABASE% < "%SQL_FILE%"

if %errorlevel% equ 0 (
  echo ✅ 测试数据初始化完成
  echo.
  echo 已完成：
  echo   1. 为项目 1 创建草稿版本（带配置错误）
  echo   2. 为项目 1 创建激活版本（修复 P6 数据依赖）
  echo   3. 添加测试节点和连线
  echo.
  echo 现在可以运行：
  echo   npm run test:e2e -- workflow-validation.spec.ts
) else (
  echo ❌ 测试数据初始化失败
  exit /b 1
)

endlocal
