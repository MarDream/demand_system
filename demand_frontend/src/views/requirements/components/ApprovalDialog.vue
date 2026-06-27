<template>
  <el-dialog
    v-model="dialogVisible"
    title="审核操作"
    width="520px"
    draggable
    class="resizable-dialog"
    :close-on-click-modal="false"
    @closed="handleReset"
    @opened="initResize"
  >
    <p class="approval-dialog-tip">
      提交到下一节点前{{ ratingRequired ? '请补充审核信息' : '可补充审核信息（选填）' }}。
    </p>

    <!-- 多维评分模式 -->
    <template v-if="dimensions.length > 0">
      <div
        v-for="dim in dimensions"
        :key="dim.key"
        class="approval-dialog-dimension"
      >
        <div class="approval-dialog-dim-header">
          <span class="approval-dialog-label">{{ dim.name }}</span>
          <el-tooltip v-if="dim.description" :content="dim.description" placement="top">
            <el-icon class="rating-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <el-rate
          v-model="localRatingDimensions[dim.key]"
          :max="5"
          :texts="[dim.minLabel || '1星', '2星', '3星', '4星', dim.maxLabel || '5星']"
          show-text
        />
      </div>
    </template>

    <!-- 单一评分模式 -->
    <div v-else class="approval-dialog-rate">
      <span class="approval-dialog-label">{{ ratingRequired ? '评分' : '评分（选填）' }}</span>
      <el-rate v-model="localRating" :max="5" />
    </div>

    <el-input
      v-model="localComment"
      type="textarea"
      :rows="4"
      placeholder="请输入审核意见（选填）"
      maxlength="1000"
      show-word-limit
    />
    
    <!-- 拖拽手柄 -->
    <div class="resize-handle" @mousedown="startResize"></div>
    
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">确认提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'

interface RatingDimension {
  key: string
  name: string
  description?: string
  minLabel?: string
  maxLabel?: string
}

const props = defineProps<{
  visible: boolean
  rating: number
  ratingDimensions: Record<string, number>
  dimensions: RatingDimension[]
  ratingRequired: boolean
  comment: string
  loading: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:rating': [value: number]
  'update:ratingDimensions': [value: Record<string, number>]
  'update:comment': [value: string]
  confirm: []
  cancel: []
  reset: []
}>()

const localRating = computed({
  get: () => props.rating,
  set: (val) => emit('update:rating', val),
})

const localRatingDimensions = computed({
  get: () => props.ratingDimensions || {},
  set: (val) => emit('update:ratingDimensions', val),
})

const localComment = computed({
  get: () => props.comment,
  set: (val) => emit('update:comment', val),
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}

function handleReset() {
  emit('reset')
}

// 拖拽调整大小逻辑
let dialogElement: HTMLElement | null = null
let isResizing = false
let startX = 0
let startY = 0
let startWidth = 0
let startHeight = 0

function initResize() {
  // 弹框打开后，找到 el-dialog 元素
  setTimeout(() => {
    dialogElement = document.querySelector('.resizable-dialog .el-dialog') as HTMLElement
  }, 100)
}

function startResize(e: MouseEvent) {
  if (!dialogElement) return
  
  isResizing = true
  startX = e.clientX
  startY = e.clientY
  startWidth = dialogElement.offsetWidth
  startHeight = dialogElement.offsetHeight
  
  document.addEventListener('mousemove', doResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

function doResize(e: MouseEvent) {
  if (!isResizing || !dialogElement) return
  
  const deltaX = e.clientX - startX
  const deltaY = e.clientY - startY
  
  const newWidth = Math.max(420, Math.min(window.innerWidth * 0.9, startWidth + deltaX))
  const newHeight = Math.max(350, Math.min(window.innerHeight * 0.9, startHeight + deltaY))
  
  dialogElement.style.width = newWidth + 'px'
  dialogElement.style.height = newHeight + 'px'
}

function stopResize() {
  isResizing = false
  document.removeEventListener('mousemove', doResize)
  document.removeEventListener('mouseup', stopResize)
}

onUnmounted(() => {
  document.removeEventListener('mousemove', doResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.approval-dialog-tip {
  margin: 0 0 16px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.approval-dialog-rate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.approval-dialog-dimension {
  margin-bottom: 16px;
}

.approval-dialog-dim-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.approval-dialog-label {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.rating-help {
  color: var(--el-color-info);
  cursor: help;
  font-size: 14px;
}

/* 拖拽手柄样式 */
.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
  z-index: 10;
}

.resize-handle::before {
  content: '';
  position: absolute;
  right: 3px;
  bottom: 3px;
  width: 12px;
  height: 12px;
  border-right: 2px solid #dcdfe6;
  border-bottom: 2px solid #dcdfe6;
  border-bottom-right-radius: 2px;
}

.resize-handle::after {
  content: '';
  position: absolute;
  right: 7px;
  bottom: 7px;
  width: 6px;
  height: 6px;
  border-right: 2px solid #dcdfe6;
  border-bottom: 2px solid #dcdfe6;
}

/* 弹框容器样式 */
.resizable-dialog :deep(.el-dialog) {
  min-width: 420px;
  min-height: 350px;
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  position: relative;
}

.resizable-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow-y: auto;
  position: relative;
}

.resizable-dialog :deep(.el-dialog__footer) {
  flex-shrink: 0;
}
</style>
