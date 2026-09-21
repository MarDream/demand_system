import request from '@/api/request'
import type { PageResult } from '@/types/api'
import type { User, UserQuery, Position, OrgNode, RosterStats, RosterImportResult, RosterExportLog } from '@/types/user'
import axios from 'axios'
import { getToken } from '@/utils/auth'

export function getUserList(params: UserQuery) {
  return request.get<PageResult<User>>('/v1/users', { params })
}

/** 花名册统计板（在职/员工类型/用工状态分布），随筛选范围联动 */
export function getRosterStats(params: UserQuery) {
  return request.get<RosterStats>('/v1/users/roster-stats', { params }) as unknown as Promise<RosterStats>
}

/** 花名册导入模板下载（XLSX Blob） */
export function downloadRosterImportTemplate() {
  const baseURL = import.meta.env.VITE_API_BASE_URL
  return axios.get(`${baseURL}/v1/users/import-template`, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${getToken()}` },
    timeout: 60000,
  })
}

/** 导入花名册（XLSX 文件 + 统一归属部门） */
export function importRoster(file: File, orgId?: number) {
  const form = new FormData()
  form.append('file', file)
  if (orgId != null) form.append('orgId', String(orgId))
  return request.post<RosterImportResult>('/v1/users/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  }) as unknown as Promise<RosterImportResult>
}

/** 导出花名册（按当前筛选生成 XLSX，写入导出历史），返回历史条目 */
export function exportRoster(params: UserQuery) {
  return request.post<RosterExportLog>('/v1/users/export', params) as unknown as Promise<RosterExportLog>
}

/** 导出历史（分页倒序） */
export function getRosterExportHistory(pageNum = 1, pageSize = 10) {
  return request.get<PageResult<RosterExportLog>>('/v1/users/export-history', {
    params: { pageNum, pageSize },
  }) as unknown as Promise<PageResult<RosterExportLog>>
}

/** 删除导出历史 */
export function deleteRosterExportLog(id: number) {
  return request.delete<void>(`/v1/users/export-history/${id}`)
}

/** 下载历史导出文件（XLSX Blob） */
export function downloadRosterExportLog(id: number) {
  const baseURL = import.meta.env.VITE_API_BASE_URL
  return axios.get(`${baseURL}/v1/users/export-history/${id}/download`, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${getToken()}` },
    timeout: 120000,
  })
}

/** 获取活跃用户列表（仅 id/username/realName/avatar），供前端筛选框、成员选择器使用 */
export function getFilterUsers() {
  return request.get<Array<{ id: number; username: string; realName: string; avatar?: string | null }>>('/v1/users/active')
}

export function getUserById(id: number) {
  return request.get<User>('/v1/users/' + id)
}

export function createUser(data: Partial<User>) {
  return request.post<void>('/v1/users', data)
}

export function updateUser(id: number, data: Partial<User>) {
  return request.put<void>('/v1/users/' + id, data)
}

export function deleteUser(id: number) {
  return request.delete<void>('/v1/users/' + id)
}

/** 批量启用 / 批量停用，返回实际生效的用户数 */
export function batchUpdateUserStatus(ids: number[], status: 'active' | 'inactive') {
  return request.post<number>('/v1/users/batch/status', { ids, status }) as unknown as Promise<number>
}

/** 批量删除，返回实际删除的用户数 */
export function batchDeleteUsers(ids: number[]) {
  return request.post<number>('/v1/users/batch/delete', { ids }) as unknown as Promise<number>
}

export function sendInitialPassword(id: number) {
  return request.post<string>('/v1/users/' + id + '/send-init-password')
}

export function getUserRoles(id: number) {
  return request.get<number[]>('/v1/users/' + id + '/roles')
}

export function assignRoles(id: number, roleIds: number[]) {
  return request.put<void>('/v1/users/' + id + '/roles', roleIds)
}

export function getPositionList() {
  return request.get<Position[]>('/v1/positions')
}

export function createPosition(data: any) {
  return request.post<void>('/v1/positions', data)
}

export function updatePosition(id: number, data: any) {
  return request.put<void>('/v1/positions/' + id, data)
}

export function deletePosition(id: number) {
  return request.delete<void>('/v1/positions/' + id)
}

// Unified Org API
export function getOrgTree() {
  return request.get<OrgNode[]>('/v1/org/tree')
}

export function getOrgDetail(id: number) {
  return request.get<OrgNode>('/v1/org/' + id)
}

export function createOrg(data: any) {
  return request.post<void>('/v1/org', data)
}

export function updateOrg(id: number, data: any) {
  return request.put<void>('/v1/org/' + id, data)
}

export function deleteOrg(id: number) {
  return request.delete<void>('/v1/org/' + id)
}

export function moveOrgNode(data: { id: number; targetParentId: number | null; targetSortOrder: number }) {
  return request.put<void>('/v1/org/move', data)
}
