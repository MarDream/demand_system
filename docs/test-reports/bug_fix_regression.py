#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""回归测试: 验证 BUG-01/02/03 修复"""
import json
import os
import sys
import time
import urllib.request

sys.stdout.reconfigure(encoding='utf-8')
BASE = "http://localhost:8081"
G, R, Y, B, D = "\033[92m", "\033[91m", "\033[93m", "\033[94m", "\033[0m"


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
        body = e.read().decode("utf-8", errors="replace")
        try:
            j = json.loads(body) if body else {"_empty": True}
        except Exception:
            j = {"_raw": body[:300]}
        return e.code, j


# 登录
_, j = http("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
token = j["data"]["accessToken"]
print(f"{B}admin login OK{D}\n")

results = []


def case(label, fn):
    print(f"{Y}>>> {label}{D}")
    t0 = time.time()
    try:
        ok = fn()
        dt = time.time() - t0
        print(f"  {'PASS' if ok else 'FAIL'} ({dt:.2f}s)\n")
        results.append(ok)
        return ok
    except Exception as e:
        print(f"  [ERROR] {e}\n")
        results.append(False)
        return False


# === BUG-01 回归: type/priority 字典校验 ===
def tc_bug01_bad_type():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"B1-{suffix}", "type": "FakeType", "priority": "High"}
    code, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  bad type: code={j.get('code')}, msg={j.get('message')}")
    # 修复后应返回 400
    return j.get("code") == 400 and "类型" in (j.get("message") or "")


case("BUG-01: 非法 type 字典值应被拒绝", tc_bug01_bad_type)


def tc_bug01_bad_priority():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"B1P-{suffix}", "type": "Requirement", "priority": "P9"}
    code, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  bad priority: code={j.get('code')}, msg={j.get('message')}")
    return j.get("code") == 400 and "优先级" in (j.get("message") or "")


case("BUG-01: 非法 priority 字典值应被拒绝", tc_bug01_bad_priority)


def tc_bug01_valid_type():
    # 合法 type 应该仍可通过
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"B1V-{suffix}", "type": "Requirement", "priority": "High"}
    code, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  valid: code={j.get('code')}, msg={j.get('message')}")
    return j.get("code") == 200


case("BUG-01: 合法 type/priority 不受影响", tc_bug01_valid_type)


# === BUG-02 回归: 草稿 submit 缺 version 自动补 0 ===
def tc_bug02_no_version():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"B2-{suffix}", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    # 不传 version，应该自动用 0 提交成功
    _, j2 = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={})
    print(f"  submit no version: code={j2.get('code')}, msg={j2.get('message')[:50]}")
    return j2.get("code") == 200 and j2.get("data", {}).get("nodeStatus") == "PENDING_ANALYSIS"


case("BUG-02: 草稿 submit 不传 version 自动补 0", tc_bug02_no_version)


def tc_bug02_explicit_version():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"B2E-{suffix}", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    # 显式传 version=0 也应成功
    _, j2 = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 0})
    print(f"  submit v=0: code={j2.get('code')}, msg={j2.get('message')[:50]}")
    return j2.get("code") == 200


case("BUG-02: 草稿 submit 显式传 version=0 仍正常", tc_bug02_explicit_version)


# === BUG-03 回归: 401/403 响应体非空 ===
def tc_bug03_no_token():
    code, j = http("GET", "/api/v1/requirements/my-pending?pageSize=1")
    msg = j.get("message") or j.get("_raw", "")
    print(f"  no token: HTTP={code}, code={j.get('code')}, msg={str(msg)[:60]}")
    # 修复后: 401/403 应该有 JSON body 含 message
    return (code in (401, 403)) and (j.get("code") or j.get("message"))


case("BUG-03: 无 token 应返回 401 + 统一 JSON", tc_bug03_no_token)


def tc_bug03_bad_token():
    code, j = http("GET", "/api/v1/requirements/my-pending?pageSize=1", token="fake.bogus.token")
    msg = j.get("message") or j.get("_raw", "")
    print(f"  bad token: HTTP={code}, code={j.get('code')}, msg={str(msg)[:60]}")
    return (code in (401, 403)) and (j.get("code") or j.get("message"))


case("BUG-03: 伪造 token 应返回 401/403 + 统一 JSON", tc_bug03_bad_token)


# === 回归: 已通过的测试不应被破坏 ===
def tc_regression_full_path():
    # 跑一遍完整状态机 8 步流转
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"REG-{suffix}", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    _, j = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={})
    if j.get("code") != 200:
        print(f"  submit failed: {j.get('message')}")
        return False
    targets = ["待确认", "待评审", "待排期", "开发中", "测试中", "已上线", "已验收"]
    for t in targets:
        _, j = http("GET", f"/api/v1/workflow-engine/actions/{rid}", token=token)
        lv = j["data"].get("lockVersion", 0)
        trans = j["data"].get("transitions", [])
        tn = next((tr.get("toNodeId") for tr in trans if tr.get("bindStatusName") == t), None)
        if not tn:
            print(f"  [FAIL] no transition for {t}")
            return False
        _, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
            "requirementId": rid, "toNodeId": tn, "lockVersion": lv,
            "comment": f"回归→{t}", "rating": 5, "action": "approve"
        })
        if j.get("code") != 200:
            print(f"  [FAIL] → {t}: {j.get('message')}")
            return False
        print(f"  [OK] → {t}")
    return True


case("回归: 完整 8 步状态机流转", tc_regression_full_path)


def tc_regression_cancel():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"REG-C-{suffix}", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={})
    code, j = http("POST", f"/api/v1/workflow-engine/cancel/{rid}?comment=regression", token=token, body={})
    print(f"  cancel: code={j.get('code')}, msg={j.get('message')}")
    _, j = http("GET", f"/api/v1/requirements/{rid}/detail-batch", token=token)
    final_state = j["data"]["requirement"].get("nodeStatus")
    print(f"  final state: {final_state}")
    return final_state == "CANCELLED"


case("回归: 取消功能", tc_regression_cancel)


# 汇总
print("=" * 70)
passed = sum(1 for r in results if r)
failed = len(results) - passed
color = G if failed == 0 else R
print(f"{color}BUG修复回归: 通过 {passed}/{len(results)}, 失败 {failed}{D}")
print("=" * 70)
sys.exit(0 if failed == 0 else 1)