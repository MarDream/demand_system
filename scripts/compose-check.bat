@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0.."

echo ==========================================
echo   基础设施连接检查
echo ==========================================
echo.

set "FAILED=0"

call :check_service mysql 3306
call :check_service redis 6379
call :check_service rabbitmq 5672
call :check_service minio 9000
call :check_service elasticsearch 9200
call :check_service milvus 19530
call :check_service milvus-minio 9002
call :check_service milvus-etcd 2379

echo.
echo ==========================================

if "%FAILED%"=="0" (
    echo ✅ 所有基础设施服务连接正常
    echo.
    echo 请通过 IDE 或以下命令启动应用服务：
    echo   后端: cd %ROOT_DIR%\demand_backend ^&^& mvn spring-boot:run -DskipTests
    echo   前端: cd %ROOT_DIR%\demand_frontend ^&^& npm run dev
    exit /b 0
) else (
    echo ❌ 有 %FAILED% 个服务连接异常
    echo 请手动启动 Docker 容器或检查服务状态
    echo.
    echo 常用命令：
    echo   docker ps              - 查看运行中的容器
    echo   docker compose up -d   - 启动所有容器（会创建新容器）
    echo   docker logs 容器名      - 查看容器日志
    exit /b 1
)

:check_service
set "NAME=%1"
set "PORT=%2"

docker ps -a --format "{{.Names}}" | findstr /C:"%NAME%" >nul 2>&1
if errorlevel 1 (
    echo ❌ %NAME% ^^(端口 %PORT%%^) - 容器不存在，请手动启动
    set /a FAILED+=1
    goto :eof
)

for /f "tokens=*" %%s in ('docker inspect -f "{{.State.Status}}" %NAME% 2^>nul') do set "STATUS=%%s"
if "%STATUS%" neq "running" (
    echo ❌ %NAME% ^^(端口 %PORT%%^) - 容器未运行，请手动启动
    set /a FAILED+=1
    goto :eof
)

for /f "tokens=*" %%s in ('docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}" %NAME% 2^>nul') do set "HEALTH=%%s"
if "%HEALTH%"=="healthy" (
    echo ✅ %NAME% ^^(端口 %PORT%%^) - 连接正常
) else if "%HEALTH%"=="no-healthcheck" (
    echo ✅ %NAME% ^^(端口 %PORT%%^) - 连接正常
) else (
    echo ⚠️  %NAME% ^^(端口 %PORT%%^) - 健康检查异常
)
goto :eof
