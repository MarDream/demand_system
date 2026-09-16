<template>
  <div class="gantt-view">
    <div class="gantt-header">
      <span class="gantt-header__label">日期字段：</span>
      <el-select
        v-model="dateFieldId"
        placeholder="选择日期字段"
        style="width: 200px;"
        size="small"
        @change="handleFieldChange"
      >
        <el-option
          v-for="f in dateFields"
          :key="f.id"
          :label="f.name"
          :value="f.id"
        />
      </el-select>
      <span class="gantt-header__label ml-16">范围字段：</span>
      <el-select
        v-model="rangeFieldId"
        placeholder="选择日期范围字段"
        style="width: 200px;"
        size="small"
        clearable
        @change="handleFieldChange"
      >
        <el-option
          v-for="f in rangeFields"
          :key="f.id"
          :label="f.name"
          :value="f.id"
        />
      </el-select>
    </div>

    <div v-if="!dateFieldId && !rangeFieldId" class="gantt-empty">
      <el-empty description="请选择日期字段或日期范围字段以展示甘特图" />
    </div>

    <div v-else-if="loading" class="gantt-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>

    <div v-else class="gantt-body">
      <div class="gantt-sidebar">
        <div class="gantt-sidebar-header">任务名称</div>
        <div
          v-for="record in records"
          :key="record.id"
          class="gantt-sidebar-row"
        >
          {{ getRecordTitle(record) }}
        </div>
      </div>
      <div class="gantt-chart">
        <div class="gantt-chart-header">
          <div
            v-for="day in chartDays"
            :key="day.date"
            class="gantt-day-header"
          >
            {{ day.label }}
          </div>
        </div>
        <div class="gantt-chart-body">
          <div
            v-for="record in records"
            :key="record.id"
            class="gantt-row"
          >
            <div
              v-if="getRecordDateRange(record)"
              class="gantt-bar"
              :style="getBarStyle(record)"
            >
              <span class="gantt-bar-text">{{ getRecordTitle(record) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { resolveRecordTitle } from '@/utils/bitableFieldConfig'
import type { BitableField, BitableRecord, BitableTable, CellValue, ViewConfig } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
  viewConfig?: ViewConfig | null
}>()

const emit = defineEmits<{
  fieldChange: [patch: { startFieldId: number | null; endFieldId: number | null }]
}>()

const dateFieldId = ref<number | null>(null)
const rangeFieldId = ref<number | null>(null)

// 日期字段列表
const dateFields = computed(() =>
  props.fields.filter((f) =>
    ['date', 'created_time', 'modified_time', 'last_modified_time'].includes(f.fieldType)
  )
)

// 日期范围字段列表
const rangeFields = computed(() =>
  props.fields.filter((f) => f.fieldType === 'date_range')
)

/** 本地日期格式化为 yyyy-MM-dd。不能用 toISOString()：东半球时区会把本地零点转成前一天 */
function toLocalDateStr(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 甘特图时间轴：随数据范围伸缩，最少 30 天，让历史与远期任务都可见 */
const chartDays = computed(() => {
  const days: { date: string; label: string }[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  // 取所有记录的最早/最晚日期，确定时间轴范围
  let min: Date | null = null
  let max: Date | null = null
  for (const record of props.records) {
    const range = getRecordDateRange(record)
    if (!range) continue
    const s = new Date(range.start)
    const e = new Date(range.end)
    if (isNaN(s.getTime()) || isNaN(e.getTime())) continue
    s.setHours(0, 0, 0, 0)
    e.setHours(0, 0, 0, 0)
    if (!min || s < min) min = s
    if (!max || e > max) max = e
  }

  // 时间轴：数据范围与"今天起 30 天"的并集，并前后各留 1 天余量
  const first = min ? new Date(Math.min(min.getTime(), today.getTime())) : new Date(today)
  const last = max ? new Date(Math.max(max.getTime(), today.getTime() + 29 * 86400000)) : new Date(today.getTime() + 29 * 86400000)
  first.setDate(first.getDate() - 1)
  last.setDate(last.getDate() + 1)

  const totalDays = Math.max(30, Math.ceil((last.getTime() - first.getTime()) / 86400000))
  for (let i = 0; i < totalDays; i++) {
    const d = new Date(first)
    d.setDate(first.getDate() + i)
    days.push({ date: toLocalDateStr(d), label: `${d.getMonth() + 1}/${d.getDate()}` })
  }
  return days
})

function handleFieldChange() {
  emit('fieldChange', { startFieldId: dateFieldId.value, endFieldId: rangeFieldId.value })
}

/** 起始字段：优先取视图配置里存过的值，失效或未配置时落到第一个可用日期字段 */
function resolveDateFieldId(): number | null {
  const configured = props.viewConfig?.gantt?.startFieldId
  if (configured && props.fields.some((f) => f.id === configured)) return configured
  return dateFields.value[0]?.id ?? null
}

/** 范围字段：只接受仍然存在的 date_range 字段 */
function resolveRangeFieldId(): number | null {
  const configured = props.viewConfig?.gantt?.endFieldId
  if (configured && props.fields.some((f) => f.id === configured && f.fieldType === 'date_range')) {
    return configured
  }
  return null
}

watch(
  () => [props.fields, props.viewConfig?.gantt?.startFieldId, props.viewConfig?.gantt?.endFieldId] as const,
  () => {
    const nextDate = resolveDateFieldId()
    if (nextDate !== dateFieldId.value) dateFieldId.value = nextDate
    const nextRange = resolveRangeFieldId()
    if (nextRange !== rangeFieldId.value) rangeFieldId.value = nextRange
  },
  { immediate: true },
)

// 获取记录标题（视图指定 → 文本字段 → 第一个有值字段）
function getRecordTitle(record: BitableRecord): string {
  return resolveRecordTitle(props.fields, record)
}

// 获取记录的日期范围
function getRecordDateRange(record: BitableRecord): { start: string; end: string } | null {
  if (rangeFieldId.value && record.cells?.[rangeFieldId.value]) {
    const cell = record.cells[rangeFieldId.value]
    const json = cell.valueJson as unknown
    // 兼容两种存储格式：{start, end} 对象 与 [start, end] 数组（FormView 提交格式）
    if (Array.isArray(json) && json.length === 2 && json[0] && json[1]) {
      return { start: String(json[0]), end: String(json[1]) }
    }
    if (json && typeof json === 'object' && 'start' in json && 'end' in json) {
      return { start: String((json as any).start), end: String((json as any).end) }
    }
  }
  if (dateFieldId.value && record.cells?.[dateFieldId.value]) {
    const cell = record.cells[dateFieldId.value]
    const date = cell.valueDate || cell.valueText
    if (date) {
      return { start: date, end: date }
    }
  }
  return null
}

// 计算甘特条样式
function getBarStyle(record: BitableRecord): Record<string, string> {
  const range = getRecordDateRange(record)
  if (!range) return {}

  const days = chartDays.value
  if (!days.length) return {}

  const timelineStart = new Date(days[0].date)
  timelineStart.setHours(0, 0, 0, 0)
  const startDate = new Date(range.start)
  const endDate = new Date(range.end)
  startDate.setHours(0, 0, 0, 0)
  endDate.setHours(0, 0, 0, 0)

  // 按天偏移计算位置（相对时间轴起点，而非固定"今天"）
  const dayMs = 1000 * 60 * 60 * 24
  const startOffset = Math.floor((startDate.getTime() - timelineStart.getTime()) / dayMs)
  const endOffset = Math.floor((endDate.getTime() - timelineStart.getTime()) / dayMs)

  const totalDays = days.length
  // 记录超出时间轴时裁剪到可见范围内，并保证条宽至少 1 天、不为负
  const clampedStart = Math.min(Math.max(startOffset, 0), totalDays - 1)
  const clampedEnd = Math.min(Math.max(endOffset, 0), totalDays - 1)
  const span = Math.max(clampedEnd - clampedStart + 1, 1)

  const widthPercent = (span / totalDays) * 100
  const leftPercent = (clampedStart / totalDays) * 100

  return {
    left: `${leftPercent}%`,
    width: `${widthPercent}%`,
    background: 'var(--gradient-gantt-bar, linear-gradient(180deg, #3B82F6 0%, #2563EB 100%))',
  }
}
</script>

<style scoped lang="scss">
// ===== 多维表格 GanttView 激进风格精修（2026-08-03）=====
// 设计目标：
// 1. 甘特条：品牌渐变 + 8px 圆角 + 强化阴影
// 2. 今日竖线：danger 色 0.5px 虚线
// 3. 任务条 hover：放大 + 加深阴影
// 4. 整体间距加大

.gantt-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.gantt-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border, #e2e8f0);
  background: var(--color-surface, #fff);
  flex-shrink: 0;

  .gantt-header__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-secondary, #475569);
    white-space: nowrap;
  }

  .ml-16 {
    margin-left: 16px;
  }
}

.gantt-empty,
.gantt-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.gantt-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

// 任务侧栏
.gantt-sidebar {
  width: 220px;
  border-right: 1px solid var(--color-border, #e2e8f0);
  flex-shrink: 0;
  overflow-y: auto;
  background: var(--color-surface, #fff);

  .gantt-sidebar-header {
    height: 44px;
    padding: 12px 16px;
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-secondary, #475569);
    background: var(--color-background, #f8fafc);
    border-bottom: 1px solid var(--color-border, #e2e8f0);
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }

  .gantt-sidebar-row {
    height: 38px;
    padding: 10px 16px;
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-primary, #0f172a);
    border-bottom: 0.5px solid var(--color-border, #e2e8f0);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: background-color 150ms;
  }

  .gantt-sidebar-row:hover {
    background: var(--color-row-hover-bg, rgba(59, 130, 246, 0.04));
  }
}

// 时间轴主区
.gantt-chart {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  position: relative;

  .gantt-chart-header {
    display: flex;
    height: 44px;
    background: var(--color-background, #f8fafc);
    border-bottom: 1px solid var(--color-border, #e2e8f0);
  }

  .gantt-day-header {
    width: 44px;
    height: 44px;
    padding: 10px 4px;
    font-size: 11px;
    font-weight: 500;
    color: var(--color-text-secondary, #475569);
    text-align: center;
    border-right: 0.5px solid var(--color-border, #e2e8f0);
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }

  .gantt-chart-body {
    overflow-y: auto;
  }

  .gantt-row {
    height: 38px;
    border-bottom: 0.5px solid var(--color-border, #e2e8f0);
    position: relative;
    transition: background-color 150ms;

    &:hover {
      background: var(--color-row-hover-bg, rgba(59, 130, 246, 0.04));
    }
  }

  // 甘特条：品牌渐变 + 圆角 + 阴影
  .gantt-bar {
    position: absolute;
    top: 6px;
    height: 26px;
    border-radius: var(--radius-md, 8px);
    padding: 4px 10px;
    display: flex;
    align-items: center;
    cursor: pointer;
    box-shadow: 0 2px 6px rgba(37, 99, 235, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.2);
    transition: transform 200ms var(--ease-decelerate, cubic-bezier(0, 0, 0.2, 1)), box-shadow 200ms;

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 6px 14px rgba(37, 99, 235, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.2);
    }
  }

  .gantt-bar-text {
    font-size: 12px;
    font-weight: 600;
    color: white;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    text-shadow: 0 1px 2px rgba(15, 23, 42, 0.2);
  }
}
</style>