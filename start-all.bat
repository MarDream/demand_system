@echo off
:: 防止在 Git Bash 中运行（Git Bash 不识别 NUL 设备，会创建 nul 空文件）
if defined MSYSTEM (
    echo [WARN] 检测到 Git Bash 环境，自动切换到 cmd.exe 执行...
    cmd.exe /c "%~f0" %*
    exit /b %errorlevel%
)
setlocal enabledelayedexpansion
chcp 65001 >NUL

:: ============================================================
::   需求管理系统 - 唯一启动入口
::   用法:
::     start-all.bat                     一键启动全部服务（默认，前后端隐藏窗口后台运行）
::     start-all.bat check               仅健康检查（不启动应用）
::     start-all.bat down                停止前后端进程 + 所有 Docker 容器
::     start-all.bat logs <backend|frontend>   实时查看后/前端日志
::     start-all.bat e2e                 跑全链路 E2E（依赖 + 后端 + 前端 + 测试）
::     start-all.bat contract            导出后端 OpenAPI 契约
:: ============================================================

set "ROOT_DIR=%~dp0"
set "COMPOSE_FILE=%ROOT_DIR%scripts\docker-compose.yml"
set "FRONTEND_PORT=5170"
set "BACKEND_PORT=8081"
set "KKFILEVIEW_PORT=8012"

:: ===== Maven 路径解析 =====
:: 优先使用项目自带的 Maven (.mvn-tool/maven/)，
:: 其次使用系统安装的 Maven (C:\Tools\apache-maven-3.9.16)，
:: 最后回退到 PATH 中的 mvn
set "PROJECT_MVN=%ROOT_DIR%.mvn-tool\maven\bin\mvn.cmd"
if exist "%PROJECT_MVN%" (
    set "MVN_CMD=%PROJECT_MVN%"
    echo [INFO] 使用项目内嵌 Maven: %PROJECT_MVN%
) else if exist "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" (
    set "MVN_CMD=C:\Tools\apache-maven-3.9.16\bin\mvn.cmd"
    echo [INFO] 使用系统 Maven: C:\Tools\apache-maven-3.9.16\bin\mvn.cmd
) else (
    set "MVN_CMD=mvn"
    echo [INFO] 使用系统 PATH 中的 mvn
)
:: JDK 17+ 的 jansi 需要原生访问权限，避免启动警告
set "MAVEN_OPTS=--enable-native-access=ALL-UNNAMED"

set "LOG_DIR=%ROOT_DIR%logs"
set "BACKEND_LOG=%LOG_DIR%\backend.log"
set "BACKEND_ERR=%LOG_DIR%\backend.err.log"
set "FRONTEND_LOG=%LOG_DIR%\frontend.log"
set "FRONTEND_ERR=%LOG_DIR%\frontend.err.log"
set "BACKEND_PID=%LOG_DIR%\backend.pid"
set "FRONTEND_PID=%LOG_DIR%\frontend.pid"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

if /i "%1"=="check" goto :do_check
if /i "%1"=="down"  goto :do_down
if /i "%1"=="e2e"   goto :do_e2e
if /i "%1"=="contract" goto :do_contract
if /i "%1"=="logs" goto :do_logs
goto :do_start

:: ============================================================
:do_start
echo.
echo ============================================
echo   需求管理系统 - 全量启动
echo ============================================
echo.

:: 1) 启动 Docker 容器（含 mysql/redis/rabbitmq/minio/milvus/kkfileview）
echo [1 of 4] 检查 Docker 容器...
call :containers_ready
if not "%CONTAINERS_READY%"=="1" goto start_containers
echo   Docker 容器已运行，跳过 compose up
goto after_containers
:start_containers
echo   启动 docker compose 全部服务...
docker compose -f "%COMPOSE_FILE%" up -d
:after_containers

:: 2) 健康检查 - 冷启动才等待关键容器就绪
echo.
echo [2 of 4] 健康检查...
if not "%CONTAINERS_READY%"=="1" goto wait_containers
echo   已检测到依赖容器可用，跳过等待
goto after_container_check
:wait_containers
call :wait_healthy kkfileview %KKFILEVIEW_PORT% "kkFileView"
call :wait_healthy mysql 3306 "MySQL"
call :wait_healthy redis 6379 "Redis"
call :wait_healthy rabbitmq 5672 "RabbitMQ"
call :wait_healthy minio 9000 "MinIO"
:after_container_check

:: 3) 启动后端
echo.
echo [3 of 4] 启动后端服务（端口%BACKEND_PORT%）...
call :is_port_listening %BACKEND_PORT%
if not "%PORT_LISTENING%"=="1" goto start_backend
echo   后端端口 %BACKEND_PORT% 已监听，复用现有服务
goto after_backend
:start_backend
echo   释放后端端口 %BACKEND_PORT%（避免残留进程导致端口冲突）...
call :kill_by_port %BACKEND_PORT% 后端
echo   后台启动后端 ^(隐藏窗口^), 日志: logs\backend.log
del /q "%BACKEND_PID%" >NUL 2>&1
powershell -NoProfile -Command "$p=Start-Process cmd -ArgumentList @('/c','chcp 65001>NUL && set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 && set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED && set LOG_PATH=%LOG_DIR% && cd /d %ROOT_DIR%demand_backend && %MVN_CMD% spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments=\"--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow\"') -WindowStyle Hidden -RedirectStandardOutput '%BACKEND_LOG%' -RedirectStandardError '%BACKEND_ERR%' -PassThru; Set-Content -Path '%BACKEND_PID%' -Value $p.Id -Encoding ascii"
:after_backend

:: 4) 启动前端
echo.
echo [4 of 4] 启动前端服务（端口%FRONTEND_PORT%）...
call :is_port_listening %FRONTEND_PORT%
if not "%PORT_LISTENING%"=="1" goto start_frontend
echo   前端端口 %FRONTEND_PORT% 已监听，复用现有服务
goto after_frontend
:start_frontend
echo   后台启动前端 ^(隐藏窗口^), 日志: logs\frontend.log
del /q "%FRONTEND_PID%" >NUL 2>&1
powershell -NoProfile -Command "$p=Start-Process cmd -ArgumentList @('/c','chcp 65001>NUL && cd /d %ROOT_DIR%demand_frontend && npm run dev') -WindowStyle Hidden -RedirectStandardOutput '%FRONTEND_LOG%' -RedirectStandardError '%FRONTEND_ERR%' -PassThru; Set-Content -Path '%FRONTEND_PID%' -Value $p.Id -Encoding ascii"
:after_frontend

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
echo   日志查看（实时 tail，Ctrl+C 退出）：
echo     start-all.bat logs backend
echo     start-all.bat logs frontend
echo.
echo   其它命令：
echo     start-all.bat check      健康检查
echo     start-all.bat down       停止前后端 + 所有容器
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
echo   停止前后端进程 + 所有 Docker 容器
echo ============================================
echo.
echo [1/3] 停止后端...
call :kill_by_pid_file "%BACKEND_PID%" 后端
call :kill_by_port %BACKEND_PORT% 后端
echo [2/3] 停止前端...
call :kill_by_pid_file "%FRONTEND_PID%" 前端
call :kill_by_port %FRONTEND_PORT% 前端
echo [3/3] 停止 Docker 容器...
docker compose -f "%COMPOSE_FILE%" down
echo.
echo   已停止
exit /b 0

:: ============================================================
:do_logs
set "WHICH=%~2"
if /i "%WHICH%"=="backend" (
    set "TARGET=%BACKEND_LOG%"
    goto tail_log
)
if /i "%WHICH%"=="frontend" (
    set "TARGET=%FRONTEND_LOG%"
    goto tail_log
)
echo 用法: start-all.bat logs backend^|frontend
exit /b 1
:tail_log
if not exist "%TARGET%" (
    echo 日志文件不存在: %TARGET%
    echo 服务可能尚未启动，先执行 start-all.bat
    exit /b 1
)
echo 实时查看 %TARGET% ^(Ctrl+C 退出^)
echo --------------------------------------------
powershell -NoProfile -Command "Get-Content -Path '%TARGET%' -Wait -Tail 100"
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
call :wait_healthy rabbitmq 5672 "RabbitMQ"
call :wait_healthy minio 9000 "MinIO"

:: 启动后端
echo.
echo [2/4] 启动后端...
call :kill_by_port %BACKEND_PORT% 后端
del /q "%BACKEND_PID%" >NUL 2>&1
powershell -NoProfile -Command "$p=Start-Process cmd -ArgumentList @('/c','chcp 65001>NUL && set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 && set MAVEN_OPTS=--enable-native-access=ALL-UNNAMED && set LOG_PATH=%LOG_DIR% && cd /d %ROOT_DIR%demand_backend && %MVN_CMD% spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments=\"--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow\"') -WindowStyle Hidden -RedirectStandardOutput '%BACKEND_LOG%' -RedirectStandardError '%BACKEND_ERR%' -PassThru; Set-Content -Path '%BACKEND_PID%' -Value $p.Id -Encoding ascii"

:: 等待后端就绪
echo   等待后端就绪...
set /a ITER=0
:wait_backend
set /a ITER+=1
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:%BACKEND_PORT%/v3/api-docs; if($r.StatusCode -eq 200){exit 0}else{exit 1}}catch{exit 1}" >NUL 2>&1
if not errorlevel 1 goto backend_ready
if %ITER% GEQ 60 (
    echo   ⚠️  后端 120 秒内未就绪，E2E 终止
    exit /b 1
)
timeout /t 2 /nobreak >NUL
goto wait_backend
:backend_ready
echo   后端已就绪 ✓

:: 启动前端
echo.
echo [3/4] 启动前端（端口5176）...
set "E2E_PORT=5176"
del /q "%FRONTEND_PID%" >NUL 2>&1
powershell -NoProfile -Command "$p=Start-Process cmd -ArgumentList @('/c','chcp 65001>NUL && cd /d %ROOT_DIR%demand_frontend && npm run dev -- --port %E2E_PORT% --host 0.0.0.0') -WindowStyle Hidden -RedirectStandardOutput '%FRONTEND_LOG%' -RedirectStandardError '%FRONTEND_ERR%' -PassThru; Set-Content -Path '%FRONTEND_PID%' -Value $p.Id -Encoding ascii"

:: 等待前端就绪
echo   等待前端就绪...
set /a ITER=0
:wait_frontend
set /a ITER+=1
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:%E2E_PORT%/; if($r.StatusCode -eq 200){exit 0}else{exit 1}}catch{exit 1}" >NUL 2>&1
if not errorlevel 1 goto frontend_ready
if %ITER% GEQ 60 (
    echo   ⚠️  前端 120 秒内未就绪，E2E 终止
    exit /b 1
)
timeout /t 2 /nobreak >NUL
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
call :kill_by_pid_file "%BACKEND_PID%" 后端
call :kill_by_port %BACKEND_PORT% 后端
call :kill_by_pid_file "%FRONTEND_PID%" 前端
call :kill_by_port %E2E_PORT% 前端
docker compose -f "%COMPOSE_FILE%" down >NUL 2>&1

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

:kill_by_pid_file
set "PID_FILE=%~1"
set "LABEL=%~2"
if not exist "%PID_FILE%" goto :eof
set "TARGET_PID="
set /p TARGET_PID=<"%PID_FILE%"
if defined TARGET_PID (
    taskkill /F /T /PID %TARGET_PID% >NUL 2>&1
    if not errorlevel 1 echo   已停止 %LABEL% PID %TARGET_PID%
)
del /q "%PID_FILE%" >NUL 2>&1
goto :eof

:kill_by_port
set "PORT=%~1"
set "LABEL=%~2"
for /f "tokens=*" %%i in ('powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue).OwningProcess | Select-Object -Unique"') do (
    taskkill /F /T /PID %%i >NUL 2>&1
    if not errorlevel 1 echo   已停止 %LABEL% 端口 %PORT% PID %%i
)
goto :eof

:is_port_listening
set "PORT=%~1"
set "PORT_LISTENING=0"
powershell -NoProfile -Command "if(Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue){exit 0}else{exit 1}" >NUL 2>&1
if not errorlevel 1 set "PORT_LISTENING=1"
goto :eof

:is_container_ready
set "CONTAINER_NAME=%~1"
set "CONTAINER_READY=0"
set "CONTAINER_STATUS="
set "CONTAINER_HEALTH="
for /f "tokens=*" %%s in ('docker inspect -f "{{.State.Status}}" %CONTAINER_NAME% 2^>NUL') do set "CONTAINER_STATUS=%%s"
if not "%CONTAINER_STATUS%"=="running" goto :eof
for /f "tokens=*" %%s in ('docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}" %CONTAINER_NAME% 2^>NUL') do set "CONTAINER_HEALTH=%%s"
if "%CONTAINER_HEALTH%"=="starting" goto :eof
if "%CONTAINER_HEALTH%"=="unhealthy" goto :eof
set "CONTAINER_READY=1"
goto :eof

:containers_ready
set "CONTAINERS_READY=1"
for %%c in (mysql redis rabbitmq minio elasticsearch milvus kkfileview) do (
    call :is_container_ready %%c
    if not "!CONTAINER_READY!"=="1" set "CONTAINERS_READY=0"
)
goto :eof

:wait_healthy
set "NAME=%~1"
set "PORT=%~2"
set "LABEL=%~3"
echo   等待 %LABEL% (%NAME%:%PORT%) 就绪...
set /a ITER=0
:wait_healthy_loop
set /a ITER+=1
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.BeginConnect('127.0.0.1',%PORT%,$null,$null)|Out-Null;$i=[Net.Sockets.TcpClient]::new();$i.Connect('127.0.0.1',%PORT%);$i.Close();exit 0}catch{exit 1}" >NUL 2>&1
if not errorlevel 1 goto wait_healthy_ok
if %ITER% GEQ 30 (
    echo     ⚠️  %LABEL% 30 秒内未就绪，继续（不阻塞）
    goto :eof
)
timeout /t 1 /nobreak >NUL
goto wait_healthy_loop
:wait_healthy_ok
echo     %LABEL% 已就绪 ✓
goto :eof

:check_service
set "NAME=%~1"
set "PORT=%~2"
docker ps -a --format "{{.Names}}" | findstr /C:"%NAME%" >NUL 2>&1
if errorlevel 1 (
    echo   ❌ %NAME% ^(端口 %PORT%^) - 容器不存在
    set /a FAILED+=1
    goto :eof
)
for /f "tokens=*" %%s in ('docker inspect -f "{{.State.Status}}" %NAME% 2^>NUL') do set "STATUS=%%s"
if not "%STATUS%"=="running" (
    echo   ❌ %NAME% ^(端口 %PORT%^) - 容器未运行
    set /a FAILED+=1
    goto :eof
)
for /f "tokens=*" %%s in ('docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}" %NAME% 2^>NUL') do set "HEALTH=%%s"
if "%HEALTH%"=="healthy" (
    echo   ✅ %NAME% ^(端口 %PORT%^) - 连接正常
) else if "%HEALTH%"=="no-healthcheck" (
    echo   ✅ %NAME% ^(端口 %PORT%^) - 连接正常
) else (
    echo   ⚠️  %NAME% ^(端口 %PORT%^) - 健康检查异常 (%HEALTH%)
)
goto :eof
