import request from '@/api/request'
import type { ApiResponse } from '@/types/api'
import type { RequirementAttachment } from '@/types/requirement'
import { saveBlob } from '@/utils/download'

export interface FileUploadResult extends RequirementAttachment {}

export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<FileUploadResult>>('/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export async function uploadRequirementAttachment(file: File): Promise<RequirementAttachment> {
  const urlRes = await uploadFile(file) as any
  const uploadResult = typeof urlRes === 'string' ? { url: urlRes } : urlRes
  return {
    fileId: uploadResult?.fileId,
    name: uploadResult?.name || file.name,
    url: uploadResult?.url || '',
    size: uploadResult?.size ?? file.size,
    contentType: uploadResult?.contentType || file.type || undefined,
    bucketName: uploadResult?.bucketName,
    objectName: uploadResult?.objectName,
    uploadedAt: new Date().toISOString(),
    uploaderId: uploadResult?.uploaderId,
    contentHash: uploadResult?.contentHash || null,
  }
}

/**
 * 批量查询文件内容哈希（历史文件由服务端惰性回填）。
 * 返回 fileId → SHA-256 hex。
 */
export async function fetchFileHashes(fileIds: Array<number | null | undefined>): Promise<Record<number, string>> {
  const ids = fileIds.filter((id): id is number => typeof id === 'number' && Number.isFinite(id))
  if (ids.length === 0) return {}
  const res = await request.post<ApiResponse<Record<number, string>>>('/v1/files/hashes', ids)
  return (res as any)?.data ?? {}
}

export function downloadFile(id: number) {
  return request.get<Blob>(`/v1/files/${id}`, { responseType: 'blob' })
}

export function deleteFile(id: number) {
  return request.delete<ApiResponse>(`/v1/files/${id}`)
}

export async function downloadRequirementAttachment(attachment: RequirementAttachment) {
  if (attachment.fileId) {
    const blob = await downloadFile(attachment.fileId) as unknown as Blob
    await saveBlob(blob, attachment.name || 'attachment')
    return
  }

  if (attachment.url) {
    window.open(attachment.url, '_blank', 'noopener,noreferrer')
  }
}
