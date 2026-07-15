<template>
  <div class="calendar-view">
    <div class="calendar-header">
      <span class="calendar-header__label">日期字段：</span>
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
    </div>

    <div v-if="!dateFieldId" class="calendar-empty">
      <el-empty description="请选择日期字段以展示日历视图" />
    </div>

    <div v-else-if="loading" class="calendar-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>

    <div v-else class="calendar-body">
      <div class="calendar-nav">
        <el-button link @click="prevMonth">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span class="calendar-month-label">{{ currentMonthLabel }}</span>
        <el-button link @click="nextMonth">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="calendar-grid">
        <div class="calendar-weekdays">
          <div v-for="day in weekdays" :key="day" class="calendar-weekday">{{ day }}</div>
        </div>
        <div class="calendar-days">
          <div
            v-for="(day, index) in calendarDays"
            :key="index"
            class="calendar-day"
            :class="{ 'calendar-day--other-month': day.isOtherMonth }"
          >
            <div class="calendar-day-header">{{ day.dayNumber }}</div>
            <div class="calendar-day-events">
              <div
                v-for="record in day.records.slice(0, 3)"
                :key="record.id"
                class="calendar-event"
                @click="handleRecordClick(record)"
              >
                {{ getRecordTitle(record) }}
              </div>
              <div v-if="day.records.length > 3" class="calendar-more">
                +{{ day.records.length - 3 }} 更多
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowLeft, ArrowRight, Loading } from '@element-plus/icons-vue'
import type { BitableField, BitableRecord, BitableTable } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
}>()

const emit = defineEmits<{
  recordClick: [record: BitableRecord]
}>()

const dateFieldId = ref<number | null>(null)
const currentMonth = ref(new Date())

const dateFields = computed(() =>
  props.fields.filter((f) => f.fieldType === 'date')
)

const weekdays = ['日', '一', '二', '三', '四', '五', '六']

const currentMonthLabel = computed(() => {
  const y = currentMonth.value.getFullYear()
  const m = currentMonth.value.getMonth() + 1
  return `${y}年${m}月`
})

function handleFieldChange() {
  // 字段切换无需额外处理
}

function prevMonth() {
  const d = new Date(currentMonth.value)
  d.setMonth(d.getMonth() - 1)
  currentMonth.value = d
}

function nextMonth() {
  const d = new Date(currentMonth.value)
  d.setMonth(d.getMonth() + 1)
  currentMonth.value = d
}

function getRecordTitle(record: BitableRecord): string {
  const textFields = props.fields.filter((f) => f.fieldType === 'text')
  if (textFields.length === 0) return `记录 ${record.id}`
  const cell = record.cells?.[textFields[0].id]
  return cell?.valueText || `记录 ${record.id}`
}

function handleRecordClick(record: BitableRecord) {
  emit('recordClick', record)
}

const calendarDays = computed(() => {
  const year = currentMonth.value.getFullYear()
  const month = currentMonth.value.getMonth()
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const startDay = firstDay.getDay()
  const daysInMonth = lastDay.getDate()

  const days: Array<{
    date: Date
    dayNumber: number
    isOtherMonth: boolean
    records: BitableRecord[]
  }> = []

  // 前置空白格子
  for (let i = 0; i < startDay; i++) {
    const d = new Date(year, month, -startDay + i + 1)
    days.push({
      date: d,
      dayNumber: d.getDate(),
      isOtherMonth: true,
      records: [],
    })
  }

  // 本月日期
  for (let i = 1; i <= daysInMonth; i++) {
    const d = new Date(year, month, i)
    const dateStr = d.toISOString().split('T')[0]
    const matchingRecords = props.records.filter((r) => {
      if (!dateFieldId.value || !r.cells?.[dateFieldId.value]) return false
      const cell = r.cells[dateFieldId.value]
      const cellDate = cell.valueDate || cell.valueText
      return cellDate === dateStr
    })
    days.push({
      date: d,
      dayNumber: i,
      isOtherMonth: false,
      records: matchingRecords,
    })
  }

  // 后置空白格子（补满 6 行）
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month + 1, i)
    days.push({
      date: d,
      dayNumber: i,
      isOtherMonth: true,
      records: [],
    })
  }

  return days
})
</script>

<style scoped lang="scss">
.calendar-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.calendar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;

  .calendar-header__label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }
}

.calendar-empty,
.calendar-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.calendar-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.calendar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;

  .calendar-month-label {
    font-size: var(--font-size-lg);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
  }
}

.calendar-grid {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  height: 32px;
  flex-shrink: 0;
  background: var(--color-background);
  border-bottom: 1px solid var(--color-border);

  .calendar-weekday {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    border-right: 1px solid var(--color-border);

    &:last-child {
      border-right: none;
    }
  }
}

.calendar-days {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 1fr);
  overflow: hidden;

  .calendar-day {
    border-right: 1px solid var(--color-border);
    border-bottom: 1px solid var(--color-border);
    overflow: hidden;
    display: flex;
    flex-direction: column;

    &:last-child {
      border-right: none;
    }

    &.calendar-day--other-month {
      background: var(--color-background);
    }
  }

  .calendar-day-header {
    padding: 4px 8px;
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    border-bottom: 1px solid var(--color-border);
  }

  .calendar-day-events {
    flex: 1;
    padding: 2px 4px;
    overflow-y: auto;
  }

  .calendar-event {
    font-size: var(--font-size-xs);
    color: var(--color-primary);
    background: var(--color-primary-light-9);
    padding: 2px 6px;
    margin-bottom: 2px;
    border-radius: 3px;
    cursor: pointer;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    &:hover {
      background: var(--color-primary-light-8);
    }
  }

  .calendar-more {
    font-size: var(--font-size-xs);
    color: var(--color-text-secondary);
    padding: 2px 6px;
  }
}
</style>