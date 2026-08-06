import request from '../request'

export interface RequirementType {
  id?: number
  code: string
  name: string
  color?: string
  sortOrder?: number
  isDefault?: boolean
  /** 是否启用：false=禁用（不可用于新建需求），true=启用。工作流禁用时联动置 false */
  enabled?: boolean
  /** 绑定的工作流版本ID（NULL=未绑定，该类型不可用于新建需求） */
  workflowVersionId?: number | null
  createdAt?: string
  updatedAt?: string
  /** 行内切换启用状态的 loading（仅前端UI使用，不提交后端） */
  _enabledLoading?: boolean
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
  /** 行内切换默认的 loading 状态（仅前端UI使用，不提交后端） */
  _defaultLoading?: boolean
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

  /** 仅返回已绑定活跃工作流版本的需求类型（创建需求下拉用） */
  listAvailableTypes: () => request.get<RequirementType[]>('/v1/requirement-config/types/available'),

  getCreateFormConfig: (projectId: number) =>
    request.get<RequirementCreateFormConfig>(`/v1/requirement-config/projects/${projectId}/create-form`),

  createType: (data: RequirementType) => request.post('/v1/requirement-config/types', data),

  updateType: (id: number, data: RequirementType) => request.put(`/v1/requirement-config/types/${id}`, data),

  deleteType: (id: number) => request.delete(`/v1/requirement-config/types/${id}`),

  sortTypes: (items: SortItem[]) => request.post('/v1/requirement-config/types/sort', items),

  /** 绑定/解绑需求类型的工作流版本。workflowVersionId 传 null 解绑 */
  bindWorkflow: (typeCode: string, workflowVersionId: number | null) =>
    request.put(`/v1/requirement-config/types/${typeCode}/workflow`, null, {
      params: workflowVersionId != null ? { workflowVersionId } : undefined,
    }),

  /** 启用/禁用需求类型。开启时若绑定工作流已禁用则后端拒绝 */
  updateTypeEnabled: (id: number, enabled: boolean) =>
    request.put(`/v1/requirement-config/types/${id}/enabled`, null, { params: { enabled } }),

  // 优先级
  listPriorities: () => request.get<Priority[]>('/v1/requirement-config/priorities'),

  createPriority: (data: Priority) => request.post('/v1/requirement-config/priorities', data),

  updatePriority: (id: number, data: Priority) => request.put(`/v1/requirement-config/priorities/${id}`, data),

  deletePriority: (id: number) => request.delete(`/v1/requirement-config/priorities/${id}`),

  sortPriorities: (items: SortItem[]) => request.post('/v1/requirement-config/priorities/sort', items),
}
