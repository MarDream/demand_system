import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { User, UserQuery, Region, Department, Position } from '@/types/user'

export function getUserList(params: UserQuery) {
  return request.get<ApiResponse<PageResult<User>>>('/v1/users', { params })
}

export function getUserById(id: number) {
  return request.get<ApiResponse<User>>(`/v1/users/${id}`)
}

export function createUser(data: Partial<User>) {
  return request.post<ApiResponse>('/v1/users', data)
}

export function updateUser(id: number, data: Partial<User>) {
  return request.put<ApiResponse>(`/v1/users/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete<ApiResponse>(`/v1/users/${id}`)
}

export function getRegionTree() {
  return request.get<ApiResponse<Region[]>>('/v1/regions/tree')
}

export function getDepartmentTree() {
  return request.get<ApiResponse<Department[]>>('/v1/departments/tree')
}

export function getPositionList() {
  return request.get<ApiResponse<Position[]>>('/v1/positions')
}

// Region CRUD
export function createRegion(data: any) {
  return request.post<ApiResponse>('/v1/regions', data)
}
export function updateRegion(id: number, data: any) {
  return request.put<ApiResponse>(`/v1/regions/${id}`, data)
}
export function deleteRegion(id: number) {
  return request.delete<ApiResponse>(`/v1/regions/${id}`)
}

// Department CRUD
export function createDepartment(data: any) {
  return request.post<ApiResponse>('/v1/departments', data)
}
export function updateDepartment(id: number, data: any) {
  return request.put<ApiResponse>(`/v1/departments/${id}`, data)
}
export function deleteDepartment(id: number) {
  return request.delete<ApiResponse>(`/v1/departments/${id}`)
}

// Position CRUD
export function createPosition(data: any) {
  return request.post<ApiResponse>('/v1/positions', data)
}
export function updatePosition(id: number, data: any) {
  return request.put<ApiResponse>(`/v1/positions/${id}`, data)
}
export function deletePosition(id: number) {
  return request.delete<ApiResponse>(`/v1/positions/${id}`)
}
