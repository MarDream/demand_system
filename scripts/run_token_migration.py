# -*- coding: utf-8 -*-
"""Execute database migration scripts against the dev MySQL."""
import sys
import pymysql

MIGRATIONS = [
    r"E:\Project\Vue_demo\demand_system\database\migrations\V20260803_01__add_token_usage_to_assistant_messages.sql",
    r"E:\Project\Vue_demo\demand_system\database\migrations\V20260803_02__add_reasoning_to_assistant_messages.sql",
]

def main():
    conn = pymysql.connect(
        host="localhost",
        port=33060,
        user="root",
        password="admin123",
        database="demand_system",
        charset="utf8mb4",
        autocommit=True,
    )
    cur = conn.cursor()
    # 检查列是否已存在
    cur.execute(
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='assistant_messages'"
    )
    existing = {row[0] for row in cur.fetchall()}
    print("assistant_messages 现有列:", sorted(existing))

    target = ["input_tokens", "output_tokens", "total_tokens", "reasoning"]
    missing = [col for col in target if col not in existing]
    if not missing:
        print("所有目标列已存在，跳过迁移")
    else:
        for path in MIGRATIONS:
            with open(path, "r", encoding="utf-8") as f:
                sql = f.read()
            statements = [s.strip() for s in sql.split(";") if s.strip()]
            for stmt in statements:
                try:
                    cur.execute(stmt)
                    print("已执行:", stmt.split("\n")[0][:80])
                except pymysql.err.OperationalError as e:
                    if "Duplicate column" in str(e):
                        print("列已存在，跳过:", e)
                    else:
                        raise
        cur.execute(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA='demand_system' AND TABLE_NAME='assistant_messages'"
            " AND COLUMN_NAME IN ('input_tokens','output_tokens','total_tokens','reasoning')"
        )
        print("迁移后新增列:", [row[0] for row in cur.fetchall()])

    cur.execute("SHOW COLUMNS FROM assistant_messages WHERE Field IN ('input_tokens','output_tokens','total_tokens','reasoning')")
    rows = cur.fetchall()
    print("\ntoken/reasoning 相关列确认:")
    for r in rows:
        print("  ", r[0], r[1])
    cur.close()
    conn.close()
    print("\n迁移完成 ✅")

if __name__ == "__main__":
    main()
