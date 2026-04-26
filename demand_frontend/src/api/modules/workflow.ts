import request from '@/api/request'
import type { ApiResponse } from '@/types/api'
import type { WorkflowState, WorkflowTransition, WorkflowVersion, TransitionRequest, TransitionResponse } from '@/types/workflow'

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
