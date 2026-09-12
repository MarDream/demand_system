/**
 * 通用状态 → Element Plus tag type 映射
 *
 * 抽取自 iterations/home/settings 等页面各自手写的
 * getStatusType / statusMap 重复实现。
 * 需求域的专属映射见 composables/useRequirementTag.ts。
 */

export type StatusTagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/** 各模块通用状态枚举 → tag type（覆盖中英文常见枚举） */
const COMMON_STATUS_TYPE_MAP: Record<string, StatusTagType> = {
  // 迭代 / 生命周期
  not_started: 'info',
  未开始: 'info',
  in_progress: 'primary',
  进行中: 'primary',
  completed: 'success',
  已完成: 'success',
  已上线: 'success',
  已发布: 'success',
  closed: 'warning',
  已关闭: 'warning',
  已结束: 'warning',
  // 项目
  active: 'success',
  archived: 'info',
  expired: 'warning',
  已截止: 'warning',
  // 启用类
  enabled: 'success',
  disabled: 'info',
  启用: 'success',
  停用: 'info',
  启用中: 'success',
  // 连接类（Git 平台等）
  CONNECTED: 'success',
  DISCONNECTED: 'danger',
  EXPIRED: 'warning',
  已连接: 'success',
  未连接: 'danger',
  // 成功 / 失败
  success: 'success',
  failed: 'danger',
  error: 'danger',
  pending: 'warning',
  running: 'primary',
  成功: 'success',
  失败: 'danger',
  已逾期: 'danger',
}

/**
 * 查询通用状态的 tag type，未命中回退 info。
 * 支持传入局部覆盖映射（只覆盖需要的键）。
 */
export function commonStatusType(
  status?: string | null,
  overrides?: Record<string, StatusTagType>,
): StatusTagType {
  if (!status) return 'info'
  const value = String(status).trim()
  if (overrides && value in overrides) return overrides[value]
  return COMMON_STATUS_TYPE_MAP[value] ?? 'info'
}
