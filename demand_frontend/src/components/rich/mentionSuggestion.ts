import { createApp, type App } from 'vue'
import Mention from '@tiptap/extension-mention'
import { pinyin } from 'pinyin-pro'
import { allUsers, ensureUsersLoaded } from '@/composables/useUserOptions'
import MentionList from './MentionList.vue'
import type { MentionCandidate } from '@/types/mention'

/** 弹层最多展示的候选人数 */
const MAX_CANDIDATES = 8

/** 姓名 → 拼音全拼 / 首字母 的缓存，避免每次输入都重算 */
const pinyinCache = new Map<string, { full: string; initials: string }>()

function pinyinKeys(realName: string): { full: string; initials: string } {
  const cached = pinyinCache.get(realName)
  if (cached) return cached
  let keys = { full: '', initials: '' }
  try {
    keys = {
      full: pinyin(realName, { toneType: 'none', type: 'string', v: true, nonZh: 'consecutive' })
        .replace(/\s+/g, '')
        .replace(/[^a-zA-Z0-9_]/g, '')
        .toLowerCase(),
      initials: pinyin(realName, { toneType: 'none', type: 'array', pattern: 'first', v: true, nonZh: 'consecutive' })
        .join('')
        .replace(/[^a-zA-Z0-9_]/g, '')
        .toLowerCase(),
    }
  } catch {
    keys = { full: '', initials: '' }
  }
  pinyinCache.set(realName, keys)
  return keys
}

/**
 * 按输入关键词匹配候选用户：姓名 > 拼音全拼 > 拼音首字母 > 账号。
 * 数据源复用 useUserOptions 的活跃用户列表（接口已按当前用户组织范围收窄）。
 */
export function matchMentionCandidates(query: string, limit = MAX_CANDIDATES): MentionCandidate[] {
  const users = allUsers.value || []
  const toCandidate = (u: typeof users[number]): MentionCandidate => ({
    id: u.id,
    username: u.username,
    realName: u.realName || u.username,
    avatar: u.avatar ?? null,
  })

  const q = (query || '').trim().toLowerCase().replace(/\s+/g, '')
  if (!q) return users.slice(0, limit).map(toCandidate)

  const matched: Array<{ user: typeof users[number]; score: number }> = []
  for (const user of users) {
    const name = (user.realName || '').toLowerCase().replace(/\s+/g, '')
    const account = (user.username || '').toLowerCase()
    const { full, initials } = pinyinKeys(user.realName || '')
    let score = -1
    if (name.startsWith(q)) score = 0
    else if (name.includes(q)) score = 1
    else if (full.startsWith(q)) score = 2
    else if (initials.startsWith(q)) score = 3
    else if (account.startsWith(q)) score = 4
    else if (account.includes(q)) score = 5
    else if (full.includes(q)) score = 6
    if (score >= 0) matched.push({ user, score })
  }
  matched.sort((a, b) => a.score - b.score)
  return matched.slice(0, limit).map((m) => toCandidate(m.user))
}

/**
 * 候选浮层渲染器：手动挂载 Vue 组件并按光标位置定位（固定定位 + 视口内收敛）。
 * 不依赖 tippy / VueRenderer，避免与 isle-editor 内置 tiptap 版本耦合。
 */
function createMentionRenderer() {
  let app: App | null = null
  let instance: any = null
  let anchor: HTMLElement | null = null
  let getRect: (() => DOMRect | null) | null = null

  const reposition = () => {
    if (!anchor || !getRect) return
    const rect = getRect()
    if (!rect) return
    anchor.style.visibility = 'hidden'
    const { width, height } = anchor.getBoundingClientRect()
    const gap = 6
    let left = rect.left
    let top = rect.bottom + gap
    const maxLeft = window.innerWidth - width - 8
    if (left > maxLeft) left = Math.max(8, maxLeft)
    if (top + height > window.innerHeight - 8) {
      const above = rect.top - height - gap
      top = above >= 8 ? above : Math.max(8, window.innerHeight - height - 8)
    }
    anchor.style.left = `${Math.max(8, left)}px`
    anchor.style.top = `${top}px`
    anchor.style.visibility = 'visible'
  }

  const teardown = () => {
    window.removeEventListener('scroll', reposition, true)
    window.removeEventListener('resize', reposition)
    if (app) {
      app.unmount()
      app = null
    }
    anchor?.remove()
    anchor = null
    instance = null
    getRect = null
  }

  return {
    onStart(props: any) {
      anchor = document.createElement('div')
      anchor.style.position = 'fixed'
      anchor.style.zIndex = '3000'
      anchor.style.left = '0px'
      anchor.style.top = '0px'
      anchor.style.visibility = 'hidden'
      document.body.appendChild(anchor)
      app = createApp(MentionList)
      instance = app.mount(anchor)
      instance?.update?.({ items: props.items, command: props.command })
      getRect = props.clientRect || null
      reposition()
      window.addEventListener('scroll', reposition, true)
      window.addEventListener('resize', reposition)
    },
    onUpdate(props: any) {
      instance?.update?.({ items: props.items, command: props.command })
      getRect = props.clientRect || null
      reposition()
    },
    onKeyDown(props: any) {
      if (props.event?.key === 'Escape') {
        teardown()
        return true
      }
      return instance?.onKeyDown?.(props) ?? false
    },
    onExit() {
      teardown()
    },
  }
}

/**
 * 评论编辑器的 @ 提及扩展。
 * 触发字符为 @，候选项异步取自系统活跃用户；选中后插入 mention 节点
 * （HTML 形如 <span data-type="mention" data-id="12" data-label="张三">@张三</span>）。
 */
export function createCommentMentionExtension() {
  return Mention.configure({
    HTMLAttributes: { class: 'mention-chip' },
    suggestion: {
      char: '@',
      allowSpaces: false,
      // 默认仅允许「空格/行首」前缀；中文场景常直接跟在汉字后输入 @，这里放开限制
      allowedPrefixes: null,
      items: async ({ query }: { query: string }) => {
        await ensureUsersLoaded()
        return matchMentionCandidates(query)
      },
      render: createMentionRenderer,
    },
  })
}

/**
 * 从编辑器文档中提取被@用户ID（提交时随评论一并上送，供后端发提醒）。
 * 后端另有正文 HTML 兜底解析，二者取并集。
 */
export function collectMentionedUserIds(editor: any): number[] {
  const json = editor?.getJSON?.()
  if (!json) return []
  const ids = new Set<number>()
  const walk = (node: any) => {
    if (!node || typeof node !== 'object') return
    if (node.type === 'mention' && node.attrs?.id != null) {
      const parsed = Number(node.attrs.id)
      if (Number.isFinite(parsed)) ids.add(parsed)
    }
    if (Array.isArray(node.content)) node.content.forEach(walk)
  }
  walk(json)
  return Array.from(ids)
}
