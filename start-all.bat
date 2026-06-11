@echo off
chcp 65001 >nul
echo.
echo ============================================
echo   需求管理系统 - 全量启动
echo ============================================
echo.

:: 检查并启动 Docker 容器（包含 kkfileview 预览服务）
echo [1/3] 检查 Docker 容器（mysql/redis/rabbitmq/minio/milvus/kkfileview）...
docker ps --format "table {{.Names}}" | findstr /C:"kkfileview" >nul
if errorlevel 1 (
    echo   kkfileview 容器未运行，正在启动 docker compose 全部服务...
    docker compose -f E:\Project\Vue_demo\demand_system\scripts\docker-compose.yml up -d
) else (
    echo   kkfileview 容器已运行 ✓
    echo   其它基础设施容器状态请参考 docker ps
)

:: 等待 kkfileview 健康（k8s/容器化部署时重要）
echo.
echo [2/3] 等待 kkfileview 预览服务就绪（端口8012）...
set /a KK_ITER=0
:wait_kkfileview
set /a KK_ITER+=1
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:8012/; if($r.StatusCode -eq 200){exit 0}else{exit 1}}catch{exit 1}" >nul 2>&1
if not errorlevel 1 goto kkfileview_ready
if %KK_ITER% GEQ 30 (
    echo   ⚠️  kkfileview 30 秒内未就绪，请执行 docker logs kkfileview 查看原因
    goto kkfileview_ready
)
timeout /t 1 /nobreak >nul
goto wait_kkfileview
:kkfileview_ready
echo   kkfileview 已就绪 ✓

:: 启动后端
echo.
echo [3/3] 启动后端服务（端口8081）...
start "DemandBackend" cmd /k "cd /d E:\Project\Vue_demo\demand_system\demand_backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev"

:: 启动前端
echo.
echo 启动前端服务（端口5170）...
cd /d E:\Project\Vue_demo\demand_system\demand_frontend
call npm run dev

echo.
echo ============================================
echo   启动完成！
echo ============================================
echo.
echo 访问地址：
echo   - 前端：http://127.0.0.1:5170
echo   - 后端：http://localhost:8081
echo   - kkFileView：http://localhost:8012
echo.
echo 注意：kkFileView 已在 Docker 容器中运行（端口8012），
echo       不再需要单独执行 start-kkfileview.cmd 启动本地 jar。
echo.
pause
