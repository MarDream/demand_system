<template>
  <span class="status-tag" :class="[`status-tag--${type}`, `status-tag--${size}`, { 'is-dot': dot }]">
    <span v-if="dot" class="status-tag__dot" />
    <span class="status-tag__label"><slot>{{ label }}</slot></span>
  </span>
</template>

<script setup lang="ts">
/**
 * 通用状态标签：软色底 + 状态圆点的企业风 tag。
 * 替代各页面手写的 el-tag + statusMap 组合。
 *
 * 用法：
 *   <StatusTag :value="row.status" dot />
 *   <StatusTag type="success">自定义文案</StatusTag>
 *   <StatusTag :value="row.status" :overrides="{ DELETED: 'danger' }" dot />
 */
import { computed } from 'vue'
import { commonStatusType, type StatusTagType } from '@/utils/statusTag'

const props = withDefaults(
  defineProps<{
    /** 状态值，配合内置通用映射解析 type；也可以只传 type + slot 文案 */
    value?: string | null
    /** 显式指定类型，优先于 value 推断 */
    type?: StatusTagType
    /** 局部覆盖映射 */
    overrides?: Record<string, StatusTagType>
    /** 显示文案，默认为 value 原文；也可用 slot 自定义 */
    label?: string
    /** 是否显示状态圆点 */
    dot?: boolean
    size?: 'small' | 'default'
  }>(),
  { dot: true, size: 'small' },
)

const type = computed<StatusTagType>(() => props.type ?? commonStatusType(props.value, props.overrides))
</script>

<style scoped lang="scss">
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 8px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
  color: var(--status-fg, var(--color-text-secondary));
  background: var(--status-bg, var(--color-fill-secondary, #f1f5f9));

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
    background: currentColor;
  }

  &--small {
    line-height: 20px;
  }

  &--default {
    padding: 0 10px;
    font-size: var(--font-size-sm);
    line-height: 24px;
  }

  &--success {
    --status-fg: var(--color-success);
    --status-bg: var(--color-success-bg, rgba(16, 185, 129, 0.12));
  }

  &--warning {
    --status-fg: var(--color-warning);
    --status-bg: var(--color-warning-bg, rgba(245, 158, 11, 0.14));
  }

  &--danger {
    --status-fg: var(--color-danger);
    --status-bg: var(--color-danger-bg, rgba(239, 68, 68, 0.12));
  }

  &--primary {
    --status-fg: var(--color-primary);
    --status-bg: var(--color-primary-bg, rgba(37, 99, 235, 0.1));
  }

  &--info {
    --status-fg: var(--color-text-secondary);
    --status-bg: var(--color-fill-secondary, #f1f5f9);
  }
}
</style>
