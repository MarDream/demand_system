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

/** 需求动态字段支持的字段类型 */
export type CustomFieldType =
  | 'TEXT'
  | 'SELECT'
  | 'DATE'
  | 'NUMBER'
  | 'MULTI_SELECT'
  | 'USER'
  | 'MULTI_USER'
  | 'BOOLEAN'
  | 'URL'
  | 'FILE'

/** 单选/多选选项：key 为稳定编码（字段值存储用它），label 为展示名（可修改） */
export interface CustomFieldOption {
  key: string
  label: string
}

/**
 * 解析选项 JSON，兼容两种形态：
 * 历史字符串数组 ["a","b"]（key=label）与对象数组 [{"key":"k","label":"l"}]。
 */
export function parseFieldOptions(raw?: string | CustomFieldOption[] | string[] | null): CustomFieldOption[] {
  if (!raw) return []
  const arr = Array.isArray(raw) ? raw : safeParse(raw)
  if (!Array.isArray(arr)) return []
  const result: CustomFieldOption[] = []
  const seen = new Set<string>()
  for (const item of arr) {
    if (typeof item === 'string') {
      const text = item.trim()
      if (text && !seen.has(text)) {
        seen.add(text)
        result.push({ key: text, label: text })
      }
    } else if (item && typeof item === 'object' && typeof (item as CustomFieldOption).key === 'string') {
      const key = (item as CustomFieldOption).key.trim()
      const label = ((item as CustomFieldOption).label || key).trim()
      if (key && !seen.has(key)) {
        seen.add(key)
        result.push({ key, label: label || key })
      }
    }
  }
  return result
}

function safeParse(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/** 选项 key → 展示名；无匹配（历史数据/选项已移除）时回退显示原值 */
export function fieldOptionLabel(options: CustomFieldOption[] | null | undefined, key?: string | number | null): string {
  if (key == null || key === '') return ''
  const k = String(key)
  const hit = options?.find((o) => o.key === k)
  return hit ? hit.label : k
}

/** 动态字段定义（配置管理视角） */
export interface CustomFieldDef {
  id?: number
  projectId: number
  /** 稳定字段编码，创建后不可修改 */
  fieldCode: string
  /** 所属需求类型编码，空表示项目级全局字段 */
  requirementTypeCode?: string | null
  name: string
  fieldType: CustomFieldType
  /** 选项 JSON：[{key,label}]（历史数据可能为字符串数组） */
  options?: string | CustomFieldOption[] | string[] | null
  required?: boolean
  defaultValue?: string | null
  sortOrder?: number
  enabled?: boolean
}

/** 动态字段运行时 schema（含当前节点权限与当前值） */
export interface DynamicFieldSchema {
  id: number
  fieldCode: string
  requirementTypeCode?: string | null
  name: string
  fieldType: CustomFieldType
  options?: string | CustomFieldOption[] | string[] | null
  /** 选项结构化列表（后端解析，优先使用） */
  optionList?: CustomFieldOption[] | null
  required: boolean
  defaultValue?: string | null
  sortOrder?: number
  enabled?: boolean
  /** 当前节点是否可见（后端已过滤，恒为 true） */
  visible: boolean
  /** 当前节点是否可编辑 */
  editable: boolean
  value?: string | null
  valueNumber?: number | null
  valueDate?: string | null
  valueBoolean?: boolean | null
  valueUserId?: number | null
  values?: Array<string | number> | null
}

/** 动态字段提交值 */
export interface CustomFieldValuePayload {
  fieldCode: string
  value?: string | null
  valueNumber?: number | null
  valueDate?: string | null
  valueBoolean?: boolean | null
  valueUserId?: number | null
  values?: Array<string | number> | null
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

  // ============ 动态字段 ============

  /** 查询某项目/需求类型下的动态字段定义 */
  listCustomFields: (projectId: number, typeCode?: string | null) =>
    request.get<CustomFieldDef[]>('/v1/requirement-config/fields', {
      params: typeCode ? { projectId, typeCode } : { projectId },
    }),

  /** 获取创建态字段 schema（含流程首节点权限与默认值） */
  getCustomFieldSchema: (projectId: number, typeCode: string) =>
    request.get<DynamicFieldSchema[]>('/v1/requirement-config/fields/schema', {
      params: { projectId, typeCode },
    }),

  createCustomField: (data: CustomFieldDef) => request.post('/v1/requirement-config/fields', data),

  updateCustomField: (data: CustomFieldDef) => request.put('/v1/requirement-config/fields', data),

  deleteCustomField: (id: number) => request.delete(`/v1/requirement-config/fields/${id}`),

  sortCustomFields: (items: SortItem[]) => request.put('/v1/requirement-config/fields/sort', items),
}
