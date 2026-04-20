import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export function getRelationList(requirementId: number) {
  return request.get<ApiResponse>(`/v1/requirements/${requirementId}/relations`)
}

export function createRelation(requirementId: number, data: { targetId: number; relationType: string }) {
  return request.post<ApiResponse>(`/v1/requirements/${requirementId}/relations`, data)
}

export function deleteRelation(requirementId: number, relId: number) {
  return request.delete<ApiResponse>(`/v1/requirements/${requirementId}/relations/${relId}`)
}
