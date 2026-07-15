import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listBases, getBase } from '@/api/modules/bitable'
import type { BitableBase } from '@/types/bitable'

export const useBitableStore = defineStore('bitable', () => {
  const bases = ref<BitableBase[]>([])
  const currentBase = ref<BitableBase | null>(null)
  const loading = ref(false)

  async function loadBases() {
    loading.value = true
    try {
      bases.value = await listBases()
    } finally {
      loading.value = false
    }
  }

  async function loadBase(baseId: number) {
    currentBase.value = await getBase(baseId)
  }

  return { bases, currentBase, loading, loadBases, loadBase }
})
