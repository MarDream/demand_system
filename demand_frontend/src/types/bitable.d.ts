// 字段类型
export type FieldType =
  | 'text' | 'rich_text' | 'number' | 'date' | 'date_range'
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

/** 选项（单选/多选/流程/AI 选择） */
export interface FieldOption {
  label: string
  color?: string
  /** 选项说明，表单中展示 */
  desc?: string
}

/** 进度条阈值变色规则：百分比低于 below 时使用该颜色（最后一条兜住以上区间） */
export interface ProgressColorRule {
  below: number
  color: string
}

/** 按钮字段的触发动作配置 */
export interface ButtonActionConfig {
  label?: string
  color?: string
  icon?: string
  actionType?: 'openUrl' | 'updateRecord' | 'automation'
  actionUrl?: string
  /** 可用条件：仅当 conditionFieldId 的值等于 conditionValue 时按钮可点 */
  conditionFieldId?: number
  conditionValue?: string
  confirmText?: string
  successText?: string
  failText?: string
}

/**
 * 字段配置(JSON 对象)。
 *
 * 键位约定：同一语义只允许一个键，旧的兼容键（format/digits/symbol/length/progressFormat 等）
 * 只在读取时由 `normalizeFieldConfig()` 迁移，写入一律使用本接口声明的规范键。
 */
export interface FieldConfig {
  // ===== 通用 =====
  defaultValue?: unknown
  /** 表单中隐藏 */
  formHidden?: boolean
  /** 表单输入框占位提示 */
  formPlaceholder?: string
  /** 同表内取值唯一 */
  unique?: boolean

  // ===== 文本 =====
  inputMode?: 'single' | 'multiline'
  maxLength?: number
  /** 自定义正则校验（正则源码，不含斜杠） */
  pattern?: string
  patternMessage?: string

  // ===== 电话 =====
  countryCode?: string
  allowCountrySwitch?: boolean
  masked?: boolean

  // ===== 邮箱 =====
  /** 允许的邮箱域名（留空不限），如 example.com；校验时不区分大小写 */
  allowedEmailDomains?: string[]
  /** 展示为可点击的邮件链接（点击唤起本机邮件客户端） */
  emailClickable?: boolean

  // ===== 超链接 =====
  displayText?: string
  openInNewTab?: boolean

  // ===== 地理位置 =====
  locationInputMethod?: 'map' | 'text' | 'latlng'
  locationDisplayMode?: 'name' | 'address' | 'latlng'

  // ===== 数字 =====
  numberFormat?: 'plain' | 'percent'
  precision?: number
  thousandSeparator?: boolean
  prefix?: string
  suffix?: string
  min?: number
  max?: number

  // ===== 货币 =====
  currency?: string
  currencySymbolPosition?: 'prefix' | 'suffix'

  // ===== 进度 =====
  progressStyle?: 'bar' | 'percent' | 'number'
  step?: number
  progressColorMode?: 'single' | 'threshold'
  progressColor?: string
  progressRules?: ProgressColorRule[]

  // ===== 评分 =====
  maxRating?: number
  ratingIcon?: 'star' | 'heart' | 'thumb' | 'number'
  ratingColor?: string
  allowHalf?: boolean

  // ===== 选择类 =====
  options?: FieldOption[]
  defaultOption?: string
  allowAddOption?: boolean
  maxSelect?: number
  processNodes?: FieldOption[]

  // ===== 复选框 =====
  defaultChecked?: boolean
  checkboxStyle?: 'checkbox' | 'switch'

  // ===== 日期 / 系统时间 =====
  dateFormat?: string
  withTime?: boolean
  timeFormat?: '24h' | '12h'
  dateDefaultMode?: 'none' | 'now' | 'today' | 'fixed'

  // ===== 人员 / 群组 =====
  userMode?: 'single' | 'multiple'
  userScope?: 'all' | 'dept' | 'self'
  /** 人员范围=指定部门时选中的部门 ID */
  userDeptIds?: number[]
  userDisplay?: 'avatar' | 'name' | 'both'
  defaultCurrentUser?: boolean
  groupMode?: 'single' | 'multiple'
  groupScope?: 'joined' | 'all'

  // ===== 部门 =====
  departmentMode?: 'single' | 'multiple'
  departmentScope?: 'all' | 'dept'
  /** 可选范围=指定部门时选中的部门 ID */
  departmentIds?: number[]

  // ===== 附件 =====
  fileTypeLimit?: 'any' | 'image' | 'doc' | 'custom'
  allowedExtensions?: string[]
  maxFileSizeMb?: number
  maxFiles?: number
  attachmentDisplay?: 'list' | 'thumbnail' | 'cover'

  // ===== 条码 =====
  barcodeType?: 'qrcode' | 'code128' | 'ean13'
  barcodeSource?: 'self' | 'field'
  barcodeSourceFieldId?: number
  showBarcodeText?: boolean
  barcodeSize?: number
  barcodeColor?: string

  // ===== 自动编号 =====
  digits?: number
  resetCycle?: 'never' | 'year' | 'month' | 'day'

  // ===== 按钮 =====
  button?: ButtonActionConfig

  // ===== 关联 =====
  linkTargetTableId?: number
  linkDisplayFieldId?: number
  reverseFieldId?: number
  allowMultipleLink?: boolean
  linkDeleteStrategy?: 'clear' | 'keep'

  // ===== 查找引用 / 汇总 =====
  linkFieldId?: number
  targetFieldId?: number
  lookupFieldId?: number
  rollupFieldId?: number
  aggregation?: 'count' | 'sum' | 'average' | 'min' | 'max' | 'concat' | 'distinctCount'
  rollupFormat?: 'number' | 'date' | 'text'

  // ===== 公式 =====
  formulaExpr?: string
  formulaResultFormat?: 'auto' | 'text' | 'number' | 'date'
  formulaErrorDisplay?: 'empty' | 'zero' | 'custom'
  formulaErrorText?: string

  // ===== AI 字段 =====
  sourceFieldIds?: number[]
  autoCompute?: boolean
  aiTriggerMode?: 'manual' | 'onCreate' | 'onDependencyChange'
  aiTemperature?: number
  aiMaxTokens?: number
  aiFallbackMode?: 'empty' | 'text'
  aiFallbackText?: string
}

/** 字段级权限级别：隐藏 / 只读 / 仅新增时可填 / 可编辑 */
export type FieldPermissionLevel = 'hidden' | 'readonly' | 'add_only' | 'editable'

/** 选项级权限配置（单选/多选字段）：partial 时仅 editableKeys 内的选项可被该角色选择 */
export interface FieldOptionPermissionConfig {
  mode: 'all' | 'partial'
  /** partial 模式下可编辑的选项 label；不在集合内的选项=也可查看（原值可保留、不可新选） */
  editableKeys?: string[]
  /** 选项定义管理权限：full=可增删改，add-only=仅可新增 */
  manage?: 'full' | 'add-only'
}

/** 某个角色对某个字段的权限配置 */
export interface FieldPermission {
  fieldId: number
  permissionLevel: FieldPermissionLevel
}

/** 权限配置接口的请求体（字段权限批量保存）；optionConfig 为选项级权限 JSON 字符串 */
export interface FieldPermissionChange {
  baseId?: number
  tableId: number
  fieldId: number
  roleType: 'system' | 'custom'
  systemRoleCode?: string | null
  customRoleId?: number | null
  permissionLevel: FieldPermissionLevel
  optionConfig?: string | null
}

// 多维表格 Base
export interface BitableBase {
  id: number
  name: string
  description?: string
  icon?: string
  coverColor?: string
  projectId?: number
  /** 所属 Base 分组ID，null/undefined=未分组 */
  groupId?: number | null
  creatorId: number
  creatorName?: string
  isTemplate: boolean
  sortOrder: number
  tableCount?: number
  createdAt: string
  updatedAt: string
}

// Base 分组（多维表格列表页左侧目录树，全局维度）
export interface BitableBaseGroup {
  id: number
  /** 父分组ID，null=根层级 */
  parentId?: number | null
  name: string
  sortOrder: number
  creatorId?: number
  /** 直接挂载的 Base 数量 */
  baseCount: number
  /** 含所有子孙分组的 Base 总数 */
  totalBaseCount: number
  children?: BitableBaseGroup[]
  createdAt: string
  updatedAt: string
}

export interface BitableBaseGroupCreateDTO {
  name: string
  /** 父分组ID，null/undefined=创建在根层级 */
  parentId?: number | null
}

export interface BitableBaseGroupMoveDTO {
  /** 目标父分组ID，null=根层级 */
  parentId?: number | null
  /** 目标排序号，不传则追加到末尾 */
  sortOrder?: number
}

// 数据表
export interface BitableTable {
  id: number
  baseId: number
  /** 所属分组ID，null/undefined=未分组 */
  groupId?: number | null
  name: string
  description?: string
  icon?: string
  sortOrder: number
  /** 网格行高(px)，null/undefined=使用默认 80 */
  rowHeight?: number | null
  defaultViewId?: number
  recordCount?: number
  fieldCount?: number
  createdAt: string
  updatedAt: string
}

// 数据表分组（目录树节点，parentId 自关联，任意层级）
export interface BitableTableGroup {
  id: number
  baseId: number
  /** 父分组ID，null=根层级 */
  parentId?: number | null
  name: string
  sortOrder: number
  creatorId?: number
  /** 直接挂载的数据表数量 */
  tableCount: number
  /** 含所有子孙分组的数据表总数 */
  totalTableCount: number
  children?: BitableTableGroup[]
  createdAt: string
  updatedAt: string
}

export interface BitableTableGroupCreateDTO {
  name: string
  /** 父分组ID，null/undefined=创建在根层级 */
  parentId?: number | null
}

export interface BitableTableGroupMoveDTO {
  /** 目标父分组ID，null=根层级 */
  parentId?: number | null
  /** 目标排序号，不传则追加到末尾 */
  sortOrder?: number
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
  /**
   * 当前用户对该字段的权限级别（后端按角色解析后下发）。
   * 仅在按当前用户查询字段列表时返回；内部调用（如取关联目标表字段）不返回，视为 editable。
   */
  permission?: FieldPermissionLevel
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
  /** 整列填色：fieldId → 预设色 key（fill_none=清除） */
  columnColors?: Record<number, string>
  fieldWidths?: Record<number, number>
  rowHeight?: 'compact' | 'medium' | 'tall'
  card?: { coverFieldId?: number; visibleFieldIds?: number[] }
  calendar?: { startFieldId?: number; endFieldId?: number; titleFieldId?: number; colorFieldId?: number }
  gantt?: { startFieldId?: number; endFieldId?: number; dependencyFieldId?: number; milestoneFieldId?: number }
  form?: { fieldOrder?: number[]; hiddenFieldIds?: number[]; requiredFieldIds?: number[]; descriptions?: Record<number, string>; successMessage?: string; redirectUrl?: string }
  /** 看板视图的分组字段 */
  kanban?: { groupFieldId?: number }
}

// 视图
export interface BitableView {
  id: number
  tableId: number
  name: string
  viewType: ViewType
  sortConfig?: SortItem[]
  /** 筛选配置，兼容历史数组格式与嵌套逻辑格式 */
  filterConfig?: FilterItem[] | FilterGroup
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

/** 筛选操作符 */
export type FilterOperator =
  | 'eq' | 'ne' | 'contains' | 'not_contains'
  | 'gt' | 'gte' | 'lt' | 'lte'
  | 'between' | 'is_empty' | 'is_not_empty'

/** 叶子筛选条件 */
export interface FilterItem {
  fieldId: number
  operator: FilterOperator
  value?: unknown
  /** 数组格式中与上一条规则的关系（and/or） */
  conjunction?: 'and' | 'or'
}

/** 嵌套筛选组（递归） */
export interface FilterGroup {
  logic: 'and' | 'or'
  rules: Array<FilterItem | FilterGroup>
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
  operationTypeLabel?: string
  detail?: unknown
  createdAt: string
}

// 操作历史查询参数
export interface BitableOperationQuery {
  pageNum?: number
  pageSize?: number
  /** 操作类型编码，逗号分隔 */
  operationType?: string
  /** 操作人ID */
  userId?: number
  /** 起始时间 ISO */
  startTime?: string
  /** 截止时间 ISO */
  endTime?: string
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
  /** 创建时直接归入的分组ID */
  groupId?: number | null
  /** 网格行高(px)，28-200 */
  rowHeight?: number
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

/** 记录查询参数DTO（支持筛选、排序、分组、游标分页） */
export interface RecordQueryDTO {
  pageNum?: number
  pageSize?: number
  /** 筛选配置，支持简单数组或嵌套逻辑格式 */
  filterConfig?: FilterItem[] | FilterGroup
  /** 排序配置，格式：[{fieldId, direction}] */
  sortConfig?: SortItem[]
  /** 分组字段ID */
  groupByFieldId?: number
  /** 视图ID（如果传入，自动从视图配置加载筛选/排序） */
  viewId?: number
  /** 是否启用游标分页 */
  useCursor?: boolean
  /** 上一页返回的游标 */
  cursor?: string
}

/** 记录查询响应（分页 + 可选游标） */
export interface RecordQueryResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  nextCursor?: string | null
  hasMore?: boolean
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

// ========== 权限管理 ==========

export type PermissionLevel = 'full' | 'edit' | 'view' | 'none'
/** 权限类型：data=数据表权限；view=视图权限；dashboard=仪表盘整体；dashboard_data=仪表盘数据；automation=自动化（已下线） */
export type PermissionType = 'data' | 'automation' | 'view' | 'dashboard' | 'dashboard_data'
export type RoleType = 'system' | 'custom'
export type MemberType = 'user' | 'dept'

/** 角色权限配置 */
export interface BitableBaseRolePermission {
  tableId: number
  tableName?: string
  permissionType: PermissionType
  permissionLevel: PermissionLevel
}

/** 角色成员 */
export interface BitableBaseRoleMember {
  memberType: MemberType
  memberId: number
  memberName?: string
  /** 成员头像URL（用户类成员才有）；为空时前端用姓名首字做占位缩略图 */
  memberAvatar?: string | null
}

/** 角色VO（系统角色 + 自定义角色统一视图） */
export interface BitableBaseRoleVO {
  roleType: RoleType
  systemRoleCode?: string
  customRoleId?: number
  name: string
  sortOrder: number
  members: BitableBaseRoleMember[]
  permissions: BitableBaseRolePermission[]
  createdAt?: string
  /** 自定义角色挂载的 Base（跨 Base 全局视图下用于定位角色来源） */
  baseId?: number
}

/** 自定义角色创建DTO（后端回显时带 customRoleId=新建角色ID） */
export interface BitableBaseCustomRoleCreateDTO {
  baseId: number
  name: string
  customRoleId?: number
}

/** 自定义角色更新DTO */
export interface BitableBaseCustomRoleUpdateDTO {
  name: string
}

/** 自定义角色成员DTO */
export interface BitableBaseCustomRoleMemberDTO {
  roleId: number
  memberType: MemberType
  memberId: number
}

/** 角色权限设置DTO */
export interface BitableBaseRolePermissionDTO {
  baseId: number
  roleType: RoleType
  systemRoleCode?: string
  customRoleId?: number
  tableId: number
  permissionType: PermissionType
  permissionLevel: PermissionLevel
}
