import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, logout as logoutApi, getMe, type AuthUserInfo } from '@/api/modules/auth'
import { setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref<AuthUserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const isSuperAdmin = ref(false)

  const hasAdminRole = computed(() => roles.value.includes('admin'))

  async function login(username: string, password: string) {
    const data = await loginApi(username, password) as any
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    token.value = data.accessToken
    await getUserInfo()
  }

  async function getUserInfo() {
    const data = await getMe() as any
    userInfo.value = data
    roles.value = data.roles || []
    permissions.value = data.permissions || []
    isSuperAdmin.value = !!data.isSuperAdmin
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
      permissions.value = []
      isSuperAdmin.value = false
    }
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    isSuperAdmin,
    hasAdminRole,
    login,
    logout,
    getUserInfo,
  }
})
