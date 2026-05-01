import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/modules/user'

NProgress.configure({ showSpinner: false })

export function setupGuards(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    document.title = `${to.meta.title || ''} - 需求管理系统`

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

    const requiredRoles = Array.isArray(to.meta.requiredRoles)
      ? (to.meta.requiredRoles as string[])
      : []
    if (requiredRoles.length > 0) {
      const hasRole = requiredRoles.some((role) => userStore.roles.includes(role))
      if (!hasRole) {
        next('/dashboard')
        return
      }
    }

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
