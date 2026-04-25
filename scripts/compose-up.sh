#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose -f "$ROOT_DIR/scripts/docker-compose.yml" up -d

wait_health() {
  local name="$1"
  echo "[compose-up] 等待服务就绪: $name"
  for i in {1..60}; do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      echo "[compose-up] $name => $status"
      return 0
    fi
    sleep 2
  done
  echo "[compose-up] 等待超时: $name" >&2
  docker logs "$name" --tail 200 || true
  return 1
}

wait_health mysql
wait_health redis
wait_health rabbitmq
wait_health minio

echo "[compose-up] 基础设施已启动"

