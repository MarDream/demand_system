-- =====================================================
-- P0-1 视图系统闭环 Migration
-- 日期: 2026-07-16
-- 说明: 为 bitable_tables 增加 default_view_id，
--       为 bitable_views 增加 version(乐观锁)和 config(统一配置)，
--       为已有表自动创建默认 Grid 视图并设置 default_view_id
-- =====================================================

-- 1. bitable_tables 增加默认视图ID
ALTER TABLE `bitable_tables`
  ADD COLUMN `default_view_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '默认视图ID' AFTER `sort_order`;

-- 2. bitable_views 增加乐观锁版本号
ALTER TABLE `bitable_views`
  ADD COLUMN `version` INT DEFAULT 0 COMMENT '乐观锁版本号' AFTER `sort_order`;

-- 3. bitable_views 增加统一视图配置列
ALTER TABLE `bitable_views`
  ADD COLUMN `config` JSON DEFAULT NULL COMMENT '统一视图配置(schemaVersion+data)' AFTER `color_config`;

-- 4. 为已有表自动创建默认 Grid 视图（如果该表还没有视图）
INSERT INTO bitable_views (table_id, name, view_type, sort_order, created_by, version)
SELECT t.id, '默认表格视图', 'grid', 0, COALESCE(t.base_id, 0), 0
FROM bitable_tables t
WHERE t.deleted_at = 0
  AND NOT EXISTS (
    SELECT 1 FROM bitable_views v
    WHERE v.table_id = t.id AND v.deleted_at = 0
  );

-- 5. 设置已有表的默认视图ID
UPDATE bitable_tables t
INNER JOIN (
  SELECT table_id, MIN(id) AS first_view_id
  FROM bitable_views
  WHERE deleted_at = 0
  GROUP BY table_id
) v ON t.id = v.table_id
SET t.default_view_id = v.first_view_id
WHERE t.default_view_id IS NULL;
