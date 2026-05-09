import request from '../request'

export interface RequirementType {
  id?: number
  code: string
  name: string
  color?: string
  sortOrder?: number
  isDefault?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface Priority {
  id?: number
  code: string
  name: string
  color?: string
  level?: number
  sortOrder?: number
  isDefault?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface SortItem {
  id: number
  sortOrder: number
}

export interface RequirementCreateFormConfig {
  defaultTypeCode?: string
  defaultTypeName?: string
  defaultTypeColor?: string
  visibleFields?: string[]
  requiredFields?: string[]
}

export const requirementConfigApi = {
  // 需求类型
  listTypes: () => request.get<RequirementType[]>('/v1/requirement-config/types'),

  getCreateFormConfig: (projectId: number) =>
    request.get<RequirementCreateFormConfig>(`/v1/requirement-config/projects/${projectId}/create-form`),

  createType: (data: RequirementType) => request.post('/v1/requirement-config/types', data),

  updateType: (id: number, data: RequirementType) => request.put(`/v1/requirement-config/types/${id}`, data),

  deleteType: (id: number) => request.delete(`/v1/requirement-config/types/${id}`),

  sortTypes: (items: SortItem[]) => request.post('/v1/requirement-config/types/sort', { items }),

  // 优先级
  listPriorities: () => request.get<Priority[]>('/v1/requirement-config/priorities'),

  createPriority: (data: Priority) => request.post('/v1/requirement-config/priorities', data),

  updatePriority: (id: number, data: Priority) => request.put(`/v1/requirement-config/priorities/${id}`, data),

  deletePriority: (id: number) => request.delete(`/v1/requirement-config/priorities/${id}`),

  sortPriorities: (items: SortItem[]) => request.post('/v1/requirement-config/priorities/sort', { items }),
}
