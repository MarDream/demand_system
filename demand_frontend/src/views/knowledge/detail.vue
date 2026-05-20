<template>
  <PageContainer>
    <div class="kb-detail-layout">
      <!-- 主内容区 -->
      <main class="kb-main">
        <!-- 筛选栏 -->
        <div class="filter-bar">
          <div class="filter-row">
            <div class="filter-field filter-field--name">
              <label class="filter-field__label">文件名称</label>
              <el-input
                v-model="documentFilters.fileName"
                clearable
                placeholder="输入文件名称关键词"
                class="filter-input filter-input--name"
                :prefix-icon="Search"
                @keyup.enter="applyFilters"
              />
            </div>

            <div class="filter-field filter-field--status">
              <label class="filter-field__label">文档状态</label>
              <el-select
                v-model="documentFilters.status"
                clearable
                placeholder="全部状态"
                class="filter-input filter-input--status"
              >
                <el-option label="待处理" value="pending" />
                <el-option label="已解析" value="parsed" />
                <el-option label="索引中" value="indexing" />
                <el-option label="已索引" value="indexed" />
                <el-option label="已入库" value="stored" />
                <el-option label="失败" value="failed" />
              </el-select>
            </div>

            <div class="filter-field filter-field--date">
              <label class="filter-field__label">上传时间</label>
              <el-date-picker
                v-model="documentFilters.createdAtRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                class="filter-input filter-input--date"
              />
            </div>

            <div class="filter-actions">
              <el-button type="primary" class="filter-actions__primary" :icon="Search" @click="applyFilters">
                检索
              </el-button>
              <el-button v-if="hasActiveFilters" class="filter-actions__secondary" @click="resetFilters">
                重置
              </el-button>
            </div>
          </div>

          <!-- 激活的筛选项芯片 -->
          <div v-if="activeFilterChips.length > 0" class="filter-chips">
            <span class="filter-chips__label">已筛选：</span>
            <el-tag
              v-for="chip in activeFilterChips"
              :key="chip.key"
              closable
              size="small"
              @close="removeFilterChip(chip.key)"
            >
              {{ chip.label }}
            </el-tag>
            <el-button text type="info" size="small" @click="resetFilters">清除全部</el-button>
          </div>
        </div>

        <!-- 批量操作栏 -->
        <transition name="batch-slide">
          <div v-if="selectedRows.length > 0" class="batch-bar">
            <div class="batch-info">
              <el-checkbox
                :model-value="isAllSelected"
                :indeterminate="isIndeterminate"
                @change="toggleSelectAll"
              />
              <span class="batch-count">已选 {{ selectedRows.length }} 项</span>
            </div>
            <div class="batch-actions">
              <el-button size="small" type="primary" :loading="batchDownloading" @click="handleBatchDownload">
                {{ selectedRows.length >= 2 ? '打包下载' : '下载' }} ({{ selectedRows.length }})
              </el-button>
              <el-button size="small" type="warning" :loading="batchRetrying" @click="handleBatchRetry">
                批量重传
              </el-button>
              <el-button size="small" type="danger" :loading="batchDeleting" @click="handleBatchDelete">
                批量删除
              </el-button>
            </div>
          </div>
        </transition>

        <!-- 表格 -->
        <div class="table-wrapper">
          <el-table
            :data="store.documents"
            v-loading="store.loading"
            stripe
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="file-name-cell">
                  <span v-if="canPreview(row.fileType)" class="file-name-link" @click="handlePreview(row)">
                    {{ row.fileName }}
                  </span>
                  <span v-else class="file-name-text">{{ row.fileName }}</span>
                  <el-tag size="small" class="file-type-tag">{{ row.fileType }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="projectName" label="项目名称" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.projectName || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="requirementId" label="需求编号" width="110">
              <template #default="{ row }">
                <span v-if="row.requirementId" class="requirement-id">#{{ row.requirementId }}</span>
                <span v-else class="text-secondary">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="90">
              <template #default="{ row }">
                {{ formatSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="uploaderName" label="上传人" width="100" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.uploaderName || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="上传时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="96">
              <template #default="{ row }">
                <el-tooltip v-if="row.status === 'failed' && row.errorMessage" :content="row.errorMessage" placement="top">
                  <el-tag size="small" effect="light" type="danger">{{ statusLabel(row.status) }}</el-tag>
                </el-tooltip>
                <el-tag v-else size="small" effect="light" :type="statusType(row.status)">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <!-- 查看类 -->
                  <div class="action-group__item">
                    <el-tooltip v-if="canPreview(row.fileType)" content="预览">
                      <el-button link type="primary" size="small" @click="handlePreview(row)">
                        <el-icon><View /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="下载">
                      <el-button link type="primary" size="small" @click="handleDownload(row)">
                        <el-icon><Download /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                  <el-divider direction="vertical" class="action-divider" />
                  <!-- 管理类 -->
                  <div class="action-group__item">
                    <el-tooltip content="分享">
                      <el-button link type="primary" size="small" @click="handleShare(row)">
                        <el-icon><Share /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="row.status === 'failed'" content="重传">
                      <el-button link type="warning" size="small" @click="handleRetry(row)">
                        <el-icon><RefreshRight /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                  <el-divider direction="vertical" class="action-divider" />
                  <!-- 危险类 -->
                  <div class="action-group__item">
                    <el-tooltip content="日志">
                      <el-button link type="info" size="small" @click="showLog(row)">
                        <el-icon><Document /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="删除">
                      <el-button link type="danger" size="small" @click="handleDelete(row)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 翻页区 -->
        <div class="pagination-wrapper">
          <div class="pagination-info">
            共 <strong>{{ store.totalDocs }}</strong> 条文档 ·
            第 <strong>{{ paginationStart }}-{{ paginationEnd }}</strong> 条
          </div>
          <el-pagination
            v-model:current-page="documentFilters.pageNum"
            v-model:page-size="documentFilters.pageSize"
            :total="store.totalDocs"
            :page-sizes="[10, 20, 50, 100]"
            layout="sizes, prev, pager, next"
            class="pagination"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </main>
    </div>

    <DocumentUploadDialog
      v-model="showUploadDialog"
      :knowledge-base-id="kbId"
      :show-knowledge-base-select="false"
      @uploaded="handleUploaded"
    />

    <AppDialog v-model="shareDialogVisible" title="文档分享" width="420px">
      <div class="share-dialog">
        <el-form label-width="96px" class="share-form">
          <el-form-item label="有效期">
            <el-select v-model="shareOptions.expireHours" style="width: 100%">
              <el-option :value="24" label="24小时" />
              <el-option :value="72" label="3天" />
              <el-option :value="168" label="7天" />
            </el-select>
          </el-form-item>
          <el-form-item label="访问控制">
            <el-switch v-model="shareOptions.requireLogin" active-text="需登录" inactive-text="公开访问" />
          </el-form-item>
          <el-form-item label="链接类型">
            <el-switch v-model="shareOptions.oneTimeAccess" active-text="一次性" inactive-text="可重复访问" />
          </el-form-item>
        </el-form>
        <div class="share-link">{{ currentShareLink }}</div>
        <img v-if="currentShareLink && qrCodeUrl" class="share-qr" :src="qrCodeUrl" alt="二维码" />
        <div class="share-tip">
          链接支持扫码访问，当前配置为{{ shareOptions.requireLogin ? '需登录' : '公开访问' }}、{{ shareOptions.oneTimeAccess ? '一次性链接' : '可重复访问链接' }}。
        </div>
      </div>
      <template #footer>
        <el-button @click="shareDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="generateCurrentShareLink">生成链接</el-button>
        <el-button :disabled="!currentShareLink" @click="copyCurrentShareLink">复制链接</el-button>
      </template>
    </AppDialog>

    <AppDialog v-model="logDialogVisible" title="文档处理日志" width="550px">
      <div class="log-dialog">
        <div class="log-header">
          <span class="log-file">{{ logDocument?.fileName }}</span>
          <el-tag size="small" :type="statusType(logDocument?.status || '')">{{ statusLabel(logDocument?.status || '') }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small" class="log-info">
          <el-descriptions-item label="文件类型">{{ logDocument?.fileType }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatSize(logDocument?.fileSize || 0) }}</el-descriptions-item>
          <el-descriptions-item label="上传者">{{ logDocument?.uploaderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatDateTime(logDocument?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="分块数">{{ logDocument?.chunkCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ statusLabel(logDocument?.status || '') }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="logDocument?.errorMessage && logDocument.status !== 'indexed'" class="log-error">
          <div class="log-error-title">错误信息</div>
          <div class="log-error-content">{{ logDocument.errorMessage }}</div>
        </div>
        <div class="log-timeline">
          <div class="log-error-title">处理流程</div>
          <el-timeline>
            <el-timeline-item type="primary" timestamp="上传完成">文件已上传至存储系统</el-timeline-item>
            <el-timeline-item :type="getTimelineType(logDocument?.status, 'pending')" :timestamp="statusLabel('pending')">
              <span>等待后台处理</span>
              <el-icon v-if="isStepProcessing(logDocument?.status, 'pending')" class="step-spin"><Loading /></el-icon>
            </el-timeline-item>
            <el-timeline-item :type="getTimelineType(logDocument?.status, 'parsed')" :timestamp="statusLabel('parsed')">
              <span>文件内容解析</span>
              <el-icon v-if="isStepProcessing(logDocument?.status, 'parsed')" class="step-spin"><Loading /></el-icon>
            </el-timeline-item>
            <el-timeline-item :type="getTimelineType(logDocument?.status, 'indexing')" :timestamp="statusLabel('indexing')">
              <span>文本分块与向量化</span>
              <el-icon v-if="isStepProcessing(logDocument?.status, 'indexing')" class="step-spin"><Loading /></el-icon>
            </el-timeline-item>
            <el-timeline-item v-if="logDocument?.status === 'stored'" type="success" :timestamp="statusLabel('stored')">
              文档已入库，仅支持预览和下载
            </el-timeline-item>
            <el-timeline-item v-if="logDocument?.status === 'indexed'" type="success" :timestamp="statusLabel('indexed')">处理完成</el-timeline-item>
            <el-timeline-item v-if="logDocument?.status === 'failed'" type="danger" :timestamp="statusLabel('failed')">处理失败</el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <template #footer>
        <el-button @click="logDialogVisible = false">关闭</el-button>
        <el-button v-if="logDocument?.status === 'failed'" type="warning" @click="handleRetry(logDocument!); logDialogVisible = false">重传</el-button>
      </template>
    </AppDialog>

    <FilePreviewDialog
      v-model="previewVisible"
      :file-name="previewFile?.fileName || ''"
      :file-type="previewFile?.fileType || ''"
      :knowledge-base-id="kbId"
      :document-id="previewFile?.id || 0"
      :download-url="previewDownloadUrl"
      @downloaded="handleDocumentDownloaded"
    />
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, View, Download, Share, RefreshRight, Delete, Document, Search } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import AppDialog from '@/components/common/AppDialog.vue'
import DocumentUploadDialog from '@/components/document/DocumentUploadDialog.vue'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import {
  batchDeleteDocuments,
  batchDownloadDocumentsZip,
  deleteDocument,
  downloadDocumentBlob,
  retryDocuments,
} from '@/api/modules/knowledge'
import type { KnowledgeDocument } from '@/api/modules/knowledge'
import { KKFILEVIEW_SUPPORTED_EXTENSION_SET, normalizeFileExtension } from '@/constants/knowledgeDocument'

const route = useRoute()
const store = useKnowledgeStore()
const kbId = Number(route.params.id)
const showUploadDialog = ref(false)
const shareDialogVisible = ref(false)
const currentShareLink = ref('')
const qrCodeUrl = ref('')
const currentShareDocument = ref<KnowledgeDocument | null>(null)
const shareOptions = reactive({
  expireHours: 24,
  requireLogin: false,
  oneTimeAccess: false
})

const selectedRows = ref<KnowledgeDocument[]>([])
const batchDownloading = ref(false)
const batchRetrying = ref(false)
const batchDeleting = ref(false)
const logDialogVisible = ref(false)
const logDocument = ref<KnowledgeDocument | null>(null)
const previewVisible = ref(false)
const previewFile = ref<KnowledgeDocument | null>(null)
const previewDownloadUrl = ref('')
const documentFilters = reactive({
  fileName: '',
  status: '',
  createdAtRange: [] as string[],
  pageNum: 1,
  pageSize: 15,
})

// 翻页范围
const paginationStart = computed(() => {
  if (store.totalDocs === 0) return 0
  return (documentFilters.pageNum - 1) * documentFilters.pageSize + 1
})
const paginationEnd = computed(() => {
  return Math.min(documentFilters.pageNum * documentFilters.pageSize, store.totalDocs)
})

// 激活的筛选项
const hasActiveFilters = computed(() => {
  return !!(documentFilters.fileName || documentFilters.status || documentFilters.createdAtRange.length > 0)
})

const activeFilterChips = computed(() => {
  const chips: { key: string; label: string }[] = []
  if (documentFilters.fileName) {
    chips.push({ key: 'fileName', label: `文件名: ${documentFilters.fileName}` })
  }
  if (documentFilters.status) {
    chips.push({ key: 'status', label: `状态: ${statusLabel(documentFilters.status)}` })
  }
  if (documentFilters.createdAtRange.length > 0) {
    chips.push({ key: 'createdAtRange', label: `时间: ${documentFilters.createdAtRange[0].slice(0, 10)} ~ ${documentFilters.createdAtRange[1].slice(0, 10)}` })
  }
  return chips
})

// 全选逻辑
const isAllSelected = computed(() => {
  return store.documents.length > 0 && selectedRows.value.length === store.documents.length
})
const isIndeterminate = computed(() => {
  return selectedRows.value.length > 0 && selectedRows.value.length < store.documents.length
})

watch(currentShareLink, async (value) => {
  if (!value) {
    qrCodeUrl.value = ''
    return
  }
  try {
    qrCodeUrl.value = await QRCode.toDataURL(value, { width: 220, margin: 1 })
  } catch {
    qrCodeUrl.value = ''
    ElMessage.warning('二维码生成失败，请直接复制链接')
  }
})

onMounted(async () => {
  await fetchDocumentList()
})

function canPreview(fileType: string): boolean {
  return KKFILEVIEW_SUPPORTED_EXTENSION_SET.has(normalizeFileExtension(fileType))
}

async function fetchDocumentList(pageNum = documentFilters.pageNum) {
  documentFilters.pageNum = pageNum
  const [createdAtStart, createdAtEnd] = documentFilters.createdAtRange
  await store.fetchDocuments(kbId, {
    pageNum: documentFilters.pageNum,
    pageSize: documentFilters.pageSize,
    fileName: documentFilters.fileName.trim() || undefined,
    status: documentFilters.status || undefined,
    createdAtStart: createdAtStart || undefined,
    createdAtEnd: createdAtEnd || undefined,
  })
  syncActiveDocumentRefs()
}

function syncActiveDocumentRefs() {
  if (previewFile.value) {
    previewFile.value = store.documents.find(item => item.id === previewFile.value?.id) || previewFile.value
  }
  if (logDocument.value) {
    logDocument.value = store.documents.find(item => item.id === logDocument.value?.id) || logDocument.value
  }
}

function handleUploaded() {
  documentFilters.pageNum = 1
  fetchDocumentList(1)
}

function applyFilters() {
  fetchDocumentList(1)
}

function resetFilters() {
  documentFilters.fileName = ''
  documentFilters.status = ''
  documentFilters.createdAtRange = []
  fetchDocumentList(1)
}

function removeFilterChip(key: string) {
  if (key === 'fileName') documentFilters.fileName = ''
  else if (key === 'status') documentFilters.status = ''
  else if (key === 'createdAtRange') documentFilters.createdAtRange = []
  fetchDocumentList(1)
}

function handlePageChange(page: number) {
  fetchDocumentList(page)
}

function handleSizeChange(size: number) {
  documentFilters.pageSize = size
  documentFilters.pageNum = 1
  fetchDocumentList(1)
}

function handleDocumentDownloaded() {
  fetchDocumentList()
}

function handleDelete(doc: KnowledgeDocument) {
  ElMessageBox.confirm(`确定删除「${doc.fileName}」？`, '确认', { type: 'warning' })
    .then(async () => {
      await deleteDocument(kbId, doc.id)
      ElMessage.success('删除成功')
      selectedRows.value = selectedRows.value.filter(item => item.id !== doc.id)
      const nextPage = store.documents.length === 1 && documentFilters.pageNum > 1
        ? documentFilters.pageNum - 1
        : documentFilters.pageNum
      await fetchDocumentList(nextPage)
    })
    .catch(() => {})
}

async function handleRetry(doc: KnowledgeDocument) {
  try {
    await retryDocuments(kbId, [doc.id])
    ElMessage.success(`已提交重传任务`)
    await fetchDocumentList()
  } catch {
    ElMessage.error('重传失败')
  }
}

function handleSelectionChange(rows: KnowledgeDocument[]) {
  selectedRows.value = rows
}

function toggleSelectAll(val: boolean) {
  if (val) {
    selectedRows.value = [...store.documents]
  } else {
    selectedRows.value = []
  }
}

async function handleBatchDownload() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要下载的文档')
    return
  }

  batchDownloading.value = true
  try {
    const ids = selectedRows.value.map(d => d.id)
    let blob: Blob
    let filename: string

    if (ids.length === 1) {
      blob = await downloadDocumentBlob(kbId, ids[0])
      filename = selectedRows.value[0].fileName || 'document'
    } else {
      blob = await batchDownloadDocumentsZip(kbId, ids)
      filename = 'documents.zip'
    }

    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '下载失败'
    ElMessage.error(msg)
  } finally {
    batchDownloading.value = false
  }
}

async function handleBatchRetry() {
  const failedDocs = selectedRows.value.filter(r => r.status === 'failed')
  if (failedDocs.length === 0) {
    ElMessage.warning('请选择失败状态的文档进行重传')
    return
  }
  try {
    await ElMessageBox.confirm(`确定重新处理 ${failedDocs.length} 个失败文档？`, '批量重传', { type: 'info' })
    batchRetrying.value = true
    const ids = failedDocs.map(d => d.id)
    const res = await retryDocuments(kbId, ids) as any
    const data = res.data ?? res
    ElMessage.success(`已提交 ${data.retried || failedDocs.length} 个文档的重传任务`)
    await fetchDocumentList()
    selectedRows.value = []
  } catch {
    // 用户取消
  } finally {
    batchRetrying.value = false
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedRows.value.length} 个文档？`, '批量删除', { type: 'warning' })
    batchDeleting.value = true
    const ids = selectedRows.value.map(d => d.id)
    const res = await batchDeleteDocuments(kbId, ids) as any
    const data = res.data ?? res
    ElMessage.success(`已删除 ${data.deleted || selectedRows.value.length} 个文档`)
    const nextPage = data.deleted >= store.documents.length && documentFilters.pageNum > 1
      ? documentFilters.pageNum - 1
      : documentFilters.pageNum
    await fetchDocumentList(nextPage)
    selectedRows.value = []
  } catch {
    // 用户取消
  } finally {
    batchDeleting.value = false
  }
}

function handlePreview(doc: KnowledgeDocument) {
  if (!canPreview(doc.fileType)) {
    ElMessage.warning('该文件类型暂不支持在线预览')
    return
  }
  previewFile.value = doc
  previewDownloadUrl.value = `/api/v1/knowledge/bases/${kbId}/documents/${doc.id}/download`
  previewVisible.value = true
}

async function handleDownload(doc: KnowledgeDocument) {
  try {
    const blob = await downloadDocumentBlob(kbId, doc.id)
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = doc.fileName || 'document'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.URL.revokeObjectURL(url)
    await fetchDocumentList()
  } catch {
    ElMessage.error('下载失败')
  }
}

function showLog(doc: KnowledgeDocument) {
  logDocument.value = doc
  logDialogVisible.value = true
}

function getTimelineType(currentStatus: string | undefined, stepStatus: string): string {
  if (!currentStatus) return 'info'
  const order = ['pending', 'parsed', 'indexing', 'indexed']
  const currentIdx = order.indexOf(currentStatus)
  const stepIdx = order.indexOf(stepStatus)
  if (currentStatus === 'stored') {
    return stepStatus === 'pending' || stepStatus === 'parsed' ? 'success' : 'info'
  }
  if (currentStatus === 'failed') return stepIdx < 2 ? 'success' : (stepIdx === 2 ? 'danger' : 'info')
  if (stepIdx <= currentIdx) return 'success'
  return 'info'
}

function isStepProcessing(currentStatus: string | undefined, stepStatus: string): boolean {
  if (!currentStatus) return false
  const processingStates = ['pending', 'parsed', 'indexing']
  return processingStates.includes(currentStatus) && currentStatus === stepStatus
}

function handleShare(doc: KnowledgeDocument) {
  currentShareDocument.value = doc
  currentShareLink.value = ''
  qrCodeUrl.value = ''
  shareOptions.expireHours = 24
  shareOptions.requireLogin = false
  shareOptions.oneTimeAccess = false
  shareDialogVisible.value = true
}

async function generateCurrentShareLink() {
  if (!currentShareDocument.value) return
  try {
    const shareLink = await store.getShareLink(kbId, currentShareDocument.value.id, {
      expireHours: shareOptions.expireHours,
      requireLogin: shareOptions.requireLogin,
      oneTimeAccess: shareOptions.oneTimeAccess
    })
    currentShareLink.value = shareLink
    try {
      await navigator.clipboard.writeText(shareLink)
      ElMessage.success('分享链接已生成并复制到剪贴板')
    } catch {
      ElMessage.warning('分享链接已生成，请手动复制')
    }
  } catch {
    ElMessage.error('生成分享链接失败')
  }
}

async function copyCurrentShareLink() {
  if (!currentShareLink.value) return
  try {
    await navigator.clipboard.writeText(currentShareLink.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function statusType(status: string) {
  const map: Record<string, string> = {
    pending: 'info',
    parsed: 'warning',
    indexing: 'warning',
    indexed: 'success',
    stored: 'success',
    failed: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    pending: '待处理',
    parsed: '已解析',
    indexing: '索引中',
    indexed: '已索引',
    stored: '已入库',
    failed: '失败',
  }
  return map[status] || status
}
</script>

<style scoped>
/* ===== 布局 ===== */
.kb-detail-layout {
  /* 主内容区占满宽度 */
}

/* ===== 主内容区 ===== */
.kb-main {
  flex: 1;
  min-width: 0;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.96) 0%, rgba(255, 255, 255, 0.98) 100%);
  border-radius: 18px;
  border: 1px solid #dce7f5;
  box-shadow: 0 12px 28px rgba(15, 52, 96, 0.08);
  padding: 16px 20px;
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-field__label {
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.filter-input {
  --el-input-height: 42px;
}

.filter-input :deep(.el-input__wrapper),
.filter-input :deep(.el-select__wrapper),
.filter-input :deep(.el-range-editor.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: none;
  border: 1px solid #d8e2f0;
  background: rgba(255, 255, 255, 0.96);
  transition: all 0.2s ease;
}

.filter-input :deep(.el-input__wrapper:hover),
.filter-input :deep(.el-select__wrapper:hover),
.filter-input :deep(.el-range-editor.el-input__wrapper:hover) {
  border-color: #bdd2ee;
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.08);
}

.filter-input :deep(.is-focus),
.filter-input :deep(.el-range-editor.is-active) {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.filter-input :deep(.el-input__inner),
.filter-input :deep(.el-select__selected-item),
.filter-input :deep(.el-range-input) {
  color: #334155;
  font-size: 14px;
}

.filter-input :deep(.el-input__prefix),
.filter-input :deep(.el-range__icon),
.filter-input :deep(.el-select__caret) {
  color: #8aa0bf;
}

.filter-field--name {
  flex: 1.1 1 280px;
}

.filter-field--status {
  flex: 0.7 1 170px;
}

.filter-field--date {
  flex: 1.6 1 360px;
}

.filter-input--name,
.filter-input--status,
.filter-input--date {
  width: 100%;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 1px;
}

.filter-actions__primary,
.filter-actions__secondary {
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  font-weight: 600;
}

.filter-actions__primary {
  min-width: 96px;
  box-shadow: 0 12px 20px rgba(64, 158, 255, 0.22);
}

.filter-actions__secondary {
  border-color: #d7e3f2;
  color: #5b6b82;
  background: rgba(255, 255, 255, 0.92);
}

.filter-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #e8eef7;
}

.filter-chips__label {
  font-size: 12px;
  color: #7b8798;
  font-weight: 600;
}

.filter-chips :deep(.el-tag) {
  border-radius: 999px;
  padding: 0 10px;
  border-color: #d7e8fb;
  background: #f4f9ff;
  color: #2b6cb0;
}

@media (max-width: 768px) {
  .filter-bar {
    padding: 16px;
    border-radius: 14px;
  }

  .filter-row {
    gap: 12px;
  }

  .filter-field--name,
  .filter-field--status,
  .filter-field--date,
  .filter-actions {
    flex: 1 1 100%;
  }

  .filter-actions {
    padding-bottom: 0;
    justify-content: flex-start;
  }
}

/* ===== 批量操作栏 ===== */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f4f9ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  padding: 10px 16px;
  margin-bottom: 12px;
}

.batch-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.batch-count {
  font-size: 13px;
  color: #409EFF;
  font-weight: 500;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.batch-slide-enter-active,
.batch-slide-leave-active {
  transition: all 0.2s ease;
}
.batch-slide-enter-from,
.batch-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ===== 表格 ===== */
.table-wrapper {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-name-link {
  color: #409EFF;
  cursor: pointer;
  &:hover {
    text-decoration: underline;
  }
}

.file-name-text {
  color: #303133;
}

.file-type-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
}

.requirement-id {
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
  color: #606266;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}

.text-secondary {
  color: #909399;
}

.action-group {
  display: flex;
  align-items: center;
}

.action-group__item {
  display: flex;
  align-items: center;
}

.action-divider {
  margin: 0 4px;
  height: 16px;
}

/* ===== 翻页区 ===== */
.pagination-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  padding: 12px 20px;
  margin-top: 12px;
}

.pagination-info {
  font-size: 13px;
  color: #606266;
  font-feature-settings: 'tnum';
}

.pagination-info strong {
  color: #303133;
  font-weight: 600;
}

.pagination {
  --el-pagination-button-bg-color: #fff;
  --el-pagination-hover-color: #409EFF;
}

/* ===== 分享弹窗 ===== */
.share-dialog {
  text-align: center;
}
.share-form {
  margin-bottom: 12px;
  text-align: left;
}
.share-link {
  padding: 10px 12px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  word-break: break-all;
  text-align: left;
  min-height: 42px;
}
.share-qr {
  width: 220px;
  height: 220px;
  margin: 16px auto 8px;
  display: block;
}
.share-tip {
  color: #909399;
  font-size: 12px;
}

/* ===== 日志弹窗 ===== */
.log-dialog {
  text-align: left;
}
.log-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.log-file {
  font-weight: 600;
  font-size: 15px;
}
.log-info {
  margin-bottom: 16px;
}
.log-error {
  margin-bottom: 16px;
}
.log-error-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  margin-bottom: 8px;
}
.log-error-content {
  background: #fef0f0;
  color: #f56c6c;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}
.log-timeline {
  margin-top: 4px;
}
.step-spin {
  margin-left: 6px;
  vertical-align: middle;
  animation: spin 1s linear infinite;
  color: #e6a23c;
  font-size: 14px;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
