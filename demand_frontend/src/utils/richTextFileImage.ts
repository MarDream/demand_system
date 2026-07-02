import { getToken } from '@/utils/auth'

const FILE_PREVIEW_PATH_REGEX = /\/api\/v1\/files\/(\d+)\/preview$/
const RICH_TEXT_TABLE_CLASS = 'rich-text-table'

function addClass(element: Element, className: string) {
  const classList = new Set((element.getAttribute('class') || '').split(/\s+/).filter(Boolean))
  if (!classList.has(className)) {
    classList.add(className)
    element.setAttribute('class', Array.from(classList).join(' '))
  }
}

function normalizeOfficeTableStyles(doc: Document): boolean {
  let changed = false

  doc.querySelectorAll('table').forEach((table) => {
    addClass(table, RICH_TEXT_TABLE_CLASS)
    table.removeAttribute('border')
    table.removeAttribute('cellspacing')
    table.removeAttribute('cellpadding')
    changed = true
  })

  doc.querySelectorAll('td, th').forEach((cell) => {
    const style = cell.getAttribute('style') || ''

    if (/border\s*:\s*none/i.test(style) && /mso-border/i.test(style)) {
      cell.setAttribute('style', style.replace(/border\s*:\s*none\s*;?/ig, ''))
      changed = true
    }

    const width = cell.getAttribute('width')
    if (width && !/width\s*:/i.test(cell.getAttribute('style') || '')) {
      const normalizedWidth = /^\d+$/.test(width) ? `${width}px` : width
      cell.setAttribute('style', `${cell.getAttribute('style') || ''}; width: ${normalizedWidth};`)
      changed = true
    }
  })

  return changed
}

function extractFileId(src: string): string | null {
  if (!src) return null

  try {
    const url = new URL(src, window.location.origin)
    return url.pathname.match(FILE_PREVIEW_PATH_REGEX)?.[1] || null
  } catch {
    return src.match(/\/api\/v1\/files\/(\d+)\/preview(?:\?.*)?$/)?.[1] || null
  }
}

export function buildRichTextImagePreviewUrl(fileId: number | string): string {
  const basePath = `/api/v1/files/${fileId}/preview`
  const token = getToken()
  return token ? `${basePath}?accessToken=${encodeURIComponent(token)}` : basePath
}

export function hydrateRichTextImageHtml(html?: string): string {
  if (!html) return ''

  const doc = new DOMParser().parseFromString(html, 'text/html')
  let changed = false

  doc.querySelectorAll('img[src]').forEach((img) => {
    const currentSrc = img.getAttribute('src') || ''
    const fileId = extractFileId(currentSrc)
    if (!fileId) return

    const hydratedSrc = buildRichTextImagePreviewUrl(fileId)
    if (currentSrc !== hydratedSrc) {
      img.setAttribute('src', hydratedSrc)
      changed = true
    }
  })

  changed = normalizeOfficeTableStyles(doc) || changed

  return changed ? doc.body.innerHTML : html
}

export function serializeRichTextImageHtml(html?: string): string {
  if (!html) return ''

  const doc = new DOMParser().parseFromString(html, 'text/html')
  let changed = false

  doc.querySelectorAll('img[src]').forEach((img) => {
    const currentSrc = img.getAttribute('src') || ''
    const fileId = extractFileId(currentSrc)
    if (!fileId) return

    const persistedSrc = `/api/v1/files/${fileId}/preview`
    if (currentSrc !== persistedSrc) {
      img.setAttribute('src', persistedSrc)
      changed = true
    }
  })

  return changed ? doc.body.innerHTML : html
}
