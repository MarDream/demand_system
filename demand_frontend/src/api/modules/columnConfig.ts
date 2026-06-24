import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

/**
 * 读取指定页面（pageKey）的列配置。
 * 后端约定：返回字符串数组（列 key 集合），未配置时返回 null/空数组。
 */
export function getColumnConfig(pageKey: string) {
  return request.get<ApiResponse<string[]>>(`/v1/column-config/${pageKey}`) as unknown as Promise<string[] | null>
}

/**
 * 保存指定页面的列配置。
 */
export function saveColumnConfig(pageKey: string, columns: string[]) {
  return request.put<ApiResponse>(`/v1/column-config/${pageKey}`, { columns }) as unknown as Promise<void>
}
