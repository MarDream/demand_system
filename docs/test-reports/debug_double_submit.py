#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Debug double submit issue"""
import json, sys, time, urllib.request

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


_, j = http("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
token = j["data"]["accessToken"]

suffix = int(time.time())
body = {"projectId": 1, "title": f"DUP-{suffix}", "type": "Requirement", "priority": "High"}
_, j = http("POST", "/api/v1/requirements/drafts", token=token, body=body)
rid = j["data"]
print(f"created id={rid}")
_, j1 = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 0})
print(f"first: code={j1.get('code')} msg={j1.get('message')}")
_, j2 = http("POST", f"/api/v1/requirements/{rid}/submit", token=token, body={"version": 1})
print(f"second: code={j2.get('code')} msg={j2.get('message')[:200]}")
