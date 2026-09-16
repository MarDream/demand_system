-- ============================================================
-- 多维表格：Base 分组（目录树）
-- 日期：2026-09-15
--
-- 背景：多维表格列表页原为平铺卡片，Base 一多就无法按业务归类。
-- 参考「数据表分组（bitable_table_groups）」「模型应用分组（llm_application_groups）」
-- 的方式，新增 Base 分组表，并给 bitable_bases 挂上 group_id，
-- 前端列表页改为「左侧目录树 + 右侧 Base 卡片」的形态。
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/2026-09-15-bitable-base-groups.sql
--
-- 说明：
--   - 「全部」「未分组」是前端虚拟节点，不落库。
--   - Base 分组为全局维度（不挂 base_id），与管理多维表格列表页的方式一致。
--   - 删除分组时，其子分组与 Base 会一并上移到父级，不会级联删除。
--   - 脚本可重复执行（幂等）。
-- ============================================================

-- 1. Base 分组表（parent_id 自关联形成任意层级目录树）
CREATE TABLE IF NOT EXISTS `bitable_base_groups` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父分组ID，NULL=根层级',
  `name` VARCHAR(100) NOT NULL COMMENT '分组名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同级排序',
  `creator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_bitable_base_group_parent` (`parent_id`),
  INDEX `idx_bitable_base_group_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-Base分组';

-- 2. bitable_bases 增加 group_id（可空 = 未分组）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'bitable_bases'
    AND COLUMN_NAME = 'group_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `bitable_bases` ADD COLUMN `group_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属分组ID，NULL=未分组'' AFTER `project_id`, ADD INDEX `idx_bitable_base_group_id` (`group_id`)',
  'SELECT ''bitable_bases.group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
