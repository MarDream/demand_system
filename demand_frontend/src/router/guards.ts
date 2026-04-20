import type { Router } from 'vue-router'
import { getToken } from '@/utils/auth'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

export function setupGuards(router: Router) {
  router.beforeEach((to, _from, next) => {
    NProgress.start()
    document.title = `${to.meta.title || ''} - 需求管理系统`

    const token = getToken()
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

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
