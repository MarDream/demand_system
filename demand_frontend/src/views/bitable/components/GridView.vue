<template>
  <div class="grid-view" ref="gridContainerRef">
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
      height="auto"
      auto-resize
      keep-source
      @cell-click="handleCellClick"
      @cell-menu="handleCellMenu"
      @menu-click="handleMenuClick"
      @header-dragend="handleHeaderDragend"
      @edit-closed="handleEditClosed"
    />
    <el-empty v-else-if="!loading" description="暂无字段，请先添加字段" />
    <!-- 新增记录行：即使表中尚无任何记录，也能新增首行（右键菜单需先有行，故提供此常驻入口） -->
    <div v-if="tableColumns.length" class="grid-add-row" @click="emit('rowInsert')">
      <i class="ri-add-line" />
      <span>新建记录</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, watch, nextTick } from 'vue'
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
  aiFillColumn: [fieldId: number]
  aiClassifyColumn: [fieldId: number]
  aiSummarizeColumn: [fieldId: number]
  convertToAiField: [fieldId: number]
}>()

const gridRef = ref<any>()
const gridContainerRef = ref<HTMLElement | null>(null)

// 不可编辑字段类型
const readonlyFieldTypes = new Set(['auto_number', 'created_time', 'modified_time', 'last_modified_time', 'created_user', 'modified_user', 'created_by', 'modified_by', 'formula', 'lookup', 'rollup', 'button'])

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
      [
        { code: 'aiFillColumn', name: 'AI 智能填充此列', prefixIcon: 'vxe-icon-edit' },
        { code: 'aiClassifyColumn', name: 'AI 自动分类', prefixIcon: 'vxe-icon-data' },
        { code: 'aiSummarizeColumn', name: 'AI 自动摘要', prefixIcon: 'vxe-icon-document' },
      ],
      [
        { code: 'convertToAiField', name: '转为 AI 字段', prefixIcon: 'vxe-icon-wrench' },
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

    // 单选 / 多选 / 流程 的选项构造
    const buildSelectOptions = () => {
      const opts = (config.options || []) as Array<{ label: string; color?: string }>
      // options 直接保留 color 字段，value 用 label（与后端 valueText 一致）
      return opts.map((opt) => ({ label: opt.label, value: opt.label, color: opt.color }))
    }

    const isMulti = field.fieldType === 'multi_select'

    if (!isReadonly) {
      switch (field.fieldType) {
        case 'date': {
          // 存储层 bitable_cell_values.value_date 为 DATE 类型，前端统一使用 date(仅日期) 编辑器，
          // 避免提交 "yyyy-MM-dd HH:mm:ss" 被后端 LocalDate.parse 解析失败导致保存报错。
          // 修复：废弃的 transfer 已改为 popupConfig.transfer
          column.editRender = {
            name: 'VxeDatePicker',
            props: {
              placeholder: '选择日期',
              type: 'date',
              valueFormat: 'yyyy-MM-dd',
              labelFormat: 'yyyy-MM-dd',
              popupConfig: { transfer: true },
              clearable: true,
              editable: true,
            },
          }
          // 展示用自定义只读渲染器（文本 + 图标），不把 VxeDatePicker 当作 display 渲染器，
          // 以避免在大量单元格中渲染完整日期选择器引发的渲染异常/卡顿
          column.cellRender = { name: 'BitableDate' }
          break
        }
        case 'single_select':
        case 'multi_select':
        case 'process': {
          const options = buildSelectOptions()
          // cellRender: 彩色标签展示
          column.cellRender = {
            name: 'BitableSelectTag',
            options,
            optionProps: { label: 'label', value: 'value' },
            props: { multiple: isMulti },
          }
          // editRender: VxeSelect 下拉，修复闪退
          // 关键：
          //   1) immediate: true 实时回写 model 值，避免编辑关闭时丢失选择
          //   2) popupConfig.transfer 替代已废弃的 transfer，避免 vxe-table 误判外部点击
          //   3) autoClose: true 选择后立即关闭编辑
          column.editRender = {
            name: 'VxeSelect',
            options,
            optionProps: { label: 'label', value: 'value' },
            props: {
              placeholder: isMulti ? '请选择（可多选）' : '请选择',
              immediate: true,
              autoClose: true,
              clearable: true,
              popupConfig: { transfer: true },
              ...(isMulti ? { multiple: true } : {}),
            },
          }
          break
        }
        case 'progress': {
          // 进度条展示 + 数字编辑（0-100）
          column.cellRender = { name: 'BitableProgress' }
          column.editRender = {
            name: 'VxeNumberInput',
            props: {
              type: 'integer',
              min: 0,
              max: 100,
              placeholder: '0-100',
              align: 'right',
            },
          }
          break
        }
        case 'rating': {
          // 星级展示 + VxeRate 编辑
          const max = Number(config.maxRating) || 5
          column.cellRender = { name: 'BitableRate', props: { max } }
          column.editRender = { name: 'VxeRate', props: { max, readonly: false } }
          break
        }
        case 'check':
        case 'checkbox': {
          // 复选框：只配置 cellRender，编辑通过 cell-click 直接 toggle（避免编辑态翻转歧义）
          column.cellRender = { name: 'BitableCheckbox' }
          column.editRender = null
          break
        }
        case 'number':
        case 'currency': {
          column.editRender = {
            name: 'VxeNumberInput',
            props: {
              type: 'float',
              placeholder: '',
              digits: config.precision ?? 2,
              align: 'right',
            },
          }
          break
        }
        case 'url':
        case 'email':
        case 'phone':
        case 'text':
        default: {
          column.editRender = { name: 'VxeInput', props: { placeholder: '' } }
          break
        }
      }
    }

    // select 字段可筛选
    if (field.fieldType === 'single_select' || field.fieldType === 'multi_select' || field.fieldType === 'process') {
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
// 重要：recordData 必须是“稳定引用”的 ref，而非 computed。
// 若用 computed，每次响应式 tick 都会生成全新的 row 对象；vxe-table 在 cell 编辑时
// 会通过 setCellValue 就地修改 row，从而再次触发 computed 重建 → 新对象 → 重新渲染 →
// 重新进入编辑态，形成无限渲染循环（表现即“闪退/卡死”）。
// 改为：仅在 props.records / props.fields 引用真正变化时（如 loadRecords 重载）才重建数组，
// 编辑过程中的 setCellValue 只是就地改同一批对象，不会触发重建，避免循环。
const recordData = ref<Record<string, any>[]>([])

function buildRecordData() {
  recordData.value = props.records.map((record) => {
    const row: Record<string, any> = {
      _recordId: record.id,
      _recordVersion: record.version,
    }
    // 以 fieldId 为键，并按字段类型转换值
    // 修复点：
    //   - 多选/流程 节点值统一规整为数组，避免 VxeSelect multiple 模式下值类型不匹配
    //   - progress / rating 强制转 number，让 BitableProgress / BitableRate 正确渲染
    //   - checkbox 强制转 boolean，让 BitableCheckbox 正确显示勾选态
    //   - date 优先取 valueDate，避免被 valueText 截胡
    props.fields.forEach((field) => {
      const cell = record.cells?.[field.id]
      let value: unknown = cell?.displayText ?? cell?.valueText ?? cell?.valueNumber ?? cell?.valueDate ?? cell?.valueJson ?? ''

      switch (field.fieldType) {
        case 'multi_select': {
          // 多选：valueJson 可能是 JSON 字符串数组，统一转为数组
          if (Array.isArray(value)) {
            value = value.map((v) => String(v))
          } else if (typeof value === 'string') {
            const trimmed = value.trim()
            if (trimmed.startsWith('[')) {
              try {
                const parsed = JSON.parse(trimmed)
                value = Array.isArray(parsed) ? parsed.map((v) => String(v)) : trimmed ? [trimmed] : []
              } catch {
                value = trimmed ? [trimmed] : []
              }
            } else if (trimmed) {
              value = trimmed.split(',').map((s) => s.trim()).filter(Boolean)
            } else {
              value = []
            }
          } else {
            value = []
          }
          break
        }
        case 'single_select':
        case 'process': {
          value = value == null ? '' : String(value)
          break
        }
        case 'progress':
        case 'rating': {
          const n = Number(value)
          value = Number.isFinite(n) ? n : 0
          break
        }
        case 'number':
        case 'currency': {
          if (value === '' || value == null) {
            value = ''
          } else {
            const n = Number(value)
            value = Number.isFinite(n) ? n : value
          }
          break
        }
        case 'check':
        case 'checkbox': {
          value = value === true || value === 'true' || value === 'True' || value === 1 || value === '1'
          break
        }
        case 'date': {
          // 优先 valueDate，否则保留已取的字符串
          value = cell?.valueDate ?? (typeof value === 'string' ? value : '')
          break
        }
        default: {
          value = value == null ? '' : String(value)
        }
      }
      row[String(field.id)] = value
    })
    return row
  })
}

// 仅在 records / fields 引用变化（重载、加列等）时重建；编辑期间 setCellValue 就地修改不触发
watch(
  () => [props.records, props.fields],
  () => buildRecordData(),
  { immediate: true },
)

function handleCellClick(params: { row: Record<string, any>; column: any }) {
  const { row, column } = params
  if (column.field === '_action') return
  const rowId = row._recordId as number
  const fieldId = Number(column.field)
  if (Number.isNaN(fieldId)) return
  const field = props.fields.find((f) => f.id === fieldId)
  // 复选框字段：点击直接 toggle，不走 vxe-table 编辑态
  if (field && (field.fieldType === 'check' || field.fieldType === 'checkbox')) {
    const raw = row[column.field]
    const checked = raw === true || raw === 'true' || raw === 'True' || raw === 1 || raw === '1'
    emit('cellChange', {
      rowId,
      fieldId,
      newValue: String(!checked),
    })
  }
  // 其他字段：值变化由 editRender 触发，edit-closed 时统一 emit
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
    // AI 字段捷径操作
    case 'aiFillColumn':
      if (column) {
        const fillFieldId = Number(column.field)
        if (!Number.isNaN(fillFieldId)) {
          emit('aiFillColumn', fillFieldId)
        }
      }
      break
    case 'aiClassifyColumn':
      if (column) {
        const classifyFieldId = Number(column.field)
        if (!Number.isNaN(classifyFieldId)) {
          emit('aiClassifyColumn', classifyFieldId)
        }
      }
      break
    case 'aiSummarizeColumn':
      if (column) {
        const summarizeFieldId = Number(column.field)
        if (!Number.isNaN(summarizeFieldId)) {
          emit('aiSummarizeColumn', summarizeFieldId)
        }
      }
      break
    case 'convertToAiField':
      if (column) {
        const convertFieldId = Number(column.field)
        if (!Number.isNaN(convertFieldId)) {
          emit('convertToAiField', convertFieldId)
        }
      }
      break
    default:
      ElMessage.info(`${menu.name} 功能开发中`)
  }
}

function handleEditClosed({ row, column }: any) {
  if (!row || !column) return
  const fieldId = Number(column.field)
  if (Number.isNaN(fieldId)) return
  const field = props.fields.find((item) => item.id === fieldId)
  if (!field || readonlyFieldTypes.has(field.fieldType)) return

  // 从行数据获取当前值（VxeSelect 在 model update 时通过 setCellValue 写回）
  let newValue = row[column.field]

  // 按字段类型规整值，与 editor.vue 的 handleCellChange 后端写入字段对齐
  switch (field.fieldType) {
    case 'multi_select': {
      // 多选：数组转 JSON 字符串，空数组转空字符串
      if (Array.isArray(newValue)) {
        newValue = newValue.length > 0 ? JSON.stringify(newValue) : ''
      } else if (typeof newValue === 'string' && newValue.startsWith('[')) {
        // 已经是 JSON 字符串，保留
      } else if (newValue == null || newValue === '') {
        newValue = ''
      } else {
        newValue = JSON.stringify([String(newValue)])
      }
      break
    }
    case 'single_select':
    case 'process': {
      newValue = newValue == null ? '' : String(newValue)
      break
    }
    case 'progress':
    case 'rating':
    case 'number':
    case 'currency': {
      const n = Number(newValue)
      newValue = Number.isFinite(n) ? n : 0
      break
    }
    case 'check':
    case 'checkbox': {
      const checked = newValue === true || newValue === 'true' || newValue === 1 || newValue === '1'
      newValue = String(checked)
      break
    }
    case 'date': {
      newValue = newValue == null ? '' : String(newValue)
      break
    }
    default: {
      newValue = newValue == null ? '' : String(newValue)
    }
  }

  emit('cellChange', {
    rowId: row._recordId as number,
    fieldId,
    newValue,
  })
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

  // ===== 自定义单元格渲染器样式（对齐飞书多维表格） =====

  // 进度条单元格
  :deep(.bitable-progress-cell) {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    height: 22px;
    padding: 0 4px;
  }
  :deep(.bitable-progress-cell__bar) {
    flex: 1;
    height: 8px;
    border-radius: 4px;
    background: var(--color-surface-variant, rgba(0, 0, 0, 0.06));
    overflow: hidden;
    min-width: 40px;
  }
  :deep(.bitable-progress-cell__fill) {
    height: 100%;
    border-radius: 4px;
    transition: width 0.25s ease;
    min-width: 2px;
  }
  :deep(.bitable-progress-cell__text) {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--color-text-secondary, #64748B);
    font-variant-numeric: tabular-nums;
    min-width: 32px;
    text-align: right;
  }

  // 彩色标签单元格（单选 / 多选 / 流程）
  :deep(.bitable-tag-cell) {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    width: 100%;
    min-height: 22px;
    padding: 2px 0;
  }
  :deep(.bitable-tag-cell.is-multiple) {
    gap: 4px;
  }
  :deep(.bitable-tag-cell__item) {
    display: inline-flex;
    align-items: center;
    max-width: 100%;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
    line-height: 16px;
    border: 1px solid transparent;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    cursor: default;
  }

  // 复选框单元格
  :deep(.bitable-checkbox-cell) {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    width: 100%;
    height: 22px;
    cursor: pointer;
  }
  :deep(.bitable-checkbox-cell i) {
    font-size: 18px;
    color: var(--color-text-placeholder, #94A3B8);
    transition: color 0.15s ease;
  }
  :deep(.bitable-checkbox-cell--checked) {
    color: var(--color-primary, #2563EB) !important;
  }
  :deep(.bitable-checkbox-cell:hover i) {
    color: var(--color-primary-hover, #3B82F6);
  }

  // 评分星级单元格
  :deep(.bitable-rate-cell) {
    display: flex;
    align-items: center;
    gap: 2px;
    width: 100%;
    height: 22px;
  }
  :deep(.bitable-rate-cell i) {
    font-size: 16px;
    color: var(--color-text-placeholder, #CBD5E1);
    transition: color 0.15s ease, transform 0.1s ease;
  }
  :deep(.bitable-rate-cell--active) {
    color: var(--color-warning, #F59E0B) !important;
  }
  :deep(.bitable-rate-cell__text) {
    margin-left: 6px;
    font-size: 12px;
    color: var(--color-text-secondary, #64748B);
    font-variant-numeric: tabular-nums;
  }

  // 空值单元格占位
  :deep(.bitable-cell-empty) {
    color: var(--color-text-placeholder, #CBD5E1);
    font-size: 12px;
  }

  // VxeSelect / VxeDatePicker 编辑态时，撑满单元格
  :deep(.vxe-cell--edit) {
    .vxe-select,
    .vxe-input,
    .vxe-date-picker {
      width: 100%;
    }
  }

  // 新建记录行（常驻底部入口）
  .grid-add-row {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 14px;
    cursor: pointer;
    color: var(--color-text-secondary, #64748B);
    font-size: 13px;
    border-top: 1px solid var(--color-border, #e0e0e0);
    transition: background 0.15s ease, color 0.15s ease;

    i {
      font-size: 16px;
    }

    &:hover {
      background: var(--color-surface-variant, rgba(0, 0, 0, 0.04));
      color: var(--color-primary, #2563EB);
    }
  }

  // 日期单元格展示
  :deep(.bitable-date-cell) {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    min-height: 22px;
  }
  :deep(.bitable-date-cell__icon) {
    font-size: 14px;
    color: var(--color-text-placeholder, #94A3B8);
    flex-shrink: 0;
  }
  :deep(.bitable-date-cell__text) {
    font-size: 13px;
    color: var(--color-text, #1f2937);
    font-variant-numeric: tabular-nums;
  }
}
</style>
