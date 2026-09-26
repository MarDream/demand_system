-- 需求创建时间口径调整：创建时间 = 首次提交流转（进入开始节点）的时间。
-- 1) 为已提交的存量需求补写"草稿"流转记录：草稿创建时间取需求当前 created_at（即原草稿创建时间）
-- 2) 将存量已提交需求的 created_at 对齐为首次提交（start→下一节点）流转时间
-- 仅处理有工作流实例的需求；无实例的存量需求（旧版工作流直建/未提交草稿）保持原状。
-- 可重复执行：NOT EXISTS 保证不重复补写草稿记录。

START TRANSACTION;

INSERT INTO workflow_instance_transitions
  (instance_id, requirement_id, from_node_id, from_node_name, to_node_id, to_node_name,
   operator_id, action, comment, started_at, created_at)
SELECT i.id, r.id, 'draft', '草稿', t.from_node_id, IFNULL(t.from_node_name, '开始'),
       r.creator_id, 'draft', '创建草稿', r.created_at, r.created_at
FROM requirements r
JOIN workflow_instances i ON i.id = r.workflow_instance_id
JOIN workflow_instance_transitions t
  ON t.id = (SELECT MIN(t2.id) FROM workflow_instance_transitions t2
             WHERE t2.instance_id = i.id AND t2.action = 'submit'
               AND (t2.from_node_id = 'start' OR t2.from_node_name = '开始'))
WHERE r.workflow_instance_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM workflow_instance_transitions d
                  WHERE d.instance_id = i.id AND d.action = 'draft');

UPDATE requirements r
JOIN workflow_instances i ON i.id = r.workflow_instance_id
JOIN workflow_instance_transitions t
  ON t.id = (SELECT MIN(t2.id) FROM workflow_instance_transitions t2
             WHERE t2.instance_id = i.id AND t2.action = 'submit'
               AND (t2.from_node_id = 'start' OR t2.from_node_name = '开始'))
SET r.created_at = t.created_at;

COMMIT;

-- 验证：
-- SELECT r.id, r.created_at, t.created_at AS first_submit_at
--   FROM requirements r
--   JOIN workflow_instance_transitions t ON t.requirement_id = r.id AND t.action='submit' AND t.from_node_id='start'
--  WHERE r.workflow_instance_id IS NOT NULL AND r.created_at <> t.created_at;  （应为空）
