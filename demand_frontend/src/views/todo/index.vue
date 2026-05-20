<template>
  <PageContainer :title="pageTitle">
    <TableCard>
      <template #header>
        <div class="todo-toolbar" v-if="isWorkflowApprovalPage">
          <div class="toolbar-left">
            <div class="summary-chip pending">待审核 {{ summary.pending }}</div>
            <div class="summary-chip approved">已通过 {{ summary.approved }}</div>
            <div class="summary-chip rejected">已拒绝 {{ summary.rejected }}</div>
          </div>
          <div class="toolbar-right">
            <el-select v-model="projectFilter" clearable placeholder="筛选项目" style="width: 180px">
              <el-option
                v-for="item in projectOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索项目名 / 版本名"
              style="width: 220px"
            />
            <el-select v-model="statusFilter" placeholder="筛选审核状态" clearable style="width: 180px">
              <el-option label="全部状态" value="" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
          </div>
        </div>
      </template>
      <template #table>
        <el-table v-loading="loading" :data="filteredTaskList" border class="todo-table">
          <el-table-column label="任务名称" min-width="220">
            <template #default="{ row }">
              <div class="task-name-cell">
                <span class="task-name">{{ row.versionName || `V${row.version}` }}</span>
                <span class="task-project">{{ row.projectName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="任务状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交人" width="120">
            <template #default="{ row }">{{ row.submitterName || '-' }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column label="停留时间" width="120">
            <template #default="{ row }">
              {{ row.status === 'PENDING' ? getStayDuration(row.submittedAt) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="结束时间" width="180">
            <template #default="{ row }">
              {{ row.approvedAt ? formatDateTime(row.approvedAt) : '-' }}
            </template>
          </el-table-column>
          <el-table-column v-if="isWorkflowApprovalPage" label="审核人" width="120">
            <template #default="{ row }">
              {{ row.approverName || '-' }}
            </template>
          </el-table-column>
          <el-table-column v-if="isWorkflowApprovalPage" label="审核意见" min-width="180">
            <template #default="{ row }">
              <el-tooltip v-if="row.comment" :content="row.comment" placement="top">
                <span class="comment-text">{{ row.comment }}</span>
              </el-tooltip>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                @click="openDetail(row)"
              >
                详情
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="handleViewVersion(row)"
              >
                查看配置
              </el-button>
              <el-button
                v-if="row.status === 'PENDING' && canProcessApproval"
                type="primary"
                size="small"
                @click="handleApprove(row)"
              >
                通过
              </el-button>
              <el-button
                v-if="row.status === 'PENDING' && canProcessApproval"
                type="danger"
                size="small"
                @click="handleReject(row)"
              >
                拒绝
              </el-button>
              <span v-else class="completed-text">
                {{ row.status === 'APPROVED' ? '已通过' : '已拒绝' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </TableCard>

    <!-- 审核对话框 -->
    <el-dialog
      v-model="processDialogVisible"
      :title="processAction === 'approve' ? '审核通过' : '审核拒绝'"
      width="500px"
      class="process-dialog"
    >
      <div v-if="currentTask" class="process-content">
        <el-descriptions :column="1" border class="task-info">
          <el-descriptions-item label="工作流版本">
            {{ currentTask.versionName || `V${currentTask.version}` }}
          </el-descriptions-item>
          <el-descriptions-item label="所属项目">
            {{ currentTask.projectName }}
          </el-descriptions-item>
          <el-descriptions-item label="提交人">
            {{ currentTask.submitterName }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatDateTime(currentTask.submittedAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form class="comment-form" style="margin-top: 20px">
          <el-form-item label="审核意见" required>
            <el-input
              v-model="processComment"
              type="textarea"
              :rows="4"
              :placeholder="processAction === 'approve' ? '请输入通过意见（可选）' : '请输入拒绝原因'"
              :maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="processDialogVisible = false">取消</el-button>
        <el-button
          :type="processAction === 'approve' ? 'primary' : 'danger'"
          :loading="submitting"
          @click="confirmProcess"
        >
          确认{{ processAction === 'approve' ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="detailDrawerVisible"
      title="审核详情"
      size="480px"
      direction="rtl"
    >
      <template v-if="detailTask">
        <div class="detail-section">
          <div class="detail-header">
            <div>
              <div class="detail-title">{{ detailTask.versionName || `V${detailTask.version}` }}</div>
              <div class="detail-subtitle">{{ detailTask.projectName || '未命名项目' }}</div>
            </div>
            <el-tag :type="statusTagType(detailTask.status)" effect="light">
              {{ statusLabel(detailTask.status) }}
            </el-tag>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="版本号">
              V{{ detailTask.version || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="提交人">
              {{ detailTask.submitterName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ formatDateTime(detailTask.submittedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="审核人">
              {{ detailTask.approverName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="结束时间">
              {{ detailTask.approvedAt ? formatDateTime(detailTask.approvedAt) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="审核意见">
              {{ detailTask.comment || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">流程概览</div>
          <div v-loading="detailLoading" class="graph-summary">
            <template v-if="detailVersionConfig">
              <div class="summary-grid">
                <div class="summary-card">
                  <div class="summary-value">{{ detailVersionConfig.nodes.length }}</div>
                  <div class="summary-label">节点数</div>
                </div>
                <div class="summary-card">
                  <div class="summary-value">{{ detailVersionConfig.edges.length }}</div>
                  <div class="summary-label">连线数</div>
                </div>
                <div class="summary-card">
                  <div class="summary-value">{{ nodeTypeCount('approval') }}</div>
                  <div class="summary-label">审批节点</div>
                </div>
                <div class="summary-card">
                  <div class="summary-value">{{ nodeTypeCount('condition') }}</div>
                  <div class="summary-label">条件节点</div>
                </div>
              </div>
              <div class="node-list">
                <div class="detail-section-title minor">节点清单</div>
                <div
                  v-for="node in detailVersionConfig.nodes"
                  :key="node.nodeId"
                  class="node-item"
                >
                  <span class="node-name">{{ node.nodeName }}</span>
                  <el-tag size="small" effect="plain">{{ getNodeTypeLabel(node.nodeType) }}</el-tag>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无流程详情" :image-size="80" />
          </div>
        </div>

        <div class="drawer-actions">
          <el-button @click="handleViewVersion(detailTask)">查看配置</el-button>
          <el-button
            v-if="detailTask.status === 'PENDING' && canProcessApproval"
            type="primary"
            @click="handleApprove(detailTask)"
          >
            审核通过
          </el-button>
          <el-button
            v-if="detailTask.status === 'PENDING' && canProcessApproval"
            type="danger"
            @click="handleReject(detailTask)"
          >
            审核拒绝
          </el-button>
        </div>
      </template>
    </el-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatDate as formatDateTime } from '@/utils/format'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import { usePermission } from '@/composables/usePermission'
import {
  getPendingApprovals,
  getWorkflowApprovals,
  approveWorkflow,
  rejectWorkflow,
  getVersionConfig
} from '@/api/modules/workflow-visual'
import type { WorkflowApprovalDTO, WorkflowConfigDTO } from '@/types/workflow-visual'

const route = useRoute()
const router = useRouter()
const { hasAnyRole } = usePermission()
const loading = ref(false)
const taskList = ref<WorkflowApprovalDTO[]>([])
const processDialogVisible = ref(false)
const currentTask = ref<WorkflowApprovalDTO | null>(null)
const processAction = ref<'approve' | 'reject'>('approve')
const processComment = ref('')
const submitting = ref(false)
const statusFilter = ref('')
const keyword = ref('')
const projectFilter = ref<number | undefined>()
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailTask = ref<WorkflowApprovalDTO | null>(null)
const detailVersionConfig = ref<WorkflowConfigDTO | null>(null)

const pageTitle = computed(() => route.path === '/settings/workflow-approvals' ? '工作流审核' : '待办任务')
const isWorkflowApprovalPage = computed(() => route.path === '/settings/workflow-approvals')
const canProcessApproval = computed(() => hasAnyRole(['admin']))
const projectOptions = computed(() => {
  const projectMap = new Map<number, string>()
  taskList.value.forEach((item) => {
    if (typeof item.projectId === 'number' && item.projectName) {
      projectMap.set(item.projectId, item.projectName)
    }
  })
  return Array.from(projectMap.entries())
    .map(([value, label]) => ({ value, label }))
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
})
const summary = computed(() => ({
  pending: taskList.value.filter(item => item.status === 'PENDING').length,
  approved: taskList.value.filter(item => item.status === 'APPROVED').length,
  rejected: taskList.value.filter(item => item.status === 'REJECTED').length
}))
const filteredTaskList = computed(() => {
  return taskList.value.filter((item) => {
    const matchesStatus = !statusFilter.value || item.status === statusFilter.value
    const matchesProject = !projectFilter.value || item.projectId === projectFilter.value
    if (!matchesStatus || !matchesProject) {
      return false
    }

    if (!keyword.value.trim()) {
      return true
    }

    const searchText = `${item.projectName || ''} ${item.versionName || ''} V${item.version || ''}`.toLowerCase()
    return searchText.includes(keyword.value.trim().toLowerCase())
  })
})

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝'
  }
  return map[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

function getStayDuration(submittedAt: string): string {
  if (!submittedAt) return '-'
  const start = new Date(submittedAt).getTime()
  const now = Date.now()
  const diff = now - start
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  if (hours > 24) {
    const days = Math.floor(hours / 24)
    return `${days}天${hours % 24}小时`
  }
  return `${hours}小时${minutes}分钟`
}

function getNodeTypeLabel(type?: string) {
  const map: Record<string, string> = {
    start: '开始节点',
    approval: '审批节点',
    cc: '抄送节点',
    condition: '条件节点',
    end: '结束节点'
  }
  return map[type || ''] || type || '未定义'
}

function nodeTypeCount(type: string) {
  return detailVersionConfig.value?.nodes.filter(item => item.nodeType === type).length || 0
}

function applyQueryFilters() {
  const routeStatus = Array.isArray(route.query.status) ? route.query.status[0] : route.query.status
  const routeKeyword = Array.isArray(route.query.keyword) ? route.query.keyword[0] : route.query.keyword
  const routeProjectId = Array.isArray(route.query.projectId) ? route.query.projectId[0] : route.query.projectId

  statusFilter.value = typeof routeStatus === 'string' ? routeStatus : ''
  keyword.value = typeof routeKeyword === 'string' ? routeKeyword : ''
  projectFilter.value = routeProjectId ? Number(routeProjectId) : undefined
}

async function fetchTasks() {
  loading.value = true
  try {
    const res = isWorkflowApprovalPage.value
      ? await getWorkflowApprovals()
      : await getPendingApprovals()
    taskList.value = res || []
  } catch (error) {
    console.error('获取待办任务失败:', error)
    taskList.value = []
  } finally {
    loading.value = false
  }
}

async function openDetail(row: WorkflowApprovalDTO) {
  detailTask.value = row
  detailDrawerVisible.value = true
  detailLoading.value = true
  detailVersionConfig.value = null
  try {
    const version = await getVersionConfig(row.workflowVersionId)
    detailVersionConfig.value = version.config || null
  } catch (error) {
    ElMessage.error('加载流程详情失败')
  } finally {
    detailLoading.value = false
  }
}

function handleApprove(row: WorkflowApprovalDTO) {
  currentTask.value = row
  processAction.value = 'approve'
  processComment.value = ''
  processDialogVisible.value = true
  detailDrawerVisible.value = false
}

function handleReject(row: WorkflowApprovalDTO) {
  currentTask.value = row
  processAction.value = 'reject'
  processComment.value = ''
  processDialogVisible.value = true
  detailDrawerVisible.value = false
}

function handleViewVersion(row: WorkflowApprovalDTO) {
  if (!row.workflowVersionId) return
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.workflowVersionId,
      mode: 'view',
      sourceMenu: resolveActiveMenuPath(route),
    }
  })
}

async function confirmProcess() {
  if (!canProcessApproval.value) {
    ElMessage.warning('当前账号仅可查看审核记录，无法执行审批操作')
    return
  }

  if (processAction.value === 'reject' && !processComment.value.trim()) {
    ElMessage.warning('请输入拒绝原因')
    return
  }

  submitting.value = true
  try {
    if (processAction.value === 'approve') {
      await approveWorkflow(currentTask.value!.id, { comment: processComment.value })
      ElMessage.success('审核通过')
    } else {
      await rejectWorkflow(currentTask.value!.id, { comment: processComment.value })
      ElMessage.success('已拒绝')
    }
    processDialogVisible.value = false
    await fetchTasks()
    if (currentTask.value?.id) {
      detailTask.value = taskList.value.find(item => item.id === currentTask.value?.id) || null
    }
  } catch (error) {
    const errorMsg = (error as any)?.response?.data?.message || (error as Error)?.message || '操作失败'
    ElMessage.error(errorMsg)
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.query,
  () => {
    applyQueryFilters()
  },
  { immediate: true }
)

onMounted(() => {
  fetchTasks()
})
</script>

<style lang="scss" scoped>
.todo-table {
  width: 100%;
}

.todo-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-chip {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  line-height: 1;
  border: 1px solid transparent;
}

.summary-chip.pending {
  color: #b88230;
  background: #fff7e6;
  border-color: #f3d19e;
}

.summary-chip.approved {
  color: #3f8f53;
  background: #f0f9eb;
  border-color: #b3e19d;
}

.summary-chip.rejected {
  color: #c45656;
  background: #fef0f0;
  border-color: #fab6b6;
}

.task-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-name {
  font-weight: 600;
  color: var(--text-color);
}

.task-project {
  font-size: 12px;
  color: var(--text-color-secondary);
}

.completed-text {
  color: var(--text-color-secondary);
  font-size: 14px;
}

.comment-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-color-secondary);
}

.task-info {
  margin-bottom: 0;
}

.detail-section + .detail-section {
  margin-top: 20px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.detail-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}

.detail-section-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.detail-section-title.minor {
  margin-top: 16px;
}

.graph-summary {
  min-height: 140px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
}

.summary-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.summary-label {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.node-list {
  margin-top: 4px;
}

.node-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
}

.node-item + .node-item {
  margin-top: 8px;
}

.node-name {
  color: #303133;
  font-size: 13px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
