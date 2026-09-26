-- 流转历史记录流转时角色名称：
-- 1) workflow_instance_transitions 新增 operator_role_name 快照列（写入时取操作人当时角色）
-- 2) 存量流转记录按操作人当前角色回填（历史角色无法追溯，取展示优先级最高的角色：
--    roles.sort_order 最小者，并列取 id 最小者）

START TRANSACTION;

ALTER TABLE workflow_instance_transitions
  ADD COLUMN operator_role_name VARCHAR(100) DEFAULT NULL COMMENT '操作人流转时角色名称快照' AFTER operator_id;

UPDATE workflow_instance_transitions t
JOIN (
  SELECT user_id, role_name FROM (
    SELECT ur.user_id, r.name AS role_name,
           ROW_NUMBER() OVER (PARTITION BY ur.user_id ORDER BY r.sort_order ASC, r.id ASC) AS rn
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id AND r.deleted_at = 0
  ) x WHERE x.rn = 1
) opr ON opr.user_id = t.operator_id
SET t.operator_role_name = opr.role_name
WHERE t.operator_role_name IS NULL;

COMMIT;

-- 验证：
-- SELECT operator_role_name, COUNT(*) FROM workflow_instance_transitions GROUP BY operator_role_name;
