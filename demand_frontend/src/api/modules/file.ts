import request from '@/api/request'
import type { ApiResponse } from '@/types/api'

export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<string>>('/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function downloadFile(id: number) {
  return request.get(`/v1/files/${id}`, { responseType: 'blob' })
}

export function deleteFile(id: number) {
  return request.delete<ApiResponse>(`/v1/files/${id}`)
}
