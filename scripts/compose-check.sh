#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 定义需要检查的服务
SERVICES=("mysql" "redis" "rabbitmq" "minio" "elasticsearch" "milvus" "milvus-minio" "milvus-etcd")

echo "=========================================="
echo "  基础设施连接检查"
echo "=========================================="
echo ""

check_service() {
  local name="$1"
  local port="$2"

  # 检查容器是否存在
  if ! docker ps -a --format '{{.Names}}' | grep -q "^${name}$"; then
    echo "❌ $name (端口 $port) - 容器不存在，请手动启动"
    return 1
  fi

  # 检查容器是否运行
  local status
  status="$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo "unknown")"
  if [[ "$status" != "running" ]]; then
    echo "❌ $name (端口 $port) - 容器未运行，请手动启动"
    return 1
  fi

  # 检查健康状态
  local health
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$name" 2>/dev/null || echo "unknown")"

  if [[ "$health" == "healthy" || "$health" == "no-healthcheck" ]]; then
    echo "✅ $name (端口 $port) - 连接正常"
    return 0
  else
    echo "⚠️  $name (端口 $port) - 健康检查异常"
    return 1
  fi
}

failed=0

# 检查各服务
check_service "mysql" "3306" || ((failed++))
check_service "redis" "6379" || ((failed++))
check_service "rabbitmq" "5672/15672" || ((failed++))
check_service "minio" "9000/9001" || ((failed++))
check_service "elasticsearch" "9200" || ((failed++))
check_service "milvus" "19530/9091" || ((failed++))
check_service "milvus-minio" "9002" || ((failed++))
check_service "milvus-etcd" "2379" || ((failed++))

echo ""
echo "=========================================="

if [[ $failed -eq 0 ]]; then
  echo "✅ 所有基础设施服务连接正常"
  echo ""
  echo "请通过 IDE 或以下命令启动应用服务："
  echo "  后端: cd $ROOT_DIR/demand_backend && mvn spring-boot:run -DskipTests"
  echo "  前端: cd $ROOT_DIR/demand_frontend && npm run dev"
  exit 0
else
  echo "❌ 有 $failed 个服务连接异常"
  echo "请手动启动 Docker 容器或检查服务状态"
  echo ""
  echo "常用命令："
  echo "  docker ps              - 查看运行中的容器"
  echo "  docker compose up -d   - 启动所有容器（会创建新容器）"
  echo "  docker logs <容器名>    - 查看容器日志"
  exit 1
fi
