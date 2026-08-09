<template>
  <div class="bitable-toolbar">
    <!-- 左侧：表名 + 视图选择器 -->
    <div class="bitable-toolbar__left">
      <!-- 表名（可编辑） -->
      <el-input
        v-if="editingName"
        ref="nameInputRef"
        v-model="tempName"
        size="small"
        style="width: 200px;"
        @blur="handleNameBlur"
        @keyup.enter="handleNameBlur"
        @keyup.escape="cancelEdit"
      />
      <span v-else class="bitable-toolbar__name" @click="startEditName">
        <el-icon class="bitable-toolbar__name-icon"><Document /></el-icon>
        {{ table?.name || '未选择表' }}
      </span>

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
              <el-tag v-if="view.isDefault" size="small" type="warning" class="view-selector__default-tag">默认</el-tag>
            </el-dropdown-item>
            <el-dropdown-item divided :command="{ type: 'manage' }">
              <el-icon><View /></el-icon> 管理视图
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

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

    <!-- 中间：高频操作 -->
    <div class="bitable-toolbar__center">
      <el-button size="small" type="primary" @click="emit('addField')">
        <el-icon><Plus /></el-icon> 添加字段
      </el-button>
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
            <el-dropdown-item command="members"><el-icon><User /></el-icon> 成员管理</el-dropdown-item>
            <el-dropdown-item command="fieldConfig"><el-icon><Setting /></el-icon> 字段配置</el-dropdown-item>
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
  User,
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
} from '@element-plus/icons-vue'
import type { BitableTable, BitableView, ViewType } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  views: BitableView[]
  activeViewId: number | null
}>()

const emit = defineEmits<{
  addField: []
  viewSwitch: [viewId: number]
  createView: [viewType: ViewType]
  renameTable: [tableId: number, name: string]
  renameView: [viewId: number, name: string]
  duplicateView: [viewId: number]
  setDefaultView: [tableId: number, viewId: number]
  deleteView: [viewId: number]
  openComments: []
  openMembers: []
  openAiPanel: []
  openImportExport: []
  openFieldConfig: []
  openAiFill: []
  openAiClassify: []
  openAiSummarize: []
  openAiBuildTable: []
}>()

const editingName = ref(false)
const tempName = ref('')
const nameInputRef = ref<HTMLElement | null>(null)
const viewDialogVisible = ref(false)

// 当前活动视图
const activeView = computed(() => {
  return props.views.find(v => v.id === props.activeViewId) || props.views[0] || null
})

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

watch(() => props.table, (val) => {
  if (val) {
    tempName.value = val.name
  }
  editingName.value = false
}, { immediate: true })

function startEditName() {
  if (!props.table) return
  tempName.value = props.table.name
  editingName.value = true
  nextTick(() => {
    nameInputRef.value?.focus()
  })
}

function handleNameBlur() {
  if (!props.table) return
  const newName = tempName.value.trim()
  if (!newName || newName === props.table.name) {
    editingName.value = false
    tempName.value = props.table.name
    return
  }
  editingName.value = false
  emit('renameTable', props.table.id, newName)
}

function cancelEdit() {
  editingName.value = false
  tempName.value = props.table?.name || ''
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
  if (command === 'import' || command === 'export') {
    emit('openImportExport')
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
    case 'members':
      emit('openMembers')
      break
    case 'fieldConfig':
      emit('openFieldConfig')
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

  .bitable-toolbar__left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
  }

  .bitable-toolbar__name {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    cursor: pointer;
    padding: 4px 8px;
    border-radius: var(--radius-sm);
    transition: background-color 0.2s ease;
    white-space: nowrap;
    flex-shrink: 0;

    &:hover {
      background: var(--color-surface-alt);
    }

    .bitable-toolbar__name-icon {
      font-size: 16px;
      color: var(--color-text-secondary);
    }
  }

  .bitable-toolbar__center {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .bitable-toolbar__right {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}

// 视图选择器
.view-selector {
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

// 新建视图按钮
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
</style>
