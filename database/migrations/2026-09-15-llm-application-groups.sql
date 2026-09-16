-- ============================================================
-- 模型配置：功能点模型应用分组（目录树）
-- 日期：2026-09-15
--
-- 背景：「模型配置 → 模型应用」页原为平铺卡片列表，功能点一多就无法按业务归类。
-- 本迁移新增分组表，并给 llm_applications 挂上 group_id，
-- 前端改为「左侧目录树 + 右侧功能点配置」的形态，与管理多维表格的方式一致。
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/2026-09-15-llm-application-groups.sql
--
-- 说明：
--   - 「全部应用」「未分组」是前端虚拟节点，不落库。
--   - 删除分组时，其子分组与功能点会一并上移到父级，不会级联删除。
--   - 脚本可重复执行（幂等）。
-- ============================================================

-- 1. 功能点分组表（parent_id 自关联形成任意层级目录树）
CREATE TABLE IF NOT EXISTS `llm_application_groups` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父分组ID，NULL=根层级',
  `name` VARCHAR(100) NOT NULL COMMENT '分组名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同级排序',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_llm_app_group_parent` (`parent_id`),
  INDEX `idx_llm_app_group_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LLM 功能点模型应用分组';

-- 2. llm_applications 增加 group_id（可空 = 未分组）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'llm_applications'
    AND COLUMN_NAME = 'group_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `llm_applications` ADD COLUMN `group_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属分组ID，NULL=未分组'' AFTER `sort_order`, ADD INDEX `idx_llm_application_group_id` (`group_id`)',
  'SELECT ''llm_applications.group_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 初始化分组（仅在缺失时插入，避免覆盖用户后续的改名/调整）
INSERT INTO `llm_application_groups` (`parent_id`, `name`, `sort_order`)
SELECT NULL, '智能助手', 10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `llm_application_groups` WHERE `name` = '智能助手' AND `parent_id` IS NULL);

INSERT INTO `llm_application_groups` (`parent_id`, `name`, `sort_order`)
SELECT NULL, '知识库', 20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `llm_application_groups` WHERE `name` = '知识库' AND `parent_id` IS NULL);

INSERT INTO `llm_application_groups` (`parent_id`, `name`, `sort_order`)
SELECT NULL, '其他能力', 30 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `llm_application_groups` WHERE `name` = '其他能力' AND `parent_id` IS NULL);

-- 4. 把已存在的功能点归入默认分组（仅处理尚未归组的功能点）
UPDATE `llm_applications` a
JOIN `llm_application_groups` g ON g.`name` = '智能助手' AND g.`parent_id` IS NULL AND g.`deleted_at` = 0
SET a.`group_id` = g.`id`
WHERE a.`code` IN ('assistant.chat', 'assistant.nl2sql')
  AND a.`group_id` IS NULL;

UPDATE `llm_applications` a
JOIN `llm_application_groups` g ON g.`name` = '知识库' AND g.`parent_id` IS NULL AND g.`deleted_at` = 0
SET a.`group_id` = g.`id`
WHERE a.`code` IN (
    'knowledge.intent',
    'knowledge.answer',
    'knowledge.embedding',
    'knowledge.rerank',
    'knowledge.event-rerank',
    'knowledge.image-understanding'
  )
  AND a.`group_id` IS NULL;

UPDATE `llm_applications` a
JOIN `llm_application_groups` g ON g.`name` = '其他能力' AND g.`parent_id` IS NULL AND g.`deleted_at` = 0
SET a.`group_id` = g.`id`
WHERE a.`code` IN ('bitable.ai', 'llm.translation')
  AND a.`group_id` IS NULL;
