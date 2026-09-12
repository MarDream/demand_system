import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMe, type AuthUserInfo } from '@/api/auth'
import { getToken, clearAuth } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<AuthUserInfo | null>(null)

  async function fetchUserInfo() {
    userInfo.value = await getMe()
    return userInfo.value
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
