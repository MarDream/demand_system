<template>
  <div class="attachment-uploader">
    <!-- 文件输入框 -->
    <input ref="fileInputRef" type="file" multiple style="display: none" @change="handleFileSelect" />

    <!-- 上传区域 -->
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

    <!-- 上传中提示 -->
    <div v-if="uploading" class="attachment-uploading">附件上传中...</div>

    <!-- 附件列表 -->
    <div v-if="attachments.length > 0" class="attachment-list">
      <div
        v-for="(file, index) in attachments"
        :key="`${file.fileId || file.objectName || file.url}-${index}`"
        class="attachment-item"
      >
        <el-icon :size="18" class="attachment-icon">
          <Document v-if="getFileExt(file.name) === 'pdf'" />
          <DocumentCopy v-else-if="['doc','docx','wps','xls','xlsx','csv'].includes(getFileExt(file.name))" />
          <Picture v-else-if="['jpg','jpeg','png','gif','svg','webp','bmp'].includes(getFileExt(file.name))" />
          <VideoCamera v-else-if="['mp4','mov','avi','mkv','webm'].includes(getFileExt(file.name))" />
          <Folder v-else-if="['zip','rar','7z','tar','gz'].includes(getFileExt(file.name))" />
          <Document v-else />
        </el-icon>
        <div class="attachment-main">
          <el-button link type="primary" class="attachment-name" @click="handleDownload(file)">
            {{ file.name }}
          </el-button>
          <div class="attachment-meta">
            <span v-if="file.size" class="attachment-size">{{ formatFileSize(file.size) }}</span>
            <span v-if="file.uploadedAt" class="attachment-dot">·</span>
            <span v-if="file.uploadedAt" class="attachment-time">{{ formatAttachmentTime(file.uploadedAt) }}</span>
          </div>
        </div>
        <el-button
          v-if="showPreview"
          link
          class="attachment-preview"
          aria-label="预览附件"
          @click="handlePreview(file)"
        >
          <el-icon><View /></el-icon>
        </el-button>
        <el-button
          link
          type="danger"
          aria-label="删除附件"
          @click="removeAttachment(index)"
          class="attachment-remove"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Document, DocumentCopy, Picture, VideoCamera, Folder, Delete, View } from '@element-plus/icons-vue'
import { uploadRequirementAttachment, downloadRequirementAttachment } from '@/api/modules/file'
import type { RequirementAttachment } from '@/types/requirement'
import dayjs from 'dayjs'

interface Props {
  modelValue: RequirementAttachment[]
  maxSize?: number // MB
  showPreview?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: RequirementAttachment[]): void
  (e: 'preview', file: RequirementAttachment): void
}

const props = withDefaults(defineProps<Props>(), {
  maxSize: 50,
  showPreview: true,
})

const emit = defineEmits<Emits>()

const fileInputRef = ref<HTMLInputElement>()
const dragover = ref(false)
const uploading = ref(false)

const attachments = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function triggerUpload() {
  fileInputRef.value?.click()
}

function beforeUpload(file: File): boolean {
  const maxSizeBytes = props.maxSize * 1024 * 1024
  if (file.size > maxSizeBytes) {
    ElMessage.warning(`文件大小不能超过 ${props.maxSize}MB`)
    return false
  }
  return true
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  for (const file of Array.from(files)) {
    if (!beforeUpload(file)) continue
    await uploadFile(file)
  }
  input.value = ''
}

async function handleDrop(event: DragEvent) {
  dragover.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return

  for (const file of Array.from(files)) {
    if (!beforeUpload(file)) continue
    await uploadFile(file)
  }
}

async function handlePaste(event: ClipboardEvent) {
  const items = event.clipboardData?.items
  if (!items) return

  for (const item of Array.from(items)) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        event.preventDefault()
        // 为粘贴的图片生成文件名
        const ext = getFileExtension(file.type)
        const filename = `screenshot_${Date.now()}.${ext}`
        const processedFile = new File([file], filename, { type: file.type })

        if (beforeUpload(processedFile)) {
          await uploadFile(processedFile)
        }
      }
      return
    }
  }

  // 处理粘贴的文件
  const files = event.clipboardData?.files
  if (files && files.length > 0) {
    for (const file of Array.from(files)) {
      let processedFile = file
      // 如果文件名缺失或无扩展名，尝试生成
      if (!file.name || file.name === 'image' || !file.name.includes('.')) {
        const ext = getFileExtension(file.type)
        processedFile = new File([file], `file_${Date.now()}.${ext}`, { type: file.type })
      }

      if (beforeUpload(processedFile)) {
        await uploadFile(processedFile)
      }
    }
  }
}

async function uploadFile(file: File) {
  try {
    uploading.value = true
    const attachment = await uploadRequirementAttachment(file)
    attachments.value = [...attachments.value, attachment]
    ElMessage.success('附件上传成功')
  } catch (error) {
    ElMessage.error('附件上传失败')
  } finally {
    uploading.value = false
  }
}

function removeAttachment(index: number) {
  attachments.value = attachments.value.filter((_, i) => i !== index)
}

async function handleDownload(file: RequirementAttachment) {
  try {
    await downloadRequirementAttachment(file)
  } catch {
    ElMessage.error('附件下载失败')
  }
}

function handlePreview(file: RequirementAttachment) {
  emit('preview', file)
}

function getFileExt(filename: string): string {
  if (!filename) return ''
  const parts = filename.split('.')
  return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : ''
}

function getFileExtension(mimeType: string): string {
  const mimeToExt: Record<string, string> = {
    'image/jpeg': 'jpg',
    'image/png': 'png',
    'image/gif': 'gif',
    'image/webp': 'webp',
    'image/bmp': 'bmp',
    'image/svg+xml': 'svg',
    'application/pdf': 'pdf',
    'application/msword': 'doc',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
    'application/vnd.ms-excel': 'xls',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
    'text/plain': 'txt',
    'application/zip': 'zip',
    'application/x-rar-compressed': 'rar',
  }
  return mimeToExt[mimeType] || 'bin'
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

function formatAttachmentTime(time: string): string {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<style scoped>
.attachment-uploader {
  width: 100%;
}

.upload-zone {
  border: 2px dashed var(--el-border-color);
  border-radius: 6px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background-color: var(--el-fill-color-lighter);
}

.upload-zone:hover,
.upload-zone:focus {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.upload-zone--active {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.upload-zone__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-zone__icon {
  color: var(--el-color-primary);
}

.upload-zone__text {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.attachment-uploading {
  margin-top: 12px;
  padding: 8px 12px;
  background-color: var(--el-color-info-light-9);
  border-radius: 4px;
  font-size: 14px;
  color: var(--el-color-info);
}

.attachment-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 6px;
  transition: background-color 0.3s;
}

.attachment-item:hover {
  background-color: var(--el-fill-color-light);
}

.attachment-icon {
  flex-shrink: 0;
  color: var(--el-color-primary);
}

.attachment-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.attachment-name {
  padding: 0;
  height: auto;
  font-weight: 500;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.attachment-size,
.attachment-time {
  line-height: 1;
}

.attachment-dot {
  line-height: 1;
}

.attachment-preview,
.attachment-remove {
  flex-shrink: 0;
  padding: 4px;
}
</style>
