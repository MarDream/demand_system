import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarOpened = ref(true)
  const device = ref<'desktop' | 'mobile'>('desktop')

  // Restore from localStorage
  const saved = localStorage.getItem('sidebar-opened')
  if (saved !== null) sidebarOpened.value = saved === 'true'

  function toggleSidebar() {
    sidebarOpened.value = !sidebarOpened.value
    localStorage.setItem('sidebar-opened', String(sidebarOpened.value))
  }

  function setDevice(d: 'desktop' | 'mobile') {
    device.value = d
  }

  return { sidebarOpened, device, toggleSidebar, setDevice }
})
