<template>
  <div class="share-page">
    <div class="share-header">
      <div class="share-header__meta">
        <h1 class="share-header__title">{{ context?.fileName || '文档分享' }}</h1>
        <p class="share-header__desc">
          <span v-if="context?.expireAt">有效期至 {{ formatDate(context.expireAt) }}</span>
          <span v-else>公开文档分享</span>
        </p>
      </div>
      <div class="share-header__actions">
        <el-button v-if="downloadUrl" type="primary" @click="downloadFile">
          <el-icon><Download /></el-icon>
          <span>下载文件</span>
        </el-button>
        <el-button @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </el-button>
      </div>
    </div>

    <div ref="previewContainerRef" class="share-preview" :class="{ 'share-preview--fullscreen': isFullscreen }">
      <div v-if="pageLoading" class="share-state">
        <el-icon class="share-state__spin" :size="32"><Loading /></el-icon>
        <span>正在加载分享内容...</span>
      </div>

      <el-result
        v-else-if="errorMessage"
        icon="error"
        title="访问失败"
        :sub-title="errorMessage"
      />

      <template v-else-if="context">
        <div v-if="previewType === 'office'" class="share-office-wrap">
          <div v-if="!onlyOfficeReady" class="share-state share-state--overlay">
            <el-icon class="share-state__spin" :size="32"><Loading /></el-icon>
            <span>{{ onlyOfficeAvailable ? '正在加载文档预览...' : '在线预览不可用，请直接下载文件' }}</span>
          </div>
          <div
            id="public-onlyoffice-editor"
            class="share-office"
            :class="{ 'share-office--hidden': !onlyOfficeReady }"
          ></div>
        </div>

        <div v-else-if="previewType === 'image'" class="share-image-wrap">
          <img :src="fileUrl" :alt="context.fileName" class="share-image" />
        </div>

        <div v-else-if="previewType === 'text'" class="share-text-wrap">
          <pre class="share-text">{{ textContent }}</pre>
        </div>

        <div v-else class="share-state">
          <el-icon :size="40"><Document /></el-icon>
          <span>该文件类型不支持在线预览，请下载后查看。</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Document, Download, FullScreen, Loading } from '@element-plus/icons-vue'
import { getPublicShareContext, type PublicShareContext } from '@/api/modules/publicShare'
import { getOnlyOfficeStatus, getPublicEditorConfig } from '@/api/modules/onlyoffice'
import { formatDate } from '@/utils/format'

const IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg']
const TEXT_TYPES = ['txt', 'md', 'csv', 'json', 'xml', 'log', 'yml', 'yaml']
const OFFICE_TYPES = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']

const route = useRoute()

const pageLoading = ref(true)
const errorMessage = ref('')
const context = ref<PublicShareContext | null>(null)
const textContent = ref('')
const fileUrl = ref('')
const downloadUrl = ref('')
const previewContainerRef = ref<HTMLElement>()
const isFullscreen = ref(false)
const onlyOfficeReady = ref(false)
const onlyOfficeAvailable = ref(true)

let editorInstance: any = null
let cachedApiJsUrl: string | null = null
let readyObserver: MutationObserver | null = null
let readyTimer: number | null = null

declare global {
  interface Window {
    DocsAPI?: any
  }
}

const previewType = computed(() => {
  const ext = (context.value?.fileType || '').toLowerCase()
  if (IMAGE_TYPES.includes(ext)) return 'image'
  if (TEXT_TYPES.includes(ext)) return 'text'
  if (OFFICE_TYPES.includes(ext)) return 'office'
  return 'unsupported'
})

watch(
  () => route.params.token,
  async token => {
    if (typeof token !== 'string' || !token) return
    await loadShare(token)
  },
  { immediate: true }
)

async function loadShare(token: string) {
  pageLoading.value = true
  errorMessage.value = ''
  context.value = null
  textContent.value = ''
  fileUrl.value = ''
  downloadUrl.value = ''
  onlyOfficeAvailable.value = true
  onlyOfficeReady.value = false
  destroyOnlyOffice()

  try {
    const share = await getPublicShareContext(token)
    context.value = share
    const accessToken = encodeURIComponent(share.accessToken)
    fileUrl.value = `/api/v1/public/knowledge/shares/${share.shareToken}/file?accessToken=${accessToken}`
    downloadUrl.value = `/api/v1/public/knowledge/shares/${share.shareToken}/download?accessToken=${accessToken}`

    if (previewType.value === 'text') {
      const resp = await fetch(fileUrl.value)
      textContent.value = await resp.text()
    } else if (previewType.value === 'office') {
      await loadOnlyOffice(share.accessToken)
    }
  } catch (error: any) {
    errorMessage.value = error?.message || '分享链接无法访问'
  } finally {
    pageLoading.value = false
  }
}

async function loadOnlyOffice(accessToken: string) {
  onlyOfficeReady.value = false
  try {
    const config = await getPublicEditorConfig(accessToken, 'view')
    await loadOnlyOfficeScript(config)
  } catch (error: any) {
    onlyOfficeAvailable.value = false
    errorMessage.value = error?.message || '在线预览不可用'
  }
}

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

async function loadOnlyOfficeScript(config: any) {
  const apiJsUrl = await resolveApiJsUrl()
  if (window.DocsAPI) {
    await initEditor(config)
    return
  }

  const existing = document.querySelector(`script[src="${apiJsUrl}"]`)
  if (existing) {
    await new Promise<void>(resolve => existing.addEventListener('load', () => resolve(), { once: true }))
    await initEditor(config)
    return
  }

  await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = apiJsUrl
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('文档服务脚本加载失败'))
    document.head.appendChild(script)
  })

  await initEditor(config)
}

async function initEditor(config: any) {
  await nextTick()
  if (!window.DocsAPI) {
    throw new Error('文档服务脚本未就绪')
  }
  editorInstance = new window.DocsAPI.DocEditor('public-onlyoffice-editor', {
    ...config,
    events: {
      ...(config?.events || {}),
      onAppReady: markOnlyOfficeReady,
      onDocumentReady: markOnlyOfficeReady,
    },
  })
  setupReadyFallback()
}

function setupReadyFallback() {
  clearReadyFallback()
  const container = document.getElementById('public-onlyoffice-editor')
  if (!container) return

  const attachIframeListener = (iframe: HTMLIFrameElement) => {
    iframe.addEventListener('load', () => {
      window.setTimeout(markOnlyOfficeReady, 200)
    }, { once: true })
    readyTimer = window.setTimeout(() => {
      if (container.querySelector('iframe')) {
        markOnlyOfficeReady()
      }
    }, 1500)
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

  readyObserver.observe(container, { childList: true, subtree: true })
}

function markOnlyOfficeReady() {
  onlyOfficeReady.value = true
  clearReadyFallback()
}

function clearReadyFallback() {
  if (readyObserver) {
    readyObserver.disconnect()
    readyObserver = null
  }
  if (readyTimer !== null) {
    window.clearTimeout(readyTimer)
    readyTimer = null
  }
}

function destroyOnlyOffice() {
  clearReadyFallback()
  if (editorInstance) {
    try {
      editorInstance.destroyEditor()
    } catch {
      // ignore
    }
    editorInstance = null
  }
  const container = document.getElementById('public-onlyoffice-editor')
  if (container) {
    container.innerHTML = ''
  }
}

function downloadFile() {
  if (!downloadUrl.value) return
  const anchor = document.createElement('a')
  anchor.href = downloadUrl.value
  anchor.download = context.value?.fileName || 'document'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}

function toggleFullscreen() {
  const el = previewContainerRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen?.()
    return
  }
  document.exitFullscreen?.()
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

document.addEventListener('fullscreenchange', onFullscreenChange)

onBeforeUnmount(() => {
  destroyOnlyOffice()
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.12), transparent 30%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

.share-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 20px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
}

.share-header__title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #0f172a;
}

.share-header__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.share-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.share-preview {
  min-height: calc(100vh - 160px);
  border-radius: 24px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.1);
}

.share-preview--fullscreen {
  border-radius: 0;
}

.share-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 160px);
  gap: 12px;
  color: #64748b;
}

.share-state--overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: rgba(255, 255, 255, 0.94);
}

.share-state__spin {
  animation: share-spin 1s linear infinite;
}

@keyframes share-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.share-office-wrap {
  position: relative;
  min-height: calc(100vh - 160px);
}

.share-office {
  min-height: calc(100vh - 160px);
}

.share-office--hidden {
  visibility: hidden;
}

.share-image-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 160px);
  padding: 24px;
  overflow: auto;
}

.share-image {
  max-width: 100%;
  max-height: calc(100vh - 220px);
  object-fit: contain;
}

.share-text-wrap {
  min-height: calc(100vh - 160px);
  padding: 24px;
  background: #0f172a;
  overflow: auto;
}

.share-text {
  margin: 0;
  color: #e2e8f0;
  font-family: Consolas, Monaco, "Courier New", monospace;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 768px) {
  .share-page {
    padding: 12px;
  }

  .share-header {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }

  .share-header__actions {
    justify-content: flex-end;
  }
}
</style>
