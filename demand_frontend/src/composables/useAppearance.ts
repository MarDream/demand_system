// ===== 外观设置（个人设置 → 外观）=====
// 参考桌面端外观面板：主题模式（浅色/深色/跟随系统）+ 主题色 + 圆角风格
// 状态持久化：localStorage（即时生效/免闪烁）+ 后端 users.appearance_config（跟随账号，
// 换设备/重登后仍保持），变更经 800ms 防抖同步到 PUT /auth/appearance
import { ref, watch, type Ref } from 'vue'
import { saveAppearance } from '@/api/modules/auth'
import { getToken } from '@/utils/auth'

export type ThemeMode = 'light' | 'dark' | 'auto'
export type RadiusLevel = 'none' | 'soft' | 'round'

export interface AppearanceState {
  mode: ThemeMode
  primary: string
  radius: RadiusLevel
}

const STORAGE_KEY = 'app-appearance'

export const DEFAULT_PRIMARY = '#2563EB'

// 可选主题色：与系统品牌色板保持同族（Indigo-Blue 系 + 延伸）
export const PRIMARY_SWATCHES: Array<{ value: string; name: string }> = [
  { value: '#2563EB', name: '星辉蓝' },
  { value: '#4F46E5', name: '静谧靛' },
  { value: '#0EA5E9', name: '晴空蓝' },
  { value: '#0891B2', name: '青碧' },
  { value: '#14B8A6', name: '湖水青' },
  { value: '#0D9488', name: '孔雀绿' },
  { value: '#10B981', name: '翡翠绿' },
  { value: '#84CC16', name: '嫩芽绿' },
  { value: '#F59E0B', name: '琥珀橙' },
  { value: '#F97316', name: '珊瑚橙' },
  { value: '#EF4444', name: '赤霞红' },
  { value: '#E11D48', name: '石榴红' },
  { value: '#DB2777', name: '樱粉' },
  { value: '#8B5CF6', name: '幻彩紫' },
  { value: '#7C3AED', name: '紫罗兰' },
  { value: '#1E40AF', name: '深海蓝' },
]

// 圆角档位：none = 直角利落；soft = 8px 商务（默认）；round = 14px 活泼
export const RADIUS_LEVELS: Array<{ value: RadiusLevel; name: string; desc: string }> = [
  { value: 'none', name: '标准', desc: '直角利落' },
  { value: 'soft', name: '柔和', desc: '适中圆角' },
  { value: 'round', name: '圆润', desc: '大圆角' },
]

const VALID_MODES: ThemeMode[] = ['light', 'dark', 'auto']
const VALID_RADIUS: RadiusLevel[] = ['none', 'soft', 'round']

function loadState(): AppearanceState {
  const fallback: AppearanceState = { mode: 'light', primary: DEFAULT_PRIMARY, radius: 'soft' }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return fallback
    const parsed = JSON.parse(raw) as Partial<AppearanceState>
    return {
      mode: VALID_MODES.includes(parsed.mode as ThemeMode) ? (parsed.mode as ThemeMode) : fallback.mode,
      primary: /^#[0-9A-Fa-f]{6}$/.test(parsed.primary || '') ? (parsed.primary as string) : fallback.primary,
      radius: VALID_RADIUS.includes(parsed.radius as RadiusLevel) ? (parsed.radius as RadiusLevel) : fallback.radius,
    }
  } catch {
    return fallback
  }
}

// ----- 颜色工具：hex → rgb，及按亮度混合 light-3/5/7/9 与 dark-2 梯度 -----
function hexToRgb(hex: string): [number, number, number] {
  const v = hex.replace('#', '')
  return [
    parseInt(v.slice(0, 2), 16),
    parseInt(v.slice(2, 4), 16),
    parseInt(v.slice(4, 6), 16),
  ]
}

/** 与白色/黑色混合：ratio 越大越接近基色。isDark 模式下向黑色混合（Element Plus 暗色梯度惯例） */
function mixColor(hex: string, ratio: number, isDark: boolean): string {
  const [r, g, b] = hexToRgb(hex)
  const target = isDark ? [17, 24, 39] : [255, 255, 255] // 暗色向 gray-900 混，浅色向白混
  const m = (c: number, t: number) => Math.round(t + (c - t) * ratio)
  const toHex = (n: number) => n.toString(16).padStart(2, '0')
  return `#${toHex(m(r, target[0]))}${toHex(m(g, target[1]))}${toHex(m(b, target[2]))}`
}

function resolveIsDark(mode: ThemeMode): boolean {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

// ----- 应用级单例（模块级状态，多组件共享同一份） -----
const state = loadState()
const mode = ref<ThemeMode>(state.mode)
const primary = ref(state.primary)
const radius = ref<RadiusLevel>(state.radius)

let mediaQuery: MediaQueryList | null = null
function onSystemChange() {
  if (mode.value === 'auto') applyAppearance()
}

function serializeState(): string {
  return JSON.stringify({ mode: mode.value, primary: primary.value, radius: radius.value })
}

function persist(options?: { remote?: boolean }) {
  localStorage.setItem(STORAGE_KEY, serializeState())
  // remote === false：启动时 watch immediate 的首次持久化，只写本地缓存，
  // 不能当作"本地改动"上报（否则会用默认值覆盖服务端配置并阻断远程恢复）
  if (options?.remote === false) return
  scheduleRemoteSave()
}

// ----- 用户级持久化（后端同步） -----
let remoteSaveTimer: ReturnType<typeof setTimeout> | null = null
/** 本地有改动尚未成功落库时，忽略远程回读，避免竞态回退 */
let pendingLocalChange = false
/** 是否已过 watch immediate 的首次启动持久化 */
let booted = false

function scheduleRemoteSave() {
  if (!getToken()) return
  pendingLocalChange = true
  if (remoteSaveTimer) clearTimeout(remoteSaveTimer)
  remoteSaveTimer = setTimeout(() => {
    remoteSaveTimer = null
    saveAppearance({ mode: mode.value, primary: primary.value, radius: radius.value })
      .then(() => { pendingLocalChange = false })
      .catch(() => { /* 静默失败：localStorage 兜底，下次变更会重试 */ })
  }, 800)
}

/**
 * 应用后端下发的用户外观配置（/auth/me 返回的 appearanceConfig JSON）。
 * 登录/刷新用户信息时调用；本地有未落库改动或数据相同时跳过。
 */
export function applyRemoteAppearance(raw: string | null | undefined) {
  if (!raw || pendingLocalChange) return
  if (raw === serializeState()) return
  try {
    const parsed = JSON.parse(raw) as Partial<AppearanceState>
    if (
      VALID_MODES.includes(parsed.mode as ThemeMode) &&
      /^#[0-9A-Fa-f]{6}$/.test(parsed.primary || '') &&
      VALID_RADIUS.includes(parsed.radius as RadiusLevel)
    ) {
      mode.value = parsed.mode as ThemeMode
      primary.value = parsed.primary as string
      radius.value = parsed.radius as RadiusLevel
    }
  } catch {
    // 非法数据忽略，保持本地状态
  }
}

export function applyAppearance() {
  const root = document.documentElement
  const isDark = resolveIsDark(mode.value)

  // 1) 主题模式：Element Plus 暗色变量挂在 html.dark
  root.classList.toggle('dark', isDark)
  root.setAttribute('data-appearance-mode', isDark ? 'dark' : 'light')

  // 2) 圆角档位
  root.setAttribute('data-appearance-radius', radius.value)

  // 3) 主题色：同步 Element Plus 主色梯度 + 系统 token（Element Plus 用 rgb() 格式）
  const hex = primary.value
  const [r, g, b] = hexToRgb(hex)
  root.style.setProperty('--el-color-primary', hex)
  root.style.setProperty('--el-color-primary-dark-2', mixColor(hex, 0.8, isDark))
  root.style.setProperty('--el-color-primary-light-3', mixColor(hex, 0.7, isDark))
  root.style.setProperty('--el-color-primary-light-5', mixColor(hex, 0.5, isDark))
  root.style.setProperty('--el-color-primary-light-7', mixColor(hex, 0.3, isDark))
  root.style.setProperty('--el-color-primary-light-8', mixColor(hex, 0.2, isDark))
  root.style.setProperty('--el-color-primary-light-9', mixColor(hex, 0.1, isDark))
  // 系统 token 主色族（颜色/渐变/光晕随主题色联动）
  root.style.setProperty('--color-primary', hex)
  root.style.setProperty('--color-primary-hover', mixColor(hex, 0.7, isDark))
  root.style.setProperty('--color-primary-active', mixColor(hex, 0.8, isDark))
  root.style.setProperty('--color-primary-light', mixColor(hex, 0.1, isDark))
  root.style.setProperty('--color-primary-subtle', mixColor(hex, 0.05, isDark))
  root.style.setProperty('--color-primary-bg', `rgba(${r}, ${g}, ${b}, 0.1)`)
  root.style.setProperty('--gradient-primary', `linear-gradient(135deg, ${hex} 0%, ${mixColor(hex, 0.5, isDark)} 100%)`)
  root.style.setProperty('--color-accent-glow', `rgba(${r}, ${g}, ${b}, 0.25)`)
  root.style.setProperty('--color-accent-glow-strong', `rgba(${r}, ${g}, ${b}, 0.35)`)
  root.style.setProperty('--color-accent-tint', `rgba(${r}, ${g}, ${b}, 0.2)`)
  root.style.setProperty('--color-accent-tint-light', `rgba(${r}, ${g}, ${b}, 0.06)`)
  root.style.setProperty('--color-ring', hex)

  // 4) meta theme-color（浏览器地址栏跟随）
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', isDark ? '#0B1220' : hex)
}

export function initAppearanceWatcher() {
  if (mediaQuery) return
  mediaQuery = window.matchMedia?.('(prefers-color-scheme: dark)') ?? null
  mediaQuery?.addEventListener('change', onSystemChange)

  watch([mode, primary, radius], () => {
    applyAppearance()
    persist({ remote: booted })
    booted = true
  }, { immediate: true })
}

/** 供设置页使用的响应式状态（与模块级单例同源） */
export function useAppearance(): {
  mode: Ref<ThemeMode>
  primary: Ref<string>
  radius: Ref<RadiusLevel>
} {
  return { mode, primary, radius }
}
