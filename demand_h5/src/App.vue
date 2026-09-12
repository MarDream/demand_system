<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const showTabbar = computed(() =>
  ['/tasks', '/assistant', '/notifications', '/profile'].includes(route.path),
)
</script>

<template>
  <router-view v-slot="{ Component }">
    <component :is="Component" />
  </router-view>
  <van-tabbar v-if="showTabbar" route safe-area-inset-bottom>
    <van-tabbar-item to="/tasks" icon="todo-list-o">需求</van-tabbar-item>
    <van-tabbar-item to="/assistant" icon="chat-o">助手</van-tabbar-item>
    <van-tabbar-item to="/notifications" icon="bell-o">通知</van-tabbar-item>
    <van-tabbar-item to="/profile" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>
