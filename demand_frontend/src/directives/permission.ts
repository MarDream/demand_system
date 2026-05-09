import type { Directive, DirectiveBinding } from 'vue'
import { usePermission } from '@/composables/usePermission'

export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const value = binding.value
    if (!value) {
      return
    }

    const { hasPermission, hasAnyPermission, hasRole, hasAnyRole } = usePermission()

    let allowed = false
    if (typeof value === 'string') {
      allowed = hasPermission(value) || hasRole(value)
    } else if (Array.isArray(value)) {
      allowed = hasAnyPermission(value) || hasAnyRole(value)
    }

    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  },
}
