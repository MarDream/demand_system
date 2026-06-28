<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="680px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 创建方式选择 -->
    <div class="create-mode-selector">
      <el-radio-group v-model="createMode" size="large">
        <el-radio-button value="blank">
          <el-icon><Document /></el-icon>
          <span>空白创建</span>
        </el-radio-button>
        <el-radio-button value="copy">
          <el-icon><CopyDocument /></el-icon>
          <span>从现有复制</span>
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 空白创建表单 -->
    <div v-if="createMode === 'blank'" class="blank-form">
      <el-form ref="blankFormRef" :model="blankForm" :rules="blankRules" label-width="100px">
        <el-form-item label="工作流名称" prop="name">
          <el-input v-model="blankForm.name" placeholder="请输入工作流名称" clearable />
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="blankForm.version" placeholder="例如: v1.0" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="blankForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入工作流描述"
          />
        </el-form-item>
      </el-form>
    </div>

    <!-- 复制模式 -->
    <div v-else-if="createMode === 'copy'" class="copy-mode">
      <!-- 搜索框 -->
      <el-input
        v-model="searchKeyword"
        placeholder="搜索工作流..."
        clearable
        prefix-icon="Search"
        class="search-input"
        @input="handleSearch"
      />

      <!-- 工作流列表标签页 -->
      <el-tabs v-model="activeTab" class="workflow-tabs">
        <el-tab-pane label="模板库" name="templates">
          <div v-loading="loading" class="template-list">
            <el-empty v-if="!loading && templateList.length === 0" description="暂无可用模板" />
            <div v-else class="template-grid">
              <div
                v-for="template in templateList"
                :key="template.id"
                :class="['template-card', { selected: selectedWorkflow?.id === template.id }]"
                @click="handleSelectTemplate(template)"
              >
                <div class="card-header">
                  <span class="card-title">{{ template.name }}</span>
                  <el-tag v-if="template.isTemplate" type="success" size="small">模板</el-tag>
                </div>
                <div class="card-meta">
                  <span class="version">{{ template.version }}</span>
                  <span class="copy-count">
                    <el-icon><CopyDocument /></el-icon>
                    {{ template.copyCount }}
                  </span>
                </div>
                <div class="card-info">
                  <span>{{ template.creatorName }}</span>
                  <span>{{ formatDate(template.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="我的工作流" name="my">
          <div v-loading="loading" class="template-list">
            <el-empty v-if="!loading && myWorkflowList.length === 0" description="暂无工作流" />
            <div v-else class="template-grid">
              <div
                v-for="workflow in myWorkflowList"
                :key="workflow.id"
                :class="['template-card', { selected: selectedWorkflow?.id === workflow.id }]"
                @click="handleSelectTemplate(workflow)"
              >
                <div class="card-header">
                  <span class="card-title">{{ workflow.name }}</span>
                  <el-tag v-if="workflow.activationStatus === 'active'" type="success" size="small">
                    已启用
                  </el-tag>
                </div>
                <div class="card-meta">
                  <span class="version">{{ workflow.version }}</span>
                  <span class="node-count">{{ workflow.nodeCount || 0 }} 个节点</span>
                </div>
                <div class="card-info">
                  <span>{{ workflow.projectName }}</span>
                  <span>{{ formatDate(workflow.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 已选择的工作流预览 -->
      <div v-if="selectedWorkflow" class="selected-preview">
        <div class="preview-header">
          <span class="preview-title">{{ selectedWorkflow.name }}</span>
          <el-tag v-if="selectedWorkflow.isTemplate" type="success" size="small">官方模板</el-tag>
        </div>
        <div class="preview-stats">
          <span>已被复制 {{ selectedWorkflow.copyCount }} 次</span>
          <span v-if="selectedWorkflow.nodeCount">{{ selectedWorkflow.nodeCount }} 个节点</span>
        </div>

        <!-- 复制配置表单 -->
        <el-form
          ref="copyFormRef"
          :model="copyOptions"
          :rules="copyRules"
          label-width="120px"
          class="copy-form"
        >
          <el-form-item label="新工作流名称" prop="newName">
            <el-input
              v-model="copyOptions.newName"
              placeholder="请输入新工作流名称"
              @blur="checkNameConflict"
            >
              <template #suffix>
                <el-icon v-if="nameStatus === 'checking'" class="is-loading">
                  <Loading />
                </el-icon>
                <el-icon v-else-if="nameStatus === 'conflict'" color="#F56C6C">
                  <Warning />
                </el-icon>
                <el-icon v-else-if="nameStatus === 'available'" color="#67C23A">
                  <CircleCheck />
                </el-icon>
              </template>
            </el-input>
            <div v-if="nameStatus === 'conflict' && suggestedName" class="name-hint">
              建议使用：<span class="suggested-name" @click="useSuggestedName">{{ suggestedName }}</span>
            </div>
          </el-form-item>

          <el-collapse v-model="activeAdvanced">
            <el-collapse-item title="高级选项" name="advanced">
              <div class="advanced-options">
                <el-checkbox v-model="copyOptions.resetApprovers">
                  清空审批人（需重新配置）
                </el-checkbox>
                <el-checkbox v-model="copyOptions.resetSensitiveData">
                  清空敏感数据（密钥、密码等）
                </el-checkbox>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-form>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="creating" @click="handleCreate">
        {{ createMode === 'copy' ? '复制并编辑' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, CopyDocument, Search, Loading, Warning, CircleCheck } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getWorkflowTemplates,
  copyWorkflow,
  checkWorkflowNameConflict,
  type WorkflowTemplateDTO,
  type WorkflowCopyRequest
} from '@/api/modules/workflow'
import { formatDate as formatDateUtil } from '@/utils/format'

interface Props {
  projectId?: number
}

const props = withDefaults(defineProps<Props>(), {
  projectId: 0
})

const emit = defineEmits<{
  success: [versionId: number]
}>()

const visible = ref(false)
const title = computed(() => (createMode.value === 'blank' ? '新建工作流' : '复制工作流'))
const createMode = ref<'blank' | 'copy'>('blank')
const activeTab = ref('templates')
const searchKeyword = ref('')
const loading = ref(false)
const creating = ref(false)

// 模板列表
const templateList = ref<WorkflowTemplateDTO[]>([])
const myWorkflowList = ref<WorkflowTemplateDTO[]>([])
const selectedWorkflow = ref<WorkflowTemplateDTO | null>(null)

// 空白创建表单
const blankFormRef = ref<FormInstance>()
const blankForm = reactive({
  name: '',
  version: 'v1.0',
  description: ''
})

const blankRules: FormRules = {
  name: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }]
}

// 复制选项表单
const copyFormRef = ref<FormInstance>()
const copyOptions = reactive<WorkflowCopyRequest>({
  newName: '',
  includeDescription: true,
  includeNodes: true,
  includeEdges: true,
  resetApprovers: false,
  resetFormFields: false,
  resetSensitiveData: true
})

const copyRules: FormRules = {
  newName: [{ required: true, message: '请输入新工作流名称', trigger: 'blur' }]
}

const activeAdvanced = ref<string[]>([])
const nameStatus = ref<'idle' | 'checking' | 'conflict' | 'available'>('idle')
const suggestedName = ref('')

// 打开对话框
const open = (mode: 'blank' | 'copy' = 'blank', sourceVersionId?: number) => {
  visible.value = true
  createMode.value = mode
  
  if (mode === 'copy') {
    loadTemplates()
    // 如果指定了源版本ID，自动选中该版本
    if (sourceVersionId) {
      nextTick(async () => {
        const version = [...templateList.value, ...myWorkflowList.value].find(v => v.id === sourceVersionId)
        if (version) {
          handleSelectTemplate(version)
        } else {
          // 如果在当前列表中没找到，切换到"我的工作流"标签页重新加载
          activeTab.value = 'my'
          await loadTemplates()
          const myVersion = myWorkflowList.value.find(v => v.id === sourceVersionId)
          if (myVersion) {
            handleSelectTemplate(myVersion)
          }
        }
      })
    }
  }
}

// 加载模板列表
const loadTemplates = async () => {
  loading.value = true
  try {
    const isMyWorkflows = activeTab.value === 'my'
    const res = await getWorkflowTemplates({
      page: 1,
      pageSize: 20,
      keyword: searchKeyword.value,
      includeMyWorkflows: isMyWorkflows
    })
    
    if (isMyWorkflows) {
      myWorkflowList.value = res.data.records || []
    } else {
      templateList.value = res.data.records || []
    }
  } catch (error) {
    console.error('加载模板列表失败:', error)
    ElMessage.error('加载模板列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  loadTemplates()
}

// 选择模板
const handleSelectTemplate = (template: WorkflowTemplateDTO) => {
  selectedWorkflow.value = template
  copyOptions.newName = `${template.name} - 副本`
  nameStatus.value = 'idle'
  suggestedName.value = ''
}

// 检查名称冲突
const checkNameConflict = async () => {
  if (!copyOptions.newName.trim()) {
    nameStatus.value = 'idle'
    return
  }

  nameStatus.value = 'checking'
  try {
    const res = await checkWorkflowNameConflict(copyOptions.newName, props.projectId)
    if (res.data.conflict) {
      nameStatus.value = 'conflict'
      suggestedName.value = res.data.suggestedName || ''
    } else {
      nameStatus.value = 'available'
      suggestedName.value = ''
    }
  } catch (error) {
    nameStatus.value = 'idle'
    console.error('检查名称冲突失败:', error)
  }
}

// 使用建议名称
const useSuggestedName = () => {
  copyOptions.newName = suggestedName.value
  nameStatus.value = 'available'
  suggestedName.value = ''
}

// 创建或复制
const handleCreate = async () => {
  if (createMode.value === 'blank') {
    // 空白创建逻辑（保持原有逻辑）
    await blankFormRef.value?.validate()
    // TODO: 调用空白创建API
    ElMessage.success('创建成功')
    handleClose()
  } else {
    // 复制逻辑
    if (!selectedWorkflow.value) {
      ElMessage.warning('请选择要复制的工作流')
      return
    }

    await copyFormRef.value?.validate()

    creating.value = true
    try {
      const res = await copyWorkflow(selectedWorkflow.value.id, {
        ...copyOptions,
        targetProjectId: props.projectId
      })
      
      ElMessage.success(res.data.message || '工作流复制成功')
      emit('success', res.data.workflowVersionId)
      handleClose()
    } catch (error: any) {
      console.error('复制工作流失败:', error)
      ElMessage.error(error.message || '复制工作流失败')
    } finally {
      creating.value = false
    }
  }
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
  selectedWorkflow.value = null
  nameStatus.value = 'idle'
  suggestedName.value = ''
  blankFormRef.value?.resetFields()
  copyFormRef.value?.resetFields()
}

// 格式化日期
const formatDate = (date: string) => {
  return formatDateUtil(date)
}

// 监听标签页切换
watch(activeTab, () => {
  loadTemplates()
})

// 监听创建模式切换
watch(createMode, () => {
  if (createMode.value === 'copy') {
    loadTemplates()
  }
})

defineExpose({
  open
})
</script>

<style scoped lang="scss">
.create-mode-selector {
  margin-bottom: 24px;
  text-align: center;

  :deep(.el-radio-button__inner) {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 24px;
  }
}

.blank-form {
  padding: 12px 0;
}

.copy-mode {
  .search-input {
    margin-bottom: 16px;
  }

  .workflow-tabs {
    margin-bottom: 16px;

    :deep(.el-tabs__content) {
      max-height: 360px;
      overflow-y: auto;
    }
  }

  .template-list {
    min-height: 200px;
  }

  .template-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;
  }

  .template-card {
    padding: 16px;
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      border-color: var(--el-color-primary);
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }

    &.selected {
      border-color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .card-title {
        font-weight: 500;
        font-size: 14px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        flex: 1;
      }
    }

    .card-meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
      font-size: 12px;
      color: var(--el-text-color-secondary);

      .version {
        color: var(--el-color-primary);
      }

      .copy-count,
      .node-count {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }

    .card-info {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 12px;
      color: var(--el-text-color-placeholder);
    }
  }
}

.selected-preview {
  margin-top: 20px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  .preview-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;

    .preview-title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .preview-stats {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .copy-form {
    margin-top: 16px;

    .name-hint {
      margin-top: 4px;
      font-size: 12px;
      color: var(--el-text-color-secondary);

      .suggested-name {
        color: var(--el-color-primary);
        cursor: pointer;

        &:hover {
          text-decoration: underline;
        }
      }
    }

    .advanced-options {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
  }
}
</style>
