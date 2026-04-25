<template>
  <div class="layout-container">
    <div class="sidebar" :class="{ 'sidebar--collapsed': !sidebarOpened }">
      <div class="sidebar-logo">需求管理系统</div>
      <el-menu
        :default-active="activeMenu"
        :collapse="!sidebarOpened"
        background-color="#304156"
        text-color="#BFCBD9"
        active-text-color="#409EFF"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/requirements">
          <el-icon><Document /></el-icon>
          <span>需求管理</span>
        </el-menu-item>
        <el-menu-item index="/iterations">
          <el-icon><Calendar /></el-icon>
          <span>迭代管理</span>
        </el-menu-item>
        <el-menu-item index="/reviews">
          <el-icon><ChatDotRound /></el-icon>
          <span>评审管理</span>
        </el-menu-item>
        <el-menu-item index="/workflow">
          <el-icon><Share /></el-icon>
          <span>工作流配置</span>
        </el-menu-item>
        <el-menu-item index="/statistics">
          <el-icon><TrendCharts /></el-icon>
          <span>统计报表</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
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
                  <div class="popover-item-time">{{ formatTime(item.createdAt) }}</div>
                </div>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="28">{{ userStore.userInfo?.realName?.charAt(0) || 'U' }}</el-avatar>
              <span class="user-name">{{ userStore.userInfo?.realName || '用户' }}</span>
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
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import { useNotification } from '@/composables/useNotification'
import { Odometer, Document, Calendar, ChatDotRound, Share, TrendCharts, Setting, Fold, Expand, Bell } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/layout/Breadcrumb.vue'
import { getNotificationList, markAsRead } from '@/api/modules/notification'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { unreadCount } = useNotification()

const recentNotifications = ref<any[]>([])

async function fetchRecentNotifications() {
  try {
    const res = await getNotificationList({ pageNum: 1, pageSize: 5 }) as any
    recentNotifications.value = res.list || res.data?.list || []
  } catch { /* ignore */ }
}

async function handleNotificationClick(item: any) {
  if (item.isRead === 0) {
    try { await markAsRead(item.id) } catch { /* ignore */ }
  }
  if (item.relatedId) {
    router.push({ name: 'RequirementDetail', params: { id: item.relatedId } })
  }
}

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

// Fetch recent notifications periodically alongside unread count
watch(unreadCount, () => {
  fetchRecentNotifications()
}, { immediate: true })

const sidebarOpened = computed(() => appStore.sidebarOpened)
const activeMenu = computed(() => route.path)

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

  &--collapsed {
    width: $sidebar-collapsed-width;
  }
}

.sidebar-logo {
  height: $header-height;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: $font-size-lg;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
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

.user-name {
  font-size: $font-size-sm;
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
