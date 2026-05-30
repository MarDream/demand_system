import request from '@/api/request'
import type { PageResult } from '@/types/api'
import type { User, UserQuery, Position, OrgNode } from '@/types/user'

export function getUserList(params: UserQuery) {
  return request.get<PageResult<User>>('/v1/users', { params })
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
