# -*- coding: utf-8 -*-
"""执行 bitable-base-groups 迁移脚本并验证结果"""
import sys
import pymysql
from pymysql.constants import CLIENT

SQL_FILE = r"E:\Project\Vue_demo\demand_system\database\migrations\2026-09-15-bitable-base-groups.sql"

with open(SQL_FILE, "r", encoding="utf-8") as f:
    script = f.read()

conn = pymysql.connect(
    host="localhost", port=3306, user="root", password="admin123",
    database="demand_system", charset="utf8mb4",
    client_flag=CLIENT.MULTI_STATEMENTS,
)
try:
    with conn.cursor() as cur:
        cur.execute(script)
        # 消费多语句返回的所有结果集，避免 SyntheticGitError
        while cur.nextset():
            pass
    conn.commit()

    with conn.cursor() as cur:
        cur.execute("SHOW TABLES LIKE 'bitable_base_groups'")
        print("table bitable_base_groups:", "OK" if cur.fetchone() else "MISSING")
        cur.execute(
            "SELECT COUNT(*) FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='bitable_bases' AND COLUMN_NAME='group_id'"
        )
        print("bitable_bases.group_id:", "OK" if cur.fetchone()[0] else "MISSING")
finally:
    conn.close()
print("MIGRATION DONE")
