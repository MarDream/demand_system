export interface WorkflowState {
  id: number
  projectId: number
  name: string
  color: string
  isFinal: boolean | number
  sortOrder: number
}

export interface WorkflowTransition {
  id: number
  projectId: number
  fromStateId: number
  toStateId: number
  allowedRoles?: string[] | string | null
  requiredFields?: string[] | string | null
  conditions?: Record<string, unknown> | string | null
}

export interface WorkflowVersion {
  id: number
  projectId: number
  version: number
  name: string
  definition: Record<string, unknown> | string | null
  isActive: boolean
  creatorId: number
  createdAt: string
}

export interface WorkflowNodePermission {
  id: number
  workflowVersionId: number
  nodeId: string
  allowedRoles?: string[]
  allowedUsers?: number[]
  assigneeRule?: string
  visibleFields?: string[]
  editableFields?: string[]
  requiredFields?: string[]
  availableActions?: string[]
  actionConditions?: Record<string, unknown>
  notificationRules?: Record<string, unknown>
  timeoutHours?: number
  dataPermissions?: Record<string, unknown>
  attachmentPermissions?: Record<string, unknown>
}

export interface TransitionRequest {
  targetStateId: number
  comment?: string
  fieldValues?: Record<string, unknown>
}

export interface TransitionResponse {
  success: boolean
  newStatus: string
  availableTransitions: WorkflowTransition[]
}
