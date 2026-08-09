import request from '@/api/request'
import type { ApiResponse } from '@/types/api'
import type { WorkflowState, WorkflowTransition, WorkflowVersion, WorkflowHistory, TransitionRequest, TransitionResponse } from '@/types/workflow'

export function getWorkflowStates(projectId: number) {
  return request.get<ApiResponse<WorkflowState[]>>(`/v1/projects/${projectId}/workflow/states`)
}

export function createWorkflowState(projectId: number, data: Partial<WorkflowState>) {
  return request.post<ApiResponse<WorkflowState>>(`/v1/projects/${projectId}/workflow/states`, data)
}

export function updateWorkflowState(id: number, data: Partial<WorkflowState>) {
  return request.put<ApiResponse>(`/v1/workflow/states/${id}`, data)
}

export function deleteWorkflowState(id: number) {
  return request.delete<ApiResponse>(`/v1/workflow/states/${id}`)
}

export function getWorkflowTransitions(projectId: number) {
  return request.get<ApiResponse<WorkflowTransition[]>>(`/v1/projects/${projectId}/workflow/transitions`)
}

export function createWorkflowTransition(projectId: number, data: Partial<WorkflowTransition>) {
  return request.post<ApiResponse<WorkflowTransition>>(`/v1/projects/${projectId}/workflow/transitions`, data)
}

export function updateWorkflowTransition(id: number, data: Partial<WorkflowTransition>) {
  return request.put<ApiResponse>(`/v1/workflow/transitions/${id}`, data)
}

export function deleteWorkflowTransition(id: number) {
  return request.delete<ApiResponse>(`/v1/workflow/transitions/${id}`)
}

export function getWorkflowVersions(projectId: number) {
  return request.get<ApiResponse<WorkflowVersion[]>>(`/v1/projects/${projectId}/workflow/versions`)
}

export function getWorkflowVersionHistory(versionId: number) {
  return request.get<ApiResponse<WorkflowHistory[]>>(`/v1/workflow/versions/${versionId}/history`)
}

export function createWorkflowVersion(projectId: number, data: any) {
  return request.post<ApiResponse>(`/v1/projects/${projectId}/workflow/versions`, data)
}

export function activateWorkflowVersion(id: number, projectId: number) {
  return request.post<ApiResponse>(`/v1/workflow/versions/${id}/activate`, undefined, { params: { projectId } })
}

export function getAvailableTransitions(requirementId: number) {
  return request.get<ApiResponse<WorkflowTransition[]>>(`/v1/requirements/${requirementId}/available-transitions`)
}

export function executeTransition(requirementId: number, data: TransitionRequest) {
  return request.post<ApiResponse<TransitionResponse>>(`/v1/requirements/${requirementId}/transition`, data)
}

// 会签相关 API
export interface CountersignRecord {
  id: number
  instanceId: number
  nodeId: string
  approverId: number
  approverName?: string
  status: string
  rating?: number
  comment?: string
  approvedAt?: string
  createdAt: string
}

export interface CountersignSubmitDTO {
  requirementId: number
  nodeId: string
  status: 'approved' | 'rejected'
  rating?: number
  comment?: string
}

export function submitCountersignApproval(data: CountersignSubmitDTO) {
  return request.post<ApiResponse<void>>('/v1/workflow/countersign/submit', data) as unknown as Promise<void>
}

export function getCountersignRecords(requirementId: number, nodeId: string) {
  return request.get<ApiResponse<CountersignRecord[]>>('/v1/workflow/countersign/records', {
    params: { requirementId, nodeId }
  }) as unknown as Promise<CountersignRecord[]>
}

export function canCurrentUserCountersign(requirementId: number, nodeId: string) {
  return request.get<ApiResponse<boolean>>('/v1/workflow/countersign/can-countersign', {
    params: { requirementId, nodeId }
  }) as unknown as Promise<boolean>
}

// ==================== 工作流复制相关 API ====================

export interface WorkflowCopyRequest {
  newName: string
  newVersion?: string
  includeDescription?: boolean
  includeNodes?: boolean
  includeEdges?: boolean
  resetApprovers?: boolean
  resetFormFields?: boolean
  resetSensitiveData?: boolean
  customSensitiveFields?: string[]
  targetProjectId?: number
}

export interface WorkflowCopyResponse {
  workflowVersionId: number
  name: string
  version: string
  mode: 'sync' | 'async'
  message: string
  copiedNodeCount: number
  copiedEdgeCount: number
}

export interface WorkflowTemplateDTO {
  id: number
  name: string
  version: string
  description?: string
  isTemplate: boolean
  copyCount: number
  creatorId: number
  creatorName: string
  projectId: number
  projectName: string
  createdAt: string
  lastUsedAt?: string
  previewImage?: string
  nodeCount: number
  activationStatus: string
}

export interface WorkflowLineageDTO {
  id: number
  name: string
  version: string
  createdAt: string
  creatorName: string
  source?: WorkflowLineageDTO
  isCircular?: boolean
}

/**
 * 复制工作流版本
 */
export function copyWorkflow(versionId: number, data: WorkflowCopyRequest) {
  return request.post<ApiResponse<WorkflowCopyResponse>>(`/v1/workflows/versions/${versionId}/copy`, data)
}

/**
 * 获取工作流模板列表
 */
export function getWorkflowTemplates(params: {
  page?: number
  pageSize?: number
  keyword?: string
  includeMyWorkflows?: boolean
}) {
  return request.get<ApiResponse<{ records: WorkflowTemplateDTO[]; total: number }>>('/v1/workflows/templates', { params })
}

/**
 * 检查工作流名称是否冲突
 */
export function checkWorkflowNameConflict(name: string, projectId: number) {
  return request.get<ApiResponse<{ conflict: boolean; suggestedName?: string }>>('/v1/workflows/check-name', {
    params: { name, projectId }
  })
}

/**
 * 标记工作流为模板
 */
export function markWorkflowAsTemplate(versionId: number, isTemplate: boolean) {
  return request.post<ApiResponse<void>>(`/v1/workflows/versions/${versionId}/mark-as-template`, { isTemplate })
}

/**
 * 获取工作流溯源树
 */
export function getWorkflowLineage(versionId: number) {
  return request.get<ApiResponse<WorkflowLineageDTO>>(`/v1/workflows/versions/${versionId}/lineage`)
}

export interface ParallelBranch {
  id: number
  instanceId: number
  parallelNodeId: string
  branchNodeId: string
  branchName: string
  currentNodeId?: string | null
  status: string
  startedAt?: string | null
  completedAt?: string | null
  createdAt?: string
}

/**
 * 节点评分配置（对应 ADR-002 工作流节点评分功能）
 */
export interface RatingDimension {
  key: string
  name: string
  description?: string
  minLabel?: string
  maxLabel?: string
}

export interface NodeRatingConfig {
  enabled: boolean
  required: boolean
  evaluator?: string
  showInStatistics?: boolean
  dimensions: RatingDimension[]
}

export function getParallelBranches(requirementId: number) {
  return request.get<ApiResponse<ParallelBranch[]>>('/v1/workflow/parallel/branches', {
    params: { requirementId },
  }) as unknown as Promise<ParallelBranch[]>
}

export function switchParallelBranch(requirementId: number, branchId: number) {
  return request.post<ApiResponse<void>>('/v1/workflow/parallel/switch', null, {
    params: { requirementId, branchId },
  }) as unknown as Promise<void>
}
