#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""完整状态机驱动"""
import json
import os
import sys
import urllib.request

sys.stdout.reconfigure(encoding='utf-8')
BASE = "http://localhost:8081"


def http(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8", errors="replace"))


# 登录
_, j = http("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
token = j["data"]["accessToken"]
print("[OK] admin login")

# 拿一个现有需求
_, j = http("GET", "/api/v1/requirements/my-drafts?pageSize=5", token=token)
print(f"[INFO] my-drafts count: {j['data']['total']}")

# 拿所有 my-pending
_, j = http("GET", "/api/v1/requirements/my-pending?pageSize=20", token=token)
print(f"[INFO] my-pending count: {j['data']['total']}")
for item in j["data"]["list"][:5]:
    print(f"  - id={item['id']} title={item.get('title','')} state={item.get('nodeStatus')}")

# 选一个 pending
if j["data"]["list"]:
    rid = j["data"]["list"][0]["id"]
else:
    # 创建并提交
    import time
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"E2E-FULL-{suffix}", "type": "Requirement", "priority": "High", "description": "full path"}
    code, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    print(f"[CREATE] new draft id={rid}")
    # 提交
    code, j = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 0})
    print(f"[SUBMIT] code={j.get('code')} nodeStatus={(j.get('data') or {}).get('nodeStatus')}")

print(f"\n[TEST] 对需求 {rid} 执行完整状态机推进")
print("=" * 70)

# 完整路径
TARGETS = ["待确认", "待评审", "待排期", "开发中", "测试中", "已上线", "已验收"]
for target in TARGETS:
    # 读 actions
    code, j = http("GET", f"/api/v1/workflow-engine/actions/{rid}", token=token)
    if code != 200 or j.get("code") != 200:
        print(f"  [STOP] {target}: actions read failed: {j.get('message')}")
        break
    data = j.get("data", {})
    cur = data.get("currentNodeStatusName")
    lv = data.get("lockVersion")
    eval_req = data.get("evaluationRequired")
    transitions = data.get("transitions", [])
    print(f"\n  Current: {cur}, lockVersion={lv}, evalRequired={eval_req}")
    print(f"  Available transitions: {[(t.get('bindStatusName'), t.get('toNodeId')[:30]) for t in transitions]}")

    # 找 target
    target_node = None
    for t in transitions:
        if t.get("bindStatusName") == target or t.get("toNodeName") == target:
            target_node = t.get("toNodeId")
            break

    if not target_node:
        print(f"  [INFO] 无直迁移，可能需先提交评估或审批")
        # 看是否能 cancel 关闭
        if data.get("canCancel"):
            print(f"  [INFO] 测试用 canCancel 走 cancel 路径")
            code, j2 = http("POST", f"/api/v1/workflow-engine/cancel/{rid}", token=token, body={"comment": "E2E测试用取消"})
            print(f"  [CANCEL] code={j2.get('code')}, msg={j2.get('message')}")
        break

    # 提交 transition
    body = {
        "requirementId": rid,
        "targetStateName": target,
        "toNodeId": target_node,
        "lockVersion": lv,
        "comment": f"E2E 推进至 {target}",
    }
    # 如果 evaluationRequired, 需要 1-5 星评价
    if eval_req:
        body["rating"] = 5
        body["action"] = "approve"
    code, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body=body)
    if code == 200 and j.get("code") == 200:
        # 重新读 actions 拿新状态
        _, j2 = http("GET", f"/api/v1/workflow-engine/actions/{rid}", token=token)
        new_state = j2.get("data", {}).get("currentNodeStatusName")
        new_lv = j2.get("data", {}).get("lockVersion")
        print(f"  [PASS] → {target}  (lockVersion→{new_lv}, state={new_state})")
    else:
        print(f"  [FAIL] → {target}: HTTP={code}, biz={j.get('code')}, msg={j.get('message')}")
        print(f"  body sent: {body}")
        break

# 最终状态
code, j = http("GET", f"/api/v1/requirements/{rid}/detail-batch", token=token)
req = j.get("data", {}).get("requirement", {})
hist = j.get("data", {}).get("history", [])
print(f"\n[FINAL] id={rid} state={req.get('nodeStatus')} version={req.get('version')}")
print(f"[HISTORY] {len(hist)} records")
for h in hist:
    print(f"  - {h.get('action')} | {h.get('fieldName')} | {h.get('oldValue')} → {h.get('newValue')} | operator={h.get('operatorName')}")
