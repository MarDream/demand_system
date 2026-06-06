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
    </div>

    <div ref="previewContainerRef" class="share-preview" :class="{ 'share-preview--fullscreen': isFullscreen }">
      <div class="share-preview__actions">
        <el-button
          v-if="downloadUrl"
          type="primary"
          :class="{ 'share-action-button--attention': isPreviewLoadSlow }"
          :title="isPreviewLoadSlow ? '等待时间较长，建议直接下载文件查看' : '下载文件'"
          @click="downloadFile"
        >
          <el-icon><Download /></el-icon>
          <span>下载文件</span>
        </el-button>
        <el-button @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </el-button>
      </div>

      <div v-if="pageLoading" class="share-loading-mask">
        <PreviewLoadingCard
          title="正在打开分享文档"
          description="正在校验分享信息并准备预览内容，请稍候。"
          :progress="pageLoadingProgress"
          :show-elapsed="false"
        />
      </div>

      <el-result
        v-else-if="errorMessage"
        icon="error"
        title="访问失败"
        :sub-title="errorMessage"
      />

      <template v-else-if="context">
        <div v-if="previewType === 'office'" class="share-office-wrap">
          <iframe
            v-if="officePreviewUrl"
            :src="officePreviewUrl"
            class="share-iframe"
            frameborder="0"
            allowfullscreen
            @load="handleEmbeddedPreviewLoaded"
          ></iframe>
        </div>

        <div v-else-if="previewType === 'image'" class="share-image-wrap">
          <img
            v-if="fileUrl"
            :src="fileUrl"
            :alt="context.fileName"
            class="share-image"
            @load="handleImagePreviewLoaded"
            @error="handleImagePreviewError"
          />
        </div>

        <div v-else-if="previewType === 'text'" class="share-text-wrap">
          <pre class="share-text">{{ textContent }}</pre>
        </div>

        <div v-else class="share-state">
          <el-icon :size="40"><Document /></el-icon>
          <span>该文件类型不支持在线预览，请下载后查看。</span>
        </div>
      </template>

      <div v-if="previewLoading && !pageLoading && !errorMessage" class="share-loading-mask share-loading-mask--overlay">
        <PreviewLoadingCard
          title="正在准备文档预览"
          :description="previewLoadingMessage"
          :progress="previewProgress"
          :elapsed-seconds="previewElapsedSeconds"
          :show-slow-notice="isPreviewLoadSlow"
          slow-notice="等待时间较长，建议直接下载原文件查看，右上角下载按钮可直接使用。"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, Download, FullScreen } from '@element-plus/icons-vue'
import PreviewLoadingCard from '@/components/document/PreviewLoadingCard.vue'
import { getPublicShareContext, type PublicShareContext } from '@/api/modules/publicShare'
import { getOfficePreviewUrl } from '@/api/modules/preview'
import { PREVIEW_IMAGE_SET, PREVIEW_SUPPORTED_EXTENSION_SET, PREVIEW_TEXT_SET, normalizeFileExtension } from '@/constants/knowledgeDocument'
import { formatDate } from '@/utils/format'
import { clampProgress, computeOfficePreviewProgress, fetchBlobWithProgress, fetchTextWithProgress } from '@/utils/previewLoading'

const PREVIEW_LOADING_MIN_DURATION = 500
const PREVIEW_SLOW_THRESHOLD_SECONDS = 15

const route = useRoute()

const pageLoading = ref(true)
const errorMessage = ref('')
const context = ref<PublicShareContext | null>(null)
const textContent = ref('')
const fileUrl = ref('')
const downloadUrl = ref('')
const officePreviewUrl = ref('')
const previewContainerRef = ref<HTMLElement>()
const isFullscreen = ref(false)
const previewLoading = ref(false)
const previewLoadingMessage = ref('正在为你整理预览内容...')
const previewElapsedSeconds = ref(0)
const previewProgress = ref(0)
const pageLoadingProgress = ref(12)

let previewLoadingStartedAt = 0
let previewLoadingTimer: ReturnType<typeof setTimeout> | null = null
let previewElapsedTimer: ReturnType<typeof setInterval> | null = null
let previewObjectUrl: string | null = null

const previewType = computed(() => {
  const ext = normalizeFileExtension(context.value?.fileType)
  if (PREVIEW_IMAGE_SET.has(ext)) return 'image'
  if (PREVIEW_TEXT_SET.has(ext)) return 'text'
  if (PREVIEW_SUPPORTED_EXTENSION_SET.has(ext)) return 'office'
  return 'unsupported'
})

const isPreviewLoadSlow = computed(() => previewElapsedSeconds.value >= PREVIEW_SLOW_THRESHOLD_SECONDS)

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
  pageLoadingProgress.value = 16
  errorMessage.value = ''
  resetPreviewState()

  try {
    const share = await getPublicShareContext(token)
    pageLoadingProgress.value = 42
    context.value = share
    const accessToken = encodeURIComponent(share.accessToken)
    fileUrl.value = `/api/v1/public/knowledge/shares/${share.shareToken}/file?accessToken=${accessToken}`
    downloadUrl.value = `/api/v1/public/knowledge/shares/${share.shareToken}/download?accessToken=${accessToken}`
    pageLoadingProgress.value = 100
    pageLoading.value = false

    if (previewType.value === 'unsupported') {
      return
    }
    beginPreviewLoading(getLoadingMessage(previewType.value))

    if (previewType.value === 'text') {
      textContent.value = await fetchTextWithProgress(fileUrl.value, setPreviewProgress)
      setPreviewProgress(100)
      endPreviewLoading()
    } else if (previewType.value === 'image') {
      const blob = await fetchBlobWithProgress(fileUrl.value, setPreviewProgress)
      revokePreviewObjectUrl()
      previewObjectUrl = window.URL.createObjectURL(blob)
      fileUrl.value = previewObjectUrl
      setPreviewProgress(96)
    } else if (previewType.value === 'office') {
      if (share.previewUrl) {
        try {
          const previewRes = await getOfficePreviewUrl({ fileUrl: share.previewUrl }, {
            onProgress: event => {
              if (event.message) {
                previewLoadingMessage.value = event.message
              }
              if (event.status === 'completed') {
                setPreviewProgress(96)
                return
              }
              setPreviewProgress(computeOfficePreviewProgress(event.progress, event.attempt, event.maxAttempts))
            },
          }) as any
          officePreviewUrl.value = previewRes.data?.previewUrl ?? previewRes.previewUrl ?? ''
          setPreviewProgress(97)
        } catch {
          endPreviewLoading()
          errorMessage.value = '无法生成文件预览地址'
        }
      } else {
        endPreviewLoading()
        errorMessage.value = '无法生成文件预览地址'
      }
    }
  } catch (error: any) {
    errorMessage.value = error?.message || '分享链接无法访问'
    endPreviewLoading()
  } finally {
    pageLoadingProgress.value = 100
    pageLoading.value = false
  }
}

function getLoadingMessage(type: string) {
  if (type === 'office') {
    return '文档较大时会先完成渲染，再进入预览。'
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
  previewLoadingMessage.value = message
  previewLoadingStartedAt = Date.now()
  previewProgress.value = 8
  startPreviewElapsedTimer()
  previewLoading.value = true
}

function endPreviewLoading() {
  if (!previewLoading.value) return
  stopPreviewElapsedTimer()
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

function startPreviewElapsedTimer() {
  stopPreviewElapsedTimer()
  previewElapsedSeconds.value = 0
  previewElapsedTimer = setInterval(() => {
    previewElapsedSeconds.value = Math.floor((Date.now() - previewLoadingStartedAt) / 1000)
  }, 1000)
}

function setPreviewProgress(progress: number) {
  previewProgress.value = Math.max(previewProgress.value, clampProgress(progress))
}

function revokePreviewObjectUrl() {
  if (!previewObjectUrl) return
  window.URL.revokeObjectURL(previewObjectUrl)
  previewObjectUrl = null
}

function stopPreviewElapsedTimer() {
  if (!previewElapsedTimer) return
  clearInterval(previewElapsedTimer)
  previewElapsedTimer = null
}

function resetPreviewState() {
  if (previewLoadingTimer) {
    clearTimeout(previewLoadingTimer)
    previewLoadingTimer = null
  }
  stopPreviewElapsedTimer()
  context.value = null
  textContent.value = ''
  revokePreviewObjectUrl()
  fileUrl.value = ''
  downloadUrl.value = ''
  officePreviewUrl.value = ''
  previewLoading.value = false
  previewLoadingMessage.value = '正在为你整理预览内容...'
  previewElapsedSeconds.value = 0
  previewProgress.value = 0
}

function handleEmbeddedPreviewLoaded() {
  setPreviewProgress(100)
  endPreviewLoading()
}

function handleImagePreviewLoaded() {
  setPreviewProgress(100)
  endPreviewLoading()
}

function handleImagePreviewError() {
  endPreviewLoading()
  errorMessage.value = '图片预览加载失败'
  ElMessage.error('图片预览加载失败')
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
  if (previewLoadingTimer) {
    clearTimeout(previewLoadingTimer)
  }
  revokePreviewObjectUrl()
  stopPreviewElapsedTimer()
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

.share-preview {
  position: relative;
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

.share-preview__actions {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
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

.share-loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(248, 250, 252, 0.94);
  backdrop-filter: blur(10px);
  z-index: 3;
}

.share-loading-mask--overlay {
  background: rgba(248, 250, 252, 0.88);
}

.share-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: min(340px, calc(100% - 32px));
  padding: 28px 24px;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 40px rgba(59, 130, 246, 0.12);
  text-align: center;
}

.share-loader {
  position: relative;
  width: 96px;
  height: 88px;
  animation: share-loader-float 2.4s ease-in-out infinite;
}

.share-loader__sheet {
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

.share-loader__sheet::before {
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

.share-loader__sheet--back {
  transform: translate(-10px, 4px) rotate(-8deg);
  opacity: 0.55;
}

.share-loader__sheet--mid {
  transform: translate(10px, 2px) rotate(8deg);
  opacity: 0.75;
}

.share-loader__sheet--front {
  z-index: 2;
  background: linear-gradient(180deg, #ffffff 0%, #eef6ff 100%);
}

.share-loader__eyes {
  position: absolute;
  top: 32px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: 14px;
}

.share-loader__eyes i {
  width: 6px;
  height: 10px;
  border-radius: 999px;
  background: #5b6b88;
  animation: share-loader-blink 3s ease-in-out infinite;
}

.share-loader__smile {
  position: absolute;
  left: 50%;
  bottom: 24px;
  width: 18px;
  height: 9px;
  border-bottom: 3px solid #5b6b88;
  border-radius: 0 0 999px 999px;
  transform: translateX(-50%);
}

.share-loading__title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.share-loading__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.share-loading__timer {
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.share-loading__notice {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(251, 146, 60, 0.12);
  border: 1px solid rgba(251, 146, 60, 0.2);
  color: #c2410c;
  font-size: 12px;
  line-height: 1.7;
}

.share-loading__dots {
  display: flex;
  align-items: center;
  gap: 8px;
}

.share-loading__dots span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: linear-gradient(180deg, #60a5fa 0%, #3b82f6 100%);
  animation: share-dot-bounce 1.1s ease-in-out infinite;
}

.share-loading__dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.share-loading__dots span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes share-loader-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

@keyframes share-loader-blink {
  0%, 42%, 48%, 100% {
    transform: scaleY(1);
  }
  45% {
    transform: scaleY(0.2);
  }
}

@keyframes share-dot-bounce {
  0%, 100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  50% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

@keyframes share-button-pulse {
  0%, 100% {
    transform: translateY(0) scale(1);
    box-shadow: 0 0 0 0 rgba(251, 146, 60, 0.18);
  }
  50% {
    transform: translateY(-1px) scale(1.03);
    box-shadow: 0 0 0 8px rgba(251, 146, 60, 0);
  }
}

:deep(.share-action-button--attention) {
  border-color: rgba(251, 146, 60, 0.55);
  animation: share-button-pulse 1.25s ease-in-out infinite;
}

.share-office-wrap {
  width: 100%;
  min-height: calc(100vh - 160px);
}

.share-iframe {
  width: 100%;
  height: calc(100vh - 160px);
  border: none;
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
    padding: 16px;
  }

  .share-preview__actions {
    top: 12px;
    right: 12px;
  }
}

/* 去掉全屏按钮的 focus 红色边框 */
.share-page :deep(.el-button:focus) {
  outline: none;
  box-shadow: none;
}
</style>
