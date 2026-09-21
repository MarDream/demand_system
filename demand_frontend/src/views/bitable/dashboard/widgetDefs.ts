/**
 * 可视化大屏组件类型注册表。
 * 每种组件声明数据需求（单值/分组/静态）、默认尺寸与图标，
 * 画布、组件面板、属性面板均以这里为唯一事实来源。
 */

export type WidgetCategory = 'chart' | 'component'

/** 数据形态：single=单值聚合；grouped=维度分组聚合；none=静态组件 */
export type WidgetDataMode = 'single' | 'grouped' | 'none'

export interface WidgetDef {
  type: string
  label: string
  icon: string
  category: WidgetCategory
  dataMode: WidgetDataMode
  /** 12 列栅格中的默认列宽 */
  defaultSpan: number
  /** 默认高度（px） */
  defaultHeight: number
  defaultTitle: string
  /** 允许的指标数量上限（grouped 类型用） */
  maxMetrics?: number
}

export const WIDGET_DEFS: WidgetDef[] = [
  // ---- 图表 ----
  { type: 'stat_number', label: '统计数字', icon: 'ri-hashtag', category: 'chart', dataMode: 'single', defaultSpan: 2, defaultHeight: 140, defaultTitle: '统计数字' },
  { type: 'metric_card', label: '指标卡', icon: 'ri-bank-card-line', category: 'chart', dataMode: 'single', defaultSpan: 2, defaultHeight: 150, defaultTitle: '指标卡' },
  { type: 'bar', label: '柱状图', icon: 'ri-bar-chart-2-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 260, defaultTitle: '柱状图' },
  { type: 'line', label: '折线图', icon: 'ri-line-chart-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 260, defaultTitle: '折线图' },
  { type: 'hbar', label: '条形图', icon: 'ri-bar-chart-horizontal-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 260, defaultTitle: '条形图' },
  { type: 'area', label: '面积图', icon: 'ri-pulse-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 260, defaultTitle: '面积图' },
  { type: 'pie', label: '饼图', icon: 'ri-pie-chart-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 280, defaultTitle: '饼图' },
  { type: 'donut', label: '环形图', icon: 'ri-donut-chart-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 280, defaultTitle: '环形图' },
  { type: 'combo', label: '组合图', icon: 'ri-stack-line', category: 'chart', dataMode: 'grouped', defaultSpan: 4, defaultHeight: 280, defaultTitle: '组合图', maxMetrics: 2 },
  { type: 'radar', label: '雷达图', icon: 'ri-radar-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 300, defaultTitle: '雷达图', maxMetrics: 4 },
  { type: 'funnel', label: '漏斗图', icon: 'ri-filter-3-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 280, defaultTitle: '漏斗图' },
  { type: 'treemap', label: '矩形树图', icon: 'ri-layout-grid-line', category: 'chart', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 280, defaultTitle: '矩形树图' },
  // ---- 组件 ----
  { type: 'rank', label: '排行榜', icon: 'ri-trophy-line', category: 'component', dataMode: 'grouped', defaultSpan: 3, defaultHeight: 300, defaultTitle: '排行榜' },
  { type: 'progress', label: '进度图', icon: 'ri-percent-line', category: 'component', dataMode: 'single', defaultSpan: 2, defaultHeight: 150, defaultTitle: '进度图' },
  { type: 'text', label: '文本', icon: 'ri-text', category: 'component', dataMode: 'none', defaultSpan: 4, defaultHeight: 120, defaultTitle: '文本' },
  { type: 'image', label: '图片', icon: 'ri-image-line', category: 'component', dataMode: 'none', defaultSpan: 3, defaultHeight: 240, defaultTitle: '图片' },
  { type: 'clock', label: '时钟', icon: 'ri-time-line', category: 'component', dataMode: 'none', defaultSpan: 2, defaultHeight: 150, defaultTitle: '时钟' },
]

const DEF_MAP = new Map(WIDGET_DEFS.map((d) => [d.type, d]))

/** 旧版 kpi 类型按指标卡渲染 */
export function getWidgetDef(type: string): WidgetDef {
  const def = DEF_MAP.get(type)
  if (def) return def
  return {
    type,
    label: type === 'kpi' ? '指标卡' : type,
    icon: 'ri-dashboard-2-line',
    category: 'chart',
    dataMode: type === 'kpi' ? 'single' : 'grouped',
    defaultSpan: 3,
    defaultHeight: 240,
    defaultTitle: type === 'kpi' ? '指标卡' : type,
  }
}

export function isStaticWidget(type: string): boolean {
  return getWidgetDef(type).dataMode === 'none'
}

// ==================== 组件实例与布局模型 ====================

export interface WidgetMetric {
  fieldId?: number | null
  /** 多数据源模式下按字段名跨表对齐（此时 fieldId 为空） */
  fieldName?: string | null
  aggregation: string
}

export interface WidgetFilterRule {
  fieldId: number | null
  operator: string
  value?: string
}

/** 多数据源模式：单个数据源 = 一张表 + 该表自己的筛选 */
export interface WidgetSourceConfig {
  tableId: number
  filterConfig?: { logic: string; rules: WidgetFilterRule[] } | null
}

export interface WidgetInstance {
  /** 后端真实 id 为 number，新建未保存时为临时字符串 id */
  id: number | string
  type: string
  title: string
  dataSourceConfig: {
    tableId?: number
    /** 多数据源模式：一个组件同时聚合多张数据表 */
    multiSource?: boolean
    /** 多数据源模式的数据表列表（每源可带独立筛选）；与 tableId 二选一，sources 非空时优先生效 */
    sources?: WidgetSourceConfig[]
    filterConfig?: { logic: string; rules: WidgetFilterRule[] } | null
    dimension?: { fieldId?: number | null; fieldName?: string | null; granularity?: string }
    metrics?: WidgetMetric[]
    sort?: string
    limit?: number
  }
  displayConfig: Record<string, any>
  layoutConfig: { rowId?: string; span: number; height: number }
  sortNo?: number
}

export interface DashboardRow {
  id: string
  title: string | null
  widgets: Array<number | string>
}

let tempSeq = 0
export function newTempWidgetId(): string {
  tempSeq += 1
  return `tmp_${Date.now()}_${tempSeq}`
}

export function newRowId(): string {
  tempSeq += 1
  return `row_${Date.now()}_${tempSeq}`
}

/** 按类型创建组件实例（含默认配置） */
export function createWidget(type: string, rowId: string): WidgetInstance {
  const def = getWidgetDef(type)
  const instance: WidgetInstance = {
    id: newTempWidgetId(),
    type,
    title: def.defaultTitle,
    dataSourceConfig: {},
    displayConfig: {},
    layoutConfig: { rowId, span: def.defaultSpan, height: def.defaultHeight },
  }
  if (def.dataMode === 'single') {
    instance.dataSourceConfig.metrics = [{ fieldId: null, aggregation: 'count' }]
    instance.displayConfig.decimals = 0
  } else if (def.dataMode === 'grouped') {
    instance.dataSourceConfig.metrics = [{ fieldId: null, aggregation: 'count' }]
    instance.dataSourceConfig.sort = type === 'rank' ? 'desc' : 'default'
    if (type === 'rank') instance.dataSourceConfig.limit = 10
    instance.displayConfig.colorScheme = 'default'
    instance.displayConfig.showLegend = true
  } else if (type === 'text') {
    instance.displayConfig.content = '双击右侧属性面板编辑文本内容'
  } else if (type === 'clock') {
    instance.displayConfig.showDate = true
  }
  return instance
}

/** 参与数值聚合时可选择的字段类型 */
export const NUMERIC_FIELD_TYPES = new Set([
  'number', 'currency', 'progress', 'rating', 'auto_number', 'rollup',
])

/** 可作为分维度的字段类型（其余类型也可用，这里仅用于排序提示） */
export const DATE_FIELD_TYPES = new Set(['date', 'created_time', 'last_modified_time'])

/** 判断字段是否适合作为聚合数值字段（公式字段结果类型未知，一并放开） */
export function isMetricField(field: { fieldType: string }): boolean {
  return NUMERIC_FIELD_TYPES.has(field.fieldType) || field.fieldType === 'formula' || field.fieldType === 'lookup'
}

/** 筛选操作符（与后端统一查询引擎对齐） */
export const FILTER_OPERATORS: Array<{ value: string; label: string }> = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'contains', label: '包含' },
  { value: 'not_contains', label: '不包含' },
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' },
  { value: 'is_empty', label: '为空' },
  { value: 'is_not_empty', label: '不为空' },
]

export const AGGREGATION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'count', label: '计数' },
  { value: 'sum', label: '求和' },
  { value: 'avg', label: '平均' },
  { value: 'max', label: '最大' },
  { value: 'min', label: '最小' },
]
