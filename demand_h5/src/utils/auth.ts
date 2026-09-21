const TOKEN_KEY = 'h5_access_token'
const REFRESH_TOKEN_KEY = 'h5_refresh_token'
const ACTIVE_ROLE_KEY = 'h5_active_role'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function setRefreshToken(token: string) {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/** 当前生效角色（随每次请求通过 X-Active-Role 头发送；空 = 全部角色并集） */
export function getActiveRole(): string {
  return localStorage.getItem(ACTIVE_ROLE_KEY) || ''
}

export function setActiveRole(role: string) {
  if (role) {
    localStorage.setItem(ACTIVE_ROLE_KEY, role)
  } else {
    localStorage.removeItem(ACTIVE_ROLE_KEY)
  }
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(ACTIVE_ROLE_KEY)
}
