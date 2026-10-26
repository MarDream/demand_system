import { reactive } from 'vue'

/**
 * 可持久化的分组折叠状态。
 * <p>以 Record<key, boolean> 记录各分组的折叠态（true = 已折叠），
 * 状态写入 localStorage，跨会话记忆用户偏好；任何 storage 异常静默降级为内存态。</p>
 */
export function useFoldState(storageKey: string) {
  const folded = reactive<Record<string, boolean>>(readInitial())

  function readInitial(): Record<string, boolean> {
    try {
      const raw = window.localStorage.getItem(storageKey)
      if (!raw) return {}
      const parsed = JSON.parse(raw)
      return parsed && typeof parsed === 'object' ? parsed : {}
    } catch {
      return {}
    }
  }

  function persist() {
    try {
      window.localStorage.setItem(storageKey, JSON.stringify(folded))
    } catch {
      // 隐身模式 / 存储不可用时静默降级
    }
  }

  function toggle(key: string) {
    folded[key] = !folded[key]
    persist()
  }

  function isFolded(key: string) {
    return !!folded[key]
  }

  /** 用户是否已有折叠偏好（localStorage 有记录）。无偏好时调用方可应用默认折叠策略 */
  function hasStoredPreference() {
    try {
      return !!window.localStorage.getItem(storageKey)
    } catch {
      return false
    }
  }

  return { folded, toggle, isFolded, hasStoredPreference }
}
