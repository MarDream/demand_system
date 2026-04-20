import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Requirement, RequirementQuery, RequirementCreate, RequirementUpdate, RequirementHistory } from '@/types/requirement'

export function getRequirementList(params: RequirementQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements', { params }) as unknown as Promise<PageResult<Requirement>>
}

export function getRequirementById(id: number) {
  return request.get<ApiResponse<Requirement>>(`/v1/requirements/${id}`) as unknown as Promise<Requirement>
}

export function createRequirement(data: RequirementCreate) {
  return request.post<ApiResponse>('/v1/requirements', data) as unknown as Promise<void>
}

export function updateRequirement(id: number, data: RequirementUpdate) {
  return request.put<ApiResponse>(`/v1/requirements/${id}`, data) as unknown as Promise<void>
}

export function deleteRequirement(id: number) {
  return request.delete<ApiResponse>(`/v1/requirements/${id}`) as unknown as Promise<void>
}

export function restoreRequirement(id: number) {
  return request.post<ApiResponse>(`/v1/requirements/${id}/restore`) as unknown as Promise<void>
}

export function getRequirementHistory(id: number) {
  return request.get<ApiResponse<RequirementHistory[]>>(`/v1/requirements/${id}/history`) as unknown as Promise<RequirementHistory[]>
}

export function getRequirementChildren(parentId: number) {
  return request.get<ApiResponse<Requirement[]>>(`/v1/requirements/${parentId}/children`) as unknown as Promise<Requirement[]>
}
