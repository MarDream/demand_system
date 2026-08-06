<template>
  <PageContainer
    title="知识库管理"
    subtitle="集中管理知识库、文档分块与检索入口，提升团队沉淀和复用效率"
    :breadcrumb="false"
  >
    <div class="kb-page">
      <!-- 概览卡片 -->
      <div class="kb-overview">
        <div class="kb-overview__card" v-for="item in overviewCards" :key="item.label">
          <div class="kb-overview__icon" :style="{ background: item.color }">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="kb-overview__body">
            <div class="kb-overview__label">{{ item.label }}</div>
            <div class="kb-overview__value">{{ item.value }}</div>
            <div class="kb-overview__tip">{{ item.tip }}</div>
          </div>
        </div>
      </div>

      <!-- 工具栏 + 列表 -->
      <div class="kb-shell">
        <div class="kb-toolbar">
          <div class="kb-toolbar__search-group">
            <el-input
              v-model="keyword"
              placeholder="搜索名称或描述…"
              clearable
              class="kb-toolbar__search"
              :prefix-icon="Search"
            />
            <el-segmented v-model="statusFilter" :options="statusOptions" class="kb-toolbar__seg" />
          </div>
          <div class="kb-toolbar__actions">
            <el-button text @click="store.fetchAllBases()" :loading="store.loading" class="kb-toolbar__refresh">
              <el-icon><Refresh /></el-icon>
            </el-button>
            <AppButton type="primary" permission="button:knowledge:create" @click="openCreateDialog">新建知识库</AppButton>
          </div>
        </div>

        <div class="kb-grid" v-if="filteredKnowledgeBases.length > 0">
          <div class="kb-card" v-for="kb in filteredKnowledgeBases" :key="kb.id" @click="goToDetail(kb.id)">
            <div class="kb-card__head">
              <div class="kb-card__name-row">
                <span class="kb-card__dot" :class="`is-${kb.status}`"></span>
                <span class="kb-card__name">{{ kb.name }}</span>
              </div>
              <div class="kb-card__badges">
                <span v-if="kb.isDefaultForRequirements" class="kb-card__badge is-star">
                  <el-icon><Star /></el-icon> 默认
                </span>
                <span class="kb-card__badge" :class="kb.status === 'active' ? 'is-active' : 'is-archived'">
                  {{ kb.status === 'active' ? '活跃' : '已归档' }}
                </span>
              </div>
            </div>

            <p class="kb-card__desc">{{ kb.description || '暂无描述' }}</p>

            <div class="kb-card__stats">
              <span class="kb-card__stat"><strong>{{ kb.docCount }}</strong> 文档</span>
              <span class="kb-card__divider"></span>
              <span class="kb-card__stat"><strong>{{ kb.chunkCount }}</strong> 分块</span>
              <span class="kb-card__divider"></span>
              <span class="kb-card__stat">{{ kb.creatorName || '未知' }}</span>
            </div>

            <div class="kb-card__actions" @click.stop>
              <el-button size="small" type="primary" plain @click="goToDetail(kb.id)">进入</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, kb)">
                <el-button size="small" text circle class="kb-card__more-btn">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="hasPermission('button:knowledge:update')" command="edit" :disabled="deletingId === kb.id">编辑信息</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('button:knowledge:manage') && !kb.isDefaultForRequirements" command="setDefault" divided>设为默认存储库</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('button:knowledge:manage') && kb.isDefaultForRequirements" command="unsetDefault" divided>取消默认存储库</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('button:knowledge:migrate')" command="migrate" :disabled="!kb.docCount || deletingId === kb.id" divided>迁移文档</el-dropdown-item>
                    <el-dropdown-item v-if="hasPermission('button:knowledge:delete')" command="delete" :disabled="deletingId === kb.id" divided>
                      <span class="text-danger">删除知识库</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>

        <el-empty
          v-else
          :description="keyword || statusFilter !== 'all' ? '未找到符合条件的知识库' : '暂无知识库，点击右上角「新建知识库」开始'"
          :image-size="80"
        />
      </div>
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
        <el-form-item label="超时时间">
          <div style="display: flex; align-items: center; gap: 8px;">
            <el-input-number v-model="form.docTimeoutMinutes" :min="0" :max="120" :step="5" placeholder="20" />
            <span style="color: var(--el-text-color-secondary); font-size: 13px;">分钟（0=不超时，默认10）</span>
          </div>
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
import { resolveErrorMessage } from '@/utils/error'
import { Collection, DataBoard, DocumentChecked, Files, MoreFilled, Refresh, Search, Star } from '@element-plus/icons-vue'
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
const form = reactive({ name: '', description: '', docTimeoutMinutes: 10 })
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
    { label: '知识库总数', value: total, tip: '已接入的知识空间', icon: 'Collection', color: 'linear-gradient(135deg, #2563eb, #6366f1)' },
    { label: '活跃知识库', value: active, tip: '可被检索和更新的知识库', icon: 'DocumentChecked', color: 'linear-gradient(135deg, #22c55e, #16a34a)' },
    { label: '文档总量', value: docs, tip: '所有知识库累计文档数', icon: 'Files', color: 'linear-gradient(135deg, #f59e0b, #d97706)' },
    { label: '分块总量', value: chunks, tip: '向量检索使用的文本分块', icon: 'DataBoard', color: 'linear-gradient(135deg, #8b5cf6, #7c3aed)' },
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
  form.docTimeoutMinutes = 10
}

function openCreateDialog() {
  resetForm()
  showCreateDialog.value = true
}

function openEditDialog(kb: KnowledgeBase) {
  editingKb.value = kb
  form.name = kb.name
  form.description = kb.description || ''
  form.docTimeoutMinutes = kb.docTimeoutMinutes ?? 10
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
    ElMessage.error(resolveErrorMessage(err, '迁移失败'))
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
      ElMessage.error(resolveErrorMessage(err, '设置失败'))
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
      ElMessage.error(resolveErrorMessage(err, '取消失败'))
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
    ElMessage.error(resolveErrorMessage(err, '删除失败'))
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
  const docTimeoutMinutes = form.docTimeoutMinutes
  try {
    if (editingKb.value) {
      await store.updateBase(editingKb.value.id, { name, description, docTimeoutMinutes })
      ElMessage.success('更新成功')
    } else {
      await store.createBase({ name, description, docTimeoutMinutes })
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
  gap: 24px;
}

/* ---- 概览卡片 ---- */
.kb-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kb-overview__card {
  position: relative;
  display: flex;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-white);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    opacity: 0.04;
    pointer-events: none;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(31, 35, 41, 0.08);
  }
}

.kb-overview__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  flex-shrink: 0;
  color: #fff;
  font-size: 18px;
}

.kb-overview__body {
  min-width: 0;
}

.kb-overview__label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-placeholder);
  letter-spacing: 0.3px;
  text-transform: uppercase;
}

.kb-overview__value {
  margin-top: 2px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.kb-overview__tip {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-secondary);
}

/* ---- 面板 + 工具栏 ---- */
.kb-shell {
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-bg-white);
  overflow: hidden;
}

.kb-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface-alt);
}

.kb-toolbar__search-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.kb-toolbar__search {
  max-width: 300px;
  flex: 1 1 220px;
}

.kb-toolbar__seg {
  flex-shrink: 0;
}

.kb-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.kb-toolbar__refresh {
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--color-text-placeholder);
  font-size: 16px;
}

/* ---- 卡片网格 ---- */
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px;
}

.kb-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 18px 20px 14px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-white);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(31, 35, 41, 0.07);
    border-color: #d0d7e2;
  }
}

.kb-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.kb-card__name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.kb-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-text-placeholder);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);

  &.is-active { background: #22c55e; box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15); }
  &.is-archived { background: #a1a1aa; }
  &.is-default { background: #f59e0b; }
}

.kb-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card__badges {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  flex-wrap: nowrap;
}

.kb-card__badge {
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;

  &.is-star {
    background: #fef3c7;
    color: #92400e;
    display: inline-flex;
    align-items: center;
    gap: 2px;
    .el-icon { font-size: 11px; }
  }
  &.is-active {
    background: #dcfce7;
    color: #166534;
  }
  &.is-archived {
    background: #f4f4f5;
    color: #71717a;
  }
}

.kb-card__desc {
  flex: 1;
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* 统计行 */
.kb-card__stats {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: var(--color-surface-alt);
  border: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-secondary);
  flex-wrap: wrap;
}

.kb-card__stat {
  white-space: nowrap;
  strong {
    font-weight: 600;
    color: var(--color-text-primary);
    font-variant-numeric: tabular-nums;
  }
}

.kb-card__divider {
  width: 1px;
  height: 14px;
  background: var(--color-border);
  flex-shrink: 0;
}

/* 操作行 */
.kb-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}

.kb-card__more-btn {
  width: 28px;
  height: 28px;
  color: var(--color-text-placeholder);
  font-size: 16px;
}

/* ---- 迁移弹窗 ---- */
.migrate-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.migrate-source {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-surface-alt);
}

.migrate-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-placeholder);
}

.migrate-code {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.migrate-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.migrate-form {
  margin-top: 4px;
}

.migrate-impact {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid #fde68a;
  background: #fffbeb;

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: #92400e;
    margin-bottom: 8px;
  }
}

.migrate-list {
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--color-text-secondary);

  li {
    padding: 2px 0;
    &::before { content: '• '; color: #f59e0b; }
  }

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: #fef3c7;
    font-size: 11px;
    color: #92400e;
  }
}

.text-danger { color: var(--color-danger); }

/* ---- 响应式 ---- */
@media (max-width: 1024px) {
  .kb-overview {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .kb-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .kb-toolbar__search-group {
    flex-direction: column;
  }
  .kb-toolbar__search { max-width: none; }
}

@media (max-width: 640px) {
  .kb-overview {
    grid-template-columns: 1fr;
  }
  .kb-grid {
    grid-template-columns: 1fr;
    padding: 12px;
  }
}
</style>
