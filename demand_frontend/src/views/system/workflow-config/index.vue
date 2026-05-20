<template>
  <PageContainer :title="pageTitle">
    <div class="workflow-management-page">
      <el-card shadow="never" class="overview-card">
        <div class="overview-header">
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageDescription }}</p>
          </div>
          <el-button type="primary" @click="createNewWorkflow">
            <el-icon><Plus /></el-icon>
            新建工作流
          </el-button>
        </div>

        <div class="overview-metrics">
          <div class="metric-card">
            <div class="metric-value">{{ versions.length }}</div>
            <div class="metric-label">工作流版本</div>
          </div>
          <div class="metric-card success">
            <div class="metric-value">{{ activeVersionCount }}</div>
            <div class="metric-label">启用中版本</div>
          </div>
          <div class="metric-card warning">
            <div class="metric-value">{{ approvalSummary.pending }}</div>
            <div class="metric-label">待审核记录</div>
          </div>
          <div class="metric-card danger">
            <div class="metric-value">{{ approvalSummary.rejected }}</div>
            <div class="metric-label">已拒绝记录</div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="management-card">
        <template #header>
          <div class="card-header">
            <div class="header-copy">
              <h3>工作流版本与审核管理</h3>
              <p>支持查看配置、编辑版本、审核处理、启停切换和安全删除。</p>
            </div>
            <el-button @click="reloadAllData">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </el-button>
          </div>
        </template>

        <el-tabs v-model="activeTab" class="management-tabs">
          <el-tab-pane label="版本管理" name="versions">
            <div class="table-toolbar">
              <div class="toolbar-left">
                <el-select v-model="activationFilter" clearable placeholder="筛选启停状态" style="width: 160px">
                  <el-option label="全部状态" value="" />
                  <el-option label="已启用" value="ACTIVE" />
                  <el-option label="未启用" value="INACTIVE" />
                </el-select>
                <el-select v-model="versionApprovalStatusFilter" clearable placeholder="筛选审核状态" style="width: 180px">
                  <el-option label="全部状态" value="" />
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="审核中" value="PENDING" />
                  <el-option label="已通过" value="APPROVED" />
                  <el-option label="已拒绝" value="REJECTED" />
                </el-select>
              </div>
              <div class="toolbar-right">
                <el-input
                  v-model="versionKeyword"
                  clearable
                  placeholder="搜索版本名称 / 版本号"
                  style="width: 240px"
                />
              </div>
            </div>

            <el-table :data="pagedVersions" border v-loading="versionLoading">
              <el-table-column prop="version" label="版本号" width="110">
                <template #default="{ row }">V{{ row.version }}</template>
              </el-table-column>
              <el-table-column prop="name" label="版本名称" min-width="220" />
              <el-table-column label="适用范围" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.projectId === GLOBAL_WORKFLOW_PROJECT_ID ? 'primary' : 'info'" effect="light">
                    {{ row.projectId === GLOBAL_WORKFLOW_PROJECT_ID ? '全局流程' : `项目 ${row.projectId}` }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="启停状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.isActive === 1 ? 'success' : 'info'">
                    {{ row.isActive === 1 ? '已启用' : '未启用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="审核状态" width="140">
                <template #default="{ row }">
                  <el-tag :type="approvalTagType(versionApprovalStatus(row))">
                    {{ approvalStatusLabel(versionApprovalStatus(row)) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="creatorName" label="创建人" width="140" />
              <el-table-column label="创建时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="最近提交" width="180">
                <template #default="{ row }">
                  {{ row.latestSubmittedAt ? formatDateTime(row.latestSubmittedAt) : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" min-width="360" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="viewWorkflow(row)">查看</el-button>
                  <el-button link type="primary" @click="editWorkflow(row)">编辑</el-button>
                  <el-button link type="info" @click="focusApprovalHistory(row)">审核记录</el-button>
                  <el-button
                    link
                    :type="row.isActive === 1 ? 'warning' : 'success'"
                    @click="handleToggleActivation(row)"
                  >
                    {{ row.isActive === 1 ? '停用' : '启用' }}
                  </el-button>
                  <el-button
                    link
                    type="warning"
                    :disabled="versionApprovalStatus(row) === 'PENDING'"
                    @click="openVersionMetaDialog(row)"
                  >
                    版本信息
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="row.isActive === 1"
                    @click="handleDeleteVersion(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-container">
              <el-pagination
                v-model:current-page="pagination.page"
                v-model:page-size="pagination.size"
                :total="filteredVersions.length"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="审核记录" name="approvals">
            <div class="approval-toolbar">
              <div class="toolbar-left">
                <div class="summary-chip pending">待审核 {{ approvalSummary.pending }}</div>
                <div class="summary-chip approved">已通过 {{ approvalSummary.approved }}</div>
                <div class="summary-chip rejected">已拒绝 {{ approvalSummary.rejected }}</div>
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
                  v-model="approvalKeyword"
                  clearable
                  placeholder="搜索项目名 / 版本名"
                  style="width: 220px"
                />
                <el-select v-model="approvalStatusFilter" placeholder="筛选审核状态" clearable style="width: 180px">
                  <el-option label="全部状态" value="" />
                  <el-option label="待审核" value="PENDING" />
                  <el-option label="已通过" value="APPROVED" />
                  <el-option label="已拒绝" value="REJECTED" />
                </el-select>
              </div>
            </div>

            <el-table v-loading="approvalLoading" :data="filteredApprovals" border class="approval-table">
              <el-table-column label="任务名称" min-width="240">
                <template #default="{ row }">
                  <div class="task-name-cell">
                    <span class="task-name">{{ row.versionName || `V${row.version}` }}</span>
                    <span class="task-project">{{ row.projectName }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="任务状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="approvalTagType(row.status)" size="small">
                    {{ approvalStatusLabel(row.status) }}
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
              <el-table-column label="审核人" width="120">
                <template #default="{ row }">
                  {{ row.approverName || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="审核意见" min-width="180">
                <template #default="{ row }">
                  <el-tooltip v-if="row.comment" :content="row.comment" placement="top">
                    <span class="comment-text">{{ row.comment }}</span>
                  </el-tooltip>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
                  <el-button link type="primary" size="small" @click="handleViewVersion(row)">查看配置</el-button>
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
                    {{ row.status === 'APPROVED' ? '已通过' : row.status === 'REJECTED' ? '已拒绝' : '-' }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <el-dialog v-model="versionDialogVisible" title="编辑版本信息" width="460px">
      <el-form label-position="top">
        <el-form-item label="版本号">
          <el-input-number
            v-model="versionDialogForm.version"
            :min="1"
            :step="1"
            step-strictly
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="版本名称">
          <el-input
            v-model="versionDialogForm.name"
            maxlength="50"
            show-word-limit
            placeholder="请输入版本名称"
          />
        </el-form-item>
        <div v-if="versionDialogHint" class="dialog-hint" :class="versionDialogHint.type">
          {{ versionDialogHint.message }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="versionSaving" @click="handleSaveVersionMeta">保存</el-button>
      </template>
    </el-dialog>

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

        <el-form class="comment-form">
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
            <el-tag :type="approvalTagType(detailTask.status)" effect="light">
              {{ approvalStatusLabel(detailTask.status) }}
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { formatDate as formatDateTime } from '@/utils/format'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import { usePermission } from '@/composables/usePermission'
import {
  GLOBAL_WORKFLOW_PROJECT_ID,
  approveWorkflow,
  deleteWorkflowVersion,
  getVersionConfig,
  getVersionHistory,
  getWorkflowApprovals,
  rejectWorkflow,
  updateWorkflowVersionActivation,
  updateWorkflowVersionMeta,
} from '@/api/modules/workflow-visual'
import type {
  WorkflowApprovalDTO,
  WorkflowConfigDTO,
  WorkflowVersionDTO,
  WorkflowVersionMetaUpdateDTO,
} from '@/types/workflow-visual'

const route = useRoute()
const router = useRouter()
const { hasAnyRole } = usePermission()

const versionLoading = ref(false)
const approvalLoading = ref(false)
const versions = ref<WorkflowVersionDTO[]>([])
const approvals = ref<WorkflowApprovalDTO[]>([])
const activeTab = ref<'versions' | 'approvals'>('versions')
const activationFilter = ref('')
const versionApprovalStatusFilter = ref('')
const versionKeyword = ref('')
const approvalStatusFilter = ref('')
const approvalKeyword = ref('')
const projectFilter = ref<number | undefined>()
const versionDialogVisible = ref(false)
const versionSaving = ref(false)
const editingVersion = ref<WorkflowVersionDTO>()
const processDialogVisible = ref(false)
const currentTask = ref<WorkflowApprovalDTO | null>(null)
const processAction = ref<'approve' | 'reject'>('approve')
const processComment = ref('')
const submitting = ref(false)
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailTask = ref<WorkflowApprovalDTO | null>(null)
const detailVersionConfig = ref<WorkflowConfigDTO | null>(null)

const versionDialogForm = reactive<WorkflowVersionMetaUpdateDTO>({
  version: 1,
  name: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
})

const pageTitle = computed(() => route.path === '/settings/workflow-approvals' ? '工作流管理' : '工作流配置')
const pageDescription = computed(() => route.path === '/settings/workflow-approvals'
  ? '集中管理工作流版本、审核记录、启停切换与删除操作。'
  : '维护工作流版本、流程审核和生效状态。')
const canProcessApproval = computed(() => hasAnyRole(['admin']))
const activeVersionCount = computed(() => versions.value.filter(item => item.isActive === 1).length)
const sourceMenuPath = computed(() => resolveActiveMenuPath(route))
const approvalSummary = computed(() => ({
  pending: approvals.value.filter(item => item.status === 'PENDING').length,
  approved: approvals.value.filter(item => item.status === 'APPROVED').length,
  rejected: approvals.value.filter(item => item.status === 'REJECTED').length,
}))
const projectOptions = computed(() => {
  const projectMap = new Map<number, string>()
  approvals.value.forEach((item) => {
    if (typeof item.projectId === 'number' && item.projectName) {
      projectMap.set(item.projectId, item.projectName)
    }
  })
  return Array.from(projectMap.entries())
    .map(([value, label]) => ({ value, label }))
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
})
const duplicatedVersion = computed(() => {
  if (!editingVersion.value?.id || !versionDialogForm.version) return undefined
  return versions.value.find((item) => item.version === versionDialogForm.version && item.id !== editingVersion.value?.id)
})
const versionDialogHint = computed(() => {
  const trimmedName = versionDialogForm.name.trim()
  if (!versionDialogForm.version || versionDialogForm.version < 1) {
    return { type: 'warning', message: '版本号需大于 0' }
  }
  if (duplicatedVersion.value) {
    return { type: 'error', message: `版本号 V${versionDialogForm.version} 已存在` }
  }
  if (!trimmedName) {
    return { type: 'warning', message: '版本名称不能为空' }
  }
  return { type: 'success', message: '版本信息可保存' }
})

const filteredVersions = computed(() => {
  const keyword = versionKeyword.value.trim().toLowerCase()
  return versions.value.filter((item) => {
    const approvalStatus = versionApprovalStatus(item)
    const matchesActivation = !activationFilter.value
      || (activationFilter.value === 'ACTIVE' && item.isActive === 1)
      || (activationFilter.value === 'INACTIVE' && item.isActive !== 1)
    const matchesApproval = !versionApprovalStatusFilter.value || approvalStatus === versionApprovalStatusFilter.value
    if (!matchesActivation || !matchesApproval) {
      return false
    }
    if (!keyword) {
      return true
    }
    return `${item.name} V${item.version}`.toLowerCase().includes(keyword)
  })
})

const pagedVersions = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return filteredVersions.value.slice(start, start + pagination.size)
})

const filteredApprovals = computed(() => {
  return approvals.value.filter((item) => {
    const matchesStatus = !approvalStatusFilter.value || item.status === approvalStatusFilter.value
    const matchesProject = projectFilter.value === undefined || item.projectId === projectFilter.value
    if (!matchesStatus || !matchesProject) {
      return false
    }

    const keyword = approvalKeyword.value.trim().toLowerCase()
    if (!keyword) {
      return true
    }

    const searchText = `${item.projectName || ''} ${item.versionName || ''} V${item.version || ''}`.toLowerCase()
    return searchText.includes(keyword)
  })
})

watch(
  () => [route.query.tab, route.query.status, route.query.keyword, route.query.projectId],
  () => {
    const routeTab = Array.isArray(route.query.tab) ? route.query.tab[0] : route.query.tab
    activeTab.value = routeTab === 'approvals' ? 'approvals' : 'versions'

    const routeStatus = Array.isArray(route.query.status) ? route.query.status[0] : route.query.status
    const routeKeyword = Array.isArray(route.query.keyword) ? route.query.keyword[0] : route.query.keyword
    const routeProjectId = Array.isArray(route.query.projectId) ? route.query.projectId[0] : route.query.projectId

    approvalStatusFilter.value = typeof routeStatus === 'string' ? routeStatus : ''
    approvalKeyword.value = typeof routeKeyword === 'string' ? routeKeyword : ''
    projectFilter.value = routeProjectId ? Number(routeProjectId) : undefined
  },
  { immediate: true },
)

watch(
  () => [activationFilter.value, versionApprovalStatusFilter.value, versionKeyword.value],
  () => {
    pagination.page = 1
  },
)

function versionApprovalStatus(row: WorkflowVersionDTO) {
  return row.latestApprovalStatus || 'DRAFT'
}

function approvalStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
  }
  return map[status] || status
}

function approvalTagType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
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
    end: '结束节点',
  }
  return map[type || ''] || type || '未定义'
}

function nodeTypeCount(type: string) {
  return detailVersionConfig.value?.nodes.filter(item => item.nodeType === type).length || 0
}

async function loadVersions() {
  versionLoading.value = true
  try {
    versions.value = await getVersionHistory(GLOBAL_WORKFLOW_PROJECT_ID) || []
    const maxPage = Math.max(1, Math.ceil(filteredVersions.value.length / pagination.size))
    if (pagination.page > maxPage) {
      pagination.page = maxPage
    }
  } catch (error) {
    console.error('加载工作流版本失败:', error)
    ElMessage.error('加载工作流版本失败')
    versions.value = []
  } finally {
    versionLoading.value = false
  }
}

async function loadApprovals() {
  approvalLoading.value = true
  try {
    approvals.value = await getWorkflowApprovals() || []
  } catch (error) {
    console.error('加载工作流审核记录失败:', error)
    ElMessage.error('加载工作流审核记录失败')
    approvals.value = []
  } finally {
    approvalLoading.value = false
  }
}

async function reloadAllData() {
  await Promise.all([loadVersions(), loadApprovals()])
}

function createNewWorkflow() {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      projectId: String(GLOBAL_WORKFLOW_PROJECT_ID),
      sourceMenu: sourceMenuPath.value,
    },
  })
}

function viewWorkflow(row: WorkflowVersionDTO) {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.id,
      projectId: String(row.projectId ?? GLOBAL_WORKFLOW_PROJECT_ID),
      mode: 'view',
      sourceMenu: sourceMenuPath.value,
    },
  })
}

function editWorkflow(row: WorkflowVersionDTO) {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.id,
      projectId: String(row.projectId ?? GLOBAL_WORKFLOW_PROJECT_ID),
      mode: 'edit',
      sourceMenu: sourceMenuPath.value,
    },
  })
}

function focusApprovalHistory(row: WorkflowVersionDTO) {
  activeTab.value = 'approvals'
  approvalStatusFilter.value = versionApprovalStatus(row) === 'DRAFT' ? '' : versionApprovalStatus(row)
  approvalKeyword.value = row.name
  projectFilter.value = row.projectId
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      tab: 'approvals',
      status: approvalStatusFilter.value || undefined,
      keyword: approvalKeyword.value || undefined,
      projectId: row.projectId !== undefined && row.projectId !== null ? String(row.projectId) : undefined,
    },
  })
}

const openVersionMetaDialog = (row: WorkflowVersionDTO) => {
  editingVersion.value = row
  versionDialogForm.version = row.version
  versionDialogForm.name = row.name
  versionDialogVisible.value = true
}

const handleSaveVersionMeta = async () => {
  if (!editingVersion.value) return

  const trimmedName = versionDialogForm.name.trim()
  if (!versionDialogForm.version || versionDialogForm.version < 1) {
    ElMessage.warning('版本号需大于 0')
    return
  }
  if (duplicatedVersion.value) {
    ElMessage.warning(`版本号 V${versionDialogForm.version} 已存在，请重新输入`)
    return
  }
  if (!trimmedName) {
    ElMessage.warning('版本名称不能为空')
    return
  }

  versionSaving.value = true
  try {
    await updateWorkflowVersionMeta(editingVersion.value.id, {
      version: versionDialogForm.version,
      name: trimmedName,
    })
    ElMessage.success('版本信息已更新')
    versionDialogVisible.value = false
    await loadVersions()
  } finally {
    versionSaving.value = false
  }
}

async function handleToggleActivation(row: WorkflowVersionDTO) {
  const targetActive = row.isActive !== 1
  const actionLabel = targetActive ? '启用' : '停用'
  await ElMessageBox.confirm(`确认${actionLabel}工作流“${row.name}”吗？`, `${actionLabel}工作流`, { type: 'warning' })
  await updateWorkflowVersionActivation(row.id, { active: targetActive })
  ElMessage.success(`工作流已${actionLabel}`)
  await loadVersions()
}

async function handleDeleteVersion(row: WorkflowVersionDTO) {
  await ElMessageBox.confirm(`确认删除工作流“${row.name}”吗？该操作无法恢复。`, '删除工作流', { type: 'warning' })
  await deleteWorkflowVersion(row.id)
  ElMessage.success('工作流已删除')
  await reloadAllData()
}

async function openDetail(row: WorkflowApprovalDTO) {
  detailTask.value = row
  detailDrawerVisible.value = true
  detailLoading.value = true
  detailVersionConfig.value = null
  try {
    const version = await getVersionConfig(row.workflowVersionId)
    detailVersionConfig.value = version.config || null
  } catch {
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
      sourceMenu: sourceMenuPath.value,
    },
  })
}

async function confirmProcess() {
  if (!currentTask.value) return
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
      await approveWorkflow(currentTask.value.id, { comment: processComment.value })
      ElMessage.success('审核通过')
    } else {
      await rejectWorkflow(currentTask.value.id, { comment: processComment.value })
      ElMessage.success('已拒绝')
    }
    processDialogVisible.value = false
    await reloadAllData()
    if (currentTask.value?.id) {
      detailTask.value = approvals.value.find(item => item.id === currentTask.value?.id) || null
    }
  } catch (error) {
    const errorMsg = (error as any)?.response?.data?.message || (error as Error)?.message || '操作失败'
    ElMessage.error(errorMsg)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  reloadAllData()
})
</script>

<style scoped lang="scss">
.workflow-management-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-card,
.management-card {
  :deep(.el-card__body) {
    padding: 20px;
  }
}

.overview-header,
.card-header,
.table-toolbar,
.approval-toolbar,
.toolbar-left,
.toolbar-right,
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.overview-header {
  align-items: flex-start;
}

.overview-header h2,
.card-header h3 {
  margin: 0;
  color: #303133;
}

.overview-header p,
.card-header p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.metric-card,
.summary-card {
  padding: 16px;
  border-radius: 14px;
  border: 1px solid #ebeef5;
  background: #f8fafc;
}

.metric-card.success {
  background: #f0f9eb;
}

.metric-card.warning {
  background: #fff7e6;
}

.metric-card.danger {
  background: #fef0f0;
}

.metric-value,
.summary-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.metric-label,
.summary-label {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.management-tabs {
  margin-top: -8px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
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
  color: #303133;
}

.task-project,
.completed-text,
.comment-text,
.detail-subtitle,
.node-name {
  color: #606266;
}

.comment-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.comment-form {
  margin-top: 20px;
}

.detail-section + .detail-section {
  margin-top: 20px;
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
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

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.dialog-hint {
  font-size: 12px;
  line-height: 1.6;

  &.success {
    color: #67c23a;
  }

  &.warning {
    color: #e6a23c;
  }

  &.error {
    color: #f56c6c;
  }
}

@media (max-width: 960px) {
  .overview-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .overview-metrics,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .pagination-container {
    justify-content: flex-start;
  }
}
</style>
