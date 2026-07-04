-- ============================================================
-- SAG 知识事件/实体/图结构表
-- 日期: 2026-07-04
-- 说明:
--   从知识库 chunk 提取结构化事件与命名实体，构建二分图关联。
--   支持 SAG (Self-Attention Graph) 融合与语义检索。
--
--   前置依赖:
--     - knowledge_bases
--     - knowledge_documents
--     - knowledge_chunks
-- ============================================================

-- -----------------------------------------------------------
-- 1. 知识事件表 knowledge_events
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_events`;
CREATE TABLE `knowledge_events` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '关联知识库ID',
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '关联文档ID',
  `chunk_id` BIGINT UNSIGNED NOT NULL COMMENT '来源chunk ID',
  `title` VARCHAR(512) NOT NULL COMMENT '事件标题',
  `summary` VARCHAR(2048) DEFAULT '' COMMENT '事件摘要',
  `content` TEXT COMMENT '事件内容(title+摘要+原文)',
  `category` VARCHAR(128) DEFAULT NULL COMMENT '事件分类(需求/缺陷/变更/决策/其他)',
  `keywords` JSON DEFAULT NULL COMMENT '关键词数组',
  `priority` ENUM('high','medium','low') DEFAULT 'medium' COMMENT '优先级',
  `status` ENUM('open','closed','resolved') DEFAULT 'open' COMMENT '状态',
  `title_embedding` JSON DEFAULT NULL COMMENT '标题向量embedding(float数组)',
  `content_embedding` JSON DEFAULT NULL COMMENT '内容向量embedding(float数组)',
  `chunk_rank` INT DEFAULT 0 COMMENT 'chunk中的顺序',
  `deleted_at` TIMESTAMP NULL DEFAULT NULL COMMENT '软删除',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_kb` (`knowledge_base_id`),
  INDEX `idx_doc` (`document_id`),
  INDEX `idx_chunk` (`chunk_id`),
  INDEX `idx_deleted_at` (`deleted_at`),
  INDEX `idx_category` (`category`),
  INDEX `idx_priority` (`priority`),
  INDEX `idx_status` (`status`),
  FULLTEXT INDEX `idx_search` (`title`, `summary`, `content`) WITH PARSER ngram COMMENT '中文全文检索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识事件表-从chunk提取的事件';

-- -----------------------------------------------------------
-- 2. 知识实体表 knowledge_entities
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_entities`;
CREATE TABLE `knowledge_entities` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '关联知识库ID',
  `type` VARCHAR(64) NOT NULL COMMENT '实体类型(person/org/product/metric/system/action/work/group/subject/tags)',
  `name` VARCHAR(512) NOT NULL COMMENT '实体名称',
  `normalized_name` VARCHAR(512) NOT NULL COMMENT '归一化名称(小写去标点)',
  `description` VARCHAR(2048) DEFAULT '' COMMENT '实体描述',
  `embedding` JSON DEFAULT NULL COMMENT '实体名称向量embedding',
  `event_count` INT DEFAULT 0 COMMENT '关联事件数',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_type_name` (`knowledge_base_id`, `type`, `normalized_name`) COMMENT '知识库内类型+归一化名称唯一',
  INDEX `idx_normalized` (`normalized_name`) COMMENT '归一化名称索引',
  INDEX `idx_kb` (`knowledge_base_id`),
  INDEX `idx_type` (`type`),
  FULLTEXT INDEX `idx_search` (`name`, `normalized_name`, `description`) WITH PARSER ngram COMMENT '实体全文检索'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识实体表-从事件提取的命名实体';

-- -----------------------------------------------------------
-- 3. 事件实体关联表 knowledge_event_entities
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `knowledge_event_entities`;
CREATE TABLE `knowledge_event_entities` (
  `event_id` BIGINT UNSIGNED NOT NULL COMMENT '事件ID',
  `entity_id` BIGINT UNSIGNED NOT NULL COMMENT '实体ID',
  `embedding` JSON DEFAULT NULL COMMENT '关系向量(可选)',
  PRIMARY KEY (`event_id`, `entity_id`),
  INDEX `idx_entity` (`entity_id`),
  INDEX `idx_event` (`event_id`),
  CONSTRAINT `fk_ke_event` FOREIGN KEY (`event_id`) REFERENCES `knowledge_events` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ke_entity` FOREIGN KEY (`entity_id`) REFERENCES `knowledge_entities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件实体关联表-二分图边';
