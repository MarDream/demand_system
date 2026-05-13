<template>
  <i :class="name" :style="{ fontSize: 'inherit' }" />
</template>

<script setup lang="ts">
defineProps<{
  name: string
}>()
</script>
