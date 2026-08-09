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
  assigneeType?: 'SPECIFIED_USER' | 'SPECIFIED_ROLE' | 'SPECIFIED_ROLE_GROUP' | 'SPECIFIED_ORG' | 'CREATOR' | 'PREV_APPROVER'
  ccMode?: 'MESSAGE' | 'READ_ONLY_TODO'
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
  /** 关联知识库ID，流转附件自动入库目标 */
  knowledgeBaseId?: number | null
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
  workflowDefinitionId?: number
  workflowDefinitionName?: string
  version: string
  name: string
  isActive: number
  activationStatus?: string
  runtimeHash?: string
  activatedAt?: string
  creatorId: number
  creatorName: string
  createdAt: string
  /** 编辑时间（最近一次保存/启停/复制等变更时间；存量数据回退为创建时间） */
  updatedAt?: string
  latestApprovalStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
  latestApprovalComment?: string
  latestSubmittedAt?: string
  latestApprovedAt?: string
  /** 最近一次发布时间（从workflow_history回填） */
  changeLog?: string
  config?: WorkflowConfigDTO
  knowledgeBaseId?: number
  knowledgeBaseName?: string
  approvalEvaluationEnabled?: boolean
  /** 保存时后端返回的校验问题列表 */
  validationIssues?: WorkflowValidationIssue[]
}

/** 工作流定义（独立工作流实体，承载工作流名称） */
export interface WorkflowDefinitionInfoDTO {
  id: number
  name: string
  projectId: number
  projectName?: string
  description?: string
  versionCount?: number
  activeVersionCount?: number
  creatorId?: number
  creatorName?: string
  createdAt?: string
}

export interface WorkflowVersionMetaUpdateDTO {
  version: string
  name: string
  knowledgeBaseId?: number | null
  approvalEvaluationEnabled?: boolean
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

// 工作流导出数据格式
export interface WorkflowExportData {
  exportVersion: string
  exportedAt: string
  exportedBy: string
  workflow: {
    name: string
    version: string
    projectId: number
    config: WorkflowConfigDTO
    metadata: {
      originalVersionId: number
      originalCreatedAt: string
      approvedAt?: string
      description?: string
    }
  }
}

// 工作流导入响应
export interface WorkflowImportResponse {
  success: boolean
  versionId: number
  version: string
  name: string
  message: string
  conflicts?: {
    nameConflict: boolean
    versionConflict: boolean
    resolvedName: string
    resolvedVersion: string
  }
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

// ============ ADR-002: 工作流迁移计划类型 ============

// 节点映射项
export interface NodeMappingItem {
  fromNodeId: string
  toNodeId: string | null
  fromNodeName: string
  toNodeName: string | null
}

// 节点映射 VO（含自动匹配标记）
export interface NodeMappingVO {
  fromNodeId: string
  fromNodeName: string
  fromNodeType: string
  toNodeId: string | null
  toNodeName: string | null
  toNodeType: string | null
  autoMatched: boolean
  skipped: boolean
}

// 目标节点选项（供前端下拉选择）
export interface TargetNodeOption {
  nodeId: string
  nodeName: string
  nodeType: string
}

// 迁移计划 VO
export interface MigrationPlanVO {
  id: number
  fromVersionId: number
  fromVersionName: string
  fromVersion: string
  toVersionId: number
  toVersionName: string
  toVersion: string
  projectId: number
  nodeMapping: NodeMappingVO[]
  unmappedNodes: NodeMappingVO[]
  toVersionNodes: TargetNodeOption[]
  status: 'draft' | 'pending' | 'executing' | 'completed' | 'failed'
  totalInstanceCount: number
  migratedCount: number
  failedCount: number
  operatorName: string
  remark: string
  createdAt: string
}

// 创建迁移计划请求
export interface CreateMigrationPlanRequest {
  fromVersionId: number
  toVersionId: number
  remark?: string
}

// 迁移预检结果
export interface MigrationPreviewVO {
  totalInstances: number
  canMigrateCount: number
  needManualCount: number
  items: MigrationPreviewItem[]
}

export interface MigrationPreviewItem {
  instanceId: number
  requirementId: number
  currentNodeId: string
  currentNodeName: string
  mapped: boolean
  mappedToNodeId: string | null
  mappedToNodeName: string | null
}

// 迁移执行结果
export interface MigrationResultDTO {
  totalCount: number
  successCount: number
  failedCount: number
  message: string
  planId: number
  warnings: string[]
}

// 迁移日志 VO
export interface WorkflowMigrationLogVO {
  id: number
  fromVersionId: number
  toVersionId: number
  fromNodeId: string | null
  toNodeId: string | null
  fromNodeName: string | null
  toNodeName: string | null
  requirementId: number
  instanceId: number | null
  planId: number | null
  migrationType: string
  migrationStatus: string
  errorMessage: string | null
  operatorId: number | null
  createdAt: string
}
