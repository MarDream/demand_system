<template>
  <div class="default-layout-root">
    <div class="layout-container">
    <div
      class="sidebar"
      :class="sidebarClasses"
      :style="sidebarStyleAttr"
    >
      <!-- 风格④ 双层图标轨：72px 图标轨切模块，240px 浮出面板放二级菜单 -->
      <template v-if="isDualRail">
        <div class="rail">
          <div class="rail__logo">
            <img src="@/assets/logo.png" alt="综合运营管理平台" class="rail__logo-img" />
          </div>
          <div class="rail__nav">
            <button
              v-for="item in visibleMenus"
              :key="item.index"
              class="rail__item"
              :class="{ 'is-active': railActiveModule === item.index, 'is-open': railOpenModule === item.index }"
              :title="item.title"
              type="button"
              @click="onRailClick(item)"
            >
              <template v-if="item.isRemix"><i :class="item.icon" class="sidebar-remix-icon" /></template>
              <el-icon v-else><component :is="item.icon" /></el-icon>
              <span v-if="item.children.length" class="rail__dot" />
            </button>
          </div>
          <div class="rail__footer">
            <el-avatar :size="30" :src="currentUserAvatarUrl || undefined">{{ userStore.userInfo?.realName?.charAt(0) || 'U' }}</el-avatar>
          </div>
        </div>
        <transition name="rail-panel">
          <div v-if="railOpenItem" class="rail-flyout">
            <div class="rail-flyout__title">{{ railOpenItem.title }}</div>
            <button
              v-for="child in railOpenItem.children"
              :key="child.path"
              type="button"
              class="rail-flyout__item"
              :class="{ 'is-active': isCurrentMenu(activeMenu, child.path) }"
              @click="router.push(child.path)"
            >
              {{ child.title }}
            </button>
          </div>
        </transition>
      </template>

      <!-- 其余风格：logo + 菜单（collapsible 通过 hover 层展开；grouped 分组渲染） -->
      <template v-else>
        <div class="sidebar__hover-layer">
          <div class="sidebar-logo">
            <img src="@/assets/logo.png" alt="综合运营管理平台" class="sidebar-logo__image" />
            <span class="sidebar-logo__text">综合运营管理平台</span>
          </div>
          <div class="sidebar-nav">
            <!-- 风格⑥ 分组留白：2-3 组导航 + 组名 + 底部用户区 -->
            <template v-if="isGrouped">
              <div v-for="group in menuGroups" :key="group.key" class="nav-group">
                <div class="nav-group__title">{{ group.title }}</div>
                <el-menu
                  :default-active="activeMenu"
                  :default-openeds="openedMenus"
                  :unique-opened="false"
                  :collapse="!sidebarOpened"
                  background-color="var(--color-sidebar-bg)"
                  text-color="var(--color-sidebar-text)"
                  active-text-color="var(--color-sidebar-active)"
                  router
                >
                  <template v-for="item in group.items" :key="item.index">
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
              </div>
            </template>
            <el-menu
              v-else
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
          </div>
          <!-- 风格⑥ 底部固定用户区（中间列表单独滚动） -->
          <div v-if="isGrouped" class="sidebar-user" @click="router.push('/profile')">
            <el-avatar :size="30" :src="currentUserAvatarUrl || undefined">{{ userStore.userInfo?.realName?.charAt(0) || 'U' }}</el-avatar>
            <div class="sidebar-user__meta">
              <span class="sidebar-user__name">{{ userStore.userInfo?.realName || '用户' }}</span>
              <span class="sidebar-user__role">{{ roleDisplayText || '个人设置' }}</span>
            </div>
          </div>
        </div>
        <div
          v-if="showResizer"
          class="sidebar-resizer"
          @mousedown="startResize"
        />
      </template>
    </div>
    <div class="main-container">
      <div class="header">
        <div class="header-left">
          <el-icon v-if="!isDualRail && !isCollapsible" class="hamburger" @click="appStore.toggleSidebar">
            <transition name="icon-rotate" mode="out-in">
              <Fold v-if="sidebarOpened" key="fold" />
              <Expand v-else key="expand" />
            </transition>
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
          <el-dropdown v-if="switchableRoles.length > 1" trigger="click" @command="handleSwitchRole">
            <span class="role-switcher">
              <span class="role-switcher-label">{{ activeRoleName || switchableRoles[0]?.name || '' }}</span>
              <el-icon class="role-switcher-icon"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="r in switchableRoles"
                  :key="r.code"
                  :command="r.code"
                >
                  <span class="role-option">
                    <span>{{ r.name }}</span>
                    <el-icon v-if="r.code === userStore.activeRole" class="role-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="28" :src="currentUserAvatarUrl || undefined">{{ userStore.userInfo?.realName?.charAt(0) || 'U' }}</el-avatar>
              <span class="user-meta">
                <span class="user-name">{{ userStore.userInfo?.realName || '用户' }}</span>
                <span v-if="roleDisplayText" class="user-role">{{ roleDisplayText }}</span>
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/profile')">个人设置</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="app-main">
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition name="fade" mode="out-in">
            <!-- key 并入 activeRole：切角色时当前页强制重建（数据按新角色重拉，角标/统计/列表全刷新） -->
            <div :key="`${userStore.activeRole || ''}:${viewRoute.path}`" class="view-wrapper">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </div>
    </div>
  </div>
  <SystemAssistant v-if="userStore.userInfo" />
  <OrgBindDialog v-if="userStore.needOrgBind" />
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
import { Fold, Expand, Bell, ArrowDown, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { isRemixIcon } from '@/components/common/RemixIconData'
import Breadcrumb from '@/components/layout/Breadcrumb.vue'
import OrgBindDialog from '@/components/OrgBindDialog.vue'
import SystemAssistant from '@/components/assistant/SystemAssistant.vue'
import { formatDate } from '@/utils/format'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import { getNotificationList, markAsRead } from '@/api/modules/notification'
import { getCurrentMenus, type MenuItem } from '@/api/modules/menu'
import { resolveAvatarUrl } from '@/utils/presetAvatars'
import { useAppearance } from '@/composables/useAppearance'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { unreadCount } = useNotification()
const { hasPermission, hasAnyRole, hasAnyPermission } = usePermission()

/** 顶栏头像：preset:xxx 前缀转 data URL，其余原样；空回退姓名首字 */
const currentUserAvatarUrl = computed(() => resolveAvatarUrl(userStore.userInfo?.avatar))

const recentNotifications = ref<any[]>([])
const menuList = shallowRef<MenuItem[]>([])
const roleDisplayText = computed(() => {
  const roleNames = userStore.userInfo?.roleNames?.filter(Boolean) || []
  // admin 与 SUPER_ADMIN 等编码可能共享同一中文名，展示层按名去重
  const uniqueNames = Array.from(new Set(roleNames))
  if (uniqueNames.length > 0) {
    return uniqueNames.join(' / ')
  }
  const roles = userStore.userInfo?.roles?.filter(Boolean) || []
  return roles.length > 0 ? roles.join(' / ') : ''
})

// 角色切换：store 已按显示名去重，仅当仍有多个可选角色时展示
const switchableRoles = computed(() => {
  const list = userStore.allRoles || []
  return list.length > 1 ? list : []
})
const activeRoleName = computed(() => {
  const code = userStore.activeRole
  if (!code) return ''
  return userStore.allRoles.find((r) => r.code === code)?.name || code
})

async function handleSwitchRole(roleCode: string | number) {
  const code = String(roleCode || '')
  if (!code || code === (userStore.activeRole || '')) return
  try {
    await userStore.switchRole(code)
    ElMessage.success(`已切换到角色「${activeRoleName.value}」`)
    // 菜单按新角色重新拉取
    await fetchMenus()
    // 当前页面若在新角色下无权限，回到有权限的首个菜单（无仪表盘权限时不硬回 /dashboard）
    const requiredRoles = Array.isArray(route.meta.requiredRoles) ? (route.meta.requiredRoles as string[]) : []
    const requiredPermissions = Array.isArray(route.meta.requiredPermissions) ? (route.meta.requiredPermissions as string[]) : []
    if (
      (requiredRoles.length > 0 && !hasAnyRole(requiredRoles)) ||
      (requiredPermissions.length > 0 && !hasAnyPermission(requiredPermissions))
    ) {
      router.push(appStore.resolveHomePath() ?? '/dashboard')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '角色切换失败')
  }
}

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
  '/settings/assistant': 8,
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

// ===== 侧边栏风格（个人设置 → 外观 → 侧边栏风格） =====
const { sidebar: sidebarStyle } = useAppearance()
const isDualRail = computed(() => sidebarStyle.value === 'dual-rail')
const isCollapsible = computed(() => sidebarStyle.value === 'collapsible')
const isGrouped = computed(() => sidebarStyle.value === 'grouped')
const sidebarClasses = computed(() => ({ 'sidebar--collapsed': !sidebarOpened.value }))
/** 折叠展开/双层图标轨宽度由风格 CSS 固定，不吃内联宽度与拖拽 */
const sidebarStyleAttr = computed(() => {
  if (isDualRail.value || isCollapsible.value) return {}
  return sidebarOpened.value ? { width: sidebarWidth.value + 'px', transition: isResizing.value ? 'none' : 'width 0.3s' } : {}
})
const showResizer = computed(() => sidebarOpened.value && !isDualRail.value && !isCollapsible.value)

interface MenuGroup { key: string; title: string; items: SidebarItem[] }
/** 分组规则：按路径前缀归入 概览/工作台/系统管理 三组（未命中归工作台） */
function groupKeyOf(item: SidebarItem): string {
  const p = item.path || item.children[0]?.path || ''
  if (/^\/(dashboard|home|index)\b/.test(p)) return 'overview'
  if (/^\/(settings|system)\b/.test(p)) return 'system'
  return 'workspace'
}
const menuGroups = computed<MenuGroup[]>(() => {
  const groups: MenuGroup[] = [
    { key: 'overview', title: '概览', items: [] },
    { key: 'workspace', title: '工作台', items: [] },
    { key: 'system', title: '系统管理', items: [] },
  ]
  visibleMenus.value.forEach((item) => {
    const key = groupKeyOf(item)
    ;(groups.find(g => g.key === key) || groups[1]).items.push(item)
  })
  return groups.filter(g => g.items.length > 0)
})

// 双层图标轨：图标切模块（有子菜单弹出 240px 面板，无子菜单直达）
const railOpenModule = ref<string | null>(null)
const railOpenItem = computed(() => visibleMenus.value.find(m => m.index === railOpenModule.value) || null)
const railActiveModule = computed(() =>
  visibleMenus.value.find(
    m => isCurrentMenu(activeMenu.value, m.path) || m.children.some(c => isCurrentMenu(activeMenu.value, c.path)),
  )?.index ?? null,
)
function onRailClick(item: SidebarItem) {
  if (item.children.length > 0) {
    railOpenModule.value = railOpenModule.value === item.index ? null : item.index
  } else {
    railOpenModule.value = null
    router.push(item.path)
  }
}
watch([activeMenu, visibleMenus], () => {
  // 仅当激活模块有二级菜单时自动弹出面板；直达型模块收起
  const active = visibleMenus.value.find(m => m.index === railActiveModule.value)
  railOpenModule.value = active && active.children.length > 0 ? active.index : null
}, { immediate: true })

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
// 外层包裹器：确保 Transition 内的组件只有单一元素根节点
.default-layout-root {
  height: 100vh;
  overflow: hidden;
}

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

// 风格⑤ 折叠展开的悬停层容器 / 分组⑥ 的结构容器（普通风格下是无害的透明包裹层）
.sidebar__hover-layer {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
}

// ===== 风格⑥ 分组留白 + 底部收口（节点仅在 grouped 风格渲染） =====
.nav-group {
  margin-bottom: 32px;

  &:last-child {
    margin-bottom: 8px;
  }
}

.nav-group__title {
  font-size: 11px;
  font-weight: var(--font-weight-semibold, 600);
  letter-spacing: 1.2px;
  text-transform: uppercase;
  color: var(--color-muted-text);
  opacity: 0.8;
  padding: 0 12px;
  margin: 0 0 6px 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar--collapsed .nav-group__title {
  display: none;
}

.sidebar-user {
  margin: 8px 10px 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: var(--radius-md, 12px);
  background: var(--color-sidebar-hover);
  border: 1px solid var(--color-sidebar-border);
  cursor: pointer;
  flex-shrink: 0;
  transition: filter var(--transition-fast), background-color var(--transition-fast);

  &:hover {
    filter: brightness(1.1);
  }
}

.sidebar--collapsed .sidebar-user {
  justify-content: center;
  padding: 8px 6px;
}

.sidebar--collapsed .sidebar-user__meta {
  display: none;
}

.sidebar-user__meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar-user__name {
  font-size: 13px;
  font-weight: var(--font-weight-semibold, 600);
  color: var(--color-on-primary);
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-user__role {
  font-size: 11px;
  line-height: 1.25;
  color: var(--color-sidebar-text);
  opacity: 0.75;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

// 角色切换
.role-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);
  max-width: 180px;

  &:hover {
    background-color: var(--color-surface-alt);
  }
}

.role-switcher-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-switcher-icon {
  font-size: 12px;
  color: var(--color-muted-text);
  flex-shrink: 0;
}

.role-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 120px;
}

.role-check {
  color: var(--color-primary, var(--color-accent));
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

// 图标旋转过渡
.icon-rotate-enter-active,
.icon-rotate-leave-active {
  transition: all 0.2s var(--ease-standard);
}

.icon-rotate-enter-from {
  opacity: 0;
  transform: rotate(-90deg);
}

.icon-rotate-leave-to {
  opacity: 0;
  transform: rotate(90deg);
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

<style lang="scss">
/* ==================== 侧边栏高级风格（html[data-sidebar] 驱动，个人设置选择） ==================== */

/* ---- ① 悬浮岛式：栏体脱离屏幕边缘，四边 16px 留白，圆角 20，淡阴影 ---- */
html[data-sidebar='floating'] .layout-container {
  padding: 16px;
  gap: 16px;
  background: var(--color-background);
}
html[data-sidebar='floating'] .sidebar {
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.10), 0 2px 8px rgba(15, 23, 42, 0.05);
}
html[data-sidebar='floating'] .sidebar--collapsed {
  border-radius: 20px;
}
html[data-appearance-mode='dark'][data-sidebar='floating'] .sidebar {
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
}

/* ---- ② 深色重底：整页浅色，唯一深色给侧边栏；常规项低对比浅灰，仅激活项高亮 ---- */
html[data-sidebar='dark'] .sidebar {
  background: linear-gradient(180deg, #0B111E 0%, #070C16 100%);
  box-shadow: 1px 0 0 rgba(255, 255, 255, 0.04);
}
html[data-sidebar='dark'] .sidebar .el-menu {
  --el-menu-bg-color: transparent;
}
html[data-sidebar='dark'] .sidebar-logo {
  color: rgba(255, 255, 255, 0.92);
  border-bottom-color: rgba(255, 255, 255, 0.06);
}
html[data-sidebar='dark'] .sidebar .el-menu-item,
html[data-sidebar='dark'] .sidebar .el-sub-menu__title {
  color: rgba(255, 255, 255, 0.58) !important;
}
html[data-sidebar='dark'] .sidebar .el-menu-item:hover,
html[data-sidebar='dark'] .sidebar .el-sub-menu__title:hover {
  background-color: rgba(255, 255, 255, 0.06) !important;
  color: rgba(255, 255, 255, 0.9) !important;
}
html[data-sidebar='dark'] .sidebar .el-menu-item.is-active {
  background: var(--gradient-primary) !important;
  color: #fff !important;
  box-shadow: 0 4px 14px var(--color-accent-glow);
}
html[data-sidebar='dark'] .sidebar .el-menu-item.is-active::before {
  display: none;
}
html[data-appearance-mode='dark'][data-sidebar='dark'] .sidebar {
  box-shadow: 1px 0 0 rgba(255, 255, 255, 0.08);
}

/* ---- ③ 磨砂玻璃：半透明背景 + 24px 模糊，边缘 1px 亮白描边 ---- */
html[data-sidebar='glass'] .layout-container {
  background:
    radial-gradient(720px 480px at 6% 10%, var(--color-accent-tint), transparent 62%),
    radial-gradient(820px 560px at 92% 90%, var(--color-accent-glow), transparent 62%),
    var(--color-background);
}
html[data-sidebar='glass'] .app-main {
  background: transparent;
}
html[data-sidebar='glass'] .header {
  background: color-mix(in srgb, var(--color-background) 72%, transparent);
  backdrop-filter: blur(10px);
}
html[data-sidebar='glass'] .sidebar {
  background: color-mix(in srgb, var(--color-surface) 58%, transparent);
  backdrop-filter: blur(24px) saturate(1.4);
  -webkit-backdrop-filter: blur(24px) saturate(1.4);
  border-right: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.06);
}
html[data-sidebar='glass'] .sidebar .el-menu {
  --el-menu-bg-color: transparent;
  /* background-color prop 会写内联变量，需 !important 才能置透明（弹出子菜单除外） */
  background-color: transparent !important;
}
html[data-sidebar='glass'] .el-menu--vertical .el-menu {
  background-color: var(--color-surface) !important;
}
html[data-sidebar='glass'] .sidebar-logo {
  border-bottom-color: color-mix(in srgb, var(--color-border) 70%, transparent);
}
html[data-appearance-mode='dark'][data-sidebar='glass'] .sidebar {
  background: color-mix(in srgb, var(--color-surface) 55%, transparent);
  border-right-color: rgba(255, 255, 255, 0.10);
}

/* ---- ④ 双层图标轨：72px 图标轨 + 240px 二级面板 ---- */
html[data-sidebar='dual-rail'] .sidebar {
  width: 72px !important;
  min-width: 72px;
  background: var(--color-sidebar-bg-gradient);
}
html[data-sidebar='dual-rail'] .sidebar--collapsed {
  width: 72px !important;
}
.rail {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0;
}
.rail__logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}
.rail__logo-img {
  width: 38px;
  height: 38px;
  object-fit: contain;
}
.rail__nav {
  flex: 1;
  overflow-y: auto;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  scrollbar-width: none;
}
.rail__nav::-webkit-scrollbar {
  width: 0;
}
.rail__item {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--color-sidebar-text);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
  transition: background-color 0.18s var(--ease-standard, ease), color 0.18s var(--ease-standard, ease);
}
.rail__item:hover {
  background: var(--color-sidebar-hover);
  color: var(--color-sidebar-active);
}
.rail__item.is-active {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 4px 12px var(--color-accent-glow);
}
.rail__item .sidebar-remix-icon {
  margin-right: 0;
  font-size: 20px;
}
.rail__dot {
  position: absolute;
  top: 9px;
  right: 9px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.rail__item.is-active .rail__dot,
.rail__item.is-open .rail__dot {
  background: #fff;
}
.rail__footer {
  padding: 8px 0 4px;
}
html[data-sidebar='dual-rail'] .rail-flyout {
  position: fixed;
  left: 86px;
  top: 16px;
  width: 240px;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.14);
  padding: 14px 10px;
  z-index: 2100;
}
.rail-flyout__title {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--color-muted-text);
  padding: 0 10px 10px;
  border-bottom: 1px solid var(--color-surface-alt);
  margin-bottom: 8px;
}
.rail-flyout__item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 0 12px;
  height: 38px;
  line-height: 38px;
  border-radius: 10px;
  color: var(--color-text-regular);
  cursor: pointer;
  font-size: 13px;
  transition: background-color 0.15s var(--ease-standard, ease), color 0.15s var(--ease-standard, ease);
}
.rail-flyout__item:hover {
  background: var(--color-surface-alt);
  color: var(--color-text-primary);
}
.rail-flyout__item.is-active {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-weight: 600;
}
.rail-panel-enter-active,
.rail-panel-leave-active {
  transition: opacity 0.18s var(--ease-standard, ease), transform 0.18s var(--ease-standard, ease);
}
.rail-panel-enter-from,
.rail-panel-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

/* ---- ⑤ 折叠展开式：默认 72px 纯图标，悬停平滑展开到 240px，文字延迟 80ms 淡入 ---- */
html[data-sidebar='collapsible'] .sidebar {
  width: 72px !important;
  min-width: 72px;
  position: relative;
  z-index: 120;
  background: transparent;
  box-shadow: none;
  overflow: visible;
}
html[data-sidebar='collapsible'] .sidebar--collapsed {
  width: 72px !important;
}
html[data-sidebar='collapsible'] .sidebar__hover-layer {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 72px;
  overflow: hidden;
  background: var(--color-sidebar-bg-gradient);
  border-radius: 0 18px 18px 0;
  transition: width 0.28s var(--ease-standard, ease), box-shadow 0.28s var(--ease-standard, ease);
}
html[data-sidebar='collapsible'] .sidebar:hover .sidebar__hover-layer {
  width: 240px;
  box-shadow: 20px 0 48px rgba(15, 23, 42, 0.18);
}
html[data-sidebar='collapsible'] .sidebar__hover-layer .sidebar-logo {
  width: 240px;
  flex-shrink: 0;
  justify-content: flex-start;
  padding: 0 16px;
}
html[data-sidebar='collapsible'] .sidebar-logo__text {
  display: inline-block !important;
  opacity: 0;
  transition: opacity 0.15s ease;
}
html[data-sidebar='collapsible'] .sidebar:hover .sidebar-logo__text {
  opacity: 1;
  transition: opacity 0.2s ease 0.08s;
}
html[data-sidebar='collapsible'] .sidebar__hover-layer .el-menu {
  width: 240px;
  flex-shrink: 0;
}
html[data-sidebar='collapsible'] .el-menu span {
  opacity: 0;
  transition: opacity 0.15s ease;
}
html[data-sidebar='collapsible'] .sidebar:hover .el-menu span {
  opacity: 1;
  transition: opacity 0.2s ease 0.08s;
}
html[data-sidebar='collapsible'] .sidebar--collapsed .sidebar-logo {
  justify-content: flex-start;
  padding: 0 16px;
}

/* ---- ⑥ 分组留白：中间列表单独滚动（组间距/组名样式在 scoped 块） ---- */
html[data-sidebar='grouped'] .sidebar-nav {
  overflow-y: auto;
  padding: 8px 10px;
}
html[data-sidebar='grouped'] .sidebar-nav::-webkit-scrollbar {
  width: 4px;
}
html[data-sidebar='grouped'] .sidebar-nav::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.35);
  border-radius: 2px;
}
</style>
