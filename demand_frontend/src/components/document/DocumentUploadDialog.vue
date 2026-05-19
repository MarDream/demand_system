<template>
  <el-dialog v-model="visible" title="上传文档" width="600px" @close="resetUploadState">
    <el-form label-width="80px">
      <el-form-item v-if="showKnowledgeBaseSelect" label="知识库">
        <el-select v-model="selectedKbId" placeholder="请选择知识库" style="width: 100%">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="文件">
        <div
          class="upload-zone"
          :class="{ 'upload-zone--active': dialogDragOver }"
          @click="triggerFileSelect"
          @dragover.prevent="dialogDragOver = true"
          @dragleave.prevent="dialogDragOver = false"
          @drop.prevent="onDialogDrop"
        >
          <el-icon :size="24" class="upload-zone__icon"><Upload /></el-icon>
          <span class="upload-zone__text">点击选择文件或拖拽至此处</span>
          <span class="upload-zone__hint">支持 kkFileView 可预览的 {{ supportedExtensionCount }} 种扩展名类型</span>
        </div>
        <input ref="fileInputRef" type="file" multiple style="display: none" @change="onFileInputChange" />
        <input ref="folderInputRef" type="file" webkitdirectory multiple style="display: none" @change="onFolderInputChange" />
      </el-form-item>
      <el-form-item v-if="uploadFileList.length > 0" label="已选文件">
        <div class="selected-files">
          <div v-for="(file, index) in uploadFileList" :key="`${file.name}-${file.size}-${index}`" class="selected-file-item">
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ formatFileSize(file.size) }}</span>
            <el-button link type="danger" size="small" @click="removeFile(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <div class="selected-summary">共 {{ uploadFileList.length }} 个文件</div>
        </div>
      </el-form-item>
      <el-form-item>
        <div class="upload-actions">
          <el-button @click="triggerFolderSelect">选择文件夹</el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!effectiveKnowledgeBaseId || uploadFileList.length === 0" @click="handleUpload">
        确认上传 ({{ uploadFileList.length }})
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Delete } from '@element-plus/icons-vue'
import { getKnowledgeBases, uploadDocument, type KnowledgeBase } from '@/api/modules/knowledge'
import { KKFILEVIEW_SUPPORTED_EXTENSION_COUNT, KKFILEVIEW_SUPPORTED_EXTENSION_SET, normalizeFileExtension } from '@/constants/knowledgeDocument'

const props = withDefaults(defineProps<{
  modelValue: boolean
  knowledgeBaseId?: number | null
  showKnowledgeBaseSelect?: boolean
}>(), {
  knowledgeBaseId: null,
  showKnowledgeBaseSelect: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  uploaded: [payload: { successCount: number; failCount: number }]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const uploading = ref(false)
const selectedKbId = ref<number | null>(props.knowledgeBaseId)
const knowledgeBases = ref<KnowledgeBase[]>([])
const uploadFileList = ref<File[]>([])
const dialogDragOver = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const folderInputRef = ref<HTMLInputElement>()

const effectiveKnowledgeBaseId = computed(() => props.knowledgeBaseId || selectedKbId.value)
const supportedExtensionCount = KKFILEVIEW_SUPPORTED_EXTENSION_COUNT

watch(() => props.knowledgeBaseId, value => {
  selectedKbId.value = value || null
})

onMounted(async () => {
  if (!props.showKnowledgeBaseSelect) return
  try {
    const res = await getKnowledgeBases({ pageNum: 1, pageSize: 100 }) as any
    const data = res.data ?? res
    knowledgeBases.value = data.list ?? data ?? []
  } catch {
  }
})

function isFileAllowed(file: File): boolean {
  const ext = normalizeFileExtension(file.name)
  return !!ext && KKFILEVIEW_SUPPORTED_EXTENSION_SET.has(ext)
}

function addFiles(files: File[] | FileList) {
  let rejectedCount = 0
  for (const file of Array.from(files)) {
    if (!isFileAllowed(file)) {
      rejectedCount++
      continue
    }
    if (!uploadFileList.value.some(item => item.name === file.name && item.size === file.size)) {
      uploadFileList.value.push(file)
    }
  }
  if (rejectedCount > 0) {
    ElMessage.warning(`已忽略 ${rejectedCount} 个超出 kkFileView 支持范围的文件`)
  }
}

function openWithFiles(files?: File[] | FileList) {
  visible.value = true
  if (files) addFiles(files)
}

function removeFile(index: number) {
  uploadFileList.value.splice(index, 1)
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function onFileInputChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(input.files)
  input.value = ''
}

async function triggerFolderSelect() {
  const picker = (window as any).showDirectoryPicker
  if (typeof picker === 'function') {
    try {
      const directoryHandle = await picker()
      const files = await collectFilesFromDirectory(directoryHandle)
      addFiles(files)
      return
    } catch (error: any) {
      if (error?.name !== 'AbortError') {
        ElMessage.warning('选择文件夹失败，请重试')
      }
      return
    }
  }
  folderInputRef.value?.click()
}

function onFolderInputChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(input.files)
  input.value = ''
}

async function collectFilesFromDirectory(directoryHandle: any): Promise<File[]> {
  const files: File[] = []
  for await (const entry of directoryHandle.values()) {
    if (entry.kind === 'file') {
      files.push(await entry.getFile())
      continue
    }
    if (entry.kind === 'directory') {
      files.push(...await collectFilesFromDirectory(entry))
    }
  }
  return files
}

function onDialogDrop(e: DragEvent) {
  dialogDragOver.value = false
  const files = e.dataTransfer?.files
  if (files) addFiles(files)
}

async function handleUpload() {
  const knowledgeBaseId = effectiveKnowledgeBaseId.value
  if (!knowledgeBaseId || uploadFileList.value.length === 0) return

  uploading.value = true
  let successCount = 0
  let failCount = 0
  try {
    for (const file of uploadFileList.value) {
      try {
        await uploadDocument(knowledgeBaseId, file)
        successCount++
      } catch {
        failCount++
      }
    }
    if (successCount > 0) {
      ElMessage.success(`成功上传 ${successCount} 个文件${failCount > 0 ? `，${failCount} 个失败` : ''}，正在后台处理`)
      emit('uploaded', { successCount, failCount })
    } else {
      ElMessage.error('全部上传失败')
    }
    visible.value = false
    resetUploadState()
  } finally {
    uploading.value = false
  }
}

function resetUploadState() {
  uploadFileList.value = []
  selectedKbId.value = props.knowledgeBaseId || null
  dialogDragOver.value = false
}

defineExpose({
  addFiles,
  openWithFiles,
})
</script>

<style scoped>
.upload-zone {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
  width: 100%;
  box-sizing: border-box;
}

.upload-zone:hover,
.upload-zone:focus {
  border-color: var(--el-color-primary);
}

.upload-zone--active {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.04);
}

.upload-zone__icon {
  color: var(--el-text-color-placeholder);
}

.upload-zone:hover .upload-zone__icon,
.upload-zone--active .upload-zone__icon {
  color: var(--el-color-primary);
}

.upload-zone__text {
  display: block;
  font-size: 14px;
  color: var(--el-text-color-placeholder);
  margin-top: 8px;
}

.upload-zone:hover .upload-zone__text,
.upload-zone--active .upload-zone__text {
  color: var(--el-color-primary);
}

.upload-zone__hint {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}

.selected-files {
  width: 100%;
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 4px 0;
}

.selected-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  font-size: 13px;
}

.selected-file-item:hover {
  background: var(--el-fill-color-light);
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.selected-summary {
  padding: 4px 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-lighter);
  margin-top: 4px;
}

.upload-actions {
  display: flex;
  gap: 8px;
}
</style>
