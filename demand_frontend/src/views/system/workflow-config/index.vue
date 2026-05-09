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
          <p>1. 工作流配置是全局的，可以被多个项目复用</p>
          <p>2. 保存配置后需要提交审核，审核通过后才能启用</p>
          <p>3. 支持5种节点类型：开始节点、审批节点、抄送节点、条件节点、结束节点</p>
        </div>
      </el-alert>

      <!-- 版本列表 -->
      <el-table :data="versions" border v-loading="loading">
        <el-table-column prop="version" label="版本号" width="100" />
        <el-table-column prop="name" label="版本名称" min-width="200" />
        <el-table-column prop="projectId" label="项目ID" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.isActive === 1" type="success">已启用</el-tag>
            <el-tag v-else type="info">未启用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewWorkflow(row)">
              查看
            </el-button>
            <el-button link type="primary" @click="editWorkflow(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.isActive !== 1"
              link
              type="success"
              @click="activateVersion(row)"
            >
              启用
            </el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getVersionHistory } from '@/api/modules/workflow-visual'
import type { WorkflowVersionDTO } from '@/types/workflow-visual'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const versions = ref<WorkflowVersionDTO[]>([])

const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

// 加载版本列表
const loadVersions = async () => {
  loading.value = true
  try {
    // 暂时使用项目ID=1，后续可以改为全局配置
    const projectId = 1
    versions.value = await getVersionHistory(projectId) || []
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
  router.push('/system/workflow-config/editor')
}

// 查看工作流
const viewWorkflow = (row: WorkflowVersionDTO) => {
  router.push({
    path: '/system/workflow-config/editor',
    query: { versionId: row.id, mode: 'view' }
  })
}

// 编辑工作流
const editWorkflow = (row: WorkflowVersionDTO) => {
  router.push({
    path: '/system/workflow-config/editor',
    query: { versionId: row.id, mode: 'edit' }
  })
}

// 启用版本
const activateVersion = async (row: WorkflowVersionDTO) => {
  try {
    await ElMessageBox.confirm(
      `确定要启用版本 V${row.version} - ${row.name} 吗？启用后将替换当前生效的版本。`,
      '确认启用',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // TODO: 调用启用接口
    ElMessage.success('启用成功')
    loadVersions()
  } catch (error) {
    // 用户取消
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
}
</style>
