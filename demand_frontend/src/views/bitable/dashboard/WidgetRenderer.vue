<template>
  <div class="widget-renderer">
    <!-- 错误 / 加载 / 空态 -->
    <div v-if="data?.error" class="widget-renderer__state widget-renderer__state--error">
      <i class="ri-error-warning-line" />{{ data.error }}
    </div>
    <!-- 仪表盘数据权限=「有不可查看数据时图表不可见」：后端标记 hidden，渲染无权限占位 -->
    <div v-else-if="data?.hidden" class="widget-renderer__state widget-renderer__state--denied">
      <i class="ri-lock-2-line" />当前角色无权查看该图表的数据
    </div>
    <div v-else-if="needsData && !data" class="widget-renderer__state">
      <i class="ri-loader-4-line widget-renderer__spin" />数据加载中
    </div>
    <div v-else-if="needsData && !configured" class="widget-renderer__state">
      <i class="ri-settings-3-line" />请在属性面板中配置数据源
    </div>

    <!-- 进度图（须先于单值分支判断） -->
    <div v-else-if="widget.type === 'progress'" class="progress-wrap">
      <div class="progress-wrap__percent" :style="{ color: display.valueColor || 'var(--color-primary)' }">{{ percentText }}</div>
      <div class="progress-wrap__track">
        <div class="progress-wrap__fill" :style="{ width: percent + '%', background: display.valueColor || 'var(--color-primary)' }" />
      </div>
      <div class="progress-wrap__sub">{{ formatNumber(data?.value, decimals, unit) }} / 目标 {{ formatNumber(display.targetValue ?? 100, decimals, unit) }}</div>
    </div>

    <!-- 统计数字 / 指标卡 / 旧版 kpi -->
    <div v-else-if="isSingleValue" class="stat-value" :class="{ 'stat-value--card': widget.type === 'metric_card' || widget.type === 'kpi' }">
      <div class="stat-value__number" :style="{ color: display.valueColor || 'var(--color-text-primary)' }">
        {{ formatNumber(data?.value, decimals, unit) }}
      </div>
      <div class="stat-value__sub">共 {{ data?.recordCount ?? 0 }} 条记录参与计算</div>
    </div>

    <!-- 排行榜 -->
    <div v-else-if="widget.type === 'rank'" class="rank-list">
      <div v-for="(row, i) in rankRows" :key="row.name" class="rank-list__row">
        <span class="rank-list__badge" :class="`rank-list__badge--${i + 1}`">{{ i + 1 }}</span>
        <span class="rank-list__name" :title="row.name">{{ row.name }}</span>
        <span v-if="display.showBar !== false" class="rank-list__bar">
          <span class="rank-list__bar-fill" :style="{ width: row.percent + '%', background: colors[i % colors.length] }" />
        </span>
        <span class="rank-list__value">{{ formatNumber(row.value, decimals, unit) }}</span>
      </div>
      <div v-if="!rankRows.length" class="widget-renderer__state"><i class="ri-inbox-line" />暂无数据</div>
    </div>

    <!-- 文本 -->
    <div v-else-if="widget.type === 'text'" class="text-widget">{{ display.content || '双击编辑文本' }}</div>

    <!-- 图片 -->
    <div v-else-if="widget.type === 'image'" class="image-widget">
      <img v-if="display.url" :src="display.url" :style="{ objectFit: display.fit || 'contain' }" alt="" />
      <div v-else class="widget-renderer__state"><i class="ri-image-line" />请在属性面板中设置图片地址</div>
    </div>

    <!-- 时钟 -->
    <div v-else-if="widget.type === 'clock'" class="clock-widget">
      <div class="clock-widget__time">{{ clockText }}</div>
      <div v-if="display.showDate !== false" class="clock-widget__date">{{ clockDate }}</div>
    </div>

    <!-- ECharts 图表 -->
    <template v-else>
      <v-chart
        v-if="chartOption"
        :key="themeTick"
        :ref="setChartRef"
        class="widget-renderer__chart"
        :option="chartOption"
        autoresize
      />
      <div v-else class="widget-renderer__state"><i class="ri-inbox-line" />暂无数据</div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import type { WidgetInstance } from './widgetDefs'
import { getWidgetDef } from './widgetDefs'
import { buildChartOption, formatNumber, schemeColors } from './chartOptions'
import { chartRegistry } from './chartRegistry'
import { useAppearance } from '@/composables/useAppearance'

const props = defineProps<{
  widget: WidgetInstance
  /** 后端 getDashboardData 返回该组件的数据 */
  data?: any
}>()

// 外观模式切换后重建图表，使坐标轴/文字颜色重新按 CSS 变量解析
const { mode: appearanceMode } = useAppearance()
const themeTick = ref(0)
watch(appearanceMode, () => { themeTick.value += 1 })

const def = computed(() => getWidgetDef(props.widget.type))
const display = computed(() => props.widget.displayConfig || {})
const needsData = computed(() => def.value.dataMode !== 'none')
const isSingleValue = computed(() => def.value.dataMode === 'single')
const colors = computed(() => schemeColors(display.value.colorScheme))
const decimals = computed(() => Number(display.value.decimals ?? 0))
const unit = computed(() => display.value.unit || '')

/** 数据源是否已配置完整（有数据表且指标可用） */
const configured = computed(() => {
  const ds = props.widget.dataSourceConfig || {}
  if (!ds.tableId) return false
  const metrics = ds.metrics || []
  return metrics.length > 0
})

const chartOption = computed(() => buildChartOption(props.widget, props.data))

// ---- 排行榜行数据 ----
const rankRows = computed(() => {
  const data = props.data
  const labels: string[] = data?.labels || []
  const values: unknown[] = data?.series?.[0]?.data || data?.values || []
  const nums = values.map((v) => Number(v) || 0)
  const max = Math.max(...nums, 0)
  return labels.map((name, i) => ({
    name,
    value: values[i],
    percent: max > 0 ? Math.max(2, Math.round(((nums[i] || 0) / max) * 100)) : 0,
  }))
})

// ---- 进度 ----
const percent = computed(() => {
  const target = Number(display.value.targetValue ?? 100)
  const value = Number(props.data?.value ?? 0)
  if (!target || Number.isNaN(target)) return 0
  return Math.max(0, Math.min(100, Math.round((value / target) * 100)))
})
const percentText = computed(() => `${percent.value}%`)

// ---- 时钟 ----
const now = ref(new Date())
let clockTimer: number | undefined
onMounted(() => {
  clockTimer = window.setInterval(() => { now.value = new Date() }, 1000)
})
onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
})
const clockText = computed(() => {
  const d = now.value
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
})
const clockDate = computed(() => {
  const d = now.value
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
})

// ---- 图表实例注册（导出图片用） ----
const chartRef = ref<any>()
function setChartRef(inst: any) {
  chartRef.value = inst
}
watch(chartRef, (inst) => {
  chartRegistry.set(props.widget.id, inst || null)
})
watch(() => props.widget.id, () => {
  chartRegistry.set(props.widget.id, chartRef.value || null)
})
</script>

<style scoped lang="scss">
.widget-renderer {
  height: 100%;
  display: flex;
  flex-direction: column;

  &__state {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    font-size: 12px;
    color: var(--color-text-tertiary);
    text-align: center;
    padding: 8px;

    i { font-size: 22px; }
  }

  &--error { color: var(--el-color-danger, #ef4444); }
  &--denied { color: var(--color-warning-text, #92400e); }

  &__spin { animation: widget-spin 1s linear infinite; }

  &__chart {
    flex: 1;
    width: 100%;
    min-height: 0;
  }
}

@keyframes widget-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

// ---- 统计数字 / 指标卡 ----
.stat-value {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;

  &--card {
    border-radius: var(--radius-lg, 12px);
    background: var(--color-fill-secondary, rgba(148, 163, 184, 0.08));
    border: 1px solid var(--color-border);
  }

  &__number {
    font-size: 40px;
    line-height: 1.1;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    letter-spacing: -1px;
  }

  &__sub {
    font-size: 12px;
    color: var(--color-text-tertiary);
  }
}

// ---- 进度图 ----
.progress-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;

  &__percent {
    font-size: 30px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  &__track {
    height: 8px;
    border-radius: 4px;
    background: var(--color-fill-secondary);
    overflow: hidden;
  }

  &__fill {
    height: 100%;
    border-radius: 4px;
    transition: width 0.4s ease;
  }

  &__sub {
    font-size: 12px;
    color: var(--color-text-tertiary);
  }
}

// ---- 排行榜 ----
.rank-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-evenly;
  gap: 4px;
  padding: 8px 12px;
  overflow: auto;
  min-height: 0;

  &__row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 26px;
  }

  &__badge {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 600;
    color: var(--color-text-secondary);
    background: var(--color-fill-secondary);

    &--1 { background: var(--color-tag-amber-bg, #fef3c7); color: var(--color-tag-amber-fg, #b45309); }
    &--2 { background: var(--color-fill-tertiary); color: var(--color-text-secondary); }
    &--3 { background: var(--color-tag-orange-bg, #ffedd5); color: var(--color-tag-orange-fg, #c2410c); }
  }

  &__name {
    flex-shrink: 0;
    max-width: 40%;
    font-size: 12px;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__bar {
    flex: 1;
    height: 6px;
    border-radius: 3px;
    background: var(--color-fill-secondary);
    overflow: hidden;
  }

  &__bar-fill {
    display: block;
    height: 100%;
    border-radius: 3px;
    opacity: 0.85;
  }

  &__value {
    flex-shrink: 0;
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-primary);
    font-variant-numeric: tabular-nums;
  }
}

// ---- 文本 ----
.text-widget {
  flex: 1;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

// ---- 图片 ----
.image-widget {
  flex: 1;
  min-height: 0;
  display: flex;

  img {
    width: 100%;
    height: 100%;
    border-radius: var(--radius-md, 8px);
  }
}

// ---- 时钟 ----
.clock-widget {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;

  &__time {
    font-size: 34px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: var(--color-text-primary);
    letter-spacing: 1px;
  }

  &__date {
    font-size: 12px;
    color: var(--color-text-tertiary);
  }
}
</style>
