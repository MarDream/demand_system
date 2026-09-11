-- =====================================================
-- 代码管理系统 - 数据库契约文件 v1.0 (2026-08-09)
-- 用途: 前后端并行开发的共同接口契约
-- 执行方式: docker exec -i mysql mysql -uroot -padmin123 demand_system < 本文件
-- =====================================================

-- =====================================================
-- 1. Git 平台配置表
-- =====================================================
DROP TABLE IF EXISTS `git_platforms`;
CREATE TABLE `git_platforms` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '平台名称',
  `platform_type` ENUM('GITLAB','GITHUB','GITEE','GITEA') NOT NULL COMMENT '平台类型',
  `base_url` VARCHAR(500) NOT NULL COMMENT 'API基础地址',
  `auth_type` ENUM('TOKEN','SSH_KEY','PASSWORD') NOT NULL DEFAULT 'TOKEN',
  `credential` TEXT NOT NULL COMMENT '加密凭据',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认平台',
  `status` ENUM('CONNECTED','DISCONNECTED','EXPIRED') DEFAULT 'DISCONNECTED',
  `last_checked_at` DATETIME DEFAULT NULL COMMENT '最近连接测试时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_platform_type` (`platform_type`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Git平台配置';

-- =====================================================
-- 2. 代码仓库表
-- =====================================================
DROP TABLE IF EXISTS `repositories`;
CREATE TABLE `repositories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT UNSIGNED NOT NULL COMMENT '所属Git平台ID',
  `project_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '绑定项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '仓库名',
  `full_path` VARCHAR(500) NOT NULL COMMENT '完整路径(含namespace)',
  `description` TEXT DEFAULT NULL,
  `default_branch` VARCHAR(100) DEFAULT 'main',
  `clone_url_ssh` VARCHAR(500) DEFAULT NULL,
  `clone_url_https` VARCHAR(500) DEFAULT NULL,
  `remote_id` VARCHAR(100) DEFAULT NULL COMMENT '远端仓库ID',
  `status` ENUM('ACTIVE','ARCHIVED','DELETED') DEFAULT 'ACTIVE',
  `created_by` INT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_platform` (`platform_id`),
  INDEX `idx_project` (`project_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码仓库';

-- =====================================================
-- 3. 分支保护规则表 (核心)
-- =====================================================
DROP TABLE IF EXISTS `branch_protection_rules`;
CREATE TABLE `branch_protection_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `repo_id` BIGINT UNSIGNED NOT NULL COMMENT '所属仓库ID',
  `rule_set_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属规则集ID',
  `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
  `branch_pattern` VARCHAR(200) NOT NULL COMMENT '分支匹配模式(fnmatch)',
  `priority` INT DEFAULT 0 COMMENT '优先级(越大越优先)',

  -- 推送控制
  `forbid_push` TINYINT DEFAULT 1 COMMENT '禁止直接推送',
  `forbid_force_push` TINYINT DEFAULT 1 COMMENT '禁止强制推送',
  `forbid_delete` TINYINT DEFAULT 1 COMMENT '禁止删除分支',
  `require_mr` TINYINT DEFAULT 1 COMMENT '要求MR/PR',

  -- 合并审批
  `min_approvals` INT DEFAULT 1 COMMENT '最少审批人数',
  `dismiss_stale_approvals` TINYINT DEFAULT 1 COMMENT '驳回过期审批',
  `block_self_approve` TINYINT DEFAULT 1 COMMENT '禁止作者自批',
  `require_codeowner_approval` TINYINT DEFAULT 0 COMMENT 'CODEOWNERS审批',
  `require_thread_resolved` TINYINT DEFAULT 0 COMMENT '讨论串全部解决',

  -- 状态检查
  `require_ci_pass` TINYINT DEFAULT 0 COMMENT '要求CI通过',
  `require_up_to_date` TINYINT DEFAULT 0 COMMENT '分支必须同步',
  `ci_contexts` VARCHAR(500) DEFAULT NULL COMMENT 'CI检查项(逗号分隔)',

  -- 白名单
  `whitelist_users` TEXT DEFAULT NULL COMMENT '白名单用户ID(JSON数组)',
  `whitelist_roles` TEXT DEFAULT NULL COMMENT '白名单角色(JSON数组)',

  `enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
  `created_by` INT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_repo` (`repo_id`),
  INDEX `idx_rule_set` (`rule_set_id`),
  INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分支保护规则';

-- =====================================================
-- 4. 保护规则集表 (跨仓库复用)
-- =====================================================
DROP TABLE IF EXISTS `protection_rule_sets`;
CREATE TABLE `protection_rule_sets` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '规则集名称',
  `description` TEXT DEFAULT NULL,
  `created_by` INT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保护规则集';

-- =====================================================
-- 5. 合并请求表
-- =====================================================
DROP TABLE IF EXISTS `merge_requests`;
CREATE TABLE `merge_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `repo_id` BIGINT UNSIGNED NOT NULL COMMENT '所属仓库ID',
  `source_branch` VARCHAR(200) NOT NULL,
  `target_branch` VARCHAR(200) NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `author_id` INT UNSIGNED NOT NULL,
  `status` ENUM('OPEN','APPROVED','MERGED','CLOSED','CONFLICT') DEFAULT 'OPEN',
  `merge_strategy` ENUM('MERGE','SQUASH','REBASE') DEFAULT 'MERGE',
  `ci_status` ENUM('PENDING','RUNNING','SUCCESS','FAILED') DEFAULT NULL,
  `requirement_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求ID',
  `remote_mr_id` VARCHAR(50) DEFAULT NULL COMMENT '远端MR IID',
  `merged_by` INT UNSIGNED DEFAULT NULL,
  `merged_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_repo` (`repo_id`),
  INDEX `idx_author` (`author_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合并请求';

-- =====================================================
-- 6. 审计日志表 (五元组)
-- =====================================================
DROP TABLE IF EXISTS `git_audit_logs`;
CREATE TABLE `git_audit_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `operator_id` INT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
  `operator_ip` VARCHAR(64) DEFAULT NULL COMMENT '来源IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '操作设备',
  `target_type` VARCHAR(50) NOT NULL COMMENT '操作对象类型(platform/repo/rule/mr)',
  `target_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作对象ID',
  `target_name` VARCHAR(200) DEFAULT NULL COMMENT '操作对象名称',
  `action` VARCHAR(100) NOT NULL COMMENT '操作类型',
  `detail` TEXT DEFAULT NULL COMMENT '变更内容(JSON)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_operator` (`operator_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_action` (`action`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码管理操作审计日志';

-- =====================================================
-- 种子数据: 示例保护规则集
-- =====================================================
INSERT INTO `protection_rule_sets` (`name`, `description`, `created_by`) VALUES
('生产环境标准', '适用于生产仓库，要求双人审批 + CI 通过', 1),
('开发环境宽松', '适用于内部开发仓库，单审批即可', 1);
