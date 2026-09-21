<template>
  <div class="bitable-toolbar">
    <!-- 左侧：视图选择器（当前数据表名在顶部面包屑展示，此处不重复） -->
    <div class="bitable-toolbar__left">
      <!-- 视图选择器下拉 -->
      <el-dropdown trigger="click" class="view-selector" @command="handleViewSelectorCommand">
        <span class="view-selector__trigger">
          <el-icon class="view-selector__icon"><component :is="getViewIcon(activeView?.viewType || 'grid')" /></el-icon>
          <span class="view-selector__name">{{ activeView?.name || '选择视图' }}</span>
          <el-icon class="view-selector__arrow"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="view in views"
              :key="view.id"
              :command="{ type: 'switch', viewId: view.id }"
              :class="{ 'is-active': view.id === activeViewId }"
            >
              <el-icon><component :is="getViewIcon(view.viewType)" /></el-icon>
              <span>{{ view.name }}</span>
              <!-- 走查 P2-5：视图项上标识已应用的筛选/分组配置 -->
              <el-icon
                v-if="viewHasFilter(view) || viewHasGroup(view)"
                class="view-selector__state"
                :title="[viewHasFilter(view) ? '已应用筛选' : '', viewHasGroup(view) ? '已应用分组' : ''].filter(Boolean).join(' · ')"
              ><Filter /></el-icon>
              <el-tag v-if="view.isDefault" size="small" type="warning" class="view-selector__default-tag">默认</el-tag>
            </el-dropdown-item>
            <el-dropdown-item divided :command="{ type: 'manage' }">
              <el-icon><View /></el-icon> 管理视图
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 字段配置 -->
      <el-button size="small" class="field-config-btn" @click="emit('openFieldConfig')">
        <el-icon><Setting /></el-icon> 字段配置
      </el-button>

      <!-- 新建视图按钮 -->
      <el-dropdown trigger="click" @command="(type: string) => emit('createView', type as ViewType)">
        <el-button size="small" class="view-create-btn">
          <el-icon><Plus /></el-icon> 新建视图
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="grid"><el-icon><Grid /></el-icon> 表格视图</el-dropdown-item>
            <el-dropdown-item command="kanban"><el-icon><Menu /></el-icon> 看板视图</el-dropdown-item>
            <el-dropdown-item command="gantt"><el-icon><ArrowRight /></el-icon> 甘特视图</el-dropdown-item>
            <el-dropdown-item command="calendar"><el-icon><Calendar /></el-icon> 日历视图</el-dropdown-item>
            <el-dropdown-item command="gallery"><el-icon><Picture /></el-icon> 画廊视图</el-dropdown-item>
            <el-dropdown-item command="form"><el-icon><Tickets /></el-icon> 表单视图</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 中间：高频操作（钉钉风格动作组：添加一行 / 字段管理 / 筛选 / 分组 / 排序 / 行高） -->
    <div class="bitable-toolbar__center">
      <el-button size="small" text class="toolbar-action" @click="emit('addRow')">
        <el-icon><CirclePlus /></el-icon> <span class="toolbar-action__label">添加一行</span>
      </el-button>
      <el-button size="small" text class="toolbar-action" @click="emit('openFieldConfig')">
        <el-icon><Setting /></el-icon> <span class="toolbar-action__label">字段管理</span>
      </el-button>
      <el-button size="small" text class="toolbar-action" @click="emit('openFilter')">
        <el-icon><Filter /></el-icon> <span class="toolbar-action__label">筛选</span>
        <el-badge v-if="(filterCount ?? 0) > 0" :value="filterCount ?? 0" :max="99" class="filter-badge" />
      </el-button>
      <el-button size="small" text class="toolbar-action" @click="emit('openGroup')">
        <el-icon><Grid /></el-icon> <span class="toolbar-action__label">分组</span>
        <el-badge v-if="groupFieldId" :value="1" :max="1" class="filter-badge" />
      </el-button>
      <!-- 排序：轻量弹层（选字段 + 方向），应用走视图 sortConfig 链路 -->
      <el-popover
        v-model:visible="sortPopVisible"
        placement="bottom-start"
        :width="280"
        trigger="click"
        popper-class="bitable-toolbar-popover"
      >
        <template #reference>
          <el-button size="small" text class="toolbar-action" :class="{ 'is-active': !!sortState }">
            <el-icon><Sort /></el-icon> <span class="toolbar-action__label">排序</span>
          </el-button>
        </template>
        <div class="sort-popover">
          <div class="sort-popover__title">排序</div>
          <el-select
            v-model="sortFieldId"
            placeholder="选择字段"
            size="small"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="f in sortCandidateFields"
              :key="f.id"
              :label="f.name"
              :value="f.id"
            />
          </el-select>
          <el-radio-group v-model="sortDirection" size="small" class="sort-popover__dirs">
            <el-radio-button value="asc">升序</el-radio-button>
            <el-radio-button value="desc">降序</el-radio-button>
          </el-radio-group>
          <div class="sort-popover__footer">
            <el-button size="small" text :disabled="!sortState" @click="handleSortClear">清除排序</el-button>
            <el-button size="small" type="primary" :disabled="!sortFieldId" @click="handleSortApply">确定</el-button>
          </div>
        </div>
      </el-popover>
      <!-- 行高：快捷档位 + 自定义（复用 GridView 的自定义弹层） -->
      <el-popover placement="bottom-start" :width="180" trigger="click" popper-class="bitable-toolbar-popover">
        <template #reference>
          <el-button size="small" text class="toolbar-action" :class="{ 'is-active': isCustomRowHeight }">
            <el-icon><Operation /></el-icon> <span class="toolbar-action__label">行高</span>
          </el-button>
        </template>
        <div class="row-height-pop">
          <button
            v-for="opt in ROW_HEIGHT_PRESETS"
            :key="opt.value"
            type="button"
            class="row-height-pop__item"
            :class="{ 'is-active': currentRowHeight === opt.value }"
            @click="handleRowHeightPreset(opt.value)"
          >
            <span class="row-height-pop__demo" :style="{ height: Math.max(6, Math.round(opt.value / 4)) + 'px' }" />
            <span>{{ opt.label }}（{{ opt.value }}px）</span>
          </button>
          <button type="button" class="row-height-pop__item" @click="emit('rowHeightCustom')">
            <span class="row-height-pop__demo row-height-pop__demo--custom"><i class="ri-settings-3-line" /></span>
            <span>自定义…</span>
          </button>
        </div>
      </el-popover>
    </div>

    <!-- 右侧：中低频操作 -->
    <div class="bitable-toolbar__right">
      <!-- 导入/导出 -->
      <el-dropdown trigger="click" @command="handleImportExportCommand">
        <el-button size="small">
          <el-icon><Upload /></el-icon> 导入/导出 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="import"><el-icon><Upload /></el-icon> 导入数据</el-dropdown-item>
            <el-dropdown-item command="export"><el-icon><Download /></el-icon> 导出数据</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- AI 助手 -->
      <el-dropdown trigger="click" @command="handleAiCommand">
        <el-button size="small">
          <el-icon><MagicStick /></el-icon> AI 助手 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="aiFill"><el-icon><EditPen /></el-icon> AI 填充</el-dropdown-item>
            <el-dropdown-item command="aiClassify"><el-icon><DataAnalysis /></el-icon> AI 分类</el-dropdown-item>
            <el-dropdown-item command="aiSummarize"><el-icon><Reading /></el-icon> AI 总结</el-dropdown-item>
            <el-dropdown-item command="aiBuildTable"><el-icon><Grid /></el-icon> AI 建表</el-dropdown-item>
            <el-dropdown-item divided command="aiChat"><el-icon><ChatDotRound /></el-icon> AI 面板</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 更多 -->
      <el-dropdown trigger="click" @command="handleMoreCommand">
        <el-button size="small">
          <el-icon><MoreFilled /></el-icon> 更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="comments"><el-icon><ChatDotRound /></el-icon> 评论</el-dropdown-item>
            <el-dropdown-item command="operations"><el-icon><Clock /></el-icon> 操作记录</el-dropdown-item>
            <el-dropdown-item command="shareView"><el-icon><Share /></el-icon> 分享视图</el-dropdown-item>
            <el-dropdown-item v-if="activeView?.viewType === 'form'" command="publishForm">
              <el-icon><Position /></el-icon> 发布表单
            </el-dropdown-item>
            <el-dropdown-item command="integration"><el-icon><Connection /></el-icon> API 与 Webhook</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 视图管理弹窗 -->
    <el-dialog v-model="viewDialogVisible" title="视图管理" width="560px">
      <div v-if="views.length" class="view-manager">
        <div v-for="view in views" :key="view.id" class="view-manager__item">
          <div class="view-manager__info">
            <el-icon class="view-manager__icon"><component :is="getViewIcon(view.viewType)" /></el-icon>
            <el-input
              v-if="editingViewId === view.id"
              v-model="editingViewName"
              size="small"
              style="width: 160px;"
              @blur="confirmRename(view)"
              @keyup.enter="confirmRename(view)"
              @keyup.escape="cancelRename"
            />
            <span v-else class="view-manager__name" @dblclick="startRename(view)">{{ view.name }}</span>
            <el-tag size="small" type="info">{{ viewTypeLabelMap[view.viewType] || view.viewType }}</el-tag>
            <el-tag v-if="view.isDefault" size="small" type="warning">默认</el-tag>
          </div>
          <div class="view-manager__actions">
            <el-button link size="small" @click="startRename(view)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button link size="small" @click="emit('duplicateView', view.id)">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
            <el-button link size="small" :disabled="view.isDefault" @click="emit('setDefaultView', table!.id, view.id)">
              <el-icon><Star /></el-icon>
            </el-button>
            <el-button link size="small" type="danger" :disabled="view.isDefault" @click="emit('deleteView', view.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无视图" />
      <template #footer>
        <el-dropdown trigger="click" @command="(type: string) => { emit('createView', type as ViewType); viewDialogVisible = false }">
          <el-button type="primary" size="small">
            <el-icon><Plus /></el-icon> 新建视图
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="grid"><el-icon><Grid /></el-icon> 表格视图</el-dropdown-item>
              <el-dropdown-item command="kanban"><el-icon><Menu /></el-icon> 看板视图</el-dropdown-item>
              <el-dropdown-item command="gantt"><el-icon><ArrowRight /></el-icon> 甘特视图</el-dropdown-item>
              <el-dropdown-item command="calendar"><el-icon><Calendar /></el-icon> 日历视图</el-dropdown-item>
              <el-dropdown-item command="gallery"><el-icon><Picture /></el-icon> 画廊视图</el-dropdown-item>
              <el-dropdown-item command="form"><el-icon><Tickets /></el-icon> 表单视图</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import {
  Plus,
  Setting,
  View,
  Document,
  Grid,
  Calendar,
  Picture,
  ArrowRight,
  ArrowDown,
  Menu,
  ChatDotRound,
  Clock,
  MagicStick,
  Upload,
  Download,
  Tickets,
  Edit,
  CopyDocument,
  Delete,
  Star,
  EditPen,
  DataAnalysis,
  Reading,
  MoreFilled,
  Filter,
  Share,
  Position,
  Connection,
  CirclePlus,
  Sort,
  Operation,
} from '@element-plus/icons-vue'
import type { BitableField, BitableTable, BitableView, ViewType } from '@/types/bitable'

export interface ToolbarSortState {
  fieldId: number
  direction: 'asc' | 'desc'
}

const props = defineProps<{
  table: BitableTable | null
  views: BitableView[]
  activeViewId: number | null
  filterCount?: number
  groupFieldId?: number | null
  /** 字段列表（排序弹层候选） */
  fields?: BitableField[]
  /** 当前数据表行高（null = 默认 26） */
  rowHeight?: number | null
  /** 当前视图排序状态（排序弹层预填 + 按钮高亮） */
  sortState?: ToolbarSortState | null
}>()

const emit = defineEmits<{
  addRow: []
  openFieldConfig: []
  openFilter: []
  openGroup: []
  viewSwitch: [viewId: number]
  createView: [viewType: ViewType]
  renameView: [viewId: number, name: string]
  duplicateView: [viewId: number]
  setDefaultView: [tableId: number, viewId: number]
  deleteView: [viewId: number]
  openComments: []
  openOperations: []
  openAiPanel: []
  openImportExport: []
  openExport: []
  openShareView: []
  openFormPublish: []
  openIntegration: []
  openAiFill: []
  openAiClassify: []
  openAiSummarize: []
  openAiBuildTable: []
  sortField: [data: { fieldId: number; direction: 'asc' | 'desc' }]
  sortClear: []
  rowHeightChange: [height: number]
  rowHeightCustom: []
}>()

const viewDialogVisible = ref(false)

// ==================== 排序 / 行高弹层（钉钉工具栏风格） ====================

/** 行高快捷档位（与 GridView 右键菜单一致） */
const ROW_HEIGHT_PRESETS = [
  { label: '紧凑', value: 24 },
  { label: '标准', value: 32 },
  { label: '宽松', value: 48 },
]

const sortPopVisible = ref(false)
const sortFieldId = ref<number | null>(null)
const sortDirection = ref<'asc' | 'desc'>('asc')
const sortCandidateFields = computed(() => props.fields || [])

/** 生效行高（默认 32），用于弹层高亮当前档位 */
const currentRowHeight = computed(() => {
  const h = Number(props.rowHeight)
  return Number.isFinite(h) && h > 0 ? h : 32
})
const isCustomRowHeight = computed(() => !ROW_HEIGHT_PRESETS.some((p) => p.value === currentRowHeight.value))

// 打开排序弹层时预填当前排序状态
watch(sortPopVisible, (visible) => {
  if (visible) {
    sortFieldId.value = props.sortState?.fieldId ?? null
    sortDirection.value = props.sortState?.direction ?? 'asc'
  }
})

function handleSortApply() {
  if (!sortFieldId.value) return
  emit('sortField', { fieldId: sortFieldId.value, direction: sortDirection.value })
  sortPopVisible.value = false
}

function handleSortClear() {
  emit('sortClear')
  sortPopVisible.value = false
}

function handleRowHeightPreset(value: number) {
  emit('rowHeightChange', value)
}

// 当前活动视图
const activeView = computed(() => {
  return props.views.find(v => v.id === props.activeViewId) || props.views[0] || null
})

/** 视图是否配置了筛选（走查 P2-5：下拉项上标识，帮助识别视图差异） */
function viewHasFilter(view: BitableView): boolean {
  const cfg = (view as any).filterConfig
  if (!cfg) return false
  if (Array.isArray(cfg)) return cfg.length > 0
  // FilterGroup: { logic, children }
  const children = (cfg as any).children
  return Array.isArray(children) && children.length > 0
}

/** 视图是否配置了分组（groupConfig: [{fieldId}]） */
function viewHasGroup(view: BitableView): boolean {
  const gc = (view as any).groupConfig
  return Array.isArray(gc) && gc.length > 0
}

// 视图类型图标映射
const viewTypeIconMap: Record<string, any> = {
  grid: Grid,
  kanban: Menu,
  gantt: ArrowRight,
  calendar: Calendar,
  gallery: Picture,
  form: Tickets,
}

// 视图类型中文映射
const viewTypeLabelMap: Record<string, string> = {
  grid: '表格',
  kanban: '看板',
  gantt: '甘特',
  calendar: '日历',
  gallery: '画廊',
  form: '表单',
}

function getViewIcon(type: string) {
  return viewTypeIconMap[type] || Document
}

// 视图选择器命令处理
function handleViewSelectorCommand(cmd: { type: string; viewId?: number }) {
  if (cmd.type === 'switch' && cmd.viewId) {
    emit('viewSwitch', cmd.viewId)
  } else if (cmd.type === 'manage') {
    viewDialogVisible.value = true
  }
}

// 导入/导出命令处理
function handleImportExportCommand(command: string) {
  if (command === 'import') {
    emit('openImportExport')
  } else if (command === 'export') {
    emit('openExport')
  }
}

// AI 助手命令处理
function handleAiCommand(command: string) {
  switch (command) {
    case 'aiFill':
      emit('openAiFill')
      break
    case 'aiClassify':
      emit('openAiClassify')
      break
    case 'aiSummarize':
      emit('openAiSummarize')
      break
    case 'aiBuildTable':
      emit('openAiBuildTable')
      break
    case 'aiChat':
      emit('openAiPanel')
      break
  }
}

// 更多命令处理
function handleMoreCommand(command: string) {
  switch (command) {
    case 'comments':
      emit('openComments')
      break
    case 'operations':
      emit('openOperations')
      break
    case 'shareView':
      emit('openShareView')
      break
    case 'publishForm':
      emit('openFormPublish')
      break
    case 'integration':
      emit('openIntegration')
      break
  }
}

// 视图重命名（Tab 右键菜单触发）
const editingViewId = ref<number | null>(null)
const editingViewName = ref('')

// 视图 Tab 右键菜单命令处理
function handleViewCommand(cmd: string, view: BitableView) {
  switch (cmd) {
    case 'rename':
      startRename(view)
      break
    case 'duplicate':
      emit('duplicateView', view.id)
      break
    case 'setDefault':
      if (props.table) {
        emit('setDefaultView', props.table.id, view.id)
      }
      break
    case 'delete':
      emit('deleteView', view.id)
      break
  }
}

function startRename(view: BitableView) {
  editingViewId.value = view.id
  editingViewName.value = view.name
  nextTick(() => {
    // 聚焦到输入框（弹窗内或 Tab 内）
  })
}

function confirmRename(view: BitableView) {
  const name = editingViewName.value.trim()
  if (name && name !== view.name) {
    emit('renameView', view.id, name)
  }
  editingViewId.value = null
  editingViewName.value = ''
}

function cancelRename() {
  editingViewId.value = null
  editingViewName.value = ''
}
</script>

<style scoped lang="scss">
.bitable-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }

  // 以工具栏自身宽度响应（容器查询）：窄容器时中间动作组只留图标，省出的空间给左组
  container-type: inline-size;
  container-name: bitable-toolbar;

  // 左侧弹性占位：basis 0 会把本组压到内容宽度以下，按钮溢出侵入中间组；
  // 改成 auto + overflow hidden，空间不足时组内自己收缩（视图选择器省略号），不外溢
  .bitable-toolbar__left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
  }

  .bitable-toolbar__center {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }

  // 钉钉风格动作按钮：文字按钮无框，hover 淡底；应用了排序/自定义行高时亮主色
  .toolbar-action {
    padding: 5px 10px;
    font-weight: var(--font-weight-regular, 400);
    color: var(--color-text-primary, var(--color-text-primary));

    &.is-active {
      color: var(--el-color-primary);
    }

    + .toolbar-action {
      margin-left: 0;
    }
  }

  .bitable-toolbar__right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  // 窄容器：中间动作组收成纯图标（图标本身表意：+ / 漏斗 / 分组 / 排序 / 行高），
  // 省出的 ~180px 让左组在 1120 视口也能完整放下
  @container bitable-toolbar (max-width: 880px) {
    .toolbar-action__label {
      display: none;
    }
    .toolbar-action {
      padding: 5px 8px;
    }
  }
}

// 视图选择器
.view-selector {
  // 空间不足时选择器先于其它按钮收缩，名称出省略号；min-width 保底防止收成 0
  min-width: 56px;
  flex-shrink: 1;

  .view-selector__trigger {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px 8px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--color-border);
    background: var(--color-background);
    cursor: pointer;
    font-size: 13px;
    color: var(--color-text-primary);
    transition: all 0.15s ease;
    white-space: nowrap;
    max-width: 100%;

    &:hover {
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
    }
  }

  .view-selector__icon {
    font-size: 14px;
    flex-shrink: 0;
  }

  .view-selector__name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .view-selector__arrow {
    font-size: 12px;
    color: var(--color-text-secondary);
    flex-shrink: 0;
  }
}

// 左侧一级按钮（字段配置 / 新建视图）不参与收缩
.field-config-btn,
.view-create-btn {
  flex-shrink: 0;
}

// 视图管理弹窗
.view-manager {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.view-manager__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  transition: background-color 0.15s;

  &:hover {
    background: var(--color-surface-alt);
  }
}

.view-manager__info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.view-manager__icon {
  font-size: 16px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.view-manager__name {
  font-size: 14px;
  font-weight: 500;
  cursor: default;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

.view-manager__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

// 下拉菜单中当前选中项高亮
:deep(.el-dropdown-menu__item.is-active) {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

// 默认标签
.view-selector__default-tag {
  margin-left: 4px;
  flex-shrink: 0;
}

// 视图已应用筛选/分组的状态图标（走查 P2-5）
.view-selector__state {
  margin-left: 4px;
  flex-shrink: 0;
  color: var(--el-color-primary);
  font-size: 13px;
}
</style>

<!-- 排序 / 行高弹层：内容 teleport 到 body，必须用非 scoped 全局样式 -->
<style lang="scss">
.bitable-toolbar-popover {
  padding: 12px !important;
}

.sort-popover {
  &__title {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary, var(--color-text-primary));
  }

  &__dirs {
    margin-top: 10px;
    width: 100%;
    display: flex;

    .el-radio-button {
      flex: 1;
    }

    .el-radio-button__inner {
      width: 100%;
    }
  }

  &__footer {
    margin-top: 12px;
    display: flex;
    justify-content: space-between;
  }
}

.row-height-pop {
  display: flex;
  flex-direction: column;
  gap: 2px;

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 8px;
    border: none;
    border-radius: 6px;
    background: transparent;
    font-size: 13px;
    color: var(--color-text-primary, var(--color-text-primary));
    cursor: pointer;
    text-align: left;
    transition: background-color 120ms ease, color 120ms ease;

    &:hover {
      background: var(--color-surface-alt, var(--color-surface-alt));
    }

    &.is-active {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9, var(--color-primary-subtle));
    }
  }

  &__demo {
    display: inline-block;
    width: 22px;
    border-radius: 2px;
    background: var(--color-border, #cbd5e1);
    flex-shrink: 0;

    &--custom {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 22px;
      background: transparent;
      color: var(--color-text-secondary, var(--color-muted-text));

      i {
        font-size: 14px;
      }
    }
  }
}
</style>
