import { ref, onMounted, onUnmounted, watch } from 'vue'
import { getQuickQuestions, recordQuickQuestionClick } from '@/api/modules/assistant'
import type { QuickQuestion } from '@/api/modules/assistant'

/**
 * 快捷提问 Composable
 *
 * 轮询策略：
 * - 弹窗打开 + 无活跃对话 + 标签页可见 → 每 10s 查询
 * - 弹窗关闭 / 对话中有内容 / 标签页隐藏 → 停止轮询
 * - 本地缓存 30s 防抖
 */
export function useQuickQuestions(options: {
  pageRoute: () => string
  dialogVisible: () => boolean
  hasActiveConversation: () => boolean
}) {
  const questions = ref<QuickQuestion[]>([])
  const loading = ref(false)
  const lastFetchTime = ref(0)
  const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)
  const CACHE_TTL = 30_000
  const POLL_INTERVAL = 10_000

  async function fetchQuestions(force = false) {
    const now = Date.now()
    if (!force && now - lastFetchTime.value < CACHE_TTL) {
      return
    }

    if (!options.dialogVisible() || options.hasActiveConversation() || !isPageVisible()) {
      return
    }

    loading.value = true
    try {
      const route = options.pageRoute()
      const res = await getQuickQuestions(route || undefined)
      questions.value = res.data ?? []
      lastFetchTime.value = Date.now()
    } catch {
      // 静默失败，不影响 UI
    } finally {
      loading.value = false
    }
  }

  function startPolling() {
    stopPolling()
    fetchQuestions(true)
    pollTimer.value = setInterval(() => fetchQuestions(), POLL_INTERVAL)
  }

  function stopPolling() {
    if (pollTimer.value !== null) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
    }
  }

  function isPageVisible(): boolean {
    return document.visibilityState === 'visible'
  }

  function handleVisibilityChange() {
    if (isPageVisible() && options.dialogVisible() && !options.hasActiveConversation()) {
      fetchQuestions(true)
      startPolling()
    } else {
      stopPolling()
    }
  }

  watch(
    () => options.dialogVisible(),
    (visible) => {
      if (visible && !options.hasActiveConversation()) {
        startPolling()
      } else {
        stopPolling()
      }
    }
  )

  watch(
    () => options.hasActiveConversation(),
    (active) => {
      if (active) {
        stopPolling()
      } else if (options.dialogVisible()) {
        startPolling()
      }
    }
  )

  onMounted(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onUnmounted(() => {
    stopPolling()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  /**
   * 记录点击并递增 hit_count
   */
  async function handleQuickClick(question: QuickQuestion): Promise<string> {
    recordQuickQuestionClick(question.id).catch(() => {})
    return question.questionText
  }

  return {
    questions,
    loading,
    handleQuickClick,
    startPolling,
    stopPolling,
  }
}
