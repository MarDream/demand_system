import request from '@/api/request'
import type { RoleItem, RolePermissionInfo } from '@/api/modules/menu'

export interface RolePayload {
  code: string
  name: string
  description?: string | null
}

export function getRoleList() {
  return request.get<RoleItem[]>('/v1/rbac/roles')
}

export function createRole(data: RolePayload) {
  return request.post<RoleItem>('/v1/rbac/roles', data)
}

export function updateRole(id: number, data: RolePayload) {
  return request.put<RoleItem>(`/v1/rbac/roles/${id}`, data)
}

export function deleteRole(id: number) {
  return request.delete<void>(`/v1/rbac/roles/${id}`)
}

export function getRolePermissions(roleId: number) {
  return request.get<RolePermissionInfo>(`/v1/rbac/roles/${roleId}/permissions`)
}

export function saveRolePermissions(roleId: number, permissionCodes: string[]) {
  return request.put<void>(`/v1/rbac/roles/${roleId}/permissions`, { roleId, permissionCodes })
}

export function getGrantablePermissions() {
  return request.get<string[]>('/v1/rbac/roles/grantable-permissions')
}
