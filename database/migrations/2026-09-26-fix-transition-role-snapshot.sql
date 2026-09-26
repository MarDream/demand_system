-- 修复流转历史中的角色快照错误：
-- 问题：多角色用户（如湛兴梦同时是产品经理和运营工单员）流转时，
--       快照取的是 sort_order/id 最小的主角色（产品经理），而非流转时所在节点的角色（运营工单员）。
-- 修复：根据流转来源节点（from_node）的配置角色重新计算操作人角色快照。
--       如果操作人拥有来源节点的角色，则使用该角色；否则回退到目标节点角色；仍无则保留原快照。

START TRANSACTION;

-- 回填存量数据：使用来源节点的角色重新计算快照
UPDATE workflow_instance_transitions t
JOIN workflow_instances i ON i.id = t.instance_id
JOIN workflow_nodes from_node ON from_node.workflow_version_id = i.workflow_version_id AND from_node.node_id = t.from_node_id
JOIN workflow_nodes to_node ON to_node.workflow_version_id = i.workflow_version_id AND to_node.node_id = t.to_node_id
SET t.operator_role_name = (
    SELECT r.name
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    WHERE ur.user_id = t.operator_id
      AND ur.role_id = COALESCE(from_node.assignee_role_id, to_node.assignee_role_id)
    LIMIT 1
)
WHERE t.action IN ('submit', 'proxy_approve', 'approve')
  AND (from_node.assignee_type = 'SPECIFIED_ROLE' OR to_node.assignee_type = 'SPECIFIED_ROLE')
  AND (COALESCE(from_node.assignee_role_id, to_node.assignee_role_id) IS NOT NULL)
  AND EXISTS (
    SELECT 1 FROM user_roles ur2
    JOIN roles r2 ON r2.id = ur2.role_id
    WHERE ur2.user_id = t.operator_id
      AND ur2.role_id = COALESCE(from_node.assignee_role_id, to_node.assignee_role_id)
  );

COMMIT;

-- 验证
SELECT 
    t.action,
    t.operator_role_name,
    t.from_node_name,
    t.to_node_name,
    COUNT(*) AS cnt
FROM workflow_instance_transitions t
WHERE t.action IN ('submit', 'proxy_approve', 'approve')
GROUP BY t.action, t.operator_role_name, t.from_node_name, t.to_node_name
ORDER BY t.from_node_name, t.to_node_name;
