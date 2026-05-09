import request from '@/api/request'
import type {
  WorkflowConfigDTO,
  WorkflowVersionDTO,
  WorkflowApprovalDTO,
  ApprovalRequestDTO
} from '@/types/workflow-visual'

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
  return request.post<void>(`/v1/workflows/${projectId}/config`, config) as unknown as Promise<void>
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
 * 获取指定版本配置
 */
export function getVersionConfig(versionId: number) {
  return request.get<WorkflowVersionDTO>(`/v1/workflows/versions/${versionId}`) as unknown as Promise<WorkflowVersionDTO>
}

/**
 * 获取待审核列表（仅超级管理员）
 */
export function getPendingApprovals() {
  return request.get<WorkflowApprovalDTO[]>('/v1/workflow-approvals/pending') as unknown as Promise<WorkflowApprovalDTO[]>
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
