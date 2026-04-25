<template>
  <div class="app-page" :class="[{ 'app-page--card': props.variant === 'card' }]">
    <div v-if="showHeader" class="app-page__header">
      <div class="app-page__header-left">
        <slot name="breadcrumb">
          <Breadcrumb v-if="props.breadcrumb" />
        </slot>
        <div v-if="props.title || props.subtitle || $slots.title" class="app-page__title">
          <div class="app-page__title-main">
            <slot name="title">
              <h2 class="app-page__h2">{{ props.title }}</h2>
            </slot>
          </div>
          <div v-if="props.subtitle" class="app-page__subtitle">{{ props.subtitle }}</div>
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
import Breadcrumb from '@/components/layout/Breadcrumb.vue'

const props = withDefaults(defineProps<{
  title?: string
  subtitle?: string
  breadcrumb?: boolean
  variant?: 'plain' | 'card'
}>(), {
  breadcrumb: true,
  variant: 'plain',
})

const slots = useSlots()

const showHeader = computed(() => {
  return !!(
    props.title ||
    props.subtitle ||
    props.breadcrumb ||
    slots.breadcrumb ||
    slots.title ||
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
