/**
 * 需求管理模块统一的标签 / 状态映射 composable
 *
 * 抽取自 views/requirements/{index,detail}.vue 与
 * views/requirements/components/RequirementDetailHeader.vue
 * 三处重复的 status / priority / approvalResult 颜色映射逻辑。
 *
 * 优先级相关函数支持传入动态配置映射（来自"需求配置-优先级"），
 * 不再硬编码 P0/P1/紧急/高等映射关系。
 *
 * 使用方式：
 *   import { useRequirementTag } from '@/composables/useRequirementTag'
 *   const { statusTagType, priorityTagType, priorityLabel } = useRequirementTag()
 *
 *   // 传入动态优先级映射
 *   const priorityMap = { P0: '紧急', P1: '高', P2: '中', P3: '低' }
 *   const priorityColorMap = { P0: '#F56C6C', P1: '#E6A23C', P2: '#409EFF', P3: '#909399' }
 *   priorityTagType('P0', priorityMap, priorityColorMap) // → 'danger'
 *   priorityLabel('P0', priorityMap)                     // → '紧急'
 */

import { stripPriorityPrefix, normalizeText } from '@/utils/format'

export type ElementPlusTagType = 'success' | 'info' | 'warning' | 'danger' | 'primary' | undefined
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

/**
 * 根据优先级的 color 字段推算 Element Plus tag type
 *
 * 约定：color 为标准十六进制时，映射到最近的 tag type；
 *       color 已经是 danger/warning/info/success/primary 时直接使用。
 */
function colorToTagType(color?: string | null): ElementPlusTagType {
  if (!color) return 'info'
  const lower = color.toLowerCase().trim()

  // 已经是 tag type 名
  const tagTypes: ElementPlusTagType[] = ['danger', 'warning', 'info', 'success', 'primary']
  if (tagTypes.includes(lower as ElementPlusTagType)) return lower as ElementPlusTagType

  // 十六进制 → 基于色相粗映射
  const hex = lower.startsWith('#') ? lower : `#${lower}`
  const r = parseInt(hex.slice(1, 3), 16) || 0
  const g = parseInt(hex.slice(3, 5), 16) || 0
  const b = parseInt(hex.slice(5, 7), 16) || 0
  const max = Math.max(r, g, b)

  if (max === 0) return 'info'
  // 红色系 → danger
  if (r > 150 && r > g * 1.5 && r > b * 1.5) return 'danger'
  // 橙/黄系 → warning
  if (r > 150 && g > 100 && g < 200 && b < 100) return 'warning'
  // 绿色系 → success
  if (g > 150 && g > r * 1.2 && g > b * 1.2) return 'success'
  // 蓝色系 → primary / info
  if (b > 150 && b > r * 1.2 && b > g * 1.2) return 'primary'

  return 'info'
}

/** 英文优先级 → 中文 兜底映射（仅在动态配置未覆盖时使用） */
const EN_PRIORITY_FALLBACK: Record<string, string> = {
  urgent: '紧急',
  high: '高',
  medium: '中',
  middle: '中',
  low: '低',
}

export function useRequirementTag() {
  /** 状态 → Element Plus tag type */
  function statusTagType(status?: string | null): ElementPlusTagType {
    if (!status) return 'info'
    return STATUS_TAG_MAP[status] ?? 'info'
  }

  /**
   * 优先级 → Element Plus tag type
   *
   * @param priority  优先级 code（如 P0、high 等）
   * @param codeToName code→中文名映射（来自"需求配置-优先级"列表）
   * @param codeToColor code→颜色映射（来自"需求配置-优先级"列表的 color 字段）
   */
  function priorityTagType(
    priority?: string | null,
    codeToName?: Record<string, string>,
    codeToColor?: Record<string, string>,
  ): ElementPlusTagType {
    if (!priority) return 'info'

    // 1) 优先使用动态配置的 color 映射
    if (codeToColor && priority in codeToColor) {
      return colorToTagType(codeToColor[priority])
    }

    // 2) 使用动态配置的中文名映射 → 硬编码中文→颜色兜底
    const label = codeToName?.[priority]
    if (label) {
      const chineseMap: Record<string, ElementPlusTagType> = {
        '紧急': 'danger',
        '高': 'warning',
        '中': undefined,
        '低': 'info',
      }
      if (label in chineseMap) return chineseMap[label]
    }

    // 3) 原始 code 兜底（P0、P1 等）
    const codeMap: Record<string, ElementPlusTagType> = {
      P0: 'danger',
      P1: 'warning',
      P2: 'info',
      P3: 'success',
    }
    if (priority in codeMap) return codeMap[priority]

    // 4) 英文兜底
    const lowerPriority = priority.toLowerCase()
    if (lowerPriority === 'urgent' || lowerPriority === 'high') return 'warning'
    if (lowerPriority === 'medium' || lowerPriority === 'middle') return undefined
    if (lowerPriority === 'low') return 'info'

    return 'info'
  }

  /**
   * 优先级 code → 中文显示名
   *
   * @param code       优先级 code
   * @param codeToName code→中文名映射（来自"需求配置-优先级"列表）
   */
  function priorityLabel(
    code?: string | null,
    codeToName?: Record<string, string>,
  ): string {
    if (!code) return '-'

    const normalizedCode = String(code).trim()

    // 1) 严格按动态配置渲染
    const mapped = codeToName?.[normalizedCode] ?? codeToName?.[normalizedCode.toUpperCase()]
    if (mapped) return stripPriorityPrefix(normalizeText(mapped))

    // 2) 英文→中文兜底（仅在未配置映射时使用）
    const lowerCode = normalizedCode.toLowerCase()
    if (lowerCode in EN_PRIORITY_FALLBACK) return EN_PRIORITY_FALLBACK[lowerCode]

    // 3) 去除 P0- 前缀后回退原值
    return stripPriorityPrefix(normalizeText(normalizedCode))
  }

  /**
   * 优先级 → 自定义标签样式（直接使用"需求配置-优先级"中配置的 color）
   *
   * 返回 inline style 对象，用于 el-tag 的 :style 绑定。
   * 配置了 color 时用实色背景 + 白色文字 + 同色边框，保证任意配色下都清晰可读；
   * 未配置 color 时返回 undefined，回退到 :type 预设色。
   *
   * @param color 优先级配置的 color 字段（支持 #hex / rgb() / rgba()）
   */
  function priorityTagStyle(color?: string | null): Record<string, string> | undefined {
    if (!color || !String(color).trim()) return undefined
    return {
      backgroundColor: color,
      borderColor: color,
      color: '#fff',
    }
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
    priorityLabel,
    priorityTagStyle,
    approvalResultTagType,
    approvalTimelineItemType,
    requirementStatusLabel,
  }
}
