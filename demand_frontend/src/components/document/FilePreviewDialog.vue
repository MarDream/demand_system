<template>
  <AppDialog
    v-model="visible"
    :title="fileName || '文件预览'"
    :show-footer="false"
    width="95%"
    top="3vh"
    @close="handleClose"
  >
    <template #header-actions>
      <el-button
        v-if="previewType === 'office' && onlyOfficeAvailable && !onlyOfficeMode"
        size="small"
        @click="switchToEditMode"
      >
        <el-icon><EditPen /></el-icon>
        <span>切换到编辑模式</span>
      </el-button>
      <el-button type="primary" size="small" :loading="downloading" @click="handleDownload">
        <el-icon><Download /></el-icon>
        <span>下载文件</span>
      </el-button>
      <el-button size="small" @click="toggleFullscreen">
        <el-icon><FullScreen /></el-icon>
      </el-button>
    </template>
    <div class="preview-toolbar">
      <div class="zoom-controls">
        <el-button size="small" :disabled="zoomLevel <= 25" @click="zoomOut">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <span class="zoom-label">{{ zoomLevel }}%</span>
        <el-button size="small" :disabled="zoomLevel >= 300" @click="zoomIn">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button size="small" @click="zoomReset">重置</el-button>
      </div>
    </div>
    <div ref="previewContainerRef" class="preview-container" :class="{ 'preview-container--fullscreen': isFullscreen }">
      <div v-if="onlyOfficeMode" class="preview-onlyoffice-wrap" :style="{ transform: onlyOfficeZoomTransform, transformOrigin: 'top left' }">
        <div v-if="!onlyOfficeReady" class="preview-loading preview-overlay">
          <el-icon class="preview-spin" :size="32"><Loading /></el-icon>
          <span>{{ onlyOfficeAvailable ? '正在加载文档编辑器...' : '文档编辑服务不可用，请下载后查看' }}</span>
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
      <div v-else-if="previewType === 'image'" class="preview-image-wrap">
        <img :src="fileUrl" :alt="fileName" class="preview-image" :style="{ transform: `scale(${zoomLevel / 100})` }" />
      </div>
      <div v-else-if="previewType === 'text'" class="preview-text-wrap" :style="{ fontSize: `${zoomLevel / 100 * 13}px` }">
        <pre class="preview-text">{{ textContent }}</pre>
      </div>
      <div v-else class="preview-unsupported">
        <el-icon :size="48"><Document /></el-icon>
        <p>{{ unsupportedMessage }}</p>
      </div>
    </div>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Loading, Document, EditPen, ZoomIn, ZoomOut, FullScreen } from '@element-plus/icons-vue'
import AppDialog from '@/components/common/AppDialog.vue'
import { downloadDocumentBlob, getDocumentPreviewUrl } from '@/api/modules/knowledge'
import { getEditorConfig, getOnlyOfficeStatus } from '@/api/modules/onlyoffice'

const IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg']
const TEXT_TYPES = ['txt', 'md', 'csv', 'json', 'xml', 'log', 'yml', 'yaml']
const OFFICE_TYPES = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']

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
const downloading = ref(false)
const fileUrl = ref('')
const textContent = ref('')
const onlyOfficeMode = ref(false)
const onlyOfficeReady = ref(false)
const onlyOfficeAvailable = ref(true)
const onlyOfficeCurrentMode = ref<'view' | 'edit'>('view')
let editorInstance: any = null
let cachedApiJsUrl: string | null = null
let isRefreshingOnlyOffice = false
let onlyOfficeObserver: MutationObserver | null = null
let onlyOfficeReadyTimer: number | null = null

async function resolveApiJsUrl(forceReload = false): Promise<string> {
  if (cachedApiJsUrl && !forceReload) return cachedApiJsUrl
  const status = await getOnlyOfficeStatus()
  if (!status.available || !status.apiJsUrl) {
    throw new Error(status.message || '文档编辑服务不可用')
  }
  cachedApiJsUrl = status.apiJsUrl
  if (!forceReload) return cachedApiJsUrl
  const sep = cachedApiJsUrl.includes('?') ? '&' : '?'
  return `${cachedApiJsUrl}${sep}_dc=${Date.now()}`
}

const zoomLevel = ref(100)
const isFullscreen = ref(false)
const previewContainerRef = ref<HTMLElement>()

declare global {
  interface Window {
    DocsAPI?: any
  }
}

const previewType = computed(() => {
  const ext = (props.fileType || '').toLowerCase()
  if (IMAGE_TYPES.includes(ext)) return 'image'
  if (TEXT_TYPES.includes(ext)) return 'text'
  if (OFFICE_TYPES.includes(ext)) return 'office'
  return 'unsupported'
})

const unsupportedMessage = computed(() => {
  if (previewType.value === 'office' && !onlyOfficeAvailable.value) {
    return '文档编辑服务不可用，当前文档暂不支持在线预览'
  }
  return '该文件类型暂不支持在线预览'
})

watch(() => props.modelValue, async (open) => {
  if (!open) {
    destroyOnlyOffice()
    if (isFullscreen.value) {
      try { document.exitFullscreen() } catch { /* ignore */ }
    }
    return
  }
  textContent.value = ''
  fileUrl.value = ''
  loading.value = false
  onlyOfficeAvailable.value = true
  onlyOfficeMode.value = false
  onlyOfficeReady.value = false
  zoomLevel.value = 100

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
  destroyOnlyOffice(true)
  onlyOfficeCurrentMode.value = mode
  onlyOfficeAvailable.value = true
  onlyOfficeMode.value = true
  onlyOfficeReady.value = false
  loading.value = true

  try {
    const config = await getEditorConfig(props.knowledgeBaseId, props.documentId, mode)
    loadOnlyOfficeScript(buildEditorConfig(config, mode))
  } catch {
    onlyOfficeAvailable.value = false
    onlyOfficeMode.value = false
    loading.value = false
  }
}

function buildEditorConfig(config: any, mode: 'view' | 'edit') {
  return {
    ...config,
    events: {
      ...(config?.events || {}),
      onAppReady: () => {
        markOnlyOfficeReady()
      },
      onDocumentReady: () => {
        markOnlyOfficeReady()
      },
      onOutdatedVersion: () => {
        refreshOnlyOffice(mode)
      },
      onRequestRefreshFile: () => {
        refreshOnlyOffice(mode)
      },
      onRequestClose: () => {
        visible.value = false
      }
    }
  }
}

function markOnlyOfficeReady() {
  onlyOfficeReady.value = true
  loading.value = false
  clearOnlyOfficeReadyFallback()
}

function clearOnlyOfficeReadyFallback() {
  if (onlyOfficeObserver) {
    onlyOfficeObserver.disconnect()
    onlyOfficeObserver = null
  }
  if (onlyOfficeReadyTimer !== null) {
    window.clearTimeout(onlyOfficeReadyTimer)
    onlyOfficeReadyTimer = null
  }
}

function setupOnlyOfficeReadyFallback() {
  clearOnlyOfficeReadyFallback()
  const container = document.getElementById('onlyoffice-editor-placeholder')
  if (!container) return

  const armReadyTimer = () => {
    if (onlyOfficeReady.value) return
    if (onlyOfficeReadyTimer !== null) {
      window.clearTimeout(onlyOfficeReadyTimer)
    }
    onlyOfficeReadyTimer = window.setTimeout(() => {
      if (container.querySelector('iframe')) {
        markOnlyOfficeReady()
      }
    }, 1500)
  }

  const attachIframeListener = (iframe: HTMLIFrameElement) => {
    iframe.addEventListener('load', () => {
      window.setTimeout(() => {
        markOnlyOfficeReady()
      }, 200)
    }, { once: true })
    armReadyTimer()
  }

  const existingIframe = container.querySelector('iframe')
  if (existingIframe instanceof HTMLIFrameElement) {
    attachIframeListener(existingIframe)
    return
  }

  onlyOfficeObserver = new MutationObserver(() => {
    const iframe = container.querySelector('iframe')
    if (!(iframe instanceof HTMLIFrameElement)) return
    attachIframeListener(iframe)
    onlyOfficeObserver?.disconnect()
    onlyOfficeObserver = null
  })

  onlyOfficeObserver.observe(container, {
    childList: true,
    subtree: true,
  })
}

async function refreshOnlyOffice(mode: 'view' | 'edit') {
  if (isRefreshingOnlyOffice) return
  isRefreshingOnlyOffice = true
  cachedApiJsUrl = null
  onlyOfficeReady.value = false
  loading.value = true
  try {
    destroyOnlyOffice(true)
    const config = await getEditorConfig(props.knowledgeBaseId, props.documentId, mode)
    await loadOnlyOfficeScript(buildEditorConfig(config, mode), true)
  } catch {
    handleOnlyOfficeError()
  } finally {
    isRefreshingOnlyOffice = false
  }
}

async function loadOnlyOfficeScript(config: any, forceReload = false) {
  let apiJsUrl: string
  try {
    apiJsUrl = await resolveApiJsUrl(forceReload)
  } catch {
    handleOnlyOfficeError()
    return
  }

  if (forceReload) {
    document
      .querySelectorAll('script[src*="/web-apps/apps/api/documents/api.js"]')
      .forEach(node => node.parentNode?.removeChild(node))
    try {
      delete window.DocsAPI
    } catch {
      window.DocsAPI = undefined
    }
  }

  if (window.DocsAPI && !forceReload) {
    initEditor(config)
    return
  }

  const script = document.createElement('script')
  script.src = apiJsUrl
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
    editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor-placeholder', config)
    setupOnlyOfficeReadyFallback()
  } else {
    handleOnlyOfficeError()
  }
}

function handleOnlyOfficeError() {
  clearOnlyOfficeReadyFallback()
  onlyOfficeAvailable.value = false
  onlyOfficeMode.value = false
  loading.value = false
}

const onlyOfficeZoomTransform = computed(() => {
  if (!onlyOfficeMode.value || zoomLevel.value === 100) return undefined
  const container = document.querySelector('.preview-onlyoffice-wrap') as HTMLElement
  if (!container) return undefined
  const width = container.clientWidth
  return `scale(${zoomLevel.value / 100})`
})

function zoomIn() {
  zoomLevel.value = Math.min(300, zoomLevel.value + 25)
}

function zoomOut() {
  zoomLevel.value = Math.max(25, zoomLevel.value - 25)
}

function zoomReset() {
  zoomLevel.value = 100
}

function toggleFullscreen() {
  const el = previewContainerRef.value
  if (!el) return
  if (!isFullscreen.value) {
    if (el.requestFullscreen) el.requestFullscreen()
    else if ((el as any).webkitRequestFullscreen) (el as any).webkitRequestFullscreen()
    else if ((el as any).msRequestFullscreen) (el as any).msRequestFullscreen()
  } else {
    if (document.exitFullscreen) document.exitFullscreen()
    else if ((document as any).webkitExitFullscreen) (document as any).webkitExitFullscreen()
    else if ((document as any).msExitFullscreen) (document as any).msExitFullscreen()
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

async function switchToEditMode() {
  await openWithOnlyOffice('edit')
}

function destroyOnlyOffice(preserveMode = false) {
  clearOnlyOfficeReadyFallback()
  if (editorInstance) {
    try {
      editorInstance.destroyEditor()
    } catch (e) {
      // ignore
    }
    editorInstance = null
  }
  if (!preserveMode) {
    onlyOfficeMode.value = false
  }
  onlyOfficeReady.value = false
  const container = document.getElementById('onlyoffice-editor-placeholder')
  if (container) {
    container.innerHTML = ''
  }
}

async function handleDownload() {
  if (downloading.value) return
  downloading.value = true
  try {
    const blob = await downloadDocumentBlob(props.knowledgeBaseId, props.documentId)
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = props.fileName || 'document'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载文件失败')
  } finally {
    downloading.value = false
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
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

document.addEventListener('fullscreenchange', onFullscreenChange)
</script>

<style scoped>
.preview-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.zoom-controls {
  display: flex;
  align-items: center;
  gap: 4px;
}
.zoom-label {
  min-width: 48px;
  text-align: center;
  font-size: 13px;
  color: #606266;
}
.preview-container {
  min-height: 60vh;
  max-height: 75vh;
  overflow: hidden;
}
.preview-container--fullscreen {
  max-height: none;
  height: 100vh;
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
  overflow: hidden;
}
.editor-container {
  flex: 1;
  min-height: 600px;
  overflow: hidden;
}
.editor-container :deep(div[id^="onlyoffice-editor-placeholder"]) {
  overflow: hidden;
}
.editor-container :deep(iframe) {
  overflow: hidden;
}
.editor-container :deep(.EmbeddedViewer) {
  overflow: hidden;
}
.editor-container :deep([class*="left-panel"]),
.editor-container :deep([class*="formpreview"]),
.editor-container :deep(a[href*="onlyoffice"]) {
  display: none !important;
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
</style>
