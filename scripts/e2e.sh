#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  set +e
  if [[ -n "${FRONTEND_PID}" ]]; then
    kill "${FRONTEND_PID}" 2>/dev/null || true
  fi
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" 2>/dev/null || true
  fi
  "$ROOT_DIR/scripts/compose-down.sh" || true
}
trap cleanup EXIT

"$ROOT_DIR/scripts/compose-up.sh"

echo "[e2e] 启动后端..."
(cd "$ROOT_DIR/demand_backend" && mvn -q spring-boot:run) &
BACKEND_PID=$!

echo "[e2e] 等待后端就绪..."
for i in {1..60}; do
  if curl -fsS "http://localhost:8081/v3/api-docs" >/dev/null 2>&1; then
    echo "[e2e] 后端已就绪"
    break
  fi
  sleep 2
done

echo "[e2e] 启动前端..."
(cd "$ROOT_DIR/demand_frontend" && npm run -s dev -- --port 5176 --host 0.0.0.0) &
FRONTEND_PID=$!

echo "[e2e] 等待前端就绪..."
for i in {1..60}; do
  if curl -fsS "http://localhost:5176/" >/dev/null 2>&1; then
    echo "[e2e] 前端已就绪"
    break
  fi
  sleep 2
done

echo "[e2e] 安装依赖（如已安装会很快）..."
(cd "$ROOT_DIR/demand_frontend" && npm install -s)

echo "[e2e] 执行 Playwright E2E..."
(cd "$ROOT_DIR/demand_frontend" && E2E_BASE_URL="http://localhost:5176" npm run -s test:e2e)

echo "[e2e] 完成"

