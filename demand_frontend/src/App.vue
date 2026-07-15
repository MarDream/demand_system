<template>
  <div v-if="hasError" class="global-error-boundary">
    <el-result icon="error" title="页面出错了" :sub-title="errorMessage || '发生未知错误'">
      <template #extra>
        <el-button type="primary" @click="reload">刷新重试</el-button>
      </template>
    </el-result>
  </div>
  <router-view v-else />
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { ElButton, ElResult } from 'element-plus'

// C3: 全局错误边界 - 捕获子树渲染期未捕获异常，展示兜底 UI 而非整页白屏
const hasError = ref(false)
const errorMessage = ref('')

onErrorCaptured((err: unknown, _instance, info: string) => {
  // 生产构建已剥离 console，此 console.error 仅 dev 可见
  console.error('[onErrorCaptured]', err, info)
  errorMessage.value = err instanceof Error ? err.message : String(err)
  hasError.value = true
  // 返回 false 阻止错误继续向上冒泡导致整页白屏
  return false
})

function reload() {
  window.location.reload()
}
</script>

<style scoped>
.global-error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--el-bg-color-page, #f5f7fa);
}
</style>
