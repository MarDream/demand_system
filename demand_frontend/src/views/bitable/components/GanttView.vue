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
import type { BitableField, BitableRecord, BitableTable, CellValue } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
}>()

const dateFieldId = ref<number | null>(null)
const rangeFieldId = ref<number | null>(null)

// 日期字段列表
const dateFields = computed(() =>
  props.fields.filter((f) => f.fieldType === 'date')
)

// 日期范围字段列表
const rangeFields = computed(() =>
  props.fields.filter((f) => f.fieldType === 'date_range')
)

// 甘特图时间轴（未来 30 天）
const chartDays = computed(() => {
  const days: { date: string; label: string }[] = []
  const today = new Date()
  for (let i = 0; i < 30; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + i)
    const dateStr = d.toISOString().split('T')[0]
    const label = `${d.getMonth() + 1}/${d.getDate()}`
    days.push({ date: dateStr, label })
  }
  return days
})

function handleFieldChange() {
  // 字段切换时无需额外处理，computed 会自动更新
}

// 获取记录标题（第一个文本字段）
function getRecordTitle(record: BitableRecord): string {
  const textFields = props.fields.filter((f) => f.fieldType === 'text')
  if (textFields.length === 0) return `记录 ${record.id}`
  const cell = record.cells?.[textFields[0].id]
  return cell?.valueText || `记录 ${record.id}`
}

// 获取记录的日期范围
function getRecordDateRange(record: BitableRecord): { start: string; end: string } | null {
  if (rangeFieldId.value && record.cells?.[rangeFieldId.value]) {
    const cell = record.cells[rangeFieldId.value]
    const json = cell.valueJson as unknown
    if (json && typeof json === 'object' && 'start' in json && 'end' in json) {
      return { start: (json as any).start, end: (json as any).end }
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

  const startDate = new Date(range.start)
  const endDate = new Date(range.end)
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const firstChartDate = new Date(today)
  const lastChartDate = new Date(today)
  lastChartDate.setDate(today.getDate() + 29)

  // 计算在图表中的位置
  const startOffset = Math.max(0, Math.floor((startDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)))
  const endOffset = Math.min(29, Math.floor((endDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)))

  const widthPercent = ((endOffset - startOffset + 1) / 30) * 100
  const leftPercent = (startOffset / 30) * 100

  return {
    left: `${leftPercent}%`,
    width: `${widthPercent}%`,
    backgroundColor: '#409eff',
  }
}
</script>

<style scoped lang="scss">
.gantt-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.gantt-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;

  .gantt-header__label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
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

.gantt-sidebar {
  width: 200px;
  border-right: 1px solid var(--color-border);
  flex-shrink: 0;
  overflow-y: auto;

  .gantt-sidebar-header {
    height: 40px;
    padding: 8px 12px;
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    background: var(--color-background);
    border-bottom: 1px solid var(--color-border);
  }

  .gantt-sidebar-row {
    height: 32px;
    padding: 6px 12px;
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
    border-bottom: 1px solid var(--color-border);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.gantt-chart {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;

  .gantt-chart-header {
    display: flex;
    height: 40px;
    background: var(--color-background);
    border-bottom: 1px solid var(--color-border);
  }

  .gantt-day-header {
    width: 40px;
    height: 40px;
    padding: 8px 4px;
    font-size: var(--font-size-xs);
    color: var(--color-text-secondary);
    text-align: center;
    border-right: 1px solid var(--color-border);
  }

  .gantt-chart-body {
    overflow-y: auto;
  }

  .gantt-row {
    height: 32px;
    border-bottom: 1px solid var(--color-border);
    position: relative;
  }

  .gantt-bar {
    position: absolute;
    top: 4px;
    height: 24px;
    border-radius: 4px;
    padding: 2px 8px;
    display: flex;
    align-items: center;
    cursor: pointer;
  }

  .gantt-bar-text {
    font-size: var(--font-size-xs);
    color: white;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>