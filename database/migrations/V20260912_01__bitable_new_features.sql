-- =====================================================
-- 多维表格新功能建设：公开表单 / 视图分享 / 仪表盘 / 开放API / 定时触发器
-- 日期: 2026-09-12
-- 幂等：CREATE TABLE IF NOT EXISTS；ALTER 前检查列是否存在，可重复执行。
-- =====================================================

SET @db = DATABASE();

-- 1. 自动化：增加 last_fired_at（定时触发器记录上次触发时间）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bitable_automations' AND COLUMN_NAME = 'last_fired_at'
);
SET @sql = IF(@col_exists = 0,
  "ALTER TABLE bitable_automations ADD COLUMN last_fired_at DATETIME DEFAULT NULL COMMENT '定时触发器上次触发时间' AFTER action_config",
  "SELECT 'last_fired_at already exists' AS note"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 公开表单发布
CREATE TABLE IF NOT EXISTS `bitable_form_publishes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `view_id` BIGINT UNSIGNED NOT NULL COMMENT '表单视图ID',
  `table_id` BIGINT UNSIGNED NOT NULL COMMENT '数据表ID',
  `token` VARCHAR(64) NOT NULL COMMENT '公开访问令牌',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `password_hash` VARCHAR(128) DEFAULT NULL COMMENT '访问密码哈希（可空=无密码）',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间（可空=永不过期）',
  `submit_count` INT NOT NULL DEFAULT 0 COMMENT '已提交次数',
  `submit_limit` INT DEFAULT NULL COMMENT '提交总数上限（可空=不限制）',
  `success_message` VARCHAR(500) DEFAULT NULL COMMENT '提交成功提示语',
  `redirect_url` VARCHAR(500) DEFAULT NULL COMMENT '提交后跳转URL',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '发布人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  UNIQUE KEY `uk_view_id` (`view_id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-公开表单发布';

-- 3. 视图分享（只读链接）
CREATE TABLE IF NOT EXISTS `bitable_view_shares` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `view_id` BIGINT UNSIGNED NOT NULL COMMENT '视图ID',
  `table_id` BIGINT UNSIGNED NOT NULL COMMENT '数据表ID',
  `token` VARCHAR(64) NOT NULL COMMENT '分享令牌',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间（可空=永不过期）',
  `allow_download` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许下载',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  UNIQUE KEY `uk_view_id` (`view_id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-视图分享';

-- 4. 仪表盘
CREATE TABLE IF NOT EXISTS `bitable_dashboards` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `name` VARCHAR(200) NOT NULL COMMENT '仪表盘名称',
  `layout_config` JSON DEFAULT NULL COMMENT '布局配置（预留）',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_base_id` (`base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-仪表盘';

-- 5. 仪表盘组件
CREATE TABLE IF NOT EXISTS `bitable_dashboard_widgets` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `dashboard_id` BIGINT UNSIGNED NOT NULL COMMENT '仪表盘ID',
  `type` VARCHAR(20) NOT NULL COMMENT '组件类型: kpi/bar/line/pie',
  `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '组件标题',
  `data_source_config` JSON NOT NULL COMMENT '数据源配置: {tableId, fieldId, aggregation, groupByFieldId, filterConfig}',
  `display_config` JSON DEFAULT NULL COMMENT '展示配置（颜色等，预留）',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_dashboard_id` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-仪表盘组件';

-- 6. 开放 API 凭证
CREATE TABLE IF NOT EXISTS `bitable_api_credentials` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '绑定的多维表格ID',
  `name` VARCHAR(200) NOT NULL COMMENT '凭证名称',
  `key_id` VARCHAR(64) NOT NULL COMMENT '公开的 Key ID（bk_ 前缀）',
  `key_hash` VARCHAR(128) NOT NULL COMMENT 'Secret 的 SHA-256 哈希（明文不落库）',
  `scopes` VARCHAR(500) NOT NULL DEFAULT 'records:read' COMMENT '授权范围，逗号分隔: records:read,fields:read',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间（可空=不过期）',
  `last_used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_id` (`key_id`),
  INDEX `idx_base_id` (`base_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多维表格-开放API凭证';
