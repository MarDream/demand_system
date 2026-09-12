import { get, post } from './request'

export interface AuthUserInfo {
  id: number
  username: string
  realName: string
  email: string
  phone?: string
  avatar: string
  roles: string[]
  roleNames?: string[]
  orgId?: number | null
  needOrgBind?: boolean
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  needOrgBind?: boolean
}

export function login(username: string, password: string) {
  return post<LoginResult>('/v1/auth/login', { username, password })
}

export function logout() {
  return post<void>('/v1/auth/logout')
}

export function getMe() {
  return get<AuthUserInfo>('/v1/auth/me')
}

export interface OrgNode {
  id: number
  name: string
  parentId?: number | null
  children?: OrgNode[]
}

export function getOrgTree() {
  return get<OrgNode[]>('/v1/org/tree')
}

export function bindOrg(orgId: number) {
  return post<void>('/v1/auth/bind-org', { orgId })
}
