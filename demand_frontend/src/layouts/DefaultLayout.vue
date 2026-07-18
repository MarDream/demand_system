<template>
  <div class="layout-container">
    <div
      class="sidebar"
      :class="{ 'sidebar--collapsed': !sidebarOpened }"
      :style="sidebarOpened ? { width: sidebarWidth + 'px', transition: isResizing ? 'none' : 'width 0.3s' } : {}"
    >
      <div class="sidebar-logo">
        <img src="@/assets/logo.png" alt="综合运营管理平台" class="sidebar-logo__image" />
        <span class="sidebar-logo__text">综合运营管理平台</span>
      </div>
      <el-menu
        ref="menuRef"
        :default-active="activeMenu"
        :default-openeds="openedMenus"
        :unique-opened="false"
        :collapse="!sidebarOpened"
        background-color="var(--color-sidebar-bg)"
        text-color="var(--color-sidebar-text)"
        active-text-color="var(--color-sidebar-active)"
        router
      >
        <template v-for="item in visibleMenus" :key="item.index">
          <el-sub-menu v-if="item.children.length" :index="item.index">
            <template #title>
              <template v-if="item.isRemix"><i :class="item.icon" class="sidebar-remix-icon" /></template>
              <el-icon v-else><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.index" :index="child.path">
              <template v-if="child.isRemix"><i :class="child.icon" class="sidebar-remix-icon" /></template>
              <el-icon v-else><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <template v-if="item.isRemix"><i :class="item.icon" class="sidebar-remix-icon" /></template>
            <el-icon v-else><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div
        v-if="sidebarOpened"
        class="sidebar-resizer"
        @mousedown="startResize"
      />
    </div>
    <div class="main-container">
      <div class="header">
        <div class="header-left">
          <el-icon class="hamburger" @click="appStore.toggleSidebar">
            <Fold v-if="sidebarOpened" />
            <Expand v-else />
          </el-icon>
          <Breadcrumb />
        </div>
        <div class="header-right">
          <el-popover placement="bottom" :width="320" trigger="click">
            <template #reference>
              <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
                <el-icon style="font-size: 20px; cursor: pointer;"><Bell /></el-icon>
              </el-badge>
            </template>
            <div class="notification-popover">
              <div class="popover-header">
                <span>通知</span>
                <el-button type="primary" link size="small" @click="router.push('/notifications')">查看全部</el-button>
              </div>
              <div v-if="recentNotifications.length === 0" class="popover-empty">暂无通知</div>
              <div v-else>
                <div
                  v-for="item in recentNotifications"
                  :key="item.id"
                  class="popover-item"
                  :class="{ unread: item.isRead === 0 }"
                  @click="handleNotificationClick(item)"
                >
                  <div class="popover-item-title">{{ item.title }}</div>
                  <div class="popover-item-content">{{ item.content }}</div>
                  <div class="popover-item-time">{{ formatDate(item.createdAt) }}</div>
                </div>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="28">{{ userStore.userInfo?.realName?.charAt(0) || 'U' }}</el-avatar>
              <span class="user-meta">
                <span class="user-name">{{ userStore.userInfo?.realName || '用户' }}</span>
                <span v-if="roleDisplayText" class="user-role">{{ roleDisplayText }}</span>
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/settings/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="app-main">
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition name="fade" mode="out-in">
            <div :key="viewRoute.path" class="view-wrapper">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </div>
    </div>
  </div>
  <SystemAssistant v-if="userStore.userInfo" />
  <OrgBindDialog v-if="userStore.needOrgBind" />
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, shallowRef, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import { useNotification } from '@/composables/useNotification'
import { usePermission } from '@/composables/usePermission'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { Fold, Expand, Bell } from '@element-plus/icons-vue'
import { isRemixIcon } from '@/components/common/RemixIconData'
import Breadcrumb from '@/components/layout/Breadcrumb.vue'
import OrgBindDialog from '@/components/OrgBindDialog.vue'
import SystemAssistant from '@/components/assistant/SystemAssistant.vue'
import { formatDate } from '@/utils/format'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import { getNotificationList, markAsRead } from '@/api/modules/notification'
import { getCurrentMenus, type MenuItem } from '@/api/modules/menu'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { unreadCount } = useNotification()
const { hasPermission, hasAnyRole } = usePermission()

const recentNotifications = ref<any[]>([])
const menuList = shallowRef<MenuItem[]>([])
const roleDisplayText = computed(() => {
  const roleNames = userStore.userInfo?.roleNames?.filter(Boolean) || []
  if (roleNames.length > 0) {
    return roleNames.join(' / ')
  }
  const roles = userStore.userInfo?.roles?.filter(Boolean) || []
  return roles.length > 0 ? roles.join(' / ') : ''
})

const iconMap: Record<string, Component> = {}
for (const [name, comp] of Object.entries(ElementPlusIcons)) {
  iconMap[name] = comp as Component
}

async function fetchMenus() {
  try {
    const res = await getCurrentMenus() as any
    const data = res.data ?? res
    menuList.value = Array.isArray(data) ? data : []
    appStore.setMenuList(menuList.value)
    rebuildSidebarMenus()
  } catch {
    menuList.value = []
  }
}

interface SidebarItem {
  index: string
  path: string
  title: string
  icon: Component | string
  isRemix: boolean
  children: SidebarItem[]
}

const settingsMenuOrder: Record<string, number> = {
  '/settings/users': 1,
  '/settings/roles': 2,
  '/settings/requirements': 4,
  '/system/workflow-config': 5,
  '/settings/menus': 7,
  '/settings/llm': 10,
}

function buildSidebarItems(items: MenuItem[]): SidebarItem[] {
  return items
    .filter(m => (m.menuType === 'MENU' || m.menuType === 'DIRECTORY') && m.enabled === 1 && m.visible === 1)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map(m => {
      const children = buildSidebarItems(m.children || [])
      const iconName = m.icon || 'Document'
      const remix = isRemixIcon(iconName)
      const isDirectory = m.menuType === 'DIRECTORY'
      const defaultChildPath = children[0]?.path ?? ''
      const ownPath = m.path ?? ''
      return {
        index: m.path || `menu-${m.id}`,
        path: isDirectory ? (defaultChildPath || ownPath) : (ownPath || defaultChildPath),
        title: m.name,
        icon: remix ? iconName : (iconMap[iconName] || iconMap['Document']),
        isRemix: remix,
        children,
      }
    })
    .filter(item => item.path || item.children.length)
}

const visibleMenus = shallowRef<SidebarItem[]>([])

function rebuildSidebarMenus() {
  const builtMenus = buildSidebarItems(menuList.value)
  const settingsMenu = builtMenus.find(item => item.path === '/settings' || item.title === '系统配置')
  const canAccessLlm = hasPermission('menu:settings:llm') || hasPermission('menu:system-config')

  if (settingsMenu && canAccessLlm && !settingsMenu.children.some(child => child.path === '/settings/llm')) {
    settingsMenu.children.push({
      index: '/settings/llm',
      path: '/settings/llm',
      title: '模型配置',
      icon: 'ri-robot-2-line',
      isRemix: true,
      children: [],
    })
  }

  if (settingsMenu) {
    settingsMenu.children.sort((left, right) => {
      const leftOrder = settingsMenuOrder[left.path] ?? 999
      const rightOrder = settingsMenuOrder[right.path] ?? 999
      if (leftOrder !== rightOrder) return leftOrder - rightOrder
      return left.title.localeCompare(right.title, 'zh-CN')
    })
  }

  visibleMenus.value = builtMenus
}

onMounted(fetchMenus)

async function fetchRecentNotifications() {
  try {
    const res = await getNotificationList({ pageNum: 1, pageSize: 5 }) as any
    recentNotifications.value = res.list || res.data?.list || []
  } catch {
  }
}

async function handleNotificationClick(item: any) {
  if (item.isRead === 0) {
    try {
      await markAsRead(item.id)
    } catch {
    }
  }
  if (item.relatedId) {
    router.push({ name: 'RequirementDetail', params: { id: item.relatedId } })
  }
}


watch(unreadCount, () => {
  fetchRecentNotifications()
}, { immediate: true })

const sidebarOpened = computed(() => appStore.sidebarOpened)
const sidebarWidth = computed(() => appStore.sidebarWidth)
const activeMenu = computed(() => resolveActiveMenuPath(route))

function isCurrentMenu(current: string, path: string) {
  return path === current || (path && current.startsWith(path + '/'))
}

const openedMenus = ref<string[]>([])

function computeOpenedMenus(): string[] {
  const current = activeMenu.value
  const opened: string[] = []
  for (const item of visibleMenus.value) {
    if (!item.children.length) continue
    if (isCurrentMenu(current, item.path) || item.children.some(child => isCurrentMenu(current, child.path))) {
      opened.push(item.index)
    }
  }
  return opened
}

const menuRef = ref<any>()

watch([() => route.path, visibleMenus], () => {
  const newOpened = computeOpenedMenus()
  openedMenus.value = newOpened
}, { immediate: true })

const isResizing = ref(false)

function startResize(e: MouseEvent) {
  e.preventDefault()
  isResizing.value = true
  const startX = e.clientX
  const startWidth = sidebarWidth.value

  const onMouseMove = (ev: MouseEvent) => {
    const delta = ev.clientX - startX
    appStore.setSidebarWidth(startWidth + delta)
  }

  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  display: flex;
}

// ===== 侧边栏 =====
.sidebar {
  width: var(--sidebar-width);
  background: var(--color-sidebar-bg-gradient);
  box-shadow: var(--shadow-sidebar);
  transition: width 0.3s var(--ease-standard);
  flex-shrink: 0;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;

  &--collapsed {
    width: var(--sidebar-collapsed-width);
  }
}

.sidebar-resizer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 4px;
  cursor: col-resize;
  z-index: 10;
  transition: background-color 0.2s;

  &:hover,
  &:active {
    background-color: var(--color-accent-overlay);
  }
}

// Logo 区域
.sidebar-logo {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 0 18px;
  color: var(--color-on-primary);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  overflow: hidden;
  position: relative;
  border-bottom: 1px solid var(--color-sidebar-border);

  // Logo 发光效果
  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 24px;
    transform: translateY(-50%);
    width: 52px;
    height: 52px;
    background: radial-gradient(circle, var(--color-accent-glow) 0%, transparent 70%);
    filter: blur(16px);
    pointer-events: none;
  }
}

.sidebar-logo__image {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  object-fit: contain;
  filter: drop-shadow(0 2px 8px var(--color-accent-glow-strong));
  transition: transform var(--transition-normal);

  &:hover {
    transform: scale(1.05);
  }
}

.sidebar-logo__text {
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: 0.5px;
}

.sidebar--collapsed .sidebar-logo__text {
  display: none;
}

.sidebar--collapsed .sidebar-logo {
  justify-content: center;
  padding: 0;
}

.sidebar--collapsed .sidebar-logo__image {
  width: 42px;
  height: 42px;
}

// 菜单增强
:deep(.el-menu) {
  border-right: none;

  .el-menu-item,
  .el-sub-menu__title {
    margin: 2px 8px;
    border-radius: var(--radius-md);
    height: 44px;
    line-height: 44px;
    transition: all var(--transition-fast);

    &:hover {
      background-color: var(--color-sidebar-hover) !important;
    }
  }

  .el-menu-item.is-active {
    background-color: var(--color-sidebar-hover) !important;
    color: var(--color-sidebar-active) !important;
    position: relative;

    // 左侧激活指示条
    &::before {
      content: '';
      position: absolute;
      left: -8px;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 60%;
      background: var(--color-sidebar-active);
      border-radius: 0 3px 3px 0;
    }
  }

  // 子菜单
  .el-sub-menu .el-menu-item {
    min-width: auto;
    height: 40px;
    line-height: 40px;
    padding-left: 52px !important;
    margin: 1px 8px;
    font-size: var(--font-size-sm);
  }
}

.sidebar-remix-icon {
  font-size: 18px;
  margin-right: 5px;
  width: 24px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

// ===== 主内容区 =====
.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// 头部
.header {
  height: var(--header-height);
  background: var(--color-header-bg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
  box-shadow: var(--shadow-xs);
  border-bottom: 1px solid var(--color-header-border);
  z-index: var(--z-sticky);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.hamburger {
  cursor: pointer;
  font-size: var(--font-size-md);
  color: var(--color-header-text);
  padding: 6px;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast), color var(--transition-fast);

  &:hover {
    background-color: var(--color-surface-alt);
    color: var(--color-accent);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

// 用户信息
.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);

  &:hover {
    background-color: var(--color-surface-alt);
  }
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  line-height: 1.2;
}

.user-role {
  font-size: 12px;
  line-height: 1.2;
  color: var(--color-muted-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// 内容区域
.app-main {
  flex: 1;
  padding: 0;
  overflow: auto;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}

// 视图包裹层（确保 Transition 内只有单根节点）
.view-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

// 通知
.notification-badge {
  cursor: pointer;
}

// 页面过渡
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s var(--ease-standard);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// 通知弹窗
.notification-popover {
  .popover-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: var(--font-weight-semibold);
    margin-bottom: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--color-border);
  }

  .popover-empty {
    text-align: center;
    color: var(--color-muted-text);
    padding: 20px 0;
    font-size: var(--font-size-sm);
  }

  .popover-item {
    padding: 8px 0;
    border-bottom: 1px solid var(--color-surface-alt);
    cursor: pointer;
    border-radius: var(--radius-sm);
    transition: background-color var(--transition-fast);

    &:hover {
      background: var(--color-surface-alt);
    }
  }

  .popover-item.unread {
    background: var(--color-info-light);
  }

  .popover-item-title {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
  }

  .popover-item-content {
    font-size: var(--font-size-xs);
    color: var(--color-text-secondary);
    margin: 2px 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .popover-item-time {
    font-size: 11px;
    color: var(--color-muted-text);
  }
}
</style>
