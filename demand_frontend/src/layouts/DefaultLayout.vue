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
        :collapse="!sidebarOpened"
        background-color="#304156"
        text-color="#BFCBD9"
        active-text-color="#409EFF"
        router
      >
        <template v-for="item in visibleMenus" :key="item.index">
          <el-sub-menu v-if="item.children.length" :index="item.index">
            <template #title>
              <div class="sidebar-submenu-title" @click="handleSubmenuNavigate(item)">
                <template v-if="item.isRemix"><i :class="item.icon" class="sidebar-remix-icon" /></template>
                <el-icon v-else><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </div>
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
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
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
  initOpenedMenus()
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

const openedMenus = ref<string[]>([])

function computeOpenedMenus(): string[] {
  const current = activeMenu.value
  const opened: string[] = []
  for (const item of visibleMenus.value) {
    if (!item.children.length) continue
    if (item.children.some(child => child.path === current || (child.path && current.startsWith(child.path + '/')))) {
      opened.push(item.index)
    }
  }
  return opened
}

function updateOpenedMenus() {
  const newOpened = computeOpenedMenus()
  if (JSON.stringify(newOpened) !== JSON.stringify(openedMenus.value)) {
    openedMenus.value = newOpened
  }
}

watch(() => route.path, updateOpenedMenus)

function initOpenedMenus() {
  openedMenus.value = computeOpenedMenus()
}

const menuRef = ref<any>()

function handleSubmenuNavigate(item: SidebarItem) {
  if (item.path) {
    router.push(item.path)
  }
  menuRef.value?.open?.(item.index)
}

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

.sidebar {
  width: $sidebar-width;
  background-color: $sidebar-bg;
  transition: width 0.3s;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;

  &--collapsed {
    width: $sidebar-collapsed-width;
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
    background-color: rgba(64, 158, 255, 0.5);
  }
}

.sidebar-logo {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 0 18px;
  color: #fff;
  font-size: $font-size-lg;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-logo__image {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  object-fit: contain;
}

.sidebar-logo__text {
  overflow: hidden;
  text-overflow: ellipsis;
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

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: $header-height;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 $spacing-md;
  box-shadow: $shadow-sm;
}

.header-left {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.hamburger {
  cursor: pointer;
  font-size: $font-size-md;
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

.sidebar-submenu-title {
  display: inline-flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  cursor: pointer;
}

.header-right {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.user-info {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  cursor: pointer;
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: $font-size-sm;
  line-height: 1.2;
}

.user-role {
  font-size: 12px;
  line-height: 1.2;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-main {
  flex: 1;
  padding: 0;
  overflow: auto;
  background: $bg-color;
}

.notification-badge {
  cursor: pointer;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.notification-popover .popover-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.notification-popover .popover-empty {
  text-align: center;
  color: #909399;
  padding: 20px 0;
  font-size: 13px;
}

.notification-popover .popover-item {
  padding: 8px 0;
  border-bottom: 1px solid #f2f6fc;
  cursor: pointer;
}

.notification-popover .popover-item:hover {
  background: #f5f7fa;
}

.notification-popover .popover-item.unread {
  background: #ecf5ff;
}

.notification-popover .popover-item-title {
  font-size: 13px;
  font-weight: 500;
}

.notification-popover .popover-item-content {
  font-size: 12px;
  color: #606266;
  margin: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-popover .popover-item-time {
  font-size: 11px;
  color: #909399;
}
</style>
