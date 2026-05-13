import request from '@/api/request'

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
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  projectId?: number | null
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
  createdAt: string
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

export interface SearchResponse {
  results: SearchResultItem[]
  total: number
  answer?: string | null
  processSummary?: string | null
}

export type SearchMode = 'hybrid' | 'semantic' | 'keyword'

// ===== API 函数 =====

// 知识库管理
export function createKnowledgeBase(data: { name: string; description?: string; projectId?: number }) {
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

export function updateKnowledgeBase(id: number, data: { name?: string; description?: string }) {
  return request.put<KnowledgeBase>(`/v1/knowledge/bases/${id}`, data)
}

export function deleteKnowledgeBase(id: number) {
  return request.delete(`/v1/knowledge/bases/${id}`)
}

// 文档管理
export function uploadDocument(knowledgeBaseId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<KnowledgeDocument>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getDocuments(knowledgeBaseId: number, params: { pageNum?: number; pageSize?: number }) {
  return request.get<{ list: KnowledgeDocument[]; total: number }>(`/v1/knowledge/bases/${knowledgeBaseId}/documents`, { params })
}

export function deleteDocument(knowledgeBaseId: number, documentId: number) {
  return request.delete(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}`)
}

export function retryDocuments(knowledgeBaseId: number, documentIds: number[]) {
  return request.post<{ retried: number }>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/retry`, { documentIds })
}

export function batchDeleteDocuments(knowledgeBaseId: number, documentIds: number[]) {
  return request.post<{ deleted: number }>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/batch-delete`, { documentIds })
}

export function getDocumentPreviewUrl(knowledgeBaseId: number, documentId: number) {
  return request.get<string>(`/v1/knowledge/bases/${knowledgeBaseId}/documents/${documentId}/preview`)
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
