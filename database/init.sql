-- =====================================================
-- 需求管理系统 - 数据库初始化脚本
-- 字符集: utf8mb4, 引擎: InnoDB
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------
-- 1. 区域表 regions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `regions`;
CREATE TABLE `regions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '区域名称',
  `parent_id` INT UNSIGNED DEFAULT NULL COMMENT '父区域ID',
  `code` VARCHAR(50) DEFAULT NULL COMMENT '区域编码',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `description` TEXT COMMENT '区域描述',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域表';

-- -----------------------------------------------------
-- 2. 部门表 departments
-- -----------------------------------------------------
DROP TABLE IF EXISTS `departments`;
CREATE TABLE `departments` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '部门名称',
  `parent_id` INT UNSIGNED DEFAULT NULL COMMENT '父部门ID',
  `region_id` INT UNSIGNED DEFAULT NULL COMMENT '所属区域ID',
  `leader_id` INT UNSIGNED DEFAULT NULL COMMENT '部门负责人ID',
  `code` VARCHAR(50) DEFAULT NULL COMMENT '部门编码',
  `type` VARCHAR(50) DEFAULT NULL COMMENT '部门类型',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `description` TEXT COMMENT '部门描述',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_region_id` (`region_id`),
  INDEX `idx_leader_id` (`leader_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- -----------------------------------------------------
-- 3. 职位表 positions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `positions`;
CREATE TABLE `positions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '职位名称',
  `code` VARCHAR(50) DEFAULT NULL COMMENT '职位编码',
  `level` INT DEFAULT NULL COMMENT '职级',
  `description` TEXT COMMENT '职位描述',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位表';

-- -----------------------------------------------------
-- 4. 用户表 users
-- -----------------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
  `real_name` VARCHAR(100) NOT NULL COMMENT '真实姓名',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `region_id` INT UNSIGNED DEFAULT NULL COMMENT '所属区域ID',
  `department_id` INT UNSIGNED DEFAULT NULL COMMENT '所属部门ID',
  `position_id` INT UNSIGNED DEFAULT NULL COMMENT '岗位ID',
  `status` ENUM('active', 'inactive') DEFAULT 'active' COMMENT '状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`),
  INDEX `idx_region_id` (`region_id`),
  INDEX `idx_department_id` (`department_id`),
  INDEX `idx_position_id` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------
-- 5. 用户组织关系表 user_organizations
-- -----------------------------------------------------
DROP TABLE IF EXISTS `user_organizations`;
CREATE TABLE `user_organizations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `region_id` INT UNSIGNED DEFAULT NULL COMMENT '区域ID',
  `department_id` INT UNSIGNED DEFAULT NULL COMMENT '部门ID',
  `position_id` INT UNSIGNED DEFAULT NULL COMMENT '职位ID',
  `system_role` VARCHAR(50) NOT NULL COMMENT '系统角色(admin/manager/user)',
  `manager_id` INT UNSIGNED DEFAULT NULL COMMENT '上级ID',
  `effective_date` DATE NOT NULL COMMENT '生效日期',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_region_id` (`region_id`),
  INDEX `idx_department_id` (`department_id`),
  INDEX `idx_position_id` (`position_id`),
  INDEX `idx_manager_id` (`manager_id`),
  INDEX `idx_system_role` (`system_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户组织关系表';

-- -----------------------------------------------------
-- 6. 项目表 projects
-- -----------------------------------------------------
DROP TABLE IF EXISTS `projects`;
CREATE TABLE `projects` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '项目名称',
  `description` TEXT COMMENT '项目描述',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `status` VARCHAR(50) DEFAULT 'active' COMMENT '状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_creator_id` (`creator_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- -----------------------------------------------------
-- 7. 项目成员表 project_members
-- -----------------------------------------------------
DROP TABLE IF EXISTS `project_members`;
CREATE TABLE `project_members` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `role` VARCHAR(50) NOT NULL COMMENT '项目内角色',
  `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_user_id` (`user_id`),
  UNIQUE INDEX `uk_project_user` (`project_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员表';

-- -----------------------------------------------------
-- 8. 自定义字段表 custom_fields
-- -----------------------------------------------------
DROP TABLE IF EXISTS `custom_fields`;
CREATE TABLE `custom_fields` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `name` VARCHAR(100) NOT NULL COMMENT '字段名称',
  `field_type` VARCHAR(50) NOT NULL COMMENT '字段类型(text/number/date/select/multi_select/user)',
  `options` JSON DEFAULT NULL COMMENT '选项( select/multi_select 类型使用)',
  `required` TINYINT DEFAULT 0 COMMENT '是否必填 0=否 1=是',
  `visible_statuses` JSON DEFAULT NULL COMMENT '可见的状态列表',
  `default_value` VARCHAR(500) DEFAULT NULL COMMENT '默认值',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义字段表';

-- -----------------------------------------------------
-- 9. 工作流状态表 workflow_states
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_states`;
CREATE TABLE `workflow_states` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `name` VARCHAR(50) NOT NULL COMMENT '状态名称',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '颜色标识',
  `is_final` TINYINT DEFAULT 0 COMMENT '是否终态 0=否 1=是',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流状态表';

-- -----------------------------------------------------
-- 10. 工作流转换表 workflow_transitions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_transitions`;
CREATE TABLE `workflow_transitions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `from_state_id` INT UNSIGNED NOT NULL COMMENT '源状态ID',
  `to_state_id` INT UNSIGNED NOT NULL COMMENT '目标状态ID',
  `allowed_roles` JSON DEFAULT NULL COMMENT '允许的角色列表',
  `required_fields` JSON DEFAULT NULL COMMENT '必填字段列表',
  `conditions` JSON DEFAULT NULL COMMENT '转换条件',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_from_state_id` (`from_state_id`),
  INDEX `idx_to_state_id` (`to_state_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流转换表';

-- -----------------------------------------------------
-- 10.1 工作流转换记录表 workflow_transition_records
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_transition_records`;
CREATE TABLE `workflow_transition_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `from_state_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '源状态ID',
  `to_state_id` BIGINT UNSIGNED NOT NULL COMMENT '目标状态ID',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
  `comment` TEXT DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流转换记录表';

-- -----------------------------------------------------
-- 11. 工作流版本表 workflow_versions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_versions`;
CREATE TABLE `workflow_versions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `version` INT NOT NULL COMMENT '版本号',
  `name` VARCHAR(100) NOT NULL COMMENT '版本名称',
  `definition` JSON NOT NULL COMMENT '工作流定义JSON',
  `is_active` TINYINT DEFAULT 0 COMMENT '是否当前启用 0=否 1=是',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_is_active` (`is_active`),
  UNIQUE INDEX `uk_project_version` (`project_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流版本表';

-- -----------------------------------------------------
-- 12. 工作流节点权限表 workflow_node_permissions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_node_permissions`;
CREATE TABLE `workflow_node_permissions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `node_id` VARCHAR(100) NOT NULL COMMENT '节点ID',
  `allowed_roles` JSON DEFAULT NULL COMMENT '允许的角色列表',
  `allowed_users` JSON DEFAULT NULL COMMENT '允许的用户ID列表',
  `assignee_rule` VARCHAR(100) DEFAULT NULL COMMENT '指派规则',
  `visible_fields` JSON DEFAULT NULL COMMENT '可见字段列表',
  `editable_fields` JSON DEFAULT NULL COMMENT '可编辑字段列表',
  `required_fields` JSON DEFAULT NULL COMMENT '必填字段列表',
  `available_actions` JSON DEFAULT NULL COMMENT '可用操作列表',
  `action_conditions` JSON DEFAULT NULL COMMENT '操作条件',
  `notification_rules` JSON DEFAULT NULL COMMENT '通知规则',
  `timeout_hours` INT DEFAULT NULL COMMENT '超时小时数',
  `data_permissions` JSON DEFAULT NULL COMMENT '数据权限',
  `attachment_permissions` JSON DEFAULT NULL COMMENT '附件权限',
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`),
  INDEX `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点权限表';

-- -----------------------------------------------------
-- 13. 迭代表 iterations
-- -----------------------------------------------------
DROP TABLE IF EXISTS `iterations`;
CREATE TABLE `iterations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '迭代名称',
  `description` TEXT COMMENT '迭代描述',
  `start_date` DATE NOT NULL COMMENT '开始日期',
  `end_date` DATE NOT NULL COMMENT '结束日期',
  `capacity` DECIMAL(10, 2) DEFAULT NULL COMMENT '迭代容量',
  `status` VARCHAR(50) DEFAULT 'planned' COMMENT '状态',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迭代表';

-- -----------------------------------------------------
-- 14. 需求表 requirements
-- -----------------------------------------------------
DROP TABLE IF EXISTS `requirements`;
CREATE TABLE `requirements` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `parent_id` INT UNSIGNED DEFAULT NULL COMMENT '父需求ID(树形结构)',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `assignee_id` INT UNSIGNED DEFAULT NULL COMMENT '负责人ID',
  `ops_follow_id` INT UNSIGNED DEFAULT NULL COMMENT '运维跟进人ID',
  `maint_follow_id` INT UNSIGNED DEFAULT NULL COMMENT '维护跟进人ID',
  `department_id` INT UNSIGNED DEFAULT NULL COMMENT '所属部门ID',
  `title` VARCHAR(500) NOT NULL COMMENT '标题',
  `description` LONGTEXT COMMENT '描述',
  `type` VARCHAR(50) NOT NULL COMMENT '类型(feature/bug/improvement等)',
  `priority` VARCHAR(50) NOT NULL COMMENT '优先级(critical/high/medium/low)',
  `status` VARCHAR(50) NOT NULL COMMENT '状态',
  `module_id` INT UNSIGNED DEFAULT NULL COMMENT '模块ID',
  `iteration_id` INT UNSIGNED DEFAULT NULL COMMENT '所属迭代ID',
  `start_date` DATE DEFAULT NULL COMMENT '开始日期',
  `estimated_hours` DECIMAL(10, 2) DEFAULT NULL COMMENT '预估工时',
  `actual_hours` DECIMAL(10, 2) DEFAULT NULL COMMENT '实际工时',
  `due_date` DATE DEFAULT NULL COMMENT '截止日期',
  `analysis_completed_at` DATETIME DEFAULT NULL COMMENT '分析完成时间',
  `confirm_at` DATETIME DEFAULT NULL COMMENT '确认时间',
  `development_completed_at` DATETIME DEFAULT NULL COMMENT '开发完成时间',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `order_num` INT DEFAULT 0 COMMENT '排序号',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_assignee_id` (`assignee_id`),
  INDEX `idx_deleted_at` (`deleted_at`),
  INDEX `idx_iteration_id` (`iteration_id`),
  INDEX `idx_creator_id` (`creator_id`),
  INDEX `idx_type` (`type`),
  INDEX `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求表';

-- -----------------------------------------------------
-- 15. 文件记录表 file_records
-- -----------------------------------------------------
DROP TABLE IF EXISTS `file_records`;
CREATE TABLE `file_records` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `storage_name` VARCHAR(255) NOT NULL COMMENT 'MinIO对象名',
  `file_size` BIGINT NOT NULL COMMENT '文件大小',
  `content_type` VARCHAR(100) DEFAULT NULL COMMENT 'MIME类型',
  `bucket_name` VARCHAR(100) NOT NULL COMMENT '存储桶名称',
  `uploader_id` INT UNSIGNED NOT NULL COMMENT '上传人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_uploader_id` (`uploader_id`),
  INDEX `idx_bucket_name` (`bucket_name`),
  UNIQUE INDEX `uk_storage_name` (`storage_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件记录表';

-- -----------------------------------------------------
-- 16. 需求自定义字段值表 requirement_custom_field_values
-- -----------------------------------------------------
DROP TABLE IF EXISTS `requirement_custom_field_values`;
CREATE TABLE `requirement_custom_field_values` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `field_id` INT UNSIGNED NOT NULL COMMENT '自定义字段ID',
  `value_text` TEXT COMMENT '文本值',
  `value_number` DECIMAL(10, 2) DEFAULT NULL COMMENT '数值',
  `value_date` DATE DEFAULT NULL COMMENT '日期值',
  `value_user_ids` JSON DEFAULT NULL COMMENT '用户ID列表',
  PRIMARY KEY (`id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_field_id` (`field_id`),
  UNIQUE INDEX `uk_requirement_field` (`requirement_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求自定义字段值表';

-- -----------------------------------------------------
-- 17. 需求关系表 requirement_relations
-- -----------------------------------------------------
DROP TABLE IF EXISTS `requirement_relations`;
CREATE TABLE `requirement_relations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_id` INT UNSIGNED NOT NULL COMMENT '源需求ID',
  `target_id` INT UNSIGNED NOT NULL COMMENT '目标需求ID',
  `relation_type` VARCHAR(50) NOT NULL COMMENT '关系类型(depends_on/blocks/related_to等)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_source_target_type` (`source_id`, `target_id`, `relation_type`),
  INDEX `idx_source_id` (`source_id`),
  INDEX `idx_target_id` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求关系表';

-- -----------------------------------------------------
-- 18. 需求历史表 requirement_history
-- -----------------------------------------------------
DROP TABLE IF EXISTS `requirement_history`;
CREATE TABLE `requirement_history` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `operator_id` INT UNSIGNED NOT NULL COMMENT '操作人ID',
  `field_name` VARCHAR(100) NOT NULL COMMENT '变更字段',
  `old_value` TEXT COMMENT '旧值',
  `new_value` TEXT COMMENT '新值',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求历史表';

-- -----------------------------------------------------
-- 19. 评审表 reviews
-- -----------------------------------------------------
DROP TABLE IF EXISTS `reviews`;
CREATE TABLE `reviews` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `reviewer_id` INT UNSIGNED NOT NULL COMMENT '评审人ID',
  `result` VARCHAR(50) NOT NULL COMMENT '评审结果(pass/reject/pending)',
  `comment` TEXT COMMENT '评审意见',
  `suggestions` TEXT COMMENT '改进建议',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '评审时间',
  PRIMARY KEY (`id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_reviewer_id` (`reviewer_id`),
  INDEX `idx_result` (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审表';

-- -----------------------------------------------------
-- 20. 需求类型表 requirement_types
-- -----------------------------------------------------
DROP TABLE IF EXISTS `requirement_types`;
CREATE TABLE `requirement_types` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '类型编码',
  `name` VARCHAR(100) NOT NULL COMMENT '类型名称',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '颜色',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求类型表';

-- -----------------------------------------------------
-- 21. 优先级表 priorities
-- -----------------------------------------------------
DROP TABLE IF EXISTS `priorities`;
CREATE TABLE `priorities` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '优先级编码',
  `name` VARCHAR(100) NOT NULL COMMENT '优先级名称',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '颜色',
  `level` INT DEFAULT 0 COMMENT '级别(数字越小越高)',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优先级表';

-- =====================================================
-- SEED DATA - 初始化数据
-- =====================================================

-- 区域数据
INSERT INTO `regions` (`id`, `name`, `parent_id`, `code`, `sort_order`) VALUES
(1, '华东区', NULL, 'HD', 1),
(2, '华南区', NULL, 'HN', 2),
(3, '华北区', NULL, 'HB', 3);

-- 部门数据
INSERT INTO `departments` (`id`, `name`, `parent_id`, `region_id`, `code`, `type`, `sort_order`) VALUES
(1, '产品研发部', NULL, 1, 'RD001', '研发', 1);

-- 职位数据
INSERT INTO `positions` (`id`, `name`, `code`, `level`, `description`) VALUES
(1, '产品经理', 'PM', 1, '负责产品规划和需求管理'),
(2, '开发工程师', 'DEV', 2, '负责功能开发和技术实现'),
(3, '测试工程师', 'QA', 2, '负责功能测试和质量保证');

-- 用户数据 (密码: admin123, BCrypt加密)
INSERT INTO `users` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `avatar`, `status`, `created_at`, `updated_at`, `deleted_at`) VALUES
(1, 'admin', '$2b$12$.SPoAlnnJvD.VajrVmgCdeBTWE/DQ75Ym/P9dGL.3IzT4ewED9QVG', '系统管理员', 'admin@demand.com', NULL, NULL, 'active', NOW(), NOW(), 0);

-- 用户组织关系
INSERT INTO `user_organizations` (`user_id`, `region_id`, `department_id`, `position_id`, `system_role`, `manager_id`, `effective_date`) VALUES
(1, 1, 1, 1, 'admin', NULL, '2026-01-01');

-- 角色数据和用户角色分配移至建表语句之后

-- 需求类型数据
INSERT INTO `requirement_types` (`code`, `name`, `color`, `sort_order`, `is_default`) VALUES
('FEATURE', '功能', '#409EFF', 1, 1),
('OPTIMIZATION', '优化', '#67C23A', 2, 0),
('BUG', 'Bug', '#F56C6C', 3, 0),
('TECH_DEBT', '技术债务', '#E6A23C', 4, 0),
('OPERATION', '运营', '#909399', 5, 0);

-- 优先级数据
INSERT INTO `priorities` (`code`, `name`, `color`, `level`, `sort_order`, `is_default`) VALUES
('P0', 'P0-紧急', '#F56C6C', 0, 1, 0),
('P1', 'P1-高', '#E6A23C', 1, 2, 1),
('P2', 'P2-中', '#409EFF', 2, 3, 0),
('P3', 'P3-低', '#909399', 3, 4, 0);

-- -----------------------------------------------------
-- 21. 角色表 roles
-- -----------------------------------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
  `description` TEXT COMMENT '角色描述',
  `is_system` TINYINT DEFAULT 0 COMMENT '是否系统角色 0=否 1=是',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_code` (`code`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- -----------------------------------------------------
-- 22. 用户角色关系表 user_roles
-- -----------------------------------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` INT UNSIGNED NOT NULL COMMENT '角色ID',
  `project_id` INT UNSIGNED DEFAULT NULL COMMENT '项目ID（NULL表示全局角色）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_role_id` (`role_id`),
  INDEX `idx_project_id` (`project_id`),
  UNIQUE INDEX `uk_user_role_project` (`user_id`, `role_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

-- 角色数据（移到建表之后）
INSERT INTO `roles` (`code`, `name`, `description`, `is_system`) VALUES
('SUPER_ADMIN', '超级管理员', '系统最高权限，可管理所有配置', 1),
('REGION_ADMIN', '区域管理员', '管理指定区域的部门和人员', 1),
('DEPT_ADMIN', '部门管理员', '管理指定部门的人员', 1),
('PRODUCT_MANAGER', '产品经理', '负责需求评审和验收', 0),
('PROJECT_MANAGER', '项目经理', '负责项目管理和迭代规划', 0),
('DEVELOPER', '开发人员', '负责需求开发', 0),
('TESTER', '测试人员', '负责需求测试', 0),
('REVIEWER', '评审人', '负责需求评审', 0);

-- 给admin用户分配超级管理员角色
INSERT INTO `user_roles` (`user_id`, `role_id`, `project_id`) VALUES
(1, 1, NULL);

-- -----------------------------------------------------
-- 23. 权限字典表 sys_permissions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `sys_permissions`;
CREATE TABLE `sys_permissions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(100) NOT NULL COMMENT '权限编码',
  `name` VARCHAR(100) NOT NULL COMMENT '权限名称',
  `type` VARCHAR(20) NOT NULL COMMENT '类型(MENU/BUTTON)',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限字典表';

-- -----------------------------------------------------
-- 24. 角色权限关系表 sys_role_permissions
-- -----------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permissions`;
CREATE TABLE `sys_role_permissions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id` INT UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` INT UNSIGNED NOT NULL COMMENT '权限ID',
  `granted_by` INT UNSIGNED DEFAULT NULL COMMENT '授权人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_role_permission` (`role_id`, `permission_id`),
  INDEX `idx_role_id` (`role_id`),
  INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系表';

-- -----------------------------------------------------
-- 25. 菜单表 sys_menus
-- -----------------------------------------------------
DROP TABLE IF EXISTS `sys_menus`;
CREATE TABLE `sys_menus` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT DEFAULT 0 COMMENT '上级ID',
  `name` VARCHAR(100) NOT NULL COMMENT '名称',
  `menu_type` VARCHAR(20) NOT NULL COMMENT '类型(DIRECTORY/MENU/BUTTON)',
  `path` VARCHAR(200) DEFAULT NULL COMMENT '路径',
  `route_name` VARCHAR(100) DEFAULT NULL COMMENT '路由名称',
  `component` VARCHAR(200) DEFAULT NULL COMMENT '组件',
  `icon` VARCHAR(100) DEFAULT NULL COMMENT '图标',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `permission_code` VARCHAR(100) DEFAULT NULL COMMENT '权限编码',
  `visible` TINYINT DEFAULT 1 COMMENT '是否可见 1=是 0=否',
  `enabled` TINYINT DEFAULT 1 COMMENT '是否启用 1=是 0=否',
  `keep_alive` TINYINT DEFAULT 0 COMMENT '是否缓存 1=是 0=否',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

-- -----------------------------------------------------
-- 初始化权限数据
-- -----------------------------------------------------
INSERT INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`) VALUES
-- 菜单权限
(1,  'menu:system-config', '系统配置菜单', 'MENU', '系统配置一级菜单入口', 1),
(2,  'menu:settings:project', '项目管理菜单', 'MENU', '系统设置-项目管理', 1),
(3,  'menu:settings:user', '用户管理菜单', 'MENU', '系统设置-用户管理', 1),
(4,  'menu:settings:org', '组织架构菜单', 'MENU', '系统设置-组织架构', 1),
(5,  'menu:settings:requirement', '需求配置菜单', 'MENU', '系统设置-需求配置', 1),
(6,  'menu:settings:workflow', '工作流配置菜单', 'MENU', '系统设置-工作流配置', 1),
(7,  'menu:menu-management', '菜单管理菜单', 'MENU', '系统设置-菜单管理', 1),
(8,  'menu:rag', 'RAG文档中心菜单', 'MENU', 'RAG文档中心入口', 1),
-- 按钮权限
(9,  'button:menu:create', '新增菜单按钮', 'BUTTON', '菜单管理-新增', 1),
(10, 'button:menu:update', '编辑菜单按钮', 'BUTTON', '菜单管理-编辑', 1),
(11, 'button:menu:delete', '删除菜单按钮', 'BUTTON', '菜单管理-删除', 1),
(12, 'button:menu:grant', '角色授权按钮', 'BUTTON', '菜单管理-角色授权', 1),
(13, 'button:user:create', '新增用户按钮', 'BUTTON', '用户管理-新增', 1),
(14, 'button:user:update', '编辑用户按钮', 'BUTTON', '用户管理-编辑', 1),
(15, 'button:user:delete', '删除用户按钮', 'BUTTON', '用户管理-删除', 1),
(16, 'button:workflow:config', '工作流配置按钮', 'BUTTON', '工作流配置操作', 1),
(17, 'button:rag:upload', 'RAG文档上传按钮', 'BUTTON', 'RAG-上传文档', 1),
(18, 'button:rag:search', 'RAG文档搜索按钮', 'BUTTON', 'RAG-智能搜索', 1);

-- -----------------------------------------------------
-- 初始化菜单树数据
-- -----------------------------------------------------
INSERT INTO `sys_menus` (`id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`) VALUES
-- 一级目录
(1,  0, '仪表盘', 'MENU', '/dashboard', 'Dashboard', 'views/dashboard/index.vue', 'Odometer', 1, NULL, 1, 1, 0),
(2,  0, '需求管理', 'MENU', '/requirements', 'Requirements', 'views/requirements/index.vue', 'Document', 2, NULL, 1, 1, 0),
(3,  0, '迭代管理', 'MENU', '/iterations', 'Iterations', 'views/iterations/index.vue', 'Calendar', 3, NULL, 1, 1, 0),
(4,  0, '评审管理', 'MENU', '/reviews', 'Reviews', 'views/reviews/index.vue', 'ChatDotRound', 4, NULL, 1, 1, 0),
(5,  0, '统计报表', 'MENU', '/statistics', 'Statistics', 'views/statistics/index.vue', 'TrendCharts', 5, NULL, 1, 1, 0),
(6,  0, 'RAG文档中心', 'MENU', '/settings/rag', 'RagCenter', 'views/rag/index.vue', 'Files', 7, 'menu:rag', 1, 1, 0),
(7,  0, '系统配置', 'DIRECTORY', NULL, NULL, NULL, 'Setting', 6, 'menu:system-config', 1, 1, 0),
(8,  0, '知识库管理', 'MENU', '/settings/knowledge', 'KnowledgeBases', 'views/knowledge/index.vue', 'Collection', 8, 'menu:rag', 1, 1, 0),
-- 系统配置子菜单
(10, 0, '项目管理', 'MENU', '/settings/projects', 'SettingsProjects', 'views/settings/projects.vue', 'Folder', 9, 'menu:settings:project', 1, 1, 0),
(11, 7, '用户管理', 'MENU', '/settings/users', 'SettingsUsers', 'views/settings/users.vue', 'User', 1, 'menu:settings:user', 1, 1, 0),
(12, 7, '组织架构', 'MENU', '/settings/org', 'SettingsOrg', 'views/settings/org.vue', 'OfficeBuilding', 2, 'menu:settings:org', 1, 1, 0),
(13, 7, '需求配置', 'MENU', '/settings/requirements', 'SettingsRequirements', 'views/settings/requirements.vue', 'Setting', 3, 'menu:settings:requirement', 1, 1, 0),
(14, 7, '工作流配置', 'MENU', '/system/workflow-config', 'WorkflowConfig', 'views/system/workflow-config/index.vue', 'Share', 4, 'menu:settings:workflow', 1, 1, 0),
(15, 7, '菜单管理', 'MENU', '/settings/menus', 'MenuManagement', 'views/settings/menus.vue', 'Menu', 5, 'menu:menu-management', 1, 1, 0),
-- 菜单管理下的按钮
(20, 15, '新增菜单', 'BUTTON', NULL, NULL, NULL, NULL, 1, 'button:menu:create', 1, 1, 0),
(21, 15, '编辑菜单', 'BUTTON', NULL, NULL, NULL, NULL, 2, 'button:menu:update', 1, 1, 0),
(22, 15, '删除菜单', 'BUTTON', NULL, NULL, NULL, NULL, 3, 'button:menu:delete', 1, 1, 0),
(23, 15, '角色授权', 'BUTTON', NULL, NULL, NULL, NULL, 4, 'button:menu:grant', 1, 1, 0),
-- 用户管理下的按钮
(30, 11, '新增用户', 'BUTTON', NULL, NULL, NULL, NULL, 1, 'button:user:create', 1, 1, 0),
(31, 11, '编辑用户', 'BUTTON', NULL, NULL, NULL, NULL, 2, 'button:user:update', 1, 1, 0),
(32, 11, '删除用户', 'BUTTON', NULL, NULL, NULL, NULL, 3, 'button:user:delete', 1, 1, 0);

-- -----------------------------------------------------
-- 初始化角色授权数据（SUPER_ADMIN获得全部权限）
-- -----------------------------------------------------
INSERT INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`) VALUES
(1, 1, 1), (1, 2, 1), (1, 3, 1), (1, 4, 1), (1, 5, 1), (1, 6, 1), (1, 7, 1), (1, 8, 1),
(1, 9, 1), (1, 10, 1), (1, 11, 1), (1, 12, 1), (1, 13, 1), (1, 14, 1), (1, 15, 1), (1, 16, 1), (1, 17, 1), (1, 18, 1);

-- -----------------------------------------------------
-- 27. 工作流节点表 workflow_nodes
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_nodes`;
CREATE TABLE `workflow_nodes` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `node_id` VARCHAR(100) NOT NULL COMMENT '节点ID（前端生成的唯一ID）',
  `node_type` VARCHAR(50) NOT NULL COMMENT '节点类型(start/approval/condition/parallel/end)',
  `node_name` VARCHAR(100) NOT NULL COMMENT '节点名称',
  `position_x` INT DEFAULT 0 COMMENT 'X坐标',
  `position_y` INT DEFAULT 0 COMMENT 'Y坐标',
  `assignee_type` VARCHAR(50) DEFAULT NULL COMMENT '处理人类型(role/user/dynamic)',
  `assignee_role_id` INT UNSIGNED DEFAULT NULL COMMENT '角色ID',
  `assignee_user_ids` JSON DEFAULT NULL COMMENT '指定用户ID列表',
  `timeout_hours` INT DEFAULT NULL COMMENT '超时小时数',
  `timeout_action` VARCHAR(50) DEFAULT NULL COMMENT '超时后操作(auto_pass/auto_reject/escalate)',
  `properties` JSON DEFAULT NULL COMMENT '节点属性配置',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`),
  INDEX `idx_node_id` (`node_id`),
  UNIQUE INDEX `uk_version_node` (`workflow_version_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点表';

-- -----------------------------------------------------
-- 28. 工作流连线表 workflow_edges
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_edges`;
CREATE TABLE `workflow_edges` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `edge_id` VARCHAR(100) NOT NULL COMMENT '连线ID（前端生成的唯一ID）',
  `source_node_id` VARCHAR(100) NOT NULL COMMENT '源节点ID',
  `target_node_id` VARCHAR(100) NOT NULL COMMENT '目标节点ID',
  `label` VARCHAR(100) DEFAULT NULL COMMENT '连线标签',
  `condition` JSON DEFAULT NULL COMMENT '流转条件',
  `properties` JSON DEFAULT NULL COMMENT '连线属性配置',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`),
  INDEX `idx_source_node_id` (`source_node_id`),
  INDEX `idx_target_node_id` (`target_node_id`),
  UNIQUE INDEX `uk_version_edge` (`workflow_version_id`, `edge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流连线表';

-- -----------------------------------------------------
-- 29. 工作流审核表 workflow_approvals
-- -----------------------------------------------------
DROP TABLE IF EXISTS `workflow_approvals`;
CREATE TABLE `workflow_approvals` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `submitter_id` INT UNSIGNED NOT NULL COMMENT '提交人ID',
  `approver_id` INT UNSIGNED DEFAULT NULL COMMENT '审核人ID',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT '状态(pending/approved/rejected)',
  `comment` TEXT COMMENT '审核意见',
  `submitted_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `approved_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_submitter_id` (`submitter_id`),
  INDEX `idx_approver_id` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流审核表';

-- 演示项目
INSERT INTO `projects` (`id`, `name`, `description`, `creator_id`, `status`, `created_at`, `updated_at`, `deleted_at`) VALUES
(1, '演示项目', '默认演示项目', 1, 'active', NOW(), NOW(), 0);

-- 工作流状态 (演示项目)
INSERT INTO `workflow_states` (`project_id`, `name`, `color`, `is_final`, `sort_order`) VALUES
(1, '新建', '#409EFF', 0, 1),
(1, '待评审', '#E6A23C', 0, 2),
(1, '评审中', '#E6A23C', 0, 3),
(1, '已通过', '#67C23A', 0, 4),
(1, '开发中', '#409EFF', 0, 5),
(1, '测试中', '#E6A23C', 0, 6),
(1, '已上线', '#67C23A', 0, 7),
(1, '已验收', '#909399', 1, 8);

-- 工作流转换 (演示项目)
-- 新建 -> 待评审(创建人,产品经理), 新建 -> 已取消(创建人)
INSERT INTO `workflow_transitions` (`project_id`, `from_state_id`, `to_state_id`, `allowed_roles`, `required_fields`, `conditions`) VALUES
(1, 1, 2, '["创建人", "产品经理"]', '[]', '{}'),
(1, 1, 9, '["创建人"]', '[]', '{}');
-- Note: id=9 is "已取消", but we need to add cancelled state first

-- Add cancelled, rejected, test_failed, accepted_failed states
INSERT INTO `workflow_states` (`project_id`, `name`, `color`, `is_final`, `sort_order`) VALUES
(1, '已取消', '#909399', 1, 9),
(1, '已拒绝', '#909399', 1, 10),
(1, '打回', '#E6A23C', 0, 11),
(1, '测试不通过', '#F56C6C', 0, 12),
(1, '验收不通过', '#F56C6C', 0, 13);

-- Now add all workflow transitions (using correct state IDs)
-- from_state_id references: 1=新建, 2=待评审, 3=评审中, 4=已通过, 5=开发中, 6=测试中, 7=已上线, 8=已验收, 9=已取消, 10=已拒绝, 11=打回, 12=测试不通过, 13=验收不通过
INSERT INTO `workflow_transitions` (`project_id`, `from_state_id`, `to_state_id`, `allowed_roles`, `required_fields`, `conditions`) VALUES
-- 待评审 -> 评审中(产品经理), 待评审 -> 已取消(产品经理)
(1, 2, 3, '["产品经理"]', '[]', '{}'),
(1, 2, 9, '["产品经理"]', '[]', '{}'),
-- 评审中 -> 已通过(评审人,required:评审意见), 评审中 -> 已拒绝(评审人,required:评审意见)
(1, 3, 4, '["评审人"]', '["评审意见"]', '{}'),
(1, 3, 10, '["评审人"]', '["评审意见"]', '{}'),
-- 已通过 -> 开发中(项目经理,required:所属迭代)
(1, 4, 5, '["项目经理"]', '["所属迭代"]', '{}'),
-- 开发中 -> 测试中(开发人员,required:开发说明), 开发中 -> 打回(开发人员)
(1, 5, 6, '["开发人员"]', '["开发说明"]', '{}'),
(1, 5, 11, '["开发人员"]', '[]', '{}'),
-- 测试中 -> 已上线(测试人员,required:测试报告), 测试中 -> 测试不通过(测试人员)
(1, 6, 7, '["测试人员"]', '["测试报告"]', '{}'),
(1, 6, 12, '["测试人员"]', '[]', '{}'),
-- 已上线 -> 已验收(产品经理,required:验收结论), 已上线 -> 验收不通过(产品经理)
(1, 7, 8, '["产品经理"]', '["验收结论"]', '{}'),
(1, 7, 13, '["产品经理"]', '[]', '{}');

-- Remove the placeholder transition rows inserted earlier (the first two that referenced id=9 before it existed)
DELETE FROM `workflow_transitions` WHERE `project_id` = 1 AND `from_state_id` = 1 AND `to_state_id` IN (2, 9);

-- Re-insert the correct "新建" transitions now that state IDs exist
INSERT INTO `workflow_transitions` (`project_id`, `from_state_id`, `to_state_id`, `allowed_roles`, `required_fields`, `conditions`) VALUES
-- 新建 -> 待评审(创建人,产品经理)
(1, 1, 2, '["创建人", "产品经理"]', '[]', '{}'),
-- 新建 -> 已取消(创建人)
(1, 1, 9, '["创建人"]', '[]', '{}');

-- -----------------------------------------------------
-- 30. 通知表 notifications
-- -----------------------------------------------------
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` TEXT COMMENT '内容',
  `type` VARCHAR(50) DEFAULT NULL COMMENT '类型',
  `related_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联ID',
  `is_read` TINYINT DEFAULT 0 COMMENT '是否已读 0=否 1=是',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

-- -----------------------------------------------------
-- 31. 知识库表 knowledge_bases
-- -----------------------------------------------------
DROP TABLE IF EXISTS `knowledge_bases`;
CREATE TABLE `knowledge_bases` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '知识库名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联项目ID',
  `creator_id` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `doc_count` INT UNSIGNED DEFAULT 0 COMMENT '文档数量',
  `chunk_count` INT UNSIGNED DEFAULT 0 COMMENT '分块数量',
  `status` VARCHAR(50) DEFAULT 'active' COMMENT '状态(active/archived)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_creator_id` (`creator_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- -----------------------------------------------------
-- 32. 知识库文档表 knowledge_documents
-- -----------------------------------------------------
DROP TABLE IF EXISTS `knowledge_documents`;
CREATE TABLE `knowledge_documents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联项目ID',
  `file_name` VARCHAR(500) NOT NULL COMMENT '文件名',
  `file_type` VARCHAR(32) DEFAULT NULL COMMENT '文件类型(pdf/txt/md/docx)',
  `file_size` BIGINT UNSIGNED DEFAULT 0 COMMENT '文件大小(字节)',
  `chunk_count` INT UNSIGNED DEFAULT 0 COMMENT '分块数量',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT '状态(pending/parsed/indexing/indexed/failed)',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  `minio_key` VARCHAR(500) DEFAULT NULL COMMENT 'MinIO存储Key',
  `requirement_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求ID',
  `source_type` VARCHAR(50) DEFAULT NULL COMMENT '来源类型(requirement/knowledge_base)',
  `source_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源业务ID',
  `uploader_id` BIGINT UNSIGNED NOT NULL COMMENT '上传人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_knowledge_base_id` (`knowledge_base_id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_source_type_source_id` (`source_type`, `source_id`),
  INDEX `idx_uploader_id` (`uploader_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- -----------------------------------------------------
-- 33. 知识库文档分享表 knowledge_document_shares
-- -----------------------------------------------------
DROP TABLE IF EXISTS `knowledge_document_shares`;
CREATE TABLE `knowledge_document_shares` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `token` VARCHAR(64) NOT NULL COMMENT '分享令牌',
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  `creator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `require_login` TINYINT DEFAULT 0 COMMENT '是否需要登录 0=否 1=是',
  `one_time_access` TINYINT DEFAULT 0 COMMENT '是否一次性链接 0=否 1=是',
  `used_count` INT UNSIGNED DEFAULT 0 COMMENT '使用次数',
  `status` VARCHAR(32) DEFAULT 'active' COMMENT '状态(active/used/expired)',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_token` (`token`),
  INDEX `idx_document_id` (`document_id`),
  INDEX `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分享表';

-- -----------------------------------------------------
-- 34. 知识库文档分享访问日志表 knowledge_document_share_logs
-- -----------------------------------------------------
DROP TABLE IF EXISTS `knowledge_document_share_logs`;
CREATE TABLE `knowledge_document_share_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `share_id` BIGINT UNSIGNED NOT NULL COMMENT '分享记录ID',
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  `access_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '访问用户ID',
  `access_ip` VARCHAR(64) DEFAULT NULL COMMENT '访问IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '访问UA',
  `access_status` VARCHAR(32) DEFAULT 'success' COMMENT '访问状态(success/failed)',
  `failure_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_share_id` (`share_id`),
  INDEX `idx_document_id` (`document_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分享访问日志';

-- -----------------------------------------------------
-- 35. 知识库文档分块表 knowledge_chunks
-- -----------------------------------------------------
DROP TABLE IF EXISTS `knowledge_chunks`;
CREATE TABLE `knowledge_chunks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
  `chunk_index` INT UNSIGNED NOT NULL COMMENT '分块序号',
  `content` TEXT NOT NULL COMMENT '分块内容',
  `section_title` VARCHAR(256) DEFAULT NULL COMMENT '章节标题',
  `page_num` INT UNSIGNED DEFAULT NULL COMMENT '页码',
  `char_count` INT UNSIGNED DEFAULT 0 COMMENT '字符数',
  `vector_id` VARCHAR(128) DEFAULT NULL COMMENT 'Milvus中的向量ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_document_id` (`document_id`),
  INDEX `idx_knowledge_base_id` (`knowledge_base_id`),
  INDEX `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分块表';

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------
-- 增量变更：需求表新增字段
-- -----------------------------------------------------
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS ops_follow_id BIGINT UNSIGNED DEFAULT NULL COMMENT '运营跟进人ID' AFTER assignee_id;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS maint_follow_id BIGINT UNSIGNED DEFAULT NULL COMMENT '运维跟进人ID' AFTER ops_follow_id;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS department_id BIGINT UNSIGNED DEFAULT NULL COMMENT '归属部门ID' AFTER maint_follow_id;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS analysis_completed_at DATETIME DEFAULT NULL COMMENT '分析完成时间' AFTER due_date;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS confirm_at DATETIME DEFAULT NULL COMMENT '需求确认时间' AFTER analysis_completed_at;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS development_completed_at DATETIME DEFAULT NULL COMMENT '开发完成时间' AFTER confirm_at;

-- -----------------------------------------------------
-- 增量变更：用户列配置表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS user_column_configs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  page_key VARCHAR(64) NOT NULL COMMENT '页面标识',
  visible_columns JSON DEFAULT NULL COMMENT '可见列配置',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_page (user_id, page_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户列配置';

-- -----------------------------------------------------
-- 增量变更：LLM接入组配置表 + 模型实例表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `llm_models`;
DROP TABLE IF EXISTS `llm_providers`;

CREATE TABLE `llm_providers` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '接入组名称',
  `protocol` VARCHAR(20) NOT NULL DEFAULT 'openai' COMMENT '协议: openai/anthropic',
  `base_url` VARCHAR(500) NOT NULL COMMENT 'API Base URL',
  `api_key` VARCHAR(500) NOT NULL COMMENT 'API Key',
  `enabled` TINYINT DEFAULT 1 COMMENT '启用状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM接入组配置';

CREATE TABLE `llm_models` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_id` INT UNSIGNED NOT NULL COMMENT '所属接入组ID',
  `name` VARCHAR(200) NOT NULL COMMENT '模型显示名',
  `model_id` VARCHAR(200) NOT NULL COMMENT 'API调用模型标识',
  `model_type` VARCHAR(50) DEFAULT 'general' COMMENT '模型类型: primary/haiku/sonnet/opus 或自定义',
  `temperature` DECIMAL(3,2) DEFAULT 0.30 COMMENT '温度参数',
  `max_tokens` INT DEFAULT 2048 COMMENT '最大token数',
  `is_default` TINYINT DEFAULT 0 COMMENT '该类型下的默认模型',
  `enabled` TINYINT DEFAULT 1 COMMENT '启用状态',
  `test_success` TINYINT DEFAULT NULL COMMENT '最近测试是否成功(1/0/null)',
  `test_duration` INT DEFAULT NULL COMMENT '最近测试耗时ms',
  `test_error` VARCHAR(500) DEFAULT NULL COMMENT '最近测试错误信息',
  `test_at` DATETIME DEFAULT NULL COMMENT '最近测试时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_provider_id` (`provider_id`),
  INDEX `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM模型实例';

-- 模型配置权限
INSERT IGNORE INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`) VALUES
(19, 'menu:settings:llm', '模型配置菜单', 'MENU', '系统设置-模型配置', 1),
(20, 'button:llm:create', '新增模型配置', 'BUTTON', '模型配置-新增', 1),
(21, 'button:llm:update', '编辑模型配置', 'BUTTON', '模型配置-编辑', 1),
(22, 'button:llm:delete', '删除模型配置', 'BUTTON', '模型配置-删除', 1);

-- SUPER_ADMIN 授权模型配置权限
INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`) VALUES
(1, 19), (1, 20), (1, 21), (1, 22);

-- 模型配置菜单（系统设置子菜单）
INSERT IGNORE INTO `sys_menus` (`id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`) VALUES
(16, 0, '模型配置', 'MENU', '/settings/llm', 'LlmConfig', 'views/settings/llm.vue', 'MagicStick', 10, 'menu:settings:llm', 1, 1, 0);

-- =====================================================
-- Sprint 1 增量变更：工作流实例 + 流转记录 + 项目/岗位字段补全
-- =====================================================

-- -----------------------------------------------------
-- 节点状态全局字典表（独立于项目的全局状态定义）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `node_statuses` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '状态名称',
  `code` VARCHAR(50) NOT NULL COMMENT '状态编码',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '颜色',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_start` TINYINT DEFAULT 0 COMMENT '是否开始状态',
  `is_end` TINYINT DEFAULT 0 COMMENT '是否结束状态',
  `is_cancel` TINYINT DEFAULT 0 COMMENT '是否取消状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点状态全局字典';

INSERT IGNORE INTO `node_statuses` (`code`, `name`, `color`, `sort_order`, `is_start`, `is_end`, `is_cancel`) VALUES
('DRAFT', '新建', '#409EFF', 1, 1, 0, 0),
('PENDING_ANALYSIS', '待分析', '#E6A23C', 2, 0, 0, 0),
('PENDING_CONFIRM', '待确认', '#E6A23C', 3, 0, 0, 0),
('PENDING_REVIEW', '待评审', '#E6A23C', 4, 0, 0, 0),
('IN_DEVELOPMENT', '开发中', '#409EFF', 5, 0, 0, 0),
('IN_TESTING', '测试中', '#E6A23C', 6, 0, 0, 0),
('LIVE', '已上线', '#67C23A', 7, 0, 0, 0),
('ACCEPTED', '已验收', '#909399', 8, 0, 1, 0),
('CANCELLED', '已取消', '#909399', 9, 0, 0, 1);

UPDATE `node_statuses`
SET `name` = CASE `code`
  WHEN 'DRAFT' THEN '新建'
  WHEN 'PENDING_ANALYSIS' THEN '待分析'
  WHEN 'PENDING_CONFIRM' THEN '待确认'
  WHEN 'PENDING_REVIEW' THEN '待评审'
  WHEN 'IN_DEVELOPMENT' THEN '开发中'
  WHEN 'IN_TESTING' THEN '测试中'
  WHEN 'LIVE' THEN '已上线'
  WHEN 'ACCEPTED' THEN '已验收'
  WHEN 'CANCELLED' THEN '已取消'
  ELSE `name`
END
WHERE `code` IN (
  'DRAFT',
  'PENDING_ANALYSIS',
  'PENDING_CONFIRM',
  'PENDING_REVIEW',
  'IN_DEVELOPMENT',
  'IN_TESTING',
  'LIVE',
  'ACCEPTED',
  'CANCELLED'
);

-- -----------------------------------------------------
-- 工作流实例表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `workflow_instances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `workflow_version_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `current_node_id` VARCHAR(100) NOT NULL COMMENT '当前节点ID',
  `previous_node_id` VARCHAR(100) DEFAULT NULL COMMENT '上一节点ID（用于回退）',
  `status` VARCHAR(50) DEFAULT 'running' COMMENT 'running/completed/cancelled',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_requirement_id` (`requirement_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流实例';

-- -----------------------------------------------------
-- 工作流流转记录表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `workflow_instance_transitions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `from_node_id` VARCHAR(100) DEFAULT NULL COMMENT '源节点ID',
  `from_node_name` VARCHAR(100) DEFAULT NULL COMMENT '源节点名称',
  `to_node_id` VARCHAR(100) NOT NULL COMMENT '目标节点ID',
  `to_node_name` VARCHAR(100) NOT NULL COMMENT '目标节点名称',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作人ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作: submit/approve/reject/cancel/rollback',
  `comment` TEXT COMMENT '操作意见',
  `started_at` DATETIME NOT NULL COMMENT '进入该节点时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '离开该节点时间',
  `duration_seconds` BIGINT DEFAULT NULL COMMENT '停留秒数',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_instance_id` (`instance_id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流流转记录';

-- -----------------------------------------------------
-- 项目表字段补全
-- -----------------------------------------------------
ALTER TABLE projects ADD COLUMN IF NOT EXISTS `company_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '归属公司ID' AFTER description;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS `team` VARCHAR(200) DEFAULT NULL COMMENT '归属团队' AFTER company_id;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS `leader_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '负责人ID' AFTER team;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS `start_date` DATE DEFAULT NULL COMMENT '开始日期' AFTER leader_id;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS `end_date` DATE DEFAULT NULL COMMENT '截止日期' AFTER start_date;
ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联项目ID' AFTER knowledge_base_id;
ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS `requirement_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求ID' AFTER minio_key;
ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS `source_type` VARCHAR(50) DEFAULT NULL COMMENT '来源类型(requirement/knowledge_base)' AFTER requirement_id;
ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS `source_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源业务ID' AFTER source_type;

-- -----------------------------------------------------
-- 岗位表字段补全
-- -----------------------------------------------------
ALTER TABLE positions ADD COLUMN IF NOT EXISTS `region_id` INT UNSIGNED DEFAULT NULL COMMENT '归属区域ID' AFTER description;
ALTER TABLE positions ADD COLUMN IF NOT EXISTS `department_id` INT UNSIGNED DEFAULT NULL COMMENT '归属部门ID' AFTER region_id;
ALTER TABLE positions ADD COLUMN IF NOT EXISTS `menu_permissions` JSON DEFAULT NULL COMMENT '菜单权限ID列表' AFTER department_id;

-- -----------------------------------------------------
-- 需求表增加工作流相关字段
-- -----------------------------------------------------
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS `workflow_instance_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '工作流实例ID' AFTER iteration_id;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS `node_status` VARCHAR(50) DEFAULT 'DRAFT' COMMENT '当前节点状态' AFTER workflow_instance_id;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS `is_draft` TINYINT DEFAULT 1 COMMENT '是否草稿 0=否 1=是' AFTER node_status;

-- -----------------------------------------------------
-- 增量变更：知识库文档分享与访问审计
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_document_shares` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `token` VARCHAR(64) NOT NULL COMMENT '分享令牌',
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  `creator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `require_login` TINYINT DEFAULT 0 COMMENT '是否需要登录 0=否 1=是',
  `one_time_access` TINYINT DEFAULT 0 COMMENT '是否一次性链接 0=否 1=是',
  `used_count` INT UNSIGNED DEFAULT 0 COMMENT '使用次数',
  `status` VARCHAR(32) DEFAULT 'active' COMMENT '状态(active/used/expired)',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分享表';

CREATE TABLE IF NOT EXISTS `knowledge_document_share_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `share_id` BIGINT UNSIGNED NOT NULL COMMENT '分享记录ID',
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '文档ID',
  `access_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '访问用户ID',
  `access_ip` VARCHAR(64) DEFAULT NULL COMMENT '访问IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '访问UA',
  `access_status` VARCHAR(32) DEFAULT 'success' COMMENT '访问状态(success/failed)',
  `failure_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_share_id` (`share_id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分享访问日志';

-- -----------------------------------------------------
-- 增量变更：系统配置菜单收敛
-- -----------------------------------------------------
INSERT INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`)
VALUES (1, 'menu:system-config', '系统配置菜单', 'MENU', '系统配置一级菜单入口', 1)
ON DUPLICATE KEY UPDATE
  `code` = VALUES(`code`),
  `name` = VALUES(`name`),
  `type` = VALUES(`type`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

INSERT INTO `sys_menus` (
  `id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`,
  `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`
)
VALUES (
  7, 0, '系统配置', 'DIRECTORY', NULL, NULL, NULL, 'Setting',
  6, 'menu:system-config', 1, 1, 0
)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `name` = VALUES(`name`),
  `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`),
  `route_name` = VALUES(`route_name`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`),
  `permission_code` = VALUES(`permission_code`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `keep_alive` = VALUES(`keep_alive`);

UPDATE `sys_menus`
SET `parent_id` = 7,
    `sort_order` = CASE `id`
      WHEN 11 THEN 1
      WHEN 12 THEN 2
      WHEN 13 THEN 3
      WHEN 14 THEN 4
      WHEN 15 THEN 5
      ELSE `sort_order`
    END
WHERE `id` IN (11, 12, 13, 14, 15);

UPDATE `sys_menus`
SET `parent_id` = 0,
    `sort_order` = CASE `id`
      WHEN 6 THEN 7
      WHEN 8 THEN 8
      WHEN 10 THEN 9
      WHEN 16 THEN 10
      ELSE `sort_order`
    END
WHERE `id` IN (6, 8, 10, 16);

INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`)
VALUES (1, 1, 1);
