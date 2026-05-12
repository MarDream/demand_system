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
import { getEditorConfig } from '@/api/modules/onlyoffice'
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

function loadEditor() {
  if (!props.knowledgeBaseId || !props.documentId) {
    showError('参数错误', '缺少必要参数')
    return
  }

  loading.value = true
  error.value = false

  getEditorConfig(props.knowledgeBaseId, props.documentId, props.mode || 'edit')
    .then(res => {
      const config: OnlyOfficeEditorConfig = res
      initOnlyOffice(config)
    })
    .catch(err => {
      showError('获取编辑器配置失败', err.message || '请检查网络连接')
    })
    .finally(() => {
      loading.value = false
    })
}

function initOnlyOffice(config: OnlyOfficeEditorConfig) {
  const script = document.createElement('script')
  script.src = 'http://localhost:8443/web-apps/apps/api/documents/api.js'
  script.onload = () => {
    if (window.DocsAPI) {
      setTimeout(() => {
        if (editorPlaceholder.value && window.DocsAPI) {
          editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor-placeholder', config)
        }
      }, 100)
    } else {
      showError('OnlyOffice 脚本加载失败', '请确认 OnlyOffice 服务已启动')
    }
  }
  script.onerror = () => {
    showError('OnlyOffice 脚本加载失败', '无法连接到 OnlyOffice 服务')
  }
  document.head.appendChild(script)
}

function showError(title: string, message: string) {
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
  if (editorInstance) {
    try {
      editorInstance.destroyEditor()
    } catch (e) {
      // ignore
    }
    editorInstance = null
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
