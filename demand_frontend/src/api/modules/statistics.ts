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
