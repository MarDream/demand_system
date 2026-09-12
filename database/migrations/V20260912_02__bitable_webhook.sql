-- =====================================================
-- 多维表格新功能建设：Webhook 订阅（出站推送）
-- 日期: 2026-09-12
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- =====================================================

-- Webhook 订阅：记录事件后异步 HMAC-SHA256 签名推送到目标 URL
CREATE TABLE IF NOT EXISTS `bitable_webhook_subscriptions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `table_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '限定数据表ID（可空=整表容器全部表）',
  `name` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '订阅名称',
  `event_types` VARCHAR(300) NOT NULL DEFAULT 'record_created,record_updated,record_deleted,form_submitted' COMMENT '订阅事件类型，逗号分隔',
  `url` VARCHAR(500) NOT NULL COMMENT '目标 URL（仅 http/https）',
  `secret` VARCHAR(128) NOT NULL COMMENT '签名密钥（服务端持有，用于 HMAC-SHA256）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `last_status` VARCHAR(20) DEFAULT NULL COMMENT '最近一次投递结果: succeeded/failed',
  `last_delivered_at` DATETIME DEFAULT NULL COMMENT '最近一次投递时间',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_base_id` (`base_id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-Webhook订阅';
