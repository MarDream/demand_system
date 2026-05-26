<template>
  <div class="config-container">
    <div class="config-header">
      <h2>模型配置</h2>
      <p class="config-desc">管理大模型接入组和模型实例，支持 OpenAI 和 Anthropic 协议</p>
    </div>

    <div class="tab-content">
      <div class="tab-header">
        <div class="tab-header-left">
          <el-button :icon="Setting" circle @click="showColumnConfig = true" title="列设置" />
        </div>
        <AppButton type="primary" permission="button:llm-provider:create" @click="openCreateProvider">
          <el-icon><Plus /></el-icon>
          新增接入组
        </AppButton>
      </div>

      <el-table :data="providers" border style="width: 100%" v-loading="loading" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="expand-header">
                <span class="expand-title">模型列表 ({{ row.models?.length ?? 0 }})</span>
                <el-button type="primary" size="small" @click="openCreateModel(row)">
                  <el-icon><Plus /></el-icon> 新增模型
                </el-button>
              </div>
              <el-table :data="row.models ?? []" size="small" style="width: 100%">
                <el-table-column prop="name" label="名称" min-width="120" />
                <el-table-column prop="modelId" label="模型ID" min-width="140" show-overflow-tooltip />
                <el-table-column prop="modelType" label="类型" width="100" align="center">
                  <template #default="{ row: model }">
                    <el-tag :type="typeTagType(model.modelType)" size="small">{{ model.modelType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="temperature" label="温度" width="60" align="center" />
                <el-table-column prop="maxTokens" label="Max Tokens" width="90" align="center" />
                <el-table-column label="默认" width="55" align="center">
                  <template #default="{ row: model }">
                    <el-tag v-if="model.isDefault" type="success" size="small">是</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="60" align="center">
                  <template #default="{ row: model }">
                    <el-switch :model-value="model.enabled" size="small" @change="handleToggleModel(row, model)" />
                  </template>
                </el-table-column>
                <el-table-column label="连通性" width="70" align="center">
                  <template #default="{ row: model }">
                    <div class="conn-status">
                      <template v-if="testingModels[model.id!]">
                        <el-icon class="is-loading"><Loading /></el-icon>
                      </template>
                      <template v-else-if="model.testSuccess != null">
                        <el-tooltip :content="connTooltip(model)" placement="top">
                          <span class="conn-light" :class="connLightClass(model)"></span>
                        </el-tooltip>
                      </template>
                      <span v-else class="conn-pending">-</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90" align="center">
                  <template #default="{ row: model }">
                    <el-tooltip content="测试" placement="top">
                      <el-icon class="action-icon" @click="handleTestModel(model)"><Connection /></el-icon>
                    </el-tooltip>
                    <el-tooltip content="编辑" placement="top">
                      <span v-permission="'button:llm-provider:update'" class="action-icon primary" @click="openEditModel(model)"><EditPen /></span>
                    </el-tooltip>
                    <el-tooltip content="删除" placement="top">
                      <span v-permission="'button:llm-provider:delete'" class="action-icon danger" @click="handleDeleteModel(model)"><Delete /></span>
                    </el-tooltip>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <template v-for="col in visibleColumns" :key="col.key">
          <el-table-column
            v-if="col.key === 'name'"
            prop="name" label="名称" min-width="140"
          />
          <el-table-column
            v-else-if="col.key === 'protocol'"
            prop="protocol" label="协议" width="100" align="center"
          >
            <template #default="{ row }">
              <el-tag :type="row.protocol === 'openai' ? 'primary' : 'warning'" size="small">
                {{ row.protocol === 'openai' ? 'OpenAI' : 'Anthropic' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            v-else-if="col.key === 'baseUrl'"
            prop="baseUrl" label="API Base URL" min-width="240" show-overflow-tooltip
          />
          <el-table-column
            v-else-if="col.key === 'apiKey'"
            prop="maskedApiKey" label="API Key" width="160"
          />
          <el-table-column
            v-else-if="col.key === 'modelCount'"
            label="模型数" width="80" align="center"
          >
            <template #default="{ row }">{{ row.models?.length ?? 0 }}</template>
          </el-table-column>
          <el-table-column
            v-else-if="col.key === 'enabled'"
            label="状态" width="70" align="center"
          >
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" size="small" @change="handleToggleProvider(row)" />
            </template>
          </el-table-column>
          <el-table-column
            v-else-if="col.key === 'operations'"
            label="操作" width="140" fixed="right" align="center"
          >
            <template #default="{ row }">
              <el-tooltip content="嗅探模型" placement="top">
                <el-icon class="action-icon" style="color: #E6A23C;" @click="handleSniff(row)"><Search /></el-icon>
              </el-tooltip>
              <el-tooltip content="查看密钥" placement="top">
                <el-icon class="action-icon" @click="handleViewApiKey(row)"><View /></el-icon>
              </el-tooltip>
              <el-tooltip content="编辑" placement="top">
                <span v-permission="'button:llm-provider:update'" class="action-icon primary" @click="openEditProvider(row)"><EditPen /></span>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <span v-permission="'button:llm-provider:delete'" class="action-icon danger" @click="handleDeleteProvider(row)"><Delete /></span>
              </el-tooltip>
            </template>
          </el-table-column>
        </template>
      </el-table>
    </div>

    <!-- 列设置对话框 -->
    <el-dialog v-model="showColumnConfig" title="列设置" width="360px">
      <el-checkbox v-model="columnCheckAll" :indeterminate="columnIndeterminate" @change="handleCheckAll">全选</el-checkbox>
      <el-divider style="margin: 8px 0" />
      <el-checkbox-group v-model="selectedColumnKeys">
        <div v-for="col in allColumns.filter(c => c.key !== 'operations')" :key="col.key" style="margin-bottom: 4px">
          <el-checkbox :label="col.key">{{ col.label }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="showColumnConfig = false">取消</el-button>
        <el-button type="primary" @click="saveColumns">保存</el-button>
      </template>
    </el-dialog>

    <!-- Provider 对话框 -->
    <el-dialog
      v-model="providerDialogVisible"
      :title="editingProviderId ? '编辑接入组' : '新增接入组'"
      width="620px"
      class="provider-dialog"
    >
      <el-form
        ref="providerFormRef"
        :model="providerForm"
        :rules="providerRules"
        label-width="126px"
        class="provider-form provider-dialog-form"
      >
        <section class="form-section-card">
          <div class="form-section-title">接入信息</div>
          <el-form-item label="名称" prop="name">
            <el-input v-model="providerForm.name" placeholder="如 OpenAI 官方、智谱 GLM" />
          </el-form-item>
          <div class="form-row form-row--provider">
            <el-form-item label="协议类型" prop="protocol" class="form-row-item">
              <el-select v-model="providerForm.protocol" style="width: 100%">
                <el-option label="OpenAI" value="openai" />
                <el-option label="Anthropic" value="anthropic" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用" class="form-row-item form-switch-item" label-width="60px">
              <el-switch v-model="providerForm.enabled" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section-card">
          <div class="form-section-title">接入配置</div>
          <el-form-item label="API Base URL" prop="baseUrl">
            <el-input v-model="providerForm.baseUrl" placeholder="https://api.openai.com" />
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model="providerForm.apiKey"
              :type="apiKeyVisible ? 'text' : 'password'"
              :placeholder="editingProviderId ? '不修改请留空' : '请输入 API Key'"
            >
              <template #suffix>
                <el-icon class="apiKey-eye" @click="toggleApiKeyVisible" style="cursor: pointer">
                  <View v-if="!apiKeyVisible" />
                  <Hide v-else />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="providerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleProviderSubmit">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Model 对话框 -->
    <el-dialog v-model="modelDialogVisible" :title="editingModelId ? '编辑模型' : '新增模型'" width="560px">
      <el-form :model="modelForm" :rules="modelRules" ref="modelFormRef" label-width="110px" class="provider-form">
        <div class="form-section-title">基础信息</div>
        <div class="form-row">
          <el-form-item label="名称" prop="name" class="form-row-item">
            <el-input v-model="modelForm.name" placeholder="如 GPT-4o" />
          </el-form-item>
          <el-form-item label="模型ID" prop="modelId" class="form-row-item">
            <el-input v-model="modelForm.modelId" placeholder="如 gpt-4o" />
          </el-form-item>
        </div>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="modelForm.modelType" allow-create filterable default-first-option style="width: 100%">
            <el-option v-for="r in presetTypes" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <div class="form-section-title">模型参数</div>
        <div class="form-row">
          <el-form-item label="温度" class="form-row-item">
            <el-slider v-model="modelForm.temperature" :min="0" :max="1" :step="0.01" show-input size="small" />
          </el-form-item>
          <el-form-item label="最大 Tokens" class="form-row-item">
            <el-input-number v-model="modelForm.maxTokens" :min="1" :max="128000" :step="512" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-section-title">其他设置</div>
        <div class="form-row">
          <el-form-item label="角色默认" class="form-row-item">
            <el-switch v-model="modelForm.isDefault" />
          </el-form-item>
          <el-form-item label="启用" class="form-row-item" label-width="60px">
            <el-switch v-model="modelForm.enabled" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleModelSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 嗅探模型对话框 -->
    <el-dialog v-model="sniffDialogVisible" title="嗅探模型" width="560px">
      <div v-loading="sniffing" style="min-height: 100px;">
        <el-alert
          v-if="!sniffing && sniffedModels.length > 0"
          type="info"
          :closable="false"
          style="margin-bottom: 12px;"
        >
          发现 {{ sniffedModels.length }} 个可用模型，已自动选择未导入的模型。已导入的模型将显示为灰色。
        </el-alert>
        <el-empty v-if="!sniffing && sniffedModels.length === 0" description="未发现可用模型" />
        <el-checkbox-group v-model="sniffSelectedModelIds">
          <div v-for="model in sniffedModels" :key="model.modelId" style="margin-bottom: 6px;">
            <el-checkbox :label="model.modelId" :disabled="model.alreadyExists">
              <span :style="{ color: model.alreadyExists ? '#c0c4cc' : '' }">
                {{ model.modelId }}
                <el-tag v-if="model.alreadyExists" size="small" type="info" style="margin-left: 6px;">已导入</el-tag>
                <el-tag v-if="model.ownedBy" size="small" style="margin-left: 6px;">{{ model.ownedBy }}</el-tag>
              </span>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="sniffDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="sniffSelectedModelIds.length === 0" @click="handleSniffImport">
          导入选中 ({{ sniffSelectedModelIds.length }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Loading, View, Hide, Setting, EditPen, Delete, Connection, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  llmProviderApi,
  getColumnConfig,
  saveColumnConfig,
  type LlmProvider,
  type LlmProviderForm,
  type LlmModel,
  type LlmModelForm,
  type SniffedModel,
} from '@/api/modules/llmProvider'
import AppButton from '@/components/common/AppButton.vue'

// ==================== State ====================

const loading = ref(false)
const submitting = ref(false)
const providers = ref<LlmProvider[]>([])

// Column config
const showColumnConfig = ref(false)
const allColumns = [
  { key: 'name', label: '名称' },
  { key: 'protocol', label: '协议' },
  { key: 'baseUrl', label: 'API Base URL' },
  { key: 'apiKey', label: 'API Key' },
  { key: 'modelCount', label: '模型数' },
  { key: 'enabled', label: '状态' },
  { key: 'operations', label: '操作' },
]
const defaultColumnKeys = ['name', 'protocol', 'baseUrl', 'modelCount', 'enabled', 'operations']
const selectedColumnKeys = ref<string[]>([...defaultColumnKeys])

const columnCheckAll = computed({
  get: () => selectedColumnKeys.value.length >= allColumns.filter(c => c.key !== 'operations').length,
  set: () => {},
})
const columnIndeterminate = computed(() => {
  const ops = allColumns.filter(c => c.key !== 'operations')
  return selectedColumnKeys.value.length > 0 && selectedColumnKeys.value.length < ops.length
})
function handleCheckAll(val: boolean) {
  const ops = allColumns.filter(c => c.key !== 'operations').map(c => c.key)
  selectedColumnKeys.value = val ? [...ops, 'operations'] : ['operations']
}

const visibleColumns = computed(() => {
  const cols = allColumns.filter(c => selectedColumnKeys.value.includes(c.key))
  if (!cols.find(c => c.key === 'operations')) {
    cols.push(allColumns.find(c => c.key === 'operations')!)
  }
  return cols
})

async function loadColumnConfig() {
  try {
    const res = await getColumnConfig('llm_provider_list') as any
    if (res && Array.isArray(res)) {
      selectedColumnKeys.value = [...res, 'operations']
    }
  } catch { /* use defaults */ }
}

async function saveColumns() {
  try {
    const keys = selectedColumnKeys.value.filter(k => k !== 'operations')
    await saveColumnConfig('llm_provider_list', keys)
    ElMessage.success('列配置已保存')
    showColumnConfig.value = false
  } catch {
    ElMessage.error('保存列配置失败')
  }
}

// Provider dialog
const providerDialogVisible = ref(false)
const editingProviderId = ref<number | null>(null)
const providerFormRef = ref<FormInstance>()
const apiKeyVisible = ref(false)
const providerForm = reactive<LlmProviderForm>({
  name: '',
  protocol: 'openai',
  baseUrl: '',
  apiKey: '',
  enabled: true,
})
const providerRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  protocol: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 API Base URL', trigger: 'blur' }],
})

// Model dialog
const modelDialogVisible = ref(false)
const editingModelId = ref<number | null>(null)
const currentProviderId = ref<number | null>(null)
const modelFormRef = ref<FormInstance>()
const presetTypes = ref<string[]>(['primary', 'haiku', 'sonnet', 'opus', 'embedding', 'rerank', 'general'])
const modelForm = reactive<LlmModelForm>({
  name: '',
  modelId: '',
  modelType: 'general',
  temperature: 0.3,
  maxTokens: 2048,
  isDefault: false,
  enabled: true,
})
const modelRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入模型ID', trigger: 'blur' }],
})

// Testing state
const testingModels = reactive<Record<number, boolean>>({})

// Sniff state
const sniffDialogVisible = ref(false)
const sniffing = ref(false)
const sniffedModels = ref<SniffedModel[]>([])
const sniffSelectedModelIds = ref<string[]>([])
const sniffProviderId = ref<number | null>(null)

// ==================== Lifecycle ====================

onMounted(() => {
  loadProviders()
  loadColumnConfig()
  loadRoles()
})

// ==================== Data ====================

async function loadProviders() {
  loading.value = true
  try {
    const res = await llmProviderApi.list() as any
    providers.value = res?.data ?? res ?? []
  } catch {
    providers.value = []
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await llmProviderApi.getRoles() as any
    const data = res?.data ?? res
    if (Array.isArray(data)) presetTypes.value = data
  } catch { /* use defaults */ }
}

// ==================== Provider CRUD ====================

function resetProviderForm() {
  editingProviderId.value = null
  apiKeyVisible.value = false
  providerForm.name = ''
  providerForm.protocol = 'openai'
  providerForm.baseUrl = ''
  providerForm.apiKey = ''
  providerForm.enabled = true
  providerFormRef.value?.resetFields()
}

function openCreateProvider() {
  resetProviderForm()
  providerDialogVisible.value = true
}

function openEditProvider(row: LlmProvider) {
  editingProviderId.value = row.id!
  apiKeyVisible.value = false
  providerForm.name = row.name
  providerForm.protocol = row.protocol
  providerForm.baseUrl = row.baseUrl
  providerForm.apiKey = row.maskedApiKey
  providerForm.enabled = row.enabled
  providerDialogVisible.value = true
}

async function handleProviderSubmit() {
  const valid = await providerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!editingProviderId.value && !providerForm.apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }

  submitting.value = true
  try {
    if (editingProviderId.value) {
      await llmProviderApi.update(editingProviderId.value, providerForm)
      ElMessage.success('更新成功')
    } else {
      await llmProviderApi.create(providerForm)
      ElMessage.success('创建成功')
    }
    providerDialogVisible.value = false
    await loadProviders()
  } catch {
    ElMessage.error(editingProviderId.value ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

async function handleToggleProvider(row: LlmProvider) {
  try {
    await llmProviderApi.toggle(row.id!)
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await loadProviders()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleDeleteProvider(row: LlmProvider) {
  await ElMessageBox.confirm(`确认删除接入组"${row.name}"及其所有模型吗？`, '提示', { type: 'warning' })
  try {
    await llmProviderApi.delete(row.id!)
    ElMessage.success('删除成功')
    await loadProviders()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function toggleApiKeyVisible() {
  if (!apiKeyVisible.value && editingProviderId.value) {
    try {
      const res = await llmProviderApi.getApiKey(editingProviderId.value) as any
      const data = res?.data ?? res
      providerForm.apiKey = data.apiKey ?? ''
    } catch {
      ElMessage.error('获取 API Key 失败')
      return
    }
  }
  apiKeyVisible.value = !apiKeyVisible.value
}

async function handleViewApiKey(row: LlmProvider) {
  try {
    const res = await llmProviderApi.getApiKey(row.id!) as any
    const data = res?.data ?? res
    ElMessageBox.alert(data.apiKey ?? '', 'API Key', { confirmButtonText: '关闭' })
  } catch {
    ElMessage.error('获取失败')
  }
}

// ==================== Model CRUD ====================

function resetModelForm() {
  editingModelId.value = null
  modelForm.name = ''
  modelForm.modelId = ''
  modelForm.modelType = 'general'
  modelForm.temperature = 0.3
  modelForm.maxTokens = 2048
  modelForm.isDefault = false
  modelForm.enabled = true
  modelFormRef.value?.resetFields()
}

function openCreateModel(provider: LlmProvider) {
  currentProviderId.value = provider.id!
  resetModelForm()
  modelDialogVisible.value = true
}

function openEditModel(model: LlmModel) {
  currentProviderId.value = model.providerId
  editingModelId.value = model.id!
  modelForm.name = model.name
  modelForm.modelId = model.modelId
  modelForm.modelType = model.modelType
  modelForm.temperature = model.temperature
  modelForm.maxTokens = model.maxTokens
  modelForm.isDefault = model.isDefault
  modelForm.enabled = model.enabled
  modelDialogVisible.value = true
}

async function handleModelSubmit() {
  const valid = await modelFormRef.value?.validate().catch(() => false)
  if (!valid || !currentProviderId.value) return

  submitting.value = true
  try {
    if (editingModelId.value) {
      await llmProviderApi.updateModel(currentProviderId.value, editingModelId.value, modelForm)
      ElMessage.success('更新成功')
    } else {
      await llmProviderApi.addModel(currentProviderId.value, modelForm)
      ElMessage.success('创建成功')
    }
    modelDialogVisible.value = false
    await loadProviders()
  } catch {
    ElMessage.error(editingModelId.value ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

async function handleToggleModel(_provider: LlmProvider, model: LlmModel) {
  try {
    await llmProviderApi.toggleModel(model.providerId, model.id!)
    ElMessage.success(model.enabled ? '已停用' : '已启用')
    await loadProviders()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleDeleteModel(model: LlmModel) {
  await ElMessageBox.confirm(`确认删除模型"${model.name}"吗？`, '提示', { type: 'warning' })
  try {
    await llmProviderApi.deleteModel(model.providerId, model.id!)
    ElMessage.success('删除成功')
    await loadProviders()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ==================== Test ====================

function connLightClass(model: LlmModel): string {
  if (!model.testSuccess) return 'conn-red'
  if (model.testDuration != null && model.testDuration > 5000) return 'conn-yellow'
  return 'conn-green'
}

function connTooltip(model: LlmModel): string {
  if (!model.testSuccess) return model.testError || '连接失败'
  if (model.testDuration != null && model.testDuration > 5000) return `响应较慢 ${model.testDuration}ms`
  return `连通正常 ${model.testDuration ?? 0}ms`
}

async function handleTestModel(model: LlmModel) {
  testingModels[model.id!] = true
  try {
    const res = await llmProviderApi.testModel(model.providerId, model.id!, { userMessage: '你好' }) as any
    const result = res?.data ?? res
    if (result.success) {
      if (result.durationMs > 5000) {
        ElMessage.warning(`${model.name} 响应较慢 (${result.durationMs}ms)`)
      } else {
        ElMessage.success(`${model.name} 连通正常 (${result.durationMs}ms)`)
      }
    } else {
      ElMessage.error(`${model.name} 连接失败: ${result.errorMessage}`)
    }
    await loadProviders()
  } catch {
    ElMessage.error(`${model.name} 请求失败`)
  } finally {
    delete testingModels[model.id!]
  }
}

// ==================== Role ====================

function typeTagType(modelType: string): string {
  const map: Record<string, string> = {
    primary: 'primary',
    haiku: 'info',
    sonnet: 'success',
    opus: 'warning',
    embedding: '',
    rerank: '',
  }
  return map[modelType] ?? ''
}

// ==================== Sniff ====================

async function handleSniff(row: LlmProvider) {
  sniffProviderId.value = row.id!
  sniffing.value = true
  sniffDialogVisible.value = true
  sniffedModels.value = []
  sniffSelectedModelIds.value = []
  try {
    const res = await llmProviderApi.sniffModels(row.id!) as any
    const data = res?.data ?? res ?? []
    sniffedModels.value = data
    sniffSelectedModelIds.value = data.filter((m: SniffedModel) => !m.alreadyExists).map((m: SniffedModel) => m.modelId)
  } catch {
    ElMessage.error('嗅探模型失败，请检查接入配置')
    sniffDialogVisible.value = false
  } finally {
    sniffing.value = false
  }
}

async function handleSniffImport() {
  if (!sniffProviderId.value || sniffSelectedModelIds.value.length === 0) {
    ElMessage.warning('请选择要导入的模型')
    return
  }
  submitting.value = true
  let imported = 0
  try {
    for (const modelId of sniffSelectedModelIds.value) {
      const model = sniffedModels.value.find(m => m.modelId === modelId)
      if (model && !model.alreadyExists) {
        await llmProviderApi.addModel(sniffProviderId.value!, {
          name: modelId,
          modelId: model.modelId,
          modelType: 'general',
          temperature: 0.3,
          maxTokens: 2048,
          isDefault: false,
          enabled: true,
        })
        imported++
      }
    }
    ElMessage.success(`成功导入 ${imported} 个模型`)
    sniffDialogVisible.value = false
    await loadProviders()
  } catch {
    ElMessage.error('导入模型失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.config-container {
  padding: 20px;
}

.config-header {
  margin-bottom: 24px;
  h2 { margin: 0 0 8px; font-size: 22px; color: #303133; }
  .config-desc { margin: 0; color: #909399; font-size: 14px; }
}

.tab-content {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.tab-header-left {
  display: flex;
  gap: 8px;
}

// Expand
.expand-content {
  padding: 12px 20px 20px 50px;
}

.expand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.expand-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

// Conn status
.conn-status {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 20px;
}

.conn-pending { color: #c0c4cc; font-size: 14px; }

.conn-light {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.15);
}

.conn-green { background-color: #67C23A; box-shadow: 0 0 6px rgba(103, 194, 58, 0.5); }
.conn-yellow { background-color: #E6A23C; box-shadow: 0 0 6px rgba(230, 162, 60, 0.5); }
.conn-red { background-color: #F56C6C; box-shadow: 0 0 6px rgba(245, 108, 108, 0.5); }

// Action icons
.action-icon {
  font-size: 16px;
  cursor: pointer;
  margin: 0 4px;
  color: #606266;
  transition: color 0.2s;

  &:hover { color: var(--el-color-primary); }
  &.primary { color: var(--el-color-primary); }
  &.danger { color: #F56C6C; }
  &.danger:hover { color: #e04040; }
}

// Dialog form
.form-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 8px 0 16px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  line-height: 1;
}

.provider-form {
  .el-form-item { margin-bottom: 18px; }
}

.form-row {
  display: flex;
  gap: 16px;
  .form-row-item { flex: 1; min-width: 0; }
}

.form-section-card {
  margin-bottom: 18px;
  padding: 18px 18px 6px;
  border: 1px solid #e7edf5;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.form-row--provider {
  align-items: flex-start;
}

.form-switch-item {
  flex: 0 0 132px !important;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.provider-dialog-form {
  :deep(.el-form-item__label) {
    white-space: nowrap;
    color: #4b5563;
    font-weight: 500;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    min-height: 44px;
    border-radius: 12px;
    box-shadow: 0 0 0 1px #d8e2f0 inset;
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px var(--el-color-primary) inset;
  }
}

.provider-dialog {
  :deep(.el-dialog) {
    border-radius: 20px;
    overflow: hidden;
  }

  :deep(.el-dialog__header) {
    margin-right: 0;
    padding: 24px 24px 18px;
    border-bottom: 1px solid #eef2f7;
  }

  :deep(.el-dialog__title) {
    font-size: 28px;
    font-weight: 700;
    color: #1f2937;
    letter-spacing: 0.02em;
  }

  :deep(.el-dialog__body) {
    padding: 20px 24px 8px;
  }

  :deep(.el-dialog__footer) {
    padding: 8px 24px 24px;
  }
}
</style>
