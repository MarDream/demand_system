import { defineStore } from 'pinia'
import { ref } from 'vue'
import { showToast } from 'vant'
import {
  getUnreadCount,
  markAsRead as apiMarkAsRead,
  markAllAsRead as apiMarkAll,
} from '@/api/notification'
import { getToken } from '@/utils/auth'

/** 未读数轮询间隔：移动端流量敏感，45s 足够覆盖提醒场景 */
const POLL_INTERVAL_MS = 45000
/** 新通知提示节流：同一批新通知只提示一次，避免切换页面重复打扰 */
const TOAST_COOLDOWN_MS = 60000

export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)

  let timer: ReturnType<typeof setInterval> | null = null
  let lastToastAt = 0

  /**
   * 拉取未读数。
   * @param notifyIfNew 未读数上涨时是否弹轻提示（首帧/主动刷新传 false，避免把存量未读当成新消息）
   */
  async function refresh(notifyIfNew = true) {
    if (!getToken()) {
      unread.value = 0
      return
    }
    try {
      const count = await getUnreadCount()
      const prev = unread.value
      unread.value = count
      const delta = count - prev
      if (notifyIfNew && delta > 0 && Date.now() - lastToastAt > TOAST_COOLDOWN_MS) {
        lastToastAt = Date.now()
        showToast(`收到 ${delta} 条新通知`)
      }
    } catch {
      // 网络异常保留旧值：轮询失败不应把角标清零造成误导
    }
  }

  /** 单条已读：接口成功后本地递减 */
  async function markOneRead(id: number): Promise<boolean> {
    try {
      await apiMarkAsRead(id)
      unread.value = Math.max(0, unread.value - 1)
      return true
    } catch {
      return false
    }
  }

  /** 全部已读 */
  async function markAllRead(): Promise<boolean> {
    try {
      await apiMarkAll()
      unread.value = 0
      return true
    } catch {
      return false
    }
  }

  function startPolling() {
    if (timer) return
    void refresh(false)
    timer = setInterval(() => void refresh(true), POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  return { unread, refresh, markOneRead, markAllRead, startPolling, stopPolling }
})
