import request from '@/api/request'
import type { ApiResponse } from '@/types/api'
import type { Iteration } from '@/types/iteration'

export function getIterationList(projectId: number) {
  return request.get<ApiResponse<Iteration[]>>(`/v1/projects/${projectId}/iterations`)
}

export function getIterationById(id: number) {
  return request.get<ApiResponse<Iteration>>(`/v1/iterations/${id}`)
}

export function createIteration(projectId: number, data: any) {
  return request.post<ApiResponse>(`/v1/projects/${projectId}/iterations`, data)
}

export function updateIteration(id: number, data: any) {
  return request.put<ApiResponse>(`/v1/iterations/${id}`, data)
}

export function deleteIteration(id: number) {
  return request.delete<ApiResponse>(`/v1/iterations/${id}`)
}

export function assignRequirements(iterationId: number, requirementIds: number[]) {
  return request.post<ApiResponse>(`/v1/iterations/${iterationId}/requirements`, { requirementIds })
}

export function getBurndownData(iterationId: number) {
  return request.get<ApiResponse>(`/v1/iterations/${iterationId}/burndown`)
}
