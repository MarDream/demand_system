-- ============================================================
-- 多维表格数据表级权限管理
-- 支持：系统角色(所有者/管理员/编辑者/评论者/查看者) + 自定义角色
-- 权限类型：数据权限(data) / 自动化权限(automation)
-- 权限级别：完全权限(full) / 可编辑(edit) / 可查看(view) / 无权限(none)
-- ============================================================

USE `demand_system`;

-- --------------------------------------------------------
-- 1. 自定义角色表（按 Base 隔离）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bitable_base_custom_roles` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `base_id`         BIGINT NOT NULL COMMENT '多维表格ID',
    `name`            VARCHAR(100) NOT NULL COMMENT '角色名称',
    `sort_order`      INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `creator_id`      BIGINT NOT NULL COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at`      TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=删除)',
    INDEX `idx_base_id` (`base_id`),
    INDEX `idx_base_deleted` (`base_id`, `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='多维表格-自定义角色';

-- --------------------------------------------------------
-- 2. 自定义角色成员表（用户或部门）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bitable_base_custom_role_members` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `role_id`         BIGINT NOT NULL COMMENT '自定义角色ID',
    `member_type`     VARCHAR(20) NOT NULL COMMENT '成员类型: user=用户, dept=部门',
    `member_id`       BIGINT NOT NULL COMMENT '成员ID(用户ID或部门ID)',
    `creator_id`      BIGINT NOT NULL COMMENT '创建人ID',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_member` (`role_id`, `member_type`, `member_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_member` (`member_type`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='多维表格-自定义角色成员';

-- --------------------------------------------------------
-- 3. 角色数据表权限表（系统角色 + 自定义角色统一存储）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bitable_base_role_permissions` (
    `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `base_id`             BIGINT NOT NULL COMMENT '多维表格ID',
    `role_type`           VARCHAR(20) NOT NULL COMMENT '角色类型: system=系统角色, custom=自定义角色',
    `system_role_code`    VARCHAR(50) DEFAULT NULL COMMENT '系统角色编码(OWNER/ADMIN/EDITOR/COMMENTER/VIEWER)',
    `custom_role_id`      BIGINT DEFAULT NULL COMMENT '自定义角色ID',
    `table_id`            BIGINT NOT NULL COMMENT '数据表ID',
    `permission_type`     VARCHAR(20) NOT NULL DEFAULT 'data' COMMENT '权限类型: data=数据权限, automation=自动化权限',
    `permission_level`    VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '权限级别: full=完全权限, edit=可编辑, view=可查看, none=无权限',
    `creator_id`          BIGINT NOT NULL COMMENT '创建人ID',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 用生成列保证唯一性（MySQL 允许 NULL 在 UNIQUE 中视为不同值，需合成唯一标识）
    `role_identifier`     VARCHAR(120) AS (CONCAT(`role_type`, ':', COALESCE(`system_role_code`, ''), ':', COALESCE(`custom_role_id`, ''))) STORED,
    UNIQUE KEY `uk_permission` (`base_id`, `role_identifier`, `table_id`, `permission_type`),
    INDEX `idx_base_table` (`base_id`, `table_id`),
    INDEX `idx_custom_role` (`custom_role_id`),
    INDEX `idx_system_role` (`base_id`, `system_role_code`, `permission_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='多维表格-角色数据表权限';

-- --------------------------------------------------------
-- 4. 为现有数据初始化默认权限（保证已有基地不中断）
--    系统角色按 base 成员角色，对新表默认继承该角色能力：
--    OWNER/ADMIN → full, EDITOR → edit, COMMENTER/VIEWER → view
-- --------------------------------------------------------

-- 数据权限：按 base 成员角色，为每张表生成默认权限
INSERT IGNORE INTO `bitable_base_role_permissions`
    (`base_id`, `role_type`, `system_role_code`, `table_id`, `permission_type`, `permission_level`, `creator_id`, `created_at`)
SELECT
    DISTINCT
    t.base_id,
    'system',
    m.role,
    t.id,
    'data',
    CASE m.role
        WHEN 'OWNER'  THEN 'full'
        WHEN 'ADMIN'  THEN 'full'
        WHEN 'EDITOR' THEN 'edit'
        ELSE 'view'
    END,
    1,
    NOW()
FROM `bitable_tables` t
JOIN `bitable_base_members` m ON m.base_id = t.base_id
WHERE t.deleted_at = 0;

-- 自动化权限：默认与数据权限相同
INSERT IGNORE INTO `bitable_base_role_permissions`
    (`base_id`, `role_type`, `system_role_code`, `table_id`, `permission_type`, `permission_level`, `creator_id`, `created_at`)
SELECT
    DISTINCT
    t.base_id,
    'system',
    m.role,
    t.id,
    'automation',
    CASE m.role
        WHEN 'OWNER'  THEN 'full'
        WHEN 'ADMIN'  THEN 'full'
        WHEN 'EDITOR' THEN 'edit'
        ELSE 'view'
    END,
    1,
    NOW()
FROM `bitable_tables` t
JOIN `bitable_base_members` m ON m.base_id = t.base_id
WHERE t.deleted_at = 0;
