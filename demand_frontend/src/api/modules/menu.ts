import request from '@/api/request'

export interface MenuItem {
  id: number
  parentId: number
  name: string
  menuType: string
  path?: string
  routeName?: string
  component?: string
  icon?: string
  sortOrder: number
  permissionCode?: string
  visible: number
  enabled: number
  keepAlive: number
  remark?: string
  children: MenuItem[]
}

export interface MenuPayload {
  parentId?: number
  name: string
  menuType: string
  path?: string
  routeName?: string
  component?: string
  icon?: string
  sortOrder?: number
  permissionCode?: string
  visible?: number
  enabled?: number
  keepAlive?: number
  remark?: string
}

export function getAllMenus() {
  return request.get<MenuItem[]>('/v1/rbac/menus')
}

export function getCurrentMenus() {
  return request.get<MenuItem[]>('/v1/rbac/menus/current')
}

export function getMenuDetail(id: number) {
  return request.get<MenuItem>(`/v1/rbac/menus/${id}`)
}

export function createMenu(data: MenuPayload) {
  return request.post('/v1/rbac/menus', data)
}

export function updateMenu(id: number, data: MenuPayload) {
  return request.put(`/v1/rbac/menus/${id}`, data)
}

export function deleteMenu(id: number) {
  return request.delete(`/v1/rbac/menus/${id}`)
}

export interface MenuSortItem {
  id: number
  parentId: number
  sortOrder: number
}

export function batchSortMenu(items: MenuSortItem[]) {
  return request.put('/v1/rbac/menus/batch-sort', items)
}

export interface RoleItem {
  id: number
  code: string
  name: string
  description: string
  isSystem: number
}

export function getRoleList() {
  return request.get<RoleItem[]>('/v1/rbac/roles')
}

export interface RolePermissionInfo {
  roleId: number
  roleCode: string
  roleName: string
  permissionCodes: string[]
  grantablePermissionCodes: string[]
}

export function getRolePermissions(roleId: number) {
  return request.get<RolePermissionInfo>(`/v1/rbac/roles/${roleId}/permissions`)
}

export function saveRolePermissions(roleId: number, permissionCodes: string[]) {
  return request.put(`/v1/rbac/roles/${roleId}/permissions`, { roleId, permissionCodes })
}

export function getGrantablePermissions() {
  return request.get<string[]>('/v1/rbac/roles/grantable-permissions')
}
