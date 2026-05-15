<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="95%"
    :close-on-click-modal="false"
    :destroy-on-close="true"
    class="onlyoffice-dialog"
    @close="handleClose"
  >
    <div class="editor-wrapper">
      <div v-if="loading" class="loading-container">
        <el-icon class="loading-spinner"><Loading /></el-icon>
        <span>正在加载编辑器...</span>
      </div>
      <div v-else-if="error" class="error-container">
        <el-result
          icon="error"
          :title="errorTitle"
          :sub-title="errorMessage"
        >
          <template #extra>
            <el-button type="primary" @click="retry">重试</el-button>
          </template>
        </el-result>
      </div>
      <div v-else ref="editorPlaceholder" id="onlyoffice-editor-placeholder" class="editor-container"></div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getEditorConfig, getOnlyOfficeStatus } from '@/api/modules/onlyoffice'
import type { OnlyOfficeEditorConfig } from '@/api/modules/onlyoffice'

const props = defineProps<{
  modelValue: boolean
  knowledgeBaseId: number
  documentId: number
  documentName: string
  mode?: 'edit' | 'view'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'saved': []
  'error': [error: string]
}>()

declare global {
  interface Window {
    DocsAPI?: any
  }
}

const visible = ref(false)
const loading = ref(false)
const error = ref(false)
const errorTitle = ref('加载失败')
const errorMessage = ref('')
const editorPlaceholder = ref<HTMLElement>()
let editorInstance: any = null
let cachedApiJsUrl: string | null = null
let isRefreshingOnlyOffice = false
let readyObserver: MutationObserver | null = null
let readyTimer: number | null = null

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

const dialogTitle = computed(() => {
  const name = props.documentName || '文档'
  return props.mode === 'view' ? `预览 - ${name}` : `编辑 - ${name}`
})

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && !editorInstance) {
    loadEditor()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadEditor() {
  if (!props.knowledgeBaseId || !props.documentId) {
    showError('参数错误', '缺少必要参数')
    return
  }

  loading.value = true
  error.value = false

  try {
    const mode = props.mode || 'edit'
    const config: OnlyOfficeEditorConfig = await getEditorConfig(props.knowledgeBaseId, props.documentId, mode)
    await initOnlyOffice(buildEditorConfig(config, mode))
  } catch (err: any) {
    showError('获取编辑器配置失败', err.message || '请检查网络连接')
  } finally {
    loading.value = false
  }
}

function buildEditorConfig(config: OnlyOfficeEditorConfig, mode: 'edit' | 'view'): OnlyOfficeEditorConfig & { events: Record<string, any> } {
  return {
    ...config,
    events: {
      ...((config as any)?.events || {}),
      onAppReady: () => {
        markEditorReady()
      },
      onDocumentReady: () => {
        markEditorReady()
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

function markEditorReady() {
  loading.value = false
  error.value = false
  clearEditorReadyFallback()
}

function clearEditorReadyFallback() {
  if (readyObserver) {
    readyObserver.disconnect()
    readyObserver = null
  }
  if (readyTimer !== null) {
    window.clearTimeout(readyTimer)
    readyTimer = null
  }
}

function setupEditorReadyFallback() {
  clearEditorReadyFallback()
  const container = editorPlaceholder.value
  if (!container) return

  const armReadyTimer = () => {
    if (readyTimer !== null) {
      window.clearTimeout(readyTimer)
    }
    readyTimer = window.setTimeout(() => {
      if (container.querySelector('iframe')) {
        markEditorReady()
      }
    }, 1500)
  }

  const attachIframeListener = (iframe: HTMLIFrameElement) => {
    iframe.addEventListener('load', () => {
      window.setTimeout(() => {
        markEditorReady()
      }, 200)
    }, { once: true })
    armReadyTimer()
  }

  const existingIframe = container.querySelector('iframe')
  if (existingIframe instanceof HTMLIFrameElement) {
    attachIframeListener(existingIframe)
    return
  }

  readyObserver = new MutationObserver(() => {
    const iframe = container.querySelector('iframe')
    if (!(iframe instanceof HTMLIFrameElement)) return
    attachIframeListener(iframe)
    readyObserver?.disconnect()
    readyObserver = null
  })

  readyObserver.observe(container, {
    childList: true,
    subtree: true,
  })
}

async function refreshOnlyOffice(mode: 'edit' | 'view') {
  if (isRefreshingOnlyOffice) return
  isRefreshingOnlyOffice = true
  loading.value = true
  error.value = false
  cachedApiJsUrl = null
  try {
    handleClose()
    const config: OnlyOfficeEditorConfig = await getEditorConfig(props.knowledgeBaseId, props.documentId, mode)
    await initOnlyOffice(buildEditorConfig(config, mode), true)
  } catch (err: any) {
    showError('刷新文档失败', err?.message || '请稍后重试')
  } finally {
    isRefreshingOnlyOffice = false
  }
}

async function initOnlyOffice(config: OnlyOfficeEditorConfig, forceReload = false) {
  let apiJsUrl: string
  try {
    apiJsUrl = await resolveApiJsUrl(forceReload)
  } catch {
    showError('获取文档服务配置失败', '请检查网络连接')
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
    if (editorPlaceholder.value) {
      editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor-placeholder', config)
      setupEditorReadyFallback()
    }
    return
  }

  const existing = document.querySelector(`script[src="${apiJsUrl}"]`)
  if (existing) {
    await new Promise<void>((resolve) => {
      existing.addEventListener('load', () => resolve())
    })
    if (window.DocsAPI && editorPlaceholder.value) {
      editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor-placeholder', config)
      setupEditorReadyFallback()
    }
    return
  }

  await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = apiJsUrl
    script.onload = () => {
      if (window.DocsAPI) {
        setTimeout(() => {
          if (editorPlaceholder.value && window.DocsAPI) {
            editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor-placeholder', config)
            setupEditorReadyFallback()
          }
          resolve()
        }, 100)
      } else {
        showError('文档服务脚本加载失败', '请确认文档编辑服务已启动')
        resolve()
      }
    }
    script.onerror = () => {
      showError('文档服务脚本加载失败', '无法连接到文档编辑服务')
      resolve()
    }
    document.head.appendChild(script)
  })
}

function showError(title: string, message: string) {
  clearEditorReadyFallback()
  errorTitle.value = title
  errorMessage.value = message
  error.value = true
  emit('error', message)
}

function retry() {
  error.value = false
  loadEditor()
}

function handleClose() {
  clearEditorReadyFallback()
  if (editorInstance) {
    try {
      editorInstance.destroyEditor()
    } catch (e) {
      // ignore
    }
    editorInstance = null
  }
  if (editorPlaceholder.value) {
    editorPlaceholder.value.innerHTML = ''
  }
  emit('saved')
}

onBeforeUnmount(() => {
  handleClose()
})
</script>

<style scoped lang="scss">
.onlyoffice-dialog {
  :deep(.el-dialog__body) {
    padding: 0;
    height: calc(100vh - 120px);
    max-height: 800px;
  }
}

.editor-wrapper {
  width: 100%;
  height: 100%;
  background: #f5f5f5;
  position: relative;
}

.editor-container {
  width: 100%;
  height: 100%;
  min-height: 600px;

  :deep(iframe) {
    width: 100%;
    height: 100%;
    border: none;
  }
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
  color: #666;

  .loading-spinner {
    font-size: 32px;
    animation: rotate 1s linear infinite;
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.error-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
