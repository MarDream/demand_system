-- 迁移: 确保多维表格(bitable_*)与 AI 助手(assistant_*)表存在
-- 背景: 原 init.sql 已含这些建表语句，但未在本环境数据库执行，
--        导致 2026-07-15 测评时 `GET /api/v1/bitable/bases` 持续 500（表不存在），
--        且 assistant_sessions 在历史日志中报过同样的错。
-- 幂等: 全部使用 DROP TABLE IF EXISTS + CREATE TABLE，可重复执行。
-- 来源: 提取自 database/init.sql（bitable 段 29.4~29.12，assistant 段 48.1~48.2）

-- ============ 29.4 多维表格容器 ============
DROP TABLE IF EXISTS `bitable_bases`;
CREATE TABLE `bitable_bases` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '表格名称',
  `description` TEXT DEFAULT NULL COMMENT '描述',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标标识',
  `cover_color` VARCHAR(20) DEFAULT NULL COMMENT '封面颜色',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联项目ID(可选)',
  `creator_id` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `is_template` TINYINT DEFAULT 0 COMMENT '是否模板: 0=否, 1=是',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_creator_id` (`creator_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格容器';

-- ============ 29.5 多维表格-数据表 ============
DROP TABLE IF EXISTS `bitable_tables`;
CREATE TABLE `bitable_tables` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '所属多维表格ID',
  `name` VARCHAR(200) NOT NULL COMMENT '表名',
  `description` TEXT DEFAULT NULL COMMENT '表描述',
  `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_base_id` (`base_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-数据表';

-- ============ 29.6 多维表格-字段定义 ============
DROP TABLE IF EXISTS `bitable_fields`;
CREATE TABLE `bitable_fields` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `table_id` BIGINT UNSIGNED NOT NULL COMMENT '所属数据表ID',
  `name` VARCHAR(200) NOT NULL COMMENT '字段名',
  `field_type` VARCHAR(30) NOT NULL COMMENT '字段类型: text/number/date/single_select/multi_select/user/check/auto_number/created_time/modified_time/created_user/modified_user/url/email/progress/rating/link/formula/attachment',
  `config` JSON DEFAULT NULL COMMENT '字段配置(options/format/defaultValue/linkTargetTableId/formulaExpr等)',
  `required` TINYINT DEFAULT 0 COMMENT '是否必填: 0=否, 1=是',
  `ai_prompt` TEXT DEFAULT NULL COMMENT 'AI填充提示词(AI字段专用)',
  `is_ai_field` TINYINT DEFAULT 0 COMMENT '是否AI自动填充字段: 0=否, 1=是',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `width` INT DEFAULT 150 COMMENT '列宽(像素)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-字段定义';

-- ============ 29.7 多维表格-记录行 ============
DROP TABLE IF EXISTS `bitable_records`;
CREATE TABLE `bitable_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `table_id` BIGINT UNSIGNED NOT NULL COMMENT '所属数据表ID',
  `sort_order` INT DEFAULT 0 COMMENT '行排序',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人ID',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-记录行';

-- ============ 29.8 多维表格-单元格值(EAV模式) ============
DROP TABLE IF EXISTS `bitable_cell_values`;
CREATE TABLE `bitable_cell_values` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录行ID',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段ID',
  `value_text` TEXT DEFAULT NULL COMMENT '文本值',
  `value_number` DECIMAL(20,4) DEFAULT NULL COMMENT '数值',
  `value_date` DATE DEFAULT NULL COMMENT '日期值',
  `value_json` JSON DEFAULT NULL COMMENT '复杂值(多选数组/关联ID列表/附件列表等)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_record_field` (`record_id`, `field_id`),
  INDEX `idx_field_id` (`field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-单元格值';

-- ============ 29.9 多维表格-视图定义 ============
DROP TABLE IF EXISTS `bitable_views`;
CREATE TABLE `bitable_views` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `table_id` BIGINT UNSIGNED NOT NULL COMMENT '所属数据表ID',
  `name` VARCHAR(200) NOT NULL COMMENT '视图名称',
  `view_type` VARCHAR(20) NOT NULL DEFAULT 'grid' COMMENT '视图类型: grid/kanban/gantt/calendar/gallery',
  `sort_config` JSON DEFAULT NULL COMMENT '排序配置',
  `filter_config` JSON DEFAULT NULL COMMENT '筛选配置',
  `group_config` JSON DEFAULT NULL COMMENT '分组配置',
  `column_config` JSON DEFAULT NULL COMMENT '列宽/顺序/冻结/隐藏配置',
  `color_config` JSON DEFAULT NULL COMMENT '行颜色规则',
  `sort_order` INT DEFAULT 0 COMMENT '视图排序',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-视图定义';

-- ============ 29.10 多维表格-协作成员权限 ============
DROP TABLE IF EXISTS `bitable_base_members`;
CREATE TABLE `bitable_base_members` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role` VARCHAR(20) NOT NULL DEFAULT 'viewer' COMMENT '角色: owner/admin/editor/commenter/viewer',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_base_user` (`base_id`, `user_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-协作成员权限';

-- ============ 29.11 多维表格-行级评论 ============
DROP TABLE IF EXISTS `bitable_comments`;
CREATE TABLE `bitable_comments` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录行ID',
  `table_id` BIGINT UNSIGNED NOT NULL COMMENT '数据表ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '评论人ID',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `quote_field_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '引用字段ID(单元格评论)',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '回复评论ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_record_id` (`record_id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-行级评论';

-- ============ 29.12 多维表格-操作历史审计 ============
DROP TABLE IF EXISTS `bitable_operations`;
CREATE TABLE `bitable_operations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `base_id` BIGINT UNSIGNED NOT NULL COMMENT '多维表格ID',
  `table_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '数据表ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
  `operation_type` VARCHAR(30) NOT NULL COMMENT '操作类型: insert_record/update_cell/delete_record/add_field/update_field/delete_field/add_view/update_view/delete_view/add_table/delete_table/update_base',
  `detail` JSON DEFAULT NULL COMMENT '操作详情(变更前后值)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  INDEX `idx_base_id` (`base_id`),
  INDEX `idx_table_id` (`table_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_operation_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多维表格-操作历史审计';

-- ============ 48.1 AI助手会话表 ============
DROP TABLE IF EXISTS `assistant_sessions`;
CREATE TABLE `assistant_sessions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(120) NOT NULL COMMENT '会话标题',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_deleted_at` (`deleted_at`),
  INDEX `idx_user_updated_at` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI助手会话';

-- ============ 48.2 AI助手消息表 ============
DROP TABLE IF EXISTS `assistant_messages`;
CREATE TABLE `assistant_messages` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  `role` VARCHAR(20) NOT NULL COMMENT '消息角色:user/assistant',
  `content` LONGTEXT DEFAULT NULL COMMENT '消息正文',
  `status` VARCHAR(20) DEFAULT 'completed' COMMENT '消息状态:streaming/completed/failed',
  `intent` VARCHAR(100) DEFAULT NULL COMMENT '识别出的用户意图',
  `page_context` JSON DEFAULT NULL COMMENT '页面上下文',
  `actions` JSON DEFAULT NULL COMMENT '建议动作列表',
  `sources` JSON DEFAULT NULL COMMENT '建议来源列表',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_role` (`role`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI助手消息';
