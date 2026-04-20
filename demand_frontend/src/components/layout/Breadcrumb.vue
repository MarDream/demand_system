<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
      {{ item.meta?.title || item.name }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const breadcrumbs = computed(() => route.matched.filter(r => r.meta?.title))
</script>
