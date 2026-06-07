@echo off
chcp 65001 >nul
echo.
echo ============================================
echo   需求管理系统 - 全量启动
echo ============================================
echo.

:: 检查并启动 Docker 容器
echo [1/3] 检查 Docker 容器...
docker ps --format "table {{.Names}}\t{{.Status}}" | findstr /C:"mysql" >nul
if errorlevel 1 (
    echo   Docker 容器未运行，正在启动...
    docker-compose -f E:\Project\Vue_demo\demand_system\scripts\docker-compose.yml up -d
) else (
    echo   Docker 容器已运行 ✓
)

:: 启动 kkFileView 服务
echo [2/3] 启动 kkFileView 预览服务...
start "kkFileView" cmd /k "cd /d E:\Project\mygit\kkFileView && startup.bat"

:: 启动后端
echo [3/3] 启动后端服务...
start "DemandBackend" cmd /k "cd /d E:\Project\Vue_demo\demand_system\demand_backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev"

:: 启动前端
echo.
echo 启动前端服务...
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
pause