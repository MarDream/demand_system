import request from '@/api/request'
import type { RequirementAttachment } from '@/types/requirement'

export interface FlowTransitionRequest {
  requirementId: number
  toNodeId: string
  projectId?: number | null
  action?: string
  comment?: string
  rating?: number | null
  lockVersion?: number | null
  attachments?: RequirementAttachment[]
}

export interface TransitionAssigneeCandidate {
  id: number
  name: string
}

export interface AvailableTransition {
  toNodeId: string
  toNodeName: string
  label?: string | null
  bindStatusCode?: string | null
  bindStatusName?: string | null
  projectRequired?: boolean | null
  assigneeType?: string | null
  assigneeTypeName?: string | null
  assigneeDisplayName?: string | null
  assigneeCandidates?: TransitionAssigneeCandidate[] | null
  defaultAssigneeId?: number | null
}

export interface WorkflowAvailableActions {
  canTransition: boolean
  canRollback: boolean
  canCancel: boolean
  currentNodeId?: string | null
  currentNodeName?: string | null
  currentNodeType?: string | null
  currentNodeStatusCode?: string | null
  currentNodeStatusName?: string | null
  transitions: AvailableTransition[]
  lockVersion?: number | null
  evaluationRequired?: boolean | null
  countersignEnabled?: boolean | null
  canCountersign?: boolean | null
  countersignPending?: boolean | null
  parallelActive?: boolean | null
  activeParallelBranchId?: number | null
  parallelBranches?: import('@/api/modules/workflow').ParallelBranch[] | null
  /** 修复 P2：当前节点是否要求必填审批意见 */
  currentNodeRequireComment?: boolean | null
  /** 当前节点是否要求流转时必须上传附件 */
  currentNodeRequireAttachment?: boolean | null
  /** 当前用户是否可编辑此需求（基于工作流节点权限判断） */
  canEdit?: boolean | null
  /** 当前用户是否可删除此需求（仅创建人或管理员） */
  canDelete?: boolean | null
  /** 当前用户是否可拆分子需求（与 canEdit 一致） */
  canSplit?: boolean | null
  /** 关联工作流版本当前是否处于启用状态（is_active=1）。
   *  false 时表示工作流被管理员停用，canTransition/canRollback/canCancel 都会是 false，
   *  前端应隐藏操作按钮并提示"工作流已停用"。 */
  workflowActive?: boolean | null
}

export interface TransitionVO {
  id: number
  instanceId: number
  requirementId: number
  fromNodeId: string | null
  fromNodeName: string | null
  toNodeId: string
  toNodeName: string
  operatorId: number
  operatorName: string | null
  action: string
  comment: string | null
  startedAt: string
  completedAt: string | null
  durationSeconds: number | null
  durationDisplay: string | null
  createdAt: string
}

export interface NodeStatus {
  id: number
  name: string
  code: string
  color: string | null
  sortOrder: number
  isStart: boolean
  isEnd: boolean
  isCancel: boolean
}

export interface SortItem {
  id: number
  sortOrder: number
}

export const workflowEngineApi = {
  initWorkflow(requirementId: number, workflowVersionId: number) {
    return request.post(`/v1/workflow-engine/init?requirementId=${requirementId}&workflowVersionId=${workflowVersionId}`)
  },

  transition(data: FlowTransitionRequest) {
    return request.post('/v1/workflow-engine/transition', data)
  },

  rollback(requirementId: number, comment?: string) {
    return request.post(`/v1/workflow-engine/rollback/${requirementId}`, null, {
      params: comment ? { comment } : undefined
    })
  },

  cancel(requirementId: number, comment?: string) {
    return request.post(`/v1/workflow-engine/cancel/${requirementId}`, null, {
      params: comment ? { comment } : undefined
    })
  },

  saveDraft(requirementId: number) {
    return request.post(`/v1/workflow-engine/draft/${requirementId}`)
  },

  getTransitionHistory(requirementId: number) {
    return request.get<TransitionVO[]>(`/v1/workflow-engine/transitions/${requirementId}`)
  },

  getAvailableActions(requirementId: number) {
    return request.get<WorkflowAvailableActions>(`/v1/workflow-engine/actions/${requirementId}`) as unknown as Promise<WorkflowAvailableActions>
  }
}

export const nodeStatusApi = {
  list() {
    return request.get<NodeStatus[]>('/v1/node-statuses')
  },

  create(data: Partial<NodeStatus>) {
    return request.post('/v1/node-statuses', data)
  },

  update(id: number, data: Partial<NodeStatus>) {
    return request.put(`/v1/node-statuses/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/v1/node-statuses/${id}`)
  },

  sort(items: SortItem[]) {
    return request.post<NodeStatus[]>('/v1/node-statuses/sort', items)
  }
}
