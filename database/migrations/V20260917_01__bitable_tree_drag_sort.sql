-- ============================================================
-- 多维表格：目录树拖拽排序支持
-- 日期：2026-09-17
--
-- 背景：多维表格列表页（Base 分组树）与编辑器（数据表分组树）需要支持
-- 「长按拖拽」调整同级顺序。四张表均已自带 sort_order 列：
--   - bitable_base_groups.sort_order   （同级排序，见 2026-09-15-bitable-base-groups.sql）
--   - bitable_bases.sort_order         （init.sql 建表自带）
--   - bitable_table_groups.sort_order  （init.sql 建表自带）
--   - bitable_tables.sort_order        （init.sql 建表自带）
-- 本脚本不新增列，仅做幂等确认：缺列则补建（正常已存在，会跳过），
-- 并为列表读取路径补充 (parent_id, sort_order) / (group_id, sort_order) 组合索引加速同级排序。
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/V20260917_01__bitable_tree_drag_sort.sql
--
-- 说明：
--   - 排序回写走后端批量接口（PUT /base-groups/sort、/table-groups/sort、/bases/sort、/tables/sort），
--     按传入顺序以列表下标回写 sort_order，无需数据订正。
--   - 脚本可重复执行（幂等）。
-- ============================================================

-- 1. 幂等确认四张表的 sort_order 列（均已存在则全部跳过）
SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_base_groups' AND COLUMN_NAME = 'sort_order'
) = 0,
  'ALTER TABLE `bitable_base_groups` ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT ''同级排序''',
  'SELECT ''bitable_base_groups.sort_order 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_bases' AND COLUMN_NAME = 'sort_order'
) = 0,
  'ALTER TABLE `bitable_bases` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT ''排序''',
  'SELECT ''bitable_bases.sort_order 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_table_groups' AND COLUMN_NAME = 'sort_order'
) = 0,
  'ALTER TABLE `bitable_table_groups` ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT ''同级排序''',
  'SELECT ''bitable_table_groups.sort_order 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_tables' AND COLUMN_NAME = 'sort_order'
) = 0,
  'ALTER TABLE `bitable_tables` ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT ''排序''',
  'SELECT ''bitable_tables.sort_order 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 同级排序读取索引（不存在则创建）
SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_base_groups' AND INDEX_NAME = 'idx_bitable_base_group_parent_sort'
) = 0,
  'ALTER TABLE `bitable_base_groups` ADD INDEX `idx_bitable_base_group_parent_sort` (`parent_id`, `sort_order`)',
  'SELECT ''idx_bitable_base_group_parent_sort 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_table_groups' AND INDEX_NAME = 'idx_bitable_table_group_parent_sort'
) = 0,
  'ALTER TABLE `bitable_table_groups` ADD INDEX `idx_bitable_table_group_parent_sort` (`base_id`, `parent_id`, `sort_order`)',
  'SELECT ''idx_bitable_table_group_parent_sort 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
