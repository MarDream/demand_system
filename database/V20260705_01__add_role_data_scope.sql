-- 角色数据权限-可见组织范围表
-- 用于角色管理中进行组织架构级的数据权限控制
CREATE TABLE IF NOT EXISTS `role_data_scope_orgs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `role_id` INT UNSIGNED NOT NULL COMMENT '角色ID',
    `org_id` INT UNSIGNED NOT NULL COMMENT '组织ID（角色下用户可见的需求创建人所属组织范围）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_role_org` (`role_id`, `org_id`) USING BTREE,
    INDEX `idx_org_id` (`org_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色数据权限-可见组织范围';
