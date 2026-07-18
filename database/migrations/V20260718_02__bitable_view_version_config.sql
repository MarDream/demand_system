-- =====================================================
-- P0 修复：补齐 bitable_views 缺失的 version / config 列
-- 日期: 2026-07-18
-- 背景: 全链路冒烟测试中 POST /bitable/bases/{id}/tables
--       返回 5000 bad SQL grammar on BitableViewMapper.insert。
--       根因：bitable_views 缺少 version、config 两列
--       （实体 BitableView 已含这两个字段，MyBatis-Plus insert
--        会写入，但运行库 schema 未同步）。
--       本迁移幂等：仅当列不存在时才 ALTER，且默认视图回填
--       用 NOT EXISTS / IS NULL 守卫，可重复执行。
-- =====================================================

SET @db = DATABASE();

-- 1. 补齐 version 列（乐观锁）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bitable_views' AND COLUMN_NAME = 'version'
);
SET @sql = IF(@col_exists = 0,
  "ALTER TABLE bitable_views ADD COLUMN version INT DEFAULT 0 COMMENT '乐观锁版本号' AFTER sort_order",
  "SELECT 'version already exists' AS note"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 补齐 config 列（统一视图配置）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bitable_views' AND COLUMN_NAME = 'config'
);
SET @sql = IF(@col_exists = 0,
  "ALTER TABLE bitable_views ADD COLUMN config JSON DEFAULT NULL COMMENT '统一视图配置(schemaVersion+data)' AFTER color_config",
  "SELECT 'config already exists' AS note"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 为尚无视图的数据表补齐默认 Grid 视图（幂等）
INSERT INTO bitable_views (table_id, name, view_type, sort_order, created_by, version)
SELECT t.id, '默认表格视图', 'grid', 0, COALESCE(t.base_id, 0), 0
FROM bitable_tables t
WHERE t.deleted_at = 0
  AND NOT EXISTS (
    SELECT 1 FROM bitable_views v
    WHERE v.table_id = t.id AND v.deleted_at = 0
  );

-- 4. 设置数据表的默认视图ID（幂等）
UPDATE bitable_tables t
INNER JOIN (
  SELECT table_id, MIN(id) AS first_view_id
  FROM bitable_views
  WHERE deleted_at = 0
  GROUP BY table_id
) v ON t.id = v.table_id
SET t.default_view_id = v.first_view_id
WHERE t.default_view_id IS NULL;
