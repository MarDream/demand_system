-- ============================================================
-- 文件内容哈希(2026-09-19)
--   file_records 新增 content_hash 列(SHA-256 hex,64位小写):
--   同一工单附件上传前做内容级去重校验,避免"文件名不同但内容相同"的重复文件浪费存储。
--   历史文件为 NULL,批量哈希查询接口惰性回填。
-- 执行：docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -padmin123 demand_system < <本文件>
-- ============================================================

ALTER TABLE `file_records`
  ADD COLUMN `content_hash` CHAR(64) DEFAULT NULL COMMENT '内容哈希(SHA-256 hex),NULL=未计算' AFTER `uploader_id`,
  ADD INDEX `idx_content_hash` (`content_hash`);
