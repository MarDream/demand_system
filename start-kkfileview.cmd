@echo off
chcp 65001 >nul
echo.
echo ============================================
echo   kkFileView 预览服务 - 容器化启动
echo ============================================
echo.

:: 检查 kkfileview 容器是否已在运行
docker ps --format "table {{.Names}}" | findstr /C:"kkfileview" >nul
if not errorlevel 1 (
    echo   kkfileview 容器已在运行 ✓
    echo   端口：http://localhost:8012
    echo.
    pause
    exit /b 0
)

:: 容器未运行：启动 docker compose 拉起 kkfileview
echo   kkfileview 容器未运行，正在通过 docker compose 启动...
docker compose -f E:\Project\Vue_demo\demand_system\scripts\docker-compose.yml up -d kkfileview
if errorlevel 1 (
    echo.
    echo   ❌ docker compose 启动失败
    echo   常见原因：
    echo     1. 镜像 kkfileview:5.0.1 未构建，参考：
    echo        cd E:\Project\mygit\kkFileView ^&^& ./scripts/build-docker.sh 5.0.1
    echo     2. 其它基础设施容器（mysql/redis/minio）未运行
    echo        可先跑 E:\Project\Vue_demo\demand_system\start-all.bat
    echo.
    pause
    exit /b 1
)

:: 等待健康
echo   等待 kkfileview 健康检查通过...
set /a KK_ITER=0
:wait_kkfileview
set /a KK_ITER+=1
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:8012/; if($r.StatusCode -eq 200){exit 0}else{exit 1}}catch{exit 1}" >nul 2>&1
if not errorlevel 1 goto kkfileview_ready
if %KK_ITER% GEQ 30 (
    echo   ⚠️  30 秒内未就绪，请执行 docker logs kkfileview 查看原因
    goto kkfileview_done
)
timeout /t 1 /nobreak >nul
goto wait_kkfileview
:kkfileview_ready
echo   kkfileview 已就绪 ✓
:kkfileview_done
echo.
echo   访问地址：http://localhost:8012
echo.
pause
