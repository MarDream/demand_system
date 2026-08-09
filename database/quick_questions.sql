-- =====================================================
-- AI 助手 - 快捷提问配置表
-- 说明: 存储人工维护的高频问题 + AI 自动提炼的问题
-- 补齐规则: 前台每页面最多 3 条；人工 ≥ 3 条时不展示 AI 的；
--           人工 < 3 条时由 AI 自动提炼补齐（取 ai_confidence 最高的）
-- =====================================================

DROP TABLE IF EXISTS `quick_questions`;
CREATE TABLE `quick_questions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category` ENUM('auto_extracted','manual_curated','ai_suggested') NOT NULL DEFAULT 'manual_curated' COMMENT '分类: auto_extracted=AI自动提炼, manual_curated=人工维护, ai_suggested=AI推荐待采纳',
  `page_route` VARCHAR(100) DEFAULT NULL COMMENT '归属页面路由名(route.name), NULL=全局',
  `question_text` VARCHAR(500) NOT NULL COMMENT '问题文本',
  `weight` INT DEFAULT 50 COMMENT '排序权重(1-100), 数值越大越靠前',
  `sort_order` INT DEFAULT 0 COMMENT '拖拽排序序号(同权重时优先)',
  `status` ENUM('enabled','disabled','pending_review') DEFAULT 'enabled' COMMENT '状态',
  `source` ENUM('user_behavior','admin_manual','ai_suggested','system_default') DEFAULT 'admin_manual' COMMENT '来源',
  `hit_count` INT DEFAULT 0 COMMENT '近 30 天前台点击次数',
  `ai_confidence` DECIMAL(3,2) DEFAULT NULL COMMENT 'AI置信度(0.00-1.00), 仅 source=ai_suggested/auto_extracted 时有值',
  `ai_cluster_id` VARCHAR(64) DEFAULT NULL COMMENT 'AI聚类分组ID(同组相似问题合并)',
  `created_by` INT UNSIGNED DEFAULT NULL COMMENT '创建人ID(人工创建时记录)',
  `reviewed_by` INT UNSIGNED DEFAULT NULL COMMENT '审核人ID(采纳 AI 建议时记录)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_category` (`category`),
  INDEX `idx_page_route` (`page_route`),
  INDEX `idx_status` (`status`),
  INDEX `idx_weight_sort` (`weight` DESC, `sort_order`),
  INDEX `idx_source` (`source`),
  INDEX `idx_ai_cluster` (`ai_cluster_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI助手快捷提问配置表';

-- 前台查询视图(按排序权重取启用的问题)
-- SELECT * FROM quick_questions 
-- WHERE status='enabled' AND (page_route=? OR page_route IS NULL)
-- ORDER BY field(category, 'manual_curated', 'auto_extracted'), weight DESC, sort_order ASC
-- LIMIT 3
