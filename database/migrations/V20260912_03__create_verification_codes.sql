-- =====================================================
-- 修复：verification_codes 表缺失导致注册/找回密码流程不可用
-- 日期: 2026-09-12
-- 背景: VerificationCodeServiceImpl 依赖该表存储邮箱验证码，
--       但历史迁移中从未建表，验证码发送/校验必然报错。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- =====================================================

CREATE TABLE IF NOT EXISTS `verification_codes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(200) NOT NULL COMMENT '接收验证码的邮箱',
  `code` VARCHAR(10) NOT NULL COMMENT '6位数字验证码',
  `type` VARCHAR(30) NOT NULL COMMENT '用途: register/password_reset',
  `used` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未使用, 1=已使用',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_email_type_used` (`email`, `type`, `used`),
  INDEX `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮箱验证码';
