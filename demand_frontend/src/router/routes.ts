import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' },
      },
      {
        path: 'requirements',
        name: 'Requirements',
        component: () => import('@/views/requirements/index.vue'),
        meta: { title: '需求管理', icon: 'Document' },
      },
      {
        path: 'requirements/:id',
        name: 'RequirementDetail',
        component: () => import('@/views/requirements/detail.vue'),
        meta: { title: '需求详情', hidden: true },
      },
      {
        path: 'requirements/create',
        name: 'RequirementCreate',
        component: () => import('@/views/requirements/create.vue'),
        meta: { title: '新建/编辑需求', hidden: true },
      },
      {
        path: 'iterations',
        name: 'Iterations',
        component: () => import('@/views/iterations/index.vue'),
        meta: { title: '迭代管理', icon: 'Calendar' },
      },
      {
        path: 'reviews',
        name: 'Reviews',
        component: () => import('@/views/reviews/index.vue'),
        meta: { title: '评审管理', icon: 'ChatDotRound' },
      },
      {
        path: 'workflow',
        name: 'Workflow',
        component: () => import('@/views/workflow/index.vue'),
        meta: { title: '工作流配置', icon: 'Share' },
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '统计报表', icon: 'TrendCharts' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '系统设置', icon: 'Setting' },
      },
      {
        path: 'settings/projects',
        name: 'ProjectSettings',
        component: () => import('@/views/settings/projects.vue'),
        meta: { title: '项目管理', hidden: true },
      },
      {
        path: 'settings/users',
        name: 'UserManage',
        component: () => import('@/views/settings/users.vue'),
        meta: { title: '用户管理', hidden: true },
      },
      {
        path: 'settings/org',
        name: 'OrgManage',
        component: () => import('@/views/settings/org.vue'),
        meta: { title: '组织架构', hidden: true },
      },
      {
        path: 'settings/requirements',
        name: 'RequirementConfig',
        component: () => import('@/views/settings/requirements.vue'),
        meta: { title: '需求配置', hidden: true },
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notifications/index.vue'),
        meta: { title: '通知中心', hidden: true },
      },
    ],
  },
]
