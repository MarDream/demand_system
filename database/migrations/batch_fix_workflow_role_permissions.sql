-- ============================================
-- 批量修复工作流角色的RBAC权限
-- ============================================
-- 用途：为所有被工作流引用的角色添加必需的操作权限
-- 执行时机：首次部署权限验证功能时，或发现权限缺失时
-- ============================================

SET @submit_permission_id = (SELECT id FROM sys_permissions WHERE code = 'button:requirement:submit');
SET @comment_permission_id = (SELECT id FROM sys_permissions WHERE code = 'button:requirement:comment');
SET @rollback_permission_id = (SELECT id FROM sys_permissions WHERE code = 'button:requirement:rollback');

-- ============================================
-- 1. 为所有工作流引用的角色添加基础权限
-- ============================================
-- 查找被工作流引用的角色
SELECT '=== 被工作流引用的角色 ===' AS step;
SELECT DISTINCT
    r.id,
    r.code,
    r.name,
    COUNT(DISTINCT wn.id) AS node_count,
    COUNT(DISTINCT wv.id) AS version_count
FROM roles r
JOIN workflow_nodes wn ON wn.assignee_role_id = r.id
JOIN workflow_versions wv ON wv.id = wn.workflow_version_id
WHERE wv.is_active = 1
  AND wn.assignee_type = 'SPECIFIED_ROLE'
  AND r.deleted_at = 0
GROUP BY r.id, r.code, r.name
ORDER BY node_count DESC;

-- 为这些角色添加 button:requirement:submit 权限
SELECT '=== 添加 button:requirement:submit 权限 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT DISTINCT
    r.id,
    @submit_permission_id,
    NOW()
FROM roles r
JOIN workflow_nodes wn ON wn.assignee_role_id = r.id
JOIN workflow_versions wv ON wv.id = wn.workflow_version_id
WHERE wv.is_active = 1
  AND wn.assignee_type = 'SPECIFIED_ROLE'
  AND r.deleted_at = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = @submit_permission_id
  );

SELECT ROW_COUNT() AS added_submit_permissions;

-- ============================================
-- 2. 按角色类型应用权限模板
-- ============================================

-- DEMAND_OPS (运维需求分析员)
SELECT '=== 为 DEMAND_OPS 角色应用权限模板 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    NOW()
FROM roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'DEMAND_OPS'
  AND r.deleted_at = 0
  AND p.code IN (
    'menu:requirement',
    'menu:requirement:view:all',
    'menu:requirement:view:pending',
    'menu:requirement:view:done',
    'menu:requirement:view:follow',
    'button:requirement:submit',
    'button:requirement:comment',
    'button:requirement:rollback'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

SELECT ROW_COUNT() AS added_demand_ops_permissions;

-- DEMAND_ANALYST (业务需求分析员)
SELECT '=== 为 DEMAND_ANALYST 角色应用权限模板 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    NOW()
FROM roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'DEMAND_ANALYST'
  AND r.deleted_at = 0
  AND p.code IN (
    'menu:requirement',
    'menu:requirement:view:all',
    'menu:requirement:view:pending',
    'menu:requirement:view:done',
    'menu:requirement:view:draft',
    'menu:requirement:view:follow',
    'button:requirement:create',
    'button:requirement:submit',
    'button:requirement:update',
    'button:requirement:draft',
    'button:requirement:comment',
    'button:requirement:split'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

SELECT ROW_COUNT() AS added_demand_analyst_permissions;

-- DEVELOPER (开发人员)
SELECT '=== 为 DEVELOPER 角色应用权限模板 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    NOW()
FROM roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'DEVELOPER'
  AND r.deleted_at = 0
  AND p.code IN (
    'menu:requirement',
    'menu:requirement:view:pending',
    'menu:requirement:view:done',
    'button:requirement:submit',
    'button:requirement:comment'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

SELECT ROW_COUNT() AS added_developer_permissions;

-- TESTER (测试人员)
SELECT '=== 为 TESTER 角色应用权限模板 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    NOW()
FROM roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'TESTER'
  AND r.deleted_at = 0
  AND p.code IN (
    'menu:requirement',
    'menu:requirement:view:pending',
    'menu:requirement:view:done',
    'button:requirement:submit',
    'button:requirement:comment'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

SELECT ROW_COUNT() AS added_tester_permissions;

-- PRODUCT_MANAGER (产品经理)
SELECT '=== 为 PRODUCT_MANAGER 角色应用权限模板 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    NOW()
FROM roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'PRODUCT_MANAGER'
  AND r.deleted_at = 0
  AND p.code IN (
    'menu:requirement',
    'menu:requirement:view:all',
    'menu:requirement:view:pending',
    'menu:requirement:view:done',
    'menu:requirement:view:draft',
    'menu:requirement:view:follow',
    'button:requirement:create',
    'button:requirement:submit',
    'button:requirement:update',
    'button:requirement:draft',
    'button:requirement:comment',
    'button:requirement:split',
    'button:requirement:cancel'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

SELECT ROW_COUNT() AS added_product_manager_permissions;

-- PROJECT_MANAGER (项目经理)
SELECT '=== 为 PROJECT_MANAGER 角色应用权限模板 ===' AS step;
INSERT INTO sys_role_permissions (role_id, permission_id, created_at)
SELECT
    r.id,
    p.id,
    NOW()
FROM roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'PROJECT_MANAGER'
  AND r.deleted_at = 0
  AND p.code IN (
    'menu:requirement',
    'menu:requirement:view:all',
    'menu:requirement:view:pending',
    'menu:requirement:view:done',
    'menu:requirement:view:follow',
    'button:requirement:submit',
    'button:requirement:comment',
    'button:requirement:export'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permissions rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

SELECT ROW_COUNT() AS added_project_manager_permissions;

-- ============================================
-- 3. 验证修复结果
-- ============================================
SELECT '=== 验证：被工作流引用的角色权限配置 ===' AS step;
SELECT
    r.code AS role_code,
    r.name AS role_name,
    GROUP_CONCAT(p.code ORDER BY p.code SEPARATOR ', ') AS permissions,
    COUNT(p.id) AS permission_count
FROM roles r
JOIN workflow_nodes wn ON wn.assignee_role_id = r.id
JOIN workflow_versions wv ON wv.id = wn.workflow_version_id
LEFT JOIN sys_role_permissions rp ON rp.role_id = r.id
LEFT JOIN sys_permissions p ON p.id = rp.permission_id AND p.type = 'BUTTON'
WHERE wv.is_active = 1
  AND wn.assignee_type = 'SPECIFIED_ROLE'
  AND r.deleted_at = 0
GROUP BY r.id, r.code, r.name
ORDER BY r.code;

SELECT '=== 完成 ===' AS step;
