<template>
  <AppDialog
    v-model="visible"
    class="file-preview-dialog"
    :title="fileName || '文件预览'"
    :show-footer="false"
    width="95%"
    top="3vh"
    @close="handleClose"
  >
    <template #header-actions>
      <el-button size="small" :loading="downloading" @click="handleDownload" title="下载文件">
        <el-icon><Download /></el-icon>
      </el-button>
      <el-button size="small" @click="toggleFullscreen" title="全屏">
        <el-icon><FullScreen /></el-icon>
      </el-button>
      <div v-if="previewType !== 'office'" class="zoom-toolbar">
        <el-button size="small" :disabled="zoom <= 50" title="缩小" @click="zoomOut">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <span class="zoom-level">{{ zoom }}%</span>
        <el-button size="small" :disabled="zoom >= 200" title="放大" @click="zoomIn">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button size="small" title="重置为100%" @click="resetZoom">
          <el-icon><RefreshLeft /></el-icon>
        </el-button>
      </div>
    </template>
    <div ref="previewContainerRef" class="preview-container" :class="{ 'preview-container--fullscreen': isFullscreen }">
      <div v-if="previewType === 'office'" class="preview-office-wrap">
        <iframe
          v-if="officePreviewUrl"
          :src="officePreviewUrl"
          class="preview-iframe"
          frameborder="0"
          allowfullscreen
          @load="handleEmbeddedPreviewLoaded"
        ></iframe>
      </div>
      <div v-else-if="previewType === 'image'" class="preview-image-wrap">
        <img
          v-if="fileUrl"
          :src="fileUrl"
          :alt="fileName"
          class="preview-image"
          :style="{ transform: `scale(${zoom / 100})` }"
          @load="handleVisualPreviewLoaded"
          @error="handleVisualPreviewError"
        />
      </div>
      <div v-else-if="previewType === 'text'" class="preview-text-wrap" :style="{ fontSize: `${zoom}%` }">
        <pre class="preview-text">{{ textContent }}</pre>
      </div>
      <div v-else class="preview-unsupported">
        <el-icon :size="48"><Document /></el-icon>
        <p>该文件类型暂不支持在线预览</p>
      </div>
      <div v-if="previewLoading" class="preview-loading-mask">
        <div class="preview-loading-card">
          <div class="preview-loader">
            <span class="preview-loader__sheet preview-loader__sheet--back"></span>
            <span class="preview-loader__sheet preview-loader__sheet--mid"></span>
            <span class="preview-loader__sheet preview-loader__sheet--front">
              <span class="preview-loader__eyes">
                <i></i>
                <i></i>
              </span>
              <span class="preview-loader__smile"></span>
            </span>
          </div>
          <div class="preview-loading__title">正在准备文档预览</div>
          <div class="preview-loading__desc">{{ loadingMessage }}</div>
          <div class="preview-loading__dots" aria-hidden="true">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>
  </AppDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Document, FullScreen, ZoomIn, ZoomOut, RefreshLeft } from '@element-plus/icons-vue'
import AppDialog from '@/components/common/AppDialog.vue'
import { downloadDocumentBlob, getDocumentPreviewUrl } from '@/api/modules/knowledge'
import { getOfficePreviewUrl } from '@/api/modules/preview'
import { PREVIEW_IMAGE_SET, PREVIEW_SUPPORTED_EXTENSION_SET, PREVIEW_TEXT_SET, normalizeFileExtension } from '@/constants/knowledgeDocument'
import { useUserStore } from '@/stores/modules/user'

const PREVIEW_LOADING_MIN_DURATION = 500

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
  'downloaded': []
}>()

const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const downloading = ref(false)
const fileUrl = ref('')
const textContent = ref('')
const officePreviewUrl = ref('')
const isFullscreen = ref(false)
const previewContainerRef = ref<HTMLElement>()
const zoom = ref(100)
const previewLoading = ref(false)
const loadingMessage = ref('正在为你整理预览内容...')

let previewLoadingStartedAt = 0
let previewLoadingTimer: ReturnType<typeof setTimeout> | null = null

const previewType = computed(() => {
  const ext = normalizeFileExtension(props.fileType)
  if (PREVIEW_IMAGE_SET.has(ext)) return 'image'
  if (PREVIEW_TEXT_SET.has(ext)) return 'text'
  if (PREVIEW_SUPPORTED_EXTENSION_SET.has(ext)) return 'office'
  return 'unsupported'
})

watch(() => props.modelValue, async (open) => {
  if (!open) {
    if (isFullscreen.value) {
      try { document.exitFullscreen() } catch { /* ignore */ }
    }
    zoom.value = 100
    resetPreviewState()
    return
  }
  resetPreviewState()
  zoom.value = 100

  if (previewType.value === 'unsupported') {
    return
  }

  beginPreviewLoading(getLoadingMessage(previewType.value))

  if (previewType.value === 'office') {
    try {
      const watermark = buildWatermark()
      const previewRes = await getOfficePreviewUrl({
        knowledgeBaseId: props.knowledgeBaseId,
        documentId: props.documentId,
        watermarkTxt: watermark || undefined,
      }) as any
      officePreviewUrl.value = previewRes.data?.previewUrl ?? previewRes.previewUrl ?? ''
    } catch {
      officePreviewUrl.value = ''
      endPreviewLoading()
      ElMessage.error('获取预览地址失败')
    }
    return
  }

  try {
    const res = await getDocumentPreviewUrl(props.knowledgeBaseId, props.documentId) as any
    const url = res.data ?? res

    if (previewType.value === 'text') {
      const resp = await fetch(url)
      textContent.value = await resp.text()
      endPreviewLoading()
    } else {
      fileUrl.value = url
    }
  } catch {
    fileUrl.value = ''
    textContent.value = '加载失败'
    endPreviewLoading()
  }
})

function getLoadingMessage(type: string) {
  if (type === 'office') {
    return '正在转换文档格式，请稍候...'
  }
  if (type === 'image') {
    return '图片正在展开，马上就好。'
  }
  return '正在整理文本内容，请稍候。'
}

function beginPreviewLoading(message: string) {
  if (previewLoadingTimer) {
    clearTimeout(previewLoadingTimer)
    previewLoadingTimer = null
  }
  loadingMessage.value = message
  previewLoadingStartedAt = Date.now()
  previewLoading.value = true
}

/**
 * 模拟打字机效果的加载提示更新
 */
function updateLoadingMessage(message: string) {
  loadingMessage.value = message
}

function endPreviewLoading() {
  if (!previewLoading.value) return
  const remaining = PREVIEW_LOADING_MIN_DURATION - (Date.now() - previewLoadingStartedAt)
  if (remaining <= 0) {
    previewLoading.value = false
    return
  }
  if (previewLoadingTimer) {
    clearTimeout(previewLoadingTimer)
  }
  previewLoadingTimer = setTimeout(() => {
    previewLoading.value = false
    previewLoadingTimer = null
  }, remaining)
}

function resetPreviewState() {
  if (previewLoadingTimer) {
    clearTimeout(previewLoadingTimer)
    previewLoadingTimer = null
  }
  previewLoading.value = false
  loadingMessage.value = '正在为你整理预览内容...'
  textContent.value = ''
  fileUrl.value = ''
  officePreviewUrl.value = ''
}

/**
 * 构造 kkFileView 水印内容。
 *
 * 优先交替显示「登录用户名」与「手机号后 4 位」：
 * - 行内 2~3 次混合，行间交替，共 3 行
 * - 仅有用户名或仅有手机号时，退化为单值 3 行
 * - 两者都缺失则返回空串，由调用方跳过水印
 *
 * 注意：多行水印使用真实换行符，由请求参数编码后透传给预览服务。
 */
const WATERMARK_LINE_BREAK = '\n'

function buildWatermark(): string {
  const user = userStore.userInfo
  const username = (user?.realName || user?.username || '').trim()
  const phone = (user?.phone || '').trim()
  const phone4 = phone ? phone.slice(-4) : ''

  if (!username && !phone4) return ''

  if (username && phone4) {
    // 只输出2行：姓名行和手机号行，kkFileView 会按行交替渲染
    return [username, phone4].join(WATERMARK_LINE_BREAK)
  }

  return username || phone4
}

function handleEmbeddedPreviewLoaded() {
  endPreviewLoading()
}

function handleVisualPreviewLoaded() {
  endPreviewLoading()
}

function handleVisualPreviewError() {
  endPreviewLoading()
  ElMessage.error('图片预览加载失败')
}

function zoomIn() {
  zoom.value = Math.min(200, zoom.value + 25)
}

function zoomOut() {
  zoom.value = Math.max(50, zoom.value - 25)
}

function resetZoom() {
  zoom.value = 100
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
    emit('downloaded')
  } catch {
    ElMessage.error('下载文件失败')
  } finally {
    downloading.value = false
  }
}

function handleClose() {
  resetPreviewState()
  emit('saved')
}

onBeforeUnmount(() => {
  if (previewLoadingTimer) {
    clearTimeout(previewLoadingTimer)
  }
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

document.addEventListener('fullscreenchange', onFullscreenChange)
</script>

<style scoped>
:global(.file-preview-dialog .el-dialog__header) {
  position: relative;
  padding-right: 120px;
}

:global(.file-preview-dialog .app-dialog__header) {
  position: relative;
  padding-right: 0;
  width: 100%;
}

:global(.file-preview-dialog .app-dialog__header-actions) {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

:global(.file-preview-dialog .el-dialog__headerbtn) {
  top: 12px;
  right: 12px;
}

.zoom-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.96);
  border: 1px solid #e2e8f0;
}

.zoom-level {
  font-size: 12px;
  color: #475569;
  min-width: 48px;
  display: inline-block;
  text-align: center;
  font-weight: 600;
}

.preview-container {
  position: relative;
  min-height: 60vh;
  max-height: 75vh;
  overflow: auto;
  background:
    radial-gradient(circle at top left, rgba(125, 211, 252, 0.2), transparent 28%),
    linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
}
.preview-container--fullscreen {
  max-height: none;
  height: 100vh;
}

.preview-loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(248, 251, 255, 0.92);
  backdrop-filter: blur(10px);
  z-index: 3;
}

.preview-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: min(320px, calc(100% - 32px));
  padding: 28px 24px;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 18px 40px rgba(59, 130, 246, 0.12);
  text-align: center;
}

.preview-loader {
  position: relative;
  width: 96px;
  height: 88px;
  animation: preview-loader-float 2.4s ease-in-out infinite;
}

.preview-loader__sheet {
  position: absolute;
  inset: auto 0 0 0;
  margin: auto;
  width: 72px;
  height: 86px;
  border-radius: 18px 18px 16px 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f6f9ff 100%);
  border: 1px solid rgba(96, 165, 250, 0.28);
  box-shadow: 0 12px 20px rgba(59, 130, 246, 0.12);
}

.preview-loader__sheet::before {
  content: '';
  position: absolute;
  top: 12px;
  left: 14px;
  right: 14px;
  height: 6px;
  border-radius: 999px;
  background: rgba(96, 165, 250, 0.2);
  box-shadow:
    0 14px 0 rgba(148, 163, 184, 0.16),
    0 28px 0 rgba(148, 163, 184, 0.16);
}

.preview-loader__sheet--back {
  transform: translate(-10px, 4px) rotate(-8deg);
  opacity: 0.55;
}

.preview-loader__sheet--mid {
  transform: translate(10px, 2px) rotate(8deg);
  opacity: 0.75;
}

.preview-loader__sheet--front {
  z-index: 2;
  background: linear-gradient(180deg, #ffffff 0%, #eef6ff 100%);
}

.preview-loader__eyes {
  position: absolute;
  top: 32px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 14px;
}

.preview-loader__eyes i {
  width: 6px;
  height: 10px;
  border-radius: 999px;
  background: #5b6b88;
  animation: preview-loader-blink 3s ease-in-out infinite;
}

.preview-loader__smile {
  position: absolute;
  left: 50%;
  bottom: 24px;
  width: 18px;
  height: 9px;
  border-bottom: 3px solid #5b6b88;
  border-radius: 0 0 999px 999px;
  transform: translateX(-50%);
}

.preview-loading__title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.preview-loading__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.preview-loading__dots {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-loading__dots span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: linear-gradient(180deg, #60a5fa 0%, #3b82f6 100%);
  animation: preview-dot-bounce 1.1s ease-in-out infinite;
}

.preview-loading__dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.preview-loading__dots span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes preview-loader-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

@keyframes preview-loader-blink {
  0%, 42%, 48%, 100% {
    transform: scaleY(1);
  }
  45% {
    transform: scaleY(0.2);
  }
}

@keyframes preview-dot-bounce {
  0%, 100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  50% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

.preview-office-wrap {
  width: 100%;
  height: 75vh;
}
.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
.preview-image-wrap {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: 60vh;
  max-height: 75vh;
  overflow: auto;
}
.preview-image {
  max-width: none;
  max-height: none;
  object-fit: none;
  transform-origin: top left;
  transition: transform 0.2s ease;
}
.preview-text-wrap {
  max-height: 75vh;
  overflow: auto;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 16px;
  font-size: 13px;
}
.preview-text {
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
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

/* 去掉全屏/下载按钮的 focus 红色边框 */
.file-preview-dialog :deep(.el-button:focus) {
  outline: none;
  box-shadow: none;
}
</style>
