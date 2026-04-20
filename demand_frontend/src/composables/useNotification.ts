import { ref, onMounted, onUnmounted } from 'vue'
import { getUnreadCount } from '@/api/modules/notification'
import { getToken } from '@/utils/auth'

export function useNotification() {
  const unreadCount = ref(0)

  async function fetchUnreadCount() {
    if (!getToken()) return
    try {
      const { data } = await getUnreadCount()
      unreadCount.value = data.data || 0
    } catch {
      // ignore
    }
  }

  let timer: ReturnType<typeof setInterval> | null = null

  onMounted(() => {
    fetchUnreadCount()
    timer = setInterval(fetchUnreadCount, 60000)
  })

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { unreadCount, fetchUnreadCount }
}
