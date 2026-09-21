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
 * 归一化模型输出的编号写法。
 *
 * 模型常见几种不规范写法，这里统一收敛成裸 [N]，否则角标匹配不到、会在正文里留下
 * 「（资料[4]）」这类原文：`（资料[4]）`、`【资料4】`、`[资料4]`、`资料[4]`、全角括号。
 */
function normalizeCitationMarkers(html: string): string {
  return html
    // 【资料4】/【参考资料 4】→ [4]
    .replace(/【\s*(?:参考)?资料\s*(\d+)\s*】/g, '[$1]')
    // [资料4]/[参考资料 4] → [4]
    .replace(/\[\s*(?:参考)?资料\s*(\d+)\s*\]/g, '[$1]')
    // （资料[4]）/（参考资料[4]）→ [4]（含括号一并去掉，角标自身已足够表意）
    .replace(/[（(]\s*(?:参考)?资料\s*\[(\d+)\]\s*[)）]/g, '[$1]')
    // 残留的裸前缀：资料[4] / 参考资料 [4] → [4]
    .replace(/(?:参考)?资料\s*(\[\d+\])/g, '$1')
    // 全角方括号 ［4］ → [4]
    .replace(/［\s*(\d+)\s*］/g, '[$1]')
}

/**
 * 将回答正文中的 [N] 角标替换为可点击的 sup 标签
 * N 对应 citations 数组的序号（1-based）
 */
export function replaceCitationLinks(html: string, citations: RagCitationLike[] = []): string {
  const normalized = normalizeCitationMarkers(html)
  // 默认参数不兜底 null（历史消息 citations 可能为 null），此处显式判空
  if (!citations || !citations.length) return normalized
  return normalized.replace(/\[(\d+)\]/g, (_, num: string) => {
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
