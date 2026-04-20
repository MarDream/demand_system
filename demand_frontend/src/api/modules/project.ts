import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Project, ProjectMember } from '@/types/project'

export function getProjectList(params: { name?: string; pageNum: number; pageSize: number }) {
  return request.get<ApiResponse<PageResult<Project>>>('/v1/projects', { params })
}

export function getProjectById(id: number) {
  return request.get<ApiResponse<Project>>(`/v1/projects/${id}`)
}

export function createProject(data: { name: string; description: string }) {
  return request.post<ApiResponse>('/v1/projects', data)
}

export function updateProject(id: number, data: { name: string; description: string; status: string }) {
  return request.put<ApiResponse>(`/v1/projects/${id}`, data)
}

export function deleteProject(id: number) {
  return request.delete<ApiResponse>(`/v1/projects/${id}`)
}

export function getProjectMembers(projectId: number) {
  return request.get<ApiResponse<ProjectMember[]>>(`/v1/projects/${projectId}/members`)
}

export function addProjectMember(projectId: number, data: { userId: number; role: string }) {
  return request.post<ApiResponse>(`/v1/projects/${projectId}/members`, data)
}

export function removeProjectMember(projectId: number, userId: number) {
  return request.delete<ApiResponse>(`/v1/projects/${projectId}/members/${userId}`)
}
