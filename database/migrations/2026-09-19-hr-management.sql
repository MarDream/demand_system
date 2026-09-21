-- 人事管理（参考钉钉人事管理）：
-- 1. users 表补充花名册 HR 字段：员工类型 / 用工状态 / 入职日期
-- 2. hr_employee_records 人事事件台账：入职/新人成长/转正/异动/离职/合同/退休/员工关怀/用工安全
-- 3. roster_export_logs 花名册导出历史

ALTER TABLE `users`
  ADD COLUMN `employee_type` ENUM('full_time','part_time','intern','dispatch','other') NOT NULL DEFAULT 'full_time' COMMENT '员工类型(全职/兼职/实习/劳务派遣/其他)' AFTER `status`,
  ADD COLUMN `work_status` ENUM('probation','confirmed','pending_resign','resigned') NOT NULL DEFAULT 'confirmed' COMMENT '用工状态(试用期/已转正/待离职/已离职)' AFTER `employee_type`,
  ADD COLUMN `hire_date` DATE DEFAULT NULL COMMENT '入职日期' AFTER `work_status`,
  ADD INDEX `idx_employee_type` (`employee_type`),
  ADD INDEX `idx_work_status` (`work_status`);

-- 存量员工：入职日期以账号创建日期兜底
UPDATE `users` SET `hire_date` = DATE(`created_at`) WHERE `hire_date` IS NULL;

DROP TABLE IF EXISTS `hr_employee_records`;
CREATE TABLE `hr_employee_records` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `record_type` ENUM('onboarding','newcomer','regularization','transfer','resignation','contract','retirement','care','safety') NOT NULL COMMENT '人事事件类型(入职/新人成长/转正/异动/离职/合同/退休/员工关怀/用工安全)',
  `user_id` INT UNSIGNED NOT NULL COMMENT '关联员工ID',
  `title` VARCHAR(200) NOT NULL COMMENT '事项标题',
  `detail` JSON DEFAULT NULL COMMENT '类型化明细JSON',
  `record_date` DATE DEFAULT NULL COMMENT '业务日期(生效日/到期日/关怀日等)',
  `status` ENUM('processing','done','cancelled') NOT NULL DEFAULT 'processing' COMMENT '状态(办理中/已完成/已取消)',
  `operator_id` INT UNSIGNED DEFAULT NULL COMMENT '经办人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` TINYINT DEFAULT 0 COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_type_status` (`record_type`, `status`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_record_date` (`record_date`),
  INDEX `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人事事件台账';

DROP TABLE IF EXISTS `roster_export_logs`;
CREATE TABLE `roster_export_logs` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `operator_id` INT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
  `file_name` VARCHAR(200) DEFAULT NULL COMMENT '文件名',
  `total` INT NOT NULL DEFAULT 0 COMMENT '导出行数',
  `filter_json` JSON DEFAULT NULL COMMENT '导出时的筛选条件JSON',
  `file_content` MEDIUMTEXT COMMENT 'CSV内容(UTF-8 BOM)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='花名册导出历史';
