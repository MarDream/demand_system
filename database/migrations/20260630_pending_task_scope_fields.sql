-- 需求待办任务表扩展：支持角色/角色组/组织粒度的待办范围
-- 解决大批量用户导致字段超限问题，同时支持"选择了具体用户"的精确场景

ALTER TABLE requirement_pending_tasks
  ADD COLUMN role_id BIGINT COMMENT '角色ID（当assigneeType=SPECIFIED_ROLE时）' AFTER assignee_type,
  ADD COLUMN role_group_id BIGINT COMMENT '角色组ID（当assigneeType=SPECIFIED_ROLE_GROUP时）' AFTER role_id,
  ADD COLUMN org_id BIGINT COMMENT '组织ID（当assigneeType=SPECIFIED_ORG时）' AFTER role_group_id,
  MODIFY COLUMN user_id BIGINT COMMENT '指定用户ID（当assigneeType=SPECIFIED_USER或CREATOR或PREV_APPROVER或选中具体人时可为null）';

-- 索引优化：确保范围查询走索引
CREATE INDEX idx_pending_role_id ON requirement_pending_tasks (role_id);
CREATE INDEX idx_pending_role_group_id ON requirement_pending_tasks (role_group_id);
CREATE INDEX idx_pending_org_id ON requirement_pending_tasks (org_id);
