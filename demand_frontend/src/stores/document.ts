import { defineStore } from 'pinia'
import { ref } from 'vue'
import { searchDocuments, type DocumentSearchResult } from '@/api/modules/document'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref<any[]>([])
  const searchResults = ref<DocumentSearchResult | null>(null)
  const loading = ref(false)

  async function fetchDocuments() {
    documents.value = []
  }

  async function search(query: string, mode = 'hybrid') {
    loading.value = true
    try {
      searchResults.value = await searchDocuments(query, mode) as any
    } finally {
      loading.value = false
    }
  }

  return { documents, searchResults, loading, fetchDocuments, search }
})
