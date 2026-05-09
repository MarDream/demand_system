# 工作流可视化配置

本文档维护工作流可视化配置功能的使用、接口和约束说明。

## 功能范围

基于 LogicFlow 实现工作流可视化编辑器，支持拖拽节点、配置连线、保存草稿、提交审核、版本管理和启用版本。

节点拖拽依赖 LogicFlow 的 `lf.dnd.startDrag`。左侧节点类型按下后会进入拖拽状态，拖入画布并松开鼠标后创建节点。

路由：

- 列表页：`/system/workflow-config`
- 编辑器页：`/system/workflow-config/editor`

权限：

- `admin`
- `workflow:config`

## 节点类型

| 类型 | 说明 |
| --- | --- |
| `start` | 开始节点 |
| `approval` | 审批节点 |
| `cc` | 抄送节点 |
| `condition` | 条件节点 |
| `end` | 结束节点 |

## 处理人类型

| 类型 | 说明 |
| --- | --- |
| `SPECIFIED_USER` | 指定用户 |
| `SPECIFIED_ROLE` | 指定角色 |
| `SPECIFIED_POSITION` | 指定岗位 |
| `INITIATOR` | 发起人 |
| `SUPERIOR` | 上级领导 |

## 主要流程

创建工作流：

1. 进入工作流配置列表。
2. 点击新建工作流。
3. 从左侧工具栏按住节点类型并拖拽到画布，松开鼠标后生成节点。
4. 从节点边缘锚点拖向目标节点，生成流转连线。
5. 点击节点，在右侧面板配置节点名称、处理人、超时动作和条件说明。
6. 点击连线，在右侧面板配置连线标签和条件表达式。
7. 保存草稿。
8. 确认流程完整后提交审核。

启用版本：

1. 在版本列表选择目标版本。
2. 执行启用操作。
3. 后端校验版本完整性和状态兼容性。
4. 启用成功后该版本成为当前生效版本。

## 前端文件

```text
demand_frontend/src/api/modules/workflow-visual.ts
demand_frontend/src/types/workflow-visual.d.ts
demand_frontend/src/views/system/workflow-config/index.vue
demand_frontend/src/views/system/workflow-config/editor.vue
demand_frontend/src/views/system/workflow-config/logicflow-config.ts
```

## 后端接口

工作流配置：

- `GET /api/v1/workflows/{projectId}/config`
- `POST /api/v1/workflows/{projectId}/config`
- `POST /api/v1/workflows/{projectId}/publish`
- `GET /api/v1/workflows/{projectId}/versions`
- `GET /api/v1/workflows/versions/{versionId}`

审批：

- `GET /api/v1/workflow-approvals/pending`
- `POST /api/v1/workflow-approvals/{id}/approve`
- `POST /api/v1/workflow-approvals/{id}/reject`

响应格式统一为 `{ code, message, data }`。

## 数据结构

节点：

```typescript
interface WorkflowNodeDTO {
  nodeId: string
  nodeType: 'start' | 'approval' | 'cc' | 'condition' | 'end'
  nodeName: string
  positionX: number
  positionY: number
  assigneeType?: string
  assigneeRoleId?: number
  assigneeUserIds?: number[]
  timeoutHours?: number
  timeoutAction?: string
  properties?: Record<string, unknown>
}
```

连线：

```typescript
interface WorkflowEdgeDTO {
  edgeId: string
  sourceNodeId: string
  targetNodeId: string
  label?: string
  condition?: Record<string, unknown>
  properties?: Record<string, unknown>
}
```

## 约束

- 工作流配置是版本化数据，保存草稿不直接影响运行态。
- 启用版本前必须校验流程完整性。
- 启用新版本时，如果现有需求状态无法映射到新版本节点，必须阻止启用。
- 权限校验需要覆盖配置权限、审批权限和需求流转执行权限。
- 当前如果代码仍使用固定项目 ID，需要在后续迭代改为从上下文或路由参数获取。

## 示例工作流数据

以下示例描述一个需求从提交、产品经理审批、技术评审、条件分支到上线或驳回的流程。可作为 `POST /api/v1/workflows/{projectId}/config` 的请求体参考。

```json
{
  "nodes": [
    {
      "nodeId": "start_submit",
      "nodeType": "start",
      "nodeName": "提交需求",
      "positionX": 120,
      "positionY": 220,
      "properties": {
        "nodeId": "start_submit",
        "nodeType": "start",
        "nodeName": "提交需求"
      }
    },
    {
      "nodeId": "approval_pm",
      "nodeType": "approval",
      "nodeName": "产品经理审批",
      "positionX": 320,
      "positionY": 220,
      "assigneeType": "SPECIFIED_ROLE",
      "assigneeRoleId": 2,
      "timeoutHours": 24,
      "timeoutAction": "ESCALATE",
      "properties": {
        "nodeId": "approval_pm",
        "nodeType": "approval",
        "nodeName": "产品经理审批"
      }
    },
    {
      "nodeId": "approval_tech",
      "nodeType": "approval",
      "nodeName": "技术评审",
      "positionX": 540,
      "positionY": 220,
      "assigneeType": "SPECIFIED_ROLE",
      "assigneeRoleId": 3,
      "timeoutHours": 48,
      "timeoutAction": "AUTO_REJECT",
      "properties": {
        "nodeId": "approval_tech",
        "nodeType": "approval",
        "nodeName": "技术评审"
      }
    },
    {
      "nodeId": "condition_priority",
      "nodeType": "condition",
      "nodeName": "优先级判断",
      "positionX": 760,
      "positionY": 220,
      "properties": {
        "nodeId": "condition_priority",
        "nodeType": "condition",
        "nodeName": "优先级判断",
        "conditionDesc": "高优先级进入快速排期，其他需求进入普通排期"
      }
    },
    {
      "nodeId": "approval_fast_schedule",
      "nodeType": "approval",
      "nodeName": "快速排期确认",
      "positionX": 980,
      "positionY": 140,
      "assigneeType": "SPECIFIED_ROLE",
      "assigneeRoleId": 1,
      "timeoutHours": 12,
      "timeoutAction": "AUTO_APPROVE",
      "properties": {
        "nodeId": "approval_fast_schedule",
        "nodeType": "approval",
        "nodeName": "快速排期确认"
      }
    },
    {
      "nodeId": "approval_normal_schedule",
      "nodeType": "approval",
      "nodeName": "普通排期确认",
      "positionX": 980,
      "positionY": 300,
      "assigneeType": "SPECIFIED_ROLE",
      "assigneeRoleId": 2,
      "timeoutHours": 72,
      "timeoutAction": "ESCALATE",
      "properties": {
        "nodeId": "approval_normal_schedule",
        "nodeType": "approval",
        "nodeName": "普通排期确认"
      }
    },
    {
      "nodeId": "cc_related",
      "nodeType": "cc",
      "nodeName": "抄送相关人员",
      "positionX": 1200,
      "positionY": 220,
      "assigneeType": "SPECIFIED_ROLE",
      "assigneeRoleId": 4,
      "properties": {
        "nodeId": "cc_related",
        "nodeType": "cc",
        "nodeName": "抄送相关人员"
      }
    },
    {
      "nodeId": "end_online",
      "nodeType": "end",
      "nodeName": "进入开发上线",
      "positionX": 1420,
      "positionY": 220,
      "properties": {
        "nodeId": "end_online",
        "nodeType": "end",
        "nodeName": "进入开发上线"
      }
    },
    {
      "nodeId": "end_reject",
      "nodeType": "end",
      "nodeName": "驳回关闭",
      "positionX": 540,
      "positionY": 420,
      "properties": {
        "nodeId": "end_reject",
        "nodeType": "end",
        "nodeName": "驳回关闭"
      }
    }
  ],
  "edges": [
    {
      "edgeId": "edge_start_pm",
      "sourceNodeId": "start_submit",
      "targetNodeId": "approval_pm",
      "label": "提交",
      "condition": {},
      "properties": {
        "label": "提交"
      }
    },
    {
      "edgeId": "edge_pm_tech",
      "sourceNodeId": "approval_pm",
      "targetNodeId": "approval_tech",
      "label": "通过",
      "condition": {
        "expr": "action == 'approve'"
      },
      "properties": {
        "label": "通过",
        "condition": {
          "expr": "action == 'approve'"
        }
      }
    },
    {
      "edgeId": "edge_pm_reject",
      "sourceNodeId": "approval_pm",
      "targetNodeId": "end_reject",
      "label": "驳回",
      "condition": {
        "expr": "action == 'reject'"
      },
      "properties": {
        "label": "驳回",
        "condition": {
          "expr": "action == 'reject'"
        }
      }
    },
    {
      "edgeId": "edge_tech_condition",
      "sourceNodeId": "approval_tech",
      "targetNodeId": "condition_priority",
      "label": "评审通过",
      "condition": {
        "expr": "action == 'approve'"
      },
      "properties": {
        "label": "评审通过",
        "condition": {
          "expr": "action == 'approve'"
        }
      }
    },
    {
      "edgeId": "edge_high_fast",
      "sourceNodeId": "condition_priority",
      "targetNodeId": "approval_fast_schedule",
      "label": "高优先级",
      "condition": {
        "expr": "priority == 'HIGH'"
      },
      "properties": {
        "label": "高优先级",
        "condition": {
          "expr": "priority == 'HIGH'"
        }
      }
    },
    {
      "edgeId": "edge_normal_schedule",
      "sourceNodeId": "condition_priority",
      "targetNodeId": "approval_normal_schedule",
      "label": "普通优先级",
      "condition": {
        "expr": "priority != 'HIGH'"
      },
      "properties": {
        "label": "普通优先级",
        "condition": {
          "expr": "priority != 'HIGH'"
        }
      }
    },
    {
      "edgeId": "edge_fast_cc",
      "sourceNodeId": "approval_fast_schedule",
      "targetNodeId": "cc_related",
      "label": "确认",
      "condition": {},
      "properties": {
        "label": "确认"
      }
    },
    {
      "edgeId": "edge_normal_cc",
      "sourceNodeId": "approval_normal_schedule",
      "targetNodeId": "cc_related",
      "label": "确认",
      "condition": {},
      "properties": {
        "label": "确认"
      }
    },
    {
      "edgeId": "edge_cc_end",
      "sourceNodeId": "cc_related",
      "targetNodeId": "end_online",
      "label": "完成",
      "condition": {},
      "properties": {
        "label": "完成"
      }
    }
  ]
}
```

## 测试重点

- 创建、编辑、查看工作流。
- 添加、删除节点和连线。
- 保存草稿、提交审核、审批通过、启用版本。
- 孤立节点、无开始节点、无结束节点、非法连线等异常流程。
- 大型流程图下的渲染性能。
