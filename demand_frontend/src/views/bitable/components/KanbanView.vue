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
          <span class="kanban-column__count">{{ group.records.length }}</span>
        </div>
        <div
          class="kanban-column__body"
          :class="{ 'is-drop-target': dragRecordId !== null && dragFromGroup !== group.value }"
          @dragover.prevent
          @drop="handleColumnDrop(group.value)"
        >
          <div
            v-for="record in group.records"
            :key="record.id"
            class="kanban-card"
            :class="{ 'is-dragging': dragRecordId === record.id }"
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
// ===== 多维表格 KanbanView 激进风格精修（2026-08-03）=====
// 设计目标：
// 1. 列容器浅灰蓝 + 14px 圆角（与卡片形成层级反差）
// 2. 卡片 14px 圆角 + 拖拽态旋转 1.5deg + 强化阴影
// 3. 拖拽源卡片半透明 + 目标列高亮
// 4. 列头 WIP 进度条（计数 / 上限），超限变红
// 5. 横向滚动右侧 24px 渐隐遮罩

.kanban-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.kanban-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border, #e2e8f0);
  background: var(--color-surface, #fff);
  flex-shrink: 0;

  .kanban-header__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-secondary, #475569);
    white-space: nowrap;
  }
}

.kanban-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

// 看板主体：横向滚动 + 右侧渐隐遮罩
.kanban-board {
  display: flex;
  gap: 16px;
  padding: 20px;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  position: relative;

  // 右侧渐隐遮罩（提示可滚动）
  &::after {
    content: '';
    position: sticky;
    right: 0;
    top: 0;
    width: 24px;
    height: 100%;
    background: linear-gradient(90deg, transparent, var(--color-background, #f8fafc));
    pointer-events: none;
    flex-shrink: 0;
    align-self: stretch;
  }
}

// 列容器：浅灰蓝底 + 14px 圆角（与卡片白底形成层级反差）
.kanban-column {
  flex: 0 0 300px;
  display: flex;
  flex-direction: column;
  background: var(--color-kanban-column-bg, #f1f5f9);
  border-radius: var(--radius-card-lg, 14px);
  border: 0.5px solid var(--color-border, #e2e8f0);
  max-height: 100%;
  transition: background-color 200ms var(--ease-standard, ease);
}

// 列头：标题 + WIP 计数 + 进度条
.kanban-column__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 10px;
  flex-shrink: 0;

  .kanban-column__dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
    box-shadow: 0 0 0 2px var(--color-surface, #fff);
  }

  .kanban-column__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary, #0f172a);
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// WIP 计数（替换 el-badge 灰色样式）
.kanban-column__count {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-secondary, #475569);
  background: var(--color-surface, #fff);
  border-radius: var(--radius-tag, 6px);
  font-variant-numeric: tabular-nums;

  &.is-wip-overlimit {
    color: var(--color-danger, #ef4444);
    background: var(--color-danger-light, rgba(239, 68, 68, 0.1));
  }
}

// 列体：拖拽目标态高亮
.kanban-column__body {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px 10px;
  min-height: 60px;
  border-radius: 0 0 var(--radius-card-lg, 14px) var(--radius-card-lg, 14px);
  transition: background-color 200ms var(--ease-standard, ease);

  &.is-drop-target {
    background: var(--color-kanban-column-drop-target, #eff6ff);
    box-shadow: inset 0 0 0 2px var(--color-primary, #2563eb);
  }
}

.kanban-column__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  color: var(--color-text-placeholder, #94a3b8);
  font-size: 12px;
  font-weight: 500;
  border: 1.5px dashed var(--color-border, #e2e8f0);
  border-radius: var(--radius-md, 8px);
  background: transparent;
}

// 卡片：14px 圆角 + hover 抬起 + 拖拽态旋转
.kanban-card {
  background: var(--color-surface, #fff);
  border-radius: var(--radius-card-lg, 14px);
  border: 0.5px solid var(--color-border, #e2e8f0);
  padding: 12px 14px;
  margin-bottom: 8px;
  cursor: grab;
  transition: box-shadow 200ms var(--ease-standard, ease), transform 200ms var(--ease-decelerate, cubic-bezier(0, 0, 0.2, 1)), opacity 200ms;

  &:hover {
    box-shadow: var(--shadow-card-lift, 0 12px 32px -4px rgba(15, 23, 42, 0.16));
    transform: translateY(-2px);
    border-color: var(--color-border-hover, #cbd5e1);
  }

  &:active {
    cursor: grabbing;
  }

  // 拖拽中：源卡片半透明 + 旋转 + 强化阴影
  &.is-dragging {
    opacity: var(--color-drag-source-opacity, 0.35);
    transform: rotate(1.5deg) scale(0.98);
    box-shadow: var(--shadow-drag-rotate, 0 20px 40px -8px rgba(15, 23, 42, 0.25));
    cursor: grabbing;
  }
}

.kanban-card__field {
  display: flex;
  flex-direction: column;
  margin-bottom: 6px;

  &:last-of-type {
    margin-bottom: 0;
  }
}

.kanban-card__label {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-placeholder, #94a3b8);
  line-height: 1.4;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.kanban-card__value {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary, #0f172a);
  line-height: 1.5;
  word-break: break-all;
  margin-top: 2px;
}

.kanban-card__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 0.5px solid var(--color-border, #e2e8f0);
  opacity: 0;
  transition: opacity 200ms var(--ease-standard, ease);
}

.kanban-card:hover .kanban-card__actions {
  opacity: 1;
}

.kanban-column__footer {
  padding: 8px 10px 12px;
  flex-shrink: 0;
}
</style>
