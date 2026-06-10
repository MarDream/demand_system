import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MenuItem } from '@/api/modules/menu'

interface BreadcrumbItem {
  name: string
  path: string | null
}

export const useAppStore = defineStore('app', () => {
  const sidebarOpened = ref(true)
  const device = ref<'desktop' | 'mobile'>('desktop')
  const menuList = ref<MenuItem[]>([])

  const DEFAULT_SIDEBAR_WIDTH = 210
  const MIN_SIDEBAR_WIDTH = 150
  const MAX_SIDEBAR_WIDTH = 420

  const sidebarWidth = ref(DEFAULT_SIDEBAR_WIDTH)

  // Restore from localStorage
  const saved = localStorage.getItem('sidebar-opened')
  if (saved !== null) sidebarOpened.value = saved === 'true'
  const savedWidth = localStorage.getItem('sidebar-width')
  if (savedWidth !== null) {
    const w = Number(savedWidth)
    if (w >= MIN_SIDEBAR_WIDTH && w <= MAX_SIDEBAR_WIDTH) sidebarWidth.value = w
  }

  function toggleSidebar() {
    sidebarOpened.value = !sidebarOpened.value
    localStorage.setItem('sidebar-opened', String(sidebarOpened.value))
  }

  function setSidebarWidth(width: number) {
    sidebarWidth.value = Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_SIDEBAR_WIDTH, width))
    localStorage.setItem('sidebar-width', String(sidebarWidth.value))
  }

  function setDevice(d: 'desktop' | 'mobile') {
    device.value = d
  }

  function setMenuList(menus: MenuItem[]) {
    menuList.value = menus
  }

  function getMenuNameByPath(path: string): string | null {
    function find(items: MenuItem[]): string | null {
      for (const item of items) {
        if (item.path === path) return item.name
        if (item.children?.length) {
          const found = find(item.children)
          if (found) return found
        }
      }
      return null
    }
    return find(menuList.value)
  }

  function getBreadcrumbChain(targetPath: string): BreadcrumbItem[] {
    const chain: BreadcrumbItem[] = []

    function find(items: MenuItem[], parents: BreadcrumbItem[]): boolean {
      for (const item of items) {
        if (item.menuType === 'BUTTON') continue
        const current: BreadcrumbItem = {
          name: item.path ? item.name : item.name,
          path: item.path || null,
        }
        const newParents = [...parents, current]

        if (item.path === targetPath) {
          chain.push(...newParents)
          return true
        }
        if (item.children?.length && find(item.children, newParents)) {
          return true
        }
      }
      return false
    }

    find(menuList.value, [])
    return chain
  }

  return { sidebarOpened, sidebarWidth, toggleSidebar, setSidebarWidth, device, menuList, setDevice, setMenuList, getMenuNameByPath, getBreadcrumbChain }
})
