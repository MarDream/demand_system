import request from '@/api/request'

export interface AuthUserInfo {
  id: number
  username: string
  realName: string
  email: string
  avatar: string
  roles: string[]
  roleNames?: string[]
  permissions: string[]
  isSuperAdmin: boolean
  regionId?: number
  departmentId?: number
  positionId?: number
}

export function login(username: string, password: string) {
  return request.post<{ accessToken: string; refreshToken: string; expiresIn: number }>('/v1/auth/login', { username, password })
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
