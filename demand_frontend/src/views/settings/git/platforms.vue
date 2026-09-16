<template>
  <PageContainer title="Git 平台配置" subtitle="配置 GitLab / GitHub / Gitee / Gitea 等代码托管平台连接" :show-title="true">
    <template #headerActions>
      <AppButton type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        添加平台
      </AppButton>
    </template>

    <el-card shadow="never" class="git-card">
      <el-table
        :data="platforms"
        border
        v-loading="loading"
        :cell-style="{ textAlign: 'center' }"
        :header-cell-style="{ textAlign: 'center' }"
        class="git-table"
      >
        <el-table-column prop="name" label="平台名" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="repo-name">{{ row.name }}</span>
            <el-tag v-if="row.isDefault === 1" size="small" type="primary" effect="light" class="default-tag">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            <el-tag :type="platformTypeTag(row.platformType)" effect="plain" round>{{ platformTypeLabel(row.platformType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-text">{{ row.baseUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="认证方式" min-width="110">
          <template #default="{ row }">{{ authTypeLabel(row.authType) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <span class="status-pill">
              <span class="status-dot" :style="{ background: statusColor(row.status) }" />
              {{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最近检测" min-width="150">
          <template #default="{ row }">
            {{ row.lastCheckedAt ? formatDate(row.lastCheckedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Connection" @click="handleTest(row)">测试</el-button>
            <el-button link type="primary" :icon="EditPen" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑平台' : '添加平台'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="dialog.form" :rules="formRules" label-width="100px">
        <el-form-item label="平台名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="例如：公司 GitLab" />
        </el-form-item>
        <el-form-item label="平台类型" prop="platformType">
          <el-select v-model="dialog.form.platformType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in platformTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="dialog.form.baseUrl" placeholder="例如：https://gitlab.example.com" class="mono-input" />
        </el-form-item>
        <el-form-item label="认证方式" prop="authType">
          <el-select v-model="dialog.form.authType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in authTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="凭据" prop="credential">
          <el-input
            v-model="dialog.form.credential"
            type="password"
            show-password
            :placeholder="dialog.isEdit ? '留空则不修改（当前已脱敏）' : '请输入 Token / SSH Key / 密码'"
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <FieldLabelTip tip="默认平台在新建仓库时自动选中">设为默认</FieldLabelTip>
          </template>
          <el-switch v-model="dialog.form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="dialog.saving" @click="handleSave">保存</AppButton>
        </div>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Delete, EditPen, Plus } from '@element-plus/icons-vue'
import AppButton from '@/components/common/AppButton.vue'
import PageContainer from '@/components/common/PageContainer.vue'
import FieldLabelTip from '@/components/common/FieldLabelTip.vue'
import { formatDate } from '@/utils/format'
import type { GitPlatform } from '@/types/git'
import {
  getGitPlatforms,
  createGitPlatform,
  updateGitPlatform,
  deleteGitPlatform,
  testGitPlatform,
} from '@/api/modules/git'

const loading = ref(false)
const platforms = ref<GitPlatform[]>([])

async function loadPlatforms() {
  loading.value = true
  try {
    const res = await getGitPlatforms() as any
    platforms.value = (res?.data ?? res ?? []) as GitPlatform[]
  } catch {
    ElMessage.error('平台列表加载失败')
  } finally {
    loading.value = false
  }
}

// ── 弹窗 ──
const dialog = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingId: 0,
  form: {
    name: '',
    platformType: 'GITLAB' as GitPlatform['platformType'],
    baseUrl: '',
    authType: 'TOKEN' as GitPlatform['authType'],
    credential: '',
    isDefault: 0 as number,
  },
})

const formRef = ref<FormInstance>()
const formRules: FormRules = {
  name: [{ required: true, message: '请输入平台名称', trigger: 'blur' }],
  platformType: [{ required: true, message: '请选择平台类型', trigger: 'change' }],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' },
    { type: 'url', message: '请输入合法的 URL', trigger: 'blur' },
  ],
  authType: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
}

const platformTypeOptions = [
  { label: 'GitLab', value: 'GITLAB' },
  { label: 'GitHub', value: 'GITHUB' },
  { label: 'Gitee', value: 'GITEE' },
  { label: 'Gitea', value: 'GITEA' },
]
const authTypeOptions = [
  { label: 'Token', value: 'TOKEN' },
  { label: 'SSH Key', value: 'SSH_KEY' },
  { label: '密码', value: 'PASSWORD' },
]

function openCreateDialog() {
  dialog.isEdit = false
  dialog.editingId = 0
  dialog.form = { name: '', platformType: 'GITLAB', baseUrl: '', authType: 'TOKEN', credential: '', isDefault: 0 }
  dialog.visible = true
}

function openEditDialog(row: GitPlatform) {
  dialog.isEdit = true
  dialog.editingId = row.id
  dialog.form = {
    name: row.name,
    platformType: row.platformType,
    baseUrl: row.baseUrl,
    authType: row.authType,
    credential: '',
    isDefault: row.isDefault,
  }
  dialog.visible = true
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  dialog.saving = true
  try {
    const payload = { ...dialog.form }
    if (!payload.credential) delete (payload as Partial<typeof payload>).credential
    if (dialog.isEdit) {
      await updateGitPlatform(dialog.editingId, payload)
      ElMessage.success('平台已更新')
    } else {
      await createGitPlatform(payload)
      ElMessage.success('平台已添加')
    }
    dialog.visible = false
    await loadPlatforms()
  } catch {
    ElMessage.error(dialog.isEdit ? '更新失败' : '添加失败')
  } finally {
    dialog.saving = false
  }
}

// ── 测试连接 / 删除 ──
async function handleTest(row: GitPlatform) {
  try {
    const res = await testGitPlatform(row.id) as any
    const data = res?.data ?? res
    if (data?.connected) {
      ElMessage.success(data.message || `平台「${row.name}」连接成功`)
    } else {
      ElMessage.warning(data.message || `平台「${row.name}」连接失败`)
    }
  } catch {
    ElMessage.error('连接测试失败')
  } finally {
    await loadPlatforms()
  }
}

async function handleDelete(row: GitPlatform) {
  try {
    await ElMessageBox.confirm(`确定删除平台「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteGitPlatform(row.id)
    ElMessage.success('已删除')
    await loadPlatforms()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ── 展示辅助 ──
function platformTypeLabel(type: GitPlatform['platformType']) {
  return platformTypeOptions.find(o => o.value === type)?.label ?? type
}
function platformTypeTag(type: GitPlatform['platformType']): 'primary' | 'info' | 'warning' {
  if (type === 'GITLAB') return 'primary'
  if (type === 'GITHUB') return 'info'
  return 'warning'
}
function authTypeLabel(type: GitPlatform['authType']) {
  return authTypeOptions.find(o => o.value === type)?.label ?? type
}
function statusColor(status: GitPlatform['status']) {
  if (status === 'CONNECTED') return 'var(--color-success)'
  if (status === 'DISCONNECTED') return 'var(--color-danger)'
  return 'var(--color-warning)'
}
function statusLabel(status: GitPlatform['status']) {
  if (status === 'CONNECTED') return '已连接'
  if (status === 'DISCONNECTED') return '未连接'
  return '已过期'
}

onMounted(loadPlatforms)
</script>

<style scoped lang="scss">
.git-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }
}

.git-table {
  width: 100%;
}

.repo-name {
  color: var(--color-text-primary);
  font-weight: 600;
}

.default-tag {
  margin-left: 6px;
}

.mono-text {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.mono-input :deep(input) {
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.form-tip {
  width: 100%;
  margin-top: 4px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}
</style>
