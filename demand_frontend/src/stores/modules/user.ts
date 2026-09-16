import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, logout as logoutApi, getMe, bindOrg as bindOrgApi, type AuthUserInfo } from '@/api/modules/auth'
import { setToken, removeToken, setRefreshToken, removeRefreshToken, getActiveRole, setActiveRole } from '@/utils/auth'

export interface RoleOption {
  code: string
  name: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref<AuthUserInfo | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const isSuperAdmin = ref(false)
  /** 是否需要强制选择组织（无组织用户首次登录） */
  const needOrgBind = ref(false)
  /** 角色切换：用户全部角色（供下拉选择）与当前生效角色（空 = 全部角色权限） */
  const allRoles = ref<RoleOption[]>([])
  const activeRole = ref<string>('')

  const hasAdminRole = computed(() => roles.value.includes('admin'))

  async function login(username: string, password: string) {
    const data = await loginApi(username, password) as any
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    token.value = data.accessToken
    // 新会话不继承上一个会话的激活角色
    setActiveRole('')
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
    const codes: string[] = data.allRoles || data.roles || []
    const names: string[] = data.allRoleNames || []
    // 同名角色只保留一个切换项（后进优先，如 SUPER_ADMIN 覆盖同名 legacy admin）
    const byName = new Map<string, RoleOption>()
    codes.forEach((code, i) => {
      byName.set(names[i] || code, { code, name: names[i] || code })
    })
    allRoles.value = Array.from(byName.values())
    // 校验本地残留的激活角色是否仍有效（角色可能被移除或被同名去重合并）
    const stored = data.activeRole || getActiveRole()
    const validActive = stored && allRoles.value.some((r) => r.code === stored)
    activeRole.value = validActive ? stored : ''
    if (!validActive) {
      setActiveRole('')
    }
    // 没有「全部角色权限」默认态：多角色用户必须处于某个具体角色下，未选时取第一个
    if (allRoles.value.length > 1 && !activeRole.value) {
      await switchRole(allRoles.value[0].code)
    }
  }

  /** 切换当前生效角色（多角色用户始终处于某个具体角色下） */
  async function switchRole(roleCode: string) {
    if (!roleCode) return
    setActiveRole(roleCode)
    await getUserInfo()
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
      setActiveRole('')
      token.value = ''
      userInfo.value = null
      roles.value = []
      permissions.value = []
      isSuperAdmin.value = false
      needOrgBind.value = false
      allRoles.value = []
      activeRole.value = ''
    }
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    isSuperAdmin,
    needOrgBind,
    allRoles,
    activeRole,
    hasAdminRole,
    login,
    logout,
    getUserInfo,
    switchRole,
    bindOrg,
  }
})
