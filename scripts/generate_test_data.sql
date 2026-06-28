-- ============================================================
-- 需求管理系统 - 性能测试数据生成脚本 V2（批量版）
-- 生成150,000条测试需求 + 150,000条工作流实例
--
-- 使用数字表辅助批量INSERT，效率比逐行INSERT高100倍以上
--
-- 数据分布:
--   待分析 (running, node=v17_v12_v3_c82fae9b): 100,000条
--   待评审 (running, node=v17_v12_v3_55674b5d):  20,000条
--   待确认 (running, node=v17_v12_v3_33d8b6ab):  15,000条
--   已验收 (completed)                            : 10,000条
--   已取消 (cancelled)                             :  5,000条
-- ============================================================

-- ==========================================================
-- Step 0: 创建数字辅助表（使用WITH递归CTE）
-- ==========================================================
SET SESSION cte_max_recursion_depth = 200000;

DROP TEMPORARY TABLE IF EXISTS tmp_numbers_150k;
CREATE TEMPORARY TABLE tmp_numbers_150k (n INT PRIMARY KEY);

INSERT INTO tmp_numbers_150k (n)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 150000
)
SELECT n FROM seq;

-- ==========================================================
-- Step 1: 清理历史测试数据
-- ==========================================================
SELECT 'Step 1: 清理旧数据...' AS progress;

DELETE FROM workflow_instance_transitions WHERE requirement_id > 100;
DELETE FROM workflow_instances WHERE requirement_id > 100;
DELETE FROM requirement_histories WHERE requirement_id > 100;
DELETE FROM requirement_comments WHERE requirement_id > 100;
DELETE FROM requirement_follows WHERE requirement_id > 100;
DELETE FROM custom_field_values WHERE entity_id > 100 AND entity_type = 'requirement';
DELETE FROM knowledge_documents WHERE requirement_id > 100;
DELETE FROM requirements WHERE id > 100;

-- 重置auto_increment
ALTER TABLE requirements AUTO_INCREMENT = 101;
ALTER TABLE workflow_instances AUTO_INCREMENT = 101;

SELECT '旧数据清理完成' AS progress;

-- ==========================================================
-- Step 2: 批量插入需求数据
-- ==========================================================
SELECT 'Step 2: 开始批量插入需求数据...' AS progress;

-- Phase 1: 待分析 100,000条 (n=1 to 100000)
SET @ts = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%S');
SET @fraction = LPAD(FLOOR(RAND() * 999999), 6, '0');
SET @base_no = CONCAT('BR', @ts, @fraction);

INSERT INTO requirements (
    project_id, creator_id, org_id, requirement_no, title, description,
    type, priority, status, node_status, is_draft, deleted_at,
    creator_role_codes, legacy_workflow, order_num, version, created_at, updated_at
)
SELECT
    1 AS project_id,
    1 AS creator_id,                          -- admin创建
    14 AS org_id,
    CONCAT(@base_no, '_PENDING_ANALYSIS_', LPAD(n, 8, '0')) AS requirement_no,
    CONCAT('[性能测试] 待分析需求-', LPAD(n, 8, '0')) AS title,
    '性能测试自动生成数据' AS description,
    'Requirement' AS type,
    CASE WHEN n % 5 = 0 THEN 'P0'
         WHEN n % 5 = 1 THEN 'P1'
         WHEN n % 5 = 2 THEN 'P2'
         WHEN n % 5 = 3 THEN 'P3'
         ELSE 'P0' END AS priority,
    '待分析' AS status,
    'PENDING_ANALYSIS' AS node_status,
    0 AS is_draft,
    0 AS deleted_at,
    '["USER","SUPER_ADMIN"]' AS creator_role_codes,
    0 AS legacy_workflow,
    0 AS order_num,
    1 AS version,
    DATE_ADD(NOW(), INTERVAL -n SECOND) AS created_at,
    NOW() AS updated_at
FROM tmp_numbers_150k
WHERE n BETWEEN 1 AND 100000;

SELECT CONCAT('Phase 1 待分析: ', ROW_COUNT(), ' 条插入完成') AS progress;

-- Phase 2: 待评审 20,000条 (n=100001 to 120000)
INSERT INTO requirements (
    project_id, creator_id, org_id, requirement_no, title, description,
    type, priority, status, node_status, is_draft, deleted_at,
    creator_role_codes, legacy_workflow, order_num, version, created_at, updated_at
)
SELECT
    1 AS project_id,
    1 AS creator_id,
    14 AS org_id,
    CONCAT(@base_no, '_PENDING_REVIEW_', LPAD(n, 8, '0')) AS requirement_no,
    CONCAT('[性能测试] 待评审需求-', LPAD(n, 8, '0')) AS title,
    '性能测试自动生成数据' AS description,
    CASE WHEN n % 10 < 7 THEN 'Requirement' WHEN n % 10 < 9 THEN 'Bug' ELSE 'Task' END AS type,
    CASE WHEN n % 5 = 0 THEN 'P0'
         WHEN n % 5 = 1 THEN 'P1'
         WHEN n % 5 = 2 THEN 'P2'
         ELSE 'P3' END AS priority,
    '待评审' AS status,
    'PENDING_REVIEW' AS node_status,
    0 AS is_draft,
    0 AS deleted_at,
    '["USER","SUPER_ADMIN"]' AS creator_role_codes,
    0 AS legacy_workflow,
    0 AS order_num,
    1 AS version,
    DATE_ADD(NOW(), INTERVAL -n SECOND) AS created_at,
    NOW() AS updated_at
FROM tmp_numbers_150k
WHERE n BETWEEN 100001 AND 120000;

SELECT CONCAT('Phase 2 待评审: ', ROW_COUNT(), ' 条插入完成') AS progress;

-- Phase 3: 待确认 15,000条 (n=120001 to 135000)
INSERT INTO requirements (
    project_id, creator_id, org_id, requirement_no, title, description,
    type, priority, status, node_status, is_draft, deleted_at,
    creator_role_codes, legacy_workflow, order_num, version, created_at, updated_at
)
SELECT
    1 AS project_id,
    1 AS creator_id,
    14 AS org_id,
    CONCAT(@base_no, '_PENDING_CONFIRM_', LPAD(n, 8, '0')) AS requirement_no,
    CONCAT('[性能测试] 待确认需求-', LPAD(n, 8, '0')) AS title,
    '性能测试自动生成数据' AS description,
    'Requirement' AS type,
    CASE WHEN n % 3 = 0 THEN 'P1' WHEN n % 3 = 1 THEN 'P2' ELSE 'P0' END AS priority,
    '待确认' AS status,
    'PENDING_CONFIRM' AS node_status,
    0 AS is_draft,
    0 AS deleted_at,
    '["USER","SUPER_ADMIN"]' AS creator_role_codes,
    0 AS legacy_workflow,
    0 AS order_num,
    1 AS version,
    DATE_ADD(NOW(), INTERVAL -n SECOND) AS created_at,
    NOW() AS updated_at
FROM tmp_numbers_150k
WHERE n BETWEEN 120001 AND 135000;

SELECT CONCAT('Phase 3 待确认: ', ROW_COUNT(), ' 条插入完成') AS progress;

-- Phase 4: 已验收 10,000条 (n=135001 to 145000)
INSERT INTO requirements (
    project_id, creator_id, org_id, requirement_no, title, description,
    type, priority, status, node_status, is_draft, deleted_at,
    creator_role_codes, legacy_workflow, order_num, version, created_at, updated_at
)
SELECT
    1 AS project_id,
    1 AS creator_id,
    14 AS org_id,
    CONCAT(@base_no, '_ACCEPTED_', LPAD(n, 8, '0')) AS requirement_no,
    CONCAT('[性能测试] 已验收需求-', LPAD(n, 8, '0')) AS title,
    '性能测试自动生成数据' AS description,
    'Requirement' AS type,
    CASE WHEN n % 2 = 0 THEN 'P3' ELSE 'P2' END AS priority,
    '已验收' AS status,
    'ACCEPTED' AS node_status,
    0 AS is_draft,
    0 AS deleted_at,
    '["USER","SUPER_ADMIN"]' AS creator_role_codes,
    0 AS legacy_workflow,
    0 AS order_num,
    1 AS version,
    DATE_ADD(NOW(), INTERVAL -n SECOND) AS created_at,
    NOW() AS updated_at
FROM tmp_numbers_150k
WHERE n BETWEEN 135001 AND 145000;

SELECT CONCAT('Phase 4 已验收: ', ROW_COUNT(), ' 条插入完成') AS progress;

-- Phase 5: 已取消 5,000条 (n=145001 to 150000)
INSERT INTO requirements (
    project_id, creator_id, org_id, requirement_no, title, description,
    type, priority, status, node_status, is_draft, deleted_at,
    creator_role_codes, legacy_workflow, order_num, version, created_at, updated_at
)
SELECT
    1 AS project_id,
    1 AS creator_id,
    14 AS org_id,
    CONCAT(@base_no, '_CANCELLED_', LPAD(n, 8, '0')) AS requirement_no,
    CONCAT('[性能测试] 已取消需求-', LPAD(n, 8, '0')) AS title,
    '性能测试自动生成数据' AS description,
    'Requirement' AS type,
    'P3' AS priority,
    '已取消' AS status,
    'CANCELLED' AS node_status,
    0 AS is_draft,
    0 AS deleted_at,
    '["USER","SUPER_ADMIN"]' AS creator_role_codes,
    0 AS legacy_workflow,
    0 AS order_num,
    1 AS version,
    DATE_ADD(NOW(), INTERVAL -n SECOND) AS created_at,
    NOW() AS updated_at
FROM tmp_numbers_150k
WHERE n BETWEEN 145001 AND 150000;

SELECT CONCAT('Phase 5 已取消: ', ROW_COUNT(), ' 条插入完成') AS progress;

-- ==========================================================
-- Step 3: 创建 workflow_instances
-- ==========================================================
SELECT 'Step 3: 创建工作流实例...' AS progress;

-- 为待分析需求创建running实例 (完整node_id: v17_v12_v3_c82fae9b-5ed5-4126-a295-17a517a01410)
INSERT INTO workflow_instances (requirement_id, workflow_version_id, current_node_id, status, created_at, updated_at)
SELECT r.id, 17, 'v17_v12_v3_c82fae9b-5ed5-4126-a295-17a517a01410', 'running', NOW(), NOW()
FROM requirements r
WHERE r.status = '待分析' AND r.deleted_at = 0
  AND r.workflow_instance_id IS NULL;

SELECT CONCAT('待分析workflow_instances: ', ROW_COUNT(), ' 条') AS progress;

-- 为待评审需求创建running实例 (完整node_id: v17_v12_v3_55674b5d-373d-4519-ad6d-ca3f278c29b8)
INSERT INTO workflow_instances (requirement_id, workflow_version_id, current_node_id, status, created_at, updated_at)
SELECT r.id, 17, 'v17_v12_v3_55674b5d-373d-4519-ad6d-ca3f278c29b8', 'running', NOW(), NOW()
FROM requirements r
WHERE r.status = '待评审' AND r.deleted_at = 0
  AND r.workflow_instance_id IS NULL;

SELECT CONCAT('待评审workflow_instances: ', ROW_COUNT(), ' 条') AS progress;

-- 为待确认需求创建running实例 (完整node_id: v17_v12_v3_33d8b6ab-a9c2-459a-992a-99f817584a60)
INSERT INTO workflow_instances (requirement_id, workflow_version_id, current_node_id, status, created_at, updated_at)
SELECT r.id, 17, 'v17_v12_v3_33d8b6ab-a9c2-459a-992a-99f817584a60', 'running', NOW(), NOW()
FROM requirements r
WHERE r.status = '待确认' AND r.deleted_at = 0
  AND r.workflow_instance_id IS NULL;

SELECT CONCAT('待确认workflow_instances: ', ROW_COUNT(), ' 条') AS progress;

-- 为已验收需求创建completed实例 (完整node_id: v17_v12_v3_e45e6826-b1b0-4b8f-85b9-60eab779683b)
INSERT INTO workflow_instances (requirement_id, workflow_version_id, current_node_id, status, created_at, updated_at)
SELECT r.id, 17, 'v17_v12_v3_e45e6826-b1b0-4b8f-85b9-60eab779683b', 'completed', NOW(), NOW()
FROM requirements r
WHERE r.status = '已验收' AND r.deleted_at = 0
  AND r.workflow_instance_id IS NULL;

SELECT CONCAT('已验收workflow_instances: ', ROW_COUNT(), ' 条') AS progress;

-- 为已取消需求创建cancelled实例 (完整node_id: v17_v12_v3_c82fae9b-5ed5-4126-a295-17a517a01410)
INSERT INTO workflow_instances (requirement_id, workflow_version_id, current_node_id, status, created_at, updated_at)
SELECT r.id, 17, 'v17_v12_v3_c82fae9b-5ed5-4126-a295-17a517a01410', 'cancelled', NOW(), NOW()
FROM requirements r
WHERE r.status = '已取消' AND r.deleted_at = 0
  AND r.workflow_instance_id IS NULL;

SELECT CONCAT('已取消workflow_instances: ', ROW_COUNT(), ' 条') AS progress;

-- ==========================================================
-- Step 4: 回填 workflow_instance_id
-- ==========================================================
SELECT 'Step 4: 回填workflow_instance_id...' AS progress;

UPDATE requirements r
INNER JOIN workflow_instances wi ON wi.requirement_id = r.id
SET r.workflow_instance_id = wi.id
WHERE r.id > 100 AND r.workflow_instance_id IS NULL;

SELECT CONCAT('回填完成，影响 ', ROW_COUNT(), ' 条记录') AS progress;

-- ==========================================================
-- Step 5: 分析索引统计信息
-- ==========================================================
SELECT 'Step 5: 更新索引统计...' AS progress;
ANALYZE TABLE requirements;
ANALYZE TABLE workflow_instances;
ANALYZE TABLE workflow_node_assignees;

-- ==========================================================
-- Step 6: 最终统计
-- ==========================================================
SELECT '=== 生成完成 ===' AS summary;

SELECT 'requirements 统计' AS info;
SELECT status, COUNT(*) AS record_count
FROM requirements
WHERE id > 100
GROUP BY status WITH ROLLUP;

SELECT 'workflow_instances 统计' AS info;
SELECT wi.status, COUNT(*) AS record_count
FROM workflow_instances wi
INNER JOIN requirements r ON r.id = wi.requirement_id
WHERE r.id > 100
GROUP BY wi.status WITH ROLLUP;

-- ==========================================================
-- Step 7: 模拟wujiahua待办查询（性能测试基准）
-- ==========================================================
SELECT '=== wujiahua待办查询基准测试 ===' AS benchmark;

-- 统计wujiahua的待办总数（优化前查询 - 使用V2架构）
SELECT 
    CONCAT('wujiahua(user_id=2, role_id=9, org_id=7) 待办总数: ', COUNT(*)) AS result,
    CONCAT('耗时预估: 优化前>10秒, 优化后<3秒') AS estimate
FROM requirements r
INNER JOIN workflow_instances wi ON r.workflow_instance_id = wi.id
INNER JOIN workflow_node_assignees wna ON
    wna.workflow_version_id = wi.workflow_version_id
    AND wna.node_id = wi.current_node_id
WHERE r.deleted_at = 0
  AND r.is_draft = 0
  AND wi.status = 'running'
  AND (
    (wna.assignee_type = 'USER' AND wna.assignee_id = 2)
    OR (wna.assignee_type = 'ROLE' AND wna.assignee_id IN (9))
    OR (wna.assignee_type = 'ORG' AND wna.assignee_id IN (7))
  );

-- 数据分布明细
SELECT 'wujiahua待办数据分布' AS info;
SELECT 
    wna.assignee_type,
    COUNT(*) AS count
FROM requirements r
INNER JOIN workflow_instances wi ON r.workflow_instance_id = wi.id
INNER JOIN workflow_node_assignees wna ON
    wna.workflow_version_id = wi.workflow_version_id
    AND wna.node_id = wi.current_node_id
WHERE r.deleted_at = 0
  AND r.is_draft = 0
  AND wi.status = 'running'
  AND (
    (wna.assignee_type = 'USER' AND wna.assignee_id = 2)
    OR (wna.assignee_type = 'ROLE' AND wna.assignee_id IN (9))
    OR (wna.assignee_type = 'ORG' AND wna.assignee_id IN (7))
  )
GROUP BY wna.assignee_type;

-- ==========================================================
-- 清理辅助表
-- ==========================================================
DROP TEMPORARY TABLE IF EXISTS tmp_numbers_150k;
