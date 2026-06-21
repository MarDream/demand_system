-- =====================================================
-- 知识库默认设置功能 - 数据库迁移脚本
-- 功能：支持设置默认知识库用于存储需求文件
-- =====================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------
-- 1. 修改 knowledge_bases 表，增加默认标识字段
-- -----------------------------------------------------
ALTER TABLE `knowledge_bases`
ADD COLUMN `is_default_for_requirements` TINYINT DEFAULT 0 COMMENT '是否为需求文件默认存储库(0=否, 1=是)' AFTER `status`;

-- -----------------------------------------------------
-- 2. 创建文档需求关联表（多对多关系）
-- -----------------------------------------------------
CREATE TABLE `knowledge_document_requirement_refs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `requirement_code` VARCHAR(64) DEFAULT NULL COMMENT '需求编号(冗余)',
  `requirement_title` VARCHAR(500) DEFAULT NULL COMMENT '需求标题(冗余)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_doc_req` (`document_id`, `requirement_id`),
  INDEX `idx_document_id` (`document_id`),
  INDEX `idx_requirement_id` (`requirement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档需求引用关系表';

-- -----------------------------------------------------
-- 3. 为 knowledge_documents 表增加复合索引（文件判重）
-- -----------------------------------------------------
CREATE INDEX `idx_filename_size` ON `knowledge_documents` (`file_name`, `file_size`, `deleted_at`);

-- -----------------------------------------------------
-- 4. 数据回填：为已有文档建立需求引用关系
-- -----------------------------------------------------
INSERT INTO `knowledge_document_requirement_refs` (`document_id`, `requirement_id`, `requirement_code`, `requirement_title`)
SELECT
  kd.id AS document_id,
  kd.requirement_id,
  r.requirement_no AS requirement_code,
  r.title AS requirement_title
FROM `knowledge_documents` kd
INNER JOIN `requirements` r ON kd.requirement_id = r.id
WHERE kd.requirement_id IS NOT NULL
  AND kd.deleted_at = 0
  AND r.deleted_at = 0
  AND NOT EXISTS (
    SELECT 1 FROM `knowledge_document_requirement_refs` ref
    WHERE ref.document_id = kd.id AND ref.requirement_id = kd.requirement_id
  );

-- =====================================================
-- 注意事项：
-- 1. 全局唯一性约束通过应用层实现（事务+校验）
-- 2. MySQL 5.7 不支持部分索引，因此使用应用层保证唯一性
-- 3. 执行前请备份数据库
-- =====================================================
