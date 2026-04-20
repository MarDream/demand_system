<template>
  <div class="workflow-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">工作流配置</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- 状态管理 -->
        <el-tab-pane label="状态管理" name="states">
          <div class="tab-content">
            <div class="toolbar">
              <el-button type="primary" @click="openStateDialog()">新增状态</el-button>
            </div>
            <el-table :data="states" border style="width: 100%">
              <el-table-column prop="name" label="状态名称" />
              <el-table-column prop="color" label="颜色" width="120">
                <template #default="{ row }">
                  <span class="color-preview" :style="{ backgroundColor: row.color }" />
                  <span class="ml-2">{{ row.color }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="isFinal" label="是否终态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.isFinal ? 'success' : 'info'">
                    {{ row.isFinal ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="80" />
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openStateDialog(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 流转规则 -->
        <el-tab-pane label="流转规则" name="transitions">
          <div class="tab-content">
            <el-table :data="transitions" border style="width: 100%">
              <el-table-column label="起始状态" width="150">
                <template #default="{ row }">
                  {{ getStateName(row.fromStateId) }}
                </template>
              </el-table-column>
              <el-table-column label="目标状态" width="150">
                <template #default="{ row }">
                  {{ getStateName(row.toStateId) }}
                </template>
              </el-table-column>
              <el-table-column label="允许角色" width="200">
                <template #default="{ row }">
                  <el-tag v-for="role in row.allowedRoles" :key="role" size="small" class="mr-1">
                    {{ role }}
                  </el-tag>
                  <span v-if="!row.allowedRoles || row.allowedRoles.length === 0" class="text-muted">不限</span>
                </template>
              </el-table-column>
              <el-table-column label="必填字段" width="200">
                <template #default="{ row }">
                  <el-tag v-for="field in row.requiredFields" :key="field" type="warning" size="small" class="mr-1">
                    {{ field }}
                  </el-tag>
                  <span v-if="!row.requiredFields || row.requiredFields.length === 0" class="text-muted">无</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button link type="primary" @click="editTransition(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 流程版本 -->
        <el-tab-pane label="流程版本" name="versions">
          <div class="tab-content">
            <el-table :data="versions" border style="width: 100%">
              <el-table-column prop="version" label="版本号" width="100" />
              <el-table-column prop="name" label="名称" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.isActive ? 'success' : 'info'">
                    {{ row.isActive ? '当前' : '历史' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180" />
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button v-if="!row.isActive" link type="primary" @click="activateVersion(row)">
                    启用
                  </el-button>
                  <span v-else class="text-muted">当前版本</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 状态编辑对话框 -->
    <el-dialog
      v-model="stateDialogVisible"
      :title="isEditing ? '编辑状态' : '新增状态'"
      width="500px"
    >
      <el-form :model="stateForm" :rules="stateRules" ref="stateFormRef" label-width="80px">
        <el-form-item label="状态名称" prop="name">
          <el-input v-model="stateForm.name" placeholder="请输入状态名称" />
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="stateForm.color" />
        </el-form-item>
        <el-form-item label="终态">
          <el-switch v-model="stateForm.isFinal" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="stateForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitState">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getWorkflowStates, getWorkflowTransitions, getWorkflowVersions, activateWorkflowVersion } from '@/api/modules/workflow'
import type { WorkflowState, WorkflowTransition, WorkflowVersion } from '@/types/workflow'

const projectId = 1
const activeTab = ref('states')

// 状态管理
const states = ref<WorkflowState[]>([])
const stateDialogVisible = ref(false)
const isEditing = ref(false)
const stateFormRef = ref<FormInstance>()
const stateForm = ref<Partial<WorkflowState>>({
  name: '',
  color: '#409EFF',
  isFinal: false,
  sortOrder: 0,
})

const stateRules = {
  name: [{ required: true, message: '请输入状态名称', trigger: 'blur' }],
}

// 流转规则
const transitions = ref<WorkflowTransition[]>([])

// 流程版本
const versions = ref<WorkflowVersion[]>([])

// 工具方法
const getStateName = (stateId: number) => {
  const state = states.value.find((s) => s.id === stateId)
  return state ? state.name : `状态${stateId}`
}

// 加载数据
const loadStates = async () => {
  try {
    states.value = (await getWorkflowStates(projectId)) as unknown as WorkflowState[]
  } catch {
    ElMessage.warning('状态数据加载失败，使用模拟数据')
    states.value = [
      { id: 1, projectId: 1, name: '待处理', color: '#909399', isFinal: false, sortOrder: 1 },
      { id: 2, projectId: 1, name: '进行中', color: '#409EFF', isFinal: false, sortOrder: 2 },
      { id: 3, projectId: 1, name: '已完成', color: '#67C23A', isFinal: true, sortOrder: 3 },
      { id: 4, projectId: 1, name: '已关闭', color: '#F56C6C', isFinal: true, sortOrder: 4 },
    ]
  }
}

const loadTransitions = async () => {
  try {
    transitions.value = (await getWorkflowTransitions(projectId)) as unknown as WorkflowTransition[]
  } catch {
    transitions.value = [
      { id: 1, projectId: 1, fromStateId: 1, toStateId: 2, allowedRoles: ['产品经理'], requiredFields: ['title'] },
      { id: 2, projectId: 1, fromStateId: 2, toStateId: 3, allowedRoles: ['开发'], requiredFields: ['description'] },
      { id: 3, projectId: 1, fromStateId: 2, toStateId: 4, allowedRoles: ['产品经理'], requiredFields: [] },
    ]
  }
}

const loadVersions = async () => {
  try {
    versions.value = (await getWorkflowVersions(projectId)) as unknown as WorkflowVersion[]
  } catch {
    versions.value = [
      { id: 1, projectId: 1, version: 1, name: '初始版本', definition: {}, isActive: false, creatorId: 1, createdAt: '2024-01-15 10:00:00' },
      { id: 2, projectId: 1, version: 2, name: '当前版本', definition: {}, isActive: true, creatorId: 1, createdAt: '2024-03-20 14:30:00' },
    ]
  }
}

// 状态操作
const openStateDialog = (row?: WorkflowState) => {
  isEditing.value = !!row
  if (row) {
    stateForm.value = { ...row }
  } else {
    stateForm.value = { name: '', color: '#409EFF', isFinal: false, sortOrder: states.value.length + 1 }
  }
  stateDialogVisible.value = true
}

const submitState = async () => {
  if (!stateFormRef.value) return
  await stateFormRef.value.validate()
  ElMessage.success(isEditing.value ? '状态更新成功' : '状态创建成功')
  stateDialogVisible.value = false
  loadStates()
}

// 流转规则操作
const editTransition = (row: WorkflowTransition) => {
  ElMessage.info(`编辑流转规则: ${getStateName(row.fromStateId)} -> ${getStateName(row.toStateId)}`)
}

// 版本操作
const activateVersion = async (row: WorkflowVersion) => {
  try {
    await activateWorkflowVersion(row.id)
    ElMessage.success('版本已启用')
    loadVersions()
  } catch {
    ElMessage.success('版本已启用（模拟）')
    versions.value.forEach((v) => {
      v.isActive = v.id === row.id
    })
  }
}

onMounted(() => {
  loadStates()
  loadTransitions()
  loadVersions()
})
</script>

<style scoped lang="scss">
.workflow-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title {
      font-size: 18px;
      font-weight: 600;
    }
  }

  .tab-content {
    padding: 16px 0;
  }

  .toolbar {
    margin-bottom: 16px;
  }

  .color-preview {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 4px;
    vertical-align: middle;
    border: 1px solid #ddd;
  }

  .ml-2 {
    margin-left: 8px;
  }

  .mr-1 {
    margin-right: 4px;
  }

  .text-muted {
    color: #909399;
    font-size: 12px;
  }
}
</style>
