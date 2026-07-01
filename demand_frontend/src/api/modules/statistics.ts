import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export function getDashboardData(projectId: number) {
  return request.get<ApiResponse>(`/v1/projects/${projectId}/stats/dashboard`)
}

export function getDistributionData(projectId: number) {
  return request.get<ApiResponse>(`/v1/projects/${projectId}/stats/distribution`)
}

export function getDurationData(projectId: number) {
  return request.get<ApiResponse>(`/v1/projects/${projectId}/stats/duration`)
}

export function getBurndownData(iterationId: number) {
  return request.get<ApiResponse>(`/v1/iterations/${iterationId}/stats/burndown`)
}

export function getCfdData(projectId: number) {
  return request.get<ApiResponse>(`/v1/projects/${projectId}/stats/cfd`)
}

// ==================== 评分统计（对应 ADR-002 Phase 2） ====================

export interface RatingTrendPoint {
  label: string
  average: number
  count: number
}

export interface LowRatingRequirement {
  requirementId: number
  requirementNo: string
  title: string
  nodeName: string
  nodeId?: string
  rating: number
  ratingDimensions?: Record<string, number> | null
  comment?: string
  evaluatorId?: number
  evaluatorName?: string
  createdAt?: string
}

export interface RatingStatistics {
  overallAverage: number
  dimensionAverages: Record<string, number>
  trends?: RatingTrendPoint[]
  distribution: Record<number, number>
  topLowRated: LowRatingRequirement[]
  nodeAverages: Record<string, number>
  periodStart?: string
  periodEnd?: string
  totalEvaluations: number
}

export interface RatingQueryParams {
  projectId?: number
  iterationId?: number
  startDate?: string
  endDate?: string
  granularity?: 'WEEK' | 'MONTH'
  threshold?: number
  limit?: number
  workflowVersionId?: number
}

function toParams(params: RatingQueryParams) {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
  )
}

/** 评分综合统计 */
export function getRatingStatistics(params: RatingQueryParams = {}) {
  return request.get<ApiResponse<RatingStatistics>>('/v1/statistics/rating', { params: toParams(params) })
}

/** 评分趋势 */
export function getRatingTrend(params: RatingQueryParams = {}) {
  return request.get<ApiResponse<RatingTrendPoint[]>>('/v1/statistics/rating/trend', { params: toParams(params) })
}

/** 评分分布 */
export function getRatingDistribution(params: RatingQueryParams = {}) {
  return request.get<ApiResponse<Record<number, number>>>('/v1/statistics/rating/distribution', { params: toParams(params) })
}

/** 各维度平均分 */
export function getRatingDimensionAverages(params: RatingQueryParams = {}) {
  return request.get<ApiResponse<Record<string, number>>>('/v1/statistics/rating/dimensions', { params: toParams(params) })
}

/** 低分需求列表 */
export function getLowRatedRequirements(params: RatingQueryParams = {}) {
  return request.get<ApiResponse<LowRatingRequirement[]>>('/v1/statistics/rating/low-rated', { params: toParams(params) })
}

/** 各节点平均分 */
export function getNodeAverageRatings(params: RatingQueryParams = {}) {
  return request.get<ApiResponse<Record<string, number>>>('/v1/statistics/rating/node-averages', { params: toParams(params) })
}

// ==================== 流程统计 ====================

export interface WorkflowProcessStats {
  /** 待办流程数 */
  pending: number
  /** 已办流程数 */
  processed: number
  /** 我发起的流程数 */
  initiated: number
  /** 抄送我的流程数 */
  cc: number
}

/** 流程处理概览统计（当前登录用户维度） */
export function getWorkflowProcessStats() {
  return request.get<ApiResponse<WorkflowProcessStats>>('/v1/workflow/process-stats')
}
