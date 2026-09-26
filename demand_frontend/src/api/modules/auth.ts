import request from '@/api/request'

export interface AuthUserInfo {
  id: number
  username: string
  realName: string
  email: string
  phone?: string
  avatar: string
  roles: string[]
  roleNames?: string[]
  permissions: string[]
  isSuperAdmin: boolean
  regionId?: number
  departmentId?: number
  orgId?: number
  /** 无组织用户首次登录需强制选择组织 */
  needOrgBind?: boolean
  positionId?: number
  /** 用户全部角色编码（供角色切换下拉，不受激活角色影响） */
  allRoles?: string[]
  /** 与 allRoles 一一对应的显示名 */
  allRoleNames?: string[]
  /** 当前生效的激活角色（未切换时为空） */
  activeRole?: string | null
  /** 工号（只读展示） */
  jobNumber?: string | null
  /** 所属组织名称（只读展示） */
  orgName?: string | null
  /** 外观设置 JSON 字符串（{mode,primary,radius}），null = 未自定义 */
  appearanceConfig?: string | null
}

export function login(username: string, password: string) {
  return request.post<{
    accessToken: string
    refreshToken: string
    expiresIn: number
    needOrgBind?: boolean
  }>('/v1/auth/login', { username, password })
}

export function logout() {
  return request.post('/v1/auth/logout')
}

export function refreshToken(refreshToken: string) {
  return request.post<{ accessToken: string; refreshToken: string }>('/v1/auth/refresh', { refreshToken })
}

export function getMe() {
  return request.get<AuthUserInfo>('/v1/auth/me')
}

/** 个人设置：仅允许修改本人邮箱/手机号/头像，其余信息只读 */
export function updateProfile(data: { email: string; phone?: string; avatar?: string }) {
  return request.put<AuthUserInfo>('/v1/auth/profile', data)
}

/** 个人设置：保存本人外观配置（主题模式/主题色/圆角档位/侧边栏风格），跟随账号持久化 */
export function saveAppearance(data: { mode: string; primary: string; radius: string; sidebar?: string }) {
  return request.put<void>('/v1/auth/appearance', data)
}

/** 个人设置：验证旧密码后修改本人密码 */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put<void>('/v1/auth/password', data)
}

export function bindOrg(orgId: number) {
  return request.post<void>('/v1/auth/bind-org', { orgId })
}

export function register(data: {
  username: string
  realName: string
  email: string
  password: string
  regionId?: number
  departmentId?: number
  positionId?: number
}) {
  return request.post('/v1/auth/register', data)
}

export function requestPasswordReset(data: { username?: string; email?: string }) {
  return request.post('/v1/auth/password-reset/request', data)
}

export function resetPassword(data: { token: string; newPassword: string }) {
  return request.post('/v1/auth/password-reset/confirm', data)
}
