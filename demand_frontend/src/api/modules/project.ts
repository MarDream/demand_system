import request from '@/api/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Project, ProjectImportResult, ProjectMember } from '@/types/project'

export interface ProjectPayload {
  name: string
  projectCode?: string | null
  description?: string | null
  companyId?: number | null
  team?: string | null
  leaderId?: number | null
  startDate?: string | null
  endDate?: string | null
  contactPhone?: string | null
  status?: string
}

export function getProjectList(params: { name?: string; status?: string; pageNum: number; pageSize: number }) {
  return request.get<ApiResponse<PageResult<Project>>>('/v1/projects', { params })
}

export function getProjectById(id: number) {
  return request.get<ApiResponse<Project>>(`/v1/projects/${id}`)
}

export function createProject(data: ProjectPayload) {
  return request.post<ApiResponse>('/v1/projects', data)
}

export function updateProject(id: number, data: ProjectPayload) {
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

export function downloadProjectTemplate() {
  return request.get('/v1/projects/template', { responseType: 'blob' }) as unknown as Promise<Blob>
}

export function importProjects(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<ProjectImportResult>>('/v1/projects/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }) as unknown as Promise<ProjectImportResult>
}
