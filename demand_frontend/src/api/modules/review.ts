import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export function getReviewList(requirementId: number) {
  return request.get<ApiResponse>(`/v1/requirements/${requirementId}/reviews`)
}

export function createReview(requirementId: number, data: { reviewerId: number }) {
  return request.post<ApiResponse>(`/v1/requirements/${requirementId}/reviews`, data)
}

export function updateReview(id: number, data: { result: string; comment: string; suggestions: string }) {
  return request.put<ApiResponse>(`/v1/reviews/${id}`, data)
}

export function concludeReview(requirementId: number) {
  return request.post<ApiResponse>(`/v1/requirements/${requirementId}/reviews/conclude`)
}
