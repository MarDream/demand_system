import request from '@/api/request'
import type { RoleItem, RolePermissionInfo } from '@/api/modules/menu'
import type { ApiResponse } from '@/types/api'

// Role group and role batch sort APIs

export interface RoleGroupItem {
  id: number
  name: string
  description?: string | null
  sortOrder?: number | null
}

export interface RolePayload {
  code: string
  name: string
  description?: string | null
  roleGroupId?: number | null
}

export interface RoleGroupSortItem {
  id: number
  sortOrder: number
}

export interface RoleSortItem {
  id: number
  roleGroupId: number | null
  sortOrder: number
}

export function getRoleList() {
  return request.get<RoleItem[]>('/v1/rbac/roles')
}

export function getRoleGroups() {
  return request.get<RoleGroupItem[]>('/v1/rbac/roles/groups')
}

export function createRoleGroup(data: { name: string; description?: string | null; roleIds?: number[] }) {
  return request.post<RoleGroupItem>('/v1/rbac/roles/groups', data)
}

export function updateRoleGroup(id: number, data: { name: string; description?: string | null }) {
  return request.put<RoleGroupItem>(`/v1/rbac/roles/groups/${id}`, data)
}

export function deleteRoleGroup(id: number) {
  return request.delete<void>(`/v1/rbac/roles/groups/${id}`)
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

export function batchSortRoleGroups(items: RoleGroupSortItem[]) {
  return request.put<ApiResponse<void>>('/v1/rbac/roles/groups/batch-sort', items)
}

export function batchSortRoles(items: RoleSortItem[]) {
  return request.put<ApiResponse<void>>('/v1/rbac/roles/batch-sort', items)
}
