import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { ElMessage } from 'element-plus'
import { getToken, buildLoginPath } from '@/utils/auth'
import { useUserStore } from '@/stores/modules/user'
import { useAppStore } from '@/stores/modules/app'
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

    // 无组织用户首次登录：必须先绑定组织。OrgBindDialog 挂在 DefaultLayout（任何页面都会弹），
    // 所以落点可以是任意页面 —— 若 /dashboard 无权限，落点按菜单顺序解析，避免重定向死循环。
    if (userStore.needOrgBind) {
      const orgBindAppStore = useAppStore()
      let orgBindHome: string | null = '/dashboard'
      try {
        if (!orgBindAppStore.menuList.length) {
          const { getCurrentMenus } = await import('@/api/modules/menu')
          const res = await getCurrentMenus() as any
          const data = res.data ?? res
          orgBindAppStore.setMenuList(Array.isArray(data) ? data : [])
        }
        orgBindHome = orgBindAppStore.resolveHomePath() ?? '/dashboard'
      } catch { /* 拉不到菜单就回 /dashboard 兜底 */ }
      // 目标已经是落点（或其子路由）则放行，让 OrgBindDialog 弹窗完成绑定
      if (to.path === orgBindHome || (orgBindHome !== '/' && to.path.startsWith(orgBindHome + '/'))) {
        next()
        return
      }
      next(orgBindHome)
      return
    }

    const { hasAnyRole, hasAnyPermission } = usePermission()
    const requiredRoles = Array.isArray(to.meta.requiredRoles)
      ? (to.meta.requiredRoles as string[])
      : []
    const requiredPermissions = Array.isArray(to.meta.requiredPermissions)
      ? (to.meta.requiredPermissions as string[])
      : []

    const deniedByRoles = requiredRoles.length > 0 && !hasAnyRole(requiredRoles)
    const deniedByPermissions = requiredPermissions.length > 0 && !hasAnyPermission(requiredPermissions)
    const wantsHome = to.path === '/' || to.path === '/dashboard'
    if (deniedByRoles || deniedByPermissions) {
      // 访问首页（/ 或 /dashboard）无权限时，按菜单顺序取首个有权限的菜单；
      // 菜单树未加载则先拉一次。找不到任何有权限菜单时提示并留在 /dashboard。
      if (wantsHome) {
        const appStore = useAppStore()
        try {
          if (!appStore.menuList.length) {
            const { getCurrentMenus } = await import('@/api/modules/menu')
            const res = await getCurrentMenus() as any
            const data = res.data ?? res
            appStore.setMenuList(Array.isArray(data) ? data : [])
          }
        } catch { /* ignore */ }
        const home = appStore.resolveHomePath()
        if (home && home !== '/dashboard') {
          next(home)
          return
        }
      }
      ElMessage.warning('您没有访问该页面的权限，请联系管理员')
      next('/dashboard')
      return
    }

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
