<template>
  <el-button v-if="visible" v-bind="$attrs">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePermission } from '@/composables/usePermission'

const props = defineProps<{
  permission?: string | string[]
}>()

const { hasPermission, hasAnyPermission, hasRole, hasAnyRole } = usePermission()

const visible = computed(() => {
  if (!props.permission) {
    return true
  }
  if (typeof props.permission === 'string') {
    return hasPermission(props.permission) || hasRole(props.permission)
  }
  return hasAnyPermission(props.permission) || hasAnyRole(props.permission)
})
</script>
