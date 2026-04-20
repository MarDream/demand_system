<template>
  <el-button v-bind="$attrs" v-permission="permission">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
defineProps<{
  permission?: string | string[]
}>()
</script>
