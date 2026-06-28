// 条件规则
export interface ConditionRule {
  field: string
  operator: 'eq' | 'ne' | 'in' | 'notIn' | 'isEmpty' | 'notEmpty' | 'matches'
  value: string | string[]
}

// 条件配置（边上的条件数据）
export interface ConditionConfig {
  logic: 'AND' | 'OR'
  rules: ConditionRule[]
  expr?: string  // 只读预览，向后兼容
}

// 条件分支（条件节点面板使用）
export interface ConditionBranch {
  edgeId: string
  targetNodeId: string
  targetNodeName: string
  label: string
  condition: ConditionConfig
}

// LogicFlow 工作流可视化配置类型定义

export interface WorkflowNodeDTO {
  nodeId: string
  nodeType: 'start' | 'approval' | 'cc' | 'condition' | 'parallel' | 'end'
  nodeName: string
  positionX: number
  positionY: number
  assigneeType?: 'SPECIFIED_USER' | 'SPECIFIED_ROLE' | 'SPECIFIED_ROLE_GROUP' | 'SPECIFIED_ORG'
  assigneeRoleId?: number
  assigneeRoleGroupId?: number
  assigneeOrgId?: number
  orgScopeType?: 'current' | 'include_children'
  assigneeUserIds?: number[]
  timeoutHours?: number
  timeoutAction?: string
  properties?: Record<string, any>
}

export interface WorkflowEdgeDTO {
  edgeId: string
  sourceNodeId: string
  targetNodeId: string
  label?: string
  condition?: ConditionConfig | Record<string, any>
  properties?: Record<string, any>
}

export interface WorkflowConfigDTO {
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
  /** 有值=编辑已有草稿版本；null=新建草稿版本 */
  versionId?: number
  /** 目标版本号（前端传入或后端建议） */
  version?: string
  /** 目标版本名称 */
  versionName?: string
}

export interface WorkflowValidationIssue {
  path: string
  message: string
  severity: 'error' | 'warning' | 'info' | string
  ruleCode?: string
  fieldPath?: string
  suggestion?: string
  blocking?: boolean
}

export interface WorkflowValidationReport {
  versionId: number
  versionName: string
  version: string
  validatedAt: string
  issues: WorkflowValidationIssue[]
  errorCount: number
  warningCount: number
  infoCount: number
  canSubmit: boolean
}

export interface WorkflowVersionDTO {
  id: number
  projectId: number
  version: string
  name: string
  isActive: number
  activationStatus?: string
  runtimeHash?: string
  activatedAt?: string
  creatorId: number
  creatorName: string
  createdAt: string
  latestApprovalStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
  latestApprovalComment?: string
  latestSubmittedAt?: string
  latestApprovedAt?: string
  config?: WorkflowConfigDTO
}

export interface WorkflowVersionMetaUpdateDTO {
  version: string
  name: string
}

export interface WorkflowVersionActivationDTO {
  active: boolean
}

export interface WorkflowApprovalDTO {
  id: number
  workflowVersionId: number
  projectId: number
  projectName: string
  version: string
  versionName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submitterId: number
  submitterName: string
  submittedAt: string
  approverId?: number
  approverName?: string
  approvedAt?: string
  comment?: string
  config: WorkflowConfigDTO
}

export interface ApprovalRequestDTO {
  comment?: string
}

export interface WorkflowMigrationReport {
  markedLegacyCount: number
  backfilledInstanceCount: number
  skippedCount: number
  failedRequirementIds: number[]
}

// LogicFlow 节点数据结构
export interface LogicFlowNodeData {
  id: string
  type: string
  x: number
  y: number
  text?: {
    value: string
    x: number
    y: number
  }
  properties: WorkflowNodeDTO
}

// LogicFlow 边数据结构
export interface LogicFlowEdgeData {
  id: string
  type: string
  sourceNodeId: string
  targetNodeId: string
  startPoint?: { x: number; y: number }
  endPoint?: { x: number; y: number }
  text?: {
    value: string
    x: number
    y: number
  }
  properties: WorkflowEdgeDTO
}

// LogicFlow 图数据结构
export interface LogicFlowGraphData {
  nodes: LogicFlowNodeData[]
  edges: LogicFlowEdgeData[]
}
