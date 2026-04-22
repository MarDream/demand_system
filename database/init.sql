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
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`)
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
  `code` VARCHAR(50) DEFAULT NULL COMMENT '部门编码',
  `type` VARCHAR(50) DEFAULT NULL COMMENT '部门类型',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_region_id` (`region_id`)
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
  PRIMARY KEY (`id`)
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
  `status` ENUM('active', 'inactive') DEFAULT 'active' COMMENT '状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`)
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
  `title` VARCHAR(500) NOT NULL COMMENT '标题',
  `description` LONGTEXT COMMENT '描述',
  `type` VARCHAR(50) NOT NULL COMMENT '类型(feature/bug/improvement等)',
  `priority` VARCHAR(50) NOT NULL COMMENT '优先级(critical/high/medium/low)',
  `status` VARCHAR(50) NOT NULL COMMENT '状态',
  `module_id` INT UNSIGNED DEFAULT NULL COMMENT '模块ID',
  `iteration_id` INT UNSIGNED DEFAULT NULL COMMENT '所属迭代ID',
  `estimated_hours` DECIMAL(10, 2) DEFAULT NULL COMMENT '预估工时',
  `actual_hours` DECIMAL(10, 2) DEFAULT NULL COMMENT '实际工时',
  `due_date` DATE DEFAULT NULL COMMENT '截止日期',
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
-- 15. 需求自定义字段值表 requirement_custom_field_values
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
-- 16. 需求关系表 requirement_relations
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
-- 17. 需求历史表 requirement_history
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
-- 18. 评审表 reviews
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
-- 19. 需求类型表 requirement_types
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
-- 20. 优先级表 priorities
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
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin@demand.com', NULL, NULL, 'active', NOW(), NOW(), 0);

-- 用户组织关系
INSERT INTO `user_organizations` (`user_id`, `region_id`, `department_id`, `position_id`, `system_role`, `manager_id`, `effective_date`) VALUES
(1, 1, 1, 1, 'admin', NULL, '2026-01-01');

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

SET FOREIGN_KEY_CHECKS = 1;
