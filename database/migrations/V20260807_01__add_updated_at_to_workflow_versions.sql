-- 版本管理列表排序需求：为 workflow_versions 增加 updated_at 编辑时间列
-- 用于「按编辑时间 + 启用状态」排序显示版本列表
-- 存量数据回填为 created_at，保证排序对旧数据依然有效
ALTER TABLE `workflow_versions`
  ADD COLUMN `updated_at` DATETIME DEFAULT NULL COMMENT '编辑时间(最近一次保存/启停/复制等变更时间)' AFTER `created_at`;

UPDATE `workflow_versions` SET `updated_at` = `created_at` WHERE `updated_at` IS NULL;
