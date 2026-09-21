/**
 * 多维表格字段属性：默认值 / 兼容迁移 / 取值清洗 / 展示格式化 / 录入校验
 *
 * 设计约定：
 * 1. 同一语义只允许一个 config 键。历史数据里的旧键（format / digits / symbol / length /
 *    progressFormat / allowMultiple / allowedFileTypes / countryCode 等）只在
 *    `normalizeFieldConfig()` 读取时迁移，写入一律用规范键。
 * 2. 每个字段类型只允许它自己的键 + 通用键落地，`sanitizeFieldConfig()` 负责裁剪，
 *    避免切换字段类型后残留一堆无关配置。
 * 3. 本文件是纯函数模块（不依赖 Vue 实例），供字段属性面板、网格渲染器、表单视图共用。
 */
import type {
  BitableField,
  CellValue,
  FieldConfig,
  FieldOption,
  FieldPermissionLevel,
  FieldType,
} from '@/types/bitable'
import { stripRichTextHtml } from '@/utils/bitableRichText'

// ==================== 常量 ====================

/** 系统自动填充、绝对只读的字段类型（不参与手动编辑与校验） */
export const READONLY_FIELD_TYPES = new Set<string>([
  'auto_number',
  'created_time',
  'modified_time',
  'last_modified_time',
  'created_user',
  'modified_user',
  'created_by',
  'modified_by',
  'formula',
  'lookup',
  'rollup',
  'button',
])

/** 系统自动写入时间/人员的字段类型（属性面板只暴露格式相关配置） */
export const SYSTEM_FIELD_TYPES = new Set<string>([
  'created_time',
  'modified_time',
  'last_modified_time',
  'created_user',
  'modified_user',
  'created_by',
  'modified_by',
])

/** 选项型字段（选项管理 + 允许新增 + 默认选项） */
export const OPTION_FIELD_TYPES = new Set<string>(['single_select', 'multi_select', 'process'])

/** 关联型字段 */
export const LINK_FIELD_TYPES = new Set<string>(['link', 'bidirectional_link'])

/** 支持「唯一」约束的字段类型（空值不参与唯一性校验） */
export const UNIQUE_CAPABLE_TYPES = new Set<string>([
  'text',
  'number',
  'currency',
  'email',
  'phone',
  'url',
  'barcode',
  'auto_number',
])

/** 支持「默认值」配置的字段类型 */
export const DEFAULT_VALUE_TYPES = new Set<string>([
  'text',
  'number',
  'currency',
  'date',
  'single_select',
  'multi_select',
  'checkbox',
  'user',
  'rating',
  'progress',
  'url',
  'email',
  'phone',
])

export const CURRENCY_OPTIONS: { value: string; label: string; symbol: string }[] = [
  { value: 'CNY', label: '人民币 CNY', symbol: '¥' },
  { value: 'USD', label: '美元 USD', symbol: '$' },
  { value: 'EUR', label: '欧元 EUR', symbol: '€' },
  { value: 'JPY', label: '日元 JPY', symbol: '¥' },
  { value: 'GBP', label: '英镑 GBP', symbol: '£' },
  { value: 'HKD', label: '港元 HKD', symbol: 'HK$' },
  { value: 'KRW', label: '韩元 KRW', symbol: '₩' },
  { value: 'SGD', label: '新加坡元 SGD', symbol: 'S$' },
]

export const RATING_ICON_OPTIONS: { value: NonNullable<FieldConfig['ratingIcon']>; label: string }[] = [
  { value: 'star', label: '星星' },
  { value: 'heart', label: '爱心' },
  { value: 'thumb', label: '点赞' },
  { value: 'number', label: '数字' },
]

export const DATE_FORMAT_OPTIONS = [
  'YYYY-MM-DD',
  'YYYY/MM/DD',
  'YYYY年MM月DD日',
  'DD/MM/YYYY',
  'MM/DD/YYYY',
  'YYYYMMDD',
]

/** 预设选项颜色（与 bitableCellRenderers 的 TAG_COLOR_VARS 对齐） */
export const OPTION_COLORS = [
  'red', 'orange', 'yellow', 'green', 'teal', 'blue', 'purple', 'pink', 'gray',
]

const RATING_ICON_CHARS: Record<NonNullable<FieldConfig['ratingIcon']>, string> = {
  star: '★',
  heart: '♥',
  thumb: '👍',
  number: '',
}

// ==================== 每类型允许的配置键 ====================

/** 所有类型都可用的通用键 */
const COMMON_KEYS: (keyof FieldConfig)[] = [
  'defaultValue',
  'formHidden',
  'formPlaceholder',
  'unique',
]

/**
 * 各字段类型专属的配置键白名单。
 * `sanitizeFieldConfig` 依据它裁剪，`normalizeFieldConfig` 依据它补默认值。
 */
const TYPE_KEYS: Record<string, (keyof FieldConfig)[]> = {
  text: ['inputMode', 'maxLength', 'pattern', 'patternMessage'],
  phone: ['countryCode', 'allowCountrySwitch', 'masked'],
  email: ['allowedEmailDomains', 'emailClickable'],
  url: ['displayText', 'openInNewTab'],
  location: ['locationInputMethod', 'locationDisplayMode'],
  number: [
    'numberFormat', 'precision', 'thousandSeparator', 'prefix', 'suffix', 'min', 'max',
  ],
  currency: ['currency', 'currencySymbolPosition', 'precision', 'thousandSeparator'],
  progress: ['progressStyle', 'step', 'progressColorMode', 'progressColor', 'progressRules'],
  rating: ['maxRating', 'ratingIcon', 'ratingColor', 'allowHalf'],
  single_select: ['options', 'defaultOption', 'allowAddOption', 'formPlaceholder'],
  multi_select: ['options', 'defaultOption', 'allowAddOption', 'maxSelect', 'formPlaceholder'],
  process: ['options', 'defaultOption', 'allowAddOption', 'formPlaceholder'],
  checkbox: ['defaultChecked', 'checkboxStyle'],
  check: ['defaultChecked', 'checkboxStyle'],
  date: ['dateFormat', 'withTime', 'timeFormat', 'dateDefaultMode'],
  date_range: ['dateFormat', 'withTime', 'timeFormat', 'dateDefaultMode'],
  created_time: ['dateFormat', 'withTime', 'timeFormat'],
  modified_time: ['dateFormat', 'withTime', 'timeFormat'],
  last_modified_time: ['dateFormat', 'withTime', 'timeFormat'],
  user: ['userMode', 'userScope', 'userDeptIds', 'userDisplay', 'defaultCurrentUser'],
  group: ['groupMode', 'groupScope'],
  attachment: [
    'fileTypeLimit', 'allowedExtensions', 'maxFileSizeMb', 'maxFiles', 'attachmentDisplay',
  ],
  barcode: [
    'barcodeType', 'barcodeSource', 'barcodeSourceFieldId',
    'showBarcodeText', 'barcodeSize', 'barcodeColor',
  ],
  auto_number: ['prefix', 'suffix', 'dateFormat', 'digits', 'resetCycle'],
  button: ['button'],
  link: [
    'linkTargetTableId', 'linkDisplayFieldId', 'allowMultipleLink', 'linkDeleteStrategy',
  ],
  bidirectional_link: [
    'linkTargetTableId', 'linkDisplayFieldId', 'reverseFieldId',
    'allowMultipleLink', 'linkDeleteStrategy',
  ],
  lookup: ['linkFieldId', 'targetFieldId', 'lookupFieldId', 'aggregation'],
  rollup: ['linkFieldId', 'targetFieldId', 'rollupFieldId', 'aggregation', 'rollupFormat'],
  formula: ['formulaExpr', 'formulaResultFormat', 'formulaErrorDisplay', 'formulaErrorText'],
  ai_text: ['sourceFieldIds', 'autoCompute', 'aiTriggerMode', 'aiTemperature', 'aiMaxTokens', 'aiFallbackMode', 'aiFallbackText'],
  ai_select: ['options', 'sourceFieldIds', 'autoCompute', 'aiTriggerMode', 'aiTemperature', 'aiMaxTokens', 'aiFallbackMode', 'aiFallbackText'],
  created_by: [],
  modified_by: [],
  created_user: [],
  modified_user: [],
  department: ['departmentMode', 'departmentScope', 'departmentIds'],
}

/** 取某字段类型允许的全部配置键（通用 + 专属） */
export function allowedConfigKeys(fieldType: string): (keyof FieldConfig)[] {
  return [...COMMON_KEYS, ...(TYPE_KEYS[fieldType] || [])]
}

// ==================== 默认值 / 迁移 / 清洗 ====================

/** 该字段类型是否有可配置属性（除通用属性外） */
export function hasTypeSpecificAttributes(fieldType: string): boolean {
  return (TYPE_KEYS[fieldType] || []).length > 0
}

/** 某类型的属性默认值（新建字段时的初始配置） */
export function createDefaultFieldConfig(fieldType: string): FieldConfig {
  const base: FieldConfig = {}
  switch (fieldType) {
    case 'text':
      return { ...base, inputMode: 'single' }
    case 'rich_text':
      // 富文本无需类型专属配置；formPlaceholder 由属性面板通用区写入
      return { ...base }
    case 'phone':
      return { ...base, countryCode: '+86', allowCountrySwitch: false, masked: false }
    case 'email':
      // 白名单先给空数组：面板模板要按索引直接写，undefined 会让 v-model 失焦
      return { ...base, emailClickable: true, allowedEmailDomains: [] }
    case 'url':
      return { ...base, openInNewTab: true }
    case 'location':
      return { ...base, locationInputMethod: 'text', locationDisplayMode: 'address' }
    case 'number':
      return { ...base, numberFormat: 'plain', precision: 0, thousandSeparator: false }
    case 'currency':
      return {
        ...base,
        currency: 'CNY',
        currencySymbolPosition: 'prefix',
        precision: 2,
        thousandSeparator: true,
      }
    case 'progress':
      return {
        ...base,
        progressStyle: 'bar',
        step: 5,
        progressColorMode: 'single',
        progressColor: '#3B82F6',
      }
    case 'rating':
      return { ...base, maxRating: 5, ratingIcon: 'star', allowHalf: false }
    case 'single_select':
    case 'multi_select':
    case 'process':
    case 'ai_select':
      return { ...base, options: [], allowAddOption: false }
    case 'checkbox':
    case 'check':
      return { ...base, defaultChecked: false, checkboxStyle: 'checkbox' }
    case 'date':
    case 'date_range':
      return { ...base, dateFormat: 'YYYY-MM-DD', withTime: false, timeFormat: '24h', dateDefaultMode: 'none' }
    case 'created_time':
    case 'modified_time':
    case 'last_modified_time':
      return { ...base, dateFormat: 'YYYY-MM-DD', withTime: true, timeFormat: '24h' }
    case 'user':
      return { ...base, userMode: 'single', userScope: 'all', userDisplay: 'name' }
    case 'group':
      return { ...base, groupMode: 'single', groupScope: 'joined' }
    case 'department':
      // 同 email：白名单数组先给空，面板模板要按索引直接写
      return { ...base, departmentMode: 'single', departmentScope: 'all', departmentIds: [] }
    case 'attachment':
      return {
        ...base,
        fileTypeLimit: 'any',
        maxFileSizeMb: 50,
        maxFiles: 10,
        attachmentDisplay: 'list',
      }
    case 'barcode':
      return {
        ...base,
        barcodeType: 'qrcode',
        barcodeSource: 'self',
        showBarcodeText: true,
        barcodeSize: 120,
        barcodeColor: '#0F172A',
      }
    case 'auto_number':
      return { ...base, prefix: '', suffix: '', digits: 4, resetCycle: 'never' }
    case 'button':
      return { ...base, button: { actionType: 'openUrl', color: '#3B82F6' } }
    case 'link':
    case 'bidirectional_link':
      return { ...base, allowMultipleLink: true, linkDeleteStrategy: 'clear' }
    case 'lookup':
      return { ...base }
    case 'rollup':
      return { ...base, aggregation: 'sum', rollupFormat: 'number' }
    case 'formula':
      return { ...base, formulaResultFormat: 'auto', formulaErrorDisplay: 'empty' }
    case 'ai_text':
      return { ...base, autoCompute: false, aiTriggerMode: 'manual', aiTemperature: 0.3, aiFallbackMode: 'empty' }
    default:
      return { ...base }
  }
}

/**
 * 读取字段配置：迁移历史键 → 补默认值。
 *
 * 历史键映射：
 * - `format` → `dateFormat`（日期/系统时间）或忽略（数字，数字用 `digits`）
 * - `digits` → `precision`（数字/货币）或保留（自动编号序号位数）
 * - `length` → `digits`（自动编号）
 * - `symbol` → `ratingColor`（评分，旧值存的是单个字符） / `currency` 符号
 * - `progressFormat` → `progressStyle`（'percent' → 'percent'，'value' → 'number'）
 * - `allowMultiple` → `allowMultipleLink`（关联） / `userMode`（人员）
 * - `allowedFileTypes` → `allowedExtensions`
 * - `countryCode` 保留（已是规范键）
 * - `barcodeMode` → `barcodeType`
 */
export function normalizeFieldConfig(fieldType: string, raw?: FieldConfig | null): FieldConfig {
  const defaults = createDefaultFieldConfig(fieldType)
  const source = (raw && typeof raw === 'object' ? { ...raw } : {}) as Record<string, unknown>

  // ---- 历史键迁移 ----
  if (source.dateFormat === undefined && typeof source.format === 'string') {
    source.dateFormat = source.format
  }
  if (fieldType === 'auto_number') {
    if (source.digits === undefined && typeof source.length === 'number') {
      source.digits = source.length
    }
  } else if (source.precision === undefined && typeof source.digits === 'number') {
    source.precision = source.digits
  }
  if (fieldType === 'rating' && source.ratingColor === undefined && typeof source.symbol === 'string' && source.symbol) {
    // 旧版 symbol 存的是评分字符（★/♥），只有是颜色时才当颜色用
    if (/^#|^rgb/i.test(source.symbol)) source.ratingColor = source.symbol
  }
  if (source.progressStyle === undefined && typeof source.progressFormat === 'string') {
    source.progressStyle = source.progressFormat === 'value' ? 'number' : 'percent'
  }
  if (LINK_FIELD_TYPES.has(fieldType) && source.allowMultipleLink === undefined && typeof source.allowMultiple === 'boolean') {
    source.allowMultipleLink = source.allowMultiple
  }
  if (fieldType === 'user' && source.userMode === undefined && typeof source.allowMultiple === 'boolean') {
    source.userMode = source.allowMultiple ? 'multiple' : 'single'
  }
  if (source.allowedExtensions === undefined && Array.isArray(source.allowedFileTypes)) {
    source.allowedExtensions = source.allowedFileTypes
  }
  if (source.barcodeType === undefined && typeof source.barcodeMode === 'string') {
    source.barcodeType = source.barcodeMode
  }
  if (source.maxRating === undefined && typeof source.max === 'number' && fieldType === 'rating') {
    source.maxRating = source.max
  }
  // 旧版自动编号把前缀存在 config.prefix（已是规范键），保留

  // ---- 按白名单取值 + 补默认 ----
  const allowed = allowedConfigKeys(fieldType)
  const result: Record<string, unknown> = { ...defaults }
  for (const key of allowed) {
    if (source[key] !== undefined && source[key] !== null && source[key] !== '') {
      result[key] = source[key]
    }
  }
  // 选项数组做一次深拷贝，避免面板直接改到 store 里的对象
  if (Array.isArray(result.options)) {
    result.options = (result.options as FieldOption[]).map((opt) => ({
      label: String(opt?.label ?? ''),
      color: opt?.color,
      desc: opt?.desc,
    }))
  }
  if (Array.isArray(result.sourceFieldIds)) {
    result.sourceFieldIds = (result.sourceFieldIds as unknown[]).map((v) => Number(v)).filter((v) => Number.isFinite(v))
  }
  return result as FieldConfig
}

/** 写入前清洗：只保留该类型允许的键，并丢掉空值 */
export function sanitizeFieldConfig(fieldType: string, config?: FieldConfig | null): FieldConfig {
  const allowed = allowedConfigKeys(fieldType)
  const source = (config || {}) as Record<string, unknown>
  const result: Record<string, unknown> = {}
  for (const key of allowed) {
    const value = source[key]
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value) && value.length === 0) continue
    if (key === 'options') {
      const opts = (value as FieldOption[]).filter((o) => String(o?.label ?? '').trim())
      if (opts.length) result.options = opts
      continue
    }
    result[key] = value
  }
  return result as FieldConfig
}

// ==================== 展示格式化 ====================

export function currencySymbol(code?: string): string {
  return CURRENCY_OPTIONS.find((c) => c.value === code)?.symbol ?? '¥'
}

/** 千分位 + 固定小数位 */
export function formatDecimal(value: number, precision = 0, thousand = false): string {
  const fixed = Number.isFinite(value) ? value.toFixed(Math.max(0, Math.min(10, precision))) : '0'
  if (!thousand) return fixed
  const [intPart, decimalPart] = fixed.split('.')
  const sign = intPart.startsWith('-') ? '-' : ''
  const digits = sign ? intPart.slice(1) : intPart
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return decimalPart ? `${sign}${grouped}.${decimalPart}` : `${sign}${grouped}`
}

/**
 * 数字/货币单元格的展示文本。
 *
 * 百分比约定：**不做 100 倍缩放**，输入 85 就展示 `85%`。
 * 若按「存储 0.85 展示 85%」实现，用户在网格里输入 0.85 却看到 85%，
 * 而同一字段的最小/最大值又按输入值比较，两套单位会互相矛盾。
 */
export function formatNumberCell(value: unknown, config: FieldConfig): string {
  if (value === null || value === undefined || value === '') return ''
  const num = Number(value)
  if (!Number.isFinite(num)) return String(value)
  const precision = config.precision ?? 0
  const thousand = config.thousandSeparator ?? false
  const body = formatDecimal(num, precision, thousand)
  if (config.numberFormat === 'percent') return `${body}%`
  const prefix = config.prefix ?? ''
  const suffix = config.suffix ?? ''
  return `${prefix}${body}${suffix}`
}

/** 货币单元格的展示文本 */
export function formatCurrencyCell(value: unknown, config: FieldConfig): string {
  if (value === null || value === undefined || value === '') return ''
  const num = Number(value)
  if (!Number.isFinite(num)) return String(value)
  const symbol = currencySymbol(config.currency)
  const body = formatDecimal(num, config.precision ?? 2, config.thousandSeparator ?? true)
  return config.currencySymbolPosition === 'suffix' ? `${body}${symbol}` : `${symbol}${body}`
}

/** 进度百分比对应的颜色 */
export function resolveProgressColor(percent: number, config: FieldConfig): string {
  if (config.progressColorMode === 'threshold' && config.progressRules?.length) {
    const rules = [...config.progressRules].sort((a, b) => a.below - b.below)
    const hit = rules.find((rule) => percent < rule.below)
    if (hit?.color) return hit.color
  }
  return config.progressColor || 'var(--gradient-progress-fill, #3B82F6)'
}

export function ratingIconChar(icon?: FieldConfig['ratingIcon']): string {
  return RATING_ICON_CHARS[icon || 'star'] ?? '★'
}

/** 日期格式（含时间时拼接时间部分） */
export function resolveDatePattern(config: FieldConfig): string {
  const base = config.dateFormat || 'YYYY-MM-DD'
  if (!config.withTime) return base
  return config.timeFormat === '12h' ? `${base} hh:mm A` : `${base} HH:mm`
}

/** 电话脱敏：138****1234 */
export function maskPhone(value: string): string {
  const digits = value.replace(/\D/g, '')
  if (digits.length < 7) return value
  return `${digits.slice(0, 3)}****${digits.slice(-4)}`
}

/**
 * 日期单元格的展示文本。
 * 后端 value_date 统一为 `yyyy-MM-dd HH:mm:ss`（未开启「包含时间」时为 00:00:00）。
 *
 * 占位符同时支持 `YYYY`/`yyyy`（年）与 `DD`/`dd`（日）两套写法：
 * DATE_FORMAT_OPTIONS / createDefaultFieldConfig 用的是 `YYYY-MM-DD`，
 * 而 Element Plus 的 value-format 用 `yyyy-MM-dd`，只认一套会把
 * `YYYY-MM-DD` 原样吐出来（曾出现日期列显示 `YYYY/03/DD`）。
 */
export function formatDateCell(raw: string | null | undefined, config: FieldConfig): string {
  if (raw === null || raw === undefined || raw === '') return ''
  const matched = String(raw).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if (!matched) return String(raw)
  const [, y, mo, d, hh = '00', mi = '00', ss = '00'] = matched

  const hour24 = Number(hh)
  const hour12 = hour24 % 12 === 0 ? 12 : hour24 % 12
  const pad = (n: number) => String(n).padStart(2, '0')
  const map: Record<string, string> = {
    YYYY: y,
    yyyy: y,
    MM: mo,
    DD: d,
    dd: d,
    HH: hh,
    hh: pad(hour12),
    mm: mi,
    ss,
    SS: ss,
    A: hour24 < 12 ? 'AM' : 'PM',
  }
  const pattern = resolveDatePattern(config)
  const rendered = pattern.replace(/YYYY|yyyy|MM|DD|dd|HH|hh|mm|ss|SS|A/g, (token) => map[token] ?? token)
  // 未开启「包含时间」时，即便配置里写了时间占位符也只展示日期部分
  return config.withTime ? rendered : rendered.replace(/[ ]?(HH:mm(?::ss)?|hh:mm(?::ss)? ?A)$/, '')
}

/** 附件值 → 文件名列表（兼容数组 / JSON 字符串 / 逗号分隔字符串三种历史形态） */
function attachmentNames(raw: unknown): string[] {
  if (raw == null || raw === '') return []
  let parsed: unknown = raw
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed) return []
    if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
      try {
        parsed = JSON.parse(trimmed)
      } catch {
        return [trimmed]
      }
    } else {
      return trimmed
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
    }
  }
  const list = Array.isArray(parsed) ? parsed : [parsed]
  return list
    .map((item) => {
      if (typeof item === 'string') return item
      if (item && typeof item === 'object') {
        const rec = item as Record<string, unknown>
        const name = rec.name ?? rec.fileName ?? rec.url
        return typeof name === 'string' ? name : ''
      }
      return ''
    })
    .filter(Boolean)
}

/**
 * 日期范围值 → { start, end }。
 * 兼容两种存储格式：`{start, end}` 对象与 `[start, end]` 数组（表单视图提交的格式），
 * 以及它们被序列化成 JSON 字符串的情况。取不到完整范围时返回 null。
 */
export function parseDateRange(raw: unknown): { start: string; end: string } | null {
  let value = raw
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
    try {
      value = JSON.parse(trimmed)
    } catch {
      return null
    }
  }
  if (Array.isArray(value)) {
    const [start, end] = value
    if (!start || !end) return null
    return { start: String(start), end: String(end) }
  }
  if (value && typeof value === 'object') {
    const { start, end } = value as { start?: unknown; end?: unknown }
    if (!start || !end) return null
    return { start: String(start), end: String(end) }
  }
  return null
}

/**
 * 地理位置值 → 展示文本。
 * 值可能是 `{name, address, lat, lng}` 对象、JSON 字符串，或直接是地址文本。
 * 不处理的话网格里会渲染成 `[object Object]`。
 */
export function formatLocationCell(raw: unknown, config: FieldConfig): string {
  if (raw == null || raw === '') return ''
  let parsed: unknown = raw
  if (typeof parsed === 'string') {
    const trimmed = parsed.trim()
    if (!trimmed) return ''
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      try {
        parsed = JSON.parse(trimmed)
      } catch {
        return trimmed
      }
    } else {
      return trimmed
    }
  }
  if (typeof parsed !== 'object' || parsed === null) return String(parsed)
  const rec = parsed as Record<string, unknown>
  const mode = config.locationDisplayMode || 'name'
  const asText = (v: unknown) => (typeof v === 'string' ? v : typeof v === 'number' ? String(v) : '')
  if (mode === 'latlng') {
    const lat = asText(rec.lat ?? rec.latitude)
    const lng = asText(rec.lng ?? rec.longitude)
    return lat && lng ? `${lat},${lng}` : ''
  }
  if (mode === 'address') return asText(rec.address) || asText(rec.name)
  return asText(rec.name) || asText(rec.address)
}

/**
 * 单元格的展示文本 —— 所有视图（网格 / 看板 / 画廊 / 日历 / 甘特）共用的唯一取值口径。
 *
 * 字段属性（精度 / 前后缀 / 日期格式 / 评分图标 / 电话脱敏 …）都在这里生效。
 * 若各视图各写一套取值逻辑，就会出现「同一条数据网格里显示 85%、看板里显示 8500%」
 * 这类不一致，因此取值口径必须收敛到这一个函数。
 *
 * 约定：入参 field 的 config 已由 `normalizeFieldConfig()` 归一（editor.vue#normalizeField）。
 */
export function formatCellDisplay(field: BitableField, cell?: CellValue | null): string {
  if (!cell) return ''
  const config = field.config || {}
  switch (field.fieldType) {
    case 'number':
      return formatNumberCell(cell.valueNumber, config)
    case 'currency':
      return formatCurrencyCell(cell.valueNumber, config)
    case 'progress': {
      const n = Number(cell.valueNumber)
      if (!Number.isFinite(n)) return ''
      // 与 formatNumberCell 的百分比约定保持一致：不做 100 倍缩放
      return `${formatDecimal(n, config.precision ?? 0, false)}%`
    }
    case 'rating': {
      const n = Number(cell.valueNumber)
      if (!Number.isFinite(n) || n <= 0) return ''
      const max = Number(config.maxRating) || 5
      const icon = ratingIconChar(config.ratingIcon)
      // ratingIcon='number' 时 ratingIconChar 返回空串 → 用「n / max」文本样式
      if (!icon) return `${n} / ${max}`
      const full = Math.min(Math.floor(n), max)
      const half = config.allowHalf && n - full >= 0.5 ? '½' : ''
      return icon.repeat(full) + half
    }
    case 'date':
      return formatDateCell(cell.valueDate, config)
    // 日期范围是结构化值，不处理会被 String() 成 "[object Object]"
    case 'date_range': {
      const range = parseDateRange(cell.valueJson)
      if (!range) return cell.valueText || ''
      return range.start === range.end ? range.start : `${range.start} ~ ${range.end}`
    }
    // 系统时间字段后端只下发已按字段配置格式化好的 valueText（没有 valueDate），
    // 因此优先用 valueDate，缺失时回退 valueText，避免这些列变成空白。
    case 'created_time':
    case 'modified_time':
    case 'last_modified_time':
      return cell.valueDate ? formatDateCell(cell.valueDate, config) : cell.valueText || ''
    case 'checkbox':
      return cell.valueText === 'true' ? '✓' : '✗'
    case 'multi_select':
    case 'user':
    case 'group':
    case 'department':
    case 'created_user':
    case 'modified_user':
    case 'created_by':
    case 'modified_by':
    case 'link':
    case 'bidirectional_link': {
      if (Array.isArray(cell.valueJson)) {
        const list = (cell.valueJson as unknown[]).filter((v): v is string => typeof v === 'string')
        if (list.length) return list.join(', ')
      }
      return cell.valueText || ''
    }
    case 'phone':
      return config.masked && cell.valueText ? maskPhone(cell.valueText) : cell.valueText || ''
    case 'attachment':
      return attachmentNames(cell.valueJson).join(', ')
    case 'location':
      return formatLocationCell(cell.valueJson, config) || cell.valueText || ''
    case 'url':
      return config.displayText || cell.valueText || ''
    case 'rich_text':
      // 只读预览走纯文本口径，HTML 交给网格渲染器/详情弹窗处理
      return stripRichTextHtml(cell.valueText || '')
    default:
      if (cell.valueText) return cell.valueText
      return cell.valueJson != null ? String(cell.valueJson) : ''
  }
}

/**
 * 卡片标题文本：视图指定标题字段 → 第一个文本字段 → 第一个有值的字段 → 「记录 N」。
 *
 * 看板 / 画廊 / 日历 / 甘特 共用。此前各视图只认「第一个文本字段」，
 * 表里没有文本字段时整块卡片全变成「记录 12」，有数据的选项/数字字段反而不显示。
 */
export function resolveRecordTitle(
  fields: BitableField[],
  record: { id: number; cells?: Record<number, CellValue> },
  preferredFieldId?: number | null,
): string {
  const pick = (field?: BitableField): string => {
    if (!field) return ''
    return formatCellDisplay(field, record.cells?.[field.id]).trim()
  }

  const preferred = pick(fields.find((f) => f.id === preferredFieldId))
  if (preferred) return preferred

  const textValue = pick(fields.find((f) => f.fieldType === 'text'))
  if (textValue) return textValue

  // 兜底：第一个有值的字段。系统自动字段（创建/修改时间、创建人）没有信息量，跳过。
  for (const field of fields) {
    if (SYSTEM_FIELD_TYPES.has(field.fieldType)) continue
    const value = pick(field)
    if (value) return value
  }
  return `记录 ${record.id}`
}

/** 字段权限级别 → 展示文案 */
export function fieldPermissionLabel(level: FieldPermissionLevel | undefined): string {
  switch (level) {
    case 'hidden':
      return '隐藏'
    case 'readonly':
      return '只读'
    default:
      return '可编辑'
  }
}

/** 该字段对当前用户是否只读（字段类型只读 或 字段权限为只读/隐藏） */
export function isFieldReadonly(field: BitableField): boolean {
  if (READONLY_FIELD_TYPES.has(field.fieldType)) return true
  return field.permission === 'readonly' || field.permission === 'hidden'
}

/** 该字段对当前用户是否不可见 */
export function isFieldHidden(field: BitableField): boolean {
  return field.permission === 'hidden'
}

// ==================== 录入校验 ====================

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const URL_RE = /^(https?:\/\/|www\.)\S+$/i

/**
 * 邮箱域名白名单归一化：去掉前导 `@`、统一小写、丢弃空项。
 * 面板里允许用户写 `example.com` 或 `@example.com`，两种写法都要能用。
 */
export function normalizeEmailDomains(list?: string[] | null): string[] {
  if (!Array.isArray(list)) return []
  return list
    .map((item) => String(item ?? '').trim().toLowerCase().replace(/^@/, ''))
    .filter(Boolean)
}

/** 取校验用的字符串值 */
function toText(value: unknown): string {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

/**
 * 校验单个字段值，返回错误文案；通过返回 null。
 *
 * 覆盖：必填、文本长度、自定义正则、数值范围、邮箱/URL/电话格式、
 * 选项合法性、多选上限。
 */
export function validateFieldValue(field: BitableField, value: unknown): string | null {
  const config = (field.config || {}) as FieldConfig
  const required = Boolean(field.required)
  const type = field.fieldType

  const isEmpty =
    value === null ||
    value === undefined ||
    value === '' ||
    (Array.isArray(value) && value.length === 0)
  if (isEmpty) {
    // 富文本空值是 ''（编辑器空内容输出空串），必填语义与其它类型一致
    return required ? `请填写「${field.name}」` : null
  }

  switch (type) {
    case 'rich_text': {
      // 长度校验没有意义（HTML 标签不算内容），空内容已在 isEmpty 拦截
      return null
    }
    case 'text': {
      const text = toText(value)
      if (config.maxLength && text.length > config.maxLength) {
        return `「${field.name}」最多 ${config.maxLength} 个字符`
      }
      if (config.pattern) {
        try {
          const re = new RegExp(config.pattern)
          if (!re.test(text)) {
            return config.patternMessage || `「${field.name}」格式不正确`
          }
        } catch {
          // 正则本身非法时不做校验（保存时已拦截）
        }
      }
      return null
    }
    case 'number':
    case 'currency':
    case 'progress':
    case 'rating': {
      const num = Number(value)
      if (!Number.isFinite(num)) return `「${field.name}」必须是数字`
      if (type === 'number' || type === 'currency') {
        if (config.min !== undefined && num < config.min) return `「${field.name}」不能小于 ${config.min}`
        if (config.max !== undefined && num > config.max) return `「${field.name}」不能大于 ${config.max}`
      }
      if (type === 'progress' && (num < 0 || num > 100)) return '进度需在 0-100 之间'
      if (type === 'rating') {
        const max = config.maxRating ?? 5
        if (num < 0 || num > max) return `评分需在 0-${max} 之间`
        if (!config.allowHalf && !Number.isInteger(num)) return '评分只能为整数'
      }
      return null
    }
    case 'email': {
      const text = toText(value)
      if (!EMAIL_RE.test(text)) return `「${field.name}」邮箱格式不正确`
      const domains = normalizeEmailDomains(config.allowedEmailDomains)
      if (domains.length) {
        const domain = text.slice(text.lastIndexOf('@') + 1).toLowerCase()
        if (!domains.includes(domain)) {
          return `「${field.name}」仅支持 ${domains.join('、')} 邮箱`
        }
      }
      return null
    }
    case 'url': {
      const text = toText(value)
      return URL_RE.test(text) ? null : `「${field.name}」需以 http:// 或 https:// 开头`
    }
    case 'phone': {
      const text = toText(value)
      const digits = text.replace(/\D/g, '')
      return digits.length >= 6 && digits.length <= 15 ? null : `「${field.name}」电话号码位数不正确`
    }
    case 'single_select':
    case 'process': {
      const text = toText(value)
      const options = config.options || []
      if (!options.length) return null
      if (config.allowAddOption) return null
      return options.some((opt) => opt.label === text) ? null : `「${field.name}」不在可选范围内`
    }
    case 'multi_select': {
      const values = Array.isArray(value) ? value.map((v) => toText(v)) : toText(value).split(',').map((s) => s.trim()).filter(Boolean)
      if (config.maxSelect && values.length > config.maxSelect) {
        return `「${field.name}」最多选择 ${config.maxSelect} 项`
      }
      if (!config.allowAddOption && (config.options || []).length) {
        const allowed = new Set((config.options || []).map((opt) => opt.label))
        const invalid = values.find((v) => !allowed.has(v))
        if (invalid) return `「${field.name}」包含不在可选范围内的选项`
      }
      return null
    }
    case 'attachment': {
      const files = Array.isArray(value) ? value : []
      if (config.maxFiles && files.length > config.maxFiles) {
        return `「${field.name}」最多上传 ${config.maxFiles} 个文件`
      }
      return null
    }
    default:
      return null
  }
}

/** 生成 Element Plus 表单校验规则（供表单视图 / 记录编辑弹窗复用） */
export function buildFieldRules(field: BitableField) {
  return [
    {
      validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
        const message = validateFieldValue(field, value)
        if (message) callback(new Error(message))
        else callback()
      },
      trigger: ['blur', 'change'],
    },
  ]
}

/** 新建记录时该字段的默认值（无默认返回 undefined，调用方跳过该字段） */
export function resolveFieldDefault(field: BitableField): unknown {
  const config = (field.config || {}) as FieldConfig
  const type = field.fieldType

  if (type === 'date' || type === 'date_range') {
    switch (config.dateDefaultMode) {
      case 'now':
        return new Date().toISOString()
      case 'today':
        return new Date().toISOString().slice(0, 10)
      case 'fixed':
        return config.defaultValue ?? undefined
      default:
        return undefined
    }
  }
  if (type === 'checkbox' || type === 'check') {
    return config.defaultChecked ? true : undefined
  }
  if (type === 'user') {
    return config.defaultCurrentUser ? 'currentUser' : undefined
  }
  if (type === 'single_select' || type === 'process') {
    return config.defaultOption || config.defaultValue || undefined
  }
  if (type === 'multi_select') {
    const value = config.defaultValue
    if (Array.isArray(value) && value.length) return value
    if (typeof value === 'string' && value) return [value]
    return undefined
  }
  return config.defaultValue ?? undefined
}

/** 附件类型限制 → 允许的扩展名（含点号，空数组表示不限） */
export function resolveAllowedExtensions(config: FieldConfig): string[] {
  switch (config.fileTypeLimit) {
    case 'image':
      return ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.svg']
    case 'doc':
      return ['.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx', '.pdf', '.txt', '.md', '.csv']
    case 'custom':
      return (config.allowedExtensions || []).map((ext) => (ext.startsWith('.') ? ext : `.${ext}`))
    default:
      return []
  }
}

/** 附件是否允许上传该文件（按扩展名 + 大小） */
export function checkAttachmentFile(
  file: { name: string; size: number },
  config: FieldConfig,
  currentCount: number,
): string | null {
  if (config.maxFiles && currentCount >= config.maxFiles) {
    return `最多上传 ${config.maxFiles} 个文件`
  }
  const allowed = resolveAllowedExtensions(config)
  if (allowed.length) {
    const lower = file.name.toLowerCase()
    if (!allowed.some((ext) => lower.endsWith(ext))) {
      return `仅支持 ${allowed.join('、')} 格式`
    }
  }
  if (config.maxFileSizeMb && file.size > config.maxFileSizeMb * 1024 * 1024) {
    return `单个文件不能超过 ${config.maxFileSizeMb}MB`
  }
  return null
}
