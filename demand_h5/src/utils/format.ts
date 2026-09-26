import dayjs from 'dayjs'

/** 状态 → 颜色类型（与 Web 端 useRequirementTag 保持一致） */
const STATUS_COLOR_MAP: Record<string, string> = {
  新建: '#909399',
  待分析: '#E6A23C',
  待确认: '#E6A23C',
  待评审: '#E6A23C',
  评审中: '#E6A23C',
  已通过: '#67C23A',
  开发中: '#1989fa',
  测试中: '#1989fa',
  已上线: '#67C23A',
  已验收: '#67C23A',
  已取消: '#909399',
  已拒绝: '#EE0A24',
  打回: '#EE0A24',
  测试不通过: '#EE0A24',
  验收不通过: '#EE0A24',
  PENDING_REVIEW: '#E6A23C',
  REJECTED: '#EE0A24',
  SENT_BACK: '#EE0A24',
  TEST_FAILED: '#EE0A24',
  ACCEPT_FAILED: '#EE0A24',
}

const STATUS_LABEL_MAP: Record<string, string> = {
  PENDING_REVIEW: '待评审',
  REJECTED: '已拒绝',
  SENT_BACK: '已打回',
  TEST_FAILED: '测试不通过',
  ACCEPT_FAILED: '验收不通过',
}

const PRIORITY_MAP: Record<string, { label: string; color: string }> = {
  P0: { label: '紧急', color: '#EE0A24' },
  URGENT: { label: '紧急', color: '#EE0A24' },
  P1: { label: '高', color: '#FF976A' },
  HIGH: { label: '高', color: '#FF976A' },
  P2: { label: '中', color: '#1989fa' },
  MEDIUM: { label: '中', color: '#1989fa' },
  P3: { label: '低', color: '#969799' },
  LOW: { label: '低', color: '#969799' },
}

const TYPE_MAP: Record<string, string> = {
  FEATURE: '功能',
  BUG: '缺陷',
  OPTIMIZATION: '优化',
  TASK: '任务',
  OTHER: '其他',
  TECHNICAL_SUPPORT: '技术支持',
  REQUIREMENT: '需求',
  SUGGESTION: '建议',
}

export function typeLabel(type?: string | null): string {
  if (!type) return '-'
  const upper = type.toUpperCase()
  if (TYPE_MAP[upper]) return TYPE_MAP[upper]
  // 未映射的英文代码 → 下划线转空格的可读形式
  return upper.includes('_') ? upper.replace(/_/g, ' ') : type
}

export function statusColor(status?: string | null): string {
  if (!status) return '#909399'
  return STATUS_COLOR_MAP[status] || '#909399'
}

export function statusLabel(status?: string | null): string {
  if (!status) return '-'
  return STATUS_LABEL_MAP[status] || status
}

export function priorityInfo(priority?: string | null): { label: string; color: string } {
  if (!priority) return { label: '-', color: '#969799' }
  const upper = priority.toUpperCase()
  return PRIORITY_MAP[upper] || PRIORITY_MAP[priority] || { label: priority, color: '#969799' }
}

export function formatTime(time?: string | null): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

/** 纯日期格式化（YYYY-MM-DD），用于期望上线日期等不含时间的字段 */
export function formatDate(time?: string | null): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD')
}

/** 富文本描述剥离 HTML 标签为纯文本（H5 端不做富文本渲染，避免样式与 XSS 问题） */
export function stripHtml(html?: string | null): string {
  if (!html) return ''
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|h[1-6]|li)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

export function formatRelative(time?: string | null): string {
  if (!time) return '-'
  const diff = Date.now() - dayjs(time).valueOf()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < hour) return `${Math.max(1, Math.floor(diff / minute))}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)}天前`
  return dayjs(time).format('YYYY-MM-DD')
}
