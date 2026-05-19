import { getToken } from '@/utils/auth'

const FILE_PREVIEW_PATH_REGEX = /\/api\/v1\/files\/(\d+)\/preview$/

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
