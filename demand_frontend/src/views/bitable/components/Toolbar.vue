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

    <!-- 按钮组 -->
    <div class="bitable-toolbar__right">
      <!-- 视图切换 -->
      <el-radio-group v-model="viewTypeModel" size="small">
        <el-radio-button value="grid">
          <el-icon><Grid /></el-icon> 表格
        </el-radio-button>
        <el-radio-button value="kanban">
          <el-icon><Menu /></el-icon> 看板
        </el-radio-button>
        <el-radio-button value="gantt">
          <el-icon><ArrowRight /></el-icon> 甘特
        </el-radio-button>
        <el-radio-button value="calendar">
          <el-icon><Calendar /></el-icon> 日历
        </el-radio-button>
        <el-radio-button value="gallery">
          <el-icon><Picture /></el-icon> 画廊
        </el-radio-button>
      </el-radio-group>

      <el-button-group>
        <el-button size="small" @click="emit('addField')">
          <el-icon><Plus /></el-icon> 添加字段
        </el-button>
        <el-button size="small" @click="emit('openAiPanel')">
          <el-icon><MagicStick /></el-icon> AI
        </el-button>
        <el-button size="small" @click="emit('openFieldConfig')">
          <el-icon><Setting /></el-icon> 字段配置
        </el-button>
        <el-button size="small" @click="viewDialogVisible = true">
          <el-icon><View /></el-icon> 视图管理
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

    <!-- 视图管理弹窗（占位） -->
    <el-dialog v-model="viewDialogVisible" title="视图管理" width="500px">
      <div v-if="views.length" class="bitable-toolbar__views">
        <div v-for="view in views" :key="view.id" class="bitable-toolbar__view-item">
          <el-icon><component :is="getViewIcon(view.viewType)" /></el-icon>
          <span>{{ view.name }}</span>
          <el-tag size="small">{{ view.viewType }}</el-tag>
        </div>
      </div>
      <el-empty v-else description="暂无视图" />
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
  Menu,
  ChatDotRound,
  User,
  MagicStick,
  Upload,
} from '@element-plus/icons-vue'
import type { BitableTable, BitableView, ViewType } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  views: BitableView[]
  viewType: ViewType
}>()

const emit = defineEmits<{
  addField: []
  manageView: []
  openComments: []
  openMembers: []
  openAiPanel: []
  openImportExport: []
  openFieldConfig: []
  viewTypeChange: [type: ViewType]
}>()

const viewTypeModel = ref<ViewType>(props.viewType)

watch(() => props.viewType, (val) => {
  viewTypeModel.value = val
})

watch(viewTypeModel, (val) => {
  emit('viewTypeChange', val)
})

const editingName = ref(false)
const tempName = ref('')
const nameInputRef = ref<HTMLElement | null>(null)
const viewDialogVisible = ref(false)

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
  emit('manageView')
  editingName.value = false
}

function cancelEdit() {
  editingName.value = false
  tempName.value = props.table?.name || ''
}

function getViewIcon(type: string) {
  const iconMap: Record<string, any> = {
    grid: Grid,
    kanban: Menu,
    gantt: ArrowRight,
    calendar: Calendar,
    gallery: Picture,
  }
  return iconMap[type] || Document
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

.bitable-toolbar__views {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.bitable-toolbar__view-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
  border-bottom: 1px solid var(--color-border);

  &:last-child {
    border-bottom: none;
  }
}
</style>
