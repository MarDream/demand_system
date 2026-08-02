// 字段类型
export type FieldType =
  | 'text' | 'number' | 'date' | 'date_range'
  | 'single_select' | 'multi_select'
  | 'user' | 'group' | 'department'
  | 'checkbox' | 'check' | 'auto_number'
  | 'created_time' | 'last_modified_time' | 'modified_time'
  | 'created_by' | 'modified_by' | 'created_user' | 'modified_user'
  | 'url' | 'email' | 'phone' | 'location' | 'barcode' | 'currency'
  | 'process' | 'button' | 'progress' | 'rating'
  | 'link' | 'bidirectional_link' | 'rollup' | 'lookup'
  | 'formula' | 'attachment'
  | 'ai_text' | 'ai_select'

// 视图类型
export type ViewType = 'grid' | 'kanban' | 'gantt' | 'calendar' | 'gallery' | 'form'

// 成员角色
export type MemberRole = 'owner' | 'admin' | 'editor' | 'commenter' | 'viewer'

// 字段配置(JSON 对象)
export interface FieldConfig {
  options?: { label: string; color?: string }[]  // single_select/multi_select 选项
  format?: string  // date 格式/number 格式
  defaultValue?: unknown
  linkTargetTableId?: number  // link 字段目标表
  formulaExpr?: string  // formula 表达式
  precision?: number  // number/currency 小数位
  symbol?: string  // rating/currency 符号
  prefix?: string
  suffix?: string
  maxRating?: number
  progressFormat?: 'percent' | 'value'
  linkDisplayFieldId?: number
  reverseFieldId?: number
  linkFieldId?: number
  targetFieldId?: number
  lookupFieldId?: number
  rollupFieldId?: number
  aggregation?: 'count' | 'sum' | 'average' | 'min' | 'max'
  digits?: number
  length?: number
  dateFormat?: string
  formHidden?: boolean
  formPlaceholder?: string
  allowMultiple?: boolean
  allowedFileTypes?: string[]
  maxFiles?: number
  countryCode?: string
  barcodeMode?: string
  processNodes?: { label: string; color?: string }[]
  button?: { label?: string; color?: string; actionType?: string; actionId?: number }
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
  defaultViewId?: number
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
  description?: string
  /** 后端为 Integer(tinyint)，实际返回 0/1 */
  required: boolean | number
  aiPrompt?: string
  /** 后端为 Integer(tinyint)，实际返回 0/1 */
  isAiField: boolean | number
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

// 视图统一配置
export interface ViewConfig {
  schemaVersion: number
  columnOrder?: number[]
  hiddenFieldIds?: number[]
  frozenFieldIds?: number[]
  fieldWidths?: Record<number, number>
  rowHeight?: 'compact' | 'medium' | 'tall'
  card?: { coverFieldId?: number; visibleFieldIds?: number[] }
  calendar?: { startFieldId?: number; endFieldId?: number; titleFieldId?: number; colorFieldId?: number }
  gantt?: { startFieldId?: number; endFieldId?: number; dependencyFieldId?: number; milestoneFieldId?: number }
  form?: { fieldOrder?: number[]; hiddenFieldIds?: number[]; requiredFieldIds?: number[]; descriptions?: Record<number, string>; successMessage?: string; redirectUrl?: string }
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
  version: number
  config?: ViewConfig
  isDefault?: boolean
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
  description?: string
  /** 后端为 Integer(tinyint)，接受 0/1 或 boolean */
  required?: boolean | number
  aiPrompt?: string
  /** 后端为 Integer(tinyint)，接受 0/1 或 boolean */
  isAiField?: boolean | number
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
  config?: ViewConfig
}

// 自动化规则
export interface BitableAutomation {
  id: number
  baseId: number
  tableId?: number
  name: string
  status: 'enabled' | 'disabled'
  triggerType: string
  triggerConfig?: any
  actionType: string
  actionConfig?: any
  createdBy: number
  lastRunStatus?: string
  lastRunAt?: string
  createdAt: string
  updatedAt: string
}

/** 记录查询参数DTO（支持筛选、排序、分组） */
export interface RecordQueryDTO {
  pageNum?: number
  pageSize?: number
  /** 筛选配置，支持简单数组或嵌套逻辑格式 */
  filterConfig?: FilterItem[] | { logic: 'and' | 'or'; rules: FilterItem[] }
  /** 排序配置，格式：[{fieldId, direction}] */
  sortConfig?: SortItem[]
  /** 分组字段ID */
  groupByFieldId?: number
  /** 视图ID（如果传入，自动从视图配置加载筛选/排序） */
  viewId?: number
}

/** 分组查询结果 */
export interface RecordGroupVO {
  /** 分组键值 */
  groupKey: string
  /** 分组内的记录列表 */
  records: BitableRecord[]
  /** 分组内记录数 */
  count: number
}
