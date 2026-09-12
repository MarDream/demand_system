import { createRouter, createWebHistory } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/tasks',
      name: 'TaskList',
      component: () => import('@/views/tasks/TaskList.vue'),
      meta: { title: '需求', requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'NotificationList',
      component: () => import('@/views/notifications/NotificationList.vue'),
      meta: { title: '通知', requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/profile/Profile.vue'),
      meta: { title: '我的', requiresAuth: true },
    },
    {
      path: '/assistant',
      name: 'AssistantChat',
      component: () => import('@/views/assistant/AssistantChat.vue'),
      meta: { title: 'AI 助手', requiresAuth: true },
    },
    {
      path: '/requirement/:id',
      name: 'RequirementDetail',
      component: () => import('@/views/requirement/RequirementDetail.vue'),
      meta: { title: '需求详情', requiresAuth: true },
    },
    {
      path: '/',
      redirect: '/tasks',
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/tasks',
    },
  ],
})

router.beforeEach(async (to) => {
  document.title = to.meta.title ? String(to.meta.title) : '需求管理系统'
  if (!to.meta.requiresAuth) return true

  const userStore = useUserStore()
  if (!userStore.isLogin()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router
