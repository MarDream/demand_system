import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getMe } from '@/api/modules/auth'
import { setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref<any>(null)
  const roles = ref<string[]>([])

  async function login(username: string, password: string) {
    const res = await loginApi(username, password)
    const data = (res as any).data || res
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    token.value = data.accessToken
    await getUserInfo()
  }

  async function getUserInfo() {
    const res = await getMe()
    const data = (res as any).data || res
    userInfo.value = data
    roles.value = data.roles || []
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      removeToken()
      removeRefreshToken()
      token.value = ''
      userInfo.value = null
      roles.value = []
    }
  }

  return { token, userInfo, roles, login, logout, getUserInfo }
})
