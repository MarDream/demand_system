<template>
  <AppDialog v-model="visible" :title="fileName || '文件预览'" width="95%" top="3vh" @close="handleClose">
    <div class="preview-container">
      <div v-if="onlyOfficeMode" class="preview-onlyoffice-wrap">
        <div v-if="!onlyOfficeReady" class="preview-loading preview-overlay">
          <el-icon class="preview-spin" :size="32"><Loading /></el-icon>
          <span>{{ onlyOfficeAvailable ? '正在加载 OnlyOffice 编辑器...' : 'OnlyOffice 服务不可用，请下载后查看' }}</span>
        </div>
        <div
          id="onlyoffice-editor-placeholder"
          class="editor-container"
          :class="{ 'editor-container--hidden': !onlyOfficeReady }"
        ></div>
      </div>
      <div v-else-if="loading" class="preview-loading">
        <el-icon class="preview-spin" :size="32"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="previewType === 'pdf'" class="preview-iframe-wrap">
        <iframe :src="fileUrl" class="preview-iframe" frameborder="0" />
      </div>
      <div v-else-if="previewType === 'image'" class="preview-image-wrap">
        <img :src="fileUrl" :alt="fileName" class="preview-image" />
      </div>
      <div v-else-if="previewType === 'text'" class="preview-text-wrap">
        <pre class="preview-text">{{ textContent }}</pre>
      </div>
      <div v-else class="preview-unsupported">
        <el-icon :size="48"><Document /></el-icon>
        <p>{{ unsupportedMessage }}</p>
      </div>
    </div>
    <template #footer>
      <div class="footer-actions">
        <el-button v-if="previewType === 'office' && onlyOfficeAvailable && !onlyOfficeMode" @click="switchToEditMode">
          <el-icon><EditPen /></el-icon>
          <span>切换到编辑模式</span>
        </el-button>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="handleDownload">
          <el-icon><Download /></el-icon>
          <span>下载文件</span>
        </el-button>
      </div>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { Download, Loading, Document, EditPen } from '@element-plus/icons-vue'
import AppDialog from '@/components/common/AppDialog.vue'
import { getDocumentPreviewUrl } from '@/api/modules/knowledge'
import { getEditorConfig } from '@/api/modules/onlyoffice'

const IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg']
const TEXT_TYPES = ['txt', 'md', 'csv', 'json', 'xml', 'log', 'yml', 'yaml']
const PDF_TYPES = ['pdf']
const OFFICE_TYPES = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']

const props = defineProps<{
  modelValue: boolean
  fileName: string
  fileType: string
  knowledgeBaseId: number
  documentId: number
  downloadUrl: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'saved': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const loading = ref(false)
const fileUrl = ref('')
const textContent = ref('')
const onlyOfficeMode = ref(false)
const onlyOfficeReady = ref(false)
const onlyOfficeAvailable = ref(true)
let editorInstance: any = null

declare global {
  interface Window {
    DocsAPI?: any
  }
}

const previewType = computed(() => {
  const ext = (props.fileType || '').toLowerCase()
  if (PDF_TYPES.includes(ext)) return 'pdf'
  if (IMAGE_TYPES.includes(ext)) return 'image'
  if (TEXT_TYPES.includes(ext)) return 'text'
  if (OFFICE_TYPES.includes(ext)) return 'office'
  return 'unsupported'
})

const unsupportedMessage = computed(() => {
  if (previewType.value === 'office' && !onlyOfficeAvailable.value) {
    return 'OnlyOffice 服务不可用，当前 Office 文档暂不支持在线预览'
  }
  return '该文件类型暂不支持在线预览'
})

watch(() => props.modelValue, async (open) => {
  if (!open) {
    destroyOnlyOffice()
    return
  }
  textContent.value = ''
  fileUrl.value = ''
  loading.value = false
  onlyOfficeAvailable.value = true
  onlyOfficeMode.value = false
  onlyOfficeReady.value = false

  if (previewType.value === 'office') {
    await openWithOnlyOffice('view')
    return
  }

  loading.value = true
  try {
    const res = await getDocumentPreviewUrl(props.knowledgeBaseId, props.documentId) as any
    const url = res.data ?? res

    if (previewType.value === 'text') {
      const resp = await fetch(url)
      textContent.value = await resp.text()
    } else {
      fileUrl.value = url
    }
  } catch {
    fileUrl.value = ''
    textContent.value = '加载失败'
  } finally {
    loading.value = false
  }
})

async function openWithOnlyOffice(mode: 'view' | 'edit') {
  destroyOnlyOffice()
  onlyOfficeAvailable.value = true
  onlyOfficeMode.value = true
  onlyOfficeReady.value = false
  loading.value = true

  try {
    const config = await getEditorConfig(props.knowledgeBaseId, props.documentId, mode)
    loadOnlyOfficeScript(config)
  } catch {
    onlyOfficeAvailable.value = false
    onlyOfficeMode.value = false
    loading.value = false
  }
}

function loadOnlyOfficeScript(config: any) {
  // 如果已有 DocsAPI，直接初始化
  if (window.DocsAPI) {
    initEditor(config)
    return
  }

  const script = document.createElement('script')
  script.src = 'http://localhost:8443/web-apps/apps/api/documents/api.js'
  script.onload = () => {
    if (window.DocsAPI) {
      initEditor(config)
    } else {
      handleOnlyOfficeError()
    }
  }
  script.onerror = () => {
    handleOnlyOfficeError()
  }
  document.head.appendChild(script)
}

async function initEditor(config: any) {
  await nextTick()
  const container = document.getElementById('onlyoffice-editor-placeholder')
  if (container && window.DocsAPI) {
    onlyOfficeReady.value = true
    editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor-placeholder', config)
    loading.value = false
  } else {
    handleOnlyOfficeError()
  }
}

function handleOnlyOfficeError() {
  onlyOfficeAvailable.value = false
  onlyOfficeMode.value = false
  loading.value = false
}

async function switchToEditMode() {
  await openWithOnlyOffice('edit')
}

function destroyOnlyOffice() {
  if (editorInstance) {
    try {
      editorInstance.destroyEditor()
    } catch (e) {
      // ignore
    }
    editorInstance = null
  }
  onlyOfficeMode.value = false
  onlyOfficeReady.value = false
}

function handleDownload() {
  if (props.downloadUrl) {
    window.open(props.downloadUrl, '_blank')
  }
}

function handleClose() {
  destroyOnlyOffice()
  fileUrl.value = ''
  textContent.value = ''
  emit('saved')
}

onBeforeUnmount(() => {
  destroyOnlyOffice()
})
</script>

<style scoped>
.preview-container {
  min-height: 60vh;
  max-height: 75vh;
  overflow: hidden;
}
.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
  color: #909399;
}
.preview-spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.preview-iframe-wrap {
  height: 75vh;
}
.preview-iframe {
  width: 100%;
  height: 100%;
}
.preview-image-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  max-height: 75vh;
  overflow: auto;
}
.preview-image {
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
}
.preview-text-wrap {
  max-height: 75vh;
  overflow: auto;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 16px;
}
.preview-text {
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
  color: #909399;
}
.preview-onlyoffice-wrap {
  position: relative;
  height: 75vh;
  display: flex;
  flex-direction: column;
}
.editor-container {
  flex: 1;
  min-height: 600px;
}
.editor-container--hidden {
  visibility: hidden;
}
.preview-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: rgba(255, 255, 255, 0.92);
}
.footer-actions {
  display: flex;
  gap: 8px;
}
</style>
