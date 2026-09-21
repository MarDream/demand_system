/**
 * 大屏图表 ECharts option 构建器。
 * ECharts 5 不解析 CSS 变量，坐标轴/文本/提示框颜色一律通过 cssVar()
 * 在构建时解析（跟随应用明暗模式），数据系列使用固定图表色板。
 */
import type { WidgetInstance } from './widgetDefs'

export function cssVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

/** 数据系列色板（图表专用色，明暗两种模式下均保持可读） */
export const COLOR_SCHEMES: Record<string, string[]> = {
  default: ['#3b82f6', '#22d3ee', '#a78bfa', '#34d399', '#fbbf24', '#fb7185', '#60a5fa', '#2dd4bf'],
  ocean: ['#38bdf8', '#2563eb', '#67e8f9', '#1d4ed8', '#7dd3fc', '#0ea5e9', '#93c5fd', '#3b82f6'],
  sunset: ['#fb923c', '#f87171', '#fbbf24', '#f472b6', '#fb7185', '#fdba74', '#e879f9', '#fca5a5'],
  forest: ['#34d399', '#a3e635', '#2dd4bf', '#4ade80', '#10b981', '#bef264', '#5eead4', '#22c55e'],
  berry: ['#f472b6', '#c084fc', '#fb7185', '#e879f9', '#a855f7', '#f0abfc', '#d8b4fe', '#fda4af'],
}

export const COLOR_SCHEME_OPTIONS = [
  { value: 'default', label: '经典蓝' },
  { value: 'ocean', label: '海洋' },
  { value: 'sunset', label: '暖阳' },
  { value: 'forest', label: '森林' },
  { value: 'berry', label: '浆果' },
]

export function schemeColors(scheme?: string): string[] {
  return COLOR_SCHEMES[scheme || 'default'] || COLOR_SCHEMES.default
}

export function formatNumber(v: unknown, decimals = 0, unit = ''): string {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const s = n.toLocaleString('zh-CN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
  return unit ? `${s} ${unit}` : s
}

interface ChartContext {
  textColor: string
  textSecondary: string
  axisLine: string
  splitLine: string
  tooltipBg: string
  tooltipBorder: string
}

function chartContext(): ChartContext {
  return {
    textColor: cssVar('--color-text-primary', '#1f2937'),
    textSecondary: cssVar('--color-text-secondary', '#6b7280'),
    axisLine: cssVar('--color-border', '#e5e7eb'),
    splitLine: cssVar('--color-border', '#e5e7eb'),
    tooltipBg: cssVar('--color-surface', '#ffffff'),
    tooltipBorder: cssVar('--color-border', '#e5e7eb'),
  }
}

interface WidgetData {
  labels?: string[]
  series?: Array<{ name: string; data: unknown[] }>
  values?: unknown[]
  recordCount?: number
  error?: string
}

function widgetSeries(widget: WidgetInstance, data: WidgetData) {
  if (Array.isArray(data?.series) && data.series.length) return data.series
  const name = widget.dataSourceConfig.metrics?.[0]?.aggregation === 'count' ? '记录数' : '数值'
  return [{ name, data: (data?.values || []) as unknown[] }]
}

function valueAxisName(unit?: string): string {
  return unit ? unit : ''
}

/**
 * 组件类型 → ECharts option。
 * 返回 null 表示该组件不是 ECharts 图表（单值/静态组件由 DOM 渲染）。
 */
export function buildChartOption(widget: WidgetInstance, data: WidgetData | undefined): any | null {
  if (!data || data.error) return null
  const type = widget.type
  const charted = ['bar', 'line', 'hbar', 'area', 'pie', 'donut', 'combo', 'radar', 'funnel', 'treemap', 'kpi']
  if (!charted.includes(type)) return null

  const labels = (data.labels || []).map(String)
  const series = widgetSeries(widget, data)
  const display = widget.displayConfig || {}
  const colors = schemeColors(display.colorScheme)
  const ctx = chartContext()
  const unit: string = display.unit || ''
  const decimals: number = Number(display.decimals ?? 0)
  const showLegend = display.showLegend !== false && (series.length > 1 || type === 'pie' || type === 'donut' || type === 'funnel')
  const showValue = display.showValue === true
  const smooth = display.smooth !== false

  const tooltipStyle = {
    backgroundColor: ctx.tooltipBg,
    borderColor: ctx.tooltipBorder,
    textStyle: { color: ctx.textColor, fontSize: 12 },
    valueFormatter: (v: unknown) => formatNumber(v, decimals, unit),
  }

  const legend = {
    show: showLegend,
    bottom: 0,
    left: 'center',
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
    textStyle: { color: ctx.textSecondary, fontSize: 11 },
    pageIconColor: ctx.textSecondary,
    pageIconInactiveColor: ctx.axisLine,
    pageTextStyle: { color: ctx.textSecondary },
  }

  // ---- 饼/环 ----
  if (type === 'pie' || type === 'donut') {
    return {
      color: colors,
      tooltip: { trigger: 'item', ...tooltipStyle },
      legend,
      series: [{
        type: 'pie',
        radius: type === 'donut' ? ['42%', '70%'] : ['0%', '70%'],
        center: ['50%', '46%'],
        data: labels.map((label, i) => ({ name: label, value: series[0]?.data?.[i] ?? 0 })),
        label: showValue
          ? { color: ctx.textSecondary, fontSize: 11, formatter: (p: any) => `${p.name} ${formatNumber(p.value, decimals, unit)}` }
          : { color: ctx.textSecondary, fontSize: 11, formatter: '{b}' },
        labelLine: { lineStyle: { color: ctx.axisLine } },
        itemStyle: { borderColor: ctx.tooltipBg, borderWidth: 2 },
      }],
    }
  }

  // ---- 雷达 ----
  if (type === 'radar') {
    const maxVals = series[0]?.data?.map((v) => Number(v) || 0) || []
    return {
      color: colors,
      tooltip: { trigger: 'item', ...tooltipStyle },
      legend,
      radar: {
        indicator: labels.map((label, i) => ({ name: label, max: niceMax(maxVals[i]) })),
        radius: '62%',
        center: ['50%', '48%'],
        axisName: { color: ctx.textSecondary, fontSize: 11 },
        splitLine: { lineStyle: { color: ctx.splitLine } },
        splitArea: { show: false },
        axisLine: { lineStyle: { color: ctx.splitLine } },
      },
      series: [{
        type: 'radar',
        symbolSize: 4,
        data: series.map((s, i) => ({
          name: s.name,
          value: s.data,
          areaStyle: { opacity: series.length > 1 ? 0.12 : 0.2 },
          lineStyle: { width: 2, color: colors[i % colors.length] },
          itemStyle: { color: colors[i % colors.length] },
        })),
      }],
    }
  }

  // ---- 漏斗 ----
  if (type === 'funnel') {
    return {
      color: colors,
      tooltip: { trigger: 'item', ...tooltipStyle },
      legend,
      series: [{
        type: 'funnel',
        left: '12%',
        right: '12%',
        top: 12,
        bottom: showLegend ? 36 : 12,
        sort: 'desc',
        gap: 2,
        data: labels.map((label, i) => ({ name: label, value: series[0]?.data?.[i] ?? 0 })),
        label: { color: '#fff', fontSize: 11, formatter: (p: any) => `${p.name} ${formatNumber(p.value, decimals, unit)}` },
        itemStyle: { borderColor: ctx.tooltipBg, borderWidth: 1 },
      }],
    }
  }

  // ---- 矩形树图 ----
  if (type === 'treemap') {
    return {
      color: colors,
      tooltip: { trigger: 'item', ...tooltipStyle },
      series: [{
        type: 'treemap',
        top: 8,
        bottom: 8,
        left: 8,
        right: 8,
        roam: false,
        nodeClick: false,
        breadcrumb: { show: false },
        data: labels.map((label, i) => ({ name: label, value: series[0]?.data?.[i] ?? 0 })),
        label: { color: '#fff', fontSize: 11, formatter: (p: any) => `${p.name}\n${formatNumber(p.value, decimals, unit)}` },
        upperLabel: { show: false },
        itemStyle: { borderColor: ctx.tooltipBg, borderWidth: 2, gapWidth: 2 },
      }],
    }
  }

  // ---- 直角坐标图：bar/line/area/hbar/combo ----
  const horizontal = type === 'hbar'
  const valueFormatter = (v: unknown) => formatNumber(v, decimals, unit)
  const categoryAxis = {
    type: 'category' as const,
    data: labels,
    axisLine: { lineStyle: { color: ctx.axisLine } },
    axisTick: { show: false },
    axisLabel: {
      color: ctx.textSecondary,
      fontSize: 11,
      interval: 'auto',
      rotate: !horizontal && labels.length > 6 ? 30 : 0,
      hideOverlap: true,
    },
  }
  const valueAxis = {
    type: 'value' as const,
    name: valueAxisName(unit),
    nameTextStyle: { color: ctx.textSecondary, fontSize: 10 },
    axisLabel: { color: ctx.textSecondary, fontSize: 11, formatter: (v: number) => compactNumber(v) },
    splitLine: { lineStyle: { color: ctx.splitLine, type: 'dashed' as const } },
  }

  const echartsSeries = series.map((s, i) => {
    const color = colors[i % colors.length]
    // 组合图：首指标柱状、后续指标折线；其余类型按自身类型渲染
    const seriesType = type === 'combo' ? (i === 0 ? 'bar' : 'line') : type === 'bar' || type === 'hbar' ? 'bar' : 'line'
    const base: any = {
      name: s.name,
      type: seriesType,
      data: s.data,
      barMaxWidth: 28,
      itemStyle: { color, borderRadius: seriesType === 'bar' ? (horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]) : 0 },
      emphasis: { focus: 'series' },
    }
    if (seriesType === 'line') {
      base.smooth = smooth
      base.symbolSize = 5
      base.lineStyle = { width: 2.5, color }
      if (type === 'area') base.areaStyle = { opacity: 0.18 }
    }
    if (showValue) {
      base.label = {
        show: true,
        position: seriesType === 'bar' ? (horizontal ? 'right' : 'top') : 'top',
        color: ctx.textSecondary,
        fontSize: 10,
        formatter: (p: any) => formatNumber(p.value, decimals, ''),
      }
    }
    return base
  })

  return {
    color: colors,
    tooltip: { trigger: 'axis', ...tooltipStyle, axisPointer: { type: 'shadow', shadowStyle: { color: cssVar('--color-fill-secondary', 'rgba(148,163,184,0.12)') } } },
    legend,
    grid: { left: 8, right: 16, top: 20, bottom: showLegend ? 40 : 12, containLabel: true },
    [horizontal ? 'yAxis' : 'xAxis']: categoryAxis,
    [horizontal ? 'xAxis' : 'yAxis']: valueAxis,
    series: echartsSeries,
  }
}

function niceMax(v: number): number {
  if (!v || v <= 0) return 10
  const magnitude = Math.pow(10, Math.floor(Math.log10(v)))
  return Math.ceil(v / magnitude) * magnitude
}

/** 坐标轴刻度紧凑显示：12000 → 1.2万 */
function compactNumber(v: number): string {
  if (Math.abs(v) >= 100000000) return `${(v / 100000000).toFixed(1)}亿`
  if (Math.abs(v) >= 10000) return `${(v / 10000).toFixed(1).replace(/\.0$/, '')}万`
  return String(v)
}
