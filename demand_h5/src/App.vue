<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useNotificationStore } from '@/stores/notification'
import { getToken } from '@/utils/auth'

const route = useRoute()
const notificationStore = useNotificationStore()
const { unread } = storeToRefs(notificationStore)

const showTabbar = computed(() =>
  ['/tasks', '/assistant', '/notifications', '/profile'].includes(route.path),
)

/** 角标：>99 显示 99+；0 时传空串隐藏（Vant 对 0 会渲染数字角标） */
const unreadBadge = computed(() => {
  if (unread.value <= 0) return ''
  return unread.value > 99 ? '99+' : String(unread.value)
})

function onVisibilityChange() {
  // 从后台/其它应用切回时立即刷新，不必等下一个轮询周期
  if (document.visibilityState === 'visible' && getToken()) {
    void notificationStore.refresh(false)
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  if (getToken()) {
    notificationStore.startPolling()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

// 登录成功跳转到首页时启动轮询；退出登录回到 /login 时停止
// （localStorage 非响应式，不能直接 watch getToken()，用路由变化作为触发点）
watch(
  () => route.path,
  () => {
    if (getToken()) {
      notificationStore.startPolling()
    } else {
      notificationStore.stopPolling()
      notificationStore.unread = 0
    }
  },
)
</script>

<template>
  <router-view v-slot="{ Component }">
    <component :is="Component" />
  </router-view>
  <van-tabbar v-if="showTabbar" route safe-area-inset-bottom>
    <van-tabbar-item to="/tasks" icon="todo-list-o">需求</van-tabbar-item>
    <van-tabbar-item to="/assistant" icon="chat-o">助手</van-tabbar-item>
    <van-tabbar-item to="/notifications" icon="bell-o" :badge="unreadBadge">通知</van-tabbar-item>
    <van-tabbar-item to="/profile" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>
