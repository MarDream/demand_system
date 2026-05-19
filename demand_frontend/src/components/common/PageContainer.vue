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
  return appStore.getMenuNameByPath(route.path) || props.title
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
  padding: $page-padding;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  min-height: 100%;
}

.app-page--card {
  .app-page__content {
    background: $bg-container;
    border: 1px solid $border-color;
    border-radius: $card-radius;
    padding: $card-padding;
  }
}

.app-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-md;
}

.app-page__header-left {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  min-width: 0;
}

.app-page__title {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.app-page__h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
  font-weight: 600;
  color: $text-color;
}

.app-page__subtitle {
  color: $text-color-secondary;
  font-size: $font-size-sm;
}

.app-page__header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
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
