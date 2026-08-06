-- 高级工作流模板：条件分支、AND/OR并行、站内消息抄送、只读查阅待办抄送及综合示例。
-- 模板属于全局项目（project_id=0），使用 WHERE NOT EXISTS 保证重复执行幂等。
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-2.0.0', '条件分支审批', '{"id":null,"name":"条件分支审批","nodes":[{"nodeId":"tpl_tpl_2_0_0_start","type":"start","name":"开始","sortOrder":1,"properties":{"nodeStatusCode":"DRAFT"}},{"nodeId":"tpl_tpl_2_0_0_condition","type":"condition","name":"优先级条件分支","sortOrder":2,"properties":{"conditionDesc":"高优需求进入技术评审，其他需求走普通评审"}},{"nodeId":"tpl_tpl_2_0_0_normal","type":"approval","name":"普通产品评审","sortOrder":3,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_0_0_high","type":"approval","name":"高优产品评审","sortOrder":4,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_0_0_tech","type":"approval","name":"技术评审","sortOrder":5,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},{"nodeId":"tpl_tpl_2_0_0_end","type":"end","name":"结束","sortOrder":6,"properties":{"nodeStatusCode":"ACCEPTED"}}],"edges":[{"edgeId":"tpl2_0_0_e1","source":"tpl_tpl_2_0_0_start","target":"tpl_tpl_2_0_0_condition","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl2_0_0_e2","source":"tpl_tpl_2_0_0_condition","target":"tpl_tpl_2_0_0_high","label":"高优分支","condition":{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"},"properties":{"condition":{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"}}},{"edgeId":"tpl2_0_0_e3","source":"tpl_tpl_2_0_0_condition","target":"tpl_tpl_2_0_0_normal","label":"默认分支","condition":{"defaultFlow":true},"properties":{"condition":{"defaultFlow":true}}},{"edgeId":"tpl2_0_0_e4","source":"tpl_tpl_2_0_0_high","target":"tpl_tpl_2_0_0_tech","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl2_0_0_e5","source":"tpl_tpl_2_0_0_tech","target":"tpl_tpl_2_0_0_end","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl2_0_0_e6","source":"tpl_tpl_2_0_0_normal","target":"tpl_tpl_2_0_0_end","label":null,"condition":{},"properties":{"condition":{}}}]}', 0, 1, 0, 'approved', '按需求优先级选择普通评审或高优评审+技术评审；包含默认分支示例。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.0.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.0.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_0_0_start', 'start', '开始', 180, 180, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"DRAFT"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_0_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_0_0_condition', 'condition', '优先级条件分支', 420, 180, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"conditionDesc":"高优需求进入技术评审，其他需求走普通评审"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_0_0_condition');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_0_0_normal', 'approval', '普通产品评审', 700, 280, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_0_0_normal');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_0_0_high', 'approval', '高优产品评审', 700, 80, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_0_0_high');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_0_0_tech', 'approval', '技术评审', 980, 80, 'SPECIFIED_USER', NULL, NULL, NULL, '[2]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_0_0_tech');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_0_0_end', 'end', '结束', 1260, 180, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"ACCEPTED"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_0_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl2_0_0_e1', 'tpl_tpl_2_0_0_start', 'tpl_tpl_2_0_0_condition', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl2_0_0_e1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl2_0_0_e2', 'tpl_tpl_2_0_0_condition', 'tpl_tpl_2_0_0_high', '高优分支', '{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"}', '{"condition":{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl2_0_0_e2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl2_0_0_e3', 'tpl_tpl_2_0_0_condition', 'tpl_tpl_2_0_0_normal', '默认分支', '{"defaultFlow":true}', '{"condition":{"defaultFlow":true}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl2_0_0_e3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl2_0_0_e4', 'tpl_tpl_2_0_0_high', 'tpl_tpl_2_0_0_tech', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl2_0_0_e4');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl2_0_0_e5', 'tpl_tpl_2_0_0_tech', 'tpl_tpl_2_0_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl2_0_0_e5');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl2_0_0_e6', 'tpl_tpl_2_0_0_normal', 'tpl_tpl_2_0_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl2_0_0_e6');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-2.1.0', 'AND并行会签', '{"id":null,"name":"AND并行会签","nodes":[{"nodeId":"tpl_tpl_2_1_0_start","type":"start","name":"开始","sortOrder":1,"properties":{"nodeStatusCode":"DRAFT"}},{"nodeId":"tpl_tpl_2_1_0_parallel","type":"parallel","name":"AND并行评审","sortOrder":2,"properties":{"parallelType":"AND","branches":[{"branchId":"tpl_tpl_2_1_0_product","branchName":"产品评审","condition":{}},{"branchId":"tpl_tpl_2_1_0_tech","branchName":"技术评审","condition":{}},{"branchId":"tpl_tpl_2_1_0_finance","branchName":"财务评审","condition":{}}]}},{"nodeId":"tpl_tpl_2_1_0_product","type":"approval","name":"产品评审","sortOrder":3,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_1_0_tech","type":"approval","name":"技术评审","sortOrder":4,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},{"nodeId":"tpl_tpl_2_1_0_finance","type":"approval","name":"财务评审","sortOrder":5,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[3]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[3]},{"nodeId":"tpl_tpl_2_1_0_merge","type":"parallel","name":"并行汇聚","sortOrder":6,"properties":{"parallelType":"AND"}},{"nodeId":"tpl_tpl_2_1_0_owner","type":"approval","name":"汇聚负责人确认","sortOrder":7,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_1_0_end","type":"end","name":"结束","sortOrder":8,"properties":{"nodeStatusCode":"ACCEPTED"}}],"edges":[{"edgeId":"tpl_tpl_2_1_0_e1","source":"tpl_tpl_2_1_0_start","target":"tpl_tpl_2_1_0_parallel","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_e2","source":"tpl_tpl_2_1_0_parallel","target":"tpl_tpl_2_1_0_product","label":"产品评审","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_b1","source":"tpl_tpl_2_1_0_product","target":"tpl_tpl_2_1_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_e3","source":"tpl_tpl_2_1_0_parallel","target":"tpl_tpl_2_1_0_tech","label":"技术评审","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_b2","source":"tpl_tpl_2_1_0_tech","target":"tpl_tpl_2_1_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_e4","source":"tpl_tpl_2_1_0_parallel","target":"tpl_tpl_2_1_0_finance","label":"财务评审","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_b3","source":"tpl_tpl_2_1_0_finance","target":"tpl_tpl_2_1_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_e_merge","source":"tpl_tpl_2_1_0_merge","target":"tpl_tpl_2_1_0_owner","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_1_0_e_end","source":"tpl_tpl_2_1_0_owner","target":"tpl_tpl_2_1_0_end","label":null,"condition":{},"properties":{"condition":{}}}]}', 0, 1, 0, 'approved', '三个评审分支全部完成后进入汇聚负责人确认；当前运行时按并行分支记录顺序切换活动分支。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.1.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.1.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_start', 'start', '开始', 150, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"DRAFT"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_parallel', 'parallel', 'AND并行评审', 390, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"parallelType":"AND","branches":[{"branchId":"tpl_tpl_2_1_0_product","branchName":"产品评审","condition":{}},{"branchId":"tpl_tpl_2_1_0_tech","branchName":"技术评审","condition":{}},{"branchId":"tpl_tpl_2_1_0_finance","branchName":"财务评审","condition":{}}]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_parallel');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_product', 'approval', '产品评审', 690, 80, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_product');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_tech', 'approval', '技术评审', 690, 220, 'SPECIFIED_USER', NULL, NULL, NULL, '[2]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_tech');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_finance', 'approval', '财务评审', 690, 360, 'SPECIFIED_USER', NULL, NULL, NULL, '[3]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[3]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_finance');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_merge', 'parallel', '并行汇聚', 980, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"parallelType":"AND"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_merge');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_owner', 'approval', '汇聚负责人确认', 1250, 220, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_owner');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_end', 'end', '结束', 1510, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"ACCEPTED"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_1_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_e1', 'tpl_tpl_2_1_0_start', 'tpl_tpl_2_1_0_parallel', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_e1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_e2', 'tpl_tpl_2_1_0_parallel', 'tpl_tpl_2_1_0_product', '产品评审', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_e2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_b1', 'tpl_tpl_2_1_0_product', 'tpl_tpl_2_1_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_b1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_e3', 'tpl_tpl_2_1_0_parallel', 'tpl_tpl_2_1_0_tech', '技术评审', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_e3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_b2', 'tpl_tpl_2_1_0_tech', 'tpl_tpl_2_1_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_b2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_e4', 'tpl_tpl_2_1_0_parallel', 'tpl_tpl_2_1_0_finance', '财务评审', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_e4');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_b3', 'tpl_tpl_2_1_0_finance', 'tpl_tpl_2_1_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_b3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_e_merge', 'tpl_tpl_2_1_0_merge', 'tpl_tpl_2_1_0_owner', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_e_merge');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_1_0_e_end', 'tpl_tpl_2_1_0_owner', 'tpl_tpl_2_1_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_1_0_e_end');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-2.2.0', 'OR并行任选一', '{"id":null,"name":"OR并行任选一","nodes":[{"nodeId":"tpl_tpl_2_2_0_start","type":"start","name":"开始","sortOrder":1,"properties":{"nodeStatusCode":"DRAFT"}},{"nodeId":"tpl_tpl_2_2_0_parallel","type":"parallel","name":"OR并行评审","sortOrder":2,"properties":{"parallelType":"OR","branches":[{"branchId":"tpl_tpl_2_2_0_legal","branchName":"法务评审","condition":{}},{"branchId":"tpl_tpl_2_2_0_tech","branchName":"技术评审","condition":{}}]}},{"nodeId":"tpl_tpl_2_2_0_legal","type":"approval","name":"法务评审","sortOrder":3,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_2_0_tech","type":"approval","name":"技术评审","sortOrder":4,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},{"nodeId":"tpl_tpl_2_2_0_merge","type":"parallel","name":"并行汇聚","sortOrder":5,"properties":{"parallelType":"OR"}},{"nodeId":"tpl_tpl_2_2_0_owner","type":"approval","name":"汇聚负责人确认","sortOrder":6,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_2_0_end","type":"end","name":"结束","sortOrder":7,"properties":{"nodeStatusCode":"ACCEPTED"}}],"edges":[{"edgeId":"tpl_tpl_2_2_0_e1","source":"tpl_tpl_2_2_0_start","target":"tpl_tpl_2_2_0_parallel","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_2_0_e2","source":"tpl_tpl_2_2_0_parallel","target":"tpl_tpl_2_2_0_legal","label":"法务评审","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_2_0_b1","source":"tpl_tpl_2_2_0_legal","target":"tpl_tpl_2_2_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_2_0_e3","source":"tpl_tpl_2_2_0_parallel","target":"tpl_tpl_2_2_0_tech","label":"技术评审","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_2_0_b2","source":"tpl_tpl_2_2_0_tech","target":"tpl_tpl_2_2_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_2_0_e_merge","source":"tpl_tpl_2_2_0_merge","target":"tpl_tpl_2_2_0_owner","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_2_0_e_end","source":"tpl_tpl_2_2_0_owner","target":"tpl_tpl_2_2_0_end","label":null,"condition":{},"properties":{"condition":{}}}]}', 0, 1, 0, 'approved', '法务或技术任一分支完成后即可进入汇聚负责人确认；当前运行时按并行分支记录顺序切换活动分支。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.2.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.2.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_start', 'start', '开始', 150, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"DRAFT"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_parallel', 'parallel', 'OR并行评审', 390, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"parallelType":"OR","branches":[{"branchId":"tpl_tpl_2_2_0_legal","branchName":"法务评审","condition":{}},{"branchId":"tpl_tpl_2_2_0_tech","branchName":"技术评审","condition":{}}]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_parallel');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_legal', 'approval', '法务评审', 690, 80, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_legal');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_tech', 'approval', '技术评审', 690, 220, 'SPECIFIED_USER', NULL, NULL, NULL, '[2]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_tech');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_merge', 'parallel', '并行汇聚', 980, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"parallelType":"OR"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_merge');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_owner', 'approval', '汇聚负责人确认', 1250, 220, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_owner');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_end', 'end', '结束', 1510, 220, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"ACCEPTED"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_2_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_e1', 'tpl_tpl_2_2_0_start', 'tpl_tpl_2_2_0_parallel', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_e1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_e2', 'tpl_tpl_2_2_0_parallel', 'tpl_tpl_2_2_0_legal', '法务评审', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_e2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_b1', 'tpl_tpl_2_2_0_legal', 'tpl_tpl_2_2_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_b1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_e3', 'tpl_tpl_2_2_0_parallel', 'tpl_tpl_2_2_0_tech', '技术评审', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_e3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_b2', 'tpl_tpl_2_2_0_tech', 'tpl_tpl_2_2_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_b2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_e_merge', 'tpl_tpl_2_2_0_merge', 'tpl_tpl_2_2_0_owner', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_e_merge');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_2_0_e_end', 'tpl_tpl_2_2_0_owner', 'tpl_tpl_2_2_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_2_0_e_end');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-2.3.0', '站内消息抄送', '{"id":null,"name":"站内消息抄送","nodes":[{"nodeId":"tpl_tpl_2_3_0_start","type":"start","name":"开始","sortOrder":1,"properties":{"nodeStatusCode":"DRAFT"}},{"nodeId":"tpl_tpl_2_3_0_review","type":"approval","name":"产品评审","sortOrder":2,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_3_0_cc","type":"cc","name":"项目相关人抄送","sortOrder":3,"properties":{"ccMode":"MESSAGE","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2,3]}},{"nodeId":"tpl_tpl_2_3_0_confirm","type":"approval","name":"负责人确认","sortOrder":4,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_3_0_end","type":"end","name":"结束","sortOrder":5,"properties":{"nodeStatusCode":"ACCEPTED"}}],"edges":[{"edgeId":"tpl_tpl_2_3_0_e1","source":"tpl_tpl_2_3_0_start","target":"tpl_tpl_2_3_0_review","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_3_0_e2","source":"tpl_tpl_2_3_0_review","target":"tpl_tpl_2_3_0_cc","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_3_0_e3","source":"tpl_tpl_2_3_0_cc","target":"tpl_tpl_2_3_0_confirm","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_3_0_e4","source":"tpl_tpl_2_3_0_confirm","target":"tpl_tpl_2_3_0_end","label":null,"condition":{},"properties":{"condition":{}}}]}', 0, 1, 0, 'approved', '抄送节点示例：发送站内消息，不生成待办。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.3.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.3.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_start', 'start', '开始', 150, 120, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"DRAFT"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_3_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_review', 'approval', '产品评审', 450, 120, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_3_0_review');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_cc', 'cc', '项目相关人抄送', 730, 120, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"ccMode":"MESSAGE","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2,3]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_3_0_cc');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_confirm', 'approval', '负责人确认', 1010, 120, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_3_0_confirm');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_end', 'end', '结束', 1280, 120, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"ACCEPTED"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_3_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_e1', 'tpl_tpl_2_3_0_start', 'tpl_tpl_2_3_0_review', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_3_0_e1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_e2', 'tpl_tpl_2_3_0_review', 'tpl_tpl_2_3_0_cc', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_3_0_e2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_e3', 'tpl_tpl_2_3_0_cc', 'tpl_tpl_2_3_0_confirm', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_3_0_e3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_3_0_e4', 'tpl_tpl_2_3_0_confirm', 'tpl_tpl_2_3_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_3_0_e4');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-2.4.0', '只读查阅待办抄送', '{"id":null,"name":"只读查阅待办抄送","nodes":[{"nodeId":"tpl_tpl_2_4_0_start","type":"start","name":"开始","sortOrder":1,"properties":{"nodeStatusCode":"DRAFT"}},{"nodeId":"tpl_tpl_2_4_0_review","type":"approval","name":"产品评审","sortOrder":2,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_4_0_cc","type":"cc","name":"项目相关人抄送","sortOrder":3,"properties":{"ccMode":"READ_ONLY_TODO","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2,3]}},{"nodeId":"tpl_tpl_2_4_0_confirm","type":"approval","name":"负责人确认","sortOrder":4,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_4_0_end","type":"end","name":"结束","sortOrder":5,"properties":{"nodeStatusCode":"ACCEPTED"}}],"edges":[{"edgeId":"tpl_tpl_2_4_0_e1","source":"tpl_tpl_2_4_0_start","target":"tpl_tpl_2_4_0_review","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_4_0_e2","source":"tpl_tpl_2_4_0_review","target":"tpl_tpl_2_4_0_cc","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_4_0_e3","source":"tpl_tpl_2_4_0_cc","target":"tpl_tpl_2_4_0_confirm","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_4_0_e4","source":"tpl_tpl_2_4_0_confirm","target":"tpl_tpl_2_4_0_end","label":null,"condition":{},"properties":{"condition":{}}}]}', 0, 1, 0, 'approved', '抄送节点示例：生成只读查阅待办，不允许审批或流转。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.4.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.4.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_start', 'start', '开始', 150, 120, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"DRAFT"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_4_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_review', 'approval', '产品评审', 450, 120, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_4_0_review');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_cc', 'cc', '项目相关人抄送', 730, 120, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"ccMode":"READ_ONLY_TODO","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2,3]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_4_0_cc');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_confirm', 'approval', '负责人确认', 1010, 120, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_4_0_confirm');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_end', 'end', '结束', 1280, 120, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"ACCEPTED"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_4_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_e1', 'tpl_tpl_2_4_0_start', 'tpl_tpl_2_4_0_review', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_4_0_e1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_e2', 'tpl_tpl_2_4_0_review', 'tpl_tpl_2_4_0_cc', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_4_0_e2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_e3', 'tpl_tpl_2_4_0_cc', 'tpl_tpl_2_4_0_confirm', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_4_0_e3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_4_0_e4', 'tpl_tpl_2_4_0_confirm', 'tpl_tpl_2_4_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_4_0_e4');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-2.5.0', '条件+并行+只读抄送综合示例', '{"id":null,"name":"条件+并行+只读抄送综合示例","nodes":[{"nodeId":"tpl_tpl_2_5_0_start","type":"start","name":"开始","sortOrder":1,"properties":{"nodeStatusCode":"DRAFT"}},{"nodeId":"tpl_tpl_2_5_0_condition","type":"condition","name":"优先级分支","sortOrder":2,"properties":{"conditionDesc":"按优先级选择普通评审或高优评审"}},{"nodeId":"tpl_tpl_2_5_0_normal","type":"approval","name":"普通产品评审","sortOrder":3,"properties":{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_5_0_high","type":"approval","name":"高优产品评审","sortOrder":4,"properties":{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_5_0_parallel","type":"parallel","name":"多角色并行评审","sortOrder":5,"properties":{"parallelType":"AND","branches":[{"branchId":"tpl_tpl_2_5_0_product","branchName":"产品评审","condition":{}},{"branchId":"tpl_tpl_2_5_0_tech","branchName":"技术评审","condition":{}}]}},{"nodeId":"tpl_tpl_2_5_0_product","type":"approval","name":"产品评审","sortOrder":6,"properties":{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_5_0_tech","type":"approval","name":"技术评审","sortOrder":7,"properties":{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]},{"nodeId":"tpl_tpl_2_5_0_merge","type":"parallel","name":"并行汇聚","sortOrder":8,"properties":{"parallelType":"AND"}},{"nodeId":"tpl_tpl_2_5_0_cc","type":"cc","name":"项目干系人抄送","sortOrder":9,"properties":{"ccMode":"READ_ONLY_TODO","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2,3]}},{"nodeId":"tpl_tpl_2_5_0_final","type":"approval","name":"负责人最终确认","sortOrder":10,"properties":{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},"assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]},{"nodeId":"tpl_tpl_2_5_0_end","type":"end","name":"结束","sortOrder":11,"properties":{"nodeStatusCode":"ACCEPTED"}}],"edges":[{"edgeId":"tpl_tpl_2_5_0_e1","source":"tpl_tpl_2_5_0_start","target":"tpl_tpl_2_5_0_condition","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e2","source":"tpl_tpl_2_5_0_condition","target":"tpl_tpl_2_5_0_normal","label":"默认分支","condition":{"defaultFlow":true},"properties":{"condition":{"defaultFlow":true}}},{"edgeId":"tpl_tpl_2_5_0_e3","source":"tpl_tpl_2_5_0_condition","target":"tpl_tpl_2_5_0_high","label":"高优分支","condition":{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"},"properties":{"condition":{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"}}},{"edgeId":"tpl_tpl_2_5_0_e4","source":"tpl_tpl_2_5_0_normal","target":"tpl_tpl_2_5_0_parallel","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e5","source":"tpl_tpl_2_5_0_high","target":"tpl_tpl_2_5_0_parallel","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e6","source":"tpl_tpl_2_5_0_parallel","target":"tpl_tpl_2_5_0_product","label":"产品分支","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e7","source":"tpl_tpl_2_5_0_parallel","target":"tpl_tpl_2_5_0_tech","label":"技术分支","condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_b1","source":"tpl_tpl_2_5_0_product","target":"tpl_tpl_2_5_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_b2","source":"tpl_tpl_2_5_0_tech","target":"tpl_tpl_2_5_0_merge","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e8","source":"tpl_tpl_2_5_0_merge","target":"tpl_tpl_2_5_0_cc","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e9","source":"tpl_tpl_2_5_0_cc","target":"tpl_tpl_2_5_0_final","label":null,"condition":{},"properties":{"condition":{}}},{"edgeId":"tpl_tpl_2_5_0_e10","source":"tpl_tpl_2_5_0_final","target":"tpl_tpl_2_5_0_end","label":null,"condition":{},"properties":{"condition":{}}}]}', 0, 1, 0, 'approved', '综合展示条件分支、AND并行评审、只读查阅待办抄送和最终确认的组合用法。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.5.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-2.5.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_start', 'start', '开始', 120, 240, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"DRAFT"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_condition', 'condition', '优先级分支', 330, 240, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"conditionDesc":"按优先级选择普通评审或高优评审"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_condition');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_normal', 'approval', '普通产品评审', 570, 380, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_normal');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_high', 'approval', '高优产品评审', 570, 100, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_high');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_parallel', 'parallel', '多角色并行评审', 820, 240, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"parallelType":"AND","branches":[{"branchId":"tpl_tpl_2_5_0_product","branchName":"产品评审","condition":{}},{"branchId":"tpl_tpl_2_5_0_tech","branchName":"技术评审","condition":{}}]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_parallel');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_product', 'approval', '产品评审', 1050, 130, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"PENDING_REVIEW","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_product');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_tech', 'approval', '技术评审', 1050, 350, 'SPECIFIED_USER', NULL, NULL, NULL, '[2]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"nodeStatusCode":"IN_DEVELOPMENT","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_tech');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_merge', 'parallel', '并行汇聚', 1280, 240, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"parallelType":"AND"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_merge');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_cc', 'cc', '项目干系人抄送', 1510, 240, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"ccMode":"READ_ONLY_TODO","assigneeType":"SPECIFIED_USER","assigneeUserIds":[2,3]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_cc');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_final', 'approval', '负责人最终确认', 1760, 240, 'SPECIFIED_USER', NULL, NULL, NULL, '[1]', NULL, NULL, '{"allowCancel":true,"projectRequired":true,"notifyOnEnter":true,"nodeStatusCode":"PENDING_CONFIRM","assigneeType":"SPECIFIED_USER","assigneeUserIds":[1]}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_final');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y, assignee_type, assignee_role_id, assignee_role_group_id, assignee_org_id, assignee_user_ids, timeout_hours, timeout_action, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_end', 'end', '结束', 2020, 240, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '{"nodeStatusCode":"ACCEPTED"}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl_2_5_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e1', 'tpl_tpl_2_5_0_start', 'tpl_tpl_2_5_0_condition', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e2', 'tpl_tpl_2_5_0_condition', 'tpl_tpl_2_5_0_normal', '默认分支', '{"defaultFlow":true}', '{"condition":{"defaultFlow":true}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e3', 'tpl_tpl_2_5_0_condition', 'tpl_tpl_2_5_0_high', '高优分支', '{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"}', '{"condition":{"logic":"AND","rules":[{"field":"priority","operator":"eq","value":"HIGH"}],"expr":"priority == ''HIGH''"}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e3');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e4', 'tpl_tpl_2_5_0_normal', 'tpl_tpl_2_5_0_parallel', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e4');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e5', 'tpl_tpl_2_5_0_high', 'tpl_tpl_2_5_0_parallel', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e5');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e6', 'tpl_tpl_2_5_0_parallel', 'tpl_tpl_2_5_0_product', '产品分支', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e6');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e7', 'tpl_tpl_2_5_0_parallel', 'tpl_tpl_2_5_0_tech', '技术分支', '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e7');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_b1', 'tpl_tpl_2_5_0_product', 'tpl_tpl_2_5_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_b1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_b2', 'tpl_tpl_2_5_0_tech', 'tpl_tpl_2_5_0_merge', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_b2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e8', 'tpl_tpl_2_5_0_merge', 'tpl_tpl_2_5_0_cc', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e8');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e9', 'tpl_tpl_2_5_0_cc', 'tpl_tpl_2_5_0_final', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e9');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id, label, `condition`, properties)
SELECT @workflow_template_id, 'tpl_tpl_2_5_0_e10', 'tpl_tpl_2_5_0_final', 'tpl_tpl_2_5_0_end', NULL, '{}', '{"condition":{}}'
FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'tpl_tpl_2_5_0_e10');
COMMIT;
