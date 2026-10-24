import { get, post } from './request'
import type { PageResult } from './request'

export interface NotificationItem {
  id: number
  title?: string
  content?: string
  type?: string
  isRead?: number
  relatedId?: number | null
  /** 关联评论ID（@提醒场景：跳转到工单评论处定位用） */
  commentId?: number | null
  relatedType?: string | null
  createdAt?: string
}

export function getNotificationList(params: { pageNum: number; pageSize: number }) {
  return get<PageResult<NotificationItem>>('/v1/notifications', params)
}

export function getUnreadCount() {
  return get<number>('/v1/notifications/unread')
}

export function markAsRead(id: number) {
  return post<void>(`/v1/notifications/${id}/read`)
}

export function markAllAsRead() {
  return post<void>('/v1/notifications/read-all')
}
