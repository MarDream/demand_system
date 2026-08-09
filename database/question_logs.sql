-- =====================================================
-- AI 助手 - 用户问答埋点表
-- 说明: 记录每次用户向 AI 助手的提问，用于冷启动期提炼高频问题
-- =====================================================

DROP TABLE IF EXISTS `question_logs`;
CREATE TABLE `question_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '提问用户ID',
  `org_id` INT UNSIGNED DEFAULT NULL COMMENT '所属组织ID',
  `session_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属会话ID(assistant_sessions.id)',
  `page_route` VARCHAR(100) DEFAULT NULL COMMENT '提问所在页面路由名(route.name)',
  `question_text` VARCHAR(1000) NOT NULL COMMENT '用户原始问题',
  `question_hash` VARCHAR(64) DEFAULT NULL COMMENT '问题文本的MD5哈希(用于去重聚合)',
  `response_rating` TINYINT DEFAULT NULL COMMENT '用户反馈: 0=差评, 1=中立, 2=好评, NULL=未评价',
  `report_count` INT DEFAULT 0 COMMENT '被标记为"无帮助"的次数',
  `token_cost` INT DEFAULT 0 COMMENT '本次消耗token数',
  `answered` TINYINT DEFAULT 1 COMMENT '是否成功回答: 0=失败/中断, 1=正常',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
  PRIMARY KEY (`id`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_page_route` (`page_route`),
  INDEX `idx_org_id` (`org_id`),
  INDEX `idx_question_hash` (`question_hash`),
  INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI助手问答记录埋点表';

-- 定期清理: 保留 90 天数据用于滚动统计，90 天前归档或删除
-- CREATE EVENT IF NOT EXISTS `evt_clean_old_question_logs`
-- ON SCHEDULE EVERY 1 DAY
-- DO DELETE FROM `question_logs` WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 90 DAY);
