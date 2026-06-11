@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

:: ============================================================
::   需求管理系统 - 唯一启动入口
::   用法:
::     start-all.bat            一键启动全部服务（默认）
::     start-all.bat check      仅健康检查（不启动应用）
::     start-all.bat down       停止所有 Docker 容器
::     start-all.bat e2e        跑全链路 E2E（依赖 + 后端 + 前端 + 测试）
::     start-all.bat contract   导出后端 OpenAPI 契约
:: ============================================================

set "ROOT_DIR=%~dp0"
set "COMPOSE_FILE=%ROOT_DIR%scripts\docker-compose.yml"
set "FRONTEND_PORT=5170"
set "BACKEND_PORT=8081"
set "KKFILEVIEW_PORT=8012"

if /i "%1"=="check" goto :do_check
if /i "%1"=="down"  goto :do_down
if /i "%1"=="e2e"   goto :do_e2e
if /i "%1"=="contract" goto :do_contract
goto :do_start

:: ============================================================
:do_start
echo.
echo ============================================
echo   需求管理系统 - 全量启动
echo ============================================
echo.

:: 1) 启动 Docker 容器（含 mysql/redis/rabbitmq/minio/milvus/kkfileview）
echo [1/4] 启动 Docker 容器...
docker ps --format "table {{.Names}}" | findstr /C:"kkfileview" >nul 2>&1
if errorlevel 1 (
    echo   启动 docker compose 全部服务...
    docker compose -f "%COMPOSE_FILE%" up -d
) else (
    echo   kkfileview 容器已运行 ✓，其它容器按需启动
    docker compose -f "%COMPOSE_FILE%" up -d
)

:: 2) 健康检查 - 等待关键容器就绪
echo.
echo [2/4] 健康检查...
call :wait_healthy kkfileview %KKFILEVIEW_PORT% "kkFileView"
call :wait_healthy mysql 3306 "MySQL"
call :wait_healthy redis 6379 "Redis"
call :wait_healthy minio 9000 "MinIO"

:: 3) 启动后端
echo.
echo [3/4] 启动后端服务（端口%BACKEND_PORT%）...
start "DemandBackend" cmd /k "cd /d %ROOT_DIR%demand_backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev"

:: 4) 启动前端
echo.
echo [4/4] 启动前端服务（端口%FRONTEND_PORT%）...
start "DemandFrontend" cmd /k "cd /d %ROOT_DIR%demand_frontend && npm run dev"

echo.
echo ============================================
echo   启动完成！
echo ============================================
echo.
echo   访问地址：
echo     - 前端:        http://127.0.0.1:%FRONTEND_PORT%
echo     - 后端:        http://localhost:%BACKEND_PORT%
echo     - kkFileView:  http://localhost:%KKFILEVIEW_PORT%
echo     - MinIO 控制台: http://localhost:9001
echo     - RabbitMQ:    http://localhost:15672
echo.
echo   其它命令：
echo     start-all.bat check      健康检查
echo     start-all.bat down       停止所有容器
echo     start-all.bat e2e        跑全链路 E2E
echo     start-all.bat contract   导出 OpenAPI 契约
echo.
exit /b 0

:: ============================================================
:do_check
echo.
echo ============================================
echo   基础设施 + kkFileView 健康检查
echo ============================================
echo.
set "FAILED=0"
call :check_service mysql 3306
call :check_service redis 6379
call :check_service rabbitmq 5672
call :check_service minio 9000
call :check_service elasticsearch 9200
call :check_service milvus 19530
call :check_service kkfileview 8012
echo.
if "%FAILED%"=="0" (
    echo   ✅ 所有服务连接正常
    exit /b 0
) else (
    echo   ❌ 有 %FAILED% 个服务连接异常
    echo   修复方法：执行 start-all.bat 启动
    exit /b 1
)

:: ============================================================
:do_down
echo.
echo ============================================
echo   停止所有 Docker 容器
echo ============================================
echo.
docker compose -f "%COMPOSE_FILE%" down
echo.
echo   已停止
exit /b 0

:: ============================================================
:do_e2e
echo.
echo ============================================
echo   全链路 E2E 测试
echo ============================================
echo.

:: 启动依赖
echo [1/4] 启动依赖服务...
docker compose -f "%COMPOSE_FILE%" up -d
call :wait_healthy mysql 3306 "MySQL"
call :wait_healthy redis 6379 "Redis"
call :wait_healthy minio 9000 "MinIO"

:: 启动后端
echo.
echo [2/4] 启动后端...
for /f "tokens=*" %%i in ('powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort %BACKEND_PORT% -ErrorAction SilentlyContinue).OwningProcess"') do set "EXIST_PID=%%i"
if defined EXIST_PID (
    echo   后端端口 %BACKEND_PORT% 已被 PID %EXIST_PID% 占用，先杀掉...
    taskkill /F /PID %EXIST_PID% >nul 2>&1
)
start "DemandBackend-E2E" cmd /k "cd /d %ROOT_DIR%demand_backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev"

:: 等待后端就绪
echo   等待后端就绪...
set /a ITER=0
:wait_backend
set /a ITER+=1
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:%BACKEND_PORT%/v3/api-docs; if($r.StatusCode -eq 200){exit 0}else{exit 1}}catch{exit 1}" >nul 2>&1
if not errorlevel 1 goto backend_ready
if %ITER% GEQ 60 (
    echo   ⚠️  后端 120 秒内未就绪，E2E 终止
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto wait_backend
:backend_ready
echo   后端已就绪 ✓

:: 启动前端
echo.
echo [3/4] 启动前端（端口5176）...
set "E2E_PORT=5176"
start "DemandFrontend-E2E" cmd /k "cd /d %ROOT_DIR%demand_frontend && npm run dev -- --port %E2E_PORT% --host 0.0.0.0"

:: 等待前端就绪
echo   等待前端就绪...
set /a ITER=0
:wait_frontend
set /a ITER+=1
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:%E2E_PORT%/; if($r.StatusCode -eq 200){exit 0}else{exit 1}}catch{exit 1}" >nul 2>&1
if not errorlevel 1 goto frontend_ready
if %ITER% GEQ 60 (
    echo   ⚠️  前端 120 秒内未就绪，E2E 终止
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto wait_frontend
:frontend_ready
echo   前端已就绪 ✓

:: 跑 E2E
echo.
echo [4/4] 跑 Playwright E2E...
cd /d %ROOT_DIR%demand_frontend
call npm install -s
set "E2E_BASE_URL=http://127.0.0.1:%E2E_PORT%"
call npm run -s test:e2e
set "E2E_RC=%errorlevel%"

echo.
if %E2E_RC%==0 (
    echo   ✅ E2E 测试通过
) else (
    echo   ❌ E2E 测试失败，退出码 %E2E_RC%
)

:: 清理 E2E 启动的进程
echo.
echo 清理 E2E 启动的进程...
taskkill /FI "WINDOWTITLE eq DemandBackend-E2E*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq DemandFrontend-E2E*" /T /F >nul 2>&1
docker compose -f "%COMPOSE_FILE%" down >nul 2>&1

exit /b %E2E_RC%

:: ============================================================
:do_contract
echo.
echo ============================================
echo   导出 OpenAPI 契约
echo ============================================
echo.
if not exist "%ROOT_DIR%contracts\openapi" mkdir "%ROOT_DIR%contracts\openapi"
echo   从 http://localhost:%BACKEND_PORT%/v3/api-docs 拉取...
powershell -NoProfile -Command "try{Invoke-WebRequest -UseBasicParsing -OutFile '%ROOT_DIR%contracts\openapi\openapi.latest.json' http://127.0.0.1:%BACKEND_PORT%/v3/api-docs; exit 0}catch{exit 1}"
if errorlevel 1 (
    echo   ❌ 拉取失败，请先执行 start-all.bat 启动后端
    exit /b 1
)
echo   ✅ 已保存到 %ROOT_DIR%contracts\openapi\openapi.latest.json
exit /b 0

:: ============================================================
::  工具函数
:: ============================================================

:wait_healthy
set "NAME=%~1"
set "PORT=%~2"
set "LABEL=%~3"
echo   等待 %LABEL% (%NAME%:%PORT%) 就绪...
set /a ITER=0
:wait_healthy_loop
set /a ITER+=1
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.BeginConnect('127.0.0.1',%PORT%,$null,$null)|Out-Null;$i=[Net.Sockets.TcpClient]::new();$i.Connect('127.0.0.1',%PORT%);$i.Close();exit 0}catch{exit 1}" >nul 2>&1
if not errorlevel 1 goto wait_healthy_ok
if %ITER% GEQ 30 (
    echo     ⚠️  %LABEL% 30 秒内未就绪，继续（不阻塞）
    goto :eof
)
timeout /t 1 /nobreak >nul
goto wait_healthy_loop
:wait_healthy_ok
echo     %LABEL% 已就绪 ✓
goto :eof

:check_service
set "NAME=%~1"
set "PORT=%~2"
docker ps -a --format "{{.Names}}" | findstr /C:"%NAME%" >nul 2>&1
if errorlevel 1 (
    echo   ❌ %NAME% ^(端口 %PORT%^) - 容器不存在
    set /a FAILED+=1
    goto :eof
)
for /f "tokens=*" %%s in ('docker inspect -f "{{.State.Status}}" %NAME% 2^>nul') do set "STATUS=%%s"
if not "%STATUS%"=="running" (
    echo   ❌ %NAME% ^(端口 %PORT%^) - 容器未运行
    set /a FAILED+=1
    goto :eof
)
for /f "tokens=*" %%s in ('docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}" %NAME% 2^>nul') do set "HEALTH=%%s"
if "%HEALTH%"=="healthy" (
    echo   ✅ %NAME% ^(端口 %PORT%^) - 连接正常
) else if "%HEALTH%"=="no-healthcheck" (
    echo   ✅ %NAME% ^(端口 %PORT%^) - 连接正常
) else (
    echo   ⚠️  %NAME% ^(端口 %PORT%^) - 健康检查异常 (%HEALTH%)
)
goto :eof
