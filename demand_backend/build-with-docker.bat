@echo off
REM 使用 Docker 容器中的 Java 17 编译项目

docker run --rm ^
  -v "%cd%":/app ^
  -w /app ^
  maven:3.9.8-eclipse-temurin-17 ^
  mvn clean package -DskipTests

echo.
echo 编译完成！可以使用以下命令启动：
echo mvn spring-boot:run -Dspring-boot.run.profiles=dev
