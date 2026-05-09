import request from '@/api/request'

export interface DocumentChunkItem {
  chunkId: string
  sectionTitle: string
  matchedText: string
}

export interface DocumentItem {
  documentId: number
  fileName: string
  avgScore: number
  chunks: DocumentChunkItem[]
}

export interface DocumentSearchResult {
  documents: DocumentItem[]
  answer?: string
}

export function searchDocuments(query: string, mode = 'hybrid') {
  return request.post<DocumentSearchResult>('/v1/rbac/documents/search', { query, mode })
}
