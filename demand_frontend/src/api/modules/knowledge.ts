import request from '@/api/request'
import { getToken } from '@/utils/auth'

// ===== 类型定义 =====

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  projectId: number | null
  projectName: string | null
  creatorId: number
  creatorName: string | null
  docCount: number
  chunkCount: number
  status: string
  isDefaultForRequirements: boolean
  docTimeoutMinutes: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  projectId?: number | null
  projectName?: string | null
  fileName: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: string
  errorMessage: string | null
  requirementId?: number | null
  sourceType?: string | null
  sourceId?: number | null
  uploaderId: number
  uploaderName: string | null
  downloadCount: number
  createdAt: string
}

export interface KnowledgeDocumentQueryParams {
  pageNum?: number
  pageSize?: number
  fileName?: string
  status?: string
  createdAtStart?: string
  createdAtEnd?: string
  projectName?: string
  requirementId?: number
}

export interface SearchResultItem {
  chunkId: number
  documentId: number
  fileName: string
  sectionTitle: string | null
  content: string
  pageNum: number | null
  score: number
  knowledgeBaseId: string
  requirement?: {
    id: number
    title: string
    status: string
    type: string
    summary: string
  } | null
}

export interface ThinkingStep {
  stepType: 'query_parse' | 'retrieve' | 'rerank' | 'synthesize'
  title: string
  detail: string
  score?: number
  metadata?: Record<string, any>
}

export interface SearchResponse {
  results: SearchResultItem[]
  total: number
  answer?: string | null
  processSummary?: string | null
  thinkingSteps?: ThinkingStep[]
}

export type SearchMode = 'hybrid' | 'semantic' | 'keyword'

export interface DocumentRequirementRef {
  id: number
  documentId: number
  requirementId: number
  requirementCode: string
  requirementTitle: string
  createdAt: string
}

// ===== API 函数 =====

// 知识库管理
export function createKnowledgeBase(data: { name: string; description?: string; projectId?: number; docTimeoutMinutes?: number }) {
  return request.post<KnowledgeBase>('/v1/knowledge/bases', data)
}

export function getKnowledgeBases(params: { name?: string; pageNum?: number; pageSize?: number }) {
  return request.get<{ list: KnowledgeBase[]; total: number }>('/v1/knowledge/bases', { params })
}

export function getAllKnowledgeBases() {
  return request.get<KnowledgeBase[]>('/v1/knowledge/bases/all')
}

export function getKnowledgeBase(id: number) {
  return request.get<KnowledgeBase>(`/v1/knowledge/bases/${id}`)
}

export function updateKnowledgeBase(id: number, data: { name?: string; description?: string; docTimeoutMinutes?: number }) {
  return request.put<KnowledgeBase>(`/v1/knowledge/bases/${id}`, data)
}

export function deleteKnowledgeBase(id: number) {
  return request.delete(`/v1/knowledge/bases/${id}`)
}

// 文档迁移
export interface KnowledgeMigrateParams {
  targetKnowledgeBaseId: number
  documentIds?: number[]
  reason?: string
}

export interface KnowledgeMigrateResult {
  migratedDocuments: number
  migratedChunks: number
  sourceKnowledgeBaseId: number
  targetKnowledgeBaseId: number
}

export function migrateKnowledgeBaseDocuments(id: number, data: KnowledgeMigrateParams) {
  return request.post<KnowledgeMigrateResult>(`/v1/knowledge/bases/${id}/migrate`, data)
}

// 设置/取消默认知识库
export function setAsDefaultKnowledgeBase(id: number) {
  return request.patch<void>(`/v1/knowledge/bases/${id}/set-default`)
}

export function unsetDefaultKnowledgeBase(id: number) {
  return request.patch<void>(`/v1/knowledge/bases/${id}/unset-default`)
}

// 文档管理
export function uploadDocument(knowledgeBaseId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<KnowledgeDocument>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getDocuments(knowledgeBaseId: number, params: KnowledgeDocumentQueryParams) {
  return request.get<{ list: KnowledgeDocument[]; total: number }>(`/v1/knowledge/bases/${knowledgeBaseId}/documents`, { params })
}

export function deleteDocument(knowledgeBaseId: number, documentId: number) {
  return request.delete(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}`)
}

export function retryDocuments(knowledgeBaseId: number, documentIds: number[]) {
  return request.post<{ retried: number }>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/retry`, { documentIds })
}

/**
 * 跳过文档索引（仅保留文件存储）。
 *
 * 适用于"持续索引中"卡死场景。状态切到 stored，预览/下载能力保留。
 */
export function skipIndexing(knowledgeBaseId: number, documentId: number) {
  return request.post<void>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}/skip-indexing`)
}

export function batchDeleteDocuments(knowledgeBaseId: number, documentIds: number[]) {
  return request.post<{ deleted: number }>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/batch-delete`, { documentIds })
}

export function getDocumentPreviewUrl(knowledgeBaseId: number, documentId: number) {
  return request.get<string>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}/preview`)
}

export async function downloadDocumentBlob(knowledgeBaseId: number, documentId: number): Promise<Blob> {
  const res = await request.get<Blob>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}/download`, {
    responseType: 'blob',
  })
  return res as unknown as Blob
}

export async function batchDownloadDocumentsZip(knowledgeBaseId: number, documentIds: number[]): Promise<Blob> {
  const res = await request.post<Blob>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/batch-download`, { documentIds }, {
    responseType: 'blob',
  })
  return res as unknown as Blob
}

export function generateDocumentShareLink(
  knowledgeBaseId: number,
  documentId: number,
  options?: { expireHours?: number; requireLogin?: boolean; oneTimeAccess?: boolean }
) {
  const expireHours = options?.expireHours ?? 24
  const requireLogin = options?.requireLogin ? 'true' : 'false'
  const oneTimeAccess = options?.oneTimeAccess ? 'true' : 'false'
  return request.post<string>(
    `/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}/share?expireHours=${expireHours}&requireLogin=${requireLogin}&oneTimeAccess=${oneTimeAccess}`
  )
}

// 获取文档的需求引用
export function getDocumentRequirementRefs(knowledgeBaseId: number, documentId: number) {
  return request.get<DocumentRequirementRef[]>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}/requirement-refs`)
}

// 语义检索
export function searchKnowledge(data: {
  query: string
  knowledgeBaseId?: number
  mode?: SearchMode
  topK?: number
  llmModelId?: number
}) {
  return request.post<SearchResponse>('/v1/knowledge/search', data)
}

export interface StreamSearchHandlers {
  onResults?: (response: SearchResponse) => void
  onDelta?: (delta: string) => void
  onDone?: (response: SearchResponse) => void
  onError?: (message: string) => void
}

export async function streamSearchKnowledge(
  data: {
    query: string
    knowledgeBaseId?: number
    mode?: SearchMode | 'rag'
    topK?: number
    llmModelId?: number
  },
  handlers: StreamSearchHandlers
) {
  const baseURL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  const token = getToken()
  const response = await fetch(`${baseURL}/v1/knowledge/search/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })

  if (!response.ok || !response.body) {
    throw new Error(`流式检索请求失败: HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    events.forEach(eventBlock => handleStreamEvent(eventBlock, handlers))
  }

  if (buffer.trim()) {
    handleStreamEvent(buffer, handlers)
  }
}

function handleStreamEvent(eventBlock: string, handlers: StreamSearchHandlers) {
  const lines = eventBlock.split(/\r?\n/)
  let eventName = 'message'
  const dataLines: string[] = []

  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  })

  const payload = dataLines.join('\n')
  if (!payload) return

  if (eventName === 'delta') {
    handlers.onDelta?.(payload)
    return
  }

  if (eventName === 'error') {
    handlers.onError?.(parseStreamMessage(payload))
    return
  }

  const parsed = JSON.parse(payload) as SearchResponse
  if (eventName === 'results') {
    handlers.onResults?.(parsed)
  } else if (eventName === 'done') {
    handlers.onDone?.(parsed)
  }
}

function parseStreamMessage(payload: string) {
  try {
    const parsed = JSON.parse(payload)
    return parsed?.message || payload
  } catch {
    return payload
  }
}

// ===== RAG 配置动态获取 =====

export interface RagModelStatus {
  configured: boolean
  modelId?: string
  name?: string
  providerName?: string | null
  dimension?: number | null
  dimensionMatch?: boolean | null
  testSuccess?: boolean | null
  testError?: string | null
}

export interface RagModelCandidate {
  id: number
  modelId: string
  name: string
  providerId: number
  providerName: string
  modelType?: string
  isDefault: boolean
  enabled?: boolean
  dimension?: number | null
}

export interface RagConfig {
  chunkSize: number
  chunkOverlap: number
  searchTopK: number
  milvusDimension: number
  embedding: RagModelStatus
  reranker: RagModelStatus
  chat?: RagModelStatus
  embeddingCandidates: RagModelCandidate[]
  rerankerCandidates: RagModelCandidate[]
  chatCandidates?: RagModelCandidate[]
}

export function getRagConfig() {
  return request.get<RagConfig>('/v1/knowledge/config')
}

export function updateRagConfig(data: { chunkSize?: number; chunkOverlap?: number; searchTopK?: number }) {
  return request.put<{ chunkSize: number; chunkOverlap: number; searchTopK: number }>('/v1/knowledge/config', data)
}
