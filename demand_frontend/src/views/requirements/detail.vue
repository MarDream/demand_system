<template>
  <PageContainer variant="card" :breadcrumb="false">
    <div v-loading="loading" class="detail-page">
      <template v-if="detail">
        <!-- Header -->
        <div class="detail-header">
          <div class="header-left">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ name: 'Requirements' }">需求管理</el-breadcrumb-item>
              <el-breadcrumb-item>需求详情</el-breadcrumb-item>
            </el-breadcrumb>
            <h2 class="detail-title">
              {{ detail.title }}
              <el-tag :type="statusTagType(detail.status)" size="small" style="margin-left: 12px">
                {{ detail.status }}
              </el-tag>
            </h2>
          </div>
          <div class="header-actions">
            <el-button @click="handleEdit">编辑</el-button>
            <el-button type="success" @click="handleSplit">拆分子需求</el-button>
            <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete">
              <template #reference>
                <el-button type="danger">删除</el-button>
              </template>
            </el-popconfirm>
            <el-select
              v-model="selectedTransitionTargetId"
              :disabled="transitionLoading || transitionOptions.length === 0"
              :placeholder="transitionOptions.length > 0 ? '选择目标状态' : '当前无可执行流转'"
              style="width: 140px; margin-right: 8px"
            >
              <el-option
                v-for="transition in transitionOptions"
                :key="transitionOptionKey(transition)"
                :label="transitionOptionLabel(transition)"
                :value="transitionOptionValue(transition)"
              />
            </el-select>
            <el-button
              type="primary"
              :loading="transitionLoading"
              :disabled="transitionOptions.length === 0"
              @click="handleStatusTransition"
            >
              执行流转
            </el-button>
            <el-button
              v-if="usingUnifiedEngine && workflowRuntime.canRollback"
              :loading="transitionLoading"
              @click="handleRollback"
            >
              回退
            </el-button>
            <el-button
              v-if="usingUnifiedEngine && workflowRuntime.canCancel"
              type="warning"
              :loading="transitionLoading"
              @click="handleCancel"
            >
              取消
            </el-button>
          </div>
        </div>

        <!-- Tabs -->
        <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="所属项目">{{ projectLabel(detail.projectId) }}</el-descriptions-item>
            <el-descriptions-item label="需求类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag :type="priorityTagType(detail.priority)">{{ priorityLabel(detail.priority) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.assigneeName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.creatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="所属迭代">{{ detail.iterationId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="截止日期">{{ detail.dueDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="估算工时">{{ detail.estimatedHours ? detail.estimatedHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="实际工时">{{ detail.actualHours ? detail.actualHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="附件" :span="2">
              <div v-if="detail.attachments?.length" class="attachment-list">
                <div v-for="attachment in detail.attachments" :key="attachment.fileId || attachment.url" class="attachment-item">
                  <el-button link type="primary" @click="handleAttachmentDownload(attachment)">{{ attachment.name }}</el-button>
                  <span class="attachment-meta">
                    {{ formatAttachmentMeta(attachment) }}
                  </span>
                </div>
              </div>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              <div v-if="detail.description" class="rich-content" v-html="detail.description"></div>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 子需求列表 -->
          <div class="children-section">
            <div class="section-header">
              <h3>子需求（{{ children.length }} 个）</h3>
              <el-button type="primary" size="small" @click="handleSplit">+ 拆分子需求</el-button>
            </div>
            <el-table v-if="children.length > 0" :data="children" border size="small">
              <el-table-column label="ID" width="60" align="center">
                <template #default="{ row }">{{ row.id }}</template>
              </el-table-column>
              <el-table-column label="标题" min-width="200">
                <template #default="{ row }">
                  <el-link type="primary" @click="router.push({ name: 'RequirementDetail', params: { id: row.id } })">
                    {{ row.title }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="80" align="center">
                <template #default="{ row }">{{ typeLabel(row.type) }}</template>
              </el-table-column>
              <el-table-column label="优先级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="priorityTagType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无子需求，点击上方按钮进行拆分" :image-size="60" />
          </div>
        </el-tab-pane>

        <!-- 变更历史 -->
        <el-tab-pane label="变更历史" name="history">
          <el-timeline v-if="history.length > 0">
            <el-timeline-item
              v-for="item in history"
              :key="item.id"
              :timestamp="formatDate(item.createdAt)"
              placement="top"
            >
              <el-card>
                <p><strong>{{ item.operatorName || '系统' }}</strong></p>
                <p>
                  <span>{{ item.fieldName }}: </span>
                  <span class="old-value">{{ item.oldValue || '空' }}</span>
                  <span> -> </span>
                  <span class="new-value">{{ item.newValue || '空' }}</span>
                </p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无变更历史" />
        </el-tab-pane>

        <!-- 关联需求 -->
        <el-tab-pane label="关联需求" name="relations">
          <el-table :data="relatedRequirements" border>
            <el-table-column label="需求ID" width="80" align="center">
              <template #default="{ row }">{{ row.id }}</template>
            </el-table-column>
            <el-table-column label="需求标题" min-width="200">
              <template #default="{ row }">
                <el-link type="primary" @click="router.push({ name: 'RequirementDetail', params: { id: row.id } })">
                  {{ row.title }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ typeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="priorityTagType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag>{{ row.relationType || '关联' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="relatedRequirements.length === 0" description="暂无关联需求" />
        </el-tab-pane>

        <!-- 评论 -->
        <el-tab-pane label="评论" name="comments">
          <div class="comment-section">
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="4"
              placeholder="输入评论内容..."
              maxlength="500"
              show-word-limit
            />
            <div class="comment-actions">
              <el-button type="primary" :disabled="!commentText.trim()" @click="handleComment">
                提交评论
              </el-button>
            </div>
          </div>
          <el-empty v-if="comments.length === 0" description="暂无评论" />
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <el-avatar :size="32">{{ comment.userName?.charAt(0) || 'U' }}</el-avatar>
            <div class="comment-content">
              <div class="comment-header">
                <strong>{{ comment.userName || '用户' }}</strong>
                <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
              </div>
              <p>{{ comment.content }}</p>
            </div>
          </div>
        </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { requirementApi, projectApi } from '@/api'
import { downloadRequirementAttachment } from '@/api/modules/file'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { workflowEngineApi, type AvailableTransition, type WorkflowAvailableActions } from '@/api/modules/workflow-engine'
import { executeTransition, getAvailableTransitions, getWorkflowStates } from '@/api/modules/workflow'
import type { Requirement, RequirementAttachment, RequirementHistory, RequirementUpdate } from '@/types/requirement'
import type { WorkflowState, WorkflowTransition } from '@/types/workflow'
import { normalizeText, formatDate } from '@/utils/format'
import PageContainer from '@/components/common/PageContainer.vue'

const route = useRoute()
const router = useRouter()

const id = Number(route.params.id)
const loading = ref(false)
const detail = ref<Requirement | null>(null)
const history = ref<RequirementHistory[]>([])
const relatedRequirements = ref<any[]>([])
const comments = ref<any[]>([])
const children = ref<any[]>([])
const activeTab = ref('basic')
const commentText = ref('')
const projectName = ref<string>('')
const typeMap = ref<Record<string, string>>({})
const priorityMap = ref<Record<string, string>>({})
const workflowStates = ref<WorkflowState[]>([])
const availableTransitions = ref<WorkflowTransition[]>([])
const workflowRuntime = ref<WorkflowAvailableActions>({
  canTransition: false,
  canRollback: false,
  canCancel: false,
  transitions: [],
})
const usingUnifiedEngine = ref(false)
const selectedTransitionTargetId = ref<string | number | null>(null)
const transitionLoading = ref(false)

function resetWorkflowMeta() {
  workflowStates.value = []
  availableTransitions.value = []
  workflowRuntime.value = {
    canTransition: false,
    canRollback: false,
    canCancel: false,
    transitions: [],
  }
  usingUnifiedEngine.value = false
  selectedTransitionTargetId.value = null
}

// Fetch detail
async function fetchDetail() {
  loading.value = true
  try {
    const res = await requirementApi.getRequirementById(id)
    detail.value = res
    await Promise.all([loadProjectName(), loadWorkflowMeta()])
  } catch {
    ElMessage.error('获取需求详情失败')
  } finally {
    loading.value = false
  }
}

async function loadProjectName() {
  if (!detail.value?.projectId) {
    projectName.value = ''
    return
  }
  try {
    const res = await projectApi.getProjectById(detail.value.projectId) as any
    projectName.value = res?.name || ''
  } catch {
    projectName.value = ''
  }
}

async function loadConfig() {
  try {
    const [typesRes, prioritiesRes] = await Promise.all([
      requirementConfigApi.listTypes(),
      requirementConfigApi.listPriorities(),
    ])
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any)?.data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any)?.data || []
    typeMap.value = Object.fromEntries(typeList.map((t: any) => [t.code, normalizeText(t.name)]))
    priorityMap.value = Object.fromEntries(priorityList.map((p: any) => [p.code, normalizeText(p.name)]))
  } catch {
    typeMap.value = {}
    priorityMap.value = {}
  }
}

async function loadWorkflowMeta() {
  resetWorkflowMeta()

  if (detail.value?.workflowInstanceId) {
    try {
      const actions = await workflowEngineApi.getAvailableActions(id)
      workflowRuntime.value = actions
      usingUnifiedEngine.value = true
      selectedTransitionTargetId.value = actions.transitions[0]?.toNodeId ?? null
      return
    } catch {
      usingUnifiedEngine.value = false
    }
  }

  if (!detail.value?.projectId) return

  try {
    const [statesRes, transitionsRes] = await Promise.all([
      getWorkflowStates(detail.value.projectId),
      getAvailableTransitions(id),
    ])

    workflowStates.value = Array.isArray(statesRes) ? statesRes : []
    availableTransitions.value = Array.isArray(transitionsRes) ? transitionsRes : []

    const exists = availableTransitions.value.some(
      (transition) => transition.toStateId === selectedTransitionTargetId.value,
    )
    if (!exists) {
      selectedTransitionTargetId.value = availableTransitions.value[0]?.toStateId ?? null
    }
  } catch {
    resetWorkflowMeta()
  }
}

// Fetch history
async function fetchHistory() {
  try {
    if (detail.value?.workflowInstanceId) {
      const transitions = await workflowEngineApi.getTransitionHistory(id)
      history.value = Array.isArray(transitions)
        ? transitions.map((item: any) => ({
            id: item.id,
            requirementId: item.requirementId,
            operatorId: item.operatorId,
            operatorName: item.operatorName,
            fieldName: item.action === 'rollback' ? '流程回退' : item.action === 'cancel' ? '流程取消' : '流程流转',
            oldValue: item.fromNodeName || item.fromNodeId || '开始',
            newValue: item.toNodeName || item.toNodeId || (item.durationDisplay ? `已处理（${item.durationDisplay}）` : '完成'),
            createdAt: item.createdAt,
          }))
        : []
      return
    }

    const res = await requirementApi.getRequirementHistory(id)
    history.value = Array.isArray(res) ? res : []
  } catch {
    history.value = []
  }
}

// Fetch children
async function fetchChildren() {
  try {
    const res = await requirementApi.getRequirementChildren(id)
    children.value = res
  } catch {
    // children fetch failure is non-critical
  }
}

// Tag type helpers
function priorityTagType(priority: string): string {
  const map: Record<string, string> = { P0: 'danger', P1: 'warning', P2: 'info', P3: 'success' }
  return map[priority] || 'info'
}

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待评审': 'info', '评审中': 'warning', '已通过': 'success',
    '开发中': 'primary', '测试中': 'info', '已上线': 'success', '已验收': 'success',
  }
  return map[status] || 'info'
}

function typeLabel(code: string) {
  return typeMap.value[code] || code || '-'
}

function priorityLabel(code: string) {
  return priorityMap.value[code] || code || '-'
}

function projectLabel(projectId: number) {
  return projectName.value || String(projectId || '-')
}

function formatAttachmentMeta(attachment: RequirementAttachment) {
  const parts: string[] = []
  if (attachment.size) {
    if (attachment.size < 1024) {
      parts.push(`${attachment.size} B`)
    } else if (attachment.size < 1024 * 1024) {
      parts.push(`${(attachment.size / 1024).toFixed(1)} KB`)
    } else {
      parts.push(`${(attachment.size / 1024 / 1024).toFixed(1)} MB`)
    }
  }
  if (attachment.contentType) {
    parts.push(attachment.contentType)
  }
  return parts.join(' / ')
}

async function handleAttachmentDownload(attachment: RequirementAttachment) {
  try {
    await downloadRequirementAttachment(attachment)
  } catch {
    ElMessage.error('附件下载失败')
  }
}

function workflowStateName(stateId: number) {
  return workflowStates.value.find((state) => state.id === stateId)?.name || `状态${stateId}`
}

type TransitionOption = WorkflowTransition | AvailableTransition

const transitionOptions = computed<TransitionOption[]>(() => (
  usingUnifiedEngine.value ? workflowRuntime.value.transitions : availableTransitions.value
))

function transitionOptionKey(transition: TransitionOption) {
  return 'toNodeId' in transition ? transition.toNodeId : (transition.id || transition.toStateId)
}

function transitionOptionValue(transition: TransitionOption) {
  return 'toNodeId' in transition ? transition.toNodeId : transition.toStateId
}

function transitionOptionLabel(transition: TransitionOption) {
  if ('toNodeId' in transition) {
    return transition.label || transition.toNodeName
  }
  return transition.label || workflowStateName(transition.toStateId)
}

// Handlers
function handleEdit() {
  router.push({ name: 'RequirementCreate', query: { id } })
}

function handleSplit() {
  router.push({ name: 'RequirementCreate', query: { parentId: id } })
}

async function handleDelete() {
  try {
    await requirementApi.deleteRequirement(id)
    ElMessage.success('删除成功')
    router.push({ name: 'Requirements' })
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleStatusTransition() {
  if (!selectedTransitionTargetId.value) {
    ElMessage.warning('请选择目标状态')
    return
  }

  transitionLoading.value = true
  try {
    if (usingUnifiedEngine.value) {
      await workflowEngineApi.transition({
        requirementId: id,
        toNodeId: String(selectedTransitionTargetId.value),
        action: 'submit',
      })
    } else {
      await executeTransition(id, { targetStateId: Number(selectedTransitionTargetId.value) })
    }
    ElMessage.success('状态流转成功')
    selectedTransitionTargetId.value = null
    await Promise.all([fetchDetail(), fetchHistory()])
  } catch {
    ElMessage.error('状态流转失败')
  } finally {
    transitionLoading.value = false
  }
}

async function handleRollback() {
  await confirmAndExecute(
    '请输入回退说明（可选）', '回退需求', '确认回退', '请输入回退说明',
    (v) => workflowEngineApi.rollback(id, v || undefined),
    '回退成功', '回退失败'
  )
}

async function handleCancel() {
  await confirmAndExecute(
    '请输入取消原因', '取消需求', '确认取消', '取消原因必填',
    (v) => workflowEngineApi.cancel(id, v),
    '需求已取消', '取消失败',
    (input) => !!input?.trim() || '请输入取消原因'
  )
}

async function confirmAndExecute(
  message: string, title: string, confirmText: string, placeholder: string,
  action: (value: string) => Promise<any>,
  successMsg: string, errorMsg: string,
  validator?: (input: string) => string | boolean
) {
  try {
    const opts: any = { confirmButtonText: confirmText, cancelButtonText: '取消', inputPlaceholder: placeholder }
    if (validator) opts.inputValidator = validator
    const { value } = await ElMessageBox.prompt(message, title, opts)
    transitionLoading.value = true
    await action(value)
    ElMessage.success(successMsg)
    await Promise.all([fetchDetail(), fetchHistory()])
  } catch (error: any) {
    if (error === 'cancel' || error?.action === 'cancel') return
    ElMessage.error(errorMsg)
  } finally {
    transitionLoading.value = false
  }
}

function handleComment() {
  if (!commentText.value.trim()) return
  ElMessage.info('评论功能开发中')
  commentText.value = ''
}

async function initializePage() {
  await Promise.all([loadConfig(), fetchDetail()])
  await Promise.all([
    fetchHistory(),
    fetchChildren(),
  ])
}

onMounted(() => {
  void initializePage()
})
</script>

<style scoped>
.detail-page {
  min-height: 200px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  flex: 1;
}

.detail-title {
  margin: 12px 0 0;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-tabs {
  margin-top: 16px;
}

.rich-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.rich-content :deep(p) {
  margin: 0 0 8px 0;
}

.old-value {
  color: #909399;
  text-decoration: line-through;
}

.new-value {
  color: #409eff;
  font-weight: 500;
}

.comment-section {
  margin-bottom: 24px;
}

.comment-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.children-section {
  margin-top: 24px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.attachment-meta {
  color: #909399;
  font-size: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
</style>
