import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken, buildLoginPath } from '@/utils/auth'
import { useUserStore } from '@/stores/modules/user'
import { usePermission } from '@/composables/usePermission'

// 禁用 trickle（自动递增定时器），避免其内部 setTimeout 循环
// 在路由切换时与 DOM 渲染竞争主线程，触发 Chrome Violation 警告
// 进度条仍会显示，由 afterEach 的 done() 结束
NProgress.configure({ showSpinner: false, trickle: false })

export function setupGuards(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    document.title = `${to.meta.title || ''} - 综合运营管理平台`

    if (to.meta.publicAccess) {
      next()
      return
    }

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

    if (to.path === buildLoginPath() || to.path === '/login') {
      if (token) {
        next('/')
      } else {
        next()
      }
      return
    }

    if (!token) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    if (!userStore.userInfo) {
      try {
        await userStore.getUserInfo()
      } catch {
        await userStore.logout()
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
    }

    // 无组织用户首次登录：必须先绑定组织，拦截去往非 dashboard 页的导航
    // dashboard 页面挂载了 OrgBindDialog，放行让弹窗展示即可，避免 next('/dashboard') 死循环
    if (userStore.needOrgBind && to.path !== '/dashboard') {
      next('/dashboard')
      return
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
