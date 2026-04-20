import dayjs from 'dayjs'

export function formatDate(date: string | Date | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  return dayjs(date).format(format)
}

export function formatPriority(priority: string): string {
  const map: Record<string, string> = {
    critical: '紧急',
    high: '高',
    medium: '中',
    low: '低',
  }
  return map[priority] || priority
}

export function formatStatus(status: string): string {
  const map: Record<string, string> = {
    new: '新建',
    pending_review: '待评审',
    reviewing: '评审中',
    approved: '已通过',
    developing: '开发中',
    testing: '测试中',
    released: '已上线',
    accepted: '已验收',
    cancelled: '已取消',
    rejected: '已拒绝',
    test_failed: '测试不通过',
    accepted_failed: '验收不通过',
    active: '启用',
    inactive: '停用',
    planned: '计划中',
    in_progress: '进行中',
    completed: '已完成',
  }
  return map[status] || status
}
