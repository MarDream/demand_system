import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, logout as logoutApi, getMe, bindOrg as bindOrgApi, type AuthUserInfo } from '@/api/modules/auth'
import { setToken, removeToken, setRefreshToken, removeRefreshToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref<AuthUserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const isSuperAdmin = ref(false)
  /** 是否需要强制选择组织（无组织用户首次登录） */
  const needOrgBind = ref(false)

  const hasAdminRole = computed(() => roles.value.includes('admin'))

  async function login(username: string, password: string) {
    const data = await loginApi(username, password) as any
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    token.value = data.accessToken
    needOrgBind.value = !!data.needOrgBind
    await getUserInfo()
    // getUserInfo 拿到的是最新 needOrgBind，覆盖一次以保持一致
    needOrgBind.value = !!userInfo.value?.needOrgBind
  }

  async function getUserInfo() {
    const data = await getMe() as any
    userInfo.value = data
    roles.value = data.roles || []
    permissions.value = data.permissions || []
    isSuperAdmin.value = !!data.isSuperAdmin
  }

  async function bindOrg(orgId: number) {
    await bindOrgApi(orgId)
    needOrgBind.value = false
    await getUserInfo()
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
      needOrgBind.value = false
    }
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    isSuperAdmin,
    needOrgBind,
    hasAdminRole,
    login,
    logout,
    getUserInfo,
    bindOrg,
  }
})
