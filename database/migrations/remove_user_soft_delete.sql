-- 移除用户表的软删除字段，改为硬删除
-- 执行日期: 2026-06-21

USE demand_system;

-- 删除 deleted_at 字段和相关索引
ALTER TABLE `users` DROP INDEX `idx_deleted_at`;
ALTER TABLE `users` DROP COLUMN `deleted_at`;
