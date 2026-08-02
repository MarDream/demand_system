#!/usr/bin/env bash
# P1 修复验证：业务异常现在应返回真实 HTTP 状态码（不再统一 200）
set -u
BASE="http://localhost:8081"
TOKEN=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')
echo "TOKEN.len=${#TOKEN}"

probe() {
  local label="$1"; local method="$2"; local url="$3"; local data="${4:-}"
  local body http
  if [ -n "$data" ]; then
    body=$(curl -s -m 12 -X "$method" "$url" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$data" -w $'\n__HTTP__%{http_code}' 2>/dev/null)
  else
    body=$(curl -s -m 12 -X "$method" "$url" -H "Authorization: Bearer $TOKEN" -w $'\n__HTTP__%{http_code}' 2>/dev/null)
  fi
  http=$(printf '%s' "$body" | sed -n 's/.*__HTTP__//p')
  local clean=$(printf '%s' "$body" | sed 's/__HTTP__.*//')
  printf '%-42s HTTP %s | body=%s\n' "$label" "$http" "${clean:0:160}"
}

echo "================ P1 STATUS CODE VERIFY ================"
probe "GET  /requirements/999999 (not found)"   GET  "$BASE/api/v1/requirements/999999"
probe "DELETE /requirements/999999 (not found)" DELETE "$BASE/api/v1/requirements/999999"
probe "POST /requirements/drafts missing title"  POST "$BASE/api/v1/requirements/drafts" '{"priority":"high"}'
probe "POST /requirements/drafts bad priority"  POST "$BASE/api/v1/requirements/drafts" '{"title":"t","priority":"zzz"}'
probe "GET  /requirements (list, should 200)"    GET  "$BASE/api/v1/requirements?pageNum=1&pageSize=5"
probe "GET  /knowledge/bases (should 200)"      GET  "$BASE/api/v1/knowledge/bases"

# 401: 错误密码
echo "--- login wrong password (expect 401) ---"
curl -s -m 10 -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}' -w $'\n__HTTP__%{http_code}' 2>/dev/null | sed 's/__HTTP__/ | HTTP /'
echo "====================================================="
