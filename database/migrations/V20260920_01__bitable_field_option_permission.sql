-- 字段权限增加选项级配置：单选/多选字段可按角色限制哪些选项可编辑、以及选项定义的管理权限
-- option_config JSON: {"mode":"all|partial","editableKeys":["选项A",...],"manage":"full|add-only"}
ALTER TABLE `bitable_field_permissions`
    ADD COLUMN `option_config` TEXT NULL COMMENT '选项级权限配置JSON: {mode:all|partial, editableKeys:[选项label], manage:full|add-only}' AFTER `permission_level`;
