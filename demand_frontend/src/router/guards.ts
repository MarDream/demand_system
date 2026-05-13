import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/modules/user'
import { usePermission } from '@/composables/usePermission'

NProgress.configure({ showSpinner: false })

export function setupGuards(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    document.title = `${to.meta.title || ''} - 综合运营管理平台`

    // Override with dynamic menu name if available
    try {
      const { useAppStore } = await import('@/stores/modules/app')
      const appStore = useAppStore()
      const menuName = appStore.getMenuNameByPath(to.path)
      if (menuName) {
        document.title = `${menuName} - 综合运营管理平台`
      }
    } catch { /* ignore */ }

    const token = getToken()
    const userStore = useUserStore()

    if (to.path === '/login') {
      if (token) {
        next('/')
      } else {
        next()
      }
      return
    }

    if (!token) {
      next('/login')
      return
    }

    if (!userStore.userInfo) {
      try {
        await userStore.getUserInfo()
      } catch {
        await userStore.logout()
        next('/login')
        return
      }
    }

    const { hasAnyRole, hasAnyPermission } = usePermission()
    const requiredRoles = Array.isArray(to.meta.requiredRoles)
      ? (to.meta.requiredRoles as string[])
      : []
    const requiredPermissions = Array.isArray(to.meta.requiredPermissions)
      ? (to.meta.requiredPermissions as string[])
      : []

    if (requiredRoles.length > 0 && !hasAnyRole(requiredRoles)) {
      next('/dashboard')
      return
    }

    if (requiredPermissions.length > 0 && !hasAnyPermission(requiredPermissions)) {
      next('/dashboard')
      return
    }

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
