<template>
  <teleport to="body">
    <transition-group
      name="toast"
      tag="div"
      class="app-toast-container"
    >
      <div
        v-for="toast in toastQueue"
        :key="toast.id"
        class="app-toast"
        :class="`app-toast--${toast.type}`"
        @mouseenter="pauseToast(toast.id)"
        @mouseleave="resumeToast(toast.id)"
      >
        <div class="app-toast__icon">
          <el-icon :size="18">
            <component :is="iconMap[toast.type]" />
          </el-icon>
        </div>
        <div class="app-toast__content">
          <div v-if="toast.title" class="app-toast__title">{{ toast.title }}</div>
          <div class="app-toast__message">{{ toast.message }}</div>
        </div>
        <button
          class="app-toast__close"
          type="button"
          @click="removeToast(toast.id)"
        >
          <el-icon :size="14"><Close /></el-icon>
        </button>
      </div>
    </transition-group>
  </teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  CircleCheck,
  CircleClose,
  InfoFilled,
  WarningFilled,
  Close,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

type ToastType = 'success' | 'warning' | 'error' | 'info'

interface ToastOptions {
  title?: string
  message: string
  type?: ToastType
  duration?: number
}

interface ToastItem {
  id: number
  title?: string
  message: string
  type: ToastType
  duration: number
  timer?: ReturnType<typeof setTimeout>
}

const iconMap: Record<ToastType, Component> = {
  success: CircleCheck,
  warning: WarningFilled,
  error: CircleClose,
  info: InfoFilled,
}

const toastQueue = ref<ToastItem[]>([])
let toastId = 0

function removeToast(id: number) {
  const idx = toastQueue.value.findIndex(t => t.id === id)
  if (idx > -1) {
    toastQueue.value.splice(idx, 1)
  }
}

function showToast(options: ToastOptions) {
  const id = ++toastId
  const duration = options.duration ?? 3000
  const toast: ToastItem = {
    ...options,
    id,
    type: options.type ?? 'info',
    duration,
  }
  toastQueue.value.push(toast)

  if (duration > 0) {
    toast.timer = setTimeout(() => removeToast(id), duration)
  }
}

function pauseToast(id: number) {
  const toast = toastQueue.value.find(t => t.id === id)
  if (toast?.timer) {
    clearTimeout(toast.timer)
    toast.timer = undefined
  }
}

function resumeToast(id: number) {
  const toast = toastQueue.value.find(t => t.id === id)
  if (toast) {
    toast.timer = setTimeout(() => removeToast(id), toast.duration)
  }
}

function success(message: string, title?: string, options?: Partial<ToastOptions>) {
  showToast({ message, title, type: 'success', ...options })
}

function warning(message: string, title?: string, options?: Partial<ToastOptions>) {
  showToast({ message, title, type: 'warning', ...options })
}

function error(message: string, title?: string, options?: Partial<ToastOptions>) {
  showToast({ message, title, type: 'error', duration: 5000, ...options })
}

function info(message: string, title?: string, options?: Partial<ToastOptions>) {
  showToast({ message, title, type: 'info', ...options })
}

// 导出全局 API — 通过 defineExpose 暴露给父组件
const toastApi = { success, warning, error, info, showToast }

defineExpose(toastApi)
</script>

<style scoped lang="scss">
.app-toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  pointer-events: none;
  max-width: 400px;
}

.app-toast {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg);
  border-radius: var(--radius-lg);
  background: var(--color-surface, #fff);
  border: 1px solid var(--color-border, #e2e8f0);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  pointer-events: auto;
  position: relative;
  overflow: hidden;

  &__icon {
    flex-shrink: 0;
    margin-top: 2px;
  }

  &__content {
    flex: 1;
    min-width: 0;
  }

  &__title {
    font-size: var(--font-size-sm, 14px);
    font-weight: var(--font-weight-semibold, 600);
    color: var(--color-text-primary, #0f172a);
    margin-bottom: var(--spacing-xs, 4px);
  }

  &__message {
    font-size: var(--font-size-sm, 14px);
    color: var(--color-text-secondary, #475569);
    line-height: 1.5;
  }

  &__close {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border: none;
    background: transparent;
    color: var(--color-muted-text, #94a3b8);
    cursor: pointer;
    border-radius: var(--radius-sm, 4px);
    transition: all 0.15s ease;
    margin-top: 2px;

    &:hover {
      background: var(--color-surface-alt, #f1f5f9);
      color: var(--color-text-primary, #0f172a);
    }
  }

  // 类型样式
  &--success {
    .app-toast__icon { color: var(--el-color-success, #10b981); }
    border-left: 3px solid var(--el-color-success, #10b981);
  }

  &--warning {
    .app-toast__icon { color: var(--el-color-warning, #f59e0b); }
    border-left: 3px solid var(--el-color-warning, #f59e0b);
  }

  &--error {
    .app-toast__icon { color: var(--el-color-danger, #ef4444); }
    border-left: 3px solid var(--el-color-danger, #ef4444);
  }

  &--info {
    .app-toast__icon { color: var(--el-color-info, #64748b); }
    border-left: 3px solid var(--el-color-info, #64748b);
  }
}

// 入场/离场动画
.toast-enter-active {
  transition: all 0.25s cubic-bezier(0, 0, 0.2, 1);
}

.toast-leave-active {
  transition: all 0.15s cubic-bezier(0.4, 0, 1, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(20px) scale(0.95);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(20px) scale(0.95);
}
</style>
