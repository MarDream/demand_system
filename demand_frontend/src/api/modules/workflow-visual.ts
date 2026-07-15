import request from '@/api/request'
import type {
  WorkflowConfigDTO,
  WorkflowVersionDTO,
  WorkflowVersionActivationDTO,
  WorkflowVersionMetaUpdateDTO,
  WorkflowApprovalDTO,
  ApprovalRequestDTO,
  WorkflowValidationIssue,
  WorkflowValidationReport,
  WorkflowMigrationReport,
  WorkflowExportData,
  WorkflowImportResponse,
} from '@/types/workflow-visual'

export const GLOBAL_WORKFLOW_PROJECT_ID = 0

/**
 * 获取当前工作流配置（节点+连线）
 */
export function getWorkflowConfig(projectId: number) {
  return request.get<WorkflowConfigDTO>(`/v1/workflows/${projectId}/config`) as unknown as Promise<WorkflowConfigDTO>
}

/**
 * 保存工作流配置（草稿）
 */
export function saveWorkflowConfig(projectId: number, config: WorkflowConfigDTO) {
  return request.post<WorkflowVersionDTO>(`/v1/workflows/${projectId}/config`, config) as unknown as Promise<WorkflowVersionDTO>
}

/**
 * 提交审核
 */
export function submitForApproval(projectId: number) {
  return request.post<void>(`/v1/workflows/${projectId}/publish`) as unknown as Promise<void>
}

/**
 * 获取历史版本列表
 */
export function getVersionHistory(projectId: number) {
  return request.get<WorkflowVersionDTO[]>(`/v1/workflows/${projectId}/versions`) as unknown as Promise<WorkflowVersionDTO[]>
}

/**
 * 获取全部已启用工作流版本（需求类型绑定用）
 */
export function listActiveWorkflowVersions() {
  return request.get<WorkflowVersionDTO[]>('/v1/workflows/versions/active') as unknown as Promise<WorkflowVersionDTO[]>
}

/**
 * 获取指定版本配置
 */
export function getVersionConfig(versionId: number) {
  return request.get<WorkflowVersionDTO>(`/v1/workflows/versions/${versionId}`) as unknown as Promise<WorkflowVersionDTO>
}

/**
 * 更新工作流版本元数据
 */
export function updateWorkflowVersionMeta(versionId: number, data: WorkflowVersionMetaUpdateDTO) {
  return request.put<WorkflowVersionDTO>(`/v1/workflows/versions/${versionId}/meta`, data) as unknown as Promise<WorkflowVersionDTO>
}

/**
 * 更新工作流版本启停状态
 */
export function updateWorkflowVersionActivation(versionId: number, data: WorkflowVersionActivationDTO) {
  return request.put<WorkflowVersionDTO>(`/v1/workflows/versions/${versionId}/activation`, data) as unknown as Promise<WorkflowVersionDTO>
}

/**
 * 删除工作流版本
 */
export function deleteWorkflowVersion(versionId: number) {
  return request.delete<void>(`/v1/workflows/versions/${versionId}`) as unknown as Promise<void>
}

/**
 * 获取待审核列表（仅超级管理员）
 */
export function getPendingApprovals() {
  return request.get<WorkflowApprovalDTO[]>('/v1/workflow-approvals/pending') as unknown as Promise<WorkflowApprovalDTO[]>
}

/**
 * 获取全部审核记录（仅超级管理员）
 */
export function getWorkflowApprovals() {
  return request.get<WorkflowApprovalDTO[]>('/v1/workflow-approvals') as unknown as Promise<WorkflowApprovalDTO[]>
}

/**
 * 审核通过
 */
export function approveWorkflow(id: number, data: ApprovalRequestDTO) {
  return request.post<void>(`/v1/workflow-approvals/${id}/approve`, data) as unknown as Promise<void>
}

/**
 * 审核拒绝
 */
export function rejectWorkflow(id: number, data: ApprovalRequestDTO) {
  return request.post<void>(`/v1/workflow-approvals/${id}/reject`, data) as unknown as Promise<void>
}

/**
 * 删除单条审核记录
 */
export function deleteWorkflowApproval(id: number) {
  return request.delete<void>(`/v1/workflow-approvals/${id}`) as unknown as Promise<void>
}

/**
 * 清空全部审核记录
 */
export function clearAllWorkflowApprovals() {
  return request.delete<void>('/v1/workflow-approvals') as unknown as Promise<void>
}

export function validateBeforeSubmit(projectId: number) {
  return request.post<WorkflowValidationReport>(`/v1/workflows/${projectId}/validate-before-submit`) as unknown as Promise<WorkflowValidationReport>
}

export function validateWorkflowVersionReport(versionId: number) {
  return request.post<WorkflowValidationReport>(`/v1/workflows/versions/${versionId}/validate/report`) as unknown as Promise<WorkflowValidationReport>
}

export function validateWorkflowVersion(versionId: number) {
  return request.post<WorkflowValidationIssue[]>(`/v1/workflows/versions/${versionId}/validate`) as unknown as Promise<WorkflowValidationIssue[]>
}

/**
 * 预校验工作流配置（不持久化，用于保存草稿前的提示）
 */
export function validateWorkflowConfig(config: WorkflowConfigDTO) {
  return request.post<WorkflowValidationReport>('/v1/workflows/validate-config', config) as unknown as Promise<WorkflowValidationReport>
}

export function markLegacyWorkflowRequirements() {
  return request.post<WorkflowMigrationReport>('/v1/admin/workflow-migration/mark-legacy') as unknown as Promise<WorkflowMigrationReport>
}

export function backfillWorkflowInstances() {
  return request.post<WorkflowMigrationReport>('/v1/admin/workflow-migration/backfill-instances') as unknown as Promise<WorkflowMigrationReport>
}

/**
 * 导出工作流（审核通过的版本）
 */
export function exportWorkflowVersion(versionId: number) {
  return request.get<WorkflowExportData>(`/v1/workflow/versions/${versionId}/export`) as unknown as Promise<WorkflowExportData>
}

/**
 * 导入工作流
 */
export function importWorkflowVersion(data: WorkflowExportData, targetProjectId?: number) {
  return request.post<WorkflowImportResponse>(
    '/v1/workflow/import',
    data,
    { params: { projectId: targetProjectId ?? GLOBAL_WORKFLOW_PROJECT_ID } },
  ) as unknown as Promise<WorkflowImportResponse>
}
