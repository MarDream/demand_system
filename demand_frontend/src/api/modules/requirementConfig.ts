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

export const requirementConfigApi = {
  // 需求类型
  listTypes: () => request.get<RequirementType[]>('/requirement-config/types'),

  createType: (data: RequirementType) => request.post('/requirement-config/types', data),

  updateType: (id: number, data: RequirementType) => request.put(`/requirement-config/types/${id}`, data),

  deleteType: (id: number) => request.delete(`/requirement-config/types/${id}`),

  // 优先级
  listPriorities: () => request.get<Priority[]>('/requirement-config/priorities'),

  createPriority: (data: Priority) => request.post('/requirement-config/priorities', data),

  updatePriority: (id: number, data: Priority) => request.put(`/requirement-config/priorities/${id}`, data),

  deletePriority: (id: number) => request.delete(`/requirement-config/priorities/${id}`),
}
