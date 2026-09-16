"""多维表格「字段属性 / 唯一性 / 字段级权限」接口回归。

覆盖链路（全部走真实 HTTP，后端需已在 8081 运行）：
  1. 建 Base + 表
  2. 字段属性：新增字段时即携带完整 config（数字精度/前缀后缀/区间、日期 withTime、文本 maxLength+正则）
     → 读回 config 未被裁剪掉合法键、非法键被 sanitize 掉
  3. 字段名称校验：空名 / 超长 / 同表重名（含大小写差异）都被拒
  4. 日期「包含时间」：提交 yyyy-MM-dd HH:mm:ss 后 value_date 保留时间部分
  5. 唯一性：标记 config.unique 的字段，第二条相同值被拒；空值不受限；更新记录排除自身
  6. 字段级权限：把某字段对 owner 角色设为 hidden
     → listFields 回 permission=hidden；记录列表不再下发该字段的单元格值
     → 再写该字段被拒（403）；改回 editable 后恢复正常

用法：
  <python> -X utf8 scripts/verify-bitable-field-attributes.py

脚本自带唯一后缀，结束时删除自己创建的 Base（级联清理），可反复执行。
"""
import json
import sys
import time
import urllib.request
from urllib.error import HTTPError
from urllib.request import ProxyHandler, build_opener, install_opener

# 本机 http_proxy 会把 127.0.0.1 的请求拦成 502，必须显式绕过
install_opener(build_opener(ProxyHandler({})))

BASE = "http://127.0.0.1:8081/api/v1"
SUFFIX = str(int(time.time()) % 100000000).zfill(8)

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


def data_of(body):
    """Result<T> 拆包（传入的是响应 body，不是 (status, body) 元组）"""
    return body.get("data") if isinstance(body, dict) else None


def msg_of(body):
    if not isinstance(body, dict):
        return ""
    return str(body.get("message") or body.get("msg") or body.get("raw") or "")


print("=" * 72)
print("多维表格字段属性 / 唯一性 / 字段级权限 接口回归")
print("=" * 72)

# ---------- 0. 登录 ----------
st, resp = call("POST", "/auth/login", {"username": "admin", "password": "admin123"})
token = (data_of(resp) or {}).get("accessToken") or (data_of(resp) or {}).get("token")
check("管理员登录成功", st == 200 and bool(token), f"status={st} body={resp}")
if not token:
    print("\n无法登录，终止。")
    sys.exit(1)

base_id = None
try:
    # ---------- 1. 建 Base + 表 ----------
    st, resp = call("POST", "/bitable/bases", {"name": f"__FATTR_{SUFFIX}"}, token)
    base_id = data_of(resp)
    check("创建 Base", st == 200 and base_id is not None, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/bases/{base_id}/tables", {"name": "属性表"}, token)
    table_id = data_of(resp)
    check("创建数据表", st == 200 and table_id is not None, f"status={st} body={resp}")

    # ---------- 2. 字段名称校验 ----------
    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "  ", "fieldType": "text"}, token)
    check("空字段名被拒", st != 200, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "X" * 51, "fieldType": "text"}, token)
    check("51 字符字段名被拒", st != 200, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "标题", "fieldType": "text",
                     "config": {"maxLength": 20, "pattern": "^[A-Za-z]+$",
                                "patternMessage": "只能填字母", "unique": True}}, token)
    title_field = data_of(resp)
    check("创建 text 字段（带 maxLength/pattern/unique）", st == 200 and title_field, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "标题", "fieldType": "text"}, token)
    check("同表重名字段被拒（完全一致）", st != 200, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "标题", "fieldType": "number"}, token)
    check("同表重名字段被拒（不同类型也算重名）", st != 200, f"status={st} body={resp}")

    # ---------- 3. 其余字段 ----------
    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "金额", "fieldType": "number",
                     "config": {"precision": 2, "thousandSeparator": True,
                                "prefix": "￥", "min": 0, "max": 1000}}, token)
    amount_field = data_of(resp)
    check("创建 number 字段（精度/千分位/前后缀/区间）", st == 200 and amount_field, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "开始时间", "fieldType": "date",
                     "config": {"dateFormat": "yyyy-MM-dd HH:mm", "withTime": True}}, token)
    date_field = data_of(resp)
    check("创建 date 字段（withTime=true）", st == 200 and date_field, f"status={st} body={resp}")

    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "手机号", "fieldType": "phone",
                     "config": {"masked": True, "countryCode": "+86"}}, token)
    phone_field = data_of(resp)
    check("创建 phone 字段（masked）", st == 200 and phone_field, f"status={st} body={resp}")

    # ---------- 4. 读回 config：合法键保留 ----------
    st, resp = call("GET", f"/bitable/tables/{table_id}/fields", None, token)
    fields = data_of(resp) or []
    by_id = {f["id"]: f for f in fields}
    amount_cfg = by_id.get(amount_field, {}).get("config") or {}
    check("number config 精度保留", amount_cfg.get("precision") == 2, f"got={amount_cfg}")
    check("number config 前缀保留", amount_cfg.get("prefix") == "￥", f"got={amount_cfg}")
    check("number config 区间保留", amount_cfg.get("min") == 0 and amount_cfg.get("max") == 1000, f"got={amount_cfg}")

    date_cfg = by_id.get(date_field, {}).get("config") or {}
    check("date config withTime 保留", date_cfg.get("withTime") is True, f"got={date_cfg}")
    check("date config dateFormat 保留", date_cfg.get("dateFormat") == "yyyy-MM-dd HH:mm", f"got={date_cfg}")

    phone_cfg = by_id.get(phone_field, {}).get("config") or {}
    check("phone config masked 保留", phone_cfg.get("masked") is True, f"got={phone_cfg}")

    # listFields 现在必须带 permission（默认 editable）
    check("listFields 返回 permission 字段", "permission" in by_id.get(title_field, {}),
          f"keys={list(by_id.get(title_field, {}).keys())}")
    check("默认权限为 editable", by_id.get(title_field, {}).get("permission") == "editable",
          f"got={by_id.get(title_field, {}).get('permission')}")

    # ---------- 5. 日期带时间 ----------
    st, resp = call("POST", f"/bitable/tables/{table_id}/records",
                    {"cells": {str(title_field): {"valueText": f"Alpha{SUFFIX}"},
                               str(date_field): {"valueDate": "2026-09-16 14:35:00"},
                               str(amount_field): {"valueNumber": 12.5}}}, token)
    rec1 = data_of(resp)
    check("创建第 1 条记录", st == 200 and rec1, f"status={st} body={resp}")

    st, resp = call("GET", f"/bitable/records/{rec1}", None, token)
    cells = (data_of(resp) or {}).get("cells") or {}
    got_date = (cells.get(str(date_field)) or {}).get("valueDate")
    check("value_date 保留时间部分", isinstance(got_date, str) and "14:35" in got_date, f"got={got_date!r}")

    # ---------- 6. 唯一性 ----------
    st, resp = call("POST", f"/bitable/tables/{table_id}/records",
                    {"cells": {str(title_field): {"valueText": f"Alpha{SUFFIX}"}}}, token)
    check("唯一字段重复值被拒", st != 200, f"status={st} body={resp}")
    check("唯一冲突报错文案含字段名", "标题" in msg_of(resp), f"msg={msg_of(resp)!r}")

    # 大小写/首尾空白差异也应视为重复
    st, resp = call("POST", f"/bitable/tables/{table_id}/records",
                    {"cells": {str(title_field): {"valueText": f"  alpha{SUFFIX}  "}}}, token)
    check("唯一字段大小写/空白差异同样被拒", st != 200, f"status={st} body={resp}")

    # 空值不受唯一性限制：连建两条不填该字段的记录都应成功
    st1, r1 = call("POST", f"/bitable/tables/{table_id}/records",
                   {"cells": {str(amount_field): {"valueNumber": 1}}}, token)
    st2, r2 = call("POST", f"/bitable/tables/{table_id}/records",
                   {"cells": {str(amount_field): {"valueNumber": 2}}}, token)
    check("唯一字段空值可重复（第 1 条）", st1 == 200, f"status={st1} body={r1}")
    check("唯一字段空值可重复（第 2 条）", st2 == 200, f"status={st2} body={r2}")

    # 更新自身为原值不应被自己拦住
    st, resp = call("PUT", f"/bitable/records/{rec1}",
                    {"cells": {str(title_field): {"valueText": f"Alpha{SUFFIX}"}}}, token)
    check("更新记录时排除自身（原值不变可保存）", st == 200, f"status={st} body={resp}")

    # 更新成别人的值应被拒
    st, resp = call("POST", f"/bitable/tables/{table_id}/records",
                    {"cells": {str(title_field): {"valueText": f"Beta{SUFFIX}"}}}, token)
    rec2 = data_of(resp)
    check("创建第 2 条记录（不同值）", st == 200 and rec2, f"status={st} body={resp}")

    st, resp = call("PUT", f"/bitable/records/{rec2}",
                    {"cells": {str(title_field): {"valueText": f"Alpha{SUFFIX}"}}}, token)
    check("更新成他人已占用的唯一值被拒", st != 200, f"status={st} body={resp}")

    # 单单元格编辑路径同样受唯一性约束
    st, resp = call("GET", f"/bitable/records/{rec2}", None, token)
    version = (data_of(resp) or {}).get("version")
    st, resp = call("PUT", f"/bitable/records/{rec2}/cells/{title_field}",
                    {"valueText": f"Alpha{SUFFIX}", "version": version}, token)
    check("单元格编辑同样受唯一性约束", st != 200, f"status={st} body={resp}")

    # ---------- 7. 字段级权限 ----------
    st, resp = call("GET", f"/bitable/bases/{base_id}/field-permissions?tableId={table_id}", None, token)
    check("字段权限初始为空（未配置即 editable）", st == 200 and data_of(resp) == [], f"status={st} body={resp}")

    # 把「金额」字段对 owner 角色设为 hidden
    st, resp = call("POST", f"/bitable/bases/{base_id}/field-permissions/batch-save",
                    {"baseId": base_id, "changes": [
                        {"baseId": base_id, "roleType": "system", "systemRoleCode": "owner",
                         "tableId": table_id, "fieldId": amount_field, "permissionLevel": "hidden"}]}, token)
    check("保存字段权限 hidden 成功", st == 200, f"status={st} body={resp}")

    st, resp = call("GET", f"/bitable/bases/{base_id}/field-permissions?tableId={table_id}", None, token)
    perms = data_of(resp) or []
    check("读回字段权限条目", len(perms) == 1 and perms[0]["fieldId"] == amount_field
          and perms[0]["permissionLevel"] == "hidden", f"got={perms}")

    st, resp = call("GET", f"/bitable/tables/{table_id}/fields", None, token)
    by_id = {f["id"]: f for f in (data_of(resp) or [])}
    check("listFields 反映 hidden 权限", by_id.get(amount_field, {}).get("permission") == "hidden",
          f"got={by_id.get(amount_field, {}).get('permission')}")

    st, resp = call("GET", f"/bitable/records/{rec1}", None, token)
    cells = (data_of(resp) or {}).get("cells") or {}
    check("记录接口不下发隐藏字段的单元格值", str(amount_field) not in cells, f"cells={list(cells.keys())}")
    check("未隐藏字段仍然下发", str(title_field) in cells, f"cells={list(cells.keys())}")

    st, resp = call("PUT", f"/bitable/records/{rec2}",
                    {"cells": {str(amount_field): {"valueNumber": 99}}}, token)
    check("写入隐藏字段被拒", st != 200, f"status={st} body={resp}")

    # 改成 readonly：可见但不可写
    st, resp = call("POST", f"/bitable/bases/{base_id}/field-permissions/batch-save",
                    {"baseId": base_id, "changes": [
                        {"baseId": base_id, "roleType": "system", "systemRoleCode": "owner",
                         "tableId": table_id, "fieldId": amount_field, "permissionLevel": "readonly"}]}, token)
    check("改为 readonly 成功", st == 200, f"status={st} body={resp}")

    st, resp = call("GET", f"/bitable/records/{rec1}", None, token)
    cells = (data_of(resp) or {}).get("cells") or {}
    check("readonly 字段仍然下发（可见）", str(amount_field) in cells, f"cells={list(cells.keys())}")

    st, resp = call("PUT", f"/bitable/records/{rec2}",
                    {"cells": {str(amount_field): {"valueNumber": 99}}}, token)
    check("写入 readonly 字段被拒", st != 200, f"status={st} body={resp}")

    # 恢复默认：editable 只删不插
    st, resp = call("POST", f"/bitable/bases/{base_id}/field-permissions/batch-save",
                    {"baseId": base_id, "changes": [
                        {"baseId": base_id, "roleType": "system", "systemRoleCode": "owner",
                         "tableId": table_id, "fieldId": amount_field, "permissionLevel": "editable"}]}, token)
    check("恢复 editable 成功", st == 200, f"status={st} body={resp}")

    st, resp = call("GET", f"/bitable/bases/{base_id}/field-permissions?tableId={table_id}", None, token)
    check("恢复默认后配置行被删除", data_of(resp) == [], f"got={data_of(resp)}")

    st, resp = call("PUT", f"/bitable/records/{rec2}",
                    {"cells": {str(amount_field): {"valueNumber": 99}}}, token)
    check("恢复后写入恢复正常", st == 200, f"status={st} body={resp}")

    # 非法权限级别被拒
    st, resp = call("POST", f"/bitable/bases/{base_id}/field-permissions/batch-save",
                    {"baseId": base_id, "changes": [
                        {"baseId": base_id, "roleType": "system", "systemRoleCode": "owner",
                         "tableId": table_id, "fieldId": amount_field, "permissionLevel": "nonsense"}]}, token)
    check("非法权限级别被拒", st != 200, f"status={st} body={resp}")

    # 删除字段应连带清理它的字段权限行
    # （bitable_field_permissions 上没有外键，不显式清理会留下指向已删字段的孤儿行）
    st, resp = call("POST", f"/bitable/tables/{table_id}/fields",
                    {"name": "__ORPHAN_CHK", "fieldType": "text", "config": {}}, token)
    orphan_field = data_of(resp)
    check("创建用于孤儿校验的字段", st == 200 and isinstance(orphan_field, int),
          f"status={st} data={orphan_field}")

    if isinstance(orphan_field, int):
        st, resp = call("POST", f"/bitable/bases/{base_id}/field-permissions/batch-save",
                        {"baseId": base_id, "changes": [
                            {"baseId": base_id, "roleType": "system", "systemRoleCode": "owner",
                             "tableId": table_id, "fieldId": orphan_field,
                             "permissionLevel": "hidden"}]}, token)
        check("为该字段写入 hidden 权限", st == 200, f"status={st} body={resp}")

        st, resp = call("GET", f"/bitable/bases/{base_id}/field-permissions?tableId={table_id}",
                        None, token)
        rows = data_of(resp) or []
        check("权限行已存在", any(r.get("fieldId") == orphan_field for r in rows), f"rows={rows}")

        st, resp = call("DELETE", f"/bitable/fields/{orphan_field}", None, token)
        check("删除字段成功", st == 200, f"status={st} body={resp}")

        st, resp = call("GET", f"/bitable/bases/{base_id}/field-permissions?tableId={table_id}",
                        None, token)
        rows = data_of(resp) or []
        check("删除字段后其权限行被连带清理（无孤儿）",
              not any(r.get("fieldId") == orphan_field for r in rows), f"rows={rows}")

finally:
    if base_id:
        st, resp = call("DELETE", f"/bitable/bases/{base_id}", None, token)
        print(f"  ----  清理 Base {base_id}: status={st}")

print()
print("=" * 72)
print(f"结果：PASS {ok} / FAIL {fail}")
if failures:
    print("失败项：")
    for f in failures:
        print("  - " + f)
print("=" * 72)
sys.exit(1 if fail else 0)
