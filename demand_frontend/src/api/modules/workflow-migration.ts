import request from '@/api/request'
import type {
  MigrationPlanVO,
  CreateMigrationPlanRequest,
  NodeMappingItem,
  MigrationPreviewVO,
  MigrationResultDTO,
  WorkflowMigrationLogVO,
} from '@/types/workflow-visual'

/**
 * 创建迁移计划（草稿状态，含自动建议的节点映射）
 */
export function createMigrationPlan(data: CreateMigrationPlanRequest) {
  return request.post<MigrationPlanVO>('/v1/admin/workflow-migration/plans', data) as unknown as Promise<MigrationPlanVO>
}

/**
 * 查询迁移计划列表
 */
export function listMigrationPlans(projectId?: number) {
  return request.get<MigrationPlanVO[]>('/v1/admin/workflow-migration/plans', {
    params: { projectId },
  }) as unknown as Promise<MigrationPlanVO[]>
}

/**
 * 查询迁移计划详情
 */
export function getMigrationPlan(planId: number) {
  return request.get<MigrationPlanVO>(`/v1/admin/workflow-migration/plans/${planId}`) as unknown as Promise<MigrationPlanVO>
}

/**
 * 更新节点映射配置
 */
export function updateNodeMapping(planId: number, mapping: NodeMappingItem[]) {
  return request.put<MigrationPlanVO>(`/v1/admin/workflow-migration/plans/${planId}/mapping`, mapping) as unknown as Promise<MigrationPlanVO>
}

/**
 * 预检迁移计划
 */
export function previewMigration(planId: number) {
  return request.post<MigrationPreviewVO>(`/v1/admin/workflow-migration/plans/${planId}/preview`) as unknown as Promise<MigrationPreviewVO>
}

/**
 * 执行迁移计划
 */
export function executeMigration(planId: number) {
  return request.post<MigrationResultDTO>(`/v1/admin/workflow-migration/plans/${planId}/execute`) as unknown as Promise<MigrationResultDTO>
}

/**
 * 查询迁移日志
 */
export function listMigrationLogs(planId: number) {
  return request.get<WorkflowMigrationLogVO[]>(`/v1/admin/workflow-migration/plans/${planId}/logs`) as unknown as Promise<WorkflowMigrationLogVO[]>
}
