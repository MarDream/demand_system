import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  NextNodeOption,
  Requirement,
  RequirementComment,
  RequirementCommentCreate,
  RequirementCreate,
  RequirementDraftCreate,
  RequirementDraftUpdate,
  RequirementHistory,
  RequirementMyListQuery,
  RequirementQuery,
  RequirementSubmit,
  RequirementUpdate,
} from '@/types/requirement'

export function getRequirementList(params: RequirementQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements', { params }) as unknown as Promise<PageResult<Requirement>>
}

export function getRequirementById(id: number) {
  return request.get<ApiResponse<Requirement>>(`/v1/requirements/${id}`) as unknown as Promise<Requirement>
}

export function createRequirement(data: RequirementCreate) {
  return request.post<ApiResponse>('/v1/requirements', data) as unknown as Promise<void>
}

export function createRequirementDraft(data: RequirementDraftCreate) {
  return request.post<ApiResponse<number>>('/v1/requirements/drafts', data) as unknown as Promise<number>
}

export function updateRequirementDraft(id: number, data: RequirementDraftUpdate) {
  return request.put<ApiResponse>(`/v1/requirements/${id}/draft`, data) as unknown as Promise<void>
}

export function getMyRequirementDrafts(params: RequirementMyListQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements/my-drafts', { params }) as unknown as Promise<PageResult<Requirement>>
}

export function getRequirementNextNodes(id: number) {
  return request.get<ApiResponse<NextNodeOption[]>>(`/v1/requirements/${id}/next-nodes`) as unknown as Promise<NextNodeOption[]>
}

export function submitRequirementDraft(id: number, data: RequirementSubmit) {
  return request.post<ApiResponse<Requirement>>(`/v1/requirements/${id}/submit`, data) as unknown as Promise<Requirement>
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

export function getRequirementComments(id: number) {
  return request.get<ApiResponse<RequirementComment[]>>(`/v1/requirements/${id}/comments`) as unknown as Promise<RequirementComment[]>
}

export function createRequirementComment(id: number, data: RequirementCommentCreate) {
  return request.post<ApiResponse>(`/v1/requirements/${id}/comments`, data) as unknown as Promise<void>
}

export function getRequirementChildren(parentId: number) {
  return request.get<ApiResponse<Requirement[]>>(`/v1/requirements/${parentId}/children`) as unknown as Promise<Requirement[]>
}

// 列配置 API
export function getColumnConfig(pageKey: string) {
  return request.get<ApiResponse<string[]>>(`/v1/column-config/${pageKey}`) as unknown as Promise<string[] | null>
}

export function saveColumnConfig(pageKey: string, columns: string[]) {
  return request.put<ApiResponse>(`/v1/column-config/${pageKey}`, { columns }) as unknown as Promise<void>
}
