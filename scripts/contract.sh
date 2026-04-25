#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OUT_DIR="$ROOT_DIR/contracts/openapi"
mkdir -p "$OUT_DIR"

echo "[contract] 确保后端已启动（默认读取 http://localhost:8081/v3/api-docs）"
curl -fsS "http://localhost:8081/v3/api-docs" -o "$OUT_DIR/openapi.latest.json"
echo "[contract] 已生成: $OUT_DIR/openapi.latest.json"

