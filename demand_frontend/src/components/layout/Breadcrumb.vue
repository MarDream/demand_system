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
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { getKnowledgeBase } from '@/api/modules/knowledge'
import { resolveActiveMenuPath, resolveRouteBreadcrumbTitle } from '@/utils/menuNavigation'

const route = useRoute()
const appStore = useAppStore()
const knowledgeDetailName = ref('')
const activeMenuPath = computed(() => resolveActiveMenuPath(route))
const currentTitle = computed(() => {
  if (route.name === 'KnowledgeDetail') {
    return knowledgeDetailName.value || '知识库详情'
  }

  return resolveRouteBreadcrumbTitle(route)
})

watch(
  () => [route.name, route.params.id],
  async ([routeName, routeId]) => {
    knowledgeDetailName.value = ''

    if (routeName !== 'KnowledgeDetail') {
      return
    }

    const knowledgeBaseId = Number(routeId)
    if (!Number.isFinite(knowledgeBaseId) || knowledgeBaseId <= 0) {
      return
    }

    try {
      const res = await getKnowledgeBase(knowledgeBaseId)
      const data = (res as any)?.data || res
      knowledgeDetailName.value = data?.name || ''
    } catch {
      knowledgeDetailName.value = ''
    }
  },
  { immediate: true }
)

const breadcrumbs = computed(() => {
  const chain = appStore.getBreadcrumbChain(activeMenuPath.value)
  if (chain.length > 0) {
    if (route.path === activeMenuPath.value) {
      return chain
    }

    const lastItem = chain[chain.length - 1]
    if (!currentTitle.value || lastItem?.name === currentTitle.value) {
      return chain
    }

    return [
      ...chain,
      {
        name: currentTitle.value,
        path: null,
      },
    ]
  }

  return route.matched
    .filter(r => r.meta?.title)
    .map(r => ({
      name: r.meta?.title as string || r.name || '',
      path: r.path || null,
    }))
})
</script>
