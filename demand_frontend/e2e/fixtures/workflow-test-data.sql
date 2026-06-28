-- 工作流 E2E 测试数据初始化脚本
-- 用途：确保项目 1 有草稿版本，方便完整闭环测试

-- 1. 为项目 1 创建一个草稿版本（带故意的配置错误用于测试校验）
INSERT INTO workflow_versions (
    project_id,
    version,
    name,
    description,
    definition,
    activation_status,
    is_active,
    created_by,
    created_at,
    updated_at
) VALUES (
    1,
    '1.0.0-draft',
    '测试草稿版本（带配置错误）',
    '用于 E2E 测试的草稿版本，故意保留配置错误',
    '{}',
    'draft',
    0,
    1,
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 获取刚创建的版本 ID（假设为 @draft_version_id）
SET @draft_version_id = LAST_INSERT_ID();

-- 2. 为草稿版本添加节点（故意缺少审批人，触发校验错误）
INSERT INTO workflow_nodes (
    workflow_version_id,
    node_id,
    node_type,
    node_name,
    position_x,
    position_y,
    config,
    created_at,
    updated_at
) VALUES
-- 开始节点
(
    @draft_version_id,
    CONCAT('v', @draft_version_id, '_start'),
    'start',
    '开始',
    100,
    100,
    '{}',
    NOW(),
    NOW()
),
-- 审批节点（故意不配置 assignee，触发校验错误）
(
    @draft_version_id,
    CONCAT('v', @draft_version_id, '_approval1'),
    'approval',
    '部门审批',
    300,
    100,
    '{"approvalType":"single"}',
    NOW(),
    NOW()
),
-- 结束节点
(
    @draft_version_id,
    CONCAT('v', @draft_version_id, '_end'),
    'end',
    '结束',
    500,
    100,
    '{"statusCode":"completed"}',
    NOW(),
    NOW()
);

-- 3. 添加连线
INSERT INTO workflow_edges (
    workflow_version_id,
    edge_id,
    source_node_id,
    target_node_id,
    label,
    created_at,
    updated_at
) VALUES
(
    @draft_version_id,
    CONCAT('e', @draft_version_id, '_1'),
    CONCAT('v', @draft_version_id, '_start'),
    CONCAT('v', @draft_version_id, '_approval1'),
    '提交',
    NOW(),
    NOW()
),
(
    @draft_version_id,
    CONCAT('e', @draft_version_id, '_2'),
    CONCAT('v', @draft_version_id, '_approval1'),
    CONCAT('v', @draft_version_id, '_end'),
    '通过',
    NOW(),
    NOW()
);

-- 4. 修复 workflow-multi-role P6 测试的数据依赖问题
-- P6 期望草稿需求的 actions.data 不为 null，需确保有激活的工作流版本
-- 确保项目 1 有一个激活版本（如果没有，基于草稿版本创建一个）
INSERT INTO workflow_versions (
    project_id,
    version,
    name,
    description,
    definition,
    activation_status,
    is_active,
    created_by,
    created_at,
    updated_at
) 
SELECT 
    1,
    '1.0.0',
    '项目 1 激活版本',
    '用于支持 P6 测试的激活版本',
    definition,
    'active',
    1,
    created_by,
    NOW(),
    NOW()
FROM workflow_versions 
WHERE project_id = 1 
  AND activation_status = 'draft' 
LIMIT 1
ON DUPLICATE KEY UPDATE is_active = 1, activation_status = 'active';

-- 复制草稿版本的节点和连线到激活版本（简化处理，仅用于测试）
SET @active_version_id = (SELECT id FROM workflow_versions WHERE project_id = 1 AND is_active = 1 LIMIT 1);

INSERT INTO workflow_nodes (
    workflow_version_id,
    node_id,
    node_type,
    node_name,
    position_x,
    position_y,
    config,
    created_at,
    updated_at
)
SELECT 
    @active_version_id,
    REPLACE(node_id, CONCAT('v', workflow_version_id), CONCAT('v', @active_version_id)),
    node_type,
    node_name,
    position_x,
    position_y,
    CASE 
        WHEN node_type = 'approval' THEN '{"approvalType":"single","assignee":[{"type":"user","id":1}],"statusCode":"in_review"}'
        ELSE config
    END,
    NOW(),
    NOW()
FROM workflow_nodes 
WHERE workflow_version_id = @draft_version_id
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO workflow_edges (
    workflow_version_id,
    edge_id,
    source_node_id,
    target_node_id,
    label,
    created_at,
    updated_at
)
SELECT 
    @active_version_id,
    REPLACE(edge_id, CONCAT('e', workflow_version_id), CONCAT('e', @active_version_id)),
    REPLACE(source_node_id, CONCAT('v', workflow_version_id), CONCAT('v', @active_version_id)),
    REPLACE(target_node_id, CONCAT('v', workflow_version_id), CONCAT('v', @active_version_id)),
    label,
    NOW(),
    NOW()
FROM workflow_edges 
WHERE workflow_version_id = @draft_version_id
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 输出提示
SELECT 
    '测试数据初始化完成' AS status,
    @draft_version_id AS draft_version_id,
    @active_version_id AS active_version_id;
