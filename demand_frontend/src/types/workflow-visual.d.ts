// LogicFlow 工作流可视化配置类型定义

export interface WorkflowNodeDTO {
  nodeId: string
  nodeType: 'start' | 'approval' | 'cc' | 'condition' | 'end'
  nodeName: string
  positionX: number
  positionY: number
  assigneeType?: 'SPECIFIED_USER' | 'SPECIFIED_ROLE' | 'SPECIFIED_POSITION' | 'INITIATOR' | 'SUPERIOR'
  assigneeRoleId?: number
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
  condition?: Record<string, any>
  properties?: Record<string, any>
}

export interface WorkflowConfigDTO {
  nodes: WorkflowNodeDTO[]
  edges: WorkflowEdgeDTO[]
}

export interface WorkflowVersionDTO {
  id: number
  projectId: number
  version: number
  name: string
  isActive: number
  creatorId: number
  creatorName: string
  createdAt: string
  config?: WorkflowConfigDTO
}

export interface WorkflowApprovalDTO {
  id: number
  projectId: number
  projectName: string
  version: number
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
