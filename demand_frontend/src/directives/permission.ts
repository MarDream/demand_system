import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/modules/user'

export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    if (value) {
      const userStore = useUserStore()
      const roles = userStore.roles
      const hasPermission = roles.includes('admin') ||
        (Array.isArray(value) ? value.some((r: string) => roles.includes(r)) : roles.includes(value))
      if (!hasPermission) {
        el.parentNode?.removeChild(el)
      }
    }
  },
}
