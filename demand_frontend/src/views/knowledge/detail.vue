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
      <!-- 左侧：文档列表 -->
      <div class="detail-left">
        <div class="section-header">
          <h3>文档列表</h3>
          <el-button type="primary" size="small" @click="showUploadDialog = true">上传文档</el-button>
        </div>

        <el-table :data="store.documents" v-loading="store.loading" stripe>
          <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="fileType" label="类型" width="80" />
          <el-table-column prop="fileSize" label="大小" width="100">
            <template #default="{ row }">
              {{ formatSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块" width="80" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleShare(row)">分享</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="store.totalDocs > 10"
          :total="store.totalDocs"
          :page-size="10"
          layout="prev, pager, next"
          class="pagination"
          @current-change="(p: number) => store.fetchDocuments(kbId, p, 10)"
        />
      </div>

      <!-- 右侧：检索区 -->
      <div class="detail-right">
        <h3>语义检索</h3>
        <el-input
          v-model="searchQuery"
          placeholder="输入检索内容..."
          @keyup.enter="handleSearch"
          clearable
        >
          <template #append>
            <el-button @click="handleSearch">检索</el-button>
          </template>
        </el-input>
        <el-radio-group v-model="searchMode" size="small" class="mode-group">
          <el-radio-button value="hybrid">混合</el-radio-button>
          <el-radio-button value="semantic">语义</el-radio-button>
          <el-radio-button value="keyword">关键词</el-radio-button>
        </el-radio-group>

        <div v-if="store.searchResults?.results?.length" class="search-results">
          <p class="result-count">找到 {{ store.searchResults.total }} 条结果</p>
          <el-card v-for="item in store.searchResults.results" :key="item.chunkId" class="result-card" shadow="never">
            <div class="result-header">
              <span class="result-file">{{ item.fileName }}</span>
              <el-tag size="small" type="success">{{ Math.round(item.score * 100) }}% 相关</el-tag>
            </div>
            <div v-if="item.sectionTitle" class="result-title">{{ item.sectionTitle }}</div>
            <div class="result-content">{{ item.content }}</div>
          </el-card>
        </div>
        <el-empty v-else-if="store.searchResults" description="未找到相关结果" />
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
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import AppDialog from '@/components/common/AppDialog.vue'
import DocumentUploadDialog from '@/components/document/DocumentUploadDialog.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { deleteKnowledgeBase, getKnowledgeBase, updateKnowledgeBase } from '@/api/modules/knowledge'
import type { KnowledgeBase, KnowledgeDocument, SearchMode } from '@/api/modules/knowledge'

const route = useRoute()
const router = useRouter()
const store = useKnowledgeStore()
const kbId = Number(route.params.id)
const kbInfo = ref<KnowledgeBase | null>(null)
const searchQuery = ref('')
const searchMode = ref<SearchMode>('hybrid')
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
  store.fetchDocuments(kbId, 1, 10)
})

async function fetchKnowledgeBase() {
  try {
    const res = await getKnowledgeBase(kbId)
    kbInfo.value = (res as any)?.data || res
  } catch {}
}

function handleUploaded() {
  store.fetchDocuments(kbId, 1, 10)
}

function handleDelete(doc: KnowledgeDocument) {
  ElMessageBox.confirm(`确定删除「${doc.fileName}」？`, '确认', { type: 'warning' })
    .then(() => store.removeDoc(kbId, doc.id).then(() => ElMessage.success('删除成功')))
    .catch(() => {})
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
    await navigator.clipboard.writeText(shareLink)
    currentShareLink.value = shareLink
    ElMessage.success('分享链接已生成并复制到剪贴板')
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

function handleSearch() {
  if (!searchQuery.value.trim()) return
  store.search(searchQuery.value, searchMode.value, kbId)
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

function statusType(status: string) {
  const map: Record<string, string> = { pending: 'info', parsed: 'warning', indexing: 'warning', indexed: 'success', failed: 'danger' }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = { pending: '待处理', parsed: '已解析', indexing: '索引中', indexed: '已索引', failed: '失败' }
  return map[status] || status
}
</script>

<style scoped>
.detail-layout {
  display: flex;
  gap: 24px;
}
.detail-left {
  flex: 1;
  min-width: 0;
}
.detail-right {
  width: 400px;
  flex-shrink: 0;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.section-header h3 {
  margin: 0;
}
.mode-group {
  margin: 8px 0 16px;
}
.search-results {
  margin-top: 16px;
}
.result-count {
  color: #909399;
  font-size: 13px;
  margin-bottom: 12px;
}
.result-card {
  margin-bottom: 12px;
}
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.result-file {
  font-weight: 600;
  font-size: 14px;
}
.result-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.result-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  max-height: 120px;
  overflow: hidden;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
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
</style>
