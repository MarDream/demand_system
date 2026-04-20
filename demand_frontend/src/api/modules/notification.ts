import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'

export function getNotificationList(params: { pageNum: number; pageSize: number }) {
  return request.get<ApiResponse<PageResult<unknown>>>('/v1/notifications', { params })
}

export function getUnreadCount() {
  return request.get<ApiResponse<number>>('/v1/notifications/unread')
}

export function markAsRead(id: number) {
  return request.post<ApiResponse>(`/v1/notifications/${id}/read`)
}

export function markAllAsRead() {
  return request.post<ApiResponse>('/v1/notifications/read-all')
}
