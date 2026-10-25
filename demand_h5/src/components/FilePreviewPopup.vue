<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { showToast, showImagePreview } from 'vant'
import { fetchInlineBlob, downloadFileBlob, getOfficePreviewUrl } from '@/api/file'
import { previewKindOf, saveBlob } from '@/utils/file'

const props = defineProps<{
  /** 弹窗显示（v-model） */
  show: boolean
  /** 文件记录 ID（预览/下载的必需标识） */
  fileId?: number | null
  fileName: string
  /** 文件名或 contentType，用于判定预览类型 */
  fileType?: string | null
  /** 历史兜底地址：无 fileId 时只能下载/新窗口打开 */
  url?: string | null
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  downloaded: []
}>()

const kind = computed(() => previewKindOf(props.fileType || props.fileName, props.fileType))
const canPreview = computed(() => !!props.fileId && kind.value !== 'unsupported')

/** van-popup 的 v-model:show 需要可写计算属性（props + emit 单向流转） */
const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value),
})

const loading = ref(false)
const stageText = ref('')
const errorText = ref('')
const objectUrl = ref('')
const textContent = ref('')
const officeUrl = ref('')

watch(
  () => props.show,
  async (open) => {
    if (!open) {
      resetState()
      return
    }
    resetState()
    await loadPreview()
  },
)

async function loadPreview() {
  errorText.value = ''
  if (!canPreview.value || !props.fileId) return
  loading.value = true
  stageText.value = '正在准备预览…'
  try {
    if (kind.value === 'image') {
      const blob = await fetchInlineBlob(props.fileId)
      objectUrl.value = URL.createObjectURL(blob)
      loading.value = false
      showImagePreview({
        images: [objectUrl.value],
        showIndex: false,
        closeable: true,
        onClose: () => close(),
      })
      return
    }
    if (kind.value === 'text') {
      const blob = await fetchInlineBlob(props.fileId)
      // 文本预览截断，防止超大文本拖垮渲染
      textContent.value = (await blob.text()).slice(0, 200000)
    } else {
      officeUrl.value = await getOfficePreviewUrl(props.fileId, (stage) => {
        if (stage === 'poll') stageText.value = '文档转换中，请稍候…'
        else if (stage === 'complete') stageText.value = '即将完成…'
        else stageText.value = '正在提交预览任务…'
      })
      officeUrl.value = appendFileName(officeUrl.value, props.fileName)
    }
  } catch (err) {
    errorText.value = err instanceof Error ? err.message : '预览失败，请下载原文件查看'
  } finally {
    loading.value = false
  }
}

/** kkFileView 通过 fullfilename 参数识别原始文件名（与 PC 端同处理） */
function appendFileName(url: string, fileName: string): string {
  if (!url || !fileName) return url
  try {
    const parsed = new URL(url, window.location.origin)
    parsed.searchParams.set('fullfilename', fileName)
    return parsed.toString()
  } catch {
    return url + (url.includes('?') ? '&' : '?') + 'fullfilename=' + encodeURIComponent(fileName)
  }
}

async function handleDownload() {
  try {
    let blob: Blob
    if (props.fileId) {
      blob = await downloadFileBlob(props.fileId)
    } else if (props.url) {
      const res = await fetch(props.url)
      blob = await res.blob()
    } else {
      showToast('文件不存在')
      return
    }
    saveBlob(blob, props.fileName || 'download')
    emit('downloaded')
  } catch {
    showToast('下载失败')
  }
}

function resetState() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  textContent.value = ''
  officeUrl.value = ''
  errorText.value = ''
  loading.value = false
}

function close() {
  emit('update:show', false)
}
</script>

<template>
  <van-popup
    v-model:show="visible"
    teleport="body"
    position="bottom"
    :style="{ height: '100%', width: '100%' }"
    safe-area-inset-top
    safe-area-inset-bottom
  >
    <div class="preview-page">
      <van-nav-bar title="附件预览" left-arrow fixed placeholder safe-area-inset-top @click-left="close">
        <template #right>
          <span class="preview-page__action" @click="handleDownload">下载</span>
        </template>
      </van-nav-bar>

      <div class="preview-page__body">
        <!-- office：kkFileView iframe -->
        <iframe
          v-if="kind === 'office' && officeUrl && !loading && !errorText"
          :src="officeUrl"
          class="preview-iframe"
          frameborder="0"
          allowfullscreen
        ></iframe>

        <!-- 文本 -->
        <pre v-else-if="kind === 'text' && !loading && !errorText" class="preview-text">{{ textContent }}</pre>

        <!-- 图片由 vant ImagePreview 浮层承担，这里不再渲染 -->

        <!-- 加载中 -->
        <div v-if="loading" class="preview-state">
          <van-loading size="28px" />
          <div class="preview-state__text">{{ stageText }}</div>
        </div>

        <!-- 失败 / 不支持预览 -->
        <div v-else-if="errorText" class="preview-state">
          <van-icon name="warning-o" size="44" color="#ee0a24" />
          <div class="preview-state__text">{{ errorText }}</div>
          <van-button size="small" round type="primary" @click="loadPreview">重试</van-button>
        </div>
        <div v-else-if="!canPreview" class="preview-state">
          <van-icon name="description" size="44" color="#969799" />
          <div class="preview-state__text">该文件类型暂不支持在线预览</div>
          <van-button size="small" round type="primary" @click="handleDownload">下载原文件</van-button>
        </div>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.preview-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f7f8fa;
}

.preview-page__action {
  font-size: 14px;
  color: #1989fa;
}

.preview-page__body {
  position: relative;
  flex: 1;
  overflow: hidden;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

.preview-text {
  margin: 0;
  padding: 12px;
  height: 100%;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: #323233;
  background: #fff;
}

.preview-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.preview-state__text {
  font-size: 13px;
  color: #646566;
  text-align: center;
  padding: 0 32px;
  line-height: 1.5;
}
</style>
