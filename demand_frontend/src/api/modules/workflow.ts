import request from '@/api/request'
import type { ApiResponse } from '@/types/api'
import type { WorkflowState, WorkflowTransition, WorkflowVersion, TransitionRequest, TransitionResponse } from '@/types/workflow'

export function getWorkflowStates(projectId: number) {
  return request.get<ApiResponse<WorkflowState[]>>(`/v1/projects/${projectId}/workflow/states`)
}

export function getWorkflowTransitions(projectId: number) {
  return request.get<ApiResponse<WorkflowTransition[]>>(`/v1/projects/${projectId}/workflow/transitions`)
}

export function getWorkflowVersions(projectId: number) {
  return request.get<ApiResponse<WorkflowVersion[]>>(`/v1/projects/${projectId}/workflow/versions`)
}

export function createWorkflowVersion(projectId: number, data: any) {
  return request.post<ApiResponse>(`/v1/projects/${projectId}/workflow/versions`, data)
}

export function activateWorkflowVersion(id: number) {
  return request.post<ApiResponse>(`/v1/workflow/versions/${id}/activate`)
}

export function executeTransition(requirementId: number, data: TransitionRequest) {
  return request.post<ApiResponse<TransitionResponse>>(`/v1/requirements/${requirementId}/transition`, data)
}
