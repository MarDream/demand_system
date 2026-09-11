<template>
  <PageContainer title="代码仓库管理" subtitle="统一管理接入的 Git 仓库，配置分支保护与合并流程" :show-title="true">
    <template #headerActions>
      <el-button @click="$router.push('/settings/git/platforms')">
        <el-icon><Connection /></el-icon>
        平台配置
      </el-button>
      <el-button @click="$router.push('/settings/git/rule-sets')">
        <el-icon><Collection /></el-icon>
        保护规则集
      </el-button>
      <AppButton @click="openImportDialog">
        <el-icon><Download /></el-icon>
        导入
      </AppButton>
      <AppButton type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        创建仓库
      </AppButton>
    </template>

    <el-card shadow="never" class="git-card">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索仓库名 / 完整路径"
          clearable
          class="filter-control filter-control--keyword"
          @keyup.enter="loadRepositories"
          @clear="loadRepositories"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.platformId" placeholder="全部平台" clearable class="filter-control">
          <el-option v-for="p in platforms" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-control">
          <el-option label="活跃" value="ACTIVE" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-button type="primary" @click="loadRepositories">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table
        :data="repositories"
        border
        v-loading="loading"
        :cell-style="{ textAlign: 'center' }"
        :header-cell-style="{ textAlign: 'center' }"
        class="git-table"
      >
        <el-table-column label="仓库" min-width="200">
          <template #default="{ row }">
            <div class="repo-cell">
              <el-icon class="repo-icon"><FolderOpened /></el-icon>
              <span class="repo-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fullPath" label="完整路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-text">{{ row.fullPath }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属平台" min-width="130">
          <template #default="{ row }">
            <span v-if="row.platformName">{{ row.platformName }}</span>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="所属项目" min-width="130">
          <template #default="{ row }">
            <span v-if="row.projectName">{{ row.projectName }}</span>
            <span v-else class="text-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="默认分支" min-width="120">
          <template #default="{ row }">
            <span class="mono-text">{{ row.defaultBranch || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="repoStatusType(row.status)" effect="light" size="small">{{ repoStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="goDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="warning"
              :icon="FolderChecked"
              @click="handleArchive(row)"
            >归档</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建 / 编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑仓库' : '创建仓库'"
      width="620px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="dialog.form" :rules="formRules" label-width="110px">
        <el-form-item label="仓库名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="例如：demand-backend" />
        </el-form-item>
        <el-form-item label="所属平台" prop="platformId">
          <el-select v-model="dialog.form.platformId" placeholder="请选择平台" style="width: 100%">
            <el-option v-for="p in platforms" :key="p.id" :label="`${p.name}（${platformTypeLabel(p.platformType)}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目" prop="projectId">
          <el-select
            v-model="dialog.form.projectId"
            placeholder="可留空，不关联项目"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option v-for="pr in projectOptions" :key="pr.id" :label="pr.name" :value="pr.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="完整路径" prop="fullPath">
          <el-input v-model="dialog.form.fullPath" placeholder="例如：group/demand-backend" class="mono-input" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="2" placeholder="仓库描述（可选）" />
        </el-form-item>
        <el-form-item label="默认分支" prop="defaultBranch">
          <el-input v-model="dialog.form.defaultBranch" placeholder="例如：main / master" class="mono-input" />
        </el-form-item>
        <el-form-item label="SSH 克隆地址">
          <el-input v-model="dialog.form.cloneUrlSsh" placeholder="git@host:group/repo.git" class="mono-input" />
        </el-form-item>
        <el-form-item label="HTTPS 克隆地址">
          <el-input v-model="dialog.form.cloneUrlHttps" placeholder="https://host/group/repo.git" class="mono-input" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="dialog.saving" @click="handleSave">保存</AppButton>
        </div>
      </template>
    </el-dialog>

    <!-- 导入弹窗（简化版：填写 fullPath 创建） -->
    <el-dialog v-model="importDialog.visible" title="导入仓库" width="480px" destroy-on-close>
      <el-form :model="importDialog" label-width="90px">
        <el-form-item label="所属平台" required>
          <el-select v-model="importDialog.platformId" placeholder="请选择平台" style="width: 100%">
            <el-option v-for="p in platforms" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="完整路径" required>
          <el-input v-model="importDialog.fullPath" placeholder="例如：group/demand-backend" class="mono-input" />
        </el-form-item>
        <div class="import-tip">填写远端仓库路径后确认，系统将从所属平台拉取仓库信息并接入。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="importDialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="importDialog.saving" @click="handleImport">确认导入</AppButton>
        </div>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Collection, Connection, Delete, Download, FolderChecked, FolderOpened, Plus, Search, View } from '@element-plus/icons-vue'
import AppButton from '@/components/common/AppButton.vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { getProjectList } from '@/api/modules/project'
import type { GitPlatform, GitRepository } from '@/types/git'
import {
  getGitPlatforms,
  getGitRepositories,
  createGitRepository,
  updateGitRepository,
  archiveGitRepository,
  deleteGitRepository,
} from '@/api/modules/git'

const router = useRouter()

const loading = ref(false)
const repositories = ref<GitRepository[]>([])
const platforms = ref<GitPlatform[]>([])

const filters = reactive({
  keyword: '',
  platformId: undefined as number | undefined,
  status: '',
})

// ── 项目下拉数据 ──
const projectOptions = ref<Array<{ id: number; name: string }>>([])
async function loadProjectOptions() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 200 }) as any
    const data = res?.data ?? res
    projectOptions.value = (data?.list ?? data ?? []).map((p: any) => ({ id: p.id, name: p.name }))
  } catch {
    projectOptions.value = []
  }
}

// ── 加载 ──
async function loadPlatforms() {
  try {
    const res = await getGitPlatforms() as any
    platforms.value = (res?.data ?? res ?? []) as GitPlatform[]
  } catch {
    platforms.value = []
  }
}

async function loadRepositories() {
  loading.value = true
  try {
    const res = await getGitRepositories({
      keyword: filters.keyword || undefined,
      platformId: filters.platformId,
      status: filters.status || undefined,
    }) as any
    repositories.value = (res?.data ?? res ?? []) as GitRepository[]
  } catch {
    ElMessage.error('仓库列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  filters.keyword = ''
  filters.platformId = undefined
  filters.status = ''
  loadRepositories()
}

// ── 弹窗 ──
const dialog = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingId: 0,
  form: {
    name: '',
    platformId: undefined as number | undefined,
    projectId: undefined as number | undefined,
    fullPath: '',
    description: '',
    defaultBranch: 'main',
    cloneUrlSsh: '',
    cloneUrlHttps: '',
  },
})

const formRef = ref<FormInstance>()
const formRules: FormRules = {
  name: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  platformId: [{ required: true, message: '请选择所属平台', trigger: 'change' }],
  fullPath: [{ required: true, message: '请输入完整路径', trigger: 'blur' }],
  defaultBranch: [{ required: true, message: '请输入默认分支', trigger: 'blur' }],
}

function openCreateDialog() {
  dialog.isEdit = false
  dialog.editingId = 0
  dialog.form = {
    name: '',
    platformId: platforms.value[0]?.id,
    projectId: undefined,
    fullPath: '',
    description: '',
    defaultBranch: 'main',
    cloneUrlSsh: '',
    cloneUrlHttps: '',
  }
  dialog.visible = true
}

function openEditDialog(row: GitRepository) {
  dialog.isEdit = true
  dialog.editingId = row.id
  dialog.form = {
    name: row.name,
    platformId: row.platformId,
    projectId: row.projectId ?? undefined,
    fullPath: row.fullPath,
    description: row.description ?? '',
    defaultBranch: row.defaultBranch,
    cloneUrlSsh: row.cloneUrlSsh ?? '',
    cloneUrlHttps: row.cloneUrlHttps ?? '',
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
    if (dialog.isEdit) {
      await updateGitRepository(dialog.editingId, payload)
      ElMessage.success('仓库已更新')
    } else {
      await createGitRepository(payload)
      ElMessage.success('仓库已创建')
    }
    dialog.visible = false
    await loadRepositories()
  } catch {
    ElMessage.error(dialog.isEdit ? '更新失败' : '创建失败')
  } finally {
    dialog.saving = false
  }
}

// ── 导入 ──
const importDialog = reactive({
  visible: false,
  saving: false,
  platformId: undefined as number | undefined,
  fullPath: '',
})

function openImportDialog() {
  importDialog.platformId = platforms.value[0]?.id
  importDialog.fullPath = ''
  importDialog.visible = true
}

async function handleImport() {
  if (!importDialog.platformId) {
    ElMessage.warning('请选择所属平台')
    return
  }
  if (!importDialog.fullPath.trim()) {
    ElMessage.warning('请填写完整路径')
    return
  }
  importDialog.saving = true
  try {
    const path = importDialog.fullPath.trim()
    const name = path.split('/').pop() || path
    await createGitRepository({
      platformId: importDialog.platformId,
      name,
      fullPath: path,
      defaultBranch: 'main',
    })
    ElMessage.success('仓库导入成功')
    importDialog.visible = false
    await loadRepositories()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importDialog.saving = false
  }
}

// ── 操作 ──
function goDetail(row: GitRepository) {
  router.push(`/settings/git/repositories/${row.id}`)
}

async function handleArchive(row: GitRepository) {
  try {
    await ElMessageBox.confirm(`确定归档仓库「${row.name}」吗？归档后仅保留记录，不再参与日常流程。`, '归档确认', {
      type: 'warning',
      confirmButtonText: '归档',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await archiveGitRepository(row.id)
    ElMessage.success('已归档')
    await loadRepositories()
  } catch {
    ElMessage.error('归档失败')
  }
}

async function handleDelete(row: GitRepository) {
  try {
    await ElMessageBox.confirm(`确定删除仓库「${row.name}」吗？此操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteGitRepository(row.id)
    ElMessage.success('已删除')
    await loadRepositories()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ── 展示辅助 ──
const platformTypeOptions = [
  { label: 'GitLab', value: 'GITLAB' },
  { label: 'GitHub', value: 'GITHUB' },
  { label: 'Gitee', value: 'GITEE' },
  { label: 'Gitea', value: 'GITEA' },
]
function platformTypeLabel(type: string) {
  return platformTypeOptions.find(o => o.value === type)?.label ?? type
}

function repoStatusType(status: GitRepository['status']): 'success' | 'info' | 'danger' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'danger'
}
function repoStatusLabel(status: GitRepository['status']) {
  if (status === 'ACTIVE') return '活跃'
  if (status === 'ARCHIVED') return '已归档'
  return '已删除'
}

onMounted(() => {
  loadPlatforms()
  loadProjectOptions()
  loadRepositories()
})
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

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  margin-bottom: var(--spacing-md);
}

.filter-control {
  width: 180px;

  &--keyword {
    width: 260px;
  }
}

.git-table {
  width: 100%;
}

.repo-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.repo-icon {
  color: var(--color-accent);
}

.repo-name {
  color: var(--color-text-primary);
  font-weight: 600;
}

.mono-text {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.mono-input :deep(input) {
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.text-placeholder {
  color: var(--color-text-placeholder);
}

.import-tip {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--color-primary-subtle);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}
</style>
