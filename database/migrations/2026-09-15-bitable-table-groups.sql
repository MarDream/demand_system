-- ============================================================
-- 多维表格：数据表分组（目录树）
-- 日期：2026-09-15
--
-- 背景：编辑器左侧「数据表」侧边栏原为扁平列表，无法按业务分组管理。
-- 本迁移新增分组表，并给数据表挂上 group_id。
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/2026-09-15-bitable-table-groups.sql
--
-- 说明：
--   - 「全部实体」「未分组」是前端虚拟节点，不落库。
--   - 脚本可重复执行（幂等）。
-- ============================================================

-- 1. 数据表分组表
CREATE TABLE IF NOT EXISTS `bitable_table_groups` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '所属多维表格ID',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父分组ID，NULL=根层级',
  `name` VARCHAR(100) NOT NULL COMMENT '分组名称',
  `sort_order` INT DEFAULT 0 COMMENT '同级排序',
  `creator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_base_parent` (`base_id`, `parent_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-数据表分组';

-- 2. 数据表增加 group_id（可空 = 未分组）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'bitable_tables'
    AND COLUMN_NAME = 'group_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `bitable_tables` ADD COLUMN `group_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属分组ID，NULL=未分组'' AFTER `base_id`, ADD INDEX `idx_group_id` (`group_id`)',
  'SELECT ''bitable_tables.group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
