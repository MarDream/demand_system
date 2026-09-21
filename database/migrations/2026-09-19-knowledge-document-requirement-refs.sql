-- 知识文档-需求关联表
-- 该表此前只存在于 init.sql，历史库缺对应迁移，创建需求草稿同步附件到知识库时报
-- Table 'demand_system.knowledge_document_requirement_refs' doesn't exist
CREATE TABLE IF NOT EXISTS `knowledge_document_requirement_refs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '知识库文档ID',
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `requirement_code` VARCHAR(64) DEFAULT NULL COMMENT '需求编号',
  `requirement_title` VARCHAR(255) DEFAULT NULL COMMENT '需求标题',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_requirement` (`document_id`, `requirement_id`),
  KEY `idx_requirement_id` (`requirement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文档-需求关联';
