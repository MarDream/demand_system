-- =====================================================
-- 需求管理系统 - 数据库初始化脚本
-- 字符集: utf8mb4, 引擎: InnoDB
-- 说明: 合并历史增量变更,按表集中管理 (DROP+CREATE+INSERT)
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 一、用户/组织/角色相关表
-- =====================================================

-- 1. 用户表 users
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
  `job_number` VARCHAR(20) DEFAULT NULL COMMENT '工号(A001~Z999, AA001...)',
  `org_id` INT UNSIGNED DEFAULT NULL COMMENT '所属组织ID',
  `status` ENUM('active', 'inactive') DEFAULT 'active' COMMENT '状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`),
  INDEX `idx_status` (`status`),
  INDEX `idx_region_id` (`region_id`),
  INDEX `idx_department_id` (`department_id`),
  INDEX `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 职位表 positions
DROP TABLE IF EXISTS `positions`;
CREATE TABLE `positions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '职位名称',
  `code` VARCHAR(50) DEFAULT NULL COMMENT '职位编码',
  `level` INT DEFAULT NULL COMMENT '职位级别',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '职位描述',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_position_code` (`code`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位表';
-- 3. 用户组织关系表 user_organizations
DROP TABLE IF EXISTS `user_organizations`;
CREATE TABLE `user_organizations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `region_id` INT UNSIGNED DEFAULT NULL COMMENT '区域ID',
  `department_id` INT UNSIGNED DEFAULT NULL COMMENT '部门ID',
  `org_id` INT UNSIGNED DEFAULT NULL COMMENT '组织ID',
  `system_role` VARCHAR(50) NOT NULL COMMENT '系统角色(admin/manager/user)',
  `manager_id` INT UNSIGNED DEFAULT NULL COMMENT '上级ID',
  `effective_date` DATE NOT NULL COMMENT '生效日期',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_region_id` (`region_id`),
  INDEX `idx_department_id` (`department_id`),
  INDEX `idx_manager_id` (`manager_id`),
  INDEX `idx_system_role` (`system_role`),
  INDEX `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户组织关系表';

-- 4. 统一组织架构表 sys_org
DROP TABLE IF EXISTS `sys_org`;
CREATE TABLE `sys_org` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '组织名称',
  `parent_id` INT UNSIGNED DEFAULT NULL COMMENT '父节点ID',
  `org_type` VARCHAR(20) NOT NULL COMMENT '类型: region/company/bureau/department/group',
  `code` VARCHAR(50) DEFAULT NULL COMMENT '组织编码',
  `leader_id` INT UNSIGNED DEFAULT NULL COMMENT '负责人ID',
  `description` TEXT COMMENT '描述',
  `sort_order` INT DEFAULT 0 COMMENT '同级排序号',
  `path` VARCHAR(500) DEFAULT NULL COMMENT '物化路径 /1/3/7/',
  `level` TINYINT UNSIGNED DEFAULT 0 COMMENT '层级深度',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除',
  PRIMARY KEY (`id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_org_type` (`org_type`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一组织架构表';

-- 5. 部门管理者角色配置
DROP TABLE IF EXISTS `department_manager_roles`;
CREATE TABLE `department_manager_roles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT UNSIGNED NOT NULL COMMENT '部门ID(SysOrg 部门节点ID)',
  `manager_role_codes` JSON NOT NULL COMMENT '部门管理者角色码列表(部门维度配置)',
  `updated_by` BIGINT UNSIGNED DEFAULT NULL,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_department_id` (`department_id`),
  INDEX `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门管理者角色配置表';

-- 6. 角色组表 role_groups
DROP TABLE IF EXISTS `role_groups`;
CREATE TABLE `role_groups` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '角色组名称',
  `description` TEXT COMMENT '角色组描述',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_role_group_name` (`name`),
  INDEX `idx_role_group_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色组表';
-- 7. 角色表 roles
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `name` VARCHAR(100) NOT NULL COMMENT '角色名称',
  `description` TEXT COMMENT '角色描述',
  `role_group_id` INT UNSIGNED DEFAULT NULL COMMENT '角色组ID',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_system` TINYINT DEFAULT 0 COMMENT '是否系统角色 0=否 1=是',
  `is_admin` TINYINT DEFAULT 0 COMMENT '是否管理员角色 0=否 1=是',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_code` (`code`),
  INDEX `idx_role_group_id` (`role_group_id`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 8. 用户角色关系表 user_roles
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` INT UNSIGNED NOT NULL COMMENT '角色ID',
  `project_id` INT UNSIGNED DEFAULT NULL COMMENT '项目ID(NULL表示全局角色)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_role_id` (`role_id`),
  INDEX `idx_project_id` (`project_id`),
  UNIQUE INDEX `uk_user_role_project` (`user_id`, `role_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

-- 9. 权限字典表 sys_permissions
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

-- 10. 角色权限关系表 sys_role_permissions
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

-- 11. 菜单表 sys_menus
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
-- =====================================================
-- 二、项目/迭代/工作流表
-- =====================================================

-- 12. 项目表 projects
DROP TABLE IF EXISTS `projects`;
CREATE TABLE `projects` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '项目名称',
  `description` TEXT COMMENT '项目描述',
  `company_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '归属公司ID',
  `team` VARCHAR(200) DEFAULT NULL COMMENT '归属团队',
  `leader_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '负责人ID',
  `start_date` DATE DEFAULT NULL COMMENT '开始日期',
  `end_date` DATE DEFAULT NULL COMMENT '截止日期',
  `contact_phone` VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `status` VARCHAR(50) DEFAULT 'active' COMMENT '状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_creator_id` (`creator_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`),
  INDEX `idx_company_id` (`company_id`),
  INDEX `idx_leader_id` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 13. 项目成员表 project_members
DROP TABLE IF EXISTS `project_members`;
CREATE TABLE `project_members` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID,0=未绑定项目',
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `role` VARCHAR(50) NOT NULL COMMENT '项目内角色',
  `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_user_id` (`user_id`),
  UNIQUE INDEX `uk_project_user` (`project_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员表';

-- 14. 自定义字段表 custom_fields
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

-- 15. 工作流状态表 workflow_states
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

-- 16. 工作流转换表 workflow_transitions
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

-- 17. 工作流转换记录表 workflow_transition_records
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
-- 18. 工作流版本表 workflow_versions
DROP TABLE IF EXISTS `workflow_versions`;
CREATE TABLE `workflow_versions` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `project_id` INT UNSIGNED NOT NULL COMMENT '项目ID',
  `version` VARCHAR(20) NOT NULL COMMENT '版本号',
  `name` VARCHAR(100) NOT NULL COMMENT '版本名称',
  `definition` JSON NOT NULL COMMENT '工作流定义JSON',
  `runtime_hash` VARCHAR(64) DEFAULT NULL COMMENT '启用时图结构哈希',
  `is_active` TINYINT DEFAULT 0 COMMENT '是否当前启用 0=否 1=是',
  `activation_status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending/approved/active/inactive',
  `activated_at` DATETIME DEFAULT NULL COMMENT '最近一次启用时间',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_is_active` (`is_active`),
  UNIQUE INDEX `uk_project_version` (`project_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流版本表';

-- 19. 工作流节点权限表 workflow_node_permissions
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

-- 20. 工作流节点表 workflow_nodes
DROP TABLE IF EXISTS `workflow_nodes`;
CREATE TABLE `workflow_nodes` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `node_id` VARCHAR(100) NOT NULL COMMENT '节点ID(前端生成的唯一ID)',
  `node_type` VARCHAR(50) NOT NULL COMMENT '节点类型(start/approval/condition/parallel/end)',
  `node_name` VARCHAR(100) NOT NULL COMMENT '节点名称',
  `position_x` INT DEFAULT 0 COMMENT 'X坐标',
  `position_y` INT DEFAULT 0 COMMENT 'Y坐标',
  `assignee_type` VARCHAR(50) DEFAULT NULL COMMENT '处理人类型(SPECIFIED_USER/SPECIFIED_ROLE/SPECIFIED_ROLE_GROUP/SPECIFIED_ORG)',
  `assignee_role_id` INT UNSIGNED DEFAULT NULL COMMENT '角色ID',
  `assignee_role_group_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '角色组ID',
  `assignee_org_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '组织ID',
  `assignee_user_ids` JSON DEFAULT NULL COMMENT '指定用户ID列表',
  `timeout_hours` INT DEFAULT NULL COMMENT '超时小时数',
  `timeout_action` VARCHAR(50) DEFAULT NULL COMMENT '超时后操作(auto_pass/auto_reject/escalate)',
  `properties` JSON DEFAULT NULL COMMENT '节点属性配置',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`),
  INDEX `idx_node_id` (`node_id`),
  INDEX `idx_version_node_assignee` (`workflow_version_id`, `node_id`, `assignee_type`) COMMENT '性能优化:待办/已办查询中的节点权限匹配',
  UNIQUE INDEX `uk_version_node` (`workflow_version_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点表';

-- 21. 工作流连线表 workflow_edges
DROP TABLE IF EXISTS `workflow_edges`;
CREATE TABLE `workflow_edges` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_version_id` INT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `edge_id` VARCHAR(100) NOT NULL COMMENT '连线ID(前端生成的唯一ID)',
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
-- 22. 工作流审核表 workflow_approvals
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

-- 23. 迭代表 iterations
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
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迭代表';

-- 24. 工作流会签记录表 workflow_countersign_records
DROP TABLE IF EXISTS `workflow_countersign_records`;
CREATE TABLE `workflow_countersign_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `node_id` VARCHAR(100) NOT NULL COMMENT '会签节点ID',
  `approver_id` BIGINT UNSIGNED NOT NULL COMMENT '审批人ID',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `rating` TINYINT DEFAULT NULL COMMENT '评分1-5',
  `comment` VARCHAR(1000) DEFAULT NULL COMMENT '审批意见',
  `approved_at` DATETIME DEFAULT NULL COMMENT '审批时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_instance_node` (`instance_id`, `node_id`),
  INDEX `idx_approver_id` (`approver_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流会签记录表';

-- 25. 工作流实例表 workflow_instances
DROP TABLE IF EXISTS `workflow_instances`;
CREATE TABLE `workflow_instances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `workflow_version_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流版本ID',
  `current_node_id` VARCHAR(100) NOT NULL COMMENT '当前节点ID',
  `previous_node_id` VARCHAR(100) DEFAULT NULL COMMENT '上一节点ID(用于回退)',
  `status` VARCHAR(50) DEFAULT 'running' COMMENT 'running/completed/cancelled',
  `lock_version` INT NOT NULL DEFAULT 0 COMMENT '流转乐观锁',
  `parallel_node_id` VARCHAR(100) DEFAULT NULL COMMENT '当前并行网关节点ID',
  `active_parallel_branch_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '当前激活并行分支ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_requirement_id` (`requirement_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`),
  INDEX `idx_requirement_status` (`requirement_id`, `status`) COMMENT '性能优化:通过工作流实例查询需求'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流实例';

-- 26. 工作流流转记录表 workflow_instance_transitions
DROP TABLE IF EXISTS `workflow_instance_transitions`;
CREATE TABLE `workflow_instance_transitions` (
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
  `attachments_json` JSON DEFAULT NULL COMMENT '附件ID列表(关联 file_records.id)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_instance_id` (`instance_id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_requirement_operator` (`requirement_id`, `operator_id`) COMMENT '性能优化:查询我审批过的需求',
  INDEX `idx_instance_to_node` (`instance_id`, `to_node_id`) COMMENT '性能优化:PREV_APPROVER类型的待办匹配',
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流流转记录';
-- 27. 工作流并行分支表 workflow_parallel_branches
DROP TABLE IF EXISTS `workflow_parallel_branches`;
CREATE TABLE `workflow_parallel_branches` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `parallel_node_id` VARCHAR(100) NOT NULL COMMENT '并行网关节点ID',
  `branch_node_id` VARCHAR(100) NOT NULL COMMENT '分支入口节点ID',
  `branch_name` VARCHAR(100) NOT NULL COMMENT '分支名称',
  `current_node_id` VARCHAR(100) DEFAULT NULL COMMENT '分支当前节点ID',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT 'pending/running/completed/skipped',
  `started_at` DATETIME DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_instance_id` (`instance_id`),
  INDEX `idx_parallel_node_id` (`parallel_node_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流并行分支执行记录';

-- 28. 需求待办任务物化表(性能优化)
DROP TABLE IF EXISTS `requirement_pending_tasks`;
CREATE TABLE `requirement_pending_tasks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `user_id` INT UNSIGNED NOT NULL COMMENT '待办用户ID',
  `assignee_type` VARCHAR(50) NOT NULL COMMENT '待办来源类型(SPECIFIED_USER/SPECIFIED_ROLE/SPECIFIED_ROLE_GROUP/SPECIFIED_ORG/PREV_APPROVER/CREATOR)',
  `workflow_instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `current_node_id` VARCHAR(100) NOT NULL COMMENT '当前节点ID',
  `current_node_name` VARCHAR(100) NOT NULL COMMENT '当前节点名称',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '待办创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_requirement_user` (`requirement_id`, `user_id`) COMMENT '同一需求同一用户只有一条待办',
  INDEX `idx_user_updated` (`user_id`, `updated_at` DESC) COMMENT '按用户查询待办列表(核心索引)',
  INDEX `idx_requirement` (`requirement_id`) COMMENT '反查需求的待办人',
  INDEX `idx_workflow_instance` (`workflow_instance_id`) COMMENT '关联工作流实例',
  INDEX `idx_created_at` (`created_at`) COMMENT '按创建时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求待办任务表(物化视图-性能优化)';

-- 29. 需求待办任务历史表(用于审计和统计)
DROP TABLE IF EXISTS `requirement_pending_task_history`;
CREATE TABLE `requirement_pending_task_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `assignee_type` VARCHAR(50) NOT NULL COMMENT '待办来源类型',
  `workflow_instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `current_node_id` VARCHAR(100) NOT NULL COMMENT '节点ID',
  `current_node_name` VARCHAR(100) NOT NULL COMMENT '节点名称',
  `assigned_at` DATETIME NOT NULL COMMENT '分配时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `action` VARCHAR(50) DEFAULT NULL COMMENT '处理动作(approve/reject/cancel)',
  `duration_seconds` BIGINT DEFAULT NULL COMMENT '处理耗时(秒)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_requirement` (`requirement_id`),
  INDEX `idx_user_assigned` (`user_id`, `assigned_at` DESC) COMMENT '用户的历史待办',
  INDEX `idx_completed` (`completed_at`) COMMENT '已完成待办'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办任务历史表';
-- =====================================================
-- 三、需求表
-- =====================================================

-- 30. 需求类型表 requirement_types
DROP TABLE IF EXISTS `requirement_types`;
CREATE TABLE `requirement_types` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL COMMENT '类型编码',
  `name` VARCHAR(100) NOT NULL COMMENT '类型名称',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '颜色',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认',
  `workflow_version_id` INT UNSIGNED DEFAULT NULL COMMENT '绑定的工作流版本ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_workflow_version_id` (`workflow_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求类型表';

-- 31. 优先级表 priorities
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

-- 32. 需求表 requirements
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
  `requirement_no` VARCHAR(64) DEFAULT NULL COMMENT '需求编号',
  `title` VARCHAR(500) NOT NULL COMMENT '标题',
  `description` LONGTEXT COMMENT '描述',
  `type` VARCHAR(50) NOT NULL COMMENT '类型(feature/bug/improvement等)',
  `priority` VARCHAR(50) NOT NULL COMMENT '优先级(critical/high/medium/low)',
  `status` VARCHAR(50) NOT NULL COMMENT '状态',
  `module_id` INT UNSIGNED DEFAULT NULL COMMENT '模块ID',
  `iteration_id` INT UNSIGNED DEFAULT NULL COMMENT '所属迭代ID',
  `workflow_instance_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '工作流实例ID',
  `node_status` VARCHAR(50) DEFAULT 'DRAFT' COMMENT '当前节点状态',
  `is_draft` TINYINT DEFAULT 1 COMMENT '是否草稿 0=否 1=是',
  `last_saved_at` DATETIME DEFAULT NULL COMMENT '最后保存草稿时间(用于区分自动草稿和手动保存草稿)',
  `creator_role_codes` JSON DEFAULT NULL COMMENT '创建人角色码快照(SecurityUtils.getCurrentUserRoles),用于草稿可见性(同部门同角色)',
  `legacy_workflow` TINYINT NOT NULL DEFAULT 0 COMMENT '历史无实例需求标记',
  `start_date` DATE DEFAULT NULL COMMENT '开始日期',
  `estimated_hours` DECIMAL(10, 2) DEFAULT NULL COMMENT '预估工时',
  `actual_hours` DECIMAL(10, 2) DEFAULT NULL COMMENT '实际工时',
  `due_date` DATE DEFAULT NULL COMMENT '截止日期',
  `analysis_completed_at` DATETIME DEFAULT NULL COMMENT '分析完成时间',
  `confirm_at` DATETIME DEFAULT NULL COMMENT '需求确认时间',
  `development_completed_at` DATETIME DEFAULT NULL COMMENT '开发完成时间',
  `cc_user_ids` JSON DEFAULT NULL COMMENT '抄送人ID列表',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `order_num` INT DEFAULT 0 COMMENT '排序号',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  `org_id` INT UNSIGNED DEFAULT NULL COMMENT '归属组织ID',
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
  INDEX `idx_priority` (`priority`),
  INDEX `idx_deleted_draft_updated` (`deleted_at`, `is_draft`, `updated_at` DESC) COMMENT '性能优化:按删除状态+草稿状态+更新时间查询',
  INDEX `idx_deleted_draft_orgid` (`deleted_at`, `is_draft`, `org_id`) COMMENT '性能优化:按删除状态+草稿状态+组织过滤',
  INDEX `idx_workflow_instance` (`workflow_instance_id`) COMMENT '性能优化:通过工作流实例反查需求',
  UNIQUE INDEX `uk_requirement_no` (`requirement_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求表';
-- 33. 需求关注表 requirement_follows
DROP TABLE IF EXISTS `requirement_follows`;
CREATE TABLE `requirement_follows` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `user_id` INT UNSIGNED NOT NULL COMMENT '关注用户ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_requirement_user` (`requirement_id`, `user_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_user_requirement` (`user_id`, `requirement_id`) COMMENT '性能优化:批量查询用户关注状态的覆盖索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求关注表';

-- 34. 文件记录表 file_records
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

-- 35. 需求自定义字段值表 requirement_custom_field_values
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

-- 36. 需求关系表 requirement_relations
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

-- 37. 需求历史表 requirement_history
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

-- 38. 需求评论表 requirement_comments
DROP TABLE IF EXISTS `requirement_comments`;
CREATE TABLE `requirement_comments` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` INT UNSIGNED NOT NULL COMMENT '需求ID',
  `user_id` INT UNSIGNED NOT NULL COMMENT '评论人ID',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求评论表';

-- 39. 需求审批评价表 requirement_approval_evaluations
DROP TABLE IF EXISTS `requirement_approval_evaluations`;
CREATE TABLE `requirement_approval_evaluations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_id` BIGINT UNSIGNED NOT NULL COMMENT '需求ID',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '工作流实例ID',
  `transition_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联流转记录ID',
  `node_id` VARCHAR(100) NOT NULL COMMENT '审批节点ID',
  `node_name` VARCHAR(100) NOT NULL COMMENT '审批节点名称',
  `node_status_code` VARCHAR(50) DEFAULT NULL COMMENT '节点状态码快照',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父审核记录ID(用于补充意见)',
  `is_supplement` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否补充意见 0=否 1=是',
  `evaluator_id` INT UNSIGNED NOT NULL COMMENT '评价人ID',
  `rating` TINYINT DEFAULT NULL COMMENT '评价星级1-5(补充意见场景可为空)',
  `content` VARCHAR(1000) DEFAULT NULL COMMENT '评价意见',
  `attachments` JSON DEFAULT NULL COMMENT '附件列表',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_requirement_id` (`requirement_id`),
  INDEX `idx_instance_id` (`instance_id`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_evaluator_id` (`evaluator_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求审批环节评价';
-- 40. 需求模板表 requirement_templates
DROP TABLE IF EXISTS `requirement_templates`;
CREATE TABLE `requirement_templates` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requirement_type_code` VARCHAR(50) NOT NULL COMMENT '需求类型编码',
  `template_name` VARCHAR(200) NOT NULL COMMENT '模板名称',
  `template_content` JSON NOT NULL COMMENT '模板内容(结构化字段)',
  `is_active` TINYINT DEFAULT 1 COMMENT '是否启用',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否该类型下的默认模板',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `creator_id` INT UNSIGNED NOT NULL COMMENT '创建人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_type_name` (`requirement_type_code`, `template_name`, `deleted_at`),
  INDEX `idx_type_code` (`requirement_type_code`),
  INDEX `idx_is_active` (`is_active`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求模板表';

-- 41. 评审表 reviews
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
  UNIQUE INDEX `uk_requirement_reviewer` (`requirement_id`, `reviewer_id`),
  INDEX `idx_result` (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审表';

-- 42. 通知表 notifications
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

-- 43. 知识库表 knowledge_bases
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

-- 44. 知识库文档表 knowledge_documents
DROP TABLE IF EXISTS `knowledge_documents`;
CREATE TABLE `knowledge_documents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` BIGINT UNSIGNED NOT NULL COMMENT '知识库ID',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联项目ID',
  `file_name` VARCHAR(500) NOT NULL COMMENT '文件名',
  `file_type` VARCHAR(32) DEFAULT NULL COMMENT '文件类型(pdf/txt/md/docx)',
  `file_size` BIGINT UNSIGNED DEFAULT 0 COMMENT '文件大小(字节)',
  `chunk_count` INT UNSIGNED DEFAULT 0 COMMENT '分块数量',
  `status` VARCHAR(50) DEFAULT 'pending' COMMENT '状态(pending/parsed/indexing/indexed/stored/failed)',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  `minio_key` VARCHAR(500) DEFAULT NULL COMMENT 'MinIO存储Key',
  `requirement_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求ID',
  `source_type` VARCHAR(50) DEFAULT NULL COMMENT '来源类型(requirement/knowledge_base)',
  `source_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '来源业务ID',
  `uploader_id` BIGINT UNSIGNED NOT NULL COMMENT '上传人ID',
  `download_count` INT UNSIGNED DEFAULT 0 COMMENT '下载次数',
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
-- 45. 知识库文档分享表 knowledge_document_shares
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

-- 46. 知识库文档分享访问日志表 knowledge_document_share_logs
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

-- 47. 知识库文档分块表 knowledge_chunks
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

-- 48. 节点状态全局字典表 node_statuses
DROP TABLE IF EXISTS `node_statuses`;
CREATE TABLE `node_statuses` (
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

-- 49. 用户列配置表 user_column_configs
DROP TABLE IF EXISTS `user_column_configs`;
CREATE TABLE `user_column_configs` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL,
  `page_key` VARCHAR(64) NOT NULL COMMENT '页面标识',
  `visible_columns` JSON DEFAULT NULL COMMENT '可见列配置',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_page` (`user_id`, `page_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户列配置';

-- 50. LLM接入组配置表 llm_providers
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

-- 51. LLM模型实例表 llm_models
DROP TABLE IF EXISTS `llm_models`;
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
-- =====================================================
-- 初始化数据
-- =====================================================

-- 职位数据
INSERT IGNORE INTO `positions` (`id`, `name`, `code`, `level`, `description`) VALUES
(1, '产品经理', 'PM', 1, '负责产品规划和需求管理'),
(2, '开发工程师', 'DEV', 2, '负责功能开发和技术实现'),
(3, '测试工程师', 'QA', 2, '负责功能测试和质量保证');

-- 用户数据(密码: admin123, BCrypt加密)
INSERT INTO `users` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `avatar`, `status`, `created_at`, `updated_at`, `deleted_at`) VALUES
(1, 'admin', '$2b$12$.SPoAlnnJvD.VajrVmgCdeBTWE/DQ75Ym/P9dGL.3IzT4ewED9QVG', '系统管理员', 'admin@demand.com', NULL, NULL, 'active', NOW(), NOW(), 0);

-- 用户组织关系
INSERT INTO `user_organizations` (`user_id`, `region_id`, `department_id`, `system_role`, `manager_id`, `effective_date`) VALUES
(1, 1, 1, 'admin', NULL, '2026-01-01');

-- 角色数据
INSERT INTO `roles` (`id`, `code`, `name`, `description`, `is_system`, `is_admin`) VALUES
(1, 'SUPER_ADMIN', '超级管理员', '系统最高权限,可管理所有配置', 1, 1),
(2, 'REGION_ADMIN', '区域管理员', '管理指定区域的部门和人员', 1, 0),
(3, 'DEPT_ADMIN', '部门管理员', '管理指定部门的人员', 1, 0),
(4, 'PRODUCT_MANAGER', '产品经理', '负责需求评审和验收', 0, 0),
(5, 'PROJECT_MANAGER', '项目经理', '负责项目管理和迭代规划', 0, 0),
(6, 'DEVELOPER', '开发人员', '负责需求开发', 0, 0),
(7, 'TESTER', '测试人员', '负责需求测试', 0, 0),
(8, 'REVIEWER', '评审人', '负责需求评审', 0, 0);

-- 给admin用户分配超级管理员角色
INSERT INTO `user_roles` (`user_id`, `role_id`, `project_id`) VALUES
(1, 1, NULL);

-- 初始化统一组织数据
INSERT INTO `sys_org` (`id`, `name`, `parent_id`, `org_type`, `code`, `description`, `sort_order`, `path`, `level`, `created_at`, `updated_at`, `deleted_at`) VALUES
(1, '东莞市', NULL, 'region', 'HD', '东莞市区域', 1, '/1/', 0, NOW(), NOW(), 0),
(5, '开普云科技有限公司', 1, 'company', 'KP01', '开普云科技有限公司', 1, '/1/5/', 1, NOW(), NOW(), 0),
(7, '市民服务中心', 5, 'department', 'SM01', '市民服务中心', 1, '/1/5/7/', 2, NOW(), NOW(), 0);

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

-- 工作流状态(演示项目,合并为一次插入)
INSERT INTO `workflow_states` (`project_id`, `name`, `color`, `is_final`, `sort_order`) VALUES
(1, '新建', '#409EFF', 0, 1),
(1, '待评审', '#E6A23C', 0, 2),
(1, '评审中', '#E6A23C', 0, 3),
(1, '已通过', '#67C23A', 0, 4),
(1, '开发中', '#409EFF', 0, 5),
(1, '测试中', '#E6A23C', 0, 6),
(1, '已上线', '#67C23A', 0, 7),
(1, '已验收', '#909399', 1, 8),
(1, '已取消', '#909399', 1, 9),
(1, '已拒绝', '#909399', 1, 10),
(1, '打回', '#E6A23C', 0, 11),
(1, '测试不通过', '#F56C6C', 0, 12),
(1, '验收不通过', '#F56C6C', 0, 13);

-- 工作流转换(演示项目,合并多次插入为一次)
-- state 映射: 1=新建 2=待评审 3=评审中 4=已通过 5=开发中 6=测试中 7=已上线 8=已验收 9=已取消 10=已拒绝 11=打回 12=测试不通过 13=验收不通过
INSERT INTO `workflow_transitions` (`project_id`, `from_state_id`, `to_state_id`, `allowed_roles`, `required_fields`, `conditions`) VALUES
-- 新建 -> 待评审(创建人,产品经理) / 新建 -> 已取消(创建人)
(1, 1, 2, '['创建人', '产品经理']', '[]', '{}'),
(1, 1, 9, '['创建人']', '[]', '{}'),
-- 待评审 -> 评审中(产品经理) / 待评审 -> 已取消(产品经理)
(1, 2, 3, '['产品经理']', '[]', '{}'),
(1, 2, 9, '['产品经理']', '[]', '{}'),
-- 评审中 -> 已通过(评审人,required:评审意见) / 评审中 -> 已拒绝(评审人,required:评审意见)
(1, 3, 4, '['评审人']', '['评审意见']', '{}'),
(1, 3, 10, '['评审人']', '['评审意见']', '{}'),
-- 已通过 -> 开发中(项目经理,required:所属迭代)
(1, 4, 5, '['项目经理']', '['所属迭代']', '{}'),
-- 开发中 -> 测试中(开发人员,required:开发说明) / 开发中 -> 打回(开发人员)
(1, 5, 6, '['开发人员']', '['开发说明']', '{}'),
(1, 5, 11, '['开发人员']', '[]', '{}'),
-- 测试中 -> 已上线(测试人员,required:测试报告) / 测试中 -> 测试不通过(测试人员)
(1, 6, 7, '['测试人员']', '['测试报告']', '{}'),
(1, 6, 12, '['测试人员']', '[]', '{}'),
-- 已上线 -> 已验收(产品经理,required:验收结论) / 已上线 -> 验收不通过(产品经理)
(1, 7, 8, '['产品经理']', '['验收结论']', '{}'),
(1, 7, 13, '['产品经理']', '[]', '{}');

-- 节点状态全局字典
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
-- 权限数据(合并所有历史INSERT为一次INSERT IGNORE)
INSERT IGNORE INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`) VALUES
(1,  'menu:system-config',          '系统配置菜单',       'MENU',   '系统配置一级菜单入口', 1),
(2,  'menu:settings:project',       '项目管理菜单',       'MENU',   '系统设置-项目管理', 1),
(3,  'menu:settings:user',          '用户管理菜单',       'MENU',   '系统设置-用户管理', 1),
(5,  'menu:settings:requirement',   '需求配置菜单',       'MENU',   '系统设置-需求配置', 1),
(6,  'menu:settings:workflow',      '工作流配置菜单',     'MENU',   '系统设置-工作流配置', 1),
(7,  'menu:settings:role',          '角色管理菜单',       'MENU',   '系统设置-角色管理', 1),
(8,  'menu:menu-management',        '菜单管理菜单',       'MENU',   '系统设置-菜单管理', 1),
(9,  'menu:rag',                    'RAG文档中心菜单',    'MENU',   'RAG文档中心入口', 1),
(10, 'button:menu:create',          '新增菜单按钮',       'BUTTON', '菜单管理-新增', 1),
(11, 'button:menu:update',          '编辑菜单按钮',       'BUTTON', '菜单管理-编辑', 1),
(12, 'button:menu:delete',          '删除菜单按钮',       'BUTTON', '菜单管理-删除', 1),
(13, 'button:menu:grant',           '角色授权按钮',       'BUTTON', '菜单管理-角色授权', 1),
(14, 'button:user:create',          '新增用户按钮',       'BUTTON', '用户管理-新增', 1),
(15, 'button:user:update',          '编辑用户按钮',       'BUTTON', '用户管理-编辑', 1),
(16, 'button:user:delete',          '删除用户按钮',       'BUTTON', '用户管理-删除', 1),
(17, 'button:role:create',          '新增角色按钮',       'BUTTON', '角色管理-新增', 1),
(18, 'button:role:update',          '编辑角色按钮',       'BUTTON', '角色管理-编辑', 1),
(23, 'button:role:delete',          '删除角色按钮',       'BUTTON', '角色管理-删除', 1),
(24, 'button:role:grant',           '角色授权按钮',       'BUTTON', '角色管理-授权', 1),
(28, 'button:workflow:config',      '工作流配置按钮',     'BUTTON', '工作流配置操作', 1),
(29, 'button:rag:upload',           'RAG文档上传按钮',    'BUTTON', 'RAG-上传文档', 1),
(30, 'button:rag:search',           'RAG文档搜索按钮',    'BUTTON', 'RAG-智能搜索', 1),
(31, 'menu:settings:llm',           '模型配置菜单',       'MENU',   '系统设置-模型配置', 1),
(32, 'button:llm-provider:create',  '新建模型提供商',     'BUTTON', 'LLM配置-新建提供商', 1),
(33, 'button:llm-provider:update',  '编辑模型提供商',     'BUTTON', 'LLM配置-编辑提供商', 1),
(34, 'button:llm-provider:delete',  '删除模型提供商',     'BUTTON', 'LLM配置-删除提供商', 1),
(40, 'button:requirement:create',   '新建需求',           'BUTTON', '需求管理-新建', 1),
(41, 'button:requirement:update',   '编辑需求',           'BUTTON', '需求管理-编辑', 1),
(42, 'button:requirement:delete',   '删除需求',           'BUTTON', '需求管理-删除', 1),
(43, 'button:requirement:export',   '导出需求',           'BUTTON', '需求管理-导出Excel', 1),
(44, 'button:requirement:submit',   '提交需求',           'BUTTON', '需求管理-提交/流转', 1),
(45, 'button:requirement:split',    '拆分需求',           'BUTTON', '需求管理-拆分子需求', 1),
(46, 'button:requirement:comment',  '评论需求',           'BUTTON', '需求管理-评论', 1),
(47, 'button:requirement:rollback', '回退需求',           'BUTTON', '需求管理-回退', 1),
(48, 'button:requirement:cancel',   '撤销需求',           'BUTTON', '需求管理-撤销', 1),
(50, 'button:project:create',       '新建项目',           'BUTTON', '项目管理-新建', 1),
(51, 'button:project:update',       '编辑项目',           'BUTTON', '项目管理-编辑', 1),
(52, 'button:project:delete',       '删除项目',           'BUTTON', '项目管理-删除', 1),
(53, 'button:project:import',       '导入项目',           'BUTTON', '项目管理-导入', 1),
(54, 'button:project:export',       '导出项目',           'BUTTON', '项目管理-导出', 1),
(55, 'button:iteration:create',     '新建迭代',           'BUTTON', '迭代管理-新建', 1),
(56, 'button:iteration:update',     '编辑迭代',           'BUTTON', '迭代管理-编辑', 1),
(57, 'button:iteration:delete',     '删除迭代',           'BUTTON', '迭代管理-删除', 1),
(58, 'button:review:create',        '发起评审',           'BUTTON', '评审管理-发起', 1),
(59, 'button:review:update',        '编辑评审',           'BUTTON', '评审管理-编辑', 1),
(60, 'button:review:submit',        '提交评审',           'BUTTON', '评审管理-提交', 1),
(61, 'button:knowledge:create',     '新建知识库',         'BUTTON', '知识库-新建', 1),
(62, 'button:knowledge:update',     '编辑知识库',         'BUTTON', '知识库-编辑', 1),
(63, 'button:knowledge:delete',     '删除知识库',         'BUTTON', '知识库-删除', 1),
(64, 'button:knowledge:upload',     '上传文档',           'BUTTON', '知识库-上传文档', 1),
(65, 'button:knowledge:download',   '下载文档',           'BUTTON', '知识库-下载文档', 1),
(66, 'button:knowledge:share',      '分享文档',           'BUTTON', '知识库-分享', 1),
(67, 'button:requirement-config:create', '新增配置项',    'BUTTON', '需求配置-新增', 1),
(68, 'button:requirement-config:update', '编辑配置项',    'BUTTON', '需求配置-编辑', 1),
(69, 'button:requirement-config:delete', '删除配置项',    'BUTTON', '需求配置-删除', 1),
(70, 'button:workflow:create',      '新建工作流',         'BUTTON', '工作流配置-新建', 1),
(71, 'button:workflow:update',      '编辑工作流',         'BUTTON', '工作流配置-编辑', 1),
(72, 'button:workflow:delete',      '删除工作流',         'BUTTON', '工作流配置-删除', 1),
(73, 'button:workflow:activate',    '启用/停用工作流',    'BUTTON', '工作流配置-启用停用', 1),
(74, 'button:workflow:approve',     '审批工作流',         'BUTTON', '工作流配置-审批', 1),
(78, 'button:llm-provider:test',    '测试模型提供商',     'BUTTON', 'LLM配置-测试提供商', 1),
(79, 'menu:requirement:view:all',   '全部需求视图',       'MENU',   '需求管理-全部需求', 1),
(80, 'menu:requirement:view:pending','我的待办视图',      'MENU',   '需求管理-我的待办', 1),
(81, 'menu:requirement:view:done',  '我的已办视图',       'MENU',   '需求管理-我的已办', 1),
(82, 'menu:requirement:view:draft', '我的草稿视图',       'MENU',   '需求管理-我的草稿', 1),
(83, 'button:requirement:batch-delete', '批量删除需求',   'BUTTON', '需求管理-批量删除', 1),
(93, 'menu:requirement:view:follow','我的关注',           'MENU',   '需求管理-我的关注', 1),
(94, 'button:requirement-template:create', '新建需求模板','BUTTON', '需求模板-新建', 1),
(95, 'button:requirement-template:update', '编辑需求模板','BUTTON', '需求模板-编辑', 1),
(96, 'button:requirement-template:delete', '删除需求模板','BUTTON', '需求模板-删除', 1),
(97, 'button:requirement-template:toggle', '启停需求模板','BUTTON', '需求模板-启停', 1),
(100, 'button:knowledge:migrate',   '迁移知识库文档',     'BUTTON', '知识库-迁移文档', 1),
(101, 'button:user:batch-delete',   '批量启停/删除用户',  'BUTTON', '用户管理-批量启停/删除', 1),
(102, 'button:user:invite',         '邀请成员',           'BUTTON', '用户管理-邀请成员', 1),
(103, 'button:user:export',         '导出花名册',         'BUTTON', '用户管理-导出花名册', 1),
(104, 'button:user:import',         '导入花名册',         'BUTTON', '用户管理-导入花名册', 1),
(105, 'button:role:import',         '批量导入角色',       'BUTTON', '角色管理-批量导入', 1),
(106, 'button:role:export',         '导出角色',           'BUTTON', '角色管理-导出', 1),
(107, 'button:notification:manage', '通知管理',           'BUTTON', '通知中心-全部已读', 1),
(108, 'button:project:template',    '下载导入模板',       'BUTTON', '项目管理-下载模板', 1),
(112, 'button:org:create',          '新增组织/部门',      'BUTTON', '组织管理-新增', 1),
(113, 'button:org:update',          '编辑组织/部门',      'BUTTON', '组织管理-编辑', 1),
(114, 'button:org:delete',          '删除组织/部门',      'BUTTON', '组织管理-删除', 1),
(115, 'button:org:batch-create',    '批量创建部门',       'BUTTON', '组织管理-批量创建', 1),
(116, 'button:requirement:countersign-approve', '会签通过','BUTTON','需求-会签通过', 1),
(117, 'button:requirement:countersign-reject',  '会签驳回','BUTTON','需求-会签驳回', 1),
(118, 'button:requirement:draft',   '保存需求草稿',       'BUTTON', '需求-保存草稿', 1),
(119, 'button:review:view',         '查看评审详情',       'BUTTON', '评审-查看详情', 1),
(120, 'button:iteration:view',      '查看迭代/燃尽图',    'BUTTON', '迭代-查看详情', 1),
(121, 'button:relation:create',     '创建需求关联',       'BUTTON', '需求-创建关联', 1),
(122, 'button:relation:delete',     '删除需求关联',       'BUTTON', '需求-删除关联', 1);
-- 菜单数据(合并所有历史INSERT/UPDATE为一次)
INSERT IGNORE INTO `sys_menus` (`id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`) VALUES
-- 一级目录
(1,  0, '仪表盘',       'MENU',      '/dashboard',              'Dashboard',          'views/dashboard/index.vue',                  'Odometer',     1, NULL,                          1, 1, 0),
(2,  0, '需求管理',     'MENU',      '/requirements',           'Requirements',       'views/requirements/index.vue',                'Document',     2, NULL,                          1, 1, 0),
(3,  0, '迭代管理',     'MENU',      '/iterations',             'Iterations',         'views/iterations/index.vue',                  'Calendar',     3, NULL,                          1, 1, 0),
(4,  0, '评审管理',     'MENU',      '/reviews',                'Reviews',            'views/reviews/index.vue',                     'ChatDotRound', 4, NULL,                          1, 1, 0),
(5,  0, '统计报表',     'MENU',      '/statistics',             'Statistics',         'views/statistics/index.vue',                  'TrendCharts',  5, NULL,                          1, 1, 0),
(6,  0, 'RAG文档中心',  'MENU',      '/settings/rag',           'RagCenter',          'views/rag/index.vue',                          'Files',        7, 'menu:rag',                  1, 1, 0),
(7,  0, '系统配置',     'DIRECTORY', NULL,                        NULL,                  NULL,                                            'Setting',      6, 'menu:system-config',        1, 1, 0),
(8,  0, '知识库管理',   'MENU',      '/settings/knowledge',     'KnowledgeBases',     'views/knowledge/index.vue',                   'Collection',   8, 'menu:rag',                  1, 1, 0),
(10, 0, '项目管理',     'MENU',      '/settings/projects',      'SettingsProjects',   'views/settings/projects.vue',                 'Folder',       9, 'menu:settings:project',     1, 1, 0),
(11, 7, '用户管理',     'MENU',      '/settings/users',         'SettingsUsers',      'views/settings/users.vue',                    'User',         1, 'menu:settings:user',        1, 1, 0),
(13, 7, '需求配置',     'MENU',      '/settings/requirements',  'SettingsRequirements','views/settings/requirements.vue',             'Setting',      4, 'menu:settings:requirement', 1, 1, 0),
(14, 7, '工作流配置',   'MENU',      '/system/workflow-config', 'WorkflowConfig',     'views/system/workflow-config/index.vue',       'Share',        5, 'menu:settings:workflow',    1, 1, 0),
(15, 7, '菜单管理',     'MENU',      '/settings/menus',         'MenuManagement',     'views/settings/menus.vue',                    'Menu',         6, 'menu:menu-management',      1, 1, 0),
(17, 7, '角色管理',     'MENU',      '/settings/roles',         'RoleManage',         'views/settings/roles.vue',                    'UserFilled',   2, 'menu:settings:role',        1, 1, 0),
(16, 0, '模型配置',     'MENU',      '/settings/llm',           'LlmConfig',          'views/settings/llm.vue',                       'MagicStick',  10, 'menu:settings:llm',         1, 1, 0);
(20, 15, '新增菜单',     'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:menu:create',  1, 1, 0),
(21, 15, '编辑菜单',     'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:menu:update',  1, 1, 0),
(22, 15, '删除菜单',     'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:menu:delete',  1, 1, 0),
(23, 15, '角色授权',     'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:menu:grant',   1, 1, 0),
-- 角色管理下的按钮
(24, 17, '新增角色',     'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:role:create',  1, 1, 0),
(25, 17, '编辑角色',     'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:role:update',  1, 1, 0),
(26, 17, '删除角色',     'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:role:delete',  1, 1, 0),
(27, 17, '角色授权',     'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:role:grant',   1, 1, 0),
-- 用户管理下的按钮
(30, 11, '新增用户',     'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:user:create',  1, 1, 0),
(31, 11, '编辑用户',     'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:user:update',  1, 1, 0),
(32, 11, '删除用户',     'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:user:delete',  1, 1, 0),
-- 需求管理下的按钮
(40, 2, '新建需求',      'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:requirement:create',  1, 1, 0),
(41, 2, '编辑需求',      'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:requirement:update',  1, 1, 0),
(42, 2, '删除需求',      'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:requirement:delete',  1, 1, 0),
(43, 2, '导出需求',      'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:requirement:export',  1, 1, 0),
(44, 2, '提交需求',      'BUTTON',    NULL, NULL, NULL, NULL, 5, 'button:requirement:submit',  1, 1, 0),
(45, 2, '拆分需求',      'BUTTON',    NULL, NULL, NULL, NULL, 6, 'button:requirement:split',   1, 1, 0),
(46, 2, '评论需求',      'BUTTON',    NULL, NULL, NULL, NULL, 7, 'button:requirement:comment', 1, 1, 0),
(47, 2, '回退需求',      'BUTTON',    NULL, NULL, NULL, NULL, 8, 'button:requirement:rollback',1, 1, 0),
(48, 2, '撤销需求',      'BUTTON',    NULL, NULL, NULL, NULL, 9, 'button:requirement:cancel',  1, 1, 0),
(79, 2, '全部需求视图',  'BUTTON',    NULL, NULL, NULL, NULL,10, 'menu:requirement:view:all',  1, 1, 0),
(80, 2, '我的待办视图',  'BUTTON',    NULL, NULL, NULL, NULL,11, 'menu:requirement:view:pending', 1, 1, 0),
(81, 2, '我的已办视图',  'BUTTON',    NULL, NULL, NULL, NULL,12, 'menu:requirement:view:done', 1, 1, 0),
(82, 2, '我的草稿视图',  'BUTTON',    NULL, NULL, NULL, NULL,13, 'menu:requirement:view:draft', 1, 1, 0),
(83, 2, '批量删除需求',  'BUTTON',    NULL, NULL, NULL, NULL,14, 'button:requirement:batch-delete', 1, 1, 0),
(84, 2, '我的关注',      'BUTTON',    NULL, NULL, NULL, NULL,15, 'menu:requirement:view:follow', 1, 1, 0);
(50, 10, '新建项目',     'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:project:create',  1, 1, 0),
(51, 10, '编辑项目',     'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:project:update',  1, 1, 0),
(52, 10, '删除项目',     'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:project:delete',  1, 1, 0),
(90, 10, '下载导入模板', 'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:project:template', 1, 1, 0),
-- 迭代管理下的按钮
(55, 3, '新建迭代',      'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:iteration:create', 1, 1, 0),
(56, 3, '编辑迭代',      'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:iteration:update', 1, 1, 0),
(57, 3, '删除迭代',      'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:iteration:delete', 1, 1, 0),
-- 评审管理下的按钮
(58, 4, '发起评审',      'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:review:create',  1, 1, 0),
(59, 4, '编辑评审',      'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:review:update',  1, 1, 0),
-- 知识库下的按钮
(61, 8, '新建知识库',    'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:knowledge:create',  1, 1, 0),
(62, 8, '编辑知识库',    'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:knowledge:update',  1, 1, 0),
(63, 8, '删除知识库',    'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:knowledge:delete',  1, 1, 0),
(64, 8, '上传文档',      'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:knowledge:upload',  1, 1, 0),
(65, 8, '下载文档',      'BUTTON',    NULL, NULL, NULL, NULL, 5, 'button:knowledge:download', 1, 1, 0),
(66, 8, '分享文档',      'BUTTON',    NULL, NULL, NULL, NULL, 6, 'button:knowledge:share',   1, 1, 0),
(89, 8, '迁移知识库文档','BUTTON',    NULL, NULL, NULL, NULL, 7, 'button:knowledge:migrate', 1, 1, 0),
-- 需求配置下的按钮
(67, 13, '新增配置项',   'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:requirement-config:create', 1, 1, 0),
(68, 13, '编辑配置项',   'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:requirement-config:update', 1, 1, 0),
(69, 13, '删除配置项',   'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:requirement-config:delete', 1, 1, 0),
-- 需求模板下的按钮
(85, 13, '新建需求模板', 'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:requirement-template:create', 1, 1, 0),
(86, 13, '编辑需求模板', 'BUTTON',    NULL, NULL, NULL, NULL, 5, 'button:requirement-template:update', 1, 1, 0),
(87, 13, '删除需求模板', 'BUTTON',    NULL, NULL, NULL, NULL, 6, 'button:requirement-template:delete', 1, 1, 0),
(88, 13, '启停需求模板', 'BUTTON',    NULL, NULL, NULL, NULL, 7, 'button:requirement-template:toggle', 1, 1, 0),
-- 工作流配置下的按钮
(70, 14, '新建工作流',   'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:workflow:create',  1, 1, 0),
(71, 14, '编辑工作流',   'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:workflow:update',  1, 1, 0),
(72, 14, '删除工作流',   'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:workflow:delete',  1, 1, 0),
(73, 14, '启用停用',     'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:workflow:activate', 1, 1, 0),
(74, 14, '审批',         'BUTTON',    NULL, NULL, NULL, NULL, 5, 'button:workflow:approve',  1, 1, 0),
-- LLM配置下的按钮
(75, 16, '新建提供商',   'BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:llm-provider:create', 1, 1, 0),
(76, 16, '编辑提供商',   'BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:llm-provider:update', 1, 1, 0),
(77, 16, '删除提供商',   'BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:llm-provider:delete', 1, 1, 0),
(78, 16, '测试提供商',   'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:llm-provider:test',  1, 1, 0),
-- 组织管理(用户管理下)
(95, 11, '新增组织/部门','BUTTON',    NULL, NULL, NULL, NULL, 1, 'button:org:create',  1, 1, 0),
(96, 11, '编辑组织/部门','BUTTON',    NULL, NULL, NULL, NULL, 2, 'button:org:update',  1, 1, 0),
(97, 11, '删除组织/部门','BUTTON',    NULL, NULL, NULL, NULL, 3, 'button:org:delete',  1, 1, 0),
(98, 11, '批量创建部门', 'BUTTON',    NULL, NULL, NULL, NULL, 4, 'button:org:batch-create', 1, 1, 0);
-- 角色权限数据(合并所有历史INSERT为一次INSERT IGNORE)
-- SUPER_ADMIN(id=1) 获得全部权限
INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`, `granted_by`) VALUES
(1, 1, 1), (1, 2, 1), (1, 3, 1), (1, 5, 1), (1, 6, 1), (1, 7, 1), (1, 8, 1), (1, 9, 1),
(1, 10, 1), (1, 11, 1), (1, 12, 1), (1, 13, 1), (1, 14, 1), (1, 15, 1), (1, 16, 1), (1, 17, 1), (1, 18, 1),
(1, 23, 1), (1, 24, 1), (1, 28, 1), (1, 29, 1), (1, 30, 1),
(1, 31, 1), (1, 32, 1), (1, 33, 1), (1, 34, 1), (1, 78, 1),
(1, 40, 1), (1, 41, 1), (1, 42, 1), (1, 43, 1), (1, 44, 1), (1, 45, 1), (1, 46, 1), (1, 47, 1), (1, 48, 1),
(1, 50, 1), (1, 51, 1), (1, 52, 1), (1, 53, 1), (1, 54, 1), (1, 90, 1),
(1, 55, 1), (1, 56, 1), (1, 57, 1),
(1, 58, 1), (1, 59, 1), (1, 60, 1),
(1, 61, 1), (1, 62, 1), (1, 63, 1), (1, 64, 1), (1, 65, 1), (1, 66, 1), (1, 89, 1),
(1, 67, 1), (1, 68, 1), (1, 69, 1),
(1, 70, 1), (1, 71, 1), (1, 72, 1), (1, 73, 1), (1, 74, 1),
(1, 79, 1), (1, 80, 1), (1, 81, 1), (1, 82, 1), (1, 83, 1), (1, 93, 1),
(1, 94, 1), (1, 95, 1), (1, 96, 1), (1, 97, 1),
(1, 100, 1), (1, 101, 1), (1, 102, 1), (1, 103, 1), (1, 104, 1),
(1, 105, 1), (1, 106, 1), (1, 107, 1), (1, 108, 1),
(1, 112, 1), (1, 113, 1), (1, 114, 1), (1, 115, 1),
(1, 116, 1), (1, 117, 1), (1, 118, 1),
(1, 119, 1), (1, 120, 1),
(1, 121, 1), (1, 122, 1);

-- 业务角色授权需求管理视图权限,
-- 产品经理: 全部需求 + 我的草稿 + 我的关注 + 新建需求 + 导出 + 批量删除
(4, 79), (4, 82), (4, 93), (4, 40), (4, 43), (4, 83),
-- 项目经理: 全部需求 + 我的待办 + 我的已办 + 我的草稿 + 我的关注 + 新建需求 + 导出 + 批量删除
(5, 79), (5, 80), (5, 81), (5, 82), (5, 93), (5, 40), (5, 43), (5, 83),
-- 开发人员: 我的待办 + 我的已办 + 我的关注 + 导出
(6, 80), (6, 81), (6, 93), (6, 43),
-- 测试人员: 我的待办 + 我的已办 + 我的关注 + 导出
(7, 80), (7, 81), (7, 93), (7, 43),
-- 评审人: 全部需求 + 我的待办 + 我的已办 + 我的关注 + 导出
(8, 79), (8, 80), (8, 81), (8, 93), (8, 43);
-- =====================================================
-- 性能优化: 初始化待办任务物化表数据(已合并到init.sql, 仅在已有工作流实例时执行)
-- =====================================================
-- 说明: 根据现有工作流状态生成所有待办任务
-- 首次运行可能需要 30-60 秒(取决于数据量)
-- 在全新初始化的数据库上,无数据,不会插入任何行

INSERT IGNORE INTO requirement_pending_tasks (
  requirement_id,
  user_id,
  assignee_type,
  workflow_instance_id,
  current_node_id,
  current_node_name,
  created_at,
  updated_at
)
SELECT DISTINCT
  r.id AS requirement_id,
  u.user_id AS user_id,
  wn.assignee_type,
  wi.id AS workflow_instance_id,
  wi.current_node_id,
  wn.node_name AS current_node_name,
  wi.updated_at AS created_at,
  wi.updated_at AS updated_at
FROM requirements r
JOIN workflow_instances wi ON wi.requirement_id = r.id
JOIN workflow_nodes wn ON wn.workflow_version_id = wi.workflow_version_id
                       AND wn.node_id = wi.current_node_id
CROSS JOIN (
  SELECT DISTINCT
    wi2.id AS instance_id,
    CASE
      WHEN wn2.assignee_type = 'SPECIFIED_USER' THEN user_ids.user_id
      WHEN wn2.assignee_type = 'SPECIFIED_ROLE' THEN ru.user_id
      WHEN wn2.assignee_type = 'SPECIFIED_ROLE_GROUP' THEN rgu.user_id
      WHEN wn2.assignee_type = 'SPECIFIED_ORG' THEN ou.user_id
      WHEN wn2.assignee_type = 'CREATOR' THEN r2.creator_id
      WHEN wn2.assignee_type = 'PREV_APPROVER' THEN prev_op.operator_id
    END AS user_id
  FROM workflow_instances wi2
  JOIN workflow_nodes wn2 ON wn2.workflow_version_id = wi2.workflow_version_id
                          AND wn2.node_id = wi2.current_node_id
  JOIN requirements r2 ON r2.id = wi2.requirement_id
  LEFT JOIN JSON_TABLE(
    wn2.assignee_user_ids,
    '$[*]' COLUMNS (user_id INT PATH '$')
  ) AS user_ids ON wn2.assignee_type = 'SPECIFIED_USER'
  LEFT JOIN role_user ru ON wn2.assignee_type = 'SPECIFIED_ROLE'
                         AND ru.role_id = wn2.assignee_role_id
  LEFT JOIN roles rg ON wn2.assignee_type = 'SPECIFIED_ROLE_GROUP'
                     AND rg.role_group_id = wn2.assignee_role_group_id
                     AND rg.deleted_at = 0
  LEFT JOIN role_user rgu ON rgu.role_id = rg.id
  LEFT JOIN user_organizations ou ON wn2.assignee_type = 'SPECIFIED_ORG'
                                   AND ou.org_id = wn2.assignee_org_id
  LEFT JOIN (
    SELECT
      wit.instance_id,
      wit.operator_id,
      wit.to_node_id,
      ROW_NUMBER() OVER (PARTITION BY wit.instance_id, wit.to_node_id ORDER BY wit.id DESC) AS rn
    FROM workflow_instance_transitions wit
  ) AS prev_op ON wn2.assignee_type = 'PREV_APPROVER'
               AND prev_op.instance_id = wi2.id
               AND prev_op.to_node_id = wi2.current_node_id
               AND prev_op.rn = 1
  WHERE wi2.status = 'running'
) AS u ON u.instance_id = wi.id
WHERE r.deleted_at = 0
  AND r.is_draft = 0
  AND wi.status = 'running'
  AND wn.assignee_type IS NOT NULL
  AND wn.assignee_type != ''
  AND u.user_id IS NOT NULL;

SET FOREIGN_KEY_CHECKS = 1;
