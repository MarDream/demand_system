#!/bin/bash
# 工作流测试数据初始化脚本
# 用途：在运行 E2E 测试前初始化必要的测试数据

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/workflow-test-data.sql"

# Docker 容器名称（根据实际情况调整）
MYSQL_CONTAINER="mysql"
MYSQL_USER="root"
MYSQL_PASSWORD="root123"
MYSQL_DATABASE="demand_system"

echo "🔄 开始初始化工作流测试数据..."

# 检查 Docker 容器是否运行
if ! docker ps | grep -q "$MYSQL_CONTAINER"; then
  echo "❌ MySQL 容器 '$MYSQL_CONTAINER' 未运行"
  exit 1
fi

# 执行 SQL 脚本
docker exec -i "$MYSQL_CONTAINER" mysql \
  -u"$MYSQL_USER" \
  -p"$MYSQL_PASSWORD" \
  "$MYSQL_DATABASE" < "$SQL_FILE"

if [ $? -eq 0 ]; then
  echo "✅ 测试数据初始化完成"
  echo ""
  echo "已完成："
  echo "  1. 为项目 1 创建草稿版本（带配置错误）"
  echo "  2. 为项目 1 创建激活版本（修复 P6 数据依赖）"
  echo "  3. 添加测试节点和连线"
  echo ""
  echo "现在可以运行："
  echo "  npm run test:e2e -- workflow-validation.spec.ts"
else
  echo "❌ 测试数据初始化失败"
  exit 1
fi
