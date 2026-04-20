import { ref } from 'vue'

export function useTable<T>(fetchFn: (params: any) => Promise<any>) {
  const loading = ref(false)
  const data = ref<T[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)

  async function loadData() {
    loading.value = true
    try {
      const { data: res } = await fetchFn({ pageNum: pageNum.value, pageSize: pageSize.value })
      if (res.code === 200) {
        data.value = res.data.list || []
        total.value = res.data.total || 0
      }
    } finally {
      loading.value = false
    }
  }

  function handlePageChange(page: number) {
    pageNum.value = page
    loadData()
  }

  function handleSizeChange(size: number) {
    pageSize.value = size
    pageNum.value = 1
    loadData()
  }

  return { loading, data, total, pageNum, pageSize, loadData, handlePageChange, handleSizeChange }
}
