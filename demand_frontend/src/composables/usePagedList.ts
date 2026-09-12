import { ref } from 'vue'

/**
 * 分页列表通用状态管理。
 *
 * 统一各页面的 pageNum / pageSize / total / loading / list 散装写法，
 * fetchFn 接收 (pageNum, pageSize)，返回分页数据。
 * 自动兼容三种返回形态：{ list, total }、{ data: { list, total } }、纯数组。
 *
 * @example
 * const { list, total, pageNum, pageSize, loading, fetchPage, reset, handleSizeChange, handleCurrentChange } =
 *   usePagedList((pageNum, pageSize) => getXxxList({ pageNum, pageSize }))
 */
export function usePagedList<T>(
  fetchFn: (pageNum: number, pageSize: number) => Promise<unknown>,
  options: { pageSize?: number; onError?: (error: unknown) => void } = {},
) {
  const list = ref<T[]>([])
  const pageNum = ref(1)
  const pageSize = ref(options.pageSize ?? 10)
  const total = ref(0)
  const loading = ref(false)

  /** 请求指定页数据（默认当前页） */
  async function fetchPage(page: number = pageNum.value) {
    pageNum.value = page
    loading.value = true
    try {
      const res = await fetchFn(pageNum.value, pageSize.value)
      // 兼容 { data: { list, total } } 包装与直接返回
      const payload = res && typeof res === 'object' && 'data' in res && res.data && typeof res.data === 'object' && ('list' in res.data || 'total' in res.data)
        ? res.data
        : res
      if (Array.isArray(payload)) {
        list.value = payload as T[]
        total.value = payload.length
      } else {
        const paged = payload as { list?: T[]; total?: number } | null
        list.value = paged?.list ?? []
        total.value = paged?.total ?? list.value.length
      }
    } catch (error) {
      if (options.onError) {
        options.onError(error)
      } else {
        throw error
      }
    } finally {
      loading.value = false
    }
  }

  /** 回到第一页重新拉取（搜索/筛选条件变化时调用） */
  function reset() {
    return fetchPage(1)
  }

  /** el-pagination 的 page-size 变更回调：重置到第一页 */
  function handleSizeChange() {
    return fetchPage(1)
  }

  /** el-pagination 的 current-page 变更回调 */
  function handleCurrentChange(page: number) {
    return fetchPage(page)
  }

  return { list, pageNum, pageSize, total, loading, fetchPage, reset, handleSizeChange, handleCurrentChange }
}
