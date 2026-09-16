/**
 * Bitable 自定义单元格渲染器
 *
 * 每个渲染器都从 `cellRender.config`（即字段的 FieldConfig）读取属性，
 * 使「字段属性面板」里配置的格式真正作用于网格展示：
 * - BitableProgress   进度条（样式 / 步长 / 阈值配色）
 * - BitableSelectTag  彩色标签（单选/多选/流程）
 * - BitableCheckbox   复选框 / 开关
 * - BitableRate       星级 / 爱心 / 数字评分
 * - BitableDate       日期（按 dateFormat + withTime 格式化）
 * - BitableNumber     数字（精度 / 千分位 / 前缀后缀）
 * - BitableCurrency   货币（币种符号 / 位置 / 精度）
 * - BitableUrl        超链接（显示文案 / 是否新窗口）
 * - BitablePhone      电话（脱敏）
 * - BitableAttachment 附件（list 文件名列表 / thumbnail 缩略图 / cover 封面）
 * - BitableText       长文本（截断展示）
 *
 * 注册后可在 vxe-table 的 cellRender 中通过 name 引用：
 *   { cellRender: { name: 'BitableProgress', config } }
 */
import { h } from 'vue'
import { VxeUI } from 'vxe-table'
import {
  formatCurrencyCell,
  formatDateCell,
  formatLocationCell,
  formatNumberCell,
  maskPhone,
  parseDateRange,
  ratingIconChar,
  resolveProgressColor,
} from '@/utils/bitableFieldConfig'
import type { FieldConfig } from '@/types/bitable'

// 本地定义渲染器参数类型（vxe-pc-ui 4.13 中 VxeColumnPropTypes.RenderCellParams 未直接导出）
// 使用 any 避免 vxe-table 内部 ColumnInfo 索引签名不兼容问题
type RenderOptions = any
type RenderParams = any

function readConfig(renderOpts: RenderOptions): FieldConfig {
  return (renderOpts?.config || {}) as FieldConfig
}

function isEmptyValue(raw: unknown): boolean {
  return raw == null || raw === '' || (Array.isArray(raw) && raw.length === 0)
}

// 飞书风格调色板（与 FieldConfig.options.color 字符串对应）
// 全部走 CSS 变量（见 styles/tokens/colors.scss），便于主题切换与品牌色联动
const TAG_COLOR_VARS: Record<string, { bg: string; fg: string; border: string }> = {
  red: {
    bg: 'var(--color-tag-red-bg)',
    fg: 'var(--color-tag-red-fg)',
    border: 'var(--color-tag-red-border)',
  },
  orange: {
    bg: 'var(--color-tag-orange-bg)',
    fg: 'var(--color-tag-orange-fg)',
    border: 'var(--color-tag-orange-border)',
  },
  yellow: {
    bg: 'var(--color-tag-yellow-bg)',
    fg: 'var(--color-tag-yellow-fg)',
    border: 'var(--color-tag-yellow-border)',
  },
  green: {
    bg: 'var(--color-tag-green-bg)',
    fg: 'var(--color-tag-green-fg)',
    border: 'var(--color-tag-green-border)',
  },
  teal: {
    bg: 'var(--color-tag-teal-bg)',
    fg: 'var(--color-tag-teal-fg)',
    border: 'var(--color-tag-teal-border)',
  },
  blue: {
    bg: 'var(--color-tag-blue-bg)',
    fg: 'var(--color-tag-blue-fg)',
    border: 'var(--color-tag-blue-border)',
  },
  purple: {
    bg: 'var(--color-tag-purple-bg)',
    fg: 'var(--color-tag-purple-fg)',
    border: 'var(--color-tag-purple-border)',
  },
  pink: {
    bg: 'var(--color-tag-pink-bg)',
    fg: 'var(--color-tag-pink-fg)',
    border: 'var(--color-tag-pink-border)',
  },
  gray: {
    bg: 'var(--color-tag-gray-bg)',
    fg: 'var(--color-tag-gray-fg)',
    border: 'var(--color-tag-gray-border)',
  },
  default: {
    bg: 'var(--color-tag-default-bg)',
    fg: 'var(--color-tag-default-fg)',
    border: 'var(--color-tag-default-border)',
  },
}

function resolveTagColor(color?: string) {
  if (!color) return TAG_COLOR_VARS.default
  return TAG_COLOR_VARS[color] || TAG_COLOR_VARS.default
}

/**
 * 将值规整为数组（用于多选单元格的展示）
 */
function toArrayValue(cellValue: unknown): string[] {
  if (cellValue == null || cellValue === '') return []
  if (Array.isArray(cellValue)) return cellValue.map((v) => String(v))
  // 兼容后端存储的 JSON 字符串或逗号分隔字符串
  const str = String(cellValue)
  if (str.startsWith('[')) {
    try {
      const parsed = JSON.parse(str)
      if (Array.isArray(parsed)) return parsed.map((v) => String(v))
    } catch {
      /* ignore */
    }
  }
  return str.split(',').map((s) => s.trim()).filter(Boolean)
}

const EMPTY = () => h('span', { class: 'bitable-cell-empty' }, '')

/**
 * BitableProgress - 进度条单元格
 * 支持配置：progressStyle(bar/line)、progressColorMode(default/rules)、progressRules
 */
VxeUI.renderer.add('BitableProgress', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    let num = Number(raw)
    if (!Number.isFinite(num)) num = 0
    num = Math.min(100, Math.max(0, num))
    const color = resolveProgressColor(num, config)
    return h('div', { class: 'bitable-progress-cell' }, [
      h('div', { class: 'bitable-progress-cell__bar' }, [
        h('div', {
          class: 'bitable-progress-cell__fill',
          style: { width: `${num}%`, background: color },
        }),
      ]),
      h('span', { class: 'bitable-progress-cell__text' }, `${num}%`),
    ])
  },
})

/**
 * BitableSelectTag - 彩色标签展示
 * 编辑由 VxeSelect 负责，此处只负责展示
 */
VxeUI.renderer.add('BitableSelectTag', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const options = (renderOpts.options || []) as Array<{ label: string; color?: string }>
    const optionProps = renderOpts.optionProps || { label: 'label', value: 'label' }
    const labelKey = optionProps.label || 'label'
    const valueKey = optionProps.value || 'value'
    const isMultiple = !!renderOpts.props?.multiple
    const raw = row[column.field as string]
    const values = isMultiple ? toArrayValue(raw) : raw == null || raw === '' ? [] : [String(raw)]
    if (values.length === 0) {
      return EMPTY()
    }
    return h(
      'div',
      { class: `bitable-tag-cell${isMultiple ? ' is-multiple' : ''}` },
      values.map((val) => {
        const opt = options.find((o) => String((o as Record<string, unknown>)[valueKey]) === val)
        const color = resolveTagColor(opt?.color)
        return h(
          'span',
          {
            class: 'bitable-tag-cell__item',
            style: {
              background: color.bg,
              color: color.fg,
              borderColor: color.border,
            },
          },
          opt ? String((opt as Record<string, unknown>)[labelKey]) : val,
        )
      }),
    )
  },
})

/**
 * BitableCheckbox - 复选框 / 开关单元格
 * 配置：checkboxStyle('checkbox' | 'switch')
 * 编辑：不使用 vxe-table 内置编辑态，由 GridView 的 cell-click 直接 toggle 并 emit
 */
VxeUI.renderer.add('BitableCheckbox', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    const checked = raw === true || raw === 'true' || raw === 'True' || raw === 1 || raw === '1'
    if (config.checkboxStyle === 'switch') {
      return h('div', { class: 'bitable-checkbox-cell' }, [
        h('span', {
          class: `bitable-switch-cell${checked ? ' is-checked' : ''}`,
        }, [h('i', { class: 'bitable-switch-cell__dot' })]),
      ])
    }
    return h('div', { class: 'bitable-checkbox-cell' }, [
      h('i', {
        class: checked
          ? 'ri-checkbox-line bitable-checkbox-cell--checked'
          : 'ri-checkbox-blank-line',
      }),
    ])
  },
})

/**
 * BitableRate - 评分单元格
 * 配置：ratingIcon / max / allowHalf
 */
VxeUI.renderer.add('BitableRate', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    let num = Number(raw)
    if (!Number.isFinite(num)) num = 0
    const max = Number(config.maxRating) || 5
    const icon = ratingIconChar(config.ratingIcon)
    const iconClass = `is-${config.ratingIcon || 'star'}`
    // 「数字」样式只展示分值，不画图标
    if (!icon) {
      return h('div', { class: 'bitable-rate-cell' }, [
        h('span', { class: 'bitable-rate-cell__text is-number' }, `${num} / ${max}`),
      ])
    }
    const full = Math.min(Math.floor(num), max)
    const half = !!config.allowHalf && num - full >= 0.5
    const stars: number[] = []
    for (let i = 1; i <= max; i++) stars.push(i)
    return h('div', { class: 'bitable-rate-cell' }, [
      ...stars.map((i) => {
        // 图标字符必须作为「文本内容」渲染。此前误写进 class 属性（class="♥ …"），
        // 结果是 i 标签里没有任何文字，评分图标永远不显示，只剩右边的数字。
        const cls =
          i <= full
            ? `bitable-rate-cell--active ${iconClass}`
            : half && i === full + 1
              ? `bitable-rate-cell--active is-half ${iconClass}`
              : `is-empty ${iconClass}`
        return h('i', { class: cls }, icon)
      }),
      h('span', { class: 'bitable-rate-cell__text' }, String(num)),
    ])
  },
})

/**
 * BitableDate - 日期单元格展示
 * 配置：dateFormat / withTime
 */
VxeUI.renderer.add('BitableDate', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) {
      return EMPTY()
    }
    const text = formatDateCell(String(raw), config)
    if (!text) {
      return EMPTY()
    }
    return h('div', { class: 'bitable-date-cell' }, [
      h('i', { class: 'ri-calendar-line bitable-date-cell__icon' }),
      h('span', { class: 'bitable-date-cell__text' }, text),
    ])
  },
})

/**
 * BitableNumber - 数字单元格（精度 / 千分位 / 前缀后缀）
 */
VxeUI.renderer.add('BitableNumber', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const text = formatNumberCell(raw, config)
    return h('span', { class: 'bitable-number-cell' }, text)
  },
})

/**
 * BitableCurrency - 货币单元格（币种符号 / 位置 / 精度 / 千分位）
 */
VxeUI.renderer.add('BitableCurrency', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const text = formatCurrencyCell(raw, config)
    return h('span', { class: 'bitable-number-cell' }, text)
  },
})

/**
 * BitableUrl - 超链接单元格（显示文案 / 新窗口打开）
 */
VxeUI.renderer.add('BitableUrl', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const url = String(raw)
    const text = config.displayText && String(config.displayText).trim()
      ? String(config.displayText)
      : url
    return h(
      'a',
      {
        class: 'bitable-link-cell',
        href: url,
        target: config.openInNewTab === false ? '_self' : '_blank',
        rel: 'noopener noreferrer',
        // 网格内点击链接不应顺带进入编辑态
        onClick: (e: MouseEvent) => e.stopPropagation(),
      },
      text,
    )
  },
})

/**
 * BitablePhone - 电话单元格（按配置脱敏）
 */
VxeUI.renderer.add('BitablePhone', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const text = config.masked ? maskPhone(String(raw)) : String(raw)
    return h('span', { class: 'bitable-phone-cell' }, text)
  },
})

/**
 * BitableAttachment - 附件单元格
 * 配置：attachmentDisplay('list' 文件名列表 | 'thumbnail' 缩略图 | 'cover' 封面图)
 * 后端以 valueJson 存储附件元信息数组。
 */
VxeUI.renderer.add('BitableAttachment', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    const files = normalizeAttachments(raw)
    if (files.length === 0) return EMPTY()
    const display = config.attachmentDisplay || 'list'

    if (display === 'thumbnail' || display === 'cover') {
      const images = files.filter((file) => isImageFile(file))
      const shown = display === 'cover' ? images.slice(0, 1) : images.slice(0, 4)
      if (shown.length === 0) {
        // 非图片附件退回文件名列表，避免出现空白单元格
        return renderFileList(files)
      }
      return h(
        'div',
        { class: 'bitable-attachment-cell is-thumbnail' },
        shown.map((file) =>
          h('img', {
            class: 'bitable-attachment-cell__thumb',
            src: file.url,
            alt: file.name,
            title: file.name,
          }),
        ),
      )
    }

    return renderFileList(files)
  },
})

function renderFileList(files: { name: string; url?: string }[]) {
  return h(
    'div',
    { class: 'bitable-attachment-cell' },
    files.map((file) =>
      h('span', { class: 'bitable-attachment-cell__item', title: file.name }, [
        h('i', { class: 'ri-attachment-2' }),
        h('span', null, file.name),
      ]),
    ),
  )
}

const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg']

function isImageFile(file: { name: string; url?: string }): boolean {
  const target = file.url || file.name
  const ext = target.split('?')[0].split('.').pop()?.toLowerCase()
  return !!ext && IMAGE_EXTENSIONS.includes(ext)
}

/** 兼容 valueJson 直接存数组 / JSON 字符串 / 逗号分隔字符串三种历史形态。 */
function normalizeAttachments(raw: unknown): { name: string; url?: string }[] {
  if (raw == null || raw === '') return []
  let parsed: unknown = raw
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed) return []
    if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
      try {
        parsed = JSON.parse(trimmed)
      } catch {
        return [{ name: trimmed }]
      }
    } else {
      return [{ name: trimmed }]
    }
  }
  const list = Array.isArray(parsed) ? parsed : [parsed]
  return list
    .map((item) => {
      if (item == null) return null
      if (typeof item === 'string') return { name: item }
      if (typeof item === 'object') {
        const obj = item as Record<string, unknown>
        const name = obj.name ?? obj.fileName ?? obj.filename ?? obj.url
        if (name == null) return null
        return { name: String(name), url: obj.url != null ? String(obj.url) : undefined }
      }
      return { name: String(item) }
    })
    .filter((item): item is { name: string; url?: string } => item !== null)
}

/**
 * 关联 / 人员 / 群组 的值 → 可读文本。
 * 历史形态有 id 数组、名称数组、`[{id,name}]` 对象数组三种，
 * 直接 `String(数组)` 会渲染出 `1,2` 或 `[object Object]`。
 */
function relationText(raw: unknown): string {
  const pickName = (item: unknown): string => {
    if (item == null) return ''
    if (typeof item === 'string' || typeof item === 'number') return String(item)
    if (typeof item === 'object') {
      const rec = item as Record<string, unknown>
      const name = rec.name ?? rec.label ?? rec.text ?? rec.title ?? rec.userName ?? rec.id
      return typeof name === 'string' || typeof name === 'number' ? String(name) : ''
    }
    return ''
  }
  if (Array.isArray(raw)) {
    return raw.map(pickName).filter(Boolean).join(', ')
  }
  return pickName(raw)
}

/**
 * BitableRelation - 关联 / 人员 / 群组单元格
 */
VxeUI.renderer.add('BitableRelation', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const text = relationText(row[column.field as string])
    if (!text) return EMPTY()
    return h('span', { class: 'bitable-relation-cell', title: text }, text)
  },
})

/**
 * BitableLocation - 地理位置单元格
 * 配置：locationDisplayMode('name' 地名 | 'address' 详细地址 | 'latlng' 经纬度)
 */
VxeUI.renderer.add('BitableLocation', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const text = formatLocationCell(raw, config)
    if (!text) return EMPTY()
    return h('span', { class: 'bitable-location-cell', title: text }, text)
  },
})

/**
 * BitableDateRange - 日期范围单元格
 * 值是 {start, end} 对象 / [start, end] 数组，直接落进文本渲染会变成 "[object Object]"
 */
VxeUI.renderer.add('BitableDateRange', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const range = parseDateRange(raw)
    if (!range) return EMPTY()
    const text = range.start === range.end ? range.start : `${range.start} ~ ${range.end}`
    return h('span', { class: 'bitable-daterange-cell', title: text }, text)
  },
})

/**
 * BitableText - 文本单元格（按 maxLength 截断展示，完整内容走 tooltip）
 */
VxeUI.renderer.add('BitableText', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const config = readConfig(renderOpts)
    const raw = row[column.field as string]
    if (isEmptyValue(raw)) return EMPTY()
    const text = String(raw)
    const maxLength = Number(config.maxLength) || 0
    if (maxLength > 0 && text.length > maxLength) {
      return h('span', { class: 'bitable-text-cell', title: text }, `${text.slice(0, maxLength)}…`)
    }
    return h('span', { class: 'bitable-text-cell' }, text)
  },
})

// ==================== 可编辑列的展示钩子 ====================

/**
 * vxe 的渲染优先级是 **editRender > cellRender**（vxe-table/es/table/src/cell.js:451）：
 * 列上一旦同时配了 editRender，展示态走的就是「编辑渲染器」的 `renderTableCell`，
 * `cellRender` 的 `renderTableDefault` 根本不会被调用。
 *
 * 这就是「字段属性配了却不生效」的根因：进度不画进度条、货币没有符号、
 * 电话不脱敏、评分画不出图标、日期不走 dateFormat…… 全被原生编辑器的纯文本展示顶掉了。
 *
 * 因此给每个「可编辑列也要按属性展示」的渲染器补两个钩子：
 *   renderTableCell  展示态（列上有 editRender 时真正生效的那条）
 *   renderTableEdit  编辑态，委托给原生编辑器（EDITOR_BINDINGS 指定）
 * 二者都只补新键 —— `renderer.add` 是合并语义，不会覆盖已有的 renderTableDefault。
 */
const EDITOR_BINDINGS: Record<string, string> = {
  BitableText: 'VxeInput',
  BitableNumber: 'VxeNumberInput',
  BitableCurrency: 'VxeNumberInput',
  BitableDate: 'VxeDatePicker',
  BitableProgress: 'VxeNumberInput',
  BitableRate: 'VxeRate',
  BitableSelectTag: 'VxeSelect',
  BitableUrl: 'VxeInput',
  BitablePhone: 'VxeInput',
}

Object.keys(EDITOR_BINDINGS).forEach((name) => {
  const conf = VxeUI.renderer.get(name) as Record<string, unknown> | undefined
  const display = conf?.renderTableDefault
  if (typeof display !== 'function') return
  // 这里是运行时动态补钩子，vxe 的 add 签名要求具体的渲染函数类型，统一放宽
  VxeUI.renderer.add(name, {
    renderTableCell: display,
    renderTableEdit(renderOpts: RenderOptions, params: RenderParams) {
      const target = EDITOR_BINDINGS[name]
      const stock = VxeUI.renderer.get(target) as Record<string, unknown> | undefined
      // VxeRate 这类组件没有 renderTableEdit，它的 renderTableDefault 本身就是可交互的编辑态 UI
      const rtEdit = stock && (stock.renderTableEdit || stock.renderEdit || stock.renderTableDefault)
      if (typeof rtEdit !== 'function') return []
      // 原生编辑器是拿 renderOpts.name 去反查组件实例的
      // （render/index.js 的 getDefaultComponent → getComponent(name)），
      // 沿用 BitableXxx 这个名字会取到 null，Vue 直接抛 "Invalid vnode type when creating vnode: null"，
      // 表现为格子进了编辑态却挂不出任何输入框。所以这里必须换回原生渲染器名。
      return (rtEdit as (o: unknown, p: unknown) => unknown)({ ...renderOpts, name: target }, params)
    },
  } as any)
})

// 显式导出以便类型推导
export const BitableProgressRenderer = 'BitableProgress'
export const BitableSelectTagRenderer = 'BitableSelectTag'
export const BitableCheckboxRenderer = 'BitableCheckbox'
export const BitableRateRenderer = 'BitableRate'
export const BitableDateRenderer = 'BitableDate'
export const BitableNumberRenderer = 'BitableNumber'
export const BitableCurrencyRenderer = 'BitableCurrency'
export const BitableUrlRenderer = 'BitableUrl'
export const BitablePhoneRenderer = 'BitablePhone'
export const BitableAttachmentRenderer = 'BitableAttachment'
export const BitableRelationRenderer = 'BitableRelation'
export const BitableLocationRenderer = 'BitableLocation'
export const BitableDateRangeRenderer = 'BitableDateRange'
export const BitableTextRenderer = 'BitableText'
