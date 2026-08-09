<template>
  <PageContainer
    title="AI 助手设置"
    subtitle="管理智能助手快捷提问：人工维护高频问题，AI 自动提炼补齐"
  >
    <div class="assistant-settings">
      <!-- 概览统计 -->
      <div class="stat-row" aria-label="快捷提问统计">
        <div class="stat-card">
          <span class="stat-label">快捷问题总数</span>
          <strong class="stat-value">{{ questions.length }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">已启用</span>
          <strong class="stat-value stat-value--success">{{ enabledCount }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">AI 提炼建议</span>
          <strong class="stat-value stat-value--accent">{{ extracted.length }}</strong>
        </div>
        <div class="stat-card stat-card--tip">
          <span class="stat-label">补齐规则</span>
          <span class="stat-tip">每页面最多展示 3 条，人工维护优先，数量不足时由 AI 自动提炼补齐</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="settings-tabs">
        <!-- ==================== 快捷问题（人工维护） ==================== -->
        <el-tab-pane label="快捷问题" name="questions">
          <div class="table-toolbar">
            <div class="toolbar-left">
              <el-select
                v-model="filters.pageRoute"
                clearable
                filterable
                allow-create
                placeholder="归属页面"
                class="toolbar-control toolbar-control--md"
              >
                <el-option label="全局（所有页面）" value="" />
                <el-option v-for="r in pageRouteOptions" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
              <el-select v-model="filters.status" clearable placeholder="状态" class="toolbar-control toolbar-control--sm">
                <el-option label="全部状态" value="" />
                <el-option label="已启用" value="enabled" />
                <el-option label="已停用" value="disabled" />
              </el-select>
              <el-select v-model="filters.category" clearable placeholder="来源" class="toolbar-control toolbar-control--sm">
                <el-option label="全部来源" value="" />
                <el-option label="人工维护" value="manual_curated" />
                <el-option label="AI 自动提炼" value="auto_extracted" />
                <el-option label="AI 推荐待采纳" value="ai_suggested" />
              </el-select>
            </div>
            <div class="toolbar-right">
              <AppButton type="primary" @click="openCreateDialog">
                <el-icon><Plus /></el-icon>
                新增快捷问题
              </AppButton>
            </div>
          </div>

          <el-table
            :data="filteredQuestions"
            border
            v-loading="loading"
            :cell-style="{ textAlign: 'center' }"
            :header-cell-style="{ textAlign: 'center' }"
            class="settings-table"
          >
            <el-table-column label="问题文本" min-width="260">
              <template #default="{ row }">
                <div class="question-cell">
                  <span class="question-text">{{ row.questionText }}</span>
                  <el-tag v-if="row.category === 'manual_curated'" size="small" type="success" effect="light" round>人工</el-tag>
                  <el-tag v-else-if="row.category === 'auto_extracted'" size="small" type="warning" effect="light" round>AI 提炼</el-tag>
                  <el-tag v-else size="small" type="info" effect="light" round>待采纳</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="归属页面" min-width="150">
              <template #default="{ row }">
                <span v-if="row.pageRoute" class="page-route-chip">{{ pageRouteLabel(row.pageRoute) }}</span>
                <span v-else class="text-secondary">全局</span>
              </template>
            </el-table-column>
            <el-table-column label="权重" width="90">
              <template #default="{ row }">
                <span class="weight-badge">{{ row.weight }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column label="命中次数" width="100">
              <template #default="{ row }">
                <span class="hit-count">{{ row.hitCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="启停状态" width="100">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status === 'enabled'"
                  size="small"
                  @change="(val: boolean | string | number) => handleToggleStatus(row, val)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="EditPen" @click="openEditDialog(row)" />
                <el-button link type="danger" :icon="Delete" @click="handleDelete(row)" />
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              :total="filteredQuestions.length"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
            />
          </div>
        </el-tab-pane>

        <!-- ==================== AI 提炼建议 ==================== -->
        <el-tab-pane label="AI 提炼建议" name="extracted">
          <div class="table-toolbar">
            <div class="toolbar-left">
              <span class="toolbar-hint">统计近</span>
              <el-select v-model="extractedParams.windowDays" class="toolbar-control toolbar-control--sm">
                <el-option label="30 天" :value="30" />
                <el-option label="60 天" :value="60" />
                <el-option label="90 天" :value="90" />
              </el-select>
              <span class="toolbar-hint">内提问频率 ≥</span>
              <el-input-number
                v-model="extractedParams.minFrequency"
                :min="1"
                :max="100"
                controls-position="right"
                class="toolbar-control toolbar-control--num"
              />
              <span class="toolbar-hint">次的用户问题</span>
            </div>
            <div class="toolbar-right">
              <AppButton plain @click="loadExtracted">
                <el-icon><Refresh /></el-icon>
                刷新
              </AppButton>
            </div>
          </div>

          <div class="extracted-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>基于用户问答埋点自动提炼高频问题，点击「采纳」后转入人工维护并立即生效于前台展示</span>
          </div>

          <el-table
            :data="extracted"
            border
            v-loading="extractedLoading"
            :cell-style="{ textAlign: 'center' }"
            :header-cell-style="{ textAlign: 'center' }"
            class="settings-table"
          >
            <el-table-column label="问题文本" min-width="260">
              <template #default="{ row }">
                <span class="question-text">{{ row.questionText }}</span>
              </template>
            </el-table-column>
            <el-table-column label="归属页面" min-width="140">
              <template #default="{ row }">
                <span class="page-route-chip">{{ pageRouteLabel(row.pageRoute) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="提问频率" width="100">
              <template #default="{ row }">
                <span class="frequency-badge">{{ row.frequency }} 次</span>
              </template>
            </el-table-column>
            <el-table-column label="平均评分" width="100">
              <template #default="{ row }">
                <span :class="['rating-text', ratingTone(row.avgRating)]">{{ row.avgRating?.toFixed(1) ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="AI 置信度" width="160">
              <template #default="{ row }">
                <div class="confidence-cell">
                  <el-progress
                    :percentage="Math.round((row.aiConfidence ?? 0) * 100)"
                    :stroke-width="6"
                    :color="confidenceColor(row.aiConfidence)"
                    class="confidence-bar"
                  />
                  <span class="confidence-num">{{ ((row.aiConfidence ?? 0) * 100).toFixed(0) }}%</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="信息丰富度" width="110">
              <template #default="{ row }">
                <el-tag :type="infoLevelType(row.infoLevel)" size="small" effect="light" round>
                  {{ row.infoLevel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近提问" min-width="150">
              <template #default="{ row }">
                {{ row.lastAskedAt ? formatDateTime(row.lastAskedAt) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <AppButton type="success" size="small" @click="handleAdopt(row)">
                  <el-icon><Check /></el-icon>
                  采纳
                </AppButton>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!extractedLoading && extracted.length === 0"
            description="暂无可提炼的高频问题，用户提问数据积累后会自动出现"
            :image-size="96"
          />
        </el-tab-pane>
      </el-tabs>

      <!-- 新增/编辑对话框 -->
      <el-dialog
        v-model="dialog.visible"
        :title="dialog.isEdit ? '编辑快捷问题' : '新增快捷问题'"
        width="520px"
        destroy-on-close
        class="question-dialog"
      >
        <el-form ref="formRef" :model="dialog.form" :rules="formRules" label-width="90px">
          <el-form-item label="问题文本" prop="questionText">
            <el-input
              v-model="dialog.form.questionText"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="例如：如何新建一个需求？"
            />
          </el-form-item>
          <el-form-item label="归属页面" prop="pageRoute">
            <el-select
              v-model="dialog.form.pageRoute"
              clearable
              filterable
              allow-create
              placeholder="留空表示全局展示"
              style="width: 100%"
            >
              <el-option label="全局（所有页面）" value="" />
              <el-option v-for="r in pageRouteOptions" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
            <div class="form-tip">留空则在所有页面展示；填写路由名则仅在该页面展示</div>
          </el-form-item>
          <el-form-item label="权重" prop="weight">
            <el-input-number v-model="dialog.form.weight" :min="1" :max="100" controls-position="right" style="width: 140px" />
            <div class="form-tip">数值越大越靠前（1-100，默认 50）</div>
          </el-form-item>
          <el-form-item label="排序" prop="sortOrder">
            <el-input-number v-model="dialog.form.sortOrder" :min="0" :max="999" controls-position="right" style="width: 140px" />
            <div class="form-tip">同权重时数字小的优先（默认 0）</div>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="dialog.form.status">
              <el-radio value="enabled">启用</el-radio>
              <el-radio value="disabled">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="dialog.visible = false">取消</el-button>
            <AppButton type="primary" :loading="dialog.saving" @click="handleSave">保存</AppButton>
          </div>
        </template>
      </el-dialog>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Check,
  Delete,
  EditPen,
  InfoFilled,
  Plus,
  Refresh,
} from '@element-plus/icons-vue'
import AppButton from '@/components/common/AppButton.vue'
import PageContainer from '@/components/common/PageContainer.vue'
import {
  listAllQuickQuestions,
  createQuickQuestion,
  updateQuickQuestion,
  deleteQuickQuestion,
  toggleQuickQuestionStatus,
  getExtractedQuestions,
  adoptAiSuggestion,
  type QuickQuestion,
  type ExtractedQuestion,
} from '@/api/modules/assistant'

// ── 页面路由名选项（归属页面的下拉候选，允许自定义输入） ──
const pageRouteOptions = [
  { value: 'Home', label: '仪表盘' },
  { value: 'Requirements', label: '需求管理' },
  { value: 'Iterations', label: '迭代管理' },
  { value: 'BitableList', label: '多维表格' },
  { value: 'Documents', label: '文档中心' },
  { value: 'KnowledgeBases', label: '知识库管理' },
  { value: 'Notifications', label: '通知中心' },
  { value: 'WorkflowConfig', label: '工作流配置' },
  { value: 'SettingsProjects', label: '项目管理' },
  { value: 'SettingsUsers', label: '用户管理' },
  { value: 'SettingsRoles', label: '角色管理' },
  { value: 'SettingsRequirements', label: '需求配置' },
  { value: 'LlmConfig', label: '模型配置' },
  { value: 'AssistantSettings', label: 'AI 助手设置' },
]

function pageRouteLabel(route: string): string {
  return pageRouteOptions.find((r) => r.value === route)?.label ?? route
}

// ── 状态与数据 ──
const activeTab = ref('questions')
const loading = ref(false)
const extractedLoading = ref(false)
const questions = ref<QuickQuestion[]>([])
const extracted = ref<ExtractedQuestion[]>([])

const filters = reactive({
  pageRoute: '',
  status: '',
  category: '',
})
const extractedParams = reactive({
  windowDays: 30,
  minFrequency: 5,
})
const pagination = reactive({ page: 1, size: 10 })

const enabledCount = computed(() => questions.value.filter((q) => q.status === 'enabled').length)

const filteredQuestions = computed(() => {
  return questions.value.filter((q) => {
    if (filters.pageRoute && q.pageRoute !== filters.pageRoute) return false
    if (filters.status && q.status !== filters.status) return false
    if (filters.category && q.category !== filters.category) return false
    return true
  })
})

// ── 加载 ──
async function loadQuestions() {
  loading.value = true
  try {
    const res = await listAllQuickQuestions()
    questions.value = res.data ?? []
  } catch {
    ElMessage.error('快捷问题列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadExtracted() {
  extractedLoading.value = true
  try {
    const res = await getExtractedQuestions(extractedParams.windowDays, extractedParams.minFrequency)
    extracted.value = res.data ?? []
  } catch {
    ElMessage.error('AI 提炼建议加载失败')
  } finally {
    extractedLoading.value = false
  }
}

// ── 新增 / 编辑 ──
const dialog = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editingId: 0,
  form: {
    questionText: '',
    pageRoute: '',
    weight: 50,
    sortOrder: 0,
    status: 'enabled' as 'enabled' | 'disabled',
  },
})

const formRef = ref<FormInstance>()
const formRules: FormRules = {
  questionText: [
    { required: true, message: '请输入问题文本', trigger: 'blur' },
    { min: 2, max: 500, message: '长度需在 2~500 字符之间', trigger: 'blur' },
  ],
}

function resetDialogForm() {
  dialog.form = {
    questionText: '',
    pageRoute: '',
    weight: 50,
    sortOrder: 0,
    status: 'enabled',
  }
}

function openCreateDialog() {
  dialog.isEdit = false
  dialog.editingId = 0
  resetDialogForm()
  dialog.visible = true
}

function openEditDialog(row: QuickQuestion) {
  dialog.isEdit = true
  dialog.editingId = row.id
  dialog.form = {
    questionText: row.questionText,
    pageRoute: row.pageRoute ?? '',
    weight: row.weight,
    sortOrder: row.sortOrder,
    status: row.status === 'disabled' ? 'disabled' : 'enabled',
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
    const payload = {
      questionText: dialog.form.questionText.trim(),
      pageRoute: dialog.form.pageRoute || null,
      weight: dialog.form.weight,
      sortOrder: dialog.form.sortOrder,
      status: dialog.form.status,
    }
    if (dialog.isEdit) {
      await updateQuickQuestion(dialog.editingId, payload)
      ElMessage.success('快捷问题已更新')
    } else {
      await createQuickQuestion(payload)
      ElMessage.success('快捷问题已创建')
    }
    dialog.visible = false
    await loadQuestions()
  } catch {
    ElMessage.error(dialog.isEdit ? '更新失败' : '创建失败')
  } finally {
    dialog.saving = false
  }
}

// ── 启停 / 删除 ──
async function handleToggleStatus(row: QuickQuestion, val: boolean | string | number) {
  const next = val ? 'enabled' : 'disabled'
  try {
    await toggleQuickQuestionStatus(row.id, next)
    row.status = next
    ElMessage.success(next === 'enabled' ? '已启用' : '已停用')
  } catch {
    ElMessage.error('状态切换失败')
  }
}

async function handleDelete(row: QuickQuestion) {
  try {
    await ElMessageBox.confirm(`确定删除快捷问题「${row.questionText}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteQuickQuestion(row.id)
    ElMessage.success('已删除')
    await loadQuestions()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ── 采纳 AI 建议 ──
async function handleAdopt(row: ExtractedQuestion) {
  try {
    await ElMessageBox.confirm(
      `采纳「${row.questionText}」为人工维护问题？采纳后立即生效于前台展示。`,
      '采纳 AI 建议',
      {
        type: 'info',
        confirmButtonText: '采纳',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  try {
    await adoptAiSuggestion({
      questionText: row.questionText,
      pageRoute: row.pageRoute || null,
      questionHash: row.questionHash,
    })
    ElMessage.success('已采纳，转入人工维护')
    await loadExtracted()
    await loadQuestions()
  } catch {
    ElMessage.error('采纳失败')
  }
}

// ── 展示辅助 ──
function ratingTone(rating: number | undefined): string {
  if (rating == null) return 'rating-text--none'
  if (rating >= 4) return 'rating-text--good'
  if (rating >= 3) return 'rating-text--mid'
  return 'rating-text--low'
}

function confidenceColor(conf: number | undefined): string {
  const c = conf ?? 0
  if (c >= 0.7) return '#10b981'
  if (c >= 0.5) return '#f59e0b'
  return '#94a3b8'
}

function infoLevelType(level: string): 'success' | 'warning' | 'info' {
  if (level === '丰富') return 'success'
  if (level === '中等') return 'warning'
  return 'info'
}

function formatDateTime(value: string): string {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadQuestions()
  loadExtracted()
})
</script>

<style scoped lang="scss">
.assistant-settings {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* ── 统计行 ── */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-sm);
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #fff;
}

.stat-card--tip {
  background: #f5f7fa;
  border-style: dashed;
}

.stat-label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-text-primary);

  &--success {
    color: var(--color-success);
  }

  &--accent {
    color: var(--color-accent);
  }
}

.stat-tip {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

/* ── Tabs ── */
.settings-tabs {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 0 var(--spacing-lg) var(--spacing-lg);
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.settings-tabs :deep(.el-tabs__content) {
  padding-top: var(--spacing-md);
}

/* ── 工具栏 ── */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  margin-bottom: var(--spacing-md);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.toolbar-control {
  &--sm {
    width: 130px;
  }

  &--md {
    width: 200px;
  }

  &--num {
    width: 110px;
  }
}

.toolbar-hint {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

/* ── 表格单元格 ── */
.settings-table {
  width: 100%;
}

.question-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.question-text {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
  text-align: left;
}

.page-route-chip {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: var(--color-accent);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.weight-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  background: #f5f7fa;
  color: var(--color-text-primary);
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.hit-count {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.frequency-badge {
  font-weight: 600;
  color: var(--color-warning);
  font-variant-numeric: tabular-nums;
}

.rating-text {
  font-weight: 600;
  font-variant-numeric: tabular-nums;

  &--good {
    color: var(--color-success);
  }

  &--mid {
    color: var(--color-warning);
  }

  &--low,
  &--none {
    color: var(--color-text-placeholder);
  }
}

.confidence-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confidence-bar {
  flex: 1;
}

.confidence-num {
  min-width: 38px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

/* ── 分页 ── */
.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: var(--spacing-md) 0 0;
}

/* ── 提炼提示条 ── */
.extracted-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: var(--spacing-md);
  border-radius: var(--radius-md);
  background: #eff6ff;
  color: #1d4ed8;
  font-size: var(--font-size-sm);
}

/* ── 表单 ── */
.form-tip {
  width: 100%;
  margin-top: 4px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  line-height: 1.4;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.text-secondary {
  color: var(--color-text-secondary);
}

@media (max-width: 900px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
