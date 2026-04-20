<template>
  <div class="empty-state">
    <el-icon :size="64" color="#c0c4cc"><Document /></el-icon>
    <p>{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'

defineProps<{ text?: string }>()
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #909399;
}
</style>
