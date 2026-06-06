import request from '@/api/request'

/**
 * 调用后端统一预览接口，生成外部文件预览服务的访问地址。
 *
 * <p>前端不感知底层预览服务实现（如 kkFileView、OnlyOffice 等），
 * 由后端 {@code /api/v1/preview/office-submit} + {@code /api/v1/preview/office-status}
 * 负责协议转换与异步轮询。前端只需调用 {@link getOfficePreviewUrl}，
 * 内部封装 submit → 轮询 status 流程，转换多久都不会被前端 axios 15s 超时打断。</p>
 *
 * <p>知识库文档场景下，MinIO 预签名 URL 由后端按需签发（24h 有效期），
 * 前端无需再单独请求 {@code /preview} 拿短效 URL，避免 kkFileView 异步转码过程中 URL 过期。</p>
 */

export interface OfficePreviewByDocument {
  knowledgeBaseId: number
  documentId: number
  watermarkTxt?: string
}

export interface OfficePreviewByFileUrl {
  fileUrl: string
  watermarkTxt?: string
}

export type OfficePreviewParams = OfficePreviewByDocument | OfficePreviewByFileUrl

export interface OfficePreviewResult {
  status: string
  previewUrl?: string
  taskId?: string
  message?: string
}

/** 轮询间隔（毫秒） */
const POLL_INTERVAL_MS = 2000
/** 轮询最大次数：与 application-dev.yml 的 max-poll-attempts: 30 保持一致 */
const MAX_POLL_ATTEMPTS = 30
/** 单次状态查询 HTTP 超时（毫秒），远小于 axios 默认 15s */
const POLL_HTTP_TIMEOUT_MS = 5000

function isByDocument(params: OfficePreviewParams): params is OfficePreviewByDocument {
  return (params as OfficePreviewByDocument).knowledgeBaseId !== undefined
    && (params as OfficePreviewByDocument).documentId !== undefined
}

function buildQueryParams(params: OfficePreviewParams): Record<string, string | number | undefined> {
  if (isByDocument(params)) {
    return {
      knowledgeBaseId: params.knowledgeBaseId,
      documentId: params.documentId,
      watermarkTxt: params.watermarkTxt,
    }
  }
  return {
    fileUrl: params.fileUrl,
    watermarkTxt: params.watermarkTxt,
  }
}

function unwrap<T>(res: unknown): T {
  return (res as { data: T })?.data ?? (res as T)
}

/**
 * 提交异步预览任务（不阻塞）。
 *
 * <p>小文件可能被 kkFileView 同步转完，直接返回 {@code status=completed}；
 * 大文件返回 {@code status=processing} + taskId，前端需轮询。</p>
 */
export async function submitOfficePreview(params: OfficePreviewParams): Promise<OfficePreviewResult> {
  const res = await request.get<OfficePreviewResult>('/v1/preview/office-submit', {
    params: buildQueryParams(params),
  })
  return unwrap<OfficePreviewResult>(res)
}

/**
 * 查询异步任务状态。
 *
 * <p>单次 HTTP 调用 < 1s，不会触发 axios 15s 超时。</p>
 */
export async function pollOfficeStatus(taskId: string): Promise<OfficePreviewResult> {
  const res = await request.get<OfficePreviewResult>('/v1/preview/office-status', {
    params: { taskId },
    timeout: POLL_HTTP_TIMEOUT_MS,
  })
  return unwrap<OfficePreviewResult>(res)
}

/**
 * 阻塞式获取预览 URL：内部封装 submit + 轮询。
 *
 * <p>调用方按原签名使用即可：返回的 {@code data.previewUrl} 在 completed 时为最终可嵌入 URL。</p>
 *
 * @throws 当任务失败或轮询超时时抛出 Error
 */
export async function getOfficePreviewUrl(
  params: OfficePreviewParams,
): Promise<{ data: OfficePreviewResult }> {
  const submitted = await submitOfficePreview(params)

  if (submitted.status === 'completed') {
    return { data: submitted }
  }

  if (submitted.status !== 'processing' || !submitted.taskId) {
    throw new Error(submitted.message || '预览提交失败')
  }

  for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
    await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS))
    const status = await pollOfficeStatus(submitted.taskId)
    if (status.status === 'completed' && status.previewUrl) {
      return { data: status }
    }
    if (status.status === 'failed') {
      throw new Error(status.message || '预览转换失败')
    }
    // status=processing 继续轮询
  }

  throw new Error('预览转换超时，请稍后重试')
}
