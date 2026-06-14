<template>
  <div class="detail-actions">
    <div class="header-actions">
      <div v-if="showCurrentNodeStatus" class="current-node-status">
        <span class="current-node-status__label">当前节点</span>
        <span class="current-node-status__value">{{ currentNodeDisplayName }}</span>
        <span class="current-node-status__divider">/</span>
        <span class="current-node-status__label">节点状态</span>
        <el-tag size="small" effect="plain" :type="statusTagType(currentNodeStatusName)">
          {{ currentNodeStatusName }}
        </el-tag>
      </div>
      <el-button v-if="canEditRequirement" type="primary" @click="$emit('edit')">编辑</el-button>
      <el-button v-if="canSplitRequirement" type="success" @click="$emit('split')">拆分子需求</el-button>
      <el-button v-if="canDeleteRequirement" type="danger">
        <el-popconfirm title="确定删除该需求吗？" @confirm="$emit('delete')">
          <template #reference>
            <el-button type="danger">删除</el-button>
          </template>
        </el-popconfirm>
      </el-button>
      <el-select
        v-model="localSelectedTransitionTargetId"
        :disabled="transitionLoading || transitionOptions.length === 0"
        :placeholder="transitionOptions.length > 0 ? '选择目标节点' : '当前无可执行操作'"
        style="width: 140px; margin-right: 8px"
      >
        <el-option
          v-for="transition in transitionOptions"
          :key="transitionOptionKey(transition)"
          :label="transitionOptionLabel(transition)"
          :value="transitionOptionValue(transition)"
        />
      </el-select>
      <el-select
        v-if="requiresProjectBinding"
        v-model="localBindingProjectId"
        filterable
        clearable
        placeholder="流转前绑定项目"
        style="width: 180px; margin-right: 8px"
      >
        <el-option
          v-for="project in bindableProjects"
          :key="project.id"
          :label="projectOptionLabel(project)"
          :value="project.id"
        />
      </el-select>
      <AppButton
        v-if="usingUnifiedEngine && workflowRuntime.canCountersign"
        type="warning"
        permission="button:requirement:submit"
        @click="$emit('openCountersign', workflowRuntime.currentNodeId || '')"
      >
        会签审批
      </AppButton>
      <el-select
        v-if="workflowRuntime.parallelActive && parallelBranches.length > 0"
        :model-value="workflowRuntime.activeParallelBranchId"
        placeholder="切换并行分支"
        style="width: 160px; margin-right: 8px"
        @change="$emit('switchBranch', $event)"
      >
        <el-option
          v-for="branch in parallelBranches"
          :key="branch.id"
          :label="branch.branchName + ' (' + parallelBranchStatusLabel(branch.status) + ')'"
          :value="branch.id"
          :disabled="branch.status === 'completed' || branch.status === 'skipped'"
        />
      </el-select>
      <AppButton
        type="primary"
        :loading="transitionLoading"
        :disabled="transitionOptions.length === 0 || (requiresProjectBinding && !localBindingProjectId)"
        permission="button:requirement:submit"
        @click="$emit('submit')"
      >
        提交审核
      </AppButton>
      <AppButton
        v-if="usingUnifiedEngine && workflowRuntime.canRollback"
        :loading="transitionLoading"
        permission="button:requirement:rollback"
        @click="$emit('rollback')"
      >
        驳回
      </AppButton>
      <AppButton
        v-if="usingUnifiedEngine && workflowRuntime.canCancel"
        type="warning"
        :loading="transitionLoading"
        permission="button:requirement:cancel"
        @click="$emit('cancel')"
      >
        取消
      </AppButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AvailableTransition, WorkflowAvailableActions } from '@/api/modules/workflow-engine'
import type { ParallelBranch } from '@/api/modules/workflow'

const props = defineProps<{
  workflowRuntime: WorkflowAvailableActions
  usingUnifiedEngine: boolean
  transitionOptions: AvailableTransition[]
  transitionLoading: boolean
  selectedTransitionTargetId: string | number | null
  bindingProjectId: number | null
  requiresProjectBinding: boolean
  parallelBranches: ParallelBranch[]
  currentNodeStatusName: string
  currentNodeDisplayName: string
  showCurrentNodeStatus: boolean
  bindableProjects: Array<{ id: number; name: string; status?: string | null; endDate?: string | null }>
  canEditRequirement?: boolean
  canSplitRequirement?: boolean
  canDeleteRequirement?: boolean
}>()

const emit = defineEmits<{
  'update:selectedTransitionTargetId': [value: string | number | null]
  'update:bindingProjectId': [value: number | null]
  edit: []
  split: []
  delete: []
  submit: []
  rollback: []
  cancel: []
  openCountersign: [nodeId: string]
  switchBranch: [branchId: number]
}>()

const localSelectedTransitionTargetId = computed({
  get: () => props.selectedTransitionTargetId,
  set: (val) => emit('update:selectedTransitionTargetId', val),
})

const localBindingProjectId = computed({
  get: () => props.bindingProjectId,
  set: (val) => emit('update:bindingProjectId', val),
})

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待分析': 'warning', '待确认': 'warning', '待评审': 'warning',
    '评审中': 'warning', '已通过': 'success', '开发中': 'primary', '测试中': 'info',
    '已上线': 'success', '已验收': 'success', '已取消': 'info', '已拒绝': 'danger',
    '打回': 'danger', '测试不通过': 'danger', '验收不通过': 'danger',
    PENDING_REVIEW: 'warning', REJECTED: 'danger', SENT_BACK: 'danger',
    TEST_FAILED: 'danger', ACCEPT_FAILED: 'danger',
  }
  return map[status] || 'info'
}

type TransitionOption = AvailableTransition

function transitionOptionKey(transition: TransitionOption) {
  return transition.toNodeId
}

function transitionOptionValue(transition: TransitionOption) {
  return transition.toNodeId
}

function transitionOptionLabel(transition: TransitionOption) {
  const baseLabel = transition.label || transition.toNodeName
  const projectLabel = transition.projectRequired ? ' [需绑定项目]' : ''
  return baseLabel + projectLabel
}

function projectOptionLabel(project: { name: string; status?: string | null; endDate?: string | null }) {
  if (project.status === 'expired') return project.name + '（已截止）'
  if (!project.endDate) return project.name
  if (new Date(project.endDate).getTime() < Date.now() - 24 * 60 * 60 * 1000) {
    return project.name + '（已截止）'
  }
  return project.name
}

function parallelBranchStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待处理',
    running: '进行中',
    completed: '已完成',
    skipped: '已跳过',
  }
  return map[status] || status
}
</script>

<style scoped>
.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 24px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.current-node-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  background: #f4faff;
}

.current-node-status__label {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.current-node-status__value {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 500;
}

.current-node-status__divider {
  color: #c0c4cc;
}
</style>
