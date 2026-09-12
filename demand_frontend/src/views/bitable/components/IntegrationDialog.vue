<template>
  <el-dialog :model-value="visible" title="API 与 Webhook 集成" width="720px" @close="handleClose">
    <el-tabs v-model="activeTab">
      <!-- API Keys -->
      <el-tab-pane label="API Keys" name="keys">
        <div class="integration-toolbar">
          <el-button size="small" type="primary" @click="showCreateKey = !showCreateKey">
            <el-icon><Plus /></el-icon> 创建 API Key
          </el-button>
        </div>
        <div v-if="showCreateKey" class="integration-create">
          <el-input v-model="keyForm.name" size="small" placeholder="凭证名称" style="width: 200px" />
          <el-select v-model="keyForm.scopes" multiple size="small" placeholder="授权范围" style="width: 240px">
            <el-option label="读取记录 (records:read)" value="records:read" />
            <el-option label="读取字段 (fields:read)" value="fields:read" />
          </el-select>
          <el-button size="small" type="primary" :loading="creating" @click="handleCreateKey">创建</el-button>
        </div>
        <el-alert v-if="createdSecret" type="success" :closable="false" class="integration-secret">
          <template #title>
            请立即保存 Secret（仅显示一次）：{{ createdSecret.keyId }}.{{ createdSecret.secret }}
            <el-button link size="small" @click="copyText(createdSecret.keyId + '.' + createdSecret.secret)">复制</el-button>
          </template>
        </el-alert>
        <el-table :data="apiKeys" size="small" border>
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="keyId" label="Key ID" min-width="160" />
          <el-table-column label="范围" min-width="140">
            <template #default="{ row }">{{ (row.scopes || []).join(', ') }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link size="small" @click="copyText(row.keyId)">复制 Key ID</el-button>
              <el-button link size="small" type="danger" @click="revokeKey(row.id)">吊销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Webhooks -->
      <el-tab-pane label="Webhooks" name="webhooks">
        <div class="integration-toolbar">
          <el-button size="small" type="primary" @click="showCreateHook = !showCreateHook">
            <el-icon><Plus /></el-icon> 创建 Webhook
          </el-button>
        </div>
        <div v-if="showCreateHook" class="integration-create">
          <el-input v-model="hookForm.name" size="small" placeholder="名称" style="width: 130px" />
          <el-input v-model="hookForm.url" size="small" placeholder="https://example.com/webhook" style="width: 260px" />
          <el-select v-model="hookForm.eventTypes" multiple size="small" placeholder="事件" style="width: 220px">
            <el-option label="记录创建" value="record_created" />
            <el-option label="记录更新" value="record_updated" />
            <el-option label="记录删除" value="record_deleted" />
            <el-option label="表单提交" value="form_submitted" />
          </el-select>
          <el-button size="small" type="primary" :loading="creating" @click="handleCreateHook">创建</el-button>
        </div>
        <el-alert v-if="createdHookSecret" type="success" :closable="false" class="integration-secret">
          <template #title>
            签名密钥（仅显示一次，用于校验 X-Webhook-Signature）：{{ createdHookSecret }}
            <el-button link size="small" @click="copyText(createdHookSecret)">复制</el-button>
          </template>
        </el-alert>
        <el-table :data="webhooks" size="small" border>
          <el-table-column prop="name" label="名称" min-width="110" />
          <el-table-column prop="url" label="目标 URL" min-width="200" show-overflow-tooltip />
          <el-table-column label="事件" min-width="150">
            <template #default="{ row }">{{ (row.eventTypes || []).join(', ') }}</template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'enabled' ? 'success' : 'info'">
                {{ row.status === 'enabled' ? '启用' : '停用' }}
              </el-tag>
              <el-tag v-if="row.lastStatus" size="small" :type="row.lastStatus === 'succeeded' ? 'success' : 'danger'">
                {{ row.lastStatus === 'succeeded' ? '投递成功' : '投递失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link size="small" @click="toggleHook(row)">{{ row.status === 'enabled' ? '停用' : '启用' }}</el-button>
              <el-button link size="small" type="danger" @click="deleteHook(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listApiKeys, createApiKey, revokeApiKey,
  listWebhooks, createWebhook, updateWebhookStatus, deleteWebhook,
  type ApiKeyInfo, type WebhookSubscriptionInfo,
} from '@/api/modules/bitable'

const props = defineProps<{
  visible: boolean
  baseId: number | null
}>()

const emit = defineEmits<{ close: [] }>()

const activeTab = ref('keys')
const creating = ref(false)
const apiKeys = ref<ApiKeyInfo[]>([])
const webhooks = ref<WebhookSubscriptionInfo[]>([])
const createdSecret = ref<{ keyId: string; secret: string } | null>(null)
const createdHookSecret = ref('')
const showCreateKey = ref(false)
const showCreateHook = ref(false)

const keyForm = reactive({ name: '', scopes: ['records:read'] as string[] })
const hookForm = reactive({ name: '', url: '', eventTypes: ['record_created'] as string[] })

watch(
  () => props.visible,
  (v) => {
    if (v && props.baseId) {
      createdSecret.value = null
      createdHookSecret.value = ''
      showCreateKey.value = false
      showCreateHook.value = false
      reload()
    }
  },
  { immediate: true },
)

async function reload() {
  if (!props.baseId) return
  apiKeys.value = await listApiKeys(props.baseId).catch(() => [])
  webhooks.value = await listWebhooks(props.baseId).catch(() => [])
}

async function handleCreateKey() {
  if (!props.baseId) return
  creating.value = true
  try {
    const result = await createApiKey(props.baseId, { name: keyForm.name, scopes: keyForm.scopes })
    createdSecret.value = { keyId: String(result.keyId), secret: String(result.secret) }
    ElMessage.success('API Key 已创建')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function revokeKey(id: number) {
  try {
    await ElMessageBox.confirm('确定吊销该 API Key 吗？使用它的集成将立即失效。', '吊销确认', { type: 'warning' })
    await revokeApiKey(id)
    ElMessage.success('已吊销')
    await reload()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function handleCreateHook() {
  if (!props.baseId) return
  if (!hookForm.url.trim().startsWith('http')) {
    ElMessage.warning('请输入有效的 http/https URL')
    return
  }
  creating.value = true
  try {
    const result = await createWebhook(props.baseId, {
      name: hookForm.name,
      url: hookForm.url.trim(),
      eventTypes: hookForm.eventTypes,
    })
    createdHookSecret.value = String(result.secret)
    ElMessage.success('Webhook 已创建')
    hookForm.name = ''
    hookForm.url = ''
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function toggleHook(row: WebhookSubscriptionInfo) {
  await updateWebhookStatus(row.id, row.status !== 'enabled')
  await reload()
}

async function deleteHook(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该 Webhook 订阅吗？', '删除确认', { type: 'warning' })
    await deleteWebhook(id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.integration-toolbar {
  margin-bottom: 10px;
}
.integration-create {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
  padding: 10px;
  border: 1px dashed var(--color-border, #e2e8f0);
  border-radius: 8px;
}
.integration-secret {
  margin-bottom: 10px;
  word-break: break-all;
}
</style>
