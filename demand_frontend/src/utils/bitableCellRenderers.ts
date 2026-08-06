/**
 * Bitable 自定义单元格渲染器
 *
 * 对齐飞书多维表格字段交互：
 * - BitableProgress: 进度条展示 + 数字编辑（0-100）
 * - BitableSelectTag: 彩色标签展示（单选/多选/流程） + VxeSelect 编辑
 * - BitableCheckbox: 复选框展示 + 点击切换
 * - BitableRate: 星级展示 + 点击编辑
 *
 * 注册后可在 vxe-table 的 cellRender / editRender 中通过 name 引用：
 *   { cellRender: { name: 'BitableProgress' } }
 *   { editRender: { name: 'BitableSelectTag', options, optionProps, props } }
 */
import { h } from 'vue'
import { VxeUI } from 'vxe-table'

// 本地定义渲染器参数类型（vxe-pc-ui 4.13 中 VxeColumnPropTypes.RenderCellParams 未直接导出）
// 使用 any 避免 vxe-table 内部 ColumnInfo 索引签名不兼容问题
type RenderOptions = any
type RenderParams = any

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

/**
 * BitableProgress - 进度条单元格
 * cellRender: 显示进度条 + 百分比文本
 * editRender: 由调用方自行指定（推荐 VxeInput type=number）
 */
VxeUI.renderer.add('BitableProgress', {
  renderTableDefault(_renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const raw = row[column.field as string]
    let num = Number(raw)
    if (!Number.isFinite(num)) num = 0
    if (num < 0) num = 0
    if (num > 100) num = 100
    // 进度条填充：使用品牌渐变（>30% 走主色→强调渐变，更激进；<30% 走警示色提醒）
    const fillStyle =
      num < 30
        ? { background: 'var(--color-progress-low, #F59E0B)' }
        : { background: 'var(--gradient-progress-fill, linear-gradient(90deg, #3B82F6 0%, #6366F1 100%))' }
    return h('div', { class: 'bitable-progress-cell' }, [
      h('div', { class: 'bitable-progress-cell__bar' }, [
        h('div', {
          class: 'bitable-progress-cell__fill',
          style: { width: `${num}%`, ...fillStyle },
        }),
      ]),
      h('span', { class: 'bitable-progress-cell__text' }, `${num}%`),
    ])
  },
})

/**
 * BitableSelectTag - 彩色标签展示
 * cellRender: 显示彩色标签（单选显示1个，多选显示多个，流程显示状态色块）
 * 编辑由 VxeSelect 负责，此处只负责展示
 *
 * 配置：
 *   cellRender: {
 *     name: 'BitableSelectTag',
 *     options: [{ label, color }],
 *     optionProps: { label: 'label', value: 'label' },
 *     props: { multiple: false }
 *   }
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
      return h('span', { class: 'bitable-cell-empty' }, '')
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
 * BitableCheckbox - 复选框单元格
 * cellRender: 显示复选框（已勾选/未勾选）
 * 编辑：不使用 vxe-table 内置编辑态，由 GridView 的 cell-click 直接 toggle 并 emit
 * （这样避免 mode='cell' + trigger='click' 下每次进入编辑态翻转一次值的歧义）
 */
VxeUI.renderer.add('BitableCheckbox', {
  renderTableDefault(_renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const raw = row[column.field as string]
    const checked = raw === true || raw === 'true' || raw === 'True' || raw === 1 || raw === '1'
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
 * BitableRate - 评分星级单元格
 * cellRender: 显示 N 颗星（基于值与 max）
 * editRender: 由 VxeRate 接管（vxe-table 内置支持），调用方使用 editRender: { name: 'VxeRate', props: { max } } 即可
 * 此处仅提供展示渲染
 */
VxeUI.renderer.add('BitableRate', {
  renderTableDefault(renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const raw = row[column.field as string]
    let num = Number(raw)
    if (!Number.isFinite(num)) num = 0
    const max = Number(renderOpts.props?.max) || 5
    const stars: number[] = []
    for (let i = 1; i <= max; i++) stars.push(i)
    return h('div', { class: 'bitable-rate-cell' }, [
      ...stars.map((i) =>
        h('i', {
          class: i <= num ? 'ri-star-fill bitable-rate-cell--active' : 'ri-star-line',
        }),
      ),
      h('span', { class: 'bitable-rate-cell__text' }, String(num)),
    ])
  },
})

/**
 * BitableDate - 日期单元格展示
 * cellRender: 显示日历图标 + 日期文本（只读展示，编辑交给 VxeDatePicker）
 * 仅做展示，避免把 VxeDatePicker 当作 display 渲染器带来的渲染异常/闪退
 */
VxeUI.renderer.add('BitableDate', {
  renderTableDefault(_renderOpts: RenderOptions, params: RenderParams) {
    const { row, column } = params
    const raw = row[column.field as string]
    const str = raw == null || raw === '' ? '' : String(raw)
    if (!str) {
      return h('span', { class: 'bitable-cell-empty' }, '')
    }
    return h('div', { class: 'bitable-date-cell' }, [
      h('i', { class: 'ri-calendar-line bitable-date-cell__icon' }),
      h('span', { class: 'bitable-date-cell__text' }, str),
    ])
  },
})

// 显式导出以便类型推导
export const BitableProgressRenderer = 'BitableProgress'
export const BitableSelectTagRenderer = 'BitableSelectTag'
export const BitableCheckboxRenderer = 'BitableCheckbox'
export const BitableRateRenderer = 'BitableRate'
export const BitableDateRenderer = 'BitableDate'
