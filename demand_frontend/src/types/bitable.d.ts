// 字段类型
export type FieldType =
  | 'text' | 'number' | 'date' | 'date_range'
  | 'single_select' | 'multi_select'
  | 'user' | 'department'
  | 'check' | 'auto_number'
  | 'created_time' | 'modified_time' | 'created_user' | 'modified_user'
  | 'url' | 'email' | 'phone'
  | 'progress' | 'rating'
  | 'link' | 'rollup' | 'lookup'
  | 'formula' | 'attachment'
  | 'ai_text' | 'ai_select'

// 视图类型
export type ViewType = 'grid' | 'kanban' | 'gantt' | 'calendar' | 'gallery'

// 成员角色
export type MemberRole = 'owner' | 'admin' | 'editor' | 'commenter' | 'viewer'

// 字段配置(JSON 对象)
export interface FieldConfig {
  options?: { label: string; color?: string }[]  // single_select/multi_select 选项
  format?: string  // date 格式/number 格式
  defaultValue?: unknown
  linkTargetTableId?: number  // link 字段目标表
  formulaExpr?: string  // formula 表达式
  precision?: number  // number 小数位
  symbol?: string  // rating 符号
}

// 多维表格 Base
export interface BitableBase {
  id: number
  name: string
  description?: string
  icon?: string
  coverColor?: string
  projectId?: number
  creatorId: number
  creatorName?: string
  isTemplate: boolean
  sortOrder: number
  tableCount?: number
  createdAt: string
  updatedAt: string
}

// 数据表
export interface BitableTable {
  id: number
  baseId: number
  name: string
  description?: string
  icon?: string
  sortOrder: number
  recordCount?: number
  fieldCount?: number
  createdAt: string
  updatedAt: string
}

// 字段
export interface BitableField {
  id: number
  tableId: number
  name: string
  fieldType: FieldType
  config?: FieldConfig
  required: boolean
  aiPrompt?: string
  isAiField: boolean
  sortOrder: number
  width: number
  createdAt: string
  updatedAt: string
}

// 记录行
export interface BitableRecord {
  id: number
  tableId: number
  sortOrder: number
  createdBy: number
  createdByName?: string
  createdAt: string
  updatedBy?: number
  updatedByName?: string
  updatedAt: string
  version: number
  // 单元格值,以 fieldId 为键
  cells?: Record<number, CellValue>
}

// 单元格值
export interface CellValue {
  fieldId: number
  valueText?: string
  valueNumber?: number
  valueDate?: string
  valueJson?: unknown  // 多选数组/关联ID列表/附件等
  // 展示用,后端可附带
  displayText?: string
}

// 视图
export interface BitableView {
  id: number
  tableId: number
  name: string
  viewType: ViewType
  sortConfig?: SortItem[]
  filterConfig?: FilterItem[]
  groupConfig?: GroupItem[]
  columnConfig?: ColumnItem[]
  colorConfig?: unknown
  sortOrder: number
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface SortItem {
  fieldId: number
  direction: 'asc' | 'desc'
}

export interface FilterItem {
  fieldId: number
  operator: string  // eq/ne/contains/gt/lt/between/is_empty/is_not_empty
  value?: unknown
  conjunction?: 'and' | 'or'  // 与上一条件的关系
}

export interface GroupItem {
  fieldId: number
  direction?: 'asc' | 'desc'
}

export interface ColumnItem {
  fieldId: number
  width?: number
  hidden?: boolean
  frozen?: boolean
}

// 协作成员
export interface BitableBaseMember {
  id: number
  baseId: number
  userId: number
  userName?: string
  avatar?: string
  role: MemberRole
  createdAt: string
}

// 评论
export interface BitableComment {
  id: number
  recordId: number
  tableId: number
  userId: number
  userName?: string
  avatar?: string
  content: string
  quoteFieldId?: number
  parentId?: number
  createdAt: string
}

// 操作历史
export interface BitableOperation {
  id: number
  baseId: number
  tableId?: number
  userId: number
  userName?: string
  operationType: string
  detail?: unknown
  createdAt: string
}

// DTO 请求类型
export interface BitableBaseCreateDTO {
  name: string
  description?: string
  icon?: string
  coverColor?: string
  projectId?: number
}

export interface BitableTableCreateDTO {
  name: string
  description?: string
  icon?: string
}

export interface BitableFieldCreateDTO {
  name: string
  fieldType: FieldType
  config?: FieldConfig
  required?: boolean
  aiPrompt?: string
  isAiField?: boolean
  width?: number
}

export interface BitableRecordCreateDTO {
  cells?: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }>
}

export interface CellUpdateDTO {
  valueText?: string
  valueNumber?: number
  valueDate?: string
  valueJson?: unknown
  version: number  // 乐观锁版本
}

export interface BitableViewCreateDTO {
  name: string
  viewType: ViewType
}
