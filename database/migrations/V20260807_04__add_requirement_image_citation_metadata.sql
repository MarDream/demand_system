-- 工单正文图片引用定位元数据
ALTER TABLE `knowledge_chunks`
  ADD COLUMN `source_content_type` VARCHAR(32) DEFAULT NULL COMMENT '来源内容类型(body/image_ocr/image_caption)' AFTER `char_count`,
  ADD COLUMN `source_ref_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源引用ID，例如正文图片文件ID' AFTER `source_content_type`,
  ADD COLUMN `source_position` INT UNSIGNED DEFAULT NULL COMMENT '来源位置，例如正文图片序号' AFTER `source_ref_id`;

CREATE INDEX `idx_chunk_source_ref` ON `knowledge_chunks` (`source_content_type`, `source_ref_id`, `source_position`);
