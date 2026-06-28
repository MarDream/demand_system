#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""工作流 E2E 调试 - 直接查 transitions 状态"""
import json
import os
import sys
import urllib.request

sys.stdout.reconfigure(encoding='utf-8')
os.environ['PYTHONIOENCODING'] = 'utf-8'

BASE = "http://localhost:8081"


def http(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8", errors="replace"))


# 登录
code, j = http("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
token = j["data"]["accessToken"]
print("OK login")

# 拿需求 4 的 actions
code, j = http("GET", "/api/v1/workflow-engine/actions/4", token=token)
print(f"actions: code={j.get('code')}, message={j.get('message')}")
data = j.get("data", {})
print(f"currentNode: {data.get('currentNodeStatusName')}")
print(f"transitions count: {len(data.get('transitions', []))}")
for t in data.get("transitions", []):
    print(f"  - to={t.get('toNodeName')} bindStatusName={t.get('bindStatusName')} projectRequired={t.get('projectRequired')}")
    print(f"    assigneeCandidates: {t.get('assigneeCandidates')}")
print(f"canTransition: {data.get('canTransition')}, canCancel: {data.get('canCancel')}")
print(f"evaluationRequired: {data.get('evaluationRequired')}")
print(f"currentHandlerName: {data.get('currentHandlerName')}")
print(f"lockVersion: {data.get('lockVersion')}")

# 拿历史
code, j = http("GET", "/api/v1/requirements/4/history", token=token)
hist = j.get("data") or []
print(f"\nhistory ({len(hist)}):")
for h in hist[:3]:
    print(f"  {h}")

# 拿 next-nodes
code, j = http("GET", "/api/v1/requirements/4/next-nodes", token=token)
print(f"\nnext-nodes: {j.get('data')}")
