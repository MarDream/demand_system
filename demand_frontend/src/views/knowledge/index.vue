<template>
  <PageContainer
    title="知识库管理"
    subtitle="集中管理知识库、文档分块与检索入口，提升团队沉淀和复用效率"
    :breadcrumb="false"
  >
    <div class="kb-page">
      <el-row :gutter="16" class="kb-overview">
        <el-col :xs="12" :md="6" v-for="item in overviewCards" :key="item.label">
          <el-card shadow="hover" class="overview-card">
            <div class="overview-label">{{ item.label }}</div>
            <div class="overview-value">{{ item.value }}</div>
            <div class="overview-tip">{{ item.tip }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="kb-panel" shadow="never">
        <div class="kb-toolbar">
          <div class="kb-toolbar__main">
            <el-input
              v-model="keyword"
              placeholder="搜索知识库名称或描述"
              clearable
              class="kb-toolbar__search"
            />
            <el-segmented v-model="statusFilter" :options="statusOptions" class="kb-toolbar__filter" />
          </div>

          <div class="kb-toolbar__actions">
            <el-button @click="store.fetchAllBases()" :loading="store.loading">刷新</el-button>
            <AppButton type="primary" permission="button:knowledge:create" @click="openCreateDialog">新建知识库</AppButton>
          </div>
        </div>

        <el-row v-if="filteredKnowledgeBases.length > 0" :gutter="20">
          <el-col v-for="kb in filteredKnowledgeBases" :key="kb.id" :xs="24" :sm="12" :md="8" :xl="6">
            <el-card class="kb-card" shadow="hover" @click="goToDetail(kb.id)">
              <template #header>
                <div class="kb-header">
                  <div class="kb-header__main">
                    <span class="kb-name">{{ kb.name }}</span>
                    <!-- 默认知识库标识 -->
                    <el-tag v-if="kb.isDefaultForRequirements" type="warning" size="small">
                      <el-icon><Star /></el-icon> 默认存储库
                    </el-tag>
                    <el-tag size="small" :type="kb.status === 'active' ? 'success' : 'info'">
                      {{ kb.status === 'active' ? '活跃' : '已归档' }}
                    </el-tag>
                  </div>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, kb)" @click.stop>
                    <el-icon class="more-btn"><MoreFilled /></el-icon>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="hasPermission('button:knowledge:update')" command="edit" :disabled="deletingId === kb.id">编辑</el-dropdown-item>
                        <!-- 设置/取消默认 -->
                        <el-dropdown-item
                          v-if="hasPermission('button:knowledge:manage') && !kb.isDefaultForRequirements"
                          command="setDefault"
                          divided
                        >
                          设为需求文件默认存储库
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="hasPermission('button:knowledge:manage') && kb.isDefaultForRequirements"
                          command="unsetDefault"
                          divided
                        >
                          取消默认存储库
                        </el-dropdown-item>
                        <el-dropdown-item v-if="hasPermission('button:knowledge:migrate')" command="migrate" :disabled="!kb.docCount || deletingId === kb.id" divided>
                          迁移文档
                        </el-dropdown-item>
                        <el-dropdown-item v-if="hasPermission('button:knowledge:delete')" command="delete" :disabled="deletingId === kb.id" divided>
                          <span class="text-danger">删除</span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
              <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
              <div class="kb-stats">
                <div class="kb-stat">
                  <span class="kb-stat__value">{{ kb.docCount }}</span>
                  <span class="kb-stat__label">文档</span>
                </div>
                <div class="kb-stat">
                  <span class="kb-stat__value">{{ kb.chunkCount }}</span>
                  <span class="kb-stat__label">分块</span>
                </div>
              </div>
              <div class="kb-footer">
                <span class="kb-creator">创建人：{{ kb.creatorName || '未知' }}</span>
              </div>
              <div class="kb-actions" @click.stop>
                <el-button size="small" @click="goToDetail(kb.id)">进入知识库</el-button>
                <AppButton size="small" type="primary" permission="button:knowledge:update" :disabled="deletingId === kb.id" @click="openEditDialog(kb)">
                  编辑
                </AppButton>
                <AppButton
                  v-if="kb.docCount > 0"
                  size="small"
                  type="warning"
                  permission="button:knowledge:migrate"
                  :disabled="deletingId === kb.id"
                  @click="openMigrateDialog(kb)"
                >
                  迁移文档
                </AppButton>
                <AppButton size="small" type="danger" plain permission="button:knowledge:delete" :disabled="deletingId === kb.id" @click="handleDelete(kb)">
                  删除
                </AppButton>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-empty
          v-else
          :description="keyword || statusFilter !== 'all' ? '未找到符合条件的知识库' : '暂无知识库，点击右上角创建'"
        />
      </el-card>
    </div>

    <!-- 新建/编辑对话框 -->
    <AppDialog
      v-model="showCreateDialog"
      :title="editingKb ? '编辑知识库' : '新建知识库'"
      width="500px"
      @close="resetForm"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="请输入知识库名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </AppDialog>

    <!-- 文档迁移对话框 -->
    <el-dialog
      v-model="showMigrateDialog"
      title="迁移文档到其他知识库"
      width="640px"
      :close-on-click-modal="false"
    >
      <template v-if="migratingKb">
        <el-alert
          v-if="migratingKb.docCount > 0"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>
            <strong>「{{ migratingKb.name }}」</strong> 当前包含
            <strong>{{ migratingKb.docCount }}</strong> 个文档、
            <strong>{{ migratingKb.chunkCount }}</strong> 个分块
          </template>
          迁移后这些文档将归属到目标知识库，便于删除源知识库时保留数据。
        </el-alert>

        <el-form :model="migrateForm" label-width="100px" style="margin-top: 16px">
          <el-form-item label="目标知识库" required>
            <el-select
              v-model="migrateForm.targetId"
              placeholder="选择目标知识库"
              filterable
              style="width: 100%"
              :disabled="availableTargets.length === 0"
            >
              <el-option
                v-for="opt in availableTargets"
                :key="opt.id"
                :label="`${opt.name}（${opt.docCount} 文档 / ${opt.chunkCount} 分块）`"
                :value="opt.id"
              />
            </el-select>
            <div v-if="availableTargets.length === 0" class="migrate-hint">
              没有可用的目标知识库，请先创建其他知识库。
            </div>
          </el-form-item>
          <el-form-item label="迁移原因">
            <el-input
              v-model="migrateForm.reason"
              type="textarea"
              :rows="2"
              placeholder="例如：删除前的数据迁移、合并到统一知识库等"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-form>

        <el-divider>迁移影响说明</el-divider>
        <ul class="migrate-impact">
          <li>📦 数据库：<code>knowledge_documents</code> / <code>knowledge_chunks</code> 表的 <code>knowledge_base_id</code> 字段会被更新</li>
          <li>🧠 向量：原 Milvus 向量将被删除，文档会通过消息队列<strong>异步重新索引</strong>到目标知识库</li>
          <li>📊 计数：源/目标知识库的文档数和分块数会自动调整</li>
          <li>📁 文件：MinIO 中的文件本身<strong>保持不变</strong>，无需复制</li>
        </ul>
      </template>

      <template #footer>
        <el-button @click="showMigrateDialog = false">取消</el-button>
        <el-button
          v-permission="'button:knowledge:migrate'"
          type="primary"
          :loading="migrating"
          :disabled="!migrateForm.targetId"
          @click="confirmMigrate"
        >
          确认迁移
        </el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Star } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import AppDialog from '@/components/common/AppDialog.vue'
import AppButton from '@/components/common/AppButton.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { usePermission } from '@/composables/usePermission'
import { setAsDefaultKnowledgeBase, unsetDefaultKnowledgeBase } from '@/api/modules/knowledge'
import type { KnowledgeBase } from '@/api/modules/knowledge'

const router = useRouter()
const store = useKnowledgeStore()
const { hasPermission } = usePermission()
const showCreateDialog = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const submitting = ref(false)
const deletingId = ref<number | null>(null)
const form = reactive({ name: '', description: '' })
const keyword = ref('')
const statusFilter = ref<'all' | 'active' | 'archived'>('all')

// 迁移相关状态
const showMigrateDialog = ref(false)
const migratingKb = ref<KnowledgeBase | null>(null)
const migrating = ref(false)
const migrateForm = reactive({
  targetId: undefined as number | undefined,
  reason: '',
})

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '活跃', value: 'active' },
  { label: '已归档', value: 'archived' },
]

const filteredKnowledgeBases = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return store.knowledgeBases.filter((kb) => {
    const matchStatus = statusFilter.value === 'all' || kb.status === statusFilter.value
    const matchKeyword = !search
      || kb.name.toLowerCase().includes(search)
      || (kb.description || '').toLowerCase().includes(search)
    return matchStatus && matchKeyword
  })
})

// 可选目标知识库：排除当前正在迁移的源知识库
const availableTargets = computed(() => {
  if (!migratingKb.value) return []
  return store.knowledgeBases.filter(
    (kb) => kb.id !== migratingKb.value!.id && kb.status === 'active'
  )
})

const overviewCards = computed(() => {
  const total = store.knowledgeBases.length
  const active = store.knowledgeBases.filter((item) => item.status === 'active').length
  const docs = store.knowledgeBases.reduce((sum, item) => sum + (item.docCount || 0), 0)
  const chunks = store.knowledgeBases.reduce((sum, item) => sum + (item.chunkCount || 0), 0)
  return [
    { label: '知识库总数', value: total, tip: '当前已接入的知识空间' },
    { label: '活跃知识库', value: active, tip: '可被检索和更新的知识库' },
    { label: '文档总量', value: docs, tip: '所有知识库累计文档数' },
    { label: '分块总量', value: chunks, tip: '向量检索使用的文本分块' },
  ]
})

onMounted(() => {
  store.fetchAllBases()
})

function goToDetail(id: number) {
  router.push(`/settings/knowledge/${id}`)
}

function resetForm() {
  editingKb.value = null
  form.name = ''
  form.description = ''
}

function openCreateDialog() {
  resetForm()
  showCreateDialog.value = true
}

function openEditDialog(kb: KnowledgeBase) {
  editingKb.value = kb
  form.name = kb.name
  form.description = kb.description || ''
  showCreateDialog.value = true
}

// ===== 迁移流程 =====
function openMigrateDialog(kb: KnowledgeBase) {
  migratingKb.value = kb
  migrateForm.targetId = undefined
  migrateForm.reason = ''
  showMigrateDialog.value = true
}

async function confirmMigrate() {
  if (!migratingKb.value || !migrateForm.targetId) return
  const sourceName = migratingKb.value.name
  const target = store.knowledgeBases.find((kb) => kb.id === migrateForm.targetId)
  const targetName = target?.name || '目标知识库'

  try {
    await ElMessageBox.confirm(
      `确认将「${sourceName}」下的所有文档迁移到「${targetName}」？该操作会立即更新数据库与计数，并向消息队列发送重新索引任务。`,
      '确认迁移',
      { type: 'warning', confirmButtonText: '确认迁移', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  migrating.value = true
  try {
    const result = await store.migrateDocuments(migratingKb.value.id, {
      targetKnowledgeBaseId: migrateForm.targetId,
      reason: migrateForm.reason || undefined,
    })
    ElMessage.success(
      `迁移完成：${result.migratedDocuments} 个文档、${result.migratedChunks} 个分块（后台正在重新索引）`
    )
    showMigrateDialog.value = false
  } catch (err: any) {
    ElMessage.error(err?.message || '迁移失败')
  } finally {
    migrating.value = false
  }
}

async function handleCommand(cmd: string, kb: KnowledgeBase) {
  if (cmd === 'edit') {
    openEditDialog(kb)
  } else if (cmd === 'migrate') {
    openMigrateDialog(kb)
  } else if (cmd === 'delete') {
    await handleDelete(kb)
  } else if (cmd === 'setDefault') {
    await handleSetDefault(kb)
  } else if (cmd === 'unsetDefault') {
    await handleUnsetDefault(kb)
  }
}

async function handleSetDefault(kb: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(
      '设置为默认存储库后，需求工单流转中上传的所有文件将自动存储到此知识库。系统全局只允许一个默认存储库，确认设置？',
      '确认操作',
      { type: 'warning' }
    )
    await setAsDefaultKnowledgeBase(kb.id)
    ElMessage.success('设置成功')
    store.fetchAllBases()
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '设置失败')
    }
  }
}

async function handleUnsetDefault(kb: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(
      '取消后，需求文件将存储到各项目的专属知识库中，确认取消？',
      '确认操作',
      { type: 'warning' }
    )
    await unsetDefaultKnowledgeBase(kb.id)
    ElMessage.success('取消成功')
    store.fetchAllBases()
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '取消失败')
    }
  }
}

async function handleDelete(kb: KnowledgeBase) {
  if (deletingId.value) return

  // 当知识库下还有文档时，强制要求先迁移
  if ((kb.docCount || 0) > 0) {
    const choice = await ElMessageBox({
      type: 'warning',
      title: '该知识库下还有文档',
      message: `「${kb.name}」包含 ${kb.docCount} 个文档、${kb.chunkCount} 个分块。直接删除将不可恢复地清理这些数据。\n\n请先选择处理方式：`,
      showCancelButton: true,
      showClose: true,
      confirmButtonText: '先迁移文档',
      cancelButtonText: '我已知晓，强制删除',
      distinguishCancelAndClose: true,
      dangerouslyUseHTMLString: false,
    }).then(() => 'migrate').catch((action) => action)

    if (choice === 'close') return
    if (choice === 'migrate') {
      openMigrateDialog(kb)
      return
    }
    // choice === 'cancel' -> 强制删除
  }

  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${kb.name}」？删除后将同步清理该知识库下的文档和索引数据。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  deletingId.value = kb.id
  try {
    await store.removeBase(kb.id)
    ElMessage.success('删除成功')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  } finally {
    if (deletingId.value === kb.id) {
      deletingId.value = null
    }
  }
}

async function handleSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  submitting.value = true
  const name = form.name.trim()
  const description = form.description.trim()
  try {
    if (editingKb.value) {
      await store.updateBase(editingKb.value.id, { name, description })
      ElMessage.success('更新成功')
    } else {
      await store.createBase({ name, description })
      ElMessage.success('创建成功')
    }
    showCreateDialog.value = false
    resetForm()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.kb-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.kb-overview {
  margin-bottom: 0;
}

.overview-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.overview-label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.overview-value {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--color-text-primary);
}

.overview-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.kb-panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.kb-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.kb-toolbar__main {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.kb-toolbar__search {
  max-width: 360px;
  flex: 1 1 260px;
}

.kb-toolbar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.kb-card {
  margin-bottom: 20px;
  cursor: pointer;
  border-radius: var(--radius-lg);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.kb-card:hover {
  transform: translateY(-2px);
}

.kb-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
}

.kb-header__main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.kb-name {
  font-weight: 600;
  font-size: 16px;
  color: var(--color-text-primary);
  word-break: break-word;
}

.more-btn {
  cursor: pointer;
  color: var(--color-text-placeholder);
}

.kb-desc {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 14px;
  min-height: 44px;
}

.kb-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.kb-stat {
  padding: 12px;
  border-radius: 10px;
  background: var(--color-surface-alt);
  border: 1px solid var(--color-border);
}

.kb-stat__value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.kb-stat__label {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.kb-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.kb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  flex-wrap: wrap;
}

.kb-creator {
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.text-danger {
  color: var(--color-danger);
}

.migrate-hint {
  font-size: var(--font-size-xs);
  color: var(--color-muted-text);
  margin-top: 4px;
}

.migrate-impact {
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.8;

  li {
    padding: 4px 0;
  }

  code {
    background: var(--color-surface-alt);
    padding: 1px 6px;
    border-radius: var(--radius-sm);
    font-size: 12px;
    color: var(--color-text-primary);
  }
}

@media (max-width: 768px) {
  .kb-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .kb-toolbar__main,
  .kb-toolbar__actions {
    width: 100%;
  }

  .kb-toolbar__actions {
    justify-content: flex-start;
  }

  .kb-toolbar__search {
    max-width: none;
  }
}
</style>
