import request from '@/api/request'

export interface FlowTransitionRequest {
  requirementId: number
  toNodeId: string
  action?: string
  comment?: string
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
  }
}
