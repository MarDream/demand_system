<template>
  <PageContainer
    title="知识库管理"
    subtitle="集中管理知识库、文档分块与检索入口，提升团队沉淀和复用效率"
    :breadcrumb="false"
  >
    <template #headerActions>
      <el-button @click="store.fetchAllBases()" :loading="store.loading">刷新</el-button>
      <AppButton type="primary" permission="button:knowledge:create" @click="openCreateDialog">新建知识库</AppButton>
    </template>

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
          <el-input
            v-model="keyword"
            placeholder="搜索知识库名称或描述"
            clearable
            class="kb-toolbar__search"
          />
          <el-segmented v-model="statusFilter" :options="statusOptions" class="kb-toolbar__filter" />
        </div>

        <el-row v-if="filteredKnowledgeBases.length > 0" :gutter="20">
          <el-col v-for="kb in filteredKnowledgeBases" :key="kb.id" :xs="24" :sm="12" :md="8" :xl="6">
            <el-card class="kb-card" shadow="hover" @click="goToDetail(kb.id)">
              <template #header>
                <div class="kb-header">
                  <div class="kb-header__main">
                    <span class="kb-name">{{ kb.name }}</span>
                    <el-tag size="small" :type="kb.status === 'active' ? 'success' : 'info'">
                      {{ kb.status === 'active' ? '活跃' : '已归档' }}
                    </el-tag>
                  </div>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, kb)" @click.stop>
                    <el-icon class="more-btn"><MoreFilled /></el-icon>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="hasPermission('button:knowledge:update')" command="edit" :disabled="deletingId === kb.id">编辑</el-dropdown-item>
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
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import AppDialog from '@/components/common/AppDialog.vue'
import AppButton from '@/components/common/AppButton.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { usePermission } from '@/composables/usePermission'
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

async function handleCommand(cmd: string, kb: KnowledgeBase) {
  if (cmd === 'edit') {
    openEditDialog(kb)
  } else if (cmd === 'delete') {
    await handleDelete(kb)
  }
}

async function handleDelete(kb: KnowledgeBase) {
  if (deletingId.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${kb.name}」？删除后将同步清理该知识库下的文档和索引数据。`,
      '确认删除',
      { type: 'warning' }
    )
    deletingId.value = kb.id
    await store.removeBase(kb.id)
    ElMessage.success('删除成功')
  } catch {
    // 用户取消或接口错误时保持当前列表状态。
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
  gap: $spacing-md;
}

.kb-overview {
  margin-bottom: 0;
}

.overview-card {
  border-radius: $card-radius;
  border: 1px solid $border-color;
}

.overview-label {
  color: $text-color-secondary;
  font-size: $font-size-sm;
}

.overview-value {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 700;
  color: $text-color;
}

.overview-tip {
  margin-top: 6px;
  font-size: 12px;
  color: $text-color-placeholder;
}

.kb-panel {
  border-radius: $card-radius;
  border: 1px solid $border-color;
}

.kb-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.kb-toolbar__search {
  max-width: 360px;
}

.kb-card {
  margin-bottom: 20px;
  cursor: pointer;
  border-radius: $card-radius;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.kb-card:hover {
  transform: translateY(-2px);
}

.kb-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: $spacing-sm;
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
  color: $text-color;
  word-break: break-word;
}

.more-btn {
  cursor: pointer;
  color: $text-color-placeholder;
}

.kb-desc {
  color: $text-color-secondary;
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
  background: #f8fafc;
  border: 1px solid #eef2f7;
}

.kb-stat__value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: $text-color;
}

.kb-stat__label {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: $text-color-secondary;
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
  border-top: 1px solid $border-color;
}

.kb-creator {
  color: $text-color-placeholder;
  font-size: 12px;
}

.text-danger {
  color: $danger-color;
}

@media (max-width: 768px) {
  .kb-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .kb-toolbar__search {
    max-width: none;
  }
}
</style>
