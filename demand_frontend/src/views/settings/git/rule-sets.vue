<template>
  <PageContainer title="保护规则集" subtitle="将常用分支保护规则组合成规则集，批量应用到仓库" :show-title="true">
    <template #headerActions>
      <AppButton type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        创建规则集
      </AppButton>
    </template>

    <div v-loading="loading" class="rule-set-list">
      <el-empty v-if="!loading && ruleSets.length === 0" description="暂无规则集，点击右上角创建" />
      <el-card
        v-for="set in ruleSets"
        :key="set.id"
        shadow="hover"
        class="rule-set-card"
      >
        <div class="rule-set-card__body">
          <div class="rule-set-card__icon">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="rule-set-card__info">
            <div class="rule-set-card__name">{{ set.name }}</div>
            <div class="rule-set-card__desc">{{ set.description || '暂无描述' }}</div>
            <div class="rule-set-card__meta">
              ID：{{ set.id }} · 创建于 {{ formatDate(set.createdAt) }}
            </div>
          </div>
          <div class="rule-set-card__actions">
            <el-button type="primary" :icon="SetUp" @click="openApplyDialog(set)">应用到仓库</el-button>
            <el-button :icon="Delete" @click="handleDelete(set)">删除</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 创建弹窗 -->
    <el-dialog v-model="dialog.visible" title="创建规则集" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="dialog.form" :rules="formRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="例如：主干分支保护策略" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="3" placeholder="规则集用途说明（可选）" />
        </el-form-item>
        <div class="import-tip">创建规则集后，可在仓库详情的「分支保护」页将规则应用到单个仓库，或在本页批量应用到多个仓库。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="dialog.saving" @click="handleSave">创建</AppButton>
        </div>
      </template>
    </el-dialog>

    <!-- 应用到仓库 弹窗 -->
    <el-dialog
      v-model="applyDialog.visible"
      :title="`应用规则集「${applyDialog.setName}」`"
      width="560px"
      destroy-on-close
    >
      <el-form label-width="90px">
        <el-form-item label="目标仓库" required>
          <el-select
            v-model="applyDialog.repoIds"
            multiple
            filterable
            placeholder="选择要应用到的仓库"
            style="width: 100%"
          >
            <el-option v-for="r in repositories" :key="r.id" :label="`${r.name}（${r.fullPath}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <div class="import-tip">应用后将在所选仓库创建规则集中的所有保护规则，覆盖同分支模式的现有规则。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="applyDialog.visible = false">取消</el-button>
          <AppButton type="primary" :loading="applyDialog.saving" @click="handleApply">确认应用</AppButton>
        </div>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Collection, Delete, Plus, SetUp } from '@element-plus/icons-vue'
import AppButton from '@/components/common/AppButton.vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { formatDate } from '@/utils/format'
import type { GitRepository, ProtectionRuleSet } from '@/types/git'
import { getRuleSets, createRuleSet, applyRuleSet } from '@/api/modules/git'
import { getGitRepositories } from '@/api/modules/git'

const loading = ref(false)
const ruleSets = ref<ProtectionRuleSet[]>([])
const repositories = ref<GitRepository[]>([])

async function loadRuleSets() {
  loading.value = true
  try {
    const res = await getRuleSets() as any
    ruleSets.value = (res?.data ?? res ?? []) as ProtectionRuleSet[]
  } catch {
    ElMessage.error('规则集加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRepositories() {
  try {
    const res = await getGitRepositories({ status: 'ACTIVE' }) as any
    repositories.value = (res?.data ?? res ?? []) as GitRepository[]
  } catch {
    repositories.value = []
  }
}

// ── 创建 ──
const dialog = reactive({
  visible: false,
  saving: false,
  form: {
    name: '',
    description: '',
  },
})

const formRef = ref<FormInstance>()
const formRules: FormRules = {
  name: [{ required: true, message: '请输入规则集名称', trigger: 'blur' }],
}

function openCreateDialog() {
  dialog.form = { name: '', description: '' }
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
    await createRuleSet({ name: dialog.form.name, description: dialog.form.description || undefined })
    ElMessage.success('规则集已创建')
    dialog.visible = false
    await loadRuleSets()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    dialog.saving = false
  }
}

// ── 应用到仓库 ──
const applyDialog = reactive({
  visible: false,
  saving: false,
  setId: 0,
  setName: '',
  repoIds: [] as number[],
})

function openApplyDialog(set: ProtectionRuleSet) {
  applyDialog.setId = set.id
  applyDialog.setName = set.name
  applyDialog.repoIds = []
  applyDialog.visible = true
}

async function handleApply() {
  if (applyDialog.repoIds.length === 0) {
    ElMessage.warning('请至少选择一个仓库')
    return
  }
  applyDialog.saving = true
  try {
    await applyRuleSet(applyDialog.setId, applyDialog.repoIds)
    ElMessage.success(`已应用到 ${applyDialog.repoIds.length} 个仓库`)
    applyDialog.visible = false
  } catch {
    ElMessage.error('应用失败')
  } finally {
    applyDialog.saving = false
  }
}

// ── 删除（契约暂未提供规则集删除接口，仅前端本地移除提示） ──
async function handleDelete(set: ProtectionRuleSet) {
  try {
    await ElMessageBox.confirm(
      `规则集「${set.name}」当前版本暂不支持在线删除，请联系管理员处理后端数据。`,
      '删除规则集',
      { type: 'info', confirmButtonText: '知道了', cancelButtonText: '取消', showCancelButton: false },
    )
  } catch {
    // 用户关闭
  }
}

onMounted(() => {
  loadRuleSets()
  loadRepositories()
})
</script>

<style scoped lang="scss">
.rule-set-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  min-height: 120px;
}

.rule-set-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);

  :deep(.el-card__body) {
    padding: var(--spacing-md) var(--spacing-lg);
  }
}

.rule-set-card__body {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.rule-set-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--color-primary-subtle);
  color: var(--color-accent);
  font-size: 22px;
  flex-shrink: 0;
}

.rule-set-card__info {
  flex: 1;
  min-width: 0;
}

.rule-set-card__name {
  color: var(--color-text-primary);
  font-weight: 600;
  font-size: 15px;
}

.rule-set-card__desc {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-set-card__meta {
  margin-top: 4px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.rule-set-card__actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-shrink: 0;
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
