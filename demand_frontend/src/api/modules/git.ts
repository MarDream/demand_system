import request from '@/api/request'
import type { GitTestResult } from '@/types/git'
import type {
  GitPlatform,
  GitRepository,
  BranchProtectionRule,
  ProtectionRuleSet,
  MergeRequest,
  GitAuditLog,
} from '@/types/git'

// ==================== 平台管理 ====================

export function getGitPlatforms() {
  return request.get<GitPlatform[]>('/v1/git/platforms')
}

export function createGitPlatform(data: Partial<GitPlatform>) {
  return request.post<GitPlatform>('/v1/git/platforms', data)
}

export function updateGitPlatform(id: number, data: Partial<GitPlatform>) {
  return request.put<GitPlatform>(`/v1/git/platforms/${id}`, data)
}

export function deleteGitPlatform(id: number) {
  return request.delete<void>(`/v1/git/platforms/${id}`)
}

export function testGitPlatform(id: number) {
  return request.post<GitTestResult>(`/v1/git/platforms/${id}/test`)
}

// ==================== 仓库管理 ====================

export interface GitRepositoryQuery {
  keyword?: string
  platformId?: number
  projectId?: number
  status?: string
}

export function getGitRepositories(params?: GitRepositoryQuery) {
  return request.get<GitRepository[]>('/v1/git/repositories', { params })
}

export function createGitRepository(data: Partial<GitRepository>) {
  return request.post<GitRepository>('/v1/git/repositories', data)
}

export function getGitRepository(id: number) {
  return request.get<GitRepository>(`/v1/git/repositories/${id}`)
}

export function updateGitRepository(id: number, data: Partial<GitRepository>) {
  return request.put<GitRepository>(`/v1/git/repositories/${id}`, data)
}

export function archiveGitRepository(id: number) {
  return request.post<void>(`/v1/git/repositories/${id}/archive`)
}

export function deleteGitRepository(id: number) {
  return request.delete<void>(`/v1/git/repositories/${id}`)
}

// ==================== 分支保护规则 ====================

export function getProtectionRules(repoId: number) {
  return request.get<BranchProtectionRule[]>(`/v1/git/repositories/${repoId}/protection/rules`)
}

export function createProtectionRule(repoId: number, data: Partial<BranchProtectionRule>) {
  return request.post<BranchProtectionRule>(`/v1/git/repositories/${repoId}/protection/rules`, data)
}

export function updateProtectionRule(id: number, data: Partial<BranchProtectionRule>) {
  return request.put<BranchProtectionRule>(`/v1/git/protection/rules/${id}`, data)
}

export function deleteProtectionRule(id: number) {
  return request.delete<void>(`/v1/git/protection/rules/${id}`)
}

export function toggleProtectionRule(id: number, enabled: boolean) {
  return request.put<void>(`/v1/git/protection/rules/${id}/toggle`, null, { params: { enabled } })
}

// ==================== 规则集 ====================

export function getRuleSets() {
  return request.get<ProtectionRuleSet[]>('/v1/git/protection/rule-sets')
}

export function createRuleSet(data: Partial<ProtectionRuleSet>) {
  return request.post<ProtectionRuleSet>('/v1/git/protection/rule-sets', data)
}

export function applyRuleSet(id: number, repoIds: number[]) {
  return request.post<void>(`/v1/git/protection/rule-sets/${id}/apply`, null, { params: { repoIds: repoIds.join(',') } })
}

// ==================== 合并请求 ====================

export interface MergeRequestQuery {
  status?: string
}

export function getMergeRequests(repoId: number, params?: MergeRequestQuery) {
  return request.get<MergeRequest[]>(`/v1/git/repositories/${repoId}/merge-requests`, { params })
}

export function createMergeRequest(repoId: number, data: Partial<MergeRequest>) {
  return request.post<MergeRequest>(`/v1/git/repositories/${repoId}/merge-requests`, data)
}

export function getMergeRequest(id: number) {
  return request.get<MergeRequest>(`/v1/git/merge-requests/${id}`)
}

export function approveMergeRequest(id: number) {
  return request.post<MergeRequest>(`/v1/git/merge-requests/${id}/approve`)
}

export function requestChangesMergeRequest(id: number) {
  return request.post<MergeRequest>(`/v1/git/merge-requests/${id}/request-changes`)
}

export function mergeMergeRequest(id: number) {
  return request.post<MergeRequest>(`/v1/git/merge-requests/${id}/merge`)
}

// ==================== 审计日志 ====================

export interface GitAuditLogQuery {
  keyword?: string
  operatorId?: number
  targetType?: string
  action?: string
}

export function getGitAuditLogs(params?: GitAuditLogQuery) {
  return request.get<GitAuditLog[]>('/v1/git/audit-logs', { params })
}
