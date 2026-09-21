import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMe, type AuthUserInfo } from '@/api/auth'
import { getToken, clearAuth, getActiveRole, setActiveRole } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<AuthUserInfo | null>(null)

  async function fetchUserInfo() {
    userInfo.value = await getMe()
    syncActiveRole(userInfo.value)
    return userInfo.value
  }

  /**
   * 同步生效角色：后端 /auth/me 返回的 roles 已按 X-Active-Role 收窄。
   * - 本地残留的 activeRole 仍有效（在 allRoles 中）→ 保留，H5 端独立记忆
   * - 残留失效或从未设置 → 清掉（回全部角色并集口径）
   */
  function syncActiveRole(info: AuthUserInfo) {
    const stored = getActiveRole()
    const allRoles = info.allRoles || info.roles || []
    if (stored && allRoles.includes(stored)) {
      return
    }
    setActiveRole('')
  }

  function isLogin() {
    return !!getToken()
  }

  function reset() {
    userInfo.value = null
    clearAuth()
  }

  return { userInfo, fetchUserInfo, isLogin, reset }
})
