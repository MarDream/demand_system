import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  NextNodeOption,
  Requirement,
  RequirementApprovalEvaluation,
  RequirementApprovalSupplementCreate,
  RequirementComment,
  RequirementCommentCreate,
  RequirementCreate,
  RequirementDraftCreate,
  RequirementDraftUpdate,
  RequirementHistory,
  RequirementMyListQuery,
  RequirementQuery,
  RequirementSubmit,
  RequirementTemplate,
  RequirementTemplateSave,
  RequirementUpdate, RequirementDetailVO,
} from '@/types/requirement'
import axios from 'axios'
import { getToken } from '@/utils/auth'

export function getRequirementList(params: RequirementQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements', { params }) as unknown as Promise<PageResult<Requirement>>
}

/**
 * 导出需求列表为 Excel 文件
 * @param params   检索条件（与列表查询共用 RequirementQuery）
 * @param view     视图类型：all / drafts / pending / done / follows
 * @param columns  导出列配置：key 数组，决定导出哪些字段及顺序；不传则后端使用默认全量列
 * @param signal   可选的 AbortSignal，用于取消正在进行的导出请求
 * @returns Blob 文件流
 */
export function exportRequirementExcel(params: RequirementQuery, view: string, columns?: string[], signal?: AbortSignal) {
  const baseURL = import.meta.env.VITE_API_BASE_URL
  const token = getToken()
  return axios.get(`${baseURL}/v1/requirements/export`, {
    params: { ...params, view, columns: columns?.join(',') || undefined },
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` },
    timeout: 120000,
    signal,  // 支持取消请求
  })
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

export function getMyRequirementPending(params: RequirementMyListQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements/my-pending', { params }) as unknown as Promise<PageResult<Requirement>>
}

export function getMyRequirementFollows(params: RequirementMyListQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements/my-follows', { params }) as unknown as Promise<PageResult<Requirement>>
}

/**
 * 获取我的已办需求列表
 */
export function getMyRequirementDone(params: RequirementMyListQuery) {
  return request.get<ApiResponse<PageResult<Requirement>>>('/v1/requirements/my-done', { params }) as unknown as Promise<PageResult<Requirement>>
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

export function followRequirement(id: number) {
  return request.post<ApiResponse>(`/v1/requirements/${id}/follow`) as unknown as Promise<void>
}

export function unfollowRequirement(id: number) {
  return request.delete<ApiResponse>(`/v1/requirements/${id}/follow`) as unknown as Promise<void>
}

export function getRequirementHistory(id: number) {
  return request.get<ApiResponse<RequirementHistory[]>>(`/v1/requirements/${id}/history`) as unknown as Promise<RequirementHistory[]>
}

export function getRequirementComments(id: number) {
  return request.get<ApiResponse<RequirementComment[]>>(`/v1/requirements/${id}/comments`) as unknown as Promise<RequirementComment[]>
}

export function getApprovalEvaluations(id: number) {
  return request.get<ApiResponse<RequirementApprovalEvaluation[]>>(
    `/v1/requirements/${id}/approval-evaluations`,
  ) as unknown as Promise<RequirementApprovalEvaluation[]>
}

export function createApprovalEvaluationSupplement(
  requirementId: number,
  evaluationId: number,
  data: RequirementApprovalSupplementCreate,
) {
  return request.post<ApiResponse>(
    `/v1/requirements/${requirementId}/approval-evaluations/${evaluationId}/supplements`,
    data,
  ) as unknown as Promise<void>
}

export function createRequirementComment(id: number, data: RequirementCommentCreate) {
  return request.post<ApiResponse>(`/v1/requirements/${id}/comments`, data) as unknown as Promise<void>
}

export function getRequirementChildren(parentId: number) {
  return request.get<ApiResponse<Requirement[]>>(`/v1/requirements/${parentId}/children`) as unknown as Promise<Requirement[]>
}

// 列配置 API - 已抽取至 @/api/modules/columnConfig，下方保留转发兼容旧引用
export { getColumnConfig, saveColumnConfig } from './columnConfig'

// 需求模板 API
export function getRequirementTemplateByType(typeCode: string) {
  return request.get<ApiResponse<RequirementTemplate>>('/v1/requirement/templates/by-type', {
    params: { typeCode }
  }) as unknown as Promise<RequirementTemplate>
}

export function getRequirementTemplatesByType(typeCode: string) {
  return request.get<ApiResponse<RequirementTemplate[]>>('/v1/requirement/templates/by-type-list', {
    params: { typeCode }
  }) as unknown as Promise<RequirementTemplate[]>
}

export function getAllRequirementTemplates() {
  return request.get<ApiResponse<RequirementTemplate[]>>('/v1/requirement/templates/list') as unknown as Promise<RequirementTemplate[]>
}

export function saveRequirementTemplate(data: RequirementTemplateSave) {
  return request.post<ApiResponse>('/v1/requirement/templates/save', data) as unknown as Promise<void>
}

export function deleteRequirementTemplate(id: number) {
  return request.delete<ApiResponse>(`/v1/requirement/templates/${id}`) as unknown as Promise<void>
}

export function toggleRequirementTemplateStatus(id: number, isActive: number) {
  return request.put<ApiResponse>(`/v1/requirement/templates/${id}/status`, null, {
    params: { isActive }
  }) as unknown as Promise<void>
}

export function setDefaultRequirementTemplate(id: number) {
  return request.put<ApiResponse>(`/v1/requirement/templates/${id}/default`) as unknown as Promise<void>
}
export function getRequirementDetailBatch(id: number) {
  return request.get<ApiResponse<RequirementDetailVO>>(`/v1/requirements/${id}/detail-batch`) as unknown as Promise<RequirementDetailVO>
}
