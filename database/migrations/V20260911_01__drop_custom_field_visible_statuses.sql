-- 字段可见性统一由工作流节点权限（visible_fields/editable_fields/required_fields）控制，
-- custom_fields.visible_statuses 为早期遗留机制，代码中已无任何读写，安全下线。
ALTER TABLE custom_fields DROP COLUMN visible_statuses;
