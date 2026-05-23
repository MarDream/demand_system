import { ref, onMounted, onUnmounted } from 'vue'
import { getUnreadCount } from '@/api/modules/notification'
import { getToken } from '@/utils/auth'

const unreadCount = ref(0)
let timer: ReturnType<typeof setInterval> | null = null
let activeConsumers = 0
let pendingFetch: Promise<void> | null = null

async function fetchUnreadCount() {
  if (!getToken()) {
    unreadCount.value = 0
    return
  }
  if (pendingFetch) {
    return pendingFetch
  }

  pendingFetch = getUnreadCount()
    .then(({ data }) => {
      unreadCount.value = data.data || 0
    })
    .catch(() => {
      // ignore
    })
    .finally(() => {
      pendingFetch = null
    })

  return pendingFetch
}

function startPolling() {
  if (timer) return
  fetchUnreadCount()
  timer = setInterval(fetchUnreadCount, 60000)
}

function stopPolling() {
  if (!timer || activeConsumers > 0) return
  clearInterval(timer)
  timer = null
}

export function useNotification() {
  onMounted(() => {
    activeConsumers++
    startPolling()
  })

  onUnmounted(() => {
    activeConsumers = Math.max(0, activeConsumers - 1)
    stopPolling()
  })

  return { unreadCount, fetchUnreadCount }
}
