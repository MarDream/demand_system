<template>
  <div class="bitable-toolbar">
    <!-- 表名（可编辑） -->
    <div class="bitable-toolbar__left">
      <el-input
        v-if="editingName"
        ref="nameInputRef"
        v-model="tempName"
        size="small"
        style="width: 240px;"
        @blur="handleNameBlur"
        @keyup.enter="handleNameBlur"
        @keyup.escape="cancelEdit"
      />
      <span v-else class="bitable-toolbar__name" @click="startEditName">
        <el-icon class="bitable-toolbar__name-icon"><Document /></el-icon>
        {{ table?.name || '未选择表' }}
      </span>
    </div>

    <!-- 视图 Tab 栏 + 操作按钮 -->
    <div class="bitable-toolbar__right">
      <!-- 视图 Tab 栏 -->
      <div class="view-tabs">
        <div class="view-tabs__scroll">
          <div
            v-for="view in views"
            :key="view.id"
            class="view-tab"
            :class="{ 'view-tab--active': view.id === activeViewId }"
            @click="emit('viewSwitch', view.id)"
          >
            <el-icon class="view-tab__icon"><component :is="getViewIcon(view.viewType)" /></el-icon>
            <span class="view-tab__name">{{ view.name }}</span>
            <el-dropdown trigger="click" @command="(cmd: string) => handleViewCommand(cmd, view)">
              <span class="view-tab__more" @click.stop>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <el-icon><Edit /></el-icon> 重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="duplicate">
                    <el-icon><CopyDocument /></el-icon> 复制视图
                  </el-dropdown-item>
                  <el-dropdown-item command="setDefault" :disabled="view.isDefault">
                    <el-icon><Star /></el-icon> 设为默认
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" :disabled="view.isDefault" divided>
                    <el-icon><Delete /></el-icon> 删除视图
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- 新建视图按钮 -->
        <el-dropdown trigger="click" @command="(type: string) => emit('createView', type as ViewType)">
          <span class="view-tabs__add">
            <el-icon><Plus /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="grid">
                <el-icon><Grid /></el-icon> 表格视图
              </el-dropdown-item>
              <el-dropdown-item command="kanban">
                <el-icon><Menu /></el-icon> 看板视图
              </el-dropdown-item>
              <el-dropdown-item command="gantt">
                <el-icon><ArrowRight /></el-icon> 甘特视图
              </el-dropdown-item>
              <el-dropdown-item command="calendar">
                <el-icon><Calendar /></el-icon> 日历视图
              </el-dropdown-item>
              <el-dropdown-item command="gallery">
                <el-icon><Picture /></el-icon> 画廊视图
              </el-dropdown-item>
              <el-dropdown-item command="form">
                <el-icon><Tickets /></el-icon> 表单视图
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 视图管理按钮 -->
        <el-button size="small" link class="view-tabs__manage" @click="viewDialogVisible = true">
          <el-icon><View /></el-icon>
        </el-button>
      </div>

      <el-divider direction="vertical" />

      <el-button-group>
        <el-button size="small" @click="emit('addField')">
          <el-icon><Plus /></el-icon> 添加字段
        </el-button>
        <el-button size="small" @click="emit('openFieldConfig')">
          <el-icon><Setting /></el-icon> 字段配置
        </el-button>
        <el-button size="small" @click="emit('openImportExport')">
          <el-icon><Upload /></el-icon> 导入/导出
        </el-button>
        <el-button size="small" @click="emit('openComments')">
          <el-icon><ChatDotRound /></el-icon> 评论
        </el-button>
        <el-button size="small" @click="emit('openMembers')">
          <el-icon><User /></el-icon> 成员
        </el-button>
      </el-button-group>
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
import { ref, watch, nextTick } from 'vue'
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
  Tickets,
  Edit,
  CopyDocument,
  Delete,
  Star,
  EditPen,
  DataAnalysis,
  Reading,
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

// 视图重命名（Tab 右键菜单触发）
const editingViewId = ref<number | null>(null)
const editingViewName = ref('')

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
    return
  }
  editingName.value = false
}

function cancelEdit() {
  editingName.value = false
  tempName.value = props.table?.name || ''
}

function getViewIcon(type: string) {
  return viewTypeIconMap[type] || Document
}

// 字段捷径命令处理
function handleShortcutCommand(command: string) {
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
    case 'openFieldConfig':
      emit('openFieldConfig')
      break
  }
}

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
  gap: var(--spacing-sm);
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;

  .bitable-toolbar__left {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
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

    &:hover {
      background: var(--color-surface-alt);
    }

    .bitable-toolbar__name-icon {
      font-size: 16px;
      color: var(--color-text-secondary);
    }
  }

  .bitable-toolbar__right {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
  }
}

// 视图 Tab 栏
.view-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  padding: 2px;
  border: 1px solid var(--color-border);
}

.view-tabs__scroll {
  display: flex;
  align-items: center;
  gap: 2px;
  overflow-x: auto;
  max-width: 480px;

  &::-webkit-scrollbar {
    display: none;
  }
}

.view-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-text-secondary);
  transition: all 0.15s ease;
  position: relative;
  user-select: none;

  &:hover {
    background: var(--color-surface-alt);
    color: var(--color-text-primary);

    .view-tab__more {
      opacity: 1;
    }
  }

  &--active {
    background: var(--color-surface);
    color: var(--el-color-primary);
    font-weight: 500;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

    &::after {
      content: '';
      position: absolute;
      bottom: -2px;
      left: 50%;
      transform: translateX(-50%);
      width: 60%;
      height: 2px;
      background: var(--el-color-primary);
      border-radius: 1px;
    }

    .view-tab__icon {
      color: var(--el-color-primary);
    }
  }

  .view-tab__icon {
    font-size: 14px;
    flex-shrink: 0;
  }

  .view-tab__name {
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .view-tab__more {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    border-radius: 3px;
    opacity: 0;
    transition: opacity 0.15s;
    font-size: 12px;
    margin-left: 2px;

    &:hover {
      background: var(--color-border);
    }
  }
}

.view-tabs__add {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: all 0.15s ease;
  flex-shrink: 0;

  &:hover {
    background: var(--color-surface-alt);
    color: var(--el-color-primary);
  }
}

.view-tabs__manage {
  color: var(--color-text-secondary);
  font-size: 16px;
  padding: 4px;

  &:hover {
    color: var(--el-color-primary);
  }
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
</style>
