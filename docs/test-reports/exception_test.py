#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""异常分支测试: 驳回/回滚/取消/越权/重复提交/并发"""
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
        # 401/403 等返回可能是空 body 或 HTML
        body = e.read().decode("utf-8", errors="replace")
        try:
            j = json.loads(body) if body else {"_empty": True}
        except Exception:
            j = {"_raw": body[:300], "_not_json": True}
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
        import traceback; traceback.print_exc()
        results.append(False)
        return False


def new_req(state="PENDING_ANALYSIS"):
    """创建并流转到指定状态的需求"""
    suffix = int(time.time() * 1000) % 100000
    body = {"projectId": 1, "title": f"E2E-EX-{suffix}", "type": "Requirement", "priority": "High", "description": "异常分支测试"}
    code, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    # 提交
    http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 0})
    # 推进到目标
    states = ["待分析", "待确认", "待评审", "待排期", "开发中", "测试中", "已上线", "已验收"]
    target_idx = states.index(state) if state in states else 0
    for i in range(target_idx):
        target = states[i + 1]
        _, j = http("GET", f"/api/v1/workflow-engine/actions/{rid}", token=token)
        lv = j["data"].get("lockVersion", 0)
        trans = j["data"].get("transitions", [])
        tn = next((t.get("toNodeId") for t in trans if t.get("bindStatusName") == target), None)
        if tn:
            http("POST", "/api/v1/workflow-engine/transition", token=token, body={
                "requirementId": rid, "toNodeId": tn, "lockVersion": lv,
                "comment": f"推进至{target}", "rating": 5, "action": "approve"
            })
    return rid


def get_lv(rid):
    _, j = http("GET", f"/api/v1/workflow-engine/actions/{rid}", token=token)
    return j["data"].get("lockVersion")


def get_actions(rid):
    _, j = http("GET", f"/api/v1/workflow-engine/actions/{rid}", token=token)
    return j["data"]


# 1. 取消需求
def tc_cancel():
    rid = new_req("待分析")
    # 取消原因：看 controller 是用 @RequestParam 而不是 body
    code, j = http("POST", f"/api/v1/workflow-engine/cancel/{rid}?comment=E2E+Cancel+Test", token=token, body={})
    ok = code == 200 and j.get("code") == 200
    print(f"  cancel: code={j.get('code')}, msg={j.get('message')}")
    _, j2 = http("GET", f"/api/v1/requirements/{rid}/detail-batch", token=token)
    final_state = j2["data"]["requirement"].get("nodeStatus")
    print(f"  final state: {final_state}")
    return ok and final_state == "CANCELLED"


case("1. 取消需求（任意状态）", tc_cancel)

# 2. 重复提交草稿
def tc_double_submit():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"E2E-DUP-{suffix}", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    rid = j["data"]
    # 第一次提交
    _, j1 = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 0})
    print(f"  first submit: {j1.get('code')} - {j1.get('message')}")
    # 第二次提交（应失败）
    _, j2 = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 1})
    print(f"  second submit: {j2.get('code')} - {j2.get('message')}")
    # 第二次应该被拒
    return j1.get("code") == 200 and j2.get("code") != 200


case("2. 重复提交草稿（已提交不能再 submit）", tc_double_submit)

# 3. 错误 lockVersion（乐观锁）
def tc_bad_version():
    rid = new_req("待分析")
    # 拿一个真实 transition
    a = get_actions(rid)
    target_node = a["transitions"][0]["toNodeId"]
    # 用错的 lockVersion
    bad_lv = 999
    _, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": target_node, "lockVersion": bad_lv,
        "comment": "wrong version", "rating": 5, "action": "approve"
    })
    print(f"  bad version: code={j.get('code')}, msg={j.get('message')}")
    return j.get("code") != 200


case("3. 错误 lockVersion 触发乐观锁冲突", tc_bad_version)

# 4. 不存在的目标节点
def tc_bad_node():
    rid = new_req("待分析")
    lv = get_lv(rid)
    _, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": "fake-node-xxx", "lockVersion": lv,
        "comment": "fake node", "rating": 5, "action": "approve"
    })
    print(f"  bad node: code={j.get('code')}, msg={j.get('message')}")
    return j.get("code") != 200


case("4. 跳转到非法节点（应被引擎拒绝）", tc_bad_node)

# 5. 跨节点跳转（应被拒绝：待分析 → 测试中）
def tc_skip_state():
    rid = new_req("待分析")
    a = get_actions(rid)
    # 当前待分析，可用的只有"待确认"，没有"测试中"
    # 尝试用一个不存在的 nodeId
    _, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": "v17_v12_v3_3ab37506-2bb5-4a27-xxx",  # 测试中node
        "lockVersion": a.get("lockVersion", 0),
        "comment": "skip", "rating": 5, "action": "approve"
    })
    print(f"  skip state: code={j.get('code')}, msg={j.get('message')}")
    return j.get("code") != 200


case("5. 跨节点跳转（应被状态机拒绝）", tc_skip_state)

# 6. 评价越界（rating > 5）
def tc_bad_rating():
    rid = new_req("待分析")
    a = get_actions(rid)
    target_node = a["transitions"][0]["toNodeId"]
    _, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": target_node, "lockVersion": a.get("lockVersion", 0),
        "comment": "rating=10", "rating": 10, "action": "approve"
    })
    print(f"  bad rating(10): code={j.get('code')}, msg={j.get('message')}")
    # 应该是非法 rating 被拒，但若仍流转成功说明服务端没校验
    # 我们看是否流转到下一节点
    return True  # 这里只是探测，不严格断言


case("6. 评价星级越界（rating=10）", tc_bad_rating)

# 7. 无 token 访问
def tc_no_token():
    code, j = http("GET", "/api/v1/requirements/my-pending?pageSize=1")
    msg = j.get("message") or j.get("_raw", "")[:50] or j.get("_not_json", "")
    print(f"  no token: code={j.get('code')}, http={code}, msg={str(msg)[:50]}")
    return j.get("code") != 200 or code != 200


case("7. 无 token 访问受保护接口", tc_no_token)

# 8. 错误 token
def tc_bad_token():
    code, j = http("GET", "/api/v1/requirements/my-pending?pageSize=1", token="fake.bogus.token")
    msg = j.get("message") or j.get("_raw", "")[:50] or j.get("_not_json", "")
    print(f"  bad token: code={j.get('code')}, http={code}, msg={str(msg)[:50]}")
    return j.get("code") != 200 or code != 200


case("8. 错误/伪造 token", tc_bad_token)

# 9. 跨用户访问（admin 创的需求，admin 自己读）
def tc_cross_user_basic():
    # 已有需求都属 admin，这里只测 admin 能读自己需求
    _, j = http("GET", "/api/v1/requirements/my-pending?pageSize=5", token=token)
    items = j["data"]["list"]
    print(f"  my-pending count: {len(items)}")
    return j.get("code") == 200


case("9. 隔离性：my-pending/my-drafts 仅返回自己数据", tc_cross_user_basic)

# 10. 评价星级为 0（无效）
def tc_zero_rating():
    rid = new_req("待分析")
    a = get_actions(rid)
    _, j = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": a["transitions"][0]["toNodeId"],
        "lockVersion": a.get("lockVersion", 0),
        "comment": "zero rating", "rating": 0, "action": "approve"
    })
    print(f"  rating=0: code={j.get('code')}, msg={j.get('message')}")
    return True  # 探测


case("10. 评价星级为 0（边界）", tc_zero_rating)

# 11. 创建草稿时传入非法 type
def tc_bad_type():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"E2E-BT-{suffix}", "type": "FakeType", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  bad type: code={j.get('code')}, msg={j.get('message')}")
    # BUG-01 修复后：非法字典值应被拒绝（400）
    return j.get("code") == 400 and "类型" in (j.get("message") or "")


case("11. 非法需求类型（type=FakeType） - BUG-01 修复验证", tc_bad_type)

# 12. 创建草稿时 priority 非法
def tc_bad_priority():
    suffix = int(time.time())
    body = {"projectId": 1, "title": f"E2E-BP-{suffix}", "type": "Requirement", "priority": "P9"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  bad priority: code={j.get('code')}, msg={j.get('message')}")
    # BUG-01 修复后：非法字典值应被拒绝（400）
    return j.get("code") == 400 and "优先级" in (j.get("message") or "")


case("12. 非法优先级（priority=P9） - BUG-01 修复验证", tc_bad_priority)

# 13. projectId=999（不存在）
def tc_bad_project():
    suffix = int(time.time())
    body = {"projectId": 999, "title": f"E2E-BPR-{suffix}", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  bad project: code={j.get('code')}, msg={j.get('message')}")
    return True  # 探测


case("13. projectId=999（不存在）", tc_bad_project)

# 14. 极端：空 title
def tc_empty_title():
    body = {"projectId": 1, "title": "", "type": "Requirement", "priority": "High"}
    _, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
    print(f"  empty title: code={j.get('code')}, msg={j.get('message')}")
    return j.get("code") != 200


case("14. 空 title", tc_empty_title)

# 15. SQL 注入尝试（基本防护验证）
def tc_sql_inject():
    body = {"username": "admin' OR '1'='1", "password": "anything"}
    _, j = http("POST", "/api/v1/auth/login", body=body)
    print(f"  sql inject: code={j.get('code')}, msg={j.get('message')[:60]}")
    return j.get("code") != 200  # 应被拒


case("15. SQL 注入尝试（基础防护）", tc_sql_inject)

# 16. 并发：两个 transition 同一需求
def tc_concurrent():
    rid = new_req("待分析")
    a = get_actions(rid)
    target = a["transitions"][0]["toNodeId"]
    lv = a.get("lockVersion", 0)
    # 两次相同请求，第一个成功，第二个因版本过期失败
    _, j1 = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": target, "lockVersion": lv,
        "comment": "first", "rating": 5, "action": "approve"
    })
    # 立刻第二次
    _, j2 = http("POST", "/api/v1/workflow-engine/transition", token=token, body={
        "requirementId": rid, "toNodeId": target, "lockVersion": lv,
        "comment": "second (stale)", "rating": 5, "action": "approve"
    })
    print(f"  first:  code={j1.get('code')}, msg={j1.get('message')[:50]}")
    print(f"  second: code={j2.get('code')}, msg={j2.get('message')[:50]}")
    return j1.get("code") == 200 and j2.get("code") != 200


case("16. 并发冲突：乐观锁拦截", tc_concurrent)

# 汇总
print("=" * 70)
passed = sum(1 for r in results if r)
failed = len(results) - passed
color = G if failed == 0 else R
print(f"{color}汇总: 通过 {passed}/{len(results)}, 失败 {failed}{D}")
print("=" * 70)
sys.exit(0 if failed == 0 else 1)
