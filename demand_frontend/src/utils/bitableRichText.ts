/**
 * Bitable 富文本字段工具
 *
 * 富文本单元格在 value_text 里存 HTML（IsleEditor 的输出）。
 * - sanitizeRichTextHtml: v-html 渲染前的白名单消毒（多维表格是多用户写入口，
 *   直接渲染他人写入的 HTML 是 XSS 注入面，必须过这一层）
 * - stripRichTextHtml:    HTML → 纯文本，用于网格单元格预览、筛选/导出的可读文本
 */

/** 整体丢弃（连同子内容）的标签 */
const DROP_WITH_CONTENT = new Set([
  'script', 'style', 'iframe', 'frame', 'frameset', 'object', 'embed',
  'link', 'meta', 'base', 'noscript', 'form',
  'svg', 'math', 'template', 'textarea', 'select', 'button', 'video', 'audio', 'source',
])

/** 允许保留的标签（其余标签被解包：删除标签本身、保留子内容） */
const KEEP_TAGS = new Set([
  'p', 'br', 'hr', 'blockquote', 'pre', 'code',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'strong', 'b', 'em', 'i', 'u', 's', 'del', 'strike', 'mark', 'sub', 'sup',
  'ul', 'ol', 'li',
  'span', 'div', 'a',
  'table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th', 'col', 'colgroup',
])

/** style 属性里允许保留的 CSS 属性（排版类），其余（position/url 等）一律丢弃 */
const ALLOWED_STYLE_PROPS = new Set([
  'color', 'background-color', 'text-align', 'font-size', 'font-family',
  'font-weight', 'font-style', 'line-height', 'letter-spacing',
  'text-decoration', 'text-indent', 'padding-left', 'margin-left',
])

/** 危险值特征：出现即整条样式丢弃 */
const DANGEROUS_STYLE_VALUE = /(url\s*\(|expression|javascript:|@import|behavior)/i

/** 无子节点的标签，解包时直接删除节点 */
const VOID_TAGS = new Set(['br', 'hr', 'col'])

function sanitizeStyleValue(styleText: string): string {
  return styleText
    .split(';')
    .map((rule) => rule.trim())
    .filter(Boolean)
    .filter((rule) => {
      const colon = rule.indexOf(':')
      if (colon <= 0) return false
      const prop = rule.slice(0, colon).trim().toLowerCase()
      const value = rule.slice(colon + 1).trim()
      return ALLOWED_STYLE_PROPS.has(prop) && !DANGEROUS_STYLE_VALUE.test(value)
    })
    .map((rule) => {
      const colon = rule.indexOf(':')
      return `${rule.slice(0, colon).trim().toLowerCase()}: ${rule.slice(colon + 1).trim()}`
    })
    .join('; ')
}

function sanitizeAttributes(el: Element) {
  const attrs = Array.from(el.attributes)
  for (const attr of attrs) {
    const name = attr.name.toLowerCase()
    const value = attr.value
    // 事件属性与任何 data- 之外的未知属性一律去掉
    if (name.startsWith('on')) {
      el.removeAttribute(attr.name)
      continue
    }
    if (name === 'style') {
      const cleaned = sanitizeStyleValue(value)
      if (cleaned) el.setAttribute('style', cleaned)
      else el.removeAttribute('style')
      continue
    }
    if (el.tagName === 'A' && name === 'href') {
      const v = value.trim().toLowerCase()
      if (v.startsWith('http://') || v.startsWith('https://') || v.startsWith('mailto:') || v.startsWith('#')) {
        el.setAttribute('rel', 'noopener noreferrer nofollow')
        continue
      }
      el.removeAttribute('href')
      continue
    }
    if (el.tagName === 'A' && name === 'target') continue
    if ((el.tagName === 'TD' || el.tagName === 'TH') && (name === 'colspan' || name === 'rowspan')) continue
    if (name === 'class' || name.startsWith('data-')) continue
    el.removeAttribute(attr.name)
  }
}

/**
 * 白名单消毒：只保留排版类标签与安全属性，脚本类标签连内容一起删，
 * 未知标签解包保留文字。解析失败时退化为转义后的纯文本。
 */
export function sanitizeRichTextHtml(html: string): string {
  if (!html) return ''
  if (typeof DOMParser === 'undefined') return ''
  let doc: Document
  try {
    doc = new DOMParser().parseFromString(html, 'text/html')
  } catch {
    return ''
  }
  const body = doc.body
  if (!body) return ''

  // 深度优先收集，避免遍历中修改树导致跳节点
  const all = Array.from(body.querySelectorAll('*'))
  for (const el of all) {
    const tag = el.tagName.toLowerCase()
    const dropMatch = tag === 'input' ? true : DROP_WITH_CONTENT.has(tag)
    if (dropMatch) {
      el.remove()
      continue
    }
    if (!KEEP_TAGS.has(tag)) {
      // 未知标签：解包，保留子内容
      const parent = el.parentNode
      if (!parent) continue
      while (el.firstChild) parent.insertBefore(el.firstChild, el)
      parent.removeChild(el)
      continue
    }
    sanitizeAttributes(el)
  }

  return body.innerHTML
}

/** HTML → 纯文本预览：块级标签转换行、剥标签、还原常见实体、压平连续空行 */
export function stripRichTextHtml(html: string): string {
  if (!html) return ''
  const text = html
    .replace(/<\s*(br|hr)\s*\/?\s*>/gi, '\n')
    .replace(/<\s*li[^>]*>/gi, '\n')
    .replace(/<\/\s*(p|div|li|h[1-6]|tr|blockquote|pre)\s*>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&#(\d+);/g, (_, code: string) => {
      const num = Number(code)
      return Number.isFinite(num) && num > 0 && num < 0x110000 ? String.fromCodePoint(num) : ''
    })
  return text.replace(/\n{3,}/g, '\n\n').trim()
}

/** 网格单元格预览文本：压成单行（换行转空格），供截断展示 */
export function richTextCellPreview(html: string, maxChars = 120): string {
  const text = stripRichTextHtml(html).replace(/\s*\n+\s*/g, ' ').trim()
  if (!text) return ''
  return text.length > maxChars ? `${text.slice(0, maxChars)}…` : text
}

/** 富文本是否为空：剥完标签无可见文字即视为空（供必填校验/占位判断） */
export function isRichTextEmpty(html: string): boolean {
  return stripRichTextHtml(html).length === 0
}
