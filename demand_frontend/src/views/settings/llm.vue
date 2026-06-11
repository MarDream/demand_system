<template>
  <div class="config-container">
    <div class="config-header">
      <h2>模型配置</h2>
      <p class="config-desc">管理大模型接入组和模型实例，支持 OpenAI 和 Anthropic 协议</p>
    </div>

    <div class="config-layout">
      <!-- 左侧：接入组列表 -->
      <div class="provider-panel">
        <div class="panel-header">
          <span class="panel-title">接入组</span>
          <el-button :icon="Plus" size="small" type="primary" @click="openCreateProvider">新增</el-button>
        </div>
        <div class="provider-list" v-loading="loading">
          <div
            v-for="p in providers"
            :key="p.id"
            class="provider-item"
            :class="{ 'is-selected': selectedProviderId === p.id }"
            @click="selectedProviderId = p.id!"
          >
            <div class="provider-item-main">
              <div class="provider-item-left">
                <div class="provider-name">
                  <span>{{ p.name }}</span>
                  <el-tag :type="p.protocol === 'openai' ? 'primary' : 'warning'" size="small">
                    {{ p.protocol === 'openai' ? 'OpenAI' : 'Anthropic' }}
                  </el-tag>
                </div>
                <div class="provider-meta">{{ p.baseUrl }}</div>
              </div>
              <div class="provider-item-right">
                <el-switch
                  :model-value="p.enabled"
                  size="small"
                  @change="handleToggleProvider(p)"
                  @click.stop
                />
                <div class="provider-count">{{ p.models?.length ?? 0 }} 个模型</div>
              </div>
            </div>
            <div class="provider-item-actions" @click.stop>
              <el-tooltip content="嗅探模型" placement="top">
                <el-icon class="action-icon" style="color: #E6A23C;" @click="handleSniff(p)"><Search /></el-icon>
              </el-tooltip>
              <el-tooltip content="查看密钥" placement="top">
                <el-icon class="action-icon" @click="handleViewApiKey(p)"><View /></el-icon>
              </el-tooltip>
              <el-tooltip content="编辑" placement="top">
                <span v-permission="'button:llm-provider:update'">
                  <el-icon class="action-icon primary" @click="openEditProvider(p)"><EditPen /></el-icon>
                </span>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <span v-permission="'button:llm-provider:delete'">
                  <el-icon class="action-icon danger" @click="handleDeleteProvider(p)"><Delete /></el-icon>
                </span>
              </el-tooltip>
            </div>
          </div>
          <el-empty v-if="!loading && providers.length === 0" description="暂无接入组" />
        </div>
      </div>

      <!-- 右侧：模型管理 -->
      <div class="model-panel">
        <div v-if="!selectedProviderId" class="model-empty">
          <el-empty description="请选择左侧接入组" />
        </div>
        <template v-else-if="selectedProvider">
          <div class="panel-header">
            <div class="panel-header-left">
              <span class="panel-title">
                {{ selectedProvider.name }} 的模型
                <el-tag :type="selectedProvider.protocol === 'openai' ? 'primary' : 'warning'" size="small" style="margin-left: 8px;">
                  {{ selectedProvider.protocol === 'openai' ? 'OpenAI' : 'Anthropic' }}
                </el-tag>
              </span>
              <el-button :icon="Plus" size="small" type="primary" @click="openCreateModel(selectedProvider)">新增模型</el-button>
            </div>
            <div class="panel-header-right">
              <el-tooltip content="嗅探可用模型" placement="top">
                <el-button size="small" :icon="Search" @click="handleSniff(selectedProvider)">嗅探</el-button>
              </el-tooltip>
            </div>
          </div>

          <el-table :data="selectedProviderModels" border style="width: 100%" size="small" row-key="id">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="modelId" label="模型ID" min-width="140" show-overflow-tooltip />
            <el-table-column prop="modelType" label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="typeTagType(row.modelType)" size="small">{{ row.modelType || 'general' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="temperature" label="温度" width="60" align="center" />
            <el-table-column prop="maxTokens" label="Max Tokens" width="90" align="center" />
            <el-table-column label="默认" width="55" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="60" align="center">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled" size="small" @change="handleToggleModel(selectedProvider, row)" />
              </template>
            </el-table-column>
            <el-table-column label="连通性" width="90" align="center">
              <template #default="{ row }">
                <div class="conn-status">
                  <template v-if="testingModels[row.id!]">
                    <span class="testing-text"><el-icon class="is-loading"><Loading /></el-icon> 测试中</span>
                  </template>
                  <template v-else-if="row.testSuccess != null">
                    <el-tooltip :content="connTooltip(row)" placement="top">
                      <span class="conn-light" :class="connLightClass(row)"></span>
                    </el-tooltip>
                  </template>
                  <span v-else class="conn-pending">-</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <el-tooltip content="测试连通性" placement="top">
                  <el-icon class="action-icon" @click="handleTestModel(row)"><Connection /></el-icon>
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <span v-permission="'button:llm-provider:update'">
                    <el-icon class="action-icon primary" @click="openEditModel(row)"><EditPen /></el-icon>
                  </span>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <span v-permission="'button:llm-provider:delete'">
                    <el-icon class="action-icon danger" @click="handleDeleteModel(row)"><Delete /></el-icon>
                  </span>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="selectedProviderModels.length === 0" description="暂无模型，请新增或嗅探" />
        </template>
      </div>
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
const selectedProviderId = ref<number | null>(null)

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

// Computed
const selectedProvider = computed(() =>
  providers.value.find(p => p.id === selectedProviderId.value) ?? null
)
const selectedProviderModels = computed(() =>
  selectedProvider.value?.models ?? []
)

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
    // Auto-select first provider
    if (providers.value.length > 0 && selectedProviderId.value === null) {
      selectedProviderId.value = providers.value[0].id!
    }
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
    if (selectedProviderId.value === row.id) {
      selectedProviderId.value = providers.value.find(p => p.id !== row.id)?.id ?? null
    }
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
    general: '',
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
  } catch (error) {
    ElMessage.error(formatSniffError(error))
    sniffDialogVisible.value = false
  } finally {
    sniffing.value = false
  }
}

function formatSniffError(error: unknown): string {
  const message = getRequestErrorMessage(error)
  return message ? `嗅探模型失败：${message}` : '嗅探模型失败，请检查接入配置'
}

function getRequestErrorMessage(error: unknown): string {
  const requestError = error as {
    message?: string
    response?: {
      data?: string | { message?: string }
    }
  }
  const data = requestError.response?.data
  if (typeof data === 'string' && data.trim()) {
    return data
  }
  if (typeof data === 'object' && data?.message?.trim()) {
    return data.message
  }
  if (requestError.message?.trim()) {
    return requestError.message
  }
  return ''
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
  height: 100%;
  display: flex;
  flex-direction: column;
}

.config-header {
  margin-bottom: 20px;
  flex-shrink: 0;
  h2 { margin: 0 0 8px; font-size: 22px; color: #303133; }
  .config-desc { margin: 0; color: #909399; font-size: 14px; }
}

// ==================== 两栏布局 ====================
.config-layout {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

// ==================== 左侧接入组面板 ====================
.provider-panel {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f2f5;
  flex-shrink: 0;
}

.panel-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.provider-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.provider-item {
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 6px;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition: all 0.18s ease;
  background: #fafafa;

  &:hover {
    background: #f0f7ff;
    border-color: #d0e3ff;
  }

  &.is-selected {
    background: #eff6ff;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
}

.provider-item-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.provider-item-left {
  flex: 1;
  min-width: 0;
}

.provider-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 4px;
}

.provider-meta {
  font-size: 12px;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.provider-item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.provider-count {
  font-size: 11px;
  color: #9ca3af;
}

.provider-item-actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}

// ==================== 右侧模型面板 ====================
.model-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.model-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.model-panel .el-table {
  flex: 1;
  border-radius: 0;
}

// ==================== 操作图标 ====================
.action-icon {
  font-size: 15px;
  cursor: pointer;
  color: #6b7280;
  padding: 2px;
  border-radius: 4px;
  transition: color 0.15s, background 0.15s;

  &:hover {
    color: var(--el-color-primary);
    background: rgba(59, 130, 246, 0.08);
  }
  &.primary { color: var(--el-color-primary); }
  &.primary:hover { color: var(--el-color-primary); }
  &.danger { color: #ef4444; }
  &.danger:hover { color: #dc2626; background: rgba(239, 68, 68, 0.08); }
}

// ==================== 连通性状态 ====================
.conn-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 20px;
}

.testing-text {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #6b7280;
}

.conn-pending { color: #d1d5db; font-size: 13px; }

.conn-light {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.12);
}

.conn-green { background-color: #22c55e; }
.conn-yellow { background-color: #f59e0b; }
.conn-red { background-color: #ef4444; }

// ==================== 对话框 ====================
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
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
    letter-spacing: 0.01em;
  }

  :deep(.el-dialog__body) {
    padding: 20px 24px 8px;
  }

  :deep(.el-dialog__footer) {
    padding: 8px 24px 24px;
  }
}
</style>
