<template>
  <div class="workflow-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <h2>工作流配置</h2>
            <p class="subtitle">使用可视化流程图配置需求审批流程</p>
          </div>
          <el-button type="primary" @click="createNewWorkflow">
            <el-icon><Plus /></el-icon>
            新建工作流
          </el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-4"
      >
        <template #title>
          工作流配置说明
        </template>
        <div>
          <p>1. 当前页面维护的是全局标准工作流，审核通过后对全系统生效</p>
          <p>2. 保存配置后需要提交审核，审核通过后系统会自动切换到最新版本</p>
          <p>3. 支持5种节点类型：开始节点、审批节点、抄送节点、条件节点、结束节点</p>
        </div>
      </el-alert>

      <div class="table-toolbar">
        <el-select v-model="approvalStatusFilter" placeholder="筛选审核状态" clearable style="width: 180px">
          <el-option label="全部状态" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="审核中" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </div>

      <!-- 版本列表 -->
      <el-table :data="filteredVersions" border v-loading="loading">
        <el-table-column prop="version" label="版本号" width="100" />
        <el-table-column prop="name" label="版本名称" min-width="200" />
        <el-table-column label="适用范围" width="120">
          <template #default="{ row }">
            <el-tag :type="row.projectId === GLOBAL_WORKFLOW_PROJECT_ID ? 'primary' : 'info'" effect="light">
              {{ row.projectId === GLOBAL_WORKFLOW_PROJECT_ID ? '全局流程' : `项目 ${row.projectId}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.isActive === 1" type="success">已启用</el-tag>
            <el-tag v-else type="info">未启用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.latestApprovalStatus === 'PENDING'" type="warning">审核中</el-tag>
            <el-tag v-else-if="row.latestApprovalStatus === 'APPROVED'" type="success">已通过</el-tag>
            <el-tooltip
              v-else-if="row.latestApprovalStatus === 'REJECTED'"
              :content="row.latestApprovalComment || '审核已拒绝'"
              placement="top"
            >
              <el-tag type="danger">已拒绝</el-tag>
            </el-tooltip>
            <el-tag v-else type="info">草稿</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="最近提交" width="180">
          <template #default="{ row }">
            {{ row.latestSubmittedAt ? formatDate(row.latestSubmittedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="查看">
              <el-button link type="primary" @click="viewWorkflow(row)"><el-icon><View /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="编辑">
              <el-button link type="primary" @click="editWorkflow(row)"><el-icon><EditPen /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="审核记录">
              <el-button link type="info" @click="viewApprovalHistory(row)">记录</el-button>
            </el-tooltip>
            <el-tooltip content="版本信息">
              <el-button
                link
                type="warning"
                :disabled="row.latestApprovalStatus === 'PENDING'"
                @click="openVersionMetaDialog(row)"
              >
                <el-icon><Edit /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadVersions"
          @current-change="loadVersions"
        />
      </div>
    </el-card>

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
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, View, EditPen, Edit } from '@element-plus/icons-vue'
import {
  GLOBAL_WORKFLOW_PROJECT_ID,
  getVersionHistory,
  updateWorkflowVersionMeta
} from '@/api/modules/workflow-visual'
import type { WorkflowVersionDTO, WorkflowVersionMetaUpdateDTO } from '@/types/workflow-visual'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const versions = ref<WorkflowVersionDTO[]>([])
const approvalStatusFilter = ref('')
const versionDialogVisible = ref(false)
const versionSaving = ref(false)
const editingVersion = ref<WorkflowVersionDTO>()
const versionDialogForm = reactive<WorkflowVersionMetaUpdateDTO>({
  version: 1,
  name: ''
})

const pagination = ref({
  page: 1,
  size: 20,
  total: 0
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
  if (!approvalStatusFilter.value) {
    return versions.value
  }

  return versions.value.filter((item) => {
    const approvalStatus = item.latestApprovalStatus || 'DRAFT'
    return approvalStatus === approvalStatusFilter.value
  })
})

// 加载版本列表
const loadVersions = async () => {
  loading.value = true
  try {
    versions.value = await getVersionHistory(GLOBAL_WORKFLOW_PROJECT_ID) || []
    pagination.value.total = versions.value.length
  } catch (error) {
    console.error('加载版本列表失败:', error)
    ElMessage.error('加载版本列表失败')
  } finally {
    loading.value = false
  }
}

// 格式化日期
const formatDate = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

// 新建工作流
const createNewWorkflow = () => {
  router.push({
    path: '/system/workflow-config/editor',
    query: { projectId: String(GLOBAL_WORKFLOW_PROJECT_ID) }
  })
}

// 查看工作流
const viewWorkflow = (row: WorkflowVersionDTO) => {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.id,
      projectId: String(row.projectId ?? GLOBAL_WORKFLOW_PROJECT_ID),
      mode: 'view'
    }
  })
}

// 编辑工作流
const editWorkflow = (row: WorkflowVersionDTO) => {
  router.push({
    path: '/system/workflow-config/editor',
    query: {
      versionId: row.id,
      projectId: String(row.projectId ?? GLOBAL_WORKFLOW_PROJECT_ID),
      mode: 'edit'
    }
  })
}

const viewApprovalHistory = (row: WorkflowVersionDTO) => {
  router.push({
    path: '/settings/workflow-approvals',
    query: {
      projectId: row.projectId,
      keyword: row.name,
      status: row.latestApprovalStatus || ''
    }
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
      name: trimmedName
    })
    ElMessage.success('版本信息已更新')
    versionDialogVisible.value = false
    await loadVersions()
  } finally {
    versionSaving.value = false
  }
}

onMounted(() => {
  loadVersions()
})
</script>

<style scoped lang="scss">
.workflow-config-page {
  padding: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }

    .subtitle {
      margin: 4px 0 0;
      font-size: 14px;
      color: #909399;
    }
  }

  .mb-4 {
    margin-bottom: 16px;
  }

  .table-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
  }

  :deep(.el-alert__description) {
    p {
      margin: 4px 0;
      line-height: 1.6;
    }
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
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
}
</style>
