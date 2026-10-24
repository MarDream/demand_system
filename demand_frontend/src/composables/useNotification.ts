import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getUnreadCount } from '@/api/modules/notification'
import { getToken } from '@/utils/auth'

const unreadCount = ref(0)
/** 是否有未读（供红点类展示用，避免调用方各自写 unreadCount > 0） */
const hasUnread = computed(() => unreadCount.value > 0)
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
    // 注意：request 拦截器已把响应解包到 ApiResponse.data，
    // 这里拿到的**直接就是数字**，不是 AxiosResponse（此前多取一层 .data.data 导致未读数恒为 0）
    .then((count) => {
      unreadCount.value = Number(count) || 0
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

  return { unreadCount, hasUnread, fetchUnreadCount }
}
