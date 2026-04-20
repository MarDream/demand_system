export interface Requirement {
  id: number
  projectId: number
  parentId: number | null
  creatorId: number
  assigneeId: number | null
  title: string
  description: string
  type: string
  priority: string
  status: string
  moduleId: number | null
  iterationId: number | null
  estimatedHours: number | null
  actualHours: number | null
  dueDate: string | null
  orderNum: number
  version: number
  createdAt: string
  updatedAt: string
  creatorName?: string
  assigneeName?: string
  childCount?: number
}

export interface RequirementCreate {
  projectId: number
  parentId?: number
  title: string
  description: string
  type: string
  priority: string
  assigneeId?: number
  iterationId?: number
  moduleId?: number
  dueDate?: string
  estimatedHours?: number
}

export interface RequirementUpdate extends Partial<RequirementCreate> {
  id: number
  status?: string
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
