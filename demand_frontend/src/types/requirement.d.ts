export interface Requirement {
  id: number
  projectId: number
  parentId: number | null
  creatorId: number
  assigneeId: number | null
  opsFollowId: number | null
  maintFollowId: number | null
  departmentId: number | null
  orgId?: number | null
  requirementNo: string | null
  title: string
  description: string
  type: string
  priority: string
  status: string
  moduleId: number | null
  iterationId: number | null
  workflowInstanceId?: number | null
  nodeStatus?: string | null
  isDraft?: boolean | null
  startDate?: string | null
  estimatedHours: number | null
  actualHours: number | null
  dueDate: string | null
  analysisCompletedAt: string | null
  confirmAt: string | null
  developmentCompletedAt: string | null
  attachments?: RequirementAttachment[]
  ccUserIds?: number[]
  orderNum: number
  version: number
  createdAt: string
  updatedAt: string
  creatorName?: string
  assigneeName?: string
  opsFollowName?: string
  maintFollowName?: string
  departmentName?: string
  childCount?: number
}

export interface RequirementAttachment {
  fileId?: number | null
  name: string
  url: string
  size?: number | null
  contentType?: string | null
  bucketName?: string | null
  objectName?: string | null
}

export interface RequirementCreate {
  projectId: number
  parentId?: number
  title: string
  description: string
  type: string
  priority: string
  assigneeId?: number
  ccUserIds?: number[]
  moduleId?: number
  startDate?: string
  dueDate?: string
  estimatedHours?: number
  attachments?: RequirementAttachment[]
}

export interface RequirementUpdate extends Partial<RequirementCreate> {
  id: number
  iterationId?: number
  status?: string
}

export interface RequirementDraftCreate {
  projectId: number
  parentId?: number
  title: string
  description: string
  priority: string
  assigneeId?: number
  ccUserIds?: number[]
  moduleId?: number
  startDate?: string
  dueDate?: string
  estimatedHours?: number
  attachments?: RequirementAttachment[]
}

export interface RequirementDraftUpdate extends Partial<RequirementDraftCreate> {
  id: number
  version: number
}

export interface RequirementMyListQuery {
  projectId?: number
  keyword?: string
  pageNum: number
  pageSize: number
}

export interface NextNodeOption {
  nodeId: string
  nodeName: string
  bindStatusCode?: string | null
  bindStatusName?: string | null
  projectRequired?: boolean | null
}

export interface RequirementSubmit {
  version: number
  nextNodeId?: string
  projectId?: number | null
  comment?: string
}

export interface RequirementQuery {
  projectId?: number
  parentId?: number
  type?: string
  priority?: string
  status?: string
  assigneeId?: number
  iterationId?: number
  keyword?: string
  createdAtStart?: string
  createdAtEnd?: string
  analysisCompletedAtStart?: string
  analysisCompletedAtEnd?: string
  confirmAtStart?: string
  confirmAtEnd?: string
  developmentCompletedAtStart?: string
  developmentCompletedAtEnd?: string
  pageNum: number
  pageSize: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export interface RequirementHistory {
  id: number
  requirementId: number
  operatorId: number
  fieldName: string
  oldValue: string
  newValue: string
  createdAt: string
  operatorName?: string
}

export interface RequirementComment {
  id: number
  requirementId: number
  userId: number
  userName?: string
  content: string
  createdAt: string
}

export interface RequirementCommentCreate {
  content: string
}

export interface CustomField {
  id: number
  projectId: number
  name: string
  fieldType: string
  options?: string[]
  required: boolean
  visibleStatuses?: string[]
  defaultValue?: string
  sortOrder: number
}

export interface CustomFieldValue {
  id: number
  requirementId: number
  fieldId: number
  valueText?: string
  valueNumber?: number
  valueDate?: string
  valueUserIds?: number[]
}
