# 工作流与需求审批优化方案

> 基于现有代码分析与需求梳理，形成的优化方案文档。

## 一、现状分析

### 1.1 现有工作流机制

**工作流配置结构**：
- `WorkflowVersion` - 工作流版本，支持多版本共存，仅1个激活版本
- `WorkflowNodeDefinition` - 节点定义（start/approval/cc/condition/end）
- `WorkflowEdgeDefinition` - 流转线定义（source/target/label/allowedRoles/conditions）
- `WorkflowNodePermission` - 节点权限配置（allowedRoles/allowedUsers/assigneeRule）

**节点处理人类型**（已实现）：
- `SPECIFIED_USER` - 指定用户
- `SPECIFIED_ROLE` - 指定角色
- `SPECIFIED_ROLE_GROUP` - 指定角色组
- `SPECIFIED_ORG` - 指定组织（含层级范围）

**需求状态管理**：
- `isDraft` - 草稿标识
- `workflowInstanceId` - 工作流实例ID
- `nodeStatus` - 节点状态

### 1.2 现有待办/已办/草稿机制

| 视图 | API | 筛选条件 | 操作类型 |
|------|-----|---------|---------|
| 全部 | `getRequirementList` | 无 | edit/view |
| 草稿 | `getMyRequirementDrafts` | isDraft=true | edit |
| 待办 | `getMyRequirementPending` | 有权限处理 | approve |
| 已办 | `getMyRequirementDone` | 已处理过 | view |

### 1.3 现有评审意见机制

**存储表**：`requirement_approval_evaluation`

**展示位置**：`detail.vue` 审批评价Tab

**展示形式**：时间轴，包含评审人、节点名称、星级评分(1-5)、意见内容

---

## 二、需求梳理

### 2.1 工作流单版本启用

**需求**：工作流配置中仅启用1个当前正在使用的工作流。

**现状**：已实现 - `WorkflowVersion.isActive` 控制，仅1个激活版本。

### 2.2 完整流程覆盖

**需求**：工作流从开始节点经历多个审批节点流转到结束节点，此过程也是需求从创建到办结的完整过程。

**结论**：工作流从"提交"开始，创建草稿不算入工作流流程。

**流程定义**：
```
DRAFT(草稿) → [点击提交] → 工作流流转 → ... → END(办结)
```

**说明**：
- 创建需求时进入"草稿"状态，存储在 `requirements.is_draft = true`
- 点击提交后，工作流初始化，进入流转状态
- 工作流覆盖范围：提交 → 审批节点1 → 审批节点2 → ... → 结束节点

### 2.3 节点处理人权限控制

**需求**：每个节点配置处理人类型，仅该类型的人/组织/角色拥有审批权限。

**现状**：已实现 `WorkflowNodeDTO.assigneeType` + `assigneeRoleId/assigneeOrgId/assigneeUserIds`。

**决策**：
- 处理人类型支持动态类型 `PREV_APPROVER`（上一节点处理人）
- 组织类型层级：include_children（继承用户管理中的组织层级）

### 2.4 我的待办权限可见性

**需求**：
- 未办结需求：拥有权限的用户可在"我的待办"可见
- 其他用户：我的待办不可见

**现状**：`getMyRequirementPending` 返回有权限处理的需求。

**优化方向**：
- 后端：根据当前用户权限过滤返回列表
- 前端：我的待办Tab仅显示当前用户有审批权的任务

### 2.5 我的已办可见性

**需求**：
- 已办结需求在"我的已办"可见
- 凡参与过需求任何阶段的用户，需求办结时均可见

**结论**："参与"定义 = 创建或审核过该需求

**参与人判定**：
| 操作 | 是否算参与 |
|------|----------|
| 创建需求（提交进入工作流） | ✅ 是 |
| 在任意节点审批通过 | ✅ 是 |
| 在任意节点审批驳回 | ✅ 是 |
| 仅查看需求（无审批操作） | ❌ 否 |

**全生命周期可见**：满足"参与"条件的用户，自参与时刻起至需求办结后，可在"我的已办"查看该需求完整流转记录。

### 2.6 我的草稿功能

**需求**："我的草稿"用于临时存储需求提出人还未流转的需求。

**决策**：
- 草稿列表仅显示"我创建的"草稿（需求提出人本人）
- 草稿删除机制：用户可删除自己创建的草稿，删除后物理删除
- 草稿不支持"分配给我"逻辑（草稿在提交前未进入工作流，无分配概念）

### 2.7 提交流转与审核提交

**需求**：
- 创建完点击提交开始，需求进入工作流
- 进入工作流后，所有操作显示"审核"作为提交

**现状分析**：
- 现状：`createRequirementDraft` 创建草稿 → `submitRequirementDraft` 提交流转
- 进入工作流后通过 `executeTransition` 执行流转

**优化方向**：
- 统一"提交"语义：所有操作（创建、审批、回退）都通过同一提交接口
- 前端操作按钮文案统一为"提交审核"

### 2.8 评审意见存储与展示

**需求**：
- 每个节点的审核意见均随需求存储
- 后续审核人可看到所有评审意见
- 按时间轴显示在评审页面
- 包含评审人、评审时间、审核意见
- 审核页面默认显示当前需求状态

**现状**：
- `RequirementApprovalEvaluation` 已存储评审意见
- 详情页 `detail.vue` 已展示时间轴

**结论**：驳回意见与通过意见需要区分显示，通过时为绿色，驳回时为红色。

---

## 三、优化方案

### 3.1 工作流定义优化

#### 3.1.1 工作流版本管理

```
WorkflowVersion 表结构：
- id, projectId, version, name, definition, isActive
- 约束：同一 projectId 下 isActive=1 的记录仅能有1条
```

#### 3.1.2 工作流结构定义

```typescript
interface WorkflowDefinition {
  nodes: {
    [nodeId: string]: {
      id: string
      name: string
      type: 'START' | 'APPROVAL' | 'END'
      assigneeType: 'ROLE' | 'ROLE_GROUP' | 'ORG' | 'USER' | 'PREV_APPROVER'
      assigneeId?: number        // 角色ID/角色组ID/组织ID/用户ID
      assigneeOrgScope?: 'CURRENT' | 'INCLUDE_CHILDREN'
      isFinal: boolean
      sortOrder: number
    }
  }
  edges: {
    [fromNodeId: string]: {
      [toNodeId: string]: {
        label?: string
        conditions?: Record<string, any>
      }
    }
  }
}
```

### 3.2 需求状态机优化

#### 3.2.1 需求状态流转

```
DRAFT(草稿) 
    ↓ [提交]
PENDING_WORKFLOW(待流转)
    ↓ [工作流初始化]
IN_WORKFLOW(流转中) → Node1 → Node2 → ... → END(办结)
```

#### 3.2.2 需求表关键字段

```
requirements 表：
- id, project_id, title, description, type, priority, status
- creator_id, assignee_id
- workflow_instance_id
- current_node_id          -- 当前所处节点ID
- is_draft                 -- 是否草稿
- created_at, updated_at
```

### 3.3 评审意见存储优化

#### 3.3.1 评审意见表

```sql
CREATE TABLE requirement_approval_record (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  requirement_id    BIGINT NOT NULL COMMENT '需求ID',
  workflow_instance_id BIGINT COMMENT '工作流实例ID',
  node_id           VARCHAR(64) NOT NULL COMMENT '节点ID',
  node_name         VARCHAR(128) COMMENT '节点名称',
  action            VARCHAR(32) NOT NULL COMMENT 'APPROVE/REJECT/SUBMIT',
  result            VARCHAR(32) COMMENT 'PASS/REJECT 结果',
  comment           TEXT NOT NULL COMMENT '审核意见',
  parent_id         BIGINT COMMENT '回复的父记录ID(用于补充编辑)',
  is_supplement     TINYINT(1) DEFAULT 0 COMMENT '是否为补充意见',
  operator_id       BIGINT NOT NULL COMMENT '操作人',
  operator_name     VARCHAR(64) COMMENT '操作人名称',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_requirement_id (requirement_id),
  INDEX idx_parent_id (parent_id),
  INDEX idx_operator_id (operator_id),
  INDEX idx_created (created_at)
) COMMENT '需求评审意见表';
```

**设计说明**：
- `parent_id`：支持补充编辑，补充意见指向原始评审记录
- `is_supplement`：标记是否为补充意见
- 不可删除：仅设置 `deleted_at` 软删除标记，不物理删除
- 不可修改原始意见：只有 `is_supplement=1` 的补充意见可追加

#### 3.3.2 评审意见展示

```
评审页面展示：
┌─────────────────────────────────────────────────┐
│ 评审时间线                                        │
├─────────────────────────────────────────────────┤
│ 🟢通过  │ 张三    │ 2026-05-25 10:30            │
│         │ 节点：需求审批                        │
│         │ 意见：同意该需求实施                  │
│         │ [补充意见]                           │
├─────────────────────────────────────────────────┤
│ 🔴驳回  │ 李四    │ 2026-05-25 09:15            │
│         │ 节点：需求评估                        │
│         │ 意见：优先级需要调整，请重新提交       │
│         │ ┌───────────────────────────────┐    │
│         │ │ 💬 补充：已调整为高优先级     │    │
│         │ │     张三 2026-05-25 10:00    │    │
│         │ └───────────────────────────────┘    │
├─────────────────────────────────────────────────┤
│ 🟢通过  │ 王五    │ 2026-05-25 08:00            │
│         │ 节点：提交                            │
│         │ 意见：提交需求申请                    │
└─────────────────────────────────────────────────┘

图例：🟢通过(绿色)  🔴驳回(红色)  💬补充(蓝色)
```

### 3.4 我的待办/已办优化

#### 3.4.1 我的待办逻辑

```sql
-- 我的待办：当前节点处理人包含当前用户的需求
SELECT r.* 
FROM requirements r
JOIN workflow_instance wi ON r.workflow_instance_id = wi.id
JOIN workflow_node_assignment wna ON wna.instance_id = wi.id AND wna.node_id = r.current_node_id
WHERE wna.assignee_type = 'USER' AND wna.assignee_id = #{currentUserId}
   OR wna.assignee_type = 'ROLE' AND EXISTS (
       SELECT 1 FROM user_role ur WHERE ur.user_id = #{currentUserId} AND ur.role_id = wna.assignee_id
   )
   OR wna.assignee_type = 'ORG' AND EXISTS (
       SELECT 1 FROM user_org uo WHERE uo.user_id = #{currentUserId} AND uo.org_id = wna.assignee_id
   )
   -- 排除已办结
   AND r.current_node_id != (SELECT node_id FROM workflow_node WHERE type = 'END')
```

#### 3.4.2 我的已办逻辑

```sql
-- 我的已办：曾是任意节点处理人的已办结需求
SELECT DISTINCT r.*
FROM requirements r
JOIN workflow_instance wi ON r.workflow_instance_id = wi.id
JOIN workflow_node_assignment wna ON wna.instance_id = wi.id
WHERE wna.participant_user_id = #{currentUserId}
  AND r.current_node_id = (SELECT node_id FROM workflow_node WHERE type = 'END')
ORDER BY r.updated_at DESC
```

#### 3.4.3 参与人记录表

```sql
CREATE TABLE workflow_participant (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id       BIGINT NOT NULL,
  node_id           VARCHAR(64) NOT NULL,
  user_id           BIGINT NOT NULL,
  action            VARCHAR(32) DEFAULT 'PARTICIPATED',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_instance_node_user (instance_id, node_id, user_id)
);
```

### 3.5 前端界面优化

#### 3.5.1 需求列表视图切换

```typescript
interface ViewMode {
  value: 'all' | 'drafts' | 'pending' | 'done'
  label: string
  api: string
  badge?: 'count'  // 待办数量角标
}

// 视图配置
const VIEW_MODES = [
  { value: 'all', label: '全部需求', api: 'getRequirementList' },
  { value: 'drafts', label: '我的草稿', api: 'getMyRequirementDrafts', badge: 'draftCount' },
  { value: 'pending', label: '我的待办', api: 'getMyRequirementPending', badge: 'pendingCount' },
  { value: 'done', label: '我的已办', api: 'getMyRequirementDone', badge: 'doneCount' }
]
```

#### 3.5.2 统一审核提交按钮

```
详情页操作按钮：
┌──────────┐ ┌──────────┐ ┌──────────┐
│  [提交]  │ │  [驳回]  │ │  [取消]  │
└──────────┘ └──────────┘ └──────────┘

* 提交：执行流转到下一节点（需要选择目标节点）
* 驳回：执行流转回上一节点（仅能驳回到上一节点，不支持任意节点）
* 取消：取消整个工作流（需管理员权限）
```

**驳回规则**：
- 仅能驳回到当前节点的上一节点
- 驳回时必须填写驳回意见
- 驳回后需求流转至上一节点，由上一节点处理人重新审批

#### 3.5.3 审核对话框

```
┌─────────────────────────────────────────────────┐
│ 审核操作                                    [X]  │
├─────────────────────────────────────────────────┤
│ 当前节点：需求评估                              │
│ 目标节点：[下拉选择 ▼]                          │
│                                                 │
│ 审核意见：                                      │
│ ┌─────────────────────────────────────────────┐ │
│ │ 请输入审核意见...                            │ │
│ └─────────────────────────────────────────────┘ │
│                                                 │
│      [取消]                    [确认提交]       │
└─────────────────────────────────────────────────┘
```

---

## 六、业界工作流设计模式参考

### 6.1 状态机与事件驱动模式

**核心思想**：工作流本质是一个状态机，每个节点是一个状态，边是状态转换事件。

```
┌─────────┐    submit     ┌─────────┐    approve    ┌─────────┐
│  DRAFT  │ ──────────→ │ PENDING │ ──────────→ │ APPROVED │
└─────────┘              └─────────┘              └─────────┘
     │                        │                        │
     │ cancel                 │ reject                  │ reject
     ↓                        ↓                        ↓
┌─────────┐              ┌─────────┐              ┌─────────┐
│CANCELLED│              │ REJECTED│              │ REJECTED│
└─────────┘              └─────────┘              └─────────┘
```

**设计要点**：
1. 状态转换必须原子性（事务保证）
2. 每个转换必须记录事件日志
3. 转换前的合法性校验（权限、条件）
4. 转换后的副作用处理（通知、触发）

### 6.2 参与者-角色-权限模型

**核心思想**：将"人"的概念抽象为"参与者"，通过角色间接关联。

```
        ┌──────────────┐
        │   Workflow    │
        │   Instance   │
        └──────┬───────┘
               │
        ┌──────▼───────┐
        │  Node Config │  (节点配置)
        └──────┬───────┘
               │
        ┌──────▼───────┐
        │  Assignee    │  (分配规则)
        │  Type/ID     │
        └──────┬───────┘
               │
        ┌──────▼───────┐
        │  Resolved    │  (解析为实际用户)
        │  User List   │
        └──────────────┘
```

**分配规则类型**：
| 类型 | 说明 | 示例 |
|------|------|------|
| `SPECIFIC_USER` | 指定具体用户 | 张三 |
| `ROLE` | 指定角色 | 需求经理 |
| `ROLE_GROUP` | 指定角色组 | 项目经理组 |
| `ORG` | 指定组织 | 技术部 |
| `PREV_APPROVER` | 上一节点审批人 | 动态追溯 |
| `CREATOR` | 创建人 | 需求提交者 |

### 6.3 参与人追溯机制

**核心思想**：通过"参与者记录表"追踪谁在哪个节点做了什么。

```sql
-- 参与者记录表（核心）
CREATE TABLE workflow_history (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id     BIGINT NOT NULL COMMENT '工作流实例ID',
  requirement_id  BIGINT NOT NULL COMMENT '需求ID',
  node_id         VARCHAR(64) NOT NULL COMMENT '节点ID',
  node_name       VARCHAR(128) COMMENT '节点名称',
  user_id         BIGINT NOT NULL COMMENT '操作用户ID',
  user_name       VARCHAR(64) COMMENT '操作用户名称',
  action          VARCHAR(32) NOT NULL COMMENT 'APPROVE/REJECT/SUBMIT/RETURN',
  action_display  VARCHAR(64) COMMENT '操作显示名',
  comment         TEXT COMMENT '审核意见',
  result          VARCHAR(32) COMMENT 'PASS/REJECT',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_instance (instance_id),
  INDEX idx_requirement (requirement_id),
  INDEX idx_user (user_id),
  INDEX idx_created (created_at)
) COMMENT '工作流历史记录表';
```

**查询"我的已办"逻辑**：
```sql
SELECT DISTINCT r.*
FROM requirements r
JOIN workflow_history wh ON r.id = wh.requirement_id
WHERE wh.user_id = #{currentUserId}
  AND r.current_node_id = (SELECT node_id FROM workflow_node WHERE type = 'END')
ORDER BY wh.created_at DESC
```

### 6.4 评审意见时间线展示

**交互设计**：
1. 按时间倒序排列（最新在上）
2. 结果通过显示绿色图标 + "通过"
3. 结果驳回显示红色图标 + "驳回"
4. 每次操作记录：操作人头像、操作时间、节点名称、操作结果、意见内容

**展示格式**：
```
┌─────────────────────────────────────────────────────┐
│ 评审时间线                              [筛选: 全部] │
├─────────────────────────────────────────────────────┤
│ 🟢 通过  │  张三  │  2026-05-25 10:30              │
│          │  节点：需求审批                          │
│          │  意见：同意该需求实施，计划6月上线         │
├─────────────────────────────────────────────────────┤
│ 🔴 驳回  │  李四  │  2026-05-25 09:15              │
│          │  节点：需求评估                          │
│          │  意见：优先级需要调整为高，请重新提交       │
├─────────────────────────────────────────────────────┤
│ 🟢 通过  │  王五  │  2026-05-25 08:00              │
│          │  节点：提交                              │
│          │  意见：提交需求申请                       │
└─────────────────────────────────────────────────────┘
```

### 6.5 节点超时处理机制

**设计模式**：
```sql
-- 节点超时配置
ALTER TABLE workflow_node ADD COLUMN timeout_hours INT DEFAULT NULL COMMENT '超时时长(小时)';
ALTER TABLE workflow_node ADD COLUMN timeout_action VARCHAR(32) DEFAULT 'AUTO_PASS' COMMENT '超时动作';

-- 自动提醒定时任务
SELECT r.*, wn.timeout_hours, wh.created_at as node_enter_time
FROM requirements r
JOIN workflow_instance wi ON r.workflow_instance_id = wi.id
JOIN workflow_node wn ON wn.node_id = r.current_node_id
JOIN workflow_history wh ON wh.requirement_id = r.id 
  AND wh.node_id = r.current_node_id
  AND wh.action = 'SUBMIT'
WHERE wn.timeout_hours IS NOT NULL
  AND TIMESTAMPDIFF(HOUR, wh.created_at, NOW()) > wn.timeout_hours
  AND r.id NOT IN (SELECT requirement_id FROM workflow_timeout_notified)
```

### 6.6 并行审批与会签模式

**并行审批**：同一节点多人可同时审批，任意一人通过即流转

```
     ┌─────────────────────────────────┐
     │         需求审批(并行)            │
     │   [角色: 测试负责人] × 3人        │
     └─────────────┬───────────────────┘
                   │
      ┌────────────┼────────────┐
      ↓            ↓            ↓
   [甲审批]    [乙审批]     [丙审批]
      │            │            │
      └──OK──┬───NOT OK────┘
             │
             ↓
      继续流转(只需1人通过)
```

**会签模式**：同一节点多人必须全部通过才可流转

```sql
-- 会签配置
ALTER TABLE workflow_node ADD COLUMN approval_type VARCHAR(32) DEFAULT 'ANY' COMMENT 'ANY/ALL';

-- 会签统计查询
SELECT node_id, COUNT(*) as total, 
       SUM(CASE WHEN result = 'PASS' THEN 1 ELSE 0 END) as passed
FROM workflow_history
WHERE instance_id = #{instanceId} AND node_id = #{nodeId}
GROUP BY node_id
HAVING passed = total  -- ALL模式需要全部通过
```

---

## 七、实施计划

### Phase 1: 数据模型扩展

1. 新增 `workflow_participant` 表
2. 修改 `requirement_approval_record` 表结构
3. 修改 `requirements` 表添加 `current_node_id` 字段

### Phase 2: 后端服务实现

1. 重构工作流实例初始化逻辑
2. 实现节点权限校验服务
3. 实现"我的待办"精确筛选
4. 实现"我的已办"参与人逻辑
5. 重构评审意见存储与查询

### Phase 3: 前端界面实现

1. 统一需求列表视图切换组件
2. 优化审核对话框交互
3. 完善评审时间线展示
4. 添加待办数量角标

### Phase 4: 测试验证

1. 单元测试：工作流权限校验
2. 集成测试：完整流程测试
3. E2E测试：用户操作路径验证

---

## 五、需求决策记录

| # | 问题 | 决策 | 说明 |
|---|------|------|------|
| 1 | 工作流从何时开始 | ✅ 从"提交"开始 | 创建草稿不算入工作流 |
| 2 | "参与"定义 | ✅ 创建或审核过 | 仅创建/审批操作可见，查看不算 |
| 3 | 组织类型层级范围 | ✅ include_children + 层级深度 | 层级配置继承用户管理中的组织层级 |
| 4 | 评审意见区分显示 | ✅ 通过绿色/驳回红色 | 时间线中通过🟢驳回🔴 |
| 5 | 评审意见修改/删除 | ✅ 不可删除，可补充编辑 | 保持审计追溯完整性 |
| 6 | 回退规则 | ✅ 仅能驳回到上一节点 | 不支持任意节点回退 |

---

## 六、附录

### 6.1 关键文件清单

| 文件 | 说明 |
|------|------|
| `workflow-visual.ts` | 工作流可视化API |
| `workflow.ts` | 工作流配置API |
| `workflow-engine.ts` | 工作流引擎API |
| `requirement.ts` | 需求管理API |
| `requirements/index.vue` | 需求列表页 |
| `requirements/detail.vue` | 需求详情页 |
| `requirements/create.vue` | 需求创建页 |
| `todo/index.vue` | 待办任务页 |
| `workflow-config/editor.vue` | 工作流编辑器 |

### 6.2 现有类型定义

详见 `demand_frontend/src/types/workflow.d.ts` 和 `workflow-visual.d.ts`
