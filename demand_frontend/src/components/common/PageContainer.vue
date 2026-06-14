<template>
  <div class="app-page" :class="[{ 'app-page--card': props.variant === 'card' }]">
    <div v-if="showHeader" class="app-page__header">
      <div class="app-page__header-left">
        <slot name="breadcrumb">
          <Breadcrumb v-if="props.breadcrumb" />
        </slot>
        <div v-if="props.showTitle && (props.title || props.subtitle || $slots.title)" class="app-page__title">
          <div class="app-page__title-main">
            <slot name="title">
              <h2 class="app-page__h2">{{ displayTitle }}</h2>
            </slot>
          </div>
          <div v-if="props.subtitle && props.showTitle" class="app-page__subtitle">{{ props.subtitle }}</div>
        </div>
      </div>
      <div v-if="$slots.headerActions" class="app-page__header-actions">
        <slot name="headerActions" />
      </div>
    </div>

    <div class="app-page__content">
      <slot />
    </div>

    <div v-if="$slots.footer" class="app-page__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { useRoute } from 'vue-router'
import Breadcrumb from '@/components/layout/Breadcrumb.vue'
import { useAppStore } from '@/stores/modules/app'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'

const route = useRoute()
const appStore = useAppStore()

const props = withDefaults(defineProps<{
  title?: string
  subtitle?: string
  breadcrumb?: boolean
  variant?: 'plain' | 'card'
  showTitle?: boolean
}>(), {
  breadcrumb: false,
  variant: 'plain',
  showTitle: false,
})

const slots = useSlots()

const displayTitle = computed(() => {
  if (!props.title) return ''
  return appStore.getMenuNameByPath(resolveActiveMenuPath(route)) || props.title
})

const showHeader = computed(() => {
  const hasTitle = props.showTitle && !!(props.title || props.subtitle || slots.title)
  return !!(
    hasTitle ||
    props.breadcrumb ||
    slots.breadcrumb ||
    slots.headerActions
  )
})
</script>

<style scoped lang="scss">
.app-page {
  padding: var(--page-padding-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  min-height: 100%;
}

.app-page--card {
  .app-page__content {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    padding: var(--card-padding-lg);
  }
}

.app-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.app-page__header-left {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-width: 0;
}

.app-page__title {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.app-page__h2 {
  margin: 0;
  font-size: var(--font-size-xl);
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.app-page__subtitle {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.app-page__header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.app-page__content {
  min-width: 0;
}

.app-page__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
