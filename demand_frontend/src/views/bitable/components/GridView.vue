<template>
  <div class="grid-view">
    <vxe-grid
      v-if="tableColumns.length"
      ref="gridRef"
      :columns="tableColumns"
      :data="recordData"
      :loading="loading"
      border
      :edit-config="{ trigger: 'click', mode: 'cell' }"
      :checkbox-config="{ checkMethod: () => false }"
      :menu-config="menuConfig"
      :header-drag-config="{ enabled: true }"
      :height="500"
      @cell-click="handleCellClick"
      @cell-menu="handleCellMenu"
      @menu-click="handleMenuClick"
      @header-dragend="handleHeaderDragend"
    />
    <el-empty v-else-if="!loading" description="暂无字段，请先添加字段" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BitableField, BitableRecord, BitableTable } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
}>()

const emit = defineEmits<{
  cellChange: [data: { rowId: number; fieldId: number; newValue: unknown }]
  rowInsert: [data?: { position: 'above' | 'below'; rowId: number }]
  rowDelete: [rowId: number]
  renameField: [fieldId: number]
  cloneField: [fieldId: number]
  headerDragend: [fields: { fieldId: number; newIndex: number }[]]
}>()

const gridRef = ref<any>()

// 不可编辑字段类型
const readonlyFieldTypes = new Set(['auto_number', 'created_time', 'modified_time', 'created_user', 'modified_user'])

// 右键菜单配置
const menuConfig = reactive({
  enabled: true,
  trigger: 'cell' as const,
  body: {
    options: [
      [
        { code: 'insertRowAbove', name: '在上方插入行', prefixIcon: 'vxe-icon-arrow-top' },
        { code: 'insertRowBelow', name: '在下方插入行', prefixIcon: 'vxe-icon-arrow-bottom' },
      ],
      [
        { code: 'deleteRow', name: '删除行', prefixIcon: 'vxe-icon-delete' },
      ],
      [
        { code: 'copyCell', name: '复制单元格', prefixIcon: 'vxe-icon-copy' },
      ],
    ],
  },
  header: {
    options: [
      [
        { code: 'renameCol', name: '重命名列' },
        { code: 'cloneCol', name: '克隆列' },
        { code: 'insertColLeft', name: '在左侧插入列' },
        { code: 'insertColRight', name: '在右侧插入列' },
        { code: 'deleteCol', name: '删除列' },
      ],
    ],
  },
})

// 构建列配置
const tableColumns = computed(() => {
  const cols = props.fields.map((field) => {
    const isReadonly = readonlyFieldTypes.has(field.fieldType)
    const config = field.config || {}
    const column: Record<string, any> = {
      field: String(field.id),
      title: field.name,
      width: field.width || 150,
      minWidth: 80,
      sortable: true,
      showOverflow: true,
    }

    if (!isReadonly) {
      column.editRender = { name: '$input', props: { placeholder: '' } }
    }

    // select 字段可筛选
    if (field.fieldType === 'single_select' || field.fieldType === 'multi_select') {
      const options = config.options || []
      column.filters = options.map((opt: { label: string; value?: string }) => ({
        label: opt.label,
        value: opt.label,
      }))
    }

    return column
  })

  return cols
})

// 构建行数据
const recordData = computed(() => {
  return props.records.map((record) => {
    const row: Record<string, any> = {
      _recordId: record.id,
      _recordVersion: record.version,
    }
    // 以 fieldId 为键
    props.fields.forEach((field) => {
      const cell = record.cells?.[field.id]
      row[String(field.id)] = cell?.displayText ?? cell?.valueText ?? cell?.valueNumber ?? cell?.valueDate ?? cell?.valueJson ?? ''
    })
    return row
  })
})

function handleCellClick({ row, column }: { row: Record<string, any>; column: any }) {
  if (column.field === '_action' || readonlyFieldTypes.has(column.field)) return
  const rowId = row._recordId as number
  const fieldId = Number(column.field)
  if (Number.isNaN(fieldId)) return
  // 值变化由 editRender 触发，此处仅做记录
}

function handleCellMenu({ row, column }: any) {
  // 右键菜单打开时的回调，可用于动态控制菜单项
}

function handleMenuClick({ menu, row, column }: any) {
  switch (menu.code) {
    case 'renameCol':
      if (column) {
        const fieldId = Number(column.field)
        if (!Number.isNaN(fieldId)) {
          emit('renameField', fieldId)
        }
      }
      break
    case 'cloneCol':
      if (column) {
        const fieldId = Number(column.field)
        if (!Number.isNaN(fieldId)) {
          emit('cloneField', fieldId)
        }
      }
      break
    case 'insertRowAbove':
      emit('rowInsert', { position: 'above', rowId: row._recordId })
      break
    case 'insertRowBelow':
      emit('rowInsert', { position: 'below', rowId: row._recordId })
      break
    case 'deleteRow':
      ElMessageBox.confirm('确定删除该行吗？', '删除确认', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        emit('rowDelete', row._recordId as number)
      }).catch(() => {})
      break
    case 'copyCell':
      if (row && column) {
        const cellValue = row[column.field]
        navigator.clipboard.writeText(String(cellValue ?? '')).then(() => {
          ElMessage.success('已复制')
        }).catch(() => {
          ElMessage.error('复制失败')
        })
      }
      break
    default:
      ElMessage.info(`${menu.name} 功能开发中`)
  }
}

function handleCellChange(row: Record<string, any>, fieldId: number, newValue: unknown) {
  emit('cellChange', {
    rowId: row._recordId as number,
    fieldId,
    newValue,
  })
}

// 拖拽列头完成时，计算新的列顺序
function handleHeaderDragend({ startIndex, endIndex, columns }: any) {
  const newOrder = columns.map((col: any, index: number) => ({
    fieldId: Number(col.field),
    newIndex: index,
  }))
  emit('headerDragend', newOrder)
}
</script>

<style scoped lang="scss">
.grid-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  :deep(.vxe-grid) {
    border-radius: 0;
  }

  // 显示列边框（纵线条）
  :deep(.vxe-table--render-default) {
    &.vxe-table--border {
      .vxe-table--body-wrapper,
      .vxe-table--header-wrapper {
        .vxe-cell {
          border-right: 1px solid var(--color-border, #e0e0e0);
        }
      }
    }
  }

  // 确保 vxe-table 样式正确显示
  :deep(.vxe-table--body-wrapper) {
    overflow-y: auto;
  }

  // 表格行边框
  :deep(.vxe-body--row) {
    .vxe-cell {
      border-bottom: 1px solid var(--color-border, #e0e0e0);
    }
  }

  // 表头行列边框
  :deep(.vxe-header--row) {
    .vxe-cell {
      border-bottom: 1px solid var(--color-border, #e0e0e0);
    }
  }

  :deep(.vxe-table--header-wrapper) {
    background: var(--color-surface);
  }

  // 右键菜单样式覆盖（与项目主题一致）
  :deep(.vxe-context-menu) {
    border-radius: var(--radius-md, 6px);
    box-shadow: var(--shadow-lg, 0 8px 24px rgba(0, 0, 0, 0.12));
  }
}
</style>
