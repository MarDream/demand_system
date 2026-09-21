<template>
  <el-popover ref="popoverRef" :width="440" trigger="click" popper-class="widget-palette-popper">
    <template #reference>
      <slot />
    </template>
    <div class="widget-palette">
      <div v-for="group in groups" :key="group.key" class="widget-palette__section">
        <div class="widget-palette__section-title">{{ group.label }}</div>
        <div class="widget-palette__grid">
          <button
            v-for="item in group.items"
            :key="item.type"
            type="button"
            class="palette-item"
            @click="onSelect(item.type)"
          >
            <span class="palette-item__icon"><i :class="item.icon" /></span>
            <span class="palette-item__label">{{ item.label }}</span>
          </button>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { WIDGET_DEFS } from './widgetDefs'

const emit = defineEmits<{
  (e: 'select', type: string): void
}>()

const popoverRef = ref<{ hide: () => void }>()

const groups = computed(() => [
  { key: 'chart', label: '图表', items: WIDGET_DEFS.filter((d) => d.category === 'chart') },
  { key: 'component', label: '组件', items: WIDGET_DEFS.filter((d) => d.category === 'component') },
])

function onSelect(type: string) {
  popoverRef.value?.hide()
  emit('select', type)
}
</script>

<style scoped lang="scss">
.widget-palette {
  display: flex;
  flex-direction: column;
  gap: 14px;

  &__section-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
  }
}

.palette-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 4px 8px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: var(--color-fill-secondary);
  }

  &__icon {
    width: 44px;
    height: 36px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-md, 8px);
    background: var(--color-fill-secondary);
    color: var(--color-primary);
    font-size: 20px;
  }

  &__label {
    font-size: 12px;
    color: var(--color-text-secondary);
  }
}
</style>
