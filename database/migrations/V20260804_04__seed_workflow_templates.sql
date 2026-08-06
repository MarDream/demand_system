-- 官方工作流模板种子数据。模板属于全局项目（project_id=0），复制后会以当前操作者为创建人生成草稿版本。
-- 仅允许 approved/active 版本出现在复制工作流的模板库中。
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-1.0.0', '需求审批流程', '{"id":null,"name":"需求审批流程","nodes":[{"nodeId":"tpl_tpl-1_0_0_start","type":"start","name":"开始","sortOrder":1},{"nodeId":"tpl_tpl-1_0_0_review","type":"approval","name":"产品评审","sortOrder":2},{"nodeId":"tpl_tpl-1_0_0_dev","type":"approval","name":"研发确认","sortOrder":3},{"nodeId":"tpl_tpl-1_0_0_end","type":"end","name":"结束","sortOrder":4}],"edges":[{"edgeId":"edge_1","source":"tpl_tpl-1_0_0_start","target":"tpl_tpl-1_0_0_review","label":null},{"edgeId":"edge_2","source":"tpl_tpl-1_0_0_review","target":"tpl_tpl-1_0_0_dev","label":null},{"edgeId":"edge_3","source":"tpl_tpl-1_0_0_dev","target":"tpl_tpl-1_0_0_end","label":null}]}', 0, 1, 0, 'approved', '适用于需求提交、产品评审和研发确认的标准审批流程。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.0.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.0.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_0_0_start', 'start', '开始', 180, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_0_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_0_0_review', 'approval', '产品评审', 520, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_0_0_review');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_0_0_dev', 'approval', '研发确认', 860, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_0_0_dev');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_0_0_end', 'end', '结束', 1200, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_0_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_1', 'tpl_tpl-1_0_0_start', 'tpl_tpl-1_0_0_review' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_2', 'tpl_tpl-1_0_0_review', 'tpl_tpl-1_0_0_dev' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_3', 'tpl_tpl-1_0_0_dev', 'tpl_tpl-1_0_0_end' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_3');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-1.1.0', '采购申请审批', '{"id":null,"name":"采购申请审批","nodes":[{"nodeId":"tpl_tpl-1_1_0_start","type":"start","name":"开始","sortOrder":1},{"nodeId":"tpl_tpl-1_1_0_manager","type":"approval","name":"部门负责人审批","sortOrder":2},{"nodeId":"tpl_tpl-1_1_0_finance","type":"approval","name":"财务复核","sortOrder":3},{"nodeId":"tpl_tpl-1_1_0_end","type":"end","name":"结束","sortOrder":4}],"edges":[{"edgeId":"edge_1","source":"tpl_tpl-1_1_0_start","target":"tpl_tpl-1_1_0_manager","label":null},{"edgeId":"edge_2","source":"tpl_tpl-1_1_0_manager","target":"tpl_tpl-1_1_0_finance","label":null},{"edgeId":"edge_3","source":"tpl_tpl-1_1_0_finance","target":"tpl_tpl-1_1_0_end","label":null}]}', 0, 1, 0, 'approved', '适用于采购申请、部门负责人审批和财务复核。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.1.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.1.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_1_0_start', 'start', '开始', 180, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_1_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_1_0_manager', 'approval', '部门负责人审批', 520, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_1_0_manager');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_1_0_finance', 'approval', '财务复核', 860, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_1_0_finance');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_1_0_end', 'end', '结束', 1200, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_1_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_1', 'tpl_tpl-1_1_0_start', 'tpl_tpl-1_1_0_manager' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_2', 'tpl_tpl-1_1_0_manager', 'tpl_tpl-1_1_0_finance' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_3', 'tpl_tpl-1_1_0_finance', 'tpl_tpl-1_1_0_end' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_3');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-1.2.0', '请假审批流程', '{"id":null,"name":"请假审批流程","nodes":[{"nodeId":"tpl_tpl-1_2_0_start","type":"start","name":"开始","sortOrder":1},{"nodeId":"tpl_tpl-1_2_0_leave","type":"approval","name":"直属负责人审批","sortOrder":2},{"nodeId":"tpl_tpl-1_2_0_end","type":"end","name":"结束","sortOrder":3}],"edges":[{"edgeId":"edge_1","source":"tpl_tpl-1_2_0_start","target":"tpl_tpl-1_2_0_leave","label":null},{"edgeId":"edge_2","source":"tpl_tpl-1_2_0_leave","target":"tpl_tpl-1_2_0_end","label":null}]}', 0, 1, 0, 'approved', '适用于员工请假申请和直属负责人审批。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.2.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.2.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_2_0_start', 'start', '开始', 180, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_2_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_2_0_leave', 'approval', '直属负责人审批', 620, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_2_0_leave');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_2_0_end', 'end', '结束', 1060, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_2_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_1', 'tpl_tpl-1_2_0_start', 'tpl_tpl-1_2_0_leave' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_2', 'tpl_tpl-1_2_0_leave', 'tpl_tpl-1_2_0_end' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_2');
INSERT INTO workflow_versions (project_id, version, name, definition, is_active, is_template, copy_count, activation_status, change_log, submitted_for_approval_at, approved_at, approved_by, creator_id)
SELECT 0, 'tpl-1.3.0', '发布变更审批', '{"id":null,"name":"发布变更审批","nodes":[{"nodeId":"tpl_tpl-1_3_0_start","type":"start","name":"开始","sortOrder":1},{"nodeId":"tpl_tpl-1_3_0_risk","type":"approval","name":"变更风险评估","sortOrder":2},{"nodeId":"tpl_tpl-1_3_0_release","type":"approval","name":"上线确认","sortOrder":3},{"nodeId":"tpl_tpl-1_3_0_end","type":"end","name":"结束","sortOrder":4}],"edges":[{"edgeId":"edge_1","source":"tpl_tpl-1_3_0_start","target":"tpl_tpl-1_3_0_risk","label":null},{"edgeId":"edge_2","source":"tpl_tpl-1_3_0_risk","target":"tpl_tpl-1_3_0_release","label":null},{"edgeId":"edge_3","source":"tpl_tpl-1_3_0_release","target":"tpl_tpl-1_3_0_end","label":null}]}', 0, 1, 0, 'approved', '适用于生产发布、变更风险评估和上线确认。', NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.3.0');
SET @workflow_template_id := (SELECT id FROM workflow_versions WHERE project_id = 0 AND version = 'tpl-1.3.0' LIMIT 1);
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_3_0_start', 'start', '开始', 180, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_3_0_start');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_3_0_risk', 'approval', '变更风险评估', 500, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_3_0_risk');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_3_0_release', 'approval', '上线确认', 860, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_3_0_release');
INSERT INTO workflow_nodes (workflow_version_id, node_id, node_type, node_name, position_x, position_y)
SELECT @workflow_template_id, 'tpl_tpl-1_3_0_end', 'end', '结束', 1200, 80 FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_nodes WHERE workflow_version_id = @workflow_template_id AND node_id = 'tpl_tpl-1_3_0_end');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_1', 'tpl_tpl-1_3_0_start', 'tpl_tpl-1_3_0_risk' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_1');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_2', 'tpl_tpl-1_3_0_risk', 'tpl_tpl-1_3_0_release' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_2');
INSERT INTO workflow_edges (workflow_version_id, edge_id, source_node_id, target_node_id)
SELECT @workflow_template_id, 'edge_3', 'tpl_tpl-1_3_0_release', 'tpl_tpl-1_3_0_end' FROM DUAL
WHERE @workflow_template_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow_edges WHERE workflow_version_id = @workflow_template_id AND edge_id = 'edge_3');
COMMIT;
