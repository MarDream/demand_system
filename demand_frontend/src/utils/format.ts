import dayjs from 'dayjs'

export function formatDate(date: string | Date | null | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  return dayjs(date).format(format)
}

/**
 * 从文件名中提取小写扩展名。空值/无扩展名返回 ''。
 * 使用 lastIndexOf('.') 而非 split('.') 避免临时数组分配。
 */
export function getFileExt(filename: string | null | undefined): string {
  if (!filename) return ''
  const dot = filename.lastIndexOf('.')
  return dot >= 0 ? filename.slice(dot + 1).toLowerCase() : ''
}

/** 把字节数格式化为可读字符串（B / KB / MB）。 */
export function formatFileSize(size: number | null | undefined): string {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function formatLabel(value: string, map?: Record<string, string>): string {
  const normalizedValue = normalizeText(value)
  if (!map) return normalizedValue
  const mapped = map[value] ?? map[normalizedValue]
  return mapped ? normalizeText(mapped) : normalizedValue
}

export function formatPriority(priority: string, map?: Record<string, string>): string {
  return formatLabel(priority, map)
}

export function stripPriorityPrefix(text: string): string {
  const normalized = normalizeText(text)
  if (!normalized) return normalized
  const stripped = normalized.replace(/^P\d+\s*[-－_:：]?\s*/i, '').trim()
  return stripped || normalized
}

export function formatStatus(status: string, map?: Record<string, string>): string {
  return formatLabel(status, map)
}

export function normalizeText(text: string): string {
  if (!text) return text
  if (/[\u4E00-\u9FFF]/.test(text)) return text
  if (!/[ÃÂâäåæçèéêëìíîïðñòóôõöùúûüýþÿ]/.test(text)) return text

  try {
    const bytes = Uint8Array.from(Array.from(text, ch => ch.charCodeAt(0) & 0xff))
    const decoded = new TextDecoder('utf-8').decode(bytes)
    if (/[\u4E00-\u9FFF]/.test(decoded) && !decoded.includes('\uFFFD')) {
      return decoded
    }
  } catch {}

  return text
}
