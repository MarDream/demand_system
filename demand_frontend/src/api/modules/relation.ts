import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export interface RelationItem {
  id: number
  sourceId: number
  targetId: number
  relationType: string
  targetTitle: string
  targetType?: string | null
  targetStatus?: string | null
  targetPriority?: string | null
}

export function getRelationList(requirementId: number) {
  return request.get<ApiResponse<RelationItem[]>>(`/v1/requirements/${requirementId}/relations`) as unknown as Promise<RelationItem[]>
}

export function createRelation(requirementId: number, data: { targetId: number; relationType: string }) {
  return request.post<ApiResponse>(`/v1/requirements/${requirementId}/relations`, data) as unknown as Promise<void>
}

export function deleteRelation(requirementId: number, relId: number) {
  return request.delete<ApiResponse>(`/v1/requirements/${requirementId}/relations/${relId}`) as unknown as Promise<void>
}
