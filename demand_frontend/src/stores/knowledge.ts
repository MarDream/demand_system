import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteDocument,
  generateDocumentShareLink,
  getAllKnowledgeBases,
  getDocuments,
  migrateKnowledgeBaseDocuments,
  searchKnowledge,
  updateKnowledgeBase,
  uploadDocument,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeDocumentQueryParams,
  type KnowledgeMigrateParams,
  type KnowledgeMigrateResult,
  type SearchMode,
  type SearchResponse
} from '@/api/modules/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const currentBase = ref<KnowledgeBase | null>(null)
  const documents = ref<KnowledgeDocument[]>([])
  const searchResults = ref<SearchResponse | null>(null)
  const loading = ref(false)
  const totalDocs = ref(0)

  async function fetchAllBases() {
    loading.value = true
    try {
      const res = await getAllKnowledgeBases()
      knowledgeBases.value = (res as any)?.data || res || []
    } finally {
      loading.value = false
    }
  }

  async function createBase(data: { name: string; description?: string; projectId?: number; docTimeoutMinutes?: number }) {
    const res = await createKnowledgeBase(data)
    await fetchAllBases()
    return res
  }

  async function updateBase(id: number, data: { name?: string; description?: string; docTimeoutMinutes?: number }) {
    const res = await updateKnowledgeBase(id, data)
    await fetchAllBases()
    return res
  }

  async function removeBase(id: number) {
    await deleteKnowledgeBase(id)
    await fetchAllBases()
  }

  /**
   * 将源知识库下的文档迁移到目标知识库。
   * 通常在删除源知识库前调用，以保留文档数据。
   */
  async function migrateDocuments(sourceId: number, params: KnowledgeMigrateParams): Promise<KnowledgeMigrateResult> {
    const res = await migrateKnowledgeBaseDocuments(sourceId, params) as any
    await fetchAllBases()
    return (res?.data ?? res) as KnowledgeMigrateResult
  }

  async function fetchDocuments(knowledgeBaseId: number, params: KnowledgeDocumentQueryParams = { pageNum: 1, pageSize: 20 }) {
    loading.value = true
    try {
      const res = await getDocuments(knowledgeBaseId, params)
      const data = (res as any)?.data || res
      documents.value = data?.list || []
      totalDocs.value = data?.total || 0
    } finally {
      loading.value = false
    }
  }

  async function uploadDoc(knowledgeBaseId: number, file: File) {
    const res = await uploadDocument(knowledgeBaseId, file)
    if (currentBase.value?.id === knowledgeBaseId) {
      await fetchDocuments(knowledgeBaseId)
    }
    return res
  }

  async function removeDoc(knowledgeBaseId: number, documentId: number) {
    await deleteDocument(knowledgeBaseId, documentId)
    await fetchDocuments(knowledgeBaseId)
  }

  async function getShareLink(
    knowledgeBaseId: number,
    documentId: number,
    options?: { expireHours?: number; requireLogin?: boolean; oneTimeAccess?: boolean }
  ) {
    return await generateDocumentShareLink(knowledgeBaseId, documentId, options) as unknown as string
  }

  async function search(
    query: string,
    mode: SearchMode = 'hybrid',
    knowledgeBaseId?: number,
    topK?: number,
    llmModelId?: number,
    searchScopes?: Array<'REQUIREMENT_BODY' | 'KNOWLEDGE_BASE' | 'WEB'>
  ) {
    loading.value = true
    try {
      const res = await searchKnowledge({ query, mode, knowledgeBaseId, topK, llmModelId, searchScopes })
      searchResults.value = (res as any)?.data || res
      return searchResults.value
    } finally {
      loading.value = false
    }
  }

  return {
    knowledgeBases,
    currentBase,
    documents,
    searchResults,
    loading,
    totalDocs,
    fetchAllBases,
    createBase,
    updateBase,
    removeBase,
    migrateDocuments,
    fetchDocuments,
    uploadDoc,
    removeDoc,
    getShareLink,
    search
  }
})
