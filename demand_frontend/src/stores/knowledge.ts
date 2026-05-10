import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteDocument,
  generateDocumentShareLink,
  getAllKnowledgeBases,
  getDocuments,
  searchKnowledge,
  updateKnowledgeBase,
  uploadDocument,
  type KnowledgeBase,
  type KnowledgeDocument,
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

  async function createBase(data: { name: string; description?: string; projectId?: number }) {
    const res = await createKnowledgeBase(data)
    await fetchAllBases()
    return res
  }

  async function updateBase(id: number, data: { name?: string; description?: string }) {
    const res = await updateKnowledgeBase(id, data)
    await fetchAllBases()
    return res
  }

  async function removeBase(id: number) {
    await deleteKnowledgeBase(id)
    await fetchAllBases()
  }

  async function fetchDocuments(knowledgeBaseId: number, pageNum = 1, pageSize = 20) {
    loading.value = true
    try {
      const res = await getDocuments(knowledgeBaseId, { pageNum, pageSize })
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

  async function search(query: string, mode: SearchMode = 'hybrid', knowledgeBaseId?: number) {
    loading.value = true
    try {
      const res = await searchKnowledge({ query, mode, knowledgeBaseId })
      searchResults.value = (res as any)?.data || res
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
    fetchDocuments,
    uploadDoc,
    removeDoc,
    getShareLink,
    search
  }
})
