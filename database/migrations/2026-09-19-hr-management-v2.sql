-- 人事管理 v2：users 补充生日字段（员工关怀/花名册用）

ALTER TABLE `users`
  ADD COLUMN `birthday` DATE DEFAULT NULL COMMENT '生日' AFTER `hire_date`,
  ADD INDEX `idx_birthday` (`birthday`);
