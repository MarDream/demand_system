<template>
  <div class="kanban-view">
    <div class="kanban-header">
      <span class="kanban-header__label">分组字段：</span>
      <el-select
        v-model="groupFieldId"
        placeholder="选择分组字段"
        style="width: 200px;"
        size="small"
        @change="handleGroupChange"
      >
        <el-option
          v-for="f in selectFields"
          :key="f.id"
          :label="f.name"
          :value="f.id"
        />
      </el-select>
    </div>

    <div v-if="!groupFieldId" class="kanban-empty">
      <el-empty description="请选择一个单选/多选字段作为分组依据" />
    </div>

    <div v-else class="kanban-board">
      <div
        v-for="group in groups"
        :key="group.value"
        class="kanban-column"
      >
        <div class="kanban-column__header">
          <span
            class="kanban-column__dot"
            :style="{ background: group.color || '#909399' }"
          />
          <span class="kanban-column__title">{{ group.label }}</span>
          <el-badge :value="group.records.length" :max="999" type="info" />
        </div>
        <div
          class="kanban-column__body"
          @dragover.prevent
          @drop="handleColumnDrop(group.value)"
        >
          <div
            v-for="record in group.records"
            :key="record.id"
            class="kanban-card"
            draggable="true"
            @dragstart="handleDragStart(record, group.value)"
            @dragend="handleDragEnd"
          >
            <div
              v-for="field in cardFields"
              :key="field.id"
              class="kanban-card__field"
            >
              <span class="kanban-card__label">{{ field.name }}</span>
              <span class="kanban-card__value">{{ formatCellValue(record.cells?.[field.id], field) }}</span>
            </div>
            <div class="kanban-card__actions">
              <el-button link size="small" @click="emit('recordUpdate', record)">
                <el-icon><Edit /></el-icon>
              </el-button>
            </div>
          </div>
          <div v-if="!group.records.length" class="kanban-column__placeholder">
            拖拽卡片到此处
          </div>
        </div>
        <div class="kanban-column__footer">
          <el-button link type="primary" size="small" @click="handleAddRecord(group.value)">
            <el-icon><Plus /></el-icon> 添加卡片
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus, Edit } from '@element-plus/icons-vue'
import type { BitableField, BitableRecord, BitableTable, CellValue } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
}>()

const emit = defineEmits<{
  recordUpdate: [record: BitableRecord]
  cardMove: [data: { recordId: number; fieldId: number; fromGroup: string; toGroup: string }]
  rowInsert: [data: { groupValue: string; fieldId: number }]
}>()

// 当前选中的分组字段 ID
const groupFieldId = ref<number | null>(null)

// 拖拽状态
const dragRecordId = ref<number | null>(null)
const dragFromGroup = ref<string>('')

// 可选作分组的字段（单选/多选）
const selectFields = computed(() =>
  props.fields.filter((f) => f.fieldType === 'single_select' || f.fieldType === 'multi_select')
)

// 分组字段
const groupField = computed(() =>
  props.fields.find((f) => f.id === groupFieldId.value) ?? null
)

// 卡片上显示的字段（前 4 个非分组字段）
const cardFields = computed(() =>
  props.fields.filter((f) => f.id !== groupFieldId.value).slice(0, 4)
)

// 根据选中字段的选项进行分组
const groups = computed(() => {
  const field = groupField.value
  if (!field?.config?.options) return []

  const options = field.config.options
  const groupMap = new Map<string, { label: string; color?: string; records: BitableRecord[] }>()

  // 初始化分组桶
  for (const opt of options) {
    groupMap.set(opt.label, { label: opt.label, color: opt.color, records: [] })
  }
  // 添加"未分组"桶
  groupMap.set('__ungrouped__', { label: '未分组', records: [] })

  for (const record of props.records) {
    const cell = record.cells?.[field.id]
    if (!cell) {
      groupMap.get('__ungrouped__')!.records.push(record)
      continue
    }

    const valueJson = cell.valueJson
    const valueText = cell.valueText
    let labels: string[] = []

    if (Array.isArray(valueJson)) {
      // 多选：过滤确保元素为 string
      labels = valueJson.filter((v): v is string => typeof v === 'string')
    } else if (valueText) {
      labels = [valueText]
    }

    let matched = false
    for (const label of labels) {
      if (groupMap.has(label)) {
        groupMap.get(label)!.records.push(record)
        matched = true
      }
    }
    if (!matched) {
      groupMap.get('__ungrouped__')!.records.push(record)
    }
  }

  // 过滤掉空的"未分组"桶
  return Array.from(groupMap.entries())
    .filter(([key, g]) => key !== '__ungrouped__' || g.records.length > 0)
    .map(([key, g]) => ({ value: key, ...g }))
})

function handleGroupChange() {
  // 切换分组字段时无需额外处理，computed 会自动更新
}

// 拖拽
function handleDragStart(record: BitableRecord, fromGroup: string) {
  dragRecordId.value = record.id
  dragFromGroup.value = fromGroup
}

function handleDragEnd() {
  dragRecordId.value = null
  dragFromGroup.value = ''
}

function handleColumnDrop(toGroup: string) {
  if (dragRecordId.value == null || dragFromGroup.value === toGroup || !groupFieldId.value) return
  emit('cardMove', {
    recordId: dragRecordId.value,
    fieldId: groupFieldId.value,
    fromGroup: dragFromGroup.value,
    toGroup,
  })
  dragRecordId.value = null
  dragFromGroup.value = ''
}

function handleAddRecord(groupValue: string) {
  if (!groupFieldId.value) return
  emit('rowInsert', { groupValue, fieldId: groupFieldId.value })
}

// 格式化单元格值
function formatCellValue(cell: CellValue | undefined, field: BitableField): string {
  if (!cell) return '-'
  if (cell.displayText) return cell.displayText

  switch (field.fieldType) {
    case 'multi_select':
      if (Array.isArray(cell.valueJson)) {
        return (cell.valueJson as unknown[]).filter((v): v is string => typeof v === 'string').join(', ')
      }
      return cell.valueText || '-'
    case 'single_select':
      return cell.valueText || '-'
    case 'number':
      return cell.valueNumber != null ? String(cell.valueNumber) : '-'
    case 'date':
      return cell.valueDate || '-'
    case 'check':
      return cell.valueText === 'true' ? '✓' : '✗'
    case 'progress':
      return cell.valueNumber != null ? `${Math.round(cell.valueNumber * 100)}%` : '-'
    case 'rating':
      return cell.valueNumber != null ? '★'.repeat(cell.valueNumber) : '-'
    default:
      return cell.valueText || String(cell.valueJson || '-')
  }
}
</script>

<style scoped lang="scss">
.kanban-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.kanban-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;

  .kanban-header__label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    white-space: nowrap;
  }
}

.kanban-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.kanban-board {
  display: flex;
  gap: 12px;
  padding: 16px;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
}

.kanban-column {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  background: var(--color-background);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  max-height: 100%;
}

.kanban-column__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 12px 8px;
  flex-shrink: 0;

  .kanban-column__dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .kanban-column__title {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    flex: 1;
  }
}

.kanban-column__body {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
  min-height: 60px;
}

.kanban-column__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
}

.kanban-card {
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: grab;
  transition: box-shadow 0.2s ease, transform 0.15s ease;

  &:hover {
    box-shadow: var(--shadow-sm);
    transform: translateY(-1px);
  }

  &:active {
    cursor: grabbing;
  }
}

.kanban-card__field {
  display: flex;
  flex-direction: column;
  margin-bottom: 4px;

  &:last-of-type {
    margin-bottom: 0;
  }
}

.kanban-card__label {
  font-size: 11px;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.kanban-card__value {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  line-height: 1.5;
  word-break: break-all;
}

.kanban-card__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.kanban-card:hover .kanban-card__actions {
  opacity: 1;
}

.kanban-column__footer {
  padding: 8px;
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}
</style>
