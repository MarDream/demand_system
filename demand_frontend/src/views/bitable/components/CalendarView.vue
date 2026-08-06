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
            :class="{
              'calendar-day--other-month': day.isOtherMonth,
              'calendar-day--today': isToday(day)
            }"
          >
            <div class="calendar-day-header">
              <span class="calendar-day-number">{{ day.dayNumber }}</span>
            </div>
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

function isToday(day: { date: Date }): boolean {
  const today = new Date()
  return (
    day.date.getFullYear() === today.getFullYear() &&
    day.date.getMonth() === today.getMonth() &&
    day.date.getDate() === today.getDate()
  )
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
// ===== 多维表格 CalendarView 激进风格精修（2026-08-03）=====
// 设计目标：
// 1. 今日：主色边框 + 圆形日期徽章 + 主色底色
// 2. 事件块加左侧色条（按字段类型可选色）
// 3. 单元格行高更舒展
// 4. 月份标签字体加大加粗

.calendar-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.calendar-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border, #e2e8f0);
  background: var(--color-surface, #fff);
  flex-shrink: 0;

  .calendar-header__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-secondary, #475569);
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
  gap: 20px;
  padding: 16px;
  border-bottom: 1px solid var(--color-border, #e2e8f0);
  flex-shrink: 0;

  .calendar-month-label {
    font-size: 18px;
    font-weight: 700;
    color: var(--color-text-primary, #0f172a);
    letter-spacing: -0.01em;
    min-width: 120px;
    text-align: center;
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
  height: 40px;
  flex-shrink: 0;
  background: var(--color-background, #f8fafc);
  border-bottom: 1px solid var(--color-border, #e2e8f0);

  .calendar-weekday {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-secondary, #475569);
    letter-spacing: 0.04em;
    border-right: 1px solid var(--color-border, #e2e8f0);

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
    border-right: 1px solid var(--color-border, #e2e8f0);
    border-bottom: 1px solid var(--color-border, #e2e8f0);
    overflow: hidden;
    display: flex;
    flex-direction: column;
    transition: background-color 150ms var(--ease-standard, ease);

    &:nth-child(7n) {
      border-right: none;
    }

    &.calendar-day--other-month {
      background: var(--color-background, #f8fafc);

      .calendar-day-number {
        color: var(--color-text-placeholder, #cbd5e1);
      }
    }

    // 今日：主色边框 + 浅蓝底 + 日期圆形徽章
    &.calendar-day--today {
      background: var(--color-today-bg, #eff6ff);
      box-shadow: inset 0 0 0 1.5px var(--color-today-ring, #3b82f6);

      .calendar-day-number {
        background: var(--color-primary, #2563eb);
        color: #fff;
        font-weight: 700;
        box-shadow: 0 2px 6px rgba(37, 99, 235, 0.35);
      }
    }

    &:hover:not(.calendar-day--other-month) {
      background: var(--color-row-hover-bg, rgba(59, 130, 246, 0.04));
    }
  }

  .calendar-day-header {
    padding: 6px 8px;
    border-bottom: 1px solid var(--color-border, #e2e8f0);
    display: flex;
    align-items: center;
  }

  .calendar-day-number {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 22px;
    height: 22px;
    padding: 0 6px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-text-primary, #0f172a);
    font-variant-numeric: tabular-nums;
    border-radius: 50%;
    transition: all 150ms var(--ease-standard, ease);
  }

  .calendar-day-events {
    flex: 1;
    padding: 3px 4px 4px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  // 事件块：左侧 2px 主色条
  .calendar-event {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-primary, #2563eb);
    background: var(--color-primary-subtle, #eff6ff);
    padding: 3px 6px 3px 8px;
    border-radius: var(--radius-tag, 6px);
    border-left: 2px solid var(--color-primary, #2563eb);
    cursor: pointer;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: all 150ms var(--ease-standard, ease);

    &:hover {
      background: var(--color-primary-light, #dbeafe);
      transform: translateX(1px);
    }
  }

  .calendar-more {
    font-size: 11px;
    font-weight: 600;
    color: var(--color-text-secondary, #475569);
    padding: 2px 6px;
    border-radius: var(--radius-tag, 6px);
    cursor: pointer;
    transition: background-color 150ms;

    &:hover {
      background: var(--color-surface-alt, #f1f5f9);
    }
  }
}
</style>