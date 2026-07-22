<template>
  <div v-if="hasError" class="global-error-boundary">
    <el-result icon="error" title="页面出错了" :sub-title="errorMessage || '发生未知错误'">
      <template #extra>
        <el-button type="primary" @click="reload">刷新重试</el-button>
      </template>
    </el-result>
  </div>
  <template v-else>
    <router-view v-slot="{ Component }">
      <transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <AppToast ref="toastRef" />
  </template>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured, provide } from 'vue'
import { ElButton, ElResult } from 'element-plus'
import AppToast from '@/components/common/AppToast.vue'

// C3: 全局错误边界 - 捕获子树渲染期未捕获异常，展示兜底 UI 而非整页白屏
const hasError = ref(false)
const errorMessage = ref('')
const toastRef = ref<InstanceType<typeof AppToast>>()

// 提供全局 toast 引用
provide('toast', toastRef)

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

/* 页面切换过渡动画 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--duration-normal) var(--ease-standard),
              transform var(--duration-normal) var(--ease-standard);
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
.page-fade-enter-to,
.page-fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
