<template>
  <div class="attachment-section">
    <input ref="fileInputRef" type="file" multiple style="display: none" @change="handleFileSelect" />
    <div
      class="upload-zone"
      :class="{ 'upload-zone--active': dragover }"
      @click="triggerUpload"
      @dragover.prevent="dragover = true"
      @dragleave.prevent="dragover = false"
      @drop.prevent="handleDrop"
      @paste.prevent="handlePaste"
      tabindex="0"
    >
      <div class="upload-zone__content">
        <el-icon :size="24" class="upload-zone__icon"><Upload /></el-icon>
        <span class="upload-zone__text">点击上传、拖拽文件或粘贴截图至此处</span>
      </div>
    </div>
    <div v-if="uploading" class="attachment-uploading">附件上传中...</div>
    <div v-if="attachments.length > 0" class="attachment-list">
      <div v-for="(file, index) in attachments" :key="file.fileId || file.objectName || file.url + '-' + index" class="attachment-item">
        <div class="attachment-meta">
          <el-button link type="primary" @click="handleDownload(file)">{{ file.name }}</el-button>
          <span v-if="file.size" class="attachment-size">{{ formatFileSize(file.size) }}</span>
        </div>
        <el-button link type="danger" @click="$emit('remove', index)">移除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import type { RequirementAttachment } from '@/types/requirement'
import { uploadRequirementAttachment, downloadRequirementAttachment } from '@/api/modules/file'

const props = defineProps<{
  attachments: RequirementAttachment[]
  uploading?: boolean
}>()

const emit = defineEmits<{
  'update:attachments': [value: RequirementAttachment[]]
  upload: [file: File]
  remove: [index: number]
  download: [file: RequirementAttachment]
  paste: [event: ClipboardEvent]
}>()

const dragover = ref(false)
const fileInputRef = ref<HTMLInputElement>()

function triggerUpload() {
  fileInputRef.value?.click()
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    emit('upload', file)
  }
  input.value = ''
}

async function handleDrop(event: DragEvent) {
  dragover.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (file.size > 50 * 1024 * 1024) {
      ElMessage.error('单个附件不能超过 50MB')
      continue
    }
    emit('upload', file)
  }
}

async function handlePaste(event: ClipboardEvent) {
  // Let parent handle paste events for rich text editor
  emit('paste', event)
}

async function handleDownload(file: RequirementAttachment) {
  try {
    await downloadRequirementAttachment(file)
  } catch {
    ElMessage.error('附件下载失败')
  }
}

function formatFileSize(size?: number | null) {
  if (!size) return ''
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.attachment-section {
  margin-top: 16px;
}

.upload-zone {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
  outline: none;
}

.upload-zone:hover,
.upload-zone:focus {
  border-color: var(--el-color-primary);
}

.upload-zone--active {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.04);
}

.upload-zone__content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.upload-zone__icon {
  color: var(--el-text-color-placeholder);
}

.upload-zone:hover .upload-zone__icon,
.upload-zone--active .upload-zone__icon {
  color: var(--el-color-primary);
}

.upload-zone__text {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.upload-zone:hover .upload-zone__text,
.upload-zone--active .upload-zone__text {
  color: var(--el-color-primary);
}

.attachment-uploading {
  color: var(--color-muted-text);
  font-size: 13px;
  padding: 8px 0;
}

.attachment-list {
  margin-top: 12px;
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
}

.attachment-item:last-child {
  border-bottom: none;
}

.attachment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.attachment-meta .el-button {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  color: var(--color-muted-text);
  font-size: 12px;
}
</style>
