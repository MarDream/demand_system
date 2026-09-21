<template>
  <!-- --grid-row-height 与 vxe row-config 同源（effectiveRowHeight），保证 .vxe-cell 高度永远等于实际行高，避免遮挡 -->
  <div class="grid-view" ref="gridContainerRef" :style="{ '--grid-row-height': effectiveRowHeight + 'px' }">
      <!-- 网格区：vxe 需要确定高度的父容器（flex:1）。注意不能用 tableColumns.length 判断（恒含 seq + 幽灵列） -->
      <div v-if="visibleFieldCount > 0" class="grid-view__table">
        <vxe-grid
        ref="gridRef"
        :columns="tableColumns"
        :data="recordData"
        :loading="loading"
        border
        :cell-config="{ height: effectiveRowHeight }"
        :header-cell-config="{ height: effectiveRowHeight }"
        :seq-config="{ startIndex: seqStartIndex || 0 }"
        :edit-config="{ trigger: 'click', mode: 'cell', showIcon: false, beforeEditMethod: handleBeforeEdit }"
        :keyboard-config="{ isArrow: false, isDel: false, isEnter: true, isTab: false, isEdit: false, isChecked: false, isEsc: true }"
        :row-class-name="rowClassName"
        :menu-config="menuConfig"
        :column-config="{ resizable: true }"
        :header-drag-config="{ enabled: true }"
        height="auto"
        auto-resize
        keep-source
        @cell-click="handleCellClick"
        @cell-menu="handleCellMenu"
        @cell-mouseenter="handleCellMouseEnter"
        @cell-mouseleave="handleCellMouseLeave"
        @scroll="hideCellTooltip"
        @header-cell-menu="handleCellMenu"
        @menu-click="handleMenuClick"
        @header-dragend="handleHeaderDragend"
        @resizable-change="handleColResize"
        @edit-closed="handleEditClosed"
      >
        <!-- 序号列表头：全选框（半选态自动显示） -->
        <template #seqSelectHeader>
          <span class="seq-check-header" @click.stop="toggleAllSelection">
            <i
              v-if="isAllSelected"
              class="ri-checkbox-line seq-check-header__box is-checked"
            />
            <i
              v-else-if="isIndeterminate"
              class="ri-checkbox-indeterminate-line seq-check-header__box is-checked"
            />
            <i v-else class="ri-checkbox-blank-line seq-check-header__box" />
          </span>
        </template>
        <!-- 序号列单元格：hover 显示复选框，勾选后常显蓝色勾；默认显示序号 -->
        <template #seqCell="{ row, rowIndex }">
          <span
            class="seq-check-cell"
            :class="{ 'is-checked': isRowChecked(row) }"
            @click.stop="toggleRowSelection(row)"
          >
            <i
              v-if="isRowChecked(row)"
              class="ri-checkbox-line seq-check-cell__box is-checked"
            />
            <i v-else class="ri-checkbox-blank-line seq-check-cell__box" />
            <span class="seq-check-cell__seq">{{ (seqStartIndex || 0) + rowIndex + 1 }}</span>
          </span>
        </template>
        <!-- 表头插槽：类型图标 + 字段名 + 描述角标（钉钉 AI 多维表格样式，hover 显示字段描述） -->
        <template #fieldHeader="{ column }">
          <span
            class="bitable-th"
            :style="columnFillColor(column) ? { background: columnFillColor(column)!, height: '100%', display: 'flex' } : undefined"
          >
            <i
              v-if="getFieldTypeIcon(column)"
              class="bitable-th__type"
              :class="getFieldTypeIcon(column)!.icon"
              :style="{ color: getFieldTypeIcon(column)!.color }"
            />
            <span class="bitable-th__title">{{ column.title }}</span>
            <el-tooltip
              v-if="getFieldDescription(column)"
              :content="getFieldDescription(column)"
              placement="top"
            >
              <!-- 内联 SVG：任意缩放都锐利，替代字体图标发糊的问题 -->
              <svg
                class="bitable-th__badge"
                viewBox="0 0 16 16"
                width="14"
                height="14"
                fill="none"
                aria-hidden="true"
              >
                <circle cx="8" cy="8" r="6.6" stroke="currentColor" stroke-width="1.3" />
                <rect x="7.25" y="6.9" width="1.5" height="4.6" rx="0.75" fill="currentColor" />
                <circle cx="8" cy="4.6" r="0.95" fill="currentColor" />
              </svg>
            </el-tooltip>
          </span>
        </template>
        <!-- 表头末尾「+ 字段」幽灵列（钉钉布局）：点击整格即添加字段 -->
        <template #addFieldHeader>
          <span class="bitable-add-field" title="添加字段" @click.stop="emit('addField')">
            <i class="ri-add-line" />
            <span>字段</span>
          </span>
        </template>
      </vxe-grid>
    </div>
    <!-- 空表引导（走查 P1-4）：行动点前置 + 常用字段一键创建 -->
    <div v-else-if="!loading" class="grid-empty-guide">
      <div class="grid-empty-guide__icon"><i class="ri-table-line" /></div>
      <p class="grid-empty-guide__title">这张表还没有字段</p>
      <p class="grid-empty-guide__hint">字段就是表格的列，添加后即可开始录入数据</p>
      <el-button type="primary" class="grid-empty-guide__cta" @click="emit('addField')">
        <i class="ri-add-line" style="margin-right: 4px" />添加第一个字段
      </el-button>
      <div class="grid-empty-guide__quick">
        <span class="grid-empty-guide__quick-label">常用字段</span>
        <button
          v-for="q in QUICK_FIELDS"
          :key="q.type"
          class="grid-empty-guide__chip"
          type="button"
          @click="emit('addQuickField', { fieldType: q.type, name: q.name })"
        >
          <i :class="q.icon" :style="{ color: q.color }" />{{ q.name }}
        </button>
      </div>
    </div>
    <!-- 表尾 ⊕ 行：添加记录，⊕ 圆形图标与行号列对齐（钉钉布局） -->
    <div v-if="visibleFieldCount > 0" class="grid-add-row" title="添加一行" @click="emit('rowInsert')">
      <span class="grid-add-row__plus"><i class="ri-add-circle-line" /></span>
    </div>

    <!-- 批量操作浮层：勾选行后浮现（钉钉多维表格同款交互） -->
    <transition name="selection-bar">
      <div v-if="selectedCount > 0" class="grid-selection-bar">
        <span class="grid-selection-bar__count">
          已选 <b>{{ selectedCount }}</b> 行
        </span>
        <el-button size="small" type="danger" plain @click="handleBatchDelete">
          <i class="ri-delete-bin-line" style="margin-right: 4px" />批量删除
        </el-button>
        <el-button size="small" @click="clearSelection">取消选择</el-button>
      </div>
    </transition>

    <!-- 自定义行高弹层（右键菜单 → 行距设置 → 自定义） -->
    <teleport to="body">
      <div
        v-if="rowHeightMenuVisible"
        class="row-height-popover"
        @keydown.esc="rowHeightMenuVisible = false"
      >
        <div class="row-height-popover__mask" @click="rowHeightMenuVisible = false" />
        <div class="row-height-popover__panel">
          <div class="row-height-popover__title">自定义行高（px）</div>
          <el-input-number
            v-model="rowHeightDraft"
            :min="28"
            :max="200"
            :step="4"
            controls-position="right"
            style="width: 100%"
          />
          <div class="row-height-popover__footer">
            <el-button size="small" @click="rowHeightMenuVisible = false">取消</el-button>
            <el-button size="small" type="primary" @click="confirmRowHeight">应用</el-button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 富文本单元格编辑弹窗（点击富文本单元格打开） -->
    <RichTextEditDialog
      :visible="richTextEdit.visible"
      :field-name="richTextEdit.fieldName"
      :initial-value="richTextEdit.initialValue"
      :placeholder="richTextEdit.placeholder"
      @cancel="richTextEdit.visible = false"
      @save="handleRichTextSave"
    />

    <!-- 只读单元格轻提示：跟随鼠标浮出，自动消隐（不用弹框/顶部消息） -->
    <teleport to="body">
      <transition name="bitable-readonly-hint">
        <div
          v-if="readonlyHint.visible"
          class="bitable-readonly-hint"
          :style="{ left: `${readonlyHint.x}px`, top: `${readonlyHint.y}px` }"
        >
          <i class="ri-forbid-line" />
          <span>「{{ readonlyHint.fieldName }}」为只读字段，无法编辑</span>
        </div>
      </transition>
    </teleport>

    <!-- 单元格内容截断浮框：悬停显示完整内容（锚定单元格，可移入浮框继续阅读/滚动） -->
    <!-- 不加 Transition：快速显隐切换时 leave 动画可能丢失 transitionend 导致幽灵元素常驻 -->
    <teleport to="body">
      <div
        v-if="cellTooltip.visible"
        class="bitable-cell-tooltip"
        :style="{ left: `${cellTooltip.x}px`, top: `${cellTooltip.y}px`, maxWidth: `${cellTooltip.maxWidth}px` }"
        @mouseenter="cancelCellTooltipHide"
        @mouseleave="handleCellMouseLeave"
        @mousedown="hideCellTooltip"
      >{{ cellTooltip.text }}</div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BitableField, BitableRecord, BitableTable, ViewConfig } from '@/types/bitable'
import { isFieldReadonly, validateFieldValue } from '@/utils/bitableFieldConfig'
import { isRichTextEmpty } from '@/utils/bitableRichText'
import { useUserStore } from '@/stores/modules/user'
import { allUsers, ensureUsersLoaded, filterUsersByScope, type UserOption } from '@/composables/useUserOptions'
import RichTextEditDialog from './RichTextEditDialog.vue'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
  /** 当前视图配置（整列填色 columnColors / 冻结列 frozenFieldIds 等） */
  viewConfig?: ViewConfig | null
  /** 行号列起始索引（分页时传入 (page-1)*pageSize，让行号跨页续号） */
  seqStartIndex?: number
}>()

/** 空表快捷创建的常用字段（走查 P1-4：新用户一键加列，降低起步成本；主色随外观主题色联动） */
const QUICK_FIELDS: { type: string; name: string; icon: string; color: string }[] = [
  { type: 'text', name: '文本', icon: 'ri-text', color: 'var(--color-primary)' },
  { type: 'number', name: '数字', icon: 'ri-hashtag', color: '#E8A33D' },
  { type: 'date', name: '日期', icon: 'ri-calendar-line', color: '#50B987' },
  { type: 'single_select', name: '单选', icon: 'ri-radio-button-line', color: '#7A5AF8' },
  { type: 'user', name: '人员', icon: 'ri-user-3-line', color: 'var(--color-primary)' },
]

// ==================== 人员字段可选项（系统用户列表） ====================
const userStore = useUserStore()

/** 按字段属性 userScope 过滤后的可选项 */
function scopedUserOptions(field: BitableField): UserOption[] {
  return filterUsersByScope(allUsers.value, field, userStore.userInfo?.id ?? null)
}

// 有人员字段时拉取用户列表（共享缓存，多入口只发一次请求）
watch(
  () => props.fields,
  (fields) => {
    if (fields.some((f) => f.fieldType === 'user')) {
      ensureUsersLoaded()
    }
  },
  { immediate: true },
)

const emit = defineEmits<{
  cellChange: [data: { rowId: number; fieldId: number; newValue: unknown }]
  rowInsert: [data?: { position: 'above' | 'below'; rowId: number }]
  rowDelete: [rowId: number]
  /** 复制行（右键菜单） */
  rowCopy: [rowId: number]
  /** 复选选区变化：当前勾选的记录 ID 列表 */
  selectionChange: [rowIds: number[]]
  /** 批量删除已勾选行（确认弹窗已在 GridView 内完成） */
  rowsDelete: [rowIds: number[]]
  renameField: [fieldId: number]
  editField: [fieldId: number]
  cloneField: [fieldId: number]
  hideField: [fieldId: number]
  deleteField: [fieldId: number]
  insertField: [data: { position: 'left' | 'right'; fieldId: number }]
  sortField: [data: { fieldId: number; direction: 'asc' | 'desc' }]
  fillColumnColor: [data: { fieldId: number; color: string }]
  freezeToLeft: [fieldId: number]
  groupByField: [fieldId: number]
  filterByField: [fieldId: number]
  createElement: [fieldId: number]
  setRemind: [data: { fieldId: number; mode: 'daily' | 'weekly' | 'off' }]
  addField: []
  addQuickField: [data: { fieldType: string; name: string }]
  rowHeightChange: [height: number]
  colResize: [data: { fieldId: number; width: number }]
  headerDragend: [fields: { fieldId: number; newIndex: number }[]]
  aiFillColumn: [fieldId: number]
  aiClassifyColumn: [fieldId: number]
  aiSummarizeColumn: [fieldId: number]
  convertToAiField: [fieldId: number]
}>()

const gridRef = ref<any>()
const gridContainerRef = ref<HTMLElement | null>(null)

// ------------------------------------------------------------------
// 行复选 + 批量操作
// 序号列合并复选：表头全选框；行内 hover 序号变复选框（钉钉交互）
// 自维护 checkedIds 集合（跨页保留，不依赖 vxe checkbox 列）
// ------------------------------------------------------------------

/** 当前勾选的记录 ID（seqCell 插槽 + 批量浮层共用） */
const selectedRowIds = ref<number[]>([])
const selectedCount = computed(() => selectedRowIds.value.length)
const checkedIdSet = computed(() => new Set(selectedRowIds.value))

/** 表头全选态：当前页全部勾选 = 全选；部分 = 半选 */
const isAllSelected = computed(() => {
  if (!recordData.value.length) return false
  return recordData.value.every((row) => checkedIdSet.value.has(Number(row._recordId)))
})
const isIndeterminate = computed(() => {
  if (!recordData.value.length) return false
  const hit = recordData.value.filter((row) => checkedIdSet.value.has(Number(row._recordId))).length
  return hit > 0 && hit < recordData.value.length
})

function isRowChecked(row: any): boolean {
  return checkedIdSet.value.has(Number(row._recordId))
}

/** 勾选/取消单行 */
function toggleRowSelection(row: any) {
  const id = Number(row._recordId)
  if (Number.isNaN(id)) return
  const next = checkedIdSet.value.has(id)
    ? selectedRowIds.value.filter((v) => v !== id)
    : [...selectedRowIds.value, id]
  setSelection(next)
}

/** 表头全选/取消全选（作用于当前页全部行） */
function toggleAllSelection() {
  const allChecked = isAllSelected.value
  const pageIds = recordData.value.map((row) => Number(row._recordId)).filter((id) => !Number.isNaN(id))
  const next = allChecked
    ? selectedRowIds.value.filter((id) => !pageIds.includes(id))
    : Array.from(new Set([...selectedRowIds.value, ...pageIds]))
  setSelection(next)
}

function setSelection(ids: number[]) {
  selectedRowIds.value = ids
  emit('selectionChange', ids)
}

/** 勾选行高亮（淡蓝底，钉钉选中行样式） */
function rowClassName({ row }: { row: Record<string, any> }) {
  return checkedIdSet.value.has(Number(row._recordId)) ? 'row--checked' : ''
}

/** 清除勾选（浮层「取消」/ 数据重载后） */
function clearSelection() {
  setSelection([])
}

function handleBatchDelete() {
  if (selectedRowIds.value.length === 0) return
  const count = selectedRowIds.value.length
  ElMessageBox.confirm(`确定删除选中的 ${count} 行吗？删除后不可恢复。`, '批量删除', {
    confirmButtonText: `删除 ${count} 行`,
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      emit('rowsDelete', [...selectedRowIds.value])
      clearSelection()
    })
    .catch(() => {})
}

// 右键菜单配置（钉钉多维表格风格：每行一项 + 分组分隔线 + 「设置提醒」子菜单）
// prefixIcon 用 vxe 内置图标；整列填色/冻结等代码在 handleMenuClick 中处理
// 表头菜单抽成常量：右键「+ 字段」幽灵列 / 行号列时整组换成「添加字段」单项（handleCellMenu）
const HEADER_MENU_OPTIONS: any[][] = [
      [
        { code: 'editField', name: '编辑字段', prefixIcon: 'vxe-icon-edit' },
        {
          code: 'fillColumnColor', name: '整列填色', prefixIcon: 'vxe-icon-repeat',
          children: [
            { code: 'fillColor_gray', name: '默认灰', prefixIcon: 'menu-swatch menu-swatch--gray' },
            { code: 'fillColor_blue', name: '蓝色', prefixIcon: 'menu-swatch menu-swatch--blue' },
            { code: 'fillColor_green', name: '绿色', prefixIcon: 'menu-swatch menu-swatch--green' },
            { code: 'fillColor_orange', name: '橙色', prefixIcon: 'menu-swatch menu-swatch--orange' },
            { code: 'fillColor_purple', name: '紫色', prefixIcon: 'menu-swatch menu-swatch--purple' },
            { code: 'fillColor_none', name: '清除填色', prefixIcon: 'vxe-icon-close' },
          ],
        },
        { code: 'hideCol', name: '隐藏列', prefixIcon: 'vxe-icon-eye-fill' },
        { code: 'freezeToLeft', name: '冻结至此列', prefixIcon: 'vxe-icon-lock' },
        {
          // 父项不设 code：vxe 规定无 code 的父项仅展开子菜单，不会派发 menu-click
          name: '设置提醒', prefixIcon: 'vxe-icon-bell',
          suffixConfig: { content: '新能力' },
          children: [
            { code: 'remindDaily', name: '每天提醒' },
            { code: 'remindWeekly', name: '每周提醒' },
            { code: 'remindOff', name: '关闭提醒' },
          ],
        },
      ],
      [
        { code: 'createElement', name: '创建编辑', prefixIcon: 'vxe-icon-file' },
      ],
      [
        { code: 'cloneCol', name: '复制字段', prefixIcon: 'vxe-icon-copy' },
        { code: 'insertColLeft', name: '向左插入列', prefixIcon: 'vxe-icon-arrow-left' },
        { code: 'insertColRight', name: '向右插入列', prefixIcon: 'vxe-icon-arrow-right' },
      ],
      [
        { code: 'sortAsc', name: '升序', prefixIcon: 'vxe-icon-sort-asc' },
        { code: 'sortDesc', name: '降序', prefixIcon: 'vxe-icon-sort-desc' },
      ],
      [
        { code: 'groupByField', name: '按字段分组', prefixIcon: 'vxe-icon-funnel-clear' },
      ],
      [
        { code: 'filterByField', name: '按字段筛选', prefixIcon: 'vxe-icon-funnel' },
      ],
      [
        { code: 'aiFillColumn', name: 'AI 智能填充此列', prefixIcon: 'vxe-icon-edit' },
        { code: 'aiClassifyColumn', name: 'AI 自动分类', prefixIcon: 'vxe-icon-repeat' },
        { code: 'aiSummarizeColumn', name: 'AI 自动摘要', prefixIcon: 'vxe-icon-file' },
      ],
      [
        { code: 'convertToAiField', name: '转为 AI 字段', prefixIcon: 'vxe-icon-setting' },
      ],
      [
        {
          // 行距设置：仅对当前数据表生效，持久化到 bitable_tables.row_height
          name: '行距设置', prefixIcon: 'vxe-icon-question-circle',
          children: [
            { code: 'rowHeightCompact', name: '紧凑（24px）' },
            { code: 'rowHeightStandard', name: '标准（32px）' },
            { code: 'rowHeightLoose', name: '宽松（48px）' },
            { code: 'rowHeightCustom', name: '自定义…' },
          ],
        },
      ],
      [
        { code: 'deleteCol', name: '删除字段', prefixIcon: 'vxe-icon-delete' },
      ],
]

/** 右键「+ 字段」幽灵列 / 行号列时的替代表头菜单：只提供「添加字段」 */
const HEADER_MENU_ADD_FIELD: any[][] = [
  [{ code: 'addFieldMenu', name: '添加字段', prefixIcon: 'vxe-icon-add' }],
]

const menuConfig = reactive({
  enabled: true,
  trigger: 'cell' as const,
  body: {
    options: [
      [
        { code: 'insertRowAbove', name: '在上方插入行', prefixIcon: 'vxe-icon-arrow-up' },
        { code: 'insertRowBelow', name: '在下方插入行', prefixIcon: 'vxe-icon-arrow-down' },
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
    options: HEADER_MENU_OPTIONS,
  },
})

// 右键菜单打开时记录当前列，用于动态渲染「按「字段名」分组/筛选」文案与填色子菜单选中态
const currentMenuColumn = ref<any>(null)

/** 当前右键菜单针对的字段（无则返回 null） */
const currentMenuField = computed<BitableField | null>(() => {
  const fieldId = Number(currentMenuColumn.value?.field)
  if (Number.isNaN(fieldId)) return null
  return props.fields.find((f) => f.id === fieldId) ?? null
})

/**
 * 菜单渲染前动态修正文案（按「字段名」分组/筛选）与填色子菜单。
 * vxe 的 menuConfig.header.options 是响应式的：在 cell-menu 回调里同步改 reactive 配置，
 * 菜单弹出时即渲染最新内容。
 * 表头与表体共用：header-cell-menu / cell-menu 都绑到本函数。
 * 右键「+ 字段」幽灵列或行号列（无有效字段）时，表头菜单整组换成「添加字段」单项。
 */
function handleCellMenu({ column }: any) {
  currentMenuColumn.value = column
  const field = currentMenuField.value
  if (field) {
    // 字段列：恢复完整字段菜单（同一引用无需重建），并刷新动态文案
    if (menuConfig.header.options !== HEADER_MENU_OPTIONS) {
      menuConfig.header.options = HEADER_MENU_OPTIONS
    }
    const groupItem = menuConfig.header.options.flat().find((item: any) => item.code === 'groupByField')
    const filterItem = menuConfig.header.options.flat().find((item: any) => item.code === 'filterByField')
    ;(groupItem as any).name = `按「${field.name}」分组`
    ;(filterItem as any).name = `按「${field.name}」筛选`
  } else {
    // 幽灵列 / 行号列：只给「添加字段」
    menuConfig.header.options = HEADER_MENU_ADD_FIELD
  }
}

// 构建列配置
const ADD_FIELD_COL_FIELD = '__add_field__'
/** 可见字段数：决定渲染网格还是空表引导（tableColumns 恒含 seq + 幽灵列，不可作为判断依据） */
const visibleFieldCount = computed(() => props.fields.filter((f) => f.permission !== 'hidden').length)
const tableColumns = computed(() => {
  const frozenIds = new Set(props.viewConfig?.frozenFieldIds ?? [])
  // 视图级列宽覆盖（拖拽列边框后持久化；无记录时用字段默认宽度）
  const fieldWidths = props.viewConfig?.fieldWidths || {}
  // 字段级权限为 hidden 的列整列不渲染（后端同时会剔除这些字段的单元格值）
  const cols = props.fields
    .filter((field) => field.permission !== 'hidden')
    .map((field) => {
      // 只读列（权限只读/隐藏、类型只读、add_only）不配编辑渲染器，等价于系统只读字段
      const isReadonly = !isCellEditable(field)
      const config = field.config || {}
      const column: Record<string, any> = {
        field: String(field.id),
        title: field.name,
        width: fieldWidths[field.id] || field.width || 150,
        minWidth: 80,
        // 钉钉多维表格风格：表头不显示排序箭头，排序走字段菜单
        sortable: false,
        showOverflow: true,
        // 表头插槽：类型图标 + 字段名 + 描述角标（hover 弹出字段描述）
        slots: { header: 'fieldHeader' },
      }
      // 冻结列：该列及之前所有列固定左侧
      if (frozenIds.has(field.id)) {
        column.fixed = 'left'
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
            // 开启「包含时间」时提交 yyyy-MM-dd HH:mm:ss，否则只提交日期
            const withTime = !!config.withTime
            const valueFormat = withTime ? 'yyyy-MM-dd HH:mm:ss' : 'yyyy-MM-dd'
            column.editRender = {
              // name 指向 BitableDate：vxe 在「列有 editRender」时用编辑渲染器的
              // renderTableCell 做展示，用 renderTableEdit 进编辑态。
              // 直接写 VxeDatePicker 的话，dateFormat / withTime 只会作用于选择器，
              // 单元格展示仍是原生纯文本（见 bitableCellRenderers.ts 的 EDITOR_BINDINGS）。
              name: 'BitableDate',
              config,
              props: {
                placeholder: config.formPlaceholder || '',
                type: withTime ? 'datetime' : 'date',
                valueFormat,
                labelFormat: valueFormat,
                popupConfig: { transfer: true },
                clearable: true,
                editable: true,
              },
            }
            // 展示用自定义只读渲染器（文本 + 图标），不把 VxeDatePicker 当作 display 渲染器，
            // 以避免在大量单元格中渲染完整日期选择器引发的渲染异常/卡顿
            column.cellRender = { name: 'BitableDate', config }
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
              name: 'BitableSelectTag',
              config,
              options,
              optionProps: { label: 'label', value: 'value' },
              props: {
                placeholder: config.formPlaceholder || '',
                immediate: true,
                autoClose: !isMulti,
                clearable: true,
                popupConfig: { transfer: true },
                ...(isMulti ? { multiple: true } : {}),
              },
            }
            break
          }
          case 'progress': {
            // 进度条展示 + 数字编辑（0-100）
            column.cellRender = { name: 'BitableProgress', config }
            column.editRender = {
              name: 'BitableProgress',
              config,
              props: {
                type: 'integer',
                min: 0,
                max: 100,
                placeholder: config.formPlaceholder || '',
                align: 'right',
              },
            }
            break
          }
          case 'rating': {
            // 星级展示 + VxeRate 编辑
            const max = Number(config.maxRating) || 5
            column.cellRender = { name: 'BitableRate', config }
            column.editRender = {
              name: 'BitableRate',
              config,
              props: {
                max,
                readonly: false,
                ...(config.allowHalf ? { allowHalf: true } : {}),
              },
            }
            break
          }
          case 'check':
          case 'checkbox': {
            // 复选框：只配置 cellRender，编辑通过 cell-click 直接 toggle（避免编辑态翻转歧义）
            column.cellRender = { name: 'BitableCheckbox', config }
            column.editRender = null
            break
          }
          case 'number': {
            column.cellRender = { name: 'BitableNumber', config }
            column.editRender = {
              name: 'BitableNumber',
              config,
              props: {
                type: 'float',
                placeholder: config.formPlaceholder || '',
                digits: config.precision ?? 0,
                align: 'right',
                ...(config.min != null ? { min: config.min } : {}),
                ...(config.max != null ? { max: config.max } : {}),
              },
            }
            break
          }
          case 'currency': {
            column.cellRender = { name: 'BitableCurrency', config }
            column.editRender = {
              name: 'BitableCurrency',
              config,
              props: {
                type: 'float',
                placeholder: config.formPlaceholder || '',
                digits: config.precision ?? 2,
                align: 'right',
                ...(config.min != null ? { min: config.min } : {}),
                ...(config.max != null ? { max: config.max } : {}),
              },
            }
            break
          }
          case 'url': {
            column.cellRender = { name: 'BitableUrl', config }
            column.editRender = { name: 'BitableUrl', config, props: { placeholder: config.formPlaceholder || '' } }
            break
          }
          case 'phone': {
            column.cellRender = { name: 'BitablePhone', config }
            column.editRender = { name: 'BitablePhone', config, props: { placeholder: config.formPlaceholder || '' } }
            break
          }
          case 'attachment': {
            column.cellRender = { name: 'BitableAttachment', config }
            // 附件走记录详情弹框上传，网格内不做内联编辑
            column.editRender = null
            break
          }
          case 'location': {
            // 地理位置是结构化值（{name, address, lat, lng}），若给文本编辑器，
            // String(对象) 会写回库变成 "[object Object]"，因此网格内只读展示
            column.cellRender = { name: 'BitableLocation', config }
            column.editRender = null
            break
          }
          case 'date_range': {
            // 日期范围同样是结构化值（{start, end}），网格内只读展示，编辑走记录详情弹框
            column.cellRender = { name: 'BitableDateRange' }
            column.editRender = null
            break
          }
          case 'user': {
            // 人员：可选项 = 系统用户列表。展示态头像+姓名，编辑态 VxeSelect 下拉。
            // 值形态：单人选 id（数字），多人选 id 数组；保存时由 handleEditClosed
            // 规整为 valueJson=[{id,name}] + valueText=姓名串。
            const userOptions = scopedUserOptions(field)
            const isUserMulti = config.userMode === 'multiple'
            column.cellRender = { name: 'BitableUser', config }
            column.editRender = {
              name: 'VxeSelect',
              config,
              options: userOptions.map((u) => ({ label: u.realName, value: u.id })),
              optionProps: { label: 'label', value: 'value' },
              props: {
                placeholder: config.formPlaceholder || '选择人员',
                immediate: true,
                autoClose: !isUserMulti,
                clearable: true,
                filterable: true,
                popupConfig: { transfer: true },
                ...(isUserMulti ? { multiple: true } : {}),
              },
            }
            break
          }
          case 'group':
          case 'department':
          case 'link':
          case 'bidirectional_link': {
            // 关联 / 群组 / 部门是结构化值（id 数组或对象数组），若给文本编辑器，
            // handleEditClosed 会把 String(数组) 写回库（变成 "1,2"）从而破坏关联，
            // 因此网格内只读展示，编辑走记录详情弹框。
            column.cellRender = { name: 'BitableRelation' }
            column.editRender = null
            break
          }
          case 'email': {
            column.cellRender = { name: 'BitableEmail', config }
            column.editRender = { name: 'BitableEmail', config, props: { placeholder: config.formPlaceholder || '' } }
            break
          }
          case 'rich_text': {
            // 富文本：网格内只读预览（剥标签单行文本），点击单元格打开编辑弹窗
            column.cellRender = { name: 'BitableRichText', config }
            column.editRender = null
            break
          }
          case 'text':
          default: {
            column.cellRender = { name: 'BitableText', config }
            column.editRender = {
              name: 'BitableText',
              config,
              props: {
                placeholder: config.formPlaceholder || '',
                ...(config.maxLength ? { maxlength: config.maxLength } : {}),
              },
            }
            break
          }
        }
      } else {
        // 只读列也按类型展示格式化结果，只是不提供编辑器
        switch (field.fieldType) {
          case 'date':
            column.cellRender = { name: 'BitableDate', config }
            break
          case 'number':
            column.cellRender = { name: 'BitableNumber', config }
            break
          case 'currency':
            column.cellRender = { name: 'BitableCurrency', config }
            break
          case 'rating':
            column.cellRender = { name: 'BitableRate', config }
            break
          case 'progress':
            column.cellRender = { name: 'BitableProgress', config }
            break
          case 'checkbox':
          case 'check':
            column.cellRender = { name: 'BitableCheckbox', config }
            break
          case 'url':
            column.cellRender = { name: 'BitableUrl', config }
            break
          case 'phone':
            column.cellRender = { name: 'BitablePhone', config }
            break
          case 'email':
            column.cellRender = { name: 'BitableEmail', config }
            break
          case 'rich_text':
            column.cellRender = { name: 'BitableRichText', config }
            break
          case 'attachment':
            column.cellRender = { name: 'BitableAttachment', config }
            break
          case 'single_select':
          case 'multi_select':
          case 'process':
            column.cellRender = {
              name: 'BitableSelectTag',
              options: buildSelectOptions(),
              optionProps: { label: 'label', value: 'value' },
              props: { multiple: isMulti },
            }
            break
          case 'location':
            column.cellRender = { name: 'BitableLocation', config }
            break
          case 'date_range':
            column.cellRender = { name: 'BitableDateRange' }
            break
          case 'user':
            column.cellRender = { name: 'BitableUser', config }
            break
          case 'group':
          case 'department':
          case 'link':
          case 'bidirectional_link':
            column.cellRender = { name: 'BitableRelation' }
            break
          default:
            column.cellRender = { name: 'BitableText', config }
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

  // 序号列（钉钉布局：表头为全选框；行内 hover 时序号变复选框，勾选后常显蓝色勾）
  cols.unshift({
    type: 'seq',
    width: 48,
    fixed: 'left',
    align: 'center',
    headerAlign: 'center',
    resizable: false,
    sortable: false,
    slots: {
      // 表头：全选框（替代原独立复选列的表头）
      header: 'seqSelectHeader',
      // 行内：默认显示序号，hover 显示复选框，已勾选常显
      default: 'seqCell',
    },
  })

  // 表头末尾「+ 字段」幽灵列（钉钉布局）：点击表头整格添加字段；表体单元格为空白
  cols.push({
    field: ADD_FIELD_COL_FIELD,
    title: '',
    width: 150,
    resizable: false,
    sortable: false,
    showOverflow: true,
    slots: { header: 'addFieldHeader' },
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
      // 隐藏字段不出现在列里，也不必构建单元格数据
      if (field.permission === 'hidden') {
        return
      }
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
        case 'department': {
          // 部门值是 id 数组（{valueJson:[3], valueText:'研发部'}）。
          // 直接渲染 id 数组只会显示 "3"，所以优先用可读名称。
          value = cell?.displayText || cell?.valueText || cell?.valueJson || value
          break
        }
        case 'attachment':
        case 'location':
        case 'date_range':
        case 'user':
        case 'group':
        case 'link':
        case 'bidirectional_link': {
          // 结构化值：保留原始 JSON（数组 / 对象），交给对应渲染器解析。
          // 不能落到 default 分支，否则 String(对象) 会变成 "[object Object]"
          value = cell?.valueJson ?? value
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

/** 上一轮记录 ID 快照（识别新增行用）；必须先于下方 immediate watch 声明（TDZ） */
let lastRecordIds = new Set<number>()
function snapshotRecordIds() {
  lastRecordIds = new Set(props.records.map((r) => r.id))
}

// 仅在 records / fields 引用变化（重载、加列等）时重建；编辑期间 setCellValue 就地修改不触发
watch(
  () => [props.records, props.fields],
  () => {
    const prevIds = lastRecordIds
    buildRecordData()
    snapshotRecordIds()
    // 新增行自动进入编辑态（走查 P1-3：点 + 后用户应直接输入，而不是面对一个空白行）
    const added = props.records.find((r) => !prevIds.has(r.id))
    if (added && prevIds.size > 0) focusNewRecord(added.id)
  },
  { immediate: true },
)

// 数据重载（翻页/切表/增删行后 recordData 重建）时同步清空复选，避免残留幽灵选区。
// 注意：必须放在 recordData 声明之后 —— watch 的 getter 注册时会立即执行一次（TDZ）。
watch(
  () => recordData.value,
  () => {
    if (selectedRowIds.value.length === 0) return
    selectedRowIds.value = []
    emit('selectionChange', [])
  },
)

/**
 * 让新行进入编辑态：滚动到该行、闪一次高亮渐隐（与占位行区分），并把第一个
 * 可编辑（非只读/隐藏）单元格设为激活编辑。失败（列全只读等）静默降级为仅滚动。
 */
function focusNewRecord(recordId: number) {
  nextTick(() => {
    const grid = gridRef.value
    if (!grid) return
    const row = (grid.getTableData?.().fullData || []).find(
      (r: Record<string, any>) => r._recordId === recordId,
    )
    if (!row) return
    // 新行高亮渐隐：先滚动到位，再给行 DOM 挂 flash class
    Promise.resolve(grid.scrollToRow(row))
      .then(() => {
        nextTick(() => {
          const rowid = grid.getRowid?.(row)
          const rowEl = gridContainerRef.value?.querySelector(
            `.vxe-body--row[rowid="${rowid}"]`,
          )
          if (rowEl) {
            rowEl.classList.add('bitable-row--flash')
            setTimeout(() => rowEl.classList.remove('bitable-row--flash'), 1600)
          }
        })
      })
      .catch(() => {})
    const firstEditable = props.fields.find((f) => isCellEditable(f))
    if (!firstEditable) return
    const column = grid.getColumns?.().find((c: any) => c.field === String(firstEditable.id))
    if (column && grid.setEditCell) {
      Promise.resolve(grid.setEditCell(row, column))
        .then(() => {
          // setEditCell 只渲染编辑输入框不聚焦（自定义渲染器无 autofocus 配置），
          // 手动把焦点移进去，用户点 + 后可直接打字
          nextTick(() => {
            const rowid = grid.getRowid?.(row)
            const input = gridContainerRef.value?.querySelector(
              `.vxe-body--row[rowid="${rowid}"] input, .vxe-body--row[rowid="${rowid}"] textarea`,
            ) as HTMLInputElement | HTMLTextAreaElement | null
            input?.focus()
          })
        })
        .catch(() => {})
    }
  })
}

/**
 * 网格内单元格可编辑判定：类型只读、权限只读/隐藏、add_only
 * （网格行全是已有记录，「仅新增可填」字段对已有记录一律不可改）都不可就地编辑。
 */
function isCellEditable(field?: BitableField | null): boolean {
  if (!field) return false
  return !isFieldReadonly(field) && field.permission !== 'add_only'
}

// ==================== 只读单元格轻提示（鼠标处浮层，自动消隐，不弹框） ====================

const readonlyHint = reactive({
  visible: false,
  x: 0,
  y: 0,
  fieldName: '',
})
let readonlyHintTimer: ReturnType<typeof setTimeout> | undefined

function showReadonlyHint(fieldName: string, event?: MouseEvent) {
  readonlyHint.fieldName = fieldName
  const mouseX = event?.clientX ?? window.innerWidth / 2
  const mouseY = event?.clientY ?? window.innerHeight / 2
  readonlyHint.x = Math.min(mouseX + 12, window.innerWidth - 250)
  readonlyHint.y = Math.min(mouseY + 16, window.innerHeight - 44)
  readonlyHint.visible = true
  if (readonlyHintTimer) clearTimeout(readonlyHintTimer)
  readonlyHintTimer = setTimeout(() => {
    readonlyHint.visible = false
  }, 1600)
}

/** vxe 编辑入口兜底：只读单元格不允许进入编辑态，鼠标处轻提示 */
function handleBeforeEdit(params: { column?: { field?: string | number }; $event?: MouseEvent }) {
  const fieldId = Number(params.column?.field)
  if (Number.isNaN(fieldId)) return false
  const field = props.fields.find((f) => f.id === fieldId)
  if (isCellEditable(field)) return true
  hideCellTooltip()
  showReadonlyHint(field?.name || '', params.$event)
  return false
}

// ==================== 单元格内容截断浮框（悬停显示完整内容） ====================

const cellTooltip = reactive({
  visible: false,
  x: 0,
  y: 0,
  maxWidth: 380,
  text: '',
})
let cellTooltipHideTimer: ReturnType<typeof setTimeout> | undefined

function cancelCellTooltipHide() {
  if (cellTooltipHideTimer) {
    clearTimeout(cellTooltipHideTimer)
    cellTooltipHideTimer = undefined
  }
}

function hideCellTooltip() {
  cancelCellTooltipHide()
  cellTooltip.visible = false
}

/**
 * 自定义渲染器的省略号发生在内层文本元素上，vxe 的 showOverflow 只查 .vxe-cell
 * 外层永远检测不到溢出，因此自建浮框：找单元格里第一个真实横向溢出的元素，
 * 以其渲染文本（innerText，即格式化后的完整显示值）作为浮框内容，锚定单元格。
 * 浮框可与鼠标交互（悬停保持、滚动阅读），因此离开单元格时延迟收起，
 * 给「单元格 → 浮框」的移动留出宽限，浮框 mouseenter 会取消收起。
 */
function handleCellMouseEnter(params: { row: Record<string, any>; column: any; $event?: Event }) {
  const { row, column } = params
  const fieldId = Number(column?.field)
  if (Number.isNaN(fieldId)) return
  const field = props.fields.find((f) => f.id === fieldId)
  if (!field) return

  const eventTarget = params.$event?.target
  const td = eventTarget instanceof Element ? eventTarget.closest('td') : null
  if (!td) return

  const wrapper = td.querySelector('.vxe-cell--wrapper') || td.querySelector('.vxe-cell')
  if (!wrapper) return
  const overflowing = [wrapper, ...Array.from(wrapper.querySelectorAll('*'))]
    .find(el => el.scrollWidth > el.clientWidth + 1)
  if (!overflowing) {
    hideCellTooltip()
    return
  }

  const text = ((overflowing as HTMLElement).innerText || overflowing.textContent || '').trim()
  if (!text) {
    hideCellTooltip()
    return
  }

  cancelCellTooltipHide()
  const rect = td.getBoundingClientRect()
  const maxWidth = Math.min(400, Math.max(220, window.innerWidth - rect.left - 24))
  cellTooltip.text = text
  cellTooltip.maxWidth = maxWidth
  // 优先显示在单元格下方（与单元格留 1px 重叠，鼠标可无死区移入浮框）；靠近底部时翻转到上方
  const estimatedHeight = Math.min(220, Math.ceil(Math.min(text.length * 14, maxWidth) / maxWidth) * 20 + 20)
  cellTooltip.x = Math.max(8, Math.min(rect.left, window.innerWidth - maxWidth - 8))
  cellTooltip.y = rect.bottom + estimatedHeight > window.innerHeight - 8
    ? Math.max(8, rect.top - estimatedHeight + 1)
    : rect.bottom - 1
  cellTooltip.visible = true
}

function handleCellMouseLeave() {
  // 延迟收起：鼠标可能正移入浮框（浮框 mouseenter 会取消），或快速换格（新格 mouseenter 会刷新）
  cancelCellTooltipHide()
  cellTooltipHideTimer = setTimeout(() => {
    cellTooltip.visible = false
  }, 180)
}

function handleCellClick(params: { row: Record<string, any>; column: any; $event?: Event }) {
  const { row, column } = params
  const clickEvent = params.$event instanceof MouseEvent ? params.$event : undefined
  if (column.field === '_action') return
  const rowId = row._recordId as number
  const fieldId = Number(column.field)
  if (Number.isNaN(fieldId)) return
  const field = props.fields.find((f) => f.id === fieldId)
  // 只读列（权限只读/隐藏、类型只读、add_only）禁止任何就地编辑，鼠标处轻提示
  if (!field || !isCellEditable(field)) {
    if (field) showReadonlyHint(field.name, clickEvent)
    return
  }
  // 可编辑单元格进入编辑态后不再显示截断浮框
  hideCellTooltip()
  // 复选框字段：点击直接 toggle，不走 vxe-table 编辑态
  if (field.fieldType === 'check' || field.fieldType === 'checkbox') {
    const raw = row[column.field]
    const checked = raw === true || raw === 'true' || raw === 'True' || raw === 1 || raw === '1'
    emit('cellChange', {
      rowId,
      fieldId,
      newValue: String(!checked),
    })
    return
  }
  // 富文本字段：网格行高放不下工具栏，点击打开编辑弹窗，保存走 cellChange 通道
  if (field.fieldType === 'rich_text') {
    richTextEdit.rowId = rowId
    richTextEdit.fieldId = fieldId
    richTextEdit.fieldName = field.name
    richTextEdit.initialValue = String(row[column.field] ?? '')
    richTextEdit.placeholder = field.config?.formPlaceholder || field.description || `请输入${field.name}`
    richTextEdit.visible = true
    return
  }
  // 其他字段：值变化由 editRender 触发，edit-closed 时统一 emit
}

// ==================== 富文本单元格编辑 ====================

const richTextEdit = reactive({
  visible: false,
  rowId: 0,
  fieldId: 0,
  fieldName: '',
  initialValue: '',
  placeholder: '',
})

function handleRichTextSave(html: string) {
  const field = props.fields.find((f) => f.id === richTextEdit.fieldId)
  if (field?.required && isRichTextEmpty(html)) {
    ElMessage.warning(`请填写「${field.name}」`)
    return
  }
  richTextEdit.visible = false
  const row = recordData.value.find((r) => r._recordId === richTextEdit.rowId)
  if (row) row[String(richTextEdit.fieldId)] = html
  emit('cellChange', {
    rowId: richTextEdit.rowId,
    fieldId: richTextEdit.fieldId,
    newValue: html,
  })
}

/** 表头角标：取字段描述（无描述时角标不渲染） */
function getFieldDescription(column: any): string {
  const fieldId = Number(column?.field)
  if (Number.isNaN(fieldId)) return ''
  const field = props.fields.find((f) => f.id === fieldId)
  return String(field?.description || '').trim()
}

// ==================== 字段类型图标（钉钉 AI 多维表格风格） ====================

/**
 * 每种字段类型一对 remixicon 图标 + 品牌色，表头一眼可辨类型。
 * 图标语义对齐钉钉多维表格：文本≈、单选◎、多选☰、日期📅、数字#、货币¥……
 * 主色系用 var(--color-primary) 随外观主题色联动；其余为类型语义色（双主题恒定）。
 */
const FIELD_TYPE_ICONS: Record<string, { icon: string; color: string }> = {
  text: { icon: 'ri-text', color: 'var(--color-primary)' },
  rich_text: { icon: 'ri-file-text-line', color: '#E8684A' },
  number: { icon: 'ri-hashtag', color: '#E8A33D' },
  date: { icon: 'ri-calendar-line', color: '#50B987' },
  single_select: { icon: 'ri-radio-button-line', color: '#7A5AF8' },
  multi_select: { icon: 'ri-list-check-2', color: '#7A5AF8' },
  process: { icon: 'ri-flow-chart', color: '#F5A623' },
  user: { icon: 'ri-user-3-line', color: 'var(--color-primary)' },
  group: { icon: 'ri-group-line', color: 'var(--color-primary)' },
  department: { icon: 'ri-organization-chart', color: 'var(--color-primary)' },
  check: { icon: 'ri-checkbox-line', color: '#50B987' },
  checkbox: { icon: 'ri-checkbox-line', color: '#50B987' },
  attachment: { icon: 'ri-attachment-line', color: '#E8A33D' },
  url: { icon: 'ri-link', color: '#33B8D9' },
  email: { icon: 'ri-mail-line', color: '#E8684A' },
  phone: { icon: 'ri-phone-line', color: '#50B987' },
  location: { icon: 'ri-map-pin-line', color: '#F06292' },
  currency: { icon: 'ri-currency-line', color: '#E8A33D' },
  progress: { icon: 'ri-dashboard-3-line', color: '#7A5AF8' },
  rating: { icon: 'ri-star-line', color: '#F5A623' },
  link: { icon: 'ri-share-forward-line', color: '#33B8D9' },
  bidirectional_link: { icon: 'ri-links-line', color: '#33B8D9' },
  rollup: { icon: 'ri-calculator-line', color: '#7A5AF8' },
  lookup: { icon: 'ri-search-eye-line', color: '#7A5AF8' },
  formula: { icon: 'ri-function-line', color: '#7A5AF8' },
  ai_text: { icon: 'ri-sparkling-2-line', color: '#E8684A' },
  ai_select: { icon: 'ri-sparkling-2-line', color: '#E8684A' },
  auto_number: { icon: 'ri-sort-asc', color: 'var(--color-text-tertiary)' },
  barcode: { icon: 'ri-qr-code-line', color: 'var(--color-muted-text)' },
  button: { icon: 'ri-cursor-line', color: '#F06291' },
  created_time: { icon: 'ri-time-line', color: 'var(--color-text-tertiary)' },
  modified_time: { icon: 'ri-time-line', color: 'var(--color-text-tertiary)' },
  last_modified_time: { icon: 'ri-time-line', color: 'var(--color-text-tertiary)' },
  created_user: { icon: 'ri-user-add-line', color: 'var(--color-text-tertiary)' },
  modified_user: { icon: 'ri-user-add-line', color: 'var(--color-text-tertiary)' },
  created_by: { icon: 'ri-user-add-line', color: 'var(--color-text-tertiary)' },
  modified_by: { icon: 'ri-user-add-line', color: 'var(--color-text-tertiary)' },
  date_range: { icon: 'ri-calendar-event-line', color: '#50B987' },
}

/** 表头类型图标：按列 field（即字段 ID）反查字段类型 */
function getFieldTypeIcon(column: any): { icon: string; color: string } | null {
  const fieldId = Number(column?.field)
  if (Number.isNaN(fieldId)) return null
  const field = props.fields.find((f) => f.id === fieldId)
  if (!field) return null
  return FIELD_TYPE_ICONS[field.fieldType] || { icon: 'ri-text', color: 'var(--color-primary)' }
}

// ==================== 行高（默认 32px，数据表可单独设置） ====================

/** 默认行高：32px（wolai/飞书风格紧凑基准） */
const DEFAULT_ROW_HEIGHT = 32
/** 行高可设置范围（与后端校验一致） */
const ROW_HEIGHT_MIN = 20
const ROW_HEIGHT_MAX = 200

/** 生效行高：数据表设置了 rowHeight 用表级值，否则用默认 32 */
const effectiveRowHeight = computed(() => {
  const h = Number(props.table?.rowHeight)
  return Number.isFinite(h) && h >= ROW_HEIGHT_MIN && h <= ROW_HEIGHT_MAX ? h : DEFAULT_ROW_HEIGHT
})

/** 行距设置下拉可见性 + 待设置值 */
const rowHeightMenuVisible = ref(false)
const rowHeightDraft = ref(DEFAULT_ROW_HEIGHT)

function openRowHeightMenu() {
  rowHeightDraft.value = effectiveRowHeight.value
  rowHeightMenuVisible.value = true
}

function applyRowHeightPreset(value: number) {
  rowHeightDraft.value = value
  emit('rowHeightChange', value)
  rowHeightMenuVisible.value = false
}

function confirmRowHeight() {
  const h = Math.round(Number(rowHeightDraft.value))
  if (!Number.isFinite(h) || h < ROW_HEIGHT_MIN || h > ROW_HEIGHT_MAX) {
    ElMessage.warning(`行高需在 ${ROW_HEIGHT_MIN}-${ROW_HEIGHT_MAX} 之间`)
    return
  }
  emit('rowHeightChange', h)
  rowHeightMenuVisible.value = false
}

/** 表头整列填色：取视图 config.columnColors 中该列的颜色（无则返回 null 不着色） */
function columnFillColor(column: any): string | null {
  const fieldId = Number(column?.field)
  if (Number.isNaN(fieldId)) return null
  const colors = props.viewConfig?.columnColors || {}
  return colors[fieldId] || null
}

/**
 * 滚动定位到指定记录行（新增记录后由 editor 调用）。
 * 记录在当前页数据里时用 vxe scrollToRow 精确滚动；找不到（如分页外）则滚到表尾。
 */
function scrollToRecord(recordId: number) {
  nextTick(() => {
    const grid = gridRef.value
    if (!grid) return
    const target = (grid.getTableData?.().fullData || []).find(
      (row: Record<string, any>) => row._recordId === recordId,
    )
    if (target && typeof grid.scrollToRow === 'function') {
      Promise.resolve(grid.scrollToRow(target)).catch(() => {})
    } else {
      // 目标行不在当前页：滚动到底部的「添加记录」占位行
      const wrap = gridContainerRef.value?.querySelector('.vxe-table--body-wrapper')
      wrap?.scrollTo({ top: (wrap as HTMLElement).scrollHeight })
    }
  })
}

defineExpose({ scrollToRecord, openRowHeightMenu })

function handleMenuClick({ menu, row, column }: any) {
  // 从右键菜单所在列解析字段 ID（列头与列体菜单共用此逻辑）
  const resolveFieldId = (): number | null => {
    if (!column?.field) return null
    const id = Number(column.field)
    return Number.isNaN(id) ? null : id
  }
  const code: string = menu.code || ''
  // 整列填色子菜单：code 形如 fillColor_blue / fillColor_none
  if (code.startsWith('fillColor_')) {
    const fieldId = resolveFieldId()
    if (fieldId !== null) emit('fillColumnColor', { fieldId, color: code.replace('fillColor_', '') })
    return
  }
  // 设置提醒子菜单
  if (code.startsWith('remind')) {
    const fieldId = resolveFieldId()
    if (fieldId !== null) {
      const mode = code === 'remindDaily' ? 'daily' : code === 'remindWeekly' ? 'weekly' : 'off'
      emit('setRemind', { fieldId, mode })
    }
    return
  }
  switch (code) {
    // 右键「+ 字段」幽灵列 / 行号列的替代表头菜单
    case 'addFieldMenu':
      emit('addField')
      break
    case 'editField':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('editField', fieldId)
      }
      break
    case 'renameCol':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('renameField', fieldId)
      }
      break
    case 'cloneCol':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('cloneField', fieldId)
      }
      break
    case 'hideCol':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('hideField', fieldId)
      }
      break
    case 'deleteCol':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('deleteField', fieldId)
      }
      break
    // 向左 / 向右插入列
    case 'insertColLeft':
    case 'insertColRight':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) {
          emit('insertField', { position: code === 'insertColLeft' ? 'left' : 'right', fieldId })
        }
      }
      break
    // 升序 / 降序（持久化到视图 sortConfig）
    case 'sortAsc':
    case 'sortDesc':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) {
          emit('sortField', { fieldId, direction: code === 'sortAsc' ? 'asc' : 'desc' })
        }
      }
      break
    // 冻结至此列（含该列及之前的所有列）
    case 'freezeToLeft':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('freezeToLeft', fieldId)
      }
      break
    // 按「字段」分组 / 筛选
    case 'groupByField':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('groupByField', fieldId)
      }
      break
    case 'filterByField':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('filterByField', fieldId)
      }
      break
    // 创建编辑：打开字段配置抽屉（与字段配置入口一致，聚焦编辑）
    case 'createElement':
      {
        const fieldId = resolveFieldId()
        if (fieldId !== null) emit('editField', fieldId)
      }
      break
    case 'fillColumnColor':
    case 'remind':
      // 父级菜单项本身不带操作（仅展开子菜单）
      break
    case 'insertRowAbove':
      emit('rowInsert', { position: 'above', rowId: row._recordId })
      break
    case 'insertRowBelow':
      emit('rowInsert', { position: 'below', rowId: row._recordId })
      break
    case 'copyRow':
      if (row && typeof row._recordId === 'number' && !Number.isNaN(row._recordId)) {
        emit('rowCopy', row._recordId)
      }
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
    // 行距设置（当前数据表级，持久化到 bitable_tables.row_height）
    case 'rowHeightCompact':
      emit('rowHeightChange', 24)
      break
    case 'rowHeightStandard':
      emit('rowHeightChange', 32)
      break
    case 'rowHeightLoose':
      emit('rowHeightChange', 48)
      break
    case 'rowHeightCustom':
      openRowHeightMenu()
      break
    default:
      ElMessage.info(`${menu.name || code} 功能开发中`)
  }
}

function handleEditClosed({ row, column }: any) {
  if (!row || !column) return
  const fieldId = Number(column.field)
  if (Number.isNaN(fieldId)) return
  const field = props.fields.find((item) => item.id === fieldId)
  if (!field || !isCellEditable(field)) return

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
    case 'user': {
      // 人员：选中用户 id（单个数字 / 数组）→ [{id, name}] + 姓名串。
      // 直接把 String(id) 写库会破坏展示口径（BitableUser/详情弹框都按 id 反查姓名）。
      const ids = (Array.isArray(newValue) ? newValue : newValue != null ? [newValue] : [])
        .map(Number)
        .filter((n) => Number.isFinite(n))
      if (!ids.length) {
        newValue = ''
      } else {
        const picked = ids
          .map((id) => allUsers.value.find((u) => u.id === id))
          .filter((u): u is UserOption => !!u)
        newValue = {
          valueJson: picked.map((u) => ({ id: u.id, name: u.realName })),
          valueText: picked.map((u) => u.realName).join(', '),
        }
      }
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

  // 字段属性校验：长度 / 正则 / 数值区间 / 邮箱 / 网址 / 电话 / 多选上限
  const error = validateFieldValue(field, newValue)
  if (error) {
    ElMessage.warning(error)
    // 回滚到 records 里的原值，避免界面停留在非法状态
    const original = props.records.find((r) => r.id === row._recordId)
    const originalCell = original?.cells?.[fieldId]
    row[column.field] =
      originalCell?.valueDate ?? originalCell?.valueText ?? originalCell?.valueNumber ?? ''
    buildRecordData()
    return
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
  // 行号列（无 field）与「+ 字段」幽灵列（__add_field__）不参与字段顺序持久化，
  // 只保留真实字段列的相对顺序（按原 newIndex 排序）
  const newOrder = columns
    .map((col: any, index: number) => ({
      fieldId: Number(col.field),
      newIndex: index,
    }))
    .filter((item: { fieldId: number }) => Number.isFinite(item.fieldId))
  emit('headerDragend', newOrder)
}

/**
 * 拖拽列边框调整列宽：通知父级持久化到视图 config.fieldWidths。
 * column.field 即字段 ID；resizeWidth 为拖拽后的最终像素宽度。
 */
function handleColResize({ column, resizeWidth }: any) {
  const fieldId = Number(column?.field)
  if (Number.isNaN(fieldId) || !resizeWidth) return
  emit('colResize', { fieldId, width: Math.round(resizeWidth) })
}
</script>

<style scoped lang="scss">
// ===== 多维表格 GridView 钉钉风格精修 =====
// 1. 行高默认 32px（--grid-row-height 同源 effectiveRowHeight），表头同步
// 2. 行 hover 左缘 3px 主色条 + 极淡蓝底（方向感反馈）
// 3. 选中行 2px 主色 outline + 渐变左缘条（编辑态突出）
// 4. 单元格 active outline（2px 主色 + 光晕）
// 5. 全部颜色走 var(--color-*)，不再硬编码
// 6. tabular-nums 应用于所有数字单元格
// 7. + 号布局：表头右上角加列 / 表尾整行加行（钉钉多维表格布局）

.grid-view {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  // 撑满 editor-main（走查 P1-2：此前网格只有内容高度 ~200px，表格外大面积留白，
  // 且挂在 vxe-grid 内部的右键菜单被矮容器裁剪）
  flex: 1;
  min-height: 0;

  // 圆角卡片化（对齐需求管理列表 TableCard 的观感）：四周留白浮在画布上，
  // --radius-lg 圆角 + 1px 边线；overflow:hidden 负责把 vxe 内部直角裁出圆角
  margin: 12px;
  background: var(--color-surface, var(--color-surface));
  border: 1px solid var(--color-border, var(--color-border));
  border-radius: var(--radius-lg, 12px);

  // 表格基础容器
  :deep(.vxe-grid) {
    border-radius: 0;
    flex: 1;
    min-height: 0;
  }

  // vxe 全边框模式的外框线与卡片边线重叠会成双线，只保留卡片边
  :deep(.vxe-table--border-line) {
    border: 0;
  }

  // ===== 表头区 =====
  :deep(.vxe-table--header-wrapper) {
    background: var(--color-background, var(--color-background));
    // 上下双线：上分割 + 下强调
    box-shadow: inset 0 -1px 0 var(--color-border, #e2e8f0), inset 0 -2px 0 rgba(37, 99, 235, 0.04);
  }

  // ===== 表头字段描述角标（钉钉 AI 多维表格样式）=====
  .bitable-th {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    min-width: 0;
    max-width: 100%;
  }
  // 字段类型图标（钉钉风格：彩色小图标表意）
  .bitable-th__type {
    flex-shrink: 0;
    font-size: 14px;
    line-height: 1;
    opacity: 0.85;
  }
  .bitable-th__title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  // 字段描述角标（内联 SVG，currentColor 跟随 hover 变色）
  .bitable-th__badge {
    flex-shrink: 0;
    width: 14px;
    height: 14px;
    color: var(--color-text-placeholder, var(--color-text-tertiary));
    cursor: help;
    transition: color 120ms var(--ease-standard, ease), transform 120ms var(--ease-standard, ease);

    &:hover {
      color: var(--color-primary, var(--color-primary));
      transform: scale(1.12);
    }
  }

  // ===== 表头行高：跟随 --grid-row-height（与 vxe row-config 同源，避免遮挡）=====
  :deep(.vxe-header--row) {
    .vxe-cell {
      height: var(--grid-row-height, 32px);
      min-height: var(--grid-row-height, 32px);
      padding: 0 10px;
      background: transparent;
      border-bottom: 1px solid var(--color-border, var(--color-border));
      font-weight: 600;
      font-size: 12px;
      letter-spacing: 0.02em;
      color: var(--color-text-secondary, var(--color-text-secondary));
      text-transform: none;
      // 钉钉风格：表头内容（类型图标 + 字段名 + 描述角标）水平垂直居中
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  // ===== 序号列合并复选（钉钉交互）=====
  // 表头全选框
  .seq-check-header {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    cursor: pointer;
    color: var(--color-text-placeholder, var(--color-text-tertiary));
    transition: color 120ms ease;

    &:hover {
      color: var(--color-primary, var(--color-primary));
    }

    &__box {
      font-size: 16px;
      line-height: 1;

      &.is-checked {
        color: var(--color-primary, var(--color-primary));
      }
    }
  }

  // 行内：默认显示序号，行 hover 时序号隐藏、复选框出现；勾选行复选框常显
  :deep(.seq-check-cell) {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    cursor: pointer;
    user-select: none;
    font-variant-numeric: tabular-nums;

    .seq-check-cell__box {
      display: none;
      font-size: 16px;
      line-height: 1;
      color: var(--color-text-placeholder, var(--color-text-tertiary));
      transition: color 120ms ease, transform 120ms ease;

      &:hover {
        color: var(--color-primary, var(--color-primary));
        transform: scale(1.08);
      }

      &.is-checked {
        display: inline-block;
        color: var(--color-primary, var(--color-primary));
      }
    }

    .seq-check-cell__seq {
      font-size: 12px;
      color: var(--color-text-placeholder, var(--color-text-tertiary));
    }

    // 勾选行：序号让位给复选框
    &.is-checked {
      .seq-check-cell__seq { display: none; }
      .seq-check-cell__box { display: inline-block; }
    }
  }
  // 行 hover：序号隐藏、复选框出现
  :deep(.vxe-body--row:hover) {
    .seq-check-cell__seq { display: none; }
    .seq-check-cell__box { display: inline-block; }
  }

  // 勾选行：淡蓝底（钉钉选中行样式）
  :deep(.vxe-body--row.row--checked) {
    background-color: var(--color-primary-bg, rgba(37, 99, 235, 0.06));
  }

  :deep(.vxe-body--row .vxe-cell) {
    // 显式 flex 垂直居中：不依赖 vxe 默认样式，防止被其他规则覆盖后内容沉底被裁
    display: flex;
    align-items: center;
    height: var(--grid-row-height, 32px);
    min-height: var(--grid-row-height, 32px);
    padding: 0 10px;
    border-bottom: 1px solid var(--color-border, var(--color-border));
    font-size: 13px;
    color: var(--color-text-primary, var(--color-text-primary));
    transition: background-color 120ms var(--ease-standard, ease), box-shadow 120ms var(--ease-standard, ease);
  }

  // 行 hover：左缘 3px 主色条 + 极淡蓝底（方向感反馈）
  :deep(.vxe-body--row:hover .vxe-cell) {
    background-color: var(--color-row-hover-bg, rgba(59, 130, 246, 0.04));
  }
  :deep(.vxe-body--row:hover) {
    box-shadow: inset 3px 0 0 var(--color-row-hover-bar, #3b82f6);
  }

  // 选中行：2px 主色 outline + 渐变左缘条（编辑态突出）
  :deep(.vxe-body--row.row--selected .vxe-cell),
  :deep(.vxe-body--row.vxe-body--row-selected .vxe-cell) {
    background-color: var(--color-row-selected-bg, rgba(37, 99, 235, 0.08));
  }
  :deep(.vxe-body--row.row--selected),
  :deep(.vxe-body--row.vxe-body--row-selected) {
    box-shadow: inset 3px 0 0 var(--color-row-selected-bar, linear-gradient(180deg, #3b82f6 0%, #6366f1 100%));
  }

  // 单元格 active（编辑态）：2px 主色 outline + 浅蓝底
  :deep(.vxe-cell--edit),
  :deep(.vxe-cell.is--active) {
    background-color: var(--color-cell-hover-bg, rgba(37, 99, 235, 0.04));
    box-shadow: inset 0 0 0 2px var(--color-cell-active-outline, rgba(37, 99, 235, 0.35));
  }

  // ===== 列分隔线（淡化）=====
  :deep(.vxe-table--render-default.vxe-table--border) {
    .vxe-table--body-wrapper,
    .vxe-table--header-wrapper {
      .vxe-cell {
        border-right: 0.5px solid var(--color-border, var(--color-border));
      }
    }
  }

  // 表体容器
  :deep(.vxe-table--body-wrapper) {
    overflow-y: auto;
  }

  // 右键菜单（与项目主题一致 + 更大圆角）
  :deep(.vxe-context-menu) {
    border-radius: var(--radius-md, 8px);
    box-shadow: var(--shadow-2xl, 0 25px 50px -12px rgba(15, 23, 42, 0.18));
    border: 0.5px solid var(--color-border, var(--color-border));
  }
  :deep(.vxe-context-menu--option) {
    border-radius: 4px;
    margin: 2px;
  }

  // ===== 自定义单元格渲染器（激进风格精修）=====

  // 进度条
  :deep(.bitable-progress-cell) {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    height: 100%;
    padding: 0 2px;
  }
  :deep(.bitable-progress-cell__bar) {
    flex: 1;
    height: 6px;
    border-radius: 3px;
    background: var(--color-progress-track, rgba(99, 102, 241, 0.12));
    overflow: hidden;
    min-width: 40px;
  }
  :deep(.bitable-progress-cell__fill) {
    height: 100%;
    border-radius: 3px;
    transition: width 280ms var(--ease-decelerate, cubic-bezier(0, 0, 0.2, 1));
    min-width: 2px;
  }
  :deep(.bitable-progress-cell__text) {
    flex-shrink: 0;
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-primary, var(--color-text-primary));
    font-variant-numeric: tabular-nums;
    min-width: 32px;
    text-align: right;
  }

  // 彩色标签（单选/多选/流程）
  :deep(.bitable-tag-cell) {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    width: 100%;
    height: 100%;
    padding: 2px 0;
  }
  :deep(.bitable-tag-cell__item) {
    display: inline-flex;
    align-items: center;
    max-width: 100%;
    padding: 2px 8px;
    border-radius: var(--radius-tag, 6px);
    font-size: 12px;
    font-weight: 500;
    line-height: 18px;
    border: 0.5px solid transparent;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    cursor: default;
    transition: transform 120ms var(--ease-standard, ease);
  }
  :deep(.bitable-tag-cell__item:hover) {
    transform: translateY(-1px);
  }

  // 复选框
  :deep(.bitable-checkbox-cell) {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    width: 100%;
    height: 100%;
    cursor: pointer;
  }
  :deep(.bitable-checkbox-cell i) {
    font-size: 20px;
    color: var(--color-checkbox-inactive, #cbd5e1);
    transition: color 150ms var(--ease-standard, ease), transform 150ms var(--ease-spring, cubic-bezier(0.34, 1.56, 0.64, 1));
  }
  :deep(.bitable-checkbox-cell--checked) {
    color: var(--color-checkbox-active, var(--color-primary)) !important;
  }
  :deep(.bitable-checkbox-cell:hover i) {
    color: var(--color-primary-hover, #3b82f6);
    transform: scale(1.1);
  }

  // 评分星级
  :deep(.bitable-rate-cell) {
    display: flex;
    align-items: center;
    gap: 2px;
    width: 100%;
    height: 100%;
  }
  :deep(.bitable-rate-cell i) {
    font-size: 16px;
    color: var(--color-rating-inactive, #e2e8f0);
    transition: color 150ms var(--ease-standard, ease), transform 100ms var(--ease-standard, ease);
  }
  :deep(.bitable-rate-cell--active) {
    color: var(--color-rating-active, #f59e0b) !important;
  }
  // 未点亮的图标（心形/点赞/旗帜等自定义图标同色处理）
  :deep(.bitable-rate-cell i.is-empty) {
    color: var(--color-rating-inactive, #e2e8f0);
  }
  // 半星：allowHalf 开启且分值落在半格上
  :deep(.bitable-rate-cell i.is-half) {
    opacity: 0.55;
  }
  :deep(.bitable-rate-cell__text) {
    margin-left: 6px;
    font-size: 12px;
    font-weight: 500;
    color: var(--color-text-secondary, var(--color-text-secondary));
    font-variant-numeric: tabular-nums;
  }

  // 开关样式的复选框
  :deep(.bitable-switch-cell) {
    display: inline-flex;
    align-items: center;
    width: 32px;
    height: 18px;
    padding: 2px;
    border-radius: 9px;
    background: var(--color-fill, var(--color-muted));
    transition: background 150ms var(--ease-standard, ease);
  }
  :deep(.bitable-switch-cell__dot) {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--color-background, var(--color-surface));
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.2);
    transition: transform 150ms var(--ease-standard, ease);
  }
  :deep(.bitable-switch-cell.is-checked) {
    background: var(--color-primary, var(--color-primary));
  }
  :deep(.bitable-switch-cell.is-checked .bitable-switch-cell__dot) {
    transform: translateX(14px);
  }

  // 数字 / 货币
  :deep(.bitable-number-cell) {
    display: block;
    width: 100%;
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  // 超链接
  :deep(.bitable-link-cell) {
    display: block;
    width: 100%;
    color: var(--color-primary, var(--color-primary));
    text-decoration: none;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &:hover {
      text-decoration: underline;
    }
  }

  // 电话
  :deep(.bitable-phone-cell) {
    font-variant-numeric: tabular-nums;
  }

  // 邮箱（未开启「点击发信」时是纯文本）
  :deep(.bitable-email-cell) {
    display: block;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 文本
  :deep(.bitable-text-cell) {
    display: block;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 地理位置
  :deep(.bitable-location-cell) {
    display: block;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.bitable-daterange-cell) {
    display: block;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 关联 / 人员 / 群组
  :deep(.bitable-relation-cell) {
    display: block;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 人员：头像 + 姓名
  :deep(.bitable-user-cell) {
    display: flex;
    align-items: center;
    gap: 4px;
    width: 100%;
    overflow: hidden;
    white-space: nowrap;
  }
  :deep(.bitable-user-cell__item) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    min-width: 0;
    overflow: hidden;
  }
  :deep(.bitable-user-cell__avatar) {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--color-primary, var(--color-primary));
    color: #fff;
    font-size: 11px;
    line-height: 1;
  }
  :deep(.bitable-user-cell__name) {
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // 附件
  :deep(.bitable-attachment-cell) {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    height: 100%;
    overflow: hidden;
  }
  :deep(.bitable-attachment-cell__item) {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    max-width: 100%;
    color: var(--color-primary, var(--color-primary));
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  :deep(.bitable-attachment-cell__icon) {
    color: var(--color-text-placeholder, var(--color-text-tertiary));
  }
  :deep(.bitable-attachment-cell.is-thumbnail) {
    gap: 4px;
  }
  :deep(.bitable-attachment-cell__thumb) {
    width: 24px;
    height: 24px;
    object-fit: cover;
    border-radius: 4px;
    border: 1px solid var(--color-border, var(--color-border));
  }

  // 日期单元格
  :deep(.bitable-date-cell) {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    height: 100%;
  }
  :deep(.bitable-date-cell__icon) {
    font-size: 14px;
    color: var(--color-text-placeholder, var(--color-text-tertiary));
    flex-shrink: 0;
  }
  :deep(.bitable-date-cell__text) {
    font-size: 13px;
    color: var(--color-text-primary, var(--color-text-primary));
    font-variant-numeric: tabular-nums;
  }

  // 空值占位
  :deep(.bitable-cell-empty) {
    color: var(--color-text-placeholder, #cbd5e1);
    font-size: 13px;
    font-style: italic;
  }

  // 编辑态撑满
  :deep(.vxe-cell--edit) {
    .vxe-select,
    .vxe-input,
    .vxe-date-picker {
      width: 100%;
    }
  }

  // ===== 钉钉布局：网格区容器 =====
  &__table {
    position: relative;
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    // 走查 P1-2：不能 overflow:hidden——vxe 的右键菜单挂载在 vxe-grid 内部，
    // hidden 会把菜单向下超出的部分整块裁掉（表现为只剩一条白条）
    overflow: visible;
  }

  // ===== 表头末尾「+ 字段」幽灵列（钉钉布局）：灰字 + 号，hover 亮起 =====
  :deep(.bitable-add-field) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 2px;
    width: 100%;
    height: 100%;
    font-size: 12.5px;
    color: var(--color-text-placeholder, var(--color-text-tertiary));
    cursor: pointer;
    user-select: none;
    transition: color 150ms var(--ease-standard, ease), background-color 150ms var(--ease-standard, ease);

    i {
      font-size: 14px;
      line-height: 1;
    }

    &:hover {
      color: var(--color-primary, var(--color-primary));
      background: var(--color-primary-subtle, var(--color-primary-subtle));
    }
  }

  // ===== 空表引导（走查 P1-4）=====
  .grid-empty-guide {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 40px 24px;
    text-align: center;

    &__icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      margin-bottom: 8px;
      border-radius: 18px;
      background: var(--color-primary-light, #dbeafe);

      i {
        font-size: 30px;
        color: var(--color-primary, var(--color-primary));
      }
    }

    &__title {
      font-size: 16px;
      font-weight: 600;
      color: var(--color-text-primary, var(--color-text-primary));
    }

    &__hint {
      margin-bottom: 12px;
      font-size: 13px;
      color: var(--color-text-secondary, var(--color-muted-text));
    }

    &__cta {
      margin-bottom: 18px;
    }

    &__quick {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: center;
    }

    &__quick-label {
      font-size: 12px;
      color: var(--color-text-placeholder, var(--color-text-tertiary));
    }

    &__chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 5px 12px;
      border: 1px solid var(--color-border, var(--color-border));
      border-radius: 16px;
      background: var(--color-surface, var(--color-surface));
      font-size: 12.5px;
      color: var(--color-text-primary, var(--color-text-primary));
      cursor: pointer;
      transition: border-color 120ms ease, background 120ms ease;

      i {
        font-size: 14px;
      }

      &:hover {
        border-color: var(--color-primary, #2563eb);
        background: var(--color-primary-subtle, var(--color-primary-subtle));
      }
    }
  }

  .grid-add-row {    display: flex;
    align-items: stretch;
    height: var(--grid-row-height, 32px);
    margin: 0;
    padding: 0;
    background: var(--color-surface, var(--color-surface));
    // 底边线由卡片边框承担，避免与卡片圆角边叠成双线
    cursor: pointer;
    user-select: none;

    &__plus {
      display: flex;
      align-items: center;
      justify-content: center;
      // 与行号列（type: seq, width 48）对齐，⊕ 圆形图标正对行号列
      width: 48px;
      flex-shrink: 0;

      i {
        font-size: 16px;
        line-height: 1;
        color: var(--color-text-placeholder, var(--color-text-tertiary));
        transition: color 150ms var(--ease-standard, ease), transform 150ms var(--ease-standard, ease);
      }
    }

    &:hover {
      background: var(--color-primary-light, #dbeafe);

      .grid-add-row__plus i {
        color: var(--color-primary, var(--color-primary));
        transform: scale(1.15);
      }
    }
  }

  // 批量操作浮层：勾选行后从底部浮现
  .grid-selection-bar {
    position: absolute;
    left: 50%;
    bottom: 44px;
    transform: translateX(-50%);
    z-index: 20;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    background: var(--color-surface, var(--color-surface));
    border: 1px solid var(--color-border, var(--color-border));
    border-radius: 10px;
    box-shadow: 0 8px 28px rgba(15, 23, 42, 0.16);

    &__count {
      margin-right: 4px;
      font-size: 13px;
      color: var(--color-text-secondary, var(--color-text-secondary));
      white-space: nowrap;

      b {
        color: var(--color-primary, var(--color-primary));
        font-weight: 600;
      }
    }
  }

  .selection-bar-enter-active,
  .selection-bar-leave-active {
    transition: opacity 180ms ease, transform 180ms ease;
  }

  .selection-bar-enter-from,
  .selection-bar-leave-to {
    opacity: 0;
    transform: translateX(-50%) translateY(12px);
  }
}
</style>

<style lang="scss">
/* ===== 新增行高亮渐隐（走查 P1-3：与「+ 添加记录」占位行区分）===== */
@keyframes bitable-row-flash {
  0% {
    background-color: rgba(59, 130, 246, 0.18);
  }
  100% {
    background-color: transparent;
  }
}
.bitable-row--flash > td {
  animation: bitable-row-flash 1.5s ease-out forwards;
}

/* ===== 自定义行高弹层（teleport 到 body，需全局样式）===== */
.row-height-popover {
  position: fixed;
  inset: 0;
  z-index: 3100;

  &__mask {
    position: absolute;
    inset: 0;
    background: rgba(15, 23, 42, 0.2);
  }

  &__panel {
    position: absolute;
    top: 30vh;
    left: 50%;
    transform: translateX(-50%);
    width: 280px;
    padding: 14px;
    border-radius: var(--radius-lg, 12px);
    background: var(--color-surface, var(--color-surface));
    box-shadow: var(--shadow-2xl, 0 25px 50px -12px rgba(15, 23, 42, 0.25));
  }

  &__title {
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary, var(--color-text-primary));
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }
}

/* ===== 只读单元格轻提示（teleport 到 body，鼠标处浮出，需全局样式）===== */
.bitable-readonly-hint {
  position: fixed;
  z-index: 3200;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-text-primary, #1f2937);
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border, rgba(100, 116, 139, 0.24));
  box-shadow: var(--shadow-xl, 0 10px 24px rgba(15, 23, 42, 0.16));
  pointer-events: none;

  i {
    flex-shrink: 0;
    font-size: 14px;
    color: var(--color-warning, #f59e0b);
  }
}

.bitable-readonly-hint-enter-active,
.bitable-readonly-hint-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.bitable-readonly-hint-enter-from,
.bitable-readonly-hint-leave-to {
  opacity: 0;
  transform: translateY(2px);
}

/* ===== 单元格内容截断浮框（teleport 到 body，锚定单元格，可悬停滚动，需全局样式）===== */
.bitable-cell-tooltip {
  position: fixed;
  z-index: 3190;
  max-height: 240px;
  padding: 8px 10px;
  overflow: auto;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-primary, #1f2937);
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border, rgba(100, 116, 139, 0.24));
  box-shadow: var(--shadow-xl, 0 10px 24px rgba(15, 23, 42, 0.16));
}
</style>

<style lang="scss">
/* ===== 右键菜单全局样式（context-menu 挂载在 body 下，scoped 无法命中）===== */
/* 整列填色子菜单的色点（prefixIcon 位置渲染成小方块） */
.menu-swatch {
  display: inline-block;
  width: 13px;
  height: 13px;
  border-radius: 3px;
  border: 1px solid rgba(15, 23, 42, 0.12);

  &::before {
    content: '';
    display: none;
  }

  &--gray {
    background: #94a3b8;
  }
  &--blue {
    background: #3b82f6;
  }
  &--green {
    background: #22c55e;
  }
  &--orange {
    background: #f97316;
  }
  &--purple {
    background: #8b5cf6;
  }
}

/* 「设置提醒」的「新能力」角标（suffixConfig.content 渲染在 item-suffix 内） */
.vxe-context-menu--item-suffix {
  font-size: 11px;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 4px;
  color: #f97316;
  background: rgba(249, 115, 22, 0.1);
  white-space: nowrap;
}
</style>
