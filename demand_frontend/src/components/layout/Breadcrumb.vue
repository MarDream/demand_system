<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item
      v-for="(item, index) in breadcrumbs"
      :key="item.path || item.name"
    >
      <router-link v-if="item.path && index < breadcrumbs.length - 1" :to="item.path">
        {{ item.name }}
      </router-link>
      <span v-else>{{ item.name }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'

const route = useRoute()
const appStore = useAppStore()

const breadcrumbs = computed(() => {
  const chain = appStore.getBreadcrumbChain(route.path)
  if (chain.length > 0) return chain

  return route.matched
    .filter(r => r.meta?.title)
    .map(r => ({
      name: r.meta?.title as string || r.name || '',
      path: r.path || null,
    }))
})
</script>
