import { get } from './request'
import service from './request'

export interface OfficePreviewResult {
  status: string
  previewUrl?: string
  taskId?: string
  message?: string
  progress?: number
  waitingSeconds?: number
}

/** 轮询间隔（毫秒），与 PC 端 api/modules/preview.ts 一致 */
const POLL_INTERVAL_MS = 2000
/** 轮询最大次数：与 application-dev.yml 的 max-poll-attempts: 30 一致 */
const MAX_POLL_ATTEMPTS = 30

/** 下载文件原始流（走 /v1/files/{id}，携带 JWT） */
export async function downloadFileBlob(id: number): Promise<Blob> {
  const res = await service.get<Blob>(`/v1/files/${id}`, { responseType: 'blob' })
  return res.data
}

/**
 * 内联预览流：浏览器原生渲染（图片、文本），走 /v1/files/{id}/preview。
 * 返回 Blob，调用方转 objectURL / 文本。
 */
export async function fetchInlineBlob(id: number): Promise<Blob> {
  const res = await service.get<Blob>(`/v1/files/${id}/preview`, { responseType: 'blob' })
  return res.data
}

/**
 * 取 MinIO 24h 预签名 URL（供 kkFileView 容器反向拉取）。
 * request 层已解包 Result，返回 { url }。
 */
export function getPresignedPreviewUrl(id: number) {
  return get<{ url: string }>(`/v1/files/${id}/preview-url`)
}

/** 提交异步 office 预览任务 */
export function submitOfficePreview(params: { fileUrl: string }): Promise<OfficePreviewResult> {
  return get<OfficePreviewResult>('/v1/preview/office-submit', params)
}

/** 查询异步预览任务状态 */
export function pollOfficeStatus(taskId: string): Promise<OfficePreviewResult> {
  return get<OfficePreviewResult>('/v1/preview/office-status', { taskId })
}

/**
 * 阻塞式获取 office 预览地址：submit → 轮询 status。
 * 与 PC 端 getOfficePreviewUrl 同流程（后端 submit + poll）。
 */
export async function getOfficePreviewUrl(
  fileId: number,
  onStage?: (stage: 'submit' | 'poll' | 'complete') => void,
): Promise<string> {
  const { url } = await getPresignedPreviewUrl(fileId)
  if (!url) {
    throw new Error('获取文件预览地址失败')
  }
  onStage?.('submit')
  const submitted = await submitOfficePreview({ fileUrl: url })
  if (submitted.status === 'completed' && submitted.previewUrl) {
    onStage?.('complete')
    return submitted.previewUrl
  }
  if (submitted.status !== 'processing' || !submitted.taskId) {
    throw new Error(submitted.message || '预览提交失败')
  }
  for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
    if (attempt > 0) {
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
    }
    onStage?.('poll')
    const status = await pollOfficeStatus(submitted.taskId)
    if (status.status === 'completed' && status.previewUrl) {
      onStage?.('complete')
      return status.previewUrl
    }
    if (status.status === 'failed') {
      throw new Error(status.message || '预览转换失败')
    }
  }
  throw new Error('预览转换超时，请稍后重试')
}
