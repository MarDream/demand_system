/**
 * 需求管理模块统一的标签 / 状态映射 composable
 *
 * 抽取自 views/requirements/{index,detail}.vue 与
 * views/requirements/components/RequirementDetailHeader.vue
 * 三处重复的 status / priority / approvalResult 颜色映射逻辑。
 *
 * 使用方式：
 *   import { useRequirementTag } from '@/composables/useRequirementTag'
 *   const { statusTagType, priorityTagType, approvalResultTagType } = useRequirementTag()
 */

import { stripPriorityPrefix, normalizeText } from '@/utils/format'

export type ElementPlusTagType = '' | 'success' | 'info' | 'warning' | 'danger' | 'primary' | undefined
export type TimelineItemType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** 状态 → 颜色映射（中文状态 + 后端枚举） */
const STATUS_TAG_MAP: Record<string, ElementPlusTagType> = {
  // 中文业务状态
  新建: 'info',
  待分析: 'warning',
  待确认: 'warning',
  待评审: 'warning',
  评审中: 'warning',
  已通过: 'success',
  开发中: 'primary',
  测试中: 'info',
  已上线: 'success',
  已验收: 'success',
  已取消: 'info',
  已拒绝: 'danger',
  打回: 'danger',
  测试不通过: 'danger',
  验收不通过: 'danger',
  // 后端枚举（兼容）
  PENDING_REVIEW: 'warning',
  REJECTED: 'danger',
  SENT_BACK: 'danger',
  TEST_FAILED: 'danger',
  ACCEPT_FAILED: 'danger',
}

/** 优先级 → 颜色映射 */
const PRIORITY_TAG_MAP: Record<string, ElementPlusTagType> = {
  P0: 'danger',
  P1: 'warning',
  P2: 'info',
  P3: 'success',
  // 支持中文标签
  '紧急': 'danger',
  '高': 'warning',
  '中': undefined,
  '低': 'info',
}

/** 审核结果 → 颜色映射 */
const APPROVAL_RESULT_TAG_MAP: Record<string, ElementPlusTagType> = {
  SUBMIT: 'primary',
  PASS: 'success',
  REJECT: 'danger',
  CANCEL: 'warning',
}

/** 状态可读映射（用于 tag / 工具提示） */
const STATUS_LABEL_MAP: Record<string, string> = {
  新建: '新建',
  待分析: '待分析',
  待确认: '待确认',
  待评审: '待评审',
  评审中: '评审中',
  已通过: '已通过',
  开发中: '开发中',
  测试中: '测试中',
  已上线: '已上线',
  已验收: '已验收',
  已取消: '已取消',
  已拒绝: '已拒绝',
  打回: '打回',
  测试不通过: '测试不通过',
  验收不通过: '验收不通过',
  PENDING_REVIEW: '待评审',
  REJECTED: '已拒绝',
  SENT_BACK: '已打回',
  TEST_FAILED: '测试不通过',
  ACCEPT_FAILED: '验收不通过',
}

export function useRequirementTag() {
  /** 状态 → Element Plus tag type */
  function statusTagType(status?: string | null): ElementPlusTagType {
    if (!status) return 'info'
    return STATUS_TAG_MAP[status] ?? 'info'
  }

  /** 优先级 → Element Plus tag type */
  function priorityTagType(priority?: string | null): ElementPlusTagType {
    if (!priority) return 'info'

    // 先提取中文标签（处理 P0-紧急 格式）
    const label = stripPriorityPrefix(normalizeText(priority))

    // 尝试用中文标签映射（注意：undefined 也是有效值）
    if (label in PRIORITY_TAG_MAP) {
      return PRIORITY_TAG_MAP[label]
    }

    // 尝试用原始值映射（P0、P1等）
    if (priority in PRIORITY_TAG_MAP) {
      return PRIORITY_TAG_MAP[priority]
    }

    // 处理英文优先级（High、low等）
    const lowerPriority = priority.toLowerCase()
    if (lowerPriority === 'urgent' || lowerPriority === 'high') {
      return 'warning'
    } else if (lowerPriority === 'medium' || lowerPriority === 'middle') {
      return undefined
    } else if (lowerPriority === 'low') {
      return 'info'
    }

    return 'info'
  }

  /** 审核结果 → Element Plus tag type */
  function approvalResultTagType(result?: string | null): ElementPlusTagType {
    if (!result) return 'info'
    return APPROVAL_RESULT_TAG_MAP[result] ?? 'info'
  }

  /** 审核结果 → Element Plus timeline-item type */
  function approvalTimelineItemType(result?: string | null): TimelineItemType {
    const type = approvalResultTagType(result)
    const allowed: TimelineItemType[] = ['primary', 'success', 'warning', 'danger', 'info']
    return allowed.includes(type as TimelineItemType) ? (type as TimelineItemType) : 'info'
  }

  /** 状态码 → 可读中文（无则回退原文） */
  function requirementStatusLabel(status?: string | null): string {
    if (!status) return '-'
    return STATUS_LABEL_MAP[status] ?? status
  }

  return {
    statusTagType,
    priorityTagType,
    approvalResultTagType,
    approvalTimelineItemType,
    requirementStatusLabel,
  }
}
