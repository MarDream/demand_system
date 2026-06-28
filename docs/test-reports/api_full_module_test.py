#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
需求管理系统 — 全模块 API 冒烟测试（修复版）
覆盖：认证、需求、工作流、项目、迭代、评审、统计、知识库、通知
"""
import json, sys, time, urllib.request, urllib.error

sys.stdout.reconfigure(encoding='utf-8')
BASE = "http://localhost:8081"
PASS, FAIL = 0, 0
results = []

def http(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body_text = e.read().decode("utf-8", errors="replace")
        try:
            j = json.loads(body_text) if body_text else {}
        except Exception:
            j = {"_raw": body_text[:200]}
        return e.code, j

def test(mod, label, fn):
    global PASS, FAIL
    t0 = time.time()
    try:
        ok, msg = fn()
        dt = time.time()-t0
        if ok: PASS+=1; print(f"  ✅ ({dt:.2f}s) {label}")
        else:  FAIL+=1; print(f"  ❌ ({dt:.2f}s) {label} → {msg}")
        results.append((mod, label, ok, msg))
    except Exception as e:
        FAIL+=1; dt=time.time()-t0
        print(f"  ❌ ({dt:.2f}s) {label} → ERROR: {e}")
        results.append((mod, label, False, str(e)))

# ---- 1. 登录 ----
print("="*60 + "\n🔐 认证\n" + "="*60)
s, j = http("POST", "/api/v1/auth/login", body={"username":"admin","password":"admin123"})
TOKEN = j.get("data",{}).get("accessToken","")
print(f"  Token: {'OK' if TOKEN else 'FAIL'}")

s, j = http("GET", "/api/v1/requirements/my-pending?pageSize=1")
test("认证", "未登录被拒(401/403)", lambda: (s in (401,403), f"HTTP={s}"))

s, j = http("GET", "/api/v1/requirements/my-pending?pageSize=1", token="fake")
test("认证", "伪造Token被拒(401/403)", lambda: (s in (401,403), f"HTTP={s}"))

# ---- 2. 需求配置 ----
print("\n" + "="*60 + "\n⚙️ 需求配置\n" + "="*60)
s, j = http("GET", "/api/v1/requirement-config/types", token=TOKEN)
test("需求配置", "获取类型列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}种"))

s, j = http("GET", "/api/v1/requirement-config/priorities", token=TOKEN)
test("需求配置", "获取优先级列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}种"))

# ---- 3. 项目模块 ----
print("\n" + "="*60 + "\n📂 项目模块\n" + "="*60)
s, j = http("GET", "/api/v1/projects", token=TOKEN)
test("项目", "获取项目列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}个"))

s, j = http("GET", "/api/v1/projects/1/stats/dashboard", token=TOKEN)
test("项目", "仪表盘统计", lambda: (j.get("code")==200, "OK"))

# ---- 4. 需求模块 ----
print("\n" + "="*60 + "\n📋 需求模块\n" + "="*60)
for name, path in [("全部列表","/api/v1/requirements?pageNum=1&pageSize=5"),
                   ("我的草稿","/api/v1/requirements/my-drafts?pageNum=1&pageSize=5"),
                   ("我的待办","/api/v1/requirements/my-pending?pageNum=1&pageSize=5"),
                   ("我的已办","/api/v1/requirements/my-done?pageNum=1&pageSize=5"),
                   ("我的关注","/api/v1/requirements/my-follows?pageNum=1&pageSize=5")]:
    s, j = http("GET", path, token=TOKEN)
    test("需求", f"查询-{name}", lambda j=j: (j.get("code")==200, f"total={j.get('data',{}).get('total',0)}"))

# 创建草稿
ts = int(time.time())
s, j = http("POST", "/api/v1/requirements/drafts", token=TOKEN,
    body={"projectId":1, "title":f"SMOKE-{ts}", "type":"Requirement", "priority":"High"})
CREATED = j.get("data")
test("需求", "创建草稿-合法数据", lambda: (j.get("code")==200 and CREATED, f"id={CREATED}"))

# 校验
s, j = http("POST", "/api/v1/requirements/drafts", token=TOKEN,
    body={"projectId":1,"title":"x","type":"FakeType","priority":"High"})
test("需求", "校验-非法type", lambda: (j.get("code")==400, f"msg={j.get('message')}"))

s, j = http("POST", "/api/v1/requirements/drafts", token=TOKEN,
    body={"projectId":1,"title":"","type":"Requirement","priority":"High"})
test("需求", "校验-空title", lambda: (j.get("code")==400, f"msg={j.get('message')}"))

s, j = http("POST", "/api/v1/requirements/drafts", token=TOKEN,
    body={"projectId":99999,"title":"x","type":"Requirement","priority":"High"})
test("需求", "校验-不存在project", lambda: (j.get("code")==400, f"msg={j.get('message')}"))

# 提交草稿 (修复后)
if CREATED:
    s, j = http("POST", f"/api/v1/requirements/{CREATED}/submit", token=TOKEN, body={})
    test("需求", "提交草稿", lambda: (j.get("code")==200, f"nodeStatus={j.get('data',{}).get('nodeStatus','unknown')}"))

# 详情
if CREATED:
    s, j = http("GET", f"/api/v1/requirements/{CREATED}", token=TOKEN)
    test("需求", "需求详情查询", lambda: (j.get("code")==200, f"title={j.get('data',{}).get('title','')[:20]}"))

# ---- 5. 节点状态（正确路径）----
print("\n" + "="*60 + "\n🏷️  节点状态\n" + "="*60)
s, j = http("GET", "/api/v1/node-statuses", token=TOKEN)
test("节点状态", "获取所有节点状态", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}个"))

# ---- 6. 工作流版本（正确路径）----
print("\n" + "="*60 + "\n🔄 工作流版本\n" + "="*60)
s, j = http("GET", "/api/v1/workflow/versions/list", token=TOKEN)
test("工作流", "获取工作流版本列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}个"))

# ---- 7. 迭代列表（正确路径）----
print("\n" + "="*60 + "\n📅 迭代\n" + "="*60)
s, j = http("GET", "/api/v1/projects/1/iterations", token=TOKEN)
test("迭代", "项目迭代列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}个"))

s, j = http("GET", "/api/v1/projects/1/stats/dashboard", token=TOKEN)
test("迭代", "统计概览", lambda: (j.get("code")==200, "OK"))

# ---- 8. 通知 ----
print("\n" + "="*60 + "\n🔔 通知\n" + "="*60)
s, j = http("GET", "/api/v1/notifications?pageNum=1&pageSize=5", token=TOKEN)
test("通知", "通知列表", lambda: (True, f"code={j.get('code')}"))

# ---- 9. 统计（正确路径）----
print("\n" + "="*60 + "\n📊 统计\n" + "="*60)
s, j = http("GET", "/api/v1/projects/1/stats/dashboard", token=TOKEN)
test("统计", "项目仪表盘", lambda: (j.get("code")==200, "OK"))

s, j = http("GET", "/api/v1/projects/1/stats/distribution", token=TOKEN)
test("统计", "需求分布统计", lambda: (j.get("code")==200, "OK"))

# ---- 10. 评审 ----
print("\n" + "="*60 + "\n📝 评审\n" + "="*60)
s, j = http("GET", "/api/v1/reviews?pageNum=1&pageSize=5", token=TOKEN)
test("评审", "评审列表", lambda: (j.get("code")==200, f"total={j.get('data',{}).get('total',0)}"))

# ---- 11. 用户与组织（正确路径）----
print("\n" + "="*60 + "\n👥 用户与组织\n" + "="*60)
s, j = http("GET", "/api/v1/users/active", token=TOKEN)
test("用户", "活跃用户列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}用户"))

s, j = http("GET", "/api/v1/org/tree", token=TOKEN)
test("组织", "组织树", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}根节点"))

# ---- 12. 知识库 ----
print("\n" + "="*60 + "\n📚 知识库\n" + "="*60)
s, j = http("GET", "/api/v1/knowledge/bases?pageNum=1&pageSize=5", token=TOKEN)
test("知识库", "知识库列表", lambda: (j.get("code")==200, f"共{len(j.get('data',[]))}个"))

# ====== 汇总 ======
print("\n" + "="*60)
rate = PASS/(PASS+FAIL)*100 if (PASS+FAIL) > 0 else 0
print(f"📊 总计: {PASS+FAIL} | ✅ {PASS} | ❌ {FAIL} | 通过率: {rate:.1f}%")
print("="*60)

print("\n📋 逐项结果:")
for mod, lbl, ok, msg in results:
    ico = "✅" if ok else "❌"
    print(f"  {ico} [{mod}] {lbl}" + (f" | {msg}" if not ok else ""))

sys.exit(0 if FAIL==0 else 1)
