<template>
  <div
    class="document-center"
    @dragover.prevent="isDragOver = true"
    @dragleave.prevent="isDragOver = false"
    @drop.prevent="onDrop"
    @paste="onPaste"
  >
    <div class="dc-header">
      <h2>文档中心</h2>
      <AppButton type="primary" permission="button:document:upload" @click="showUploadDialog = true">上传文档</AppButton>
    </div>

    <!-- 拖拽覆盖层 -->
    <div v-if="isDragOver" class="drag-overlay">
      <el-icon :size="48"><Upload /></el-icon>
      <p>拖放文件到此处上传</p>
    </div>

    <div class="dc-layout">
      <main class="dc-main">
        <SmartSearchBar @search="handleSearch" />
        <el-tabs>
          <el-tab-pane label="检索结果">
            <RAGSearchResult :results="searchResults" />
          </el-tab-pane>
          <el-tab-pane label="文档列表">
            <DocumentTable :data="documents" :loading="loading" @view="handleView" @delete="handleDelete" />
          </el-tab-pane>
        </el-tabs>
      </main>
    </div>

    <!-- 上传文档对话框 -->
    <DocumentUploadDialog ref="uploadDialogRef" v-model="showUploadDialog" @uploaded="handleUploaded" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import AppButton from '@/components/common/AppButton.vue'
import DocumentTable from '@/components/document/DocumentTable.vue'
import DocumentUploadDialog from '@/components/document/DocumentUploadDialog.vue'
import RAGSearchResult from '@/components/document/RAGSearchResult.vue'
import SmartSearchBar from '@/components/document/SmartSearchBar.vue'
import { useDocumentStore } from '@/stores/document'

const store = useDocumentStore()
const documents = computed(() => store.documents)
const searchResults = computed(() => store.searchResults)
const loading = computed(() => store.loading)

const showUploadDialog = ref(false)
const uploadDialogRef = ref<InstanceType<typeof DocumentUploadDialog>>()
const isDragOver = ref(false)

onMounted(async () => {
  store.fetchDocuments()
})

function handleSearch(query: string, mode: string) {
  if (!query?.trim()) return
  store.search(query, mode)
}

// ---- 拖拽（页面全局） ----
function onDrop(e: DragEvent) {
  isDragOver.value = false
  const files = e.dataTransfer?.files
  uploadDialogRef.value?.openWithFiles(files || undefined)
}

// ---- 粘贴 ----
function onPaste(e: ClipboardEvent) {
  const files = e.clipboardData?.files
  if (!files || files.length === 0) return
  uploadDialogRef.value?.openWithFiles(files)
}

function handleUploaded() {
  store.fetchDocuments()
}

function handleView(_doc: unknown) {}
function handleDelete(_doc: unknown) {}
</script>

<style scoped>
.document-center {
  padding: 20px;
  position: relative;
}

.dc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.dc-layout {
  display: flex;
  gap: 20px;
}

.dc-main {
  flex: 1;
}

/* 拖拽覆盖层 */
.drag-overlay {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(64, 158, 255, 0.1);
  border: 2px dashed var(--color-accent);
  border-radius: 8px;
  pointer-events: none;
  font-size: 16px;
  color: var(--color-accent);
  gap: 8px;
}

</style>
