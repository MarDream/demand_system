-- ============================================================
-- 用户管理：邀请成员 / 添加·申请记录 / 批量管理
-- 日期：2026-09-15
--
-- 背景：用户管理页的「邀请成员（通过链接邀请 / 批量邀请）」「添加·申请记录」
-- 「批量管理（批量启用 / 批量停用 / 批量删除）」三处入口原先只弹一句
-- 「xxx能力将在后续接口完善后接入」的提示，没有任何后端与数据支撑。
-- 本迁移新增两张表承载邀请与加入申请，形成完整闭环：
--
--   管理员生成邀请（链接 / 批量）
--     → 被邀请人打开链接填写资料提交
--     → 生成一条「申请记录」（pending）
--     → 管理员审批通过 → 自动建号并归入组织 / 授予角色
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/2026-09-15-user-invitation.sql
--
-- 说明：
--   - 脚本可重复执行（幂等），已存在的表/列会跳过。
--   - 枚举取值统一用 VARCHAR 存字面量，列注释里写明全部取值，
--     与 users.status / bitable_* 系列保持一致的风格。
-- ============================================================

-- 1. 邀请记录表（链接邀请 + 批量邀请）
CREATE TABLE IF NOT EXISTS `sys_invitations` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `invite_code` VARCHAR(64) NOT NULL COMMENT '邀请码，链接邀请拼在 URL 上（/public/invite/{code}）',
  `invite_type` VARCHAR(16) NOT NULL DEFAULT 'link' COMMENT 'link=通过链接邀请, batch=批量邀请',
  `target` VARCHAR(128) DEFAULT NULL COMMENT '被邀请人手机号或邮箱；链接邀请为空（谁拿到链接都能用）',
  `target_name` VARCHAR(64) DEFAULT NULL COMMENT '被邀请人姓名，批量邀请时填写',
  `org_id` INT UNSIGNED DEFAULT NULL COMMENT '预分配组织ID，接受后自动归入',
  `role_ids` VARCHAR(255) DEFAULT NULL COMMENT '预分配角色ID，逗号分隔；为空则不授角色',
  `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending=待接受, accepted=已接受, expired=已过期, revoked=已撤回',
  `max_uses` INT NOT NULL DEFAULT 0 COMMENT '链接最多可用次数，0=不限次',
  `used_count` INT NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `expires_at` DATETIME DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
  `invited_by` INT UNSIGNED DEFAULT NULL COMMENT '邀请人用户ID',
  `accepted_by` INT UNSIGNED DEFAULT NULL COMMENT '接受人用户ID（审批通过后回填）',
  `accepted_at` DATETIME DEFAULT NULL COMMENT '接受时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `deleted_at` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_invite_code` (`invite_code`),
  INDEX `idx_invitation_status` (`status`),
  INDEX `idx_invitation_type` (`invite_type`),
  INDEX `idx_invitation_invited_by` (`invited_by`),
  INDEX `idx_invitation_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户邀请记录（链接/批量）';

-- 2. 加入申请记录表
CREATE TABLE IF NOT EXISTS `sys_join_requests` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `invitation_id` INT UNSIGNED DEFAULT NULL COMMENT '来源邀请ID，管理员直接添加时为空',
  `user_id` INT UNSIGNED DEFAULT NULL COMMENT '审批通过后创建/关联的用户ID',
  `applicant_name` VARCHAR(64) NOT NULL COMMENT '申请人姓名',
  `applicant_phone` VARCHAR(32) DEFAULT NULL COMMENT '申请人手机号',
  `applicant_email` VARCHAR(128) DEFAULT NULL COMMENT '申请人邮箱',
  `org_id` INT UNSIGNED DEFAULT NULL COMMENT '申请加入的组织ID',
  `role_ids` VARCHAR(255) DEFAULT NULL COMMENT '申请授予的角色ID，逗号分隔',
  `source` VARCHAR(16) NOT NULL DEFAULT 'link' COMMENT 'link=邀请链接, batch=批量邀请, admin=管理员添加, self=自助申请',
  `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending=待处理, approved=已通过, rejected=已拒绝',
  `apply_remark` VARCHAR(255) DEFAULT NULL COMMENT '申请人留言',
  `reviewed_by` INT UNSIGNED DEFAULT NULL COMMENT '审批人用户ID',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '审批时间',
  `review_remark` VARCHAR(255) DEFAULT NULL COMMENT '审批意见（拒绝原因等）',
  `deleted_at` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_join_request_status` (`status`),
  INDEX `idx_join_request_invitation` (`invitation_id`),
  INDEX `idx_join_request_user` (`user_id`),
  INDEX `idx_join_request_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户加入申请记录';

-- 3. 批量管理独立权限码
--    「批量管理」下拉里的启用/停用原与删除共用一个权限码，
--    这里拆出 button:user:batch-update，便于只给部分角色「可批量启停但不可批量删除」。
INSERT IGNORE INTO `sys_permissions` (`id`, `code`, `name`, `type`, `description`, `status`) VALUES
(140, 'button:user:batch-update', '批量启用/停用用户', 'BUTTON', '用户管理-批量启用/停用', 1);

INSERT IGNORE INTO `sys_menus` (`id`, `parent_id`, `name`, `menu_type`, `path`, `route_name`, `component`, `icon`, `sort_order`, `permission_code`, `visible`, `enabled`, `keep_alive`) VALUES
(240, 11, '批量启用/停用用户', 'BUTTON', NULL, NULL, NULL, NULL, 12, 'button:user:batch-update', 1, 1, 0);

-- 4. 把新权限码补授给已经拥有「批量启停/删除用户」的角色，
--    避免升级后老角色看到「批量管理」菜单却点不动。
INSERT IGNORE INTO `sys_role_permissions` (`role_id`, `permission_id`)
SELECT rp.`role_id`, 140
FROM `sys_role_permissions` rp
JOIN `sys_permissions` p ON p.`id` = rp.`permission_id` AND p.`code` = 'button:user:batch-delete';
