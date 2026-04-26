import dayjs from 'dayjs'

export function formatDate(date: string | Date | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  return dayjs(date).format(format)
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
