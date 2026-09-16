"""用户管理「邀请成员 / 添加·申请记录 / 批量管理」接口回归。

覆盖链路：
  生成邀请链接 → 匿名读邀请信息 → 匿名提交申请 → 申请记录出现
  → 审批通过自动建号 → 邀请状态回写 → 批量启停 → 批量删除
  以及批量邀请去重、撤回/重发、非法邀请码等边界。

用法（后端需已在 8081 运行）：
  <python> -X utf8 scripts/verify-user-invitation-api.py

脚本自带唯一后缀与收尾清理，可反复执行。
"""
import json
import subprocess
import sys
import time
import urllib.request
from urllib.error import HTTPError
from urllib.request import ProxyHandler, build_opener, install_opener

# 本机 http_proxy 会把 127.0.0.1 的请求拦成 502，必须显式绕过
install_opener(build_opener(ProxyHandler({})))

BASE = "http://127.0.0.1:8081/api/v1"
SUFFIX = str(int(time.time()) % 100000000).zfill(8)
PHONE_A = "138" + SUFFIX
PHONE_B = "139" + SUFFIX
EMAIL_A = f"smoke{SUFFIX}@example.com"
EMAIL_B = f"batch{SUFFIX}@example.com"
USERNAME_A = f"smoke{SUFFIX}"

ok = 0
fail = 0
failures = []


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode())
    except HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def check(label, cond, detail=""):
    global ok, fail
    if cond:
        ok += 1
        print(f"  PASS  {label}")
    else:
        fail += 1
        failures.append(label)
        print(f"  FAIL  {label}  {detail}")


def cleanup():
    """清掉本轮造出来的邀请与申请记录（无对应接口，直接走 SQL）。"""
    sql = (
        "DELETE FROM demand_system.sys_join_requests "
        f"WHERE applicant_phone IN ('{PHONE_A}','{PHONE_B}') "
        f"OR applicant_email IN ('{EMAIL_A}','{EMAIL_B}');"
        "DELETE FROM demand_system.sys_invitations "
        f"WHERE target IN ('{PHONE_A}','{PHONE_B}','{EMAIL_A}','{EMAIL_B}') "
        f"OR (invite_type='link' AND remark='接口回归');"
    )
    try:
        # 必须带 --default-character-set=utf8mb4：客户端默认 latin1，
        # 中文条件会被转码成乱码，DELETE 静默命中 0 行
        subprocess.run(
            ["docker", "exec", "-i", "mysql", "mysql", "-uroot", "-padmin123",
             "--default-character-set=utf8mb4"],
            input=sql.encode(), capture_output=True, check=False,
        )
    except Exception as e:  # noqa: BLE001
        print(f"  (清理跳过：{e})")


print(f"── 用户邀请/申请/批量管理 接口回归（后缀 {SUFFIX}）──")

# ── 认证与基础数据 ──
st, res = call("POST", "/auth/login", {"username": "admin", "password": "admin123"})
check("登录 admin", st == 200 and res.get("code") == 200, res)
token = res["data"]["accessToken"]

st, res = call("GET", "/org/tree", token=token)
nodes = res.get("data") or []
org_id = nodes[0]["id"] if nodes else None
check("获取组织树", st == 200 and org_id is not None, res)

# ── 链接邀请 ──
st, res = call("POST", "/invitations/link",
               {"orgId": org_id, "expireDays": 7, "maxUses": 0, "remark": "接口回归"}, token)
check("生成邀请链接", st == 200 and res.get("code") == 200, res)
link = res.get("data") or {}
code = link.get("inviteCode")
check("链接含邀请码", bool(code), link)
check("初始状态为待接受", link.get("statusText") == "待接受", link)
check("组织名已回填", bool(link.get("orgName")), link)

# ── 匿名侧 ──
st, res = call("GET", f"/public/invitations/{code}")
info = res.get("data") or {}
check("匿名读取邀请信息", st == 200 and info.get("valid") is True, res)
check("邀请人姓名可见", info.get("inviterName") == "系统管理员", info)

st, res = call("POST", f"/public/invitations/{code}/apply",
               {"name": "接口回归员", "phone": PHONE_A, "email": EMAIL_A, "remark": "自动化"})
check("提交加入申请", st == 200 and res.get("code") == 200, res)

st, res = call("POST", f"/public/invitations/{code}/apply",
               {"name": "接口回归员", "phone": PHONE_A, "email": EMAIL_A})
check("同号重复提交被拒", res.get("code") != 200, res)

# ── 申请记录 ──
st, res = call("GET", "/join-requests?status=pending&pageNum=1&pageSize=20", token=token)
rows = (res.get("data") or {}).get("list") or []
target = next((r for r in rows if r.get("applicantPhone") == PHONE_A), None)
check("申请记录出现", target is not None, [r.get("applicantPhone") for r in rows])
check("来源标记为邀请链接", bool(target) and target.get("sourceText") == "邀请链接", target)

# ── 审批通过 ──
st, res = call("PUT", f"/join-requests/{target['id']}/approve",
               {"orgId": org_id, "reviewRemark": "回归通过"}, token)
check("审批通过", st == 200 and res.get("code") == 200, res)

st, res = call("PUT", f"/join-requests/{target['id']}/approve", {"orgId": org_id}, token)
check("重复审批被拒", res.get("code") != 200, res)

st, res = call("GET", "/users?pageNum=1&pageSize=100", token=token)
users = (res.get("data") or {}).get("list") or []
created = next((u for u in users if u.get("phone") == PHONE_A), None)
check("账号已自动创建", created is not None, [u.get("phone") for u in users])
# 账号名只允许 [a-zA-Z0-9_]，邮箱前缀里的非安全字符会被剔除
check("账号名由邮箱前缀生成", bool(created) and created.get("username") == USERNAME_A, created)
check("已归入所选组织", bool(created) and created.get("orgId") == org_id, created)
check("工号已自动分配", bool(created) and bool(created.get("jobNumber")), created)

# ── 单行状态切换（曾因前端写 'disabled' 触发 ENUM 截断错误）──
st, res = call("PUT", f"/users/{created['id']}", {"status": "inactive"}, token)
check("单行停用不再报错", res.get("code") == 200, res)
st, res = call("GET", f"/users/{created['id']}", token=token)
check("单行停用已落库", (res.get("data") or {}).get("status") == "inactive", res)
st, res = call("PUT", f"/users/{created['id']}", {"status": "active"}, token)
check("单行启用已落库", res.get("code") == 200, res)

st, res = call("GET", f"/invitations?keyword={code}&pageNum=1&pageSize=10", token=token)
inv = ((res.get("data") or {}).get("list") or [{}])[0]
check("邀请状态回写为已接受", inv.get("statusText") == "已接受", inv)

# ── 批量管理 ──
st, res = call("POST", "/users/batch/status", {"ids": [created["id"]], "status": "inactive"}, token)
check("批量停用", res.get("code") == 200 and res.get("data") == 1, res)
st, res = call("POST", "/users/batch/status", {"ids": [created["id"]], "status": "active"}, token)
check("批量启用", res.get("code") == 200 and res.get("data") == 1, res)
st, res = call("POST", "/users/batch/status", {"ids": [], "status": "active"}, token)
check("空选被拦截", res.get("code") != 200 and "勾选" in (res.get("message") or ""), res)
st, res = call("POST", "/users/batch/status", {"ids": [created["id"]], "status": "weird"}, token)
check("非法状态被拦截", res.get("code") != 200, res)
st, res = call("POST", "/users/batch/status", {"ids": [1], "status": "inactive"}, token)
check("主管理员不可停用", res.get("code") != 200, res)

# ── 批量邀请 ──
st, res = call("POST", "/invitations/batch",
               {"orgId": org_id, "expireDays": 7,
                "members": [{"name": "批量甲", "phone": PHONE_B},
                            {"name": "批量乙", "email": EMAIL_B},
                            {"name": "重复行", "phone": PHONE_B},
                            {"name": "缺号", "email": "not-an-email"}]}, token)
data = res.get("data") or {}
check("批量邀请成功 2 条", data.get("successCount") == 2, data)
reasons = [s.get("reason") for s in (data.get("skipped") or [])]
check("同批次重复行被跳过", any("重复" in r for r in reasons), reasons)
check("非法邮箱被跳过", any("邮箱" in r for r in reasons), reasons)

st, res = call("GET", "/invitations?inviteType=batch&pageNum=1&pageSize=10", token=token)
batch_inv = ((res.get("data") or {}).get("list") or [{}])[0]
check("批量邀请类型正确", batch_inv.get("inviteTypeText") == "批量邀请", batch_inv)

# 一人一码：用过即失效
st, res = call("POST", f"/public/invitations/{batch_inv['inviteCode']}/apply",
               {"name": "批量甲", "phone": PHONE_B, "email": EMAIL_B})
check("批量码可提交", st == 200 and res.get("code") == 200, res)
st, res = call("GET", f"/public/invitations/{batch_inv['inviteCode']}")
check("批量码用过即失效", (res.get("data") or {}).get("valid") is False, res)

# ── 撤回 / 重发 ──
st, res = call("PUT", f"/invitations/{batch_inv['id']}/revoke", token=token)
check("撤回邀请", res.get("code") == 200 and (res.get("data") or {}).get("statusText") == "已撤回", res)
st, res = call("PUT", f"/invitations/{batch_inv['id']}/revoke", token=token)
check("重复撤回被拒", res.get("code") != 200, res)
st, res = call("PUT", f"/invitations/{batch_inv['id']}/resend", token=token)
new_code = (res.get("data") or {}).get("inviteCode")
check("重发换新码", res.get("code") == 200 and new_code and new_code != batch_inv.get("inviteCode"), res)
st, res = call("GET", f"/public/invitations/{batch_inv['inviteCode']}")
check("旧码已失效", res.get("code") != 200, res)

# ── 拒绝 ──
st, res = call("GET", "/join-requests?status=pending&pageNum=1&pageSize=20", token=token)
pending = ((res.get("data") or {}).get("list") or [])
reject_target = next((r for r in pending if r.get("applicantPhone") == PHONE_B), None)
if reject_target:
    st, res = call("PUT", f"/join-requests/{reject_target['id']}/reject",
                   {"reviewRemark": "回归拒绝"}, token)
    check("审批拒绝", st == 200 and res.get("code") == 200, res)
else:
    check("找到待拒绝的申请", False, [r.get("applicantPhone") for r in pending])

# ── 非法邀请码 ──
st, res = call("GET", "/public/invitations/NOTEXIST00")
check("非法码被拒", res.get("code") != 200, res)

# ── 批量删除与收尾 ──
st, res = call("POST", "/users/batch/delete", {"ids": [created["id"]]}, token)
check("批量删除", res.get("code") == 200 and res.get("data") == 1, res)
st, res = call("GET", "/users?pageNum=1&pageSize=100", token=token)
users = (res.get("data") or {}).get("list") or []
check("用户已删除", not any(u.get("phone") == PHONE_A for u in users))
st, res = call("POST", "/users/batch/delete", {"ids": [1]}, token)
check("主管理员不可删除", res.get("code") != 200, res)

cleanup()

print(f"\n==== 通过 {ok} / 失败 {fail} ====")
if failures:
    print("失败项：" + "、".join(failures))
sys.exit(1 if fail else 0)
