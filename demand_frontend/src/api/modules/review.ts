import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Review, ReviewListQuery } from '@/types/review'

export function getReviews(params: ReviewListQuery) {
  return request.get<ApiResponse<PageResult<Review>>>('/v1/reviews', { params }) as unknown as Promise<PageResult<Review>>
}

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
