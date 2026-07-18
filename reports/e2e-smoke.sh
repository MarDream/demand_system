#!/usr/bin/env bash
# 全链路冒烟测试脚本 - 综合运营管理平台
# 后端: http://localhost:8081  基准路径: /api/v1
BASE="http://localhost:8081"
LOG="/tmp/e2e_result.log"
> "$LOG"

# 计时辅助：返回 "HTTP_CODE TIME_MS BODY_LEN"
probe() {
  local method="$1"; local path="$2"; local auth="$3"; local body="$4"
  local tmp; tmp="$(mktemp)"
  local start; start=$(date +%s%3N)
  if [ "$method" = "GET" ]; then
    code=$(curl -s -m 15 -o "$tmp" -w "%{http_code}" -H "Authorization: Bearer $auth" "$BASE$path")
  else
    code=$(curl -s -m 30 -o "$tmp" -w "%{http_code}" -X "$method" \
      -H "Authorization: Bearer $auth" -H "Content-Type: application/json" \
      -d "$body" "$BASE$path")
  fi
  local end; end=$(date +%s%3N)
  local len; len=$(wc -c < "$tmp")
  echo "$code $((end-start)) $len" | tee -a "$LOG"
  echo "    └─ body: $(head -c 400 "$tmp" | tr '\n' ' ')"
  rm -f "$tmp"
}

echo "=================================================="
echo " 综合运营管理平台 - 全链路冒烟测试"
echo " 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo " 后端: $BASE"
echo "=================================================="

# ---- 1. 认证 ----
echo ""; echo "【1. 认证链路】"
TOKEN=$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')
echo "  login        -> $(probe POST /api/v1/auth/login "" '{"username":"admin","password":"admin123"}')"
if [ -z "$TOKEN" ]; then echo "  ❌ 登录失败，无法继续!"; exit 1; fi
echo "  token.len    = ${#TOKEN} (截断: ${TOKEN:0:20}...)"
echo "  /auth/me     -> $(probe GET /api/v1/auth/me "$TOKEN")"
echo "  /auth/me(无token) -> $(probe GET /api/v1/auth/me "")"

# ---- 2. 知识库 + RAG 问答 ----
echo ""; echo "【2. 知识库 RAG 问答】"
echo "  /knowledge/bases        -> $(probe GET /api/v1/knowledge/bases "$TOKEN")"
echo "  /knowledge/bases/all    -> $(probe GET /api/v1/knowledge/bases/all "$TOKEN")"
echo "  /knowledge/config       -> $(probe GET /api/v1/knowledge/config "$TOKEN")"
# 知识库检索（同步）
echo "  /knowledge/search(同步)  -> $(probe POST /api/v1/knowledge/search "$TOKEN" '{"query":"测试用例","topK":3}')"
# 知识库检索（SSE 流式）
echo "  /knowledge/search/stream (SSE 5s采样):"
SSE=$(curl -s -m 6 -N -X POST "$BASE/api/v1/knowledge/search/stream" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"query":"如何创建需求","topK":3,"knowledgeBaseId":null}' 2>/dev/null | head -c 800)
echo "    └─ SSE样本: $SSE"

# ---- 3. AI 操作助手 ----
echo ""; echo "【3. AI 操作助手】"
echo "  /assistant/sessions(POST) -> $(probe POST /api/v1/assistant/sessions "$TOKEN" '{"title":"冒烟测试会话"}')"
SID=$(curl -s -m 10 -X POST "$BASE/api/v1/assistant/sessions" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"title":"冒烟测试"}' | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "  session.id = $SID"
if [ -n "$SID" ]; then
  echo "  /assistant/sessions/$SID/messages(GET) -> $(probe GET /api/v1/assistant/sessions/$SID/messages "$TOKEN")"
  echo "  /assistant/sessions/$SID/messages/stream (SSE 8s采样):"
  ASSE=$(curl -s -m 8 -N -X POST "$BASE/api/v1/assistant/sessions/$SID/messages/stream" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"content":"你好，介绍一下这个系统的需求管理功能","llmModelId":null,"knowledgeBaseId":null}' 2>/dev/null | head -c 1200)
  echo "    └─ SSE样本: $ASSE"
  echo "  /assistant/sessions(列表) -> $(probe GET /api/v1/assistant/sessions "$TOKEN")"
fi

# ---- 4. 需求管理 ----
echo ""; echo "【4. 需求管理】"
echo "  /requirements (列表) -> $(probe GET /api/v1/requirements "$TOKEN")"
echo "  /requirements (POST) -> $(probe POST /api/v1/requirements "$TOKEN" '{"title":"E2E冒烟测试需求","description":"自动创建","priority":"high"}')"

# ---- 5. 工作流 ----
echo ""; echo "【5. 工作流引擎】"
echo "  /workflows/versions/active -> $(probe GET /api/v1/workflows/versions/active "$TOKEN")"
echo "  /workflow-approvals/pending -> $(probe GET /api/v1/workflow-approvals/pending "$TOKEN")"
echo "  /projects (列表) -> $(probe GET /api/v1/projects "$TOKEN")"
PID=$(curl -s -m 10 "$BASE/api/v1/projects" -H "Authorization: Bearer $TOKEN" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "  project.id = $PID"
if [ -n "$PID" ]; then
  echo "  /projects/$PID/workflow/states -> $(probe GET /api/v1/projects/$PID/workflow/states "$TOKEN")"
  echo "  /projects/$PID/iterations -> $(probe GET /api/v1/projects/$PID/iterations "$TOKEN")"
fi

# ---- 6. 多维表格 Bitable (已知未建表) ----
echo ""; echo "【6. 多维表格 Bitable】"
echo "  /bitable/bases -> $(probe GET /api/v1/bitable/bases "$TOKEN")"
echo "  /bitable/templates -> $(probe GET /api/v1/bitable/templates "$TOKEN")"

# ---- 7. 权限菜单 / 通知 / LLM ----
echo ""; echo "【7. 权限·通知·模型】"
echo "  /meta/version -> $(probe GET /api/v1/meta/version "$TOKEN")"
echo "  /notifications -> $(probe GET /api/v1/notifications "$TOKEN")"
echo "  /notifications/unread -> $(probe GET /api/v1/notifications/unread "$TOKEN")"
echo "  /llm-providers/chat-models -> $(probe GET /api/v1/llm-providers/chat-models "$TOKEN")"

# ---- 8. 越权/安全探测 ----
echo ""; echo "【8. 安全与越权探测】"
echo "  CORS 预检(Origin任意) ->"
curl -s -m 8 -o /dev/null -w "    Access-Control-Allow-Origin: %{header_json}\n" -X OPTIONS \
  "$BASE/api/v1/requirements" -H "Origin: http://evil.com" -H "Access-Control-Request-Method: GET" 2>/dev/null | head -c 300
echo "  /requirements DELETE(无body,探测权限校验) -> $(probe DELETE /api/v1/requirements/999999 "$TOKEN")"

echo ""; echo "=================================================="
echo " 测试完成。详细日志: $LOG"
echo "=================================================="
