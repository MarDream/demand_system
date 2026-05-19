<template>
  <PageContainer :title="kbInfo?.name || '知识库详情'">
    <template #headerActions>
      <el-button @click="goBack">返回</el-button>
      <el-button type="primary" :disabled="!kbInfo || deleting" @click="openEditDialog">编辑知识库</el-button>
      <el-button type="danger" :loading="deleting" :disabled="!kbInfo" @click="handleDeleteKnowledgeBase">
        删除知识库
      </el-button>
    </template>

    <div class="detail-layout">
      <div>
        <div class="section-header">
          <h3>文档列表</h3>
          <el-button type="primary" size="small" @click="showUploadDialog = true">上传文档</el-button>
        </div>

        <div class="table-filters">
          <el-input
            v-model="documentFilters.fileName"
            clearable
            placeholder="按文档名称检索"
            class="table-filters__name"
            @keyup.enter="applyFilters"
          />
          <el-select v-model="documentFilters.status" clearable placeholder="按状态筛选" class="table-filters__status">
            <el-option label="待处理" value="pending" />
            <el-option label="已解析" value="parsed" />
            <el-option label="索引中" value="indexing" />
            <el-option label="已索引" value="indexed" />
            <el-option label="已入库" value="stored" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-date-picker
            v-model="documentFilters.createdAtRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="上传开始时间"
            end-placeholder="上传结束时间"
            class="table-filters__time"
          />
          <el-button type="primary" @click="applyFilters">检索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <el-table :data="store.documents" v-loading="store.loading" stripe @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="45" />
          <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="canPreview(row.fileType)" class="file-name-link" @click="handlePreview(row)">{{ row.fileName }}</span>
              <span v-else>{{ row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="类型" width="80" />
          <el-table-column prop="fileSize" label="大小" width="100">
            <template #default="{ row }">
              {{ formatSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="downloadCount" label="下载次数" width="96" />
          <el-table-column prop="uploaderName" label="上传人" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.uploaderName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块" width="80" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tooltip v-if="row.status === 'failed' && row.errorMessage" :content="row.errorMessage" placement="top">
                <el-tag size="small" type="danger">{{ statusLabel(row.status) }}</el-tag>
              </el-tooltip>
              <el-tag v-else size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-tooltip v-if="canPreview(row.fileType)" content="预览">
                <el-button link type="primary" size="small" @click="handlePreview(row)"><el-icon><View /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip content="下载">
                <el-button link type="primary" size="small" @click="handleDownload(row)"><el-icon><Download /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip content="分享">
                <el-button link type="primary" size="small" @click="handleShare(row)"><el-icon><Share /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip v-if="row.status === 'failed'" content="重传">
                <el-button link type="warning" size="small" @click="handleRetry(row)"><el-icon><RefreshRight /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button link type="danger" size="small" @click="handleDelete(row)"><el-icon><Delete /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip content="日志">
                <el-button link type="info" size="small" @click="showLog(row)"><el-icon><Document /></el-icon></el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="selectedRows.length > 0" class="batch-actions">
          <span class="batch-info">已选 {{ selectedRows.length }} 项</span>
          <el-button type="warning" size="small" :loading="batchRetrying" @click="handleBatchRetry">
            批量重传
          </el-button>
          <el-button type="danger" size="small" :loading="batchDeleting" @click="handleBatchDelete">
            批量删除
          </el-button>
        </div>

        <el-pagination
          v-if="store.totalDocs > 10"
          :total="store.totalDocs"
          :current-page="documentFilters.pageNum"
          :page-size="documentFilters.pageSize"
          layout="prev, pager, next"
          class="pagination"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <DocumentUploadDialog
      v-model="showUploadDialog"
      :knowledge-base-id="kbId"
      :show-knowledge-base-select="false"
      @uploaded="handleUploaded"
    />

    <AppDialog v-model="editDialogVisible" title="编辑知识库" width="500px" @close="resetEditForm">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" placeholder="请输入知识库名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入描述" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdateKnowledgeBase">确定</el-button>
      </template>
    </AppDialog>

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
          <el-descriptions-item label="上传时间">{{ logDocument?.createdAt?.replace('T', ' ').substring(0, 19) || '-' }}</el-descriptions-item>
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
import { onMounted, reactive, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, View, Download, Share, RefreshRight, Delete, Document } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import AppDialog from '@/components/common/AppDialog.vue'
import DocumentUploadDialog from '@/components/document/DocumentUploadDialog.vue'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import {
  batchDeleteDocuments,
  deleteDocument,
  deleteKnowledgeBase,
  downloadDocumentBlob,
  getKnowledgeBase,
  retryDocuments,
  updateKnowledgeBase,
} from '@/api/modules/knowledge'
import type { KnowledgeBase, KnowledgeDocument } from '@/api/modules/knowledge'
import { KKFILEVIEW_SUPPORTED_EXTENSION_SET, normalizeFileExtension } from '@/constants/knowledgeDocument'

const route = useRoute()
const router = useRouter()
const store = useKnowledgeStore()
const kbId = Number(route.params.id)
const kbInfo = ref<KnowledgeBase | null>(null)
const showUploadDialog = ref(false)
const editDialogVisible = ref(false)
const submitting = ref(false)
const deleting = ref(false)
const shareDialogVisible = ref(false)
const currentShareLink = ref('')
const qrCodeUrl = ref('')
const currentShareDocument = ref<KnowledgeDocument | null>(null)
const editForm = reactive({ name: '', description: '' })
const shareOptions = reactive({
  expireHours: 24,
  requireLogin: false,
  oneTimeAccess: false
})

const selectedRows = ref<KnowledgeDocument[]>([])
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
  pageSize: 10,
})

watch(currentShareLink, async (value) => {
  if (!value) {
    qrCodeUrl.value = ''
    return
  }
  try {
    qrCodeUrl.value = await QRCode.toDataURL(value, {
      width: 220,
      margin: 1
    })
  } catch {
    qrCodeUrl.value = ''
    ElMessage.warning('二维码生成失败，请直接复制链接')
  }
})

onMounted(async () => {
  await fetchKnowledgeBase()
  await fetchDocumentList()
})

async function fetchKnowledgeBase() {
  try {
    const res = await getKnowledgeBase(kbId)
    kbInfo.value = (res as any)?.data || res
  } catch {}
}

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

function handlePageChange(page: number) {
  fetchDocumentList(page)
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

function goBack() {
  router.push('/settings/knowledge')
}

function resetEditForm() {
  editForm.name = ''
  editForm.description = ''
}

function openEditDialog() {
  if (!kbInfo.value) return
  editForm.name = kbInfo.value.name
  editForm.description = kbInfo.value.description || ''
  editDialogVisible.value = true
}

async function handleUpdateKnowledgeBase() {
  const name = editForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  submitting.value = true
  try {
    const res = await updateKnowledgeBase(kbId, { name, description: editForm.description.trim() })
    kbInfo.value = (res as any)?.data || res
    editDialogVisible.value = false
    resetEditForm()
    ElMessage.success('更新成功')
  } finally {
    submitting.value = false
  }
}

async function handleDeleteKnowledgeBase() {
  if (!kbInfo.value || deleting.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${kbInfo.value.name}」？删除后将同步清理该知识库下的文档和索引数据。`,
      '确认删除',
      { type: 'warning' }
    )
    deleting.value = true
    await deleteKnowledgeBase(kbId)
    ElMessage.success('删除成功')
    router.push('/settings/knowledge')
  } catch {
    // 用户取消或接口错误时保持当前页面状态。
  } finally {
    deleting.value = false
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
.table-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.table-filters__name {
  width: 240px;
}

.table-filters__status {
  width: 160px;
}

.table-filters__time {
  width: 360px;
}

.file-name-link {
  color: var(--el-color-primary);
  cursor: pointer;
  &:hover {
    text-decoration: underline;
  }
}
.pagination {
  margin-top: 16px;
  justify-content: center;
}
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
.batch-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0 0;
}
.batch-info {
  font-size: 13px;
  color: #606266;
}
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
