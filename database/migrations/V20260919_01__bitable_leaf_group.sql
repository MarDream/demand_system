-- ============================================================
-- 多维表格：数据表 / 仪表盘独立分组归属（叶子级移动）
-- 日期：2026-09-19
--
-- 背景：外层统一树（BaseGroupTree）里「数据表 / 仪表盘」节点的分组位置
-- 此前完全由其所属多维表格（Base）的 group_id 派生，拖一张表到别的分组
-- 实际是把整个 Base 挪走，导致原分组中同 Base 的其它数据表一并被移走。
-- 为两张叶子表补 base_group_id：
--   - NULL  = 跟随所属 Base 的分组（历史数据默认，行为不变）
--   - 非空  = 该叶子独立挂在该分组下（可跨分组自由移动）
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/V20260919_01__bitable_leaf_group.sql
--
-- 脚本可重复执行（幂等）。
-- ============================================================

-- 1. bitable_tables 补 base_group_id
SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_tables' AND COLUMN_NAME = 'base_group_id'
) = 0,
  'ALTER TABLE `bitable_tables` ADD COLUMN `base_group_id` BIGINT NULL DEFAULT NULL COMMENT ''独立分组归属（null=跟随所属Base分组）'' AFTER `group_id`',
  'SELECT ''bitable_tables.base_group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. bitable_dashboards 补 base_group_id
SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_dashboards' AND COLUMN_NAME = 'base_group_id'
) = 0,
  'ALTER TABLE `bitable_dashboards` ADD COLUMN `base_group_id` BIGINT NULL DEFAULT NULL COMMENT ''独立分组归属（null=跟随所属Base分组）'' AFTER `sort_order`',
  'SELECT ''bitable_dashboards.base_group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 叶子按分组过滤的索引（树渲染用 baseGroupIdOf 等价查询）
SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_tables' AND INDEX_NAME = 'idx_base_group_id'
) = 0,
  'ALTER TABLE `bitable_tables` ADD INDEX `idx_base_group_id` (`base_group_id`)',
  'SELECT ''bitable_tables.idx_base_group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_dashboards' AND INDEX_NAME = 'idx_base_group_id'
) = 0,
  'ALTER TABLE `bitable_dashboards` ADD INDEX `idx_base_group_id` (`base_group_id`)',
  'SELECT ''bitable_dashboards.idx_base_group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
