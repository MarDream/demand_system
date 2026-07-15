import { marked } from 'marked'

interface RagCitationLike {
  documentId?: number
  fileName?: string
}

marked.setOptions({
  breaks: true,
  gfm: true
})

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 将 markdown 文本渲染为 HTML，去除 ## ** 等符号
 */
export function renderMarkdown(content: string): string {
  if (!content) return ''
  try {
    const html = marked.parse(content, { async: false }) as string
    return html
  } catch {
    return escapeHtml(content)
  }
}

/**
 * 将回答正文中的 [N] 角标替换为可点击的 sup 标签
 * N 对应 citations 数组的序号（1-based）
 */
export function replaceCitationLinks(html: string, citations: RagCitationLike[] = []): string {
  if (!citations.length) return html
  return html.replace(/\[(\d+)\]/g, (_, num: string) => {
    const idx = parseInt(num, 10) - 1
    if (idx >= 0 && idx < citations.length) {
      const fileName = citations[idx]?.fileName || ''
      return `<sup class="citation-ref" data-citation-index="${idx}" title="${escapeHtml(fileName)}">[${num}]</sup>`
    }
    return `<sup class="citation-ref citation-ref--invalid">[${num}]</sup>`
  })
}

/**
 * 一步完成：渲染 markdown 并替换角标
 */
export function renderWithCitations(content: string, citations: RagCitationLike[] = []): string {
  const html = renderMarkdown(content)
  return replaceCitationLinks(html, citations)
}
